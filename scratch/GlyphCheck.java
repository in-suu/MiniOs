import arcadeos.ArchiveManualData;
import java.awt.Font;
import java.util.HashSet;
import java.util.Set;

public class GlyphCheck {
    public static void main(String[] args) {
        Font font = new Font("Consolas", Font.PLAIN, 12);
        String[] tabNames = {"theory", "formulas", "pseudocode", "workedExample", "cheatSheet"};
        Set<Character> offenders = new HashSet<>();
        for (int m = 0; m < 8; m++) {
            ArchiveManualData.ManualEntry entry = ArchiveManualData.getManual(m);
            String[] tabs = {entry.theory, entry.formulas, entry.pseudocode, entry.workedExample, entry.cheatSheet};
            for (int t = 0; t < 5; t++) {
                String text = tabs[t];
                if (text == null) continue;
                for (int i = 0; i < text.length(); i++) {
                    char ch = text.charAt(i);
                    if (ch == '\n' || ch == '\r' || ch == '\t') continue;
                    if (!font.canDisplay(ch)) {
                        offenders.add(ch);
                        System.out.printf("Manual %s / %s cannot display: '%c' (U+%04X)\n",
                                entry.id, tabNames[t], ch, (int) ch);
                    }
                }
            }
        }
        System.out.println("Total distinct missing glyphs: " + offenders.size());
        for (char c : offenders) {
            System.out.printf("  '%c' -> U+%04X\n", c, (int) c);
        }
    }
}
