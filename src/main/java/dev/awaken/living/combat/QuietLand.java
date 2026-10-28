package dev.awaken.living.combat;

import dev.awaken.living.api.Regions;
import dev.awaken.living.region.RegionMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.player.Player;

/**
 * A quieter land (SPEC §21.11, decision 23, as changed by the Wild Lands, SPEC §41 decision 2): animals follow wildness, not
 * danger. Natural spawns of animals thin in tamed land and pasture (people's land holds fewer wild animals; the wild's herds
 * come from the ledger) and never by rank, so the deep wild of a hard land is full of life. A settlement's own land and a
 * Hearth's calm keep every spawn (its farm animals). The rank still makes wild kin shyer of a player who is not sneaking,
 * and the wild life layer (birds, insects) still thins by rank and falls nearly silent in A and S: the dread keeps its hush.
 */
public final class QuietLand {
    private QuietLand() {}

    /** The share of natural animal spawns kept, by wildness band (Tamed, Pasture, Wild, Deep wild). */
    public static final float[] KEEP = {0.7F, 0.9F, 1F, 1F};
    /** The share of the small ambient life (birds, insects) kept, by rank. */
    public static final float[] WILD = {1F, 0.8F, 0.5F, 0.3F, 0.08F, 0.02F};

    public static float keep(dev.awaken.living.api.Wildness.Band band) {
        return KEEP[band.ordinal()];
    }

    /** The ambient life's share in the land of this level (-1 = unknown: all). */
    public static float wild(int landLevel) {
        return landLevel < 1 ? 1F : WILD[Math.max(0, Math.min(5, RegionMap.rankOf(landLevel)))];
    }

    /** How far wild kin keep from a player who is not sneaking: 10 blocks in D, four more a rank. */
    public static float shy(int rank) {
        return 6F + 4F * Math.max(1, Math.min(5, rank));
    }

    /** Whether a natural spawn of this creature is turned away: an animal, nature's own, in tamed land or pasture, not on a settlement's land. */
    public static boolean thins(ServerLevel level, Mob mob, EntitySpawnReason reason, RandomSource rnd) {
        if (!Regions.on(level) || mob.getType().getCategory() != MobCategory.CREATURE) return false;
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) return false;
        if (dev.awaken.living.population.Ledger.spawning() || Regions.landCap(level, mob.blockPosition()) != Integer.MAX_VALUE) return false;
        // during generation the chunk isn't loaded yet: the land's own value (no record)
        double w = dev.awaken.living.api.Wildness.land(level, mob.getBlockX(), mob.getBlockZ());
        return rnd.nextFloat() >= keep(dev.awaken.living.api.Wildness.band(w));
    }

    /** Wild kin shy from a player on their feet; a sneaking hunter walks up to them. */
    public static void shy(PathfinderMob mob, int rank) {
        mob.goalSelector.addGoal(1, new AvoidEntityGoal<>(mob, Player.class, shy(rank), 1.1, 1.5, p -> !p.isCrouching() && !p.isSpectator()));
    }
}
