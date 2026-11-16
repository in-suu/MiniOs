package scratch;

import arcadeos.FrameHopperDialog;
import arcadeos.PagingPanel;
import arcadeos.RetroTheme;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.*;

public class CaptureFrameHopper {
    public static void main(String[] args) throws Exception {
        RetroTheme.applyGlobalUIDefaults();
        SwingUtilities.invokeAndWait(() -> {
            try {
                Map<Integer, Integer> table = new LinkedHashMap<>();
                table.put(0, 1);
                table.put(1, 4);
                table.put(2, 7);
                table.put(3, 10);
                table.put(4, 13);
                table.put(5, 0);

                // 1. Capture FrameHopperDialog
                FrameHopperDialog dlg = new FrameHopperDialog(null, 512, table);
                dlg.setSize(960, 720);
                dlg.doLayout();
                dlg.validate();

                BufferedImage img = new BufferedImage(960, 720, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = img.createGraphics();
                dlg.paint(g2);
                g2.dispose();

                File outDir = new File("C:/Users/yun-tzu/.gemini/antigravity/brain/98f8e768-e4ca-4081-9dea-6c37c29bad67");
                if (!outDir.exists()) outDir.mkdirs();
                File outFile = new File(outDir, "frame_hopper_arcade.png");
                ImageIO.write(img, "png", outFile);
                System.out.println("Saved Frame Hopper screenshot to: " + outFile.getAbsolutePath());

                // 2. Capture PagingPanel with active translation (2500)
                PagingPanel panel = new PagingPanel();
                panel.setSize(1100, 620);
                panel.doLayout();
                panel.validate();

                // Trigger translateAddress using reflection
                try {
                    Method m = PagingPanel.class.getDeclaredMethod("translateAddress");
                    m.setAccessible(true);
                    m.invoke(panel);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                BufferedImage pImg = new BufferedImage(1100, 620, BufferedImage.TYPE_INT_ARGB);
                Graphics2D pg2 = pImg.createGraphics();
                panel.paint(pg2);
                pg2.dispose();
                File pOutFile = new File(outDir, "paging_panel_button.png");
                ImageIO.write(pImg, "png", pOutFile);
                System.out.println("Saved PagingPanel screenshot to: " + pOutFile.getAbsolutePath());

                dlg.dispose();
                System.exit(0);
            } catch (Throwable t) {
                t.printStackTrace();
                System.exit(1);
            }
        });
    }
}
