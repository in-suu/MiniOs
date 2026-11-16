package arcadeos;

import java.awt.Color;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FAULT DEFENDER - a data-driven level definition loaded from JSON.
 *
 * <pre>
 * {
 *   "level": 2, "name": "EDGE CASES",
 *   "fallTimeSec": 6.0, "edgeFallTimeSec": 7.8,
 *   "segments": [ { "id":0, "name":"Kernel", "base":0, "limit":600, "ro":true }, ... ],
 *   "ships":    [ { "t":1.0, "seg":1, "off":699, "op":"r", "callout":"..." }, ... ]
 * }
 * </pre>
 *
 * A ship is just a <em>request</em>: a time to launch ({@code t} seconds), a
 * segment selector, an offset and an operation ({@code "r"} read / {@code "w"}
 * write). <b>No legality is stored anywhere</b>: {@link #mode} and the game
 * decide whether a request faults by checking the live segment table at the
 * moment it reaches the fault line.
 *
 * Levels are read from {@code fault_defender_levels.json} beside the working
 * directory; when that file is missing or unusable the built-in table is used.
 * Every level's segment table is validated for overlaps before it is accepted.
 */
public class LevelDef {

    public enum Mode { NORMAL, MOVING, BOSS }

    /** One scripted request. {@code edge} means the offset sits on the limit boundary. */
    public static class ShipDef {
        public final float t;          // launch time in seconds
        public final int seg;          // segment selector (may name an unmapped segment)
        public final int off;          // offset into the segment
        public final boolean write;    // "w" = write, "r" = read
        public final boolean edge;     // off == limit-1 || off == limit || off == limit+1
        public final String callout;   // optional one-line spotlight prompt

        ShipDef(float t, int seg, int off, boolean write, boolean edge, String callout) {
            this.t = Math.max(0f, t);
            this.seg = seg;
            this.off = Math.max(0, off);
            this.write = write;
            this.edge = edge;
            this.callout = (callout == null || callout.trim().isEmpty()) ? null : callout.trim();
        }
    }

    /** Physical distance a ship falls before it is judged: field top -> fault line. */
    private static final float FALL_DISTANCE = GameState.FAULT_LINE_Y - GameState.FIELD_TOP;

    public final int level;
    public final String name;
    public final float fallTimeSec;
    public final float edgeFallTimeSec;
    /** px per 60 Hz tick for a normal request / for an edge request. */
    public final float fallSpeed;
    public final float edgeSpeed;
    public final Mode mode;
    public final int bossHp;
    public final List<SegmentConfig> segments;
    public final List<ShipDef> ships;

    // Moving-fence parameters (Mode.MOVING)
    public final int moveTicks;      // ticks between fence slides
    public final int growthStep;     // how far the growing segment's limit advances
    public final int growSegment;    // segment whose limit grows
    public final int growInto;       // segment it eventually collides with

    private LevelDef(int level, String name, float fallTimeSec, float edgeFallTimeSec, Mode mode,
                     int bossHp, List<SegmentConfig> segments, List<ShipDef> ships,
                     int moveTicks, int growthStep, int growSegment, int growInto) {
        this.level = level;
        this.name = name;
        this.fallTimeSec = fallTimeSec <= 0 ? 6f : fallTimeSec;
        this.edgeFallTimeSec = edgeFallTimeSec <= 0 ? this.fallTimeSec * 1.3f : edgeFallTimeSec;
        this.fallSpeed = FALL_DISTANCE / (this.fallTimeSec * 60f);
        this.edgeSpeed = FALL_DISTANCE / (this.edgeFallTimeSec * 60f);
        this.mode = mode == null ? Mode.NORMAL : mode;
        this.bossHp = Math.max(1, bossHp);
        this.segments = segments;
        this.ships = ships;
        this.moveTicks = Math.max(60, moveTicks);
        this.growthStep = Math.max(1, growthStep);
        this.growSegment = growSegment;
        this.growInto = growInto;
    }

    public int waveSize() { return ships.size(); }

    // ── Loading ───────────────────────────────────────────────────────────────

    /** Problems found while loading the last library (empty when all was well). */
    public static List<String> lastErrors = new ArrayList<>();

    /** Returns the level library: on-disk JSON when usable, otherwise the built-ins. */
    public static List<LevelDef> library() {
        List<String> errors = new ArrayList<>();
        try {
            File f = new File("fault_defender_levels.json");
            if (f.isFile()) {
                String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                List<LevelDef> custom = parse(text, errors);
                if (!custom.isEmpty() && errors.isEmpty()) {
                    lastErrors = errors;
                    return custom;
                }
            }
        } catch (Exception ignored) {
            // Any I/O or parse problem: silently use the built-in library.
        }
        errors.clear();
        List<LevelDef> built = parse(BUILTIN, errors);
        lastErrors = errors;
        return built;
    }

    /** Parses a JSON library. Problems are collected in {@code errors}. */
    public static List<LevelDef> parse(String json, List<String> errors) {
        List<LevelDef> out = new ArrayList<>();
        if (json == null) return out;
        Map<String, Object> root;
        try {
            root = Json.obj(Json.parse(json));
        } catch (Exception ex) {
            if (errors != null) errors.add("level file is not valid JSON: " + ex.getMessage());
            return out;
        }
        if (root == null) {
            if (errors != null) errors.add("level file must be a JSON object");
            return out;
        }
        for (Object o : Json.children(root, "levels")) {
            Map<String, Object> lm = Json.obj(o);
            if (lm == null) continue;
            try {
                LevelDef ld = parseLevel(lm, errors);
                if (ld != null) out.add(ld);
            } catch (Exception ex) {
                if (errors != null) errors.add("level could not be parsed: " + ex.getMessage());
            }
        }
        out.sort(Comparator.comparingInt(l -> l.level));
        return out;
    }

    private static LevelDef parseLevel(Map<String, Object> lm, List<String> errors) {
        int level = Json.intg(lm, "level", 0);
        String name = Json.str(lm, "name", "LEVEL " + level);
        if (level <= 0) {
            if (errors != null) errors.add("a level is missing its \"level\" number");
            return null;
        }

        List<SegmentConfig> segs = new ArrayList<>();
        Map<Integer, Integer> limits = new HashMap<>();
        int ci = 0;
        for (Object so : Json.children(lm, "segments")) {
            Map<String, Object> sm = Json.obj(so);
            if (sm == null) continue;
            int id = Json.intg(sm, "id", -1);
            if (id < 0) {
                if (errors != null) errors.add("level " + level + ": a segment is missing its id");
                return null;
            }
            int base = Json.intg(sm, "base", 0);
            int limit = Json.intg(sm, "limit", 0);
            boolean ro = Json.bool(sm, "ro", false);
            Color color = LevelConfig.parseColor(Json.str(sm, "color", null));
            if (color == null) color = LevelConfig.PALETTE[ci % LevelConfig.PALETTE.length];
            segs.add(new SegmentConfig(id, Json.str(sm, "name", null), base, limit, ro, color));
            limits.put(id, limit);
            ci++;
        }

        String problem = LevelConfig.validate(segs);
        if (problem != null) {
            if (errors != null) errors.add("level " + level + " (" + name + "): " + problem);
            return null;
        }

        List<ShipDef> ships = new ArrayList<>();
        for (Object po : Json.children(lm, "ships")) {
            Map<String, Object> pm = Json.obj(po);
            if (pm == null) continue;
            float t = (float) Json.num(pm, "t", 0);
            int seg = Json.intg(pm, "seg", -1);
            int off = Json.intg(pm, "off", 0);
            String op = Json.str(pm, "op", "r");
            boolean write = op != null && op.trim().toLowerCase().startsWith("w");
            Integer lim = limits.get(seg);
            boolean edge = lim != null && (off == lim - 1 || off == lim || off == lim + 1);
            ships.add(new ShipDef(t, seg, off, write, edge, Json.str(pm, "callout", null)));
        }
        ships.sort(Comparator.comparingDouble(s -> s.t));

        Mode mode = Mode.NORMAL;
        String ms = Json.str(lm, "mode", "NORMAL");
        if (ms != null) {
            if (ms.trim().equalsIgnoreCase("MOVING")) mode = Mode.MOVING;
            else if (ms.trim().equalsIgnoreCase("BOSS")) mode = Mode.BOSS;
        }
        int bossHp = Json.intg(lm, "bossHp", 24);
        int moveSec = Json.intg(lm, "moveSec", 10);
        int growthStep = Json.intg(lm, "growthStep", 300);
        int growSegment = Json.intg(lm, "growSegment", 2);
        int growInto = Json.intg(lm, "growInto", 1);

        return new LevelDef(level, name, (float) Json.num(lm, "fallTimeSec", 6),
                (float) Json.num(lm, "edgeFallTimeSec", 0), mode, bossHp, segs, ships,
                moveSec * 60, growthStep, growSegment, growInto);
    }

    // ── Built-in library (used when fault_defender_levels.json is absent) ─────

    private static final String BUILTIN =
        "{ 'levels': [\n" +
        "  { 'level':1, 'name':'LIMIT CHECK', 'fallTimeSec':7.0, 'edgeFallTimeSec':9.1,\n" +
        "    'segments':[ {'id':0,'name':'Kernel','base':0,'limit':600,'ro':true},\n" +
        "                 {'id':1,'name':'Heap','base':1000,'limit':400,'ro':false} ],\n" +
        "    'ships':[ {'t':1.0,'seg':1,'off':120,'op':'r'}, {'t':2.4,'seg':1,'off':380,'op':'w'},\n" +
        "              {'t':3.8,'seg':0,'off':250,'op':'r'}, {'t':5.2,'seg':1,'off':430,'op':'r'},\n" +
        "              {'t':6.6,'seg':1,'off':300,'op':'r'}, {'t':8.0,'seg':0,'off':90,'op':'r'},\n" +
        "              {'t':9.4,'seg':1,'off':520,'op':'r'}, {'t':10.8,'seg':1,'off':60,'op':'w'},\n" +
        "              {'t':12.2,'seg':1,'off':700,'op':'r'}, {'t':13.6,'seg':0,'off':300,'op':'r'},\n" +
        "              {'t':15.0,'seg':1,'off':380,'op':'r'}, {'t':16.4,'seg':1,'off':460,'op':'w'},\n" +
        "              {'t':17.8,'seg':1,'off':210,'op':'r'}, {'t':19.2,'seg':1,'off':640,'op':'r'} ] },\n" +
        "  { 'level':2, 'name':'EDGE CASES', 'fallTimeSec':6.0, 'edgeFallTimeSec':7.8,\n" +
        "    'segments':[ {'id':0,'name':'Kernel','base':0,'limit':600,'ro':true},\n" +
        "                 {'id':1,'name':'Sprites','base':800,'limit':700,'ro':false},\n" +
        "                 {'id':2,'name':'Heap','base':2000,'limit':600,'ro':false},\n" +
        "                 {'id':3,'name':'Audio','base':3200,'limit':300,'ro':false} ],\n" +
        "    'ships':[ {'t':1.0,'seg':1,'off':699,'op':'r','callout':'offset = limit-1 (699): the LAST legal byte - do NOT shoot.'},\n" +
        "              {'t':3.0,'seg':1,'off':700,'op':'r','callout':'offset = limit (700): off by one - this is a SEGFAULT, shoot it!'},\n" +
        "              {'t':5.0,'seg':2,'off':200,'op':'r'},\n" +
        "              {'t':6.5,'seg':2,'off':601,'op':'r','callout':'offset = limit+1 (601): past the wall - SEGFAULT, shoot it.'},\n" +
        "              {'t':8.0,'seg':0,'off':599,'op':'w','callout':'a WRITE into a read-only segment is a PROTECTION FAULT - shoot it.'},\n" +
        "              {'t':9.5,'seg':0,'off':599,'op':'r'}, {'t':11.0,'seg':3,'off':299,'op':'r'},\n" +
        "              {'t':12.5,'seg':3,'off':300,'op':'r'}, {'t':14.0,'seg':2,'off':300,'op':'w'},\n" +
        "              {'t':15.5,'seg':1,'off':100,'op':'r'}, {'t':17.0,'seg':2,'off':600,'op':'r'},\n" +
        "              {'t':18.5,'seg':1,'off':350,'op':'w'}, {'t':20.0,'seg':0,'off':100,'op':'r'},\n" +
        "              {'t':21.5,'seg':3,'off':301,'op':'r'}, {'t':23.0,'seg':2,'off':599,'op':'r'} ] },\n" +
        "  { 'level':3, 'name':'PROTECTION BITS', 'fallTimeSec':5.0, 'edgeFallTimeSec':6.5,\n" +
        "    'segments':[ {'id':0,'name':'Kernel','base':0,'limit':600,'ro':true},\n" +
        "                 {'id':1,'name':'Sprites','base':800,'limit':700,'ro':false},\n" +
        "                 {'id':2,'name':'Text','base':2000,'limit':400,'ro':true},\n" +
        "                 {'id':3,'name':'Heap','base':3000,'limit':600,'ro':false} ],\n" +
        "    'ships':[ {'t':1.0,'seg':0,'off':200,'op':'r'}, {'t':2.2,'seg':0,'off':200,'op':'w'},\n" +
        "              {'t':3.4,'seg':2,'off':100,'op':'r'}, {'t':4.6,'seg':2,'off':100,'op':'w'},\n" +
        "              {'t':5.8,'seg':1,'off':300,'op':'w'}, {'t':7.0,'seg':2,'off':399,'op':'w'},\n" +
        "              {'t':8.2,'seg':3,'off':550,'op':'w'}, {'t':9.4,'seg':0,'off':599,'op':'r'},\n" +
        "              {'t':10.6,'seg':2,'off':250,'op':'r'}, {'t':11.8,'seg':3,'off':650,'op':'r'},\n" +
        "              {'t':13.0,'seg':1,'off':690,'op':'w'}, {'t':14.2,'seg':0,'off':5,'op':'w'},\n" +
        "              {'t':15.4,'seg':2,'off':1,'op':'w'}, {'t':16.6,'seg':3,'off':200,'op':'r'},\n" +
        "              {'t':17.8,'seg':1,'off':100,'op':'r'} ] },\n" +
        "  { 'level':4, 'name':'BAD SELECTORS', 'fallTimeSec':4.6, 'edgeFallTimeSec':6.0,\n" +
        "    'segments':[ {'id':0,'name':'Kernel','base':0,'limit':500,'ro':true},\n" +
        "                 {'id':1,'name':'Sprites','base':800,'limit':700,'ro':false},\n" +
        "                 {'id':4,'name':'Audio','base':2000,'limit':300,'ro':false},\n" +
        "                 {'id':5,'name':'Save','base':4000,'limit':600,'ro':false} ],\n" +
        "    'ships':[ {'t':1.0,'seg':1,'off':200,'op':'r'},\n" +
        "              {'t':2.2,'seg':2,'off':120,'op':'r','callout':'segment 2 is NOT in the table - it falls over the UNMAPPED gap. Shoot it.'},\n" +
        "              {'t':3.4,'seg':4,'off':100,'op':'r'}, {'t':4.6,'seg':3,'off':260,'op':'w'},\n" +
        "              {'t':5.8,'seg':9,'off':40,'op':'r'}, {'t':7.0,'seg':5,'off':500,'op':'w'},\n" +
        "              {'t':8.2,'seg':0,'off':120,'op':'r'}, {'t':9.4,'seg':7,'off':900,'op':'w'},\n" +
        "              {'t':10.6,'seg':4,'off':299,'op':'r'}, {'t':11.8,'seg':1,'off':700,'op':'r'},\n" +
        "              {'t':13.0,'seg':6,'off':10,'op':'r'}, {'t':14.2,'seg':5,'off':100,'op':'r'},\n" +
        "              {'t':15.4,'seg':8,'off':500,'op':'r'}, {'t':16.6,'seg':0,'off':480,'op':'w'},\n" +
        "              {'t':17.8,'seg':1,'off':50,'op':'w'} ] },\n" +
        "  { 'level':5, 'name':'STACK / HEAP GROWTH', 'mode':'MOVING', 'moveSec':10, 'growthStep':300,\n" +
        "    'growSegment':1, 'growInto':2, 'fallTimeSec':4.4, 'edgeFallTimeSec':5.7,\n" +
        "    'segments':[ {'id':0,'name':'Kernel','base':0,'limit':600,'ro':true},\n" +
        "                 {'id':1,'name':'Stack','base':1000,'limit':500,'ro':false},\n" +
        "                 {'id':2,'name':'Heap','base':2600,'limit':500,'ro':false},\n" +
        "                 {'id':3,'name':'MMIO','base':6000,'limit':400,'ro':false} ],\n" +
        "    'ships':[ {'t':1.0,'seg':1,'off':120,'op':'r'}, {'t':2.4,'seg':2,'off':200,'op':'w'},\n" +
        "              {'t':3.8,'seg':0,'off':200,'op':'r'}, {'t':5.2,'seg':1,'off':480,'op':'w'},\n" +
        "              {'t':6.6,'seg':2,'off':520,'op':'r'}, {'t':8.0,'seg':3,'off':100,'op':'r'},\n" +
        "              {'t':9.4,'seg':1,'off':300,'op':'r'}, {'t':11.5,'seg':2,'off':300,'op':'r'},\n" +
        "              {'t':13.0,'seg':3,'off':399,'op':'w'}, {'t':14.5,'seg':1,'off':499,'op':'r'},\n" +
        "              {'t':16.0,'seg':0,'off':500,'op':'w'}, {'t':17.5,'seg':2,'off':100,'op':'r'},\n" +
        "              {'t':19.0,'seg':3,'off':410,'op':'r'}, {'t':20.5,'seg':1,'off':60,'op':'w'},\n" +
        "              {'t':22.0,'seg':2,'off':400,'op':'r'}, {'t':23.5,'seg':0,'off':20,'op':'r'},\n" +
        "              {'t':25.0,'seg':1,'off':200,'op':'r'}, {'t':26.5,'seg':2,'off':480,'op':'r'},\n" +
        "              {'t':28.0,'seg':0,'off':300,'op':'r'}, {'t':29.5,'seg':3,'off':200,'op':'w'},\n" +
        "              {'t':31.0,'seg':1,'off':520,'op':'r'}, {'t':32.5,'seg':2,'off':120,'op':'w'},\n" +
        "              {'t':34.0,'seg':3,'off':420,'op':'r'}, {'t':35.5,'seg':1,'off':40,'op':'r'},\n" +
        "              {'t':37.0,'seg':2,'off':610,'op':'r'}, {'t':38.5,'seg':0,'off':500,'op':'w'},\n" +
        "              {'t':40.0,'seg':1,'off':480,'op':'r'}, {'t':41.5,'seg':3,'off':100,'op':'r'},\n" +
        "              {'t':43.0,'seg':2,'off':300,'op':'r'}, {'t':44.5,'seg':1,'off':250,'op':'w'},\n" +
        "              {'t':46.0,'seg':3,'off':399,'op':'r'} ] },\n" +
        "  { 'level':6, 'name':'BOSS: BUFFER OVERFLOW', 'mode':'BOSS', 'bossHp':24,\n" +
        "    'fallTimeSec':4.0, 'edgeFallTimeSec':5.2,\n" +
        "    'segments':[ {'id':0,'name':'Kernel','base':0,'limit':600,'ro':true},\n" +
        "                 {'id':1,'name':'Sprites','base':1000,'limit':800,'ro':false},\n" +
        "                 {'id':2,'name':'Heap','base':3000,'limit':600,'ro':false} ],\n" +
        "    'ships':[ {'t':3.0,'seg':1,'off':200,'op':'r'}, {'t':10.0,'seg':2,'off':300,'op':'w'},\n" +
        "              {'t':18.0,'seg':0,'off':200,'op':'r'}, {'t':26.0,'seg':1,'off':780,'op':'r'},\n" +
        "              {'t':34.0,'seg':2,'off':610,'op':'r'}, {'t':42.0,'seg':0,'off':100,'op':'w'},\n" +
        "              {'t':50.0,'seg':1,'off':100,'op':'r'}, {'t':58.0,'seg':3,'off':50,'op':'r'},\n" +
        "              {'t':66.0,'seg':2,'off':200,'op':'r'} ] }\n" +
        "] }";
}
