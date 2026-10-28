package dev.awaken.ground.wild;

/**
 * What a wild plant is, for the rules that read many blocks at once (SPEC §41.2): how much it hides someone crouching in
 * it, and whether it grazes, presses flat or sheds seed. One per block; the looks live in the block's state.
 */
public enum WildKind {
    MEADOW(0.30), FROST(0.25), LONG(0.85), RYE(1.0), TRAMPLED(0.0), CLOVER(0.0), PARSLEY(0.40), FOXGLOVE(0.40), THISTLE(0.20),
    HEATHER(0.25), GORSE(0.60), TUSSOCK(0.10), BRAMBLE(0.60), BRACKEN(0.85), REEDS(0.80), STILL(0.0);

    /** How hidden a crouching body is when this plant stands in its block (0 open, 1 gone). */
    public final double cover;

    WildKind(double cover) {
        this.cover = cover;
    }
}
