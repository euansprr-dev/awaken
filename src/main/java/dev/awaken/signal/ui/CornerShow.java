package dev.awaken.signal.ui;

/**
 * When the corners speak on the clear screen (SPEC §40.6): the Now line top left and the task card top right show when
 * they help and step aside the rest of the time. Classic shows them as always. Plain fields: side-agnostic, checked by
 * GameTests.
 */
public final class CornerShow {
    private CornerShow() {}

    /**
     * The Now line (SPEC §27.11): one place, shown for 8 s when it is new, while you are within 50 m, when you have looked
     * more than 70° away from it for 3 s, or stood still for 2 s; then held 3 s more, so it never blinks. Walking toward
     * it, it fades: the beam and the Trails show the way in the world. In a fight it steps aside.
     */
    public static final class NowLine {
        public static final int FRESH = 160, TURN_DEG = 70, TURN_TICKS = 60, STILL_TICKS = 40, NEAR = 50, HOLD = 60;
        private int turned, still, hold = -1;

        /**
         * Once a tick: ticks since the place changed, degrees between the look and the place (0 when it has no bearing
         * here), blocks to it (huge in another dimension), whether the player is moving, and whether a fight is on.
         * True: show it.
         */
        public boolean tick(int sinceNew, float offDeg, double dist, boolean moving, boolean fighting) {
            turned = offDeg > TURN_DEG ? Math.min(turned + 1, TURN_TICKS) : 0;
            still = moving ? 0 : Math.min(still + 1, STILL_TICKS);
            boolean cause = sinceNew < FRESH || dist <= NEAR || turned >= TURN_TICKS || still >= STILL_TICKS;
            hold = cause ? HOLD : Math.max(-1, hold - 1);
            return hold >= 0 && !fighting;
        }

        public void reset() {
            turned = still = 0;
            hold = -1;
        }
    }

    /**
     * The task card (SPEC §27.10): 8 s after the task or a step changes or ticks; a step that teaches a key stays until
     * that key has been pressed (then its 8 s run from the press). In a fight it steps aside, unless its step teaches a
     * key (a fight's lesson needs its card).
     */
    public static final class TaskCard {
        public static final int SHOW = 160;
        private int since = SHOW;
        private boolean taught;

        /** The server sent a change (a new task, a step ticked or moved on). */
        public void changed() {
            since = 0;
            taught = false;
        }

        /** Once a tick: whether the current step teaches a key, whether that key is down now, and a fight. True: show it. */
        public boolean tick(boolean teachesKey, boolean keyDown, boolean fighting) {
            since = Math.min(since + 1, SHOW);
            if (teachesKey && keyDown && !taught) {
                taught = true;
                since = 0;
            }
            boolean lesson = teachesKey && !taught;
            return lesson || since < SHOW && !fighting;
        }

        public void reset() {
            since = SHOW;
            taught = false;
        }
    }
}
