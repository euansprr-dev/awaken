package dev.awaken.frontier;

import dev.awaken.Awaken;
import dev.awaken.core.api.TheLull;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The two moorings (SPEC §67): the ridge's, just past Aldo's milestone on the north road (built the first time someone
 * comes near), and the landing's, far out on the ground the seed chose (built when its land is loaded, at the latest
 * just before the first skiff comes down there). Each finds the flattest dry spot near where it should be, lifts its
 * jetty three blocks over the highest ground there, clears the air above it, and stands on log posts with steps down from
 * the stern. Nobody can break or build into one.
 */
final class Moorings {
    private Moorings() {}

    enum Which { RIDGE, LANDING }

    /** Past the ridge on the north road, beside Aldo's milestone (he sits 12 past it, by the road). */
    static final int RIDGE_EAST = 22, RIDGE_PAST = 30;
    /** How far round its spot a mooring looks for flat ground, and on what step. */
    static final int SEARCH = 20, SEARCH_STEP = 4;
    /** A player this near the ridge's spot gets it built. */
    static final int BUILD_NEAR = 160;
    /** The deck stands this far over the highest ground under it. */
    static final int LIFT = 3;

    /** Where the ridge's mooring should be (its berth's middle, before the ground is found). */
    static BlockPos ridgeSpot(MinecraftServer server) {
        BlockPos c = TheLull.centre(server);
        return new BlockPos(c.getX() + RIDGE_EAST, 0, c.getZ() - TheLull.ridge(server) - RIDGE_PAST);
    }

    static @Nullable BlockPos landingSpot(MinecraftServer server) {
        return TheLull.landing(server);
    }

    /** The way a mooring's bow points: at the other end of the flight, to the nearest of the four. */
    static Direction facing(MinecraftServer server, Which which) {
        BlockPos c = TheLull.centre(server), l = TheLull.landing(server);
        if (l == null) return Direction.NORTH;
        int dx = l.getX() - c.getX(), dz = l.getZ() - c.getZ();
        if (which == Which.LANDING) {
            dx = -dx;
            dz = -dz;
        }
        return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
    }

    static FrontierData.@Nullable Mooring get(MinecraftServer server, Which which) {
        FrontierData d = FrontierData.get(server);
        return which == Which.RIDGE ? d.ridge() : d.landing();
    }

    /** Every 40 ticks: builds whichever mooring is due and loaded. */
    static void tick(MinecraftServer server) {
        if (!TheLull.farLanding(server)) return;
        ServerLevel level = server.overworld();
        FrontierData d = FrontierData.get(server);
        if (d.ridge() == null || !d.ridge().built()) {
            BlockPos spot = ridgeSpot(server);
            for (ServerPlayer p : level.players()) {
                double dx = p.getX() - spot.getX(), dz = p.getZ() - spot.getZ();
                if (dx * dx + dz * dz < BUILD_NEAR * BUILD_NEAR) {
                    ensure(level, Which.RIDGE);
                    break;
                }
            }
        }
        if (d.landing() == null || !d.landing().built()) ensure(level, Which.LANDING);
    }

    /** Builds the mooring if its land is loaded; true when it stands. */
    static boolean ensure(ServerLevel level, Which which) {
        MinecraftServer server = level.getServer();
        FrontierData d = FrontierData.get(server);
        FrontierData.Mooring m = get(server, which);
        if (m != null && m.built()) return true;
        BlockPos spot = which == Which.RIDGE ? ridgeSpot(server) : landingSpot(server);
        if (spot == null || !loaded(level, spot, SEARCH + Skiff.FOOT_X + 12)) return false;
        Direction facing = facing(server, which);
        BlockPos origin = m != null ? m.origin() : choose(level, spot, facing);
        build(level, origin, facing, which == Which.LANDING);
        FrontierData.Mooring built = new FrontierData.Mooring(origin, facing, true);
        if (which == Which.RIDGE) d.ridge(built);
        else d.landing(built);
        Awaken.LOG.info("[frontier] the {} mooring built at {} facing {}", which == Which.RIDGE ? "ridge's" : "landing's", origin.toShortString(),
                facing.getSerializedName());
        return true;
    }

    /** Every chunk within {@code r} of a spot is loaded. */
    static boolean loaded(ServerLevel level, BlockPos spot, int r) {
        for (int x = (spot.getX() - r) >> 4; x <= (spot.getX() + r) >> 4; x++)
            for (int z = (spot.getZ() - r) >> 4; z <= (spot.getZ() + r) >> 4; z++)
                if (!level.hasChunk(x, z)) return false;
        return true;
    }

    /** The berth's middle at deck height: the flattest dry ground near the spot, clear of the road. */
    static BlockPos choose(ServerLevel level, BlockPos spot, Direction facing) {
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        int bestTop = level.getSeaLevel();
        for (int ox = -SEARCH; ox <= SEARCH; ox += SEARCH_STEP)
            for (int oz = -SEARCH; oz <= SEARCH; oz += SEARCH_STEP) {
                BlockPos at = spot.offset(ox, 0, oz);
                int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, wet = 0, road = 0;
                for (int x = -Skiff.FOOT_X; x <= Skiff.FOOT_X; x += 2)
                    for (int z = Skiff.FOOT_Z0; z <= Skiff.FOOT_Z1; z += 2) {
                        BlockPos c = Skiff.at(at, facing, x, 0, z);
                        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
                        BlockState s = level.getBlockState(new BlockPos(c.getX(), top - 1, c.getZ()));
                        if (!s.getFluidState().isEmpty()) wet++;
                        if (s.is(Blocks.DIRT_PATH) || s.is(Blocks.GRAVEL) || s.is(BlockTags.SLABS) || s.is(BlockTags.STAIRS)) road++;
                        lo = Math.min(lo, top);
                        hi = Math.max(hi, top);
                    }
                double score = (hi - lo) * 3.0 + wet * 6 + road * 8 + Math.hypot(ox, oz) / 6;
                if (score < bestScore) {
                    bestScore = score;
                    best = at;
                    bestTop = hi;
                }
            }
        BlockPos at = best == null ? spot : best;
        return new BlockPos(at.getX(), bestTop - 1 + LIFT, at.getZ());
    }

    /** Puts the mooring's blocks up: clears its air, the jetty, the skiff, the posts and the steps. */
    static void build(ServerLevel level, BlockPos origin, Direction facing, boolean landing) {
        var rot = Skiff.rotation(facing);
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        // clear: from the keel to the clear air above, across the whole footprint
        for (int x = -Skiff.FOOT_X; x <= Skiff.FOOT_X; x++)
            for (int z = Skiff.FOOT_Z0; z <= Skiff.FOOT_Z1; z++)
                for (int y = Skiff.KEEL; y <= Skiff.CLEAR_UP; y++) {
                    BlockPos p = Skiff.at(origin, facing, x, y, z);
                    if (!level.getBlockState(p).isAir()) level.setBlock(p, Blocks.AIR.defaultBlockState(), flags);
                }
        List<BlockPos> placed = new ArrayList<>();
        List<Skiff.Part> parts = new ArrayList<>(Skiff.jetty());
        parts.addAll(Skiff.skiff());
        if (landing) parts.addAll(Skiff.landingExtras());
        for (Skiff.Part part : parts) {
            BlockPos p = Skiff.at(origin, facing, part.x(), part.y(), part.z());
            level.setBlock(p, part.state().rotate(rot), flags);
            placed.add(p);
        }
        // the posts, down to the ground
        BlockState post = Skiff.b("spruce_log");
        for (int[] xz : Skiff.POSTS) {
            for (int y = -1; y > -40; y--) {
                BlockPos p = Skiff.at(origin, facing, xz[0], y, xz[1]);
                BlockState s = level.getBlockState(p);
                if (!s.isAir() && !s.canBeReplaced() && !s.is(BlockTags.LEAVES)) break;
                level.setBlock(p, post, flags);
            }
        }
        // the steps down from the stern's gap, until they reach the ground
        BlockState step = Skiff.b("spruce_stairs").setValue(StairBlock.FACING, Direction.SOUTH).setValue(StairBlock.HALF, Half.BOTTOM).rotate(rot);
        for (int k = 0; k < 16; k++) {
            boolean grounded = false;
            for (int x = -1; x <= 1; x++) {
                BlockPos p = Skiff.at(origin, facing, x, -k, Skiff.STEPS_Z - k);
                BlockState s = level.getBlockState(p);
                if (!s.isAir() && !s.canBeReplaced()) {
                    grounded = true;
                    continue;
                }
                level.setBlock(p, step, flags);
                placed.add(p);
                for (int up = 1; up <= 3; up++) {
                    BlockPos a = p.above(up);
                    if (!level.getBlockState(a).isAir() && !Skiff.inFootprint(origin, facing, a)) level.setBlock(a, Blocks.AIR.defaultBlockState(), flags);
                }
                BlockState below = level.getBlockState(p.below());
                if (!below.isAir() && !below.canBeReplaced()) grounded = true;
            }
            if (grounded) break;
        }
        // fences join, stairs turn, lanterns check their hooks
        for (BlockPos p : placed) {
            BlockState s = level.getBlockState(p);
            BlockState shaped = Block.updateFromNeighbourShapes(s, level, p);
            if (shaped != s && !shaped.isAir()) level.setBlock(p, shaped, flags);
        }
    }

    /** The mooring whose helm (or hold, or board) is at this block, if any. */
    static @Nullable Which at(MinecraftServer server, BlockPos pos, BlockPos local) {
        for (Which w : Which.values()) {
            FrontierData.Mooring m = get(server, w);
            if (m != null && m.built() && Skiff.at(m.origin(), m.facing(), local).equals(pos)) return w;
        }
        return null;
    }

    /** Whether this block belongs to a built mooring (nobody breaks or builds into one). */
    static boolean guarded(MinecraftServer server, BlockPos pos) {
        for (Which w : Which.values()) {
            FrontierData.Mooring m = get(server, w);
            if (m != null && m.built() && Skiff.inFootprint(m.origin(), m.facing(), pos)) return true;
        }
        return false;
    }

    /** Where a player stands to fly, at the helm, facing the bow. */
    static double[] stand(FrontierData.Mooring m) {
        BlockPos s = Skiff.at(m.origin(), m.facing(), Skiff.STAND);
        return new double[] {s.getX() + 0.5, s.getY(), s.getZ() + 0.5, Skiff.yaw(m.facing())};
    }
}
