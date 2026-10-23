package dev.awaken.gates.world;

import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.block.GateBlocks;
import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.content.Modifier;
import dev.awaken.gates.content.Palette;
import dev.awaken.gates.content.Theme;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Turns a {@link GateLayout} into blocks, one chunk at a time. Pure: the same layout and chunk always produce the same
 * blocks, so it can run on any worldgen thread and in tests (through {@link Sink}).
 */
public final class GatePainter {
    private GatePainter() {}

    /** Receives blocks for one chunk: x/z are chunk-local (0..15), y absolute. Air is never written. */
    public interface Sink {
        void set(int x, int y, int z, BlockState state);
    }

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    /** Paints every floor band that covers this chunk. */
    public static void paint(GateLayout layout, int cell, int chunkX, int chunkZ, Sink out) {
        int lcx = chunkX * 16 - Cells.originX(cell), lcz = chunkZ * 16 - Cells.originZ(cell);
        if (lcx < 0 || lcz < 0 || lcx >= Cells.SIZE || lcz >= Cells.SIZE) return;
        int slotX = lcx >> 4, slotZ = lcz >> 4;
        for (FloorLayout f : layout.main) if (f.coversSlot(slotX, slotZ, 1)) paintFloor(layout.plan, f, slotX, slotZ, out);
        for (FloorLayout f : layout.annex) if (f.coversSlot(slotX, slotZ, 1)) paintFloor(layout.plan, f, slotX, slotZ, out);
    }

    static void paintFloor(GatePlan plan, FloorLayout f, int slotX, int slotZ, Sink out) {
        int gx = slotX - f.originSlotX, gz = slotZ - f.originSlotZ;
        Room r = f.roomAtSlot(gx, gz);
        Palette p = f.theme.palette();
        int yLo = f.baseY - Cells.BAND_BELOW, yHi = f.baseY + Cells.BAND_ABOVE - 1;
        if (r == null) {
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = yLo; y <= yHi; y++) out.set(x, y, z, p.fill());
            return;
        }
        Ctx c = new Ctx(plan, f, r, p, out, gx, gz, yLo, yHi);
        for (int bx = 0; bx < 16; bx++) for (int bz = 0; bz < 16; bz++) c.column(bx, bz);
        c.features();
    }

    /** Per-room painting state (one per chunk and floor; never shared between threads). */
    private static final class Ctx {
        final GatePlan plan;
        final FloorLayout f;
        final Room r;
        final Palette p;
        final Sink out;
        final int gx, gz, yLo, yHi, floorY;
        final boolean lightless;
        final Theme theme;
        final Room.Door[] stairDoor = new Room.Door[1];
        final boolean hangFull;

        Ctx(GatePlan plan, FloorLayout f, Room r, Palette p, Sink out, int gx, int gz, int yLo, int yHi) {
            this.plan = plan;
            this.f = f;
            this.r = r;
            this.p = p;
            this.out = out;
            this.gx = gx;
            this.gz = gz;
            this.yLo = yLo;
            this.yHi = yHi;
            this.floorY = f.baseY + r.dy;
            this.lightless = plan.has(Modifier.LIGHTLESS) && r.type != Room.Type.SHRINE;
            this.theme = f.theme;
            this.hangFull = p.hang().canOcclude();
        }

        void set(int bx, int y, int bz, BlockState s) {
            if (y < yLo || y > yHi || s.isAir()) return;
            out.set(bx, y, bz, s);
        }

        void solidColumn(int bx, int bz, int from, int to) {
            for (int y = Math.max(yLo, from); y <= Math.min(yHi, to); y++) set(bx, y, bz, p.fill());
        }

        boolean open(int x, int z) {
            if (RoomGeometry.tunnel(r, x, z) != null) return true;
            if (RoomGeometry.stairStep(r, x, z, null) > 0) return true;
            return RoomGeometry.inside(r, x, z) && !RoomGeometry.solid(r, x, z);
        }

        boolean doorFrame(int x, int z) {
            for (Room.Door d : r.doors) {
                int dep = RoomGeometry.depth(d, x, z);
                int lat = RoomGeometry.lateral(d, x, z);
                if (dep >= 0 && dep < RoomGeometry.TUNNEL && (lat == RoomGeometry.DOOR_LO - 1 || lat == RoomGeometry.DOOR_HI + 1)
                        && x >= d.gx * 16 && x < d.gx * 16 + 16 && z >= d.gz * 16 && z < d.gz * 16 + 16) return true;
            }
            return false;
        }

        long h(int x, int z, int salt) {
            return Hash.of(r.seed, x, z, salt);
        }

        double u(int x, int z, int salt) {
            return Hash.unit(h(x, z, salt));
        }

        // ---- columns ------------------------------------------------------------------------------------------------

        void column(int bx, int bz) {
            int x = gx * 16 + bx, z = gz * 16 + bz;
            Room.Door door = RoomGeometry.tunnel(r, x, z);
            if (door != null) {
                doorColumn(bx, bz, x, z, door);
                return;
            }
            int step = r.shape == Room.Shape.SHAFT ? 0 : RoomGeometry.stairStep(r, x, z, stairDoor);
            if (step > 0) {
                stairColumn(bx, bz, x, z, step, stairDoor[0]);
                return;
            }
            boolean in = RoomGeometry.inside(r, x, z);
            if (in && RoomGeometry.solid(r, x, z)) {
                pillarColumn(bx, bz, x, z);
                return;
            }
            if (in) {
                switch (r.shape) {
                    case SHAFT -> shaftColumn(bx, bz, x, z);
                    case ISLANDS -> islandColumn(bx, bz, x, z);
                    default -> roomColumn(bx, bz, x, z);
                }
                return;
            }
            wallColumn(bx, bz, x, z);
        }

        void doorColumn(int bx, int bz, int x, int z, Room.Door d) {
            int walk = f.baseY + d.y;
            int top = walk + RoomGeometry.DOOR_HEIGHT;
            solidColumn(bx, bz, yLo, walk - 2);
            set(bx, walk - 1, bz, p.floor());
            boolean bars = d.sealed && d.ownsBars && RoomGeometry.barsPlane(d, x, z);
            for (int y = walk; y < top; y++) if (bars) set(bx, y, bz, GatesRegistry.BARS.get().defaultBlockState());
            set(bx, top, bz, p.wallAlt());
            solidColumn(bx, bz, top + 1, yHi);
            // a floor that continues the room's own floor where the tunnel meets the interior
            if (r.shape == Room.Shape.SHAFT && RoomGeometry.depth(d, x, z) >= RoomGeometry.INSET) {
                // shaft platforms continue below in shaftColumn; nothing else here
            }
        }

        void stairColumn(int bx, int bz, int x, int z, int step, Room.Door d) {
            int top = floorY + step - 1;
            solidColumn(bx, bz, yLo, top - 1);
            BlockState stair = GatePainter.facing(p.stair(), d.side);
            set(bx, top, bz, stair);
            int ceil = floorY + RoomGeometry.ceiling(r, x, z);
            set(bx, ceil, bz, p.ceiling());
            solidColumn(bx, bz, ceil + 1, yHi);
        }

        void pillarColumn(int bx, int bz, int x, int z) {
            int ceil = floorY + RoomGeometry.ceiling(r, x, z);
            solidColumn(bx, bz, yLo, floorY - 2);
            set(bx, floorY - 1, bz, p.floor());
            boolean core = r.shape == Room.Shape.RING;
            for (int y = floorY; y < ceil; y++) {
                BlockState s = core ? p.wallAlt() : p.pillar();
                if (!lightless && y == floorY + 2 && (core ? edgeOfCore(x, z) && (x + z) % 3 == 0 : Math.floorMod(x + z, 8) == 2)) s = p.light();
                set(bx, y, bz, s);
            }
            set(bx, ceil, bz, p.ceiling());
            solidColumn(bx, bz, ceil + 1, yHi);
        }

        boolean edgeOfCore(int x, int z) {
            return !RoomGeometry.solid(r, x + 1, z) || !RoomGeometry.solid(r, x - 1, z)
                    || !RoomGeometry.solid(r, x, z + 1) || !RoomGeometry.solid(r, x, z - 1);
        }

        void wallColumn(int bx, int bz, int x, int z) {
            boolean face = open(x + 1, z) || open(x - 1, z) || open(x, z + 1) || open(x, z - 1);
            if (!face) {
                solidColumn(bx, bz, yLo, yHi);
                return;
            }
            boolean frame = doorFrame(x, z);
            int ceilTop = floorY + r.height + 4;
            for (int y = yLo; y <= yHi; y++) {
                BlockState s = p.fill();
                if (y >= floorY - 1 && y <= ceilTop) {
                    s = p.wall();
                    int rel = y - floorY;
                    if (frame && rel >= 0 && rel <= RoomGeometry.DOOR_HEIGHT) s = p.pillar();
                    else if (rel == 0) s = p.wallAlt();
                    else if (rel == 4 && r.height >= 7) s = p.wallAlt();
                    else if (!lightless && rel == 2 && Math.floorMod(x * 31 + z * 17, r.dark ? 13 : 7) == 0) s = p.light();
                    else s = themeWall(x, z, rel, s);
                }
                set(bx, y, bz, s);
            }
        }

        BlockState themeWall(int x, int z, int rel, BlockState s) {
            switch (theme) {
                case BELL_CRYPT -> {
                    if (rel == 1 && Math.floorMod(x + z, 3) == 0) return GatePainter.bone();
                    if (rel == 2 && Math.floorMod(x + z, 6) == 3) return p.wallAlt();
                }
                case DROWNED_ARCHIVE -> {
                    if (rel >= 1 && rel <= 3) return p.wallAlt();
                }
                case HIVE -> {
                    if (Math.floorMod(x + 2 * z + rel * 3, 5) == 0) return p.wallAlt();
                }
                case MIRROR_PALACE -> {
                    if (Math.floorMod(x + z, 4) == 0) return p.wallAlt();
                }
                case PRECURSOR_LAB -> {
                    if (rel == 2 || rel == 6) return p.wallAlt();
                }
                case OVERGROWN_TEMPLE -> {
                    if (rel <= 2 && u(x, z, rel + 40) < 0.35) return Blocks.MOSS_BLOCK.defaultBlockState();
                }
                case NIGHTMARE -> {
                    if (u(x, z, rel + 50) < 0.08) return p.clutter().get((int) Math.floorMod(h(x, z, rel), (long) p.clutter().size()));
                }
                case EMBER_MINES -> {
                    if (rel >= 1 && rel <= 3 && u(x, z, rel + 60) < 0.07) return p.clutter().get((int) Math.floorMod(h(x, z, rel + 61), 2L));
                }
                default -> {}
            }
            return s;
        }

        BlockState floorBlock(int x, int z, int edge) {
            int cx = r.centerX(), cz = r.centerZ();
            switch (theme) {
                case NIGHTMARE -> {
                    double n = Hash.noise(r.seed, x, z, 5);
                    return n < 0.3 ? p.floor() : n < 0.55 ? p.floorAlt() : n < 0.75 ? GatePainter.state("minecraft:white_wool") : p.wall();
                }
                case FUNGAL_HOLLOW, OVERGROWN_TEMPLE, ABYSSAL_SHRINE -> {
                    if (Hash.noise(r.seed, x, z, 4) < 0.35) return p.floorAlt();
                }
                default -> {}
            }
            boolean feature = r.type == Room.Type.SHRINE || r.type == Room.Type.START || r.type == Room.Type.BOSS || r.type == Room.Type.HUB;
            if (feature && Math.abs(x - cx) + Math.abs(z - cz) <= (r.type == Room.Type.BOSS ? 4 : 2)) return p.floorAlt();
            if ((r.type == Room.Type.ARENA || r.type == Room.Type.BOSS) && Math.floorMod((x >> 1) + (z >> 1), 2) == 0) return p.floorAlt();
            if (edge == 0 && r.area() > 1) return p.floorAlt();
            if (theme == Theme.CLOCKWORK_FOUNDRY && Math.floorMod(x, 6) == 0) return GatePainter.state("minecraft:waxed_copper_grate");
            return p.floor();
        }

        void roomColumn(int bx, int bz, int x, int z) {
            int fo = RoomGeometry.floorOffset(r, x, z);
            int walk = floorY + fo;
            int ceil = floorY + RoomGeometry.ceiling(r, x, z);
            int edge = RoomGeometry.edgeDistance(r, x, z);
            solidColumn(bx, bz, yLo, walk - 2);
            if (r.shape == Room.Shape.PIT && fo == -2 && !p.liquid().isAir()) {
                set(bx, walk - 1, bz, p.floorAlt());
                set(bx, walk, bz, p.liquid());
            } else if (r.shape == Room.Shape.TERRACE && fo == 1 && edgeOfDais(x, z)) {
                set(bx, walk - 2, bz, p.fill());
                set(bx, walk - 1, bz, GatePainter.facing(p.stair(), daisFacing(x, z)));
            } else {
                set(bx, walk - 1, bz, fo < 0 ? p.floorAlt() : floorBlock(x, z, edge));
            }
            set(bx, ceil, bz, lightCeiling(x, z) ? p.light() : p.ceiling());
            solidColumn(bx, bz, ceil + 1, yHi);
            if (fo == 0) decorate(bx, bz, x, z, walk, ceil, edge);
            else if (fo == -2 && r.shape == Room.Shape.PIT && theme == Theme.CLOCKWORK_FOUNDRY) set(bx, walk, bz, AIR);
        }

        boolean edgeOfDais(int x, int z) {
            return RoomGeometry.floorOffset(r, x + 1, z) == 0 || RoomGeometry.floorOffset(r, x - 1, z) == 0
                    || RoomGeometry.floorOffset(r, x, z + 1) == 0 || RoomGeometry.floorOffset(r, x, z - 1) == 0;
        }

        int daisFacing(int x, int z) {
            // stairs climb toward the dais centre: the high side faces inward
            int dx = r.centerX() - x, dz = r.centerZ() - z;
            if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? 1 : 3;
            return dz > 0 ? 2 : 0;
        }

        boolean lightCeiling(int x, int z) {
            if (lightless || !hangFull) return false;
            int sp = r.dark ? 9 : 5;
            return Math.floorMod(x - r.x0(), sp) == sp / 2 && Math.floorMod(z - r.z0(), sp) == sp / 2;
        }

        boolean lightSpot(int x, int z) {
            int sp = r.dark ? 9 : theme == Theme.LANTERN_NIGHT_MARKET ? 4 : 5;
            return Math.floorMod(x - r.x0(), sp) == sp / 2 && Math.floorMod(z - r.z0(), sp) == sp / 2;
        }

        void decorate(int bx, int bz, int x, int z, int walk, int ceil, int edge) {
            int headroom = ceil - walk;
            boolean axis = RoomGeometry.onDoorAxis(r, x, z, 1);
            boolean nearFeature = false;
            for (Room.Feature ft : r.features) if (Math.abs(ft.x() - x) <= 1 && Math.abs(ft.z() - z) <= 1) nearFeature = true;
            // hanging lights
            if (!lightless && !hangFull && lightSpot(x, z) && headroom >= 4) {
                if (headroom >= 10) {
                    set(bx, ceil - 1, bz, GatePainter.chain());
                    set(bx, ceil - 2, bz, GatePainter.chain());
                    set(bx, ceil - 3, bz, p.hang());
                } else set(bx, ceil - 1, bz, p.hang());
                return;
            }
            if (lightless && r.type == Room.Type.SHRINE && x == r.centerX() && z == r.centerZ() + 2) {
                set(bx, walk, bz, GatePainter.state("minecraft:candle[candles=4,lit=true]"));
                return;
            }
            if (nearFeature) return;
            // carpet runners on the main walks
            if (axis && !p.carpet().isAir() && (r.type == Room.Type.NORMAL || r.type == Room.Type.HALL || r.type == Room.Type.ANTE
                    || r.type == Room.Type.START || r.type == Room.Type.KEY)) {
                set(bx, walk, bz, p.carpet());
                return;
            }
            if (axis) return;
            double roll = u(x, z, 1);
            if (edge == 0) {
                if (theme == Theme.LANTERN_NIGHT_MARKET && headroom >= 5) {
                    // stalls: striped awnings over crates along the walls
                    set(bx, walk + 3, bz, Math.floorMod(x + z, 2) == 0 ? p.clutter().get(0) : p.clutter().get(1));
                    if (roll < 0.4) set(bx, walk, bz, p.clutter().get(2 + (int) Math.floorMod(h(x, z, 2), 2L)));
                    return;
                }
                if (theme == Theme.EMBER_MINES && isCorner(x, z)) {
                    for (int y = walk; y < ceil; y++) set(bx, y, bz, p.pillar());
                    return;
                }
                if (theme == Theme.DROWNED_ARCHIVE && headroom >= 5 && roll < 0.45) {
                    set(bx, walk, bz, p.wallAlt());
                    set(bx, walk + 1, bz, p.wallAlt());
                    if (roll < 0.2) set(bx, walk + 2, bz, p.wallAlt());
                    return;
                }
                if (roll < 0.05) {
                    BlockState cl = pick(p.clutter(), x, z, 3);
                    set(bx, walk, bz, cl);
                    if (u(x, z, 4) < 0.35 && headroom > 4) set(bx, walk + 1, bz, cl);
                    return;
                }
                if (roll < 0.21) {
                    set(bx, walk, bz, pick(p.floorDecor(), x, z, 5));
                    return;
                }
                if (theme == Theme.BELL_CRYPT && u(x, z, 6) < 0.3 && headroom >= 4) set(bx, ceil - 1, bz, GatePainter.state("minecraft:cobweb"));
            }
            if (theme == Theme.EMBER_MINES && headroom >= 5 && Math.floorMod(x - r.x0(), 5) == 0) {
                set(bx, ceil - 1, bz, p.ceiling());
                return;
            }
            if (headroom >= 5 && u(x, z, 7) < 0.045) {
                set(bx, ceil - 1, bz, pick(p.hangDecor(), x, z, 8));
                return;
            }
            switch (theme) {
                case FROST_MONASTERY -> { if (roll > 0.72) set(bx, walk, bz, GatePainter.state("minecraft:snow[layers=1]")); }
                case FUNGAL_HOLLOW -> { if (roll > 0.94) set(bx, walk, bz, pick(p.floorDecor(), x, z, 9)); }
                case OVERGROWN_TEMPLE -> { if (roll > 0.8) set(bx, walk, bz, p.carpet()); }
                case ABYSSAL_SHRINE -> { if (roll > 0.985) set(bx, walk, bz, p.floorDecor().get(0)); }
                case NIGHTMARE -> {
                    if (roll > 0.992 && headroom >= 6) set(bx, walk + 2 + (int) Math.floorMod(h(x, z, 10), 3L), bz, pick(p.clutter(), x, z, 11));
                }
                default -> {}
            }
        }

        boolean isCorner(int x, int z) {
            return (x == r.x0() || x == r.x1()) && (z == r.z0() || z == r.z1());
        }

        BlockState pick(List<BlockState> list, int x, int z, int salt) {
            return list.get((int) Math.floorMod(h(x, z, salt), (long) list.size()));
        }

        void shaftColumn(int bx, int bz, int x, int z) {
            int ceil = floorY + r.height;
            double uu = (x - r.centerX() + 0.5) / (r.gw * 8.0 - RoomGeometry.INSET);
            double vv = (z - r.centerZ() + 0.5) / (r.gh * 8.0 - RoomGeometry.INSET);
            boolean pool = Math.abs(uu) <= 0.5 && Math.abs(vv) <= 0.5;
            if (pool) {
                solidColumn(bx, bz, yLo, floorY - 5);
                set(bx, floorY - 4, bz, p.floorAlt());
                for (int y = floorY - 3; y < floorY; y++) set(bx, y, bz, Blocks.WATER.defaultBlockState());
            } else {
                solidColumn(bx, bz, yLo, floorY - 2);
                set(bx, floorY - 1, bz, p.floor());
            }
            set(bx, ceil, bz, p.ceiling());
            solidColumn(bx, bz, ceil + 1, yHi);
            // a platform in front of every balcony door, with steps down for the low ones
            for (Room.Door d : r.doors) {
                int lat = RoomGeometry.lateral(d, x, z);
                int dep = RoomGeometry.depth(d, x, z);
                boolean inSlot = x >= d.gx * 16 && x < d.gx * 16 + 16 && z >= d.gz * 16 && z < d.gz * 16 + 16;
                if (!inSlot || lat < RoomGeometry.DOOR_LO - 1 || lat > RoomGeometry.DOOR_HI + 1) continue;
                int py = f.baseY + d.y - 1;
                if (d.y > 0 && dep >= RoomGeometry.INSET && dep <= RoomGeometry.INSET + 2) {
                    set(bx, py, bz, p.floor());
                    if (dep == RoomGeometry.INSET + 2 && (lat == RoomGeometry.DOOR_LO - 1 || lat == RoomGeometry.DOOR_HI + 1) && !lightless)
                        set(bx, py + 1, bz, p.hang().canOcclude() ? p.light() : GatePainter.state("minecraft:lantern"));
                    set(bx, py - 1, bz, p.wallAlt());
                }
                if (d.y > 0 && d.y <= 6 && lat >= RoomGeometry.DOOR_LO && lat <= RoomGeometry.DOOR_HI) {
                    int k = dep - (RoomGeometry.INSET + 3);
                    int top = py - 1 - k;
                    if (k >= 0 && top >= floorY) {
                        solidColumn(bx, bz, floorY, top - 1);
                        set(bx, top, bz, GatePainter.facing(p.stair(), d.side));
                    }
                }
            }
        }

        void islandColumn(int bx, int bz, int x, int z) {
            int cloud = floorY - 4;
            int ceil = floorY + RoomGeometry.ceiling(r, x, z);
            solidColumn(bx, bz, yLo, cloud - 1);
            set(bx, cloud, bz, p.wall());
            set(bx, ceil, bz, lightCeiling(x, z) ? p.light() : p.ceiling());
            solidColumn(bx, bz, ceil + 1, yHi);
            if (!RoomGeometry.island(r, x, z)) return;
            set(bx, floorY - 2, bz, p.floorAlt());
            set(bx, floorY - 1, bz, p.floor());
            double roll = u(x, z, 12);
            boolean axis = RoomGeometry.onDoorAxis(r, x, z, 1);
            if (!lightless && lightSpot(x, z) && ceil - floorY >= 4) set(bx, ceil - 1, bz, p.hang());
            else if (!axis && roll < 0.22) set(bx, floorY, bz, pick(p.floorDecor(), x, z, 13));
            else if (!axis && roll > 0.993 && ceil - floorY >= 7) {
                for (int y = floorY; y < floorY + 3; y++) set(bx, y, bz, p.pillar());
                set(bx, floorY + 3, bz, p.clutter().get(0));
            }
        }

        // ---- features -----------------------------------------------------------------------------------------------

        void features() {
            int x0 = gx * 16, z0 = gz * 16;
            for (Room.Feature ft : r.features) {
                if (ft.kind() == Room.FeatureKind.INNER_GATE) innerGate(ft, x0, z0);
                if (ft.kind() == Room.FeatureKind.RIFT_WALL) riftWall(ft, x0, z0);
                if (ft.kind() == Room.FeatureKind.VAULT_CACHE) {
                    for (int[] b : FeatureBlocks.vaultBars(ft)) put(b[0], floorY + b[1], b[2], x0, z0, GatesRegistry.BARS.get().defaultBlockState());
                }
                int bx = ft.x() - x0, bz = ft.z() - z0;
                if (bx < 0 || bx > 15 || bz < 0 || bz > 15) continue;
                int y = floorY + ft.y();
                switch (ft.kind()) {
                    case SHRINE -> set(bx, y, bz, GatesRegistry.SHRINE.get().defaultBlockState());
                    case SEAL -> set(bx, y, bz, GatesRegistry.SEAL.get().defaultBlockState());
                    case CACHE, VAULT_CACHE -> set(bx, y, bz, GatesRegistry.CACHE.get().defaultBlockState());
                    case RUNE -> {
                        int n = r.features(Room.FeatureKind.RUNE).size();
                        set(bx, y, bz, GatesRegistry.RUNE.get().defaultBlockState().setValue(GateBlocks.Rune.LIT, Puzzles.initiallyLit(r, n, ft.index())));
                    }
                    case ENTRANCE -> set(bx, y - 1, bz, GatesRegistry.ENTRANCE.get().defaultBlockState());
                    case LANTERN -> set(bx, y, bz, GatesRegistry.LANTERN.get().defaultBlockState());
                    case UPDRAFT -> set(bx, floorY - 4, bz, GatesRegistry.UPDRAFT.get().defaultBlockState());
                    default -> {}
                }
            }
        }

        void put(int x, int y, int z, int x0, int z0, BlockState s) {
            int bx = x - x0, bz = z - z0;
            if (bx < 0 || bx > 15 || bz < 0 || bz > 15) return;
            set(bx, y, bz, s);
        }

        void innerGate(Room.Feature ft, int x0, int z0) {
            BlockState frame = GatesRegistry.FRAME.get().defaultBlockState();
            BlockState core = GatesRegistry.INNER.get().defaultBlockState();
            int y = floorY + ft.y();
            for (int[] b : FeatureBlocks.innerGateFrame(ft)) put(b[0], y + b[1], b[2], x0, z0, frame);
            for (int[] b : FeatureBlocks.innerGatePortal(ft)) put(b[0], y + b[1], b[2], x0, z0, core);
        }

        void riftWall(Room.Feature ft, int x0, int z0) {
            BlockState cracked = GatesRegistry.FRAME_CRACKED.get().defaultBlockState();
            for (int[] b : FeatureBlocks.riftWall(r, ft)) put(b[0], floorY + b[1], b[2], x0, z0, cracked);
        }
    }

    // ---- shared helpers ---------------------------------------------------------------------------------------------

    private static volatile BlockState chain, bone;

    static BlockState chain() {
        BlockState c = chain;
        if (c == null) chain = c = state("minecraft:iron_chain[axis=y]");
        return c;
    }

    static BlockState bone() {
        BlockState b = bone;
        if (b == null) bone = b = state("minecraft:bone_block[axis=x]");
        return b;
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, BlockState> STATES = new java.util.concurrent.ConcurrentHashMap<>();

    static BlockState state(String spec) {
        return STATES.computeIfAbsent(spec, dev.awaken.gates.content.Bs::of);
    }

    /** A stairs state whose high side faces the given door side (0 N, 1 E, 2 S, 3 W). */
    static BlockState facing(BlockState stair, int side) {
        String dir = switch (side) {
            case 0 -> "north";
            case 1 -> "east";
            case 2 -> "south";
            default -> "west";
        };
        return dev.awaken.gates.content.Bs.set(stair, "facing", dir);
    }
}
