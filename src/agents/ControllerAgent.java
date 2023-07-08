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
import jade.wrapper.StaleProxyException;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.Objects;
import java.util.Random;

import static java.lang.Thread.sleep;

public class ControllerAgent extends Agent {

    // Agents variables
    private AID[] wanderer_agents;
    private double[][] agent_positions;
    private AgentStatus[] agent_statuses;
    private int sick_agent_count;
    private int contamination_radius;

    // DEBUG
    private final boolean DEBUG = false;

    private int agent_count;

    private final int MAX_X = 100;
    private final int MAX_Y = 100;

    private ACLMessage move_go_msg;
    private TickerBehaviour loop_behavior;
    private final MessageTemplate status_message_template = MessageTemplate.and(
            MessageTemplate.MatchPerformative(ACLMessage.INFORM),
            MessageTemplate.MatchOntology("status"));

    // Panels
    private JFrame container_frame;
    private WandererEnvironmentPanel wandererEnvironmentPanel;
    private JFrame exit_confirmation_window;
    private JLabel exit_confirmation_info;

    private boolean is_running = true;
    private boolean is_done = false;


    @Override
    protected void setup() {

        // Retrieving initial status and parameters
        Object[] args = getArguments();
        if (args.length < 8) {
            System.out.println("Not enough arguments, self destruction");
            doDelete();
        }

        // Setup variables
        int container_id = ((int) args[0]);
        this.agent_count = ((int) args[1]);
        int init_sick = ((int) args[2]);
        double agent_speed = ((double) args[3]);
        contamination_radius = ((int) args[4]);
        double contamination_prob = ((double) args[5]);
        int min_contamination_length = ((int) args[6]);
        int max_contamination_length = ((int) args[7]);

        // Registering to the Wanderer group service
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            ServiceDescription sd = new ServiceDescription();
            sd.setType("controller-group");
            sd.setName("Controllers");
            dfd.addServices(sd);
            DFService.register(this, dfd);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }

        // Generation of agents
        AgentContainer ac = getContainerController();

        // Choosing sick agents
        sick_agent_count = init_sick;
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
                AgentController wandererAgent = ac.createNewAgent("Wanderer-" + container_id + "-" + i, "agents.WanderingAgent", new Object[]{
                        container_id,
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
        sd.setType("wanderer-group-" + container_id);
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
        String[] replies = new String[agent_count];
        for (int i = 0; i < agent_count; i++) replies[i] = blockingReceive(status_message_template).getContent();

        // Defining sizes of agent variables
        agent_positions = new double[agent_count][2];
        agent_statuses = new AgentStatus[agent_count];
        
        // Retrieving initial agent positions and statuses
        processReplies(replies);

        if (DEBUG) {
            System.out.println("New locations");
            for (String repl : replies) System.out.println(repl);
        }

        // Setup move go message
        move_go_msg = new ACLMessage(ACLMessage.INFORM);
        move_go_msg.setOntology("GO");
        for (AID agent: wanderer_agents) move_go_msg.addReceiver(agent);


        // Creation of exit confirmation window
        exit_confirmation_window = new JFrame();
        exit_confirmation_window.setTitle("Simulation still running");
        exit_confirmation_window.setLayout(new FlowLayout(FlowLayout.CENTER,10,10));

        exit_confirmation_info = new JLabel("The simulation is still running, stay or stop simulation:");
        exit_confirmation_window.add(exit_confirmation_info);

        JButton stay_button = new JButton("Stay");
        stay_button.addActionListener(e -> {
            exit_confirmation_window.setVisible(false);
            is_running = true;
        });
        exit_confirmation_window.add(stay_button);

        JButton exit_button = new JButton("Exit simulation");
        exit_button.addActionListener(e -> {
            exit_confirmation_window.dispose();
            doDelete();
        });
        exit_confirmation_window.add(exit_button);

        // Finalization of frame
        exit_confirmation_window.setBounds(200,200,600, 80);
        exit_confirmation_window.setResizable(false);


        // Container frame
        container_frame = new JFrame();
        container_frame.setTitle("Container-"+container_id);
        container_frame.setLocation(100,100);



//        container_frame.setSize(600,600);
//        container_frame.setPreferredSize(new Dimension(560,590));
//        container_frame.setBounds(100, 100, (MAX_X*SCALE)+(4*MARGIN), (MAX_Y*SCALE)+(6*MARGIN));

        // Content pane
        JPanel contentPane = new JPanel();
        Border padding = BorderFactory.createEmptyBorder(10, 10, 10, 10);
        contentPane.setBorder(padding);
        container_frame.add(contentPane);
        contentPane.setLayout(new BorderLayout(20,20));

        JLabel title = new JLabel("Container-" + container_id + " - simulation", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 30));
        contentPane.add(title, BorderLayout.NORTH);

        wandererEnvironmentPanel = new WandererEnvironmentPanel();
        contentPane.add(wandererEnvironmentPanel, BorderLayout.CENTER);


        // Finalization of Frame
        container_frame.setVisible(true);

        Insets insets = container_frame.getInsets();
        int addedWidth = insets.left + insets.right;
        int addedHeight = insets.top + insets.bottom;

        System.out.println(title.getSize());

        container_frame.setSize(540+addedWidth, 596+addedHeight);

        container_frame.setResizable(false);
        container_frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        container_frame.addWindowListener(new WindowListener() {

            @Override
            public void windowOpened(WindowEvent e) {}

            @Override
            public void windowClosing(WindowEvent e) {
                if(is_done) {
                    doDelete();
                    return;
                }
                is_running = false;
                exit_confirmation_window.setVisible(true);
            }

            @Override
            public void windowClosed(WindowEvent e) {}

            @Override
            public void windowIconified(WindowEvent e) {}

            @Override
            public void windowDeiconified(WindowEvent e) {}

            @Override
            public void windowActivated(WindowEvent e) {}

            @Override
            public void windowDeactivated(WindowEvent e) {}
        });

        // Initialize loop
        loop_behavior = new TickerBehaviour(this, 100) {
            @Override
            protected void onTick() {

                // Return if it is not running anymore
                if (! is_running) return;

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

                // Process if simulation is done
                if (sick_agent_count == 0){
                    is_done = true;
                    exit_confirmation_window.setTitle("Simulation done");
                    exit_confirmation_info.setText("The simulation is done, exit or stay to observe results:");

                    // Creation of exiting window
                    exit_confirmation_window.setVisible(true);

                    // Remove the ticking loop
                    myAgent.removeBehaviour(loop_behavior);
                }

                // Tracking time end
                long duration_s = System.currentTimeMillis() - start_time;
                if (DEBUG) System.out.println("[Controller] Iteration done in (ms): " + duration_s);
            }
        };

        addBehaviour(loop_behavior);
    }
    
    private void processReplies(String[] replies){
        sick_agent_count = 0;
        for (int i=0; i<agent_count; i++) {
            String[] status = replies[i].split(";");
            agent_positions[i][0] = Double.parseDouble(status[0]);
            agent_positions[i][1] = Double.parseDouble(status[1]);

            agent_statuses[i] = AgentStatus.fromString(status[2]);
            if (agent_statuses[i] == AgentStatus.SICK) sick_agent_count++;
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
                AgentStatus status = agent_statuses[i];

                g.setColor(status.color());
                int x = (int) (dot[0] * DRAW_SCALE) + DRAW_MARGIN;
                int y = (int) (dot[1] * DRAW_SCALE) + DRAW_MARGIN;
                g.fillOval(x-(DRAW_SCALE/2), y-(DRAW_SCALE/2), DRAW_SCALE, DRAW_SCALE);

                if (status == AgentStatus.SICK){
                    g.drawOval(x-(DRAW_SCALE*contamination_radius),y-(DRAW_SCALE*contamination_radius),DRAW_SCALE*contamination_radius*2, DRAW_SCALE*contamination_radius*2);
                }
            }
        }
    }

    private boolean arrayContains(int[] array, int element){
        boolean result = false;
        for (Integer array_element: array) result |= (Objects.equals(array_element, element));
        return result;
    }

    @Override
    public void doDelete() {
        // Sending auto-destruct signal to wanderers
        move_go_msg.setContent("delete");
        send(move_go_msg);

        int confirmations = 0;
        for (int i = 0; i < agent_count; i++) {
            String response = blockingReceive(status_message_template).getContent();
            if (Objects.equals(response, "deleted")) confirmations++;
        }

        if (confirmations != agent_count) System.out.println("Not all agents have been deleted");
        agent_count = 0;

        // Deleting container window
        container_frame.dispose();

        super.doDelete();
    }
}
