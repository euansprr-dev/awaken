package dev.awaken.gates.world;

import dev.awaken.gates.content.Theme;

/**
 * The set pieces that make a theme's big rooms memorable, and the Lords' arenas (docs/modules/gates.md "Interiors").
 * Chosen per room by {@link FloorBuilder} (content version 2+), shaped by {@link Pieces} (pure geometry shared with
 * spawning and pathing) and painted by {@link GatePainter}. {@code minArea} is in slots; {@code minHeight} is the room
 * height it needs; {@code shape} replaces the room's shape when the piece is chosen (null keeps it).
 */
public enum SetPiece {
    // Bell Crypt: hanging bells, ossuary walls, the dead in rows
    BELL_LOFT(Theme.BELL_CRYPT, 2, 9, Room.Shape.ROUND),
    OSSUARY_AISLES(Theme.BELL_CRYPT, 2, 7, Room.Shape.RECT),
    SARCOPHAGI(Theme.BELL_CRYPT, 1, 7, Room.Shape.RECT),
    // Fungal Hollow: a grove of giant caps, a glowing spore pool
    MUSHROOM_GROVE(Theme.FUNGAL_HOLLOW, 2, 9, Room.Shape.ROUND),
    SPORE_POOL(Theme.FUNGAL_HOLLOW, 1, 7, Room.Shape.ROUND),
    // Ember Mines: a timbered gallery with its rails and ore face, a forge over a lava channel
    MINE_GALLERY(Theme.EMBER_MINES, 1, 7, Room.Shape.RECT),
    LAVA_FORGE(Theme.EMBER_MINES, 2, 9, Room.Shape.RECT),
    // Drowned Archive: shelves standing in the flood, a sunken reading room
    FLOODED_STACKS(Theme.DROWNED_ARCHIVE, 2, 9, Room.Shape.RECT),
    READING_ROOM(Theme.DROWNED_ARCHIVE, 1, 7, Room.Shape.RECT),
    // Clockwork Foundry: the gear wall, the conveyor line, the boiler
    GEAR_WALL(Theme.CLOCKWORK_FOUNDRY, 1, 7, Room.Shape.RECT),
    CONVEYOR(Theme.CLOCKWORK_FOUNDRY, 2, 9, Room.Shape.RECT),
    BOILER(Theme.CLOCKWORK_FOUNDRY, 2, 9, Room.Shape.ROUND),
    // Glass Desert: crystals out of the drifts, a colonnade half under the sand
    GLASS_GARDEN(Theme.GLASS_DESERT, 1, 7, Room.Shape.ROUND),
    BURIED_COLONNADE(Theme.GLASS_DESERT, 2, 9, Room.Shape.RECT),
    // Hive: the brood comb, honey pools
    BROOD_COMB(Theme.HIVE, 2, 9, Room.Shape.ROUND),
    HONEY_POOLS(Theme.HIVE, 1, 7, Room.Shape.OCTAGON),
    // Overgrown Temple: a tree that split the temple, a reflecting pool
    ROOT_TREE(Theme.OVERGROWN_TEMPLE, 2, 9, Room.Shape.ROUND),
    REFLECTING_POOL(Theme.OVERGROWN_TEMPLE, 1, 7, Room.Shape.RECT),
    // Lantern Night Market: a street of stalls, a tree hung with lanterns
    MARKET_STALLS(Theme.LANTERN_NIGHT_MARKET, 1, 7, Room.Shape.RECT),
    LANTERN_TREE(Theme.LANTERN_NIGHT_MARKET, 2, 9, Room.Shape.ROUND),
    // Frost Monastery: the cloister round a frozen garden, the choir
    CLOISTER(Theme.FROST_MONASTERY, 2, 9, Room.Shape.RECT),
    CHOIR(Theme.FROST_MONASTERY, 1, 7, Room.Shape.RECT),
    // Mirror Palace: the reflecting hall (a ring of mirrors), the maze of glass
    MIRROR_HALL(Theme.MIRROR_PALACE, 1, 7, Room.Shape.OCTAGON),
    MIRROR_MAZE(Theme.MIRROR_PALACE, 2, 9, Room.Shape.RECT),
    // Precursor Lab: specimen tanks, the server hall
    TANKS(Theme.PRECURSOR_LAB, 1, 7, Room.Shape.RECT),
    SERVER_HALL(Theme.PRECURSOR_LAB, 2, 9, Room.Shape.RECT),
    // Nightmare: the table you are too small for, the upside-down room
    GIANT_TABLE(Theme.NIGHTMARE, 2, 9, Room.Shape.RECT),
    WRONG_ROOM(Theme.NIGHTMARE, 1, 7, Room.Shape.RECT),
    // Sky Archipelago: floating islands with their trees and bridges, a wind shrine
    FLOATING_ISLES(Theme.SKY_ARCHIPELAGO, 1, 7, Room.Shape.ISLANDS),
    WIND_SHRINE(Theme.SKY_ARCHIPELAGO, 2, 9, Room.Shape.ROUND),
    // Abyssal Shrine: a rift into the void, unlit monoliths
    VOID_RIFT(Theme.ABYSSAL_SHRINE, 2, 9, Room.Shape.ROUND),
    MONOLITHS(Theme.ABYSSAL_SHRINE, 1, 7, Room.Shape.ROUND),

    // ---- the Lords' arenas (one per Lord; built for its moves) ------------------------------------------------------
    ARENA_BELL(Theme.BELL_CRYPT),
    ARENA_SPORES(Theme.FUNGAL_HOLLOW),
    ARENA_EMBER(Theme.EMBER_MINES),
    ARENA_ARCHIVE(Theme.DROWNED_ARCHIVE),
    ARENA_GEARS(Theme.CLOCKWORK_FOUNDRY),
    ARENA_GLASS(Theme.GLASS_DESERT),
    ARENA_HIVE(Theme.HIVE),
    ARENA_ROOTS(Theme.OVERGROWN_TEMPLE),
    ARENA_MARKET(Theme.LANTERN_NIGHT_MARKET),
    ARENA_FROST(Theme.FROST_MONASTERY),
    ARENA_MIRROR(Theme.MIRROR_PALACE),
    ARENA_LAB(Theme.PRECURSOR_LAB),
    ARENA_DREAM(Theme.NIGHTMARE),
    ARENA_SKY(Theme.SKY_ARCHIPELAGO),
    ARENA_ABYSS(Theme.ABYSSAL_SHRINE);

    public final Theme theme;
    public final int minArea, minHeight;
    public final Room.Shape shape;
    public final boolean arena;

    SetPiece(Theme theme, int minArea, int minHeight, Room.Shape shape) {
        this.theme = theme;
        this.minArea = minArea;
        this.minHeight = minHeight;
        this.shape = shape;
        this.arena = false;
    }

    SetPiece(Theme theme) {
        this.theme = theme;
        this.minArea = 9;
        this.minHeight = 17;
        // round so every spot a player can stand on is inside the Lord's arena radius (22 to 28, mobs module); the Sky
        // Leviathan's (radius 28) is islands over a cloud sea, corners included
        this.shape = theme == Theme.SKY_ARCHIPELAGO ? Room.Shape.ISLANDS : Room.Shape.ROUND;
        this.arena = true;
    }

    /**
     * Radius of the obstacle a piece puts at the room's centre (a boiler, a tree, a rift): the door axes stop there and
     * a walkway of at least 2.5 blocks round it stays clear, so every doorway still reaches every other.
     */
    public double core() {
        return switch (this) {
            case BOILER, ROOT_TREE -> 3.0;
            case LANTERN_TREE -> 2.6;
            case BROOD_COMB, WIND_SHRINE -> 2.0;
            case VOID_RIFT -> 4.6;
            case MIRROR_HALL -> 1.6;
            default -> 0;
        };
    }

    /** The arena built for this theme's Lord. */
    public static SetPiece arenaFor(Theme lordTheme) {
        for (SetPiece p : values()) if (p.arena && p.theme == lordTheme) return p;
        return ARENA_BELL;
    }

    /** The room set pieces of a theme, in a stable order. */
    public static java.util.List<SetPiece> roomPieces(Theme t) {
        java.util.List<SetPiece> out = new java.util.ArrayList<>();
        for (SetPiece p : values()) if (!p.arena && p.theme == t) out.add(p);
        return out;
    }
}
