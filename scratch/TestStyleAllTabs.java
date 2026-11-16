import arcadeos.*;
import javax.swing.*;
import javax.swing.text.*;
import java.awt.Color;

public class TestStyleAllTabs {
    public static void main(String[] args) throws Exception {
        Color boxColor = new Color(115, 95, 175);
        for (int m = 0; m < 8; m++) {
            ArchiveManualDialog dlg = new ArchiveManualDialog(null, null, m);
            java.lang.reflect.Field f = ArchiveManualDialog.class.getDeclaredField("tabTextPanes");
            f.setAccessible(true);
            JTextPane[] panes = (JTextPane[]) f.get(dlg);
            for (int t = 0; t < 5; t++) {
                JTextPane pane = panes[t];
                StyledDocument doc = pane.getStyledDocument();
                String text = doc.getText(0, doc.getLength());
                String[] lines = text.split("\n");
                int offset = 0;
                for (String line : lines) {
                    if (line.endsWith("│") || line.endsWith("║") || line.endsWith("╗") || line.endsWith("╝") || line.endsWith("┐") || line.endsWith("┘") || line.endsWith("┤")) {
                        int lastCharPos = offset + line.length() - 1;
                        Element elem = doc.getCharacterElement(lastCharPos);
                        AttributeSet as = elem.getAttributes();
                        Color color = StyleConstants.getForeground(as);
                        if (!boxColor.equals(color)) {
                            System.out.printf("M%d T%d Mismatch at '%s': char='%c', color=%s\n",
                                    m, t,
                                    line.substring(0, Math.min(25, line.length())),
                                    doc.getText(lastCharPos, 1).charAt(0),
                                    color);
                        }
                    }
                    offset += line.length() + 1;
                }
            }
            dlg.dispose();
        }
        System.out.println("Done checking all tabs.");
    }
}
