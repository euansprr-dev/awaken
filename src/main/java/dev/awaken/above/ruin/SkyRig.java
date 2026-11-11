package dev.awaken.above.ruin;

import dev.awaken.body.ascent.AscentContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A sky platform's way up and its Wings core (SPEC §17.9). Placed once, the first time the platform's chunks are
 * loaded after this shipped, so old worlds' platforms get it too and the blueprint never changes. A short pier runs out
 * from the deck's rim on the diagonal (the four torn-up islands and their tether chains hang off the deck's sides, in
 * any turn of the blueprint: the diagonal is clear of them all); from its end a chain hangs down to a rope's reach of
 * the ground (over water, a buoy to stand on: the Ascent stands aside while swimming); glowing anchor rings sit round
 * the chain every {@link #RING_STEP} blocks, a quarter turn apart, each nearly overhead from under the last (a zip up
 * and a hang under it: AscentRules.ZIP_UP_*). The top ring stands in a small perch: a zip to its side pulls the body
 * onto it, and from it a zip to the pier's side does the same. The core sits in the cabin's middle. Over a forest the
 * canopy under the first ring is opened, so a throw from the ground sees it.
 * <p>
 * The stair (11 Nov 2026, Euan: the platforms should be built to be swung up): stones of earth the platform tore up
 * as it rose spiral up round it from the ground to under the pier, each a short sling over the last ({@link #stair}),
 * a lantern hanging under each. Placed with the rig, and on its own for platforms rigged before it.
 */
public final class SkyRig {
    private SkyRig() {}

    /**
     * Ring spacing, the first ring's height over the ground, the pier's end (out along both x and z from the deck's
     * centre), the rings' offset from the chain, the top ring's depth under the deck.
     */
    public static final int RING_STEP = 22, FIRST_RING = 26, PIER = 12, RING_OFFSET = 3, TOP_GAP = 9;
    /** The pier's first cells (out along x and z): they reach back onto the deck's disc (radius 11.6). */
    static final int PIER_ROOT = 7;
    /** The blueprint's deck (local) and centre (RuinBuilder.sky). */
    static final int DECK = 12, C = 24;

    private static final ConcurrentLinkedQueue<BoundingBox> PENDING = new ConcurrentLinkedQueue<>();

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

    /** What to place, as pure geometry from the deck's centre and height and the ground under the chain. */
    /** perch: the plates round the top ring (it stands in their middle). */
    public record Layout(BlockPos core, List<BlockPos> pier, BlockPos chainTop, int chainBottom, List<BlockPos> rings, List<BlockPos> stubs,
                         List<BlockPos> perch) {}

    public static Layout layout(int cx, int deckY, int cz, int groundUnderChain) {
        BlockPos core = new BlockPos(cx, deckY + 1, cz);
        // a band three cells wide along the diagonal, out from the deck to the chain over its end (the end last)
        List<BlockPos> pier = new ArrayList<>();
        for (int a = PIER_ROOT; a <= PIER; a++) for (int b = PIER_ROOT; b <= PIER; b++) {
            if (Math.abs(a - b) <= 1 && (a != PIER || b != PIER)) pier.add(new BlockPos(cx + a, deckY, cz + b));
        }
        pier.add(new BlockPos(cx + PIER, deckY, cz + PIER));
        int chainX = cx + PIER, chainZ = cz + PIER, top = deckY - 1, bottomRing = Math.min(groundUnderChain + FIRST_RING, deckY - TOP_GAP);
        List<BlockPos> rings = new ArrayList<>(), stubs = new ArrayList<>();
        int[][] dirs = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
        // from the top ring down, evenly spaced at most RING_STEP apart down to the first one: the top one sits TOP_GAP
        // under the deck on the far side of the chain from the deck (its zip lands on the pier's end)
        int topRing = deckY - TOP_GAP, gaps = Math.max(0, (int) Math.ceil((topRing - bottomRing) / (double) RING_STEP));
        List<BlockPos> drop = new ArrayList<>();
        for (int k = 0; k <= gaps; k++) {
            int y = gaps == 0 ? topRing : topRing - (int) Math.round((topRing - bottomRing) * k / (double) gaps);
            int[] d = dirs[k & 3];
            rings.add(new BlockPos(chainX + d[0] * RING_OFFSET, y, chainZ + d[1] * RING_OFFSET));
            if (k == 0) {
                // the top is a perch to stand on (a 3x3 round the top ring), linked to the chain at its own level: from
                // the ring below, a zip to its side pulls the body onto it; from it, a zip up to the pier's side does
                // the same (from a hang right under a ring, its own fittings always sat in the last zip's way)
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dz != 0) drop.add(new BlockPos(chainX + dx, y, chainZ + RING_OFFSET + dz));
                }
                stubs.add(new BlockPos(chainX, y, chainZ + 1));
            } else {
                for (int i = 1; i < RING_OFFSET; i++) stubs.add(new BlockPos(chainX + d[0] * i, y, chainZ + d[1] * i));
            }
        }
        int chainBottom = rings.isEmpty() ? top : rings.getLast().getY();
        return new Layout(core, pier, new BlockPos(chainX, top, chainZ), chainBottom, rings, stubs, drop);
    }

    // ---- the stair (11 Nov 2026) --------------------------------------------------------------------------------

    /**
     * The sky stair's shape: the radius it climbs round the deck's centre, each step's rise and run (the straight line
     * from one stone's middle to the next), a stone's radius, the top stone's depth under the deck and its distance from
     * the centre (on the pier's diagonal, so the last pull lands on the pier), and the least a stone sits over the
     * ground under it.
     */
    public record Stair(double radius, double rise, double run, double rad, int topGap, double topRadius, int firstOver) {
        public static final Stair DEFAULT = new Stair(30, 12, 18, 3.6, 8, 29, 6);
    }

    /** A stone of the stair: the middle of its top, the height of its grass, its radius; a raft floats at the water. */
    public record Stone(int x, int top, int z, double rad, boolean raft) {}

    /**
     * The stair's stones from the top down to {@code lowest} (the helix alone; {@link #stair} cuts it at the ground): the
     * top one under the pier's end on the diagonal, each next one a step back round the deck and a rise lower, at the
     * stair's radius from the second down.
     */
    public static List<Stone> helix(int cx, int deckY, int cz, Stair st, int lowest) {
        List<Stone> out = new ArrayList<>();
        double a = Math.PI / 4, r = st.topRadius();
        int top0 = deckY - st.topGap();
        for (int k = 0; k < 64; k++) {
            int top = top0 - (int) Math.round(k * st.rise());
            if (top < lowest) break;
            out.add(new Stone(cx + (int) Math.round(r * Math.cos(a)), top, cz + (int) Math.round(r * Math.sin(a)), st.rad(), false));
            double next = st.radius();
            // the next one down, back round: its middle a run from this one's (the chord between the two radii)
            double c = Math.max(-1, Math.min(1, (r * r + next * next - st.run() * st.run()) / (2 * r * next)));
            a -= Math.acos(c);
            r = next;
        }
        return out;
    }

    /**
     * The stair as placed, bottom first: the helix down to where a stone would sit less than {@code firstOver} over
     * the ground under it ({@code ground}: the first free height of a column, as a heightmap gives it). Over water the
     * next place round gets a raft at the surface ({@code water}: whether a column's top is water), to climb out onto.
     */
    public static List<Stone> stair(int cx, int deckY, int cz, Stair st, java.util.function.IntBinaryOperator ground,
                                    java.util.function.BiPredicate<Integer, Integer> water) {
        List<Stone> kept = new ArrayList<>();
        for (Stone s : helix(cx, deckY, cz, st, -64)) {
            int g = ground.applyAsInt(s.x(), s.z());
            if (s.top() >= g + st.firstOver()) {
                kept.add(s);
                continue;
            }
            if (water.test(s.x(), s.z()) && !kept.isEmpty()) kept.add(new Stone(s.x(), g - 1, s.z(), s.rad() + 0.6, true));
            break;
        }
        return kept.reversed();
    }

    /** What a stone's cell is made of. */
    public enum Cell { GRASS, COARSE, DIRT, STONE, ANDESITE, COAL, IRON, ROOTS, FLOWER, PLATE, TRIM }

    /** One cell of a stone. */
    public interface CellSink {
        void cell(int x, int y, int z, Cell c);
    }

    /**
     * A stone's cells, the same every time (a hash of the place, no random): earth like the blueprint's islands, grass
     * on top (coarse dirt at the rim), dirt under, a keel of stone tapering down with ore in it, roots hanging under it,
     * flowers on top; a raft is a disc of plating with a trim rim. FLOWER and ROOTS have no collision.
     */
    public static void stoneCells(Stone s, CellSink out) {
        int R = (int) Math.ceil(s.rad()) + 1;
        for (int x = s.x() - R; x <= s.x() + R; x++) {
            for (int z = s.z() - R; z <= s.z() + R; z++) {
                double d = Math.sqrt((x - s.x()) * (x - s.x()) + (z - s.z()) * (z - s.z()));
                if (s.raft()) {
                    if (d <= s.rad()) out.cell(x, s.top(), z, d > s.rad() - 1 ? Cell.TRIM : Cell.PLATE);
                    continue;
                }
                d += n(x, s.top(), z) * 0.6;
                if (d > s.rad()) continue;
                // a rim of earth three deep all round (a clean face for a pull up onto it), the keel under the middle
                int depth = Math.max(3, (int) Math.round((s.rad() - d) * 1.6 + 1 + n(x, 3, z) * 1.5));
                for (int k = 0; k <= depth; k++) {
                    int y = s.top() - k;
                    Cell c;
                    if (k == 0) c = d > s.rad() - 0.8 && n(x, y, z) < 0.4 ? Cell.COARSE : Cell.GRASS;
                    else if (k <= 1) c = Cell.DIRT;
                    else {
                        double o = n(x, y, z);
                        c = o < 0.06 ? Cell.COAL : o < 0.1 ? Cell.IRON : o < 0.35 ? Cell.ANDESITE : Cell.STONE;
                    }
                    out.cell(x, y, z, c);
                }
                if (n(x, s.top() - depth - 1, z) < 0.3) out.cell(x, s.top() - depth - 1, z, Cell.ROOTS);
                if (d < s.rad() - 1 && n(x, s.top() + 1, z) < 0.25) out.cell(x, s.top() + 1, z, Cell.FLOWER);
            }
        }
    }

    /** A hash of a cell in [0, 1): the stones' shape and dress, the same on the server and in the bench. */
    static double n(int x, int y, int z) {
        long h = x * 73856093L ^ y * 19349663L ^ z * 83492791L;
        h ^= h >>> 13;
        h *= 0x5bd1e995L;
        h ^= h >>> 15;
        return (h & 0xFFFFFF) / (double) 0x1000000;
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

    /** A few a tick: rig the queued platforms whose chunks are all finished (once: the core's there after). */
    static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        ServerLevel level = server.overworld();
        for (int n = PENDING.size(); n > 0; n--) {
            BoundingBox box = PENDING.poll();
            if (box == null) return;
            int cx = box.minX() + C, cz = box.minZ() + C, deckY = box.minY() + DECK;
            if (!finished(level, cx, cz)) {
                PENDING.add(box); // later
                return;
            }
            if (!level.getBlockState(new BlockPos(cx, deckY + 1, cz)).is(AscentContent.WING_CORE.get())) rig(level, cx, deckY, cz);
            else {
                if (STAIR && !stairPlaced(level, cx, deckY, cz)) rigStair(level, cx, deckY, cz);
                if (!hallPlaced(level, cx, deckY, cz)) rigHall(level, cx, deckY, cz);
            }
        }
    }

    /**
     * Whether every chunk the rig reads or builds in (the core out to the buoy and groundUnder's columns, 17 out along
     * x and z, and the stair, {@link #STAIR_REACH} round) is loaded and finished: a loaded one may still be generating (under a player flying over new land), and
     * reading it would wait for it on the server thread.
     */
    static boolean finished(ServerLevel level, int cx, int cz) {
        int lo = STAIR ? -STAIR_REACH : 0, hi = STAIR ? STAIR_REACH : PIER + 5;
        for (int x = cx + lo >> 4; x <= cx + hi >> 4; x++) for (int z = cz + lo >> 4; z <= cz + hi >> 4; z++) {
            if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        }
        return true;
    }

    /**
     * The ground under the chain: the lowest of four columns diagonally round it (the chain's own column holds the pier
     * and the chain once rigged, and the deck or an island may hang over one of them). Its chunks must be finished
     * ({@link #finished}): it reads them as they are.
     */
    public static int groundUnder(ServerLevel level, int cx, int cz) {
        int g = Integer.MAX_VALUE;
        for (int dx = -5; dx <= 5; dx += 10) for (int dz = -5; dz <= 5; dz += 10) {
            g = Math.min(g, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx + PIER + dx, cz + PIER + dz));
        }
        return g;
    }

    /** Places the pier, the chain, the rings, the stair (while {@link #STAIR}), the core and the wings' hall. */
    public static void rig(ServerLevel level, int cx, int deckY, int cz) {
        rig(level, cx, deckY, cz, STAIR);
    }

    /** {@code stair}: with the stair down to the ground or without it (a test's rig high over other tests). */
    public static void rig(ServerLevel level, int cx, int deckY, int cz, boolean stair) {
        int ground = groundUnder(level, cx, cz);
        Layout l = layout(cx, deckY, cz, ground);
        int flags = Block.UPDATE_CLIENTS;
        BlockState plate = RuinBuilder.trim();
        for (BlockPos p : l.pier()) {
            if (level.getBlockState(p).canBeReplaced()) level.setBlock(p, plate, flags);
            // the rim's trim studs stand in the way off the pier onto the deck
            for (int up = 1; up <= 2; up++) {
                if (level.getBlockState(p.above(up)).is(plate.getBlock())) level.setBlock(p.above(up), Blocks.AIR.defaultBlockState(), flags);
            }
        }
        BlockState chainY = Blocks.IRON_CHAIN.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
        for (int y = l.chainTop().getY(); y >= l.chainBottom(); y--) {
            BlockPos p = new BlockPos(l.chainTop().getX(), y, l.chainTop().getZ());
            if (level.getBlockState(p).canBeReplaced()) level.setBlock(p, chainY, flags);
        }
        for (BlockPos s : l.stubs()) {
            Direction.Axis axis = s.getX() != l.chainTop().getX() ? Direction.Axis.X : Direction.Axis.Z;
            if (level.getBlockState(s).canBeReplaced()) level.setBlock(s, Blocks.IRON_CHAIN.defaultBlockState().setValue(BlockStateProperties.AXIS, axis), flags);
        }
        for (BlockPos d : l.perch()) level.setBlock(d, Math.abs(d.getX() - l.chainTop().getX()) == 1 && Math.abs(d.getZ() - l.chainTop().getZ() - RING_OFFSET) == 1
                ? RuinBuilder.trim() : RuinBuilder.plating(), flags);
        BlockState ring = AscentContent.ANCHOR_RING.get().defaultBlockState();
        for (BlockPos r : l.rings()) level.setBlock(r, ring, flags);
        // over water (the Ascent stands aside while swimming): a buoy to stand on and throw from, under the first ring
        BlockPos first = l.rings().isEmpty() ? null : l.rings().getLast();
        if (first != null && !level.getFluidState(new BlockPos(first.getX(), ground - 1, first.getZ())).isEmpty()) {
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                boolean rim = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                level.setBlock(new BlockPos(first.getX() + dx, ground - 1, first.getZ() + dz), rim ? plate : RuinBuilder.plating(), flags);
            }
        }
        // over a forest: the canopy under the first ring opened (leaves and vines, 2 round its column) so a throw from
        // the ground sees it
        if (first != null) {
            int opened = 0;
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int y = ground; y < first.getY(); y++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz > 5) continue;
                m.set(first.getX() + dx, y, first.getZ() + dz);
                if (!level.isLoaded(m)) continue;
                BlockState b = level.getBlockState(m);
                if (b.is(net.minecraft.tags.BlockTags.LEAVES) || b.is(Blocks.VINE)) {
                    level.setBlock(m, Blocks.AIR.defaultBlockState(), flags);
                    opened++;
                }
            }
            if (opened > 0) dev.awaken.Awaken.LOG.info("[ascent] sky platform at {}, {}: {} leaves cleared under the first ring", cx, cz, opened);
        }
        if (stair) rigStair(level, cx, deckY, cz);
        level.setBlock(l.core(), AscentContent.WING_CORE.get().defaultBlockState(), flags);
        rigHall(level, cx, deckY, cz);
        dev.awaken.Awaken.LOG.info("[ascent] sky platform rigged at {}, {} (deck {}): {} rings down to {} (ground {})", cx, cz, deckY,
                l.rings().size(), l.chainBottom(), ground);
    }

    // ---- the wings' hall (11 Nov 2026) ---------------------------------------------------------------------------

    /** Whether the cabin is the wings' hall yet: the lit panel under the core is its mark (placed last). */
    public static boolean hallPlaced(ServerLevel level, int cx, int deckY, int cz) {
        return level.getBlockState(new BlockPos(cx, deckY, cz)).is(RuinBuilder.glow().getBlock());
    }

    /**
     * Which of the cabin's diagonals has its inner corners empty. The blueprint keeps its chest and its stand in two
     * opposite corners whichever way it was turned: 1 when the pier's diagonal (+x +z, -x -z) is free, so the pier
     * runs straight in at a portal; -1 when the other is (+x -z, -x +z), a quarter turn round the deck from the pier.
     */
    public static int hallAxis(ServerLevel level, int cx, int deckY, int cz) {
        return level.getBlockState(new BlockPos(cx + 3, deckY + 1, cz + 3)).isAir() && level.getBlockState(new BlockPos(cx - 3, deckY + 1, cz - 3)).isAir() ? 1 : -1;
    }

    /**
     * The cabin made the hall where the wings hang (SPEC §17.9, "The wings on show"; the client's WingCaseRenderer
     * draws the pair and the case's glass): two opposite corners opened as portals four high with pillars for jambs,
     * light in the threshold and a soul lantern hung in the head; a rail of wall round the core two out, which the case's glass stands on (nobody
     * walks into the case), light in the deck under its corners; a line of light down the pier's middle; and the mark
     * under the core last. Placed with the rig, and on its own for platforms rigged before it.
     */
    public static void rigHall(ServerLevel level, int cx, int deckY, int cz) {
        int flags = Block.UPDATE_CLIENTS, axis = hallAxis(level, cx, deckY, cz);
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int s = -1; s <= 1; s += 2) {
            int px = s, pz = s * axis;
            int[][] open = {{4, 4}, {3, 4}, {4, 3}, {2, 4}, {4, 2}}, jambs = {{1, 4}, {4, 1}};
            for (int up = 1; up <= 4; up++) {
                for (int[] o : open) level.setBlock(new BlockPos(cx + px * o[0], deckY + up, cz + pz * o[1]), air, flags);
                for (int[] j : jambs) level.setBlock(new BlockPos(cx + px * j[0], deckY + up, cz + pz * j[1]), RuinBuilder.pillar(), flags);
            }
            level.setBlock(new BlockPos(cx + px * 4, deckY, cz + pz * 4), RuinBuilder.glow(), flags);
            // a lamp of the Signal's colour hung in the portal's head (three clear under it)
            level.setBlock(new BlockPos(cx + px * 4, deckY + 4, cz + pz * 4), Blocks.SOUL_LANTERN.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true), flags);
        }
        BlockState wall = Blocks.POLISHED_DEEPSLATE_WALL.defaultBlockState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        // the rail, then each piece joined to its neighbours (a block set from code takes no shape by itself)
        for (int pass = 0; pass < 2; pass++) {
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != 2) continue;
                m.set(cx + dx, deckY + 1, cz + dz);
                BlockState now = level.getBlockState(m);
                if (pass == 0 && now.canBeReplaced()) level.setBlock(m, wall, flags);
                else if (pass == 1 && now.is(wall.getBlock())) level.setBlock(m, Block.updateFromNeighbourShapes(now, level, m), flags);
            }
        }
        for (int dx = -2; dx <= 2; dx += 4) for (int dz = -2; dz <= 2; dz += 4) level.setBlock(new BlockPos(cx + dx, deckY, cz + dz), RuinBuilder.glow(), flags);
        // the pier's middle: a lamp every third cell (its end stays brass: the first flight's last step)
        for (int a = PIER_ROOT; a < PIER; a += 3) level.setBlock(new BlockPos(cx + a, deckY, cz + a), RuinBuilder.glow(), flags);
        level.setBlock(new BlockPos(cx, deckY, cz), RuinBuilder.glow(), flags);
        dev.awaken.Awaken.LOG.info("[ascent] sky platform at {}, {}: the wings' hall, its portals on the {} diagonal", cx, cz, axis > 0 ? "pier's" : "other");
    }

    /** How far round the deck's centre the stair builds (its radius and a stone's, and the canopy cleared round it). */
    static final int STAIR_REACH = 40;
    /**
     * Whether the rig builds the stair. Off until Euan has seen the climb filmed (11 Nov 2026): a laid stair stays in a
     * saved world for good, and nobody has seen one yet. {@code /awaken ascent sky goto stair} builds it on the platform
     * it goes to either way, so it can be looked at before it is turned on.
     */
    public static final boolean STAIR = false;

    /** Whether the stair is up: its top stone's grass is there (the top one is always kept, the ground never reaches it). */
    public static boolean stairPlaced(ServerLevel level, int cx, int deckY, int cz) {
        Stone top = helix(cx, deckY, cz, Stair.DEFAULT, deckY - 64).getFirst();
        return !level.getBlockState(new BlockPos(top.x(), top.top(), top.z())).isAir();
    }

    /**
     * Places the stair ({@link #stair} on the land as it is): each stone's cells where nothing solid stands (leaves and
     * vines give way), a lantern hanging under its keel, and for the stones low enough to stand in a forest, the canopy
     * opened round each one and along the way up to the next, so the rope sees them and the body has room.
     */
    public static void rigStair(ServerLevel level, int cx, int deckY, int cz) {
        List<Stone> stones = stairOn(level, cx, deckY, cz);
        int flags = Block.UPDATE_CLIENTS, opened = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < stones.size(); i++) {
            Stone s = stones.get(i);
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, s.x(), s.z());
            if (s.top() - ground < 40) {
                // the canopy round the stone and along the way to the next one up
                Stone up = i + 1 < stones.size() ? stones.get(i + 1) : s;
                for (int k = 0; k <= 12; k++) {
                    double f = k / 12.0, px = s.x() + (up.x() - s.x()) * f, pz = s.z() + (up.z() - s.z()) * f;
                    int py = (int) Math.round(s.top() + (up.top() - s.top()) * f), r = k == 0 ? (int) Math.ceil(s.rad()) + 2 : 3;
                    for (int x = (int) px - r; x <= (int) px + r; x++) for (int z = (int) pz - r; z <= (int) pz + r; z++) {
                        if ((x - px) * (x - px) + (z - pz) * (z - pz) > r * r) continue;
                        for (int y = k == 0 ? ground : py - 3; y <= py + 4; y++) {
                            m.set(x, y, z);
                            BlockState b = level.getBlockState(m);
                            if (b.is(net.minecraft.tags.BlockTags.LEAVES) || b.is(Blocks.VINE)) {
                                level.setBlock(m, Blocks.AIR.defaultBlockState(), flags);
                                opened++;
                            }
                        }
                    }
                }
            }
            int[] bottom = {s.top()};
            stoneCells(s, (x, y, z, c) -> {
                m.set(x, y, z);
                BlockState here = level.getBlockState(m);
                boolean free = here.canBeReplaced() || here.is(net.minecraft.tags.BlockTags.LEAVES) || here.is(Blocks.VINE);
                if (!free) return;
                if ((c == Cell.FLOWER || c == Cell.ROOTS) && !here.isAir()) return;
                level.setBlock(m, cellState(c, x, y, z), flags);
                if (x == s.x() && z == s.z() && c != Cell.ROOTS) bottom[0] = Math.min(bottom[0], y);
            });
            if (!s.raft()) {
                // a lantern on a short chain under the keel's tip: the stair reads as a spiral of lights at night
                for (int k = 1; k <= 2; k++) {
                    m.set(s.x(), bottom[0] - k, s.z());
                    if (level.getBlockState(m).canBeReplaced()) level.setBlock(m, Blocks.IRON_CHAIN.defaultBlockState(), flags);
                }
                m.set(s.x(), bottom[0] - 3, s.z());
                if (level.getBlockState(m).canBeReplaced()) {
                    level.setBlock(m, Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true), flags);
                }
            }
        }
        if (opened > 0) dev.awaken.Awaken.LOG.info("[ascent] sky stair at {}, {}: {} leaves cleared", cx, cz, opened);
        dev.awaken.Awaken.LOG.info("[ascent] sky stair placed at {}, {}: {} stones from {} up", cx, cz, stones.size(),
                stones.isEmpty() ? "-" : stones.getFirst().top());
    }

    /**
     * The stair's stones as they stand, bottom first: a laid stair can't be worked out from the land again (the land's
     * heights are the stones' own tops now, so {@link #stairOn} finds none), but it can be read back, the helix from
     * the top down for as long as a column's height is its stone's top.
     */
    public static List<Stone> stairStanding(ServerLevel level, int cx, int deckY, int cz) {
        List<Stone> kept = new ArrayList<>();
        for (Stone s : helix(cx, deckY, cz, Stair.DEFAULT, level.getMinY())) {
            if (level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, s.x(), s.z()) != s.top() + 1) break;
            kept.add(s);
        }
        return kept.reversed();
    }

    /** The stair's stones on the land as it is under them, bottom first ({@link #stair}): only before it is laid. */
    public static List<Stone> stairOn(ServerLevel level, int cx, int deckY, int cz) {
        return stair(cx, deckY, cz, Stair.DEFAULT, (x, z) -> level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z),
                (x, z) -> !level.getFluidState(new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z)).isEmpty());
    }

    /** The block for a stone's cell. */
    private static BlockState cellState(Cell c, int x, int y, int z) {
        return switch (c) {
            case GRASS -> Blocks.GRASS_BLOCK.defaultBlockState();
            case COARSE -> Blocks.COARSE_DIRT.defaultBlockState();
            case DIRT -> Blocks.DIRT.defaultBlockState();
            case STONE -> Blocks.STONE.defaultBlockState();
            case ANDESITE -> Blocks.ANDESITE.defaultBlockState();
            case COAL -> Blocks.COAL_ORE.defaultBlockState();
            case IRON -> Blocks.IRON_ORE.defaultBlockState();
            case ROOTS -> Blocks.HANGING_ROOTS.defaultBlockState();
            case FLOWER -> {
                double o = n(x, 5, z);
                yield o < 0.8 ? Blocks.SHORT_GRASS.defaultBlockState() : n(x, 6, z) < 0.5 ? Blocks.POPPY.defaultBlockState() : Blocks.DANDELION.defaultBlockState();
            }
            case PLATE -> RuinBuilder.plating();
            case TRIM -> RuinBuilder.trim();
        };
    }

    public static void register(net.neoforged.bus.api.IEventBus bus) {
        bus.addListener(SkyRig::onChunkLoad);
        bus.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) -> tick(e.getServer()));
    }
}
