package dev.awaken.gates.world;

import dev.awaken.gates.GatesRegistry;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.BitSet;

/**
 * The Bell Crypt's wear (SPEC §38.14, stage 3), over the floors its own engines lay (the hall, the passages, the shaft and
 * the void; content version 7). The architecture stands as built and the years show on the flagstones: they sink half a
 * block in patches, and where a patch sinks deepest a wet hollow opens a block down, ringed by the half-sunk stones so it's
 * walked into and out of; rubble from the vault lies at the walls' feet where the place is wide (crumbling limestone in
 * the corners, limestone stairs tumbling out with slabs a block further, now and then a fallen piece of tracery);
 * limestone dust drifts against the walls; bone shards lie in patches; damp moss grows in and round the hollows; and a bell
 * lies toppled in the floor's biggest place. Doorways, handbells, touchstones and every small mark stay as built, and the
 * engine keeps its water, pits and basins clear ({@link Keep}). The sinking is decided by position alone, so a chunk's
 * carve never needs its neighbour's; what leans on a wall only sees the walls in its own chunk.
 */
final class CryptWear {
    /** Columns the engine keeps as built: water near, a pit, a basin, a secret place. */
    interface Keep {
        boolean keep(Room r, int x, int z);
    }

    private static final double SINK = 0.38, HOLLOW = 0.24;

    private final int W;
    private final long seed;
    private final Keep keep;
    private final BitSet pin = new BitSet(Plots.SIDE * Plots.SIDE);
    private final Room[] slots = new Room[256];
    private final BlockState floor, wet, tracery, flagSlab, limeSlab, limeStairs, crumbling, dust, bones, moss, bell, glow;
    /** The toppled bell's column, -1 when the floor has none. */
    private int bellX = -1, bellZ = -1;

    /** Made after the engine's carve, before its handbells are set ({@code p}'s hands and marks are all known by then). */
    CryptWear(WorldPlan p, int W, long seed, BlockState floor, BlockState wet, BlockState tracery, Keep keep) {
        this.W = W;
        this.seed = seed;
        this.keep = keep;
        this.floor = floor;
        this.wet = wet;
        this.tracery = tracery;
        flagSlab = GatesRegistry.theme("crypt_flagstone_slab").defaultBlockState();
        limeSlab = GatesRegistry.theme("crypt_limestone_slab").defaultBlockState();
        limeStairs = GatesRegistry.theme("crypt_limestone_stairs").defaultBlockState();
        crumbling = GatesRegistry.CRYPT_LIMESTONE_CRUMBLING.get().defaultBlockState();
        dust = GatesRegistry.theme("crypt_dust").defaultBlockState();
        bones = GatesRegistry.theme("crypt_bones").defaultBlockState();
        moss = GatePainter.state("minecraft:moss_carpet");
        bell = GatesRegistry.CRYPT_BELL_TOPPLED.get().defaultBlockState();
        glow = WorldMats.light(10);
        Room big = null;
        for (Room r : p.b.rooms) {
            for (int gx = r.gx; gx < r.gx + r.gw; gx++) for (int gz = r.gz; gz < r.gz + r.gh; gz++) slots[gx + gz * 16] = r;
            for (Room.Door d : r.doors) {
                // the threshold and a block past it, and round the doorway's middle
                int[] b = WorldPlan.doorBox(d, Sculpt.SHELL + 1);
                box(b[0] - 1, b[2] - 1, b[1] + 1, b[3] + 1);
                circle(d.centerX() + 0.5, d.centerZ() + 0.5, 3);
            }
            if (!skip(r) && (big == null || r.gw * r.gh > big.gw * big.gh)) big = r;
        }
        for (int[] h : p.handSpots()) circle(h[0] + 0.5, h[1] + 0.5, 3.5);
        // a bell, a secret, a prop, a touchstone and a basin stay as the engine left them (an event's wide box doesn't)
        for (FloorLayout.Mark m : p.marks) {
            boolean basin = m.kind().equals(FloorLayout.Mark.BASIN);
            if (!basin && (m.x1() - m.x0() + 1) * (m.z1() - m.z0() + 1) > 49) continue;
            int pad = basin ? 3 : 2;
            box(m.x0() - pad, m.z0() - pad, m.x1() + pad, m.z1() + pad);
        }
        if (big != null) {
            // off the middle line, toward a side where it won't stand in the way
            long h = Hash.of(seed + 79, big.gx, big.gz);
            int off = Math.min(big.gw * 8 - 4, 6);
            int x = big.centerX() + (Hash.unit(h) < 0.5 ? -off : off), z = big.centerZ() + (int) ((Hash.unit(Hash.mix(h)) - 0.5) * 6);
            if (sink(x, z) >= 0 && !pin.get(x + z * Plots.SIDE) && !keep.keep(big, x, z)) {
                bellX = x;
                bellZ = z;
            }
        }
    }

    private void box(int x0, int z0, int x1, int z1) {
        for (int x = Math.max(0, x0); x <= Math.min(Plots.SIDE - 1, x1); x++)
            for (int z = Math.max(0, z0); z <= Math.min(Plots.SIDE - 1, z1); z++) pin.set(x + z * Plots.SIDE);
    }

    private void circle(double cx, double cz, double r) {
        int ri = (int) Math.ceil(r);
        for (int x = (int) cx - ri; x <= (int) cx + ri; x++)
            for (int z = (int) cz - ri; z <= (int) cz + ri; z++)
                if (Math.hypot(x + 0.5 - cx, z + 0.5 - cz) < r && x >= 0 && z >= 0 && x < Plots.SIDE && z < Plots.SIDE) pin.set(x + z * Plots.SIDE);
    }

    /** A Rig room, the Lord's arena, a gate within and a built room are their own. */
    private static boolean skip(Room r) {
        return WorldPlan.built(r) || r.rig != null || r.type == Room.Type.BOSS || r.type == Room.Type.INNER_GATE;
    }

    private Room room(int x, int z) {
        if (x < 0 || z < 0 || x >= Plots.SIDE || z >= Plots.SIDE) return null;
        Room r = slots[(x >> 4) + (z >> 4) * 16];
        return r == null || skip(r) ? null : r;
    }

    /** -1 where nothing wears (pinned, kept, no place), 0 where the floor stays, 1 where it's sunk. */
    private int sink(int x, int z) {
        Room r = room(x, z);
        if (r == null || pin.get(x + z * Plots.SIDE) || keep.keep(r, x, z)) return -1;
        return Hash.noise(seed + 71, x + 0.5, z + 0.5, 5) < SINK ? 1 : 0;
    }

    /** How far the flagstone at (x, z) has sunk, in half blocks: 0, 1, or 2 for a wet hollow (every stone round it sunk too). */
    int depth(int x, int z) {
        if (sink(x, z) < 1) return 0;
        if (Hash.noise(seed + 71, x + 0.5, z + 0.5, 5) >= HOLLOW) return 1;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (sink(x + dx, z + dz) < 1) return 1;
        return 2;
    }

    /** Where the engine may stand a spawn or a feature: no stone sunk in the three by three round it, the bell well off. */
    boolean level(int x, int z) {
        if (bellX >= 0 && Math.hypot(x - bellX, z - bellZ) < 3) return false;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (depth(x + dx, z + dz) != 0) return false;
        return true;
    }

    /** Adds the wear to the carve: one pass per column over the plot, after everything the engine set. */
    void apply(Sculpt s) {
        s.op(0, W, 0, Plots.SIDE - 1, W, Plots.SIDE - 1, (b, x, y, z) -> column(b, x, z));
    }

    // ---- carving (worldgen threads) -------------------------------------------------------------------------------------

    private void column(Sculpt.Buf b, int x, int z) {
        Room r = room(x, z);
        if (r == null || pin.get(x + z * Plots.SIDE) || keep.keep(r, x, z)) return;
        int fl = W + r.dy;
        // the floor: a flagstone (or the floor's skin) with two clear over it, at the place's line or a little under it
        for (int y = fl + 1; y >= fl - 4; y--) {
            if (!b.open(x, y, z) || !b.open(x, y + 1, z) || !floorAt(b, x, y - 1, z)) continue;
            wear(b, x, y, z);
            return;
        }
    }

    private boolean floorAt(Sculpt.Buf b, int x, int y, int z) {
        if (b.get(x, y, z) == Sculpt.SOLID) return true;
        BlockState st = b.state(x, y, z);
        return st == floor || st == wet;
    }

    /** One column's wear, {@code y} the cell you stand in over its flagstone. */
    private void wear(Sculpt.Buf b, int x, int y, int z) {
        int d = depth(x, z);
        long h = Hash.of(seed + 75, x, z);
        if (d == 2 && b.get(x, y - 2, z) != Sculpt.OPEN) {
            // a wet hollow a block down, moss on its floor here and there; a block lower than the floor's lights reach
            // as well, so a hidden light lies in every other stone of it
            b.set(x, y - 2, z, wet);
            if (Hash.unit(h) < 0.35) b.set(x, y - 1, z, moss);
            else if (((x + z) & 1) == 0) b.set(x, y - 1, z, glow);
            else b.mark(x, y - 1, z, Sculpt.OPEN);
            return;
        }
        if (d >= 1) {
            b.set(x, y - 1, z, flagSlab);
            return;
        }
        if (x == bellX && z == bellZ) {
            b.set(x, y, z, bell.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,
                    Direction.from2DDataValue((int) (Hash.unit(h) * 4))));
            return;
        }
        if (bellX >= 0 && Math.abs(x - bellX) <= 1 && Math.abs(z - bellZ) <= 1) {
            // what fell with it
            double u = Hash.unit(Hash.mix(h + 3));
            if (u < 0.45) b.set(x, y, z, limeSlab);
            else if (u < 0.8) b.set(x, y, z, layers(dust, 1 + (int) (Hash.unit(Hash.mix(h + 5)) * 3)));
            return;
        }
        // the walls beside it, in this chunk (the buffer can't see past it)
        Direction wall = null, other = null;
        int walls = 0;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            int nx = x + dir.getStepX(), nz = z + dir.getStepZ();
            if (!inChunk(b, nx, nz) || !solid(b, nx, y, nz) || !solid(b, nx, y + 1, nz)) continue;
            walls++;
            if (wall == null) wall = dir;
            else other = dir;
        }
        double rub = Hash.noise(seed + 73, x + 0.5, z + 0.5, 6);
        if (wall != null && rub > 0.6) {
            // rubble only where the place is wide: three clear out from every wall it leans on
            boolean wide = roomy(b, x, y, z, wall.getOpposite()) && (other == null || roomy(b, x, y, z, other.getOpposite()));
            if (wide && walls == 2 && other.getAxis() != wall.getAxis()) {
                b.set(x, y, z, crumbling);
                return;
            }
            if (wide && walls == 1) {
                if (Hash.unit(h) < 0.12) b.set(x, y, z, tracery);
                else b.set(x, y, z, limeStairs.setValue(StairBlock.FACING, wall));
                return;
            }
        }
        if (wall == null && rub > 0.66) {
            // the rubble's tail, a block out from the wall's foot
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                int nx = x + 2 * dir.getStepX(), nz = z + 2 * dir.getStepZ();
                if (inChunk(b, nx, nz) && solid(b, nx, y, nz) && solid(b, nx, y + 1, nz) && roomy(b, x, y, z, dir.getOpposite())) {
                    b.set(x, y, z, limeSlab);
                    return;
                }
            }
        }
        if (wall != null && Hash.unit(h) < 0.5) {
            b.set(x, y, z, layers(dust, 1 + (int) (Hash.unit(Hash.mix(h)) * 3)));
            return;
        }
        double patch = 0.25 + 2.5 * Relief.smooth(0.45, 0.72, Hash.noise(seed + 77, x + 0.5, z + 0.5, 4));
        if (Hash.unit(Hash.mix(h + 7)) < 0.12 * patch) {
            b.set(x, y, z, bones);
            return;
        }
        if (Hash.unit(Hash.mix(h + 9)) < 0.4 && hollowBeside(x, z)) b.set(x, y, z, moss);
    }

    private boolean hollowBeside(int x, int z) {
        for (Direction dir : Direction.Plane.HORIZONTAL) if (depth(x + dir.getStepX(), z + dir.getStepZ()) > 0) return true;
        return false;
    }

    /** Three clear blocks of floor out from (x, z) toward {@code dir}, in this chunk. */
    private static boolean roomy(Sculpt.Buf b, int x, int y, int z, Direction dir) {
        for (int k = 1; k <= 3; k++) {
            int nx = x + k * dir.getStepX(), nz = z + k * dir.getStepZ();
            if (!inChunk(b, nx, nz) || !b.open(nx, y, nz) || !b.open(nx, y + 1, nz)) return false;
        }
        return true;
    }

    private static boolean inChunk(Sculpt.Buf b, int x, int z) {
        return x >= b.x0 && x < b.x0 + 16 && z >= b.z0 && z < b.z0 + 16;
    }

    private static boolean solid(Sculpt.Buf b, int x, int y, int z) {
        if (b.get(x, y, z) == Sculpt.SOLID) return true;
        BlockState st = b.state(x, y, z);
        return st != null && st.isSolidRender();
    }

    private static BlockState layers(BlockState drift, int n) {
        return drift.trySetValue(BlockStateProperties.LAYERS, n);
    }
}
