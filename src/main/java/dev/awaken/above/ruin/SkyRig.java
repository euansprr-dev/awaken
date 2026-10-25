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

    /** A few a tick: rig the queued platforms whose core and chain columns are loaded (once: the core's there after). */
    static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        ServerLevel level = server.overworld();
        for (int n = PENDING.size(); n > 0; n--) {
            BoundingBox box = PENDING.poll();
            if (box == null) return;
            int cx = box.minX() + C, cz = box.minZ() + C, deckY = box.minY() + DECK;
            BlockPos core = new BlockPos(cx, deckY + 1, cz), chain = new BlockPos(cx + PIER, deckY, cz + PIER);
            if (!level.isLoaded(core) || !level.isLoaded(chain)) {
                PENDING.add(box); // later
                return;
            }
            if (!level.getBlockState(core).is(AscentContent.WING_CORE.get())) rig(level, cx, deckY, cz);
        }
    }

    /**
     * The ground under the chain: the lowest of four columns diagonally round it (the chain's own column holds the pier
     * and the chain once rigged, and the deck or an island may hang over one of them).
     */
    public static int groundUnder(ServerLevel level, int cx, int cz) {
        int g = Integer.MAX_VALUE;
        for (int dx = -5; dx <= 5; dx += 10) for (int dz = -5; dz <= 5; dz += 10) {
            g = Math.min(g, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx + PIER + dx, cz + PIER + dz));
        }
        return g;
    }

    /** Places the pier, the chain, the rings and the core. */
    public static void rig(ServerLevel level, int cx, int deckY, int cz) {
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
        level.setBlock(l.core(), AscentContent.WING_CORE.get().defaultBlockState(), flags);
        dev.awaken.Awaken.LOG.info("[ascent] sky platform rigged at {}, {} (deck {}): {} rings down to {} (ground {})", cx, cz, deckY,
                l.rings().size(), l.chainBottom(), ground);
    }

    public static void register(net.neoforged.bus.api.IEventBus bus) {
        bus.addListener(SkyRig::onChunkLoad);
        bus.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) -> tick(e.getServer()));
    }
}
