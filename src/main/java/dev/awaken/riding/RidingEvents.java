package dev.awaken.riding;

import dev.awaken.core.api.GateHooks;
import dev.awaken.core.api.RidingHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.jspecify.annotations.Nullable;

/** What a thread changes when blows land and monsters choose (SPEC §55). */
final class RidingEvents {
    private RidingEvents() {}

    private static final double SWEEP_RANGE = 24;
    private static final double MIN_SIGHT = 16, MAX_SIGHT = 48;

    // ---- the catch -----------------------------------------------------------------------------------------------------

    /** HIGHEST: before a gate counts the death or Cheat Death is spent. */
    static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !Bonds.rider(p) || e.getSource().is(DamageTypes.GENERIC_KILL)) return;
        ServerPlayer anchor = Bonds.partner(p);
        if (anchor == null || !anchor.isAlive() || anchor.isSpectator() || !Bonds.inReach(p, anchor)) return; // past the thread's reach it's a real fall
        e.setCanceled(true);
        Fold.begin(p);
    }

    /** LOWEST and not cancelled: this one really went down. */
    static void afterDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) Bonds.wentDown(p);
    }

    // ---- blows ---------------------------------------------------------------------------------------------------------

    /** HIGHEST: a folded rider can't be hit and can't hit; just back, they can't be hit either. */
    static void onIncoming(LivingIncomingDamageEvent e) {
        DamageSource src = e.getSource();
        if (e.getEntity() instanceof ServerPlayer p && Fold.shielded(p.getUUID()) && !src.is(DamageTypes.GENERIC_KILL)) {
            e.setCanceled(true);
            return;
        }
        if (src.getEntity() instanceof ServerPlayer by && Fold.folded(by.getUUID())) e.setCanceled(true);
    }

    /** LOWEST, so it scales what every other bonus has already made of the hit. */
    static void onDamage(LivingDamageEvent.Pre e) {
        Entity by = e.getSource().getEntity();
        if (by instanceof OwnableEntity pet) by = pet.getOwner();
        if (by instanceof ServerPlayer p && p != e.getEntity() && Bonds.rider(p)) e.setNewDamage(e.getNewDamage() * RidingHooks.HIT_SHARE);
    }

    // ---- monsters ------------------------------------------------------------------------------------------------------

    /** Someone a monster should prefer over this rider, or null if the rider is all there is. */
    private static @Nullable ServerPlayer better(Mob mob, ServerPlayer rider) {
        AttributeInstance sight = mob.getAttribute(Attributes.FOLLOW_RANGE);
        double range = Mth.clamp(sight == null ? MIN_SIGHT : sight.getValue(), MIN_SIGHT, MAX_SIGHT);
        ServerPlayer best = null;
        double bestD = range * range;
        for (ServerPlayer o : ((ServerLevel) mob.level()).players()) {
            if (o == rider || Bonds.rider(o) || !o.isAlive() || o.isSpectator()) continue;
            double d = o.distanceToSqr(mob);
            if (d <= bestD && mob.canAttack(o) && GateHooks.mayTarget(mob, o)) {
                best = o;
                bestD = d;
            }
        }
        return best;
    }

    /**
     * Every target a monster picks comes through here. A rider is never its first choice while anyone else is in its sight;
     * alone, a rider can still be hunted, so riding off by yourself is no way to walk through a gate unseen.
     */
    static void onTarget(LivingChangeTargetEvent e) {
        if (!(e.getNewAboutToBeSetTarget() instanceof ServerPlayer p) || !Bonds.rider(p) || !(e.getEntity() instanceof Mob mob)) return;
        ServerPlayer other = better(mob, p);
        if (other != null) e.setNewAboutToBeSetTarget(other);
        else if (Fold.folded(p.getUUID())) e.setNewAboutToBeSetTarget(null);
    }

    /** Monsters already after this rider look again (once a second, and the moment they fold). */
    static void sweep(ServerPlayer rider) {
        AABB box = rider.getBoundingBox().inflate(SWEEP_RANGE);
        for (Mob m : rider.level().getEntitiesOfClass(Mob.class, box, x -> x.getTarget() == rider)) m.setTarget(rider); // the event above decides
    }

    /** They've just folded: nothing keeps chasing what isn't there. */
    static void shakeOff(ServerPlayer rider) {
        sweep(rider);
    }
}
