# FAULT DEFENDER

A Galaga / Space-Invaders style arcade shooter that teaches OS memory segmentation.
It replaces the old **SEG RUNNER** mini-game on **Mission 05: Memory Segmentation**
of the *Retro Arcade OS Prototype - MINI // OS [KAWAII 8-BIT ARCADE EDITION]*.

A request is a logical address `(segment s, offset d)`. It is legal only while `d < limit(s)`:

```
inside a zone        offset <  limit        LEGAL       -> PA = base + offset
past the right edge  offset >= limit        SEGFAULT    -> shoot it
over the UNMAPPED band  segment does not exist  SEGFAULT -> shoot it
write into a [RO]    protection fault       -> shoot it
```

You shoot the illegal ships and let the legal ones land. The rule is learned by
shooting and dodging, not by arithmetic.

---

## How to run

From the project root (`Arcade-OS-Simulator`):

```
javac -encoding UTF-8 -d out arcadeos/*.java
java -cp out arcadeos.ArcadeOS
```

Three ways to reach the game:

1. **In the app:** Simulator tab **05 - Memory Segmentation** → button
   **`ARCADE: FAULT DEFENDER`**. The segment table currently in the simulator is
   handed to the game.
2. **Direct:** `java -cp out arcadeos.ArcadeOS --seg`
3. **With your own level file:** put a `fault_defender_levels.txt` in the folder
   you launch `java` from (see *Adding or editing levels*).

The cabinet opens on a menu with **PLAY TUTORIAL**, **START CAMPAIGN** and
**RETURN TO OS**.

## Controls

| Key | Action |
| --- | --- |
| `A` / `D` or `Left` / `Right` | Move the MMU ship along the rail |
| `SPACE` | Shoot (also advances tutorial callouts, and Next Level on the report) |
| `P` | Pause (dim overlay) |
| `R` | Restart the run |
| `M` | Mute / unmute sound |
| `ESC` | Return to OS (banks XP for the final rank) |

## Levels

Six data-driven levels ship in the built-in library:

| # | Name | Teaches |
| --- | --- | --- |
| 1 | LIMIT CHECK | only out-of-bounds ships, slow |
| 2 | MIXED TRAFFIC | faster waves, mixed legal / illegal |
| 3 | PROTECTION BITS | read-only segments; writes to them are illegal |
| 4 | BAD SELECTORS | invalid segment numbers over the UNMAPPED gap |
| 5 | STACK / HEAP GROWTH | growth hazard between stack and heap |
| 6 | BOSS: BUFFER OVERFLOW | boss with a health bar firing bursts of out-of-bounds requests |

Speed, ship count and the illegal ratio rise per level.

## Adding or editing levels

Create `fault_defender_levels.txt` beside the app (the working directory you run
`java` from). It is a plain text file, one level per line, `#` starts a comment:

```
id | name | speed | spawnTicks | illegal% | invalid% | mode | wave | segments
```

* `speed` - pixels per 60 Hz tick (the ship fall speed)
* `spawnTicks` - ticks between spawns
* `illegal%` - chance of an offset `>= limit` (out of bounds)
* `invalid%` - chance of a segment number that does not exist
* `mode` - `NORMAL`, `GROWTH` or `BOSS`
* `wave` - ships to spawn before the wave can end
* `segments` - `;`-separated list of `number,name,base,limit[,R|W][,color]`

Colors accept `cyan`, `pink`, `magenta`, `yellow`, `green`, `purple`, `orange`
or a hex value such as `0x05D9E8`. Adding `R` marks a segment read-only.

Example:

```
# id | name        | speed | spawnTicks | illegal% | invalid% | mode   | wave | segments
7  | TLB STRESS    | 2.2 | 50 | 55 | 5 | NORMAL | 24 | 0,Kernel,0,600,R;1,Sprites,1000,800;2,Heap,3000,600,yellow
8  | BOSS: PAGE FAULT | 2.1 | 52 | 60 | 10 | BOSS | 999 | 0,Kernel,0,600;1,Data,1000,700
```

Notes:

* A custom file **replaces** the built-ins. Any of the designed levels `1..6` you
  do not redefine are merged back so the campaign stays complete.
* Every level's segment table is validated before it is used: it must be non-empty,
  hold at most 8 segments, have positive limits, and contain **no overlapping** ranges.
  A malformed level line is skipped and the built-ins are used instead.

## Editing the Mission 05 segments

The game uses **your** segment table for **level 1** when it is usable (at most 4
segments, exactly as validated above). Edit the rows in the Memory Segmentation
simulator (Segment # / Segment Name / Base Address / Limit) and press
`ARCADE: FAULT DEFENDER` again. If the table is empty, overlapping or too large,
the game falls back to its built-in teaching table and says so on the menu.

---

## Files

| File | Role |
| --- | --- |
| `SegmentConfig.java` | one segment (number, name, base, limit, read-only, color) |
| `LevelConfig.java` | level definition, the built-in library, the text parser, validation |
| `GameState.java` | model + logic: spawning, ships, faults, scoring, powers, the report |
| `FaultDefenderPanel.java` | rendering (zones, memory strip, HUD, report) and input |
| `FaultDefenderDialog.java` | the cabinet window, menu, drag chrome, XP on exit |
| `FdSfx.java` | runtime-synthesised chiptune cues (no audio assets) |

Model, logic and rendering are separated; the panel owns the ~60 FPS
`javax.swing.Timer` game loop with a fixed-step `GameState.update()`.

## Assumptions

1. **Fixed canvas.** The game renders into a 940x620 virtual buffer and blits it
   with integer nearest-neighbour scaling, matching the other cabinets, so nothing
   clips or overlaps when the window is resized.

2. **Imported segments are read-write.** The Mission 05 table has no read-only
   column, so segments imported from the simulator are all writable and are tinted
   from the neon palette in order. Read-only zones appear in the built-in levels
   3-6 and in the tutorial.

3. **Only level 1 uses the imported table.** Later levels use their designed
   tables, as each level needs a specific teaching layout. The imported table is
   used when it holds at most 4 non-overlapping, valid segments.

4. **XP is granted once, on exit,** based on the final rank (S=400, A=300, B=200,
   C=120, D=60) through `AppState.addScore`. The rank comes from the last completed
   wave's accuracy, or from the current tallies if you leave mid-wave.

5. **No audio assets.** Every sound is synthesised at runtime with
   `javax.sound.sampled`. If no mixer is available all failures are swallowed and
   the game stays fully playable and silent.

6. **Level 5 growth is modelled as a meter.** Rather than physically resizing the
   zones, a growth meter fills as requests fault; when it fills, the stack/heap
   zones flash and a collision fault fires. Shooting illegal ships drains it.

7. **The boss level has no wave counter.** It ends when the boss health bar is
   depleted, which awards the victory report.

8. **Tutorial is scripted.** The guided wave spawns three fixed ships (a legal one,
   an out-of-bounds one, and a write to the read-only zone), pauses for a one-line
   callout the first time each concept appears, costs no lives, and then hands off
   to level 1 automatically.

9. **One short formula line** sits at the very bottom; all other numbers appear in
   popups, floating text and the end-of-wave report.

10. **`M` for mute** was added on top of the requested controls so the game can be
    silenced without leaving it; the header also has a SOUND ON/OFF button.
