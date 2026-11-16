package game.snake;

import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Offline rules check for HEAP vs STACK SNAKE (not part of the game).
 *
 * Drives the real {@link SnakeModel} into each implemented fault path and prints
 * the exact cause line the end screen would show, then paints the end card for
 * each cause so it can be eyeballed without launching the window. It also drives
 * Level 2 to its first COLLISION to measure the no-return time to collision, and
 * exercises the JSON loader (good files, a broken file, and the built-in
 * fallback). Every file this harness touches is backed up and restored.
 */
public final class SnakeCauseCheck {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        int w = 1366, h = 734;   // panel size at 1366x768 with the header strip
        byte[] scores = readScores();   // the game's real table must survive the test
        try {
            System.out.println("== cause lines (driven, not forced) ==");
            checkTopLimit();
            checkBase();
            checkSelfBite();
            checkUnreachable();

            System.out.println();
            System.out.println("== level 2: the stack ==");
            checkFirstCollision();
            checkReturnCooldown();
            checkSurvivableWithReturns();

            System.out.println();
            System.out.println("== json level loader ==");
            checkJsonLevels();

            System.out.println();
            System.out.println("== campaign + tooltip gate ==");
            checkCampaign();
            checkTooltipGate(w, h);

            System.out.println();
            System.out.println("== end card renders ==");
            render(w, h, "top_limit", Fault.GO_TOP);
            render(w, h, "below_base", Fault.GO_BASE);
            render(w, h, "self_bite", Fault.GO_SELF);
            render(w, h, "collision", Fault.GO_COLLIDE);

            System.out.println();
            System.out.println("== high-score persistence ==");
            checkScores();

            System.out.println();
            System.out.println(failures == 0 ? "ALL CAUSE CHECKS PASSED" : (failures + " CHECK(S) FAILED"));
        } finally {
            restoreScores(scores);
        }
        System.exit(failures == 0 ? 0 : 1);
    }

    /** Copies the game's high-score file aside so the test run leaves it untouched. */
    private static final File SCORES = new File("snake_scores.txt");

    private static byte[] readScores() {
        try { return SCORES.isFile() ? Files.readAllBytes(SCORES.toPath()) : null; }
        catch (Exception e) { return null; }
    }

    private static void restoreScores(byte[] saved) {
        try {
            if (saved == null) SCORES.delete();
            else Files.write(SCORES.toPath(), saved);
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------
    // fault scenarios
    // ------------------------------------------------------------------
    /** Steers the head up until it leaves the top of the heap arena. */
    private static void checkTopLimit() {
        SnakeModel m = new SnakeModel(SnakeLevel.level1());
        m.queueDir(0, 1);
        boolean hit = runToFault(m);
        String line = m.causeLine();
        boolean ok = hit && m.lastFault == SnakeModel.Fault.SEGFAULT
                && m.lastFaultOffset >= m.lastFaultLimit
                && line.startsWith("SEGFAULT: head left through the TOP (offset ");
        report("top limit", ok, line + "   (offset=" + m.lastFaultOffset
                + " limit=" + m.lastFaultLimit + " base=" + m.lastFaultBase + ")");
    }

    /** Steers the head down until it falls below the heap base. */
    private static void checkBase() {
        SnakeModel m = new SnakeModel(SnakeLevel.level1());
        m.queueDir(0, -1);
        boolean hit = runToFault(m);
        String line = m.causeLine();
        boolean ok = hit && m.lastFault == SnakeModel.Fault.SEGFAULT
                && m.lastFaultOffset < m.lastFaultLimit
                && line.startsWith("SEGFAULT: head left through the BASE (offset ")
                && line.endsWith("< 0)");
        report("below base", ok, line + "   (offset=" + m.lastFaultOffset
                + " base=" + m.lastFaultBase + ")");
    }

    /** Builds a 5-cell hook so the next step enters a live body cell. */
    private static void checkSelfBite() {
        SnakeModel m = new SnakeModel(SnakeLevel.level1());
        m.body.clear();
        m.body.addLast(new int[]{5, 6});   // head
        m.body.addLast(new int[]{6, 6});
        m.body.addLast(new int[]{6, 5});
        m.body.addLast(new int[]{5, 5});   // the cell the head will enter
        m.body.addLast(new int[]{4, 5});   // tail
        m.queueDir(0, -1);
        boolean hit = runToFault(m);
        String line = m.causeLine();
        boolean ok = hit && m.lastFault == SnakeModel.Fault.HEAP_CORRUPTION
                && line.equals("HEAP CORRUPTION: head hit its own body");
        report("self bite", ok, line);
    }

    /** PROTECTION FAULT has no trigger in levels 1-2 (no read-only blocks yet). */
    private static void checkUnreachable() {
        SnakeModel m = new SnakeModel(SnakeLevel.level1());
        m.lastFault = SnakeModel.Fault.PROTECTION_FAULT;
        String line = m.causeLine();
        boolean ok = line.equals("PROTECTION FAULT: touched a read-only block");
        System.out.println("  [" + (ok ? "TEXT OK " : "TEXT BAD") + "] PROTECTION_FAULT -> \""
                + line + "\"  (no read-only blocks in level 1/2: NOT STARTED)");
        if (!ok) failures++;
    }

    // ------------------------------------------------------------------
    // part 3 - level 2 stack
    // ------------------------------------------------------------------
    /**
     * Plays Level 2 with no steering and no SPACE and reports the first COLLISION:
     * its simulated time, the cause line, the two-frame retreat and the 1.5 s of
     * invulnerability. The level advertises gapFrames=6 and pushEveryMs=8000, so
     * the expected time is 6 pushes * 8000 ms = 48 000 ms.
     */
    private static void checkFirstCollision() {
        SnakeModel m = new SnakeModel(SnakeLevel.level2());
        long t = 0;
        while (t < 150_000 && m.phase == SnakeModel.Phase.PLAYING
                && m.faultCount(SnakeModel.Fault.COLLISION) == 0) {
            m.advance(16.0);
            t += 16;
        }
        boolean collided = m.faultCount(SnakeModel.Fault.COLLISION) > 0;
        String line = m.causeLine();
        if (!collided) {
            report("level 2 first collision", false,
                    "no COLLISION within 150 s; lastFault=" + m.lastFault + " -> " + line);
            return;
        }
        boolean ok = line.equals("COLLISION: heap met stack")
                && m.level.stackEnabled
                && m.level.gapFrames == 6
                && Math.abs(t - 48_000) <= 16;
        report("level 2 first collision", ok,
                String.format("t=%d ms (%.1f s), pushes=%d, gapFrames=6, then %d frames left"
                        + " -> \"%s\"", t, t / 1000.0, m.stackFrames + SnakeModel.COLLISION_RETREAT_FRAMES,
                        m.stackFrames, line));
        // After the collision the model has already retreated 2 frames + granted invuln.
        boolean retreatOk = m.stackFrames == 4;
        boolean invulnOk = m.invulnMs > 0;
        report("collision retreats 2 frames", retreatOk, "stackFrames=" + m.stackFrames);
        report("collision grants invulnerability ~1.5 s", invulnOk,
                "invulnMs=" + m.invulnMs + " (INVULN_MS=" + SnakeModel.INVULN_MS + ")");
    }

    /** SPACE pops returnPops frames once, then is refused until the cooldown ends. */
    private static void checkReturnCooldown() {
        SnakeModel m = new SnakeModel(SnakeLevel.level2());
        // Push a few frames so there is something to pop.
        m.pushFrames(5, true);
        int before = m.stackFrames;
        boolean readyFirst = m.canReturn();
        int popped = m.returnFrames();
        boolean refusedDuringCd = !m.canReturn() && m.returnFrames() == 0;
        // Let the cooldown run out.
        long t = 0;
        while (t < m.level.returnCooldownMs + 100) { m.advance(16.0); t += 16; }
        boolean readyAgain = m.canReturn();
        boolean ok = readyFirst && popped == m.level.returnPops
                && m.stackFrames == before - m.level.returnPops
                && refusedDuringCd && readyAgain;
        report("space return + cooldown", ok,
                "pops=" + popped + " (=" + m.level.returnPops + "), frames " + before + "->"
                        + (before - popped) + ", refused during cd=" + refusedDuringCd
                        + ", cooldown " + m.level.returnCooldownMs + " ms, ready again=" + readyAgain);
    }

    /** With SPACE used at every cooldown the level survives the full 60 s goal. */
    private static void checkSurvivableWithReturns() {
        SnakeModel m = new SnakeModel(SnakeLevel.level2());
        long t = 0;
        while (t < 90_000 && m.phase == SnakeModel.Phase.PLAYING
                && m.surviveMs < 60_000) {
            if (m.canReturn()) m.returnFrames();
            m.advance(16.0);
            t += 16;
        }
        // Reaching the 60 s goal ends the run as LEVEL_CLEAR, so that is success.
        boolean survived = m.surviveMs >= 60_000;
        report("level 2 survivable with returns", survived,
                String.format("survived %d ms (%.1f s), phase=%s, hearts=%d, collisions=%d",
                        m.surviveMs, m.surviveMs / 1000.0, m.phase, m.hearts,
                        m.faultCount(SnakeModel.Fault.COLLISION)));
    }

    // ------------------------------------------------------------------
    // json loader
    // ------------------------------------------------------------------
    private static void checkJsonLevels() {
        // 1. the shipped files load, with the spec values.
        SnakeLevels.reload();
        List<SnakeLevel> lib = SnakeLevels.library();
        boolean haveBoth = lib.size() >= 2;
        SnakeLevel l1 = lib.get(0), l2 = lib.size() > 1 ? lib.get(1) : null;
        boolean l1ok = l1.number == 1 && "Heap Limits".equals(l1.name) && !l1.stackEnabled
                && l1.goalMallocs == 10;
        boolean l2ok = l2 != null && l2.number == 2 && "Growing Stack".equals(l2.name)
                && l2.stackEnabled && l2.cols == 24 && l2.moveMs == 140 && l2.gapFrames == 6
                && l2.pushEveryMs == 8000 && l2.returnPops == 2 && l2.returnCooldownMs == 4000
                && l2.mallocSpawnMs == 2500 && l2.mallocSpawn == 3 && l2.goalSurviveSec == 60;
        report("load level 1 + 2 from json", haveBoth && l1ok && l2ok,
                "levels=" + lib.size() + ", L1(" + l1.name + ",stack=" + l1.stackEnabled
                        + ",goal=" + l1.goalMallocs + "), L2("
                        + (l2 == null ? "missing" : l2.name + ",gap=" + l2.gapFrames
                        + ",push=" + l2.pushEveryMs + ",survive=" + l2.goalSurviveSec) + ")");

        // 2. a broken file is reported and the built-in level is used instead.
        File f = new File("game/snake/levels/level2.json");
        byte[] backup = null;
        try {
            backup = f.isFile() ? Files.readAllBytes(f.toPath()) : null;
            boolean hadFile = backup != null;
            Files.createDirectories(f.toPath().getParent());
            Files.write(f.toPath(),
                    "{\"level\":2,\"name\":\"Broken\",\"cols\":24,\"moveMs\":140,"
                            .concat("\"stack\":{\"enabled\":true},\"goal\":{}}")   // no gapFrames, bad goal
                            .getBytes(StandardCharsets.UTF_8));
            SnakeLevels.reload();
            List<SnakeLevel> lib2 = SnakeLevels.library();
            List<String> errs = SnakeLevels.lastErrors();
            SnakeLevel fb = lib2.size() > 1 ? lib2.get(1) : null;
            boolean reported = !errs.isEmpty();
            boolean fellBack = fb != null && "Growing Stack".equals(fb.name) && fb.stackEnabled;
            report("broken json -> error + fallback", reported && fellBack,
                    "errors=" + errs + ", level 2 now=" + (fb == null ? "none" : fb.name));
            if (!hadFile) System.out.println("  [note] level2.json did not exist before the test");
        } catch (Exception ex) {
            report("broken json -> error + fallback", false, "harness error: " + ex);
        } finally {
            try {
                if (backup != null) Files.write(f.toPath(), backup);
                SnakeLevels.reload();
            } catch (Exception ignored) { }
        }

        // 3. the validator itself rejects a level that sets no goal.
        boolean threw = false;
        String msg = "";
        try {
            SnakeLevels.fromJson(1, "{\"level\":1,\"name\":\"X\",\"cols\":24,\"moveMs\":140,"
                    .concat("\"goal\":{}"));
        } catch (IllegalArgumentException ex) {
            threw = true;
            msg = ex.getMessage();
        }
        report("validator rejects a bad level", threw && msg != null && !msg.isEmpty(),
                threw ? "IllegalArgumentException: " + msg : "no error thrown");
    }

    /**
     * PART 1.1: "NEXT LEVEL" advances the campaign. It exists only when the
     * library has a level after the current one, so Level 2 (the last authored
     * level) hides it, and the model rebuilds when it does advance.
     */
    private static void checkCampaign() {
        SnakeLevels.reload();
        List<SnakeLevel> lib = SnakeLevels.library();
        SnakeLevel l1 = lib.get(0);
        SnakeLevel l2 = lib.size() > 1 ? lib.get(1) : null;
        SnakeLevel n1 = SnakeLevel.next(l1);
        SnakeLevel n2 = l2 == null ? null : SnakeLevel.next(l2);

        SnakeModel m = new SnakeModel(l1);
        int rows1 = m.rows;
        m.setLevel(n1);
        boolean switches = n1 != null && m.level == n1
                && m.rows == n1.rows() && m.phase == SnakeModel.Phase.PLAYING
                && rows1 != m.rows;
        boolean ok = n1 != null && n1.number == 2 && n2 == null && switches;
        report("next level: L1 -> L2, L2 has no next", ok,
                "next(L1)=" + (n1 == null ? "none" : "L" + n1.number + " " + n1.name)
                        + ", next(L2)=" + (n2 == null ? "none (button hidden)" : "L" + n2.number)
                        + ", model rows " + rows1 + "->" + m.rows);
    }

    /**
     * PART 1.2: no board/minimap tooltip may leak through an end card, and a
     * boundary address reads the same in the tooltip as on the ruler.
     */
    private static void checkTooltipGate(int w, int h) {
        SnakeModel m = new SnakeModel(SnakeLevel.level1());
        SnakeGamePanel p = new SnakeGamePanel(null, m);
        p.stop();
        p.setSize(w, h);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        p.paint(g);                     // sets the board-rect fields the tooltip reads
        g.dispose();

        SnakeGamePanel.Metrics mt = SnakeGamePanel.compute(w, h, m.cols, m.rows);
        int bx = mt.boardX + mt.boardW / 2;
        int by = mt.boardY + mt.boardH / 2;
        long now = System.currentTimeMillis();

        m.phase = SnakeModel.Phase.PLAYING;
        String playing = p.getToolTipText(mouse(p, bx, by, now));
        m.phase = SnakeModel.Phase.LEVEL_CLEAR;
        String clear = p.getToolTipText(mouse(p, bx, by, now));
        m.phase = SnakeModel.Phase.GAME_OVER;
        String over = p.getToolTipText(mouse(p, bx, by, now));
        m.phase = SnakeModel.Phase.PLAYING;
        m.paused = true;
        String paused = p.getToolTipText(mouse(p, bx, by, now));

        boolean ok = playing != null && clear == null && over == null && paused == null;
        report("tooltip gate (none over an end card)", ok,
                "playing=\"" + playing + "\" | clear=" + clear + " | over=" + over
                        + " | paused=" + paused);

        // The exact heap-limit row: the tooltip must print the ruler's hex address.
        int limitY = mt.boardY + mt.boardH
                - (int) Math.round(m.heapTop() / (double) m.level.addressSpace * mt.boardH);
        m.paused = false;
        String limTip = p.getToolTipText(mouse(p, bx, limitY, now));
        String hex = Integer.toHexString(m.heapTop()).toUpperCase();
        while (hex.length() < 4) hex = "0" + hex;
        boolean hexOk = limTip != null && limTip.contains("0x" + hex);
        report("tooltip address matches the ruler", hexOk,
                "heapTop=" + m.heapTop() + " expect 0x" + hex + " -> \"" + limTip + "\"");
    }

    private static MouseEvent mouse(java.awt.Component c, int x, int y, long when) {
        return new MouseEvent(c, MouseEvent.MOUSE_MOVED, when, 0, x, y, 0, false);
    }

    /** Advances the model in fixed ticks until a fault is recorded. */
    private static boolean runToFault(SnakeModel m) {
        for (int i = 0; i < 400; i++) {
            if (!m.mistakes.isEmpty()) return true;
            if (m.phase != SnakeModel.Phase.PLAYING) return false;
            m.advance(16.0);
        }
        return !m.mistakes.isEmpty();
    }

    // ------------------------------------------------------------------
    // end card render + report maths
    // ------------------------------------------------------------------
    private enum Fault { GO_TOP, GO_BASE, GO_SELF, GO_COLLIDE }

    private static void render(int w, int h, String tag, Fault kind) throws Exception {
        SnakeModel m = kind == Fault.GO_COLLIDE
                ? new SnakeModel(SnakeLevel.level2()) : new SnakeModel(SnakeLevel.level1());
        m.score = 120;
        m.mallocsEaten = 12;
        m.longest = 14;
        m.surviveMs = 41_000;
        m.mistakes.add(SnakeModel.Fault.SEGFAULT);
        m.mistakes.add(SnakeModel.Fault.SEGFAULT);
        m.mistakes.add(SnakeModel.Fault.HEAP_CORRUPTION);
        switch (kind) {
            case GO_TOP:
                m.lastFault = SnakeModel.Fault.SEGFAULT;
                m.lastFaultOffset = 600; m.lastFaultLimit = 600; m.lastFaultBase = 400;
                break;
            case GO_BASE:
                m.lastFault = SnakeModel.Fault.SEGFAULT;
                m.lastFaultOffset = -87; m.lastFaultLimit = 600; m.lastFaultBase = 400;
                break;
            case GO_COLLIDE:
                m.mistakes.add(SnakeModel.Fault.COLLISION);
                m.stackFrames = 4;
                m.lastFault = SnakeModel.Fault.COLLISION;
                m.lastFaultBase = m.level.heapBase;
                m.lastFaultOffset = m.heapLimit;                 // heap top, from the base
                m.lastFaultLimit = m.level.stackTop - m.level.heapBase;  // stack top, from the base
                break;
            default:
                m.lastFault = SnakeModel.Fault.HEAP_CORRUPTION;
                break;
        }
        m.hearts = 0;
        m.phase = SnakeModel.Phase.GAME_OVER;

        System.out.println("  cause=" + m.causeLine() + "  rank=" + m.rank()
                + "  common=" + m.mostCommonFault()
                + "  seg=" + m.faultCount(SnakeModel.Fault.SEGFAULT)
                + " cor=" + m.faultCount(SnakeModel.Fault.HEAP_CORRUPTION)
                + " col=" + m.faultCount(SnakeModel.Fault.COLLISION)
                + " gap=" + m.smallestFreeGap);

        SnakeGamePanel p = new SnakeGamePanel(null, m);
        p.stop();
        p.setSize(w, h);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        p.paint(g);
        g.dispose();
        File out = new File("scratch/out/snake_end_" + tag + "_" + w + "x" + h + ".png");
        ImageIO.write(img, "png", out);
        System.out.println("  SAVED " + out.getAbsolutePath());
    }

    // ------------------------------------------------------------------
    // persistence
    // ------------------------------------------------------------------
    private static void checkScores() {
        String bestBefore = SnakeScores.bestRank();
        SnakeScores.record(1234, "C", SnakeScores.today());
        List<SnakeScores.Entry> after = SnakeScores.load();
        boolean found = false;
        for (SnakeScores.Entry e : after) if (e.score == 1234) found = true;
        String bestAfter = SnakeScores.bestRank();
        boolean ok = found
                && SnakeScores.rankTier(bestAfter) >= SnakeScores.rankTier(bestBefore);
        report("score round-trip", ok, "best " + bestBefore + " -> " + bestAfter
                + ", entries=" + after.size() + ", score 1234 stored=" + found);
    }

    private static void report(String name, boolean ok, String detail) {
        if (!ok) failures++;
        System.out.println("  [" + (ok ? "PASS" : "FAIL") + "] " + name + " -> " + detail);
    }
}
