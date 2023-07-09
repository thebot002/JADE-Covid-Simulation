package agents;

import jade.core.*;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import static java.lang.Thread.sleep;

public class ManagerAgent extends Agent {

    // This agent
    Agent this_agent;

    // default variables
    private int container_count = 2;
    private int remote_container_count = 0;
    private int agent_count = 100;
    private int init_sick = 10;
    private double agent_speed = 1.0;
    private int contamination_radius = 5;
    private double contamination_prob = 0.8;
    private int min_contamination_length = 20;
    private int max_contamination_length = 30;

    // variable fields
    private JTextField
            containerCountInput,
            remoteContainerCountInput,
            agentCountInput,
            sickCountInput,
            agentSpeedInput,
            contaminationRadiusInput,
            contaminationProbInput,
            minContaminationLengthInput,
            maxContaminationLengthInput;

    // containers
    AgentContainer[] spawned_containers;
    AID[] controller_agents;

    //debug
    private final boolean DEBUG = true;

    @Override
    protected void setup() {
        this_agent = this;
        createMenuWindow();
    }

    private void createMenuWindow(){
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

        // Container count
        options.add(new Label("Container count:"));
        containerCountInput = new JTextField(Integer.toString(container_count));
        options.add(containerCountInput);

        // Remote container count
        options.add(new Label("Remote container count:"));
        remoteContainerCountInput = new JTextField(Integer.toString(remote_container_count));
        options.add(remoteContainerCountInput);

        // Agent count
        options.add(new Label("Agent count:"));
        agentCountInput = new JTextField(Integer.toString(agent_count));
        options.add(agentCountInput);

        // Initial sick count
        options.add(new Label("Start Sick count:"));
        sickCountInput = new JTextField(Integer.toString(init_sick));
        options.add(sickCountInput);

        // Agent speed
        options.add(new Label("Agent speed:"));
        agentSpeedInput = new JTextField(Double.toString((agent_speed)));
        options.add(agentSpeedInput);

        // Contamination radius
        options.add(new Label("Contamination radius:"));
        contaminationRadiusInput = new JTextField(Integer.toString(contamination_radius));
        options.add(contaminationRadiusInput);

        // Contamination prob
        options.add(new Label("Contamination probability:"));
        contaminationProbInput = new JTextField(Double.toString(contamination_prob));
        options.add(contaminationProbInput);

        // Min contamination length
        options.add(new Label("Min Contamination Length:"));
        minContaminationLengthInput = new JTextField(Integer.toString(min_contamination_length));
        options.add(minContaminationLengthInput);

        // Max contamination length
        options.add(new Label("Max Contamination Length:"));
        maxContaminationLengthInput = new JTextField(Integer.toString(max_contamination_length));
        options.add(maxContaminationLengthInput);

        // Grid setup
        options.setLayout(new GridLayout(9,2));

        // Start button
        JButton startButton = new JButton("Start simulation");
        startButton.addActionListener(new StartAction());

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

    private class StartAction implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            container_count = Integer.parseInt(containerCountInput.getText());
            remote_container_count = Integer.parseInt(remoteContainerCountInput.getText());
            agent_count = Integer.parseInt(agentCountInput.getText());
            init_sick = Integer.parseInt(sickCountInput.getText());
            agent_speed = Double.parseDouble(agentSpeedInput.getText());
            contamination_radius = Integer.parseInt(contaminationRadiusInput.getText());
            contamination_prob = Double.parseDouble(contaminationProbInput.getText());
            min_contamination_length = Integer.parseInt(minContaminationLengthInput.getText());
            max_contamination_length = Integer.parseInt(maxContaminationLengthInput.getText());

            if(!validateInputs()) return;

            createSubContainer();

            try {sleep(1000);} catch (InterruptedException ex) { throw new RuntimeException(ex); }

            // Find the list of controller agents
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("controller-group");
            template.addServices(sd);
            try {
                DFAgentDescription[] result = DFService.search(this_agent, template);
                controller_agents = new AID[container_count];
                for (int i = 0; i < container_count; i++) controller_agents[i] = result[i].getName();

                if (DEBUG) {
                    System.out.println("Found the following controller agents:");
                    for (AID agent : controller_agents) System.out.println("\t" + agent.getName());
                }
            }
            catch (FIPAException fe) {
                fe.printStackTrace();
            }
        }
    }

    private boolean validateInputs(){
        if (container_count <= 0
                || remote_container_count < 0
                || agent_count <= 0
                || init_sick <= 0
                || agent_speed <= 0
                || contamination_radius <= 0
                || min_contamination_length < 0
                || max_contamination_length <= 0){
            System.out.println("None of the inputs can be negative");
            return false;
        }

        if (remote_container_count > container_count){
            System.out.println("Can't have more remote containers than total ones");
            return false;
        }

        if (init_sick >= agent_count){
            System.out.println("init sick amount can't be larger than agent count");
            return false;
        }

        if (max_contamination_length < min_contamination_length){
            System.out.println("max contamination length can't be larger than min contamination length");
            return false;
        }

        return true;
    }

    private void createSubContainer(){

        spawned_containers = new AgentContainer[container_count];

        int container_id;
        String container_name;

        for (int i = 0; i < container_count; i++) {

            if (i < agent_count-remote_container_count) {
                // Local container
                container_id = i + 1;
                container_name = "Container-" + container_id;


                try {
                    AgentContainer mc = getContainerController();

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
                    AgentController controllerAgent = mc.createNewAgent("Controller-" + (i + 1), "agents.ControllerAgent", controller_arguments);
                    controllerAgent.start();

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            else {
                // Remote containers
                container_id = i + 1 - remote_container_count;
                container_name = "Container-" + container_id;

                // Creation of remote container

//        String user = "jade_creator";
//        String password = "Potato";
//        String host = "192.168.68.124";
//        int port = 22;
//
//        try {
//            JSch jsch = new JSch();
//            Session session = jsch.getSession(user, host, port);
//            session.setPassword(password);
//            session.setConfig("StrictHostKeyChecking", "no");
//            System.out.println("Establishing Connection...");
//            session.connect();
//            System.out.println("Connection established.");
//
//            Channel channel=session.openChannel("exec");
//            String container_name = "rpi-container";
//            String command = "cd jade_project; java -cp jade.jar: LaunchContainer " + container_name;
//            ((ChannelExec)channel).setCommand(command);
//
//            // X Forwarding
//            // channel.setXForwarding(true);
//
//            //channel.setInputStream(System.in);
//            channel.setInputStream(null);
//
//            //channel.setOutputStream(System.out);
//
//            //FileOutputStream fos=new FileOutputStream("/tmp/stderr");
//            //((ChannelExec)channel).setErrStream(fos);
//            ((ChannelExec)channel).setErrStream(System.err);
//
//            InputStream in=channel.getInputStream();
//
//            channel.connect();
//
//            byte[] tmp=new byte[1024];
//            while(true){
//                while(in.available()>0){
//                    int i=in.read(tmp, 0, 1024);
//                    if(i<0)break;
//                    System.out.print(new String(tmp, 0, i));
//                }
//                if(channel.isClosed()){
//                    if(in.available()>0) continue;
//                    System.out.println("exit-status: "+channel.getExitStatus());
//                    break;
//                }
//                try{Thread.sleep(1000);}catch(Exception ee){}
//            }
//
//        } catch (JSchException e) {
//            e.printStackTrace();
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
            }
        }
    }
}
