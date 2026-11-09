package dev.awaken.delve;

import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.neoforge.common.Tags;

/**
 * {@code awaken:lodes}: each overworld chunk writes its share of the lodes planned round it ({@link LodePlans}), at the
 * underground ores step, after the carvers, so a cave cuts across a seam and shows it on its wall. Only rock is
 * replaced (stone with the ore, deepslate with its deep twin, another ore with this one); air, water and anything
 * built stays. The heart goes in at the seam's centre. Pure and per chunk: nothing outside the chunk is touched.
 */
public record LodeFeature() implements Feature {
    public static final MapCodec<LodeFeature> CODEC = MapCodec.unit(LodeFeature::new);

    @Override
    public MapCodec<LodeFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        if (level.getLevel().dimension() != Level.OVERWORLD) return false;
        int x0 = (origin.getX() >> 4) << 4, z0 = (origin.getZ() >> 4) << 4;
        boolean any = false;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (Lode l : LodePlans.touching(level.getSeed(), x0, z0, x0 + 15, z0 + 15, false)) {
            for (long c : l.cells()) {
                int x = BlockPos.getX(c), z = BlockPos.getZ(c);
                if (x < x0 || x > x0 + 15 || z < z0 || z > z0 + 15) continue;
                m.set(x, BlockPos.getY(c), z);
                BlockState s = level.getBlockState(m);
                Block put = rockFor(l.ore(), s, c == l.heart());
                if (put == null) continue;
                level.setBlock(m, c == l.heart() ? LodeHeartBlock.in(put.defaultBlockState(), s) : put.defaultBlockState(), Block.UPDATE_CLIENTS);
                any = true;
            }
        }
        // a heart near this chunk shows on the rock round it, and this chunk writes its share
        for (Lode l : LodePlans.touching(level.getSeed(), x0 - TRACE_NEAR, z0 - TRACE_NEAR, x0 + 15 + TRACE_NEAR, z0 + 15 + TRACE_NEAR, false)) {
            // a lode that touches this window can have its heart far off: only one this near may be read from this chunk
            BlockPos heart = l.heartPos();
            if (heart.getX() < x0 - TRACE_NEAR || heart.getX() > x0 + 15 + TRACE_NEAR || heart.getZ() < z0 - TRACE_NEAR || heart.getZ() > z0 + 15 + TRACE_NEAR) continue;
            any |= trace(level, level.getSeed(), l.ore(), heart, x0, z0);
        }
        return any;
    }

    /** How far a trace can reach from its heart: a chunk writes the traces of the hearts this near it. */
    static final int TRACE_NEAR = 6;
    private static final int[] BACK = {1, 0, 3, 2};

    /**
     * The ore showing through the rock round a heart (SPEC §42.11): on every face of the heart open to the air, a vein leaves each
     * of its four edges and runs two to five blocks across the rock, wandering, each block joined to the last. Rock and air
     * decide where it can go and a hash of the seed and the heart does the rest, so the chunks either side agree. Only the
     * chunk at (x0, z0) is written.
     */
    static boolean trace(WorldGenLevel level, long seed, Ore ore, BlockPos heart, int x0, int z0) {
        if (rockFor(ore, level.getBlockState(heart), true) == null) return false;
        boolean any = false;
        for (Direction out : Direction.values()) {
            if (level.getBlockState(heart.relative(out)).isAir()) any |= face(level, seed, ore, heart, out, x0, z0);
        }
        return any;
    }

    private static boolean face(WorldGenLevel level, long seed, Ore ore, BlockPos heart, Direction out, int x0, int z0) {
        Direction facing = out.getOpposite();
        Long2IntOpenHashMap masks = new Long2IntOpenHashMap(), reaches = new Long2IntOpenHashMap();
        reaches.defaultReturnValue(1);
        long h = mix(seed ^ heart.asLong() * 0x9E3779B97F4A7C15L ^ out.ordinal());
        for (int port = 0; port < 4; port++) {
            h = mix(h + port);
            // not every edge: four arms would read as a cross
            if (port > 1 && Long.remainderUnsigned(h, 3) == 0) continue;
            int steps = 2 + (int) (Long.remainderUnsigned(h >>> 8, 4)), heading = port;
            BlockPos last = heart;
            long lastKey = Long.MIN_VALUE;
            for (int i = 0; i < steps; i++) {
                BlockPos rock = last.relative(LodeTraceBlock.side(facing, heading)), cell = rock.relative(out);
                BlockState behind = level.getBlockState(rock);
                if (!(behind.is(BlockTags.BASE_STONE_OVERWORLD) || behind.is(Tags.Blocks.ORES)) || !level.getBlockState(cell).isAir()) break;
                long key = cell.asLong();
                masks.put(key, masks.get(key) | 1 << BACK[heading]);
                reaches.put(key, Math.min(reaches.get(key), i <= 1 ? 0 : 1));
                if (lastKey != Long.MIN_VALUE) masks.put(lastKey, masks.get(lastKey) | 1 << heading);
                last = rock;
                lastKey = key;
                h = mix(h + i * 31L);
                // mostly straight on, sometimes off to one side; never back on itself
                if (Long.remainderUnsigned(h, 4) == 0) heading = (heading < 2 ? 2 : 0) + (int) (h >>> 40 & 1);
            }
        }
        boolean any = false;
        LodeTraceBlock block = DelveContent.LODE_TRACE.get();
        for (var e : masks.long2IntEntrySet()) {
            int x = BlockPos.getX(e.getLongKey()), z = BlockPos.getZ(e.getLongKey());
            if (x < x0 || x > x0 + 15 || z < z0 || z > z0 + 15) continue;
            level.setBlock(BlockPos.of(e.getLongKey()), block.with(facing, e.getIntValue(), reaches.get(e.getLongKey()), ore), Block.UPDATE_CLIENTS);
            any = true;
        }
        return any;
    }

    private static long mix(long z) {
        z = (z ^ z >>> 30) * 0xBF58476D1CE4E5B9L;
        z = (z ^ z >>> 27) * 0x94D049BB133111EBL;
        return z ^ z >>> 31;
    }

    /** What a lode's block becomes in this rock, or null to leave it (air, water, a build, bedrock). */
    static Block rockFor(Ore ore, BlockState s, boolean heart) {
        boolean deep;
        if (s.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)) deep = true;
        else if (s.is(BlockTags.STONE_ORE_REPLACEABLES)) deep = false;
        else if (Ore.of(s) != null || s.is(net.neoforged.neoforge.common.Tags.BlockItems.ORES.block())) deep = s.getBlock().getDescriptionId().contains("deepslate");
        else return null;
        return heart ? ore.heart() : ore.block(deep);
    }
}
