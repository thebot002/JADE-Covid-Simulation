package agents;

import graphics.ContainerFrame;
import jade.core.AID;
import jade.core.Agent;
import jade.wrapper.AgentContainer;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.wrapper.AgentController;

import java.util.Objects;
import java.util.Random;

import static java.lang.Thread.sleep;

public class ControllerAgent extends Agent {

    private String container_name;
    private AID[] wanderer_agents;

    // DEBUG
    private final boolean DEBUG = false;

    // Setup variables
    private int agent_count = 100;
    private int init_sick = 10;
    private double agent_speed = 1.0;
    private int contamination_radius = 5;
    private double contamination_prob = 0.8;
    private int min_contamination_length = 20;
    private int max_contamination_length = 30;

    private final int MAX_X = 100;
    private final int MAX_Y = 100;


    @Override
    protected void setup() {
        // Retrieving name of current container
        Object[] args = getArguments();
        if (args.length > 0) container_name = (String) args[0];

        // Generation of agents
        AgentContainer ac = getContainerController();

        // Choosing sick agents
        int[] sick_indices = new int[init_sick];

        Random rand = new Random();
        for (int i = 0; i < init_sick; i++) {
            int potential_ind;
            do {
                potential_ind = rand.nextInt(agent_count);
            } while (array_contains(sick_indices, potential_ind));
            sick_indices[i] = potential_ind;
        }

        try {
            // Generation of agents
            for (int i=0; i<agent_count; i++){
                String init_status = array_contains(sick_indices, i)? "sick": "healthy";
                AgentController wandererAgent = ac.createNewAgent("Wanderer-" + i, "agents.WanderingAgent", new Object[]{
                        init_status,
                        agent_speed,
                        contamination_radius,
                        contamination_prob,
                        min_contamination_length,
                        max_contamination_length
                });
                wandererAgent.start();
            }

            // Waiting for the wandering agents to settle before introducing the controller
            sleep(1000);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }


        // Find the list of wandering agents
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("wanderer-group");
        template.addServices(sd);
        try {
            DFAgentDescription[] result = DFService.search(this, template);
            wanderer_agents = new AID[result.length];
            for (int i = 0; i < result.length; ++i) wanderer_agents[i] = result[i].getName();

            if (DEBUG) {
                System.out.println("Found the following wandering agents:");
                for (AID agent : wanderer_agents) System.out.println("\t" + agent.getName());
            }
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }

        // Inform agents that controller is ready
        ACLMessage ready_message = new ACLMessage(ACLMessage.INFORM);
        ready_message.setOntology("ready");
        for (AID agent: wanderer_agents) ready_message.addReceiver(agent);
        send(ready_message);

        // Receive initial locations back
        MessageTemplate status_message_template = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.MatchOntology("status"));

        String[] replies = new String[wanderer_agents.length];
        for (int i = 0; i < wanderer_agents.length; i++) replies[i] = blockingReceive(status_message_template).getContent();

        //
        double[][] dots = new double[wanderer_agents.length][2];
        String[] statuses = new String[wanderer_agents.length];
        for (int i=0; i< wanderer_agents.length; i++) {
            String[] status = replies[i].split(";");
            dots[i][0] = Double.parseDouble(status[0]);
            dots[i][1] = Double.parseDouble(status[1]);

            statuses[i] = status[2];
        }

        if (DEBUG) {
            System.out.println("New locations");
            for (String repl : replies) System.out.println(repl);
        }

        // Setup move go message
        ACLMessage move_go_msg = new ACLMessage(ACLMessage.INFORM);
        move_go_msg.setOntology("GO");
        for (AID agent: wanderer_agents) move_go_msg.addReceiver(agent);

        // print frame
        ContainerFrame f= new ContainerFrame(dots, statuses);
        f.setTitle(container_name);


        // Initialize loop
        addBehaviour(new TickerBehaviour(this, 100) {
            @Override
            protected void onTick() {

                // Tracking time
                long start_time = System.currentTimeMillis();

                // Sending go
                send(move_go_msg);

                // Receive new locations
                String[] replies = new String[wanderer_agents.length];
                for (int i = 0; i < wanderer_agents.length; i++) replies[i] = blockingReceive(status_message_template).getContent();

                if (DEBUG) {
                    System.out.println("New locations");
                    for (String repl : replies) System.out.println(repl);
                }

                // Redraw container frame
                double[][] dots = new double[wanderer_agents.length][2];
                String[] statuses = new String[wanderer_agents.length];
                for (int i=0; i< wanderer_agents.length; i++) {
                    String[] status = replies[i].split(";");
                    dots[i][0] = Double.parseDouble(status[0]);
                    dots[i][1] = Double.parseDouble(status[1]);

                    statuses[i] = status[2];
                }
                f.setDots(dots, statuses);

                // Tracking time end
                long duration_s = System.currentTimeMillis() - start_time;
                if (DEBUG) System.out.println("[Controller] Iteration done in (ms): " + duration_s);
            }
        });


    }

    private boolean array_contains(int[] array, int element){
        boolean result = false;
        for (Integer array_element: array) result |= (Objects.equals(array_element, element));
        return result;
    }
}
