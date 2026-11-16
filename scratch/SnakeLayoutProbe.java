package game.snake;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Offline layout probe for HEAP vs STACK SNAKE (not part of the game).
 *
 * Prints the measured board metrics for the two acceptance resolutions using the
 * exact same maths the panel uses at runtime, and paints the real compiled
 * {@link SnakeGamePanel} into a PNG at each size so the layout can be eyeballed
 * without launching the window.
 */
public final class SnakeLayoutProbe {
    public static void main(String[] args) throws Exception {
        // Header strip measured live (~34 px); subtracted so the panel size matches.
        int header = 34;
        int[][] wins = {{1920, 1080}, {1550, 830}, {1366, 768}};
        byte[] scores = readScores();   // the game's real table must survive the probe
        try {
            run(header, wins);
        } finally {
            restoreScores(scores);
        }
        System.exit(0);   // the panels own Swing timers; exit explicitly
    }

    /** Copies the game's high-score file aside so the probe leaves it untouched. */
    private static final File SCORES = new File("snake_scores.txt");

    private static byte[] readScores() {
        try { return SCORES.isFile() ? java.nio.file.Files.readAllBytes(SCORES.toPath()) : null; }
        catch (Exception e) { return null; }
    }

    private static void restoreScores(byte[] saved) {
        try {
            if (saved == null) SCORES.delete();
            else java.nio.file.Files.write(SCORES.toPath(), saved);
        } catch (Exception ignored) { }
    }

    private static void run(int header, int[][] wins) throws Exception {

        System.out.println("== default Level 1 ==");
        SnakeLevel l1 = SnakeLevel.level1();
        for (int[] wh : wins) {
            System.out.println("  win=" + wh[0] + "x" + wh[1] + "  "
                    + SnakeGamePanel.metricsLine(wh[0], wh[1] - header, l1));
            System.out.println("    " + SnakeGamePanel.fontMetricsLine(wh[1] - header));
            System.out.println("    " + SnakeGamePanel.endPanelLine(wh[0], wh[1] - header, l1));
        }

        System.out.println("== segment arenas ==");
        int[] limits = {250, 300, 400, 500, 600};
        for (int lim : limits) {
            SnakeLevel seg = SnakeLevel.fromSegment("seg", 512, lim);
            System.out.println("  limit=" + lim + " rowUnits=" + seg.rowUnits
                    + " arenaRows=" + (seg.heapLimit / seg.rowUnits)
                    + " effectiveLimit=" + seg.heapLimit + " rows=" + seg.rows() + " cols=" + seg.cols);
        }

        System.out.println("== renders ==");
        for (int[] wh : wins) {
            dump(l1, wh[0], wh[1] - header);
        }

        System.out.println("== forced end screen ==");
        for (int[] wh : wins) {
            dumpOver(l1, wh[0], wh[1] - header);
        }

        System.out.println("== end panel across window sizes (resize sweep) ==");
        int[][] sweep = {{800, 600}, {1024, 768}, {1280, 720}, {1366, 768},
                         {1550, 830}, {1600, 900}, {1920, 1080}, {2560, 1440}};
        for (int[] wh : sweep) {
            System.out.println(String.format("  %dx%d  ", wh[0], wh[1])
                    + SnakeGamePanel.endPanelLine(wh[0], wh[1] - header, l1));
        }

        // PART 1.6 acceptance: the Level Clear card (worst case) must fit inside the
        // board height at 1366x768, 1550x830 and maximized. "Maximized" is taken as
        // the largest window in the sweep (2560x1440).
        System.out.println("== PART 1.6 target sizes: card vs board height ==");
        int[][] targets = {{1366, 768}, {1550, 830}, {2560, 1440}};
        for (int[] wh : targets) {
            SnakeGamePanel.Metrics m = SnakeGamePanel.compute(wh[0], wh[1] - header, l1.cols, l1.rows());
            SnakeGamePanel.EndLayout e = SnakeGamePanel.endLayout(wh[0], wh[1] - header,
                    l1.cols, l1.rows(), 3, 5, 3, true);
            boolean fits = e.ph <= m.boardH;
            System.out.println(String.format(
                    "  win=%dx%d  boardH=%d  cardH=%d  fits=%s (stats=%d hi=%d middle=%s review=%d)",
                    wh[0], wh[1], m.boardH, e.ph, fits, e.statRows, e.hiRows, e.showMiddle, e.reviewLines));
        }
    }

    /** Paints a forced Game Over panel offscreen so the end card can be inspected. */
    private static void dumpOver(SnakeLevel lv, int w, int h) throws Exception {
        SnakeModel m = new SnakeModel(lv);
        m.score = 120;
        m.mallocsEaten = 12;
        m.longest = 14;
        m.surviveMs = 41_000;
        m.lastFault = SnakeModel.Fault.SEGFAULT;
        m.lastFaultOffset = 600;
        m.lastFaultLimit = 600;
        m.lastFaultBase = 400;
        m.mistakes.add(SnakeModel.Fault.SEGFAULT);
        m.mistakes.add(SnakeModel.Fault.SEGFAULT);
        m.mistakes.add(SnakeModel.Fault.HEAP_CORRUPTION);
        m.hearts = 0;
        m.phase = SnakeModel.Phase.GAME_OVER;

        System.out.println("  cause: " + m.causeLine());
        System.out.println("  rank=" + m.rank() + " common=" + m.mostCommonFault()
                + " freeGap=" + m.freeGap() + " minGap=" + m.smallestFreeGap);

        // Seed a high score so the end screen is rendered in its worst case (the
        // HI-SCORES line present) and so the persistence round-trip is exercised.
        SnakeScores.record(9800, "S", SnakeScores.today());
        System.out.println("  scores best=" + SnakeScores.bestRank()
                + " entries=" + SnakeScores.load().size());

        SnakeGamePanel panel = new SnakeGamePanel(null, m);
        panel.stop();
        panel.setSize(w, h);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        panel.paint(g);
        g.dispose();
        File out = new File("scratch/out/snake_over_" + w + "x" + h + ".png");
        ImageIO.write(img, "png", out);
        System.out.println("  SAVED " + out.getAbsolutePath());
    }

    /** Paints the real panel offscreen at {@code w x h} and writes it next to this probe. */
    private static void dump(SnakeLevel lv, int w, int h) throws Exception {
        SnakeModel model = new SnakeModel(lv);
        SnakeGamePanel panel = new SnakeGamePanel(null, model);
        panel.setSize(w, h);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        panel.paint(g);
        g.dispose();
        File out = new File("scratch/out/snake_" + w + "x" + h + ".png");
        ImageIO.write(img, "png", out);
        System.out.println("  SAVED " + out.getAbsolutePath());
    }
}
