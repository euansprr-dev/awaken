package dev.awaken.powers.catalog;

import dev.awaken.core.Forces;
import dev.awaken.powers.Cast;
import dev.awaken.powers.Casting;
import dev.awaken.powers.Combat;
import dev.awaken.powers.Fx;
import dev.awaken.powers.Moves;
import dev.awaken.powers.PowerSounds;
import dev.awaken.powers.PowerState;
import dev.awaken.powers.PowerType;
import dev.awaken.powers.PowerType.Family;
import dev.awaken.powers.PowerType.Result;
import dev.awaken.powers.PowerType.Slot;
import dev.awaken.powers.PowerType.Variant;
import dev.awaken.powers.Running;
import dev.awaken.powers.content.PowerContent;
import dev.awaken.powers.content.PowerDrone;
import dev.awaken.progression.Rank;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Tech: exosuit tricks, drones and rails. Scales with INT. */
final class TechPowers {
    private TechPowers() {}

    static final int TEAL = 0x47C6B0;

    static void register() {
        PowerType.builder("grapple", "Grapple", Family.TECH, Slot.MOVEMENT)
                .cost(10).cooldown(50).damage(3).unlock(Rank.E).icon("grapple")
                .desc("Fire a hook up to 28 blocks. Hook a wall and you're yanked to it; hook a mob and it's yanked to you.")
                .variant(Variant.of("Zipline", "A faster, flatter pull").radius(1.25f))
                .variant(Variant.of("Harpoon", "Hooked mobs take more and are stunned").dmg(2f))
                .predict(TechPowers::grappleMotion)
                .cast(TechPowers::grapple).register();

        PowerType.builder("jet_burst", "Jet Burst", Family.TECH, Slot.MOVEMENT)
                .forces(Forces.Force.HEAT).cost(14).cooldown(40).damage(2).unlock(Rank.D).icon("jet")
                .desc("Kick on your boot jets for a burst in the direction you're looking. The exhaust scorches what's behind you.")
                .variant(Variant.of("Afterburner", "Faster, slower to recover").cd(1.3f))
                .variant(Variant.of("Vertical", "Straight up, high"))
                .predict((p, variant, level) -> {
                    if (variant == 2) return new Vec3(p.getDeltaMovement().x, 1.55, p.getDeltaMovement().z);
                    double s = variant == 1 ? 1.95 : 1.5;
                    Vec3 l = p.getLookAngle();
                    return new Vec3(l.x * s, Math.max(0.45, l.y * s + 0.35), l.z * s);
                })
                .cast(TechPowers::jet).register();

        PowerType.builder("drone", "Drone", Family.TECH, Slot.ACTIVE)
                .forces(Forces.Force.CHARGE).cost(25).cooldown(300).damage(2.5f).unlock(Rank.D).icon("drone")
                .desc("Launch a combat drone that orbits you for 20 s and zaps what you're fighting. Up to two at once.")
                .variant(Variant.of("Gunship", "Slower, heavier shots").dmg(1.5f))
                .variant(Variant.of("Twin", "Two lighter drones at once").dmg(0.6f))
                .cast(TechPowers::drone).register();

        PowerType.builder("turret", "Turret", Family.TECH, Slot.ACTIVE)
                .forces(Forces.Force.CHARGE).cost(30).cooldown(400).damage(3.5f).unlock(Rank.C).icon("turret")
                .desc("Deploy a turret where you aim. It holds the spot for 30 s, shooting anything hostile within 20 blocks.")
                .variant(Variant.of("Mortar", "Slow explosive shells").dmg(1.2f))
                .variant(Variant.of("Rapid", "Fast, light shots").dmg(0.5f))
                .cast(TechPowers::turret).register();

        PowerType.builder("railgun", "Railgun", Family.TECH, Slot.ACTIVE)
                .forces(Forces.Force.CHARGE).cost(35).cooldown(240).damage(18).unlock(Rank.B).icon("rail").channel(0, 24)
                .desc("Hold to charge, release to fire a hypersonic slug that pierces every enemy in a 48-block line.")
                .variant(Variant.of("Coil", "Charges twice as fast, a little weaker").dmg(0.8f))
                .variant(Variant.of("Breach", "Heavier and staggering").dmg(1.25f))
                .cast(TechPowers::railgun).register();

        PowerType.builder("nanite_skin", "Nanite Skin", Family.TECH, Slot.ACTIVE)
                .cost(25).cooldown(500).unlock(Rank.C).icon("nanite")
                .desc("For 10 s a nanite film hardens over you: +8 armour, +4 toughness, slow healing, and melee hits shock the attacker.")
                .variant(Variant.of("Hardened", "Twice the armour, no healing").dmg(2f))
                .variant(Variant.of("Mending", "Less armour, heals three times as fast").dmg(0.5f))
                .cast(TechPowers::nanite).register();

        PowerType.builder("drone_swarm", "Drone Swarm", Family.TECH, Slot.ULTIMATE)
                .forces(Forces.Force.CHARGE).cost(60).cooldown(1200).damage(2.5f).unlock(Rank.C).hidden().icon("swarm")
                .desc("Engineer's signature. Six drones pour out and focus fire on whatever you aim at for 20 s.")
                .variant(Variant.of("Hive", "Ten lighter drones").dmg(0.6f))
                .variant(Variant.of("Strike", "Three heavy drones that fire fast").dmg(2f))
                .cast(TechPowers::swarm).register();
    }

    // ---- behaviours -------------------------------------------------------------------------------------------------

    static @Nullable Vec3 grappleAnchor(Player p, double range) {
        Vec3 from = p.getEyePosition();
        Vec3 to = from.add(p.getLookAngle().scale(range));
        BlockHitResult hit = p.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.MISS ? null : hit.getLocation();
    }

    static Vec3 grappleMotion(Player p, int variant, int level) {
        Vec3 anchor = grappleAnchor(p, 28);
        if (anchor == null) return null;
        // a hooked mob is pulled instead; the client can't know that, so it only predicts when the aim hits a wall first
        Vec3 d = anchor.subtract(p.position().add(0, 0.5, 0));
        double dist = d.length();
        double speed = Math.min(variant == 1 ? 2.4 : 1.9, 0.6 + dist * 0.12);
        Vec3 v = d.normalize().scale(speed);
        return new Vec3(v.x, v.y + (variant == 1 ? 0.15 : 0.4), v.z);
    }

    static Result grapple(Cast c) {
        LivingEntity mob = Combat.aimEntity(c, 28, 0.5);
        Vec3 anchor = grappleAnchor(c.player, 28);
        boolean mobFirst = mob != null && (anchor == null || mob.distanceToSqr(c.player) < anchor.distanceToSqr(c.player.getEyePosition()));
        if (mobFirst) {
            if (c.predicted) Combat.push(c.player, Vec3.ZERO); // the client guessed a wall pull; cancel it
            Vec3 hook = mob.getBoundingBox().getCenter();
            Fx.beam(c.player, c.player, hook, 0xFFB0B8C0, 5, 0.03f, 28, false);
            c.hurt(mob, c.dmg(), null);
            if (!Combat.isHeavy(mob)) {
                Vec3 d = c.pos().subtract(mob.position());
                Combat.push(mob, d.normalize().scale(Math.min(2.2, 0.5 + d.length() * 0.18)).add(0, 0.35, 0));
            }
            if (c.is(2)) Combat.stun(mob, 30);
            c.sound(PowerSounds.GRAPPLE, 1f, 1.2f);
            return Result.OK;
        }
        if (anchor == null) return Result.fail("no_target");
        if (!c.predicted) Moves.launch(c.player, grappleMotion(c.player, c.variant, c.powerLevel));
        Moves.noFall(c, 80);
        Fx.beam(c.player, c.player, anchor, 0xFFB0B8C0, 6, 0.03f, 28, false);
        c.particles(ParticleTypes.CRIT, anchor, 6, 0.1, 0.1);
        c.sound(PowerSounds.GRAPPLE, 1f, 1f);
        return Result.OK;
    }

    static Result jet(Cast c) {
        if (!c.predicted) Moves.launch(c.player, c.type.predictor.motion(c.player, c.variant, c.powerLevel));
        Moves.noFall(c, 80);
        c.sound(PowerSounds.JET, 1f, 1f);
        Vec3 back = c.look().scale(-1);
        float dmg = c.dmg();
        Running r = Casting.start(c, 8);
        r.tick(rr -> {
            Vec3 feet = c.pos();
            c.level.sendParticles(ParticleTypes.FLAME, feet.x, feet.y, feet.z, 3, 0.1, 0.05, 0.1, 0.03);
            c.level.sendParticles(ParticleTypes.SMOKE, feet.x, feet.y, feet.z, 2, 0.15, 0.05, 0.15, 0.02);
            if (rr.age == 1) {
                for (LivingEntity e : Combat.foesInCone(c.player, feet, back, 3.5, 35)) {
                    c.hurt(e, dmg, Forces.Force.HEAT);
                    e.igniteForSeconds(2);
                }
            }
        });
        return Result.OK;
    }

    static List<PowerDrone> drones(Cast c, boolean turrets) {
        List<PowerDrone> out = new ArrayList<>();
        for (PowerDrone d : c.level.getEntitiesOfClass(PowerDrone.class, c.player.getBoundingBox().inflate(64),
                d -> d.getOwner() == c.player && d.isTurret() == turrets && d.isAlive())) out.add(d);
        out.sort(Comparator.comparingInt(PowerDrone::lifeLeft));
        return out;
    }

    static @Nullable PowerDrone spawnDrone(Cast c, Vec3 at, float dmg, int life) {
        PowerDrone d = PowerContent.DRONE.get().create(c.level, EntitySpawnReason.MOB_SUMMONED);
        if (d == null) return null;
        d.snapTo(at.x, at.y, at.z, c.yRot(), 0);
        d.setup(c.player, life, dmg);
        c.level.addFreshEntity(d);
        c.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.1);
        return d;
    }

    static Result drone(Cast c) {
        int count = c.is(2) ? 2 : 1;
        List<PowerDrone> have = drones(c, false);
        int max = 2 + (c.is(2) ? 2 : 0);
        while (have.size() + count > max && !have.isEmpty()) have.remove(0).discard();
        int interval = c.is(1) ? 24 : 16;
        int life = c.ticks(400);
        for (int i = 0; i < count; i++) {
            PowerDrone d = spawnDrone(c, c.pos().add(0, 2.2, 0), c.dmg(), life);
            if (d != null) d.orbit(have.size() + i, interval);
        }
        c.sound(PowerSounds.SUMMON, 0.8f, 1.6f);
        return Result.OK;
    }

    static Result turret(Cast c) {
        for (PowerDrone old : drones(c, true)) old.discard();
        Vec3 at = Combat.aimPoint(c, 8).subtract(c.look().scale(0.5));
        int range = 20;
        int interval = c.is(1) ? 30 : c.is(2) ? 5 : 10;
        PowerDrone t = spawnDrone(c, at, c.dmg(), c.ticks(600));
        if (t == null) return Result.fail("blocked");
        t.makeTurret(range, interval);
        var scale = t.getAttribute(Attributes.SCALE);
        if (scale != null) scale.setBaseValue(1.7);
        var hp = t.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(30);
        t.setHealth(30);
        c.sound(at, PowerSounds.BUILD, 1f, 1.4f);
        return Result.OK;
    }

    static Result railgun(Cast c) {
        int full = c.is(1) ? 12 : 24;
        c.sound(PowerSounds.CHARGE_UP, 0.8f, 0.6f);
        Running r = Casting.channel(c, full);
        r.tick(rr -> {
            Vec3 h = EnergyPowers.hand(c.player);
            c.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, h.x, h.y, h.z, 2, 0.05, 0.05, 0.05, 0.08);
            if (rr.age == full - 1) c.sound(PowerSounds.CHARGE_UP, 0.6f, 2f);
        }).end((rr, why) -> {
            if (why == Running.Why.CANCELLED) return;
            double charge = Math.min(1.0, (rr.age + 1) / (double) full);
            Vec3 eye = c.player.getEyePosition();
            Vec3 dir = c.player.getLookAngle();
            Vec3 to = eye.add(dir.scale(48));
            BlockHitResult wall = Combat.clip(c.player, eye, to);
            Vec3 end = wall.getType() == HitResult.Type.MISS ? to : wall.getLocation();
            float dmg = c.dmg(c.type.damage + 10f * (float) charge);
            LivingEntity first = null;
            for (LivingEntity e : Combat.alongLine(c.player, eye, end, 0.6)) {
                c.hurt(e, dmg, Forces.Force.CHARGE);
                Combat.push(e, e.getDeltaMovement().add(dir.scale(c.is(2) ? 1.6 : 1.0)));
                Combat.stun(e, c.is(2) ? 30 : 10);
                if (first == null) first = e;
            }
            Fx.beam(c.player, c.player, end, 0xFFCFF4FF, 8, 0.22f + 0.1f * (float) charge, 48, false);
            Fx.line(c.level, eye.add(dir.scale(1)), end, ParticleTypes.ELECTRIC_SPARK, 1.0);
            c.particles(ParticleTypes.EXPLOSION, end, 1, 0, 0);
            c.sound(PowerSounds.RAIL, 1.4f, 1f - 0.2f * (float) charge);
            c.feel(first, first != null ? 60 : 0, 3f + 2f * (float) charge);
        });
        return Result.OK;
    }

    static Result nanite(Cast c) {
        int ticks = c.ticks(200);
        double armour = 8 * c.v.dmg();
        Moves.addMod(c.player, Attributes.ARMOR, "nanite_armor", armour, AttributeModifier.Operation.ADD_VALUE);
        Moves.addMod(c.player, Attributes.ARMOR_TOUGHNESS, "nanite_toughness", 4 * c.v.dmg(), AttributeModifier.Operation.ADD_VALUE);
        PowerState.of(c.player).buff(PowerState.Buffs.NANITE, c.now() + ticks, 0.2);
        c.sound(PowerSounds.NANITE, 1f, 1f);
        int healEvery = c.is(1) ? 0 : c.is(2) ? 7 : 20;
        Running r = Casting.start(c, ticks);
        r.tick(rr -> {
            if (healEvery > 0 && rr.age % healEvery == 0) c.player.heal(1f);
            if (rr.age % 4 == 0) {
                c.level.sendParticles(Fx.dust(0x9FB4C0, 0.7f), c.player.getX(), c.player.getY() + 1, c.player.getZ(), 3, 0.3, 0.6, 0.3, 0);
            }
        }).end((rr, why) -> {
            Moves.removeMod(c.player, Attributes.ARMOR, "nanite_armor");
            Moves.removeMod(c.player, Attributes.ARMOR_TOUGHNESS, "nanite_toughness");
            PowerState.of(c.player).clearBuff(PowerState.Buffs.NANITE);
        });
        return Result.OK;
    }

    static Result swarm(Cast c) {
        int count = c.is(1) ? 10 : c.is(2) ? 3 : 6;
        int interval = c.is(2) ? 10 : 18;
        int life = c.ticks(400);
        LivingEntity focus = Combat.aimEntity(c, 32, 1.0);
        List<PowerDrone> made = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            PowerDrone d = spawnDrone(c, c.pos().add(0, 2.4, 0), c.dmg(), life);
            if (d == null) continue;
            d.orbit(i, interval);
            d.focus(focus);
            made.add(d);
        }
        if (made.isEmpty()) return Result.fail("blocked");
        c.sound(PowerSounds.SUMMON, 1.2f, 1.8f);
        Running r = Casting.start(c, life);
        r.tick(rr -> {
            if (rr.age % 20 != 0) return;
            LivingEntity t = c.player.getLastHurtMob();
            if (t != null && t.isAlive() && c.player.tickCount - c.player.getLastHurtMobTimestamp() < 60) {
                for (PowerDrone d : made) d.focus(t);
            }
        });
        return Result.OK;
    }
}
