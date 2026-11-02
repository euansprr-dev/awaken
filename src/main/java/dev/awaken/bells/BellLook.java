package dev.awaken.bells;

/**
 * How a bell is shaped (SPEC §43.16, stage 6): the numbers tools/art/specs/bells.py draws the hundred with, kept here for
 * the renderers that set one on the floor or under a beam. Every bell hangs from (8, 15, 8) in its model; its size goes by
 * rank (the lip's half-width, a house bell at E up to an S bell), its metal by where it's from ({@link #family}), and the
 * Great Bells are their own, taller shape.
 */
public final class BellLook {
    private BellLook() {}

    /** The lip's half-width by rank, E to S (model units). */
    static final float[] LIP = {4.0F, 4.5F, 5.0F, 5.5F, 6.0F, 6.5F};
    static final float GREAT_LIP = 7.5F;
    /** A bell's height as a multiple of its lip's half-width (the profile's parts summed). */
    static final float HEIGHT = 1.71F, GREAT_HEIGHT = 1.96F;
    /** Where every bell hangs from: its loop sits on the hook above. */
    public static final float TOP = 15F;

    /** How far below its hook point bell {@code no}'s lip comes, in model units. */
    public static float height(int no) {
        BellCatalog.Def d = BellCatalog.of(no);
        return Hanging.great(d) ? GREAT_LIP * GREAT_HEIGHT : LIP[Math.min(LIP.length - 1, d.rank().ordinal())] * HEIGHT;
    }

    /** The model y of bell {@code no}'s lip: set it on the floor by this. */
    public static float bottom(int no) {
        return TOP - height(no);
    }

    public static boolean great(int no) {
        return Hanging.great(BellCatalog.of(no));
    }

    /** The metal a bell is cast in, by where it's from (its model is items/bell/&lt;family&gt;_&lt;rank&gt;, or great). */
    public static String family(BellCatalog.Def d) {
        return switch (d.source()) {
            case DEEP, DESCENT -> "iron";
            case ABOVE -> "silver";
            case LUCIDIUM -> "pearl";
            case VEIL -> "glass";
            case COURT -> "court";
            case PROVING, SEED -> "great";
            default -> "bronze";
        };
    }
}
