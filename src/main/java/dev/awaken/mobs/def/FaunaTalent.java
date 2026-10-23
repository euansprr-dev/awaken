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
    TERRITORY,
    // ---- P2 (the rest of the land) ----
    /** Bogwicks: drift to a poisoned player and draw the poison out. */
    CLEANSE,
    /** Mudskippers: stop now and then on mud or sand and dig up a find (clay, slime). */
    FORAGE,
    /** Marmots: dig finds out of the scree; whistle, sit up and dive into the ground when a player runs at them. */
    DIG,
    /** Bog toads: snap up small slimes nearby (they drop their slime balls). */
    GULP,
    /** Dart frogs: climb trunks and walls; a bare-handed strike poisons the striker. */
    VENOM,
    /** Basilisks: bolt from running players on their hind legs, straight across water. */
    SPRINT,
    /** Owls: at night, stare down the nearest monster near a player and make it glow. */
    WATCH,
    /** Gloam snails: leave a patch of glow lichen where they rest in the dark (light means safety). */
    TRAIL,
    /** Petalwings: hover over open grass and a flower opens there. */
    POLLINATE,
    /** Honey bears: amble to bee nests and shake the honeycomb down. */
    HONEY,
    /** Peak kites: soar in high loops; now and then a feather drifts down near a player. */
    SOAR,
    /** Shroomlets: puff spores when startled, plant small mushrooms in the shade. */
    SPORE,
    /** Sporeback snails: mushrooms they rest beside grow into giant mushrooms. */
    MYCEL
}
