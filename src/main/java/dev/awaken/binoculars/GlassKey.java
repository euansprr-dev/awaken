package dev.awaken.binoculars;

/**
 * The binoculars key (SPEC §39.2). Tap and hold, the default: a press shorter than {@link GlassFeel#tapMs} keeps them up
 * (toggle), a longer one is a peek and lowers them on release. Hold only and toggle only are the settings' other modes.
 * Pure: the client feeds it presses and releases with their times and does what it answers.
 */
public final class GlassKey {
    public enum Mode { TAP_HOLD, HOLD, TOGGLE }

    public enum Action { NONE, RAISE, LOWER }

    private long pressedAt;
    /** This press raised them (so its release may be a peek's end). */
    private boolean raisedByPress;

    /** The key went down. {@code up}: they are up or coming up; {@code canRaise}: nothing keeps the hands busy. */
    public Action press(long ms, boolean up, boolean canRaise, Mode mode) {
        pressedAt = ms;
        raisedByPress = false;
        if (up) return Action.LOWER;
        if (!canRaise) return Action.NONE;
        raisedByPress = mode != Mode.TOGGLE;
        return Action.RAISE;
    }

    /** The key came up. */
    public Action release(long ms, boolean up, Mode mode, long tapMs) {
        boolean mine = raisedByPress;
        raisedByPress = false;
        if (!mine || !up) return Action.NONE;
        if (mode == Mode.HOLD) return Action.LOWER;
        return ms - pressedAt >= tapMs ? Action.LOWER : Action.NONE;
    }

    /** Lowered by something else (a hit, a screen): the press in progress no longer owns them. */
    public void reset() {
        raisedByPress = false;
    }
}
