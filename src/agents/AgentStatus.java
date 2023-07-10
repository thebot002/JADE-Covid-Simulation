package agents;

import java.awt.*;

public enum AgentStatus {
    HEALTHY, SICK, RECOVERED;

    public static AgentStatus fromString(String str){
        AgentStatus status = null;
        switch (str) {
            case "healthy": status = HEALTHY; break;
            case "sick": status = SICK; break;
            case "recovered": status = RECOVERED; break;
            default: throw new IllegalStateException("Unexpected value: " + str);
        }
        return status;
    }

    public String toString(){
        String status = null;
        switch (this) {
            case HEALTHY: status="healthy"; break;
            case SICK: status="sick"; break;
            case RECOVERED: status= "recovered"; break;
        }
        return status;
    }

    public Color color(){
        Color c = null;
        switch (this){
            case HEALTHY: c = Color.BLUE; break;
            case SICK: c = Color.RED; break;
            case RECOVERED: c = Color.GRAY; break;
        }
        return c;
    }
}
