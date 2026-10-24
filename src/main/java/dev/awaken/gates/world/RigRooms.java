package dev.awaken.gates.world;

import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.block.RigBlocks;
import dev.awaken.gates.content.Palette;
import dev.awaken.gates.content.Theme;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The Gate Rig's rooms (SPEC §17 "Gate Rig", content version 4): which room of a floor gets an archetype, and the
 * hooks the painter and the room geometry call for it. A rig room owns its interior columns: {@link #column} paints them
 * whole (pits, causeways, chasms, pedestals, the ceiling over the wells), {@link #stamp} sets the blocks it keeps in
 * the walls (the lens, the seal). Everything is a pure function of the room, like {@link Pieces}.
 */
public final class RigRooms {
    private RigRooms() {}

    /** The archetypes a theme's floors can roll (none: the theme keeps its rooms as they were). */
    public static List<RigRoom.Kind> kinds(Theme t) {
        return switch (t) {
            case EMBER_MINES, GLASS_DESERT, BELL_CRYPT -> List.of(RigRoom.Kind.COLLAPSE_RUN);
            case MIRROR_PALACE -> List.of(RigRoom.Kind.MIRROR_HALL);
            case PRECURSOR_LAB -> List.of(RigRoom.Kind.MIRROR_HALL, RigRoom.Kind.GRAVITY_WELLS);
            case SKY_ARCHIPELAGO, NIGHTMARE -> List.of(RigRoom.Kind.GRAVITY_WELLS);
            default -> List.of();
        };
    }

    // ---- choosing the room (FloorBuilder, content version 4) --------------------------------------------------------

    /**
     * One rig room per ordinary floor of a theme that has archetypes: a room on the critical path (never the start or
     * the end), the biggest that fits, ties broken by the room's seed. Choices come from per-room hashes, never from the
     * builder's random stream, so every other room is exactly what version 3 built.
     */
    static void assign(FloorBuilder.Builder b) {
        List<RigRoom.Kind> kinds = kinds(b.theme);
        if (kinds.isEmpty() || b.bossFloor) return;
        RigRoom.Kind kind = kinds.get(Hash.range(b.seed, 0x51C, b.floor, kinds.size()));
        List<Integer> path = b.path();
        List<Room> candidates = new ArrayList<>();
        for (int i = 1; i + 1 < path.size(); i++) {
            Room r = b.rooms.get(path.get(i));
            if (!eligible(r)) continue;
            r.pathIn = r.doorTo(path.get(i - 1));
            r.pathOut = r.doorTo(path.get(i + 1));
            candidates.add(r);
        }
        candidates.sort((x, y) -> x.area() != y.area() ? Integer.compare(y.area(), x.area())
                : Long.compare(Hash.of(x.seed, 0x51D, 1), Hash.of(y.seed, 0x51D, 1)));
        Palette p = b.theme.palette();
        Kit k = Kit.of(b.theme);
        for (Room r : candidates) {
            Room.Shape shape = r.shape;
            SetPiece piece = r.piece;
            r.shape = Room.Shape.RECT;
            r.piece = null;
            RigRoom rig = switch (kind) {
                case COLLAPSE_RUN -> RigCollapse.build(r, b.theme, p, k, b.rank);
                case MIRROR_HALL -> RigMirrors.build(r, p, k, b.rank);
                case GRAVITY_WELLS -> RigWells.build(r, b.theme, p, b.rank);
            };
            if (rig != null) {
                r.rig = rig;
                return;
            }
            r.shape = shape;
            r.piece = piece;
        }
    }

    private static boolean eligible(Room r) {
        if (r.type != Room.Type.NORMAL && r.type != Room.Type.ELITE) return false;
        if (r.gw > 2 || r.gh > 2 || r.shape == Room.Shape.SHAFT || r.shape == Room.Shape.CAVE || r.shape == Room.Shape.HALL) return false;
        for (Room.Door d : r.doors) if (d.y != r.dy) return false;
        return true;
    }

    /**
     * After the room's monsters and features are planned: a Collapse Run is the challenge itself (no monsters on the
     * causeway), rig rooms keep no stray cache, the Mirror Hall bars its way on and hides its cache in the pavilion.
     */
    static void finish(Room r) {
        RigRoom rig = r.rig;
        if (rig == null) return;
        r.features.removeIf(f -> f.kind() == Room.FeatureKind.CACHE);
        switch (rig.kind) {
            case COLLAPSE_RUN -> r.normals = r.elites = r.wave2Normals = r.wave2Elites = 0;
            case MIRROR_HALL -> {
                r.pathOut.sealed = true;
                r.pathOut.ownsBars = true;
                RigRoom.Box c = rig.mirrors.chamber();
                if (c != null) r.features.add(new Room.Feature(Room.FeatureKind.CACHE, (c.x0() + c.x1()) / 2, 0, (c.z0() + c.z1()) / 2, 0));
            }
            case GRAVITY_WELLS -> {}
        }
    }

    // ---- geometry hooks (RoomGeometry) ------------------------------------------------------------------------------

    /** Walking surface relative to the room's floor, or {@link Pieces#KEEP}: pits and chasms are open to the band's bottom. */
    public static int floorDelta(Room r, int x, int z) {
        RigRoom rig = r.rig;
        return switch (rig.kind) {
            case COLLAPSE_RUN -> {
                byte k = rig.run.kindAt(x, z);
                yield k == RigCollapse.PIT || k == RigCollapse.GAP ? RigCollapse.PIT_DY + 1 : 0;
            }
            case GRAVITY_WELLS -> rig.wells.chasmAt(rig.wells.along(x, z)) >= 0 ? RigWells.PIT_DY + 1 : 0;
            case MIRROR_HALL -> Pieces.KEEP;
        };
    }

    /** An obstacle on this column: a mirror's pedestal or the pavilion. */
    public static boolean blocks(Room r, int x, int z) {
        RigRoom rig = r.rig;
        return rig.kind == RigRoom.Kind.MIRROR_HALL && (rig.mirrors.mirrorAt(x, z) != null || rig.mirrors.pavilion(x, z));
    }

    /** The wells walk on a flat ceiling; the other rooms keep the painter's. */
    public static int ceiling(Room r, int h) {
        return r.rig.kind == RigRoom.Kind.GRAVITY_WELLS ? r.height : h;
    }

    // ---- painting (GatePainter) -------------------------------------------------------------------------------------

    /** Paints an interior column of a rig room whole. Returns false to leave it to the painter. */
    static boolean column(GatePainter.Ctx c, int bx, int bz, int x, int z) {
        RigRoom rig = c.r.rig;
        int floorY = c.floorY;
        int ceil = floorY + RoomGeometry.ceiling(c.r, x, z);
        switch (rig.kind) {
            case COLLAPSE_RUN -> {
                RigCollapse.Run run = rig.run;
                c.solidColumn(bx, bz, c.yLo, floorY + RigCollapse.PIT_DY - 1);
                c.set(bx, floorY + RigCollapse.PIT_DY, bz, run.pit());
                BlockState tile = run.tileAt(x, z);
                if (tile != null) c.set(bx, floorY - 1, bz, tile);
                c.set(bx, ceil, bz, c.ceilingBlock(x, z, ceil));
            }
            case GRAVITY_WELLS -> {
                RigWells.Field f = rig.wells;
                int a = f.along(x, z);
                if (f.chasmAt(a) >= 0) {
                    c.solidColumn(bx, bz, c.yLo, floorY + RigWells.PIT_DY - 1);
                    c.set(bx, floorY + RigWells.PIT_DY, bz, f.pit());
                } else {
                    c.solidColumn(bx, bz, c.yLo, floorY - 2);
                    c.set(bx, floorY - 1, bz, f.rim(a) ? rim() : c.floorBlock(x, z, RoomGeometry.edgeDistance(c.r, x, z)));
                }
                // over a well the ceiling is a floor you can walk, its rims lit like the ones below
                BlockState top = f.wellAt(a) < 0 ? c.ceilingBlock(x, z, ceil) : f.rim(a) ? rim() : ((x + z) & 1) == 0 ? c.p.floor() : c.k.band();
                c.set(bx, ceil, bz, top);
            }
            case MIRROR_HALL -> {
                RigMirrors.Puzzle pz = rig.mirrors;
                c.solidColumn(bx, bz, c.yLo, floorY - 2);
                c.set(bx, floorY - 1, bz, c.floorBlock(x, z, RoomGeometry.edgeDistance(c.r, x, z)));
                c.set(bx, ceil, bz, c.ceilingBlock(x, z, ceil));
                RigMirrors.Mirror m = pz.mirrorAt(x, z);
                if (m != null) {
                    c.set(bx, floorY, bz, c.k.trim());
                    c.set(bx, floorY + RigMirrors.BEAM_DY, bz, mirror(m.start()));
                } else if (pz.pavilion(x, z)) pavilion(c, bx, bz, x, z, pz);
            }
        }
        c.solidColumn(bx, bz, ceil + 1, c.yHi);
        if (!(rig.kind == RigRoom.Kind.MIRROR_HALL && rig.mirrors.pavilion(x, z))) c.fill(bx, bz, x, z, floorY, ceil - floorY);
        return true;
    }

    private static void pavilion(GatePainter.Ctx c, int bx, int bz, int x, int z, RigMirrors.Puzzle pz) {
        BlockState mirror = GatesRegistry.MIRROR.get().defaultBlockState(), cracked = GatesRegistry.MIRROR_CRACKED.get().defaultBlockState();
        for (int[] s : pz.crack()) if (s[0] == x && s[2] == z) c.set(bx, c.floorY + s[1], bz, cracked);
        for (int[] s : pz.shell()) if (s[0] == x && s[2] == z) c.set(bx, c.floorY + s[1], bz, s[1] == RigMirrors.CHAMBER_H ? c.p.wallAlt() : mirror);
        RigRoom.Box in = pz.chamber();
        if (x == (in.x0() + in.x1()) / 2 && z == (in.z0() + in.z1()) / 2 + 1) c.set(bx, c.floorY + RigMirrors.CHAMBER_H - 1, bz, GatePainter.state("minecraft:light[level=12]"));
    }

    /** The blocks a rig room keeps in its walls: the Mirror Hall's lens and its seal. Called after the chunk's columns. */
    static void stamp(GatePainter.Ctx c) {
        RigRoom rig = c.r.rig;
        if (rig == null || rig.kind != RigRoom.Kind.MIRROR_HALL) return;
        RigMirrors.Puzzle pz = rig.mirrors;
        int x0 = c.gx * 16, z0 = c.gz * 16, y = c.floorY + RigMirrors.BEAM_DY;
        c.put(pz.lensX(), y, pz.lensZ(), x0, z0, GatesRegistry.RIG_LENS.get().defaultBlockState()
                .setValue(RigBlocks.Lens.FACING, GatePainter.dir(pz.lensDir())));
        c.put(pz.sealX(), y, pz.sealZ(), x0, z0, rig.group(0).cells().getFirst().rest());
    }

    public static BlockState mirror(int turn) {
        return GatesRegistry.RIG_MIRROR.get().defaultBlockState().setValue(RigBlocks.Mirror.TURN, Math.floorMod(turn, 4));
    }

    static BlockState rim() {
        return GatesRegistry.WELL_RIM.get().defaultBlockState();
    }
}
