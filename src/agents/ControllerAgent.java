package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.core.behaviours.CyclicBehaviour;
import jade.util.leap.Iterator;
import jade.wrapper.AgentContainer;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.wrapper.AgentController;
import jade.wrapper.ControllerException;
import jade.wrapper.StaleProxyException;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.Objects;
import java.util.Random;

import static java.lang.Thread.sleep;

import static agents.Util.*;

public class ControllerAgent extends Agent {

    // DEBUG
    private final boolean DEBUG = false;

    private String container_name;
    private boolean gui_enabled;
    private int agent_count;
    private int sick_agent_count;
    private int init_sick;
    private double agent_speed;
    private int contamination_radius;
    private double contamination_prob;
    private int min_contamination_length;
    private int max_contamination_length;
    private double travel_chance;
    private int average_travel_duration;

    // Agents variables
    private AID[] wanderer_agents;
    private double[][] agent_positions;
    private AgentStatus[] agent_statuses;
    private boolean sent_done = false;
    private boolean to_refresh_receivers = false;

    // containers
    private AgentContainer ac;
    private int controller_count;

    private final int MAX_X = 100;
    private final int MAX_Y = 100;

    private final MessageTemplate intro_message_template = getMessageTemplate(ACLMessage.INFORM, "intro");
    private final MessageTemplate manager_go_message_template = getMessageTemplate(ACLMessage.INFORM, "GO");
    private final MessageTemplate status_message_template = getMessageTemplate(ACLMessage.INFORM, "status");
    private final MessageTemplate kill_message_template = getMessageTemplate(ACLMessage.INFORM, "kill");
    private final MessageTemplate travel_done_message_template = getMessageTemplate(ACLMessage.INFORM, "travel_done");
    private final MessageTemplate controller_travel_done_message_template = getMessageTemplate(ACLMessage.INFORM, "controller_travel_done");
    private final MessageTemplate travel_request_message_template = getMessageTemplate(ACLMessage.REQUEST, "travel");
    private ACLMessage move_go_msg;
    private ACLMessage travel_msg;
    private ACLMessage done_message;
    private ACLMessage controller_travel_done_message;
    private ACLMessage kill_msg;
    private ACLMessage force_kill_message;

    // Panels
    private JFrame container_frame;
    private WandererEnvironmentPanel wandererEnvironmentPanel;
    private JFrame exit_confirmation_window;

    @Override
    protected void setup() {

        // Retrieving initial status and parameters
        Object[] args = getArguments();
        if (args.length < 11) {
            System.out.println("Not enough arguments, self destruction");
            doDelete();
        }

        // Setup variables
        container_name = ((String) args[0]);
        gui_enabled = ((boolean) args[1]);
        agent_count = ((int) args[2]);
        init_sick = ((int) args[3]);
        agent_speed = ((double) args[4]);
        contamination_radius = ((int) args[5]);
        contamination_prob = ((double) args[6]);
        min_contamination_length = ((int) args[7]);
        max_contamination_length = ((int) args[8]);
        travel_chance = ((double) args[9]);
        average_travel_duration = ((int) args[10]);

        // Setup agent status and position arrays
        agent_positions = new double[agent_count][2];
        agent_statuses = new AgentStatus[agent_count];

        // Registering to the Controller group service
        registerAgentAtService(this, "controller-group");

        // Waiting for introduction from manager
        ACLMessage manager_intro_message = blockingReceive(intro_message_template);
        AID manager_agent = manager_intro_message.getSender();

        // Retrieving other controllers
        AID[] other_controllers = getAgentsAtService(this, "controller-group", getAID());
        controller_count = other_controllers.length + 1;
        controller_travel_done_message = createMessage(ACLMessage.INFORM, "controller_travel_done", other_controllers);

        // Create done message to manager
        done_message = createMessage(ACLMessage.INFORM, "done", manager_agent);
        force_kill_message = createMessage(ACLMessage.INFORM, "korce_kill", manager_agent);

        // GENERATION OF WANDERER AGENTS
        generateAgents();

        // Inform agents that controller is ready
        ACLMessage ready_message = createMessage(ACLMessage.INFORM, "ready", wanderer_agents);
        send(ready_message);

        // Receive initial locations back
        String[] replies = new String[agent_count];
        for (int i = 0; i < agent_count; i++) replies[i] = blockingReceive(status_message_template).getContent();
        
        // Retrieving initial agent positions and statuses
        processReplies(replies);

        // Setup go and travel message
        move_go_msg = createMessage(ACLMessage.INFORM, "GO", wanderer_agents);
        travel_msg = createMessage(ACLMessage.INFORM, "travel", wanderer_agents);
        kill_msg = createMessage(ACLMessage.INFORM, "kill", wanderer_agents);

        // Create graphical elements
        if (gui_enabled) {
            createExitConfirmationWindow();
            createContainerWindow();
        }

        // Send ready and Wait for go from manager
        ACLMessage manager_ready_message = createMessage(ACLMessage.INFORM, "ready", manager_agent);
        manager_ready_message.setContent(container_name);
        send(manager_ready_message);

        // Initialize loop
        CyclicBehaviour loop_behavior = new CyclicBehaviour() {
            @Override
            public void action() {
                // If kill message is received, perform doDelete()
                ACLMessage message = receive(kill_message_template);
                if (message != null) doDelete();

                // Receive go from manager
                ACLMessage manager_go = receive(manager_go_message_template);
                if (manager_go == null) return;

                // Sending travel signal to all agents
                send(travel_msg);

                // Travel loop
                int agents_done_travel_step = 0;
                int controllers_done_with_travel = 0;
                boolean controller_sent_done = false;
                int delta_agent_count = 0;

                while (controllers_done_with_travel < controller_count) {
                    // Receive travel request
                    ACLMessage travel_request_message = receive(travel_request_message_template);

                    if (travel_request_message != null) {
                        String action = travel_request_message.getContent();
                        AID traveller = travel_request_message.getSender();

                        ACLMessage travel_agreement_message = createMessage(ACLMessage.AGREE, "travel", traveller);

                        if (Objects.equals(action, "leave")) {
                            // Remove agent from the list of receivers of the go message of this controller
                            move_go_msg.removeReceiver(traveller);
                            travel_msg.removeReceiver(traveller);
                            delta_agent_count--;
                        }
                        else if (Objects.equals(action, "arrive")) {
                            travel_agreement_message.setContent(container_name);
                            move_go_msg.addReceiver(traveller);
                            travel_msg.addReceiver(traveller);
                            delta_agent_count++;

                        }
                        to_refresh_receivers = true;

                        send(travel_agreement_message);
                        continue;
                    }

                    // check if some other is done with their travel step
                    if (receive(controller_travel_done_message_template) != null) controllers_done_with_travel++;

                    // Check if agent is done with travelling
                    ACLMessage agent_done_travel_message = receive(travel_done_message_template);
                    if (agent_done_travel_message != null) agents_done_travel_step++;

                    // Check if all agents of this controller are done, if so, send message to other containers
                    if (!controller_sent_done && (agents_done_travel_step == agent_count)) {
                        controllers_done_with_travel++;
                        send(controller_travel_done_message);
                        controller_sent_done = true;
                    }
                }

                // If some travels happened, we change the amount of agents
                if (delta_agent_count != 0){
                    agent_count += delta_agent_count;

                    agent_positions = new double[agent_count][2];
                    agent_statuses = new AgentStatus[agent_count];
                }

                // If the wanderers have to refresh their neighbourhood list a refresh content is sent out
                if (to_refresh_receivers) {
                    move_go_msg.setContent("refresh");
                    to_refresh_receivers = false;
                }
                else move_go_msg.setContent(null);

                // Sending go
                send(move_go_msg);

                // Receive new locations and statuses
                String[] replies = new String[agent_count];
                for (int i = 0; i < agent_count; i++) {
                    replies[i] = blockingReceive(status_message_template).getContent();
                }
                processReplies(replies);

                // Redraw container frame
                if (gui_enabled) wandererEnvironmentPanel.repaint();

                // Send done message to manager with sick agents count
                done_message.setContent(String.valueOf(sick_agent_count));
                send(done_message);

                if (DEBUG && !sent_done) System.out.println("[" + container_name + "] " + sick_agent_count);
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
        send(kill_msg);

        int confirmations = 0;
        for (int i = 0; i < agent_count; i++) {
            String response = blockingReceive(status_message_template).getContent();
            if (Objects.equals(response, "deleted")) confirmations++;
        }

        if (confirmations != agent_count) System.out.println("Not all agents have been deleted");
        agent_count = 0;

        // Deleting windows
        if (gui_enabled) {
            container_frame.dispose();
            exit_confirmation_window.dispose();
        }

        // Kill container
        try {
            ac.kill();
        } catch (StaleProxyException e) {
            throw new RuntimeException(e);
        }

        System.out.println("Finishing deleting " + container_name);
        super.doDelete();
    }

    private void generateAgents(){
        // Generation of agents
        Runtime runtime = Runtime.instance();
        ProfileImpl pc = new ProfileImpl(false);
        pc.setParameter(ProfileImpl.CONTAINER_NAME, container_name);
//        pc.setParameter(ProfileImpl.MAIN_HOST, host_ip);

        ac = runtime.createAgentContainer(pc);
        try {ac.start();} catch (ControllerException e) {throw new RuntimeException(e);}

        String host_ip = ac.getName().split(":")[0];

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
                AgentController wandererAgent = ac.createNewAgent(container_name + "-Wanderer-" + i, "agents.WanderingAgent", new Object[]{
                        host_ip,
                        container_name,
                        init_status,
                        agent_speed,
                        contamination_radius,
                        contamination_prob,
                        min_contamination_length,
                        max_contamination_length,
                        travel_chance,
                        average_travel_duration
                });
                wandererAgent.start();
            }

            // Waiting for the wandering agents to settle before introducing the controller
            sleep(1000);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Find the list of generated wandering agents
        wanderer_agents = getAgentsAtService(this, container_name + "-wanderer-group");
        if (DEBUG) {
            System.out.println("Found the following wandering agents:");
            for (AID agent : wanderer_agents) System.out.println("\t" + agent.getName());
        }
    }

    private void createExitConfirmationWindow(){
        // Creation of exit confirmation window
        exit_confirmation_window = new JFrame();
        exit_confirmation_window.setTitle("Simulation still running");
        exit_confirmation_window.setLayout(new FlowLayout(FlowLayout.CENTER,10,10));

        JLabel exit_confirmation_info = new JLabel("The simulation is still running, stay or stop simulation:");
        exit_confirmation_window.add(exit_confirmation_info);

        JButton stay_button = new JButton("Stay");
        stay_button.addActionListener(e -> {
            exit_confirmation_window.setVisible(false);
        });
        exit_confirmation_window.add(stay_button);

        JButton exit_button = new JButton("Exit simulation");
        exit_button.addActionListener(e -> {
            exit_confirmation_window.setVisible(false);
            send(force_kill_message);
        });
        exit_confirmation_window.add(exit_button);

        // Finalization of frame
        exit_confirmation_window.setBounds(200,200,600, 80);
        exit_confirmation_window.setResizable(false);
    }

    private void createContainerWindow() {
        // Container frame
        container_frame = new JFrame();
        container_frame.setTitle(container_name);
        container_frame.setLocation(100,100);

        // TODO add statisitcs pane

//        container_frame.setSize(600,600);
//        container_frame.setPreferredSize(new Dimension(560,590));
//        container_frame.setBounds(100, 100, (MAX_X*SCALE)+(4*MARGIN), (MAX_Y*SCALE)+(6*MARGIN));

        // Content pane
        JPanel contentPane = new JPanel();
        Border padding = BorderFactory.createEmptyBorder(10, 10, 10, 10);
        contentPane.setBorder(padding);
        container_frame.add(contentPane);
        contentPane.setLayout(new BorderLayout(20,20));

        JLabel title = new JLabel(container_name + " - simulation", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 30));
        contentPane.add(title, BorderLayout.NORTH);

        wandererEnvironmentPanel = new WandererEnvironmentPanel();
        contentPane.add(wandererEnvironmentPanel, BorderLayout.CENTER);


        // Finalization of Frame
        container_frame.setVisible(true);

        Insets insets = container_frame.getInsets();
        int addedWidth = insets.left + insets.right;
        int addedHeight = insets.top + insets.bottom;

//        System.out.println(title.getSize());

        container_frame.setSize(540+addedWidth, 596+addedHeight);

        container_frame.setResizable(false);
        container_frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        container_frame.addWindowListener(new WindowListener() {

            @Override
            public void windowOpened(WindowEvent e) {}

            @Override
            public void windowClosing(WindowEvent e) {
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
    }
}
