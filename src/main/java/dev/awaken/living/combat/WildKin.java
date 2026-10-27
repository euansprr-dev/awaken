package dev.awaken.living.combat;

import dev.awaken.living.region.RegionMap;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;

import java.util.Map;
import java.util.Set;

/**
 * Wild animals are not set dressing in a harder land (SPEC §21.10, decision 8 as amended 2026-10-27): in land of rank D (level
 * 10) and up a naturally spawned cow, pig, sheep or chicken fights back when struck, carries its level (so the plate's chip is
 * true), is scaled like any creature of that level, grows a little with the land's rank, and now and then is an alpha (an elite:
 * bigger and tougher). From C land the herd rallies when one is hit. Only ever when hit (like wolves, bees and polar bears):
 * no animal goes for you unprovoked; it forgives ten seconds after the last blow. E land (the Commons, the first ring) is exactly as
 * before, and so is anything bred, penned, tamed, a baby, or in a settlement's land or a Hearth's calm.
 */
public final class WildKin {
    private WildKin() {}

    /** The vanilla animals that change, and the bite they are given (the attribute they never had). */
    static final Map<EntityType<? extends LivingEntity>, Double> BITE = Map.of(
            EntityTypes.PIG, 3.0, EntityTypes.COW, 3.0, EntityTypes.MOOSHROOM, 3.0, EntityTypes.SHEEP, 2.0, EntityTypes.CHICKEN, 1.0);
    static final Set<EntityType<?>> KINDS = Set.copyOf(BITE.keySet());

    /** How long an animal holds a grudge after the last blow. */
    static final int FORGIVE = 200;

    /** The mod bus: every one of those animals gets an attack damage attribute (pigs and cows have none in vanilla). */
    public static void attributes(EntityAttributeModificationEvent e) {
        for (var b : BITE.entrySet()) if (!e.has(b.getKey(), Attributes.ATTACK_DAMAGE)) e.add(b.getKey(), Attributes.ATTACK_DAMAGE, b.getValue());
    }

    /** Only spawns the land made itself: not bred, not from an egg, not a village's or a farm's. */
    public static boolean wild(EntitySpawnReason reason) {
        return reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION;
    }

    /** Whether a creature of this kind spawning so, on land of this level, is one that fights back. */
    public static boolean fights(EntityType<?> type, boolean wildSpawn, boolean baby, boolean settled, int landLevel) {
        return KINDS.contains(type) && wildSpawn && !baby && !settled && RegionMap.rankOf(landLevel) >= 1;
    }

    /** How much bigger the land makes them: 4% a rank above E, up to 20%. */
    public static double growth(int rank) {
        return Math.min(0.2, 0.04 * Math.max(0, rank));
    }

    /** Whether the herd rallies when one is struck (from C land). */
    public static boolean rallies(int rank) {
        return rank >= 2;
    }

    /** Whether an animal that joined a level is one of these, tagged neutral by this module (so its goals are put back after a reload). */
    public static boolean isKin(Mob mob) {
        return KINDS.contains(mob.getType());
    }

    /** Gives the animal its fight: it bites what struck it, and for ten seconds. Goals are not saved, so this runs at every join. */
    public static void arm(Mob mob, int landLevel) {
        if (!(mob instanceof PathfinderMob pm)) return;
        int rank = RegionMap.rankOf(landLevel);
        pm.goalSelector.addGoal(0, new MeleeAttackGoal(pm, 1.25, true));
        var retaliate = new Retaliate(pm);
        if (rallies(rank)) retaliate.setAlertOthers();
        pm.targetSelector.addGoal(0, retaliate);
    }

    /** Hurt-by retaliation that lets go: only while the last blow was within {@link #FORGIVE} ticks. */
    static final class Retaliate extends HurtByTargetGoal {
        Retaliate(PathfinderMob mob) {
            super(mob);
        }

        @Override
        public boolean canContinueToUse() {
            return mob.tickCount - mob.getLastHurtByMobTimestamp() < FORGIVE && super.canContinueToUse();
        }
    }
}
