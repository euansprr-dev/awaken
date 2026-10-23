package dev.awaken.powers.catalog;

import dev.awaken.core.Forces;
import dev.awaken.powers.Cast;
import dev.awaken.powers.Casting;
import dev.awaken.powers.Combat;
import dev.awaken.powers.Fx;
import dev.awaken.powers.Moves;
import dev.awaken.powers.PowerMath;
import dev.awaken.powers.PowerSounds;
import dev.awaken.powers.PowerState;
import dev.awaken.powers.PowerType;
import dev.awaken.powers.PowerType.Family;
import dev.awaken.powers.PowerType.Result;
import dev.awaken.powers.PowerType.Slot;
import dev.awaken.powers.PowerType.Variant;
import dev.awaken.powers.Running;
import dev.awaken.powers.TimeSlow;
import dev.awaken.progression.Rank;
import dev.awaken.progression.Stat;
import dev.awaken.progression.Progression;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Velocity: your Redline speedster. Scales with AGI. */
final class VelocityPowers {
    private VelocityPowers() {}

    static final int RED = 0xFF4A3D;

    static void register() {
        PowerType.builder("dash", "Dash", Family.VELOCITY, Slot.MOVEMENT)
                .cost(10).cooldown(14).damage(2).unlock(Rank.E).icon("dash").dash()
                .desc("Burst forward. Works in the air, so dashes chain: ten without touching the ground is a feat.")
                .variant(Variant.of("Blink", "Teleport a short way instead of moving"))
                .variant(Variant.of("Double", "Shorter, cheaper, and back almost at once").cost(0.7f).cd(0.55f))
                .predict(VelocityPowers::dashMotion)
                .cast(VelocityPowers::dash).register();

        PowerType.builder("flow", "Flow", Family.VELOCITY, Slot.ACTIVE)
                .cost(35).cooldown(400).unlock(Rank.E).icon("flow")
                .desc("The world slows to a quarter speed around you while you don't. Lasts 4 s plus 0.05 s per AGI.")
                .variant(Variant.of("Deep", "The world slows to a tenth, but it's shorter").dur(0.7f))
                .variant(Variant.of("Long", "Half again as long, the world at a third").dur(1.5f))
                .cast(VelocityPowers::flow).register();

        PowerType.builder("afterimage", "Afterimage", Family.VELOCITY, Slot.ACTIVE)
                .cost(20).cooldown(240).damage(6).unlock(Rank.D).icon("afterimage")
                .desc("Leave a copy of yourself behind. Enemies chase it; it bursts when it fades. You get a burst of speed.")
                .variant(Variant.of("Mirror Rush", "Three afterimages, smaller bursts").dmg(0.5f))
                .variant(Variant.of("Shade", "Lasts twice as long but doesn't burst").dur(2f))
                .cast(VelocityPowers::afterimage).register();

        PowerType.builder("wall_run", "Wall Run", Family.VELOCITY, Slot.PASSIVE)
                .unlock(Rank.D).icon("wall_run")
                .desc("Sprint along a wall and you run on it for a couple of seconds instead of falling.")
                .passive(c -> {
                    if (c.player.horizontalCollision && c.player.isSprinting() && !c.player.onGround()) c.player.resetFallDistance();
                }).register();

        PowerType.builder("water_run", "Water Run", Family.VELOCITY, Slot.PASSIVE)
                .unlock(Rank.D).icon("water_run")
                .desc("Sprint and you run across water instead of sinking.")
                .passive(c -> {
                    if (c.player.isSprinting() && c.level.getFluidState(c.player.blockPosition().below()).is(net.minecraft.tags.FluidTags.WATER)) {
                        c.player.resetFallDistance();
                        if (c.player.tickCount % 2 == 0) c.particles(ParticleTypes.SPLASH, c.pos(), 3, 0.2, 0.05);
                    }
                }).register();

        PowerType.builder("lightning_step", "Lightning Step", Family.VELOCITY, Slot.MOVEMENT)
                .forces(Forces.Force.CHARGE).cost(22).cooldown(60).damage(6).unlock(Rank.C).hidden().icon("lightning_step").dash()
                .desc("Redline's signature. Become lightning for an instant: flash up to 14 blocks, shocking everything you pass through.")
                .variant(Variant.of("Arc", "The shock forks to three more foes where you land"))
                .variant(Variant.of("Long", "Up to 20 blocks").radius(1.43f))
                .cast(VelocityPowers::lightningStep).register();
    }

    // ---- behaviours -------------------------------------------------------------------------------------------------

    static Vec3 dashMotion(Player p, int variant, int level) {
        if (variant == 1) return null;
        double speed = (variant == 2 ? 1.45 : 1.9) * (1 + 0.04 * (level - 1));
        Vec3 look = p.getLookAngle();
        Vec3 f = Moves.flat(look);
        double y = p.onGround() ? 0.12 : Math.max(0.28, look.y * speed * 0.5);
        return new Vec3(f.x * speed, Math.min(0.9, y), f.z * speed);
    }

    static Result dash(Cast c) {
        if (c.is(1)) {
            Vec3 to = Moves.blinkTarget(c.player, c.look(), 6 + c.powerLevel * 0.5);
            Vec3 from = c.pos();
            if (to.distanceToSqr(from) < 1) return Result.fail("blocked");
            Moves.teleport(c.player, to);
            Fx.line(c.level, from.add(0, 1, 0), to.add(0, 1, 0), Fx.dust(RED, 1.0f), 0.4);
            c.sound(PowerSounds.WHOOSH, 1f, 1.6f);
            return Result.OK;
        }
        if (!c.predicted) Moves.launch(c.player, dashMotion(c.player, c.variant, c.powerLevel));
        Moves.noFall(c, 30);
        c.sound(PowerSounds.WHOOSH, 0.9f, 1.2f + c.player.getRandom().nextFloat() * 0.2f);
        Running r = Casting.start(c, 7);
        float dmg = c.dmg();
        r.tick(rr -> {
            Vec3 p = c.pos().add(0, 0.9, 0);
            c.level.sendParticles(Fx.dust(RED, 1.1f), p.x, p.y, p.z, 3, 0.2, 0.4, 0.2, 0);
            c.level.sendParticles(ParticleTypes.CLOUD, p.x, p.y - 0.6, p.z, 1, 0.1, 0.1, 0.1, 0.01);
            for (LivingEntity e : Combat.foesAround(c.player, p, 1.6)) {
                if (rr.hit.add(e.getId())) {
                    c.hurt(e, dmg, null);
                    Combat.knockFrom(e, c.pos(), 0.4, 0.2);
                }
            }
        });
        return Result.OK;
    }

    static Result flow(Cast c) {
        PowerState st = PowerState.of(c.player);
        int agi = Progression.stat(c.player, Stat.AGI);
        int ticks = PowerMath.flowTicks(agi, flowPerk(c), c.v.duration());
        int factor = c.is(1) ? 10 : c.is(2) ? 3 : 4;
        TimeSlow.Zone zone = TimeSlow.add(c.player, 18, factor);
        Moves.addMod(c.player, Attributes.MOVEMENT_SPEED, "flow_speed", 0.35, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        Moves.addMod(c.player, Attributes.ATTACK_SPEED, "flow_attack", 0.4, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        Moves.postOn(c.player, Moves.FX_FLOW);
        c.sound(PowerSounds.FLOW_START, 1f, 1f);
        c.particles(ParticleTypes.END_ROD, c.pos().add(0, 1, 0), 20, 0.6, 0.15);
        Running r = Casting.start(c, ticks);
        r.tick(rr -> {
            st.flowTick();
            if (rr.age % 2 == 0) {
                Vec3 p = c.pos().add(0, 1, 0);
                c.level.sendParticles(Fx.dust(0xFFE0D0, 0.9f), p.x, p.y, p.z, 2, 0.25, 0.5, 0.25, 0);
            }
        }).end((rr, why) -> {
            TimeSlow.remove(zone);
            Moves.removeMod(c.player, Attributes.MOVEMENT_SPEED, "flow_speed");
            Moves.removeMod(c.player, Attributes.ATTACK_SPEED, "flow_attack");
            Moves.postOff(c.player, Moves.FX_FLOW);
            c.sound(PowerSounds.FLOW_END, 0.9f, 1f);
        });
        return Result.OK;
    }

    private static double flowPerk(Cast c) {
        return Progression.perk(c.player, dev.awaken.progression.Perks.FLOW_DURATION);
    }

    static Result afterimage(Cast c) {
        int ticks = c.ticks(80);
        float pop = c.is(2) ? 0 : c.dmg();
        List<Vec3> spots = new ArrayList<>();
        spots.add(c.pos());
        if (c.is(1)) {
            Vec3 side = c.flatLook().yRot((float) (Math.PI / 2));
            spots.add(c.pos().add(side.scale(1.6)));
            spots.add(c.pos().subtract(side.scale(1.6)));
        }
        for (Vec3 s : spots) Moves.decoy(c, s, ticks, pop, 3.0, 14, "afterimage");
        Moves.effect(c.player, MobEffects.SPEED, 50, 1);
        c.sound(PowerSounds.WHOOSH, 1f, 0.7f);
        c.sound(PowerSounds.SHADOW, 0.6f, 1.4f);
        return Result.OK;
    }

    static Result lightningStep(Cast c) {
        double max = 14 * c.v.radius();
        Vec3 from = c.pos();
        Vec3 to = Moves.blinkTarget(c.player, c.look(), max);
        if (to.distanceToSqr(from) < 2.25) return Result.fail("blocked");
        Moves.teleport(c.player, to);
        Moves.noFall(c, 40);
        float dmg = c.dmg();
        LivingEntity last = null;
        for (LivingEntity e : Combat.alongLine(c.player, from.add(0, 1, 0), to.add(0, 1, 0), 1.2)) {
            c.hurt(e, dmg, Forces.Force.CHARGE);
            Combat.stun(e, 12);
            last = e;
        }
        Fx.lightning(c.level, from.add(0, 1, 0), to.add(0, 1, 0), Math.max(4, (int) (from.distanceTo(to) / 2)));
        dev.awaken.powers.ForceInteractions.chargeEchoes(c.player, to, 3);
        c.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y + 1, to.z, 20, 0.4, 0.8, 0.4, 0.2);
        c.sound(from, PowerSounds.ZAP, 1.1f, 1.5f);
        c.sound(to, PowerSounds.THUNDER, 0.8f, 1.6f);
        if (c.is(1)) {
            Vec3 origin = last != null ? last.position().add(0, 1, 0) : to.add(0, 1, 0);
            int forks = 0;
            for (LivingEntity o : Combat.foesAround(c.player, origin, 8)) {
                if (forks >= 3 || o == last) continue;
                Fx.lightning(c.level, origin, o.position().add(0, 1, 0), 4);
                c.hurt(o, dmg * 0.6f, Forces.Force.CHARGE);
                forks++;
            }
        }
        c.feel(last, last != null ? 40 : 0, 1.5f);
        return Result.OK;
    }
}
