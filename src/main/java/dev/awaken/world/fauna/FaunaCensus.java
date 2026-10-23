package dev.awaken.world.fauna;

import dev.awaken.mobs.def.FaunaRole;
import dev.awaken.mobs.entity.FaunaMob;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every loaded overworld creature, per level, kept by the creatures themselves as they join and leave a level (so
 * nothing ever scans the level's entities). The spawn rules count roles near a player here; sightings look here.
 * Bounded by the fauna cap: a few dozen entries per player at most. Server thread only.
 */
final class FaunaCensus {
    private FaunaCensus() {}

    private static final Map<ResourceKey<Level>, List<FaunaMob>> LIVE = new HashMap<>();

    static void added(FaunaMob mob) {
        List<FaunaMob> list = LIVE.computeIfAbsent(mob.level().dimension(), k -> new ArrayList<>());
        if (!list.contains(mob)) list.add(mob);
    }

    static void removed(FaunaMob mob) {
        List<FaunaMob> list = LIVE.get(mob.level().dimension());
        if (list != null) list.remove(mob);
    }

    static List<FaunaMob> in(Level level) {
        List<FaunaMob> list = LIVE.get(level.dimension());
        return list == null ? List.of() : Collections.unmodifiableList(list);
    }

    /** Live creatures of {@code role} within {@code radius} blocks (horizontal) of x, z. */
    static int countNear(Level level, FaunaRole role, double x, double z, double radius) {
        List<FaunaMob> list = LIVE.get(level.dimension());
        if (list == null) return 0;
        double r2 = radius * radius;
        int n = 0;
        for (int i = 0, size = list.size(); i < size; i++) {
            FaunaMob m = list.get(i);
            if (m.def().fauna != role || !m.isAlive()) continue;
            double dx = m.getX() - x, dz = m.getZ() - z;
            if (dx * dx + dz * dz <= r2) n++;
        }
        return n;
    }

    static int total(Level level) {
        List<FaunaMob> list = LIVE.get(level.dimension());
        return list == null ? 0 : list.size();
    }

    static void clear() {
        LIVE.clear();
    }
}
