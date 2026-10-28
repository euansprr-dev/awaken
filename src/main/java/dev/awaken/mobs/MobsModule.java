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
        MobsEvents.init(modBus);
        MobHooks.install(new MobsSpawner());
        // what Arms reads (SPEC §24): the blow a creature is telling, how its stance breaks, what its body is made of
        // blows that land after their ability ran: a bolt as the class it was fired under, our fangs as the HEAVY they are
        Strikes.installClassifier(src -> src.getDirectEntity() instanceof dev.awaken.mobs.entity.AwBolt b ? b.strike()
                : src.getDirectEntity() instanceof net.minecraft.world.entity.projectile.EvokerFangs f && f.getOwner() instanceof AwakenMob
                ? Strikes.Kind.HEAVY : null);
        Strikes.installTells(e -> e instanceof AwakenMob m && m.telegraphTicks() > 0 && m.tellKind() != Strikes.Kind.NONE
                ? new Strikes.Tell(m.telegraphTicks(), m.tellKind()) : null);
        Strikes.installStaggers(MobsModule::stagger);
        Strikes.installGrounder((e, ticks) -> e instanceof AwakenMob m && m.ground(ticks));
        BodyMaterial.install(e -> e instanceof AwakenMob m ? m.def().material() : null);
        Signal.onAction("mobs", Mobs::onSignalAction);
        MobsCommands.register();
        MobsTests.register();
        dev.awaken.mobs.entity.BossHomeTests.register();
        dev.awaken.mobs.entity.FlightTests.register();
    }

    /** Ours stagger through their own stance (the wind-up ends, they stand still); any other mob is slowed to a stop. */
    private static boolean stagger(LivingEntity e, int ticks) {
        if (e instanceof AwakenMob m) return m.ground(Math.max(ticks, dev.awaken.core.api.Aim.GROUNDED_TICKS)) || m.stagger(ticks); // a flier's broken stance brings it down
        if (!(e instanceof Mob mob) || !mob.isAlive()) return false;
        mob.getNavigation().stop();
        mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 6, false, false));
        mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 4, false, false));
        return true;
    }
}
