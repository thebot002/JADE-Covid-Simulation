package agents;

import jade.core.AID;
import jade.core.Agent;

public class RemoteManagerAgent extends Agent {
    private AID local_manager;

    @Override
    protected void setup() {
        System.out.println("Hello from "+getName());
//        // Registering to the Controller group service
//        try {
//            DFAgentDescription dfd = new DFAgentDescription();
//            dfd.setName(getAID());
//            ServiceDescription sd = new ServiceDescription();
//            sd.setType("remote-manager");
//            sd.setName("Remote");
//            dfd.addServices(sd);
//            DFService.register(this, dfd);
//        }
//        catch (FIPAException fe) {
//            fe.printStackTrace();
//        }
//
//        // Receiving intro from local manager
//        System.out.println("Remote manager settled and registered to service");
//        ACLMessage intro_message = blockingReceive(MessageTemplate.and(
//                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
//                MessageTemplate.MatchOntology("intro")));
//        local_manager = intro_message.getSender();

    }
}
