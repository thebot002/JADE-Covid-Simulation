import com.jcraft.jsch.*;
import jade.util.leap.ArrayList;
import jade.wrapper.AgentContainer;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.util.ExtendedProperties;
import jade.util.leap.Properties;
import jade.wrapper.AgentController;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class MainContainer {
    public static void main(String[] args) {
        // Main container
        try {
            Runtime runtime = Runtime.instance();
            Properties properties = new ExtendedProperties();
            properties.setProperty(Profile.GUI, "true");
            properties.setProperty("jade_domain_df_autocleanup", "true");
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
    }
}
