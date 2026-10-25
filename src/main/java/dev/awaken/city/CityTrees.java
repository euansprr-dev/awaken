package dev.awaken.city;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.function.LongPredicate;
import java.util.function.Predicate;

/**
 * Trees as the groups they are: every log and leaf of some chunks, joined with the ones it touches (faces, edges and
 * corners). A group none of whose blocks rests on something solid that is not a tree hangs in the air. The scan
 * ({@link CityScan}) reports those; the writer ({@link CityWriter#collectOrphans}) removes the ones the city's own cut
 * leaves behind, where it clears a tree's trunk but not the limbs that reached out past its edge.
 */
final class CityTrees {
    private CityTrees() {}

    /** What a block of a group is (LEAF, WOOD, or OTHER for a vine, a mushroom block and the like) and, or-ed in, GROUNDED. */
    static final byte LEAF = 1, WOOD = 2, GROUNDED = 4, OTHER = 8;

    /** 0 for anything that is not a tree block, else LEAF or WOOD (any log, bark or stem in the logs tag). */
    static byte kind(BlockState s) {
        if (s.isAir()) return 0;
        if (s.is(BlockTags.LOGS)) return WOOD;
        if (s.is(BlockTags.LEAVES)) return LEAF;
        return 0;
    }

    static boolean tree(BlockState s) {
        return kind(s) != 0;
    }

    /** Something a trunk can stand on: not air, water, a plant or a tree. */
    static boolean holds(BlockState below) {
        return !below.isAir() && below.getFluidState().isEmpty() && kind(below) == 0 && !below.canBeReplaced();
    }

    /**
     * Every block of a chunk that {@code member} accepts, into {@code blocks} (keyed by {@link BlockPos#asLong}) as WOOD (a log),
     * LEAF (leaves) or OTHER, and marked GROUNDED where the block below holds. {@code woods}, when given, also learns which
     * block each wood cell is.
     */
    static void read(ChunkAccess chunk, Predicate<BlockState> member, Long2ByteOpenHashMap blocks, Long2ObjectOpenHashMap<Block> woods) {
        int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int si = 0; si < chunk.getSectionsCount(); si++) {
            LevelChunkSection sec = chunk.getSection(si);
            if (sec.hasOnlyAir() || !sec.maybeHas(member)) continue;
            int by = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(si));
            for (int ly = 0; ly < 16; ly++) {
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        BlockState s = sec.getBlockState(lx, ly, lz);
                        if (s.isAir() || !member.test(s)) continue;
                        byte k = s.is(BlockTags.LOGS) ? WOOD : s.is(BlockTags.LEAVES) ? LEAF : OTHER;
                        int y = by + ly;
                        BlockState below = ly > 0 ? sec.getBlockState(lx, ly - 1, lz) : chunk.getBlockState(pos.set(bx + lx, y - 1, bz + lz));
                        if (holds(below)) k |= GROUNDED;
                        long key = BlockPos.asLong(bx + lx, y, bz + lz);
                        blocks.put(key, k);
                        if (woods != null && (k & WOOD) != 0) woods.put(key, s.getBlock());
                    }
                }
            }
        }
    }

    /** What {@link #groups} hands over for each group; {@code cells} is reused, so read it before returning. */
    interface Visitor {
        void group(LongArrayList cells, boolean grounded, boolean clipped);
    }

    /**
     * Walks every group of {@code blocks}. A group is {@code clipped} when one of its blocks stands on the edge of a chunk
     * whose neighbour is not {@code scanned}: something unread might hold it up.
     */
    static int groups(Long2ByteOpenHashMap blocks, LongPredicate scanned, Visitor visitor) {
        LongOpenHashSet seen = new LongOpenHashSet(blocks.size());
        LongArrayList stack = new LongArrayList(), cells = new LongArrayList();
        int n = 0;
        for (var e : blocks.long2ByteEntrySet()) {
            long start = e.getLongKey();
            if (!seen.add(start)) continue;
            n++;
            stack.clear();
            cells.clear();
            stack.add(start);
            boolean grounded = false, clipped = false;
            while (!stack.isEmpty()) {
                long p = stack.removeLong(stack.size() - 1);
                cells.add(p);
                int x = BlockPos.getX(p), y = BlockPos.getY(p), z = BlockPos.getZ(p);
                if ((blocks.get(p) & GROUNDED) != 0) grounded = true;
                if (!clipped && ((x & 15) == 0 || (x & 15) == 15 || (z & 15) == 0 || (z & 15) == 15)) {
                    for (int s = 0; s < 4 && !clipped; s++) {
                        int ax = x + (s == 0 ? 1 : s == 1 ? -1 : 0), az = z + (s == 2 ? 1 : s == 3 ? -1 : 0);
                        if (!scanned.test(net.minecraft.world.level.ChunkPos.pack(ax >> 4, az >> 4))) clipped = true;
                    }
                }
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            long q = BlockPos.asLong(x + dx, y + dy, z + dz);
                            if (blocks.containsKey(q) && seen.add(q)) stack.add(q);
                        }
                    }
                }
            }
            visitor.group(cells, grounded, clipped);
        }
        return n;
    }
}
