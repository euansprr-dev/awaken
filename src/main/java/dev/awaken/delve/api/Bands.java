package dev.awaken.delve.api;

/**
 * The deep's three bands (SPEC §34.5), by height under the rock: <b>the Shallows</b> (y 40 and up), <b>the Middle Dark</b>
 * (0 to 40) and <b>the Deep</b> (below 0). Each has its own air, look, caption and creatures; the Hollows' spawn rules ask
 * {@link #allows}. Pure: other modules may call it from anywhere.
 */
public final class Bands {
    private Bands() {}

    public static final int SHALLOWS_FROM = 40, MIDDLE_FROM = 0;

    public enum Band {
        SHALLOWS("The Shallows", "under the rock", 0x8FA3B8),
        MIDDLE("The Middle Dark", "warm, still air", 0xB08A58),
        DEEP("The Deep", "where the hum lives", 0x5A3C7A);

        public final String title, words;
        /** The band's air (0xRRGGBB): its cave air's colour, the dust's tint. */
        public final int air;

        Band(String title, String words, int air) {
            this.title = title;
            this.words = words;
            this.air = air;
        }
    }

    public static Band of(int y) {
        return y >= SHALLOWS_FROM ? Band.SHALLOWS : y >= MIDDLE_FROM ? Band.MIDDLE : Band.DEEP;
    }

    /**
     * Whether a Hollows creature lives at this height: the Shallows hold drip lurkers and chime crickets, the Middle Dark
     * adds the hushlings, the Deep holds all of them (any other creature: everywhere it already may).
     */
    public static boolean allows(String mobId, int y) {
        return !mobId.equals("hushling") || of(y) != Band.SHALLOWS;
    }
}
