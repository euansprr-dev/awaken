package dev.awaken.mobs.def;

/**
 * The one thing an overworld creature does that makes it worth meeting ("no purely aesthetic creatures"). The
 * behaviour lives in {@code mobs.entity.FaunaMob}; this keeps it data on the def so new creatures can reuse it.
 */
public enum FaunaTalent {
    NONE,
    /** Moths: drift to a player holding a light, otherwise circle above ore buried below them. */
    DOWSE,
    /** Sprites: seek crops and saplings and make them grow a stage. */
    TEND,
    /** Scarabs: dive into sand when a player stomps close (sneak to catch one). */
    BURROW,
    /** Finches: hop in flocks, scatter from running players, gather at lit campfires. */
    FLOCK,
    /** Tortoises: graze slowly, shell up when struck. */
    GRAZE,
    /** Stags: bolt from running players, charge when cornered. */
    SKITTISH,
    /** Rays: glide in wide loops just above the ground. */
    GLIDE,
    /** Night hunters: creep up crouched, pounce, retreat from light. */
    STALK,
    /** Named beasts: hold a territory, warn, then fight in phases. */
    TERRITORY
}
