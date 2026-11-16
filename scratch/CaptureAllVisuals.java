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

public class CaptureAllVisuals {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "false");
        RetroTheme.applyGlobalUIDefaults();
        SwingUtilities.invokeAndWait(() -> {
            try {
                File outDir = new File("C:/Users/yun-tzu/.gemini/antigravity/brain/98f8e768-e4ca-4081-9dea-6c37c29bad67");
                if (!outDir.exists()) outDir.mkdirs();

                Map<Integer, Integer> table = new LinkedHashMap<>();
                table.put(0, 1);
                table.put(1, 4);
                table.put(2, 7);
                table.put(3, 10);
                table.put(4, 13);
                table.put(5, 0);

                // =========================================================
                // 1. Frame Hopper Dialog
                // =========================================================
                JFrame dummyParent = new JFrame();
                dummyParent.setUndecorated(true);
                dummyParent.setLocation(-2000, -2000);
                dummyParent.setVisible(true);

                FrameHopperDialog dlg = new FrameHopperDialog(dummyParent, 512, table);
                dlg.setModal(false);
                dlg.setSize(980, 740);
                dlg.validate();
                dlg.setVisible(true); // make peer realized

                // Paint initial state
                BufferedImage imgInit = new BufferedImage(980, 740, BufferedImage.TYPE_INT_ARGB);
                Graphics2D gInit = imgInit.createGraphics();
                dlg.paint(gInit);
                gInit.dispose();
                ImageIO.write(imgInit, "png", new File(outDir, "frame_hopper_arcade.png"));
                System.out.println("Saved frame_hopper_arcade.png, size=" + new File(outDir, "frame_hopper_arcade.png").length());

                // 2. Mid-Game State
                Field showTutorialF = FrameHopperDialog.class.getDeclaredField("showTutorial");
                showTutorialF.setAccessible(true);
                showTutorialF.set(dlg, false);

                Field playerRowF = FrameHopperDialog.class.getDeclaredField("playerRow");
                playerRowF.setAccessible(true);
                playerRowF.set(dlg, 5); // crossing the IRQ runway

                Field playerXF = FrameHopperDialog.class.getDeclaredField("playerX");
                playerXF.setAccessible(true);
                playerXF.set(dlg, 240f);

                Field scoreF = FrameHopperDialog.class.getDeclaredField("score");
                scoreF.setAccessible(true);
                scoreF.set(dlg, 250);

                Field completedF = FrameHopperDialog.class.getDeclaredField("completedQueries");
                completedF.setAccessible(true);
                completedF.set(dlg, 1);

                Method updateLoopM = FrameHopperDialog.class.getDeclaredMethod("updateGameLoop");
                updateLoopM.setAccessible(true);
                for (int i = 0; i < 45; i++) {
                    updateLoopM.invoke(dlg);
                }

                BufferedImage imgMid = new BufferedImage(980, 740, BufferedImage.TYPE_INT_ARGB);
                Graphics2D gMid = imgMid.createGraphics();
                dlg.paint(gMid);
                gMid.dispose();
                ImageIO.write(imgMid, "png", new File(outDir, "midgame_hopper_preview.png"));
                System.out.println("Saved midgame_hopper_preview.png, size=" + new File(outDir, "midgame_hopper_preview.png").length());
                dlg.dispose();

                // =========================================================
                // 3. PagingPanel: Normal Translated State
                // =========================================================
                JFrame pFrame = new JFrame();
                pFrame.setUndecorated(true);
                pFrame.setLocation(-2000, -2000);
                PagingPanel panel = new PagingPanel();
                pFrame.setContentPane(panel);
                pFrame.setSize(1100, 680);
                pFrame.validate();
                pFrame.setVisible(true);

                Method translateM = PagingPanel.class.getDeclaredMethod("translateAddress");
                translateM.setAccessible(true);
                Method largePresetM = PagingPanel.class.getDeclaredMethod("loadLargePreset");
                largePresetM.setAccessible(true);

                Field txtAddrF = PagingPanel.class.getDeclaredField("txtQueryAddr");
                txtAddrF.setAccessible(true);
                JTextField txtAddr = (JTextField) txtAddrF.get(panel);

                largePresetM.invoke(panel);
                txtAddr.setText("2500");
                translateM.invoke(panel);

                pFrame.validate();
                pFrame.repaint();

                BufferedImage imgPaging = new BufferedImage(1100, 680, BufferedImage.TYPE_INT_ARGB);
                Graphics2D gPaging = imgPaging.createGraphics();
                panel.paint(gPaging);
                gPaging.dispose();
                ImageIO.write(imgPaging, "png", new File(outDir, "paging_panel_button.png"));
                System.out.println("Saved paging_panel_button.png, size=" + new File(outDir, "paging_panel_button.png").length());

                // =========================================================
                // 4. PagingPanel: Page Fault / Out of bounds
                // =========================================================
                txtAddr.setText("99999");
                translateM.invoke(panel);
                pFrame.validate();
                pFrame.repaint();

                BufferedImage imgFault = new BufferedImage(1100, 680, BufferedImage.TYPE_INT_ARGB);
                Graphics2D gFault = imgFault.createGraphics();
                panel.paint(gFault);
                gFault.dispose();
                ImageIO.write(imgFault, "png", new File(outDir, "paging_fault_preview.png"));
                System.out.println("Saved paging_fault_preview.png, size=" + new File(outDir, "paging_fault_preview.png").length());

                pFrame.dispose();
                dummyParent.dispose();

                // =========================================================
                // 5. Simulator Container Header Preview (Full OS View with Tab 03 Active)
                // =========================================================
                JFrame simFrame = new JFrame();
                simFrame.setUndecorated(true);
                simFrame.setLocation(-2000, -2000);
                arcadeos.SimulatorContainerPanel simPanel = new arcadeos.SimulatorContainerPanel(null);
                simFrame.setContentPane(simPanel);
                simFrame.setSize(1200, 720);
                simFrame.validate();
                simFrame.setVisible(true);

                simPanel.selectTab(2);

                simFrame.validate();
                simFrame.repaint();

                BufferedImage imgSim = new BufferedImage(1200, 720, BufferedImage.TYPE_INT_ARGB);
                Graphics2D gSim = imgSim.createGraphics();
                simPanel.paint(gSim);
                gSim.dispose();
                ImageIO.write(imgSim, "png", new File(outDir, "simulator_header_preview.png"));
                System.out.println("Saved simulator_header_preview.png, size=" + new File(outDir, "simulator_header_preview.png").length());
                simFrame.dispose();

                System.out.println("=== ALL REALIZED SCREENSHOTS CAPTURED SUCCESSFULLY ===");
                System.exit(0);
            } catch (Throwable t) {
                t.printStackTrace();
                System.exit(1);
            }
        });
    }
}
