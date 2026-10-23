package dev.awaken.descent.rules;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.descent.Strata;
import dev.awaken.descent.world.Layout;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Rule 3, the rising water. Every five minutes the Drowned Choir sings, then the water in the flooded halls and at the
 * bottom of the shaft rises nine blocks, holds, and drains. Water is placed only around players (radius 11) as still
 * source blocks; every block placed is remembered (SavedData) so a drain or a restart always removes exactly what the
 * flood added and never touches anything else.
 */
public final class Flood {
    private Flood() {}

    public static final int CYCLE = 6000, WARN = 4400, RISE = 4600, HOLD = 5000, DRAIN = 5600;
    public static final int MAX_DELTA = 9, RADIUS = 11;
    private static final int[] HALLS = {44, 94, 144};

    public static final class Data extends SavedData {
        public static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.listOf().fieldOf("placed").forGetter(d -> {
                    List<Long> l = new ArrayList<>(d.placed.size());
                    LongIterator it = d.placed.iterator();
                    while (it.hasNext()) l.add(it.nextLong());
                    return l;
                })
        ).apply(i, l -> {
            Data d = new Data();
            for (Long v : l) d.placed.add(v.longValue());
            return d;
        }));
        public static final SavedDataType<Data> TYPE = new SavedDataType<>(Awaken.id("descent_flood"), Data::new, CODEC);
        public final LongOpenHashSet placed = new LongOpenHashSet();

        public Data() {}
    }

    public static Data data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(Data.TYPE);
    }

    /** Water height above the base for a point in the cycle. */
    public static int delta(long t) {
        long c = Math.floorMod(t, CYCLE);
        if (c < RISE) return 0;
        if (c < HOLD) return (int) Math.ceil(MAX_DELTA * (c - RISE) / (double) (HOLD - RISE));
        if (c < DRAIN) return MAX_DELTA;
        return Math.max(0, MAX_DELTA - (int) Math.ceil(MAX_DELTA * (c - DRAIN) / (double) (CYCLE - DRAIN)));
    }

    /** Local base level of the water body a player stands in (or Integer.MIN_VALUE: no flood here). */
    public static int baseFor(int form, int localY) {
        if (form == 3) {
            for (int gy : HALLS) if (localY >= gy - 1 && localY <= gy + 14) return gy + 3;
        }
        int floor = Layout.floorY(form);
        if (localY < floor + 18) return floor;
        return Integer.MIN_VALUE;
    }

    /** Fills the flood around one player up to the current height. Budgeted. */
    public static void fill(ServerLevel level, ServerPlayer p, int depth, long t, Data data) {
        int dlt = delta(t);
        if (dlt <= 0) return;
        int form = Strata.formOf(depth);
        int base = baseFor(form, p.blockPosition().getY());
        if (base == Integer.MIN_VALUE) return;
        int top = base + dlt - 1;
        int px = p.blockPosition().getX(), pz = p.blockPosition().getZ();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockState water = Blocks.WATER.defaultBlockState();
        int budget = 2500;
        for (int dx = -RADIUS; dx <= RADIUS && budget > 0; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (dx * dx + dz * dz > RADIUS * RADIUS) continue;
                int x = px + dx, z = pz + dz;
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                for (int y = base; y <= top; y++) {
                    budget--;
                    m.set(x, y, z);
                    if (level.getBlockState(m).isAir()) {
                        level.setBlock(m, water, Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
                        data.placed.add(m.asLong());
                        data.setDirty();
                    }
                }
            }
        }
    }

    /** Drains one layer from the top of everything the flood placed. Returns true when nothing is left. */
    public static boolean drainLayer(ServerLevel level, Data data) {
        if (data.placed.isEmpty()) return true;
        int maxY = Integer.MIN_VALUE;
        LongIterator it = data.placed.iterator();
        while (it.hasNext()) {
            long l = it.nextLong();
            if (level.hasChunk(BlockPos.getX(l) >> 4, BlockPos.getZ(l) >> 4)) maxY = Math.max(maxY, BlockPos.getY(l));
        }
        if (maxY == Integer.MIN_VALUE) return false;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        it = data.placed.iterator();
        int removed = 0;
        while (it.hasNext() && removed < 4000) {
            long l = it.nextLong();
            if (BlockPos.getY(l) != maxY) continue;
            m.set(l);
            if (level.hasChunk(m.getX() >> 4, m.getZ() >> 4)) {
                if (level.getBlockState(m).is(Blocks.WATER)) {
                    level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
                }
                it.remove();
                removed++;
            }
        }
        data.setDirty();
        return data.placed.isEmpty();
    }

    /** Whether the water body around a local position would be under the flood right now (for the HUD). */
    public static boolean threatened(int form, int localY) {
        return baseFor(form, localY) != Integer.MIN_VALUE;
    }
}
