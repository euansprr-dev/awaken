package dev.awaken.caves;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.awaken.Awaken;
import dev.awaken.caves.skin.CaveSkinFeature;
import dev.awaken.caves.skin.Settle;
import dev.awaken.core.api.Passage;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * {@code /awaken caves floaters [radius]} (admin; for tours, SPEC §42.4 "Settled"): counts, in the land round where it runs,
 * every block under the ground that nothing holds. The game's own rule says what holds a block ({@code canSurvive}: a lichen's
 * faces each need rock behind them, a heap the floor under it, a stalactite the roof); a web or a hanging chain that touches
 * nothing counts too. A lichen with some faces held and some not is "partial" (a face sticking out of a cut-away wall); with
 * none held it is "loose". Only chunks that have started ticking are read (the world-gen re-check has run on them by then);
 * the rest are counted as not settled. Each kind with any goes to the log with a few of its cells, and how many of its
 * missing holds lie across a chunk's edge (the mark of a neighbour cutting the rock after the dressing leaned on it).
 * <p>
 * {@code /awaken caves floaters <radius> look <pick>} then stands the player where it can see the pick-th of them (the skin's
 * first, loose before partial, nearest first; each at least {@link RegionCommands#APART} from the ones before), for a picture;
 * the spot goes to the log.
 * <p>
 * {@code /awaken caves resettle [radius]} runs the re-check on the same chunks now: every settling piece has its shape asked
 * again, as the game's own pass does when a chunk is made or an old one loads ({@link Settle}), with the change sent to the
 * players; a second census shows what that left.
 */
final class Floaters {
    private Floaters() {}

    /** The farthest a census reads (chunks), and how many cells of each kind go to the log. */
    static final int MAX_RADIUS = 12, LISTED = 6;

    private static final class Kind {
        final Block block;
        int seen, loose, partial, across, edge;
        final List<BlockPos> cells = new ArrayList<>();

        Kind(Block block) {
            this.block = block;
        }

        int bad() {
            return loose + partial;
        }
    }

    /** One unsupported piece, for a picture: the skin's first, loose before partial. */
    private record Floater(BlockPos at, boolean ours, boolean loose) {}

    static int census(CommandSourceStack src, int radius, int look) {
        ServerLevel level = src.getLevel();
        BlockPos from = BlockPos.containing(src.getPosition());
        int cx0 = from.getX() >> 4, cz0 = from.getZ() >> 4;
        Map<Block, Kind> kinds = new IdentityHashMap<>();
        Map<BlockState, Boolean> looked = new IdentityHashMap<>();
        List<Floater> all = new ArrayList<>();
        int read = 0, unsettled = 0;
        long t0 = System.nanoTime();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx0 + dx, cz0 + dz);
                if (chunk == null) continue;
                if (!level.getChunkSource().isPositionTicking(ChunkPos.pack(cx0 + dx, cz0 + dz))) {
                    unsettled++;
                    continue;
                }
                read++;
                scan(level, chunk, kinds, looked, all);
            }
        }
        List<Kind> bad = new ArrayList<>(kinds.values().stream().filter(k -> k.bad() > 0).toList());
        bad.sort(Comparator.comparingInt(Kind::bad).reversed());
        int total = kinds.values().stream().mapToInt(k -> k.seen).sum(), floating = bad.stream().mapToInt(Kind::bad).sum();
        int ours = kinds.values().stream().filter(k -> CaveSkinFeature.skin(k.block)).mapToInt(k -> k.seen).sum();
        int oursBad = bad.stream().filter(k -> CaveSkinFeature.skin(k.block)).mapToInt(Kind::bad).sum();
        long ms = (System.nanoTime() - t0) / 1_000_000;
        Awaken.LOG.info(String.format(Locale.ROOT,
                "[caves] floaters at %d %d %d r %d: %d chunks read (%d not settled yet, skipped); %d unsupported of %d hanging or standing pieces under the ground; ours (the skin) %d of %d; %d ms",
                from.getX(), from.getY(), from.getZ(), radius, read, unsettled, floating, total, oursBad, ours, ms));
        for (Kind k : bad) {
            StringBuilder cells = new StringBuilder();
            for (BlockPos p : k.cells) cells.append(cells.isEmpty() ? "" : "; ").append(p.getX()).append(' ').append(p.getY()).append(' ').append(p.getZ());
            Awaken.LOG.info(String.format(Locale.ROOT, "[caves] floaters   %s: %d of %d (%d loose, %d partial; %d leaning across a chunk edge, %d on an edge column) at %s",
                    BuiltInRegistries.BLOCK.getKey(k.block), k.bad(), k.seen, k.loose, k.partial, k.across, k.edge, cells));
        }
        int f = floating, o = oursBad, r = read, u = unsettled;
        src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Floaters: %d of %d pieces in %d settled chunks (%d ours), %d chunks not settled yet",
                f, total, r, o, u)).withColor(f == 0 ? 0x9FE6D6 : 0xE6B89F), false);
        return look < 0 ? Math.max(1, floating) : look(src, level, from, all, look);
    }

    /** To a stand that sees the pick-th floater; the spot goes to the log. */
    private static int look(CommandSourceStack src, ServerLevel level, BlockPos from, List<Floater> all, int pick) {
        if (!(src.getEntity() instanceof ServerPlayer p)) return 0;
        all.sort(Comparator.comparing((Floater f) -> !f.ours).thenComparing(f -> !f.loose).thenComparingDouble(f -> f.at.distSqr(from)));
        List<BlockPos> picks = new ArrayList<>();
        for (Floater f : all) {
            if (!picks.stream().allMatch(q -> q.distSqr(f.at) >= RegionCommands.APART * RegionCommands.APART)) continue;
            Direction open = openest(level, f.at);
            RegionCommands.Stand s = RegionCommands.stand(level, f.at, open.getOpposite(), 4);
            if (s == null) continue;
            picks.add(f.at);
            if (picks.size() > pick) {
                p.teleportTo(level, s.x(), s.y(), s.z(), Set.of(), s.yaw(), s.pitch(), true);
                Awaken.LOG.info(String.format(Locale.ROOT, "[caves] floaters look %d: stand %.2f %.2f %.2f %.1f %.1f, looking at %s %s",
                        pick, s.x(), s.y(), s.z(), s.yaw(), s.pitch(), BuiltInRegistries.BLOCK.getKey(level.getBlockState(f.at).getBlock()), f.at.toShortString()));
                return 1;
            }
        }
        Awaken.LOG.info("[caves] floaters look {}: only {} of {} can be seen", pick, picks.size(), all.size());
        src.sendFailure(Component.literal("Only " + picks.size() + " floaters in view here, of " + all.size() + "."));
        return 0;
    }

    /** The side with the longest run of open cells (where to stand to look back at it). */
    private static Direction openest(ServerLevel level, BlockPos at) {
        Direction best = Direction.NORTH;
        int bestRun = -1;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int run = 0;
            while (run < 6 && level.getBlockState(m.setWithOffset(at, d.getStepX() * (run + 1), 0, d.getStepZ() * (run + 1))).getCollisionShape(level, m).isEmpty()) run++;
            if (run > bestRun) {
                bestRun = run;
                best = d;
            }
        }
        return best;
    }

    private static void scan(ServerLevel level, LevelChunk chunk, Map<Block, Kind> kinds, Map<BlockState, Boolean> looked, List<Floater> all) {
        LevelChunkSection[] sections = chunk.getSections();
        int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
        int[] tops = new int[256];
        for (int i = 0; i < 256; i++) tops[i] = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, i & 15, i >> 4) - CaveSkinFeature.UNDER_GROUND;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int si = 0; si < sections.length; si++) {
            LevelChunkSection section = sections[si];
            if (section.hasOnlyAir() || !section.maybeHas(s -> counted(s, looked))) continue;
            int y0 = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(si));
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (y0 + y >= tops[z << 4 | x]) continue;
                        BlockState s = section.getBlockState(x, y, z);
                        if (!counted(s, looked)) continue;
                        Kind k = kinds.computeIfAbsent(s.getBlock(), Kind::new);
                        k.seen++;
                        m.set(bx + x, y0 + y, bz + z);
                        int verdict = verdict(level, m, s);
                        if (verdict == 0) continue;
                        if (verdict == LOOSE) k.loose++;
                        else k.partial++;
                        if (across(level, m, s)) k.across++;
                        if (x == 0 || x == 15 || z == 0 || z == 15) k.edge++;
                        if (k.cells.size() < LISTED) k.cells.add(m.immutable());
                        all.add(new Floater(m.immutable(), CaveSkinFeature.skin(s.getBlock()), verdict == LOOSE));
                    }
                }
            }
        }
    }

    /** A piece that hangs on or stands on something: not air, not a liquid, not a full block. */
    private static boolean counted(BlockState s, Map<BlockState, Boolean> looked) {
        Boolean b = looked.get(s);
        if (b == null) {
            b = !s.isAir() && !(s.getBlock() instanceof LiquidBlock) && !s.isSolidRender();
            looked.put(s, b);
        }
        return b;
    }

    static final int HELD = 0, LOOSE = 1, PARTIAL = 2;

    /** Held, loose (nothing holds it) or partial (a lichen with some faces in the air). */
    static int verdict(ServerLevel level, BlockPos pos, BlockState s) {
        Block b = s.getBlock();
        if (b instanceof MultifaceBlock) {
            int faces = 0, held = 0;
            for (Direction d : Direction.values()) {
                if (!MultifaceBlock.hasFace(s, d)) continue;
                faces++;
                if (MultifaceBlock.canAttachTo(level, pos, d)) held++;
            }
            return held == faces && faces > 0 ? HELD : held == 0 ? LOOSE : PARTIAL;
        }
        if (!s.canSurvive(level, pos)) return LOOSE;
        if (b instanceof WebBlock) {
            for (Direction d : Direction.values()) {
                BlockPos n = pos.relative(d);
                if (level.getBlockState(n).isFaceSturdy(level, n, d.getOpposite())) return HELD;
            }
            return LOOSE;
        }
        if (b instanceof ChainBlock && s.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y) {
            BlockPos up = pos.above();
            BlockState a = level.getBlockState(up);
            return a.getBlock() instanceof ChainBlock || a.isFaceSturdy(level, up, Direction.DOWN) ? HELD : LOOSE;
        }
        return HELD;
    }

    /** Does a missing hold lie in the next chunk (a lichen's empty face, or the cell under a floor piece or over a hanging one)? */
    private static boolean across(ServerLevel level, BlockPos pos, BlockState s) {
        if (s.getBlock() instanceof MultifaceBlock) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (!MultifaceBlock.hasFace(s, d) || MultifaceBlock.canAttachTo(level, pos, d)) continue;
                if ((pos.getX() + d.getStepX() >> 4) != pos.getX() >> 4 || (pos.getZ() + d.getStepZ() >> 4) != pos.getZ() >> 4) return true;
            }
            return false;
        }
        // a wall piece: its hold is on one side of it (which side its facing names differs by block, so either counts)
        Direction d = s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? s.getValue(BlockStateProperties.HORIZONTAL_FACING)
                : s.hasProperty(BlockStateProperties.FACING) ? s.getValue(BlockStateProperties.FACING) : null;
        if (d == null || d.getAxis() == Direction.Axis.Y) return false;
        return (pos.getX() + d.getStepX() >> 4) != pos.getX() >> 4 || (pos.getZ() + d.getStepZ() >> 4) != pos.getZ() >> 4
                || (pos.getX() - d.getStepX() >> 4) != pos.getX() >> 4 || (pos.getZ() - d.getStepZ() >> 4) != pos.getZ() >> 4;
    }

    static int resettle(CommandSourceStack src, int radius) {
        ServerLevel level = src.getLevel();
        BlockPos from = BlockPos.containing(src.getPosition());
        int cx0 = from.getX() >> 4, cz0 = from.getZ() >> 4, chunks = 0, checked = 0, changed = 0;
        long t0 = System.nanoTime();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx0 + dx, cz0 + dz);
                // ticking: its eight neighbours are loaded, as when the game runs the pass itself
                if (chunk == null || !level.getChunkSource().isPositionTicking(ChunkPos.pack(cx0 + dx, cz0 + dz))) continue;
                long n = settleNow(level, chunk);
                checked += (int) (n >> 32);
                changed += (int) n;
                chunks++;
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        int c = chunks, k = checked, g = changed;
        Awaken.LOG.info("[caves] resettle at {} r {}: {} pieces re-checked in {} chunks, {} changed (faces off or gone), {} ms",
                from.toShortString(), radius, k, c, g, ms);
        src.sendSuccess(() -> Component.literal("Re-checked " + k + " pieces in " + c + " chunks: " + g + " changed").withColor(0x9FE6D6), false);
        return Math.max(1, chunks);
    }

    /**
     * The world-gen re-check ({@link LevelChunk#postProcessGeneration}'s pass, the same shape update on every piece {@link Settle#mark}
     * would list), told to the players: unlike a chunk being made, this one is already on their screens. Checked count in the
     * high half, changed in the low.
     */
    private static long settleNow(ServerLevel level, LevelChunk chunk) {
        int checked = 0, changed = 0;
        LevelChunkSection[] sections = chunk.getSections();
        int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int si = 0; si < sections.length; si++) {
            LevelChunkSection section = sections[si];
            if (section.hasOnlyAir() || !section.maybeHas(Settle::settles)) continue;
            int y0 = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(si));
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState s = section.getBlockState(x, y, z);
                        if (!Settle.settles(s)) continue;
                        checked++;
                        BlockState t = Block.updateFromNeighbourShapes(s, level, m.set(bx + x, y0 + y, bz + z));
                        if (t == s) continue;
                        level.setBlock(m.immutable(), t, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
                        changed++;
                    }
                }
            }
        }
        return (long) checked << 32 | changed;
    }

    /** {@code /awaken caves at <x> <y> <z>} (admin; for tours): to that cell through the passage (its land made first). */
    static int at(CommandSourceStack src, int x, int y, int z) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        ServerLevel level = p.level();
        Passage.go(p, level, x + 0.5, z + 0.5, () -> {
            p.teleportTo(level, x + 0.5, y, z + 0.5, Set.of(), p.getYRot(), p.getXRot(), true);
            Awaken.LOG.info("[caves] at {} {} {}", x, y, z);
        });
        return 1;
    }
}
