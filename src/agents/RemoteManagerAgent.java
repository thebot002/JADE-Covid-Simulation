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
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;

import java.util.Objects;

public class RemoteManagerAgent extends Agent {
    private AID local_manager;

    @Override
    protected void setup() {
        // Registering to the Controller group service
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            ServiceDescription sd = new ServiceDescription();
            sd.setType("remote-manager");
            sd.setName("Remote");
            dfd.addServices(sd);
            DFService.register(this, dfd);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }

        // Receiving intro from local manager
        System.out.println("Remote manager settled and registered to service");
        ACLMessage intro_message = blockingReceive(MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.MatchOntology("intro")));
        local_manager = intro_message.getSender();

        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage container_management = blockingReceive(MessageTemplate.and(
                        MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                        MessageTemplate.MatchOntology("container")));

                String message_content = container_management.getContent();
                String[] message_content_parts = message_content.split(" ");
                String type = message_content_parts[0];

                if (Objects.equals(type, "create")) {
                    // Process parameters
                    int container_count = Integer.parseInt(message_content_parts[1]);

                    int agent_count = Integer.parseInt(message_content_parts[2]);
                    int init_sick = Integer.parseInt(message_content_parts[3]);
                    double agent_speed = Double.parseDouble(message_content_parts[4]);
                    int contamination_radius = Integer.parseInt(message_content_parts[5]);
                    double contamination_prob = Double.parseDouble(message_content_parts[6]);
                    int min_contamination_length = Integer.parseInt(message_content_parts[7]);
                    int max_contamination_length = Integer.parseInt(message_content_parts[8]);
                    double travel_chance = Double.parseDouble(message_content_parts[9]);
                    int average_travel_duration = Integer.parseInt(message_content_parts[8]);

                    // Create containers
                    for (int i = 0; i < container_count; i++) {
                        int container_id = i + 1;
                        String container_name = "Remote-Container-" + container_id;

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
                            AgentController controllerAgent = mc.createNewAgent("Remote-Controller-" + container_id, "agents.ControllerAgent", controller_arguments);
                            controllerAgent.start();

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    // Send confirmation
                    ACLMessage confirmation_message = new ACLMessage(ACLMessage.INFORM);
                    confirmation_message.setOntology("confirm_created");
                    confirmation_message.addReceiver(local_manager);
                    send(confirmation_message);
                }
            }
        });

        ACLMessage ready_message = new ACLMessage(ACLMessage.INFORM);
        ready_message.setOntology("ready");
        ready_message.addReceiver(local_manager);
        send(ready_message);

    }

    private void createSubContainers(int n) {
        // Local containers

    }
}
