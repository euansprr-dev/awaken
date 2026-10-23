package dev.awaken.gates.world;

/**
 * Pure geometry of a room, shared by the painter (what blocks go where) and the server (where monsters can stand,
 * where bars go). All coordinates are floor-local blocks. Everything here is a pure function of the room.
 */
public final class RoomGeometry {
    private RoomGeometry() {}

    /** Wall thickness on each side of a slot. */
    public static final int INSET = 2;
    /** Doorways are 4 wide, at slot offsets 6..9. */
    public static final int DOOR_LO = 6, DOOR_HI = 9;
    public static final int DOOR_HEIGHT = 4;
    /** How deep a door tunnel cuts into its room (through the wall and a little beyond). */
    public static final int TUNNEL = INSET + 2;

    // ---- doors ------------------------------------------------------------------------------------------------------

    /** Distance from the door's slot boundary into the room (0 = outermost wall layer). */
    public static int depth(Room.Door d, int x, int z) {
        return switch (d.side) {
            case 0 -> z - d.gz * 16;
            case 2 -> (d.gz + 1) * 16 - 1 - z;
            case 1 -> (d.gx + 1) * 16 - 1 - x;
            default -> x - d.gx * 16;
        };
    }

    /** Offset along the wall inside the slot (doorway spans DOOR_LO..DOOR_HI). */
    public static int lateral(Room.Door d, int x, int z) {
        return (d.side == 0 || d.side == 2) ? x - d.gx * 16 : z - d.gz * 16;
    }

    private static boolean inDoorSlot(Room.Door d, int x, int z) {
        return x >= d.gx * 16 && x < d.gx * 16 + 16 && z >= d.gz * 16 && z < d.gz * 16 + 16;
    }

    /** The door whose tunnel contains this column on this room's side, or null. */
    public static Room.Door tunnel(Room r, int x, int z) {
        for (Room.Door d : r.doors) {
            if (!inDoorSlot(d, x, z)) continue;
            int lat = lateral(d, x, z);
            if (lat < DOOR_LO || lat > DOOR_HI) continue;
            int dep = depth(d, x, z);
            // in the lower of two rooms the tunnel stops at the wall: stairs take over from there
            int max = d.y > r.dy ? INSET : TUNNEL;
            if (dep >= 0 && dep < max) return d;
        }
        return null;
    }

    /** Stair steps inside a lower room leading up to a higher doorway: returns the step height (1..h) or 0. */
    public static int stairStep(Room r, int x, int z, Room.Door[] out) {
        for (Room.Door d : r.doors) {
            int rise = d.y - r.dy;
            if (rise <= 0 || !inDoorSlot(d, x, z)) continue;
            int lat = lateral(d, x, z);
            if (lat < DOOR_LO || lat > DOOR_HI) continue;
            int dep = depth(d, x, z);
            if (dep >= INSET && dep < INSET + rise) {
                if (out != null) out[0] = d;
                return rise - (dep - INSET); // rise at the wall, 1 at the innermost step
            }
        }
        return 0;
    }

    /** Whether this column is in the bars plane of a door (outermost wall layer of the owning room). */
    public static boolean barsPlane(Room.Door d, int x, int z) {
        if (!inDoorSlot(d, x, z)) return false;
        int lat = lateral(d, x, z);
        return lat >= DOOR_LO && lat <= DOOR_HI && depth(d, x, z) == 0;
    }

    // ---- shapes -----------------------------------------------------------------------------------------------------

    private static double u(Room r, int x) {
        return (x - (r.x0() + r.x1()) / 2.0) / ((r.x1() - r.x0() + 1) / 2.0);
    }

    private static double v(Room r, int z) {
        return (z - (r.z0() + r.z1()) / 2.0) / ((r.z1() - r.z0() + 1) / 2.0);
    }

    private static boolean inRect(Room r, int x, int z) {
        return x >= r.x0() && x <= r.x1() && z >= r.z0() && z <= r.z1();
    }

    /** Near one of the axes that connect the room's doors to its centre (kept clear in every shape). */
    public static boolean onDoorAxis(Room r, int x, int z, int half) {
        for (Room.Door d : r.doors) if (onAxisOf(r, d, x, z, half)) return true;
        return false;
    }

    /** Near the axis from one door to the room's centre (straight in, then along the centre line if the door is off it). */
    public static boolean onAxisOf(Room r, Room.Door d, int x, int z, int half) {
        int cx = r.centerX(), cz = r.centerZ();
        if (d.side == 0 || d.side == 2) {
            int dx = d.gx * 16 + 8;
            boolean between = d.side == 0 ? z <= cz && z >= d.gz * 16 : z >= cz && z <= (d.gz + 1) * 16;
            if (Math.abs(x + 0.5 - dx) <= half + 0.5 && between) return true;
            return Math.abs(z + 0.5 - cz) <= half + 0.5 && x >= Math.min(dx, cx) - half && x <= Math.max(dx, cx) + half;
        }
        int dz = d.gz * 16 + 8;
        boolean between = d.side == 3 ? x <= cx && x >= d.gx * 16 : x >= cx && x <= (d.gx + 1) * 16;
        if (Math.abs(z + 0.5 - dz) <= half + 0.5 && between) return true;
        return Math.abs(x + 0.5 - cx) <= half + 0.5 && z >= Math.min(dz, cz) - half && z <= Math.max(dz, cz) + half;
    }

    /** Solid structure or a set piece's obstacle: nobody stands or spawns here. */
    public static boolean blocked(Room r, int x, int z) {
        return solid(r, x, z) || (r.piece != null && inRect(r, x, z) && Pieces.blocks(r, x, z));
    }

    /** Inside the room's open interior (not wall, not pillar). Doors and stairs are handled separately. */
    public static boolean inside(Room r, int x, int z) {
        if (!inRect(r, x, z)) return false;
        double u = u(r, x), v = v(r, z);
        return switch (r.shape) {
            case RECT, PILLARED, PIT, TERRACE, SHAFT, ISLANDS -> true;
            case OCTAGON -> Math.abs(u) + Math.abs(v) <= 1.55;
            case ROUND -> u * u + v * v <= 1.04 || onDoorAxis(r, x, z, 2);
            case CROSS -> Math.abs(u) <= 0.45 || Math.abs(v) <= 0.45;
            case RING -> true;
            case HALL -> onDoorAxis(r, x, z, 2) || (Math.abs(x + 0.5 - r.centerX()) <= 3.5 && Math.abs(z + 0.5 - r.centerZ()) <= 3.5)
                    || alcove(r, x, z);
            case CAVE -> caveInside(r, x, z);
        };
    }

    private static boolean alcove(Room r, int x, int z) {
        // small niches off long halls, every 6 blocks, 2 deep
        if (r.gw == 1 && r.gh == 1) return false;
        long s = r.seed;
        if (r.gw > r.gh) {
            int k = Math.floorDiv(x - r.x0(), 6);
            if (Hash.unit(s, k, 7) < 0.5) return false;
            int off = Math.floorMod(x - r.x0(), 6);
            return off >= 2 && off <= 3 && Math.abs(z + 0.5 - r.centerZ()) <= 4.5;
        } else {
            int k = Math.floorDiv(z - r.z0(), 6);
            if (Hash.unit(s, k, 9) < 0.5) return false;
            int off = Math.floorMod(z - r.z0(), 6);
            return off >= 2 && off <= 3 && Math.abs(x + 0.5 - r.centerX()) <= 4.5;
        }
    }

    private static boolean caveInside(Room r, int x, int z) {
        int edge = Math.min(Math.min(x - r.x0(), r.x1() - x), Math.min(z - r.z0(), r.z1() - z));
        if (onDoorAxis(r, x, z, 3)) return true;
        double n = Hash.fbm(r.seed, x, z, 11);
        if (edge < (int) (n * 9) - 1) return false;
        return true;
    }

    /** A solid column inside the room: pillars, ring cores, cave columns. */
    public static boolean solid(Room r, int x, int z) {
        if (!inRect(r, x, z)) return false;
        switch (r.shape) {
            case PILLARED -> {
                if (onDoorAxis(r, x, z, 2)) return false;
                return Math.floorMod(x - r.x0(), 4) == 2 && Math.floorMod(z - r.z0(), 4) == 2;
            }
            case RING -> {
                double u = u(r, x), v = v(r, z);
                double core = (r.gw >= 2 && r.gh >= 2) ? 0.22 : 0.17;
                return Math.abs(u) <= core && Math.abs(v) <= core;
            }
            case CAVE -> {
                if (onDoorAxis(r, x, z, 3)) return false;
                return Hash.fbm(r.seed ^ 0x77L, x, z, 7) > 0.75;
            }
            default -> {
                return false;
            }
        }
    }

    /** Offset of the walking surface relative to the room's dy (dais +1, pit ledge -1, pit bottom -2, cave bumps). */
    public static int floorOffset(Room r, int x, int z) {
        if (!inRect(r, x, z)) return 0;
        if (r.piece != null) {
            int d = Pieces.floorDelta(r, x, z);
            if (d != Pieces.KEEP) return d;
        }
        switch (r.shape) {
            case TERRACE -> {
                double u = Math.abs(u(r, x)), v = Math.abs(v(r, z));
                return (u <= 0.42 && v <= 0.42) ? 1 : 0;
            }
            case PIT -> {
                if (onDoorAxis(r, x, z, 1)) return 0;
                double u = Math.abs(u(r, x)), v = Math.abs(v(r, z));
                if (u <= 0.4 && v <= 0.4) return -2;
                if (u <= 0.58 && v <= 0.58) return -1;
                return 0;
            }
            case CAVE -> {
                if (onDoorAxis(r, x, z, 3)) return 0;
                double n = Hash.fbm(r.seed ^ 0x3131L, x, z, 9);
                return (int) Math.round(n * 3) - 1;
            }
            default -> {
                return 0;
            }
        }
    }

    /** Ceiling height above the room's dy at this column. */
    public static int ceiling(Room r, int x, int z) {
        int h = r.height;
        if (r.shape == Room.Shape.SHAFT) return h;
        if (r.shape == Room.Shape.CAVE) {
            double n = Hash.fbm(r.seed ^ 0x9191L, x, z, 8);
            return Math.max(5, h - 3 + (int) Math.round(n * 6));
        }
        if (r.area() >= 4) {
            double u = u(r, x), v = v(r, z);
            double d = Math.max(0, 1 - (u * u + v * v));
            // the Lords' arenas vault up to the top of the floor's band; other big rooms get a gentle dome
            int rise = r.piece != null && r.piece.arena ? 9 : 4;
            int dome = Math.min(rise, 27 - r.dy - h);
            if (dome > 0) h += (int) Math.round(d * dome);
        }
        if (r.piece != null) h = Math.max(4, Math.min(27 - r.dy, h + Pieces.ceilingDelta(r, x, z)));
        return h;
    }

    /** Sky Archipelago: is there island ground here (otherwise a drop to the cloud layer)? */
    public static boolean island(Room r, int x, int z) {
        if (onDoorAxis(r, x, z, 1)) return true;
        if (r.piece == SetPiece.FLOATING_ISLES) return Pieces.isle(r, x, z);
        if (r.piece == SetPiece.ARENA_SKY) return PiecesArenas.skyIsland(r, x, z);
        double n = Hash.fbm(r.seed ^ 0x5151L, x, z, 9);
        double u = u(r, x), v = v(r, z);
        return n - (u * u + v * v) * 0.18 > 0.42;
    }

    /** Distance (in blocks, capped at 3) from this interior column to the nearest non-interior column. */
    public static int edgeDistance(Room r, int x, int z) {
        for (int d = 1; d <= 3; d++) {
            if (!open(r, x + d, z) || !open(r, x - d, z) || !open(r, x, z + d) || !open(r, x, z - d)) return d - 1;
        }
        return 3;
    }

    private static boolean open(Room r, int x, int z) {
        return (inside(r, x, z) && !blocked(r, x, z)) || tunnel(r, x, z) != null;
    }

    /** A good column for a monster or an item to appear on: open, level, clear of walls and decor rings. */
    public static boolean spawnable(Room r, int x, int z) {
        if (!inside(r, x, z) || blocked(r, x, z) || floorOffset(r, x, z) != 0) return false;
        if (r.shape == Room.Shape.ISLANDS && !island(r, x, z)) return false;
        if (r.shape == Room.Shape.SHAFT) return false;
        return edgeDistance(r, x, z) >= 2;
    }
}
