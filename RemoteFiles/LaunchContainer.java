import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;

public class LaunchContainer {
    public static void main(String[] args) {
        try {
            String container_name = (String) args[0];

            Runtime runtime = Runtime.instance();
            ProfileImpl pc = new ProfileImpl(false);
            pc.setParameter(ProfileImpl.CONTAINER_NAME, container_name);
            pc.setParameter(ProfileImpl.MAIN_HOST, "10.8.0.2");
            AgentContainer ac = runtime.createAgentContainer(pc);
            ac.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
