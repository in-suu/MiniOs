package arcadeos;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;

/**
 * FAULT DEFENDER - tiny chiptune cue generator.
 *
 * Every sound is a short mono square/triangle blip synthesised on the fly, so
 * the game needs no audio assets at all. If the mixers are unavailable every
 * failure is swallowed and the game stays silent but fully playable. A single
 * global mute flag backs the in-game [M] toggle.
 */
final class FdSfx {

    private static final float RATE = 44100f;
    private static volatile boolean muted = false;
    private static volatile double sfxVolume = 0.55;

    private FdSfx() { }

    static boolean isMuted() { return muted; }

    static void setMuted(boolean m) { muted = m; }

    static boolean toggleMute() {
        muted = !muted;
        return muted;
    }

    static void shoot()     { square(880, 40, 0.22, 0.30); }
    static void hit()       { square(520, 45, 0.24, 0.35); }
    static void explode()   { square(180, 90, 0.30, 0.45); }
    static void translate() { tone(1100, 70, 0.26); tone(1500, 60, 0.20); }
    static void fault()     { tone(150, 260, 0.40); }
    static void powerup()   { tone(900, 60, 0.24); tone(1250, 60, 0.22); tone(1650, 70, 0.20); }
    static void combo()     { tone(1400, 45, 0.18); }
    static void uiClick()   { square(700, 30, 0.20, 0.25); }

    /** Alarm when an unshot illegal request crosses the fault line. */
    static void warn()      { square(300, 70, 0.22, 0.20); square(240, 80, 0.22, 0.20); }

    /** Soft two-note chime when a moving fence slides to a new limit. */
    static void chime()     { tone(1180, 55, 0.22); tone(1560, 70, 0.18); }

    /** Rising-pitch reward for a kill streak: the longer the streak, the higher the blip. */
    static void killStreak(int combo) {
        int step = Math.max(0, Math.min(24, combo));
        double f = 660 + step * 55;
        square(f, 45, 0.24, 0.35);
        square(f * 1.5, 35, 0.16, 0.35);
    }

    /** Landed-translation chime whose pitch also follows the streak. */
    static void landStreak(int combo) {
        int step = Math.max(0, Math.min(24, combo));
        double f = 900 + step * 45;
        tone(f, 60, 0.24);
        tone(f * 1.33, 55, 0.18);
    }

    /** Square wave: crunchy arcade lead. */
    private static void square(final double freq, final int ms, final double vol, final double duty) {
        play(freq, ms, vol, true, duty);
    }

    /** Sine/triangle-ish tone: softer cue. */
    private static void tone(final double freq, final int ms, final double vol) {
        play(freq, ms, vol, false, 0.5);
    }

    private static void play(final double freq, final int ms, final double vol,
                             final boolean square, final double duty) {
        if (muted) return;
        final double v0 = vol * sfxVolume;
        Thread t = new Thread(() -> {
            try {
                int n = Math.max(1, (int) (RATE * ms / 1000.0));
                byte[] buf = new byte[n];
                for (int i = 0; i < n; i++) {
                    double phase = (freq * i / RATE) % 1.0;
                    double wave = square ? (phase < duty ? 1.0 : -1.0) : Math.sin(2 * Math.PI * phase);
                    double attack = Math.min(1.0, i / (RATE * 0.004));
                    double release = Math.min(1.0, (n - i) / (RATE * 0.05));
                    double v = wave * v0 * attack * release;
                    buf[i] = (byte) (Math.max(-1.0, Math.min(1.0, v)) * 127);
                }
                AudioFormat fmt = new AudioFormat(RATE, 8, 1, true, false);
                final Clip clip = AudioSystem.getClip();
                clip.addLineListener(ev -> { if (ev.getType() == LineEvent.Type.STOP) clip.close(); });
                clip.open(fmt, buf, 0, buf.length);
                clip.start();
            } catch (Exception ignored) {
                // No audio device / mixer busy: stay silent, keep playing.
            }
        }, "fd-sfx");
        t.setDaemon(true);
        t.start();
    }
}
