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

    /**
     * Live land creatures of {@code role} within {@code radius} blocks (horizontal) of x, z. Water creatures have their
     * own caps ({@link #countWater}) and a mount that trusts a player counts for no one; named beasts are counted on land
     * and in water alike (one beast near a player).
     */
    static int countNear(Level level, FaunaRole role, double x, double z, double radius) {
        return count(level, role, x, z, radius, role == FaunaRole.BEAST ? 2 : 0);
    }

    /** Live water creatures (the {@code awaken:fauna_water} category) of {@code role} near x, z (P3's water caps). */
    static int countWater(Level level, FaunaRole role, double x, double z, double radius) {
        return count(level, role, x, z, radius, 1);
    }

    /** where: 0 land, 1 water, 2 both. */
    private static int count(Level level, FaunaRole role, double x, double z, double radius, int where) {
        List<FaunaMob> list = LIVE.get(level.dimension());
        if (list == null) return 0;
        double r2 = radius * radius;
        int n = 0;
        for (int i = 0, size = list.size(); i < size; i++) {
            FaunaMob m = list.get(i);
            if (m.def().fauna != role || !m.isAlive()) continue;
            if (m instanceof dev.awaken.mobs.entity.WildMount w && w.owner() != null) continue;
            if (where != 2 && (m.getType().getCategory() == dev.awaken.mobs.entity.Fauna3Kinds.FAUNA_WATER) != (where == 1)) continue;
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
