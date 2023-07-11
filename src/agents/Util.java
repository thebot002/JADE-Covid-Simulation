package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.Objects;

public class Util {

    public static void registerAgentAtService(Agent agent, String service){
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(agent.getAID());
            ServiceDescription sd = new ServiceDescription();
            sd.setType(service);
            sd.setName(service);
            dfd.addServices(sd);
            DFService.register(agent, dfd);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }

    public static AID[] getAgentsAtService(Agent current_agent, String service){
        AID[] agents;
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType(service);
        template.addServices(sd);

        try {
            DFAgentDescription[] result = DFService.search(current_agent, template);
            agents = new AID[result.length];
            for (int i = 0; i < result.length; i++) agents[i] = result[i].getName();
            return agents;
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }
        return null;
    }

    public static AID[] getAgentsAtService(Agent current_agent, String service, AID except){
        AID[] all_agents = getAgentsAtService(current_agent, service);
        assert all_agents != null;
        AID[] agents = new AID[all_agents.length - 1];

        int agent_number = 0;
        for (AID agent : all_agents) {
            if (!Objects.equals(agent, except)) {
                agents[agent_number] = agent;
                agent_number++;
            }
        }
        return agents;
    }

    public static MessageTemplate getMessageTemplate(int performative, String ontology){
        return MessageTemplate.and(
                MessageTemplate.MatchPerformative(performative),
                MessageTemplate.MatchOntology(ontology));
    }

    public static ACLMessage createMessage(int performative, String ontology){
        ACLMessage message = new ACLMessage(performative);
        message.setOntology(ontology);
        return message;
    }

    public static ACLMessage createMessage(int performative, String ontology, AID receiver){
        ACLMessage message = createMessage(performative, ontology);
        message.addReceiver(receiver);
        return message;
    }

    public static ACLMessage createMessage(int performative, String ontology, AID[] receivers){
        ACLMessage message = createMessage(performative, ontology);
        for (AID receiver: receivers) message.addReceiver(receiver);
        return message;
    }
}
