package scratch;

import arcadeos.PagingPanel;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public class TestRegression {

    private static Object getField(Object obj, String name) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(obj);
    }

    private static void invoke(Object obj, String name) throws Exception {
        Method m = obj.getClass().getDeclaredMethod(name);
        m.setAccessible(true);
        m.invoke(obj);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                runAllChecks();
            } catch (Throwable t) {
                t.printStackTrace();
                System.exit(1);
            }
        });
    }

    private static void runAllChecks() throws Exception {
        System.out.println("=== STARTING PAGING PANEL REGRESSION TESTS ===");

        PagingPanel panel = new PagingPanel();
        panel.setSize(1000, 600);
        panel.doLayout();

        JTextField txtPageSize = (JTextField) getField(panel, "txtPageSize");
        JTextField txtQueryAddr = (JTextField) getField(panel, "txtQueryAddr");
        DefaultTableModel model = (DefaultTableModel) getField(panel, "pageTableModel");
        JLabel lblRamStats = (JLabel) getField(panel, "lblRamStats");
        JLabel lblHero = (JLabel) getField(panel, "lblResultHero");
        JLabel lblTableHint = (JLabel) getField(panel, "lblTableHint");

        // 1. Initial Large Preset Check
        System.out.println("\n[1] Initial State (Large Preset):");
        System.out.println("Row count: " + model.getRowCount() + " (Expected: 14)");
        System.out.println("RAM Stats: " + lblRamStats.getText() + " (Expected: FRAMES: 10/16 USED, 6 FREE | TOTAL RAM: 16384 B)");

        // 2. Remove x 3
        System.out.println("\n[2] Remove 3 rows:");
        invoke(panel, "removePageRow"); // removes P13
        invoke(panel, "removePageRow"); // removes P12
        invoke(panel, "removePageRow"); // removes P11
        System.out.println("Row count after 3 removes: " + model.getRowCount() + " (Expected: 11, P0-P10)");
        System.out.println("RAM Stats: " + lblRamStats.getText() + " (Expected: FRAMES: 7/16 USED, 9 FREE | TOTAL RAM: 16384 B)");

        // 3. Add x 3
        System.out.println("\n[3] Add 3 rows back:");
        invoke(panel, "addPageRow"); // maps to F7
        invoke(panel, "addPageRow"); // maps to F8
        invoke(panel, "addPageRow"); // maps to F9
        System.out.println("Row count after 3 adds: " + model.getRowCount() + " (Expected: 14)");
        System.out.println("Row 11: P" + model.getValueAt(11, 0) + " -> F" + model.getValueAt(11, 1) + " (Expected: P11 -> F7)");
        System.out.println("Row 12: P" + model.getValueAt(12, 0) + " -> F" + model.getValueAt(12, 1) + " (Expected: P12 -> F8)");
        System.out.println("Row 13: P" + model.getValueAt(13, 0) + " -> F" + model.getValueAt(13, 1) + " (Expected: P13 -> F9)");
        System.out.println("RAM Stats: " + lblRamStats.getText() + " (Expected: FRAMES: 10/16 USED, 6 FREE)");

        // 4. Translate 12288 after two removes -> P12 -> LIMIT TRAP (12 > 11)
        System.out.println("\n[4] Translate 12288 after two removes:");
        invoke(panel, "loadLargePreset");
        invoke(panel, "removePageRow"); // removes P13 (0..12 remaining)
        invoke(panel, "removePageRow"); // removes P12 (0..11 remaining, max page = 11)
        txtQueryAddr.setText("12288");
        invoke(panel, "translateAddress");
        System.out.println("Hero result: " + lblHero.getText() + " (Expected: LIMIT TRAP)");

        // 5. Remove down to 1 row, then Remove once more
        System.out.println("\n[5] Remove down to 1 row and attempt extra remove:");
        while (model.getRowCount() > 1) {
            invoke(panel, "removePageRow");
        }
        System.out.println("Rows before extra remove: " + model.getRowCount());
        invoke(panel, "removePageRow");
        System.out.println("Rows after extra remove: " + model.getRowCount() + " (Expected: 1)");
        System.out.println("Hint text: '" + lblTableHint.getText() + "' (Expected: 'Page table needs at least 1 page.')");

        // 6. Add up to 22 rows
        System.out.println("\n[6] Add up to 22 rows:");
        invoke(panel, "loadLargePreset"); // starts at 14 rows (10 mapped, 4 unmapped)
        for (int i = model.getRowCount(); i < 22; i++) {
            invoke(panel, "addPageRow");
        }
        System.out.println("Row count: " + model.getRowCount() + " (Expected: 22)");
        System.out.println("RAM Stats: " + lblRamStats.getText() + " (Expected: FRAMES: 16/16 USED, 0 FREE)");
        System.out.println("Row 19 (16th mapped frame): P" + model.getValueAt(19, 0) + " -> F" + model.getValueAt(19, 1) + " Status: " + model.getValueAt(19, 2));
        System.out.println("Row 20 (all frames full): P" + model.getValueAt(20, 0) + " -> F" + model.getValueAt(20, 1) + " Status: " + model.getValueAt(20, 2));
        System.out.println("Row 21 (all frames full): P" + model.getValueAt(21, 0) + " -> F" + model.getValueAt(21, 1) + " Status: " + model.getValueAt(21, 2));

        // 7. Fresh Large Preset Translations (1024 B)
        System.out.println("\n[7] Translations (1024 B, Large preset, fresh):");
        invoke(panel, "loadLargePreset");

        // 2500 -> P2+452 -> F1 -> 1476 B
        txtQueryAddr.setText("2500");
        invoke(panel, "translateAddress");
        System.out.println("2500 -> Hero: " + lblHero.getText() + " (Expected: 1476 B)");

        // 4200 -> P4+104 -> PAGE FAULT
        txtQueryAddr.setText("4200");
        invoke(panel, "translateAddress");
        System.out.println("4200 -> Hero: " + lblHero.getText() + " (Expected: PAGE FAULT)");
        int highlight = (int) getField(panel, "activeHighlightRow");
        System.out.println("Active highlight row: " + highlight + " (Expected: 4)");

        // 14335 -> P13+1023 -> F9 -> 10239 B
        txtQueryAddr.setText("14335");
        invoke(panel, "translateAddress");
        System.out.println("14335 -> Hero: " + lblHero.getText() + " (Expected: 10239 B)");

        // 14336 -> LIMIT TRAP (14 > 13)
        txtQueryAddr.setText("14336");
        invoke(panel, "translateAddress");
        System.out.println("14336 -> Hero: " + lblHero.getText() + " (Expected: LIMIT TRAP)");

        // 8. Input validation checks
        System.out.println("\n[8] Input Validation:");
        String[] invalidPageSizes = {"0", "abc", "-5", "", "12.5"};
        for (String ps : invalidPageSizes) {
            txtPageSize.setText(ps);
            txtQueryAddr.setText("2500");
            invoke(panel, "translateAddress");
            System.out.println("Page Size '" + ps + "' -> Hero: " + lblHero.getText() + " | RAM Stats: " + lblRamStats.getText());
        }
        txtPageSize.setText("1024"); // restore

        String[] invalidAddrs = {"", "abc", "-5", "99999999999999999999"};
        for (String addr : invalidAddrs) {
            txtQueryAddr.setText(addr);
            invoke(panel, "translateAddress");
            System.out.println("Query Addr '" + addr + "' -> Hero: " + lblHero.getText());
        }

        // 9. Recovery: Remove / Add / Preset / Translate still work
        System.out.println("\n[9] Recovery after error:");
        txtQueryAddr.setText("2500");
        invoke(panel, "translateAddress");
        System.out.println("Valid translate after error -> Hero: " + lblHero.getText() + " (Expected: 1476 B)");

        invoke(panel, "removePageRow");
        System.out.println("Remove after error works -> Rows: " + model.getRowCount() + " (Expected: 13)");

        invoke(panel, "addPageRow");
        System.out.println("Add after error works -> Rows: " + model.getRowCount() + " (Expected: 14)");

        // 10. Frame Hopper launch data integrity
        System.out.println("\n[10] Frame Hopper launch data after Remove:");
        invoke(panel, "loadLargePreset");
        invoke(panel, "removePageRow"); // remove P13
        int capacity = (int) invokeReturn(panel, "getPageTableCapacity");
        @SuppressWarnings("unchecked")
        Map<Integer, Integer> mappings = (Map<Integer, Integer>) invokeReturn(panel, "getTableMappings");
        System.out.println("Page Table Capacity: " + capacity + " (Expected: 13)");
        System.out.println("Contains P13: " + mappings.containsKey(13) + " (Expected: false)");

        System.out.println("\n=== ALL REGRESSION CHECKS COMPLETED SUCCESSFULLY ===");
        System.exit(0);
    }

    private static Object invokeReturn(Object obj, String name) throws Exception {
        Method m = obj.getClass().getDeclaredMethod(name);
        m.setAccessible(true);
        return m.invoke(obj);
    }
}
