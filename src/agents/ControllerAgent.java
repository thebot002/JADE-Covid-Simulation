package agents;

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

import javax.swing.*;
import java.awt.*;
import java.util.Objects;
import java.util.Random;

import static java.lang.Thread.sleep;

public class ControllerAgent extends Agent {

    // Agents variables
    private AID[] wanderer_agents;
    private double[][] agent_positions;
    private String[] agent_statuses;

    // DEBUG
    private final boolean DEBUG = false;

    private int agent_count;

    private final int MAX_X = 100;
    private final int MAX_Y = 100;

    // Panels
    WandererEnvironmentPanel wandererEnvironmentPanel;


    @Override
    protected void setup() {

        // Retrieving initial status and parameters
        Object[] args = getArguments();
        if (args.length < 8) {
            System.out.println("Not enough arguments, self destruction");
            doDelete();
        }

        // Setup variables
        String container_name = (String) args[0];
        this.agent_count = ((int) args[1]);
        int init_sick = ((int) args[2]);
        double agent_speed = ((double) args[3]);
        int contamination_radius = ((int) args[4]);
        double contamination_prob = ((double) args[5]);
        int min_contamination_length = ((int) args[6]);
        int max_contamination_length = ((int) args[7]);

        // Generation of agents
        AgentContainer ac = getContainerController();

        // Choosing sick agents
        int[] sick_indices = new int[init_sick];

        Random rand = new Random();
        for (int i = 0; i < init_sick; i++) {
            int potential_ind;
            do {
                potential_ind = rand.nextInt(agent_count);
            } while (arrayContains(sick_indices, potential_ind));
            sick_indices[i] = potential_ind;
        }

        try {
            // Generation of agents
            for (int i=0; i<agent_count; i++){
                String init_status = arrayContains(sick_indices, i)? "sick": "healthy";
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
            wanderer_agents = new AID[agent_count];
            for (int i = 0; i<agent_count; ++i) wanderer_agents[i] = result[i].getName();

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

        String[] replies = new String[agent_count];
        for (int i = 0; i < agent_count; i++) replies[i] = blockingReceive(status_message_template).getContent();

        // Defining sizes of agent variables
        agent_positions = new double[agent_count][2];
        agent_statuses = new String[agent_count];
        
        // Retrieving initial agent positions and statuses
        processReplies(replies);

        if (DEBUG) {
            System.out.println("New locations");
            for (String repl : replies) System.out.println(repl);
        }

        // Setup move go message
        ACLMessage move_go_msg = new ACLMessage(ACLMessage.INFORM);
        move_go_msg.setOntology("GO");
        for (AID agent: wanderer_agents) move_go_msg.addReceiver(agent);


        // Container frame
        JFrame container_frame = new JFrame();
        container_frame.setTitle(container_name);
        container_frame.setLocation(100,100);
        container_frame.setPreferredSize(new Dimension(600,600));
//        container_frame.setBounds(100, 100, (MAX_X*SCALE)+(4*MARGIN), (MAX_Y*SCALE)+(6*MARGIN));

        wandererEnvironmentPanel = new WandererEnvironmentPanel();
        container_frame.add(wandererEnvironmentPanel);

        container_frame.pack();
        container_frame.setVisible(true);
        container_frame.setResizable(false);
        container_frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE); // Exit on close



        // Initialize loop
        addBehaviour(new TickerBehaviour(this, 100) {
            @Override
            protected void onTick() {

                // Tracking time
                long start_time = System.currentTimeMillis();

                // Sending go
                send(move_go_msg);

                // Receive new locations
                String[] replies = new String[agent_count];
                for (int i = 0; i<agent_count; i++) replies[i] = blockingReceive(status_message_template).getContent();

                if (DEBUG) {
                    System.out.println("New locations");
                    for (String repl : replies) System.out.println(repl);
                }

                // Redraw container frame
                processReplies(replies);
                wandererEnvironmentPanel.repaint();

                // Tracking time end
                long duration_s = System.currentTimeMillis() - start_time;
                if (DEBUG) System.out.println("[Controller] Iteration done in (ms): " + duration_s);
            }
        });
    }
    
    private void processReplies(String[] replies){
        for (int i=0; i<agent_count; i++) {
            String[] status = replies[i].split(";");
            agent_positions[i][0] = Double.parseDouble(status[0]);
            agent_positions[i][1] = Double.parseDouble(status[1]);

            agent_statuses[i] = status[2];
        }
    }

    private class WandererEnvironmentPanel extends JPanel {
        private final int DRAW_MARGIN = 10;
        private final int DRAW_SCALE = 5;

        public WandererEnvironmentPanel() {
            super();
            
            setSize(((MAX_X*DRAW_SCALE) + (DRAW_MARGIN*2)), ((MAX_Y*DRAW_SCALE) + (DRAW_MARGIN*2)));
            setBackground(Color.BLACK);
        }

        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            // Frame
            g.setColor(Color.WHITE);
            g.drawRect(DRAW_MARGIN,DRAW_MARGIN,DRAW_SCALE*MAX_X,DRAW_SCALE*MAX_Y);

            // Dots
            for (int i=0; i<agent_count; i++) {
                double[] dot = agent_positions[i];
                AgentStatus status = AgentStatus.fromString(agent_statuses[i]);

                g.setColor(status.color());
                int x = (int) (dot[0] * DRAW_SCALE) + DRAW_MARGIN;
                int y = (int) (dot[1] * DRAW_SCALE) + DRAW_MARGIN;
                g.fillOval(x, y, DRAW_SCALE, DRAW_SCALE);
            }
        }
    }

    private boolean arrayContains(int[] array, int element){
        boolean result = false;
        for (Integer array_element: array) result |= (Objects.equals(array_element, element));
        return result;
    }
}
