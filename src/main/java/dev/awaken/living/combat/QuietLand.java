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
 * A quieter land (SPEC §21.11, decision 23): the further the rank, the fewer animals graze, the shyer the ones that do, and the less birdsong. Natural
 * spawns of animals are thinned by rank (E all, D four fifths, C a half and a bit, B a third, A a fifth, S a tenth) except at a settlement and in a
 * Hearth's calm (which are below D by their caps), wild kin keep their distance from a player who is not sneaking, and the wild life layer
 * (birds, insects) thins and falls nearly silent in A and S. Hunting pays more as animals thin (the extra food per rank), and an alpha carries hide.
 */
public final class QuietLand {
    private QuietLand() {}

    /** The share of natural animal spawns kept, by rank E to S. */
    public static final float[] KEEP = {1F, 0.8F, 0.55F, 0.35F, 0.2F, 0.1F};
    /** The share of the small ambient life (birds, insects) kept, by rank. */
    public static final float[] WILD = {1F, 0.8F, 0.5F, 0.3F, 0.08F, 0.02F};

    public static float keep(int rank) {
        return KEEP[Math.max(0, Math.min(5, rank))];
    }

    /** The ambient life's share in the land of this level (-1 = unknown: all). */
    public static float wild(int landLevel) {
        return landLevel < 1 ? 1F : WILD[Math.max(0, Math.min(5, RegionMap.rankOf(landLevel)))];
    }

    /** How far wild kin keep from a player who is not sneaking: 10 blocks in D, four more a rank. */
    public static float shy(int rank) {
        return 6F + 4F * Math.max(1, Math.min(5, rank));
    }

    /** Whether a natural spawn of this creature is turned away: an animal, nature's own, in land of rank D or up, not on a settlement's land. */
    public static boolean thins(ServerLevel level, Mob mob, EntitySpawnReason reason, RandomSource rnd) {
        if (!Regions.on(level) || mob.getType().getCategory() != MobCategory.CREATURE) return false;
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) return false;
        int rank = RegionMap.rankOf(Regions.creatureLevel(level, mob.blockPosition(), Regions.Role.NEUTRAL));
        if (rank <= 0 || Regions.landCap(level, mob.blockPosition()) != Integer.MAX_VALUE) return false;
        return rnd.nextFloat() >= keep(rank);
    }

    /** Wild kin shy from a player on their feet; a sneaking hunter walks up to them. */
    public static void shy(PathfinderMob mob, int rank) {
        mob.goalSelector.addGoal(1, new AvoidEntityGoal<>(mob, Player.class, shy(rank), 1.1, 1.5, p -> !p.isCrouching() && !p.isSpectator()));
    }
}
