package dev.awaken.core.audio;

/**
 * The layered music's intensity (SPEC "Plan v2 · audio", docs/modules/audio.md "Layered music"): what is happening
 * to one player, reduced to four levels that pick which stems of a context sound.
 *
 * <ul>
 *   <li>{@link #LORD}: a Lord or boss is alive within {@value #LORD_RADIUS} blocks and hunts you, or you are in its
 *   arena ({@value #LORD_ARENA} blocks), or you traded blows with it in the last {@value #LORD_WINDOW} ticks.</li>
 *   <li>{@link #COMBAT}: you hit a hostile or a hostile hurt you in the last {@value #COMBAT_WINDOW} ticks, or
 *   {@value #SWARM} or more hostiles hunt you within {@value #SWARM_RADIUS} blocks.</li>
 *   <li>{@link #TENSION}: a hostile hunts you within {@value #HUNT_RADIUS} blocks, any hostile is within
 *   {@value #NEAR_RADIUS} blocks, the gate pushes (pressure stage 2+: waves, the Warden), or a Lord is alive near.</li>
 *   <li>{@link #CALM} otherwise.</li>
 * </ul>
 * A level rises at once and falls only when its hold has run out ({@link #HOLD}); the result is the highest level
 * still held. When a Lord falls ({@link Signals#lordFell}), the fight is over: Lord and combat holds end at once.
 * Pure and allocation-free: one instance per player and slot, updated every {@value #PERIOD} ticks.
 */
public final class Intensity {
    public static final int CALM = 0, TENSION = 1, COMBAT = 2, LORD = 3;
    public static final String[] NAMES = {"calm", "tension", "combat", "lord"};

    /** Ticks between evaluations (server). */
    public static final int PERIOD = 10;
    public static final double HUNT_RADIUS = 24, NEAR_RADIUS = 10, SWARM_RADIUS = 16, LORD_RADIUS = 40, LORD_ARENA = 20;
    public static final int SWARM = 3, COMBAT_WINDOW = 100, LORD_WINDOW = 300, PRESSURE = 2;
    /** How long each level outlives its last signal (ticks): tension 8 s, combat 12 s, Lord 10 s. */
    public static final int[] HOLD = {0, 160, 240, 200};

    private static final long NEVER = Long.MIN_VALUE / 4;

    /** One evaluation's inputs, filled by the server's scan and the damage hooks. Reused, never reallocated. */
    public static final class Signals {
        /** Hostiles within {@link #HUNT_RADIUS} whose target is the player. */
        public int hunting;
        /** Of those, within {@link #SWARM_RADIUS}. */
        public int huntingClose;
        /** Hostiles within {@link #NEAR_RADIUS}, aware of the player or not. */
        public int near;
        /** A Lord or boss alive within {@link #LORD_RADIUS}; its distance; whether it targets the player. */
        public boolean lordAlive, lordHunting;
        public double lordDistance = Double.MAX_VALUE;
        /** Tick of the last damage traded with a hostile / with a Lord or boss. */
        public long lastCombat = NEVER, lastLordHit = NEVER;
        /** Tick a Lord or boss near the player died. */
        public long lordFell = NEVER;
        /** The gate's pressure stage (0 calm, 1 stirring, 2 waves, 3 the Warden); 0 elsewhere. */
        public int pressure;
        /** A provider or a game master pins the level (-1: decide from the signals). */
        public int forced = -1;
        /** A new run or floor began (the bed comes back from its rest). Consumed by the watch. */
        public boolean arrival;

        /** Clears what a scan fills (damage times and the Lord's fall persist). */
        public void clearScan() {
            hunting = huntingClose = near = 0;
            lordAlive = lordHunting = false;
            lordDistance = Double.MAX_VALUE;
            pressure = 0;
            forced = -1;
        }

        /** Forgets everything (death, leaving the context). */
        public void reset() {
            clearScan();
            lastCombat = lastLordHit = lordFell = NEVER;
            arrival = false;
        }
    }

    private final long[] seen = {NEVER, NEVER, NEVER, NEVER};
    private int level = CALM;
    private long lastFell = NEVER;
    private String why = "calm";

    /** The level after this evaluation. */
    public int update(long now, Signals s) {
        if (s.forced >= 0) {
            level = Math.min(LORD, s.forced);
            why = "forced";
            return level;
        }
        if (s.lordFell != lastFell) {
            lastFell = s.lordFell;
            if (now - s.lordFell <= PERIOD * 2) { // the Lord fell: the fight is over
                seen[LORD] = seen[COMBAT] = NEVER;
                s.lastCombat = s.lastLordHit = NEVER;
            }
        }
        boolean lord = s.lordAlive && (s.lordHunting || s.lordDistance <= LORD_ARENA || now - s.lastLordHit <= LORD_WINDOW);
        boolean combat = now - s.lastCombat <= COMBAT_WINDOW || s.huntingClose >= SWARM;
        boolean tension = s.hunting > 0 || s.near > 0 || s.pressure >= PRESSURE || s.lordAlive;
        if (lord) seen[LORD] = now;
        if (combat) seen[COMBAT] = now;
        if (tension) seen[TENSION] = now;
        int next = CALM;
        for (int l = LORD; l > CALM; l--) {
            if (now - seen[l] <= HOLD[l]) {
                next = l;
                break;
            }
        }
        if (next != level) {
            why = next == LORD ? (s.lordHunting ? "the Lord hunts you" : s.lordDistance <= LORD_ARENA ? "in the Lord's arena"
                    : lord ? "blows traded with the Lord" : "held")
                    : next == COMBAT ? (now - s.lastCombat <= COMBAT_WINDOW ? "blows traded" : combat ? s.huntingClose + " hunting close" : "held")
                    : next == TENSION ? (s.hunting > 0 ? s.hunting + " hunting" : s.near > 0 ? s.near + " near"
                            : s.pressure >= PRESSURE ? "pressure " + s.pressure : s.lordAlive ? "a Lord near" : "held")
                    : "quiet";
        }
        level = next;
        return level;
    }

    public int level() {
        return level;
    }

    /** Why the level last changed (logs and /awaken audio layers). Constant strings or built only on a change. */
    public String why() {
        return why;
    }

    public void reset() {
        java.util.Arrays.fill(seen, NEVER);
        level = CALM;
        lastFell = NEVER;
        why = "calm";
    }

    public static String name(int level) {
        return level >= 0 && level < NAMES.length ? NAMES[level] : "none";
    }

    public static int byName(String name) {
        for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equals(name)) return i;
        return -1;
    }
}
