import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.table.DefaultTableModel;

/**
 * Headless smoke test for Part A step A2 (the single full-width memory strip).
 * It mutates the segment table into the edge cases from the spec, paints the
 * strip at several panel sizes and checks that painting never throws. Each
 * render is also written to scratch/seg_a2_*.png so the visuals can be eyeballed.
 */
public class SegA2Check {

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

    static DefaultTableModel model;
    static JComponent strip;
    static Object panel;

    /** Paints the strip at w x h; returns null on success or the error text. */
    static String render(String tag, int w, int h) {
        try {
            strip.setSize(w, h);
            BufferedImage img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            strip.paint(g);
            g.dispose();
            ImageIO.write(img, "png", new File("scratch/seg_a2_" + tag + "_" + w + "x" + h + ".png"));
            return null;
        } catch (Throwable t) {
            return t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    static void scenario(String name, Runnable fill) {
        model.setRowCount(0);
        if (fill != null) fill.run();
        String e1 = render(name, 720, 160);
        String e2 = render(name, 320, 96);      // minimum-ish size, must still be clean
        check("A2 render: " + name + " (720x160)",
                e1 == null, e1);
        check("A2 render: " + name + " (320x96)",
                e2 == null, e2);
    }

    public static void main(String[] args) throws Exception {
        panel = Class.forName("arcadeos.SegmentationPanel").getDeclaredConstructor().newInstance();
        model = (DefaultTableModel) get(panel, "segModel");
        strip = (JComponent) get(panel, "memoryMap");

        // 1. the built-in small preset (3 segments, a wide free gap in the middle)
        scenario("preset", () -> {
            model.addRow(new Object[]{"0", "Arcade_Sprites", "1400", "400"});
            model.addRow(new Object[]{"1", "Audio_Engine", "6300", "300"});
            model.addRow(new Object[]{"2", "High_Scores", "4300", "500"});
        });

        // 2. empty table
        scenario("empty", null);

        // 3. two overlapping segments (must be shaded red, must not crash)
        scenario("overlap", () -> {
            model.addRow(new Object[]{"0", "Over_A", "100", "500"});
            model.addRow(new Object[]{"1", "Over_B", "300", "500"});
        });

        // 4. a zero-limit segment (ignored by the strip, must not crash)
        scenario("zerolimit", () -> {
            model.addRow(new Object[]{"0", "Zero_Seg", "5000", "0"});
            model.addRow(new Object[]{"1", "Real", "100", "900"});
        });

        // 5. a single very wide segment
        scenario("wide", () -> model.addRow(new Object[]{"0", "Big_One", "0", "8000"}));

        // 6. 25 segments (the strip must stay a single readable row)
        scenario("many25", () -> {
            for (int i = 0; i < 25; i++) {
                model.addRow(new Object[]{String.valueOf(i), "Seg_" + i,
                        String.valueOf(i * 1000), String.valueOf(500)});
            }
        });

        System.out.println(fails == 0
                ? "\nALL A2 CHECKS PASSED (PNGs in scratch/seg_a2_*.png)"
                : "\n" + fails + " A2 CHECK(S) FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
