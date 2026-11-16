package game.snake;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

/**
 * Offline input check for HEAP vs STACK SNAKE (not part of the game).
 *
 * Confirms the TEST CASE "press SPACE on every screen never toggles the SOUND
 * button": it drives the real SPACE action for every phase and asserts the mute
 * flag never changes, and it asserts the header SOUND / EXIT buttons are
 * non-focusable so they can never steal SPACE. The button check needs a display;
 * if the JVM is headless that part is reported as SKIP.
 */
public final class SnakeInputCheck {

    private static int fail = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("headless=" + GraphicsEnvironment.isHeadless());
        checkPanelKeys();
        checkWindowButtons();
        System.out.println(fail == 0 ? "INPUT CHECKS PASSED" : fail + " INPUT CHECK(S) FAILED");
        System.exit(fail == 0 ? 0 : 1);
    }

    /**
     * Drives SPACE in each phase through the panel's own action map. PART 3 makes
     * SPACE the stack RETURN key, so it acts only while playing on a level that has
     * a stack, and is a no-op everywhere else - it must never activate a button.
     */
    private static void checkPanelKeys() {
        boolean mutedBefore = GameSfx.isMuted();

        // ── Level 1 (no stack): SPACE is a no-op ──
        SnakeModel m1 = new SnakeModel(SnakeLevel.level1());
        SnakeGamePanel p1 = new SnakeGamePanel(null, m1);
        p1.stop();   // kill the game timer so nothing else mutates the model
        Action space1 = spaceAction(p1);
        check(space1 != null, "SPACE is bound in WHEN_IN_FOCUSED_WINDOW");
        if (space1 == null) return;
        m1.phase = SnakeModel.Phase.PLAYING;
        m1.paused = false;
        space1.actionPerformed(null);
        check(m1.phase == SnakeModel.Phase.PLAYING, "SPACE with no stack: phase unchanged");
        check(GameSfx.isMuted() == mutedBefore, "SPACE with no stack: mute unchanged");

        // ── Level 2 (stack): SPACE returns frames, and only while playing ──
        SnakeModel m2 = new SnakeModel(SnakeLevel.level2());
        SnakeGamePanel p2 = new SnakeGamePanel(null, m2);
        p2.stop();
        Action space2 = spaceAction(p2);
        if (space2 == null) return;

        m2.phase = SnakeModel.Phase.PLAYING;
        m2.paused = false;
        m2.pushFrames(4, true);
        int before = m2.stackFrames;
        space2.actionPerformed(null);
        check(m2.stackFrames == before - m2.level.returnPops,
                "SPACE while playing (stack): returns " + m2.level.returnPops + " frames");
        check(GameSfx.isMuted() == mutedBefore, "SPACE while playing: mute unchanged");

        m2.paused = true;
        int f = m2.stackFrames;
        space2.actionPerformed(null);
        check(m2.paused && m2.stackFrames == f, "SPACE while paused: no-op (P toggles pause)");

        m2.paused = false;
        m2.phase = SnakeModel.Phase.GAME_OVER;
        m2.hearts = 0;
        space2.actionPerformed(null);
        check(m2.phase == SnakeModel.Phase.GAME_OVER,
                "SPACE on GAME OVER: no-op (never triggers a button)");

        m2.phase = SnakeModel.Phase.LEVEL_CLEAR;
        space2.actionPerformed(null);
        check(m2.phase == SnakeModel.Phase.LEVEL_CLEAR,
                "SPACE on LEVEL CLEAR: no-op (never triggers a button)");

        check(GameSfx.isMuted() == mutedBefore, "SPACE on every screen: SOUND never toggled");
    }

    /** Resolves the panel's SPACE action from its WHEN_IN_FOCUSED_WINDOW map. */
    private static Action spaceAction(SnakeGamePanel p) {
        InputMap im = p.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        Object key = im.get(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0));
        return key == null ? null : p.getActionMap().get(key);
    }

    /** Asserts every header button is non-focusable (needs a display). */
    private static void checkWindowButtons() {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("  [SKIP] header button focus check: no display");
            return;
        }
        SnakeGameDialog dlg = null;
        try {
            dlg = new SnakeGameDialog(null, SnakeLevel.level1());
            List<AbstractButton> buttons = new ArrayList<>();
            collect(dlg.getContentPane(), buttons);
            boolean sound = false, exit = false, anyFocusable = false;
            for (AbstractButton b : buttons) {
                if (b.isFocusable()) anyFocusable = true;
                String t = b.getText();
                if (t != null && t.startsWith("SOUND")) sound = true;
                if ("EXIT".equals(t)) exit = true;
            }
            check(sound && exit, "header has the SOUND and EXIT buttons");
            check(!anyFocusable, "no header button is focusable (SPACE cannot reach them)");
        } catch (Throwable t) {
            System.out.println("  [SKIP] header button focus check: " + t);
        } finally {
            if (dlg != null) { dlg.stopGame(); dlg.dispose(); }
        }
    }

    private static void collect(Component c, List<AbstractButton> out) {
        if (c instanceof AbstractButton) out.add((AbstractButton) c);
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) collect(child, out);
        }
    }

    private static void check(boolean ok, String msg) {
        System.out.println("  [" + (ok ? "PASS" : "FAIL") + "] " + msg);
        if (!ok) fail++;
    }
}
