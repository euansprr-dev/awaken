package dev.awaken.story;

/** Presentation timing and bounds; nothing here changes the director or damage. */
public final class RitualAudioRules {
    private RitualAudioRules() {}
    public static final float FOOT_PERIOD = 0.8F; // half of ritual_art.py's 1.6 second walk clip
    public static final int SWING_SOUND_DELAY = 17; // 25-tick wind-up: the whoosh occupies its final eight ticks
    public static final int MAX_VOICES = 6, MAX_HEAVY = 2;

    /** One newly planted foot, never a catch-up burst after tracking starts, a teleport or a clock reset. */
    public static int planted(float before, float after) {
        if (!Float.isFinite(before) || !Float.isFinite(after) || before < 0 || after < before || after - before > FOOT_PERIOD) return -1;
        int old = (int) Math.floor(before / FOOT_PERIOD), now = (int) Math.floor(after / FOOT_PERIOD);
        return now > old ? now : -1;
    }

    public static float distance(double blocks, double range) {
        return range <= 0 ? 0 : (float) Math.clamp(1 - blocks / range, 0, 1);
    }

    public static boolean motionEnabled(boolean cameraShake, boolean reducedMotion) { return cameraShake && !reducedMotion; }

    /** Fixed end time: another impact may strengthen an impulse, never extend or add to it. */
    public static final class Shake {
        public static final float MAX = 1.25F;
        private float value;
        private int left, rest;
        public float value() { return value; }
        public void request(float strength, int ticks) {
            if (!Float.isFinite(strength) || strength <= 0 || rest > 0) return;
            float want = Math.min(MAX, strength);
            if (left == 0) left = Math.clamp(ticks, 1, 12);
            value = Math.max(value, want);
        }
        public void tick() {
            if (left > 0) {
                value *= (left - 1F) / left;
                if (--left == 0) { value = 0; rest = 4; }
            } else if (rest > 0) rest--;
        }
        public void clear() { value = 0; left = rest = 0; }
    }

    /** Each tracked statue establishes a baseline; stopping/losing tracking discards it. */
    public static final class Footfall {
        private float previous = Float.NaN;
        public int sample(float phase, boolean moving) {
            int result = moving ? planted(previous, phase) : -1;
            previous = moving ? phase : Float.NaN;
            return result;
        }
        public void clear() { previous = Float.NaN; }
    }

    public static final class Budget {
        private final long[] next = new long[4];
        public boolean accept(int group, long now, int voices, int heavy) {
            if (voices >= MAX_VOICES || now < next[group] || group == 1 && heavy >= MAX_HEAVY) return false;
            next[group] = now + switch (group) { case 0, 1 -> 0; case 2 -> 4; default -> 2; };
            return true;
        }
        public void clear() { java.util.Arrays.fill(next, 0); }
    }
}
