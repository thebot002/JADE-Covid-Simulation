package agents;

import com.jcraft.jsch.*;

import jade.core.*;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.util.leap.ArrayList;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.Objects;

import static java.lang.Thread.sleep;

import static agents.Util.*;

public class ManagerAgent extends Agent {

    // This agent
    private Agent this_agent;

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
    private double travel_chance = 0.0001;
    private int average_travel_duration = 5;

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
            maxContaminationLengthInput,
            travelChanceInput,
            travelDurationInput;

    // Controller agents
    private AID[] controller_agents;
    private AID gui_agent;

    // Graphics
    private JFrame menuFrame;
    private JFrame exit_confirmation_window;
    private JLabel exit_confirmation_info;

    // Messaging
    private final MessageTemplate done_message_template = getMessageTemplate(ACLMessage.INFORM,"done" );
    private final MessageTemplate force_kill_message_template = getMessageTemplate(ACLMessage.INFORM, "korce_kill");
    private ACLMessage go_message;

    // Behaviors
    private final managerLoop simulation_loop_behavior = new managerLoop();

    // Remote variables
    private boolean remote_launched = false;
    private AID remote_manager;
    private InputStream remote_input_stream;

    //debug
    private final boolean DEBUG = true;

    @Override
    protected void setup() {
        this_agent = this;
        createMenuWindow();
        createExitConfirmationWindow();
    }

    private void createMenuWindow(){
        menuFrame = new JFrame();
        menuFrame.setTitle("COVID Sim - Menu");
        menuFrame.setSize(new Dimension(400,420));
        menuFrame.setLocationRelativeTo(null);

        // Creation of content pane inside
        JPanel contentFrame = new JPanel();
        Border padding = BorderFactory.createEmptyBorder(10, 10, 10, 10);
        contentFrame.setBorder(padding);
        menuFrame.add(contentFrame);

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

        // travel chance
        options.add(new Label("Agent travel chance:"));
        travelChanceInput = new JTextField(Double.toString(travel_chance));
        options.add(travelChanceInput);

        // travel duration
        options.add(new Label("Agent average travel duration:"));
        travelDurationInput = new JTextField(Integer.toString(average_travel_duration));
        options.add(travelDurationInput);

        // Grid setup
        options.setLayout(new GridLayout(11,2));

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
        menuFrame.setVisible(true);
        menuFrame.setResizable(false);
        menuFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
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
            travel_chance = Double.parseDouble(travelChanceInput.getText());
            average_travel_duration = Integer.parseInt(travelDurationInput.getText());

            if(!validateInputs()) return;

            createSubContainers();

            // Find the list of controller agents
            AID[] agents;
            do {
                agents = getAgentsAtService(this_agent, "controller-group");
            } while((agents == null) || (agents.length < container_count));
            controller_agents = agents;
            if (DEBUG) {
                System.out.println("Found the following controller agents:");
                for (AID agent : controller_agents) System.out.println("\t" + agent.getName());
            }

            // create gui agent
            try {
                AgentContainer mc = getContainerController();

                Object[] arguments = new Object[]{
                        container_count,
                        contamination_radius
                };

                AgentController controllerAgent = mc.createNewAgent("GUI-Agent", "agents.GUIAgent", arguments);
                controllerAgent.start();
            } catch (Exception except) {
                except.printStackTrace();
            }

            // receive created gui
            do {
                agents = getAgentsAtService(this_agent, "GUI");
            } while ((agents == null) || (agents.length < 1));
            gui_agent = agents[0];

            // sending introduction to controllers
            assert controller_agents != null;
            ACLMessage intro_message = createMessage(ACLMessage.INFORM, "intro", controller_agents);
            intro_message.addReceiver(gui_agent);
            send(intro_message);

            // Waiting for ready from all controllers
            MessageTemplate ready_message_template = getMessageTemplate(ACLMessage.INFORM,"ready");
            for (int i = 0; i < container_count; i++) blockingReceive(ready_message_template);

            // Send back go to all
            go_message = createMessage(ACLMessage.INFORM, "GO", controller_agents);

            this_agent.addBehaviour(simulation_loop_behavior);

            menuFrame.setVisible(false);
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
                || max_contamination_length <= 0
                || travel_chance < 0
                || average_travel_duration < 0){
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

        if (container_count == 1 && travel_chance > 0){
            System.out.println("Only one container available, setting travel chance to zero");
            travel_chance = -1.0;
        }

        return true;
    }

    private void createSubContainers(){

        // Remote containers
        if (remote_container_count > 0) {
            if(!remote_launched) launchRemote();
            ACLMessage create_containers_message = createMessage(ACLMessage.INFORM, "container", remote_manager);

            String parameters = "";
            parameters += " " + agent_count;
            parameters += " " + init_sick;
            parameters += " " + agent_speed;
            parameters += " " + contamination_radius;
            parameters += " " + contamination_prob;
            parameters += " " + min_contamination_length;
            parameters += " " + max_contamination_length;
            parameters += " " + travel_chance;
            parameters += " " + average_travel_duration;
            create_containers_message.setContent("create " + remote_container_count + parameters);
            System.out.println("Sending create container signal");
            send(create_containers_message);

            // Waiting for confirmation
            blockingReceive(getMessageTemplate(ACLMessage.INFORM, "confirm_created"));
        }

        // Local containers
        for (int i = remote_container_count; i < container_count; i++) {
            int container_id = i + 1;
            String container_name = "Container-" + container_id;

            try {
                AgentContainer mc = getContainerController();

                // Creating the controller
                Object[] controller_arguments = new Object[]{
                        container_name,
                        false, // gui enabled
                        agent_count,
                        init_sick,
                        agent_speed,
                        contamination_radius,
                        contamination_prob,
                        min_contamination_length,
                        max_contamination_length,
                        travel_chance,
                        average_travel_duration
                };
                AgentController controllerAgent = mc.createNewAgent("Controller-" + container_id, "agents.ControllerAgent", controller_arguments);
                controllerAgent.start();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void createExitConfirmationWindow(){
        // Creation of exit confirmation window
        exit_confirmation_window = new JFrame();
        exit_confirmation_window.setTitle("Simulation still running");
        exit_confirmation_window.setLayout(new FlowLayout(FlowLayout.CENTER,10,10));

        exit_confirmation_info = new JLabel("The simulation is still running, stay or stop simulation:");
        exit_confirmation_window.add(exit_confirmation_info);

        JButton stay_button = new JButton("Stay");
        stay_button.addActionListener(e -> {
            exit_confirmation_window.setVisible(false);
        });
        exit_confirmation_window.add(stay_button);

        JButton exit_button = new JButton("Exit simulation");
        exit_button.addActionListener(e -> {
            exit_confirmation_window.setVisible(false);
            deleteContainers();
            menuFrame.setVisible(true);
        });
        exit_confirmation_window.add(exit_button);

        // Finalization of frame
        exit_confirmation_window.setBounds(200,200,600, 80);
        exit_confirmation_window.setResizable(false);
    }

    private class managerLoop extends TickerBehaviour {
        public managerLoop() {
            super(this_agent, 100);
        }

        @Override
        protected void onTick() {
            // Process force kill message
            ACLMessage force_kill = receive(force_kill_message_template);
            if (force_kill != null) {
                deleteContainers();
                menuFrame.setVisible(true);
                return;
            }

            // Tracking time
            long start_time = System.currentTimeMillis();

            // Sending go to all controllers
            send(go_message);

            // Receive back done from all controllers
            int done_controllers = 0;
            for (int i = 0; i < container_count; i++) {
                ACLMessage done_response = blockingReceive(done_message_template);

                int sick_count = Integer.parseInt(done_response.getContent());
                if (sick_count == 0) done_controllers++;
            }

            // Process end
            if (done_controllers == container_count) {
                System.out.println("Simulation is done, stopping simulation loop.");
                this_agent.removeBehaviour(simulation_loop_behavior);

                exit_confirmation_window.setTitle("Simulation done.");
                exit_confirmation_info.setText("The simulation is done, exit or stay to observe results:");
                exit_confirmation_window.setVisible(true);
            }

            // Tracking time end
            long duration_s = System.currentTimeMillis() - start_time;
//            if (DEBUG) System.out.println("[Manager] Iteration done in (ms): " + duration_s);
        }
    }

    private void deleteContainers() {
        ACLMessage kill_message = createMessage(ACLMessage.INFORM, "kill", controller_agents);
        kill_message.addReceiver(gui_agent);
        send(kill_message);
    }

    private void launchRemote() {
        String user = "jade_creator";
        String password = "Potato";
        String host = "192.168.68.124";
        int port = 22;

        try {

            // Setup connection
            JSch jsch = new JSch();
            Session session = jsch.getSession(user, host, port);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            System.out.println("Establishing Connection to Remote...");
            session.connect();
            System.out.println("Remote Connection established.");

            // SFTP to transfer files
            ChannelSftp channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect(5000);

            String[] files = listFiles("./src");
            String remoteRoot = "/home/jade_creator/jade_project/";
            for (String file: files) {
                System.out.println("[Upload to Remote] " + file + " -> " + remoteRoot + file.substring(2));
                channelSftp.put(file, remoteRoot + file.substring(2));
            }

            channelSftp.disconnect();

            // Sending launch command
            System.out.println("Sending launch command to Remote");
            ChannelExec channelExec = (ChannelExec) session.openChannel("exec");
            channelExec.setCommand("cd jade_project; javac -cp ./lib/jade.jar:./lib/jsch-0.1.55.jar -d ./out/ ./src/*.java ./src/agents/*.java && java -cp ./lib/jade.jar:./lib/jsch-0.1.55.jar:./out LaunchRemote");

            channelExec.setXForwarding(true);
            channelExec.setInputStream(null);
            channelExec.setErrStream(System.err);

            remote_input_stream = channelExec.getInputStream();

            channelExec.connect(5000);

            // Added cycle behavior to read output of ssh connection execution
            addBehaviour(new CyclicBehaviour() {
                @Override
                public void action() {
                    try {
                        byte[] tmp=new byte[1024];
                        while(true){
                            if (!(remote_input_stream.available()>0)) break;
                            int i= 0;
                            i = remote_input_stream.read(tmp, 0, 1024);
                            if(i<0)break;
                            System.out.print("[REMOTE] " + new String(tmp, 0, i));
                        }
                        if(channelExec.isClosed()){
                            if(remote_input_stream.available()>0) return;
                            System.out.println("[REMOTE] " + "exit-status: "+channelExec.getExitStatus());
                            removeBehaviour(this);
                        }
//                        try{Thread.sleep(1000);}catch(Exception ee){}
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });

            // Wait for remote manager to settle
            sleep(1000);

            // Find the remote manager agents
            AID[] agents;
            do {
                agents = getAgentsAtService(this, "remote-manager");
            } while (agents == null || agents.length == 0);
            remote_manager = agents[0];

            // Send intro
            ACLMessage intro_message = createMessage(ACLMessage.INFORM, "intro", remote_manager);
            send(intro_message);

            blockingReceive(getMessageTemplate(ACLMessage.INFORM, "ready"));
            System.out.println("Received ready from remote manager");

            // finalization
            remote_launched = true;

        } catch (JSchException e) {
            e.printStackTrace();
        } catch (InterruptedException | SftpException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String[] listFiles(String src_folder){
        File folder = new File(src_folder);
        File[] listOfFiles = folder.listFiles();

        ArrayList result = new ArrayList();

        for (int i = 0; i < Objects.requireNonNull(listOfFiles).length; i++) {
            if (listOfFiles[i].isFile()) {
                result.add(src_folder + "/" + listOfFiles[i].getName());
            } else if (listOfFiles[i].isDirectory()) {
                String[] result_directory = listFiles(src_folder + "/" + listOfFiles[i].getName());
                for (String res: result_directory) result.add(res);
            }
        }

        String[] array_result = new String[result.size()];
        for (int i = 0; i < result.size(); i++) array_result[i] = (String) result.get(i);
        return array_result;
    }
}
