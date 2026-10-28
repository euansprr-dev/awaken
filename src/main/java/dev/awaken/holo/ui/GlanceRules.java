package dev.awaken.holo.ui;

/**
 * The Tab glance's rules (SPEC §40.7): holding the key raises the wrist and the band's panel; a quick tap leaves the panel
 * up for 4 s without the arm. The panel's rows, top to bottom: level and rank (with the XP line), health, Aether (once
 * you have it), food, armour, the task with its current and next step, and the way to where you're heading. Plain
 * fields: side-agnostic, checked by GameTests.
 */
public final class GlanceRules {
    private GlanceRules() {}

    /** A press shorter than this is a tap (250 ms); a tap keeps the panel up this long (4 s). */
    public static final int TAP_TICKS = 5, TAP_SHOW = 80;

    public enum Row { LEVEL, HEALTH, AETHER, FOOD, ARMOUR, TASK, STEP, NEXT, HEADING }

    /** The rows shown, in order, into {@code out}; returns how many. */
    public static int rows(boolean aether, boolean task, boolean step, boolean next, boolean heading, Row[] out) {
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
        return n;
    }

    /** The key: held (the arm up while it is), or tapped (the panel alone for a while). */
    public static final class Key {
        private boolean held;
        private int heldTicks, tapLeft;

        /** Once a tick with whether the key is down. */
        public void tick(boolean down) {
            if (down) {
                if (!held) {
                    held = true;
                    heldTicks = 0;
                    tapLeft = 0;
                }
                heldTicks++;
                return;
            }
            if (held) {
                held = false;
                tapLeft = heldTicks < TAP_TICKS ? TAP_SHOW : 0;
                return;
            }
            if (tapLeft > 0) tapLeft--;
        }

        /** The arm is up while the key is held. */
        public boolean arm() {
            return held;
        }

        /** The panel shows while the key is held and for a while after a tap. */
        public boolean panel() {
            return held || tapLeft > 0;
        }

        public void reset() {
            held = false;
            heldTicks = tapLeft = 0;
        }
    }
}
