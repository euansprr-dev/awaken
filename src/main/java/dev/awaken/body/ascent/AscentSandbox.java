package dev.awaken.body.ascent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Set;

/**
 * Phase 0's feel sandbox ({@code /awaken ascent sandbox}): a lane of giant trees in front of you, trunks 26-38 tall
 * with branches to swing from, and a launch tower at its start for glides and dives. Plain blocks, persistent leaves,
 * built at once; the player lands on the tower facing down the lane.
 */
final class AscentSandbox {
    private AscentSandbox() {}

    static final int TREES = 14, SPACING = 17, TOWER = 46;

    static int build(ServerPlayer p) {
        ServerLevel level = p.level();
        Direction facing = p.getDirection();
        int fx = facing.getStepX(), fz = facing.getStepZ();
        int sx = -fz, sz = fx; // across the lane
        BlockPos o = p.blockPosition();
        RandomSource r = RandomSource.create(o.asLong());
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, o.getX(), o.getZ());
        // the launch tower: a 5x5 column and a 9x9 deck
        BlockState stone = Blocks.POLISHED_DEEPSLATE.defaultBlockState(), trim = Blocks.SEA_LANTERN.defaultBlockState();
        int top = ground + TOWER;
        for (int y = ground - 2; y <= top; y++) {
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                if (edge || y == top) set(level, o.getX() + dx, y, o.getZ() + dz, y % 8 == 0 && edge ? trim : stone);
            }
        }
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) set(level, o.getX() + dx, top, o.getZ() + dz, stone);
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            if (Math.abs(dx) == 4 || Math.abs(dz) == 4) {
                for (int y = top + 1; y <= top + 3; y++) set(level, o.getX() + dx, y, o.getZ() + dz, Blocks.AIR.defaultBlockState());
            }
        }
        for (int y = top + 1; y <= top + 4; y++) for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            set(level, o.getX() + dx, y, o.getZ() + dz, Blocks.AIR.defaultBlockState());
        }
        // an updraft pylon off the tower's side: glide off the deck, swing round into the column, ride it back up
        int vx = o.getX() + sx * 14 + fx * 8, vz = o.getZ() + sz * 14 + fz * 8;
        int vg = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, vx, vz);
        // a 3x3 pillar 13 high with the vent on top, so the column (34 up) reaches past the deck
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            for (int y = vg - 1; y <= vg + 13; y++) set(level, vx + dx, y, vz + dz, y % 6 == 0 && (dx != 0 || dz != 0) ? trim : stone);
        }
        set(level, vx, vg + 13, vz, AscentContent.UPDRAFT_VENT.get().defaultBlockState());
        // the grove: giant trees down the lane, alternating sides
        int n = 0;
        for (int i = 0; i < TREES; i++) {
            int along = 18 + i * SPACING + r.nextInt(5) - 2;
            int side = (i % 2 == 0 ? 1 : -1) * (6 + r.nextInt(5));
            int x = o.getX() + fx * along + sx * side, z = o.getZ() + fz * along + sz * side;
            int g = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            tree(level, r, x, g, z, 26 + r.nextInt(13));
            n++;
        }
        float yaw = facing.toYRot();
        p.teleportTo(level, o.getX() + 0.5, top + 1, o.getZ() + 0.5, Set.of(), yaw, 8F, true);
        return n;
    }

    /** A giant: a 3x3 trunk, three branches out of its upper half, and a wide crown of leaves that never decay. */
    private static void tree(ServerLevel level, RandomSource r, int x, int g, int z, int h) {
        BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState();
        BlockState leaves = Blocks.DARK_OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int y = g - 3; y < g + h; y++) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) set(level, x + dx, y, z + dz, log);
        }
        // roots flaring out at the base
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int k = 2; k <= 3; k++) set(level, x + d.getStepX() * k, g, z + d.getStepZ() * k, log.setValue(RotatedPillarBlock.AXIS, d.getAxis()));
        }
        // branches: arms reaching out of the upper trunk, each with a tuft at its end
        for (int b = 0; b < 3; b++) {
            Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(r);
            int by = g + h / 2 + r.nextInt(Math.max(1, h / 3));
            int len = 4 + r.nextInt(3);
            for (int k = 2; k <= len; k++) {
                set(level, x + d.getStepX() * k, by + (k > len - 1 ? 1 : 0), z + d.getStepZ() * k, log.setValue(RotatedPillarBlock.AXIS, d.getAxis()));
            }
            blob(level, r, leaves, x + d.getStepX() * len, by + 2, z + d.getStepZ() * len, 2.4, 1.6);
        }
        blob(level, r, leaves, x, g + h + 1, z, 6.5, 4.0);
    }

    private static void blob(ServerLevel level, RandomSource r, BlockState leaves, int cx, int cy, int cz, double rh, double rv) {
        int ih = Mth.ceil(rh), iv = Mth.ceil(rv);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dy = -iv; dy <= iv; dy++) for (int dx = -ih; dx <= ih; dx++) for (int dz = -ih; dz <= ih; dz++) {
            double e = (dx * dx + dz * dz) / (rh * rh) + (dy * dy) / (rv * rv);
            if (e > 1 || (e > 0.7 && r.nextFloat() < 0.35)) continue;
            pos.set(cx + dx, cy + dy, cz + dz);
            if (level.getBlockState(pos).isAir()) level.setBlock(pos, leaves, Block.UPDATE_CLIENTS);
        }
    }

    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();

    private static void set(ServerLevel level, int x, int y, int z, BlockState s) {
        level.setBlock(POS.set(x, y, z), s, Block.UPDATE_CLIENTS);
    }
}
