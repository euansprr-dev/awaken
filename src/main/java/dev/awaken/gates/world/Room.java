package dev.awaken.gates.world;

import java.util.ArrayList;
import java.util.List;

/**
 * One room of a floor: a rectangle of grid slots (a slot is one chunk), a shape, a floor offset and its doors and
 * special blocks. Built once by {@link FloorBuilder} and never changed afterwards (shared by worldgen threads).
 * Coordinates of features and spawn spots are floor-local blocks: x = gx*16 + bx, z = gz*16 + bz, y = offset above the
 * floor's base Y.
 */
public final class Room {
    public enum Type {
        START, NORMAL, ELITE, CACHE, PUZZLE, SHRINE, BOSS, HALL, HUB, ARENA, CAVERN, ANTE, KEY, INNER_GATE, MARKET,
        SPIRIT, SHAFT, ANNEX_EXIT,
        /** The Ritual of the Bell's hall: the story's ritual director owns it (no monsters, no seal, no Lord). */
        RITUAL;

        /** Rooms that spawn monsters when first entered. */
        public boolean combat() {
            return switch (this) {
                case NORMAL, ELITE, CACHE, HALL, HUB, ARENA, CAVERN, KEY, ANTE, BOSS, SHAFT -> true;
                default -> false;
            };
        }

        /** Safe rooms: no pressure waves spawn inside. */
        public boolean safe() {
            return this == START || this == SHRINE || this == MARKET || this == ANNEX_EXIT || this == RITUAL;
        }
    }

    public enum Shape { RECT, OCTAGON, ROUND, CROSS, PILLARED, RING, PIT, TERRACE, HALL, CAVE, SHAFT, ISLANDS }

    public enum FeatureKind {
        SHRINE, SEAL, CACHE, RUNE, ENTRANCE, LANTERN, INNER_GATE, RIFT_WALL, UPDRAFT, EXIT_ANCHOR, BOSS_SPOT, SPIRIT_SPOT,
        MARKET_SPOT, ANNEX_RETURN, VAULT_CACHE
    }

    public record Feature(FeatureKind kind, int x, int y, int z, int index) {}

    public static final class Door {
        public final int gx, gz;       // slot of THIS room that the door is in
        public final int side;         // 0 north (-z), 1 east (+x), 2 south (+z), 3 west (-x)
        public final int y;            // walking offset of the doorway (max of both rooms)
        public final int toRoom;
        public boolean sealed;         // bars at generation (shrine waiting on keys/puzzles/lanterns)
        public boolean ownsBars;       // this side paints the bars
        /** Leads one room closer to the floor's end along the critical path: its frame is lit with the guide light. */
        public boolean onward;
        /** Opens into the floor's end room (Shrine or the Lord's arena): lit in the System's blue or the Lord's colour. */
        public boolean toEnd;

        Door(int gx, int gz, int side, int y, int toRoom) {
            this.gx = gx;
            this.gz = gz;
            this.side = side;
            this.y = y;
            this.toRoom = toRoom;
        }

        /** Centre of the doorway on the wall plane, floor-local block coords (x2, z2 are doubled to keep halves). */
        public int centerX() {
            return switch (side) {
                case 1 -> (gx + 1) * 16;
                case 3 -> gx * 16;
                default -> gx * 16 + 8;
            };
        }

        public int centerZ() {
            return switch (side) {
                case 0 -> gz * 16;
                case 2 -> (gz + 1) * 16;
                default -> gz * 16 + 8;
            };
        }
    }

    public final int id;
    public Type type;
    public Shape shape;
    public final int gx, gz, gw, gh;
    public int dy;
    public int height;
    public final long seed;
    public final List<Door> doors = new ArrayList<>();
    public final List<Feature> features = new ArrayList<>();
    /** Monsters planned when the room is first entered: normal and elite counts (before modifiers). */
    public int normals, elites;
    /** Second wave for arenas (normals, elites). */
    public int wave2Normals, wave2Elites;
    /** Open floor spots for spawning, floor-local (x, z) pairs. */
    public final List<int[]> spawnSpots = new ArrayList<>();
    /** Sky Archipelago and Shaft rooms can have open drops; painted by the room itself. */
    public boolean dark;
    /** The set piece or Lord's arena dressing this room (null: the theme's plain dressing). Content version 2+. */
    public SetPiece piece;
    /** On the shortest way from the floor's start to its end. */
    public boolean onPath;
    /** On the critical path: the door you came in by and the door that leads on (null at the start and the end). */
    public Door pathIn, pathOut;
    /** The Gate Rig's archetype in this room, or null (content version 4+; at most one room a floor). */
    public RigRoom rig;

    Room(int id, Type type, Shape shape, int gx, int gz, int gw, int gh, int dy, int height, long seed) {
        this.id = id;
        this.type = type;
        this.shape = shape;
        this.gx = gx;
        this.gz = gz;
        this.gw = gw;
        this.gh = gh;
        this.dy = dy;
        this.height = height;
        this.seed = seed;
    }

    public boolean containsSlot(int x, int z) {
        return x >= gx && x < gx + gw && z >= gz && z < gz + gh;
    }

    /** Interior bounds (inclusive), floor-local blocks. Walls are {@link RoomGeometry#INSET} thick on each side. */
    public int x0() { return gx * 16 + RoomGeometry.INSET; }
    public int x1() { return (gx + gw) * 16 - 1 - RoomGeometry.INSET; }
    public int z0() { return gz * 16 + RoomGeometry.INSET; }
    public int z1() { return (gz + gh) * 16 - 1 - RoomGeometry.INSET; }
    public int centerX() { return gx * 16 + gw * 8; }
    public int centerZ() { return gz * 16 + gh * 8; }

    public int area() { return gw * gh; }

    public Door doorTo(int room) {
        for (Door d : doors) if (d.toRoom == room) return d;
        return null;
    }

    public Feature feature(FeatureKind kind) {
        for (Feature f : features) if (f.kind() == kind) return f;
        return null;
    }

    public List<Feature> features(FeatureKind kind) {
        List<Feature> out = new ArrayList<>();
        for (Feature f : features) if (f.kind() == kind) out.add(f);
        return out;
    }
}
