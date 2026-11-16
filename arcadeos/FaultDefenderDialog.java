package arcadeos;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.swing.*;

/**
 * FAULT DEFENDER // MEMORY SEGMENTATION - the arcade cabinet.
 *
 * Owns the window chrome (undecorated header, drag handle, control bar, dim
 * veil) and swaps between the attract/menu card and the live game panel. The
 * game is launched with the segment table currently held by the Mission 05
 * simulator so the player's own segments drive level 1 when they are usable;
 * otherwise the built-in teaching table is used.
 */
public class FaultDefenderDialog extends JDialog {

    private static final Font F_TITLE  = new Font("Consolas", Font.BOLD, 13);
    private static final Font F_MENU   = new Font("Consolas", Font.BOLD, 22);
    private static final Font F_SUB    = new Font("Consolas", Font.BOLD, 12);
    private static final Font F_SMALL  = new Font("Consolas", Font.BOLD, 11);

    private final List<SegmentConfig> incoming;

    /** The up-to-4 segment subset the player picked for level 1 (null = built-in table). */
    private List<SegmentConfig> chosen;

    private final List<FdScores.Entry> scores;
    private String bestRank = "-";

    private final CardLayout cards = new CardLayout();
    private final JPanel center = new JPanel(cards);
    private final JLabel statusLabel = new JLabel(" ");

    private final JButton muteButton;
    private Timer menuTimer;

    private GameState state;
    private FaultDefenderPanel gamePanel;
    private ScreenDim dim;
    private boolean awarded = false;

    public FaultDefenderDialog(JFrame parent, List<SegmentConfig> incoming) {
        super(parent, "FAULT DEFENDER // MEMORY SEGMENTATION", true);
        this.incoming = incoming;
        this.chosen = defaultChoice(incoming);
        this.scores = FdScores.load();
        this.bestRank = FdScores.bestRank();

        setUndecorated(true);
        setSize(RetroTheme.getClampedDialogSize(parent, 1010, 762));
        setMinimumSize(new Dimension(900, 660));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLocationRelativeTo(parent);
        getContentPane().setBackground(RetroTheme.BG_DARK);
        setLayout(new BorderLayout());

        center.setBackground(RetroTheme.BG_DARK);
        center.add(createMenu(), "menu");
        center.add(createPicker(), "picker");

        muteButton = RetroTheme.createRetroButton(muteLabel(), RetroTheme.BTN_DARK, RetroTheme.ACCENT_CYAN);
        muteButton.addActionListener(e -> {
            FdSfx.toggleMute();
            muteButton.setText(muteLabel());
            muteButton.setForeground(FdSfx.isMuted() ? RetroTheme.ACCENT_PINK : RetroTheme.ACCENT_CYAN);
        });

        add(createHeader(), BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        add(createBottomBar(), BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { awardXp(); stopGame(); }
            @Override public void windowClosed(WindowEvent e) { stopGame(); }
        });
    }

    /** Convenience launcher that mirrors the other cabinets. */
    public static void launch(JFrame parent, List<SegmentConfig> incoming) {
        FaultDefenderDialog dlg = new FaultDefenderDialog(parent, incoming);
        dlg.setVisible(true);
    }

    // =====================================================================
    // GAME LIFECYCLE
    // =====================================================================
    private void startGame(boolean tutorial) {
        startGame(tutorial, tutorial ? null : chosen);
    }

    private void startGame(boolean tutorial, List<SegmentConfig> custom) {
        if (menuTimer != null) menuTimer.stop();
        stopGame();
        state = new GameState(LevelDef.library(), custom, tutorial);
        state.start();
        gamePanel = new FaultDefenderPanel(this, state);
        center.add(gamePanel, "game");
        cards.show(center, "game");
        center.revalidate();
        center.repaint();
        gamePanel.requestFocusNow();
        updateStatus();
    }

    /**
     * Called by the panel after it advances a level or restarts the run so the
     * header can follow along. Awards nothing: XP is granted once on exit.
     */
    public void syncAfterWave(GameState s) {
        if (s != null) state = s;
        updateStatus();
        if (gamePanel != null) gamePanel.requestFocusNow();
    }

    private void stopGame() {
        if (gamePanel != null) {
            gamePanel.stop();
        }
    }

    /** ESC / X / "Return to OS": bank the XP for the final rank, then close. */
    public void requestExit() {
        awardXp();
        stopGame();
        dispose();
    }

    private void awardXp() {
        if (awarded || state == null) return;
        int correct = state.translatedLegal + state.shotIllegal;
        int total = correct + state.falseAlarms + state.segfaults + state.protFaults;
        if (state.report == null && total == 0) return;   // nothing was played
        awarded = true;
        int acc = total == 0 ? 100 : (int) Math.round(correct * 100.0 / total);
        String rank = state.report != null ? state.report.rank : GameState.rankFor(acc);
        int xp = xpForRank(rank);
        if (xp > 0) AppState.addScore(xp);
        // persist the run: local high-score table + best rank
        bestRank = FdScores.record(state.score, rank, FdScores.today());
        scores.clear();
        scores.addAll(FdScores.load());
    }

    private static int xpForRank(String rank) {
        if ("S".equals(rank)) return 400;
        if ("A".equals(rank)) return 300;
        if ("B".equals(rank)) return 200;
        if ("C".equals(rank)) return 120;
        return 60;
    }

    private void updateStatus() {
        if (state == null) { statusLabel.setText(" "); return; }
        String name = state.level == null ? "" : (" - " + state.level.name);
        statusLabel.setText("MISSION 05 // LEVEL " + (state.levelIndex + 1) + name);
        statusLabel.setForeground(RetroTheme.ACCENT_CYAN);
    }

    // =====================================================================
    // VISIBILITY / DIM
    // =====================================================================
    @Override
    public void setVisible(boolean visible) {
        if (visible && !isVisible() && getOwner() instanceof JFrame && dim == null) {
            dim = new ScreenDim((JFrame) getOwner(), 232);
            dim.on();
        }
        try {
            super.setVisible(visible);
        } finally {
            if (!visible || !isVisible()) restoreOwner();
        }
    }

    @Override
    public void dispose() {
        restoreOwner();
        if (menuTimer != null) menuTimer.stop();
        stopGame();
        super.dispose();
    }

    private void restoreOwner() {
        if (dim != null) {
            dim.off();
            dim = null;
        }
    }

    // =====================================================================
    // MENU CARD
    // =====================================================================
    private JPanel createMenu() {
        AttractPanel menu = new AttractPanel();

        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("FAULT DEFENDER");
        title.setFont(F_MENU);
        title.setForeground(RetroTheme.ACCENT_PINK);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel sub = new JLabel("SHOOT THE ILLEGAL REQUESTS - LET THE LEGAL ONES LAND");
        sub.setFont(F_SUB);
        sub.setForeground(RetroTheme.ACCENT_CYAN);
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel rule = new JLabel("offset < limit  ->  legal (PA = base + offset)      offset >= limit  ->  SEGFAULT      write to [RO]  ->  PROTECTION FAULT");
        rule.setFont(F_SMALL);
        rule.setForeground(RetroTheme.TEXT_DIM);
        rule.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel note = new JLabel(tableNote());
        note.setFont(F_SMALL);
        note.setForeground(RetroTheme.ACCENT_YELLOW);
        note.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton tutorial = RetroTheme.createPrimaryButton("PLAY TUTORIAL");
        tutorial.setAlignmentX(Component.CENTER_ALIGNMENT);
        tutorial.addActionListener(e -> startGame(true));

        JButton campaign = RetroTheme.createRetroButton("START CAMPAIGN", new Color(48, 26, 78), RetroTheme.ACCENT_GREEN);
        campaign.setFont(new Font("Consolas", Font.BOLD, 13));
        campaign.setAlignmentX(Component.CENTER_ALIGNMENT);
        campaign.addActionListener(e -> startGame(false));

        box.add(title);
        box.add(Box.createVerticalStrut(8));
        box.add(sub);
        box.add(Box.createVerticalStrut(18));
        box.add(rule);
        box.add(Box.createVerticalStrut(10));
        box.add(note);
        box.add(Box.createVerticalStrut(26));
        box.add(tutorial);
        box.add(Box.createVerticalStrut(10));
        box.add(campaign);

        if (incoming != null && !incoming.isEmpty()) {
            JButton choose = RetroTheme.createRetroButton("CHOOSE SEGMENTS", new Color(30, 24, 60), RetroTheme.ACCENT_PURPLE);
            choose.setFont(new Font("Consolas", Font.BOLD, 13));
            choose.setAlignmentX(Component.CENTER_ALIGNMENT);
            choose.addActionListener(e -> cards.show(center, "picker"));
            box.add(Box.createVerticalStrut(10));
            box.add(choose);
        }

        JButton back = RetroTheme.createRetroButton("RETURN TO OS", RetroTheme.BTN_DARK, RetroTheme.ACCENT_PINK);
        back.setFont(new Font("Consolas", Font.BOLD, 13));
        back.setAlignmentX(Component.CENTER_ALIGNMENT);
        back.addActionListener(e -> requestExit());
        box.add(Box.createVerticalStrut(10));
        box.add(back);

        menu.add(box);
        return menu;
    }

    /**
     * Segment picker card: the player ticks up to 4 rows from their Mission 05
     * table and that subset drives level 1 instead of the built-in fallback.
     */
    private JPanel createPicker() {
        JPanel wrap = new JPanel(new GridBagLayout());
        wrap.setBackground(new Color(10, 8, 22));

        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("CHOOSE YOUR SEGMENTS");
        title.setFont(F_MENU);
        title.setForeground(RetroTheme.ACCENT_CYAN);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel hint = new JLabel("Tick up to 4 rows from Mission 05 - level 1 plays on what you pick.");
        hint.setFont(F_SMALL);
        hint.setForeground(RetroTheme.TEXT_DIM);
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);

        box.add(title);
        box.add(Box.createVerticalStrut(6));
        box.add(hint);
        box.add(Box.createVerticalStrut(16));

        final List<SegmentConfig> rows = new ArrayList<>();
        final List<JCheckBox> boxes = new ArrayList<>();
        final JLabel count = new JLabel(" ");
        if (incoming != null) {
            for (SegmentConfig s : incoming) {
                JCheckBox cb = new JCheckBox(String.format("#%d   %-12s   base %d   limit %d",
                        s.number, s.name, s.base, s.limit));
                cb.setOpaque(false);
                cb.setFont(F_SUB);
                cb.setForeground(RetroTheme.TEXT_WHITE);
                cb.setSelected(chosen != null && chosen.contains(s));
                cb.setAlignmentX(Component.CENTER_ALIGNMENT);
                rows.add(s);
                boxes.add(cb);
                box.add(cb);
                box.add(Box.createVerticalStrut(3));
            }
        }

        count.setFont(F_SMALL);
        count.setForeground(RetroTheme.ACCENT_YELLOW);
        count.setAlignmentX(Component.CENTER_ALIGNMENT);

        Runnable refresh = () -> {
            int n = 0;
            for (JCheckBox cb : boxes) if (cb.isSelected()) n++;
            for (JCheckBox cb : boxes) cb.setEnabled(cb.isSelected() || n < 4);
            count.setText("SELECTED " + n + " / 4");
            count.setForeground(RetroTheme.ACCENT_YELLOW);
        };
        for (JCheckBox cb : boxes) cb.addActionListener(e -> refresh.run());
        refresh.run();

        box.add(Box.createVerticalStrut(10));
        box.add(count);
        box.add(Box.createVerticalStrut(18));

        JButton startBtn = RetroTheme.createPrimaryButton("START CAMPAIGN");
        startBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        startBtn.addActionListener(e -> {
            List<SegmentConfig> pick = new ArrayList<>();
            for (int i = 0; i < boxes.size(); i++) if (boxes.get(i).isSelected()) pick.add(rows.get(i));
            if (pick.isEmpty()) {
                count.setText("PICK AT LEAST ONE SEGMENT");
                count.setForeground(RetroTheme.ACCENT_PINK);
                return;
            }
            chosen = pick;
            startGame(false, chosen);
        });

        JButton backBtn = RetroTheme.createRetroButton("BACK", RetroTheme.BTN_DARK, RetroTheme.ACCENT_PINK);
        backBtn.setFont(new Font("Consolas", Font.BOLD, 13));
        backBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        backBtn.addActionListener(e -> cards.show(center, "menu"));

        box.add(startBtn);
        box.add(Box.createVerticalStrut(10));
        box.add(backBtn);

        wrap.add(box);
        return wrap;
    }

    /** First usable, non-overlapping subset (up to 4) of the incoming table. */
    private static List<SegmentConfig> defaultChoice(List<SegmentConfig> in) {
        if (in == null || in.isEmpty()) return null;
        List<SegmentConfig> pick = new ArrayList<>();
        for (SegmentConfig s : in) {
            if (s.limit <= 0) continue;
            boolean clash = false;
            for (SegmentConfig p : pick) if (p.overlaps(s)) { clash = true; break; }
            if (!clash) pick.add(s);
            if (pick.size() == 4) break;
        }
        return pick.isEmpty() ? null : pick;
    }

    private String tableNote() {
        if (incoming == null || incoming.isEmpty()) {
            return "No Mission 05 table received - the built-in teaching table will be used.";
        }
        if (chosen == null || chosen.isEmpty()) {
            return "Mission 05 table is not usable - the built-in teaching table will be used.";
        }
        return "Level 1 will use " + chosen.size() + " of your Mission 05 segments (choose up to 4).";
    }

    // =====================================================================
    // CHROME
    // =====================================================================
    private JPanel createHeader() {
        JPanel bar = new JPanel(new BorderLayout(10, 0)) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(new Color(22, 14, 40));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(new Color(70, 45, 120));
                g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
                g2.dispose();
            }
        };
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(new JLabel(new ImageIcon(PixelSprites.createRobotSprite(22))));
        JLabel title = new JLabel("FAULT DEFENDER // MEMORY SEGMENTATION");
        title.setFont(F_TITLE);
        title.setForeground(RetroTheme.ACCENT_PURPLE);
        left.add(title);

        JPanel mid = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        mid.setOpaque(false);
        statusLabel.setFont(F_SMALL);
        statusLabel.setForeground(RetroTheme.ACCENT_CYAN);
        mid.add(statusLabel);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(muteButton);
        JButton close = RetroTheme.createRetroButton("X", RetroTheme.BTN_DARK, RetroTheme.ACCENT_PINK);
        close.setFont(F_SMALL);
        close.setToolTipText("Return to the OS [ESC]");
        close.addActionListener(e -> requestExit());
        right.add(close);

        bar.add(left, BorderLayout.WEST);
        bar.add(mid, BorderLayout.CENTER);
        bar.add(right, BorderLayout.EAST);

        makeDraggable(bar);
        makeDraggable(left);
        makeDraggable(right);
        return bar;
    }

    private JPanel createBottomBar() {
        JPanel bar = new JPanel(new BorderLayout(12, 0)) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(new Color(18, 12, 34));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(new Color(53, 38, 82));
                g2.drawLine(0, 0, getWidth(), 0);
                g2.dispose();
            }
        };
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

        JLabel keys = new JLabel("A/D or \u2190/\u2192 MOVE     SPACE SHOOT     P PAUSE     R RESTART     M MUTE     ESC RETURN TO OS");
        keys.setFont(F_SMALL);
        keys.setForeground(RetroTheme.TEXT_DIM);
        bar.add(keys, BorderLayout.WEST);

        JLabel tag = new JLabel("TAP [SPACE] TO SHOOT ILLEGAL SHIPS");
        tag.setFont(F_SMALL);
        tag.setForeground(RetroTheme.ACCENT_YELLOW);
        bar.add(tag, BorderLayout.EAST);
        return bar;
    }

    private static String muteLabel() {
        return FdSfx.isMuted() ? "SOUND OFF" : "SOUND ON";
    }

    private void makeDraggable(JComponent comp) {
        final Point[] winPos = new Point[1];
        final Point[] clickPos = new Point[1];
        comp.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                winPos[0] = getLocation();
                clickPos[0] = e.getLocationOnScreen();
            }
        });
        comp.addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseDragged(MouseEvent e) {
                if (winPos[0] == null) return;
                Point cur = e.getLocationOnScreen();
                setLocation(winPos[0].x + (cur.x - clickPos[0].x), winPos[0].y + (cur.y - clickPos[0].y));
            }
        });
    }

    // =====================================================================
    // ATTRACT / TITLE SCREEN
    // =====================================================================

    /**
     * Animated title screen: a slow starfield over the CRT grid, a blinking
     * "select a mode" prompt, and the local high-score table + best rank saved
     * by {@link FdScores}. The menu buttons are laid out on top of it.
     */
    private final class AttractPanel extends JPanel {
        private final float[] sx = new float[120];
        private final float[] sy = new float[120];
        private final float[] sp = new float[120];
        private float scroll = 0f;
        private float phase = 0f;

        AttractPanel() {
            setLayout(new GridBagLayout());
            setOpaque(true);
            Random r = new Random(50501L);
            for (int i = 0; i < sx.length; i++) {
                sx[i] = r.nextFloat();
                sy[i] = r.nextFloat();
                sp[i] = 0.2f + r.nextFloat() * 0.9f;
            }
            menuTimer = new Timer(33, e -> {
                phase += 0.05f;
                scroll += 1.1f;
                repaint();
            });
            menuTimer.start();
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            int W = getWidth(), H = getHeight();

            g2.setColor(new Color(10, 8, 22));
            g2.fillRect(0, 0, W, H);
            g2.setColor(new Color(22, 16, 42));
            for (int x = 0; x < W; x += 40) g2.drawLine(x, 0, x, H);
            for (int y = 0; y < H; y += 40) g2.drawLine(0, y, W, y);

            for (int i = 0; i < sx.length; i++) {
                int y = (int) ((sy[i] * H + scroll * sp[i]) % H);
                int b = 110 + (int) (sp[i] * 130);
                g2.setColor(new Color(b, b, Math.min(255, b + 40)));
                g2.fillRect((int) (sx[i] * W), y, 2, 2);
            }

            drawHighScores(g2, W, H);

            // blinking prompt
            if (((int) phase) % 2 == 0) {
                g2.setFont(F_SUB);
                g2.setColor(RetroTheme.ACCENT_YELLOW);
                g2.drawString("<< SELECT A MODE >>", 30, H - 26);
            }
            g2.dispose();
        }

        private void drawHighScores(Graphics2D g2, int W, int H) {
            int rows = Math.min(6, scores.size());
            int boxW = 252;
            int boxH = 62 + Math.max(1, rows) * 18;
            int bx = W - boxW - 26;
            int by = H - boxH - 22;

            g2.setColor(new Color(16, 11, 30, 235));
            g2.fillRoundRect(bx, by, boxW, boxH, 10, 10);
            g2.setColor(RetroTheme.ACCENT_PURPLE);
            g2.drawRoundRect(bx, by, boxW, boxH, 10, 10);

            g2.setFont(F_SMALL);
            g2.setColor(RetroTheme.ACCENT_YELLOW);
            g2.drawString("HIGH SCORES", bx + 12, by + 20);
            g2.setColor(RetroTheme.ACCENT_CYAN);
            g2.drawString("BEST RANK  " + bestRank, bx + 12, by + 38);
            g2.setColor(RetroTheme.BORDER_BRIGHT);
            g2.drawLine(bx + 12, by + 46, bx + boxW - 12, by + 46);

            if (rows == 0) {
                g2.setColor(RetroTheme.TEXT_DIM);
                g2.drawString("no runs recorded yet", bx + 12, by + 66);
                return;
            }
            for (int i = 0; i < rows; i++) {
                FdScores.Entry e = scores.get(i);
                int ry = by + 64 + i * 18;
                g2.setColor(i == 0 ? RetroTheme.ACCENT_YELLOW : RetroTheme.TEXT_WHITE);
                g2.drawString((i + 1) + ".", bx + 12, ry);
                g2.drawString(String.format("%06d", e.score), bx + 40, ry);
                g2.setColor(i == 0 ? RetroTheme.ACCENT_YELLOW : RetroTheme.ACCENT_PINK);
                g2.drawString(e.rank, bx + boxW - 24, ry);
            }
        }
    }
}
