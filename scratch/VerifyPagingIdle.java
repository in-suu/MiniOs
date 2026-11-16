package scratch;

import arcadeos.PagingPanel;
import arcadeos.RetroTheme;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Runtime checks for the PagingPanel idle-label / fault / trap / input behaviour. */
public class VerifyPagingIdle {

    private static JTextField txtAddr;
    private static JTextField txtSize;
    private static JLabel lblHero;
    private static JLabel lblDecomp;
    private static JEditorPane formulaPane;
    private static DefaultTableModel model;
    private static Method translate;
    private static Method addRow;
    private static Method smallPreset;
    private static PagingPanel panel;

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        RetroTheme.applyGlobalUIDefaults();
        SwingUtilities.invokeAndWait(() -> {
            try {
                run();
            } catch (Throwable t) {
                t.printStackTrace();
                failures++;
            }
        });
        System.out.println(failures == 0 ? "\nALL PAGING CHECKS PASSED" : "\n" + failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void run() throws Exception {
        panel = new PagingPanel();
        panel.setSize(1100, 640);
        panel.doLayout();
        panel.validate();

        txtAddr = (JTextField) field("txtQueryAddr");
        txtSize = (JTextField) field("txtPageSize");
        lblHero = (JLabel) field("lblResultHero");
        lblDecomp = (JLabel) field("lblDecompDetails");
        formulaPane = (JEditorPane) field("formulaPane");
        model = (DefaultTableModel) field("pageTableModel");

        translate = method("translateAddress");
        addRow = method("addPageRow");
        smallPreset = method("loadSmallPreset");

        // ---------- 1. Idle labels follow a non-default page size (2048) ----------
        txtSize.setText("2048");
        // A document listener fires on setText, so the idle state should already be refreshed.
        String step1 = step1Text();
        check("idle @2048 breakdown step 1", step1.contains("2048 Bytes"), step1);
        check("idle @2048 decomp label", lblDecomp.getText().contains("2048 B"), lblDecomp.getText());

        txtSize.setText("512");
        check("idle @512 breakdown step 1", step1Text().contains("512 Bytes"), step1Text());

        // ---------- 2. Build a 21-row table (pages 0..20) at 1024 B ----------
        smallPreset.invoke(panel);
        check("small preset page size", txtSize.getText().equals("1024"), txtSize.getText());
        int guard = 0;
        while (model.getRowCount() < 21 && guard++ < 60) {
            addRow.invoke(panel);
        }
        check("table grew to 21 rows", model.getRowCount() == 21, "rows=" + model.getRowCount());
        check("page 20 is unmapped", statusOf(20).contains("UNMAPPED"), "row20 status=" + statusOf(20));

        // ---------- 3. In-range fault vs out-of-range limit trap ----------
        checkOutcome("20480 (page 20, unmapped)", "20480", "PAGE FAULT");
        checkOutcome("21503 (page 20, last byte)", "21503", "PAGE FAULT");
        checkOutcome("21504 (page 21, beyond limit)", "21504", "LIMIT TRAP");

        // ---------- 4. Input validation ----------
        txtSize.setText("1024");
        checkOutcome("empty address", "", "INPUT ERROR");
        checkOutcome("abc address", "abc", "INPUT ERROR");
        checkOutcome("negative address", "-5", "INPUT ERROR");
        checkOutcome("page size 0", "2500", "INPUT ERROR", "0");
        check("page size 0 breakdown step 1 is not stale 1024",
                !step1Text().contains("1024"), step1Text());

        // ---------- 5. History re-run ----------
        txtSize.setText("1024");
        txtAddr.setText("2500");
        translate.invoke(panel);
        String first = lblHero.getText();
        JList<?> history = (JList<?>) field("historyList");
        check("history recorded one translation", history.getModel().getSize() >= 1,
                "size=" + history.getModel().getSize());
        StringBuilder histDump = new StringBuilder();
        for (int i = 0; i < history.getModel().getSize(); i++) {
            histDump.append("\n    [").append(i).append("] ").append(history.getModel().getElementAt(i));
        }
        System.out.println("  history entries:" + histDump);

        txtAddr.setText("99999");
        translate.invoke(panel);
        // Newest entry is first, so the 2500 row now sits at index 1.
        int rerunIndex = -1;
        for (int i = 0; i < history.getModel().getSize(); i++) {
            if (String.valueOf(history.getModel().getElementAt(i)).startsWith("2500 ->")) {
                rerunIndex = i;
                break;
            }
        }
        check("history contains the 2500 row", rerunIndex >= 0, "index=" + rerunIndex);
        history.clearSelection();
        history.setSelectedIndex(rerunIndex);
        check("history click re-runs the entry", lblHero.getText().equals(first),
                "after re-run=" + lblHero.getText() + " expected=" + first);

        // ---------- 6. No horizontal scrollbar under Recent translations ----------
        JScrollPane histScroll = (JScrollPane) field("historyScroll");
        check("history has no horizontal scrollbar",
                histScroll.getHorizontalScrollBarPolicy() == ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
                "policy=" + histScroll.getHorizontalScrollBarPolicy());
    }

    private static void checkOutcome(String label, String addr, String expected) throws Exception {
        checkOutcome(label, addr, expected, null);
    }

    private static void checkOutcome(String label, String addr, String expected, String size) throws Exception {
        if (size != null) txtSize.setText(size);
        txtAddr.setText(addr);
        translate.invoke(panel);
        check(label, lblHero.getText().contains(expected), "hero=" + lblHero.getText());
    }

    private static String statusOf(int page) {
        for (int r = 0; r < model.getRowCount(); r++) {
            Object p = model.getValueAt(r, 0);
            if (p != null && p.toString().replace(">", "").trim().equals(String.valueOf(page))) {
                return String.valueOf(model.getValueAt(r, 2));
            }
        }
        return "<missing>";
    }

    private static String step1Text() {
        String html = formulaPane.getText();
        int i = html.indexOf("1. Page Size");
        return i < 0 ? html : html.substring(i, Math.min(html.length(), i + 160));
    }

    private static Object field(String name) throws Exception {
        Field f = PagingPanel.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(panel);
    }

    private static Method method(String name) throws Exception {
        Method m = PagingPanel.class.getDeclaredMethod(name);
        m.setAccessible(true);
        return m;
    }

    private static void check(String label, boolean ok, String detail) {
        System.out.printf("%s %s%s%n", ok ? "PASS" : "FAIL", label, ok ? "" : "  -> " + detail);
        if (!ok) failures++;
    }
}
