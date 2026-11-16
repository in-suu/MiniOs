package scratch;

import arcadeos.*;
import javax.swing.*;
import java.awt.Dimension;
import java.lang.reflect.Method;
import java.util.List;

public class TestAllEnhancements {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                System.out.println("=== TESTING ENHANCEMENT 1: WORKED EXAMPLE LOADERS FOR ALL 8 MANUALS ===");
                ArcadeOS os = new ArcadeOS();
                os.setSize(1200, 800);
                os.doLayout();

                for (int m = 0; m < 8; m++) {
                    System.out.println("-> Triggering loadManualExample for Manual index " + m + "...");
                    os.loadManualExample(m);
                    System.out.println("   [OK] Manual " + m + " loaded successfully.");
                }

                System.out.println("\n=== TESTING ENHANCEMENT 2: HIGH-DPI DIALOG CLAMPING ===");
                Dimension clamped1 = RetroTheme.getClampedDialogSize(os, 2000, 1500);
                System.out.println("Target 2000x1500 -> Clamped to: " + clamped1.width + "x" + clamped1.height);
                if (clamped1.width > 2000 || clamped1.height > 1500) {
                    throw new RuntimeException("Clamping failed!");
                }
                Dimension clamped2 = RetroTheme.getClampedDialogSize(os, 400, 300);
                System.out.println("Target 400x300 -> Clamped to: " + clamped2.width + "x" + clamped2.height);

                System.out.println("\n=== TESTING ENHANCEMENT 3: RESILIENT SCORE PERSISTENCE ===");
                Class<?> fdCls = Class.forName("arcadeos.FdScores");
                Method bestRankMethod = fdCls.getDeclaredMethod("bestRank");
                bestRankMethod.setAccessible(true);
                Method recordMethod = fdCls.getDeclaredMethod("record", int.class, String.class, String.class);
                recordMethod.setAccessible(true);
                Method loadMethod = fdCls.getDeclaredMethod("load");
                loadMethod.setAccessible(true);

                System.out.println("Initial FdScores best: " + bestRankMethod.invoke(null));
                recordMethod.invoke(null, 999, "S", "2026-11-16");
                System.out.println("After record FdScores best: " + bestRankMethod.invoke(null));
                System.out.println("FdScores entries count: " + ((List<?>) loadMethod.invoke(null)).size());

                Class<?> snakeCls = Class.forName("game.snake.SnakeScores");
                Method snakeBestRank = snakeCls.getDeclaredMethod("bestRank");
                snakeBestRank.setAccessible(true);
                Method snakeRecord = snakeCls.getDeclaredMethod("record", int.class, String.class, String.class);
                snakeRecord.setAccessible(true);
                Method snakeLoad = snakeCls.getDeclaredMethod("load");
                snakeLoad.setAccessible(true);

                System.out.println("Initial SnakeScores best: " + snakeBestRank.invoke(null));
                snakeRecord.invoke(null, 888, "A", "2026-11-16");
                System.out.println("After record SnakeScores best: " + snakeBestRank.invoke(null));
                System.out.println("SnakeScores entries count: " + ((List<?>) snakeLoad.invoke(null)).size());

                System.out.println("\n=== ALL ENHANCEMENT VERIFICATION TESTS PASSED! ===");
                System.exit(0);
            } catch (Throwable t) {
                t.printStackTrace();
                System.exit(1);
            }
        });
    }
}
