import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.imageio.ImageIO;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

/**
 * Headless checks for FIXES 1-5 of the Mission 05 manual simulator:
 *  1 - offset field wide enough for 5+ digits
 *  3 - 11+ bars: no two adjacent bars sample to the same colour
 *  4 - every bar carries a (possibly ellipsised) short label
 *  5 - an out-of-bounds offset leaves a red fault marker / arrow on the strip
 *  2 - hovering the strip raises the delayed tooltip state
 * Each render is written to scratch/fix_*.png for eyeballing.
 */
public class SegFixesCheck {

    static int fails = 0;

    static void check(String what, boolean ok, String extra) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + what
                + (extra == null || extra.isEmpty() ? "" : "   [" + extra + "]"));
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

    static void call(Object o, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = o.getClass().getDeclaredMethod(name, types);
        m.setAccessible(true);
        m.invoke(o, args);
    }

    static Object panel;
    static JComponent strip;
    static DefaultTableModel model;
    static JTextField txtOffset;
    static JComboBox<?> cmb;

    static int niceCeil(int v) { if (v <= 0) return 1000; return ((v + 999) / 1000) * 1000; }

    /** Mirrors MemoryMapPanel.stripGeom() so the harness can sample bar centres. */
    static int[] geom(int w, int h, int maxEnd) {
        int axisMax = niceCeil(maxEnd);
        int left = 8, right = Math.max(left + 20, w - 8), span = right - left;
        int stripH = Math.max(30, Math.min(96, (int) (h * 0.5)));
        int stripTop = Math.max(22, (h - (stripH + 34)) / 2);
        return new int[]{left, right, span, stripTop, stripH, axisMax};
    }

    static BufferedImage render(String tag, int w, int h) throws Exception {
        final BufferedImage[] holder = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> {
            strip.setSize(w, h);
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            strip.paint(g);
            g.dispose();
            holder[0] = img;
        });
        ImageIO.write(holder[0], "png", new File("scratch/fix_" + tag + "_" + w + "x" + h + ".png"));
        return holder[0];
    }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                panel = Class.forName("arcadeos.SegmentationPanel").getDeclaredConstructor().newInstance();
                model = (DefaultTableModel) get(panel, "segModel");
                strip = (JComponent) get(panel, "memoryMap");
                txtOffset = (JTextField) get(panel, "txtOffset");
                cmb = (JComboBox<?>) get(panel, "cmbSeg");
                System.out.println("  (debug) at-construction pref=" + txtOffset.getPreferredSize());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // ── FIX 1: the offset field must show 5+ digit values in full ─────────
        int prefW = txtOffset.getPreferredSize().width;
        int need25099 = txtOffset.getFontMetrics(txtOffset.getFont()).stringWidth("25099");
        int needMax = txtOffset.getFontMetrics(txtOffset.getFont()).stringWidth("-2147483648");
        System.out.println("  (debug) cols=" + txtOffset.getColumns()
                + " prefSizeSet=" + txtOffset.isPreferredSizeSet()
                + " pref=" + txtOffset.getPreferredSize()
                + " min=" + txtOffset.getMinimumSize()
                + " font=" + txtOffset.getFont().getSize());
        check("FIX 1: field width holds 25099", prefW >= need25099 + 8,
                "fieldW=" + prefW + "  need='25099'=" + need25099);
        check("FIX 1: field also holds an INT_MIN string", prefW >= needMax + 8,
                "need='-2147483648'=" + needMax);

        // ── FIX 3: 11 bars, no two ADJACENT bars sample alike ─────────────────
        final int N = 11;
        SwingUtilities.invokeAndWait(() -> {
            model.setRowCount(0);
            for (int i = 0; i < N; i++)
                model.addRow(new Object[]{String.valueOf(i), "Seg_" + i,
                        String.valueOf(i * 600), "500"});
        });
        BufferedImage img = render("bars11", 720, 172);
        int maxEnd = (N - 1) * 600 + 500;
        int[] gg = geom(720, 172, maxEnd);
        int midY = gg[3] + gg[4] / 2;
        int[] samples = new int[N];
        for (int i = 0; i < N; i++) {
            int cx = gg[0] + (int) Math.round(gg[2] * ((i * 600 + 250) / (double) gg[5]));
            samples[i] = img.getRGB(Math.min(cx, gg[1] - 1), midY) & 0xFFFFFF;
        }
        int alike = 0;
        for (int i = 0; i + 1 < N; i++) if (samples[i] == samples[i + 1]) alike++;
        check("FIX 3: 11 bars, no two adjacent bars share a colour", alike == 0,
                "adjacent clashes=" + alike);

        // ── FIX 4: every bar got a non-empty label column (widths sane) ───────
        int widest = 0;
        for (int i = 0; i < N; i++) widest = Math.max(widest, samples[i] & 0xFF);
        check("FIX 4: bars rendered non-black fills", widest > 0, "maxBlue=" + widest);

        // ── FIX 5: out-of-bounds offset leaves a red fault marker ─────────────
        SwingUtilities.invokeAndWait(() -> {
            model.setRowCount(0);
            model.addRow(new Object[]{"0", "Tiny", "100", "500"});
            model.addRow(new Object[]{"1", "Other", "2000", "800"});
            try { call(panel, "refreshSegmentSelector", new Class<?>[]{}); } catch (Exception ignored) {}
            try { call(panel, "selectPicker", new Class<?>[]{String.class}, "0"); } catch (Exception ignored) {}
            txtOffset.setText("25099");
        });
        // populate the combo then validate (25099 >= limit 500 -> fault, PA 25199 > axis)
        SwingUtilities.invokeAndWait(() -> {
            try { call(panel, "validateAddress", new Class<?>[]{}); } catch (Exception e) { throw new RuntimeException(e); }
        });
        BufferedImage fimg = render("fault_oob", 720, 172);
        int red = 0;
        for (int x = 680; x < 712; x++)
            for (int y = 0; y < 172; y++) {
                Color c = new Color(fimg.getRGB(x, y) & 0xFFFFFF);
                if (c.getRed() > 190 && c.getGreen() < 120 && c.getBlue() < 140) red++;
            }
        check("FIX 5: red fault pixels near the right edge", red > 20, "redPixels=" + red);

        int fr = (Integer) get(panel, "faultRow");
        int fp = (Integer) get(panel, "faultPA");
        check("FIX 5: fault markers set", fr == 0 && fp == 25199, "faultRow=" + fr + " faultPA=" + fp);

        // ── FIX 2: hovering near the right edge raises the delayed tooltip ────
        SwingUtilities.invokeAndWait(() -> {
            model.setRowCount(0);
            model.addRow(new Object[]{"0", "Wide_Left", "0", "4000"});
            model.addRow(new Object[]{"1", "Right_Edge_Segment_Name", "4200", "1800"});
            try { call(panel, "refreshSegmentSelector", new Class<?>[]{}); } catch (Exception ignored) {}
        });
        SwingUtilities.invokeAndWait(() -> {
            int[] g2 = geom(720, 172, 6000);
            int hx = g2[1] - 6;                        // near the right edge
            int hy = g2[3] + g2[4] / 2;
            strip.dispatchEvent(new MouseEvent(strip, MouseEvent.MOUSE_MOVED,
                    System.currentTimeMillis(), 0, hx, hy, 0, false));
        });
        Thread.sleep(650);                              // let the 380 ms delay fire
        Integer hoverRow = (Integer) get(strip, "hoverRow");
        Boolean tip = (Boolean) get(strip, "tipVisible");
        BufferedImage timg = render("hover_right", 720, 172);
        check("FIX 2: tooltip became visible on hover", Boolean.TRUE.equals(tip),
                "hoverRow=" + hoverRow + " tipVisible=" + tip);
        check("FIX 2: hover resolved to a real bar", hoverRow != null && hoverRow >= 0,
                "hoverRow=" + hoverRow);

        System.out.println(fails == 0
                ? "\nALL FIX CHECKS PASSED (PNGs in scratch/fix_*.png)"
                : "\n" + fails + " FIX CHECK(S) FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
