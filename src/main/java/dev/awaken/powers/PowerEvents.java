package dev.awaken.powers;

import dev.awaken.core.Forces;
import dev.awaken.powers.catalog.MindPowers;
import dev.awaken.powers.catalog.ShadowPowers;
import dev.awaken.powers.catalog.SpiritPowers;
import dev.awaken.powers.content.PowerContent;
import dev.awaken.progression.Counters;
import dev.awaken.progression.Progression;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Game-bus wiring for the powers module: ticking, damage hooks, targeting, lifecycle and cleanup. */
final class PowerEvents {
    private PowerEvents() {}

    static void register(IEventBus bus) {
        bus.addListener(PowerEvents::onPlayerTick);
        bus.addListener(PowerEvents::onServerTick);
        bus.addListener(TimeSlow::onEntityTick);
        bus.addListener(EventPriority.LOW, PowerEvents::onIncomingDamage);
        bus.addListener(ForceInteractions::onIncoming);
        bus.addListener(ForceInteractions::onDamaged);
        bus.addListener(PowerEvents::onFall);
        bus.addListener(PowerEvents::onChangeTarget);
        bus.addListener(Zones::onChangeTarget);
        bus.addListener(PowerEvents::onDeath);
        bus.addListener(PowerEvents::onLogin);
        bus.addListener(PowerEvents::onLogout);
        bus.addListener(PowerEvents::onRespawn);
        bus.addListener(PowerEvents::onChangeDimension);
        bus.addListener(PowerEvents::onJoin);
        bus.addListener(PowerEvents::onPlace);
        bus.addListener(PowerEvents::onServerStarting);
        bus.addListener(PowerEvents::onServerStopping);
    }

    // ---- ticking ----------------------------------------------------------------------------------------------------

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer sp && sp.isAlive()) Casting.tickPlayer(sp);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        Scheduler.tick(server);
        guard("bolts", Bolts::tick);
        guard("flying blocks", FlyingBlocks::tick);
        guard("zones", Zones::tick);
        if (server.getTickCount() % 10 == 0) {
            Allies.expire(server.overworld().getGameTime());
            for (ServerLevel level : server.getAllLevels()) guard("terrain ledger", () -> TerrainLedger.tick(level));
        }
    }

    /** A broken effect must never take the server tick down with it. */
    private static void guard(String what, Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            Casting.errors++;
            dev.awaken.Awaken.LOG.error("Powers: {} failed this tick", what, e);
        }
    }

    // ---- damage -----------------------------------------------------------------------------------------------------

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        DamageSource src = event.getSource();
        Entity attacker = src.getEntity();

        // Never hurt your own summons or decoys (your aura, a stray swing, your own beam).
        if (attacker instanceof ServerPlayer sp && victim != sp) {
            Allies.Entry ally = Allies.get(victim);
            boolean mine = (victim instanceof dev.awaken.powers.content.PowerSummon s && s.getOwner() == sp)
                    || (ally != null && ally.kind() == Allies.Kind.DECOY && ally.owner().equals(sp.getUUID()));
            if (mine) {
                event.setCanceled(true);
                return;
            }
        }

        if (victim instanceof ServerPlayer sp) onPlayerHurt(event, sp, src, attacker);
        if (attacker instanceof ServerPlayer sp && victim != sp) onPlayerHits(event, sp, victim, src);
    }

    private static void onPlayerHurt(LivingIncomingDamageEvent event, ServerPlayer sp, DamageSource src, Entity attacker) {
        PowerState st = PowerState.peek(sp);
        if (st == null) return;
        long now = Casting.now(sp);
        if (attacker != null && attacker != sp) st.markCombat(sp);
        if (src.is(DamageTypeTags.IS_FALL) && st.has(PowerState.Buffs.NO_FALL, now)) {
            event.setCanceled(true);
            return;
        }
        boolean fromSomething = attacker != null || src.getDirectEntity() != null;
        if (fromSomething && st.has(PowerState.Buffs.FORESIGHT, now)) {
            st.clearBuff(PowerState.Buffs.FORESIGHT);
            event.setCanceled(true);
            MindPowers.dodge(sp, attacker instanceof LivingEntity le ? le : null);
            return;
        }
        float amount = event.getAmount();
        double bulwark = st.buff(PowerState.Buffs.BULWARK, now);
        if (bulwark > 0) amount *= (float) (1 - bulwark);
        double brace = st.buff(PowerState.Buffs.BRACE, now);
        if (brace > 0) amount *= (float) (1 - brace);
        double guard = st.buff(PowerState.Buffs.GUARD, now);
        if (guard > 0) amount *= (float) (1 - guard);
        if (attacker instanceof Mob && st.has(PowerState.Buffs.READ_INTENT, now)) amount *= 0.85f;
        event.setAmount(amount);

        // reflections only answer melee (the attacker struck directly and is close)
        if (attacker instanceof LivingEntity melee && src.getDirectEntity() == attacker && melee.distanceToSqr(sp) < 25) {
            double reflect = st.buff(PowerState.Buffs.BRACE_REFLECT, now);
            double nanite = st.buff(PowerState.Buffs.NANITE, now);
            if (reflect > 0) {
                Combat.hurtFrom(sp, null, melee, (float) (event.getOriginalAmount() * reflect), null, true);
                Combat.knockFrom(melee, sp.position(), 0.8, 0.2);
            }
            if (nanite > 0) {
                Combat.hurtFrom(sp, null, melee, (float) Math.max(1, event.getOriginalAmount() * nanite), Forces.Force.CHARGE, true);
            }
        }
    }

    private static void onPlayerHits(LivingIncomingDamageEvent event, ServerPlayer sp, LivingEntity victim, DamageSource src) {
        PowerState st = PowerState.peek(sp);
        if (st == null) return;
        long now = Casting.now(sp);
        st.markCombat(sp);
        float amount = event.getAmount();
        double backstab = st.buff(PowerState.Buffs.BACKSTAB, now);
        if (backstab > 0) {
            amount *= (float) backstab;
            st.clearBuff(PowerState.Buffs.BACKSTAB);
            double blind = st.buff(PowerState.Buffs.BACKSTAB_BLIND, now);
            if (blind > 0) {
                Moves.effect(victim, net.minecraft.world.effect.MobEffects.BLINDNESS, (int) blind, 0);
                if (victim instanceof net.minecraft.world.entity.Mob m && m.getTarget() == sp) m.setTarget(null);
                st.clearBuff(PowerState.Buffs.BACKSTAB_BLIND);
            }
            Fx.impact(sp.level(), victim.getBoundingBox().getCenter(), 0x8F6BFF);
            Fx.feel(sp, victim, 50, 1.5f);
        }
        double cloak = st.buff(PowerState.Buffs.CLOAK, now);
        if (cloak > 0) {
            amount *= 1.8f;
            ShadowPowers.breakCloak(sp, victim, cloak);
        }
        if (victim instanceof Enemy && st.has(PowerState.Buffs.READ_INTENT, now)) amount *= 1.15f;
        if (dev.awaken.powers.ladder.Riders.marked(sp, victim, now)) amount *= 1.1f;
        double momentum = st.buff(PowerState.Buffs.MOMENTUM, now);
        if (momentum > 0 && SpiritPowers.isMelee(src, sp)) {
            amount *= (float) momentum;
            st.clearBuff(PowerState.Buffs.MOMENTUM);
        }
        event.setAmount(amount);
        double twin = st.buff(PowerState.Buffs.TWIN, now);
        if (twin > 0 && SpiritPowers.isMelee(src, sp)) SpiritPowers.twinEcho(sp, victim, amount, twin);
    }

    private static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        PowerState st = PowerState.peek(sp);
        if (st != null && st.has(PowerState.Buffs.NO_FALL, Casting.now(sp))) event.setCanceled(true);
    }

    // ---- targeting --------------------------------------------------------------------------------------------------

    private static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity next = event.getNewAboutToBeSetTarget();
        if (next == null) return;
        LivingEntity mob = event.getEntity();
        Allies.Entry self = Allies.get(mob);
        if (self != null && self.kind() == Allies.Kind.DOMINATED) {
            // a puppet never turns on players or on other puppets/decoys
            if (next instanceof Player || Allies.isAlly(next)) event.setCanceled(true);
            return;
        }
        if (next instanceof ServerPlayer sp) {
            PowerState st = PowerState.peek(sp);
            if (st != null && st.has(PowerState.Buffs.CLOAK, Casting.now(sp)) && mob.getLastHurtByMob() != sp) event.setCanceled(true);
        }
    }

    // ---- deaths and lifecycle ---------------------------------------------------------------------------------------

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead instanceof ServerPlayer sp) {
            Casting.endAll(sp, Running.Why.CANCELLED);
            Moves.clearPowerPostEffects(sp);
            return;
        }
        Allies.remove(dead);
        if (event.getSource().getEntity() instanceof ServerPlayer killer && Combat.airborne(killer)) {
            Progression.count(killer, "awaken:kills_airborne");
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        Moves.clearPowerPostEffects(sp); // saved in player NBT: never let one outlive its power
        Loadout l = Powers.loadout(sp);
        if (l.energy < 0) l.energy = Powers.maxEnergy(sp);
        Progression.setCounter(sp, Counters.DASH_CHAIN_AIRBORNE_BEST + "_session", 0);
        Powers.markDirty(sp);
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        Casting.endAll(sp, Running.Why.CANCELLED);
        Moves.clearPowerPostEffects(sp);
        PowerState.forget(sp);
        Constructs.forget(sp.getUUID());
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        Casting.endAll(sp, Running.Why.CANCELLED);
        Moves.clearPowerPostEffects(sp);
        Powers.fillEnergy(sp);
    }

    private static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        Casting.endAll(sp, Running.Why.CANCELLED);
        Moves.clearPowerPostEffects(sp);
        Powers.markDirty(sp);
    }

    /** Thrown blocks and decoys never survive a save: drop any that come back from disk. */
    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && event.getEntity().entityTags().contains(FlyingBlocks.TEMP_TAG)) event.setCanceled(true);
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (event.getPlacedBlock().getBlock() == PowerContent.CONSTRUCT.get()) return;
        if (Powers.inCombat(sp)) Progression.count(sp, Counters.BLOCKS_PLACED_IN_COMBAT);
    }

    private static void onServerStarting(ServerStartingEvent event) {
        clearMemory();
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        for (ServerPlayer sp : event.getServer().getPlayerList().getPlayers()) {
            Casting.endAll(sp, Running.Why.CANCELLED);
            Moves.clearPowerPostEffects(sp);
        }
        for (ServerLevel level : event.getServer().getAllLevels()) TerrainLedger.restoreAll(level);
        FlyingBlocks.clear();
        clearMemory();
    }

    static void clearMemory() {
        Allies.clear();
        TimeSlow.clear();
        Zones.clear();
        Bolts.clear();
        Scheduler.clear();
        PowerState.clearAll();
        ForceInteractions.clear();
    }
}
