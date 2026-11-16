package scratch;

import arcadeos.FrameHopperDialog;
import arcadeos.RetroTheme;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.*;

public class TestFrameHopperInit {
    public static void main(String[] args) throws Exception {
        RetroTheme.applyGlobalUIDefaults();
        SwingUtilities.invokeAndWait(() -> {
            Map<Integer, Integer> table = new LinkedHashMap<>();
            table.put(0, 5);
            table.put(1, 6);
            table.put(2, 1);
            table.put(3, 2);

            FrameHopperDialog dlg = new FrameHopperDialog(null, 1024, table);
            dlg.pack();
            System.out.println("FrameHopperDialog created and packed successfully: " + dlg.getTitle());
            dlg.dispose();
            System.exit(0);
        });
    }
}
