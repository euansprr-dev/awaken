package dev.awaken.frontier;

import dev.awaken.Awaken;
import dev.awaken.core.api.TheLull;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
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
 * just before the first skiff comes down there). Each finds the flattest dry spot near where it should be and sets the
 * old landing ring's stone pad on the highest ground there, filled down to the land wherever it falls away; the skiff
 * rests in a cradle on the pad, a pier runs along one side on log posts with steps down from its stern end, and a mast
 * with three lights stands by its bay. Nobody can break or build into one.
 */
final class Moorings {
    private Moorings() {}

    enum Which { RIDGE, LANDING }

    /** Past the ridge on the north road, beside Aldo's milestone (he sits 12 past it, by the road). */
    static final int RIDGE_EAST = 22, RIDGE_PAST = 30;
    /** How far round its spot a mooring looks for flat ground, and on what step. */
    static final int SEARCH = 20, SEARCH_STEP = 4;
    /** How far past the footprint the trees come down. */
    static final int GLADE = 6;
    /** A player this near the ridge's spot gets it built. */
    static final int BUILD_NEAR = 160;
    /** The deck stands this far over the highest ground under it: the pad's top is that ground. */
    static final int LIFT = -Skiff.PAD;
    /** The mooring's look: a saved mooring of an older look is taken down and built again (1: the plank jetty; 2: the wind-skiff). */
    static final int LOOK = 2;

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
        if (d.ridge() == null || !d.ridge().built() || d.ridge().look() < LOOK) {
            BlockPos spot = ridgeSpot(server);
            for (ServerPlayer p : level.players()) {
                double dx = p.getX() - spot.getX(), dz = p.getZ() - spot.getZ();
                if (dx * dx + dz * dz < BUILD_NEAR * BUILD_NEAR) {
                    ensure(level, Which.RIDGE);
                    break;
                }
            }
        }
        if (d.landing() == null || !d.landing().built() || d.landing().look() < LOOK) ensure(level, Which.LANDING);
    }

    /** Builds the mooring if its land is loaded; true when it stands. */
    static boolean ensure(ServerLevel level, Which which) {
        MinecraftServer server = level.getServer();
        FrontierData d = FrontierData.get(server);
        FrontierData.Mooring m = get(server, which);
        if (m != null && m.built() && m.look() >= LOOK) return true;
        BlockPos spot = which == Which.RIDGE ? ridgeSpot(server) : landingSpot(server);
        if (spot == null || !loaded(level, spot, SEARCH + Skiff.FOOT_Z1 + GLADE + 12)) return false;
        Direction facing = facing(server, which);
        if (m != null && m.built()) {
            // an older look: nobody may be mid-flight to or from it while it's swapped
            if (Flights.anyFlying()) return true;
            razeOld(level, m);
            m = null;
        }
        BlockPos origin = m != null ? m.origin() : choose(level, spot, facing);
        build(level, origin, facing, which == Which.LANDING);
        FrontierData.Mooring built = new FrontierData.Mooring(origin, facing, true, LOOK);
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
                        // a tree is not the ground: its trunk comes down with the glade
                        while (top - 1 > level.getMinY() && level.getBlockState(new BlockPos(c.getX(), top - 1, c.getZ())).is(BlockTags.LOGS)) top--;
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

    /**
     * Puts the mooring up: a glade, the old landing ring's stone pad fitted to the ground under it, the pier and its
     * bay, the mast, the skiff in its cradle, posts down to whatever is under them, and steps down to the ground.
     */
    static void build(ServerLevel level, BlockPos origin, Direction facing, boolean landing) {
        var rot = Skiff.rotation(facing);
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        // a glade: the trees round the berth come down (and the snow that lay on them), so it stands in the open
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int pass = 0; pass < 2; pass++)
            for (int x = -Skiff.FOOT_X - GLADE; x <= Skiff.FOOT_X + GLADE; x++)
                for (int z = Skiff.FOOT_Z0 - GLADE; z <= Skiff.FOOT_Z1 + GLADE; z++)
                    for (int y = Skiff.PAD - 6; y <= Skiff.CLEAR_UP + 16; y++) {
                        BlockPos p = Skiff.at(origin, facing, x, y, z);
                        BlockState st = level.getBlockState(p);
                        if (pass == 0 ? st.is(BlockTags.LOGS) || st.is(BlockTags.LEAVES)
                                : st.is(Blocks.SNOW) && level.getBlockState(p.below()).isAir()) level.setBlock(p, air, flags);
                    }
        // clear: from the keel to the clear air above, across the whole footprint
        for (int x = -Skiff.FOOT_X; x <= Skiff.FOOT_X; x++)
            for (int z = Skiff.FOOT_Z0; z <= Skiff.FOOT_Z1; z++)
                for (int y = Skiff.KEEL; y <= Skiff.CLEAR_UP; y++) {
                    BlockPos p = Skiff.at(origin, facing, x, y, z);
                    if (!level.getBlockState(p).isAir()) level.setBlock(p, air, flags);
                }
        // the pad: old paving inside a kerb of stone brick, every column filled down to the ground so nothing hangs
        int reach = (int) Math.ceil(Math.sqrt(Skiff.PAD_R2));
        for (int x = -reach; x <= reach; x++)
            for (int z = -reach; z <= reach; z++) {
                int d2 = x * x + z * z;
                if (d2 > Skiff.PAD_R2) continue;
                boolean kerb = d2 > Skiff.PAD_R2 - 12;
                BlockPos top = Skiff.at(origin, facing, x, Skiff.PAD, z);
                long h = Mth.getSeed(top);
                level.setBlock(top, kerb ? kerb(h) : paving(h), flags);
                fillDown(level, top, kerb ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState(), flags);
            }
        List<BlockPos> placed = new ArrayList<>();
        List<Skiff.Part> parts = new ArrayList<>(Skiff.jetty());
        parts.addAll(Skiff.skiff());
        parts.addAll(landing ? Skiff.landingExtras() : Skiff.ridgeExtras());
        for (Skiff.Part part : parts) {
            BlockPos p = Skiff.at(origin, facing, part.x(), part.y(), part.z());
            level.setBlock(p, part.state().rotate(rot), flags);
            placed.add(p);
        }
        // the posts and the mast, down to whatever is under them
        BlockState post = Skiff.b("spruce_log");
        for (int[] xz : Skiff.POSTS) {
            for (int y = -1; y > -40; y--) {
                BlockPos p = Skiff.at(origin, facing, xz[0], y, xz[1]);
                BlockState s = level.getBlockState(p);
                if (!s.isAir() && !s.canBeReplaced() && !s.is(BlockTags.LEAVES)) break;
                level.setBlock(p, post, flags);
            }
        }
        // the steps down from the pier's stern end, on stone, until they reach the ground
        BlockState step = Skiff.b("spruce_stairs").setValue(StairBlock.FACING, Direction.SOUTH).setValue(StairBlock.HALF, Half.BOTTOM).rotate(rot);
        BlockState under = Blocks.STONE_BRICKS.defaultBlockState();
        for (int k = 0; k < 16; k++) {
            boolean grounded = false;
            for (int x : Skiff.STEPS_X) {
                BlockPos p = Skiff.at(origin, facing, x, -k, Skiff.STEPS_Z - k);
                BlockState s = level.getBlockState(p);
                if (!s.isAir() && !s.canBeReplaced() && !s.is(BlockTags.LEAVES)) {
                    grounded = true;
                    continue;
                }
                level.setBlock(p, step, flags);
                placed.add(p);
                for (int up = 1; up <= 3; up++) {
                    BlockPos a = p.above(up);
                    if (!level.getBlockState(a).isAir() && !Skiff.inFootprint(origin, facing, a)) level.setBlock(a, air, flags);
                }
                BlockState below = level.getBlockState(p.below());
                if (!below.isAir() && !below.canBeReplaced()) grounded = true;
                else fillDown(level, p, under, flags);
            }
            if (grounded) break;
        }
        // fences join, stairs turn
        for (BlockPos p : placed) {
            BlockState s = level.getBlockState(p);
            BlockState shaped = Block.updateFromNeighbourShapes(s, level, p);
            if (shaped != s && !shaped.isAir()) level.setBlock(p, shaped, flags);
        }
    }

    /** Stone under a block, down to the ground (at most 24), so a pad's edge or a step never hangs in the air. */
    private static void fillDown(ServerLevel level, BlockPos top, BlockState with, int flags) {
        for (int y = 1; y <= 24; y++) {
            BlockPos p = top.below(y);
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && !s.canBeReplaced() && !s.is(BlockTags.LEAVES) && s.getFluidState().isEmpty()) break;
            level.setBlock(p, with, flags);
        }
    }

    /** The pad's kerb: stone brick, some of it mossy or cracked with age. */
    private static BlockState kerb(long h) {
        int k = (int) Math.floorMod(h >> 8, 10);
        return (k < 6 ? Blocks.STONE_BRICKS : k < 8 ? Blocks.MOSSY_STONE_BRICKS : Blocks.CRACKED_STONE_BRICKS).defaultBlockState();
    }

    /** The pad's paving: worn andesite and cobble, moss in the joints. */
    private static BlockState paving(long h) {
        int k = (int) Math.floorMod(h >> 8, 20);
        return (k < 8 ? Blocks.POLISHED_ANDESITE : k < 13 ? Blocks.ANDESITE : k < 17 ? Blocks.COBBLESTONE : k < 19 ? Blocks.MOSSY_COBBLESTONE
                : Blocks.STONE_BRICKS).defaultBlockState();
    }

    /**
     * Takes down a mooring of the first look (a plank jetty round a boxy skiff, SPEC §67.4 before 11 Nov): its footprint
     * emptied, its posts and steps taken out. The new one is then built where the ground suits it.
     */
    static void razeOld(ServerLevel level, FrontierData.Mooring m) {
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos o = m.origin();
        Direction f = m.facing();
        for (int x = -5; x <= 5; x++)
            for (int z = -9; z <= 7; z++)
                for (int y = -2; y <= 10; y++) {
                    BlockPos p = Skiff.at(o, f, x, y, z);
                    if (!level.getBlockState(p).isAir()) level.setBlock(p, air, flags);
                }
        int[][] posts = {{5, -8}, {-5, -8}, {5, -1}, {-5, -1}, {5, 6}, {-5, 6}, {2, -8}, {-2, -8}};
        for (int[] xz : posts)
            for (int y = -3; y > -40; y--) {
                BlockPos p = Skiff.at(o, f, xz[0], y, xz[1]);
                if (!level.getBlockState(p).is(Blocks.SPRUCE_LOG)) break;
                level.setBlock(p, air, flags);
            }
        for (int k = 0; k < 16; k++)
            for (int x = -1; x <= 1; x++) {
                BlockPos p = Skiff.at(o, f, x, -k, -9 - k);
                if (level.getBlockState(p).is(Blocks.SPRUCE_STAIRS)) level.setBlock(p, air, flags);
            }
        Awaken.LOG.info("[frontier] the old mooring at {} taken down for the new one", o.toShortString());
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
