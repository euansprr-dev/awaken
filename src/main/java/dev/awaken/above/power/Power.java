package dev.awaken.above.power;

import dev.awaken.above.machine.MachineBlockEntity;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Essence power without cables: a machine is powered by every loaded Essence Reactor within {@link #RANGE} blocks
 * (straight-line distance between block centres). Reactors register themselves here when they load; machines keep a
 * {@link Cache} of the reactors in range that is rebuilt only when a reactor is added or removed anywhere.
 */
public final class Power {
    private Power() {}

    public static final int RANGE = 32;

    private static final Map<ResourceKey<Level>, LongOpenHashSet> REACTORS = new HashMap<>();
    private static int version;

    /** Pure range rule (GameTested). */
    public static boolean inRange(BlockPos reactor, BlockPos machine) {
        return reactor.distSqr(machine) <= (double) RANGE * RANGE;
    }

    public static synchronized void add(ServerLevel level, BlockPos pos) {
        if (REACTORS.computeIfAbsent(level.dimension(), k -> new LongOpenHashSet()).add(pos.asLong())) version++;
    }

    public static synchronized void remove(ServerLevel level, BlockPos pos) {
        LongOpenHashSet set = REACTORS.get(level.dimension());
        if (set != null && set.remove(pos.asLong())) version++;
    }

    public static synchronized void clear() {
        REACTORS.clear();
        version++;
    }

    /** Reactors in range of one machine, nearest first. Rebuilt lazily when the reactor set changes. */
    public static final class Cache {
        private int builtVersion = -1;
        private long[] near = new long[0];
        private long builtFor = Long.MIN_VALUE;
    }

    private static synchronized long[] near(ServerLevel level, BlockPos pos, @Nullable Cache cache) {
        if (cache != null && cache.builtVersion == version && cache.builtFor == pos.asLong()) return cache.near;
        LongOpenHashSet set = REACTORS.get(level.dimension());
        long[] out;
        if (set == null || set.isEmpty()) {
            out = new long[0];
        } else {
            long[] tmp = new long[set.size()];
            int n = 0;
            for (long l : set) if (inRange(BlockPos.of(l), pos)) tmp[n++] = l;
            out = Arrays.copyOf(tmp, n);
            // nearest first so draws empty the closest reactor before far ones
            for (int i = 1; i < out.length; i++) {
                long key = out[i];
                double d = BlockPos.of(key).distSqr(pos);
                int j = i - 1;
                while (j >= 0 && BlockPos.of(out[j]).distSqr(pos) > d) {
                    out[j + 1] = out[j];
                    j--;
                }
                out[j + 1] = key;
            }
        }
        if (cache != null) {
            cache.near = out;
            cache.builtVersion = version;
            cache.builtFor = pos.asLong();
        }
        return out;
    }

    private static @Nullable MachineBlockEntity reactorAt(ServerLevel level, long packed) {
        BlockPos p = BlockPos.of(packed);
        if (!level.isLoaded(p)) return null;
        return level.getBlockEntity(p) instanceof MachineBlockEntity be && be.isReactor() ? be : null;
    }

    /** Number of reactors whose range covers this position (loaded ones only). */
    public static int reactorCount(ServerLevel level, BlockPos pos, @Nullable Cache cache) {
        return near(level, pos, cache).length;
    }

    /** Total essence available to a machine at this position. */
    public static int stored(ServerLevel level, BlockPos pos, @Nullable Cache cache) {
        int total = 0;
        for (long l : near(level, pos, cache)) {
            MachineBlockEntity r = reactorAt(level, l);
            if (r != null) total += r.essence();
        }
        return total;
    }

    /** Draws exactly {@code amount} from reactors in range, nearest first, or nothing if there isn't enough. */
    public static boolean draw(ServerLevel level, BlockPos pos, int amount, @Nullable Cache cache) {
        if (amount <= 0) return true;
        if (stored(level, pos, cache) < amount) return false;
        int left = amount;
        for (long l : near(level, pos, cache)) {
            if (left <= 0) break;
            MachineBlockEntity r = reactorAt(level, l);
            if (r != null) left -= r.takeEssence(left);
        }
        return true;
    }
}
