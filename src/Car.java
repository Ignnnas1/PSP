import java.awt.*;

public class Car {
    private double x, y;
    private final Direction dir;
    private final double speed = 2.0;

    private final int width = 18;
    private final int height = 30;

    private final double radius = 14;

    private boolean stopped = false;



    public Car(double x, double y, Direction dir) {
        this.x = x;
        this.y = y;
        this.dir = dir;
    }

    public void update(boolean shouldStop, int stopLine, int _1, int _2, int _3) {

        if (shouldStop) {
            switch (dir) {
                case UP -> {
                    if (y - speed <= stopLine) {
                        stopped = true;
                        return;
                    }
                }
                case DOWN -> {
                    if (y + speed >= stopLine) {
                        stopped = true;
                        return;
                    }
                }
                case LEFT -> {
                    if (x - speed <= stopLine) {
                        stopped = true;
                        return;
                    }
                }
                case RIGHT -> {
                    if (x + speed >= stopLine) {
                        stopped = true;
                        return;
                    }
                }
            }
        }

        stopped = false;

        switch (dir) {
            case UP -> y -= speed;
            case DOWN -> y += speed;
            case LEFT -> x -= speed;
            case RIGHT -> x += speed;
        }
    }

    public boolean isOutOfBounds(int boardW, int boardH) {
        return (x < -50 || x > boardW + 50 || y < -50 || y > boardH + 50);
    }

    public void draw(Graphics2D g2) {
        g2.setColor(Color.CYAN);

        // Draw orientation
        int drawX = (int) x;
        int drawY = (int) y;

        switch (dir) {
            case UP, DOWN -> g2.fillRect(drawX - width/2, drawY - height/2, width, height);
            case LEFT, RIGHT -> g2.fillRect(drawX - height/2, drawY - width/2, height, width);
        }
    }
    public Direction getDirection() {
        return dir;
    }
    public boolean isBeforeIntersection(int stopX1, int stopY1, int stopX2, int stopY2) {
        return switch (dir) {
            case UP    -> y > stopX2;
            case DOWN  -> y < stopY1;
            case LEFT  -> x > stopY2;
            case RIGHT -> x < stopX1;
        };
    }



    public double getX() { return x; }
    public double getY() { return y; }
    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }


    public double distanceTo(Car other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public boolean collidesWith(Car other) {
        return this.distanceTo(other) < (this.radius + other.radius);
    }


    public boolean isStopped() { return stopped; }
    public void setStopped(boolean s) { stopped = s; }



}
