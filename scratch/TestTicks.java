import arcadeos.ArchiveManualData;

public class TestTicks {
    public static void main(String[] args) {
        String m3pseudo = ArchiveManualData.getManual(2).pseudocode;
        for (String line : m3pseudo.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("│")) {
                String[] cols = line.split("│", -1);
                if (cols.length != 3) {
                    System.out.println("cols.length = " + cols.length + " for line: " + line);
                }
            }
        }
    }
}
