package dev.awaken.gates.world;

import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.block.GateBlocks;
import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.content.Modifier;
import dev.awaken.gates.content.Palette;
import dev.awaken.gates.content.Theme;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Turns a {@link GateLayout} into blocks, one chunk at a time. Pure: the same layout and chunk always produce the same
 * blocks, so it can run on any worldgen thread and in tests (through {@link Sink}).
 *
 * <p>The art direction (docs/modules/gates.md "Interiors"): every wall has a rhythm (plinth, pilasters at slot offsets
 * 2, 5, 10 and 13, recessed panels of the theme's signature material between them, lintels, a cornice under the
 * ceiling); floors have bands in line with the pilasters and a medallion under each chandelier; light comes from
 * fixtures (chandeliers, niche lights, sconces) so it pools with darker gaps between; the way on is lit (guide lights
 * in the frame of every onward door, the System's blue round the Rest Shrine) and a runner on the floor joins the doors
 * of the critical path. Set pieces and the Lords' arenas are painted by {@link Pieces}.
 */
public final class GatePainter {
    private GatePainter() {}

    /** Receives blocks for one chunk: x/z are chunk-local (0..15), y absolute. Air is never written. */
    public interface Sink {
        void set(int x, int y, int z, BlockState state);
    }

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    static final int[] DX = {0, 1, 0, -1}, DZ = {-1, 0, 1, 0};
    /** Pilasters stand at these slot offsets along every wall (door frames are the ones at 5 and 10). */
    static boolean pilasterOffset(int o) {
        return o == 2 || o == 5 || o == 10 || o == 13;
    }

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
    static final class Ctx {
        final GatePlan plan;
        final FloorLayout f;
        final Room r;
        final Palette p;
        final Kit k;
        final Sink out;
        final int gx, gz, yLo, yHi, floorY;
        final boolean lightless, dim;
        /** The room's fill light (an invisible light block on an 8-block grid just above head height), or null. */
        final BlockState fillLight;
        final Theme theme;
        final Room.Door[] stairDoor = new Room.Door[1];
        /** open() for the chunk and a 3-block margin, computed once: walls look at their neighbours a lot. */
        private final byte[] openGrid = new byte[22 * 22];

        Ctx(GatePlan plan, FloorLayout f, Room r, Palette p, Sink out, int gx, int gz, int yLo, int yHi) {
            this.plan = plan;
            this.f = f;
            this.r = r;
            this.p = p;
            this.k = Kit.of(f.theme);
            this.out = out;
            this.gx = gx;
            this.gz = gz;
            this.yLo = yLo;
            this.yHi = yHi;
            this.floorY = f.baseY + r.dy;
            this.lightless = plan.has(Modifier.LIGHTLESS) && r.type != Room.Type.SHRINE;
            this.theme = f.theme;
            this.dim = r.dark || k.has(Kit.DIM);
            int fill = r.type == Room.Type.SHRINE ? Math.max(10, fillLevel(theme)) : r.type == Room.Type.BOSS ? 15 : fillLevel(theme);
            this.fillLight = lightless || fill <= 0 ? null : state("minecraft:light[level=" + fill + "]");
        }

        void set(int bx, int y, int bz, BlockState s) {
            if (y < yLo || y > yHi || s.isAir()) return;
            out.set(bx, y, bz, s);
        }

        void solidColumn(int bx, int bz, int from, int to) {
            for (int y = Math.max(yLo, from); y <= Math.min(yHi, to); y++) set(bx, y, bz, p.fill());
        }

        /** Walkable or carved: the room's open interior, its door tunnels and stairs. Solid pillars are not open. */
        boolean open(int x, int z) {
            int ix = x - gx * 16 + 3, iz = z - gz * 16 + 3;
            if (ix >= 0 && iz >= 0 && ix < 22 && iz < 22) {
                int i = ix + iz * 22;
                byte v = openGrid[i];
                if (v == 0) openGrid[i] = v = (byte) (computeOpen(x, z) ? 2 : 1);
                return v == 2;
            }
            return computeOpen(x, z);
        }

        private boolean computeOpen(int x, int z) {
            if (RoomGeometry.tunnel(r, x, z) != null) return true;
            if (RoomGeometry.stairStep(r, x, z, null) > 0) return true;
            return RoomGeometry.inside(r, x, z) && !RoomGeometry.solid(r, x, z);
        }

        int openDirs(int x, int z) {
            int m = 0;
            for (int d = 0; d < 4; d++) if (open(x + DX[d], z + DZ[d])) m |= 1 << d;
            return m;
        }

        /** The door whose frame (the pilasters either side of the doorway, in the wall) this column is, or null. */
        Room.Door frameDoor(int x, int z) {
            for (Room.Door d : r.doors) {
                if (x < d.gx * 16 || x >= d.gx * 16 + 16 || z < d.gz * 16 || z >= d.gz * 16 + 16) continue;
                int dep = RoomGeometry.depth(d, x, z);
                int lat = RoomGeometry.lateral(d, x, z);
                if (dep >= 0 && dep < RoomGeometry.TUNNEL && (lat == RoomGeometry.DOOR_LO - 1 || lat == RoomGeometry.DOOR_HI + 1)) return d;
            }
            return null;
        }

        long h(int x, int z, int salt) {
            return Hash.of(r.seed, x, z, salt);
        }

        double u(int x, int z, int salt) {
            return Hash.unit(h(x, z, salt));
        }

        /** Room interior centre in block-edge coordinates (a 12-wide room's centre lies between two blocks). */
        double cxf() {
            return (r.x0() + r.x1() + 1) / 2.0;
        }

        double czf() {
            return (r.z0() + r.z1() + 1) / 2.0;
        }

        // ---- columns ------------------------------------------------------------------------------------------------

        void column(int bx, int bz) {
            int x = gx * 16 + bx, z = gz * 16 + bz;
            Room.Door door = RoomGeometry.tunnel(r, x, z);
            boolean in = RoomGeometry.inside(r, x, z);
            if (door != null && !(in && door.y == r.dy && RoomGeometry.depth(door, x, z) >= RoomGeometry.INSET
                    && r.shape != Room.Shape.SHAFT && r.shape != Room.Shape.ISLANDS && !RoomGeometry.solid(r, x, z))) {
                // through the wall: the doorway itself. Past the wall line the room's own floor and ceiling take over.
                doorColumn(bx, bz, x, z, door);
                return;
            }
            int step = r.shape == Room.Shape.SHAFT ? 0 : RoomGeometry.stairStep(r, x, z, stairDoor);
            if (step > 0) {
                stairColumn(bx, bz, x, z, step, stairDoor[0]);
                return;
            }
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
            int dep = RoomGeometry.depth(d, x, z);
            boolean face = dep == RoomGeometry.INSET - 1;
            if (face && walk - 1 > floorY) {
                // the lower room's side of a raised doorway: wall below the threshold, not raw rock
                solidColumn(bx, bz, yLo, floorY - 2);
                for (int y = floorY - 1; y < walk - 1; y++) set(bx, y, bz, y == floorY ? k.trim() : p.wall());
            } else solidColumn(bx, bz, yLo, walk - 2);
            set(bx, walk - 1, bz, p.floor());
            boolean bars = d.sealed && d.ownsBars && RoomGeometry.barsPlane(d, x, z);
            for (int y = walk; y < top; y++) if (bars) set(bx, y, bz, GatesRegistry.BARS.get().defaultBlockState());
            int lat = RoomGeometry.lateral(d, x, z);
            BlockState lintel = p.wallAlt();
            if (face) {
                lintel = k.has(Kit.INSETS) ? k.capital() : p.wallAlt();
                BlockState g = guideFor(d);
                if (g != null && !lightless && (lat == 7 || lat == 8 || d.toEnd)) lintel = g;
            }
            set(bx, top, bz, lintel);
            if (face) {
                // the wall over the doorway, dressed like the rest of the room's face
                int hRel = r.height;
                for (int y = top + 1; y <= yHi; y++) {
                    int rel = y - floorY;
                    BlockState s = rel > hRel + 1 ? p.fill() : k.has(Kit.SKY) ? GatesRegistry.SKY.get().defaultBlockState()
                            : rel == hRel - 1 && k.has(Kit.INSETS) ? cornice((d.side + 2) % 4) : p.wall();
                    set(bx, y, bz, s);
                }
            } else solidColumn(bx, bz, top + 1, yHi);
        }

        /** The light in the frame of a door that leads on: the System's blue into (and inside) the Shrine, the theme's guide light otherwise. */
        BlockState guideFor(Room.Door d) {
            if (r.type == Room.Type.SHRINE) return GatesRegistry.SIGNAL_LAMP.get().defaultBlockState();
            if (!d.onward) return null;
            Room to = f.room(d.toRoom);
            if (d.toEnd && to != null && to.type == Room.Type.SHRINE) return GatesRegistry.SIGNAL_LAMP.get().defaultBlockState();
            return k.guide();
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
            boolean cave = r.shape == Room.Shape.CAVE;
            int h = ceil - floorY;
            int lintel = lintelRel(h);
            for (int y = floorY; y < ceil; y++) {
                int rel = y - floorY;
                BlockState s;
                if (cave) s = rel < 2 || rel > h - 3 ? p.fill() : p.wall();
                else if (core) s = rel == 0 ? k.trim() : p.wallAlt();
                else s = rel == 0 ? k.trim() : (rel == lintel || rel == h - 1) && k.has(Kit.INSETS) ? k.capital() : k.pilaster();
                boolean lamp = !lightless && rel == 3 && (core ? edgeOfCore(x, z) && Math.floorMod(x + z, 3) == 0
                        : !cave && Math.floorMod(x + z, dim ? 16 : 8) == 2);
                if (lamp) s = core ? p.light() : k.sconce();
                set(bx, y, bz, s);
            }
            set(bx, ceil, bz, p.ceiling());
            solidColumn(bx, bz, ceil + 1, yHi);
        }

        boolean edgeOfCore(int x, int z) {
            return !RoomGeometry.solid(r, x + 1, z) || !RoomGeometry.solid(r, x - 1, z)
                    || !RoomGeometry.solid(r, x, z + 1) || !RoomGeometry.solid(r, x, z - 1);
        }

        // ---- walls --------------------------------------------------------------------------------------------------

        /** The row (above the floor) of the lintel over a wall's panels, for a wall this tall. */
        static int lintelRel(int h) {
            return h >= 10 ? 6 : h >= 8 ? 5 : 4;
        }

        /** Height (above the room floor) of the ceiling seen from a wall column whose open side is d. */
        int wallH(int x, int z, int d) {
            int nx = x + DX[d], nz = z + DZ[d];
            Room.Door t = RoomGeometry.tunnel(r, nx, nz);
            if (t != null && !RoomGeometry.inside(r, nx, nz)) return t.y - r.dy + RoomGeometry.DOOR_HEIGHT;
            return RoomGeometry.ceiling(r, nx, nz);
        }

        /** If this wall column is the face of a recessed panel, the direction of the room from it; otherwise -1. */
        int insetFace(int x, int z) {
            if (!k.has(Kit.INSETS) || open(x, z)) return -1;
            int od = openDirs(x, z);
            if (Integer.bitCount(od) != 1) return -1;
            int d = Integer.numberOfTrailingZeros(od);
            boolean alongX = d == 0 || d == 2;
            if (pilasterOffset(Math.floorMod(alongX ? x : z, 16))) return -1;
            if (frameDoor(x, z) != null) return -1;
            int ax = alongX ? 1 : 0, az = 1 - ax;
            if (open(x + ax, z + az) || open(x - ax, z - az)) return -1;
            if (openDirs(x + ax, z + az) != od || openDirs(x - ax, z - az) != od) return -1;
            // a doorway's tunnel next door is not a wall to recess into
            if (RoomGeometry.tunnel(r, x + DX[d], z + DZ[d]) != null && !RoomGeometry.inside(r, x + DX[d], z + DZ[d])) return -1;
            if (wallH(x, z, d) < 6) return -1;
            if (r.piece != null && Pieces.wallClaim(r, x, z, d)) return -1;
            return d;
        }

        /** Big panels are four wide (slot offsets 6..9 and 14..1); small ones two (3..4, 11..12). */
        static boolean bigPanel(int along) {
            int o = Math.floorMod(along, 16);
            return (o >= 6 && o <= 9) || o >= 14 || o <= 1;
        }

        static boolean panelCentre(int along) {
            int o = Math.floorMod(along, 16);
            return o == 7 || o == 8 || o == 15 || o == 0;
        }

        void wallColumn(int bx, int bz, int x, int z) {
            int od = openDirs(x, z);
            if (od == 0) {
                for (int d = 0; d < 4; d++) {
                    int fx = x + DX[d], fz = z + DZ[d];
                    if (insetFace(fx, fz) == d) {
                        panelBack(bx, bz, x, z, fx, fz, d);
                        return;
                    }
                }
                solidColumn(bx, bz, yLo, yHi);
                return;
            }
            if (r.piece != null && Pieces.paintWall(this, bx, bz, x, z, od)) return;
            if (k.has(Kit.SKY)) {
                skyWall(bx, bz, x, z, od);
                return;
            }
            if (k.has(Kit.ORGANIC)) {
                organicWall(bx, bz, x, z, od);
                return;
            }
            int d = Integer.numberOfTrailingZeros(od);
            int h = wallH(x, z, d);
            int lintel = lintelRel(h);
            Room.Door fd = frameDoor(x, z);
            int inset = insetFace(x, z);
            boolean alongX = d == 0 || d == 2;
            int along = alongX ? x : z;
            boolean corner = Integer.bitCount(od) > 1;
            boolean pilaster = k.has(Kit.INSETS) && (fd != null || corner || pilasterOffset(Math.floorMod(along, 16)));
            int top = floorY + Math.max(h, r.height) + 2;
            for (int y = yLo; y <= yHi; y++) {
                if (y < floorY - 1 || y > top) {
                    set(bx, y, bz, p.fill());
                    continue;
                }
                int rel = y - floorY;
                BlockState s;
                if (rel < 0) s = p.wall();
                else if (fd != null) s = frame(fd, x, z, rel, h, lintel);
                else if (inset >= 0) s = insetFaceBlock(x, z, rel, h, lintel, along, inset);
                else if (pilaster) s = pilasterBlock(x, z, rel, h, lintel, along, corner);
                else if (k.has(Kit.INSETS)) s = rel == 0 ? k.trim() : rel == h - 1 ? cornice(d) : rel > h ? p.fill() : plainWall(x, z, rel);
                else s = legacyWall(x, z, rel, h);
                set(bx, y, bz, s);
            }
        }

        BlockState frame(Room.Door fd, int x, int z, int rel, int h, int lintel) {
            int doorRel = fd.y - r.dy;
            boolean face = RoomGeometry.depth(fd, x, z) == RoomGeometry.INSET - 1;
            if (rel == 0) return k.has(Kit.INSETS) ? k.trim() : p.pillar();
            // the last door before the floor's end (and the Shrine's own doors) is lit in its frame as well as its lintel
            if (!lightless && face && rel == doorRel + 2 && (fd.toEnd || r.type == Room.Type.SHRINE)) {
                BlockState g = guideFor(fd);
                if (g != null) return g;
            }
            if (rel <= doorRel + RoomGeometry.DOOR_HEIGHT) return k.has(Kit.INSETS) ? k.pilaster() : p.pillar();
            if (k.has(Kit.SKY)) return rel > h + 1 ? p.fill() : rel == doorRel + RoomGeometry.DOOR_HEIGHT + 1 ? k.capital() : GatesRegistry.SKY.get().defaultBlockState();
            if (!k.has(Kit.INSETS)) return rel > h + 1 ? p.fill() : p.wall();
            if (rel == h - 1 || rel == lintel) return k.capital();
            if (rel >= h) return rel > h + 1 ? p.fill() : p.wall();
            return k.pilaster();
        }

        BlockState pilasterBlock(int x, int z, int rel, int h, int lintel, int along, boolean corner) {
            if (rel == 0) return k.trim();
            if (rel >= h) return rel > h + 1 ? p.fill() : p.wall();
            if (rel == h - 1 || rel == lintel) return k.capital();
            int o = Math.floorMod(along, 16);
            return k.pilaster();
        }

        BlockState insetFaceBlock(int x, int z, int rel, int h, int lintel, int along, int d) {
            if (rel == 0) return k.trim();
            boolean big = bigPanel(along);
            boolean tier2 = big && h >= 12 && rel >= lintel + 2 && rel <= h - 3;
            if (rel < lintel || tier2) {
                // the niche: open, with its light
                if (!lightless && panelCentre(along) && nicheLit(along)) {
                    if (k.has(Kit.STAND_NICHE) ? rel == 1 : rel == lintel - 1) return k.nicheLight();
                    if (tier2 && rel == h - 3 && !k.has(Kit.STAND_NICHE)) return k.nicheLight();
                }
                if (r.piece != null) {
                    BlockState s = Pieces.niche(this, x, z, rel, d, big);
                    if (s != null) return s;
                }
                return AIR;
            }
            if (rel == lintel || (big && h >= 12 && rel == h - 2)) return k.capital();
            if (rel == h - 1) return cornice(d);
            if (rel >= h) return rel > h + 1 ? p.fill() : p.wall();
            return plainWall(x, z, rel);
        }

        boolean nicheLit(int along) {
            if (!dim) return true;
            return Math.floorMod(Math.floorDiv(along + 2, 8), 2) == 0;
        }

        void panelBack(int bx, int bz, int x, int z, int fx, int fz, int d) {
            int h = wallH(fx, fz, d);
            int lintel = lintelRel(h);
            int along = (d == 0 || d == 2) ? fx : fz;
            boolean big = bigPanel(along);
            BlockState panel = big ? k.panel() : k.panelAlt();
            if (panel.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING))
                panel = panel.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, dir(d));
            for (int y = yLo; y <= yHi; y++) {
                int rel = y - floorY;
                boolean niche = rel >= 1 && (rel < lintel || (big && h >= 12 && rel >= lintel + 2 && rel <= h - 3));
                set(bx, y, bz, niche ? panel : p.fill());
            }
        }

        /** The wall above and between the rhythm: the theme's wall with its own small variations. */
        BlockState plainWall(int x, int z, int rel) {
            if (theme == Theme.OVERGROWN_TEMPLE && rel <= 3 && u(x, z, rel + 40) < 0.3) return Blocks.MOSS_BLOCK.defaultBlockState();
            if (theme == Theme.EMBER_MINES && u(x, z, rel + 60) < 0.06) return k.panel();
            if (theme == Theme.NIGHTMARE && u(x, z, rel + 50) < 0.05) return pick(p.clutter(), x, z, rel);
            if (theme == Theme.HIVE && Math.floorMod(x + 2 * z + rel * 3, 5) == 0) return p.wallAlt();
            return p.wall();
        }

        /** Walls of themes without the pilaster rhythm: bands and the palette's own patterns (version 1's look). */
        BlockState legacyWall(int x, int z, int rel, int h) {
            if (rel > h + 1) return p.fill();
            if (rel == 0) return p.wallAlt();
            if (rel == 4 && r.height >= 7) return p.wallAlt();
            if (!lightless && rel == 2 && Math.floorMod(x * 31 + z * 17, r.dark ? 13 : 7) == 0) return p.light();
            return p.wall();
        }

        BlockState cornice(int d) {
            // upside-down stairs with their notch toward the wall: a moulding along the top of the wall
            BlockState s = withProp(k.cornice(), "half", "top");
            return GatePainter.facing(s, (d + 2) % 4);
        }

        private static BlockState withProp(BlockState s, String prop, String v) {
            return dev.awaken.gates.content.Bs.set(s, prop, v);
        }

        /** Living walls: soft rock in patches, glowing caps and shelf fungus, shallow alcoves. */
        void organicWall(int bx, int bz, int x, int z, int od) {
            int d = Integer.numberOfTrailingZeros(od);
            int h = wallH(x, z, d);
            int top = floorY + Math.max(h, r.height) + 2;
            double n = Hash.fbm(r.seed ^ 0xF00DL, x, z, 5);
            boolean alcove = n > 0.62 && Integer.bitCount(od) == 1 && h >= 6;
            for (int y = yLo; y <= yHi; y++) {
                int rel = y - floorY;
                if (y < floorY - 1 || y > top) {
                    set(bx, y, bz, p.fill());
                    continue;
                }
                if (alcove && rel >= 1 && rel <= 3) {
                    if (rel == 1 && u(x, z, 71) < 0.5) set(bx, y, bz, pick(p.floorDecor(), x, z, 72));
                    continue;
                }
                double m = Hash.noise(r.seed ^ 0xBEEFL, x * 1.0 + rel * 0.7, z * 1.0 - rel * 0.5, 3.5);
                BlockState s = rel <= 0 ? k.trim() : m < 0.3 ? k.panel() : m < 0.55 ? p.wall() : m < 0.7 ? k.panelAlt() : p.wallAlt();
                if (!lightless && rel >= 2 && rel <= h - 2 && u(x, z, rel + 90) < (dim ? 0.035 : 0.06)) s = k.medallion(); // a glowing cap in the wall
                set(bx, y, bz, s);
            }
        }

        /** Sky Archipelago: walls painted as open sky, clouds banked along the bottom. */
        void skyWall(int bx, int bz, int x, int z, int od) {
            int d = Integer.numberOfTrailingZeros(od);
            int h = wallH(x, z, d);
            int top = floorY + Math.max(h, r.height) + 2;
            BlockState sky = GatesRegistry.SKY.get().defaultBlockState(), high = sky;
            for (int y = yLo; y <= yHi; y++) {
                int rel = y - floorY;
                if (y > top) {
                    set(bx, y, bz, p.fill());
                    continue;
                }
                double c = Hash.fbm(r.seed ^ 0xC10DL, x + rel * 0.35, z - rel * 0.2, 6);
                int cloudTop = (int) Math.round(c * 5) - 3;
                BlockState s = rel <= cloudTop ? k.panel() : (c > 0.7 && rel > 4 && rel < h - 1) ? k.panel() : rel > h - 4 ? high : sky;
                set(bx, y, bz, s);
            }
        }

        // ---- floors and ceilings ------------------------------------------------------------------------------------

        BlockState floorBlock(int x, int z, int edge) {
            int cx = r.centerX(), cz = r.centerZ();
            if (r.type == Room.Type.SHRINE) {
                double dd = Math.hypot(x + 0.5 - cxf(), z + 0.5 - czf());
                if (Math.abs(dd - 5.0) < 0.55) return lightless ? p.floorAlt() : GatesRegistry.SIGNAL_LAMP.get().defaultBlockState();
                if (dd < 4.5) return p.floorAlt();
            }
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
            if (runnerInlay(x, z)) return k.runner();
            boolean feature = r.type == Room.Type.START || r.type == Room.Type.HUB;
            if (feature && Math.abs(x - cx) + Math.abs(z - cz) <= 2) return p.floorAlt();
            if (edge == 0 && r.area() > 1) return p.floorAlt();
            int ox = Math.floorMod(x, 16), oz = Math.floorMod(z, 16);
            if (k.has(Kit.ORGANIC) || k.has(Kit.SKY)) return p.floor();
            if (k.has(Kit.CHECKER)) {
                if ((ox == 7 || ox == 8) && (oz == 7 || oz == 8)) return k.medallion();
                return ((x >> 1) + (z >> 1)) % 2 == 0 ? p.floor() : k.band();
            }
            if ((ox == 7 || ox == 8) && (oz == 7 || oz == 8)) return k.medallion();
            if (ox == 5 || ox == 10 || oz == 5 || oz == 10) return k.band();
            if ((r.type == Room.Type.ARENA || r.type == Room.Type.HUB) && Math.floorMod((x >> 1) + (z >> 1), 2) == 0) return p.floorAlt();
            return p.floor();
        }

        /** The leading line: the axes of the doors you came in by and leave by, on the critical path. */
        boolean onRunner(int x, int z) {
            if (!r.onPath || k.runner().isAir() || r.piece != null && r.piece.arena) return false;
            if (r.type == Room.Type.SHRINE || r.type == Room.Type.BOSS) return false;
            if (r.pathIn != null && RoomGeometry.onAxisOf(r, r.pathIn, x, z, 0)) return true;
            return r.pathOut != null && RoomGeometry.onAxisOf(r, r.pathOut, x, z, 0);
        }

        boolean runnerInlay(int x, int z) {
            return !k.has(Kit.CARPET) && !k.has(Kit.RAILS) && onRunner(x, z);
        }

        BlockState ceilingBlock(int x, int z, int ceilY) {
            if (k.has(Kit.SKY)) {
                double c = Hash.fbm(r.seed ^ 0x5C1DL, x, z, 7);
                if (!lightless && sunSpot(x, z)) return k.chandelier();
                return c > 0.6 ? k.panel() : GatesRegistry.SKY.get().defaultBlockState();
            }
            if (r.type == Room.Type.SHRINE && !lightless) {
                int cx = r.centerX(), cz = r.centerZ();
                if ((x == cx || x == cx - 1) && (z == cz || z == cz - 1)) return GatesRegistry.SIGNAL_LAMP.get().defaultBlockState();
            }
            if (k.has(Kit.SKYLIGHT) && !lightless && skylight(x, z)) return skylightBlock();
            if (k.has(Kit.RIBS) && r.area() >= 4 && rib(x, z)) return k.beam();
            return p.ceiling();
        }

        boolean sunSpot(int x, int z) {
            int ox = Math.floorMod(x, 16), oz = Math.floorMod(z, 16);
            return (ox == 7 || ox == 8) && (oz == 7 || oz == 8) && Math.floorMod((x >> 4) + (z >> 4), 2) == 0;
        }

        boolean skylight(int x, int z) {
            int ox = Math.floorMod(x, 16), oz = Math.floorMod(z, 16);
            if (dim) return (ox == 7 || ox == 8) && (oz == 7 || oz == 8);
            return (ox >= 6 && ox <= 9) && (oz >= 6 && oz <= 9) && !((ox == 6 || ox == 9) && (oz == 6 || oz == 9));
        }

        BlockState skylightBlock() {
            return theme == Theme.GLASS_DESERT ? GatesRegistry.SANDGLASS.get().defaultBlockState() : k.chandelier();
        }

        boolean rib(int x, int z) {
            double dx = x + 0.5 - cxf(), dz = z + 0.5 - czf();
            return Math.abs(Math.abs(dx) - Math.abs(dz)) < 0.6 || Math.abs(dx) < 0.6 || Math.abs(dz) < 0.6;
        }

        void roomColumn(int bx, int bz, int x, int z) {
            int fo = RoomGeometry.floorOffset(r, x, z);
            int walk = floorY + fo;
            int ceil = floorY + RoomGeometry.ceiling(r, x, z);
            int edge = RoomGeometry.edgeDistance(r, x, z);
            solidColumn(bx, bz, yLo, walk - 2);
            if (r.shape == Room.Shape.PIT && fo == -2 && !p.liquid().isAir() && r.piece == null) {
                set(bx, walk - 1, bz, p.floorAlt());
                set(bx, walk, bz, p.liquid());
            } else if (r.shape == Room.Shape.TERRACE && fo == 1 && edgeOfDais(x, z) && r.piece == null) {
                set(bx, walk - 2, bz, p.fill());
                set(bx, walk - 1, bz, GatePainter.facing(p.stair(), daisFacing(x, z)));
            } else {
                set(bx, walk - 1, bz, fo < 0 ? p.floorAlt() : floorBlock(x, z, edge));
            }
            set(bx, ceil, bz, ceilingBlock(x, z, ceil));
            if (k.has(Kit.SKYLIGHT) && theme == Theme.GLASS_DESERT && !lightless && skylight(x, z)) {
                set(bx, ceil + 1, bz, k.sconce()); // the sun behind the amber glass
                solidColumn(bx, bz, ceil + 2, yHi);
            } else solidColumn(bx, bz, ceil + 1, yHi);
            if (r.piece != null && Pieces.paint(this, bx, bz, x, z, walk, ceil, fo)) {
                // an arena's own furniture doesn't get to darken its floor
                if (r.piece.arena && !RoomGeometry.blocked(r, x, z)) fill(bx, bz, x, z, walk, ceil - walk);
                return;
            }
            fill(bx, bz, x, z, walk, ceil - walk);
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

        // ---- light and furniture ------------------------------------------------------------------------------------

        /**
         * Where chandeliers hang: the room's centre, and each slot's centre in big rooms (not in dim rooms). Returns the
         * offset of (x, z) from the nearest chandelier as dx * 8 + dz packed, or -1 if it is not part of one.
         */
        int chandelierOffset(int x, int z) {
            int cx = r.centerX(), cz = r.centerZ();
            int best = chandelierPart(x - cx, z - cz);
            if (best >= 0 || r.dark || r.area() < 2) return best;
            int sx = Math.floorDiv(x, 16) * 16 + 8, sz = Math.floorDiv(z, 16) * 16 + 8;
            if (sx == cx && sz == cz) return -1;
            if (!RoomGeometry.inside(r, sx, sz) || RoomGeometry.blocked(r, sx, sz)) return -1;
            return chandelierPart(x - sx, z - sz);
        }

        private static int chandelierPart(int dx, int dz) {
            if (Math.abs(dx) + Math.abs(dz) > 2 || (dx != 0 && dz != 0)) return -1;
            return (dx + 2) * 8 + (dz + 2);
        }

        void chandelier(int bx, int bz, int part, int walk, int ceil) {
            int dx = part / 8 - 2, dz = part % 8 - 2;
            int head = ceil - walk;
            BlockState light = k.chandelier(), chain = k.chain();
            if (chain.isAir()) return;
            if (head >= 9) {
                int arm = ceil - 3;
                if (dx == 0 && dz == 0) {
                    for (int y = ceil - 1; y > arm; y--) set(bx, y, bz, chain);
                    set(bx, arm, bz, k.capital());
                    set(bx, arm - 1, bz, light);
                } else if (Math.abs(dx) + Math.abs(dz) == 1) {
                    set(bx, arm, bz, GatePainter.axis(chain, dx != 0 ? "x" : "z"));
                } else {
                    set(bx, arm, bz, k.trim());
                    set(bx, arm - 1, bz, light);
                }
            } else if (dx == 0 && dz == 0) {
                if (head >= 7) {
                    set(bx, ceil - 1, bz, chain);
                    set(bx, ceil - 2, bz, light);
                } else if (head >= 4) set(bx, ceil - 1, bz, light);
            }
        }

        void decorate(int bx, int bz, int x, int z, int walk, int ceil, int edge) {
            int headroom = ceil - walk;
            boolean nearFeature = false;
            for (Room.Feature ft : r.features) if (Math.abs(ft.x() - x) <= 1 && Math.abs(ft.z() - z) <= 1) nearFeature = true;
            if (lightless && r.type == Room.Type.SHRINE && x == r.centerX() && z == r.centerZ() + 2) {
                set(bx, walk, bz, GatePainter.state("minecraft:candle[candles=4,lit=true]"));
                return;
            }
            // ceiling beams resting on the cornice
            boolean beam = beamAt(x, z, walk, ceil);
            if (beam) {
                int by = floorY + r.height - 1;
                int ox = Math.floorMod(x, 16), oz = Math.floorMod(z, 16);
                boolean bxLine = ox == 5 || ox == 10, bzLine = oz == 5 || oz == 10;
                set(bx, by, bz, bxLine && bzLine ? k.capital() : GatePainter.axis(k.beam(), bxLine ? "z" : "x"));
            }
            if (!lightless && headroom >= 4) {
                int part = chandelierOffset(x, z);
                if (part >= 0 && !k.has(Kit.SKY)) chandelier(bx, bz, part, walk, ceil);
            }
            if (nearFeature) return;
            if (brazier(bx, bz, x, z, walk)) return;
            boolean axis = RoomGeometry.onDoorAxis(r, x, z, 1);
            if (onRunner(x, z)) {
                if (k.has(Kit.RAILS)) set(bx, walk, bz, rail(x, z));
                else if (k.has(Kit.CARPET)) set(bx, walk, bz, k.runner());
                return;
            }
            if (axis) return;
            double roll = u(x, z, 1);
            if (edge == 0) {
                if (theme == Theme.EMBER_MINES && isCorner(x, z)) {
                    for (int y = walk; y < ceil; y++) set(bx, y, bz, k.pilaster());
                    return;
                }
                if (roll < 0.04) {
                    BlockState cl = pick(p.clutter(), x, z, 3);
                    set(bx, walk, bz, cl);
                    if (u(x, z, 4) < 0.35 && headroom > 4) set(bx, walk + 1, bz, cl);
                    return;
                }
                if (roll < 0.14) {
                    set(bx, walk, bz, pick(p.floorDecor(), x, z, 5));
                    return;
                }
                if (theme == Theme.BELL_CRYPT && u(x, z, 6) < 0.3 && headroom >= 4) set(bx, ceil - 1, bz, GatePainter.state("minecraft:cobweb"));
            }
            if (headroom >= 6 && u(x, z, 7) < 0.025 && !beam) {
                set(bx, ceil - 1, bz, pick(p.hangDecor(), x, z, 8));
                return;
            }
            if (k.has(Kit.DRIPS) && headroom >= 6 && u(x, z, 17) < 0.03 && !beam) {
                int len = 1 + (int) Math.floorMod(h(x, z, 18), 2L);
                for (int i = 1; i <= len; i++) set(bx, ceil - i, bz, GatePainter.state("minecraft:honey_block"));
            }
            if (k.has(Kit.OVERGROWN) && headroom >= 5 && u(x, z, 19) < 0.05 && !beam) {
                set(bx, ceil - 1, bz, GatePainter.state("minecraft:hanging_roots"));
            }
            switch (theme) {
                case FROST_MONASTERY -> { if (roll > 0.8) set(bx, walk, bz, GatePainter.state("minecraft:snow[layers=1]")); }
                case FUNGAL_HOLLOW -> { if (roll > 0.94) set(bx, walk, bz, pick(p.floorDecor(), x, z, 9)); }
                case OVERGROWN_TEMPLE -> { if (roll > 0.85) set(bx, walk, bz, p.carpet()); }
                case ABYSSAL_SHRINE -> { if (roll > 0.985) set(bx, walk, bz, p.floorDecor().get(0)); }
                case NIGHTMARE -> {
                    if (roll > 0.992 && headroom >= 6) set(bx, walk + 2 + (int) Math.floorMod(h(x, z, 10), 3L), bz, pick(p.clutter(), x, z, 11));
                }
                default -> {}
            }
        }

        /**
         * Fill light: the game's gate dimension has no ambient light at all, so a room lit only by its fixtures reads
         * black between them at default brightness. An invisible light block on an 8-block grid, just above head
         * height, keeps the floor, the doors and every monster readable (median floor light 7+ on the critical path,
         * 8+ over most of a Lord's arena: {@code gates_interior_light}); the fixtures still make the pools.
         */
        void fill(int bx, int bz, int x, int z, int walk, int headroom) {
            if (fillLight == null || headroom < 4) return;
            int ox = Math.floorMod(x, 16), oz = Math.floorMod(z, 16);
            if ((ox == 4 || ox == 12) && (oz == 4 || oz == 12)) set(bx, walk + 2, bz, fillLight);
        }

        /**
         * The antechamber's braziers: a fire either side of the arena's doors, so the last room before the Lord says
         * where the Lord is. Soul fire in the cold themes.
         */
        boolean brazier(int bx, int bz, int x, int z, int walk) {
            if (r.type != Room.Type.ANTE || r.pathOut == null || lightless) return false;
            Room.Door d = r.pathOut;
            if (x < d.gx * 16 || x >= d.gx * 16 + 16 || z < d.gz * 16 || z >= d.gz * 16 + 16) return false;
            int lat = RoomGeometry.lateral(d, x, z);
            if (RoomGeometry.depth(d, x, z) != RoomGeometry.INSET || (lat != RoomGeometry.DOOR_LO - 2 && lat != RoomGeometry.DOOR_HI + 2)) return false;
            boolean soul = theme == Theme.BELL_CRYPT || theme == Theme.ABYSSAL_SHRINE || theme == Theme.FROST_MONASTERY || theme == Theme.NIGHTMARE;
            // soul fire never spreads; the warm themes burn a cauldron of lava, which can't set a wooden hall alight
            set(bx, walk, bz, k.capital());
            if (soul) {
                set(bx, walk + 1, bz, state("minecraft:soul_soil"));
                set(bx, walk + 2, bz, state("minecraft:soul_fire"));
            } else set(bx, walk + 1, bz, state("minecraft:lava_cauldron"));
            return true;
        }

        /** Beams cross flat-ceilinged rooms in line with the pilasters, just under the ceiling. */
        boolean beamAt(int x, int z, int walk, int ceil) {
            if (!k.has(Kit.BEAMS) || r.shape == Room.Shape.CAVE || r.height < 7) return false;
            int by = floorY + r.height - 1;
            if (by >= ceil || by - walk < 5) return false;
            int ox = Math.floorMod(x, 16), oz = Math.floorMod(z, 16);
            return ox == 5 || ox == 10 || oz == 5 || oz == 10;
        }

        BlockState rail(int x, int z) {
            boolean ns = false;
            for (Room.Door d : new Room.Door[]{r.pathIn, r.pathOut}) {
                if (d == null || !RoomGeometry.onAxisOf(r, d, x, z, 0)) continue;
                int dx = d.gx * 16 + 8, dz = d.gz * 16 + 8;
                boolean straight = (d.side == 0 || d.side == 2) ? Math.abs(x + 0.5 - dx) <= 0.5 : Math.abs(z + 0.5 - dz) > 0.5;
                ns = (d.side == 0 || d.side == 2) == straight;
                break;
            }
            return GatePainter.state(ns ? "minecraft:rail[shape=north_south]" : "minecraft:rail[shape=east_west]");
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
                if (!lightless && Math.floorMod(x + z, 4) == 0) set(bx, floorY - 4, bz, k.sconce()); // lights under the water
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
                    set(bx, py - 1, bz, k.has(Kit.INSETS) ? k.capital() : p.wallAlt());
                }
                if (d.y > 0 && d.y <= 6 && lat >= RoomGeometry.DOOR_LO && lat <= RoomGeometry.DOOR_HI) {
                    int kk = dep - (RoomGeometry.INSET + 3);
                    int top = py - 1 - kk;
                    if (kk >= 0 && top >= floorY) {
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
            set(bx, cloud, bz, k.has(Kit.SKY) ? k.panel() : p.wall());
            set(bx, ceil, bz, ceilingBlock(x, z, ceil));
            solidColumn(bx, bz, ceil + 1, yHi);
            if (!RoomGeometry.island(r, x, z)) {
                if (r.piece != null) Pieces.paint(this, bx, bz, x, z, cloud + 1, ceil, -3);
                fill(bx, bz, x, z, cloud + 1, ceil - cloud - 1); // whoever falls to the clouds can still see
                return;
            }
            // the island: grass over dirt, stone hanging underneath toward the clouds
            int depth = 1 + (int) Math.round(Hash.fbm(r.seed ^ 0x1515L, x, z, 4) * 2);
            for (int y = floorY - 1 - depth; y < floorY - 1; y++) if (y > cloud) set(bx, y, bz, y == floorY - 2 ? state("minecraft:dirt") : p.floorAlt());
            set(bx, floorY - 1, bz, p.floor());
            if (r.piece != null && Pieces.paint(this, bx, bz, x, z, floorY, ceil, 0)) return;
            fill(bx, bz, x, z, floorY, ceil - floorY);
            double roll = u(x, z, 12);
            boolean axis = RoomGeometry.onDoorAxis(r, x, z, 1);
            if (!axis && roll < 0.22) set(bx, floorY, bz, pick(p.floorDecor(), x, z, 13));
            else if (!axis && roll > 0.993 && ceil - floorY >= 7) {
                for (int y = floorY; y < floorY + 3; y++) set(bx, y, bz, k.pilaster());
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
            // one opening drawn as a single vortex: the bottom of the middle column is its anchor (its block entity is
            // added by the chunk generator)
            GateBlocks.Portal inner = (GateBlocks.Portal) GatesRegistry.INNER.get();
            Direction.Axis axis = ft.index() == 1 ? Direction.Axis.Z : Direction.Axis.X;
            int rank = plan.rank().ordinal();
            int y = floorY + ft.y();
            for (int[] b : FeatureBlocks.innerGateFrame(ft)) put(b[0], y + b[1], b[2], x0, z0, frame);
            for (int[] b : FeatureBlocks.innerGatePortal(ft)) {
                boolean anchor = b[1] == 0 && (axis == Direction.Axis.X ? b[0] == ft.x() : b[2] == ft.z());
                put(b[0], y + b[1], b[2], x0, z0, inner.opening(axis, rank, anchor));
            }
        }

        void riftWall(Room.Feature ft, int x0, int z0) {
            BlockState cracked = GatesRegistry.FRAME_CRACKED.get().defaultBlockState();
            for (int[] b : FeatureBlocks.riftWall(r, ft)) put(b[0], floorY + b[1], b[2], x0, z0, cracked);
        }
    }

    // ---- shared helpers ---------------------------------------------------------------------------------------------

    /**
     * Fill light per theme (0: the fixtures alone are enough). Dark themes keep their mood with a lower fill and dim
     * fixtures; bright themes need little. The Lords' arenas always fill at 15 (the Lord must read from the doors).
     */
    static int fillLevel(Theme t) {
        return switch (t) {
            // the themes that already looked right in game keep a light touch; open caverns and hubs need 13 to read
            case DROWNED_ARCHIVE, EMBER_MINES -> 12;
            case CLOCKWORK_FOUNDRY, HIVE, OVERGROWN_TEMPLE, LANTERN_NIGHT_MARKET, FROST_MONASTERY, MIRROR_PALACE, PRECURSOR_LAB,
                 FUNGAL_HOLLOW, GLASS_DESERT, SKY_ARCHIPELAGO, BELL_CRYPT -> 13;
            case ABYSSAL_SHRINE -> 14;
            case NIGHTMARE -> 15;
        };
    }

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

    /** A stairs (or any facing) state whose facing is the given door side (0 N, 1 E, 2 S, 3 W). */
    static BlockState facing(BlockState stair, int side) {
        return dev.awaken.gates.content.Bs.set(stair, "facing", dir(side).getSerializedName());
    }

    static net.minecraft.core.Direction dir(int side) {
        return switch (side) {
            case 0 -> net.minecraft.core.Direction.NORTH;
            case 1 -> net.minecraft.core.Direction.EAST;
            case 2 -> net.minecraft.core.Direction.SOUTH;
            default -> net.minecraft.core.Direction.WEST;
        };
    }

    /** Sets a log, pillar or chain's axis when it has one. */
    static BlockState axis(BlockState s, String axis) {
        return dev.awaken.gates.content.Bs.set(s, "axis", axis);
    }
}
