package dev.awaken.mobs;

import dev.awaken.Awaken;
import dev.awaken.core.api.BodyMaterial;
import dev.awaken.core.api.MobHooks;
import dev.awaken.core.api.Strikes;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.entity.AwakenMob;
import dev.awaken.signal.Signal;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.IEventBus;

/**
 * Entry point for the mobs module: every hostile creature and boss of AWAKEN. Registers one entity type, spawn egg
 * and loot table per bestiary def, installs the real {@link MobHooks.Spawner}, and wires the /awaken commands and
 * GameTests. See docs/modules/mobs.md for the bestiary.
 */
public final class MobsModule {
    private MobsModule() {}

    public static void init(IEventBus modBus) {
        Awaken.LOG.info("[mobs] bestiary: {} creatures", Bestiary.all().size());
        MobTypes.init(modBus);
        Ritual.init(modBus);
        MobSounds.init(modBus);
        MobParticles.init(modBus);
        MobLoot.init(modBus);
        dev.awaken.mobs.entity.Topple.init(modBus); // big beasts go down on a broken stance (SPEC §46.5)
        dev.awaken.mobs.entity.DuelFangs.init(modBus); // a duel's fangs bite for its own damage (SPEC §46.5)
        MobsEvents.init(modBus);
        dev.awaken.mobs.net.MobsNet.init(modBus);
        MobHooks.install(new MobsSpawner());
        // what Arms reads (SPEC §24): the blow a creature is telling, how its stance breaks, what its body is made of
        // blows that land after their ability ran: a bolt as the class it was fired under, our fangs as the HEAVY they are
        Strikes.installClassifier(src -> src.getDirectEntity() instanceof dev.awaken.mobs.entity.AwBolt b ? b.strike()
                : src.getDirectEntity() instanceof net.minecraft.world.entity.projectile.EvokerFangs f && f.getOwner() instanceof AwakenMob
                ? Strikes.Kind.HEAVY : null);
        Strikes.installTells(e -> e instanceof AwakenMob m ? m.telegraphTicks() > 0 && m.tellKind() != Strikes.Kind.NONE
                ? new Strikes.Tell(m.telegraphTicks(), m.tellKind()) : null : dev.awaken.mobs.entity.ToldBite.tellOf(e));
        // beasts on vanilla bodies bite told (SPEC §46.2)
        Strikes.installBiters(dev.awaken.mobs.entity.ToldBite::new);
        // the duel (SPEC §46.3): a deflect answered by the boss's fight, its openings, a white-gold shot sent back
        Strikes.installDuel(new Strikes.Duel() {
            @Override
            public void deflected(LivingEntity attacker, Strikes.Kind kind) {
                if (attacker instanceof dev.awaken.mobs.entity.AwakenBoss b) b.deflected(kind);
            }

            @Override
            public boolean opening(LivingEntity e) {
                return e instanceof dev.awaken.mobs.entity.AwakenBoss b && b.inOpening();
            }

            @Override
            public boolean duelist(LivingEntity e) {
                return e instanceof dev.awaken.mobs.entity.AwakenBoss b && b.duel();
            }

            @Override
            public int stance(LivingEntity e) {
                return e instanceof dev.awaken.mobs.entity.AwakenBoss b ? b.duelStance() : 0;
            }

            @Override
            public boolean returnShot(net.minecraft.world.entity.Entity shot, net.minecraft.world.entity.player.Player by) {
                return shot instanceof dev.awaken.mobs.entity.AwBolt b && b.sendBack(by);
            }

            @Override
            public Strikes.@org.jspecify.annotations.Nullable Blow lastBlow(LivingEntity e) {
                return e instanceof dev.awaken.mobs.entity.AwakenBoss b ? b.lastBlow() : null;
            }
        });
        Strikes.installStaggers(MobsModule::stagger);
        Strikes.installGrounder((e, ticks) -> e instanceof AwakenMob m && m.ground(ticks));
        BodyMaterial.install(e -> e instanceof AwakenMob m ? m.def().material() : null);
        Signal.onAction("mobs", Mobs::onSignalAction);
        MobsCommands.register();
        Stroll.init(); // tours: still creatures walked on the server (SPEC §23.1)
        MobsTests.register();
        dev.awaken.mobs.entity.BossHomeTests.register();
        dev.awaken.mobs.entity.FlightTests.register();
    }

    /** Ours stagger through their own stance (the wind-up ends, they stand still); any other mob is slowed to a stop. */
    private static boolean stagger(LivingEntity e, int ticks) {
        dev.awaken.mobs.entity.Topple.knock(e, ticks); // a big beast's broken stance puts it on its side
        if (e instanceof AwakenMob m) return m.ground(Math.max(ticks, dev.awaken.core.api.Aim.GROUNDED_TICKS)) || m.stagger(ticks); // a flier's broken stance brings it down
        if (!(e instanceof Mob mob) || !mob.isAlive()) return false;
        mob.getNavigation().stop();
        mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 6, false, false));
        mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 4, false, false));
        return true;
    }
}
