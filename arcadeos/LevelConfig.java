package arcadeos;

import java.awt.Color;
import java.util.List;

/**
 * FAULT DEFENDER - shared segment helpers.
 *
 * Level data itself now lives in JSON (see {@link LevelDef}); this class keeps
 * the neon palette, the fixed table used by the tutorial wave, the colour
 * parser the JSON loader uses, and the overlap validation every level's
 * segment table must pass before it is loaded.
 */
public final class LevelConfig {

    /** Neon palette used when a segment omits its own colour. */
    public static final Color[] PALETTE = {
            RetroTheme.ACCENT_CYAN,
            RetroTheme.ACCENT_PINK,
            RetroTheme.ACCENT_YELLOW,
            RetroTheme.ACCENT_GREEN,
            RetroTheme.ACCENT_PURPLE,
            new Color(255, 140, 40)
    };

    private LevelConfig() { }

    /** Fixed teaching table for the tutorial wave (one read-only segment included). */
    public static java.util.List<SegmentConfig> tutorialSegments() {
        java.util.List<SegmentConfig> s = new java.util.ArrayList<>();
        s.add(new SegmentConfig(0, "Kernel", 0, 500, true, PALETTE[0]));
        s.add(new SegmentConfig(1, "Sprites", 800, 700, false, PALETTE[1]));
        s.add(new SegmentConfig(2, "Heap", 2000, 600, false, PALETTE[2]));
        return s;
    }

    /**
     * Validates a segment table before it is handed to the game: no empty
     * table, no more than eight segments, and no two ranges may overlap.
     *
     * @return {@code null} when the table is usable, otherwise a short reason.
     */
    public static String validate(List<SegmentConfig> segs) {
        if (segs == null || segs.isEmpty()) return "the segment table is empty";
        if (segs.size() > 8) return "too many segments (" + segs.size() + "); the runway holds at most 8";
        for (int i = 0; i < segs.size(); i++) {
            SegmentConfig a = segs.get(i);
            if (a.limit <= 0) return a.name + " has a non-positive limit";
            for (int j = i + 1; j < segs.size(); j++) {
                if (a.overlaps(segs.get(j))) return a.name + " overlaps " + segs.get(j).name;
            }
        }
        return null;
    }

    /** Palette name (cyan/pink/...) or hex (0x05D9E8 / #RRGGBB); null when unknown. */
    static Color parseColor(String s) {
        if (s == null) return null;
        String t = s.trim().toLowerCase();
        switch (t) {
            case "cyan":   return RetroTheme.ACCENT_CYAN;
            case "pink":
            case "magenta":return RetroTheme.ACCENT_PINK;
            case "yellow": return RetroTheme.ACCENT_YELLOW;
            case "green":  return RetroTheme.ACCENT_GREEN;
            case "purple": return RetroTheme.ACCENT_PURPLE;
            case "orange": return new Color(255, 140, 40);
            default:
                try {
                    String hex = t.startsWith("0x") ? t.substring(2) : (t.startsWith("#") ? t.substring(1) : t);
                    return new Color(Integer.parseInt(hex, 16));
                } catch (Exception e) {
                    return null;
                }
        }
    }
}
