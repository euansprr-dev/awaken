package dev.awaken.finds;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The hearts that are loaded right now, per level (SPEC §58.5). Everything that happens to a block asks here whether
 * the block belongs to a find, so the answer has to be quick and has to cover sites made by hand as well as the
 * world's own: a loaded heart knows its site either way.
 */
public final class Hearts {
    private Hearts() {}

    private static final Map<ResourceKey<Level>, Set<FindHeartBlockEntity>> LOADED = new ConcurrentHashMap<>();

    static void add(ServerLevel level, FindHeartBlockEntity be) {
        LOADED.computeIfAbsent(level.dimension(), k -> ConcurrentHashMap.newKeySet()).add(be);
    }

    static void remove(ServerLevel level, FindHeartBlockEntity be) {
        Set<FindHeartBlockEntity> s = LOADED.get(level.dimension());
        if (s != null) s.remove(be);
    }

    static void reset() {
        LOADED.clear();
    }

    public static Collection<FindHeartBlockEntity> all(ServerLevel level) {
        Set<FindHeartBlockEntity> s = LOADED.get(level.dimension());
        return s == null ? List.of() : s;
    }

    /** The loaded heart whose site holds a position (nearest middle where two overlap), or null. */
    public static @Nullable FindHeartBlockEntity at(ServerLevel level, BlockPos pos) {
        FindHeartBlockEntity best = null;
        long bestD = Long.MAX_VALUE;
        for (FindHeartBlockEntity be : all(level)) {
            if (be.isRemoved()) continue;
            BlockPos h = be.getBlockPos();
            long dx = pos.getX() - h.getX(), dz = pos.getZ() - h.getZ();
            if (Math.abs(dx) > Sites.MAX_REACH + 2 || Math.abs(dz) > Sites.MAX_REACH + 2) continue;
            Site s = be.site();
            if (s == null || !s.holds(pos, 1)) continue;
            long d = dx * dx + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = be;
            }
        }
        return best;
    }
}
