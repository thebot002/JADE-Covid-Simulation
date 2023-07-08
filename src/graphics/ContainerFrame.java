package graphics;

import agents.AgentStatus;

import javax.swing.*;
import java.awt.*;

public class ContainerFrame extends JFrame {

    private class DrawPanel extends JPanel {
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            // Frame
            g.setColor(Color.WHITE);
            g.drawRect(MARGIN,MARGIN,SCALE*MAX_X,SCALE*MAX_Y);

            // Dots
            for (int i=0; i<dots.length; i++) {
                double[] dot = dots[i];
                AgentStatus status = AgentStatus.fromString(statuses[i]);

                g.setColor(status.color());
                int x = (int) (dot[0] * SCALE) + MARGIN;
                int y = (int) (dot[1] * SCALE) + MARGIN;
                g.fillOval(x, y, SCALE, SCALE);
            }
        }
    }

    private double[][] dots;
    private String[] statuses;
    private DrawPanel draw_panel;

    private final int SCALE = 5;
    private final int MAX_X = 100;
    private final int MAX_Y = 100;
    private final int MARGIN = 10;

    public ContainerFrame(double[][] dots, String[] statuses) {
        setTitle("Drawing a Circle");
        setBounds(100, 100, (MAX_X*SCALE)+(4*MARGIN), (MAX_Y*SCALE)+(6*MARGIN));
        setVisible(true);
        setResizable(false);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        this.dots = dots;
        this.statuses = statuses;

        draw_panel = new DrawPanel();
        draw_panel.setBackground(Color.BLACK);
        add(draw_panel);
    }

    public void setDots(double[][] dots, String[] statuses){
        this.dots = dots;
        this.statuses = statuses;
        draw_panel.repaint();
    }
}
