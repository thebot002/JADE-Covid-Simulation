import jade.wrapper.AgentContainer;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.util.ExtendedProperties;
import jade.util.leap.Properties;
import jade.wrapper.AgentController;

public class MainContainer {
    public static void main(String[] args) {
        // Main container
        try {
            Runtime runtime = Runtime.instance();
            Properties properties = new ExtendedProperties();
            properties.setProperty(Profile.GUI, "true");
            ProfileImpl pc = new ProfileImpl(properties);

            AgentContainer mc = runtime.createMainContainer(pc);
            mc.start();

            // Creating the manager
            AgentController managerAgent = mc.createNewAgent("Manager", "agents.ManagerAgent", new Object[]{});
            managerAgent.start();

        }
        catch (Exception e){
            e.printStackTrace();
        }



//        // Sub container
//        String container_name = "Container-1";
//
//        try {
//            Runtime runtime = Runtime.instance();
//            ProfileImpl pc = new ProfileImpl(false);
//            pc.setParameter(ProfileImpl.CONTAINER_NAME, container_name);
//            pc.setParameter(ProfileImpl.MAIN_HOST, "localhost");
//            AgentContainer ac = runtime.createAgentContainer(pc);
//            ac.start();
//
//            // Creating the controller
//            AgentController controllerAgent = ac.createNewAgent("Controller", "agents.ControllerAgent", new Object[]{container_name});
//            controllerAgent.start();
//
//        }
//        catch (Exception e){
//            e.printStackTrace();
//        }
    }
}
