package scratch;

import arcadeos.*;
import game.snake.SnakeGameDialog;
import javax.swing.*;
import java.awt.Window;
import java.util.ArrayList;
import java.util.HashMap;

public class TestLaunchers {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                System.out.println("Testing ArcadeOS window creation...");
                ArcadeOS os = new ArcadeOS();
                os.setSize(1200, 800);
                os.doLayout();
                System.out.println("ArcadeOS OK.");

                System.out.println("Testing Simulator tabs...");
                for (int i = 0; i < 7; i++) {
                    os.openSimulatorTab(i);
                }
                System.out.println("All Simulator tabs OK.");

                System.out.println("Testing Paging example loading...");
                os.loadPagingManualExample();
                System.out.println("loadPagingManualExample OK.");

                System.out.println("Testing ArchiveManualDialog for all 8 manuals...");
                for (int m = 0; m < 8; m++) {
                    ArchiveManualDialog dlg = new ArchiveManualDialog(os, null, m);
                    dlg.dispose();
                }
                System.out.println("All 8 ArchiveManualDialog instances OK.");

                System.out.println("Testing FrameHopperDialog...");
                FrameHopperDialog fhd = new FrameHopperDialog(os, 1024, new HashMap<>(), 4);
                fhd.dispose();
                System.out.println("FrameHopperDialog OK.");

                System.out.println("Testing KernelTapperDialog...");
                KernelTapperDialog ktd = new KernelTapperDialog(os, "FCFS", new ArrayList<>());
                ktd.dispose();
                System.out.println("KernelTapperDialog OK.");

                System.out.println("Testing FaultDefenderDialog...");
                FaultDefenderDialog fdd = new FaultDefenderDialog(os, new ArrayList<>());
                fdd.dispose();
                System.out.println("FaultDefenderDialog OK.");

                System.out.println("Testing SnakeGameDialog...");
                String res = SnakeGameDialog.launchFromSegment(os, "CODE", 1000, 500, null);
                System.out.println("Snake launch result: " + res);
                for (Window w : Window.getWindows()) {
                    if (w instanceof SnakeGameDialog) {
                        ((SnakeGameDialog) w).dispose();
                    }
                }
                System.out.println("SnakeGameDialog OK.");

                System.out.println("\n=== ALL SMOKE TESTS PASSED! ===");
                System.exit(0);
            } catch (Throwable t) {
                t.printStackTrace();
                System.exit(1);
            }
        });
    }
}
