package arcadeos;

import game.snake.SnakeGameDialog;
import game.snake.SnakeReviewListener;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

public class SegmentationPanel extends JPanel implements SnakeReviewListener {
    private DefaultTableModel segModel;
    private JTextField txtOffset;
    private JSlider sliderOffset;
    private JLabel lblOffsetStatus;
    private JComboBox<String> cmbSeg;
    private ResultCard resultCard;
    private JTextPane txtLog;
    private JTable table;
    private MemoryMapPanel memoryMap;
    private boolean syncingSelector = false;

    /** Fixed red for faults - distinct from the pink accent and the magenta stack. */
    private static final Color FAULT_RED = new Color(255, 74, 92);

    /**
     * A2: fixed neon palette for segments. Deliberately avoids pure green and
     * pure red, which are reserved for "valid" and "fault / overlap". A segment
     * keeps its colour by number, so reordering rows does not reshuffle hues and
     * the table swatch (A5) can match the strip exactly.
     */
    private static final Color[] SEG_PALETTE = {
            new Color(5, 217, 232),    // cyan
            new Color(255, 42, 141),   // magenta
            new Color(255, 211, 25),   // yellow
            new Color(255, 140, 40),   // orange
            new Color(180, 0, 255),    // violet
            new Color(70, 120, 255),   // blue
            new Color(0, 200, 180),    // teal
            new Color(255, 120, 200),  // pink
    };

    /** Last successful translation: model row of the picked segment and its physical address (-1 = none). */
    private int probeRow = -1;
    private int probePhys = -1;

    /** Last fault: the selected segment row and the address it tried to reach (-1 = none). */
    private int faultRow = -1;
    private int faultPA = -1;

    /** Size (in bytes) handed to a segment created by the "+ Add Segment" button. */
    private static final int NEW_SEG_LIMIT = 500;

    public SegmentationPanel() {
        setBackground(RetroTheme.BG_DARK);
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel ctrl = new JPanel(new BorderLayout(8, 0));
        ctrl.setBackground(RetroTheme.PANEL_BG);
        ctrl.setBorder(BorderFactory.createLineBorder(RetroTheme.BORDER_COLOR));

        JButton btnAdd = RetroTheme.createRetroButton("+ Add Segment", new Color(40, 26, 74), RetroTheme.ACCENT_CYAN);
        JButton btnRemove = RetroTheme.createRetroButton("- Remove", new Color(40, 26, 74), RetroTheme.ACCENT_PINK);
        btnAdd.setMnemonic('A');
        btnRemove.setMnemonic('R');
        JButton btnSmall = RetroTheme.createRetroButton("Preset: Small (3)", new Color(35, 35, 60), RetroTheme.TEXT_WHITE);
        JButton btnLarge = RetroTheme.createRetroButton("Preset: Large (6)", new Color(35, 35, 60), RetroTheme.TEXT_WHITE);

        // Left cluster: table editing actions.
        JPanel ctrlLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        ctrlLeft.setOpaque(false);
        ctrlLeft.add(btnAdd);
        ctrlLeft.add(btnRemove);
        ctrlLeft.add(btnSmall);
        ctrlLeft.add(btnLarge);
        ctrl.add(ctrlLeft, BorderLayout.WEST);

        // Right cluster: the arcade launcher is a primary action, so it lives on the
        // roomy top bar. Squeezing it onto the translate row made FlowLayout wrap it
        // off the panel edge where it was clipped.
        JPanel ctrlRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        ctrlRight.setOpaque(false);
        JButton btnArcade = RetroTheme.createRetroButton("ARCADE: HEAP vs STACK SNAKE", new Color(48, 26, 78), RetroTheme.ACCENT_GREEN);
        btnArcade.setIcon(new ImageIcon(PixelSprites.createRobotSprite(14)));
        btnArcade.setIconTextGap(6);
        btnArcade.setToolTipText("Launch HEAP vs STACK SNAKE, the Mission 05 segmentation arcade game");
        btnArcade.addActionListener(e -> launchSegRunner());
        ctrlRight.add(btnArcade);
        ctrl.add(ctrlRight, BorderLayout.EAST);

        add(ctrl, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(1, 2, 8, 8));
        center.setOpaque(false);

        segModel = new DefaultTableModel(new String[]{"", "#", "Segment Name", "Base", "Limit", "End"}, 0);

        // Rows whose [base, base+limit) range overlaps another segment are tinted red.
        table = RetroTheme.createRetroTable(segModel, this::rowOverlaps);
        table.getColumnModel().getColumn(0).setPreferredWidth(28);
        table.getColumnModel().getColumn(0).setMaxWidth(30);
        table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean isSelected, boolean hasFocus, int row, int col) {
                JPanel p = new JPanel() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        Graphics2D g2 = (Graphics2D) g;
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        Color c = colorForRow(row);
                        g2.setColor(c);
                        g2.fillRoundRect(4, 4, getWidth() - 8, getHeight() - 8, 4, 4);
                        g2.setColor(Color.WHITE);
                        g2.drawRoundRect(4, 4, getWidth() - 8, getHeight() - 8, 4, 4);
                    }
                };
                p.setBackground(isSelected ? new Color(35, 45, 75) : RetroTheme.INPUT_BG);
                return p;
            }
        });
        table.getColumnModel().getColumn(1).setPreferredWidth(36);
        table.getColumnModel().getColumn(1).setMaxWidth(45);
        DefaultTableCellRenderer centerRend = new DefaultTableCellRenderer();
        centerRend.setHorizontalAlignment(SwingConstants.CENTER);
        table.getColumnModel().getColumn(1).setCellRenderer(centerRend);

        table.getColumnModel().getColumn(2).setPreferredWidth(130);
        table.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean isSelected, boolean hasFocus, int row, int col) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(t, val, isSelected, hasFocus, row, col);
                if (rowOverlaps(row)) {
                    lbl.setText("⚠ " + (val != null ? val.toString() : ""));
                    lbl.setForeground(FAULT_RED);
                    lbl.setToolTipText("Warning: this segment overlaps another segment's address range!");
                } else {
                    lbl.setForeground(RetroTheme.TEXT_WHITE);
                    lbl.setToolTipText(null);
                }
                return lbl;
            }
        });

        DefaultTableCellRenderer rightRend = new DefaultTableCellRenderer();
        rightRend.setHorizontalAlignment(SwingConstants.RIGHT);
        table.getColumnModel().getColumn(3).setCellRenderer(rightRend);
        table.getColumnModel().getColumn(4).setCellRenderer(rightRend);
        table.getColumnModel().getColumn(5).setCellRenderer(rightRend);

        JScrollPane scroll = RetroTheme.createRetroScroll(table);
        scroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(RetroTheme.BORDER_COLOR), " [ SEGMENT TABLE ] ",
                TitledBorder.LEFT, TitledBorder.TOP, RetroTheme.RETRO_FONT_BOLD, RetroTheme.ACCENT_CYAN
        ));
        center.add(scroll);

        JPanel transBox = new JPanel(new BorderLayout(8, 8));
        transBox.setBackground(RetroTheme.PANEL_BG);
        transBox.setBorder(BorderFactory.createLineBorder(RetroTheme.BORDER_COLOR));

        JPanel inputs = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        inputs.setOpaque(false);

        // One selector only: the dropdown is the single source of truth.
        JLabel lblPick = new JLabel("Pick:");
        lblPick.setFont(RetroTheme.RETRO_FONT_BOLD);
        lblPick.setForeground(RetroTheme.ACCENT_CYAN);
        inputs.add(lblPick);
        cmbSeg = new JComboBox<>();
        RetroTheme.styleComboBox(cmbSeg);
        cmbSeg.setPreferredSize(new Dimension(140, 26));
        cmbSeg.setToolTipText("Choose the segment to translate (bound to the segment table)");
        inputs.add(cmbSeg);

        JLabel lblOff = new JLabel("Offset:");
        lblOff.setFont(RetroTheme.RETRO_FONT_BOLD);
        lblOff.setForeground(RetroTheme.ACCENT_CYAN);
        inputs.add(lblOff);
        txtOffset = RetroTheme.createRetroTextField("250", 0);
        txtOffset.setPreferredSize(new Dimension(80, 28));
        txtOffset.setMinimumSize(new Dimension(80, 28));
        txtOffset.setToolTipText("Offset into the selected segment (0 <= offset < limit)");
        inputs.add(txtOffset);

        sliderOffset = new JSlider(0, 600, 250);
        sliderOffset.setPreferredSize(new Dimension(120, 26));
        sliderOffset.setOpaque(false);
        sliderOffset.setToolTipText("Offset slider: extends past limit (turns red on fault)");
        lblOffsetStatus = new JLabel("[OK]");
        lblOffsetStatus.setFont(RetroTheme.RETRO_FONT_BOLD);
        lblOffsetStatus.setForeground(RetroTheme.ACCENT_GREEN);
        inputs.add(sliderOffset);
        inputs.add(lblOffsetStatus);

        sliderOffset.addChangeListener(e -> {
            if (sliderOffset.getValueIsAdjusting()) {
                txtOffset.setText(String.valueOf(sliderOffset.getValue()));
            }
            updateSliderStatus();
            memoryMap.repaint();
            liveValidate();
        });

        txtOffset.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { sync(); }
            public void removeUpdate(DocumentEvent e) { sync(); }
            public void changedUpdate(DocumentEvent e) { sync(); }
            private void sync() {
                Integer v = parseInt(txtOffset.getText());
                if (v != null) {
                    // P2: Automatically expand slider maximum if typed offset exceeds current maximum
                    if (v > sliderOffset.getMaximum()) {
                        sliderOffset.setMaximum(Math.max(v + 100, (int) (v * 1.25)));
                    }
                    if (v >= sliderOffset.getMinimum() && v <= sliderOffset.getMaximum()) {
                        sliderOffset.setValue(v);
                    }
                }
                updateSliderStatus();
                memoryMap.repaint();
                liveValidate();
            }
        });

        cmbSeg.addActionListener(e -> {
            if (syncingSelector) return;
            Integer segNum = selectedSegmentNumber();
            if (segNum == null) return;
            // Sync table row to match the combo selection
            for (int r = 0; r < segModel.getRowCount(); r++) {
                if (rowNum(r) == segNum) {
                    syncingSelector = true;
                    table.setRowSelectionInterval(r, r);
                    syncingSelector = false;
                    break;
                }
            }
            updateSliderForSelected();
            liveValidate();
        });

        txtOffset.addActionListener(e -> validateAddress());

        JButton btnVal = RetroTheme.createRetroButton("TRANSLATE / VALIDATE", RetroTheme.ACCENT_YELLOW, Color.BLACK);
        btnVal.setMnemonic('V');
        inputs.add(btnVal);

        // C3: CTA button on the manual screen to launch the snake game with the selected segment
        JButton btnPlay = RetroTheme.createRetroButton("[ PLAY THIS SEGMENT ]", new Color(57, 255, 20), Color.BLACK);
        btnPlay.setMnemonic('P');
        btnPlay.setToolTipText("Launch Snake Game arena with the selected segment [Alt+P]");
        btnPlay.addActionListener(e -> launchSegRunner());
        inputs.add(btnPlay);

        // A1: the result card sits directly under the controls row and owns the
        // status line, so the log below can stay a compact scroll of traces.
        resultCard = new ResultCard();
        resultCard.show("READY", RetroTheme.ACCENT_CYAN,
                "Drag the slider or type an offset.", "");
        JPanel northWrap = new JPanel(new BorderLayout(0, 8));
        northWrap.setOpaque(false);
        northWrap.add(inputs, BorderLayout.NORTH);
        northWrap.add(resultCard, BorderLayout.CENTER);
        transBox.add(northWrap, BorderLayout.NORTH);

        // A2: one full-width memory strip with zoom inset.
        memoryMap = new MemoryMapPanel();
        JPanel mapWrap = new JPanel(new BorderLayout());
        mapWrap.setBackground(RetroTheme.INPUT_BG);
        mapWrap.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(RetroTheme.BORDER_COLOR), " [ PHYSICAL MEMORY STRIP & ZOOM INSET ] ",
                TitledBorder.LEFT, TitledBorder.TOP, RetroTheme.RETRO_FONT_BOLD, RetroTheme.ACCENT_CYAN
        ));
        mapWrap.add(memoryMap, BorderLayout.CENTER);
        transBox.add(mapWrap, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(4, 6));
        south.setOpaque(false);

        txtLog = new JTextPane();
        txtLog.setBackground(RetroTheme.INPUT_BG);
        txtLog.setForeground(RetroTheme.TEXT_WHITE);
        txtLog.setFont(RetroTheme.RETRO_FONT);
        txtLog.setMargin(new Insets(8, 10, 8, 10));
        txtLog.setEditable(false);
        JScrollPane scrollLog = RetroTheme.createRetroScroll(txtLog);
        scrollLog.setPreferredSize(new Dimension(300, 168));
        scrollLog.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(RetroTheme.BORDER_COLOR), " [ VALIDATION LOG ] ",
                TitledBorder.LEFT, TitledBorder.TOP, RetroTheme.RETRO_FONT_BOLD, RetroTheme.ACCENT_CYAN
        ));
        south.add(scrollLog, BorderLayout.CENTER);
        transBox.add(south, BorderLayout.SOUTH);

        center.add(transBox);
        add(center, BorderLayout.CENTER);

        // Keep the selector + memory map in sync whenever the table changes.
        segModel.addTableModelListener(ev -> {
            refreshSegmentSelector();
            memoryMap.repaint();
        });

        // Clicking a table row highlights it and syncs the segment picker.
        table.getSelectionModel().addListSelectionListener(ev -> {
            if (ev.getValueIsAdjusting() || syncingSelector) return;
            int r = table.getSelectedRow();
            if (r < 0) return;
            selectPicker(String.valueOf(rowNum(r)));
            updateSliderForSelected();
            memoryMap.repaint();
        });

        btnAdd.addActionListener(e -> addSegmentInFirstFreeGap());
        btnRemove.addActionListener(e -> {
            int r = table.getSelectedRow();
            if (r >= 0) segModel.removeRow(r);
        });
        btnSmall.addActionListener(e -> loadSmallPreset());
        btnLarge.addActionListener(e -> loadLargePreset());
        btnVal.addActionListener(e -> validateAddress());

        loadSmallPreset();
    }

    // ── Table row helpers ────────────────────────────────────────────────────

    private static Integer parseInt(Object o) {
        if (o == null) return null;
        try { return Integer.parseInt(o.toString().trim()); }
        catch (NumberFormatException ex) { return null; }
    }

    private int rowNum(int r)   { if (r < 0 || r >= segModel.getRowCount()) return -1; Integer v = parseInt(segModel.getValueAt(r, 1)); return v == null ? -1 : v; }
    private String rowName(int r) { if (r < 0 || r >= segModel.getRowCount()) return ""; Object v = segModel.getValueAt(r, 2); return v == null ? "" : v.toString().trim(); }
    private int rowBase(int r)  { if (r < 0 || r >= segModel.getRowCount()) return -1; Integer v = parseInt(segModel.getValueAt(r, 3)); return v == null ? -1 : v; }
    private int rowLimit(int r) { if (r < 0 || r >= segModel.getRowCount()) return -1; Integer v = parseInt(segModel.getValueAt(r, 4)); return v == null ? -1 : v; }

    private void updateSliderStatus() {
        if (lblOffsetStatus == null) return;
        Integer off = parseInt(txtOffset.getText());
        int r = selRow();
        int lim = (r >= 0) ? rowLimit(r) : 400;
        if (off != null && lim > 0 && off >= lim) {
            lblOffsetStatus.setText("[TRAP]");
            lblOffsetStatus.setForeground(FAULT_RED);
        } else {
            lblOffsetStatus.setText("[OK]");
            lblOffsetStatus.setForeground(RetroTheme.ACCENT_GREEN);
        }
    }

    private void updateSliderForSelected() {
        if (sliderOffset == null) return;
        int r = selRow();
        int lim = (r >= 0) ? rowLimit(r) : 400;
        int max = Math.max(100, (int) (lim * 1.5));
        sliderOffset.setMaximum(max);
        Integer cur = parseInt(txtOffset.getText());
        if (cur != null) sliderOffset.setValue(cur);
        updateSliderStatus();
    }

    /**
     * Item 3: position of a segment among the bars, ordered by base address (then
     * by row). Colouring by rank - not by segment number - guarantees that two
     * neighbouring bars never share a hue, which is what the eye actually reads.
     * Returns -1 for rows that have no bar (bad base or non-positive limit).
     */
    private int rankOfRow(int row) {
        int b = rowBase(row), l = rowLimit(row);
        if (row < 0 || b < 0 || l <= 0) return -1;
        int rank = 0;
        for (int i = 0; i < segModel.getRowCount(); i++) {
            if (i == row) continue;
            int bi = rowBase(i), li = rowLimit(i);
            if (bi < 0 || li <= 0) continue;
            if (bi < b || (bi == b && i < row)) rank++;
        }
        return rank;
    }

    /** A2/Item 3: stable palette colour for a segment row, keyed by its rank. */
    private Color colorForRow(int row) {
        int rank = rankOfRow(row);
        return rank < 0 ? RetroTheme.TEXT_DIM : SEG_PALETTE[rank % SEG_PALETTE.length];
    }

    /** Item 3: ranks past the 8th reuse the hues but get a diagonal hatch so they differ. */
    private boolean patternedRow(int row) {
        return rankOfRow(row) >= SEG_PALETTE.length;
    }

    /** Model row that the Pick dropdown currently points at, or -1. */
    private int selRow() {
        Integer num = selectedSegmentNumber();
        if (num == null) return -1;
        for (int i = 0; i < segModel.getRowCount(); i++) if (rowNum(i) == num) return i;
        return -1;
    }

    /** True when this row's [base, base+limit) range intersects another segment's range. */
    private boolean rowOverlaps(int row) {
        int b1 = rowBase(row), l1 = rowLimit(row);
        if (b1 < 0 || l1 <= 0) return false;
        for (int j = 0; j < segModel.getRowCount(); j++) {
            if (j == row) continue;
            int b2 = rowBase(j), l2 = rowLimit(j);
            if (b2 < 0 || l2 <= 0) continue;
            if (b1 < b2 + l2 && b2 < b1 + l1) return true;
        }
        return false;
    }

    private void refreshSegmentSelector() {
        if (cmbSeg == null) return;
        syncingSelector = true;
        Object prev = cmbSeg.getSelectedItem();
        cmbSeg.removeAllItems();
        for (int i = 0; i < segModel.getRowCount(); i++) {
            cmbSeg.addItem(rowNum(i) + ": " + rowName(i));
        }
        if (prev != null) cmbSeg.setSelectedItem(prev);
        if (cmbSeg.getItemCount() > 0 && cmbSeg.getSelectedIndex() < 0) cmbSeg.setSelectedIndex(0);
        int r = selRow();
        if (r >= 0 && r < table.getRowCount()) {
            table.setRowSelectionInterval(r, r);
        }
        updateSliderForSelected();
        syncingSelector = false;
    }

    private void selectPicker(String num) {
        if (cmbSeg == null) return;
        syncingSelector = true;
        for (int i = 0; i < cmbSeg.getItemCount(); i++) {
            if (cmbSeg.getItemAt(i).startsWith(num + ":")) { cmbSeg.setSelectedIndex(i); break; }
        }
        syncingSelector = false;
    }

    /** Adds a segment in the lowest free address gap instead of stacking on a fixed base. */
    private void addSegmentInFirstFreeGap() {
        List<int[]> occupied = new ArrayList<>();   // {start, end}
        for (int i = 0; i < segModel.getRowCount(); i++) {
            int b = rowBase(i), l = rowLimit(i);
            if (b >= 0 && l > 0) occupied.add(new int[]{b, b + l});
        }
        Collections.sort(occupied, (a, b) -> Integer.compare(a[0], b[0]));

        int base = 0;
        for (int[] iv : occupied) {
            if (base + NEW_SEG_LIMIT <= iv[0]) break;   // gap large enough before this interval
            if (iv[1] > base) base = iv[1];             // slide past the overlapping interval
        }

        int num = nextSegmentNumber();
        segModel.addRow(new Object[]{"", String.valueOf(num), "Seg_" + num, String.valueOf(base), String.valueOf(NEW_SEG_LIMIT), String.valueOf(base + NEW_SEG_LIMIT - 1)});
    }

    private int nextSegmentNumber() {
        int max = -1;
        for (int i = 0; i < segModel.getRowCount(); i++) max = Math.max(max, rowNum(i));
        return max + 1;
    }

    private void loadSmallPreset() {
        segModel.setRowCount(0);
        segModel.addRow(new Object[]{"", "0", "Arcade_Sprites", "1400", "400", "1799"});
        segModel.addRow(new Object[]{"", "1", "Audio_Engine",   "6300", "300", "6599"});
        segModel.addRow(new Object[]{"", "2", "High_Scores",    "4300", "500", "4799"});
    }

    private void loadLargePreset() {
        segModel.setRowCount(0);
        segModel.addRow(new Object[]{"", "0", "Main_Kernel",    "1000", "1200", "2199"});
        segModel.addRow(new Object[]{"", "1", "Sprite_VRAM",    "2400", "800",  "3199"});
        segModel.addRow(new Object[]{"", "2", "Sound_Chip",     "3600", "600",  "4199"});
        segModel.addRow(new Object[]{"", "3", "Player_States",  "4500", "300",  "4799"});
        segModel.addRow(new Object[]{"", "4", "Level_Map_Data", "5000", "1500", "6499"});
        segModel.addRow(new Object[]{"", "5", "Save_State_RAM", "7000", "400",  "7399"});
    }

    public void loadManualExample() {
        segModel.setRowCount(0);
        segModel.addRow(new Object[]{"", "0", "Main_Code",    "1400", "1000", "2399"});
        segModel.addRow(new Object[]{"", "1", "Call_Stack",   "6300", "400",  "6699"});
        segModel.addRow(new Object[]{"", "2", "Symbol_Table", "4300", "500",  "4799"});
        segModel.addRow(new Object[]{"", "3", "Subroutines",  "3200", "1500", "4699"});
        refreshSegmentSelector();
        selectPicker("0");
        txtOffset.setText("420");
        validateAddress();
    }

    /** Writes the full trace into the compact validation log with syntax highlighting. */
    private void setLog(String logText) {
        txtLog.setText("");
        StyledDocument doc = txtLog.getStyledDocument();
        String[] lines = logText.split("\n");
        for (String line : lines) {
            SimpleAttributeSet attr = new SimpleAttributeSet();
            if (line.contains("[OK]") || line.contains("SUCCESS") || line.contains("within limit")) {
                StyleConstants.setForeground(attr, RetroTheme.ACCENT_GREEN);
            } else if (line.contains("[X]") || line.contains("FAILED") || line.contains("OUT OF BOUNDS") || line.contains("TRAP") || line.contains("FAULT") || line.contains("MALFORMED")) {
                StyleConstants.setForeground(attr, FAULT_RED);
            } else if (line.contains("Rule Check") || line.contains("VALIDATION") || line.contains("Segment ")) {
                StyleConstants.setForeground(attr, RetroTheme.ACCENT_CYAN);
            } else {
                StyleConstants.setForeground(attr, RetroTheme.TEXT_WHITE);
            }
            try {
                doc.insertString(doc.getLength(), line + "\n", attr);
            } catch (Exception ignored) {}
        }
        txtLog.setCaretPosition(0);
    }

    private String validSelectors() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segModel.getRowCount(); i++) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(rowNum(i));
        }
        return sb.length() == 0 ? "(none)" : sb.toString();
    }

    /** Segment number currently selected in the Pick dropdown, or null when it is empty. */
    private Integer selectedSegmentNumber() {
        if (cmbSeg == null) return null;
        Object sel = cmbSeg.getSelectedItem();
        if (sel == null) return null;
        return parseInt(sel.toString().split(":")[0]);
    }

    /** Clears both the probe marker and the fault marker (used before each check). */
    private void clearStripMarks() {
        if (probeRow != -1 || probePhys != -1 || faultRow != -1 || faultPA != -1) {
            probeRow = -1;
            probePhys = -1;
            faultRow = -1;
            faultPA = -1;
            memoryMap.repaint();
        }
    }

    private void validateAddress() {
        Integer targetSeg = selectedSegmentNumber();
        String segText = targetSeg == null ? "(none picked)" : String.valueOf(targetSeg);
        String offText = txtOffset.getText().trim();
        Integer offset = parseInt(offText);

        // Blank / non-numeric input: distinct message, never a crash or modal.
        if (offset == null) {
            clearStripMarks();
            showFault("ENTER A NUMBER", "offset \"" + offText + "\" - not numeric");
            setLog("MALFORMED REQUEST\n" +
                    "Pick a segment and enter a numeric offset.\n\n" +
                    "  Segment : " + segText + "\n" +
                    "  Offset  : \"" + offText + "\"\n\n" +
                    "Re-enter a numeric offset, e.g. Offset 250.");
            return;
        }

        if (offset < 0) {
            clearStripMarks();
            showFault("NEGATIVE OFFSET", "offset " + offset + " - need 0 <= offset < limit");
            setLog("TRAP: SEGMENTATION FAULT\n" +
                    "Offset (" + offset + ") is negative.\n\n" +
                    "  Rule Check : 0 <= offset < limit\n" +
                    "  Received   : " + offset + "   [X] out of range");
            return;
        }

        int row = -1;
        for (int i = 0; i < segModel.getRowCount(); i++) {
            if (rowNum(i) == targetSeg) { row = i; break; }
        }

        // The pseudocode promises a distinct trap for an unknown selector.
        if (targetSeg == null || row < 0) {
            clearStripMarks();
            showFault("ILLEGAL SEGMENT", "segment " + segText + " is not in the table");
            setLog("TRAP: ILLEGAL SEGMENT SELECTOR\n" +
                    "Segment #" + segText + " is not defined in the segment table.\n\n" +
                    "  Valid selectors : " + validSelectors() + "\n" +
                    "  No base/limit can be fetched, so no translation is attempted.");
            return;
        }

        String name = rowName(row);
        int base = rowBase(row), limit = rowLimit(row);

        if (offset >= limit) {
            // FIX 5: leave a fault marker on the strip. probeRow records the
            // selected segment (so it stays highlighted) and faultPA records the
            // address it tried to reach, which may fall beyond the axis end.
            clearStripMarks();
            probeRow = row;
            faultRow = row;
            faultPA = base + offset;
            memoryMap.repaint();
            showFault("FAULT: offset >= limit", offset + " >= " + limit + "   ->   SEGMENTATION FAULT");
            setLog("VALIDATION FAILED:\n" +
                    "Segment " + targetSeg + " [" + name + "]\n" +
                    "  Base Address : " + base + "\n" +
                    "  Limit (Size) : " + limit + "\n" +
                    "  Offset       : " + offset + "\n\n" +
                    "  Rule Check   : offset < limit  ->  " + offset + " < " + limit + "   [X] OUT OF BOUNDS\n" +
                    "  Verdict      : SEGMENTATION FAULT (offset >= limit)");
        } else {
            int phys = base + offset;
            probeRow = row;
            probePhys = phys;
            memoryMap.repaint();
            // Two-step reveal: the base appears first, then the full sum (A1).
            resultCard.show("VALID -> PA " + phys, RetroTheme.ACCENT_GREEN,
                    "base  " + base,
                    base + "  +  offset " + offset + "  =  PA " + phys);
            setLog("VALIDATION SUCCESS:\n" +
                    "Segment " + targetSeg + " [" + name + "]\n" +
                    "  Base Address : " + base + "\n" +
                    "  Limit (Size) : " + limit + "\n" +
                    "  Offset       : " + offset + "\n\n" +
                    "  Rule Check   : offset < limit  ->  " + offset + " < " + limit + "   [OK] within limit\n" +
                    "  Translation  : base (" + base + ") + offset (" + offset + ") = PA " + phys);
        }
    }

    /** Shows a fault on the result card in the fixed red. */
    private void showFault(String title, String detail) {
        resultCard.show(title, FAULT_RED, detail, "");
    }

    /**
     * Live-update entry point: called whenever cmbSeg, txtOffset, or sliderOffset changes.
     * If the offset is valid (parseable), runs validateAddress() for a full live update.
     * If the offset is non-numeric or empty, resets the result card to the idle state
     * instead of showing a "ENTER A NUMBER" / "NEGATIVE OFFSET" error popup style.
     */
    private void liveValidate() {
        Integer offset = parseInt(txtOffset.getText());
        if (offset != null) {
            validateAddress();
        } else {
            clearStripMarks();
            resultCard.show("READY", RetroTheme.ACCENT_CYAN, "Drag the slider or type an offset.", "");
        }
    }

    /** Launches the Mission 05 arcade game, HEAP vs STACK SNAKE. */
    private void launchSegRunner() {
        JFrame topFrame = (JFrame) SwingUtilities.getWindowAncestor(this);

        // Build the arena inside the game package, so this panel only makes the
        // launch call. The picked segment becomes the arena (limit/100 rows,
        // clamped to 4..10); with nothing picked the game falls back to its
        // default level. The call returns a one-line report of what it received.
        // C2: pass 'this' as SnakeReviewListener so the game can review faults in the simulator.
        int row = selRow();
        String report = (row >= 0 && rowLimit(row) > 0)
                ? SnakeGameDialog.launchFromSegment(topFrame, rowName(row), rowBase(row), rowLimit(row), this)
                : SnakeGameDialog.launchDefault(topFrame, this);

        try {
            StyledDocument doc = txtLog.getStyledDocument();
            SimpleAttributeSet attr = new SimpleAttributeSet();
            StyleConstants.setForeground(attr, RetroTheme.ACCENT_CYAN);
            doc.insertString(doc.getLength(), "\n" + report + "\n", attr);
        } catch (Exception ignored) {}
        txtLog.setCaretPosition(txtLog.getDocument().getLength());
    }

    // ── C2: SnakeReviewListener implementation ───────────────────────────────
    @Override
    public void reviewInSimulator(int segmentNumber, int base, int limit, int offset) {
        // Find segment row with matching number or base/limit
        int targetRow = -1;
        for (int r = 0; r < segModel.getRowCount(); r++) {
            if (rowNum(r) == segmentNumber) {
                targetRow = r;
                break;
            }
        }
        if (targetRow < 0) {
            for (int r = 0; r < segModel.getRowCount(); r++) {
                if (rowBase(r) == base && rowLimit(r) == limit) {
                    targetRow = r;
                    break;
                }
            }
        }
        if (targetRow >= 0) {
            selectPicker(String.valueOf(rowNum(targetRow)));
            table.setRowSelectionInterval(targetRow, targetRow);
        }
        txtOffset.setText(String.valueOf(offset));
        updateSliderForSelected();
        liveValidate();
    }

    // ── Physical memory strip (A2) ───────────────────────────────────────────

    private static int clamp(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }

    private static int niceCeil(int v) {
        if (v <= 0) return 1000;
        int step = 1000;
        return ((v + step - 1) / step) * step;
    }

    /** Rounds an interval up to a tick grid that keeps the axis to <= 8 labels. */
    private static int axisStep(int axisMax) {
        int[] mults = {100, 200, 500, 1000, 2000, 5000, 10000, 20000, 50000, 100000};
        for (int m : mults) if (axisMax / m <= 8) return m;
        return 100000;
    }

    /** Black or white text, whichever reads better on the given fill. */
    private static Color contrast(Color c) {
        double lum = (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue()) / 255.0;
        return lum > 0.6 ? new Color(12, 10, 22) : Color.WHITE;
    }

    /** Merges {base, end} intervals so free gaps can be derived. */
    private static List<int[]> mergeIntervals(List<int[]> spans) {
        List<int[]> iv = new ArrayList<>();
        for (int[] s : spans) iv.add(new int[]{s[0], s[1]});
        iv.sort((a, b) -> Integer.compare(a[0], b[0]));
        List<int[]> out = new ArrayList<>();
        for (int[] cur : iv) {
            if (!out.isEmpty() && cur[0] <= out.get(out.size() - 1)[1]) {
                int[] last = out.get(out.size() - 1);
                last[1] = Math.max(last[1], cur[1]);
            } else {
                out.add(new int[]{cur[0], cur[1]});
            }
        }
        return out;
    }

    /**
     * A2: ONE full-width physical-memory strip. Every segment is a single bar in
     * its palette colour on a shared address axis; free gaps are labelled, ranges
     * claimed by two or more segments are shaded red, and the last successful
     * translation gets a PA probe. Sharing one row means the strip stays readable
     * even with 20+ segments.
     */
    private class MemoryMapPanel extends JPanel {
        /** Cursor position, the bar under it, and the delayed-tooltip state (FIX 2). */
        private int hoverX = -1, hoverY = -1;
        private int hoverRow = -1;
        private boolean tipVisible = false;
        private final Timer tipDelay = new Timer(380, e -> {
            if (hoverRow >= 0) { tipVisible = true; repaint(); }
        });

        MemoryMapPanel() {
            setBackground(RetroTheme.INPUT_BG);
            setPreferredSize(new Dimension(360, 200));
            setMinimumSize(new Dimension(150, 140));
            setToolTipText(null);   // FIX 2: we draw our own, clamped tooltip.
            tipDelay.setRepeats(false);
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    hoverX = e.getX();
                    hoverY = e.getY();
                    int[] gg = stripGeom();
                    boolean inStrip = e.getY() >= gg[3] && e.getY() <= gg[3] + gg[4];
                    int r = inStrip ? rowAt(e.getX()) : -1;
                    if (r != hoverRow) { hoverRow = r; tipVisible = false; }
                    if (hoverRow >= 0) { if (!tipVisible && !tipDelay.isRunning()) tipDelay.restart(); }
                    else { tipDelay.stop(); tipVisible = false; }
                    repaint();
                }
                @Override public void mouseExited(MouseEvent e) {
                    hoverRow = -1; hoverX = hoverY = -1; tipVisible = false; tipDelay.stop(); repaint();
                }
            };
            addMouseMotionListener(ma);
            addMouseListener(ma);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setFont(RetroTheme.FONT_XS);
            FontMetrics fm = g2.getFontMetrics();
            int w = getWidth(), h = getHeight();

            List<int[]> segs = new ArrayList<>();   // {row, base, limit}
            for (int i = 0; i < segModel.getRowCount(); i++) {
                int b = rowBase(i), l = rowLimit(i);
                if (b >= 0 && l > 0) segs.add(new int[]{i, b, l});
            }

            int[] gg = stripGeom();
            int left = gg[0], right = gg[1], span = gg[2], stripTop = gg[3], stripH = gg[4], axisMax = gg[5];
            int axisY = stripTop + stripH + 6;

            g2.setColor(new Color(14, 11, 26));
            g2.fillRect(left, stripTop, span, stripH);
            g2.setColor(RetroTheme.BORDER_COLOR);
            g2.drawRect(left, stripTop, span, stripH);

            int step = axisStep(axisMax);
            for (int a = 0; a <= axisMax; a += step) {
                int x = left + (int) Math.round(span * (a / (double) axisMax));
                g2.setColor(RetroTheme.BORDER_COLOR);
                g2.drawLine(x, axisY, x, axisY + 3);
                g2.setColor(RetroTheme.TEXT_DIM);
                String lbl = String.valueOf(a);
                int lw = fm.stringWidth(lbl);
                g2.drawString(lbl, clamp(x - lw / 2, left, Math.max(left, right - lw)), axisY + 12);
            }
            if (axisMax % step != 0) {
                String lbl = String.valueOf(axisMax);
                int lw = fm.stringWidth(lbl);
                g2.setColor(RetroTheme.BORDER_COLOR);
                g2.drawLine(right, axisY, right, axisY + 3);
                g2.setColor(RetroTheme.TEXT_DIM);
                g2.drawString(lbl, Math.max(left, right - lw), axisY + 12);
            }

            if (segs.isEmpty()) {
                g2.setColor(RetroTheme.TEXT_DIM);
                g2.drawString("no segments defined", left + 4, stripTop + stripH / 2 + 3);
                g2.dispose();
                return;
            }

            // One bar per segment, coloured by rank so neighbouring bars never
            // share a hue. Ranks past the 8th repeat the hue but get a diagonal
            // hatch, and a 1px dark separator splits touching bars (FIX 3).
            int sel = selRow();
            for (int[] s : segs) {
                int row = s[0], base = s[1], limit = s[2];
                int xa = left + (int) Math.round(span * (base / (double) axisMax));
                int xb = left + (int) Math.round(span * ((base + limit) / (double) axisMax));
                int bw = Math.max(2, xb - xa);
                Color c = colorForRow(row);

                g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 165));
                g2.fillRect(xa, stripTop, bw, stripH);
                if (patternedRow(row)) drawHatch(g2, xa, stripTop, bw, stripH, new Color(10, 8, 18, 150));

                boolean highlighted = (row == sel || row == probeRow || row == faultRow);
                g2.setColor(highlighted ? (row == faultRow ? FAULT_RED : Color.WHITE) : c);
                g2.setStroke(new BasicStroke(highlighted ? 2f : 1f));
                g2.drawRect(xa, stripTop, bw, stripH);
                g2.setStroke(new BasicStroke(1f));

                // FIX 3: 1px dark separator on the trailing edge.
                g2.setColor(new Color(8, 6, 16));
                int sx = Math.min(xb, right);
                g2.drawLine(sx, stripTop, sx, stripTop + stripH);

                // Strip label: segment index (#0, #1) on every bar.
                String glyph = "#" + rowNum(row);
                if (fm.stringWidth(glyph) <= Math.max(1, bw - 2)) {
                    g2.setColor(contrast(c));
                    g2.drawString(glyph, xa + (bw - fm.stringWidth(glyph)) / 2,
                            stripTop + (stripH + fm.getAscent()) / 2 - 1);
                }
            }

            // Red hatch over any address range claimed by two or more segments.
            TreeSet<Integer> pts = new TreeSet<>();
            pts.add(0);
            pts.add(axisMax);
            for (int[] s : segs) {
                pts.add(clamp(s[1], 0, axisMax));
                pts.add(clamp(s[1] + s[2], 0, axisMax));
            }
            List<Integer> cuts = new ArrayList<>(pts);
            for (int k = 0; k + 1 < cuts.size(); k++) {
                int a = cuts.get(k), b = cuts.get(k + 1);
                if (b <= a) continue;
                int cover = 0;
                for (int[] s : segs) if (s[1] < b && a < s[1] + s[2]) cover++;
                if (cover < 2) continue;
                int xa = left + (int) Math.round(span * (a / (double) axisMax));
                int xb = left + (int) Math.round(span * (b / (double) axisMax));
                int bw = Math.max(1, xb - xa);
                g2.setColor(new Color(255, 60, 90, 90));
                g2.fillRect(xa, stripTop, bw, stripH);
                g2.setColor(new Color(255, 90, 120, 160));
                Shape old = g2.getClip();
                g2.clipRect(xa, stripTop, bw, stripH);
                for (int x = xa - stripH; x < xa + bw; x += 7) {
                    g2.drawLine(x, stripTop + stripH, x + stripH, stripTop);
                }
                g2.setClip(old);
            }

            // Label the free gaps (unclaimed address ranges).
            List<int[]> occupied = new ArrayList<>();
            for (int[] s : segs) occupied.add(new int[]{s[1], s[1] + s[2]});
            int cursor = 0;
            g2.setColor(RetroTheme.TEXT_DIM);
            for (int[] m : mergeIntervals(occupied)) {
                if (m[0] > cursor) labelGap(g2, fm, left, span, axisMax, stripTop, stripH, cursor, m[0]);
                cursor = Math.max(cursor, m[1]);
            }
            if (cursor < axisMax) labelGap(g2, fm, left, span, axisMax, stripTop, stripH, cursor, axisMax);

            // Probe: the physical address produced by the last successful validation.
            if (probePhys >= 0) {
                int px = left + (int) Math.round(span * (clamp(probePhys, 0, axisMax) / (double) axisMax));
                px = clamp(px, left, right);

                g2.setColor(RetroTheme.ACCENT_YELLOW);
                g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                        1f, new float[]{4f, 3f}, 0f));
                g2.drawLine(px, stripTop - 5, px, axisY);
                g2.setStroke(new BasicStroke(1f));
                g2.fillOval(px - 3, axisY - 6, 6, 6);

                String tag = "PA " + probePhys;
                int tw = fm.stringWidth(tag);
                int tx = clamp(px - tw / 2, left + 3, Math.max(left + 3, right - tw - 3));
                int ty = Math.max(0, stripTop - 19);
                g2.setColor(new Color(15, 11, 28, 225));
                g2.fillRect(tx - 3, ty, tw + 6, 14);
                g2.setColor(RetroTheme.ACCENT_YELLOW);
                g2.drawRect(tx - 3, ty, tw + 6, 14);
                g2.drawString(tag, tx, ty + 11);
            }

            // Fault state: outline, red hatch beyond-limit area, red PA marker/arrow.
            if (faultRow >= 0 && faultRow < segModel.getRowCount() && faultPA >= 0) {
                int fb = rowBase(faultRow), fl = rowLimit(faultRow);
                int oobLo = clamp(fb + fl, 0, axisMax);
                int oobHi = clamp(faultPA, 0, axisMax);
                if (oobHi > oobLo) {
                    int xa = left + (int) Math.round(span * (oobLo / (double) axisMax));
                    int xb = left + (int) Math.round(span * (oobHi / (double) axisMax));
                    int bw = Math.max(1, xb - xa);
                    g2.setColor(new Color(255, 40, 70, 110));
                    g2.fillRect(xa, stripTop, bw, stripH);
                    drawHatch(g2, xa, stripTop, bw, stripH, new Color(255, 90, 120, 215));
                }

                int fx = left + (int) Math.round(span * (clamp(faultPA, 0, axisMax) / (double) axisMax));
                fx = clamp(fx, left, right);
                g2.setColor(FAULT_RED);
                g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                        1f, new float[]{4f, 3f}, 0f));
                g2.drawLine(fx, stripTop - 5, fx, axisY);
                g2.setStroke(new BasicStroke(1f));
                g2.fillOval(fx - 3, axisY - 6, 6, 6);

                String tag = "FAULT PA " + faultPA;
                int tw = fm.stringWidth(tag);
                int tx = clamp(fx - tw / 2, left + 3, Math.max(left + 3, right - tw - 3));
                int ty = Math.max(0, stripTop - 19);
                g2.setColor(new Color(46, 8, 18, 235));
                g2.fillRect(tx - 3, ty, tw + 6, 14);
                g2.setColor(FAULT_RED);
                g2.drawRect(tx - 3, ty, tw + 6, 14);
                g2.drawString(tag, tx, ty + 11);

                if (faultPA > axisMax) {
                    int ay = stripTop + stripH / 2;
                    g2.setColor(FAULT_RED);
                    g2.fillPolygon(new int[]{right - 1, right - 11, right - 11},
                            new int[]{ay, ay - 6, ay + 6}, 3);

                    String l1 = "PA " + faultPA;
                    String l2 = "beyond mapped memory";
                    int otw = Math.max(fm.stringWidth(l1), fm.stringWidth(l2)) + 10;
                    int oth = fm.getHeight() * 2 + 8;
                    int otx = clamp(right - otw, left, Math.max(left, right - otw));
                    int oty = Math.max(2, stripTop - oth - 4);
                    g2.setColor(new Color(46, 8, 18, 235));
                    g2.fillRect(otx, oty, otw, oth);
                    g2.setColor(FAULT_RED);
                    g2.drawRect(otx, oty, otw, oth);
                    g2.drawString(l1, otx + 5, oty + 4 + fm.getAscent());
                    g2.drawString(l2, otx + 5, oty + 4 + fm.getAscent() + fm.getHeight());
                }
            }

            // Zoom Inset: detailed base-to-limit view of the selected segment with offset pointer.
            int zoomTop = axisY + 18;
            int zoomH = h - zoomTop - 6;
            drawZoomInset(g2, fm, left, right, span, zoomTop, zoomH);

            // FIX 2/4: the hover tooltip, drawn last so it sits above everything.
            drawTooltip(g2, left, span, stripTop, stripH, axisMax);

            g2.dispose();
        }

        private void labelGap(Graphics2D g2, FontMetrics fm, int left, int span, int axisMax,
                              int stripTop, int stripH, int lo, int hi) {
            int xa = left + (int) Math.round(span * (lo / (double) axisMax));
            int xb = left + (int) Math.round(span * (hi / (double) axisMax));
            if (xb - xa < 26) return;
            String lbl = "free";
            g2.setColor(RetroTheme.TEXT_DIM);
            g2.drawString(lbl, xa + (xb - xa - fm.stringWidth(lbl)) / 2,
                    stripTop + (stripH + fm.getAscent()) / 2 - 1);
        }

        /** Strip geometry for the current size: {left,right,span,stripTop,stripH,axisMax}. */
        private int[] stripGeom() {
            int w = getWidth(), h = getHeight();
            int maxEnd = 0;
            for (int i = 0; i < segModel.getRowCount(); i++) {
                int b = rowBase(i), l = rowLimit(i);
                if (b >= 0 && l > 0) maxEnd = Math.max(maxEnd, b + l);
            }
            int axisMax = niceCeil(maxEnd);
            int left = 10;
            int right = Math.max(left + 20, w - 10);
            int stripH = 26;
            int stripTop = 20;
            return new int[]{left, right, right - left, stripTop, stripH, axisMax};
        }

        private void drawZoomInset(Graphics2D g2, FontMetrics fm, int left, int right, int span, int topY, int maxH) {
            if (maxH < 36) return;
            int sel = selRow();
            if (sel < 0) sel = (faultRow >= 0) ? faultRow : ((probeRow >= 0) ? probeRow : (segModel.getRowCount() > 0 ? 0 : -1));
            if (sel < 0 || sel >= segModel.getRowCount()) {
                g2.setColor(RetroTheme.BORDER_COLOR);
                g2.drawRect(left, topY, span, maxH);
                g2.setColor(RetroTheme.TEXT_DIM);
                g2.drawString("Select a segment to view detailed offset zoom", left + 10, topY + maxH / 2 + 4);
                return;
            }

            int base = rowBase(sel);
            int limit = rowLimit(sel);
            String name = rowName(sel);
            Color segColor = colorForRow(sel);

            // Container box
            g2.setColor(new Color(10, 8, 20, 220));
            g2.fillRect(left, topY, span, maxH);
            g2.setColor(RetroTheme.BORDER_COLOR);
            g2.drawRect(left, topY, span, maxH);

            // Title header
            g2.setColor(RetroTheme.ACCENT_CYAN);
            g2.drawString("SEGMENT ZOOM: #" + rowNum(sel) + " [" + name + "]   Base: " + base + "   Limit: " + limit, left + 8, topY + 13);

            if (limit <= 0) {
                g2.setColor(RetroTheme.TEXT_DIM);
                g2.drawString("No valid limit defined for this segment", left + 8, topY + 30);
                return;
            }

            int barY = topY + 20;
            int barH = Math.max(16, Math.min(24, maxH - 46));
            int validW = (int) (span * 0.68);
            int invalidW = span - validW;

            // 1. Valid range [0 .. limit)
            g2.setColor(new Color(segColor.getRed(), segColor.getGreen(), segColor.getBlue(), 170));
            g2.fillRect(left, barY, validW, barH);
            g2.setColor(segColor);
            g2.drawRect(left, barY, validW, barH);

            g2.setColor(contrast(segColor));
            String validText = "VALID OFFSETS [0 .. " + (limit - 1) + "]";
            if (fm.stringWidth(validText) < validW - 10) {
                g2.drawString(validText, left + (validW - fm.stringWidth(validText)) / 2, barY + (barH + fm.getAscent()) / 2 - 2);
            }

            // 2. Beyond limit invalid range [limit ..]
            int invX = left + validW;
            g2.setColor(new Color(255, 40, 70, 90));
            g2.fillRect(invX, barY, invalidW, barH);
            drawHatch(g2, invX, barY, invalidW, barH, new Color(255, 80, 110, 200));
            g2.setColor(FAULT_RED);
            g2.drawRect(invX, barY, invalidW, barH);

            String invText = "TRAP: >= " + limit;
            if (fm.stringWidth(invText) < invalidW - 6) {
                g2.setColor(Color.WHITE);
                g2.drawString(invText, invX + (invalidW - fm.stringWidth(invText)) / 2, barY + (barH + fm.getAscent()) / 2 - 2);
            }

            // Sub-ticks and labels
            int subY = barY + barH + 11;
            g2.setColor(RetroTheme.TEXT_DIM);
            g2.drawString("0", left, subY);
            String limStr = "limit: " + limit;
            g2.drawString(limStr, left + validW - fm.stringWidth(limStr) / 2, subY);
            String maxStr = "+" + (int)(limit * 0.5) + " beyond";
            g2.drawString(maxStr, right - fm.stringWidth(maxStr), subY);

            // Live Offset Pointer
            Integer curOff = parseInt(txtOffset.getText());
            if (curOff == null) curOff = sliderOffset.getValue();
            if (curOff != null && curOff >= 0) {
                int ptrX;
                boolean isFault = (curOff >= limit);
                if (!isFault) {
                    ptrX = left + (int) Math.round(validW * (curOff / (double) limit));
                } else {
                    double extra = (curOff - limit) / Math.max(1.0, limit * 0.5);
                    ptrX = invX + (int) Math.round(invalidW * Math.min(1.0, extra));
                }
                ptrX = clamp(ptrX, left, right);

                Color ptrColor = isFault ? FAULT_RED : RetroTheme.ACCENT_GREEN;
                g2.setColor(ptrColor);
                g2.setStroke(new BasicStroke(1.8f));
                g2.drawLine(ptrX, barY - 2, ptrX, barY + barH + 2);
                g2.setStroke(new BasicStroke(1f));
                g2.fillPolygon(new int[]{ptrX, ptrX - 4, ptrX + 4}, new int[]{barY - 2, barY - 7, barY - 7}, 3);

                int pa = base + curOff;
                String tag = "offset " + curOff + (isFault ? " [TRAP >= " + limit + "]" : " -> PA " + pa + " [OK]");
                int tw = fm.stringWidth(tag);
                int tx = clamp(ptrX - tw / 2, left + 4, Math.max(left + 4, right - tw - 4));
                int ty = subY + 3;
                if (ty + 13 <= topY + maxH) {
                    g2.setColor(new Color(15, 11, 28, 220));
                    g2.fillRect(tx - 2, ty, tw + 4, 13);
                    g2.setColor(ptrColor);
                    g2.drawRect(tx - 2, ty, tw + 4, 13);
                    g2.drawString(tag, tx, ty + 10);
                }
            }
        }

        /** Model row whose bar covers this x, or -1. Later bars win so they match what is drawn. */
        private int rowAt(int x) {
            int[] gg = stripGeom();
            int left = gg[0], right = gg[1], span = gg[2], axisMax = gg[5];
            if (x < left || x > right) return -1;
            int hit = -1;
            for (int i = 0; i < segModel.getRowCount(); i++) {
                int b = rowBase(i), l = rowLimit(i);
                if (b < 0 || l <= 0) continue;
                int xa = left + (int) Math.round(span * (b / (double) axisMax));
                int xb = left + (int) Math.round(span * ((b + l) / (double) axisMax));
                if (x >= xa && x <= Math.max(xa + 1, xb)) hit = i;
            }
            return hit;
        }

        /** FIX 3: diagonal hatch clipped to a rectangle (used for the rank pattern and red faults). */
        private void drawHatch(Graphics2D g2, int x, int y, int w, int h, Color line) {
            Shape old = g2.getClip();
            g2.clipRect(x, y, w, h);
            g2.setColor(line);
            for (int sx = x - h; sx < x + w; sx += 6) g2.drawLine(sx, y + h, sx + h, y);
            g2.setClip(old);
        }

        /** FIX 4: truncates to fit a width, appending an ellipsis when it has to cut. */
        private String ellipsis(FontMetrics fm, String s, int maxW) {
            if (s == null) return "";
            if (maxW <= 0) return "";
            if (fm.stringWidth(s) <= maxW) return s;
            String ell = "\u2026";
            int ew = fm.stringWidth(ell);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                if (fm.stringWidth(sb.toString() + s.charAt(i)) + ew > maxW) break;
                sb.append(s.charAt(i));
            }
            return sb.length() == 0 ? "" : sb + ell;
        }

        /**
         * FIX 2: a cursor-adjacent tooltip shown after a short delay, clamped fully
         * inside the panel and placed so it never covers the bar under the cursor.
         * FIX 4: it carries the full segment name plus the base..end range.
         */
        private void drawTooltip(Graphics2D g2, int left, int span, int stripTop, int stripH, int axisMax) {
            if (!tipVisible || hoverRow < 0 || hoverRow >= segModel.getRowCount()) return;
            int w = getWidth(), h = getHeight();
            int b = rowBase(hoverRow), l = rowLimit(hoverRow);
            String name = rowName(hoverRow);
            if (name.isEmpty()) name = "Segment " + rowNum(hoverRow);
            String range = (l > 0)
                    ? ("base " + b + " .. " + (b + l - 1) + "   limit " + l)
                    : ("base " + b + "   (no valid limit)");

            Font oldFont = g2.getFont();
            g2.setFont(RetroTheme.FONT_BODY);
            FontMetrics tfm = g2.getFontMetrics();
            int pad = 6;
            int bw = Math.max(tfm.stringWidth(name), tfm.stringWidth(range)) + pad * 2;
            int bh = tfm.getHeight() * 2 + pad * 2;

            int xa = left + (int) Math.round(span * (Math.max(0, b) / (double) axisMax));
            int xb = left + (int) Math.round(span * (Math.max(0, b + Math.max(l, 1)) / (double) axisMax));
            xb = Math.max(xa + 1, xb);

            // Candidate anchors, in order of preference: right of the bar, left of
            // it, then above / below the strip. The first that sits fully inside the
            // panel without touching the hovered bar wins.
            int[][] cand = {
                    {xb + 8, hoverY - bh / 2},
                    {xa - 8 - bw, hoverY - bh / 2},
                    {hoverX - bw / 2, stripTop - 8 - bh},
                    {hoverX - bw / 2, stripTop + stripH + 34},
            };
            int bx = -1, by = -1;
            for (int[] p : cand) {
                int px = clamp(p[0], 4, Math.max(4, w - bw - 4));
                int py = clamp(p[1], 4, Math.max(4, h - bh - 4));
                boolean covers = px < xb && xa < px + bw && py < stripTop + stripH && stripTop < py + bh;
                if (!covers) { bx = px; by = py; break; }
            }
            if (bx < 0) {
                bx = clamp(hoverX - bw / 2, 4, Math.max(4, w - bw - 4));
                by = clamp(stripTop - 8 - bh, 4, Math.max(4, h - bh - 4));
            }

            g2.setColor(new Color(10, 8, 20, 238));
            g2.fillRect(bx, by, bw, bh);
            g2.setColor(RetroTheme.ACCENT_CYAN);
            g2.drawRect(bx, by, bw, bh);
            g2.drawString(name, bx + pad, by + pad + tfm.getAscent());
            g2.setColor(RetroTheme.TEXT_WHITE);
            g2.drawString(range, bx + pad, by + pad + tfm.getAscent() + tfm.getHeight());
            g2.setFont(oldFont);
        }
    }

    // ── Result card (A1) ──────────────────────────────────────────────────────

    /**
     * Neon result card shown directly under the controls. The status sits on a
     * large line; a successful translation reveals the sum in two steps (base
     * first, then base + offset = PA). Drawing is laid out from the font metrics
     * and long text is ellipsised, so nothing clips at any panel width.
     */
    private class ResultCard extends JPanel {
        private String title = "READY";
        private Color titleColor = RetroTheme.ACCENT_CYAN;
        private String line1 = "";
        private String line2 = "";
        private boolean showSecond = true;
        /** FIX 5: faults draw their explanation line bright instead of dim. */
        private boolean emphasizeLine1 = false;
        /** Reveals the second line a beat after the first, giving the two-step feel. */
        private final Timer reveal = new Timer(420, e -> { showSecond = true; repaint(); });

        ResultCard() {
            setBackground(RetroTheme.INPUT_BG);
            setPreferredSize(new Dimension(10, 86));
            reveal.setRepeats(false);
        }

        /** Shows a status; when {@code second} is non-empty it appears after a beat. */
        void show(String t, Color c, String first, String second) {
            this.title = t == null ? "" : t;
            this.titleColor = c;
            this.line1 = first == null ? "" : first;
            this.line2 = second == null ? "" : second;
            this.emphasizeLine1 = (c == FAULT_RED);
            reveal.stop();
            this.showSecond = this.line2.isEmpty();
            if (!this.showSecond) reveal.restart();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();

            g2.setColor(RetroTheme.INPUT_BG);
            g2.fillRoundRect(0, 0, w, h, 10, 10);
            g2.setColor(titleColor);
            g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);

            int pad = 12;
            g2.setFont(RetroTheme.FONT_LG);
            FontMetrics f1 = g2.getFontMetrics();
            g2.setColor(titleColor);
            g2.drawString(fit(f1, title, w - pad * 2), pad, pad + f1.getAscent());

            g2.setFont(RetroTheme.FONT_MD);
            FontMetrics f2 = g2.getFontMetrics();
            int y = pad + f1.getHeight() + 2 + f2.getAscent();
            g2.setColor(emphasizeLine1 ? RetroTheme.TEXT_WHITE : RetroTheme.TEXT_DIM);
            if (!line1.isEmpty()) g2.drawString(fit(f2, line1, w - pad * 2), pad, y);
            if (showSecond && !line2.isEmpty()) {
                g2.setColor(RetroTheme.TEXT_WHITE);
                g2.drawString(fit(f2, line2, w - pad * 2), pad, y + f2.getHeight());
            }
            g2.dispose();
        }

        /** Truncates with an ellipsis so long text never overflows the card. */
        private String fit(FontMetrics fm, String s, int maxW) {
            if (maxW <= 0 || fm.stringWidth(s) <= maxW) return s;
            String ell = "...";
            int ew = fm.stringWidth(ell);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                if (fm.stringWidth(sb.toString() + s.charAt(i)) + ew > maxW) break;
                sb.append(s.charAt(i));
            }
            return sb + ell;
        }
    }
}
