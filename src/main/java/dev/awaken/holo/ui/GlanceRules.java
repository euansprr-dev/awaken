package dev.awaken.holo.ui;

/**
 * The Tab glance's rules (SPEC §40.7): the wrist and the band's panel show while the key is held, and only then. The
 * panel's rows, top to bottom: level and rank (with the XP line), the vitals two to a row (health, Aether once you have
 * it, food, armour), the task with its current step, the way to where you're heading, and the hotbar. Plain fields:
 * side-agnostic, checked by GameTests.
 */
public final class GlanceRules {
    private GlanceRules() {}

    public enum Row { LEVEL, VITALS, TASK, STEP, HEADING, HOTBAR }

    /** The most rows a panel can have: what {@code out} must hold. */
    public static final int MOST = 24;

    /**
     * The rows shown, in order, into {@code out}; returns how many (SPEC §57.9). The vitals stand two to a row, each a
     * mark, a bar and its number ({@code vitals}: 3 before Aether, 4 with it). A task takes a row for each line of its
     * name and of its current step, so neither is ever cut; the step after it is not shown (the count says how many are
     * left). {@code heading}: the lines of the place you're heading to, 0 for nowhere.
     */
    public static int rows(int vitals, int title, int step, int heading, boolean hotbar, Row[] out) {
        int n = 0;
        out[n++] = Row.LEVEL;
        for (int i = 0; i < (vitals + 1) / 2; i++) out[n++] = Row.VITALS;
        for (int i = 0; i < title && n < out.length - 2; i++) out[n++] = Row.TASK;
        if (title > 0) for (int i = 0; i < step && n < out.length - 2; i++) out[n++] = Row.STEP;
        for (int i = 0; i < heading && n < out.length - 1; i++) out[n++] = Row.HEADING;
        if (hotbar) out[n++] = Row.HOTBAR;
        return n;
    }

    /** The key: the arm and the panel are up while it is held, and go when it is let go. */
    public static final class Key {
        private boolean held;

        /** Once a tick with whether the key is down. */
        public void tick(boolean down) {
            held = down;
        }

        public boolean arm() {
            return held;
        }

        public boolean panel() {
            return held;
        }

        public void reset() {
            held = false;
        }
    }

    /**
     * How far the hands are out of view (0 up, 1 gone) while the arm with the band is up or going: they sink as it rises, and
     * as it lowers they follow the game's own re-equip, so the weapon's arms rise exactly as its hands do.
     */
    public static float handsAway(boolean armGoing, float raise, float equip) {
        return armGoing ? equip : Math.max(0F, Math.min(1F, raise));
    }
}
