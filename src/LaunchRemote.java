import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;

public class LaunchRemote {
    public static void main(String[] args) {
        try {

            // Remote main container
            Runtime runtime = Runtime.instance();
            ProfileImpl pc = new ProfileImpl(false);
            pc.setParameter(ProfileImpl.CONTAINER_NAME, "RPI-Main-Container");
            pc.setParameter(ProfileImpl.MAIN_HOST, "10.8.0.2");
            AgentContainer ac = runtime.createAgentContainer(pc);
            ac.start();

            System.out.println("Remote Launch: Container created, setting up manager");
            AgentController remoteManagerAgent = ac.createNewAgent("Remote-Manager", "agents.RemoteManagerAgent", new Object[]{});
            remoteManagerAgent.start();

            System.out.println("Remote launch: Agent creation done");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
