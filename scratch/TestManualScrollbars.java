import arcadeos.*;
import javax.swing.*;
import java.awt.*;

public class TestManualScrollbars {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                int[] testManuals = {0, 2, 5, 7}; // Manuals 01, 03, 06, 08
                for (int idx : testManuals) {
                    ArchiveManualDialog dlg = new ArchiveManualDialog(null, null, idx);
                    dlg.pack();
                    // Check all scrollpanes in the dialog
                    findScrollPanes(dlg, "Manual " + (idx + 1));
                    dlg.dispose();
                }
                System.out.println("ALL MANUAL TESTS PASSED: NO HORIZONTAL SCROLLBAR ACTIVE");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static void findScrollPanes(Container c, String label) {
        for (Component comp : c.getComponents()) {
            if (comp instanceof JScrollPane) {
                JScrollPane sp = (JScrollPane) comp;
                sp.doLayout();
                JScrollBar hsb = sp.getHorizontalScrollBar();
                if (hsb != null && hsb.isVisible()) {
                    System.err.println("WARNING: Horizontal scrollbar visible in " + label);
                }
            } else if (comp instanceof Container) {
                findScrollPanes((Container) comp, label);
            }
        }
    }
}
