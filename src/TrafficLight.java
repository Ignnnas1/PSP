import java.awt.*;

public class TrafficLight {

    private int x, y;
    private final int width = 30;
    private final int height = 80;

    private TrafficLightState state = TrafficLightState.RED;

    // Transition control
    private boolean transitioning = false;
    private int yellowTimer = 0;
    private final int yellowDuration = 1000; // 1 second
    private TrafficLightState stateBeforeTransition = TrafficLightState.RED;

    public TrafficLight(int x, int y) {
        this.x = x;
        this.y = y;
    }


    public void requestChange() {

        if (transitioning) return;


        if (state == TrafficLightState.RED || state == TrafficLightState.GREEN) {
            stateBeforeTransition = state;
            state = TrafficLightState.YELLOW;
            yellowTimer = 0;
            transitioning = true;
        }
    }


    public void update(int deltaMs) {
        if (!transitioning) return;

        yellowTimer += deltaMs;

        if (yellowTimer >= yellowDuration) {

            if (stateBeforeTransition == TrafficLightState.RED) {
                // RED → YELLOW → GREEN
                state = TrafficLightState.GREEN;
            } else {
                // GREEN → YELLOW → RED
                state = TrafficLightState.RED;
            }
            transitioning = false;
        }
    }

    public boolean containsPoint(int mx, int my) {
        return (mx >= x && mx <= (x + width) && my >= y && my <= (y + height));
    }

    public void draw(Graphics2D g2) {
        g2.setColor(new Color(50, 50, 50));
        g2.fillRect(x, y, width, height);

        drawLight(g2, x + width/2, y + 15, Color.RED, state == TrafficLightState.RED);
        drawLight(g2, x + width/2, y + 40, Color.YELLOW, state == TrafficLightState.YELLOW);
        drawLight(g2, x + width/2, y + 65, Color.GREEN, state == TrafficLightState.GREEN);
    }

    private void drawLight(Graphics2D g2, int cx, int cy, Color c, boolean active) {
        g2.setColor(active ? c : c.darker().darker());
        g2.fillOval(cx - 8, cy - 8, 16, 16);
    }

    public TrafficLightState getState() {
        return state;
    }
    public int getX() { return x; }
    public int getY() { return y; }

    public int getWidth() { return width; }
    public int getHeight() { return height; }


}
