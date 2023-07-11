package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.ContainerID;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.Objects;
import java.util.Random;

import static agents.Util.*;

public class WanderingAgent extends Agent {

    // Position variables
    private double[] position;
    private final double MAX_X = 100.0;
    private final double MAX_Y = 100.0;

    // Direction variables
    private int headingDegrees;
    private final int MAX_DIR_CHANGE = 20;

    // Neighbours
    private AID[] neighbour_agents;

    // Status variables
    private String status;
    private int sickness_length = -1;
    private boolean is_on_travel = false;
    private int travel_length = 0;

    // common agent variables
    private String host_ip;
    private String container_name;
    private double speed;
    private int contamination_radius;
    private double contamination_probability;
    private int min_contamination_length;
    private int max_contamination_length;
    private double travel_chance;
    private int average_travel_duration;

    // Controller
    private AID controller_agent;
    private AID[] other_controller_agents;
    private AID travel_controller_agent;

    // DEBUG
    private final boolean DEBUG = false;

    @Override
    protected void setup() {

        // Retrieving initial status and parameters
        Object[] args = getArguments();
        if (args.length < 9) {
            System.out.println("Not enough arguments, self destruction");
            doDelete();
        }

        this.host_ip = (String) args[0];
        this.container_name = (String) args[1];
        this.status = (String) args[2];
        this.speed = ((double) args[3]);
        this.contamination_radius = ((int) args[4]);
        this.contamination_probability = ((double) args[5]);
        this.min_contamination_length = ((int) args[6]);
        this.max_contamination_length = ((int) args[7]);
        this.travel_chance = ((double) args[8]);
        this.average_travel_duration = ((int) args[9]);

        if (DEBUG) System.out.println("[" + getName() + "]" + " ready with status " + this.status);

        // Registering to the Wanderer group service
        registerAgentAtService(this, container_name + "-wanderer-group");

        // Define initial position
        Random rand = new Random();
        double init_x = (0.1 + (0.8 * rand.nextDouble())) * this.MAX_X; // between 10 and 90 to not spawn on edge
        double init_y = (0.1 + (0.8 * rand.nextDouble())) * this.MAX_Y; // between 10 and 90 to not spawn on edge
        this.position = new double[]{init_x, init_y};

        // Define initial heading
        this.headingDegrees = (int) (rand.nextDouble() * 360);

        // Waiting for signal from controller to probe for list of neighbour agents
        ACLMessage ready_msg = blockingReceive(getMessageTemplate(ACLMessage.INFORM,"ready"));
        controller_agent = ready_msg.getSender();
        if(DEBUG) System.out.println("[" + getName() + "]" + " received ready from " + controller_agent.getName());

        // Setup status message
        ACLMessage status_message = createMessage(ACLMessage.INFORM, "status", controller_agent);

        // Get other agents
        neighbour_agents = getAgentsAtService(this,container_name + "-wanderer-group", getAID());
        if (DEBUG) {
            System.out.println(getName() + ": found agents:");
            for (AID agent: neighbour_agents) System.out.println("\t" + agent.getName());
        }

        // Setup exhale message and template
        ACLMessage exhale_message = createMessage(ACLMessage.INFORM, "exhale", neighbour_agents);
        MessageTemplate exhale_message_template = getMessageTemplate(ACLMessage.INFORM,"exhale");

        // Reply to the ready with initial location
        status_message.setContent(statusString());
        send(status_message);

        // Receive first go from controller meaning all controllers are ready
        MessageTemplate go_message_template = getMessageTemplate(ACLMessage.INFORM, "GO");

        // Retrieving all other controllers for potential travels
        other_controller_agents = getAgentsAtService(this, "controller-group", controller_agent);

        // Initialize loop behavior
        addBehaviour(new CyclicBehaviour() {
            /*
            Steps
            1. Receive Move GO
            1.1 Delete if receives signal
            2.0 Potential Travel
            2.1 Move
            3. Exhale message to all neighbour agents
            4. Inhale message from all neighbour agents
            5. New health status
            6. Report back status and new location
             */

            @Override
            public void action() {
                Random rand = new Random();

                // 1. Receive GO
                ACLMessage go_msg = blockingReceive(go_message_template);

                if (DEBUG) System.out.println("[" + getName() + "]" + " Received GO");

                // 1.1
                if (Objects.equals(go_msg.getContent(), "delete")){
                    status_message.setContent("deleted");
                    send(status_message);
                    doDelete();
                    return;
                }

                // 2.0 Potential travel
                if (!is_on_travel) {
                    if (rand.nextDouble() < travel_chance) {
                        int other_controller_index = rand.nextInt(other_controller_agents.length);
                        travel_controller_agent = other_controller_agents[other_controller_index];
                        travel(controller_agent, travel_controller_agent);

                        is_on_travel = true;
                        return;
                    }
                }
                else {
                    // Check whether to return
                    travel_length++;
                    if (travel_length > rand.nextDouble() * average_travel_duration * 2){
                        travel(travel_controller_agent, controller_agent);
                        System.out.println("[" + getName() + "] Travelling back");
                    }
                }

                // 2.1 Move
                move();

                // 3. Exhale message to all neighbours
                exhale_message.setContent(statusString());
                send(exhale_message);

                // 4. Inhale message from all neighbours
                for (int i = 0; i < neighbour_agents.length; i++) {
                    ACLMessage inhale_message = blockingReceive(exhale_message_template);
                    String inhale_content = inhale_message.getContent();

                    if (!Objects.equals(status, "healthy")) continue;

                    // Healthy processing
                    String[] inhale_content_components = inhale_content.split(";");

                    // If other agent is not sick, we have nothing to worry about
                    if (!Objects.equals(inhale_content_components[2], "sick")) continue;

                    // Now we check the other agent is in range
                    double x = Double.parseDouble(inhale_content_components[0]);
                    double y = Double.parseDouble(inhale_content_components[1]);

                    if ((Math.pow(x-position[0], 2) + Math.pow(y-position[1],2)) < Math.pow(contamination_radius, 2)){
                        double rand_prob = rand.nextDouble();
                        if (rand_prob < contamination_probability){
                            status = "sick";
                        }
                    }
                }

                // 5. If sick check if switch back to healthy
                if (Objects.equals(status, "sick")) {
                    sickness_length++;
                    if (sickness_length >= min_contamination_length){
                        double heal_chance = ((sickness_length - min_contamination_length) * 1.0) / (max_contamination_length - min_contamination_length);

                        if (rand.nextDouble() < heal_chance) status = "recovered";
                    }
                }

                // 6. Report back status
                status_message.setContent(statusString());
                send(status_message);
            }
        });

    }

    private void move(){
        // Altering direction
        Random rand = new Random();
        int headingChange = (int)((rand.nextDouble()*(this.MAX_DIR_CHANGE*2)) - this.MAX_DIR_CHANGE);
        this.headingDegrees += headingChange;
        this.headingDegrees %= 360;

        double headingRads = (this.headingDegrees * Math.PI) / 180;

        double dx = Math.cos(headingRads);
        double dy = Math.sin(headingRads);

        // Bounce on walls
        if (position[0] + dx < 0 || position[0] + dx > MAX_X ) dx *= -1;
        if (position[1] + dy < 0 || position[1] + dy > MAX_Y ) dy *= -1;
        this.headingDegrees = (int) (Math.atan2(dy,dx) * 180.0 / Math.PI);

        // New position
        this.position[0] += dx;
        this.position[1] += dy;

        if (DEBUG) System.out.println("My position is now" + ((int)this.position[0]) + ", " + (int)this.position[1]);

    }

    public String statusString(){
        return this.position[0] + ";" + this.position[1] + ";" + this.status;
    }

    private void travel(AID from_controller, AID to_controller) {
        // Send request to source controller
        ACLMessage travel_leave_request_message = createMessage(ACLMessage.REQUEST, "travel", from_controller);
        travel_leave_request_message.setContent("leave");
        send(travel_leave_request_message);

        // Send request to target controller
        ACLMessage travel_request_message = createMessage(ACLMessage.REQUEST, "travel", from_controller);
        travel_request_message.setContent("arrive");
        send(travel_request_message);

        // Receive confirmations
        MessageTemplate agreement_message_template = getMessageTemplate(ACLMessage.AGREE, "travel");
        String target_container_name = "";
        for (int i = 0; i < 2; i++) {
            ACLMessage answer = blockingReceive(agreement_message_template);
            if (answer != null && answer.getContent() != null && answer.getContent().length() > 0) target_container_name = answer.getContent();
        }
        if (DEBUG) System.out.println("[" + getName() + "] Travelling to " + target_container_name);

        // perform the move
        ContainerID cID = new ContainerID();
        cID.setName(target_container_name); //Destination container
        cID.setAddress(host_ip); //IP of the host of the container
        cID.setPort("1099"); //port associated with Jade
        doMove(cID);

        // Send notice to all neighbours

        // Get new neighbours
    }
}
