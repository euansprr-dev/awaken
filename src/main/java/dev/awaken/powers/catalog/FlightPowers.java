package dev.awaken.powers.catalog;

import dev.awaken.powers.fx.CastAnim;
import dev.awaken.powers.fx.FxEvent;
import dev.awaken.powers.fx.FxKind;
import dev.awaken.powers.fx.FxStyle;

import dev.awaken.core.Forces;
import dev.awaken.powers.Cast;
import dev.awaken.powers.PowerNumbers;
import dev.awaken.powers.Casting;
import dev.awaken.powers.Combat;
import dev.awaken.powers.Constructs;
import dev.awaken.powers.FlyingBlocks;
import dev.awaken.powers.ForceInteractions;
import dev.awaken.powers.Fx;
import dev.awaken.powers.Moves;
import dev.awaken.powers.PowerSounds;
import dev.awaken.powers.PowerType;
import dev.awaken.powers.PowerType.Family;
import dev.awaken.powers.PowerType.Result;
import dev.awaken.powers.PowerType.Slot;
import dev.awaken.powers.PowerType.Variant;
import dev.awaken.powers.Running;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Flight and gravity: lift mobs and blocks and throw them. Scales with INT. */
final class FlightPowers {
    private FlightPowers() {}

    static final int SKY = 0xA7CDE3;
    static final int VIOLET = 0x9A6BFF;

    static void register() {
        PowerType.builder("hover", "Hover", Family.FLIGHT, Slot.ACTIVE)
                .cost(15).cooldown(200).unlock(Rank.E).icon("hover").toggle(0, 100)
                .desc("Hang in the air for 5 s and drift slowly. Press again to drop. No fall damage after.")
                .variant(Variant.of("Long", "Hover for 8 s").dur(1.6f))
                .variant(Variant.of("Glide", "Shorter, but you glide down slowly afterwards").dur(0.7f))
                .cast(FlightPowers::hover).register();

        PowerType.builder("flight", "Flight", Family.FLIGHT, Slot.MOVEMENT)
                .cost(10).cooldown(40).unlock(Rank.A).icon("flight").toggle(6, 0)
                .desc("Real flight. Drains energy while you fly (twice as fast in the Deep, half again in gates). Press again to land.")
                .variant(Variant.of("Swift", "40% faster, drains 40% faster").cost(1.4f))
                .variant(Variant.of("Efficient", "Slower, drains 40% less").cost(0.6f))
                .cast(FlightPowers::flight).register();

        PowerType.builder("gravity_well", "Gravity Well", Family.FLIGHT, Slot.ACTIVE)
                .forces(Forces.Force.GRAVITY).cost(28).cooldown(280).damage(2).unlock(Rank.D).icon("well")
                .desc("Open a well of gravity where you aim. It drags enemies (and your own walls) into a crushing knot for 4 s.")
                .variant(Variant.of("Singularity", "Smaller, pulls twice as hard, implodes at the end").radius(0.7f).dmg(1.5f))
                .variant(Variant.of("Repulsor", "Throws everything outward instead"))
                .cast(FlightPowers::gravityWell).register();

        PowerType.builder("telekinesis", "Telekinesis", Family.FLIGHT, Slot.ACTIVE)
                .forces(Forces.Force.GRAVITY).cost(20).cooldown(160).damage(9).unlock(Rank.C).icon("telekinesis").channel(6, 60)
                .desc("Hold to lift a mob in front of you, release to throw it. Aim at your own wall to throw the whole wall.")
                .variant(Variant.of("Crush", "Squeeze while you hold; release slams it down"))
                .variant(Variant.of("Fling", "Throws half again as hard, holds half as long").dmg(1.2f))
                .cast(FlightPowers::telekinesis).register();

        PowerType.builder("meteor_dive", "Meteor Dive", Family.FLIGHT, Slot.ACTIVE)
                .forces(Forces.Force.GRAVITY).cost(30).cooldown(240).damage(10).unlock(Rank.C).icon("meteor")
                .desc("Launch up, then come down like a meteor. The impact grows with the height you fell.")
                .variant(Variant.of("Comet", "Burning: sets everything it hits on fire"))
                .variant(Variant.of("Crater", "A wider crater that stuns").radius(1.4f))
                .cast(FlightPowers::meteorDive).register();

        PowerType.builder("gravity_flip", "Gravity Flip", Family.FLIGHT, Slot.ACTIVE)
                .forces(Forces.Force.GRAVITY).cost(30).cooldown(300).damage(8).unlock(Rank.B).hidden().icon("flip")
                .desc("A latent gift from the Inverted Sea. Everything near you falls up, then comes crashing down. You feel light for a moment.")
                .cast(FlightPowers::gravityFlip).register();
    }

    // ---- behaviours -------------------------------------------------------------------------------------------------

    static Result hover(Cast c) {
        Moves.startFlight(c.player, "hover", 0.02f);
        c.sound(PowerSounds.HOVER, 1f, 1.2f);
        int hoverTicks = PowerNumbers.effectTicks(c);
        // a gust ring under the feet, then wind rings and curls while you hang there
        Fx.at(c, FxKind.RING, c.pos()).scale(2.2).ticks(10).send();
        Fx.at(c, FxKind.AURA, c.pos()).aux(FxKind.Aura.HOVER.ordinal()).ticks(hoverTicks).noAnim().send();
        Running r = Casting.channel(c, hoverTicks);
        r.tick(rr -> c.player.resetFallDistance()).end((rr, why) -> {
            Fx.end(c, c.player, FxKind.Aura.HOVER.ordinal() + 1);
            Fx.end(c, c.player, 0);
            Moves.endFlight(c.player, "hover");
            Moves.noFall(c, 100);
            if (c.is(2)) Moves.effect(c.player, MobEffects.SLOW_FALLING, 140, 0);
            c.sound(PowerSounds.HOVER, 0.7f, 0.8f);
        });
        return Result.OK;
    }

    static Result flight(Cast c) {
        float speed = PowerNumbers.flightSpeed(c);
        Moves.startFlight(c.player, "flight", speed);
        c.sound(PowerSounds.WHOOSH, 1f, 0.6f);
        Fx.at(c, FxKind.RING, c.pos()).scale(3).ticks(12).send();
        Fx.at(c, FxKind.AURA, c.pos()).aux(FxKind.Aura.FLIGHT.ordinal()).ticks(120).noAnim().send();
        Running r = Casting.channel(c, -1);
        double extra = PowerNumbers.flightPressure(c) - 1.0;
        r.tick(rr -> {
            c.player.resetFallDistance();
            if (extra > 0 && !Casting.drainExtra(c.player, (float) (c.type.drain * c.v.cost() * extra / 20.0))) rr.stop(Running.Why.NO_ENERGY);
            if (!c.player.getAbilities().flying && c.player.onGround() && rr.age > 20) rr.stop(Running.Why.TOGGLED_OFF);
            // wind ribbons on hands and feet, refreshed while you fly
            if (rr.age % 100 == 99) Fx.at(c, FxKind.AURA, c.pos()).aux(FxKind.Aura.FLIGHT.ordinal()).ticks(120).noAnim().send();
        }).end((rr, why) -> {
            Fx.end(c, c.player, FxKind.Aura.FLIGHT.ordinal() + 1);
            Moves.endFlight(c.player, "flight");
            Moves.noFall(c, 120);
            Moves.effect(c.player, MobEffects.SLOW_FALLING, 60, 0);
        });
        return Result.OK;
    }

    static Vec3 aimSpot(Cast c, double range) {
        LivingEntity t = Combat.aimEntity(c, range, 0.8);
        if (t != null) return t.position().add(0, 0.5, 0);
        Vec3 p = Combat.aimPoint(c, range);
        return p.subtract(c.look().scale(0.5));
    }

    static Result gravityWell(Cast c) {
        Vec3 centre = aimSpot(c, 24);
        double radius = c.radius(6);
        boolean repulse = c.is(2);
        double pull = c.is(1) ? 0.16 : 0.09;
        float dmg = c.dmg();
        c.sound(centre, PowerSounds.GRAVITY, 1.2f, 0.7f);
        int wellTicks = PowerNumbers.effectTicks(c);
        // a void core with a violet rim, rings contracting into it, sparks and debris drawn in
        Fx.at(c, FxKind.VORTEX, centre).scale(radius).ticks(wellTicks).flag(FxEvent.REPULSE, repulse).flag(FxEvent.REACH).send();
        Running r = Casting.start(c, wellTicks).at(centre);
        r.tick(rr -> {
            for (LivingEntity e : Combat.foesAround(c.player, centre, radius)) {
                Vec3 d = centre.subtract(e.position().add(0, e.getBbHeight() * 0.5, 0));
                double len = d.length();
                if (len < 0.1) continue;
                Vec3 dir = d.scale(1 / len);
                if (repulse) dir = dir.scale(-1);
                double strength = Combat.isHeavy(e) ? pull * 0.25 : pull;
                Vec3 v = e.getDeltaMovement().scale(0.6).add(dir.scale(strength * (repulse ? 3 : Math.min(1.0, len))));
                Combat.push(e, v);
                e.fallDistance = 0;
                if (rr.age % 10 == 0) c.hurt(e, dmg, Forces.Force.GRAVITY);
            }
            if (rr.age % 2 == 0) {
                for (Projectile p : c.level.getEntitiesOfClass(Projectile.class, AABB.ofSize(centre, radius * 2, radius * 2, radius * 2),
                        p -> p.getOwner() != c.player)) {
                    Combat.push(p, p.getDeltaMovement().scale(0.5).add(centre.subtract(p.position()).normalize().scale(0.2)));
                }
            }
            if (rr.age % 5 == 0 && !repulse) volatileCheck(c, centre, radius);
            if (rr.age == 4 && !repulse) pullWalls(c, centre, radius);
            if (rr.age % 20 == 0) c.sound(centre, PowerSounds.GRAVITY, 0.7f, 0.5f);
        }).end((rr, why) -> {
            if (c.is(1) && why == Running.Why.EXPIRED) {
                Moves.blast(c, centre, radius + 1, PowerNumbers.hit(c, PowerNumbers.Hit.WELL_END), Forces.Force.GRAVITY, 1.2, 0.5);
                Fx.at(c, FxKind.NOVA, centre).scale(radius + 1).flag(FxEvent.REPULSE).noAnim().send();
                c.sound(centre, PowerSounds.BLAST, 1.2f, 0.6f);
                Fx.quake(c.level, centre, 16, 3f);
            }
        });
        return Result.OK;
    }

    /** Gravity meets Volatile: two volatile foes pulled within reach of each other detonate. */
    static void volatileCheck(Cast c, Vec3 centre, double radius) {
        List<LivingEntity> vol = new ArrayList<>();
        for (LivingEntity e : Combat.foesAround(c.player, centre, radius)) {
            if (Forces.has(e, Forces.Tag.VOLATILE) || e instanceof Creeper) vol.add(e);
        }
        for (int i = 0; i < vol.size(); i++) {
            for (int j = i + 1; j < vol.size(); j++) {
                if (vol.get(i).distanceToSqr(vol.get(j)) < 2.5 * 2.5) {
                    ForceInteractions.detonate(c.level, vol.get(i), PowerNumbers.hit(c, PowerNumbers.Hit.VOLATILE), c.player);
                    ForceInteractions.detonate(c.level, vol.get(j), PowerNumbers.hit(c, PowerNumbers.Hit.VOLATILE), c.player);
                    ForceInteractions.count(c.player, ForceInteractions.Reaction.VOLATILE_DETONATION);
                    return;
                }
            }
        }
    }

    /** Gravity meets Architect: your constructs inside the well tear loose and fly into it. */
    static void pullWalls(Cast c, Vec3 centre, double radius) {
        List<BlockPos> walls = Constructs.ownedNear(c.player, centre, radius);
        if (walls.isEmpty()) return;
        int n = 0;
        for (BlockPos p : walls) {
            if (n++ >= 16) break;
            BlockState s = Constructs.lift(c.level, p);
            Vec3 from = Vec3.atCenterOf(p);
            Vec3 v = centre.subtract(from).normalize().scale(0.9).add(0, 0.2, 0);
            var f = FlyingBlocks.launch(c.level, c.player, s, from, v, PowerNumbers.hit(c, PowerNumbers.Hit.WALL_FRAGMENT), Forces.Force.GRAVITY, 1.5f, 0.9f);
            if (f != null) f.gravity(0.01);
            if (n <= 6) Fx.trailOn(c, f, 30);
        }
        ForceInteractions.count(c.player, ForceInteractions.Reaction.WALL_THROW);
    }

    static Result telekinesis(Cast c) {
        BlockHitResult block = Combat.aimBlock(c, 20);
        if (block != null && Constructs.isConstruct(c.level, block.getBlockPos())) return wallRam(c, block.getBlockPos());
        LivingEntity t = Combat.aimEntity(c, 20, 0.8);
        if (t == null) return Result.fail("no_target");
        if (Combat.isHeavy(t)) return Result.fail("too_heavy");
        int max = c.is(2) ? 30 : c.type.maxDuration;
        c.sound(t.position(), PowerSounds.GRAVITY, 1f, 1.4f);
        // the grip: violet rings around the lifted mob and a cord of force from the hand
        Fx.at(c, FxKind.LIFT, t.position()).target(t).ticks(max).send();
        Fx.at(c, FxKind.TETHER, c.player.getEyePosition()).target(t).to(t.position()).aux(2).ticks(max).anim(CastAnim.AIM_HOLD).send();
        Running r = Casting.channel(c, max);
        r.target = t;
        r.tick(rr -> {
            if (!t.isAlive()) {
                rr.stop(Running.Why.DONE);
                return;
            }
            Vec3 hold = c.player.getEyePosition().add(c.player.getLookAngle().scale(4.0)).subtract(0, t.getBbHeight() * 0.5, 0);
            Combat.push(t, hold.subtract(t.position()).scale(0.35));
            t.fallDistance = 0;
            if (c.is(1) && rr.age % 10 == 0) c.hurt(t, PowerNumbers.hit(c, PowerNumbers.Hit.CRUSH_PULSE), Forces.Force.GRAVITY);
        }).end((rr, why) -> {
            Fx.end(c, c.player, 0);
            if (why == Running.Why.CANCELLED || !t.isAlive()) return;
            Fx.at(c, FxKind.CAST, c.player.getEyePosition()).anim(CastAnim.THROW).send();
            Fx.at(c, FxKind.TRAIL, t.position()).caster(t).aux(2).ticks(30).noAnim().send();
            Vec3 v = c.is(1) ? new Vec3(0, -2.6, 0) : c.player.getLookAngle().scale(c.is(2) ? 3.6 : 2.4);
            Combat.push(t, v);
            c.sound(PowerSounds.THROW, 1f, 1f);
            throwWatch(c, t);
        });
        return Result.OK;
    }

    /** After a throw: the first collision hurts the thrown mob and whatever it lands on. */
    static void throwWatch(Cast c, LivingEntity t) {
        Running w = Casting.start(c, "telekinesis_throw", 40);
        float dmg = c.dmg();
        w.tick(rr -> {
            if (!t.isAlive()) {
                rr.stop();
                return;
            }
            boolean bumped = rr.age > 2 && (t.horizontalCollision || t.verticalCollision || t.onGround());
            LivingEntity other = null;
            for (LivingEntity o : Combat.foesAround(c.player, t.position().add(0, t.getBbHeight() * 0.5, 0), 1.5)) {
                if (o != t) {
                    other = o;
                    break;
                }
            }
            if (bumped || other != null) {
                Vec3 at = t.position();
                c.hurt(t, dmg, Forces.Force.GRAVITY);
                for (LivingEntity o : Combat.foesAround(c.player, at, 2.5)) {
                    if (o != t) {
                        c.hurt(o, PowerNumbers.secondary(dmg, PowerNumbers.Hit.THROW_SPLASH), Forces.Force.GRAVITY);
                        Combat.knockFrom(o, at, 0.8, 0.3);
                    }
                }
                Fx.at(c, FxKind.SLAM, at).scale(2.4).noAnim().send();
                Fx.at(c, FxKind.IMPACT, at.add(0, 0.5, 0)).scale(1.6).noAnim().send();
                c.sound(at, PowerSounds.IMPACT, 1.1f, 0.9f);
                c.feel(t, 50, 2.5f);
                rr.stop();
            }
        });
    }

    /** Gravity meets Architect: telekinesis on your own construct throws the whole connected structure. */
    static Result wallRam(Cast c, BlockPos start) {
        List<BlockPos> wall = Constructs.connected(c.level, start, 40);
        if (wall.isEmpty()) return Result.fail("no_target");
        Vec3 dir = c.look();
        float dmg = PowerNumbers.hit(c, PowerNumbers.Hit.WALL_RAM);
        for (BlockPos p : wall) {
            BlockState s = Constructs.lift(c.level, p);
            var f = FlyingBlocks.launch(c.level, c.player, s, Vec3.atCenterOf(p), dir.scale(1.8).add(0, 0.12, 0), dmg, Forces.Force.GRAVITY, 1.2f, 1.0f);
            if (f != null) {
                f.pierce = true;
                f.stun = 10;
                f.gravity(0.02);
            }
            if (f != null && wall.size() <= 16) Fx.trailOn(c, f, 30);
        }
        Fx.at(c, FxKind.CAST, c.player.getEyePosition()).anim(CastAnim.PUSH2).send();
        Fx.at(c, FxKind.RING, Vec3.atCenterOf(start)).dir(dir).scale(3).ticks(8).noAnim().send();
        c.sound(Vec3.atCenterOf(start), PowerSounds.THROW, 1.3f, 0.6f);
        Fx.quake(c.level, c.pos(), 10, 2f);
        ForceInteractions.count(c.player, ForceInteractions.Reaction.WALL_THROW);
        return Result.OK;
    }

    static Result meteorDive(Cast c) {
        boolean grounded = !Combat.airborne(c.player);
        if (grounded) Moves.launch(c.player, new Vec3(0, 1.5, 0));
        Moves.noFall(c, 160);
        c.sound(PowerSounds.WHOOSH, 1f, 0.5f);
        Fx.at(c, FxKind.TRAIL, c.pos()).aux(2).ticks(24).send();
        Running r = Casting.start(c, 140);
        r.value = c.player.getY();
        r.count = grounded ? 0 : 1; // phase: 0 rising, 1 diving
        r.tick(rr -> {
            if (rr.count == 0) {
                if (rr.age > 3 && (c.player.getDeltaMovement().y < 0.05 || rr.age > 14)) {
                    rr.count = 1;
                    rr.value = c.player.getY();
                }
                return;
            }
            if (rr.count == 1) {
                Vec3 f = Moves.flat(c.player.getLookAngle()).scale(0.6);
                Moves.launch(c.player, new Vec3(f.x, -2.6, f.z));
                rr.count = 2;
                c.sound(PowerSounds.WHOOSH, 1.2f, 0.4f);
                // the comet: a burning (or violet) trail and glow until it hits
                var comet = Fx.at(c, FxKind.METEOR, c.pos()).ticks(60).anim(CastAnim.SLAM_WIND);
                if (c.is(1)) comet.style(FxStyle.HEAT);
                comet.send();
                return;
            }
            if (c.player.onGround() || c.player.isInWater()) {
                Fx.end(c, c.player, 0);
                double fallen = Math.max(0, rr.value - c.player.getY());
                impact(c, fallen);
                rr.stop();
            }
        });
        return Result.OK;
    }

    static void impact(Cast c, double fallen) {
        Vec3 at = c.pos();
        double radius = c.radius(5);
        float dmg = PowerNumbers.fallDamage(c, PowerNumbers.fallBonus(fallen));
        Forces.Force force = c.is(1) ? Forces.Force.HEAT : Forces.Force.GRAVITY;
        LivingEntity first = null;
        for (LivingEntity e : Combat.foesAround(c.player, at.add(0, 0.5, 0), radius)) {
            c.hurt(e, dmg, force);
            Combat.knockFrom(e, at, 1.0, 0.7);
            if (c.is(1)) e.igniteForSeconds(4);
            if (c.is(2)) Combat.stun(e, 30);
            if (first == null) first = e;
        }
        var slam = Fx.at(c, FxKind.SLAM, at).scale(radius).flag(FxEvent.REACH).flag(FxEvent.FULL, fallen > 10);
        if (c.is(1)) slam.style(FxStyle.HEAT);
        slam.send();
        if (first != null) Fx.at(c, FxKind.IMPACT, first.getBoundingBox().getCenter()).scale(1.6).noAnim().send();
        c.sound(PowerSounds.SLAM, 1.5f, 0.6f);
        Fx.quake(c.level, at, 24, 5f);
        c.feel(first, first != null ? 80 : 0, 5f);
    }

    static Result gravityFlip(Cast c) {
        List<LivingEntity> foes = Combat.foesAround(c.player, c.pos(), c.radius(10));
        for (LivingEntity e : foes) {
            if (Combat.isHeavy(e)) continue;
            Moves.effect(e, MobEffects.LEVITATION, 30, 3);
        }
        Moves.effect(c.player, MobEffects.SLOW_FALLING, 80, 0);
        Moves.effect(c.player, MobEffects.JUMP_BOOST, 80, 2);
        Moves.noFall(c, 120);
        c.sound(PowerSounds.GRAVITY, 1.3f, 1.5f);
        Fx.at(c, FxKind.RING, c.pos()).scale(c.radius(10)).ticks(14).flag(FxEvent.REACH).send();
        int lifted = 0;
        for (LivingEntity e : foes) {
            if (Combat.isHeavy(e) || lifted++ >= 8) continue;
            Fx.at(c, FxKind.LIFT, e.position()).target(e).ticks(30).noAnim().send();
        }
        float dmg = c.dmg();
        Running r = Casting.start(c, 45);
        r.tick(rr -> {
            if (rr.age == 30) {
                for (LivingEntity e : foes) {
                    if (!e.isAlive() || Combat.isHeavy(e)) continue;
                    e.removeEffect(MobEffects.LEVITATION);
                    Combat.push(e, new Vec3(0, -2.2, 0));
                }
            }
            if (rr.age == 38) {
                for (LivingEntity e : foes) {
                    if (!e.isAlive()) continue;
                    c.hurt(e, dmg, Forces.Force.GRAVITY);
                    Fx.at(c, FxKind.SLAM, e.position()).scale(1.8).noAnim().send();
                }
                c.sound(PowerSounds.SLAM, 1.2f, 0.8f);
            }
        });
        return Result.OK;
    }
}
