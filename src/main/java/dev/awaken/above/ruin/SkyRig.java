package dev.awaken.above.ruin;

import dev.awaken.body.ascent.AscentContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A sky platform's way up and its Wings core (SPEC §17.9, the Torn Stair of 11 Nov 2026). Placed on each platform the
 * first time its chunks are all loaded, old worlds' platforms too (the mark is the lit panel under the core; a platform
 * with the first rig's chain and rings has them taken away and gets the stair).
 * <p>
 * <b>The stair</b>: the ground the disc tore up as it rose, strung out behind it in a spiral. Terraces of turf and old
 * paving {@link #ARC} long and 8 wide wind round the platform {@link #RISE} apart, each ending in a wall up to the next
 * with a lit anchor ring in its lip: from a run along one terrace the held rope slings over the next lip (SPEC §62: a
 * top 6 to 30 ahead and above the hand), lands a third of the way along it and runs on. A sling refused leaves a wall to
 * pull up; nothing along the way is a gap. The lowest terrace stands a wall's height over the ground (a dock at the
 * water's surface where that is sea). The top one is level with the deck.
 * <p>
 * <b>The axis</b>: a bridge from the top terrace to the deck on a diagonal, through a gate, into the cabin by a corner
 * opened as a portal, to the case where the wings hang over the core (the client draws them), out by the far corner and
 * along the pier, which is where a first flight begins (WayUp.firstFlight: the pier runs out along +x and +z). The
 * blueprint's chest and stand keep two of the cabin's corners, whichever way it was turned: the portals take the other
 * two, so in half the worlds the bridge and the pier are a straight line through the case, and in the rest a quarter
 * turn apart.
 */
public final class SkyRig {
    private SkyRig() {}

    /** The pier's end (out along both x and z from the deck's centre): the first flight's start (WayUp, the dev commands). */
    public static final int PIER = 12;
    /** The pier's and the bridge's first cells (out along x and z): they reach back onto the deck's disc (radius 11.6). */
    static final int PIER_ROOT = 7;
    /** The bridge's last cells out along its diagonal: where the top terrace's inner edge is. */
    public static final int BRIDGE = 18;
    /** The blueprint's deck (local) and centre (RuinBuilder.sky). */
    static final int DECK = 12, C = 24;

    /** A terrace's rise over the one below, and its length along the middle of the stair. */
    public static final int RISE = 8, ARC = 24;
    /** The stair's inner and outer edge and its middle, from the deck's centre: clear of the four torn islands (out to 23.4). */
    public static final double R_IN = 25, R_OUT = 33, R_MID = 29;
    /** The turn of one terrace about the centre, and terraces a whole turn (7.6: a turn is 61 blocks of height). */
    static final double TURN = ARC / R_MID, PER_TURN = Math.PI * 2 / TURN;
    /** How much of a terrace (of its length) is the wall under its lip; where along the top one the bridge meets it. */
    static final double LIP = 2.2 / ARC, MEET = 0.18;
    /** Never more terraces than this (320 blocks of height). */
    static final int MAX_TIERS = 40;
    /** How far out the rig reads and builds, in blocks from the centre. */
    static final int SPAN = 35;
    /** Blocks placed a tick while a stair is being laid. */
    static final int PER_TICK = 700;

    // the first rig (10 Oct to 11 Nov 2026): a chain under the pier's end with rings round it; taken away where found
    private static final int OLD_RING_STEP = 22, OLD_FIRST_RING = 26, OLD_RING_OFFSET = 3, OLD_TOP_GAP = 9;

    private static final ConcurrentLinkedQueue<BoundingBox> PENDING = new ConcurrentLinkedQueue<>();
    private static final ArrayDeque<Job> JOBS = new ArrayDeque<>();

    /** The deck's centre of a platform starting in this chunk (its blueprint is 49 across from the chunk's corner). */
    public static BlockPos centre(net.minecraft.world.level.ChunkPos chunk) {
        return new BlockPos(chunk.getMinBlockX() + C, 0, chunk.getMinBlockZ() + C);
    }

    /**
     * The deck's height of a platform starting in this chunk, from the world's seed alone (the windways need it for
     * platforms far beyond loaded chunks): PrecursorRuinStructure draws a seed, a turn, then the height, from the
     * structure's own random (Structure.GenerationContext.makeRandom).
     */
    public static int deckY(long levelSeed, net.minecraft.world.level.ChunkPos chunk) {
        var r = new net.minecraft.world.level.levelgen.WorldgenRandom(new net.minecraft.world.level.levelgen.LegacyRandomSource(0L));
        r.setLargeFeatureSeed(levelSeed, chunk.x(), chunk.z());
        r.nextLong();
        r.nextInt(4);
        return PrecursorRuinStructure.SKY_MIN_Y + r.nextInt(8) + DECK;
    }

    // ---- the layout: plain geometry ------------------------------------------------------------------------------------

    /** The land under a stair: the first free height of a column, and whether a cell holds water. */
    public interface Land {
        int ground(int x, int z);

        default boolean wet(int x, int y, int z) {
            return false;
        }
    }

    /**
     * What a platform's rig is. {@code bx, bz}: the bridge's diagonal (each -1 or 1); {@code tiers}: terraces, the top
     * one first; {@code rings}: the anchor ring in each terrace's lip, the top one first; {@code foot}: where a climb
     * begins, on the ground a run short of the lowest wall, and the way to face there (degrees, the game's yaw);
     * {@code dockY}: the dock's top block where the lowest wall stands in water, else {@link Integer#MIN_VALUE}.
     */
    public record Layout(BlockPos core, List<BlockPos> pier, List<BlockPos> bridge, int bx, int bz, int tiers, List<BlockPos> rings, BlockPos foot,
                         float footYaw, int footY, int dockY, double top) {
        /** The top block of terrace {@code k} (0 is level with the deck). */
        public int y(int k) {
            return core.getY() - 1 - RISE * k;
        }
    }

    /** A band three cells wide along a diagonal from the deck's rim out to {@code end} (the end cell last). */
    private static List<BlockPos> band(int cx, int deckY, int cz, int sx, int sz, int end) {
        List<BlockPos> out = new ArrayList<>();
        for (int a = PIER_ROOT; a <= end; a++) for (int b = PIER_ROOT; b <= end; b++) {
            if (Math.abs(a - b) <= 1 && (a != end || b != end)) out.add(new BlockPos(cx + sx * a, deckY, cz + sz * b));
        }
        out.add(new BlockPos(cx + sx * end, deckY, cz + sz * end));
        return out;
    }

    /** The cell at this radius and angle from the centre. */
    private static int cell(int c, double r, double trig) {
        return c + (int) Math.round(r * trig);
    }

    /** The rig of a platform whose deck is at {@code deckY}, its bridge on the (bx, bz) diagonal, over this land. */
    public static Layout layout(int cx, int deckY, int cz, int bx, int bz, Land land) {
        return layout(cx, deckY, cz, bx, bz, land, -1);
    }

    /**
     * {@code known}: the terraces a stair already laid has (the land's heights now hold the stair itself, so its foot
     * can't be found from them again), else -1.
     */
    static Layout layout(int cx, int deckY, int cz, int bx, int bz, Land land, int known) {
        BlockPos core = new BlockPos(cx, deckY + 1, cz);
        // the top terrace runs a little past the bridge; the stair goes down as the angle falls
        double top = Math.atan2(bz, bx) + MEET * TURN;
        int last = MAX_TIERS, footY = deckY - RISE * MAX_TIERS, dockY = Integer.MIN_VALUE;
        double[] radii = {R_IN + 1.5, R_MID, R_OUT - 1.5};
        if (known > 0) last = known - 1;
        for (int k = 0; k <= MAX_TIERS && known <= 0; k++) {
            // the ground a step short of this terrace's wall across the stair's width, and its lowest: how deep the wall goes
            double a = top - (k + 1) * TURN - 2.0 / R_MID;
            int high = Integer.MIN_VALUE, low = Integer.MAX_VALUE, sum = 0;
            for (double r : radii) {
                int g = land.ground(cell(cx, r, Math.cos(a)), cell(cz, r, Math.sin(a)));
                high = Math.max(high, g);
                low = Math.min(low, g);
                sum += g;
            }
            // the middle of the three (one trunk or one pit is not the ground)
            if (deckY - RISE * k + 1 - (sum - high - low) <= RISE) {
                last = k;
                footY = low;
                int mx = cell(cx, R_MID, Math.cos(a)), mz = cell(cz, R_MID, Math.sin(a)), g = land.ground(mx, mz);
                if (land.wet(mx, g - 1, mz)) dockY = g - 1;
                break;
            }
        }
        List<BlockPos> rings = new ArrayList<>();
        for (int k = 0; k <= last; k++) {
            double a = top - (k + 1) * TURN + 1.2 / R_MID;
            rings.add(new BlockPos(cell(cx, R_MID, Math.cos(a)), deckY - RISE * k, cell(cz, R_MID, Math.sin(a))));
        }
        // the foot: a run short of the lowest wall, facing along the stair (the way up is the way the angle grows)
        double fa = top - (last + 1) * TURN - 9.0 / R_MID;
        int fx = cell(cx, R_MID, Math.cos(fa)), fz = cell(cz, R_MID, Math.sin(fa));
        int fy = dockY != Integer.MIN_VALUE ? dockY + 1 : land.ground(fx, fz);
        float yaw = (float) Math.toDegrees(Math.atan2(Math.sin(fa), Math.cos(fa)));
        return new Layout(core, band(cx, deckY, cz, 1, 1, PIER), band(cx, deckY, cz, bx, bz, BRIDGE), bx, bz, last + 1, rings,
                new BlockPos(fx, fy, fz), yaw, footY, dockY, top);
    }

    // ---- what to place -------------------------------------------------------------------------------------------------

    /** How a block is put: where nothing stands, always, as a plant on bare air, joined to its neighbours, or taken away. */
    enum How { FREE, FORCE, PLANT, JOIN, CLEAR_GREEN, CLEAR_RIG }

    record Put(BlockPos pos, BlockState state, How how) {}

    private static int hash(int x, int y, int z) {
        int h = x * 73428767 ^ y * 912931 ^ z * 42317861;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        return (h ^ h >>> 15) & 0x7fffffff;
    }

    private static final Block[] FLOWERS = {Blocks.OXEYE_DAISY, Blocks.AZURE_BLUET, Blocks.CORNFLOWER, Blocks.ALLIUM};

    /** The stair, the bridge and its gate, the pier's lights: everything outside the cabin. */
    static void stair(Layout l, List<Put> out) {
        int cx = l.core().getX(), cz = l.core().getZ(), deckY = l.core().getY() - 1, last = l.tiers() - 1;
        boolean dock = l.dockY() != Integer.MIN_VALUE;
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = -SPAN + 1; dx < SPAN; dx++) for (int dz = -SPAN + 1; dz < SPAN; dz++) {
            double r = Math.sqrt(dx * dx + dz * dz);
            if (r < R_IN || r >= R_OUT) continue;
            double u0 = (l.top() - Math.atan2(dz, dx)) / TURN % PER_TURN;
            if (u0 < 0) u0 += PER_TURN;
            int x = cx + dx, z = cz + dz;
            double off = Math.abs(r - R_MID);
            boolean edge = r - R_IN < 1 || R_OUT - r < 1;
            for (double u = u0; ; u += PER_TURN) {
                int k = (int) Math.floor(u);
                if (k > last + (dock ? 1 : 0)) break;
                double f = u - k;
                if (k > last) {
                    // the dock: a deck at the water's surface before the lowest wall, to climb out onto and throw from
                    if (f > 0.55) continue;
                    out.add(new Put(new BlockPos(x, l.dockY(), z), edge || f > 0.5 ? RuinBuilder.trim() : RuinBuilder.plating(), How.FORCE));
                    out.add(new Put(new BlockPos(x, l.dockY() - 1, z), RuinBuilder.plating(), How.FORCE));
                    continue;
                }
                int y = deckY - RISE * k, h = hash(x, k, z);
                boolean lip = f > 1 - LIP;
                if (lip) {
                    // the wall: paving on top, plate down its face to the terrace below (the lowest: into the ground)
                    int bottom = k < last ? y - RISE - 2 : Math.max(y - 14, (dock ? l.dockY() : Math.min(l.footY(), y)) - 2);
                    out.add(new Put(new BlockPos(x, y, z), RuinBuilder.panel(), How.FREE));
                    for (int yy = y - 1; yy >= bottom; yy--) {
                        // two lamps of the ring's light down the wall's middle: by night the stair is a spiral of them
                        boolean lamp = off < 0.75 && (yy == y - 2 || yy == y - 3);
                        out.add(new Put(new BlockPos(x, yy, z), lamp ? RuinBuilder.glow() : edge ? RuinBuilder.trim()
                                : (hash(x, yy, z) % 7 == 0 ? RuinBuilder.cracked() : RuinBuilder.plating()), How.FREE));
                    }
                } else {
                    // torn ground: old paving down the middle, turf either side, stone under it, deepest in the middle
                    int thick = 1 + (int) Math.round(3 * Math.pow(Math.max(0, 1 - off / 4.2), 0.7)) + (h & 1);
                    boolean paved = off < 2.2 && h % 100 < 46;
                    BlockState topBlock = paved ? (h % 100 < 12 ? RuinBuilder.plating() : RuinBuilder.panel())
                            : off < 2.2 && h % 100 < 62 ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
                    out.add(new Put(new BlockPos(x, y, z), topBlock, How.FREE));
                    for (int d = 1; d <= thick; d++) {
                        int hh = hash(x, y - d, z) % 10;
                        BlockState b = d == 1 && !paved ? Blocks.DIRT.defaultBlockState() : hh < 5 ? Blocks.STONE.defaultBlockState()
                                : hh < 7 ? Blocks.COBBLESTONE.defaultBlockState() : hh < 8 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                                : Blocks.DEEPSLATE.defaultBlockState();
                        out.add(new Put(new BlockPos(x, y - d, z), b, How.FREE));
                    }
                    if (h % 17 == 0) out.add(new Put(new BlockPos(x, y - thick - 1, z), Blocks.HANGING_ROOTS.defaultBlockState(), How.PLANT));
                    // life at the edges only: the middle is the run
                    if (!paved && off > 2.6 && topBlock.is(Blocks.GRASS_BLOCK)) {
                        int p = h / 100 % 100;
                        if (p < 30) out.add(new Put(new BlockPos(x, y + 1, z), Blocks.SHORT_GRASS.defaultBlockState(), How.PLANT));
                        else if (p < 40) out.add(new Put(new BlockPos(x, y + 1, z), FLOWERS[p % FLOWERS.length].defaultBlockState(), How.PLANT));
                    }
                }
                // near the ground a wood may stand in the way of the run and of the rope
                if (k >= last - 2) for (int up = 1; up <= 7; up++) out.add(new Put(new BlockPos(x, y + up, z), air, How.CLEAR_GREEN));
            }
        }
        // a ring alight in each lip (a block in from its edge), a lantern hung under each terrace's middle
        BlockState chain = Blocks.IRON_CHAIN.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
        for (int k = 0; k < l.tiers(); k++) {
            BlockPos ring = l.rings().get(k);
            out.add(new Put(ring, AscentContent.ANCHOR_RING.get().defaultBlockState(), How.FORCE));
            double a = l.top() - (k + 0.45) * TURN;
            BlockPos mid = new BlockPos(cell(cx, R_MID, Math.cos(a)), l.y(k), cell(cz, R_MID, Math.sin(a)));
            for (int d = 1; d <= 5; d++) out.add(new Put(mid.below(d), Blocks.STONE.defaultBlockState(), How.FREE));
            out.add(new Put(mid.below(6), chain, How.FREE));
            out.add(new Put(mid.below(7), chain, How.FREE));
            out.add(new Put(mid.below(8), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), How.FREE));
        }
        // the bridge and the pier: brass either side, a line of light down the middle; the rim's studs out of the way
        for (List<BlockPos> band : List.of(l.bridge(), l.pier())) {
            for (BlockPos p : band) {
                int a = Math.abs(p.getX() - cx), b = Math.abs(p.getZ() - cz);
                out.add(new Put(p, a == b ? (a % 3 == 1 ? RuinBuilder.glow() : RuinBuilder.panel()) : RuinBuilder.trim(), How.FORCE));
                for (int up = 1; up <= 2; up++) out.add(new Put(p.above(up), air, How.CLEAR_RIG));
            }
        }
        // the gate where the bridge leaves the stair: two posts and a lintel, a light on each post
        int gx = l.bx(), gz = l.bz();
        int[][] posts = {{BRIDGE - 2, BRIDGE}, {BRIDGE, BRIDGE - 2}};
        for (int[] q : posts) {
            BlockPos base = new BlockPos(cx + gx * q[0], deckY, cz + gz * q[1]);
            out.add(new Put(base, RuinBuilder.plating(), How.FORCE));
            for (int up = 1; up <= 4; up++) out.add(new Put(base.above(up), RuinBuilder.pillar(), How.FORCE));
            out.add(new Put(base.above(5), RuinBuilder.glow(), How.FORCE));
            out.add(new Put(base.above(6), RuinBuilder.trim(), How.FORCE));
        }
        out.add(new Put(new BlockPos(cx + gx * (BRIDGE - 1), deckY + 6, cz + gz * (BRIDGE - 1)), RuinBuilder.trim(), How.FORCE));
    }

    /**
     * The cabin made the wings' hall: two opposite corners opened as portals with pillars for jambs ({@code sx, sz} and
     * its opposite), a rail round the case (the client draws the glass over it), light under its corners, and the mark
     * under the core last.
     */
    static void hall(Layout l, int sx, int sz, List<Put> out) {
        int cx = l.core().getX(), cz = l.core().getZ(), deckY = l.core().getY() - 1;
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int s = -1; s <= 1; s += 2) {
            int px = sx * s, pz = sz * s;
            int[][] open = {{4, 4}, {3, 4}, {4, 3}, {2, 4}, {4, 2}};
            for (int[] o : open) for (int up = 1; up <= 4; up++) out.add(new Put(new BlockPos(cx + px * o[0], deckY + up, cz + pz * o[1]), air, How.FORCE));
            int[][] jambs = {{1, 4}, {4, 1}};
            for (int[] j : jambs) for (int up = 1; up <= 4; up++) out.add(new Put(new BlockPos(cx + px * j[0], deckY + up, cz + pz * j[1]), RuinBuilder.pillar(), How.FORCE));
            // the threshold: light in the floor
            out.add(new Put(new BlockPos(cx + px * 4, deckY, cz + pz * 4), RuinBuilder.glow(), How.FORCE));
        }
        BlockState wall = Blocks.POLISHED_DEEPSLATE_WALL.defaultBlockState();
        for (int pass = 0; pass < 2; pass++) {
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != 2) continue;
                out.add(new Put(new BlockPos(cx + dx, deckY + 1, cz + dz), wall, pass == 0 ? How.FREE : How.JOIN));
            }
        }
        for (int dx = -2; dx <= 2; dx += 4) for (int dz = -2; dz <= 2; dz += 4) out.add(new Put(new BlockPos(cx + dx, deckY, cz + dz), RuinBuilder.glow(), How.FORCE));
        out.add(new Put(l.core(), AscentContent.WING_CORE.get().defaultBlockState(), How.FORCE));
        out.add(new Put(l.core().below(), RuinBuilder.glow(), How.FORCE));
    }

    /** The first rig's chain, rings and perch, to take away (its pier stays: it is the new one's). */
    static void old(int cx, int deckY, int cz, int groundUnderChain, List<Put> out) {
        BlockState air = Blocks.AIR.defaultBlockState();
        int chainX = cx + PIER, chainZ = cz + PIER, topRing = deckY - OLD_TOP_GAP, bottomRing = Math.min(groundUnderChain + OLD_FIRST_RING, topRing);
        int gaps = Math.max(0, (int) Math.ceil((topRing - bottomRing) / (double) OLD_RING_STEP));
        int[][] dirs = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
        int lowest = topRing;
        for (int k = 0; k <= gaps; k++) {
            int y = gaps == 0 ? topRing : topRing - (int) Math.round((topRing - bottomRing) * k / (double) gaps);
            lowest = Math.min(lowest, y);
            int[] d = dirs[k & 3];
            for (int i = 1; i <= OLD_RING_OFFSET; i++) out.add(new Put(new BlockPos(chainX + d[0] * i, y, chainZ + d[1] * i), air, How.CLEAR_RIG));
            if (k == 0) {
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) out.add(new Put(new BlockPos(chainX + dx, y, chainZ + OLD_RING_OFFSET + dz), air, How.CLEAR_RIG));
            }
        }
        // the chain's length was laid from the ground as it was then: every chain in its column goes
        for (int y = deckY - 1; y >= lowest - OLD_RING_STEP; y--) out.add(new Put(new BlockPos(chainX, y, chainZ), air, How.CLEAR_RIG));
    }

    // ---- placing -------------------------------------------------------------------------------------------------------

    /** A stair being laid a few hundred blocks a tick. */
    private static final class Job {
        final List<Put> puts;
        int at;

        Job(List<Put> puts) {
            this.puts = puts;
        }
    }

    private static void put(ServerLevel level, Put p) {
        BlockState now = level.getBlockState(p.pos());
        int flags = Block.UPDATE_CLIENTS;
        switch (p.how()) {
            case FREE -> {
                if (now.canBeReplaced() || now.is(BlockTags.LEAVES)) level.setBlock(p.pos(), p.state(), flags);
            }
            case FORCE -> {
                if (now != p.state()) level.setBlock(p.pos(), p.state(), flags);
            }
            case PLANT -> {
                if (now.isAir()) level.setBlock(p.pos(), p.state(), flags);
            }
            case JOIN -> {
                if (now.is(p.state().getBlock())) level.setBlock(p.pos(), Block.updateFromNeighbourShapes(now, level, p.pos()), flags);
            }
            case CLEAR_GREEN -> {
                if (now.is(BlockTags.LEAVES) || now.is(BlockTags.LOGS) || now.is(Blocks.VINE)) level.setBlock(p.pos(), p.state(), flags);
            }
            case CLEAR_RIG -> {
                if (now.is(Blocks.IRON_CHAIN) || now.is(AscentContent.ANCHOR_RING.get()) || now.is(RuinBuilder.trim().getBlock())
                        || now.is(RuinBuilder.plating().getBlock())) level.setBlock(p.pos(), p.state(), flags);
            }
        }
    }

    /** ChunkEvent.Load: a sky platform starting in this chunk is queued (rigged on a later tick, its columns loaded). */
    static void onChunkLoad(ChunkEvent.Load e) {
        if (!(e.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        Map<net.minecraft.world.level.levelgen.structure.Structure, StructureStart> starts = e.getChunk().getAllStarts();
        if (starts.isEmpty()) return;
        for (var s : starts.entrySet()) {
            if (!(s.getKey() instanceof PrecursorRuinStructure prs) || prs.kind() != RuinKind.SKY || !s.getValue().isValid()) continue;
            for (StructurePiece piece : s.getValue().getPieces()) PENDING.add(piece.getBoundingBox());
        }
    }

    /** Whether this platform has today's rig (the lit panel under its core is the mark, placed last). */
    public static boolean rigged(ServerLevel level, int cx, int deckY, int cz) {
        return level.getBlockState(new BlockPos(cx, deckY + 1, cz)).is(AscentContent.WING_CORE.get())
                && level.getBlockState(new BlockPos(cx, deckY, cz)).is(RuinBuilder.glow().getBlock());
    }

    /** A queued platform whose chunks are all finished is planned (once); stairs being laid get their blocks for the tick. */
    static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        for (int n = PENDING.size(); n > 0; n--) {
            BoundingBox box = PENDING.poll();
            if (box == null) break;
            int cx = box.minX() + C, cz = box.minZ() + C, deckY = box.minY() + DECK;
            if (!finished(level, cx, cz)) {
                PENDING.add(box); // later
                break;
            }
            if (!rigged(level, cx, deckY, cz)) JOBS.add(new Job(plan(level, cx, deckY, cz, land(level))));
        }
        int budget = PER_TICK;
        while (budget > 0 && !JOBS.isEmpty()) {
            Job j = JOBS.peek();
            // a platform that unloaded under the work is left: its mark is not placed, so its next load lays it again
            if (j.at < j.puts.size() && !level.isLoaded(j.puts.get(j.at).pos())) {
                JOBS.poll();
                continue;
            }
            for (; budget > 0 && j.at < j.puts.size(); j.at++, budget--) put(level, j.puts.get(j.at));
            if (j.at >= j.puts.size()) JOBS.poll();
        }
    }

    /**
     * Whether every chunk the rig reads or builds in ({@link #SPAN} out from the centre) is loaded and finished: a
     * loaded one may still be generating (under a player flying over new land), and reading it would wait for it on the
     * server thread.
     */
    static boolean finished(ServerLevel level, int cx, int cz) {
        for (int x = cx - SPAN >> 4; x <= cx + SPAN >> 4; x++) for (int z = cz - SPAN >> 4; z <= cz + SPAN >> 4; z++) {
            if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        }
        return true;
    }

    /** The world's own ground (trees are not ground; water is, and is wet). Its chunks must be finished ({@link #finished}). */
    public static Land land(ServerLevel level) {
        return new Land() {
            @Override
            public int ground(int x, int z) {
                return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            }

            @Override
            public boolean wet(int x, int y, int z) {
                return !level.getFluidState(new BlockPos(x, y, z)).isEmpty();
            }
        };
    }

    /**
     * The ground under the first rig's chain (the lowest of four columns diagonally round it), as that rig read it: only
     * to find what it left.
     */
    static int groundUnder(Land land, int cx, int cz) {
        int g = Integer.MAX_VALUE;
        for (int dx = -5; dx <= 5; dx += 10) for (int dz = -5; dz <= 5; dz += 10) g = Math.min(g, land.ground(cx + PIER + dx, cz + PIER + dz));
        return g;
    }

    /**
     * The cabin's free corner on the bridge's side: the blueprint keeps its chest and its stand in two opposite corners
     * (whichever way it was turned), so the pier's diagonal is taken when its corners are empty, else the other one.
     */
    static int[] axis(ServerLevel level, int cx, int deckY, int cz) {
        boolean pierFree = level.getBlockState(new BlockPos(cx + 3, deckY + 1, cz + 3)).isAir() && level.getBlockState(new BlockPos(cx - 3, deckY + 1, cz - 3)).isAir();
        return pierFree ? new int[] {-1, -1} : new int[] {-1, 1};
    }

    /**
     * This platform's rig over the world's own ground: a stair already laid is read back from its rings (one in each
     * terrace's lip, top down), one not yet laid is worked out from the ground.
     */
    public static Layout layout(ServerLevel level, int cx, int deckY, int cz) {
        int[] ax = axis(level, cx, deckY, cz);
        Land land = land(level);
        if (!rigged(level, cx, deckY, cz)) return layout(cx, deckY, cz, ax[0], ax[1], land);
        Layout whole = layout(cx, deckY, cz, ax[0], ax[1], (x, z) -> Integer.MIN_VALUE / 2, MAX_TIERS + 1);
        int n = 0;
        while (n < whole.rings().size() && level.getBlockState(whole.rings().get(n)).is(AscentContent.ANCHOR_RING.get())) n++;
        return layout(cx, deckY, cz, ax[0], ax[1], land, Math.max(1, n));
    }

    /** Everything to place for this platform's rig, the mark last. */
    static List<Put> plan(ServerLevel level, int cx, int deckY, int cz, Land land) {
        int[] ax = axis(level, cx, deckY, cz);
        return plan(layout(cx, deckY, cz, ax[0], ax[1], land), land);
    }

    static List<Put> plan(Layout l, Land land) {
        int cx = l.core().getX(), cz = l.core().getZ(), deckY = l.core().getY() - 1;
        int[] ax = {l.bx(), l.bz()};
        List<Put> puts = new ArrayList<>(16384);
        old(cx, deckY, cz, groundUnder(land, cx, cz), puts);
        stair(l, puts);
        hall(l, ax[0], ax[1], puts);
        dev.awaken.Awaken.LOG.info("[ascent] sky platform at {}, {} (deck {}): a stair of {} terraces down to {}{}, the bridge on {}, {}", cx, cz, deckY,
                l.tiers(), l.y(l.tiers() - 1), l.dockY() != Integer.MIN_VALUE ? " and a dock" : "", ax[0], ax[1]);
        return puts;
    }

    /** Places a platform's whole rig now (the dev command, the tests); the world lays it over a few ticks ({@link #tick}). */
    public static Layout rig(ServerLevel level, int cx, int deckY, int cz) {
        return rig(level, cx, deckY, cz, land(level));
    }

    /** The same over a land of the caller's (a test's floor high in the air). */
    public static Layout rig(ServerLevel level, int cx, int deckY, int cz, Land land) {
        int[] ax = axis(level, cx, deckY, cz);
        Layout l = layout(cx, deckY, cz, ax[0], ax[1], land);
        for (Put p : plan(l, land)) put(level, p);
        return l;
    }

    public static void register(net.neoforged.bus.api.IEventBus bus) {
        bus.addListener(SkyRig::onChunkLoad);
        bus.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) -> tick(e.getServer()));
    }
}
