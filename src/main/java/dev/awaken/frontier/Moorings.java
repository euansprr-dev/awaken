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
import net.minecraft.world.level.block.LeavesBlock;
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
    /**
     * Past the glade, this far out, wood no longer joined to a trunk and leaves no longer joined to wood (through leaves,
     * within {@code HOLD}, as the game counts) come down too; the band of {@code HOLD} past that is read, never changed.
     */
    static final int FRINGE = 6, HOLD = 6;
    /** How far a berth's samples reach from its middle: the glade, and the steps' foot astern. */
    static final int REACH = Math.max(Math.max(Skiff.FOOT_X, Math.max(-Skiff.FOOT_Z0, Skiff.FOOT_Z1)) + GLADE, 9 - Skiff.STEPS_Z);
    /** What each sampled tree trunk standing in a berth's glade costs it (a giant's trunk is many samples). */
    static final double TREE = 2;
    /** How far down through a tree the land is looked for. */
    static final int DEEP = 96;
    /** A saved mooring whose pad stands this far over the land all round it was set on trees' crowns (before 12 Nov). */
    static final int PERCH = 8;
    /** A perched mooring is taken down and built again on the land only with nobody this near it (it may be underfoot). */
    static final int AWAY = 32;
    /** Land this far round a mooring's spot has to be loaded before it's built (the search, the footprint, the glade and its fringe). */
    static final int NEEDS = 48;
    /** A player this near the ridge's spot gets it built. */
    static final int BUILD_NEAR = 160;
    /** The deck stands this far over the highest ground under it: the pad's top is that ground. */
    static final int LIFT = -Skiff.PAD;
    /**
     * The mooring's look: a saved mooring of an older look is taken down and built again (1: the plank jetty; 2: the
     * wind-skiff), except that from 2 to 3 (the canvas lashed to its spars) only the canvas blocks change, and from 3 to 4
     * (12 Nov) the land under it is looked at again: one set on trees' crowns is taken down and built on the land, any
     * other has the trees round it cleared again (wood and leaves left hanging, and what hung on them).
     */
    static final int LOOK = 4;
    private static final int LASHED = 3, SETTLED = 4;

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
        if (spot == null || !loaded(level, spot, NEEDS)) return false;
        Direction facing = facing(server, which);
        boolean landing = which == Which.LANDING;
        String name = landing ? "landing's" : "ridge's";
        FrontierData.Mooring was = null;
        if (m != null && m.built()) {
            // an older look: nobody may be mid-flight to or from it while it changes
            if (Flights.anyFlying()) return true;
            if (m.look() >= LASHED - 1) {
                if (perched(level, m)) {
                    // set on trees' crowns, its pad on a stone tower over the forest floor: down, and up again on the land
                    if (anyoneNear(level, m.origin(), AWAY)) return true;
                    raze(level, m, landing, true);
                    Awaken.LOG.info("[frontier] the {} mooring at {} stood on trees, {} over the land: taken down to be built on it", name,
                            m.origin().toShortString(), m.origin().getY() + Skiff.PAD - around(level, m));
                    was = m;
                    m = null;
                } else {
                    if (m.look() < LASHED) relash(level, m, landing);
                    if (m.look() < SETTLED) clearTrees(level, m.origin(), m.facing(), false);
                    save(d, which, new FrontierData.Mooring(m.origin(), m.facing(), true, LOOK));
                    Awaken.LOG.info("[frontier] the {} mooring at {} settled: the trees round it cleared again", name, m.origin().toShortString());
                    return true;
                }
            } else {
                razeOld(level, m);
                m = null;
            }
        }
        BlockPos origin = m != null ? m.origin() : choose(level, spot, facing);
        build(level, origin, facing, landing);
        FrontierData.Mooring built = new FrontierData.Mooring(origin, facing, true, LOOK);
        save(d, which, built);
        if (was != null) d.repoint(stand(was), stand(built));
        Awaken.LOG.info("[frontier] the {} mooring built at {} facing {}", name, origin.toShortString(), facing.getSerializedName());
        return true;
    }

    private static void save(FrontierData d, Which which, FrontierData.Mooring m) {
        if (which == Which.RIDGE) d.ridge(m);
        else d.landing(m);
    }

    /** Whether a player is within {@code r} of a block, across the ground: one may be standing on it, or watching it. */
    private static boolean anyoneNear(ServerLevel level, BlockPos at, int r) {
        for (ServerPlayer p : level.players()) {
            double dx = p.getX() - at.getX() - 0.5, dz = p.getZ() - at.getZ() - 0.5;
            if (dx * dx + dz * dz < (double) r * r) return true;
        }
        return false;
    }

    /**
     * Every chunk within {@code r} of a spot is made and loaded now. Not {@code hasChunk}: that is true as soon as a ticket
     * wants the chunk, while it is still being made, and reading its ground then waits for it on the server thread (a
     * new world's far land froze the server for half a minute mid-flight, 12 Nov).
     */
    static boolean loaded(ServerLevel level, BlockPos spot, int r) {
        for (int x = (spot.getX() - r) >> 4; x <= (spot.getX() + r) >> 4; x++)
            for (int z = (spot.getZ() - r) >> 4; z <= (spot.getZ() + r) >> 4; z++)
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }

    /**
     * The land at a column, under any tree: the height a walker stands at once the trees are down. The no-leaves heightmap
     * stops on wood, so a giant's crown read as ground: Euan's ridge mooring of 12 Nov was set on Elder Colossi, its pad
     * 23 over the forest floor on a tower of its own stone. This walks on down through wood, leaves and the air and plants
     * under them to the first block that is land.
     */
    static int ground(ServerLevel level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, top, z);
        for (int y = top - 1, end = Math.max(level.getMinY(), top - DEEP); y >= end; y--)
            if (land(level.getBlockState(p.setY(y)))) return y + 1;
        return top;
    }

    /** Land a walker stands on: what the no-leaves heightmap counts, but never wood (a tree is not the ground); or water. */
    static boolean land(BlockState s) {
        return !s.getFluidState().isEmpty() || s.is(BlockTags.BLOCKS_MOTION_IN_HEIGHTMAP_NO_LEAVES) && !s.is(BlockTags.LOGS);
    }

    private static final byte WET = 1, ROAD = 2, TRUNK = 4;

    /** The land round a spot, read once a column as the berths are weighed: its height, and whether it is wet, road or a tree's foot. */
    private static final class Land {
        private final ServerLevel level;
        private final int x0, z0, size;
        private final int[] top;
        private final byte[] what;

        Land(ServerLevel level, BlockPos spot, int r) {
            this.level = level;
            x0 = spot.getX() - r;
            z0 = spot.getZ() - r;
            size = 2 * r + 1;
            top = new int[size * size];
            what = new byte[size * size];
            java.util.Arrays.fill(top, Integer.MIN_VALUE);
        }

        private int read(BlockPos c) {
            int i = (c.getZ() - z0) * size + c.getX() - x0;
            if (top[i] == Integer.MIN_VALUE) {
                int y = ground(level, c.getX(), c.getZ());
                BlockState s = level.getBlockState(new BlockPos(c.getX(), y - 1, c.getZ()));
                byte w = 0;
                if (!s.getFluidState().isEmpty()) w |= WET;
                if (s.is(Blocks.DIRT_PATH) || s.is(Blocks.GRAVEL) || s.is(BlockTags.SLABS) || s.is(BlockTags.STAIRS)) w |= ROAD;
                if (level.getBlockState(new BlockPos(c.getX(), y, c.getZ())).is(BlockTags.LOGS)) w |= TRUNK;
                top[i] = y;
                what[i] = w;
            }
            return i;
        }

        int top(BlockPos c) {
            return top[read(c)];
        }

        boolean is(BlockPos c, byte flag) {
            return (what[read(c)] & flag) != 0;
        }
    }

    /** The berth's middle at deck height: the flattest dry ground near the spot, clear of the road and of the trees' trunks. */
    static BlockPos choose(ServerLevel level, BlockPos spot, Direction facing) {
        Land land = new Land(level, spot, SEARCH + REACH);
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        int bestTop = level.getSeaLevel();
        int gx = Skiff.FOOT_X + GLADE, gz0 = Skiff.FOOT_Z0 - GLADE, gz1 = Skiff.FOOT_Z1 + GLADE;
        for (int ox = -SEARCH; ox <= SEARCH; ox += SEARCH_STEP)
            for (int oz = -SEARCH; oz <= SEARCH; oz += SEARCH_STEP) {
                BlockPos at = spot.offset(ox, 0, oz);
                int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, wet = 0, road = 0, trunks = 0;
                for (int x = -Skiff.FOOT_X; x <= Skiff.FOOT_X; x += 2)
                    for (int z = Skiff.FOOT_Z0; z <= Skiff.FOOT_Z1; z += 2) {
                        BlockPos c = Skiff.at(at, facing, x, 0, z);
                        int top = land.top(c);
                        if (land.is(c, WET)) wet++;
                        if (land.is(c, ROAD)) road++;
                        lo = Math.min(lo, top);
                        hi = Math.max(hi, top);
                    }
                // a tree standing in the glade comes down whole: a berth between the giants beats one that fells a giant
                for (int x = -gx; x <= gx; x += 2)
                    for (int z = gz0; z <= gz1; z += 2)
                        if (land.is(Skiff.at(at, facing, x, 0, z), TRUNK)) trunks++;
                // the pier's steps go down astern: where the ground falls away there they'd be a long stair on a tall wall
                int drop = 0;
                for (int back = 5; back <= 9; back += 4)
                    drop = Math.max(drop, hi - land.top(Skiff.at(at, facing, Skiff.STEPS_X[1], 0, Skiff.STEPS_Z - back)));
                double score = (hi - lo) * 3.0 + wet * 6 + road * 8 + Math.hypot(ox, oz) / 6 + Math.max(0, drop - 3) * 2.0 + trunks * TREE;
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
        BlockState air = Blocks.AIR.defaultBlockState();
        // a glade: the trees round the berth come down, so it stands in the open
        clearTrees(level, origin, facing, true);
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

    private static final byte AIR = 0, WOOD = 1, LEAF = 2, LEAF_KEPT = 3, SOLID = 4, HANGS = 5;
    /** A persistent leaf joined through leaves to one wood holds (a giant's crown has them past six). */
    private static final byte FAR = HOLD + 2;

    /**
     * The trees round a berth (SPEC §67.4). With {@code glade}, every log and leaf over the glade comes down, from six
     * under the pad to the top of the crowns. Then over the glade and a fringe round it: wood no longer joined (through
     * wood, as a branch joins) to any that rests on land, leaves no longer joined through leaves to wood within six (as the
     * game holds them), and whatever hung from them or lay on them. Without {@code glade} (a mooring already built) only
     * that second part runs, and never on a block of the mooring: what stands in the open is the player's.
     */
    static void clearTrees(ServerLevel level, BlockPos origin, Direction facing, boolean glade) {
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        BlockState air = Blocks.AIR.defaultBlockState();
        int gx = Skiff.FOOT_X + GLADE, gz0 = Skiff.FOOT_Z0 - GLADE, gz1 = Skiff.FOOT_Z1 + GLADE;
        int ix = gx + FRINGE, iz0 = gz0 - FRINGE, iz1 = gz1 + FRINGE;
        int fx = ix + HOLD, fz0 = iz0 - HOLD, fz1 = iz1 + HOLD;
        int oy = origin.getY(), y0 = Skiff.PAD - 6;
        if (glade)
            for (int x = -gx; x <= gx; x++)
                for (int z = gz0; z <= gz1; z++) {
                    BlockPos c = Skiff.at(origin, facing, x, 0, z);
                    int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, c.getX(), c.getZ()) - 1 - oy;
                    for (int y = y0; y <= top; y++) {
                        BlockPos p = c.above(y);
                        BlockState st = level.getBlockState(p);
                        if (st.is(BlockTags.LOGS) || st.is(BlockTags.LEAVES) || st.is(BlockTags.BEEHIVES)) level.setBlock(p, air, flags);
                    }
                }
        // read the land round it, up to the tops of the crowns
        int y1 = Skiff.CLEAR_UP + 16;
        for (int x = -fx; x <= fx; x++)
            for (int z = fz0; z <= fz1; z++) {
                BlockPos c = Skiff.at(origin, facing, x, 0, z);
                y1 = Math.max(y1, level.getHeight(Heightmap.Types.WORLD_SURFACE, c.getX(), c.getZ()) - 1 - oy);
            }
        // no tree stands taller than this over a deck (a colossus is 46), whatever cliff rises in the band
        y1 = Math.min(y1, Math.min(level.getMaxY() - oy, Skiff.CLEAR_UP + 80));
        int wide = 2 * fx + 1, deep = fz1 - fz0 + 1, tall = y1 - y0 + 1, n = wide * deep * tall, layer = wide * deep;
        byte[] kind = new byte[n], reach = new byte[n];
        int[] queue = new int[n];
        for (int y = y0; y <= y1; y++)
            for (int z = fz0; z <= fz1; z++)
                for (int x = -fx; x <= fx; x++) {
                    BlockState st = level.getBlockState(Skiff.at(origin, facing, x, y, z));
                    kind[((y - y0) * deep + z - fz0) * wide + x + fx] = st.isAir() ? AIR : st.is(BlockTags.LOGS) ? WOOD
                            : st.is(BlockTags.LEAVES) ? (st.hasProperty(LeavesBlock.PERSISTENT) && st.getValue(LeavesBlock.PERSISTENT) ? LEAF_KEPT : LEAF)
                            : land(st) ? SOLID : HANGS;
                }
        // wood holds if it rests on or against land, or (through wood) on wood that does; the band past the fringe and
        // the bottom layer are taken to hold, since what joins them may lie outside what was read
        int head = 0, tail = 0;
        int[] face = {1, -1, wide, -wide, layer, -layer};
        for (int i = 0; i < n; i++) {
            if (kind[i] != WOOD) continue;
            int cx = i % wide - fx, cz = i / wide % deep + fz0, cy = i / layer;
            boolean holds = Math.abs(cx) > ix || cz < iz0 || cz > iz1 || cy == 0;
            for (int k = 0; k < 6 && !holds; k++)
                if (step(i, k, wide, deep, tall)) holds = kind[i + face[k]] == SOLID;
            if (holds) {
                reach[i] = 1;
                queue[tail++] = i;
            }
        }
        while (head < tail) {
            int i = queue[head++], cx = i % wide, cz = i / wide % deep, cy = i / layer;
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int x = cx + dx, z = cz + dz, y = cy + dy;
                        if (x < 0 || x >= wide || z < 0 || z >= deep || y < 0 || y >= tall) continue;
                        int j = (y * deep + z) * wide + x;
                        if (kind[j] != WOOD || reach[j] != 0) continue;
                        reach[j] = 1;
                        queue[tail++] = j;
                    }
        }
        for (int i = 0; i < n; i++)
            if (kind[i] == WOOD && reach[i] == 0 && inner(i, wide, deep, fx, fz0, ix, iz0, iz1)) {
                if (cut(level, origin, facing, i, wide, deep, fx, fz0, y0, glade, air, flags)) kind[i] = AIR;
            }
        // leaves hold the way the game holds them: through leaves to wood within six; a persistent one joined to those holds too
        java.util.Arrays.fill(reach, (byte) 0);
        head = tail = 0;
        for (int i = 0; i < n; i++)
            if (kind[i] == WOOD) {
                reach[i] = 1;
                queue[tail++] = i;
            }
        while (head < tail) {
            int i = queue[head++];
            for (int k = 0; k < 6; k++) {
                if (!step(i, k, wide, deep, tall)) continue;
                int j = i + face[k];
                if (reach[j] != 0 || kind[j] != LEAF && kind[j] != LEAF_KEPT) continue;
                if (reach[i] <= HOLD) reach[j] = (byte) (reach[i] + 1);
                else if (kind[j] == LEAF_KEPT) reach[j] = FAR; // six from wood: only a persistent leaf goes on
                else continue;
                queue[tail++] = j;
            }
        }
        for (int i = 0; i < n; i++)
            if ((kind[i] == LEAF || kind[i] == LEAF_KEPT) && reach[i] == 0 && inner(i, wide, deep, fx, fz0, ix, iz0, iz1)) {
                if (cut(level, origin, facing, i, wide, deep, fx, fz0, y0, glade, air, flags)) kind[i] = AIR;
            }
        // what hung from them or lay on them (vines, moss, snow): from the top down, so a hanging run goes with its top
        for (int i = n - 1; i >= 0; i--) {
            if (kind[i] != HANGS || !inner(i, wide, deep, fx, fz0, ix, iz0, iz1)) continue;
            int cx = i % wide - fx, cz = i / wide % deep + fz0, cy = i / layer + y0;
            BlockPos p = Skiff.at(origin, facing, cx, cy, cz);
            if (!glade && kept(cx, cy, cz)) continue;
            BlockState st = level.getBlockState(p);
            if (!st.isAir() && st.getFluidState().isEmpty() && !st.canSurvive(level, p)) level.setBlock(p, air, flags);
        }
    }

    /** Whether cell {@code i}'s neighbour on face {@code k} (east, west, south, north, up, down in the read box) is in it. */
    private static boolean step(int i, int k, int wide, int deep, int tall) {
        int cx = i % wide, cz = i / wide % deep, cy = i / (wide * deep);
        return switch (k) {
            case 0 -> cx < wide - 1;
            case 1 -> cx > 0;
            case 2 -> cz < deep - 1;
            case 3 -> cz > 0;
            case 4 -> cy < tall - 1;
            default -> cy > 0;
        };
    }

    /** Whether cell {@code i} lies over the glade or its fringe (what may change), not the band past them (only read). */
    private static boolean inner(int i, int wide, int deep, int fx, int fz0, int ix, int iz0, int iz1) {
        int cx = i % wide - fx, cz = i / wide % deep + fz0;
        return Math.abs(cx) <= ix && cz >= iz0 && cz <= iz1;
    }

    /** Takes out the block of cell {@code i}, unless it's the mooring's own; true when it's gone. */
    private static boolean cut(ServerLevel level, BlockPos origin, Direction facing, int i, int wide, int deep, int fx, int fz0, int y0, boolean glade,
                               BlockState air, int flags) {
        int cx = i % wide - fx, cz = i / wide % deep + fz0, cy = i / (wide * deep) + y0;
        if (!glade && kept(cx, cy, cz)) return false;
        level.setBlock(Skiff.at(origin, facing, cx, cy, cz), air, flags);
        return true;
    }

    /** A block of a built mooring (local): its footprint from the pad up, or one of its posts. */
    private static boolean kept(int x, int y, int z) {
        if (Math.abs(x) <= Skiff.FOOT_X && z >= Skiff.FOOT_Z0 && z <= Skiff.FOOT_Z1 && y >= Skiff.PAD) return true;
        if (y < 0) for (int[] xz : Skiff.POSTS) if (xz[0] == x && xz[1] == z) return true;
        return false;
    }

    /** Spots just past the pad on its open sides (the bow, and the side away from the pier), where nothing of the mooring stands. */
    private static final int[][] AROUND = {{11, -8}, {11, -4}, {11, 0}, {11, 4}, {11, 8}, {-6, 12}, {-3, 12}, {0, 12}, {3, 12}, {6, 12}};

    /** The highest land just round a built mooring, under any trees. */
    static int around(ServerLevel level, FrontierData.Mooring m) {
        int best = Integer.MIN_VALUE;
        for (int[] xz : AROUND) {
            BlockPos c = Skiff.at(m.origin(), m.facing(), xz[0], 0, xz[1]);
            best = Math.max(best, ground(level, c.getX(), c.getZ()));
        }
        return best;
    }

    /** Whether a built mooring's pad stands well over the land all round it: set on trees' crowns, not on the ground. */
    static boolean perched(ServerLevel level, FrontierData.Mooring m) {
        return m.origin().getY() + Skiff.PAD - around(level, m) >= PERCH;
    }

    /**
     * Takes a wind-skiff mooring down to the land it was built on: its posts, its steps and the stone under them (first,
     * while they can still be found: the top step and the posts' heads lie in the footprint), every block of its plan, its
     * footprint from the keel up, and with {@code pad} its pad and the stone filled under it.
     */
    static void raze(ServerLevel level, FrontierData.Mooring m, boolean landing, boolean pad) {
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos o = m.origin();
        Direction f = m.facing();
        for (int[] xz : Skiff.POSTS)
            for (int y = -1; y > -40; y--) {
                BlockPos p = Skiff.at(o, f, xz[0], y, xz[1]);
                if (!level.getBlockState(p).is(Blocks.SPRUCE_LOG)) break;
                level.setBlock(p, air, flags);
            }
        for (int k = 0; k < 16; k++)
            for (int x : Skiff.STEPS_X) {
                BlockPos p = Skiff.at(o, f, x, -k, Skiff.STEPS_Z - k);
                if (!level.getBlockState(p).is(Blocks.SPRUCE_STAIRS)) continue;
                level.setBlock(p, air, flags);
                unfill(level, p, flags);
            }
        List<Skiff.Part> parts = new ArrayList<>(Skiff.jetty());
        parts.addAll(Skiff.skiff());
        parts.addAll(landing ? Skiff.landingExtras() : Skiff.ridgeExtras());
        for (Skiff.Part part : parts) {
            BlockPos p = Skiff.at(o, f, part.x(), part.y(), part.z());
            if (!level.getBlockState(p).isAir()) level.setBlock(p, air, flags);
        }
        for (int x = -Skiff.FOOT_X; x <= Skiff.FOOT_X; x++)
            for (int z = Skiff.FOOT_Z0; z <= Skiff.FOOT_Z1; z++)
                for (int y = Skiff.KEEL; y <= Skiff.CLEAR_UP; y++) {
                    BlockPos p = Skiff.at(o, f, x, y, z);
                    if (!level.getBlockState(p).isAir()) level.setBlock(p, air, flags);
                }
        if (!pad) return;
        int reach = (int) Math.ceil(Math.sqrt(Skiff.PAD_R2));
        for (int x = -reach; x <= reach; x++)
            for (int z = -reach; z <= reach; z++) {
                if (x * x + z * z > Skiff.PAD_R2) continue;
                BlockPos top = Skiff.at(o, f, x, Skiff.PAD, z);
                if (!level.getBlockState(top).isAir()) level.setBlock(top, air, flags);
                unfill(level, top, flags);
            }
    }

    /** The stone a column was filled with under a pad or a step ({@link #fillDown}), taken out down to the land. */
    private static void unfill(ServerLevel level, BlockPos top, int flags) {
        for (int y = 1; y <= 24; y++) {
            BlockPos p = top.below(y);
            BlockState s = level.getBlockState(p);
            if (!s.is(Blocks.COBBLESTONE) && !s.is(Blocks.STONE_BRICKS)) break;
            level.setBlock(p, Blocks.AIR.defaultBlockState(), flags);
        }
    }

    /**
     * For a tour (SPEC §67.4): a built mooring put up again {@code up} over where it stands, on its stone, and saved as the
     * look before, as the old ground search left one on trees' crowns. Its next check takes it down and builds it on the
     * land. Its old pad stays under the new stone, so the land is as the mooring found it once that's gone.
     */
    static boolean perch(ServerLevel level, Which which, int up) {
        MinecraftServer server = level.getServer();
        FrontierData.Mooring m = get(server, which);
        if (m == null || !m.built()) return false;
        boolean landing = which == Which.LANDING;
        raze(level, m, landing, false);
        BlockPos high = m.origin().above(up);
        build(level, high, m.facing(), landing);
        save(FrontierData.get(server), which, new FrontierData.Mooring(high, m.facing(), true, SETTLED - 1));
        Awaken.LOG.info("[frontier] the {} mooring put up {} over its land at {}, as if built on trees", landing ? "landing's" : "ridge's", up,
                high.toShortString());
        return true;
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
     * Ties a wind-skiff mooring's canvas to its spars (SPEC §67.4, 12 Nov): each canvas block of the plan still standing
     * takes the plan's state, lashed sides and all. Nothing else is touched, so a world's mooring keeps its place.
     */
    static void relash(ServerLevel level, FrontierData.Mooring m, boolean landing) {
        var rot = Skiff.rotation(m.facing());
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE, n = 0;
        List<Skiff.Part> parts = new ArrayList<>(Skiff.jetty());
        parts.addAll(Skiff.skiff());
        parts.addAll(landing ? Skiff.landingExtras() : Skiff.ridgeExtras());
        for (Skiff.Part part : parts) {
            if (!part.state().is(SkiffBlocks.SAIL.get())) continue;
            BlockPos p = Skiff.at(m.origin(), m.facing(), part.x(), part.y(), part.z());
            BlockState want = part.state().rotate(rot);
            if (level.getBlockState(p).is(SkiffBlocks.SAIL.get()) && level.getBlockState(p) != want) {
                level.setBlock(p, want, flags);
                n++;
            }
        }
        Awaken.LOG.info("[frontier] the {} mooring's canvas lashed to its spars ({} blocks)", landing ? "landing's" : "ridge's", n);
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
