import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Game panel:
 * - Level 1: one intersection (your original sizes/stop-lines preserved)
 * - Level 2: two intersections using the same rules (jams, lights, collisions)
 * - HUD timer (90s survive -> next level)
 *
 * NOTE: Car class stays as-is (update(stop, stopLine, 0,0,0)).
 */
public class Game extends JPanel {

    // ===== Config =====
    private final int width;
    private final int height;
    private static final int UPDATE_MS = 1000 / 60;


    private static final class CarEntry {
        final Car car;
        int ix;
        CarEntry(Car car, int ix) { this.car = car; this.ix = ix; }
    }
    private final List<CarEntry> cars = new ArrayList<>();
    private long lastCarSpawn = 0;
    private final int spawnInterval = 1200;


    private int level = 1;
    private long levelStartTime = System.currentTimeMillis();
    private static final int SURVIVE_DURATION_MS = 90_000;


    private static final int LANE_SPACING = 40;
    private static final int JAM_LIMIT_PX = 200;

    // Jam warning (blink)
    private boolean jamWarning = false;
    private long warningToggleTimer = 0;
    private boolean warningVisible = true;

    private Timer loopTimer;

    // ===== Intersection =====
    private static final class Intersection {
        final int cx, cy, size;
        final int stopUp, stopDown, stopLeft, stopRight;
        final TrafficLight north, east, south, west;

        Intersection(int cx, int cy, int size) {
            this.cx = cx;
            this.cy = cy;
            this.size = size;


            this.stopUp    = cy + size + 12;
            this.stopDown  = cy - 12;
            this.stopLeft  = cx + size + 12;
            this.stopRight = cx - 12;


            this.north = new TrafficLight(cx + size/2 - 15, cy - 80);
            this.east  = new TrafficLight(cx + size + 60,   cy + size/2 - 15);
            this.south = new TrafficLight(cx + size/2 - 15, cy + size + 45);
            this.west  = new TrafficLight(cx - 80,          cy + size/2 - 15);
        }

        void updateLights(int delta) {
            north.update(delta);
            east.update(delta);
            south.update(delta);
            west.update(delta);
        }

        void drawArms(Graphics2D g2, int boardW, int boardH) {
            Color asphalt = new Color(45,45,45);
            int roadW = (int)(boardW * 0.32);

            g2.setColor(asphalt);
            // vertical arm
            g2.fillRect(cx + size/2 - roadW/2, 0, roadW, boardH);
            // horizontal arm
            g2.fillRect(0, cy + size/2 - roadW/2, boardW, roadW);
        }

        void drawCenter(Graphics2D g2) {

            g2.setColor(new Color(255, 215, 0, 160));
            g2.fillRect(cx, cy, size, size);
        }

        void drawLaneLines(Graphics2D g2, int boardW, int boardH) {
            Color lane = new Color(220,220,220);
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{10f,10f}, 0));
            g2.setColor(lane);
            g2.drawLine(cx + size/2, 0, cx + size/2, boardH);
            g2.drawLine(0, cy + size/2, boardW, cy + size/2);
        }

        void drawLights(Graphics2D g2) {
            north.draw(g2);
            east.draw(g2);
            south.draw(g2);
            west.draw(g2);
        }

        TrafficLight lightFor(Direction dir) {
            return switch (dir) {
                case DOWN -> north; // entering from top
                case UP   -> south; // entering from bottom
                case RIGHT-> west;  // entering from left
                case LEFT -> east;  // entering from right
            };
        }

        boolean carInside(Car c) {
            double x = c.getX(), y = c.getY();
            return x > cx && x < cx + size && y > cy && y < cy + size;
        }
    }

    private final List<Intersection> intersections = new ArrayList<>();

    // ===== Constructor =====
    public Game(int boardWidth, int boardHeight) {
        this.width = boardWidth;
        this.height = boardHeight;

        setPreferredSize(new Dimension(width, height));
        setBackground(new Color(30, 30, 30));
        setDoubleBuffered(true);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_2) {   // Press "2" to skip to Level 2
                    level = 2;
                    buildLevel(level);
                    levelStartTime = System.currentTimeMillis();
                }
                if (e.getKeyCode() == KeyEvent.VK_1) {   // Press "1" to go back to Level 1
                    level = 1;
                    buildLevel(level);
                    levelStartTime = System.currentTimeMillis();
                }
            }
        });

        // Build initial level
        buildLevel(level);


        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int mx = e.getX(), my = e.getY();
                for (Intersection it : intersections) {
                    for (TrafficLight tl : List.of(it.north, it.east, it.south, it.west)) {
                        if (tl.containsPoint(mx, my)) {
                            tl.requestChange();
                            return;
                        }
                    }
                }
            }
        });


        loopTimer = new Timer(UPDATE_MS, (ActionEvent e) -> {
            update();
            repaint();
        });
    }


    @Override
    public void addNotify() {
        super.addNotify();
        requestFocusInWindow();
    }

    // ===== Start/Stop loop =====
    public void start() { if (!loopTimer.isRunning()) loopTimer.start(); }
    public void stop()  { if (loopTimer.isRunning())  loopTimer.stop(); }

    // ===== Level building =====
    private void buildLevel(int lvl) {
        intersections.clear();
        cars.clear();
        levelStartTime = System.currentTimeMillis();

        if (lvl == 1) {
            int size = (int)(width * 0.28);
            int cx = width/2 - size/2;
            int cy = height/2 - size/2;
            intersections.add(new Intersection(cx, cy, size));
        } else if (lvl == 2) {
            int size = (int)(width * 0.24);


            int spacing = 250;

            int cx1 = width/2 - size - spacing/2;
            int cy1 = height/2 - size/2;

            int cx2 = width/2 +  spacing/2;
            int cy2 = height/2 - size/2;

            intersections.add(new Intersection(cx1, cy1, size));
            intersections.add(new Intersection(cx2, cy2, size));
        }
    }

    // ===== Update =====
    private long lastUpdateTime = System.currentTimeMillis();

    private void update() {
        long now = System.currentTimeMillis();
        int delta = (int)(now - lastUpdateTime);
        lastUpdateTime = now;

        // Lights
        for (Intersection it : intersections) it.updateLights(delta);

        // Spawn
        if (now - lastCarSpawn >= spawnInterval) {
            spawnCar();
            lastCarSpawn = now;
        }

        // Move & cull
        cars.removeIf(entry -> {
            Car car = entry.car;
            Intersection it = intersections.get(entry.ix);

            TrafficLight light = it.lightFor(car.getDirection());
            boolean red = (light.getState() != TrafficLightState.GREEN);

            // Pick the correct stop line
            int stopLine = switch (car.getDirection()) {
                case UP    -> it.stopUp;
                case DOWN  -> it.stopDown;
                case LEFT  -> it.stopLeft;
                case RIGHT -> it.stopRight;
            };

            boolean approaching = switch (car.getDirection()) {
                case UP    -> car.getY() >= stopLine;
                case DOWN  -> car.getY() <= stopLine;
                case LEFT  -> car.getX() >= stopLine;
                case RIGHT -> car.getX() <= stopLine;
            };

            boolean shouldStop = red && approaching;


            car.update(shouldStop, stopLine, 0, 0, 0);

            return car.isOutOfBounds(width, height);
        });


        // === SWITCH INTERSECTION FOR LEFT/RIGHT CARS WHEN PASSING CENTER ===
        for (CarEntry entry : cars) {
            Car c = entry.car;

            if (intersections.size() < 2) continue;

            Intersection leftInt  = intersections.get(0);
            Intersection rightInt = intersections.get(1);


            if (c.getDirection() == Direction.RIGHT && entry.ix == 0) {
                if (c.getX() > leftInt.cx + leftInt.size) {
                    entry.ix = 1; // Now belongs to second intersection
                }
            }


            if (c.getDirection() == Direction.LEFT && entry.ix == 1) {
                if (c.getX() < rightInt.cx) {
                    entry.ix = 0;
                }
            }
        }



        // Lane spacing per intersection & direction
        for (int ix = 0; ix < intersections.size(); ix++) {
            for (Direction dir : Direction.values()) {
                int finalIx = ix;
                List<Car> lane = cars.stream()
                        .filter(c -> c.ix == finalIx && c.car.getDirection() == dir)
                        .map(c -> c.car)
                        .collect(Collectors.toList());

                lane.sort((a, b) -> switch (dir) {
                    case UP    -> Double.compare(a.getY(), b.getY());
                    case DOWN  -> Double.compare(b.getY(), a.getY());
                    case LEFT  -> Double.compare(a.getX(), b.getX());
                    case RIGHT -> Double.compare(b.getX(), a.getX());
                });

                for (int i = 1; i < lane.size(); i++) {
                    Car ahead = lane.get(i - 1);
                    Car behind = lane.get(i);
                    switch (dir) {
                        case UP    -> { if (behind.getY() <= ahead.getY() + LANE_SPACING) behind.setY(ahead.getY() + LANE_SPACING); }
                        case DOWN  -> { if (behind.getY() >= ahead.getY() - LANE_SPACING) behind.setY(ahead.getY() - LANE_SPACING); }
                        case LEFT  -> { if (behind.getX() <= ahead.getX() + LANE_SPACING) behind.setX(ahead.getX() + LANE_SPACING); }
                        case RIGHT -> { if (behind.getX() >= ahead.getX() - LANE_SPACING) behind.setX(ahead.getX() - LANE_SPACING); }
                    }
                }
            }
        }

        // Jam detection per intersection
        jamWarning = false;
        for (int ix = 0; ix < intersections.size(); ix++) {
            Intersection it = intersections.get(ix);

            for (Direction dir : Direction.values()) {
                TrafficLight light = it.lightFor(dir);
                if (light.getState() != TrafficLightState.RED) continue;

                int finalIx = ix;
                List<Car> lane = cars.stream()
                        .filter(c -> c.ix == finalIx && c.car.getDirection() == dir)
                        .map(c -> c.car)
                        .collect(Collectors.toList());
                if (lane.size() < 3) continue;

                lane.sort((a, b) -> switch (dir) {
                    case UP    -> Double.compare(a.getY(), b.getY());
                    case DOWN  -> Double.compare(b.getY(), a.getY());
                    case LEFT  -> Double.compare(a.getX(), b.getX());
                    case RIGHT -> Double.compare(b.getX(), a.getX());
                });

                Car first = lane.get(0);
                boolean firstAtLine = switch (dir) {
                    case UP    -> first.isStopped() && first.getY() >= it.stopUp;
                    case DOWN  -> first.isStopped() && first.getY() <= it.stopDown;
                    case LEFT  -> first.isStopped() && first.getX() >= it.stopLeft;
                    case RIGHT -> first.isStopped() && first.getX() <= it.stopRight;
                };
                if (!firstAtLine) continue;

                int waiting = 1;
                for (int i = 1; i < lane.size(); i++) {
                    Car a = lane.get(i - 1);
                    Car b = lane.get(i);
                    double d = switch (dir) {
                        case UP, DOWN    -> Math.abs(a.getY() - b.getY());
                        case LEFT, RIGHT -> Math.abs(a.getX() - b.getX());
                    };
                    if (d <= LANE_SPACING + 1) waiting++;
                    else break;
                }

                if (waiting >= 3 && waiting < 5) jamWarning = true;

                if (waiting >= 5) {
                    handleTrafficJam();
                    return;
                } else {
                    Car back = lane.get(Math.min(waiting - 1, lane.size() - 1));
                    double dist = switch (dir) {
                        case UP    -> (it.stopUp) - back.getY();
                        case DOWN  -> back.getY() - (it.stopDown);
                        case LEFT  -> (it.stopLeft) - back.getX();
                        case RIGHT -> back.getX() - (it.stopRight);
                    };
                    if (dist > JAM_LIMIT_PX) {
                        handleTrafficJam();
                        return;
                    }
                }
            }
        }

        // Collision detection (only within same intersection box)
        for (int i = 0; i < cars.size(); i++) {
            Car a = cars.get(i).car;
            int aix = cars.get(i).ix;
            Intersection ia = intersections.get(aix);

            for (int j = i + 1; j < cars.size(); j++) {
                if (cars.get(j).ix != aix) continue; // only collide within same intersection
                Car b = cars.get(j).car;

                if (ia.carInside(a) && ia.carInside(b) && a.collidesWith(b)) {
                    handleCollision();
                    return;
                }
            }
        }

        if (System.currentTimeMillis() - levelStartTime >= SURVIVE_DURATION_MS) {
            nextLevel();
        }
    }

    // ===== Rendering =====
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);


        for (Intersection it : intersections) it.drawArms(g2, width, height);


        for (Intersection it : intersections) it.drawCenter(g2);


        for (Intersection it : intersections) it.drawLaneLines(g2, width, height);


        for (Intersection it : intersections) it.drawLights(g2);
        for (CarEntry e : cars) e.car.draw(g2);



        drawHUD(g2);

        if (jamWarning) {
            long now = System.currentTimeMillis();
            if (now - warningToggleTimer > 300) {
                warningVisible = !warningVisible;
                warningToggleTimer = now;
            }
            if (warningVisible) {
                g2.setColor(new Color(255, 40, 40, 160));
                g2.setStroke(new BasicStroke(8));
                for (Intersection it : intersections) {
                    g2.drawRect(it.cx, it.cy, it.size, it.size);
                }
            }
        }
        g2.dispose();
    }

    private void drawHUD(Graphics2D g2) {
        long elapsed = System.currentTimeMillis() - levelStartTime;
        long remaining = Math.max(0, SURVIVE_DURATION_MS - elapsed);

        String levelText = "Level: " + level;
        String timeText = String.format("Survive: %02d:%02d",
                (remaining / 1000) / 60, (remaining / 1000) % 60);

        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 18f));
        FontMetrics fm = g2.getFontMetrics();

        int w = Math.max(fm.stringWidth(levelText), fm.stringWidth(timeText));
        int h = fm.getHeight() * 2;

        int boxW = w + 20;
        int boxH = h + 20;

        int x = width/2 - boxW/2;
        int y = 10;

        g2.setColor(new Color(0, 0, 0, 160));
        g2.fillRoundRect(x, y, boxW, boxH, 12, 12);

        g2.setColor(Color.WHITE);
        g2.drawString(levelText, x + 10, y + 10 + fm.getAscent());
        g2.drawString(timeText, x + 10, y + 10 + fm.getAscent() + fm.getHeight());
    }

    private void nextLevel() {

        if (level == 2) {
            stop();
            JOptionPane.showMessageDialog(this, "You win! Thanks for playing!");
            System.exit(0);
            return;
        }


        level++;
        buildLevel(level);
    }


    // ===== Spawning =====
    private void spawnCar() {
        Direction dir;
        double r = Math.random();


        if (r < 0.45)       dir = Direction.UP;    // 45%
        else if (r < 0.85)  dir = Direction.DOWN;  // 40%
        else if (r < 0.93)  dir = Direction.LEFT;  // 8%
        else                dir = Direction.RIGHT; // 7%

        Intersection it;

        // UP/DOWN: random intersection (each has its own vertical road)
        if (dir == Direction.UP || dir == Direction.DOWN) {
            int ix = (int)(Math.random() * intersections.size());
            it = intersections.get(ix);
        } else {
            // LEFT/RIGHT: choose closest intersection to the approach side
            if (dir == Direction.LEFT) {
                it = intersections.stream().max(Comparator.comparingInt(a -> a.cx)).get();  // from right -> right-most intersection
            } else {
                it = intersections.stream().min(Comparator.comparingInt(a -> a.cx)).get();  // from left -> left-most intersection
            }
        }

        int roadWidth = (int)(width * 0.32);
        int laneOffset = roadWidth / 4;

        Car car;
        switch (dir) {
            case UP    -> car = new Car(it.cx + it.size/2 - laneOffset, height + 20, Direction.UP);
            case DOWN  -> car = new Car(it.cx + it.size/2 + laneOffset, -20, Direction.DOWN);
            case LEFT  -> car = new Car(width + 20, it.cy + it.size/2 - laneOffset, Direction.LEFT);
            case RIGHT -> car = new Car(-20,      it.cy + it.size/2 + laneOffset, Direction.RIGHT);
            default    -> throw new IllegalStateException();
        }

        cars.add(new CarEntry(car, intersections.indexOf(it)));
    }

    // ===== Fail states =====
    private void handleCollision() {
        stop();
        JOptionPane.showMessageDialog(this, "A collision occurred. Game Over!");
        System.exit(0);
    }

    private void handleTrafficJam() {
        stop();
        JOptionPane.showMessageDialog(this, "Traffic jam! Game Over.");
        System.exit(0);
    }


}
