package dev.awaken.village;

import dev.awaken.village.plan.Terrain;

/**
 * The test valley with a mountain rising from its east (tests and the studio, SPEC §28.9): {@link TestLand}'s ground,
 * climbing from 50 blocks east of the middle by nine in ten to 56 over it, so a mining town planned there finds its
 * mountain's foot.
 */
public record TestMountain(TestLand land) implements Terrain {
    public static TestMountain at(long seed, int x, int base, int z) {
        return new TestMountain(TestLand.at(seed, x, base, z));
    }

    @Override
    public int top(int x, int z) {
        int east = x - land.ox() - 50;
        int t = land.top(x, z);
        return east <= 0 || land.water(x, z) > 0 ? t : t + Math.min(56, (int) Math.round(east * 0.9));
    }

    @Override
    public int water(int x, int z) {
        return x - land.ox() > 50 ? 0 : land.water(x, z);
    }

    @Override
    public int family(int x, int z) {
        return land.family(x, z);
    }

    @Override
    public int seaLevel() {
        return land.seaLevel();
    }

    /** A mining town planned round the middle (null if none fits, which it always does). */
    public dev.awaken.village.plan.@org.jspecify.annotations.Nullable VillagePlan plan(String key, long planSeed) {
        return dev.awaken.village.plan.VillagePlanner.plan(new dev.awaken.village.plan.VillagePlanner.Request(key, planSeed, land.ox(), land.oz(),
                dev.awaken.village.plan.VillagePlan.Kind.MINING, false, land.ox() - 124, land.oz() - 124, land.ox() + 139, land.oz() + 139, (x, z) -> false), this);
    }
}
