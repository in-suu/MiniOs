import arcadeos.*;
import javax.swing.*;
import javax.swing.text.*;
import java.awt.Color;

public class TestStyleTicks {
    public static void main(String[] args) throws Exception {
        JTextPane pane = new JTextPane();
        ArchiveManualDialog dlg = new ArchiveManualDialog(null, null, 2);
        // The dialog creates tabTextPanes
        // Let's inspect tabTextPanes[2] (pseudocode)
        java.lang.reflect.Field f = ArchiveManualDialog.class.getDeclaredField("tabTextPanes");
        f.setAccessible(true);
        JTextPane[] panes = (JTextPane[]) f.get(dlg);
        JTextPane pseudoPane = panes[2];
        StyledDocument doc = pseudoPane.getStyledDocument();
        String text = doc.getText(0, doc.getLength());
        String[] lines = text.split("\n");
        int offset = 0;
        for (String line : lines) {
            if (line.endsWith("│") || line.endsWith("║")) {
                int lastCharPos = offset + line.length() - 1;
                Element elem = doc.getCharacterElement(lastCharPos);
                AttributeSet as = elem.getAttributes();
                Color color = StyleConstants.getForeground(as);
                boolean bold = StyleConstants.isBold(as);
                // Box color is new Color(115, 95, 175)
                Color boxColor = new Color(115, 95, 175);
                if (!boxColor.equals(color)) {
                    System.out.printf("Mismatch at '%s' (pos %d): char='%c', color=%s, bold=%b\n",
                            line.substring(0, Math.min(30, line.length())),
                            lastCharPos,
                            doc.getText(lastCharPos, 1).charAt(0),
                            color, bold);
                }
            }
            offset += line.length() + 1; // +1 for newline
        }
        System.out.println("TestStyleTicks completed.");
        dlg.dispose();
    }
}
