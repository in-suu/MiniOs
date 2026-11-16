import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.swing.JComboBox;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/** Headless checks for Part A step A1 (result card messages + two-step reveal). */
public class SegA1Check {

    static int fails = 0;

    static void check(String what, boolean ok, String extra) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + what + (extra == null || extra.isEmpty() ? "" : "   [" + extra + "]"));
        if (!ok) fails++;
    }

    static Field field(Object o, String name) throws Exception {
        Class<?> c = o.getClass();
        while (c != null) {
            try { Field x = c.getDeclaredField(name); x.setAccessible(true); return x; }
            catch (NoSuchFieldException e) { c = c.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }

    static Object get(Object o, String n) throws Exception { return field(o, n).get(o); }
    static void set(Object o, String n, Object v) throws Exception { field(o, n).set(o, v); }

    static Object panel, card;
    static DefaultTableModel model;
    static JTextField off;
    static JComboBox<?> pick;
    static JTextArea log;
    static Method validate;

    static void selectSeg(int num) throws Exception {
        for (int i = 0; i < pick.getItemCount(); i++) {
            if (pick.getItemAt(i).toString().startsWith(num + ":")) { pick.setSelectedIndex(i); return; }
        }
    }

    /** Runs a validate with the given offset text and returns the card title. */
    static String run(String offsetText) throws Exception {
        off.setText(offsetText);
        validate.invoke(panel);
        return String.valueOf(get(card, "title"));
    }

    static String line2() throws Exception { return String.valueOf(get(card, "line2")); }
    static String line1() throws Exception { return String.valueOf(get(card, "line1")); }

    public static void main(String[] args) throws Exception {
        panel = Class.forName("arcadeos.SegmentationPanel").getDeclaredConstructor().newInstance();
        model = (DefaultTableModel) get(panel, "segModel");
        off = (JTextField) get(panel, "txtOffset");
        pick = (JComboBox<?>) get(panel, "cmbSeg");
        log = (JTextArea) get(panel, "txtLog");
        card = get(panel, "resultCard");
        validate = panel.getClass().getDeclaredMethod("validateAddress");
        validate.setAccessible(true);

        // Preset: 0 Arcade_Sprites 1400/400 ; 1 Audio_Engine 6300/300 ; 2 High_Scores 4300/500
        selectSeg(0);

        check("valid: offset 250 -> VALID -> PA 1650",
                "VALID -> PA 1650".equals(run("250")), run("250"));
        check("valid reveals base first", "base  1400".equals(line1()), line1());
        check("valid reveals the sum second", "1400  +  offset 250  =  PA 1650".equals(line2()), line2());
        check("valid logs a trace", log.getText().contains("VALIDATION SUCCESS"), null);

        check("offset = limit-1 -> VALID -> PA 1799",
                "VALID -> PA 1799".equals(run("399")), run("399"));

        selectSeg(0);
        check("offset = limit -> FAULT: offset >= limit",
                "FAULT: offset >= limit".equals(run("400")), run("400"));

        check("negative offset -> NEGATIVE OFFSET",
                "NEGATIVE OFFSET".equals(run("-5")), run("-5"));

        check("blank input -> ENTER A NUMBER",
                "ENTER A NUMBER".equals(run("   ")), run("   "));

        check("non-numeric input -> ENTER A NUMBER",
                "ENTER A NUMBER".equals(run("abc")), run("abc"));

        // limit 0 segment: offset 0 must fault (0 >= 0), no crash.
        model.addRow(new Object[]{"9", "Zero_Seg", "5000", "0"});
        selectSeg(9);
        check("limit 0, offset 0 -> FAULT (no crash)",
                "FAULT: offset >= limit".equals(run("0")), run("0"));

        // overlapping segments must still translate without crashing.
        model.addRow(new Object[]{"10", "Over_A", "100", "500"});
        model.addRow(new Object[]{"11", "Over_B", "300", "500"});
        selectSeg(10);
        check("overlapping segments still translate",
                "VALID -> PA 200".equals(run("100")), run("100"));

        // empty table
        model.setRowCount(0);
        check("empty table -> ILLEGAL SEGMENT",
                "ILLEGAL SEGMENT".equals(run("10")), run("10"));

        // 20+ segments
        for (int i = 0; i < 25; i++) {
            model.addRow(new Object[]{String.valueOf(i), "Seg_" + i,
                    String.valueOf(i * 1000), String.valueOf(500)});
        }
        selectSeg(24);
        check("20+ segments: renderer/model stays alive, last row translates",
                "VALID -> PA 24500".equals(run("500")), run("500"));
        check("20+ segments: row count is 25", model.getRowCount() == 25, "rows=" + model.getRowCount());

        System.out.println(fails == 0 ? "\nALL A1 CHECKS PASSED" : "\n" + fails + " A1 CHECK(S) FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
