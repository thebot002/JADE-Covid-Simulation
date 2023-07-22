package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import javax.swing.*;
import javax.swing.border.Border;

import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.Arrays;

import static agents.Util.*;

public class GUIAgent extends Agent {

    private final int MAX_X = 100;
    private final int MAX_Y = 100;

    private int controller_count = 0;
    private int contamination_radius;

    // Container variables
    private AID[] controller_agents;
    private String[] container_names;
    private JFrame[] frames;
    private JFrame exit_confirmation_window;
    private WandererEnvironmentPanel[] container_panels;
    private JLabel[] container_stats;
    private JFrame info_window;
    private JLabel it_time_label;

    // Messaging
    private final MessageTemplate agent_statuses = getMessageTemplate(ACLMessage.INFORM, "statuses");
    private final MessageTemplate kill_message_template = getMessageTemplate(ACLMessage.INFORM, "kill");
    private final MessageTemplate it_time_template = getMessageTemplate(ACLMessage.INFORM, "it_time");
    private ACLMessage force_kill_message;

    private class WandererEnvironmentPanel extends JPanel {
        private final int DRAW_MARGIN = 10;
        private final int DRAW_SCALE = 5;

        private double[][] agent_positions;
        private AgentStatus[] agent_statuses;
        private int agent_count;

        public WandererEnvironmentPanel() {
            super();
            setBackground(Color.BLACK);

            repaint();
            setSize(((MAX_X*DRAW_SCALE) + (DRAW_MARGIN*2)), ((MAX_Y*DRAW_SCALE) + (DRAW_MARGIN*2)));
        }

        public void setStatuses(int agent_count, double[][] agent_positions, AgentStatus[] agent_statuses) {
            this.agent_count = agent_count;
            this.agent_positions = agent_positions;
            this.agent_statuses = agent_statuses;
        }

        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            // Frame
            g.setColor(Color.WHITE);
            g.drawRect(DRAW_MARGIN,DRAW_MARGIN,DRAW_SCALE*MAX_X,DRAW_SCALE*MAX_Y);

            // Dots
            for (int i = 0; i< agent_count; i++) {
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

    @Override
    protected void setup() {
        Object[] args = getArguments();
        controller_count = (int) args[0];
        contamination_radius = (int) args[1];

        // Retrieve all containers
        do {
            controller_agents = getAgentsAtService(this, "controller-group");
        } while ((controller_agents == null) || (controller_agents.length < controller_count));

        // retrieve container names
        container_names = new String[controller_count];
        for (int i = 0; i < controller_count; i++) {
            container_names[i] = controller_agents[i].getName().split("@")[0].replace("Controller", "Container");
        }

        // Register to service
        registerAgentAtService(this, "GUI");

        ACLMessage manager_intro_message = blockingReceive(getMessageTemplate(ACLMessage.INFORM, "intro"));
        AID manager_agent = manager_intro_message.getSender();
        force_kill_message = createMessage(ACLMessage.INFORM, "korce_kill", manager_agent);

        // Setup GUI
        createExitConfirmationWindow();
        createContainerWindows();
        createInfoWindows();

        // Sending introduction to controllers
        ACLMessage intro_message = createMessage(ACLMessage.INFORM, "intro", controller_agents);
        send(intro_message);

        // initialize loop
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                // Kill message
                ACLMessage kill_message = receive(kill_message_template);
                if (kill_message != null) {
                    doDelete();
                    return;
                }

                // Statuses messages
                ACLMessage message = receive(agent_statuses);
                if ((message != null) && (message.getContent().length() > 0)) {
                    // Get sender's related container panel
                    AID sender = message.getSender();
                    int container_i = Arrays.asList(controller_agents).indexOf(sender);

                    String[] all_statuses = message.getContent().split("-");
                    int agent_count = all_statuses.length;
                    double[][] agent_positions = new double[agent_count][2];
                    AgentStatus[] agent_statuses = new AgentStatus[agent_count];

                    int healthy_count = 0;
                    int sick_count = 0;
                    int recovered_count = 0;
                    try {
                        for (int i = 0; i < agent_count; i++) {
                            String[] status_components = all_statuses[i].split(";");

                            agent_positions[i][0] = Double.parseDouble(status_components[0]);
                            agent_positions[i][1] = Double.parseDouble(status_components[1]);

                            agent_statuses[i] = AgentStatus.fromString(status_components[2]);

                            switch (agent_statuses[i]){
                                case HEALTHY: healthy_count++; break;
                                case SICK: sick_count++; break;
                                case RECOVERED: recovered_count++; break;
                            }
                        }

                        String container_status = container_names[container_i] + ": ";
                        container_status += healthy_count + "/";
                        container_status += sick_count + "/";
                        container_status += recovered_count;
                        container_stats[container_i].setText(container_status);

                        container_panels[container_i].setStatuses(agent_count, agent_positions, agent_statuses);
                        container_panels[container_i].repaint();
                    }
                    catch (Exception e){
                        System.out.println("Unable to parse statuses, skipping gui update");
                    }
                }

                // it time
                ACLMessage it_update_message = receive(it_time_template);
                if ((it_update_message != null) && (it_update_message.getContent().length() > 0)) {
                    String time = it_update_message.getContent();
                    it_time_label.setText("Iteration time (ms): " + time);
                    if (Integer.parseInt(time) > 100) it_time_label.setForeground(Color.RED);
                    else it_time_label.setForeground(Color.BLACK);
                }
            }
        });
    }

    @Override
    public void doDelete() {
        for (JFrame frame: frames) {
            frame.dispose();
        }
        info_window.dispose();

        super.doDelete();
    }

    private void createInfoWindows() {
        info_window = new JFrame("Simulation statistics");
        JPanel content_panel = new JPanel(new GridLayout((2+controller_count),0));
        Border padding = BorderFactory.createEmptyBorder(10, 10, 10, 10);
        content_panel.setBorder(padding);

        // Title
        JLabel title = new JLabel("Simulation statistics", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 20));
        content_panel.add(title);

        // Iteration times
        it_time_label = new JLabel("Iteration time (ms): --");
        content_panel.add(it_time_label);

        // Container stats
        container_stats = new JLabel[controller_count];
        for (int i = 0; i < controller_count; i++) {
            String container_name = container_names[i];
            container_stats[i] = new JLabel(container_name + ": --/--/--");

            content_panel.add(container_stats[i]);
        }

        info_window.add(content_panel);
        info_window.pack();
        info_window.setVisible(true);
        info_window.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        info_window.addWindowListener(new WindowListener() {

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

    private void createContainerWindows() {
        container_panels = new WandererEnvironmentPanel[controller_count];
        frames = new JFrame[controller_count];
        for (int i = 0; i < controller_count; i++) {
            // Container frame
            JFrame container_frame = new JFrame();
            container_frame.setTitle(container_names[i]);
            int x_pos = (550 * (i%3)) + (50 * (i/3));
            int y_pos = 200 + (150 * (i/3));
            container_frame.setLocation(x_pos,y_pos);

            // Adding to the list of frames
            frames[i] = container_frame;

            // Content pane
            JPanel contentPane = new JPanel();
            Border padding = BorderFactory.createEmptyBorder(10, 10, 10, 10);
            contentPane.setBorder(padding);
            container_frame.add(contentPane);
            contentPane.setLayout(new BorderLayout(20,20));

            JLabel title = new JLabel(container_names[i], SwingConstants.CENTER);
            title.setFont(new Font("Arial", Font.BOLD, 30));
            contentPane.add(title, BorderLayout.NORTH);

            container_panels[i] = new WandererEnvironmentPanel();
            contentPane.add(container_panels[i], BorderLayout.CENTER);

            // Finalization of Frame
            container_frame.setVisible(true);

            Insets insets = container_frame.getInsets();
            int addedWidth = insets.left + insets.right;
            int addedHeight = insets.top + insets.bottom;

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
}
