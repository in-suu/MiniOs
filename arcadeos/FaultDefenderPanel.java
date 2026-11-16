package arcadeos;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.swing.*;

/**
 * FAULT DEFENDER - rendering, layout and input.
 *
 * Draws into a fixed 940x620 buffer and blits it with integer nearest-neighbour
 * scaling, exactly like the other cabinets. All layout is derived from the font
 * metrics so nothing clips as the window is resized. Input uses Key Bindings
 * (never KeyListener) so the panel keeps working when focus moves.
 */
public class FaultDefenderPanel extends JPanel {

    private static final int VW = GameState.VW;
    private static final int VH = GameState.VH;

    private static final Font F_HUD     = new Font("Consolas", Font.BOLD, 14);
    private static final Font F_BIG     = new Font("Consolas", Font.BOLD, 20);
    private static final Font F_MID     = new Font("Consolas", Font.BOLD, 13);
    private static final Font F_SM      = new Font("Consolas", Font.BOLD, 12);
    private static final Font F_TINY    = new Font("Consolas", Font.BOLD, 11);
    private static final Font F_BODY    = new Font("Consolas", Font.PLAIN, 12);

    private final FaultDefenderDialog owner;
    private final GameState state;
    private final BufferedImage buffer = new BufferedImage(VW, VH, BufferedImage.TYPE_INT_ARGB);
    private final Timer timer;

    // Screen <-> virtual mapping captured during paint, used to hit-test clicks.
    private double lastScale = 1.0;
    private int lastDx = 0, lastDy = 0;
    private final List<int[]> hitButtons = new ArrayList<>();   // {x,y,w,h,id}

    private int shakeX = 0, shakeY = 0;

    // Slow scrolling starfield behind the playfield
    private static final int STAR_N = 76;
    private final float[] starX = new float[STAR_N];
    private final float[] starY = new float[STAR_N];
    private final float[] starSpd = new float[STAR_N];
    private final int[] starSz = new int[STAR_N];
    private final Color[] starCol = new Color[STAR_N];
    private float starScroll = 0f;

    public FaultDefenderPanel(FaultDefenderDialog owner, GameState state) {
        this.owner = owner;
        this.state = state;
        setBackground(new Color(6, 4, 14));
        setFocusable(true);
        setLayout(null);
        initStars();

        setupInput();

        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                onClick(e);
            }
        });

        timer = new Timer(16, e -> {
            state.update();
            if (state.shake > 0) {
                shakeX = (int) (Math.random() * state.shake * 2 - state.shake);
                shakeY = (int) (Math.random() * state.shake * 2 - state.shake);
            } else {
                shakeX = shakeY = 0;
            }
            repaint();
        });
        timer.start();
    }

    public void stop() {
        timer.stop();
    }

    public void requestFocusNow() {
        requestFocusInWindow();
    }

    // =====================================================================
    // INPUT
    // =====================================================================
    private void setupInput() {
        InputMap im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();

        hold(im, am, KeyEvent.VK_A, -1);
        hold(im, am, KeyEvent.VK_LEFT, -1);
        hold(im, am, KeyEvent.VK_D, 1);
        hold(im, am, KeyEvent.VK_RIGHT, 1);

        press(im, am, KeyEvent.VK_SPACE, "SPACE", () -> {
            if (state.advanceCallout()) { FdSfx.uiClick(); return; }
            if (state.phase == GameState.Phase.REPORT) { nextLevel(); return; }
            if (state.phase == GameState.Phase.GAME_OVER || state.phase == GameState.Phase.VICTORY) {
                playAgain();
                return;
            }
            state.setFiring(true);
            state.fireNow();
        });
        release(im, am, KeyEvent.VK_SPACE, "SPACE-UP", () -> state.setFiring(false));

        press(im, am, KeyEvent.VK_P, "PAUSE", () -> { state.paused = !state.paused; FdSfx.uiClick(); });
        press(im, am, KeyEvent.VK_R, "RESTART", this::playAgain);
        press(im, am, KeyEvent.VK_M, "MUTE", () -> FdSfx.toggleMute());
        press(im, am, KeyEvent.VK_ESCAPE, "EXIT", () -> owner.requestExit());
        press(im, am, KeyEvent.VK_ENTER, "ENTER", () -> {
            if (state.phase == GameState.Phase.REPORT) nextLevel();
            else if (state.phase == GameState.Phase.GAME_OVER || state.phase == GameState.Phase.VICTORY) playAgain();
        });
    }

    private void hold(InputMap im, ActionMap am, int key, int dir) {
        im.put(KeyStroke.getKeyStroke(key, 0, false), "down-" + key);
        am.put("down-" + key, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { state.setMoveDir(dir); }
        });
        im.put(KeyStroke.getKeyStroke(key, 0, true), "up-" + key);
        am.put("up-" + key, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (state != null) state.setMoveDir(0);
            }
        });
    }

    private void press(InputMap im, ActionMap am, int key, String name, Runnable r) {
        im.put(KeyStroke.getKeyStroke(key, 0), name);
        am.put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { r.run(); }
        });
    }

    private void release(InputMap im, ActionMap am, int key, String name, Runnable r) {
        im.put(KeyStroke.getKeyStroke(key, 0, true), name);
        am.put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { r.run(); }
        });
    }

    private void nextLevel() {
        state.nextLevel();
        owner.syncAfterWave(state);
    }

    private void playAgain() {
        state.restartRun();
        owner.syncAfterWave(state);
    }

    private void onClick(MouseEvent e) {
        requestFocusInWindow();
        if (lastScale <= 0) return;
        int vx = (int) ((e.getX() - lastDx) / lastScale);
        int vy = (int) ((e.getY() - lastDy) / lastScale);
        for (int[] b : hitButtons) {
            if (vx >= b[0] && vx <= b[0] + b[2] && vy >= b[1] && vy <= b[1] + b[3]) {
                FdSfx.uiClick();
                switch (b[4]) {
                    case 1: nextLevel(); return;
                    case 2: playAgain(); return;
                    case 3: owner.requestExit(); return;
                    default: return;
                }
            }
        }
        if (state.paused) state.paused = false;
    }

    // =====================================================================
    // PAINT
    // =====================================================================
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D vg = buffer.createGraphics();
        vg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        vg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        render(vg);
        vg.dispose();

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int panelW = getWidth(), panelH = getHeight();
        g2.setColor(getBackground());
        g2.fillRect(0, 0, panelW, panelH);
        double raw = Math.min(panelW / (double) VW, panelH / (double) VH);
        double s = raw >= 1.0 ? Math.floor(raw) : raw;
        lastScale = s;
        int dw = (int) Math.round(VW * s);
        int dh = (int) Math.round(VH * s);
        lastDx = (panelW - dw) / 2 + shakeX;
        lastDy = (panelH - dh) / 2 + shakeY;
        g2.drawImage(buffer, lastDx, lastDy, dw, dh, null);
        g2.dispose();
    }

    private void initStars() {
        Random r = new Random(20260505L);
        for (int i = 0; i < STAR_N; i++) {
            starX[i] = r.nextInt(VW);
            starY[i] = GameState.FIELD_TOP + r.nextFloat() * (GameState.ZONE_TOP - GameState.FIELD_TOP);
            starSpd[i] = 0.25f + r.nextFloat() * 0.9f;
            starSz[i] = r.nextInt(3) == 0 ? 2 : 1;
            int b = 120 + r.nextInt(110);
            int tint = r.nextInt(3);
            Color c = tint == 0 ? new Color(b, b, Math.min(255, b + 30))
                    : tint == 1 ? new Color(Math.min(255, b + 20), b, Math.min(255, b + 20))
                    : new Color(Math.min(255, b + 10), Math.min(255, b + 20), 255);
            starCol[i] = c;
        }
    }

    private void renderStars(Graphics2D g2) {
        int top = GameState.FIELD_TOP;
        int h = GameState.ZONE_TOP - GameState.FIELD_TOP;
        starScroll += 0.6f;
        for (int i = 0; i < STAR_N; i++) {
            float y = top + ((starY[i] - top + starScroll * starSpd[i]) % h + h) % h;
            int a = (int) (90 + 130 * (starSpd[i] / 1.15f));
            g2.setColor(new Color(starCol[i].getRed(), starCol[i].getGreen(), starCol[i].getBlue(),
                    Math.min(220, a)));
            g2.fillRect((int) starX[i], (int) y, starSz[i], starSz[i]);
        }
    }

    private void render(Graphics2D g2) {
        g2.setColor(new Color(10, 8, 22));
        g2.fillRect(0, 0, VW, VH);
        // slow starfield
        renderStars(g2);
        // faint CRT grid on top of the stars
        g2.setColor(new Color(20, 15, 40));
        for (int x = 0; x < VW; x += 40) g2.drawLine(x, GameState.FIELD_TOP, x, GameState.ZONE_TOP);
        for (int y = GameState.FIELD_TOP; y < GameState.ZONE_TOP; y += 40) g2.drawLine(0, y, VW, y);

        renderZones(g2);
        renderLimitFences(g2);
        renderFaultLine(g2);
        renderPhysicalStrip(g2);
        renderFormula(g2);
        renderEntities(g2);
        renderHud(g2);
        renderOverlays(g2);
    }

    // ── Zones ─────────────────────────────────────────────────────────────────

    /**
     * Dashed "limit fence": one vertical line rising from each zone's right
     * (limit) edge through the whole playfield, in that segment's colour, so the
     * player can judge an offset against the boundary without doing arithmetic.
     */
    private void renderLimitFences(Graphics2D g2) {
        int y0 = GameState.FIELD_TOP;
        int y1 = GameState.ZONE_TOP;
        for (GameState.Zone z : state.zones) {
            int x = z.right;
            Color c = z.seg.color;
            g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 44));
            g2.fillRect(x - 3, y0, 7, y1 - y0);
            g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 175));
            for (int y = y0; y < y1; y += 16) {
                g2.fillRect(x, y, 2, 10);
            }
            g2.setColor(brighten(c, 1.3f));
            g2.fillRect(x - 3, y1 - 7, 8, 7);
        }
        // fence guarding the UNMAPPED band
        int vx = GameState.AXIS_X1 - GameState.VOID_W;
        g2.setColor(new Color(160, 130, 220, 44));
        g2.fillRect(vx - 3, y0, 7, y1 - y0);
        g2.setColor(new Color(170, 140, 230, 175));
        for (int y = y0; y < y1; y += 16) {
            g2.fillRect(vx, y, 2, 10);
        }
    }

    /** Glowing tripwire above the zones; flashes pink when an illegal ship dives past it. */
    private void renderFaultLine(Graphics2D g2) {
        int y = GameState.FAULT_LINE_Y;
        int x0 = GameState.AXIS_X0 - 10, x1 = GameState.AXIS_X1 + 6;
        float f = state.faultLineFlash;
        int thick = 2 + (int) (7 * f);
        g2.setColor(new Color(255, 60, 110, 40 + (int) (170 * f)));
        g2.fillRect(x0, y - thick / 2, x1 - x0, thick);
        g2.setColor(new Color(255, 120, 165, (int) (90 + 165 * f)));
        for (int x = x0; x < x1; x += 18) {
            g2.fillRect(x, y, 10, 1);
        }
        if (f > 0.03f) {
            g2.setFont(F_SM);
            g2.setColor(new Color(255, 225, 238, (int) (255 * f)));
            g2.drawString("FAULT LINE", x0 + 4, y - 8);
        }
    }

    private void renderZones(Graphics2D g2) {
        int top = GameState.ZONE_TOP;
        int h = GameState.ZONE_H;

        for (GameState.Zone z : state.zones) {
            int w = z.right - z.left;
            g2.setColor(darken(z.seg.color, 0.28f));
            g2.fillRect(z.left, top, w, h);
            // base edge accent
            g2.setColor(z.seg.color);
            g2.fillRect(z.left, top, 3, h);
            g2.setColor(brighten(z.seg.color, 1.15f));
            g2.drawRect(z.left, top, w - 1, h - 1);

            if (z.seg.readOnly) {
                // striped border + shield: a write here is a protection fault
                g2.setColor(new Color(255, 255, 255, 60));
                for (int sx = z.left + 2; sx < z.right - 2; sx += 8) {
                    g2.drawLine(sx, top + 2, sx + 4, top + 6);
                    g2.drawLine(sx, top + h - 7, sx + 4, top + h - 3);
                }
                ImageIcon shield = new ImageIcon(
                        PixelSprites.createShieldSprite(12, RetroTheme.ACCENT_YELLOW));
                g2.drawImage(shield.getImage(), z.right - 16, top + 3, null);
            }

            if (z.flash > 0) {
                g2.setColor(new Color(255, 255, 255, (int) (z.flash * 150)));
                g2.fillRect(z.left, top, w, h);
            }

            // name label under the zone: larger and higher contrast, on a dark backing
            g2.setFont(F_MID);
            FontMetrics fm = g2.getFontMetrics();
            String name = z.seg.name;
            if (fm.stringWidth(name) <= w + 20) {
                int nx = z.left + (w - fm.stringWidth(name)) / 2;
                g2.setColor(new Color(6, 5, 14, 215));
                g2.fillRect(nx - 5, top + h + 3, fm.stringWidth(name) + 10, fm.getHeight());
                g2.setColor(Color.WHITE);
                g2.drawString(name, nx, top + h + 17);
            }
            // base / limit readout sits inside the zone so it never crowds the caption row
            if (w >= 92) {
                g2.setFont(F_TINY);
                FontMetrics fmb = g2.getFontMetrics();
                String bl = "B" + z.seg.base + "  L" + z.seg.limit + (z.seg.readOnly ? "  RO" : "");
                g2.setColor(new Color(255, 255, 255, 205));
                g2.drawString(bl, z.left + (w - fmb.stringWidth(bl)) / 2, top + h / 2 + 11);
            }
        }

        // Over-limit bands: the red hatched gutter past each zone's right edge.
        for (GameState.Zone z : state.zones) {
            int bx = z.overLeft, bw = Math.max(10, z.overRight - z.overLeft);
            g2.setColor(new Color(112, 20, 42));
            g2.fillRect(bx, top, bw, h);
            g2.setColor(new Color(255, 96, 124, 205));
            for (int y = top + 2; y < top + h; y += 6) {
                g2.drawLine(bx, y, bx + bw - 1, y);
            }
            g2.setColor(new Color(255, 150, 170));
            g2.drawRect(bx, top, bw - 1, h - 1);
            g2.drawRect(bx + 1, top + 1, bw - 3, h - 3);
            g2.setFont(F_TINY);
            g2.setColor(new Color(255, 190, 205));
            String of = ">lim";
            g2.drawString(of, bx + (bw - g2.getFontMetrics().stringWidth(of)) / 2, top + h / 2 + 8);
        }

        // UNMAPPED void band (invalid selectors fall here).
        int vx = GameState.AXIS_X1 - GameState.VOID_W;
        g2.setColor(new Color(38, 30, 58));
        g2.fillRect(vx, top, GameState.VOID_W, h);
        g2.setColor(new Color(150, 120, 200, 140));
        for (int y = top + 2; y < top + h; y += 7) g2.drawLine(vx + 2, y, vx + GameState.VOID_W - 3, y);
        g2.setColor(new Color(150, 120, 200));
        g2.drawRect(vx, top, GameState.VOID_W - 1, h - 1);
        g2.setFont(F_TINY);
        g2.setColor(new Color(190, 170, 230));
        g2.drawString("UNMAPPED", vx - 4, top + h + 14);

        // Player rail
        g2.setColor(new Color(70, 55, 110));
        g2.drawLine(GameState.AXIS_X0 - 14, GameState.RAIL_Y + 14, GameState.AXIS_X1 + 10, GameState.RAIL_Y + 14);
    }

    // ── Physical memory strip ─────────────────────────────────────────────────

    private void renderPhysicalStrip(Graphics2D g2) {
        int x0 = GameState.AXIS_X0, x1 = GameState.AXIS_X1;
        int y = GameState.MAP_TOP;
        g2.setColor(new Color(16, 12, 32));
        g2.fillRect(x0, y, x1 - x0, GameState.MAP_H);
        g2.setColor(new Color(60, 45, 100));
        g2.drawRect(x0, y, x1 - x0 - 1, GameState.MAP_H - 1);
        g2.setFont(F_TINY);
        g2.setColor(RetroTheme.TEXT_DIM);
        g2.drawString("PHYSICAL MEMORY", x0 + 6, y + GameState.MAP_H + 16);

        int span = state.physicalSpan();
        int labelIdx = -1;
        for (int i = 0; i < state.markers.size(); i++) {
            GameState.Marker m = state.markers.get(i);
            float t = m.pa / (float) span;
            int mx = x0 + 4 + (int) (t * (x1 - x0 - 10));
            int alpha = (int) (175 + 80 * Math.max(0f, m.life));
            g2.setColor(new Color(m.color.getRed(), m.color.getGreen(), m.color.getBlue(),
                    Math.min(255, alpha)));
            g2.fillRect(mx - 3, y + 3, 6, GameState.MAP_H - 6);
            g2.setColor(brighten(m.color, 1.45f));
            g2.fillRect(mx - 3, y + 3, 6, 2);
            if (m.life > 0.88f) labelIdx = i;   // only the freshest marker gets a "PA" label
        }

        // brief "PA ####" readout on the marker that just landed
        if (labelIdx >= 0) {
            GameState.Marker m = state.markers.get(labelIdx);
            float t = m.pa / (float) span;
            int mx = x0 + 4 + (int) (t * (x1 - x0 - 10));
            String txt = "PA " + m.pa;
            g2.setFont(F_SM);
            FontMetrics fm = g2.getFontMetrics();
            int tx = Math.max(x0 + 2, Math.min(x1 - fm.stringWidth(txt) - 2, mx - fm.stringWidth(txt) / 2));
            int ty = y + GameState.MAP_H / 2;
            g2.setColor(new Color(6, 5, 14));
            g2.fillRect(tx - 4, ty - 8, fm.stringWidth(txt) + 8, 16);
            g2.setColor(brighten(m.color, 1.5f));
            g2.drawRect(tx - 4, ty - 8, fm.stringWidth(txt) + 7, 15);
            g2.drawString(txt, tx, ty + 4);
        }
    }

    private void renderFormula(Graphics2D g2) {
        g2.setFont(F_TINY);
        FontMetrics fm = g2.getFontMetrics();
        String s = "offset < limit  ->  PA = base + offset        offset >= limit  ->  SEGFAULT"
                + "        write to [RO]  ->  PROTECTION FAULT";
        g2.setColor(new Color(200, 190, 230));
        g2.drawString(s, (VW - fm.stringWidth(s)) / 2, GameState.FORMULA_Y);
    }

    // ── Entities ──────────────────────────────────────────────────────────────

    private void renderEntities(Graphics2D g2) {
        // translation beams: zone -> physical memory strip
        int span = state.physicalSpan();
        int beamTop = GameState.ZONE_TOP + 8;
        int beamBot = GameState.MAP_TOP + GameState.MAP_H / 2;
        for (GameState.Beam b : state.beams) {
            float life = Math.max(0f, b.life);
            int a = (int) (life * 235);
            float t = b.pa / (float) span;
            int xTop = (int) b.fromX;
            int xBot = GameState.AXIS_X0 + 4 + (int) (t * (GameState.AXIS_X1 - GameState.AXIS_X0 - 10));
            Color c = b.color;
            g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.min(255, a)));
            g2.setStroke(new BasicStroke(1f + 3f * life));
            g2.drawLine(xTop, beamTop, xBot, beamBot);
            g2.setStroke(new BasicStroke(1f));
            float k = 1f - life;
            int sx = (int) (xTop + (xBot - xTop) * k);
            int sy = (int) (beamTop + (beamBot - beamTop) * k);
            g2.setColor(new Color(255, 255, 255, Math.min(255, a)));
            g2.fillRect(sx - 3, sy - 3, 6, 6);
        }

        // ships
        for (GameState.Ship s : state.ships) {
            drawShip(g2, s);
        }
        // edge lens bubbles float above the field
        for (GameState.Ship s : state.ships) {
            if (s.alive && s.lens) drawLens(g2, s);
        }
        // bullets: bright core with a short trail
        for (GameState.Bullet b : state.bullets) {
            g2.setColor(new Color(255, 200, 90, 90));
            g2.fillRect((int) b.x - 2, (int) b.y - 12, 5, 16);
            g2.setColor(RetroTheme.ACCENT_YELLOW);
            g2.fillRect((int) b.x - 1, (int) b.y - 7, 3, 12);
            g2.setColor(Color.WHITE);
            g2.fillRect((int) b.x, (int) b.y - 6, 1, 5);
        }
        // power-ups
        for (GameState.PowerUp p : state.powerups) {
            drawPowerUp(g2, p);
        }
        // particles
        for (GameState.Particle p : state.particles) {
            int a = (int) (Math.max(0f, p.life) * 220);
            g2.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), Math.min(255, a)));
            g2.fillRect((int) p.x, (int) p.y, 3, 3);
        }
        // boss
        if (state.level != null && state.level.mode == LevelDef.Mode.BOSS && state.phase == GameState.Phase.PLAYING) {
            drawBoss(g2);
        }
        // player
        drawPlayer(g2);
        // floating text
        g2.setFont(F_TINY);
        for (GameState.FloatText f : state.floats) {
            int a = (int) (Math.max(0f, f.life) * 255);
            g2.setColor(new Color(f.color.getRed(), f.color.getGreen(), f.color.getBlue(), Math.min(255, a)));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(f.text, (int) f.x - fm.stringWidth(f.text) / 2, (int) f.y);
        }
    }

    private void drawShip(Graphics2D g2, GameState.Ship s) {
        int w = GameState.SHIP_W, h = GameState.SHIP_H;
        int x = (int) s.x - w / 2;
        int y = (int) s.y - h / 2;

        Composite oldComposite = g2.getComposite();
        if (s.ghost) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
        }

        if (s.special) {
            g2.setColor(new Color(255, 255, 255, 80));
            g2.fillRoundRect(x - 7, y - 7, w + 14, h + 14, 14, 14);
        }

        // spiky outline: a write request is the one that can trip a protection fault
        if (s.write) {
            g2.setColor(RetroTheme.ACCENT_YELLOW);
            for (int k = 0, sx = x + 5; sx < x + w - 5; k++, sx += 11) {
                int off = (k % 2 == 0) ? 0 : 3;
                g2.drawLine(sx, y + off, sx + 6, y - 9);
                g2.drawLine(sx + 6, y - 9, sx + 12, y + off);
                g2.drawLine(sx, y + h - off, sx + 6, y + h + 9);
                g2.drawLine(sx + 6, y + h + 9, sx + 12, y + h - off);
            }
            for (int k = 0, sy = y + 5; sy < y + h - 5; k++, sy += 11) {
                int off = (k % 2 == 0) ? 0 : 3;
                g2.drawLine(x + off, sy, x - 9, sy + 6);
                g2.drawLine(x - 9, sy + 6, x + off, sy + 12);
                g2.drawLine(x + w - off, sy, x + w + 9, sy + 6);
                g2.drawLine(x + w + 9, sy + 6, x + w - off, sy + 12);
            }
        }

        g2.setColor(darken(s.color, 0.75f));
        g2.fillRoundRect(x, y, w, h, 8, 8);
        g2.setColor(s.color);
        g2.drawRoundRect(x, y, w - 1, h - 1, 8, 8);
        // little hull notch
        g2.fillRect(x + w / 2 - 3, y + h - 5, 6, 9);

        if (s.illegal()) {
            g2.setColor(RetroTheme.ACCENT_PINK);
            g2.drawRoundRect(x - 4, y - 4, w + 7, h + 7, 10, 10);
        }

        // edge ship: gold outline - its offset sits right on the limit boundary
        if (s.edge) {
            g2.setColor(new Color(255, 205, 60));
            g2.drawRoundRect(x - 2, y - 2, w + 3, h + 3, 10, 10);
            g2.setColor(new Color(255, 236, 150, 160));
            g2.drawRoundRect(x - 5, y - 5, w + 9, h + 9, 12, 12);
        }

        // ghost outline: this request names a segment that is not mapped at all
        if (s.ghost) {
            g2.setColor(new Color(190, 160, 245));
            g2.drawRoundRect(x - 8, y - 8, w + 15, h + 15, 12, 12);
            for (int k = 0, sy = y - 6; sy < y + h + 6; k++, sy += 9) {
                int wob = (k % 2 == 0) ? 0 : 2;
                g2.drawLine(x - 11 - wob, sy, x - 11 - wob, sy + 5);
                g2.drawLine(x + w + 11 + wob, sy, x + w + 11 + wob, sy + 5);
            }
        }

        // the "sN+offset" tag only shows during the tutorial or very close to the MMU
        boolean showTag = state.phase == GameState.Phase.TUTORIAL
                || s.y > GameState.RAIL_Y - 150;
        if (showTag) {
            g2.setFont(F_SM);
            FontMetrics fm = g2.getFontMetrics();
            String tag = (s.segIndex < 0) ? ("seg " + s.segNumber + "?") : ("s" + s.segNumber + "+" + s.offset);
            g2.setColor(Color.BLACK);
            g2.fillRect((int) s.x - fm.stringWidth(tag) / 2 - 3, y - 18, fm.stringWidth(tag) + 6, 15);
            g2.setColor(Color.WHITE);
            g2.drawString(tag, (int) s.x - fm.stringWidth(tag) / 2, y - 6);
        }

        // R / W badge ("?" for a request aimed at a segment that does not exist)
        String badge = s.ghost ? "?" : (s.write ? "W" : "R");
        int bw = 18, bh = 16;
        int bx = x + w - bw - 2, by = y + 3;
        g2.setColor(Color.BLACK);
        g2.fillRect(bx, by, bw, bh);
        g2.setColor(s.ghost ? new Color(200, 175, 245)
                : (s.write ? RetroTheme.ACCENT_YELLOW : RetroTheme.ACCENT_CYAN));
        g2.drawRect(bx, by, bw - 1, bh - 1);
        g2.setFont(F_SM);
        FontMetrics bfm = g2.getFontMetrics();
        g2.drawString(badge, bx + (bw - bfm.stringWidth(badge)) / 2, by + bh - 4);

        // adaptive-difficulty spotlight: pulsing ring marking the request to study
        if (s.spotlight) {
            float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 200.0));
            int r = Math.max(w, h) - 4;
            int a = (int) (110 + 120 * pulse);
            g2.setStroke(new BasicStroke(2f + 1.5f * pulse));
            g2.setColor(new Color(140, 240, 255, Math.min(255, a)));
            g2.drawOval((int) s.x - r, (int) s.y - r, r * 2, r * 2);
            g2.setStroke(new BasicStroke(1f));
        }

        g2.setComposite(oldComposite);
    }

    /**
     * The "lens bubble": magnifies a segment's limit for an edge ship that is
     * almost at the fault line. A small ruler ends in a thick "L" tick at the
     * limit; the ship's pip sits one notch before the tick when the offset is
     * limit-1 (legal) or right on it when it is past the wall (illegal), so the
     * off-by-one answer is read off the picture instead of counted.
     */
    private void drawLens(Graphics2D g2, GameState.Ship s) {
        boolean legal = !s.illegal();
        Color accent = legal ? RetroTheme.ACCENT_GREEN : RetroTheme.ACCENT_PINK;

        final int bw = 152, bh = 62;
        int bx = (int) s.x - bw / 2;
        int by = (int) s.y - GameState.SHIP_H / 2 - bh - 10;
        bx = Math.max(6, Math.min(VW - bw - 6, bx));
        by = Math.max(GameState.FIELD_TOP + 2, by);

        // bubble body + pointer down to the ship
        g2.setColor(new Color(12, 10, 26, 234));
        g2.fillRoundRect(bx, by, bw, bh, 10, 10);
        int tipX = Math.max(bx + 8, Math.min(bx + bw - 20, (int) s.x - 6));
        g2.fillPolygon(new int[]{tipX, tipX + 12, tipX + 6},
                new int[]{by + bh, by + bh, by + bh + 8}, 3);
        g2.setColor(new Color(255, 210, 80));
        g2.drawRoundRect(bx, by, bw, bh, 10, 10);

        g2.setFont(F_TINY);
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(new Color(255, 228, 150));
        g2.drawString("LIMIT LENS", bx + 8, by + 13);
        String num = "off " + s.offset + " / " + s.limit;
        g2.setColor(RetroTheme.TEXT_WHITE);
        g2.drawString(num, bx + bw - 8 - fm.stringWidth(num), by + 13);

        // ruler: notches for offsets limit-4 .. limit; the last is the thick "L" tick
        final int step = 20;
        final int notches = 5;
        int rY = by + 36;
        int baseX = bx + 18;
        g2.setColor(new Color(110, 100, 150));
        g2.fillRect(baseX, rY, step * (notches - 1), 2);
        for (int i = 0; i < notches; i++) {
            int px = baseX + i * step;
            int off = s.limit - (notches - 1) + i;
            if (off == s.limit) {
                g2.setColor(new Color(255, 242, 185));
                g2.fillRect(px - 2, rY - 12, 5, 24);
                g2.setFont(F_TINY);
                g2.drawString("L", px - 4, rY - 14);
            } else {
                g2.setColor(new Color(150, 140, 190));
                g2.fillRect(px, rY - 5, 2, 10);
            }
        }

        // pip: at the ship's own offset, clamped to the L tick for anything past it
        int pipIdx = Math.min(s.offset, s.limit) - (s.limit - (notches - 1));
        pipIdx = Math.max(0, Math.min(notches - 1, pipIdx));
        int px = baseX + pipIdx * step;
        g2.setColor(accent);
        g2.fillOval(px - 4, rY - 4, 8, 8);
        g2.setColor(Color.WHITE);
        g2.drawOval(px - 4, rY - 4, 8, 8);

        // verdict
        g2.setColor(accent);
        String verdict = legal ? "LEGAL - let it land" : "ILLEGAL - shoot it!";
        g2.drawString(verdict, bx + 8, by + bh - 6);
    }

    private void drawPowerUp(Graphics2D g2, GameState.PowerUp p) {
        int x = (int) p.x - 12, y = (int) p.y - 12;
        Color c;
        String ch;
        switch (p.type) {
            case SLOWMO:   c = RetroTheme.ACCENT_CYAN;   ch = "S"; break;
            case RELOCATE: c = RetroTheme.ACCENT_PURPLE; ch = "R"; break;
            default:       c = RetroTheme.ACCENT_GREEN;  ch = "C"; break;
        }
        g2.setColor(new Color(255, 255, 255, 60));
        g2.fillOval(x - 4, y - 4, 32, 32);
        g2.setColor(c);
        g2.fillRoundRect(x, y, 24, 24, 8, 8);
        g2.setColor(Color.BLACK);
        g2.setFont(F_MID);
        g2.drawString(ch, x + 8, y + 17);
    }

    private void drawPlayer(Graphics2D g2) {
        int w = GameState.PLAYER_W, h = GameState.PLAYER_H;
        int cx = (int) state.playerX;
        int x = cx - w / 2;
        int y = GameState.RAIL_Y - h / 2;

        // side fins
        g2.setColor(new Color(30, 20, 60));
        g2.fillRect(x - 7, y + 7, 9, h - 9);
        g2.fillRect(x + w - 2, y + 7, 9, h - 9);
        g2.setColor(new Color(96, 74, 158));
        g2.drawRect(x - 7, y + 7, 8, h - 10);
        g2.drawRect(x + w - 2, y + 7, 8, h - 10);

        // hull
        g2.setColor(new Color(40, 26, 74));
        g2.fillRoundRect(x, y, w, h, 10, 10);
        g2.setColor(RetroTheme.ACCENT_CYAN);
        g2.drawRoundRect(x, y, w - 1, h - 1, 10, 10);
        // cockpit
        g2.setColor(new Color(120, 230, 245, 200));
        g2.fillRect(x + w / 2 - 9, y + 5, 18, 9);

        // cannon
        g2.setColor(RetroTheme.ACCENT_YELLOW);
        g2.fillRect(cx - 3, y - 12, 6, 16);
        // muzzle flash
        if (state.muzzleTicks > 0) {
            int f = state.muzzleTicks;
            g2.setColor(new Color(255, 250, 210, 90 + 20 * f));
            g2.fillOval(cx - 16, y - 26, 32, 24);
            g2.setColor(Color.WHITE);
            g2.fillRect(cx - 2, y - 22, 4, 12);
            g2.setColor(new Color(255, 210, 90));
            g2.drawLine(cx - 14, y - 17, cx + 14, y - 17);
        }
        g2.setFont(F_TINY);
        g2.setColor(RetroTheme.ACCENT_CYAN);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString("MMU", cx - fm.stringWidth("MMU") / 2, y + h - 6);
        if (state.slowing) {
            g2.setColor(new Color(5, 217, 232, 90));
            g2.drawOval(cx - 36, y - 16, 72, 38);
        }
    }

    private void drawBoss(Graphics2D g2) {
        int w = 150, h = 60;
        int x = (int) state.bossX - w / 2;
        int y = (int) state.bossY - h / 2;
        g2.setColor(new Color(90, 16, 40));
        g2.fillRoundRect(x, y, w, h, 12, 12);
        g2.setColor(RetroTheme.ACCENT_PINK);
        g2.drawRoundRect(x, y, w - 1, h - 1, 12, 12);
        g2.setFont(F_MID);
        g2.setColor(RetroTheme.ACCENT_YELLOW);
        String t = "BUFFER OVERFLOW";
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(t, (int) state.bossX - fm.stringWidth(t) / 2, y + 26);
        // health bar
        int bw = w - 20;
        g2.setColor(new Color(40, 20, 30));
        g2.fillRect(x + 10, y + h - 16, bw, 8);
        g2.setColor(RetroTheme.ACCENT_PINK);
        int filled = state.bossMaxHp == 0 ? 0 : (int) (bw * (state.bossHp / (float) state.bossMaxHp));
        g2.fillRect(x + 10, y + h - 16, filled, 8);
    }

    // ── HUD ───────────────────────────────────────────────────────────────────

    private void renderHud(Graphics2D g2) {
        g2.setColor(new Color(16, 11, 30));
        g2.fillRect(0, 0, VW, GameState.HUD_H);
        g2.setColor(new Color(60, 45, 100));
        g2.drawLine(0, GameState.HUD_H - 1, VW, GameState.HUD_H - 1);

        // Score
        g2.setFont(F_TINY);
        g2.setColor(RetroTheme.TEXT_DIM);
        g2.drawString("SCORE", 26, 22);
        g2.setFont(F_BIG);
        g2.setColor(RetroTheme.ACCENT_CYAN);
        g2.drawString(String.format("%06d", state.score), 26, 46);

        // Level
        g2.setFont(F_MID);
        g2.setColor(RetroTheme.ACCENT_YELLOW);
        String lv = "LEVEL " + (state.levelIndex + 1);
        g2.drawString(lv, 250, 24);
        g2.setFont(F_TINY);
        g2.setColor(RetroTheme.TEXT_WHITE);
        g2.drawString(state.level == null ? "" : state.level.name, 250, 42);

        // Lives
        int hx = 600;
        g2.setFont(F_TINY);
        g2.setColor(RetroTheme.TEXT_DIM);
        g2.drawString("LIVES", hx, 22);
        for (int i = 0; i < 3; i++) {
            drawHeart(g2, hx + i * 24, 30, i < state.lives);
        }

        // Combo (flashes on every hit / landing)
        g2.setFont(F_TINY);
        g2.setColor(RetroTheme.TEXT_DIM);
        g2.drawString("COMBO", 720, 22);
        float mult = state.comboMult();
        float cf = state.comboFlash;
        if (cf > 0f) {
            g2.setColor(new Color(255, 255, 255, (int) (70 * cf)));
            g2.fillRect(712, 12, 96, 46);
        }
        g2.setFont(cf > 0.4f ? F_BIG : F_MID);
        g2.setColor(mult > 1.0f ? (cf > 0.4f ? RetroTheme.ACCENT_YELLOW : RetroTheme.ACCENT_GREEN)
                : RetroTheme.TEXT_WHITE);
        g2.drawString(String.format("x%.1f", mult), 720, cf > 0.4f ? 46 : 42);
        if (state.combo > 1) {
            g2.setFont(F_TINY);
            g2.setColor(cf > 0.4f ? RetroTheme.ACCENT_YELLOW : RetroTheme.ACCENT_PINK);
            g2.drawString(state.combo + " STREAK", 720, 60);
        }

        // Wave progress bar
        int bx = 810, bw = 104;
        g2.setFont(F_TINY);
        g2.setColor(RetroTheme.TEXT_DIM);
        g2.drawString("WAVE", bx, 22);
        g2.setColor(new Color(40, 30, 62));
        g2.fillRect(bx, 28, bw, 12);
        int target = Math.max(1, state.waveProgressTarget());
        int done = Math.min(target, state.waveProgressSpawned());
        g2.setColor(RetroTheme.ACCENT_PURPLE);
        g2.fillRect(bx, 28, (int) (bw * (done / (float) target)), 12);
        g2.setColor(RetroTheme.BORDER_BRIGHT);
        g2.drawRect(bx, 28, bw - 1, 11);
        g2.setColor(RetroTheme.TEXT_WHITE);
        g2.drawString(done + "/" + target, bx + 6, 38);

        // Mute
        if (FdSfx.isMuted()) {
            g2.setFont(F_TINY);
            g2.setColor(RetroTheme.ACCENT_PINK);
            g2.drawString("[M] MUTED", 26, 62);
        }

        // Popup banner
        if (state.popup != null) {
            g2.setFont(F_TINY);
            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(state.popup);
            int px = Math.max(14, (VW - tw) / 2);
            g2.setColor(new Color(12, 8, 24, 220));
            g2.fillRoundRect(px - 12, 56, tw + 24, 22, 6, 6);
            g2.setColor(state.popupColor);
            g2.drawRoundRect(px - 12, 56, tw + 24, 22, 6, 6);
            g2.drawString(state.popup, px, 72);
        }
    }

    private void drawHeart(Graphics2D g2, int x, int y, boolean filled) {
        Color c = filled ? RetroTheme.ACCENT_PINK : new Color(60, 40, 60);
        g2.setColor(c);
        g2.fillRect(x + 2, y, 4, 2);
        g2.fillRect(x + 10, y, 4, 2);
        g2.fillRect(x, y + 2, 16, 5);
        g2.fillRect(x + 2, y + 7, 12, 3);
        g2.fillRect(x + 5, y + 10, 6, 3);
        g2.fillRect(x + 7, y + 13, 2, 2);
    }

    // ── Overlays ──────────────────────────────────────────────────────────────

    private void renderOverlays(Graphics2D g2) {
        hitButtons.clear();
        if (state.paused) {
            dim(g2, 150);
            centered(g2, "PAUSED", VH / 2 - 20, F_BIG, RetroTheme.ACCENT_YELLOW);
            centered(g2, "[P] RESUME    [R] RESTART    [ESC] RETURN TO OS", VH / 2 + 14, F_SM, RetroTheme.TEXT_WHITE);
            return;
        }
        if (state.calloutText != null) {
            renderCallout(g2);
            return;
        }
        if (state.phase == GameState.Phase.REPORT) { renderReport(g2, "WAVE CLEARED", RetroTheme.ACCENT_GREEN); return; }
        if (state.phase == GameState.Phase.GAME_OVER) { renderReport(g2, "GAME OVER", RetroTheme.ACCENT_PINK); return; }
        if (state.phase == GameState.Phase.VICTORY) { renderReport(g2, "SYSTEM SECURED", RetroTheme.ACCENT_CYAN); return; }
        if (state.phase == GameState.Phase.TUTORIAL) {
            g2.setFont(F_TINY);
            g2.setColor(RetroTheme.ACCENT_YELLOW);
            g2.drawString("TUTORIAL WAVE - no lives are lost here", 26, GameState.HUD_H + 16);
        }
    }

    private void renderCallout(Graphics2D g2) {
        GameState.Ship focus = state.calloutShip;
        boolean liveFocus = focus != null && focus.alive;

        dim(g2, 175);

        // re-draw the explained ship above the veil and ring it with a pulsing spotlight
        if (liveFocus) {
            drawShip(g2, focus);
            int cx = (int) focus.x, cy = (int) focus.y;
            int r = GameState.SHIP_W;
            float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 260.0));
            int a = (int) (110 + 130 * pulse);
            g2.setStroke(new BasicStroke(2f + 2f * pulse));
            g2.setColor(new Color(255, 255, 255, Math.min(255, a)));
            g2.drawOval(cx - r, cy - r + 8, r * 2, r * 2 - 16);
            g2.setStroke(new BasicStroke(1f));
        }

        g2.setFont(F_MID);
        FontMetrics fm = g2.getFontMetrics();
        int maxW = 760;
        List<String> lines = wrap(state.calloutText, fm, maxW);
        int lh = fm.getHeight();
        int boxW = maxW + 40;
        int boxH = 54 + lines.size() * lh + 34;
        int bx = (VW - boxW) / 2;
        int by = GameState.HUD_H + 14;                 // coach box lives at the top of the screen
        g2.setColor(new Color(18, 12, 36, 245));
        g2.fillRoundRect(bx, by, boxW, boxH, 12, 12);
        g2.setColor(state.calloutColor);
        g2.drawRoundRect(bx, by, boxW, boxH, 12, 12);
        g2.setColor(RetroTheme.ACCENT_YELLOW);
        g2.setFont(F_SM);
        g2.drawString("COACH", bx + 20, by + 24);
        g2.setFont(F_MID);
        g2.setColor(RetroTheme.TEXT_WHITE);
        int ty = by + 24 + lh;
        for (String line : lines) {
            g2.drawString(line, bx + 20, ty);
            ty += lh;
        }
        g2.setFont(F_SM);
        g2.setColor(RetroTheme.ACCENT_CYAN);
        g2.drawString("[SPACE] CONTINUE", bx + 20, by + boxH - 14);
    }

    private void renderReport(Graphics2D g2, String title, Color titleColor) {
        dim(g2, 205);
        GameState.Report r = state.report;
        if (r == null) return;

        int boxW = 720, boxH = 420;
        int bx = (VW - boxW) / 2, by = (VH - boxH) / 2;
        g2.setColor(new Color(18, 12, 36, 250));
        g2.fillRoundRect(bx, by, boxW, boxH, 14, 14);
        g2.setColor(titleColor);
        g2.drawRoundRect(bx, by, boxW, boxH, 14, 14);

        g2.setFont(F_BIG);
        FontMetrics fmBig = g2.getFontMetrics();
        g2.setColor(titleColor);
        g2.drawString(title, bx + (boxW - fmBig.stringWidth(title)) / 2, by + 40);

        g2.setFont(F_TINY);
        g2.setColor(RetroTheme.TEXT_DIM);
        String sub = "LEVEL " + (state.levelIndex + 1) + " - " + (state.level == null ? "" : state.level.name);
        g2.drawString(sub, bx + (boxW - g2.getFontMetrics().stringWidth(sub)) / 2, by + 62);

        // Two columns of stats
        int colL = bx + 40, colR = bx + boxW / 2 + 20;
        int y0 = by + 100;
        int rowH = 34;
        statRow(g2, colL, y0 + 0 * rowH, "SCORE", String.valueOf(r.score), RetroTheme.ACCENT_CYAN);
        statRow(g2, colL, y0 + 1 * rowH, "ACCURACY", r.accuracy + "%", RetroTheme.ACCENT_GREEN);
        statRow(g2, colL, y0 + 2 * rowH, "BEST COMBO", "x" + (1f + Math.min(r.bestCombo, 40) * 0.1f), RetroTheme.ACCENT_YELLOW);
        statRow(g2, colR, y0 + 0 * rowH, "SEGFAULTS", String.valueOf(r.segfaults), RetroTheme.ACCENT_PINK);
        statRow(g2, colR, y0 + 1 * rowH, "PROTECTION FAULTS", String.valueOf(r.protFaults), RetroTheme.ACCENT_YELLOW);
        statRow(g2, colR, y0 + 2 * rowH, "FALSE ALARMS", String.valueOf(r.falseAlarms), RetroTheme.ACCENT_PINK);

        // Accuracy meter
        int mx = bx + 40, my = y0 + 3 * rowH + 14, mw = boxW - 80;
        g2.setFont(F_TINY);
        g2.setColor(RetroTheme.TEXT_DIM);
        g2.drawString("ACCURACY", mx, my - 6);
        g2.setColor(new Color(40, 30, 62));
        g2.fillRect(mx, my, mw, 14);
        g2.setColor(RetroTheme.ACCENT_GREEN);
        g2.fillRect(mx, my, (int) (mw * (r.accuracy / 100.0)), 14);
        g2.setColor(RetroTheme.BORDER_BRIGHT);
        g2.drawRect(mx, my, mw - 1, 13);

        // Rank + tip
        int ry = my + 46;
        g2.setFont(F_BIG);
        g2.setColor(RetroTheme.ACCENT_PURPLE);
        g2.drawString("RANK " + r.rank, mx, ry);
        g2.setFont(F_SM);
        g2.setColor(RetroTheme.TEXT_WHITE);
        g2.drawString("TIP: " + r.tip, mx, ry + 26);

        // Buttons
        int bw = 190, bh = 38, gap = 20;
        int sy = by + boxH - 62;
        boolean canAdvance = state.phase == GameState.Phase.REPORT
                && state.levelIndex + 1 < state.levels.size();
        if (canAdvance) {
            int sx = bx + (boxW - (bw * 3 + gap * 2)) / 2;
            drawButton(g2, sx, sy, bw, bh, "NEXT LEVEL", RetroTheme.ACCENT_GREEN, 1);
            drawButton(g2, sx + bw + gap, sy, bw, bh, "PLAY AGAIN", RetroTheme.ACCENT_CYAN, 2);
            drawButton(g2, sx + 2 * (bw + gap), sy, bw, bh, "RETURN TO OS", RetroTheme.ACCENT_PINK, 3);
        } else {
            int sx = bx + (boxW - (bw * 2 + gap)) / 2;
            drawButton(g2, sx, sy, bw, bh, "PLAY AGAIN", RetroTheme.ACCENT_CYAN, 2);
            drawButton(g2, sx + bw + gap, sy, bw, bh, "RETURN TO OS", RetroTheme.ACCENT_PINK, 3);
        }
    }

    private void statRow(Graphics2D g2, int x, int y, String label, String value, Color valueColor) {
        g2.setFont(F_TINY);
        g2.setColor(RetroTheme.TEXT_DIM);
        g2.drawString(label, x, y);
        g2.setFont(F_MID);
        g2.setColor(valueColor);
        g2.drawString(value, x + 210 - g2.getFontMetrics().stringWidth(value), y);
    }

    private void drawButton(Graphics2D g2, int x, int y, int w, int h, String text, Color c, int id) {
        g2.setColor(new Color(28, 20, 52));
        g2.fillRoundRect(x, y, w, h, 8, 8);
        g2.setColor(c);
        g2.drawRoundRect(x, y, w, h - 1, 8, 8);
        g2.setFont(F_SM);
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(c);
        g2.drawString(text, x + (w - fm.stringWidth(text)) / 2, y + h / 2 + 5);
        hitButtons.add(new int[]{x, y, w, h, id});
    }

    private void dim(Graphics2D g2, int alpha) {
        g2.setColor(new Color(4, 2, 12, alpha));
        g2.fillRect(0, 0, VW, VH);
    }

    private void centered(Graphics2D g2, String s, int y, Font f, Color c) {
        g2.setFont(f);
        g2.setColor(c);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(s, (VW - fm.stringWidth(s)) / 2, y);
    }

    private List<String> wrap(String text, FontMetrics fm, int maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String cand = line.length() == 0 ? word : line + " " + word;
            if (fm.stringWidth(cand) > maxW) {
                if (line.length() > 0) out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(cand);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    private static Color darken(Color c, float f) {
        return new Color(Math.max(0, (int) (c.getRed() * f)),
                Math.max(0, (int) (c.getGreen() * f)),
                Math.max(0, (int) (c.getBlue() * f)));
    }

    private static Color brighten(Color c, float f) {
        return new Color(Math.min(255, (int) (c.getRed() * f)),
                Math.min(255, (int) (c.getGreen() * f)),
                Math.min(255, (int) (c.getBlue() * f)));
    }
}
