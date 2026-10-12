
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

class CoinQuest extends JPanel implements ActionListener, KeyListener {

    private static final int W = 1100, H = 700, GROUND = 590;
    private static final double GRAVITY = 0.62, JUMP_POWER = -13.5;

    private int animationTick = 0;

    static class Platform {
        double x, y, w, h;

        Platform(double x, double y, double w, double h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    static class Coin {
        double x, y;
        boolean taken;

        Coin(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    static class Enemy {
        double x, y, min, max, speed;
        boolean alive = true;

        Enemy(double x, double y, double min, double max, double speed) {
            this.x = x;
            this.y = y;
            this.min = min;
            this.max = max;
            this.speed = speed;
        }
    }

    static class Spike {
        double x, y, w;

        Spike(double x, double y, double w) {
            this.x = x;
            this.y = y;
            this.w = w;
        }
    }

    private final List<Platform> platforms = new ArrayList<>();
    private final List<Coin> coins = new ArrayList<>();
    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Spike> spikes = new ArrayList<>();

    private double px = 100, py = GROUND - 42, vx, vy;
    private double worldWidth = 3000, cameraX, checkpointX = 100;

    private final int playerW = 30, playerH = 42;

    private int level = 1, score, lives = 3, coinCount;
    private int coyote, jumpBuffer;

    private boolean left, right, jumping, sprint, jumpWasDown;
    private boolean onGround, facingRight = true;
    private boolean started, paused, gameOver, won, checkpointReached;

    private final javax.swing.Timer timer;

    public CoinQuest() {
        setPreferredSize(new Dimension(W, H));
        setFocusable(true);
        setDoubleBuffered(true);
        addKeyListener(this);

        buildLevel();

        timer = new javax.swing.Timer(16, this);
        timer.start();
    }

    private void buildLevel() {
        platforms.clear();
        coins.clear();
        enemies.clear();
        spikes.clear();

        worldWidth = 2600 + level * 450;

        addPlatform(0, GROUND, 520, 150);

        Random r = new Random(level * 12345L);
        double x = 520;

        while (x < worldWidth - 400) {
            x += Math.min(60 + level * 7 + r.nextInt(20), 145);

            double width = 230 + r.nextInt(140);
            double y = Math.max(
                    390,
                    GROUND - r.nextInt(Math.min(100 + level * 7, 190))
            );

            addPlatform(x, y, width, H - y);

            for (int i = 0; i < 3; i++) {
                coins.add(new Coin(x + 45 + i * 45, y - 35));
            }

            if (width > 270 &&
                    r.nextDouble() < Math.min(.25 + level * .04, .7)) {
                spikes.add(new Spike(x + width * .55, y - 22, 42));
            }

            if (r.nextDouble() < Math.min(.3 + level * .04, .75)) {
                enemies.add(new Enemy(
                        x + 70, y - 30,
                        x + 10, x + width - 35,
                        1.2 + level * .17
                ));
            }

            if (r.nextDouble() < .65) {
                addPlatform(x + width * .35, y - 105, 110, 18);
            }

            x += width;
        }

        addPlatform(worldWidth - 420, GROUND, 500, 150);
        coins.add(new Coin(worldWidth - 300, GROUND - 65));

        checkpointReached = false;
        checkpointX = 100;

        px = 100;
        py = GROUND - playerH;
        vx = vy = cameraX = 0;

        onGround = false;
        coyote = jumpBuffer = 0;
        jumpWasDown = false;
    }

    private void addPlatform(double x, double y, double w, double h) {
        platforms.add(new Platform(x, y, w, h));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        animationTick++;

        if (started && !paused && !gameOver && !won) {
            updateGame();
        }

        repaint();
    }

    private Rectangle playerRect() {
        return new Rectangle((int) px, (int) py, playerW, playerH);
    }

    private void updateGame() {
        double maxSpeed = sprint ? 7.0 : 4.7;

        if (left) {
            vx -= .65;
            facingRight = false;
        }

        if (right) {
            vx += .65;
            facingRight = true;
        }

        if (!left && !right) {
            vx *= .82;
            if (Math.abs(vx) < .15) vx = 0;
        }

        vx = Math.max(-maxSpeed, Math.min(maxSpeed, vx));

        if (jumping && !jumpWasDown) jumpBuffer = 9;
        jumpWasDown = jumping;

        if (jumpBuffer > 0) jumpBuffer--;

        if (onGround) coyote = 8;
        else if (coyote > 0) coyote--;

        if (jumpBuffer > 0 && coyote > 0) {
            vy = JUMP_POWER;
            onGround = false;
            coyote = jumpBuffer = 0;
        }

        if (!jumping && vy < -4) vy *= .82;

        vy = Math.min(vy + GRAVITY, 13);

        double oldY = py;

        px += vx;
        py += vy;

        onGround = false;

        for (Platform p : platforms) {
            boolean overlap = px + playerW > p.x && px < p.x + p.w;
            boolean landing = oldY + playerH <= p.y + 4
                    && py + playerH >= p.y && vy >= 0;

            if (overlap && landing) {
                py = p.y - playerH;
                vy = 0;
                onGround = true;
            }
        }

        if (px < 0) {
            px = 0;
            vx = 0;
        }

        if (py > H + 100) {
            loseLife();
            return;
        }

        Rectangle player = playerRect();

        for (Coin c : coins) {
            if (!c.taken &&
                    player.intersects(
                            new Rectangle((int)c.x, (int)c.y, 24, 24))) {
                c.taken = true;
                score += 10;
                coinCount++;
            }
        }

        for (Spike s : spikes) {
            if (player.intersects(
                    new Rectangle((int)s.x, (int)s.y, (int)s.w, 22))) {
                loseLife();
                return;
            }
        }

        for (Enemy e : enemies) {
            if (!e.alive) continue;

            e.x += e.speed;

            if (e.x < e.min || e.x > e.max) {
                e.speed = -e.speed;
                e.x = Math.max(e.min, Math.min(e.max, e.x));
            }

            if (player.intersects(
                    new Rectangle((int)e.x, (int)e.y, 30, 30))) {
                if (vy > 0 && oldY + playerH <= e.y + 12) {
                    e.alive = false;
                    vy = -9;
                    score += 50;
                } else {
                    loseLife();
                    return;
                }
            }
        }

        if (px > worldWidth * .5 && !checkpointReached) {
            checkpointReached = true;
            checkpointX = worldWidth * .5;
            score += 100;
        }

        cameraX = Math.max(
                0, Math.min(px - W * .36, worldWidth - W)
        );

        if (px >= worldWidth - 90) {
            score += 200 * level;
            level++;

            if (level > 8) {
                won = true;
            } else {
                buildLevel();
            }
        }
    }

    private void loseLife() {
        lives--;

        if (lives <= 0) {
            gameOver = true;
            return;
        }

        px = checkpointReached ? checkpointX : 100;
        py = 250;
        vx = vy = 0;

        cameraX = Math.max(0, px - W * .36);

        onGround = false;
        coyote = jumpBuffer = 0;
    }

    // ----------------------------------------------------
    // BACKGROUND: sky, clouds, mountains, hills and trees
    // ----------------------------------------------------

    private void drawBackground(Graphics2D g) {
        Graphics2D b = (Graphics2D) g.create();

        // Gradient sky
        b.setPaint(new GradientPaint(
                0, 0, new Color(35, 133, 218),
                0, H, new Color(188, 239, 249)
        ));
        b.fillRect(0, 0, W, H);

        // Sun glow
        b.setColor(new Color(255, 246, 173, 45));
        b.fillOval(790, 35, 190, 190);

        b.setColor(new Color(255, 235, 139));
        b.fillOval(835, 68, 95, 95);

        b.setColor(new Color(255, 250, 206));
        b.fillOval(850, 82, 65, 65);

        // Distant mountains with parallax
        int farShift = (int)(cameraX * .10) % 500;

        for (int i = -1; i < 4; i++) {
            int mx = i * 500 - farShift;

            Polygon mountain = new Polygon(
                    new int[]{mx - 80, mx + 160, mx + 400},
                    new int[]{440, 120, 440}, 3
            );

            b.setColor(new Color(106, 160, 193));
            b.fillPolygon(mountain);

            Polygon snow = new Polygon(
                    new int[]{mx + 100, mx + 160, mx + 220, mx + 185,
                            mx + 160, mx + 135},
                    new int[]{200, 120, 200, 183, 197, 183}, 6
            );

            b.setColor(new Color(235, 249, 250));
            b.fillPolygon(snow);
        }

        // Moving fluffy clouds
        drawCloud(b, cloudX(100, 1250), 105, 1.0);
        drawCloud(b, cloudX(460, 1500), 175, .78);
        drawCloud(b, cloudX(850, 1800), 95, 1.15);
        drawCloud(b, cloudX(1120, 1600), 225, .7);

        // Distant green hills
        int hillShift = (int)(cameraX * .20) % 420;

        b.setColor(new Color(109, 202, 125));
        for (int i = -1; i < 5; i++) {
            int hx = i * 420 - hillShift;
            b.fillOval(hx, 340, 530, 240);
        }

        b.setColor(new Color(71, 176, 98));
        for (int i = -1; i < 6; i++) {
            int hx = i * 340 - hillShift;
            b.fillOval(hx, 410, 400, 190);
        }

        // Distant trees
        int treeShift = (int)(cameraX * .30) % 170;

        for (int i = -1; i < 9; i++) {
            int tx = i * 170 - treeShift;
            drawTree(b, tx, 490, .72);
        }

        // Foreground grass
        b.setColor(new Color(48, 155, 74));
        b.fillRect(0, 565, W, 135);

        b.setColor(new Color(77, 190, 82));
        b.fillOval(-80, 540, 400, 100);
        b.fillOval(300, 550, 420, 100);
        b.fillOval(720, 540, 450, 120);

        b.dispose();
    }

    private int cloudX(int start, int range) {
        return Math.floorMod(
                start + animationTick / 2, range + 250
        ) - 120;
    }

    private void drawCloud(Graphics2D g, int x, int y, double scale) {
        Graphics2D c = (Graphics2D) g.create();

        c.translate(x, y);
        c.scale(scale, scale);

        c.setColor(new Color(255, 255, 255, 65));
        c.fillOval(0, 15, 115, 38);

        c.setColor(new Color(255, 255, 255, 235));
        c.fillOval(8, 14, 48, 35);
        c.fillOval(28, -4, 50, 52);
        c.fillOval(55, 8, 44, 39);
        c.fillOval(75, 19, 42, 27);

        c.dispose();
    }

    private void drawTree(Graphics2D g, int x, int y, double scale) {
        Graphics2D t = (Graphics2D) g.create();

        t.translate(x, y);
        t.scale(scale, scale);

        t.setColor(new Color(107, 72, 42));
        t.fillRoundRect(16, 28, 12, 48, 4, 4);

        t.setColor(new Color(24, 112, 60));
        t.fillOval(-3, 5, 42, 40);

        t.setColor(new Color(37, 143, 68));
        t.fillOval(4, -8, 35, 39);

        t.setColor(new Color(63, 169, 74));
        t.fillOval(1, 7, 23, 23);

        t.dispose();
    }

    // ----------------------------------------------------
    // MAIN RENDERING
    // ----------------------------------------------------

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        Graphics2D g = (Graphics2D) graphics.create();

        g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );

        g.setRenderingHint(
                RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY
        );

        drawBackground(g);

        if (!started) {
            drawStartScreen(g);
            g.dispose();
            return;
        }

        g.translate(-(int)cameraX, 0);

        drawPlatforms(g);
        drawSpikes(g);
        drawCoins(g);
        drawEnemies(g);
        drawCheckpoint(g);
        drawFinish(g);
        drawPlayer(g);

        g.translate((int)cameraX, 0);

        drawHUD(g);

        if (paused) {
            overlay(g, "PAUSED", "Press P to resume");
        }

        if (gameOver) {
            overlay(g, "GAME OVER", "Press ENTER to restart");
        }

        if (won) {
            overlay(g, "YOU WIN!", "You completed all 8 levels!");
        }

        g.dispose();
    }

    private void drawStartScreen(Graphics2D g) {
        g.setColor(new Color(6, 22, 45, 135));
        g.fillRect(0, 0, W, H);

        g.setFont(new Font("Monospaced", Font.BOLD, 64));
        g.setColor(new Color(255, 183, 30));
        centered(g, "COINQUEST", 245);

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        centered(g, "ADVENTURE STARTS HERE", 290);

        g.setColor(new Color(255, 255, 255, 225));
        g.fillRoundRect(W / 2 - 170, 335, 340, 66, 20, 20);

        g.setColor(new Color(25, 115, 73));
        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        centered(g, "PRESS ENTER TO PLAY", 375);

        g.setFont(new Font("SansSerif", Font.PLAIN, 17));
        g.setColor(Color.WHITE);
        centered(g, "Collect coins. Defeat enemies. Reach the finish!", 445);
        centered(g, "A/D or Arrows: Move    SPACE: Jump    SHIFT: Sprint", 480);
    }

    private void drawPlatforms(Graphics2D g) {
        for (Platform p : platforms) {
            int x = (int)p.x;
            int y = (int)p.y;
            int w = (int)p.w;
            int h = (int)p.h;

            // Soil body
            g.setColor(new Color(123, 77, 43));
            g.fillRect(x, y, w, h);

            // Shaded lower soil
            g.setColor(new Color(91, 54, 34));
            g.fillRect(x, y + 18, w, Math.max(0, h - 18));

            // Grass surface
            g.setColor(new Color(41, 139, 53));
            g.fillRect(x, y, w, 13);

            g.setColor(new Color(98, 205, 65));
            g.fillRect(x, y, w, 5);

            // Little grass blades
            g.setColor(new Color(150, 233, 82));

            for (int i = 8; i < w - 5; i += 35) {
                g.drawLine(x + i, y + 4, x + i + 3, y - 2);
                g.drawLine(x + i + 4, y + 5, x + i + 8, y);
            }

            // Decorative stones
            g.setColor(new Color(153, 113, 78));

            for (int i = 16; i < w - 12; i += 76) {
                g.fillOval(x + i, y + 32, 11, 7);
                g.fillOval(x + i + 28, y + 55, 7, 6);
            }

            // Platform edge
            g.setColor(new Color(65, 47, 33));
            g.drawLine(x, y + h - 1, x + w, y + h - 1);
        }
    }

    private void drawSpikes(Graphics2D g) {
        for (Spike s : spikes) {
            int x = (int)s.x;
            int y = (int)s.y;

            for (int i = 0; i < s.w; i += 21) {
                Polygon spike = new Polygon(
                        new int[]{x + i, x + i + 10, x + i + 21},
                        new int[]{y + 22, y, y + 22},
                        3
                );

                g.setColor(new Color(70, 73, 85));
                g.fillPolygon(spike);

                g.setColor(new Color(200, 210, 222));
                g.drawLine(x + i + 10, y + 2, x + i + 5, y + 18);

                g.setColor(Color.WHITE);
                g.drawLine(x + i + 10, y + 3, x + i + 10, y + 10);
            }
        }
    }

    private void drawCoins(Graphics2D g) {
        for (Coin c : coins) {
            if (c.taken) continue;

            int bob = (int)(Math.sin(animationTick * .12 + c.x) * 3);
            int x = (int)c.x;
            int y = (int)c.y + bob;

            // Glow
            g.setColor(new Color(255, 220, 40, 45));
            g.fillOval(x - 7, y - 7, 38, 38);

            // Outer edge
            g.setColor(new Color(174, 105, 9));
            g.fillOval(x - 2, y - 2, 28, 28);

            // Gold coin
            g.setColor(new Color(255, 166, 20));
            g.fillOval(x, y, 24, 24);

            g.setColor(new Color(255, 222, 45));
            g.fillOval(x + 3, y + 3, 18, 18);

            g.setColor(new Color(218, 137, 9));
            g.drawOval(x + 3, y + 3, 18, 18);

            g.setFont(new Font("SansSerif", Font.BOLD, 15));
            g.setColor(new Color(174, 99, 0));
            g.drawString("$", x + 7, y + 17);

            g.setColor(Color.WHITE);
            g.fillOval(x + 5, y + 4, 4, 5);
        }
    }

    private void drawEnemies(Graphics2D g) {
        for (Enemy e : enemies) {
            if (!e.alive) continue;

            int x = (int)e.x;
            int y = (int)e.y;
            int bounce = (int)(Math.sin(animationTick * .18 + e.x) * 2);

            y += bounce;

            // Shadow
            g.setColor(new Color(0, 0, 0, 45));
            g.fillOval(x - 3, y + 26, 36, 8);

            // Feet
            g.setColor(new Color(91, 29, 40));
            g.fillRoundRect(x + 2, y + 23, 10, 8, 4, 4);
            g.fillRoundRect(x + 18, y + 23, 10, 8, 4, 4);

            // Body
            g.setColor(new Color(133, 28, 48));
            g.fillRoundRect(x, y + 5, 30, 23, 9, 9);

            g.setColor(new Color(224, 56, 62));
            g.fillRoundRect(x + 2, y + 2, 26, 23, 9, 9);

            // Horns
            g.setColor(new Color(255, 189, 103));
            g.fillPolygon(
                    new int[]{x + 5, x + 2, x + 12},
                    new int[]{y + 7, y - 4, y + 4}, 3
            );

            g.fillPolygon(
                    new int[]{x + 24, x + 28, x + 18},
                    new int[]{y + 7, y - 4, y + 4}, 3
            );

            // Eyes
            g.setColor(Color.WHITE);
            g.fillOval(x + 5, y + 9, 8, 9);
            g.fillOval(x + 18, y + 9, 8, 9);

            g.setColor(Color.BLACK);
            g.fillOval(x + 8, y + 12, 4, 5);
            g.fillOval(x + 20, y + 12, 4, 5);

            // Angry eyebrows
            g.setColor(new Color(76, 20, 30));
            g.drawLine(x + 5, y + 8, x + 13, y + 10);
            g.drawLine(x + 18, y + 10, x + 26, y + 8);

            // Mouth
            g.setColor(new Color(82, 14, 29));
            g.fillArc(x + 10, y + 18, 12, 6, 180, 180);
        }
    }

    // ----------------------------------------------------
    // IMPROVED PIXEL-ART ADVENTURER
    // ----------------------------------------------------

    private void drawPlayer(Graphics2D g) {
        Graphics2D p = (Graphics2D)g.create();

        int x = (int)px;
        int y = (int)py;

        int step = onGround && Math.abs(vx) > .5
                ? (int)(Math.sin(animationTick * .35) * 3) : 0;

        int direction = facingRight ? 1 : -1;

        // Shadow
        p.setColor(new Color(0, 0, 0, 55));
        p.fillOval(x - 5, y + 38, 39, 8);

        // Back arm
        p.setColor(new Color(191, 115, 67));
        p.fillRoundRect(x + (facingRight ? 1 : 23), y + 19, 7, 14, 4, 4);

        // Legs
        p.setColor(new Color(24, 63, 137));
        p.fillRoundRect(x + 6, y + 27, 8, 12 + Math.max(0, step), 3, 3);
        p.fillRoundRect(x + 17, y + 27, 8, 12 + Math.max(0, -step), 3, 3);

        // Boots
        p.setColor(new Color(77, 43, 31));
        p.fillRoundRect(x + 2 + Math.max(0, step), y + 36, 13, 7, 3, 3);
        p.fillRoundRect(x + 16 + Math.max(0, -step), y + 36, 13, 7, 3, 3);

        p.setColor(new Color(225, 173, 94));
        p.fillRect(x + 4 + Math.max(0, step), y + 40, 10, 2);
        p.fillRect(x + 18 + Math.max(0, -step), y + 40, 10, 2);

        // Green tunic
        p.setColor(new Color(24, 105, 54));
        p.fillRoundRect(x + 4, y + 19, 23, 13, 4, 4);

        p.setColor(new Color(55, 197, 83));
        p.fillRoundRect(x + 5, y + 19, 20, 10, 3, 3);

        // Shirt details
        p.setColor(new Color(157, 242, 99));
        p.drawLine(x + 9, y + 21, x + 13, y + 26);

        p.setColor(new Color(21, 104, 50));
        p.drawLine(x + 18, y + 21, x + 15, y + 27);

        // Belt
        p.setColor(new Color(109, 62, 33));
        p.fillRect(x + 5, y + 28, 20, 4);

        p.setColor(new Color(255, 204, 62));
        p.fillRect(x + 14, y + 28, 4, 4);

        // Neck
        p.setColor(new Color(218, 151, 104));
        p.fillRect(x + 11, y + 15, 10, 7);

        // Face
        p.setColor(new Color(105, 53, 34));
        p.fillRoundRect(x + 5, y + 4, 22, 17, 5, 5);

        p.setColor(new Color(240, 181, 127));
        p.fillRoundRect(x + 7, y + 5, 18, 15, 4, 4);

        // Ears
        p.setColor(new Color(218, 151, 104));
        p.fillRect(x + 4, y + 10, 4, 6);
        p.fillRect(x + 24, y + 10, 4, 6);

        // Hair
        p.setColor(new Color(74, 38, 24));
        p.fillRect(x + 6, y + 3, 19, 5);
        p.fillRect(x + 5, y + 5, 4, 7);
        p.fillRect(x + 11, y + 2, 6, 3);
        p.fillRect(x + 19, y + 3, 6, 3);

        // Eye
        p.setColor(Color.WHITE);
        int eyeX = facingRight ? x + 19 : x + 8;
        p.fillRect(eyeX, y + 10, 5, 5);

        p.setColor(new Color(37, 43, 55));
        p.fillRect(eyeX + (facingRight ? 2 : 0), y + 11, 3, 4);

        // Eyebrow
        p.setColor(new Color(74, 38, 24));
        p.fillRect(eyeX - 1, y + 8, 7, 2);

        // Smile
        p.setColor(new Color(123, 57, 43));
        p.drawLine(x + 13, y + 17, x + 19, y + 17);

        // Front arm
        p.setColor(new Color(240, 181, 127));
        p.fillRoundRect(
                x + (facingRight ? 23 : 1), y + 20, 6, 11, 3, 3
        );

        // Wristband
        p.setColor(new Color(255, 203, 61));
        p.fillRect(x + (facingRight ? 23 : 1), y + 25, 6, 3);

        // Small backpack
        p.setColor(new Color(177, 91, 39));
        p.fillRoundRect(x + (facingRight ? 1 : 22), y + 17, 7, 10, 3, 3);

        p.setColor(new Color(235, 151, 55));
        p.fillRect(x + (facingRight ? 2 : 23), y + 20, 5, 3);

        p.dispose();
    }

    private void drawCheckpoint(Graphics2D g) {
        if (!checkpointReached) return;

        int x = (int)checkpointX;

        g.setColor(new Color(63, 48, 36));
        g.fillRect(x, GROUND - 85, 5, 85);

        g.setColor(new Color(40, 210, 106));
        g.fillPolygon(
                new int[]{x + 5, x + 40, x + 5},
                new int[]{GROUND - 85, GROUND - 72, GROUND - 59}, 3
        );

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.drawString("CHECKPOINT", x - 27, GROUND - 95);
    }

    private void drawFinish(Graphics2D g) {
        int x = (int)worldWidth - 80;

        g.setColor(new Color(75, 49, 32));
        g.fillRoundRect(x, GROUND - 155, 9, 155, 3, 3);

        g.setColor(new Color(255, 205, 45));
        g.fillPolygon(
                new int[]{x + 9, x + 76, x + 9},
                new int[]{GROUND - 151, GROUND - 130, GROUND - 108}, 3
        );

        g.setColor(new Color(255, 244, 160));
        g.fillOval(x + 1, GROUND - 163, 25, 25);

        g.setColor(new Color(255, 182, 22));
        g.fillOval(x + 5, GROUND - 159, 17, 17);

        g.setColor(new Color(90, 58, 23));
        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        g.drawString("GO!", x + 22, GROUND - 126);
    }

    // ----------------------------------------------------
    // HUD AND MENUS
    // ----------------------------------------------------

    private void drawHUD(Graphics2D g) {
        g.setColor(new Color(10, 28, 43, 215));
        g.fillRoundRect(12, 12, 380, 111, 18, 18);

        g.setColor(new Color(80, 202, 124));
        g.fillRoundRect(12, 12, 380, 7, 8, 8);

        g.setFont(new Font("Monospaced", Font.BOLD, 22));
        g.setColor(new Color(255, 211, 63));
        g.drawString("COINQUEST", 27, 43);

        g.setFont(new Font("Monospaced", Font.BOLD, 16));
        g.setColor(Color.WHITE);
        g.drawString("COINS: " + coinCount, 27, 70);
        g.drawString("SCORE: " + score, 195, 70);

        g.setColor(new Color(160, 232, 255));
        g.drawString("LEVEL: " + Math.min(level, 8) + "/8", 27, 97);

        g.setColor(new Color(255, 133, 151));
        g.drawString("LIVES: " + lives, 195, 97);

        // Bottom controls panel
        g.setColor(new Color(10, 28, 43, 180));
        g.fillRoundRect(12, H - 43, 440, 30, 10, 10);

        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.setColor(Color.WHITE);
        g.drawString("A/D MOVE   SPACE JUMP   SHIFT SPRINT   P PAUSE",
                23, H - 23);
    }

    private void overlay(Graphics2D g, String title, String subtitle) {
        g.setColor(new Color(5, 15, 30, 190));
        g.fillRect(0, 0, W, H);

        g.setColor(new Color(255, 190, 35, 35));
        g.fillOval(W / 2 - 210, H / 2 - 190, 420, 300);

        g.setColor(new Color(255, 205, 47));
        g.setFont(new Font("Monospaced", Font.BOLD, 54));
        centered(g, title, H / 2 - 40);

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 21));
        centered(g, subtitle, H / 2 + 12);

        g.setFont(new Font("SansSerif", Font.PLAIN, 19));
        g.setColor(new Color(255, 231, 156));
        centered(g, "Score: " + score, H / 2 + 55);

        if (gameOver || won) {
            g.setFont(new Font("SansSerif", Font.BOLD, 17));
            g.setColor(Color.WHITE);
            centered(g, "Press ENTER to play again", H / 2 + 96);
        }
    }

    private void centered(Graphics2D g, String text, int y) {
        g.drawString(
                text,
                (W - g.getFontMetrics().stringWidth(text)) / 2,
                y
        );
    }

    private void restart() {
        level = 1;
        score = 0;
        lives = 3;
        coinCount = 0;

        started = true;
        paused = false;
        gameOver = false;
        won = false;

        left = right = jumping = sprint = jumpWasDown = false;

        buildLevel();
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();

        if (k == KeyEvent.VK_A || k == KeyEvent.VK_LEFT) left = true;
        if (k == KeyEvent.VK_D || k == KeyEvent.VK_RIGHT) right = true;

        if (k == KeyEvent.VK_SPACE ||
                k == KeyEvent.VK_W || k == KeyEvent.VK_UP) {
            jumping = true;
        }

        if (k == KeyEvent.VK_SHIFT) sprint = true;

        if (k == KeyEvent.VK_ENTER &&
                (!started || gameOver || won)) {
            restart();
        }

        if (k == KeyEvent.VK_P && started && !gameOver && !won) {
            paused = !paused;
        }

        if (k == KeyEvent.VK_ESCAPE && started && !gameOver && !won) {
            paused = !paused;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int k = e.getKeyCode();

        if (k == KeyEvent.VK_A || k == KeyEvent.VK_LEFT) left = false;
        if (k == KeyEvent.VK_D || k == KeyEvent.VK_RIGHT) right = false;

        if (k == KeyEvent.VK_SPACE ||
                k == KeyEvent.VK_W || k == KeyEvent.VK_UP) {
            jumping = false;
        }

        if (k == KeyEvent.VK_SHIFT) sprint = false;
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("CoinQuest - Adventure Platformer");
            CoinQuest game = new CoinQuest();

            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            frame.add(game);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            game.requestFocusInWindow();
        });
    }
}
