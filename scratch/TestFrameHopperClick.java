package scratch;

import arcadeos.ArcadeOS;
import arcadeos.FrameHopperDialog;
import arcadeos.PagingPanel;
import arcadeos.RetroTheme;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.*;

public class TestFrameHopperClick {
    public static void main(String[] args) {
        RetroTheme.applyGlobalUIDefaults();
        SwingUtilities.invokeLater(() -> {
            try {
                ArcadeOS os = new ArcadeOS();
                os.setVisible(true);
                os.showScreen(ArcadeOS.SCREEN_SIMULATOR);

                Map<Integer, Integer> table = new LinkedHashMap<>();
                table.put(0, 5);
                table.put(1, 6);
                table.put(2, 1);
                table.put(3, 2);

                FrameHopperDialog dlg = new FrameHopperDialog(os, 1024, table);
                System.out.println("FrameHopperDialog launched successfully");

                new Timer(1500, e -> {
                    dlg.dispose();
                    os.dispose();
                    System.out.println("TestFrameHopperClick passed cleanly!");
                    System.exit(0);
                }).start();

                dlg.setVisible(true);
            } catch (Throwable t) {
                t.printStackTrace();
                System.exit(1);
            }
        });
    }
}
