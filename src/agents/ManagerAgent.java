package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

import static java.lang.Thread.sleep;

public class ManagerAgent extends Agent {

    // default variables
    private int container_count = 2;
    private int agent_count = 100;
    private int init_sick = 10;
    private double agent_speed = 1.0;
    private int contamination_radius = 5;
    private double contamination_prob = 0.8;
    private int min_contamination_length = 20;
    private int max_contamination_length = 30;

    // containers
    AgentContainer[] spawned_containers;
    AID[] controller_agents;

    //debug
    private final boolean DEBUG = true;

    @Override
    protected void setup() {
        JFrame frame = new JFrame();
        frame.setTitle("COVID Sim - Menu");
        frame.setLocation(100,100);
        frame.setSize(new Dimension(400,340));

        // Creation of content pane inside
        JPanel contentFrame = new JPanel();
        Border padding = BorderFactory.createEmptyBorder(10, 10, 10, 10);
        contentFrame.setBorder(padding);
        frame.add(contentFrame);

        // Options pannel
        JPanel options = new JPanel();

        // Agent count
        options.add(new Label("Container count:"));
        JTextField containerCountInput = new JTextField(Integer.toString(container_count));
        options.add(containerCountInput);

        // Agent count
        options.add(new Label("Agent count:"));
        JTextField agentCountInput = new JTextField(Integer.toString(agent_count));
        options.add(agentCountInput);

        // Initial sick count
        options.add(new Label("Start Sick count:"));
        JTextField sickCountInput = new JTextField(Integer.toString(init_sick));
        options.add(sickCountInput);

        // Agent speed
        options.add(new Label("Agent speed:"));
        JTextField agentSpeedInput = new JTextField(Double.toString((agent_speed)));
        options.add(agentSpeedInput);

        // Contamination radius
        options.add(new Label("Contamination radius:"));
        JTextField contaminationRadiusInput = new JTextField(Integer.toString(contamination_radius));
        options.add(contaminationRadiusInput);

        // Contamination prob
        options.add(new Label("Contamination probability:"));
        JTextField contaminationProbInput = new JTextField(Double.toString(contamination_prob));
        options.add(contaminationProbInput);

        // Min contamination length
        options.add(new Label("Min Contamination Length:"));
        JTextField minContaminationLengthInput = new JTextField(Integer.toString(min_contamination_length));
        options.add(minContaminationLengthInput);

        // Max contamination length
        options.add(new Label("Max Contamination Length:"));
        JTextField maxContaminationLengthInput = new JTextField(Integer.toString(max_contamination_length));
        options.add(maxContaminationLengthInput);


        // Grid setup
        options.setLayout(new GridLayout(8,2));

        // Start button
        JButton startButton = new JButton("Start simulation");
        startButton.addActionListener(e -> {
            container_count = Integer.parseInt(containerCountInput.getText());
            agent_count = Integer.parseInt(agentCountInput.getText());
            init_sick = Integer.parseInt(sickCountInput.getText());
            agent_speed = Double.parseDouble(agentSpeedInput.getText());
            contamination_radius = Integer.parseInt(contaminationRadiusInput.getText());
            contamination_prob = Double.parseDouble(contaminationProbInput.getText());
            min_contamination_length = Integer.parseInt(minContaminationLengthInput.getText());
            max_contamination_length = Integer.parseInt(maxContaminationLengthInput.getText());

            if (init_sick >= agent_count){
                System.out.println("init sick amount can't be larger than agent count");
                return;
            }

            if (max_contamination_length < min_contamination_length){
                System.out.println("max contamination length can't be larger than min contamination length");
                return;
            }

            createSubContainer();

            try {
                sleep(1000);
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }

            // Find the list of wandering agents
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("controller-group");
            template.addServices(sd);
            try {
                DFAgentDescription[] result = DFService.search(this, template);
                controller_agents = new AID[container_count];
                for (int i = 0; i<container_count; ++i) controller_agents[i] = result[i].getName();

                if (DEBUG) {
                    System.out.println("Found the following controller agents:");
                    for (AID agent : controller_agents) System.out.println("\t" + agent.getName());
                }
            }
            catch (FIPAException fe) {
                fe.printStackTrace();
            }

        });

        // Frame components in a column layout
        contentFrame.setLayout(new BorderLayout(20,20));
        JLabel title = new JLabel("JADE COVID Sim", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 30));
        contentFrame.add(title, BorderLayout.NORTH);
        contentFrame.add(options, BorderLayout.CENTER);
        contentFrame.add(startButton, BorderLayout.SOUTH);

        // Finalization of frame
        frame.setVisible(true);
        frame.setResizable(false);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    }

    private void createSubContainer(){

        spawned_containers = new AgentContainer[container_count];

        for (int i = 0; i < container_count; i++) {

            // Sub container
            int container_id = i+1;
            String container_name = "Container-" + container_id;


            try {
                Runtime runtime = Runtime.instance();
                ProfileImpl pc = new ProfileImpl(false);
                pc.setParameter(ProfileImpl.CONTAINER_NAME, container_name);
                pc.setParameter(ProfileImpl.MAIN_HOST, "localhost");
                AgentContainer ac = runtime.createAgentContainer(pc);
                spawned_containers[i] = ac;
                ac.start();

                // Creating the controller
                Object[] controller_arguments = new Object[]{
                        container_id,
                        agent_count,
                        init_sick,
                        agent_speed,
                        contamination_radius,
                        contamination_prob,
                        min_contamination_length,
                        max_contamination_length
                };
                AgentController controllerAgent = ac.createNewAgent("Controller-"+(i+1), "agents.ControllerAgent", controller_arguments);
                controllerAgent.start();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
