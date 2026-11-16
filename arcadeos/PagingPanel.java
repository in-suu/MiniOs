package arcadeos;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

/**
 * Screen: MISSION 03 PAGING TRANSLATION WORKBENCH (V3)
 * Full Overhaul addressing all Priority 1 Bugs, Priority 2 Clarity, and Priority 3 Polish:
 * 1. Fixed Layout Weights (30% / 38% / 32%) with setMinimumSize on all panels (no pushing or horizontal scrollbars).
 * 2. Distinct Fault Types: PAGE FAULT (unmapped page in table, valid=0) vs INVALID ADDRESS TRAP (exceeds table limit).
 * 3. Robust Inline Input Validation: empty, negative, non-numeric, zero page size handled gracefully with zero exceptions.
 * 4. Hero Values (36px bold) in Decomposition boxes and Result Card with small labels above.
 * 5. Full-width line for Step 6 formula so resolved equations never wrap or split.
 * 6. Obvious table selection: full-row cyan tint, '>' indicator, and no false row-0 selection on faults.
 * 7. Colored status: [V] VALID in bright green, [X] UNMAPPED in bright red.
 * 8. Column-Major Physical Memory Stack (F00-F07 left, F08-F15 right) with raised 10-11px base addresses and separate '>' target arrow.
 * 9. Translation History List and frame stats share the Physical RAM column.
 */
public class PagingPanel extends JPanel {

    private int pageSize = 1024;
    private JTextField txtPageSize, txtQueryAddr;
    private DefaultTableModel pageTableModel;
    private JTable pageTable;
    private int activeHighlightRow = -1;

    // Visual Result & Hero Value Components
    private JPanel resultCard;
    private JLabel lblResultHero;
    private JTextArea lblResultSub;
    private JTextArea lblResultRecovery;

    private JLabel lblHeroPage;
    private JLabel lblHeroOffset;
    private JLabel lblDecompTag;
    private JLabel lblDecompDetails;
    private JEditorPane formulaPane;

    // Legend & Hint Components
    private CardLayout legendCardLayout;
    private JPanel legendCard;
    private JLabel lblTableHint;
    private javax.swing.Timer hintTimer;

    // Physical Memory View (Column-Major 2-Column Frames Stack)
    private PhysicalMemoryView physicalMemView;
    private JLabel lblRamStats;

    // Translation History List (deduplicated by address, newest first)
    private DefaultListModel<String> historyListModel;
    private JList<String> historyList;
    private JScrollPane historyScroll;
    private JLabel historyEmptyLabel;
    private JPanel historyPanel;
    private JPanel historyContent;
    private CardLayout historyCardLayout;
    private final List<HistoryEntry> historyEntries = new ArrayList<>();
    private static final int HISTORY_LIMIT = 5;

    // Result card text: kept as raw HTML and re-wrapped to the card's real width on resize
    private JPanel resultSubStack;
    private String resultSubHtml = "Enter address above & click TRANSLATE";
    private String resultRecoveryHtml = "";

    // Shake animation timer for errors
    private javax.swing.Timer shakeTimer;
    private int shakeTicks = 0;

    /** One translation-history row. Repeats of the same address are merged with a count. */
    private static class HistoryEntry {
        final long la; long p, d; int f; long pa; String outcome; int count = 1;
        HistoryEntry(long la, long p, long d, int f, long pa, String outcome) {
            this.la = la; this.p = p; this.d = d; this.f = f; this.pa = pa; this.outcome = outcome;
        }
    }

    public PagingPanel() {
        setBackground(RetroTheme.BG_DARK);
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        // 1. Top Configuration Toolbar
        add(createTopToolbar(), BorderLayout.NORTH);

        // 2. 3-Column Center Workbench: table/legend | analytics | RAM/history
        JPanel centerPanel = new JPanel(new LayoutManager() {
            @Override public void addLayoutComponent(String name, Component comp) {}
            @Override public void removeLayoutComponent(Component comp) {}
            @Override public Dimension preferredLayoutSize(Container parent) { return new Dimension(860, 480); }
            @Override public Dimension minimumLayoutSize(Container parent) { return new Dimension(0, 0); }
            @Override public void layoutContainer(Container parent) {
                Insets in = parent.getInsets();
                int w = parent.getWidth() - in.left - in.right;
                int h = parent.getHeight() - in.top - in.bottom;
                if (parent.getComponentCount() < 3) return;
                int gap = 10;
                int availW = Math.max(0, w - gap * 2);
                int w0 = (int) Math.round(availW * 0.30);
                int w1 = (int) Math.round(availW * 0.38);
                int w2 = availW - w0 - w1;

                Component c0 = parent.getComponent(0);
                Component c1 = parent.getComponent(1);
                Component c2 = parent.getComponent(2);

                c0.setBounds(in.left, in.top, w0, h);
                c1.setBounds(in.left + w0 + gap, in.top, w1, h);
                c2.setBounds(in.left + w0 + gap + w1 + gap, in.top, w2, h);
            }
        });
        centerPanel.setOpaque(false);

        // Column 1: Page Table & Legend (30%)
        JPanel leftPanel = createLeftTablePanel();
        leftPanel.setPreferredSize(new Dimension(0, 0));
        leftPanel.setMinimumSize(new Dimension(0, 0));
        centerPanel.add(leftPanel);

        // Column 2: Middle Analytics (38%)
        JPanel middlePanel = createMiddleAnalyticsPanel();
        middlePanel.setPreferredSize(new Dimension(0, 0));
        middlePanel.setMinimumSize(new Dimension(0, 0));
        centerPanel.add(middlePanel);

        // Column 3: Physical Memory & Recent Translations (32%)
        JPanel rightPanel = createRightMemoryPanel();
        rightPanel.setPreferredSize(new Dimension(0, 0));
        rightPanel.setMinimumSize(new Dimension(0, 0));
        centerPanel.add(rightPanel);

        add(centerPanel, BorderLayout.CENTER);

        // Load default preset (Large 10)
        loadLargePreset();
    }

    private JPanel createTopToolbar() {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setBackground(RetroTheme.PANEL_BG);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(RetroTheme.BORDER_COLOR),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        left.setOpaque(false);

        JLabel lblPS = new JLabel("Page Size (Bytes):");
        lblPS.setFont(RetroTheme.RETRO_FONT_BOLD);
        lblPS.setForeground(RetroTheme.ACCENT_CYAN);
        left.add(lblPS);

        txtPageSize = RetroTheme.createRetroTextField("1024", 6);
        txtPageSize.addActionListener(e -> translateAddress());
        left.add(txtPageSize);

        JButton btnAdd = RetroTheme.createRetroButton("+ Add Page", new Color(40, 26, 74), RetroTheme.ACCENT_CYAN);
        JButton btnRemove = RetroTheme.createRetroButton("- Remove", new Color(40, 26, 74), RetroTheme.ACCENT_PINK);
        btnAdd.setMnemonic('A');
        btnRemove.setMnemonic('R');
        JButton btnSmall = RetroTheme.createRetroButton("Preset: Small (4)", new Color(35, 35, 60), RetroTheme.TEXT_WHITE);
        JButton btnLarge = RetroTheme.createRetroButton("Preset: Large (10)", new Color(35, 35, 60), RetroTheme.TEXT_WHITE);

        btnAdd.addActionListener(e -> addPageRow());
        btnRemove.addActionListener(e -> removePageRow());
        btnSmall.addActionListener(e -> loadSmallPreset());
        btnLarge.addActionListener(e -> loadLargePreset());

        left.add(btnAdd);
        left.add(btnRemove);
        left.add(btnSmall);
        left.add(btnLarge);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        right.setOpaque(false);

        JButton btnArcade = RetroTheme.createRetroButton("PLAY FRAME HOPPER", new Color(20, 28, 25), RetroTheme.ACCENT_GREEN);
        btnArcade.setIcon(new ImageIcon(PixelSprites.createFrogSprite(14, RetroTheme.ACCENT_GREEN)));
        btnArcade.setIconTextGap(6);
        btnArcade.setToolTipText("Launch 16-Bit Arcade MMU Memory Bus Crossing Game");
        btnArcade.addActionListener(e -> { ensurePageSizeTextValid(); launchFrameHopper(); });
        right.add(btnArcade);

        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JPanel createLeftTablePanel() {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setOpaque(false);

        pageTableModel = new DefaultTableModel(new String[]{"Logical Page #", "Physical Frame #", "Status"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        // Plain JTable (NOT RetroTheme.createRetroTable) so this custom renderer owns the colors.
        // The shared table helper overrides cell background/foreground in prepareRenderer, which
        // used to wipe out the [V] green / [X] red status coloring.
        pageTable = new JTable(pageTableModel);
        pageTable.setBackground(RetroTheme.INPUT_BG);
        pageTable.setForeground(RetroTheme.TEXT_WHITE);
        pageTable.setFont(RetroTheme.FONT_BODY);
        pageTable.setGridColor(RetroTheme.BORDER_COLOR);
        pageTable.setRowHeight(24);
        pageTable.setSelectionBackground(new Color(80, 30, 100));
        pageTable.setSelectionForeground(RetroTheme.ACCENT_CYAN);
        pageTable.getTableHeader().setBackground(RetroTheme.PANEL_BG);
        pageTable.getTableHeader().setForeground(RetroTheme.ACCENT_CYAN);
        pageTable.getTableHeader().setFont(RetroTheme.FONT_SM);
        pageTable.getTableHeader().setReorderingAllowed(false);
        pageTable.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, RetroTheme.BORDER_COLOR));

        // Highlight active row with full cyan tint, '>' indicator, and high-contrast status
        pageTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object val, boolean isSel, boolean hasFoc, int row, int col) {
                Component c = super.getTableCellRendererComponent(table, val, isSel, hasFoc, row, col);
                setHorizontalAlignment(CENTER);

                boolean isActive = (row == activeHighlightRow);
                String status = (String) pageTableModel.getValueAt(row, 2);
                boolean isValid = (status != null && status.contains("VALID"));

                if (isActive) {
                    if (isValid) {
                        c.setBackground(new Color(15, 65, 80)); // Full-row cyan tint
                        c.setForeground(RetroTheme.ACCENT_CYAN);
                    } else {
                        c.setBackground(new Color(75, 20, 35)); // Red tint for unmapped/fault
                        c.setForeground(new Color(255, 85, 120));
                    }
                    setFont(new Font("Consolas", Font.BOLD, 12));
                } else {
                    c.setBackground(row % 2 == 0 ? RetroTheme.ROW_EVEN : RetroTheme.ROW_ODD);
                    if (col == 2) {
                        // [V] VALID green / [X] UNMAPPED red, matching the legend
                        c.setForeground(isValid ? RetroTheme.ACCENT_GREEN : new Color(255, 75, 110));
                        setFont(new Font("Consolas", Font.BOLD, 11));
                    } else if (col == 0) {
                        c.setForeground(RetroTheme.ACCENT_CYAN);
                        setFont(new Font("Consolas", Font.BOLD, 11));
                    } else {
                        c.setForeground(isValid ? RetroTheme.TEXT_WHITE : new Color(205, 130, 152));
                        setFont(new Font("Consolas", Font.PLAIN, 11));
                    }
                }
                if (c instanceof JComponent) {
                    ((JComponent) c).setBorder(isActive
                            ? BorderFactory.createMatteBorder(1, 0, 1, 0, isValid ? RetroTheme.ACCENT_CYAN : new Color(255, 85, 120)) : null);
                }
                return c;
            }
        });

        JScrollPane scroll = RetroTheme.createRetroScroll(pageTable);
        scroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(RetroTheme.BORDER_COLOR), " [ PAGE-TO-FRAME MAP TABLE ] ",
                TitledBorder.LEFT, TitledBorder.TOP, RetroTheme.RETRO_FONT_BOLD, RetroTheme.ACCENT_CYAN
        ));
        p.add(scroll, BorderLayout.CENTER);

        // Bottom-left mapping legend; translation history sits below RAM on the right.
        JPanel bottomStack = new JPanel();
        bottomStack.setLayout(new BoxLayout(bottomStack, BoxLayout.Y_AXIS));
        bottomStack.setOpaque(false);

        // Legend
        legendCardLayout = new CardLayout();
        legendCard = new JPanel(legendCardLayout);
        legendCard.setBackground(RetroTheme.PANEL_BG);
        legendCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(RetroTheme.BORDER_COLOR),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));

        JPanel legendItems = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        legendItems.setOpaque(false);

        JLabel l1 = new JLabel("[V] VALID: Present in RAM");
        l1.setFont(new Font("Consolas", Font.BOLD, 10));
        l1.setForeground(RetroTheme.ACCENT_GREEN);

        JLabel l2 = new JLabel("[X] UNMAPPED: Page Fault");
        l2.setFont(new Font("Consolas", Font.BOLD, 10));
        l2.setForeground(new Color(255, 80, 110));

        legendItems.add(l1);
        legendItems.add(l2);

        lblTableHint = new JLabel("Page table needs at least 1 page.");
        lblTableHint.setFont(new Font("Consolas", Font.BOLD, 10));
        lblTableHint.setForeground(RetroTheme.ACCENT_PINK);

        legendCard.add(legendItems, "legend");
        legendCard.add(lblTableHint, "hint");
        bottomStack.add(legendCard);
        bottomStack.add(Box.createVerticalStrut(4));

        // Translation History
        historyPanel = new JPanel(new BorderLayout(0, 2));
        historyPanel.setBackground(RetroTheme.PANEL_BG);
        historyPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(RetroTheme.BORDER_COLOR),
                BorderFactory.createEmptyBorder(4, 8, 6, 8)
        ));

        JLabel histTitle = new JLabel("RECENT TRANSLATIONS (CLICK TO RE-RUN):");
        histTitle.setFont(new Font("Consolas", Font.BOLD, 9));
        histTitle.setForeground(RetroTheme.TEXT_DIM);
        historyPanel.add(histTitle, BorderLayout.NORTH);

        historyListModel = new DefaultListModel<>();
        historyList = new JList<>(historyListModel);
        historyList.setFont(new Font("Consolas", Font.PLAIN, 10));
        historyList.setBackground(new Color(14, 10, 28));
        historyList.setForeground(RetroTheme.TEXT_WHITE);
        historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && historyList.getSelectedValue() != null) {
                String val = historyList.getSelectedValue();
                if (val.contains(" -> ")) {
                    String addrStr = val.substring(0, val.indexOf(" ->")).trim();
                    txtQueryAddr.setText(addrStr);
                    translateAddress();
                }
            }
        });

        JScrollPane histScroll = new JScrollPane(historyList);
        // Tall enough for HISTORY_LIMIT rows so the last entry never runs past the bottom edge
        histScroll.setPreferredSize(new Dimension(232, 94));
        histScroll.setMinimumSize(new Dimension(120, 94));
        histScroll.setBorder(BorderFactory.createLineBorder(new Color(40, 30, 65)));
        histScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        histScroll.getViewport().setBackground(new Color(14, 10, 28));
        historyScroll = histScroll;

        // Empty state (shown when there are no translations yet)
        historyEmptyLabel = new JLabel("No translations yet", SwingConstants.CENTER);
        historyEmptyLabel.setFont(new Font("Consolas", Font.ITALIC, 10));
        historyEmptyLabel.setForeground(RetroTheme.TEXT_DIM);
        historyEmptyLabel.setBorder(BorderFactory.createLineBorder(new Color(40, 30, 65)));

        historyCardLayout = new CardLayout();
        historyContent = new JPanel(historyCardLayout);
        historyContent.add(histScroll, "history");
        historyContent.add(historyEmptyLabel, "empty");
        historyPanel.add(historyContent, BorderLayout.CENTER);

        p.add(bottomStack, BorderLayout.SOUTH);

        rebuildHistoryModel();

        return p;
    }

    private JPanel createMiddleAnalyticsPanel() {
        JPanel middle = new JPanel(new BorderLayout(0, 6));
        middle.setOpaque(false);

        // 1. Widened Address Query Bar (Wider field fits 10+ digits without truncation)
        JPanel transQuery = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        transQuery.setBackground(RetroTheme.PANEL_BG);
        transQuery.setBorder(BorderFactory.createLineBorder(RetroTheme.BORDER_COLOR));

        JLabel lblQ = new JLabel("Logical Addr:");
        lblQ.setFont(RetroTheme.RETRO_FONT_BOLD);
        lblQ.setForeground(RetroTheme.ACCENT_YELLOW);
        transQuery.add(lblQ);

        txtQueryAddr = RetroTheme.createRetroTextField("2500", 11);
        txtQueryAddr.setPreferredSize(new Dimension(115, 26));
        txtQueryAddr.addActionListener(e -> translateAddress());
        transQuery.add(txtQueryAddr);

        JButton btnTrans = RetroTheme.createRetroButton(">> TRANSLATE ADDRESS", RetroTheme.ACCENT_YELLOW, Color.BLACK);
        btnTrans.setMnemonic('T');
        btnTrans.setFont(new Font("Consolas", Font.BOLD, 11));
        btnTrans.addActionListener(e -> translateAddress());
        transQuery.add(btnTrans);

        middle.add(transQuery, BorderLayout.NORTH);

        // 2. Center Column: Hero Decomposition Bar + Large Result Card + HTML Table
        JPanel centerStack = new JPanel();
        centerStack.setLayout(new BoxLayout(centerStack, BoxLayout.Y_AXIS));
        centerStack.setOpaque(false);

        centerStack.add(createAddressDecompositionBar());
        centerStack.add(Box.createVerticalStrut(6));
        centerStack.add(createResultCard());
        centerStack.add(Box.createVerticalStrut(10));
        centerStack.add(createFormulaBreakdownBox());

        middle.add(centerStack, BorderLayout.CENTER);
        return middle;
    }

    private JPanel createRightMemoryPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);

        // Top Stats Strip (filling space and showing memory pressure!)
        JPanel statsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        statsPanel.setBackground(RetroTheme.PANEL_BG);
        statsPanel.setBorder(BorderFactory.createLineBorder(RetroTheme.BORDER_COLOR));

        lblRamStats = new JLabel("FRAMES: 4/16 USED | FREE: 12 | RAM: 16384 B");
        lblRamStats.setFont(new Font("Consolas", Font.BOLD, 10));
        lblRamStats.setForeground(RetroTheme.ACCENT_GREEN);
        statsPanel.add(lblRamStats);
        p.add(statsPanel, BorderLayout.NORTH);

        physicalMemView = new PhysicalMemoryView();
        JScrollPane scrollMem = RetroTheme.createRetroScroll(physicalMemView);
        scrollMem.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(RetroTheme.BORDER_COLOR), " [ PHYSICAL RAM (FRAMES F00 - F15) ] ",
                TitledBorder.LEFT, TitledBorder.TOP, RetroTheme.RETRO_FONT_BOLD, RetroTheme.ACCENT_GREEN
        ));
        scrollMem.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        p.add(scrollMem, BorderLayout.CENTER);

        // Keep history beside RAM so long page tables cannot squeeze it away.
        JPanel lowerStack = new JPanel();
        lowerStack.setLayout(new BoxLayout(lowerStack, BoxLayout.Y_AXIS));
        lowerStack.setOpaque(false);

        JPanel memLegend = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 3));
        memLegend.setBackground(RetroTheme.PANEL_BG);
        memLegend.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(RetroTheme.BORDER_COLOR),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)
        ));
        memLegend.add(ramLegendChip("MAPPED", RetroTheme.ACCENT_CYAN));
        memLegend.add(ramLegendChip("SELECTED", RetroTheme.ACCENT_GREEN));
        memLegend.add(ramLegendChip("FREE", RetroTheme.TEXT_DIM));
        memLegend.setAlignmentX(Component.LEFT_ALIGNMENT);
        historyPanel.setPreferredSize(new Dimension(260, 126));
        historyPanel.setMinimumSize(new Dimension(120, 112));
        historyPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        lowerStack.add(memLegend);
        lowerStack.add(Box.createVerticalStrut(4));
        lowerStack.add(historyPanel);
        p.add(lowerStack, BorderLayout.SOUTH);
        return p;
    }

    private static JLabel ramLegendChip(String label, Color color) {
        JLabel l = new JLabel("\u25A0 " + label);
        l.setFont(new Font("Consolas", Font.BOLD, 10));
        l.setForeground(color);
        return l;
    }

    private JPanel createAddressDecompositionBar() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setBackground(RetroTheme.PANEL_BG);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(RetroTheme.BORDER_COLOR),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));

        JPanel topHeader = new JPanel(new BorderLayout());
        topHeader.setOpaque(false);

        JLabel title = new JLabel("ADDRESS DECOMPOSITION");
        title.setFont(new Font("Consolas", Font.BOLD, 10));
        title.setForeground(RetroTheme.TEXT_DIM);
        topHeader.add(title, BorderLayout.WEST);

        lblDecompTag = new JLabel("");
        lblDecompTag.setFont(new Font("Consolas", Font.BOLD, 10));
        topHeader.add(lblDecompTag, BorderLayout.EAST);
        p.add(topHeader, BorderLayout.NORTH);

        // Hero Values Boxes (36px font, value is the HERO!)
        JPanel heroBoxes = new JPanel(new GridLayout(1, 2, 8, 0));
        heroBoxes.setOpaque(false);

        // Box 1: Page (p)
        JPanel boxP = new JPanel(new BorderLayout(2, 2));
        boxP.setBackground(new Color(14, 32, 46));
        boxP.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(RetroTheme.ACCENT_CYAN, 1),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)
        ));
        JLabel lblTagP = new JLabel("PAGE NUMBER (p)", SwingConstants.CENTER);
        lblTagP.setFont(new Font("Consolas", Font.BOLD, 9));
        lblTagP.setForeground(new Color(130, 210, 240));
        boxP.add(lblTagP, BorderLayout.NORTH);

        lblHeroPage = new JLabel("--", SwingConstants.CENTER);
        lblHeroPage.setFont(new Font("Consolas", Font.BOLD, 36));
        lblHeroPage.setForeground(RetroTheme.ACCENT_CYAN);
        boxP.add(lblHeroPage, BorderLayout.CENTER);

        // Box 2: Offset (d)
        JPanel boxD = new JPanel(new BorderLayout(2, 2));
        boxD.setBackground(new Color(42, 36, 16));
        boxD.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(RetroTheme.ACCENT_YELLOW, 1),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)
        ));
        JLabel lblTagD = new JLabel("PAGE OFFSET (d)", SwingConstants.CENTER);
        lblTagD.setFont(new Font("Consolas", Font.BOLD, 9));
        lblTagD.setForeground(new Color(240, 220, 140));
        boxD.add(lblTagD, BorderLayout.NORTH);

        lblHeroOffset = new JLabel("--", SwingConstants.CENTER);
        lblHeroOffset.setFont(new Font("Consolas", Font.BOLD, 36));
        lblHeroOffset.setForeground(RetroTheme.ACCENT_YELLOW);
        boxD.add(lblHeroOffset, BorderLayout.CENTER);

        heroBoxes.add(boxP);
        heroBoxes.add(boxD);
        p.add(heroBoxes, BorderLayout.CENTER);

        lblDecompDetails = new JLabel("Enter logical address above and click [>> TRANSLATE ADDRESS]");
        lblDecompDetails.setFont(new Font("Consolas", Font.PLAIN, 10));
        lblDecompDetails.setForeground(RetroTheme.TEXT_WHITE);
        lblDecompDetails.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 0));
        p.add(lblDecompDetails, BorderLayout.SOUTH);

        return p;
    }

    private JPanel createResultCard() {
        resultCard = new JPanel(new BorderLayout(4, 4)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(18, 22, 38));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
            }
        };
        resultCard.setOpaque(false);
        resultCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(RetroTheme.BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));

        JLabel tag = new JLabel("RESOLVED PHYSICAL ADDRESS");
        tag.setFont(new Font("Consolas", Font.BOLD, 10));
        tag.setForeground(RetroTheme.TEXT_DIM);
        resultCard.add(tag, BorderLayout.NORTH);

        // Hero value (36px bold)
        lblResultHero = new JLabel("-- BYTES");
        lblResultHero.setFont(new Font("Consolas", Font.BOLD, 32));
        lblResultHero.setForeground(RetroTheme.ACCENT_CYAN);
        resultCard.add(lblResultHero, BorderLayout.CENTER);

        // Subtitle + What happens next recovery sequence
        resultSubStack = new JPanel(new BorderLayout(0, 2));
        resultSubStack.setOpaque(false);

        lblResultSub = new JTextArea(2, 32);
        lblResultSub.setEditable(false);
        lblResultSub.setFocusable(false);
        lblResultSub.setOpaque(false);
        lblResultSub.setLineWrap(true);
        lblResultSub.setWrapStyleWord(true);
        lblResultSub.setFont(new Font("Consolas", Font.PLAIN, 11));
        lblResultSub.setForeground(RetroTheme.TEXT_WHITE);
        resultSubStack.add(lblResultSub, BorderLayout.NORTH);

        lblResultRecovery = new JTextArea(2, 32);
        lblResultRecovery.setEditable(false);
        lblResultRecovery.setFocusable(false);
        lblResultRecovery.setOpaque(false);
        lblResultRecovery.setLineWrap(true);
        lblResultRecovery.setWrapStyleWord(true);
        lblResultRecovery.setFont(new Font("Consolas", Font.ITALIC, 10));
        lblResultRecovery.setForeground(new Color(255, 170, 200));
        resultSubStack.add(lblResultRecovery, BorderLayout.SOUTH);

        resultCard.add(resultSubStack, BorderLayout.SOUTH);

        // Re-wrap the message to the card's true width so it uses the full card, not a fixed 280px
        resultCard.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                applyResultTextWidth();
            }
        });
        applyResultTextWidth();

        return resultCard;
    }

    /** Refreshes the wrapping text areas after content or card-size changes. */
    private void applyResultTextWidth() {
        if (lblResultSub == null) return;
        String sub = toPlainResultText(resultSubHtml);
        lblResultSub.setText(sub);
        int columns = Math.max(18, (resultCard.getWidth() - 32) / Math.max(1, lblResultSub.getFontMetrics(lblResultSub.getFont()).charWidth('m')));
        lblResultSub.setColumns(columns);
        lblResultSub.setRows(wrappedLineCount(sub, columns));
        lblResultRecovery.setVisible(!resultRecoveryHtml.isEmpty());
        String recovery = toPlainResultText(resultRecoveryHtml);
        lblResultRecovery.setText(recovery);
        lblResultRecovery.setColumns(columns);
        lblResultRecovery.setRows(resultRecoveryHtml.isEmpty() ? 1 : wrappedLineCount(recovery, columns));
        resultSubStack.revalidate();
        resultCard.revalidate();
    }

    private int wrappedLineCount(String text, int columns) {
        int lines = 1;
        int current = 0;
        for (String word : text.split("\\s+")) {
            if (current == 0) {
                current = word.length();
            } else if (current + 1 + word.length() <= columns) {
                current += 1 + word.length();
            } else {
                lines++;
                current = word.length();
            }
        }
        return Math.max(2, lines);
    }

    private String toPlainResultText(String value) {
        return value.replace("<html><body>", "")
                .replace("</body></html>", "")
                .replace("<b>", "").replace("</b>", "")
                .replace("&rarr;", "->");
    }

    private JPanel createFormulaBreakdownBox() {
        JPanel p = new JPanel(new BorderLayout(0, 2));
        p.setOpaque(false);

        formulaPane = new JEditorPane();
        formulaPane.setContentType("text/html");
        formulaPane.setEditable(false);
        formulaPane.setBackground(RetroTheme.INPUT_BG);
        formulaPane.setFont(RetroTheme.RETRO_FONT);
        formulaPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(RetroTheme.BORDER_COLOR),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));

        renderFormulaHtml(1024, -1, -1, -1, -1, -1, 0, "");

        p.add(formulaPane, BorderLayout.CENTER);
        return p;
    }

    /**
     * faultType: 0 = Normal Hit, 1 = Page Fault (in table, valid=0), 2 = Invalid Address (limit trap), 3 = Input Error
     */
    private void renderFormulaHtml(long ps, long la, long p, long d, int f, long pa, int faultType, String errorMsg) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-family:Consolas; font-size:11px; color:#eef0f2;'>");
        sb.append("<div style='color:#05d9e8; font-weight:bold; margin-bottom:4px;'>STEP-BY-STEP TRANSLATION BREAKDOWN</div>");

        sb.append("<table width='100%' cellpadding='1' cellspacing='0'>");

        sb.append("<tr><td style='color:#a0a0b8; width:50%;'>1. Page Size (S):</td>");
        sb.append("<td align='right' style='color:#ffffff; font-weight:bold;'>").append(ps > 0 ? ps : "--").append(" Bytes</td></tr>");

        sb.append("<tr><td style='color:#a0a0b8;'>2. Logical Addr (A):</td>");
        sb.append("<td align='right' style='color:#ffffff; font-weight:bold;'>").append(la >= 0 ? la : "--").append(" Bytes</td></tr>");

        sb.append("<tr><td style='color:#a0a0b8;'>3. Page Number (p):</td>");
        if (p >= 0) sb.append("<td align='right' style='color:#05d9e8; font-weight:bold;'>A / S = ").append(p).append("</td></tr>");
        else sb.append("<td align='right' style='color:#777;'>--</td></tr>");

        sb.append("<tr><td style='color:#a0a0b8;'>4. Page Offset (d):</td>");
        if (d >= 0) sb.append("<td align='right' style='color:#ffd319; font-weight:bold;'>A % S = ").append(d).append(" Bytes</td></tr>");
        else sb.append("<td align='right' style='color:#777;'>--</td></tr>");

        sb.append("<tr><td style='color:#a0a0b8;'>5. Target Frame (f):</td>");
        if (faultType == 1) {
            sb.append("<td align='right' style='color:#ff2a8d; font-weight:bold;'>valid bit = 0 -&gt; PAGE FAULT</td></tr>");
        } else if (faultType == 2) {
            // Short, single-token form so it never wraps: LIMIT TRAP (585 &gt; 15)
            sb.append("<td align='right' style='color:#ff2a8d; font-weight:bold;'>LIMIT TRAP (")
              .append(p).append(" &gt; ").append(getTableLimitPage()).append(")</td></tr>");
        } else if (f >= 0) {
            sb.append("<td align='right' style='color:#39ff14; font-weight:bold;'>PageTable[").append(p).append("] = ").append(f).append("</td></tr>");
        } else {
            sb.append("<td align='right' style='color:#777;'>--</td></tr>");
        }

        // Dedicated full-width line for Step 6 formula so it NEVER wraps or breaks across lines!
        sb.append("<tr><td colspan='2' style='color:#a0a0b8; padding-top:4px;'>6. Physical Address (P) = (f * S) + d:</td></tr>");
        if (faultType == 1) {
            sb.append("<tr><td colspan='2' style='color:#ff2a8d; font-weight:bold; padding-left:12px;'>PAGE FAULT TRAP: OS loads page from disk</td></tr>");
        } else if (faultType == 2) {
            sb.append("<tr><td colspan='2' style='color:#ff2a8d; font-weight:bold; padding-left:12px;'>INVALID ADDRESS TRAP: outside process memory</td></tr>");
        } else if (faultType == 3) {
            sb.append("<tr><td colspan='2' style='color:#ff2a8d; font-weight:bold; padding-left:12px;'>INPUT ERROR: ").append(errorMsg).append("</td></tr>");
        } else if (pa >= 0) {
            sb.append("<tr><td colspan='2' style='color:#05d9e8; font-weight:bold; font-size:12px; padding-left:12px;'>(").append(f).append(" * ").append(ps).append(" B) + ").append(d).append(" B = ").append(pa).append(" Bytes</td></tr>");
        } else {
            sb.append("<tr><td colspan='2' style='color:#777; padding-left:12px;'>--</td></tr>");
        }

        sb.append("</table>");
        sb.append("</div>");

        formulaPane.setText(sb.toString());
        formulaPane.setCaretPosition(0);
    }

    private boolean applyPageSizeText() {
        String t = txtPageSize.getText().trim();
        long v;
        try { v = Long.parseLong(t); } catch (NumberFormatException ex) { v = -1; }
        if (v <= 0 || v > Integer.MAX_VALUE) {
            handleInlineInputError("Invalid Page Size! Must be a positive integer (e.g. 512, 1024). Still using " + pageSize + " B.");
            return false; // do NOT touch pageSize, frames strip, or history
        }
        if (v != pageSize) {
            pageSize = (int) v;
            syncAllViewsAfterModelChange();
        }
        return true;
    }

    private void ensurePageSizeTextValid() {
        String t = txtPageSize.getText().trim();
        long v;
        try { v = Long.parseLong(t); } catch (NumberFormatException ex) { v = -1; }
        if (v <= 0 || v > Integer.MAX_VALUE) {
            txtPageSize.setText(String.valueOf(pageSize));
        } else if (v != pageSize) {
            pageSize = (int) v;
            syncAllViewsAfterModelChange();
        }
    }

    private void translateAddress() {
        if (!applyPageSizeText()) {
            return;
        }

        String addrText = txtQueryAddr.getText().trim();

        if (addrText.isEmpty()) {
            handleInlineInputError("Enter a logical address (non-negative integer).");
            return;
        }

        if (addrText.startsWith("+")) {
            handleInlineInputError("Logical address must be a whole number.");
            return;
        }

        if (addrText.startsWith("-")) {
            handleInlineInputError("Logical address cannot be negative.");
            return;
        }

        boolean allDigits = true;
        for (int i = 0; i < addrText.length(); i++) {
            if (!Character.isDigit(addrText.charAt(i))) {
                allDigits = false;
                break;
            }
        }

        if (!allDigits) {
            handleInlineInputError("Logical address must be a whole number.");
            return;
        }

        long logicalAddr;
        try {
            logicalAddr = Long.parseLong(addrText);
        } catch (NumberFormatException ex) {
            handleInlineInputError("Address is too large.");
            return;
        }

        long pageNumber = logicalAddr / pageSize;
        long offset = logicalAddr % pageSize;

        // Hero Values
        lblHeroPage.setText(String.valueOf(pageNumber));
        lblHeroOffset.setText(String.valueOf(offset));
        lblDecompDetails.setText(String.format("Logical Addr: %d Bytes = Page %d * %d B + Offset %d B", logicalAddr, pageNumber, pageSize, offset));

        // Scan Page Table
        Map<Integer, Integer> map = getTableMappings();
        int highestPageInTable = -1;
        int unmappedRow = -1;

        for (int r = 0; r < pageTableModel.getRowCount(); r++) {
            Object pVal = pageTableModel.getValueAt(r, 0);
            if (pVal != null && !pVal.toString().trim().isEmpty()) {
                try {
                    String cleanP = pVal.toString().replace(">", "").trim();
                    int p = Integer.parseInt(cleanP);
                    if (p > highestPageInTable) highestPageInTable = p;

                    if (p == pageNumber) {
                        String st = (String) pageTableModel.getValueAt(r, 2);
                        if (st != null && st.contains("UNMAPPED")) {
                            unmappedRow = r;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        // 2. CASE A: TRUE PAGE FAULT (Page exists in table capacity, but valid bit is 0)
        if (unmappedRow >= 0) {
            activeHighlightRow = unmappedRow;
            refreshTableHighlightMarkers();
            pageTable.scrollRectToVisible(pageTable.getCellRect(unmappedRow, 0, true));
            physicalMemView.setSelectedFrame(-1);

            lblDecompTag.setText("[PAGE FAULT]");
            lblDecompTag.setForeground(RetroTheme.ACCENT_PINK);

            resultCard.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(RetroTheme.ACCENT_PINK, 2),
                    BorderFactory.createEmptyBorder(8, 12, 8, 12)
            ));
            lblResultHero.setText("PAGE FAULT");
            lblResultHero.setForeground(RetroTheme.ACCENT_PINK);
            resultSubHtml = String.format("Page %d is in range but its valid bit = 0. The OS traps, loads the page from disk into a free frame, updates the page table, and retries.", pageNumber);
            resultRecoveryHtml = "";
            applyResultTextWidth();

            renderFormulaHtml(pageSize, logicalAddr, pageNumber, offset, -1, -1, 1, "");
            triggerCardShake();
            recordHistory(logicalAddr, pageNumber, offset, -1, -1, "PAGE FAULT");
            return;
        }

        // 3. CASE B: INVALID ADDRESS / LIMIT TRAP (Page number exceeds table limits)
        if (pageNumber > highestPageInTable || !map.containsKey((int) pageNumber)) {
            // DO NOT HIGHLIGHT ROW 0! Clear selection completely.
            activeHighlightRow = -1;
            refreshTableHighlightMarkers();
            pageTable.clearSelection();
            physicalMemView.setSelectedFrame(-1);

            lblDecompTag.setText("[INVALID ADDR]");
            lblDecompTag.setForeground(RetroTheme.ACCENT_PINK);

            resultCard.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(RetroTheme.ACCENT_PINK, 2),
                    BorderFactory.createEmptyBorder(8, 12, 8, 12)
            ));
            lblResultHero.setText("LIMIT TRAP");
            lblResultHero.setForeground(RetroTheme.ACCENT_PINK);
            resultSubHtml = String.format("INVALID ADDRESS: Requested page %d exceeds the page table limit (last page %d).", pageNumber, highestPageInTable);
            resultRecoveryHtml = "[KERNEL TRAP]: Memory limit violation (SIGSEGV). Process memory boundary exceeded.";
            applyResultTextWidth();

            renderFormulaHtml(pageSize, logicalAddr, pageNumber, offset, -1, -1, 2, "");
            triggerCardShake();
            recordHistory(logicalAddr, pageNumber, offset, -1, -1, "LIMIT TRAP");
            return;
        }

        // 4. CASE C: NORMAL HIT
        int frame = map.get((int) pageNumber);
        long physicalAddr = ((long) frame * pageSize) + offset;

        // Highlight matching table row
        activeHighlightRow = -1;
        for (int r = 0; r < pageTableModel.getRowCount(); r++) {
            Object pVal = pageTableModel.getValueAt(r, 0);
            if (pVal != null && !pVal.toString().trim().isEmpty()) {
                try {
                    String cleanP = pVal.toString().replace(">", "").trim();
                    if (Integer.parseInt(cleanP) == pageNumber) {
                        activeHighlightRow = r;
                        pageTable.scrollRectToVisible(pageTable.getCellRect(r, 0, true));
                        break;
                    }
                } catch (Exception ignored) {}
            }
        }
        refreshTableHighlightMarkers();

        // Highlight in Physical Memory View
        physicalMemView.setMemoryState(pageSize, map, frame);
        updateRamStats();

        lblDecompTag.setText("[VALID IN RAM]");
        lblDecompTag.setForeground(RetroTheme.ACCENT_GREEN);

        // Update Result Card (Hero value: 32px bold)
        resultCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(RetroTheme.ACCENT_GREEN, 2),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        lblResultHero.setText(String.format("%d BYTES", physicalAddr));
        lblResultHero.setForeground(RetroTheme.ACCENT_GREEN);
        resultSubHtml = String.format("Frame %d (Base: %d B) + Offset %d B = <b>%d Bytes</b>", frame, (long) frame * pageSize, offset, physicalAddr);
        resultRecoveryHtml = "";
        applyResultTextWidth();

        renderFormulaHtml(pageSize, logicalAddr, pageNumber, offset, frame, physicalAddr, 0, "");
        recordHistory(logicalAddr, pageNumber, offset, frame, physicalAddr, "HIT");
    }

    private void handleInlineInputError(String message) {
        activeHighlightRow = -1;
        refreshTableHighlightMarkers();
        pageTable.clearSelection();
        physicalMemView.setSelectedFrame(-1);

        lblHeroPage.setText("--");
        lblHeroOffset.setText("--");
        lblDecompTag.setText("[INPUT ERROR]");
        lblDecompTag.setForeground(RetroTheme.ACCENT_PINK);
        lblDecompDetails.setText(message);

        resultCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(RetroTheme.ACCENT_PINK, 2),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        lblResultHero.setText("INPUT ERROR");
        lblResultHero.setForeground(RetroTheme.ACCENT_PINK);
        resultSubHtml = message;
        resultRecoveryHtml = "";
        applyResultTextWidth();

        renderFormulaHtml(pageSize, -1, -1, -1, -1, -1, 3, message);
        triggerCardShake();
    }

    private void refreshTableHighlightMarkers() {
        for (int r = 0; r < pageTableModel.getRowCount(); r++) {
            Object pVal = pageTableModel.getValueAt(r, 0);
            if (pVal != null) {
                String raw = pVal.toString().replace(">", "").trim();
                if (r == activeHighlightRow) {
                    pageTableModel.setValueAt("> " + raw, r, 0);
                } else {
                    pageTableModel.setValueAt(raw, r, 0);
                }
            }
        }
        pageTable.repaint();
    }

    private void recordHistory(long la, long p, long d, int f, long pa, String outcome) {
        // Merge repeats of the same address (keeps the newest result) instead of stacking duplicates.
        int existing = -1;
        for (int i = 0; i < historyEntries.size(); i++) {
            if (historyEntries.get(i).la == la) { existing = i; break; }
        }
        HistoryEntry he;
        if (existing >= 0) {
            he = historyEntries.remove(existing);
            he.count++;
            he.p = p; he.d = d; he.f = f; he.pa = pa; he.outcome = outcome;
        } else {
            he = new HistoryEntry(la, p, d, f, pa, outcome);
        }
        historyEntries.add(0, he);
        while (historyEntries.size() > HISTORY_LIMIT) {
            historyEntries.remove(historyEntries.size() - 1);
        }
        rebuildHistoryModel();
    }

    private void rebuildHistoryModel() {
        if (historyListModel == null) return;
        historyListModel.clear();
        for (HistoryEntry he : historyEntries) {
            String item;
            if (he.outcome.equals("HIT")) {
                item = String.format("%d -> P%d+%d -> F%d = %d [HIT]", he.la, he.p, he.d, he.f, he.pa);
            } else if (he.outcome.equals("PAGE FAULT")) {
                item = String.format("%d -> P%d+%d -> PAGE FAULT", he.la, he.p, he.d);
            } else {
                item = String.format("%d -> P%d -> %s", he.la, he.p, he.outcome);
            }
            if (he.count > 1) item += String.format("  x%d", he.count);
            historyListModel.addElement(item);
        }
        boolean empty = historyEntries.isEmpty();
        if (historyCardLayout != null && historyContent != null) {
            historyCardLayout.show(historyContent, empty ? "empty" : "history");
        }
        if (historyScroll != null) { historyScroll.revalidate(); historyScroll.repaint(); }
    }

    /** Highest logical page currently present in the table = the addressable page limit. */
    private int getTableLimitPage() {
        if (pageTableModel == null) return -1;
        int max = -1;
        for (int r = 0; r < pageTableModel.getRowCount(); r++) {
            Object v = pageTableModel.getValueAt(r, 0);
            if (v != null) {
                String s = v.toString().replace(">", "").trim();
                if (!s.isEmpty()) {
                    try { max = Math.max(max, Integer.parseInt(s)); } catch (Exception ignored) {}
                }
            }
        }
        return max;
    }

    private void triggerCardShake() {
        if (shakeTimer != null && shakeTimer.isRunning()) shakeTimer.stop();
        shakeTicks = 0;
        final Point orig = resultCard.getLocation();
        shakeTimer = new javax.swing.Timer(25, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                shakeTicks++;
                int dx = (int) (Math.sin(shakeTicks * 1.5) * (8 - shakeTicks));
                resultCard.setLocation(orig.x + dx, orig.y);
                if (shakeTicks >= 8) {
                    shakeTimer.stop();
                    resultCard.setLocation(orig);
                }
            }
        });
        shakeTimer.start();
    }

    private Map<Integer, Integer> getTableMappings() {
        Map<Integer, Integer> map = new LinkedHashMap<>();
        Set<Integer> ownedFrames = new HashSet<>();
        for (int i = 0; i < pageTableModel.getRowCount(); i++) {
            Object pObj = pageTableModel.getValueAt(i, 0);
            Object fObj = pageTableModel.getValueAt(i, 1);
            Object sObj = pageTableModel.getValueAt(i, 2);
            if (pObj != null && fObj != null && sObj != null) {
                String pStr = pObj.toString().replace(">", "").trim();
                String fStr = fObj.toString().trim();
                String sStr = sObj.toString().trim();
                if (sStr.contains("VALID") && !pStr.isEmpty() && !fStr.isEmpty() && !fStr.equals("--")) {
                    try {
                        int page = Integer.parseInt(pStr);
                        int frame = Integer.parseInt(fStr);
                        if (frame >= 0 && frame < 16 && ownedFrames.add(frame)) {
                            map.put(page, frame);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        return map;
    }

    private void showTableHint(String msg) {
        if (lblTableHint != null && legendCardLayout != null && legendCard != null) {
            lblTableHint.setText(msg);
            legendCardLayout.show(legendCard, "hint");
            if (hintTimer != null && hintTimer.isRunning()) hintTimer.stop();
            hintTimer = new javax.swing.Timer(2000, e -> {
                legendCardLayout.show(legendCard, "legend");
            });
            hintTimer.setRepeats(false);
            hintTimer.start();
        }
    }

    private void syncAllViewsAfterModelChange() {
        activeHighlightRow = -1;
        refreshTableHighlightMarkers();
        pageTable.clearSelection();
        clearTranslationHistory();
        refreshPhysicalMemoryView();
        int last = pageTableModel.getRowCount() - 1;
        if (last >= 0) {
            pageTable.scrollRectToVisible(pageTable.getCellRect(last, 0, true));
        }
    }

    private void addPageRow() {
        ensurePageSizeTextValid();
        int nextP = 0;
        for (int i = 0; i < pageTableModel.getRowCount(); i++) {
            try {
                String raw = pageTableModel.getValueAt(i, 0).toString().replace(">", "").trim();
                int p = Integer.parseInt(raw);
                if (p >= nextP) nextP = p + 1;
            } catch (Exception ignored) {}
        }
        Set<Integer> usedFrames = new HashSet<>(getTableMappings().values());
        int freeFrame = -1;
        for (int f = 0; f < 16; f++) {
            if (!usedFrames.contains(f)) {
                freeFrame = f;
                break;
            }
        }
        if (freeFrame >= 0) {
            pageTableModel.addRow(new Object[]{String.valueOf(nextP), String.valueOf(freeFrame), "[V] VALID"});
        } else {
            pageTableModel.addRow(new Object[]{String.valueOf(nextP), "--", "[X] UNMAPPED"});
        }
        syncAllViewsAfterModelChange();
    }

    private void removePageRow() {
        ensurePageSizeTextValid();
        if (pageTableModel.getRowCount() <= 1) {
            showTableHint("Page table needs at least 1 page.");
            return;
        }
        int lastRow = pageTableModel.getRowCount() - 1;
        pageTableModel.removeRow(lastRow);
        syncAllViewsAfterModelChange();
    }

    private void clearTranslationHistory() {
        historyEntries.clear();
        rebuildHistoryModel();
        showIdleState();
    }

    private void loadSmallPreset() {
        pageSize = 1024;
        txtPageSize.setText("1024");
        pageTableModel.setRowCount(0);
        pageTableModel.addRow(new Object[]{"0", "5", "[V] VALID"});
        pageTableModel.addRow(new Object[]{"1", "6", "[V] VALID"});
        pageTableModel.addRow(new Object[]{"2", "1", "[V] VALID"});
        pageTableModel.addRow(new Object[]{"3", "2", "[V] VALID"});
        padEmptyCapacityRows(8);
        syncAllViewsAfterModelChange();
    }

    public void loadManualExample() {
        pageSize = 512;
        txtPageSize.setText("512");
        pageTableModel.setRowCount(0);
        pageTableModel.addRow(new Object[]{"0", "3", "[V] VALID"});
        pageTableModel.addRow(new Object[]{"1", "1", "[V] VALID"});
        pageTableModel.addRow(new Object[]{"2", "5", "[V] VALID"});
        pageTableModel.addRow(new Object[]{"3", "2", "[V] VALID"});
        syncAllViewsAfterModelChange();
        txtQueryAddr.setText("1040");
        translateAddress();
    }

    private void loadLargePreset() {
        pageSize = 1024;
        txtPageSize.setText("1024");
        pageTableModel.setRowCount(0);
        pageTableModel.addRow(new Object[]{"0", "5", "[V] VALID"});
        pageTableModel.addRow(new Object[]{"1", "6", "[V] VALID"});
        pageTableModel.addRow(new Object[]{"2", "1", "[V] VALID"});
        pageTableModel.addRow(new Object[]{"3", "2", "[V] VALID"});
        pageTableModel.addRow(new Object[]{"4", "--", "[X] UNMAPPED"});
        pageTableModel.addRow(new Object[]{"5", "--", "[X] UNMAPPED"});
        pageTableModel.addRow(new Object[]{"6", "--", "[X] UNMAPPED"});
        pageTableModel.addRow(new Object[]{"7", "--", "[X] UNMAPPED"});
        pageTableModel.addRow(new Object[]{"8", "0", "[V] VALID"});
        pageTableModel.addRow(new Object[]{"9", "3", "[V] VALID"});
        pageTableModel.addRow(new Object[]{"10", "4", "[V] VALID"});
        pageTableModel.addRow(new Object[]{"11", "7", "[V] VALID"});
        pageTableModel.addRow(new Object[]{"12", "8", "[V] VALID"});
        pageTableModel.addRow(new Object[]{"13", "9", "[V] VALID"});
        syncAllViewsAfterModelChange();
    }

    private void padEmptyCapacityRows(int targetRows) {
        int cur = pageTableModel.getRowCount();
        for (int i = cur; i < targetRows; i++) {
            pageTableModel.addRow(new Object[]{String.valueOf(i), "--", "[X] UNMAPPED"});
        }
    }

    private void showIdleState() {
        String pageSizeLabel = pageSize + " B";
        activeHighlightRow = -1;
        refreshTableHighlightMarkers();
        pageTable.clearSelection();
        physicalMemView.setSelectedFrame(-1);
        lblHeroPage.setText("--");
        lblHeroOffset.setText("--");
        lblDecompTag.setText("");
        lblDecompDetails.setText(String.format("Logical Address: -- Bytes (Page Size: %s)", pageSizeLabel));

        resultCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(RetroTheme.BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        lblResultHero.setText("-- BYTES");
        lblResultHero.setForeground(RetroTheme.ACCENT_CYAN);
        resultSubHtml = "Enter address above & click TRANSLATE";
        resultRecoveryHtml = "";
        applyResultTextWidth();

        renderFormulaHtml(pageSize, -1, -1, -1, -1, -1, 0, "");
    }

    private void refreshPhysicalMemoryView() {
        normalizeFrameMappings();
        Map<Integer, Integer> map = getTableMappings();
        physicalMemView.setMemoryState(pageSize, map, -1);
        updateRamStats();
    }

    private void normalizeFrameMappings() {
        Set<Integer> owners = new HashSet<>();
        for (int row = 0; row < pageTableModel.getRowCount(); row++) {
            String status = String.valueOf(pageTableModel.getValueAt(row, 2));
            if (!status.contains("VALID")) continue;
            try {
                int frame = Integer.parseInt(String.valueOf(pageTableModel.getValueAt(row, 1)).trim());
                if (frame < 0 || frame >= 16 || !owners.add(frame)) {
                    pageTableModel.setValueAt("--", row, 1);
                    pageTableModel.setValueAt("[X] UNMAPPED", row, 2);
                }
            } catch (NumberFormatException ex) {
                pageTableModel.setValueAt("--", row, 1);
                pageTableModel.setValueAt("[X] UNMAPPED", row, 2);
            }
        }
    }

    private void updateRamStats() {
        // Count the same unique frame-owner map that the RAM view paints.
        int used = physicalMemView.getUsedFrameCount();
        int total = 16;
        int free = Math.max(0, total - used);
        long totalRam = (long) total * pageSize;
        lblRamStats.setText(String.format("FRAMES: %d/%d USED | FREE: %d | TOTAL RAM: %d B", used, total, free, totalRam));
    }

    private void launchFrameHopper() {
        ensurePageSizeTextValid();
        JFrame topFrame = (JFrame) SwingUtilities.getWindowAncestor(this);
        FrameHopperDialog dlg = new FrameHopperDialog(topFrame, pageSize, getTableMappings(), getPageTableCapacity());
        dlg.setVisible(true);
    }

    private int getPageTableCapacity() {
        int maxPage = -1;
        for (int row = 0; row < pageTableModel.getRowCount(); row++) {
            Object value = pageTableModel.getValueAt(row, 0);
            if (value == null) continue;
            try {
                String page = value.toString().replace(">", "").trim();
                if (!page.isEmpty()) maxPage = Math.max(maxPage, Integer.parseInt(page));
            } catch (NumberFormatException ignored) {}
        }
        return maxPage + 1;
    }

    // =========================================================================
    // PHYSICAL MEMORY VIEW (COLUMN-MAJOR: F00-F07 LEFT, F08-F15 RIGHT)
    // =========================================================================
    private static class PhysicalMemoryView extends JPanel {
        private long pageSize = 1024;
        private Map<Integer, Integer> pageToFrameMap = new LinkedHashMap<>();
        private Map<Integer, Integer> frameToPageMap = new LinkedHashMap<>();
        private int selectedFrame = -1;
        private static final int TOTAL_FRAMES = 16;
        private static final int FRAMES_PER_COL = 8; // Column-major contiguous reading!

        public PhysicalMemoryView() {
            setBackground(new Color(12, 9, 24));
            setPreferredSize(new Dimension(280, FRAMES_PER_COL * 40 + 20));
        }

        public void setMemoryState(long ps, Map<Integer, Integer> p2f, int selFrame) {
            this.pageSize = (ps > 0) ? ps : 1024;
            this.pageToFrameMap = (p2f != null) ? new LinkedHashMap<>(p2f) : new LinkedHashMap<>();
            this.frameToPageMap.clear();

            for (Map.Entry<Integer, Integer> e : this.pageToFrameMap.entrySet()) {
                frameToPageMap.put(e.getValue(), e.getKey());
            }
            this.selectedFrame = selFrame;
            repaint();
        }

        public int getUsedFrameCount() {
            return frameToPageMap.size();
        }

        public void setSelectedFrame(int f) {
            this.selectedFrame = f;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int totalW = getWidth();
            int colGap = 8;
            int slotW = (totalW - 20 - colGap) / 2;
            int slotH = 34;
            int rowGap = 5;
            int startY = 8;

            // COLUMN-MAJOR: Frame 0..7 down Column 0, Frame 8..15 down Column 1!
            for (int f = 0; f < TOTAL_FRAMES; f++) {
                int col = f / FRAMES_PER_COL; // 0 for 0..7, 1 for 8..15
                int row = f % FRAMES_PER_COL; // 0..7 down
                int x = 10 + col * (slotW + colGap);
                int y = startY + row * (slotH + rowGap);

                boolean isSelected = (f == selectedFrame);
                boolean isMapped = frameToPageMap.containsKey(f);
                long baseAddr = (long) f * pageSize;

                // Frame Card
                if (isSelected) {
                    g2.setColor(new Color(25, 65, 45));
                    g2.fillRoundRect(x, y, slotW, slotH, 6, 6);
                    g2.setColor(RetroTheme.ACCENT_GREEN);
                    g2.setStroke(new BasicStroke(2.0f));
                    g2.drawRoundRect(x, y, slotW, slotH, 6, 6);
                } else if (isMapped) {
                    g2.setColor(new Color(22, 18, 44));
                    g2.fillRoundRect(x, y, slotW, slotH, 6, 6);
                    g2.setColor(new Color(65, 48, 100));
                    g2.setStroke(new BasicStroke(1.2f));
                    g2.drawRoundRect(x, y, slotW, slotH, 6, 6);
                } else {
                    g2.setColor(new Color(14, 11, 24));
                    g2.fillRoundRect(x, y, slotW, slotH, 6, 6);
                    g2.setColor(new Color(35, 28, 50));
                    g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{3, 3}, 0));
                    g2.drawRoundRect(x, y, slotW, slotH, 6, 6);
                }

                // Left: Frame Label
                g2.setFont(new Font("Consolas", Font.BOLD, 10));
                g2.setColor(isSelected ? RetroTheme.ACCENT_GREEN : (isMapped ? RetroTheme.ACCENT_CYAN : RetroTheme.TEXT_DIM));
                String frameStr = String.format("F%02d", f);
                if (isSelected) frameStr = "> " + frameStr;
                g2.drawString(frameStr, x + 5, y + 14);

                // Raised Base Address to 10px bold!
                g2.setFont(new Font("Consolas", Font.BOLD, 10));
                g2.setColor(new Color(170, 170, 195));
                g2.drawString(String.format("[%dB]", baseAddr), x + 5, y + 27);

                // Right: Mapped Page Badge
                if (isMapped) {
                    int p = frameToPageMap.get(f);
                    int badgeW = 42;
                    int bx = x + slotW - badgeW - 6;
                    g2.setColor(new Color(36, 26, 68));
                    g2.fillRoundRect(bx, y + 6, badgeW, slotH - 12, 4, 4);
                    g2.setColor(isSelected ? RetroTheme.ACCENT_GREEN : RetroTheme.ACCENT_YELLOW);
                    g2.setStroke(new BasicStroke(1.0f));
                    g2.drawRoundRect(bx, y + 6, badgeW, slotH - 12, 4, 4);

                    g2.setFont(new Font("Consolas", Font.BOLD, 9));
                    g2.drawString(String.format("P%d", p), bx + 8, y + 20);
                } else {
                    g2.setFont(new Font("Consolas", Font.PLAIN, 8));
                    g2.setColor(new Color(90, 80, 115));
                    g2.drawString("FREE", x + slotW - 28, y + 21);
                }
            }

            g2.dispose();
        }
    }
}
