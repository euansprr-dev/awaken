package dev.awaken.holo.ui;

/**
 * The Tab glance's rules (SPEC §40.7): the wrist and the band's panel show while the key is held, and only then. The
 * panel's rows, top to bottom: level and rank (with the XP line), health, Aether (once you have it), food, armour, the
 * task with its current and next step, the way to where you're heading, and the hotbar. Plain fields: side-agnostic,
 * checked by GameTests.
 */
public final class GlanceRules {
    private GlanceRules() {}

    public enum Row { LEVEL, HEALTH, AETHER, FOOD, ARMOUR, TASK, STEP, NEXT, HEADING, HOTBAR }

    /** The rows shown, in order, into {@code out}; returns how many. */
    public static int rows(boolean aether, boolean task, boolean step, boolean next, boolean heading, boolean hotbar, Row[] out) {
        int n = 0;
        out[n++] = Row.LEVEL;
        out[n++] = Row.HEALTH;
        if (aether) out[n++] = Row.AETHER;
        out[n++] = Row.FOOD;
        out[n++] = Row.ARMOUR;
        if (task) {
            out[n++] = Row.TASK;
            if (step) out[n++] = Row.STEP;
            if (next) out[n++] = Row.NEXT;
        }
        if (heading) out[n++] = Row.HEADING;
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
