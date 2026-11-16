package scratch;

import arcadeos.ArchiveManualData;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/** Audits every manual tab: border rows must be exactly 74 columns, nothing may exceed it. */
public class CheckLengths {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("dump")) {
            dump();
            return;
        }

        String[] tabNames = {"01 THEORY", "02 FORMULAS", "03 PSEUDOCODE", "04 WORKED TRACE", "05 CHEAT SHEET"};
        int problems = 0;

        for (int m = 0; m < 8; m++) {
            ArchiveManualData.ManualEntry e = ArchiveManualData.getManual(m);
            String[] tabs = {e.theory, e.formulas, e.pseudocode, e.workedExample, e.cheatSheet};

            for (int t = 0; t < tabs.length; t++) {
                String[] lines = tabs[t].split("\n");
                for (int i = 0; i < lines.length; i++) {
                    String s = lines[i];
                    if (s.isEmpty()) continue;

                    boolean borderRow = "╔╚║┌└├│".indexOf(s.charAt(0)) >= 0;
                    if (borderRow && s.length() != 74) {
                        System.out.printf("Manual %s / %s / line %d: width %d (expected 74)%n  %s%n",
                                e.id, tabNames[t], i + 1, s.length(), s);
                        problems++;
                    } else if (s.length() > 74) {
                        System.out.printf("Manual %s / %s / line %d: OVER 74 (%d)%n  %s%n",
                                e.id, tabNames[t], i + 1, s.length(), s);
                        problems++;
                    }
                }
            }
        }

        System.out.println(problems == 0
                ? "OK: all border rows are exactly 74 columns and no line exceeds 74."
                : problems + " ROW(S) NEED FIXING");
    }

    /** Writes the manually requested tabs to a UTF-8 file so the glyphs render correctly. */
    private static void dump() throws Exception {
        java.lang.reflect.Field rf = Class.forName("arcadeos.ArchiveManualDialog")
                .getDeclaredField("READER_FONT");
        rf.setAccessible(true);
        System.out.println("ArchiveManualDialog.READER_FONT = " + rf.get(null));

        ArchiveManualData.ManualEntry m3 = ArchiveManualData.getManual(2); // Paging
        ArchiveManualData.ManualEntry m1 = ArchiveManualData.getManual(0); // CPU Scheduling

        try (PrintWriter out = new PrintWriter("scratch/manual_tabs.txt", StandardCharsets.UTF_8)) {
            out.println("===== MANUAL " + m3.id + " — " + m3.title + " — 02 FORMULAS =====");
            out.println(m3.formulas);
            out.println();
            out.println("===== MANUAL " + m3.id + " — " + m3.title + " — 05 CHEAT SHEET =====");
            out.println(m3.cheatSheet);
            out.println();
            out.println("===== MANUAL " + m1.id + " — " + m1.title + " — 01 THEORY =====");
            out.println(m1.theory);
        }
        System.out.println("wrote scratch/manual_tabs.txt");
    }
}
