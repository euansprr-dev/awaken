package dev.awaken.powers.catalog;

import dev.awaken.core.Forces;
import dev.awaken.powers.Cast;
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
        Running r = Casting.channel(c, c.ticks(100));
        r.tick(rr -> {
            c.player.resetFallDistance();
            if (rr.age % 3 == 0) c.particles(ParticleTypes.CLOUD, c.pos().add(0, -0.1, 0), 2, 0.25, 0.01);
            if (rr.age % 4 == 0) c.level.sendParticles(Fx.dust(SKY, 0.8f), c.player.getX(), c.player.getY(), c.player.getZ(), 2, 0.3, 0.05, 0.3, 0);
        }).end((rr, why) -> {
            Moves.endFlight(c.player, "hover");
            Moves.noFall(c, 100);
            if (c.is(2)) Moves.effect(c.player, MobEffects.SLOW_FALLING, 140, 0);
            c.sound(PowerSounds.HOVER, 0.7f, 0.8f);
        });
        return Result.OK;
    }

    static Result flight(Cast c) {
        float speed = (float) (0.05 * (c.is(1) ? 1.4 : c.is(2) ? 0.85 : 1.0) * (1 + 0.04 * (c.powerLevel - 1)));
        Moves.startFlight(c.player, "flight", speed);
        c.sound(PowerSounds.WHOOSH, 1f, 0.6f);
        Running r = Casting.channel(c, -1);
        double extra = pressure(c) - 1.0;
        r.tick(rr -> {
            c.player.resetFallDistance();
            if (extra > 0 && !Casting.drainExtra(c.player, (float) (c.type.drain * c.v.cost() * extra / 20.0))) rr.stop(Running.Why.NO_ENERGY);
            if (!c.player.getAbilities().flying && c.player.onGround() && rr.age > 20) rr.stop(Running.Why.TOGGLED_OFF);
            if (rr.age % 3 == 0 && c.player.getAbilities().flying) {
                c.level.sendParticles(Fx.dust(SKY, 0.9f), c.player.getX(), c.player.getY() + 0.2, c.player.getZ(), 2, 0.2, 0.1, 0.2, 0);
            }
        }).end((rr, why) -> {
            Moves.endFlight(c.player, "flight");
            Moves.noFall(c, 120);
            Moves.effect(c.player, MobEffects.SLOW_FALLING, 60, 0);
        });
        return Result.OK;
    }

    /** The Deep's pressure and gate ceilings make flying cost more there. */
    static double pressure(Cast c) {
        var dim = c.level.dimension().identifier();
        if (!dim.getNamespace().equals("awaken")) return 1.0;
        if (dim.getPath().startsWith("stratum") || dim.getPath().equals("endless")) return 2.0;
        if (dim.getPath().equals("gates")) return 1.5;
        return 1.0;
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
        Running r = Casting.start(c, c.ticks(80)).at(centre);
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
            double a = rr.age * 0.35;
            for (int i = 0; i < 3; i++) {
                double ang = a + i * Math.PI * 2 / 3;
                double rad = radius * (1 - (rr.age % 20) / 20.0);
                c.level.sendParticles(ParticleTypes.REVERSE_PORTAL, centre.x + Math.cos(ang) * rad, centre.y, centre.z + Math.sin(ang) * rad, 1, 0, 0.1, 0, 0);
            }
            c.level.sendParticles(Fx.dust(VIOLET, 1.6f), centre.x, centre.y, centre.z, 2, 0.2, 0.2, 0.2, 0);
            if (rr.age % 20 == 0) c.sound(centre, PowerSounds.GRAVITY, 0.7f, 0.5f);
        }).end((rr, why) -> {
            if (c.is(1) && why == Running.Why.EXPIRED) {
                Moves.blast(c, centre, radius + 1, c.dmg(8), Forces.Force.GRAVITY, 1.2, 0.5);
                c.particles(ParticleTypes.EXPLOSION_EMITTER, centre, 1, 0, 0);
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
                    ForceInteractions.detonate(c.level, vol.get(i), c.dmg(10), c.player);
                    ForceInteractions.detonate(c.level, vol.get(j), c.dmg(10), c.player);
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
            var f = FlyingBlocks.launch(c.level, c.player, s, from, v, c.dmg(4), Forces.Force.GRAVITY, 1.5f, 0.9f);
            if (f != null) f.gravity(0.01);
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
            if (c.is(1) && rr.age % 10 == 0) c.hurt(t, c.dmg(1.5f), Forces.Force.GRAVITY);
            if (rr.age % 2 == 0) c.level.sendParticles(Fx.dust(VIOLET, 1.0f), t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 3, 0.4, 0.4, 0.4, 0);
        }).end((rr, why) -> {
            if (why == Running.Why.CANCELLED || !t.isAlive()) return;
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
                        c.hurt(o, dmg * 0.5f, Forces.Force.GRAVITY);
                        Combat.knockFrom(o, at, 0.8, 0.3);
                    }
                }
                c.particles(ParticleTypes.EXPLOSION, at.add(0, 0.5, 0), 1, 0, 0);
                c.sound(at, PowerSounds.IMPACT, 1.1f, 0.9f);
                c.feel(t, 50, 2.5f);
                rr.stop();
            } else {
                c.level.sendParticles(ParticleTypes.CLOUD, t.getX(), t.getY() + 0.3, t.getZ(), 1, 0.1, 0.1, 0.1, 0);
            }
        });
    }

    /** Gravity meets Architect: telekinesis on your own construct throws the whole connected structure. */
    static Result wallRam(Cast c, BlockPos start) {
        List<BlockPos> wall = Constructs.connected(c.level, start, 40);
        if (wall.isEmpty()) return Result.fail("no_target");
        Vec3 dir = c.look();
        float dmg = c.dmg() * 0.6f;
        for (BlockPos p : wall) {
            BlockState s = Constructs.lift(c.level, p);
            var f = FlyingBlocks.launch(c.level, c.player, s, Vec3.atCenterOf(p), dir.scale(1.8).add(0, 0.12, 0), dmg, Forces.Force.GRAVITY, 1.2f, 1.0f);
            if (f != null) {
                f.pierce = true;
                f.stun = 10;
                f.gravity(0.02);
            }
        }
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
                return;
            }
            c.particles(c.is(1) ? ParticleTypes.FLAME : ParticleTypes.CLOUD, c.pos().add(0, 1, 0), 4, 0.3, 0.02);
            if (c.player.onGround() || c.player.isInWater()) {
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
        float dmg = c.dmg(c.type.damage + (float) Math.min(14, fallen * 0.6));
        Forces.Force force = c.is(1) ? Forces.Force.HEAT : Forces.Force.GRAVITY;
        LivingEntity first = null;
        for (LivingEntity e : Combat.foesAround(c.player, at.add(0, 0.5, 0), radius)) {
            c.hurt(e, dmg, force);
            Combat.knockFrom(e, at, 1.0, 0.7);
            if (c.is(1)) e.igniteForSeconds(4);
            if (c.is(2)) Combat.stun(e, 30);
            if (first == null) first = e;
        }
        c.particles(ParticleTypes.EXPLOSION_EMITTER, at.add(0, 0.5, 0), 1, 0, 0);
        BlockState below = c.level.getBlockState(Moves.below(c.player));
        if (!below.isAir()) Fx.burstRing(c.level, at.add(0, 0.1, 0), new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, below), 40, 0.6);
        if (c.is(1)) Fx.burstRing(c.level, at.add(0, 0.2, 0), ParticleTypes.FLAME, 24, 0.4);
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
        Fx.ring(c.level, c.pos().add(0, 0.2, 0), c.radius(10), ParticleTypes.REVERSE_PORTAL, 48, 0.05);
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
                    c.level.sendParticles(ParticleTypes.EXPLOSION, e.getX(), e.getY(), e.getZ(), 1, 0, 0, 0, 0);
                }
                c.sound(PowerSounds.SLAM, 1.2f, 0.8f);
            }
        });
        return Result.OK;
    }
}
