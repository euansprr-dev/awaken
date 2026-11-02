package dev.awaken.story;

/** Presentation timing and bounds; nothing here changes the director or damage. */
public final class RitualAudioRules {
    private RitualAudioRules() {}
    public static final float FOOT_PERIOD = 0.8F; // half of ritual_art.py's 1.6 second walk clip
    public static final int SWING_SOUND_DELAY = 19; // 25-tick wind-up: the whoosh's peak lands on the blade's contact
    public static final int MAX_VOICES = 10, MAX_HEAVY = 3, FOOT_GAP = 2;
    /** A lit plate's timer, ticks after it lights: quicker toward the deadline (Ritual.RING_WINDOW). */
    public static final int[] PLATE_TICKS = {6, 14, 22, 29, 36, 42, 48, 53, 58, 62, 66, 70, 73, 76, 79};
    // the beats, as Ritual.Beat's ordinals
    private static final int DOORS = 1, BOW = 2, RING = 3, JUDGED = 4, WAKING = 5, JUDGEMENT = 6, END = 7;
    /** The pulse racing to the eyes' bloom at the waking's tick 58: gaps of 14 down to 3 (the first two in BE JUDGED). */
    private static final int[] RACE = {-23, -9, 3, 13, 21, 28, 34, 39, 44, 48, 52, 55};

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

    /** A footfall's camera nudge: whole within 16 blocks, gone by 24. */
    public static float stepShake(double blocks) {
        return (float) Math.clamp(1 - (blocks - 16) / 8, 0, 1);
    }

    /** Which tick of a lit plate's timer falls at this age (its index, tick on even, tock on odd), or -1. */
    public static int plateTick(int age) {
        for (int i = 0; i < PLATE_TICKS.length; i++) if (PLATE_TICKS[i] == age) return i;
        return -1;
    }

    /**
     * The hall's pulse: the drum's gain on this tick of a beat, 0 for none and negative for the hard drum. It waits
     * slowly from the flames dying, quickens through the plates, races to the eyes, drives the Judgement, and stops
     * when the hand rises.
     */
    public static float pulse(int beat, int age) {
        if (beat == DOORS) return age >= 60 && (age - 60) % 30 == 0 ? 0.32F : 0;
        if (beat == BOW) return age % 30 == 0 ? 0.38F : 0;
        if (beat == RING) return age >= 10 && (age - 10) % 20 == 0 ? 0.42F : 0;
        int waking = beat == JUDGED ? age - Ritual.Beat.JUDGED.length : beat == WAKING ? age : Integer.MIN_VALUE;
        for (int i = 0; i < RACE.length; i++) if (RACE[i] == waking) return 0.4F + 0.3F * i / RACE.length;
        if (beat == JUDGED) return age >= 30 && (age - 30) % 25 == 0 ? 0.48F : 0;
        if (beat == WAKING && (age == 58 || age == 92)) return -0.95F; // the bloom and the twelve rising
        int drive = beat == WAKING ? age - 110 : beat == JUDGEMENT ? age : -1;
        if (drive < 0 || drive % 16 != 0) return 0;
        return drive / 16 % 4 == 0 ? -0.725F : 0.5F; // every fourth beat hard
    }

    /** The tone under the pulse: its level through the scene (before ducking under heavy hits). */
    public static float drone(int beat, float age) {
        return switch (beat) {
            case DOORS -> age < 34 ? 0 : age < 110 ? lerp(age, 34, 110, 0, 0.22F) : lerp(age, 110, Ritual.Beat.DOORS.length, 0.22F, 0.26F);
            case BOW -> lerp(age, 0, Ritual.Beat.BOW.length, 0.26F, 0.32F);
            case RING -> lerp(age, 0, Ritual.Beat.RING.length, 0.32F, 0.42F);
            case JUDGED -> lerp(age, 0, Ritual.Beat.JUDGED.length, 0.42F, 0.5F);
            case WAKING -> age < 58 ? lerp(age, 0, 58, 0.5F, 0.62F) : lerp(age, 58, Ritual.Beat.WAKING.length, 0.62F, 0.5F);
            case JUDGEMENT -> lerp(age, 0, Ritual.Beat.JUDGEMENT.length, 0.5F, 0.55F);
            case END -> lerp(age, 0, Ritual.CLOSE - 10, 0.55F, 0.95F);
            default -> 0;
        };
    }

    /** Through the End the tone bends up a minor third into the hand closing. */
    public static float dronePitch(int beat, float age) {
        if (beat != END) return 1;
        float u = Math.clamp(age / Ritual.CLOSE, 0, 1);
        return (float) Math.pow(2, 3 * Math.pow(u, 1.5) / 12);
    }

    private static float lerp(float x, float x0, float x1, float y0, float y1) {
        return y0 + (y1 - y0) * Math.clamp((x - x0) / (x1 - x0), 0, 1);
    }

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
            next[group] = now + switch (group) { case 0, 1 -> 0; case 2 -> FOOT_GAP; default -> 2; };
            return true;
        }
        /** A new heavy hit is never dropped for a full heavy budget: the oldest heavy's tail fades out under it. */
        public static boolean stealsHeavy(int group, int heavy) { return group == 1 && heavy >= MAX_HEAVY; }
        public void clear() { java.util.Arrays.fill(next, 0); }
    }
}
