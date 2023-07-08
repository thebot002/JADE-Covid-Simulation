package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.Objects;
import java.util.Random;

public class WanderingAgent extends Agent {

    // Position variables
    private double[] position;
    private final double MAX_X = 100.0;
    private final double MAX_Y = 100.0;

    // Direction variables
    private int headingDegrees;
    private final int MAX_DIR_CHANGE = 20;

    // Neighbours
    AID[] neighbour_agents;

    // Status variables
    private String status;
    private int sickness_length = -1;

    // common agent variables
    private int container_id;
    private double speed;
    private int contamination_radius;
    private double contamination_probability; // To make vary
    private int min_contamination_length;
    private int max_contamination_length;

    // DEBUG
    private final boolean DEBUG = false;

    @Override
    protected void setup() {

        // Retrieving initial status and parameters
        Object[] args = getArguments();
        if (args.length < 6) {
            System.out.println("Not enough arguments, self destruction");
            doDelete();
        }

        this.container_id = (int) args[0];
        this.status = (String) args[1];
        this.speed = ((double) args[2]);
        this.contamination_radius = ((int) args[3]);
        this.contamination_probability = ((double) args[4]);
        this.min_contamination_length = ((int) args[5]);
        this.max_contamination_length = ((int) args[6]);

        if (DEBUG) System.out.println("[" + getName() + "]" + " ready with status " + this.status);

        // Registering to the Wanderer group service
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            ServiceDescription sd = new ServiceDescription();
            sd.setType("wanderer-group-"+container_id);
            sd.setName("Wanderers");
            dfd.addServices(sd);
            DFService.register(this, dfd);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }

        // Define initial position
        Random rand = new Random();
        double init_x = rand.nextDouble(0.1,0.9) * this.MAX_X;
        double init_y = rand.nextDouble(0.1,0.9) * this.MAX_Y;
        this.position = new double[]{init_x, init_y};

        // Define initial heading
        this.headingDegrees = (int) (rand.nextDouble() * 360);

        // Waiting for signal to probe for list of neighbour agents
        ACLMessage ready_msg = blockingReceive(MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.MatchOntology("ready")));

        if(DEBUG) System.out.println("[" + getName() + "]" + " received ready from " + ready_msg.getSender().getName());

        // Setup status message
        ACLMessage status_message = new ACLMessage(ACLMessage.INFORM);
        status_message.setOntology("status");
        status_message.addReceiver(ready_msg.getSender());

        // Reply to the ready with initial location
        status_message.setContent(statusString());
        send(status_message);

        // Get other agents
        neighbour_agents = getNeighbours();
        if (DEBUG) {
            System.out.println(getName() + ": found agents:");
            for (AID agent: neighbour_agents) System.out.println("\t" + agent.getName());
        }

        // Setup exhale message
        ACLMessage exhale_message = new ACLMessage(ACLMessage.INFORM);
        exhale_message.setOntology("exhale");
        for (AID neighbour: neighbour_agents) exhale_message.addReceiver(neighbour);

        // Setup exhale message template
        MessageTemplate exhale_message_template = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.MatchOntology("exhale"));

        // Initialize loop behavior
        addBehaviour(new CyclicBehaviour() {
            /*
            Steps
            1. Receive Move GO
            2. Move

            5. Exhale message to all neighbour agents
            6. Inhale message from all neighbour agents

            7. Report back status and new location
             */

            @Override
            public void action() {
                // 1. Receive GO
                ACLMessage go_msg = blockingReceive(MessageTemplate.and(
                        MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                        MessageTemplate.MatchOntology("GO")));

                if (DEBUG) System.out.println("[" + getName() + "]" + " Received GO");

                // 1.1
                if (Objects.equals(go_msg.getContent(), "delete")){
                    status_message.setContent("deleted");
                    send(status_message);
                    doDelete();
                    return;
                }

                // 2. Move
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

                // 4.1 If sick check if switch back to healthy
                if (Objects.equals(status, "sick")) {
                    sickness_length++;
                    if (sickness_length >= min_contamination_length){
                        Random rand = new Random();
                        double heal_chance = ((sickness_length - min_contamination_length) * 1.0) / (max_contamination_length - min_contamination_length);

                        if (rand.nextDouble() < heal_chance) status = "recovered";
                    }
                }

                // 5. Report back status
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

    private AID[] getNeighbours(){

        AID[] neighnour_agents;
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("wanderer-group-"+container_id);
        template.addServices(sd);

        try {
            DFAgentDescription[] result = DFService.search(this, template);
            neighnour_agents = new AID[result.length-1];
            int agent_number = 0;
            for (DFAgentDescription dfAgentDescription : result) {
                AID agent = dfAgentDescription.getName();
                if (!Objects.equals(agent, getAID())) {
                    neighnour_agents[agent_number] = agent;
                    agent_number++;
                }
            }
            return neighnour_agents;
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }

        return null;
    }

}
