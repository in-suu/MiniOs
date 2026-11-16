package arcadeos;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * FAULT DEFENDER - model + logic.
 *
 * The screen is a logical address space. The bottom row of zones is that space
 * laid out left to right: each zone starts at its segment's base and is as wide
 * as its limit. Request ships fall straight down:
 *
 * <pre>
 *   inside a zone       -> offset &lt; limit      -> LEGAL, let it land
 *   past the right edge -> offset &ge; limit      -> SEGFAULT, shoot it
 *   over the void band  -> segment does not exist -> SEGFAULT, shoot it
 *   write into a [RO]   -> protection fault       -> shoot it
 * </pre>
 *
 * Levels come from JSON ({@link LevelDef}) as a script of request ships. A ship
 * carries only its selector, offset and operation - <b>no legality flag</b> is
 * ever stored. Whether it faults is recomputed every tick against the live
 * segment table ({@link #computeKind}), so when a moving fence slides under a
 * falling request the verdict changes with it.
 */
public class GameState {

    public enum Phase { TUTORIAL, PLAYING, REPORT, GAME_OVER, VICTORY }

    // ── Virtual canvas geometry ──────────────────────────────────────────────
    public static final int VW = 940;
    public static final int VH = 620;
    public static final int HUD_H = 84;
    public static final int FIELD_TOP = 96;
    public static final int RAIL_Y = 438;
    public static final int ZONE_TOP = 456;
    public static final int ZONE_H = 56;
    public static final int AXIS_X0 = 30;
    public static final int AXIS_X1 = 910;
    public static final int VOID_W = 34;     // UNMAPPED band on the far right
    public static final int ZONE_GAP = 40;   // gutter holding the over-limit band
    public static final int OVERFLOW_W = 32;
    public static final int MAP_TOP = 538;
    public static final int MAP_H = 26;
    public static final int FORMULA_Y = 594;
    /** Glowing tripwire just above the zones; it flashes when an illegal ship dives past. */
    public static final int FAULT_LINE_Y = ZONE_TOP - 16;
    public static final int PLAYER_W = 78;
    public static final int PLAYER_H = 34;
    public static final int PLAYER_MIN = AXIS_X0 + 22;
    public static final int PLAYER_MAX = AXIS_X1 - 22;

    /** Ship sprite box. Kept here so the model's hit test matches the renderer. */
    public static final int SHIP_W = 62;
    public static final int SHIP_H = 42;

    /** The lens bubble appears when an edge ship is this close to the fault line. */
    public static final float LENS_RANGE = 120f;

    private static final float PLAYER_SPEED = 7.2f;
    private static final float BULLET_SPEED = 14f;
    private static final int FIRE_COOLDOWN = 12;
    private static final int MAX_LIVES = 3;
    private static final int SLOWMO_TICKS = 300;   // 5 s at 60 Hz
    private static final int PTS_SHOOT = 100;      // correct shoot
    private static final int PTS_LET_THROUGH = 50; // correct let-through
    private static final int PTS_FALSE_ALARM = 100; // penalty
    private static final int ADAPT_MERCY = 2;      // mistakes in a row -> slow the next one
    private static final int ADAPT_STREAK = 5;     // correct in a row -> speed boost

    // ── Model types ─────────────────────────────────────────────────────────

    public enum Kind { LEGAL, OVER_LIMIT, BAD_SEGMENT, READONLY_WRITE }

    /** How a ship travels down the field. It never leaves its own band, so the
     *  horizontal position still tells the player whether the offset is legal. */
    public enum Motion { STRAIGHT, ZIGZAG, DIVE }

    /** A laid-out segment zone plus the over-limit band that follows it. */
    public static class Zone {
        public final SegmentConfig seg;
        public int left, right;
        public int overLeft, overRight;
        public float flash;
        Zone(SegmentConfig s) { this.seg = s; }
    }

    public static class Ship {
        public Kind kind;               // recomputed live from the segment table
        public final int segIndex;      // index into segments, or -1 for a bad selector
        public final int segNumber;
        public final int offset;
        public int limit;               // live limit (grows in MOVING levels)
        public int base;
        public final boolean write;
        public final Color color;
        public final boolean special;   // glows and drops a power-up when shot
        public float x, y;
        public boolean alive = true;
        public float wobble;
        public Motion motion = Motion.STRAIGHT;
        public float originX;                  // authored lane centre
        public float bandLeft, bandRight;      // the ship may zigzag only inside this band
        public float diveBoost = 0f;
        public boolean crossedFaultLine = false;
        public boolean ghost = false;          // aimed at a segment that does not exist

        // Edge-ship / lens support
        public boolean edge = false;           // offset sits on the limit boundary
        public float speed = 0f;               // px per tick (0 = use the level default)
        public float speedMul = 1f;            // adaptive-difficulty multiplier
        public boolean lens = false;           // lens bubble is showing right now
        public boolean spotlight = false;      // mercy ring from adaptive difficulty
        public String callout = null;          // authored one-liner, shown once
        public boolean growthShip = false;     // MOVING level: shooting it averts a growth

        Ship(Kind kind, int segIndex, int segNumber, int offset, int limit, int base,
             boolean write, Color color, boolean special) {
            this.kind = kind;
            this.segIndex = segIndex;
            this.segNumber = segNumber;
            this.offset = offset;
            this.limit = limit;
            this.base = base;
            this.write = write;
            this.color = color;
            this.special = special;
        }

        public boolean illegal() { return kind != Kind.LEGAL; }
        public int physical() { return base + offset; }

        /**
         * Places the ship on the runway. Legal / read-only-write ships sit inside
         * their zone (the more of the zone's width the bigger the offset); an
         * over-limit ship sits in the red band past the zone's right edge; a bad
         * selector falls over the UNMAPPED band at the far right.
         */
        Ship placed(GameState st, Zone z, boolean overflow) {
            if (z == null) {
                this.x = st.voidBandX();
                this.bandLeft = this.x - VOID_W / 2f + 6;
                this.bandRight = this.x + VOID_W / 2f - 6;
            } else if (overflow) {
                this.x = z.overLeft + OVERFLOW_W / 2f;
                this.bandLeft = z.overLeft + 4;
                this.bandRight = z.overRight - 4;
            } else {
                float t = Math.min(0.98f, this.offset / (float) Math.max(1, this.limit));
                this.x = z.left + 10 + t * Math.max(1, (z.right - z.left - 20));
                this.bandLeft = z.left + 6;
                this.bandRight = z.right - 6;
            }
            if (this.bandRight < this.bandLeft) {
                float mid = (this.bandLeft + this.bandRight) / 2f;
                this.bandLeft = this.bandRight = mid;
            }
            this.originX = this.x;
            this.y = FIELD_TOP;
            return this;
        }
    }

    public static class Bullet { public float x, y; }

    public static class PowerUp {
        public enum Type { SLOWMO, RELOCATE, COMPACT }
        public final Type type;
        public float x, y;
        PowerUp(Type t, float x, float y) { this.type = t; this.x = x; this.y = y; }
    }

    public static class Particle {
        public float x, y, vx, vy, life;
        public final Color color;
        Particle(float x, float y, float vx, float vy, Color c) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.color = c; this.life = 1f;
        }
    }

    public static class FloatText {
        public float x, y, life;
        public final String text;
        public final Color color;
        FloatText(float x, float y, String t, Color c) {
            this.x = x; this.y = y; this.text = t; this.color = c; this.life = 1f;
        }
    }

    /** A landed translation shown on the physical memory strip. */
    public static class Marker {
        public final int pa;
        public final Color color;
        public float life = 1f;
        Marker(int pa, Color c) { this.pa = pa; this.color = c; }
    }

    /** A short beam drawn from a landing zone to the physical memory strip. */
    public static class Beam {
        public final float fromX;   // zone centre the request landed in
        public final int pa;        // physical address, i.e. where the beam ends
        public final Color color;
        public float life = 1f;
        Beam(float fromX, int pa, Color c) { this.fromX = fromX; this.pa = pa; this.color = c; }
    }

    /** Snapshot shown on the end-of-wave report. */
    public static class Report {
        public int score, accuracy, segfaults, protFaults, falseAlarms, bestCombo;
        public String rank, tip;
    }

    // ── State ───────────────────────────────────────────────────────────────
    public final List<LevelDef> levels;
    private final List<SegmentConfig> customLevel1;
    private final Random rnd = new Random();

    public final List<SegmentConfig> segments = new ArrayList<>();
    public final List<Zone> zones = new ArrayList<>();
    public final List<Ship> ships = new ArrayList<>();
    public final List<Bullet> bullets = new ArrayList<>();
    public final List<PowerUp> powerups = new ArrayList<>();
    public final List<Particle> particles = new ArrayList<>();
    public final List<FloatText> floats = new ArrayList<>();
    public final List<Marker> markers = new ArrayList<>();
    public final List<Beam> beams = new ArrayList<>();

    public Phase phase = Phase.PLAYING;
    public LevelDef level;
    public int levelIndex = 0;
    public boolean tutorialEnabled;

    public float playerX = (AXIS_X0 + AXIS_X1) / 2f;
    public int score = 0;
    public int lives = MAX_LIVES;
    public int combo = 0;
    public int bestCombo = 0;
    public boolean paused = false;

    // Tallies
    public int translatedLegal = 0;
    public int shotIllegal = 0;
    public int falseAlarms = 0;
    public int segfaults = 0;
    public int protFaults = 0;

    // Effects the renderer reads
    public int shake = 0;
    public float redFlash = 0f;
    public float faultLineFlash = 0f;
    public float comboFlash = 0f;
    public int muzzleTicks = 0;
    public String popup = null;
    public Color popupColor = RetroTheme.ACCENT_PINK;
    public int popupTicks = 0;
    public String calloutText = null;
    public Color calloutColor = RetroTheme.ACCENT_CYAN;
    /** The ship the current callout is explaining, so the renderer can spotlight it. */
    public Ship calloutShip = null;

    public Report report;

    // Slow-motion (power-up + edge lens)
    public boolean slowing = false;
    public int slowTicks = 0;
    /** True while an edge ship's lens bubble is on screen, so time crawls. */
    public boolean lensSlow = false;

    // Moving fences (Mode.MOVING)
    public int fenceTimer = 0;
    public int growthApplied = 0;
    public Ship growthShip = null;
    private boolean growthPrevented = false;
    private List<SegmentConfig> baseSegments = new ArrayList<>();

    // Boss (Mode.BOSS)
    public float bossX;
    public float bossY = FIELD_TOP + 34;
    public int bossHp, bossMaxHp, bossFireTimer;
    public int bossDir = 1;

    private int spawnCooldown = 0;
    private int spawnedCount = 0;
    private int burstLeft = 0;
    private int burstDelay = 0;
    private int fireCooldown = 0;
    private int moveDir = 0;
    private boolean firing = false;
    private int tick = 0;

    // Scripted spawning
    private int scriptIdx = 0;
    private int scriptClock = 0;
    private final Set<String> seenEdge = new HashSet<>();

    // Adaptive difficulty
    private final int[] mistakeStreak = new int[Kind.values().length];
    private final int[] correctStreak = new int[Kind.values().length];
    private final int[] mercyLeft = new int[Kind.values().length];
    private int boostLeft = 0;

    // Tutorial script
    private final List<int[]> tutorialQueue = new ArrayList<>(); // {segIndex, offset, kindFlag}
    private boolean tutLegalDone, tutIllegalDone, tutReadOnlyDone;

    public GameState(List<LevelDef> levels, List<SegmentConfig> customLevel1, boolean tutorial) {
        this.levels = (levels == null || levels.isEmpty()) ? LevelDef.library() : levels;
        this.customLevel1 = customLevel1;
        this.tutorialEnabled = tutorial;
    }

    public void start() {
        if (tutorialEnabled) startTutorial();
        else startLevel(0, false);
    }

    // ── Level lifecycle ───────────────────────────────────────────────────────

    public void startLevel(int index, boolean keepScore) {
        levelIndex = Math.max(0, Math.min(levels.size() - 1, index));
        level = levels.get(levelIndex);
        phase = Phase.PLAYING;
        loadSegments();
        if (!keepScore) { score = 0; }
        if (levelIndex == 0 && !keepScore) resetTallies();
        combo = 0;
        ships.clear();
        bullets.clear();
        powerups.clear();
        particles.clear();
        floats.clear();
        markers.clear();
        beams.clear();
        spawningReset();
        playerX = (AXIS_X0 + AXIS_X1) / 2f;
        slowing = false;
        slowTicks = 0;
        lensSlow = false;
        calloutText = null;
        calloutShip = null;
        popup = null;
        popupTicks = 0;
        shake = 0;
        redFlash = 0f;
        faultLineFlash = 0f;
        comboFlash = 0f;

        // Scripted spawn + adaptive difficulty reset
        scriptIdx = 0;
        scriptClock = 0;
        seenEdge.clear();
        for (int i = 0; i < mistakeStreak.length; i++) {
            mistakeStreak[i] = 0;
            correctStreak[i] = 0;
            mercyLeft[i] = 0;
        }
        boostLeft = 0;

        // Moving fences
        fenceTimer = 0;
        growthApplied = 0;
        growthShip = null;
        growthPrevented = false;
        baseSegments = new ArrayList<>(segments);

        if (level.mode == LevelDef.Mode.BOSS) {
            bossMaxHp = bossHp = level.bossHp;
            bossX = (AXIS_X0 + AXIS_X1) / 2f;
            bossFireTimer = 90;
            bossDir = 1;
        }
    }

    public void nextLevel() {
        if (levelIndex + 1 < levels.size()) {
            startLevel(levelIndex + 1, true);
        } else {
            phase = Phase.VICTORY;
            buildReport();
        }
    }

    public void restartLevel() {
        startLevel(levelIndex, false);
    }

    /** Full restart from the first level (used by "Play Again"). */
    public void restartRun() {
        resetTallies();
        score = 0;
        lives = MAX_LIVES;
        startLevel(0, false);
    }

    private void resetTallies() {
        translatedLegal = 0;
        shotIllegal = 0;
        falseAlarms = 0;
        segfaults = 0;
        protFaults = 0;
        bestCombo = 0;
    }

    private void loadSegments() {
        segments.clear();
        if (levelIndex == 0 && customLevel1 != null && LevelConfig.validate(customLevel1) == null
                && customLevel1.size() <= 4) {
            segments.addAll(customLevel1);
        } else {
            segments.addAll(level.segments);
        }
        computeZones();
    }

    private void startTutorial() {
        levelIndex = 0;
        level = levels.get(0);
        phase = Phase.TUTORIAL;
        segments.clear();
        segments.addAll(LevelConfig.tutorialSegments());
        computeZones();
        ships.clear();
        bullets.clear();
        powerups.clear();
        particles.clear();
        floats.clear();
        markers.clear();
        resetTallies();
        score = 0;
        lives = MAX_LIVES;
        combo = 0;
        playerX = (AXIS_X0 + AXIS_X1) / 2f;
        spawnedCount = 0;
        spawningReset();
        tutorialQueue.clear();
        tutorialQueue.add(new int[]{1, 200, 0});   // LEGAL, Sprites
        tutorialQueue.add(new int[]{1, 900, 1});   // OVER_LIMIT, Sprites
        tutorialQueue.add(new int[]{0, 120, 2});   // READONLY_WRITE, Kernel [RO]
        tutLegalDone = tutIllegalDone = tutReadOnlyDone = false;
        calloutText = "TUTORIAL: a request is (segment number, offset). Watch where each ship falls.";
        calloutColor = RetroTheme.ACCENT_CYAN;
    }

    // ── Zones ─────────────────────────────────────────────────────────────────

    private void computeZones() {
        zones.clear();
        List<SegmentConfig> sorted = new ArrayList<>(segments);
        sorted.sort((a, b) -> Integer.compare(a.base, b.base));
        int n = sorted.size();
        if (n == 0) return;

        int right = AXIS_X1 - VOID_W - 12;
        int span = right - AXIS_X0;
        int usable = span - ZONE_GAP * (n - 1);
        int totalLimit = 0;
        for (SegmentConfig s : sorted) totalLimit += s.limit;

        int x = AXIS_X0;
        for (int i = 0; i < n; i++) {
            SegmentConfig s = sorted.get(i);
            int w = (int) Math.round(usable * (s.limit / (double) totalLimit));
            w = Math.max(46, w);
            if (i == n - 1) w = Math.max(46, right - x);
            Zone z = new Zone(s);
            z.left = x;
            z.right = Math.min(right, x + w);
            z.overLeft = z.right;
            z.overRight = Math.min(right, z.right + OVERFLOW_W);
            zones.add(z);
            x = z.right + ZONE_GAP;
        }
    }

    public Zone zoneForSegmentNumber(int number) {
        for (Zone z : zones) if (z.seg.number == number) return z;
        return null;
    }

    private int indexOfSegmentNumber(int number) {
        for (int i = 0; i < segments.size(); i++) if (segments.get(i).number == number) return i;
        return -1;
    }

    private SegmentConfig segmentByNumber(int number) {
        int i = indexOfSegmentNumber(number);
        return i < 0 ? null : segments.get(i);
    }

    public float voidBandX() {
        return AXIS_X1 - VOID_W / 2f;
    }

    /** Highest physical address a marker can land at (for strip scaling). */
    public int physicalSpan() {
        int max = 1000;
        for (SegmentConfig s : segments) max = Math.max(max, s.end());
        return max;
    }

    // ── Validity, computed from the LIVE table (never stored in the data) ─────

    /**
     * The verdict for a request: a read/write is valid only while its selector is
     * mapped, its offset is strictly below the segment's current limit, and - for
     * a write - the segment is not read-only. Recomputed every tick so moving
     * fences are judged at the instant the ship arrives.
     */
    public Kind computeKind(int segNumber, int offset, boolean write) {
        Zone z = zoneForSegmentNumber(segNumber);
        if (z == null) return Kind.BAD_SEGMENT;
        if (offset >= z.seg.limit) return Kind.OVER_LIMIT;
        if (write && z.seg.readOnly) return Kind.READONLY_WRITE;
        return Kind.LEGAL;
    }

    private Ship buildShip(int segNumber, int offset, boolean write, boolean edge, String callout) {
        Zone z = zoneForSegmentNumber(segNumber);
        SegmentConfig seg = z == null ? null : z.seg;
        int limit = seg == null ? 0 : seg.limit;
        int base = seg == null ? 0 : seg.base;
        Color color = seg == null ? new Color(150, 120, 200) : seg.color;
        int idx = seg == null ? -1 : segments.indexOf(seg);
        Kind k = computeKind(segNumber, offset, write);
        Ship s = new Ship(k, idx, segNumber, offset, limit, base, write, color, false);
        s.edge = edge;
        s.ghost = (k == Kind.BAD_SEGMENT);
        s.speed = edge ? level.edgeSpeed : level.fallSpeed;
        s.callout = callout;
        applyAdaptive(s, k);
        s.placed(this, z, k == Kind.OVER_LIMIT);
        return s;
    }

    /** Adaptive difficulty: mercy ring after repeated mistakes, speed boost after a streak. */
    private void applyAdaptive(Ship s, Kind k) {
        int i = k.ordinal();
        if (mercyLeft[i] > 0) {
            mercyLeft[i]--;
            s.speedMul = 0.75f;        // 25% slower
            s.spotlight = true;
        }
        if (boostLeft > 0) {
            boostLeft--;
            s.speedMul = Math.max(s.speedMul, 1.1f);   // +10% speed
        }
    }

    private void noteCorrect(Kind k, Ship s) {
        int i = k.ordinal();
        mistakeStreak[i] = 0;
        correctStreak[i]++;
        if (correctStreak[i] >= ADAPT_STREAK) {
            correctStreak[i] = 0;
            boostLeft = 5;
            setPopup("ON A ROLL: +10% speed for the next 5 requests", RetroTheme.ACCENT_GREEN);
        }
    }

    private void noteMistake(Kind k) {
        int i = k.ordinal();
        correctStreak[i] = 0;
        mistakeStreak[i]++;
        if (mistakeStreak[i] >= ADAPT_MERCY) {
            mistakeStreak[i] = 0;
            mercyLeft[i] = 1;
        }
    }

    // ── Spawning ──────────────────────────────────────────────────────────────

    private void spawningReset() {
        spawnCooldown = 40;
        spawnedCount = 0;
        burstLeft = 0;
        burstDelay = 0;
        fireCooldown = 0;
        tick = 0;
    }

    /** Bursts the tutorial wave and the scripted campaign wave. */
    private void advanceSpawning() {
        if (calloutText != null) return;

        if (phase == Phase.TUTORIAL) {
            if (spawnedCount >= 3) return;
            if (burstLeft > 0) {
                if (burstDelay > 0) { burstDelay--; return; }
                burstLeft--;
                burstDelay = 8;
                spawnTutorial();
                return;
            }
            if (spawnCooldown > 0) { spawnCooldown--; return; }
            spawnCooldown = 96;
            burstLeft = 1;
            return;
        }

        if (spawnedCount >= level.waveSize()) return;
        scriptClock++;
        while (scriptIdx < level.ships.size() && level.ships.get(scriptIdx).t * 60f <= scriptClock) {
            spawnScripted(level.ships.get(scriptIdx));
            scriptIdx++;
        }
    }

    private void spawnScripted(LevelDef.ShipDef d) {
        Ship s = buildShip(d.seg, d.off, d.write, d.edge, d.callout);
        applyMotion(s);
        ships.add(s);
        spawnedCount++;
        maybeEdgeCallout(s, d.callout);
    }

    /** Zigzag and dive are reserved for levels past the first so level 1 stays calm. */
    private void applyMotion(Ship s) {
        if (level == null || levelIndex == 0 || s.edge) return;
        int r = rnd.nextInt(100);
        if (r < 18) s.motion = Motion.ZIGZAG;
        else if (r < 30) s.motion = Motion.DIVE;
    }

    private Ship makeBadSegment() {
        int maxNum = 0;
        for (SegmentConfig s : segments) maxNum = Math.max(maxNum, s.number);
        int num = maxNum + 1 + rnd.nextInt(3);
        return buildShip(num, rnd.nextInt(400), false, false, null);
    }

    private Ship makeEdgeIllegal() {
        if (segments.isEmpty()) return null;
        SegmentConfig seg = segments.get(rnd.nextInt(segments.size()));
        return buildShip(seg.number, seg.limit, rnd.nextBoolean(), true, null);
    }

    private Ship makeOverLimit() {
        if (segments.isEmpty()) return null;
        List<SegmentConfig> any = new ArrayList<>(segments);
        SegmentConfig seg = any.get(rnd.nextInt(any.size()));
        int off = seg.limit + rnd.nextInt(Math.max(30, seg.limit / 2));
        return buildShip(seg.number, off, rnd.nextBoolean(), false, null);
    }

    private boolean randomSpecial() {
        return rnd.nextInt(100) < 9;
    }

    // ── Update ────────────────────────────────────────────────────────────────

    public void setMoveDir(int dir) { this.moveDir = dir; }

    public void setFiring(boolean f) { this.firing = f; }

    public void update() {
        tick++;
        decayVisuals();

        if (paused) return;
        if (phase == Phase.REPORT || phase == Phase.GAME_OVER || phase == Phase.VICTORY) return;
        if (calloutText != null) return;   // frozen while a callout is on screen

        if (slowTicks > 0) {
            slowTicks--;
            slowing = slowTicks > 0;
        }

        advancePlayer();
        advanceFiring();
        advanceBullets();
        advanceShips();
        advancePowerups();
        if (level.mode == LevelDef.Mode.BOSS) advanceBoss();
        else advanceSpawning();
        if (level.mode == LevelDef.Mode.MOVING) advanceMovingFences();

        checkWaveComplete();
    }

    private void advancePlayer() {
        if (moveDir != 0) {
            playerX += moveDir * PLAYER_SPEED;
            playerX = Math.max(PLAYER_MIN, Math.min(PLAYER_MAX, playerX));
        }
    }

    private void advanceFiring() {
        if (fireCooldown > 0) fireCooldown--;
        if (firing) fireNow();
    }

    /** Public so the panel can fire on SPACE press as well as while held. */
    public void fireNow() {
        if (paused || calloutText != null) return;
        if (phase == Phase.REPORT || phase == Phase.GAME_OVER || phase == Phase.VICTORY) return;
        if (fireCooldown > 0) return;
        Bullet b = new Bullet();
        b.x = playerX;
        b.y = RAIL_Y - 10;
        bullets.add(b);
        fireCooldown = FIRE_COOLDOWN;
        muzzleTicks = 6;
        FdSfx.shoot();
    }

    private void advanceBullets() {
        for (int i = bullets.size() - 1; i >= 0; i--) {
            Bullet b = bullets.get(i);
            b.y -= BULLET_SPEED;
            if (b.y < FIELD_TOP - 14) {
                bullets.remove(i);
                continue;
            }
            if (bossHitTest(b.x, b.y)) {
                bullets.remove(i);
                burst(b.x, b.y, RetroTheme.ACCENT_PINK, 6);
                damageBoss(1);
                continue;
            }
            Ship hit = findHit(b);
            if (hit != null) {
                bullets.remove(i);
                destroyShip(hit);
            }
        }
    }

    private Ship findHit(Bullet b) {
        for (Ship s : ships) {
            if (!s.alive) continue;
            if (Math.abs(b.x - s.x) < SHIP_W / 2f + 4 && Math.abs(b.y - s.y) < SHIP_H / 2f + 4) return s;
        }
        return null;
    }

    private void destroyShip(Ship s) {
        s.alive = false;
        burst(s.x, s.y, s.color, 14);
        if (s.alive) return;   // (kept for symmetry; ships are removed on the next tick)

        if (s.illegal()) {
            shotIllegal++;
            int base = PTS_SHOOT * (s.edge ? 2 : 1);
            int pts = Math.round(base * comboMult());
            score += pts;
            combo++;
            bestCombo = Math.max(bestCombo, combo);
            comboFlash = 1f;
            FdSfx.killStreak(combo);
            shake = Math.max(shake, 7);
            floatText(s.x, s.y - 12, "+" + pts + (s.edge ? " EDGE" : ""), RetroTheme.ACCENT_GREEN);
            noteCorrect(s.kind, s);
            if (s.growthShip) {
                growthPrevented = true;
                setPopup("GROWTH STOPPED: the fence holds", RetroTheme.ACCENT_GREEN);
            }
            if (s.special) dropPowerUp(s.x, s.y);
        } else {
            falseAlarms++;
            score = Math.max(0, score - PTS_FALSE_ALARM);
            combo = 0;
            noteMistake(Kind.LEGAL);
            FdSfx.hit();
            floatText(s.x, s.y - 12, "FALSE ALARM", RetroTheme.ACCENT_PINK);
            setPopup("FALSE ALARM: offset " + s.offset + " < limit " + s.limit + " is LEGAL", RetroTheme.ACCENT_PINK);
        }
    }

    private void advanceShips() {
        // A visible edge lens crawls time to 40 %.
        boolean anyLens = false;
        for (Ship s : ships) {
            if (s.alive && s.edge && s.y <= FAULT_LINE_Y && (FAULT_LINE_Y - s.y) <= LENS_RANGE) {
                anyLens = true;
                break;
            }
        }
        lensSlow = anyLens;
        float mul = (slowing ? 0.4f : 1f) * (lensSlow ? 0.4f : 1f);

        for (int i = ships.size() - 1; i >= 0; i--) {
            Ship s = ships.get(i);
            if (!s.alive) { ships.remove(i); continue; }

            // verdict against the live table (moving fences may have slid)
            s.kind = computeKind(s.segNumber, s.offset, s.write);
            Zone z = zoneForSegmentNumber(s.segNumber);
            if (z != null) { s.limit = z.seg.limit; s.base = z.seg.base; }

            float baseSpeed = s.speed > 0 ? s.speed : level.fallSpeed;
            float step = baseSpeed * s.speedMul * mul;
            if (s.motion == Motion.DIVE) {
                s.diveBoost += 0.035f;
                step *= 1f + Math.min(1.6f, s.diveBoost);
            }
            s.y += step;
            s.wobble += 0.15f;

            if (s.motion == Motion.ZIGZAG) {
                float amp = Math.min(14f, (s.bandRight - s.bandLeft) / 2f);
                float nx = s.originX + (float) Math.sin(s.wobble * 0.8f) * amp;
                s.x = Math.max(s.bandLeft, Math.min(s.bandRight, nx));
            }

            // tripwire: an unshot illegal request crossing it makes the line flash
            if (s.illegal() && !s.crossedFaultLine && s.y >= FAULT_LINE_Y) {
                s.crossedFaultLine = true;
                faultLineFlash = 1f;
                if (phase != Phase.TUTORIAL) FdSfx.warn();
            }
            s.lens = s.edge && s.y < FAULT_LINE_Y && (FAULT_LINE_Y - s.y) <= LENS_RANGE;

            if (s.y >= ZONE_TOP) {
                s.alive = false;
                ships.remove(i);
                resolveShip(s);
            }
        }
    }

    private void resolveShip(Ship s) {
        Zone z = (s.segIndex >= 0) ? zoneForSegmentNumber(s.segNumber) : null;
        // The verdict is decided HERE, against the table as it stands at arrival.
        Kind verdict = computeKind(s.segNumber, s.offset, s.write);
        switch (verdict) {
            case LEGAL: {
                translatedLegal++;
                int pa = s.base + s.offset;
                markers.add(new Marker(pa, s.color));
                if (z != null) z.flash = 1f;
                beams.add(new Beam(z == null ? s.x : z.left + (z.right - z.left) / 2f, pa, s.color));
                int base = PTS_LET_THROUGH * (s.edge ? 2 : 1);
                int pts = Math.round(base * comboMult());
                score += pts;
                combo++;
                bestCombo = Math.max(bestCombo, combo);
                comboFlash = 1f;
                FdSfx.landStreak(combo);
                noteCorrect(Kind.LEGAL, s);
                floatText(s.x, ZONE_TOP - 22, "+" + pts + "  PA " + pa, RetroTheme.ACCENT_GREEN);
                setPopup("ACCESS GRANTED: PA = " + s.base + " + " + s.offset + " = " + pa,
                        RetroTheme.ACCENT_GREEN);
                break;
            }
            case OVER_LIMIT:
                segfault("SEGFAULT! offset " + s.offset + " >= limit " + s.limit, s.kind);
                break;
            case BAD_SEGMENT:
                segfault("SEGFAULT! segment " + s.segNumber + " does not exist", s.kind);
                break;
            case READONLY_WRITE:
                protectionFault("PROTECTION FAULT! write to read-only segment " + s.segNumber, s.kind);
                break;
        }
    }

    private void segfault(String msg, Kind kind) {
        segfaults++;
        combo = 0;
        shake = 14;
        redFlash = 0.85f;
        FdSfx.fault();
        setPopup(msg, RetroTheme.ACCENT_PINK);
        if (phase != Phase.TUTORIAL) {
            noteMistake(kind == Kind.LEGAL ? Kind.OVER_LIMIT : kind);
            loseLife();
        }
    }

    private void protectionFault(String msg, Kind kind) {
        protFaults++;
        combo = 0;
        shake = 12;
        redFlash = 0.75f;
        FdSfx.fault();
        setPopup(msg, RetroTheme.ACCENT_YELLOW);
        if (phase != Phase.TUTORIAL) {
            noteMistake(kind);
            loseLife();
        }
    }

    private void loseLife() {
        lives--;
        if (lives <= 0) {
            lives = 0;
            phase = Phase.GAME_OVER;
            buildReport();
            FdSfx.fault();
        }
    }

    private void spawnTutorial() {
        if (tutorialQueue.isEmpty()) return;
        int[] spec = tutorialQueue.remove(0);
        int idx = spec[0];
        int off = spec[1];
        int flag = spec[2];
        if (idx < 0 || idx >= segments.size()) return;
        SegmentConfig seg = segments.get(idx);
        Zone z = zoneForSegmentNumber(seg.number);
        Ship s;
        if (flag == 1) {
            s = new Ship(Kind.OVER_LIMIT, idx, seg.number, seg.limit + 240, seg.limit, seg.base,
                    false, seg.color, false).placed(this, z, true);
        } else if (flag == 2) {
            s = new Ship(Kind.READONLY_WRITE, idx, seg.number, off, seg.limit, seg.base,
                    true, seg.color, false).placed(this, z, false);
        } else {
            s = new Ship(Kind.LEGAL, idx, seg.number, off, seg.limit, seg.base,
                    false, seg.color, false).placed(this, z, false);
        }
        s.speed = level.fallSpeed;
        ships.add(s);
        spawnedCount++;
        calloutFor(s);
    }

    private void calloutFor(Ship s) {
        if (s.kind == Kind.LEGAL && !tutLegalDone) {
            tutLegalDone = true;
            calloutText = "LEGAL REQUEST: offset " + s.offset + " < limit " + s.limit
                    + ". DO NOT shoot - let it land in its zone.";
            calloutColor = RetroTheme.ACCENT_GREEN;
            calloutShip = s;
        } else if (s.kind == Kind.OVER_LIMIT && !tutIllegalDone) {
            tutIllegalDone = true;
            calloutText = "ILLEGAL REQUEST: offset " + s.offset + " >= limit " + s.limit
                    + ". SHOOT it - it fell past the zone's right edge.";
            calloutColor = RetroTheme.ACCENT_PINK;
            calloutShip = s;
        } else if (s.kind == Kind.READONLY_WRITE && !tutReadOnlyDone) {
            tutReadOnlyDone = true;
            calloutText = "PROTECTION FAULT: a WRITE into the read-only zone (the one with the shield)."
                    + " SHOOT it before it lands.";
            calloutColor = RetroTheme.ACCENT_YELLOW;
            calloutShip = s;
        }
    }

    // ── Edge-case callouts (one-liners, first time each case appears) ─────────

    /** Category an edge ship falls into, or null when it is ordinary traffic. */
    private String edgeCategory(Ship s) {
        if (s.ghost) return "ghost";
        if (s.limit <= 0) return null;
        if (s.write && s.segIndex >= 0 && segments.get(s.segIndex).readOnly) return "ro-write";
        if (s.offset == s.limit - 1) return "edge-legal";
        if (s.offset == s.limit || s.offset == s.limit + 1) return "edge-illegal";
        return null;
    }

    private void maybeEdgeCallout(Ship s, String authored) {
        String key = edgeCategory(s);
        if (key == null || seenEdge.contains(key)) return;
        String text = authored != null ? authored : defaultEdgeText(key, s);
        if (text == null) return;
        seenEdge.add(key);
        calloutText = text;
        calloutColor = key.equals("edge-legal") ? RetroTheme.ACCENT_GREEN
                : key.equals("ro-write") ? RetroTheme.ACCENT_YELLOW
                : RetroTheme.ACCENT_PINK;
        calloutShip = s;
    }

    /** Level 2 teaches the edge cases itself, so it gets sensible defaults. */
    private String defaultEdgeText(String key, Ship s) {
        if (levelIndex != 1) return null;   // only "EDGE CASES" auto-coaches
        switch (key) {
            case "edge-legal":    return "offset = limit-1 (" + s.offset + "): the LAST legal byte - let it land.";
            case "edge-illegal":  return "offset >= limit (" + s.offset + "): off the end - SEGFAULT, shoot it.";
            case "ro-write":      return "WRITE into a read-only segment: PROTECTION FAULT - shoot it.";
            case "ghost":         return "segment " + s.segNumber + " is not mapped: SEGFAULT, shoot it.";
            default:              return null;
        }
    }

    /** Dismisses a callout. Returns true when one was showing. */
    public boolean advanceCallout() {
        if (calloutText == null) return false;
        calloutText = null;
        calloutShip = null;
        return true;
    }

    // ── Power-ups ─────────────────────────────────────────────────────────────

    private void advancePowerups() {
        for (int i = powerups.size() - 1; i >= 0; i--) {
            PowerUp p = powerups.get(i);
            p.y += 1.7f;
            if (p.y >= RAIL_Y - 6 && Math.abs(p.x - playerX) < 58) {
                applyPowerUp(p.type);
                powerups.remove(i);
            } else if (p.y > RAIL_Y + 46) {
                powerups.remove(i);
            }
        }
    }

    private void applyPowerUp(PowerUp.Type t) {
        FdSfx.powerup();
        switch (t) {
            case SLOWMO:
                slowing = true;
                slowTicks = SLOWMO_TICKS;
                setPopup("SLOW-MO: packets crawl for 5 seconds", RetroTheme.ACCENT_CYAN);
                break;
            case RELOCATE:
                relocateRandomZone();
                setPopup("RELOCATION: a segment base moved - watch the PA", RetroTheme.ACCENT_PURPLE);
                break;
            case COMPACT:
                compactSweep();
                break;
        }
    }

    private void relocateRandomZone() {
        if (segments.isEmpty()) return;
        int idx = rnd.nextInt(segments.size());
        SegmentConfig old = segments.get(idx);
        int[] deltas = {1400, -1400, 2200, -2200};
        for (int d : deltas) {
            int nb = old.base + d;
            if (nb < 0) continue;
            SegmentConfig cand = old.withBase(nb);
            boolean bad = false;
            for (int i = 0; i < segments.size(); i++) {
                if (i == idx) continue;
                if (cand.overlaps(segments.get(i))) bad = true;
            }
            if (!bad) {
                segments.set(idx, cand);
                computeZones();
                repositionShips();
                return;
            }
        }
    }

    /** Re-aligns in-flight ships after zones move so "inside the zone" stays truthful. */
    private void repositionShips() {
        for (Ship s : ships) {
            if (s.segIndex < 0) continue;
            Zone z = zoneForSegmentNumber(s.segNumber);
            if (z == null) continue;
            if (s.kind == Kind.OVER_LIMIT) s.x = z.overLeft + OVERFLOW_W / 2f;
            else {
                float t = Math.min(0.98f, s.offset / (float) Math.max(1, s.limit));
                s.x = z.left + 10 + t * Math.max(1, (z.right - z.left - 20));
            }
        }
    }

    private void compactSweep() {
        for (Ship s : ships) {
            burst(s.x, s.y, s.color, 8);
        }
        ships.clear();
        shake = 8;
        setPopup("COMPACTION: address space swept", RetroTheme.ACCENT_GREEN);
        FdSfx.explode();
    }

    // ── Moving fences (Mode.MOVING) ───────────────────────────────────────────

    private void advanceMovingFences() {
        fenceTimer++;

        // one growth "ship" (a boundary write) per cycle; shooting it cancels the growth
        if (growthShip == null && fenceTimer > 90) {
            SegmentConfig g = segmentByNumber(level.growSegment);
            if (g != null) {
                Ship s = buildShip(g.number, g.limit, true, true, null);
                s.growthShip = true;
                s.speed = level.fallSpeed * 1.15f;
                growthShip = s;
                growthPrevented = false;
                ships.add(s);
            }
        }

        if (fenceTimer >= level.moveTicks) {
            fenceTimer = 0;
            growthShip = null;
            if (!growthPrevented) applyGrowth();
            else FdSfx.chime();
        }
    }

    private void applyGrowth() {
        int gi = indexOfSegmentNumber(level.growSegment);
        if (gi < 0) return;
        SegmentConfig g = segments.get(gi);
        SegmentConfig into = segmentByNumber(level.growInto);
        int newLimit = g.limit + level.growthStep;
        boolean overlap = into != null && (g.base + newLimit) > into.base;

        segments.set(gi, new SegmentConfig(g.number, g.name, g.base, newLimit, g.readOnly, g.color));
        computeZones();
        repositionShips();
        growthApplied++;
        FdSfx.chime();
        int lo = g.limit;
        setPopup("STACK GROWS: limit " + lo + " -> " + newLimit, RetroTheme.ACCENT_YELLOW);

        if (overlap) {
            for (Zone z : zones) z.flash = 0.7f;
            shake = 16;
            redFlash = 0.9f;
            FdSfx.fault();
            setPopup("OVERLAP! THE HEAP FENCE SLID INTO STACK", RetroTheme.ACCENT_PINK);
            if (phase != Phase.TUTORIAL) loseLife();
            // relief: snap the fences back so the level can continue
            segments.clear();
            segments.addAll(baseSegments);
            computeZones();
            repositionShips();
            growthApplied = 0;
        }
    }

    // ── Boss (Mode.BOSS) ──────────────────────────────────────────────────────

    /** 1 = healthy, 2 = half, 3 = enraged. */
    public int bossPhase() {
        if (bossMaxHp <= 0) return 1;
        float f = bossHp / (float) bossMaxHp;
        if (f > 0.66f) return 1;
        if (f > 0.33f) return 2;
        return 3;
    }

    private void advanceBoss() {
        int ph = bossPhase();
        float drift = 1.4f + (ph - 1) * 0.9f;
        bossX += bossDir * drift;
        if (bossX < AXIS_X0 + 90) bossDir = 1;
        if (bossX > AXIS_X1 - 90) bossDir = -1;

        bossFireTimer--;
        if (bossFireTimer <= 0) {
            bossFireTimer = (ph == 3 ? 46 : ph == 2 ? 66 : 88) + rnd.nextInt(24);
            int burst = 2 + ph;                 // phase 1: 3, phase 2: 4, phase 3: 5
            for (int i = 0; i < burst; i++) {
                Ship s;
                int pick = rnd.nextInt(100);
                if (pick < 20 + ph * 6) s = makeBadSegment();
                else if (pick < 60 + ph * 6) s = makeEdgeIllegal();
                else s = makeOverLimit();
                if (s != null) {
                    s.y = bossY + 30 + i * 16;
                    applyMotion(s);
                    ships.add(s);
                    spawnedCount++;
                }
            }
            if (ph == 3) {                       // enraged: a diving finisher
                Ship d = makeOverLimit();
                if (d != null) {
                    d.motion = Motion.DIVE;
                    d.y = bossY + 40;
                    ships.add(d);
                    spawnedCount++;
                }
            }
            for (int i = 0; i < 14; i++) {
                burst(bossX + rnd.nextInt(70) - 35, bossY + 22, RetroTheme.ACCENT_PINK, 1);
            }
            FdSfx.warn();
            setPopup("BUFFER OVERFLOW: burst of " + burst + " illegal requests! (phase " + ph + ")",
                    RetroTheme.ACCENT_PINK);
        }
    }

    /** A bullet that reaches the boss damages it. */
    public void damageBoss(int amount) {
        bossHp -= amount;
        shake = 6;
        if (bossHp <= 0) {
            bossHp = 0;
            for (int i = 0; i < 40; i++) {
                burst(bossX + rnd.nextInt(80) - 40, bossY + rnd.nextInt(40) - 20, RetroTheme.ACCENT_PINK, 1);
            }
            FdSfx.explode();
            phase = Phase.VICTORY;
            buildReport();
        }
    }

    private void checkWaveComplete() {
        if (phase == Phase.TUTORIAL) {
            // The guided wave hands off to the real campaign once its 3 ships are done.
            if (tutorialQueue.isEmpty() && spawnedCount >= 3 && ships.isEmpty() && bullets.isEmpty()) {
                tutorialEnabled = false;
                startLevel(0, false);
            }
            return;
        }
        if (phase != Phase.PLAYING) return;
        if (level.mode == LevelDef.Mode.BOSS) return;
        if (spawnedCount >= level.waveSize() && ships.isEmpty() && bullets.isEmpty()) {
            phase = Phase.REPORT;
            buildReport();
        }
    }

    /** Checks whether a bullet is currently overlapping the boss. */
    public boolean bossHitTest(float bx, float by) {
        if (level == null || level.mode != LevelDef.Mode.BOSS || phase != Phase.PLAYING) return false;
        return Math.abs(bx - bossX) < 62 && Math.abs(by - bossY) < 30;
    }

    // ── Effects / helpers ─────────────────────────────────────────────────────

    public float comboMult() {
        return 1f + Math.min(combo, 40) * 0.1f;
    }

    public int waveProgressSpawned() { return spawnedCount; }

    public int waveProgressTarget() { return level == null ? 1 : Math.max(1, level.waveSize()); }

    private void setPopup(String msg, Color c) {
        popup = msg;
        popupColor = c;
        popupTicks = 110;
    }

    private void floatText(float x, float y, String t, Color c) {
        floats.add(new FloatText(x, y, t, c));
    }

    public void burst(float x, float y, Color c, int n) {
        for (int i = 0; i < n; i++) {
            float vx = (rnd.nextFloat() - 0.5f) * 5f;
            float vy = (rnd.nextFloat() - 0.5f) * 5f - 1f;
            particles.add(new Particle(x, y, vx, vy, c));
        }
    }

    private void dropPowerUp(float x, float y) {
        PowerUp.Type[] types = PowerUp.Type.values();
        PowerUp.Type t = types[rnd.nextInt(types.length)];
        powerups.add(new PowerUp(t, x, y));
        setPopup("SPECIAL PACKET DESTROYED - power-up falling", RetroTheme.ACCENT_CYAN);
    }

    private void decayVisuals() {
        if (shake > 0) shake--;
        if (redFlash > 0) redFlash = Math.max(0f, redFlash - 0.06f);
        if (faultLineFlash > 0) faultLineFlash = Math.max(0f, faultLineFlash - 0.045f);
        if (comboFlash > 0) comboFlash = Math.max(0f, comboFlash - 0.05f);
        if (muzzleTicks > 0) muzzleTicks--;
        if (popupTicks > 0) {
            popupTicks--;
            if (popupTicks == 0) popup = null;
        }
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.x += p.vx;
            p.y += p.vy;
            p.vy += 0.12f;
            p.life -= 0.035f;
            if (p.life <= 0) particles.remove(i);
        }
        for (int i = floats.size() - 1; i >= 0; i--) {
            FloatText f = floats.get(i);
            f.y -= 0.7f;
            f.life -= 0.018f;
            if (f.life <= 0) floats.remove(i);
        }
        // markers are kept for the whole wave: they fade only very slowly
        for (int i = markers.size() - 1; i >= 0; i--) {
            Marker m = markers.get(i);
            m.life = Math.max(0.55f, m.life - 0.002f);
        }
        for (int i = beams.size() - 1; i >= 0; i--) {
            Beam b = beams.get(i);
            b.life -= 0.05f;
            if (b.life <= 0) beams.remove(i);
        }
        for (Zone z : zones) {
            if (z.flash > 0) z.flash = Math.max(0f, z.flash - 0.05f);
        }
    }

    // ── Report ────────────────────────────────────────────────────────────────

    private void buildReport() {
        Report r = new Report();
        r.score = score;
        int correct = translatedLegal + shotIllegal;
        int total = correct + falseAlarms + segfaults + protFaults;
        r.accuracy = total == 0 ? 100 : (int) Math.round(correct * 100.0 / total);
        r.segfaults = segfaults;
        r.protFaults = protFaults;
        r.falseAlarms = falseAlarms;
        r.bestCombo = bestCombo;
        r.rank = rankFor(r.accuracy);
        r.tip = tipFor(r);
        report = r;
    }

    public static String rankFor(int accuracy) {
        if (accuracy >= 95) return "S";
        if (accuracy >= 85) return "A";
        if (accuracy >= 70) return "B";
        if (accuracy >= 55) return "C";
        return "D";
    }

    private String tipFor(Report r) {
        int max = Math.max(Math.max(r.segfaults, r.protFaults), r.falseAlarms);
        if (max == 0) return "FLAWLESS - every request was judged against offset < limit.";
        if (max == r.segfaults) return "offset must be strictly less than the limit.";
        if (max == r.protFaults) return "a write to a read-only segment is a protection fault.";
        return "do not shoot legal requests - let them land in their zone.";
    }
}
