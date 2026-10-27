package dev.awaken.ground;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;

/**
 * A chunk's freshly cut grass sides (SPEC §31.1, "Fresh sides"): the sides of its grass blocks that a dug, blown or
 * filled-under block bared a moment ago, which show dirt and grow green over a few stages instead of wrapping at once as
 * a natural riser does. Keyed by the grass block's place in the chunk and the side (north, east, south, west); the value
 * is the lit ticks it has grown on the server and the stage in the client's copy. Server: mutated on the server thread
 * only ({@link GroundFresh}); saved with the chunk while not empty.
 */
public final class FreshSides {
    public static final int SCHEMA = 1;
    /** Lit ticks to stage 1 and 2, and to a full wrap (the record is dropped then and the side is a riser like any other). */
    public static final int STAGE_1 = 2400, STAGE_2 = 6000, WRAPPED = 12000;
    /** A side with no record. */
    public static final int NONE = 3;

    final Int2IntOpenHashMap sides = new Int2IntOpenHashMap();

    public boolean isEmpty() {
        return this.sides.isEmpty();
    }

    public int size() {
        return this.sides.size();
    }

    /** The stage a side shows after {@code grown} lit ticks: 0 bare dirt, 1 and 2 grass reaching down, {@link #NONE} wrapped. */
    public static int stage(int grown) {
        return grown >= WRAPPED ? NONE : grown >= STAGE_2 ? 2 : grown >= STAGE_1 ? 1 : 0;
    }

    /** The key of side {@code side} of the block at {@code pos} (world coordinates; the chunk is implied): x, z, y and side in 22 bits. */
    public static int key(BlockPos pos, int side) {
        return ((pos.getY() + 2048) & 0xFFF) << 10 | (pos.getZ() & 15) << 6 | (pos.getX() & 15) << 2 | side;
    }

    public static int side(int key) {
        return key & 3;
    }

    public static BlockPos pos(ChunkPos chunk, int key) {
        return new BlockPos(chunk.getMinBlockX() + (key >> 2 & 15), (key >> 10 & 0xFFF) - 2048, chunk.getMinBlockZ() + (key >> 6 & 15));
    }

    // ---- saved ------------------------------------------------------------------------------------------------------------

    /** Saved as key, grown pairs, flat. */
    public static final MapCodec<FreshSides> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("schemaVersion").forGetter(f -> SCHEMA),
            Codec.INT.listOf().fieldOf("sides").forGetter(FreshSides::flat))
            .apply(i, (v, flat) -> {
                FreshSides f = new FreshSides();
                for (int k = 0; k + 1 < flat.size(); k += 2) f.sides.put((int) flat.get(k), (int) flat.get(k + 1));
                return f;
            }));

    private List<Integer> flat() {
        List<Integer> out = new ArrayList<>(this.sides.size() * 2);
        for (Int2IntMap.Entry e : this.sides.int2IntEntrySet()) {
            out.add(e.getIntKey());
            out.add(e.getIntValue());
        }
        return out;
    }
}
