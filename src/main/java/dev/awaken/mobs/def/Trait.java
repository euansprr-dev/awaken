package dev.awaken.mobs.def;

/** Passive behaviour a creature always has (abilities are the active, telegraphed part). */
public enum Trait {
    /** Keeps its distance and lets its abilities do the work. */
    RANGED,
    /** Only fights back (Lantern Market folk). */
    NEUTRAL,
    /** Never hostile until a duel is accepted (the Lantern Broker). */
    FRIENDLY,
    /** Walks up, swells for a moment, bursts. Always volatile. */
    FUSE,
    /** Blind: hunts the last place it heard a player (sprinting, fighting, not sneaking). */
    LISTENER,
    /** Hunts players projected into the Veil, and bites their cord. */
    SPIRIT_HUNTER,
    /** Passes through walls. */
    NOCLIP,
    /** Only moves while no player is looking at it. */
    WATCHED_FREEZE,
    /** Snatches an essence shard on a hit and runs; drops it when killed. */
    STEALER,
    /** Splits into two smaller copies when killed (once). */
    SPLITS,
    /** Leaves a spore cloud where it dies. */
    DEATH_CLOUD,
    /** Sprays shards when it dies. */
    DEATH_SHARDS,
    /** Hits set you alight. */
    IGNITE,
    /** Hits poison. */
    POISON_TOUCH,
    /** Hits slow. */
    SLOW_TOUCH,
    /** Hits wither. */
    WITHER_TOUCH,
    /** Gets up once after dying (innate Echoing). */
    RISES,
    /** Always the semi-transparent spirit variant. */
    HAUNTED,
    /** Rendered upside down (the Inverted Sea). */
    UPSIDE_DOWN,
    /** Wears the face of the nearest player. */
    MIMIC,
    /** Dormant until a player comes close or hits it. */
    DORMANT,
    FIRE_IMMUNE,
    /** Reflects projectiles back at their shooter. */
    REFLECT,
    /** Knows where its prey is from across the floor (Wardens). */
    HUNTER
}
