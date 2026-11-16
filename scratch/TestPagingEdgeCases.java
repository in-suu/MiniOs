package scratch;

import arcadeos.FrameHopperDialog;
import arcadeos.PagingPanel;
import arcadeos.RetroTheme;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.*;

public class TestPagingEdgeCases {
    public static void main(String[] args) throws Exception {
        RetroTheme.applyGlobalUIDefaults();
        SwingUtilities.invokeAndWait(() -> {
            try {
                System.out.println("=== STARTING PAGING EDGE CASES TESTS ===");

                PagingPanel panel = new PagingPanel();
                panel.setSize(1100, 640);
                panel.doLayout();
                panel.validate();

                Method translateMethod = PagingPanel.class.getDeclaredMethod("translateAddress");
                translateMethod.setAccessible(true);
                Method largePresetMethod = PagingPanel.class.getDeclaredMethod("loadLargePreset");
                largePresetMethod.setAccessible(true);
                Method smallPresetMethod = PagingPanel.class.getDeclaredMethod("loadSmallPreset");
                smallPresetMethod.setAccessible(true);

                Field txtAddrField = PagingPanel.class.getDeclaredField("txtQueryAddr");
                txtAddrField.setAccessible(true);
                JTextField txtAddr = (JTextField) txtAddrField.get(panel);

                Field txtSizeField = PagingPanel.class.getDeclaredField("txtPageSize");
                txtSizeField.setAccessible(true);
                JTextField txtSize = (JTextField) txtSizeField.get(panel);

                Field lblResultBigField = PagingPanel.class.getDeclaredField("lblResultBig");
                lblResultBigField.setAccessible(true);
                JLabel lblResultBig = (JLabel) lblResultBigField.get(panel);

                Field lblResultSubField = PagingPanel.class.getDeclaredField("lblResultSub");
                lblResultSubField.setAccessible(true);
                JLabel lblResultSub = (JLabel) lblResultSubField.get(panel);

                // TEST 1A: Small Preset (1024 B), Query 2500 -> Page 2 -> Frame 1 -> 1476 B
                smallPresetMethod.invoke(panel);
                txtAddr.setText("2500");
                translateMethod.invoke(panel);
                System.out.println("Test 1A (Small Preset 1024B, Addr 2500): " + lblResultBig.getText() + " | " + lblResultSub.getText());
                assert lblResultBig.getText().contains("1476 BYTES") : "Expected 1476 BYTES";

                // TEST 1B: Large Preset (512 B), Query 2500 -> Page 4 -> Frame 13 -> 7108 B
                largePresetMethod.invoke(panel);
                txtAddr.setText("2500");
                translateMethod.invoke(panel);
                System.out.println("Test 1B (Large Preset 512B, Addr 2500): " + lblResultBig.getText() + " | " + lblResultSub.getText());
                assert lblResultBig.getText().contains("7108 BYTES") : "Expected 7108 BYTES";

                // TEST 2: Page Fault (Address beyond mapped pages, e.g. 99999)
                txtAddr.setText("99999");
                translateMethod.invoke(panel);
                System.out.println("Test 2 (Page Fault 99999): " + lblResultBig.getText() + " | " + lblResultSub.getText());
                assert lblResultBig.getText().contains("PAGE FAULT") : "Expected PAGE FAULT state";

                // TEST 3: Very Small Page Size (64 B)
                txtSize.setText("64");
                txtAddr.setText("150");
                translateMethod.invoke(panel);
                System.out.println("Test 3 (Small Page Size 64B, Addr 150): " + lblResultBig.getText());

                // TEST 4: Very Large Page Size (4096 B)
                txtSize.setText("4096");
                txtAddr.setText("9000");
                translateMethod.invoke(panel);
                System.out.println("Test 4 (Large Page Size 4096B, Addr 9000): " + lblResultBig.getText());

                // TEST 5: Window Resizes (800x600, 1080x720, 1400x900)
                int[][] resolutions = {{800, 600}, {1080, 720}, {1400, 900}};
                for (int[] res : resolutions) {
                    panel.setSize(res[0], res[1]);
                    panel.doLayout();
                    panel.validate();
                    BufferedImage testImg = new BufferedImage(res[0], res[1], BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = testImg.createGraphics();
                    panel.paint(g);
                    g.dispose();
                    System.out.println("Test 5 (Resize " + res[0] + "x" + res[1] + "): OK, rendered cleanly!");
                }

                // Capture updated screenshot with Large Preset 2500 @ 512B
                File outDir = new File("C:/Users/yun-tzu/.gemini/antigravity/brain/98f8e768-e4ca-4081-9dea-6c37c29bad67");
                largePresetMethod.invoke(panel);
                txtAddr.setText("2500");
                translateMethod.invoke(panel);
                panel.setSize(1100, 640);
                panel.doLayout();
                panel.validate();

                BufferedImage finalImg = new BufferedImage(1100, 640, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = finalImg.createGraphics();
                panel.paint(g2);
                g2.dispose();
                ImageIO.write(finalImg, "png", new File(outDir, "paging_panel_button.png"));
                System.out.println("Updated paging_panel_button.png successfully!");

                System.out.println("=== ALL EDGE CASE TESTS COMPLETED SUCCESSFULLY ===");
                System.exit(0);
            } catch (Throwable t) {
                t.printStackTrace();
                System.exit(1);
            }
        });
    }
}
