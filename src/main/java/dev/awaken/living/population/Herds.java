package dev.awaken.living.population;

import dev.awaken.living.api.Wildness;
import dev.awaken.mobs.MobTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * How many live where (SPEC §41.2): herd and pack sizes, how often a herd stands in a cell, and how wary a herd is, all by
 * the wildness band of its home; and which vanilla animals run in herds (the biome's own animals: a wild herd of cows is an
 * aurochs herd, pigs a boar sounder, sheep a flock of wild rams; horses, donkeys and goats as they are).
 */
public final class Herds {
    private Herds() {}

    /** The vanilla animals that run in the ledger's herds where their biome has them. */
    public static final Set<EntityType<?>> VANILLA = Set.of(EntityTypes.COW, EntityTypes.PIG, EntityTypes.SHEEP, EntityTypes.HORSE,
            EntityTypes.DONKEY, EntityTypes.GOAT);
    /** The vanilla pack: wolves, in the wild and deeper. */
    public static final String WOLF = "minecraft:wolf";

    /** Herd sizes (low, high) by band: Tamed (farm animals, not the ledger's), Pasture, Wild, Deep wild. */
    static final int[][] HERD = {{2, 4}, {4, 8}, {8, 16}, {16, 30}};
    /** Pack sizes by band: none in tamed land; the deep wild's has an alpha. */
    static final int[][] PACK = {{0, 0}, {2, 3}, {3, 5}, {5, 8}};
    /** The chance that each of a cell's four herd sites holds a herd, by band of the site. */
    static final double[] ODDS = {0, 0.4, 0.85, 1};
    /** How close a person on their feet comes before the herd runs, by band (wilder herds are warier). */
    static final float[] FLIGHT = {8, 12, 18, 24};
    /** Herd and pack bodies at most (one population), and near one player. */
    public static final int HERD_CAP = 30, PACK_CAP = 8;

    static int herdSize(Wildness.Band b, RandomSource r) {
        int[] s = HERD[b.ordinal()];
        return s[0] + r.nextInt(s[1] - s[0] + 1);
    }

    static int packSize(Wildness.Band b, RandomSource r) {
        int[] s = PACK[b.ordinal()];
        return s[1] == 0 ? 0 : s[0] + r.nextInt(s[1] - s[0] + 1);
    }

    static double odds(Wildness.Band b) {
        return ODDS[b.ordinal()];
    }

    public static float flight(Wildness.Band b) {
        return FLIGHT[b.ordinal()];
    }

    /** A vanilla herd animal the biome at (x, z) spawns, by its own weights, or null where it has none. */
    static @Nullable String vanillaHerd(ServerLevel level, int x, int z, RandomSource r) {
        var mobs = level.environmentAttributes().getValue(EnvironmentAttributes.NATURAL_MOB_SPAWNS, new BlockPos(x, 64, z))
                .getMobsToSpawn(MobCategory.CREATURE);
        List<EntityType<?>> pool = new ArrayList<>();
        for (var w : mobs.unwrap()) {
            EntityType<?> t = w.value().type();
            if (VANILLA.contains(t)) for (int k = 0; k < Math.max(1, w.weight()); k++) pool.add(t);
        }
        if (pool.isEmpty()) return null;
        return BuiltInRegistries.ENTITY_TYPE.getKey(pool.get(r.nextInt(pool.size()))).toString();
    }

    /** A population's species as an entity type: our own by id ("mossback"), a vanilla one by its key ("minecraft:cow"). */
    public static @Nullable EntityType<?> type(String species) {
        if (species.indexOf(':') < 0) return MobTypes.type(species);
        Identifier id = Identifier.tryParse(species);
        return id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
    }
}
