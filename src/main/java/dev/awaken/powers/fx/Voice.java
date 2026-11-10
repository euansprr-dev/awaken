package dev.awaken.powers.fx;

import java.util.Locale;

/**
 * A power's voice (SPEC §50.7, "Scores"): its sound is a score, a few layers of real recordings for each beat of the cast.
 * The server names the beat ({@code Cast.voice}); every client plays that beat's layers from
 * {@code assets/awaken/power_voices.json} ({@code powers.client.fx.PowerVoice}), or the power's classic sound when the
 * player has chosen Classic or the power has no score yet. One event a beat, whatever the number of layers.
 */
public final class Voice {
    private Voice() {}

    /** The beats a cast can have. The order is the wire's: add at the end. */
    public enum Beat {
        /** The wind-up or the charge beginning. Plays at once, never waits for the release frame. */
        GATHER,
        /** A charge reaching full. */
        PEAK,
        /** The power leaving the body. */
        RELEASE,
        /** Something in flight (a bolt's hiss), may repeat. */
        TRAVEL,
        /** It lands. */
        IMPACT,
        /** What is left afterwards. */
        TAIL,
        /** A second arrival a moment later (Spirit Palm's echo, an afterimage's burst). */
        ECHO,
        /** While a power is held or stands in the world: a pulse that may repeat. */
        HOLD,
        /** A zone or a hold ending. */
        END,
        /** The level 5 layer, under the release or the impact. */
        LEVEL5,
        /** A power's own extra moments. */
        EXTRA_A, EXTRA_B;

        public final String key = name().toLowerCase(Locale.ROOT);

        /** Beats that belong to the press, not the release: they never wait for the body's release frame. */
        public boolean early() {
            return this == GATHER || this == PEAK || this == HOLD;
        }
    }

    private static final Beat[] BEATS = Beat.values();

    public static Beat beat(int ordinal) {
        return ordinal >= 0 && ordinal < BEATS.length ? BEATS[ordinal] : Beat.RELEASE;
    }
}
