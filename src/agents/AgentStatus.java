package agents;

import java.awt.*;

public enum AgentStatus {
    HEALTHY, SICK, RECOVERED;

    public static AgentStatus fromString(String str){
        return switch (str) {
            case "healthy" -> HEALTHY;
            case "sick" -> SICK;
            case "recovered" -> RECOVERED;
            default -> throw new IllegalStateException("Unexpected value: " + str);
        };
    }

    public String toString(){
        return switch (this) {
            case HEALTHY -> "healthy";
            case SICK -> "sick";
            case RECOVERED -> "recovered";
        };
    }

    public Color color(){
        return switch (this){
            case HEALTHY -> Color.BLUE;
            case SICK -> Color.RED;
            case RECOVERED -> Color.GRAY;
        };
    }
}
