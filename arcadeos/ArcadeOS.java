package arcadeos;

import game.snake.SnakeGameDialog;
import java.awt.*;
import javax.swing.*;

/**
 * Main Application Frame for ArcadeOS.
 * Coordinates transitions across:
 * 1. Screen 1: LoginPanel (Player Login / Arcade Security Gate)
 * 2. Screen 2: MainMenuPanel (Kawaii 8-Bit Arcade Edition)
 * 3. Screen 3: ArchivesPanel (8 Archive Cards Manuals)
 * 4. Screen 4: SimulatorContainerPanel (8 Modular Simulators)
 */
public class ArcadeOS extends JFrame {
    public static final String SCREEN_SYSTEM_INIT = "SCREEN_SYSTEM_INIT";
    public static final String SCREEN_LOGIN = "SCREEN_LOGIN";
    public static final String SCREEN_MAIN_MENU = "SCREEN_MAIN_MENU";
    public static final String SCREEN_ARCHIVES = "SCREEN_ARCHIVES";
    public static final String SCREEN_SIMULATOR = "SCREEN_SIMULATOR";
    public static final String SCREEN_QUIZ = "SCREEN_QUIZ";

    private JPanel rootCardPanel;
    private CardLayout rootCardLayout;

    private SystemInitPanel systemInitPanel;
    private LoginPanel loginPanel;
    private MainMenuPanel mainMenuPanel;
    private ArchivesPanel archivesPanel;
    private SimulatorContainerPanel simulatorPanel;
    private QuizPanel quizPanel;

    public ArcadeOS() {
        setTitle("Retro Arcade OS Prototype - MINI // OS [KAWAII 8-BIT ARCADE EDITION]");
        setSize(1280, 860);
        setMinimumSize(new Dimension(1080, 720));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(RetroTheme.BG_DARK);

        // Apply theme defaults
        RetroTheme.applyGlobalUIDefaults();

        rootCardLayout = new CardLayout();
        rootCardPanel = new JPanel(rootCardLayout);
        rootCardPanel.setBackground(RetroTheme.BG_DARK);

        // Initialize all Screens
        systemInitPanel = new SystemInitPanel(this);
        loginPanel = new LoginPanel(this);
        mainMenuPanel = new MainMenuPanel(this);
        archivesPanel = new ArchivesPanel(this);
        simulatorPanel = new SimulatorContainerPanel(this);
        quizPanel = new QuizPanel(this);

        rootCardPanel.add(systemInitPanel, SCREEN_SYSTEM_INIT);
        rootCardPanel.add(loginPanel, SCREEN_LOGIN);
        rootCardPanel.add(mainMenuPanel, SCREEN_MAIN_MENU);
        rootCardPanel.add(archivesPanel, SCREEN_ARCHIVES);
        rootCardPanel.add(simulatorPanel, SCREEN_SIMULATOR);
        rootCardPanel.add(quizPanel, SCREEN_QUIZ);

        add(rootCardPanel, BorderLayout.CENTER);

        // Start on SYSTEM INITIALIZATION (first boot) — Figma Screen 1
        showScreen(SCREEN_SYSTEM_INIT);
    }

    public void showScreen(String name) {
        if (name.equals(SCREEN_ARCHIVES) && archivesPanel != null) {
            archivesPanel.updateCounters();
        }
        if (name.equals(SCREEN_SIMULATOR) && simulatorPanel != null) {
            simulatorPanel.refreshScore();
        }
        rootCardLayout.show(rootCardPanel, name);
        rootCardPanel.revalidate();
        rootCardPanel.repaint();
    }

    public void openSimulatorTab(int tabIndex) {
        showScreen(SCREEN_SIMULATOR);
        if (simulatorPanel != null) {
            simulatorPanel.selectTab(tabIndex);
        }
    }

    public void loadManualExample(int manualIndex) {
        showScreen(SCREEN_SIMULATOR);
        if (simulatorPanel != null) {
            simulatorPanel.loadManualExample(manualIndex);
        }
    }

    public void loadPagingManualExample() {
        loadManualExample(2);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            RetroTheme.applyGlobalUIDefaults();
            ArcadeOS os = new ArcadeOS();
            if (args != null && args.length > 0) {
                if (args[0].equalsIgnoreCase("--hopper") || args[0].equalsIgnoreCase("hopper")) {
                    os.openSimulatorTab(2);
                    os.setVisible(true);
                    FrameHopperDialog dlg = new FrameHopperDialog(os, 1024, null);
                    dlg.setVisible(true);
                    return;
                } else if (args[0].equalsIgnoreCase("--seg") || args[0].equalsIgnoreCase("segment")) {
                    os.openSimulatorTab(4);
                    os.setVisible(true);
                    SnakeGameDialog.launchDefault(os);
                    return;
                } else if (args[0].equalsIgnoreCase("--paging") || args[0].equalsIgnoreCase("paging")) {
                    os.openSimulatorTab(2);
                    os.setVisible(true);
                    return;
                } else if (args[0].equalsIgnoreCase("--menu") || args[0].equalsIgnoreCase("menu")) {
                    os.showScreen(SCREEN_MAIN_MENU);
                    os.setVisible(true);
                    return;
                } else if (args[0].equalsIgnoreCase("--archives") || args[0].equalsIgnoreCase("archives") || args[0].equalsIgnoreCase("manual")) {
                    os.showScreen(SCREEN_ARCHIVES);
                    os.setVisible(true);
                    return;
                } else if (args[0].equalsIgnoreCase("--tapper") || args[0].equalsIgnoreCase("tapper")) {
                    os.openSimulatorTab(0);
                    os.setVisible(true);
                    KernelTapperDialog dlg = new KernelTapperDialog(os, "FCFS", null);
                    dlg.setVisible(true);
                    return;
                } else if (args[0].equalsIgnoreCase("--defender") || args[0].equalsIgnoreCase("defender") || args[0].equalsIgnoreCase("--fault")) {
                    os.openSimulatorTab(3);
                    os.setVisible(true);
                    FaultDefenderDialog dlg = new FaultDefenderDialog(os, null);
                    dlg.setVisible(true);
                    return;
                }
            }
            os.setVisible(true);
        });
    }
}
