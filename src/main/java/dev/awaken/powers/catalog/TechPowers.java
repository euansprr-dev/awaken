package dev.awaken.powers.catalog;

import dev.awaken.powers.fx.CastAnim;
import dev.awaken.powers.fx.FxEvent;
import dev.awaken.powers.fx.FxKind;

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
import dev.awaken.powers.Scheduler;
import dev.awaken.powers.ladder.PowerLadder;
import dev.awaken.powers.ladder.StepHooks;
import dev.awaken.powers.content.PowerContent;
import dev.awaken.powers.content.PowerDrone;
import dev.awaken.progression.Perks;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
    /** The slug hits everything within this of its line; its spiral rings are drawn this wide. */
    static final double RAIL_WIDTH = 0.6;

    static void register() {
        // shown as "Snap Cable" so "the grapple" only ever means the Tether (progression plan decision 11); the id stays
        PowerType.builder("grapple", "Snap Cable", Family.TECH, Slot.MOVEMENT)
                .cost(10).cooldown(50).damage(3).unlock(Rank.E).icon("grapple")
                .desc("Fire a hook up to 28 blocks. Hook a wall and you're yanked to it; hook a mob and it's yanked to you.")
                .variant(Variant.of("Zipline", "A faster, flatter pull").radius(1.25f))
                .variant(Variant.of("Harpoon", "Hooked mobs take more and are stunned").dmg(2f))
                .predict((p, variant, level, strafe, forward) -> grappleMotion(p, variant, level))
                .cast(TechPowers::grapple).register();

        PowerType.builder("jet_burst", "Jet Burst", Family.TECH, Slot.MOVEMENT)
                .forces(Forces.Force.HEAT).cost(14).cooldown(40).damage(2).unlock(Rank.D).icon("jet")
                .desc("Kick on your boot jets for a burst in the direction you're looking. The exhaust scorches what's behind you.")
                .variant(Variant.of("Afterburner", "Faster, slower to recover").cd(1.3f))
                .variant(Variant.of("Vertical", "Straight up, high"))
                .predict((p, variant, level, strafe, forward) -> {
                    if (variant == 2) return new Vec3(p.getDeltaMovement().x, 1.55, p.getDeltaMovement().z);
                    // Shock Diamonds (2): a faster burst
                    double s = (variant == 1 ? 1.95 : 1.5) * (1 + (level >= 2 ? PowerLadder.num("jet_burst", 2, "speed", 0f) : 0));
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

    /** Holo Cable (2) reaches further; the client's prediction reads the same table. */
    static double grappleRange(int level) {
        return 28 + (level >= 2 ? PowerLadder.num("grapple", 2, "range", 0f) : 0);
    }

    /** Quick Reel (4): the pull is faster. */
    static double reel(int level) {
        return 1 + (level >= 4 ? PowerLadder.num("grapple", 4, "reel", 0f) : 0);
    }

    static Vec3 grappleMotion(Player p, int variant, int level) {
        Vec3 anchor = grappleAnchor(p, grappleRange(level));
        if (anchor == null) return null;
        // a hooked mob is pulled instead; the client can't know that, so it only predicts when the aim hits a wall first
        Vec3 d = anchor.subtract(p.position().add(0, 0.5, 0));
        double dist = d.length();
        double speed = Math.min(variant == 1 ? 2.4 : 1.9, 0.6 + dist * 0.12) * reel(level);
        Vec3 v = d.normalize().scale(speed);
        return new Vec3(v.x, v.y + (variant == 1 ? 0.15 : 0.4), v.z);
    }

    static Result grapple(Cast c) {
        double range = grappleRange(c.powerLevel);
        LivingEntity mob = Combat.aimEntity(c, range, 0.5);
        Vec3 anchor = grappleAnchor(c.player, range);
        boolean mobFirst = mob != null && (anchor == null || mob.distanceToSqr(c.player) < anchor.distanceToSqr(c.player.getEyePosition()));
        if (mobFirst) {
            if (c.predicted) Combat.push(c.player, Vec3.ZERO); // the client guessed a wall pull; cancel it
            Vec3 hook = mob.getBoundingBox().getCenter();
            Fx.at(c, FxKind.TETHER, c.player.getEyePosition()).target(mob).to(hook).aux(1).ticks(7).send();
            Fx.at(c, FxKind.IMPACT, hook).scale(0.9).noAnim().send();
            c.hurt(mob, c.dmg(), null);
            if (!Combat.isHeavy(mob)) {
                Vec3 d = c.pos().subtract(mob.position());
                Combat.push(mob, d.normalize().scale(Math.min(2.2, 0.5 + d.length() * 0.18) * reel(c.powerLevel)).add(0, 0.35, 0));
            }
            if (c.is(2)) Combat.stun(mob, 30);
            c.sound(PowerSounds.GRAPPLE, 1f, 1.2f);
            if (c.step(5)) reelKick(c, mob);
            return Result.OK;
        }
        if (anchor == null) return Result.fail("no_target");
        if (!c.predicted) Moves.launch(c.player, grappleMotion(c.player, c.variant, c.powerLevel));
        Moves.noFall(c, 80);
        // a cable to the wall, sparks where the hook bites
        Fx.at(c, FxKind.TETHER, c.player.getEyePosition()).to(anchor).aux(1).ticks(9).send();
        Fx.at(c, FxKind.IMPACT, anchor).scale(0.7).noAnim().send();
        c.sound(PowerSounds.GRAPPLE, 1f, 1f);
        if (c.step(5)) reelKick(c, null);
        return Result.OK;
    }

    /** Reel Kick (5): you arrive feet first: the foe you hauled in, or the first one you reach, takes a flying kick. */
    static void reelKick(Cast c, @Nullable LivingEntity hauled) {
        float dmg = c.dmg() * c.num(5, "kick", 1f);
        Running r = Casting.start(c, c.type.id + ":kick", 30);
        r.tick(rr -> {
            if (rr.age < 2) return;
            LivingEntity hit = null;
            if (hauled != null) {
                if (!hauled.isAlive()) {
                    rr.stop();
                    return;
                }
                if (hauled.distanceToSqr(c.player) < 2.6 * 2.6) hit = hauled;
            } else {
                for (LivingEntity e : Combat.foesAround(c.player, c.player.position().add(0, 0.9, 0), 2.2)) {
                    hit = e;
                    break;
                }
            }
            if (hit == null) return;
            Vec3 dir = hit.position().subtract(c.player.position()).multiply(1, 0, 1);
            if (dir.lengthSqr() < 1e-3) dir = c.look();
            for (LivingEntity e : Combat.foesAround(c.player, hit.position(), 2.0)) {
                c.hurt(e, dmg, null);
                Combat.knockFrom(e, c.player.position(), 1.2, 0.35);
            }
            Fx.at(c, FxKind.IMPACT, hit.getBoundingBox().getCenter()).dir(dir).scale(1.4).noAnim().send();
            c.sound(PowerSounds.IMPACT, 1f, 0.9f);
            rr.stop();
        });
    }

    static Result jet(Cast c) {
        Vec3 motion = c.type.predictor.motion(c.player, c.variant, c.powerLevel, c.strafe(), c.forward());
        if (!c.predicted) Moves.launch(c.player, motion);
        Moves.noFall(c, 80);
        c.sound(PowerSounds.JET, 1f, 1f);
        Vec3 back = c.look().scale(-1);
        float dmg = c.dmg();
        // boot jets: a blue-white core with embers and smoke streaming behind you
        Fx.at(c, FxKind.EXHAUST, c.pos()).dir(c.player.getDeltaMovement()).ticks(8).send();
        // Afterburner Boom (5): the boom where you left throws everything near it away
        if (c.step(5)) {
            Vec3 at = c.pos();
            for (LivingEntity e : Combat.foesAround(c.player, at.add(0, 0.5, 0), 4)) Combat.knockFrom(e, at, c.num(5, "boom", 0.8f) * 1.5, 0.35);
            Fx.at(c, FxKind.RING, at).scale(4).ticks(10).plain().send();
        }
        // Scorch (3): what the exhaust catches burns longer
        float burn = 2 + c.num(3, "burn", 0f) / 20f;
        int hang = (int) c.num(4, "hang", 0);
        Running r = Casting.start(c, 8 + hang);
        r.tick(rr -> {
            Vec3 feet = c.pos();
            if (rr.age == 1) {
                for (LivingEntity e : Combat.foesInCone(c.player, feet, back, 3.5, 35)) {
                    c.hurt(e, dmg, Forces.Force.HEAT);
                    e.igniteForSeconds(burn);
                }
            }
            // Hang (4): at the top of the burst the jets hold you a moment before you drop
            if (hang > 0 && rr.age == 8 && motion != null) {
                Moves.launch(c.player, new Vec3(motion.x * 0.3, 0.04, motion.z * 0.3));
                Moves.effect(c.player, MobEffects.SLOW_FALLING, hang + 6, 0);
                Fx.at(c, FxKind.EXHAUST, c.pos()).dir(new Vec3(0, -1, 0)).ticks(hang).noAnim().send();
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
        double boost = 1 + Progression.perk(c.player, Perks.SUMMON_STRENGTH);
        d.setup(c.player, (int) (life * boost), (float) (dmg * boost));
        d.cast = c;
        c.level.addFreshEntity(d);
        Fx.at(c, FxKind.HOLO, at).dir(new Vec3(0, 1, 0)).aux(0).noAnim().send();
        return d;
    }

    static Result drone(Cast c) {
        int count = c.is(2) ? 2 : 1;
        List<PowerDrone> have = drones(c, false);
        int max = 2 + (c.is(2) ? 2 : 0);
        while (have.size() + count > max && !have.isEmpty()) have.remove(0).discard();
        int interval = c.is(1) ? 24 : 16;
        int life = c.ticks(400);
        Moves.makeRoom(c.player, count);
        Fx.at(c, FxKind.CAST, c.pos()).anim(CastAnim.RAISE).send();
        for (int i = 0; i < count; i++) {
            PowerDrone d = spawnDrone(c, c.pos().add(0, 2.2, 0), c.dmg(), life);
            if (d == null) continue;
            d.orbit(have.size() + i, interval);
            // Burst Fire (4)
            d.burst = (int) c.num(4, "bursts", 1);
        }
        c.sound(PowerSounds.SUMMON, 0.8f, 1.6f);
        if (c.step(5)) overwatch(c, life);
        return Result.OK;
    }

    /** Overwatch (5): a thread of light runs from you to each of your drones, and it cuts what crosses it. */
    static void overwatch(Cast c, int life) {
        float dmg = c.dmg(c.num(5, "dps", 2f) * 0.5f);
        Running r = Casting.start(c, c.type.id + ":overwatch", life);
        r.tick(rr -> {
            if (rr.age % 10 != 0) return;
            Vec3 chest = c.player.position().add(0, 1.2, 0);
            List<PowerDrone> mine = drones(c, false);
            if (mine.isEmpty()) {
                rr.stop();
                return;
            }
            for (PowerDrone d : mine) {
                Vec3 at = d.position().add(0, d.getBbHeight() * 0.5, 0);
                Fx.at(c, FxKind.TETHER, chest).target(d).to(at).aux(0).ticks(11).plain().flag(FxEvent.QUIET).send();
                for (LivingEntity e : Combat.alongLine(c.player, chest, at, 0.5)) c.hurt(e, dmg, Forces.Force.CHARGE);
            }
        });
    }

    static Result turret(Cast c) {
        for (PowerDrone old : drones(c, true)) old.discard();
        Vec3 at = Combat.aimPoint(c, 8).subtract(c.look().scale(0.5));
        // Scan Cone (2): it sees further
        int range = 20 + (int) c.num(2, "sight", 0);
        int interval = c.is(1) ? 30 : c.is(2) ? 5 : 10;
        Moves.makeRoom(c.player, 1);
        PowerDrone t = spawnDrone(c, at, c.dmg(), c.ticks(600));
        if (t == null) return Result.fail("blocked");
        t.makeTurret(range, interval);
        Fx.at(c, FxKind.HOLO, at).aux(1).send();
        var scale = t.getAttribute(Attributes.SCALE);
        if (scale != null) scale.setBaseValue(1.7);
        var hp = t.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(30);
        t.setHealth(30);
        c.sound(at, PowerSounds.BUILD, 1f, 1.4f);
        if (c.step(2)) Fx.at(c, FxKind.RING, at).scale(range).ticks(16).plain().flag(FxEvent.QUIET).send();
        // Shield (4): it shrugs off half of every hit
        t.shield = c.num(4, "shield", 0f);
        if (c.step(5)) sentinel(c, t);
        return Result.OK;
    }

    /** Sentinel (5): every few seconds the turret fires a charged rail through the nearest foe it can see. */
    static void sentinel(Cast c, PowerDrone t) {
        int every = Math.max(20, (int) c.num(5, "rail", 100));
        float dmg = c.dmg(c.type.damage * 2.5f);
        Running r = Casting.start(c, c.type.id + ":sentinel", 600 * 2);
        r.tick(rr -> {
            if (!t.isAlive()) {
                rr.stop();
                return;
            }
            if (rr.age == 0 || rr.age % every != 0) return;
            Vec3 from = t.position().add(0, t.getBbHeight() * 0.6, 0);
            LivingEntity aim = null;
            double best = t.range() * t.range();
            for (LivingEntity e : Combat.foesAround(c.player, from, t.range())) {
                double d = e.distanceToSqr(from);
                if (d < best && t.hasLineOfSight(e)) {
                    best = d;
                    aim = e;
                }
            }
            if (aim == null) return;
            Vec3 dir = aim.getBoundingBox().getCenter().subtract(from).normalize();
            Vec3 to = from.add(dir.scale(t.range() + 8));
            BlockHitResult wall = c.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, t));
            Vec3 end = wall.getType() == HitResult.Type.MISS ? to : wall.getLocation();
            for (LivingEntity e : Combat.alongLine(c.player, from, end, RAIL_WIDTH)) {
                c.hurt(e, dmg, Forces.Force.CHARGE);
                Combat.stun(e, 10);
            }
            Fx.at(c, FxKind.RAIL, from).to(end).scale(0.7).aux(RAIL_WIDTH).plain().send();
            c.sound(from, PowerSounds.RAIL, 1f, 1.3f);
        });
    }

    static Result railgun(Cast c) {
        // Coils (2), Quick Charge (4): it charges faster
        int full = c.chargeTicks(c.is(1) ? 12 : 24);
        c.sound(PowerSounds.CHARGE_UP, 0.8f, 0.6f);
        // coils spin up at the hand
        Fx.at(c, FxKind.CHARGE, EnergyPowers.hand(c.player)).ticks(full).scale(0.9).send();
        Running r = Casting.channel(c, full);
        r.tick(rr -> {
            if (rr.age == full - 1) c.sound(PowerSounds.CHARGE_UP, 0.6f, 2f);
        }).end((rr, why) -> {
            double charge = Math.min(1.0, (rr.age + 1) / (double) full);
            Fx.at(c, FxKind.CHARGE_END, EnergyPowers.hand(c.player)).flag(FxEvent.FULL, charge >= 1).noAnim().send();
            if (why == Running.Why.CANCELLED) return;
            Vec3 eye = c.player.getEyePosition();
            Vec3 dir = c.player.getLookAngle();
            Vec3 to = eye.add(dir.scale(48));
            BlockHitResult wall = Combat.clip(c.player, eye, to);
            Vec3 end = wall.getType() == HitResult.Type.MISS ? to : wall.getLocation();
            float dmg = c.dmg(c.type.damage + 10f * (float) charge);
            LivingEntity first = null;
            for (LivingEntity e : Combat.alongLine(c.player, eye, end, RAIL_WIDTH)) {
                c.hurt(e, dmg, Forces.Force.CHARGE);
                Combat.push(e, e.getDeltaMovement().add(dir.scale(c.is(2) ? 1.6 : 1.0)));
                Combat.stun(e, c.is(2) ? 30 : 10);
                if (first == null) first = e;
                Fx.at(c, FxKind.IMPACT, e.getBoundingBox().getCenter()).dir(dir).scale(1.2 + charge).noAnim().send();
            }
            // the slug: a white-hot line wrapped in spiral rings, a muzzle flash and a burst where it stops
            Fx.at(c, FxKind.RAIL, eye).to(end).scale(charge).aux(RAIL_WIDTH).send();
            c.sound(PowerSounds.RAIL, 1.4f, 1f - 0.2f * (float) charge);
            c.feel(first, first != null ? 60 : 0, 3f + 2f * (float) charge);
            // Gauss Lance (5): the tunnel the slug left blasts again, hitting everything in the line
            if (c.step(5)) {
                Vec3 line = end;
                float second = dmg * c.num(5, "blast", 0.6f);
                Scheduler.after(Math.max(1, (int) c.num(5, "delay", 10)), () -> {
                    if (c.player.isRemoved()) return;
                    for (LivingEntity e : Combat.alongLine(c.player, eye, line, RAIL_WIDTH * 1.6)) c.hurt(e, second, Forces.Force.CHARGE);
                    Fx.at(c, FxKind.RAIL, eye).to(line).scale(0.6).aux(RAIL_WIDTH * 1.6).noAnim().send();
                    c.sound(eye.add(dir.scale(6)), PowerSounds.RAIL, 1.1f, 1.4f);
                });
            }
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
        // a hex film closes over the body, a scan of light climbing it
        Fx.at(c, FxKind.AURA, c.pos()).aux(FxKind.Aura.NANITE.ordinal()).ticks(ticks).send();
        int healEvery = c.is(1) ? 0 : c.is(2) ? 7 : 20;
        // Arc Shock (3): the shock a melee attacker takes jumps on to a second foe (PowerEvents)
        if (c.step(3)) PowerState.of(c.player).buff(PowerState.Buffs.NANITE_ARC, c.now() + ticks, c.num(3, "arc", 0.5f));
        int repair = (int) c.num(4, "repair", 0);
        float swarm = c.step(5) ? c.dmg(3f * c.num(5, "swarm", 0.5f)) : 0;
        Running r = Casting.start(c, ticks);
        r.tick(rr -> {
            if (healEvery > 0 && rr.age % healEvery == 0) c.player.heal(1f);
            // Repair (4): the film mends your armour as it runs
            if (repair > 0 && rr.age % 10 == 0 && repairArmour(c.player, repair) && rr.age % 40 == 0) {
                Fx.at(c, FxKind.HOLO, c.pos().add(0, 1, 0)).dir(new Vec3(0, 1, 0)).aux(0).ticks(12).plain().flag(FxEvent.QUIET).send();
            }
            // Living Armour (5): the swarm leaps off you at whatever is close
            if (swarm > 0 && rr.age % 20 == 10) {
                Vec3 chest = c.pos().add(0, 1.1, 0);
                for (LivingEntity e : Combat.foesAround(c.player, chest, 3.5)) {
                    c.hurt(e, swarm, Forces.Force.CHARGE);
                    Fx.at(c, FxKind.LIGHTNING, chest).to(e.getBoundingBox().getCenter()).ticks(5).scale(0.7).plain().flag(FxEvent.QUIET).send();
                }
            }
        }).end((rr, why) -> {
            PowerState.of(c.player).clearBuff(PowerState.Buffs.NANITE_ARC);
            Fx.end(c, c.player, FxKind.Aura.NANITE.ordinal() + 1);
            Moves.removeMod(c.player, Attributes.ARMOR, "nanite_armor");
            Moves.removeMod(c.player, Attributes.ARMOR_TOUGHNESS, "nanite_toughness");
            PowerState.of(c.player).clearBuff(PowerState.Buffs.NANITE);
        });
        return Result.OK;
    }

    /** Mends every worn, damaged armour piece by {@code points}; true when something was mended. */
    static boolean repairArmour(Player p, int points) {
        boolean any = false;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            ItemStack it = p.getItemBySlot(slot);
            if (it.isEmpty() || !it.isDamaged()) continue;
            it.setDamageValue(Math.max(0, it.getDamageValue() - points));
            any = true;
        }
        return any;
    }

    static Result swarm(Cast c) {
        int count = c.is(1) ? 10 : c.is(2) ? 3 : 6;
        int interval = c.is(2) ? 10 : 18;
        int life = c.ticks(400);
        LivingEntity focus = Combat.aimEntity(c, 32, 1.0);
        Moves.makeRoom(c.player, count);
        // an ultimate: the hive opens over your head (screen beat), a reticle locks on the target
        Fx.at(c, FxKind.HOLO, c.pos().add(0, 2.4, 0)).dir(new Vec3(0, 1, 0)).aux(0).send();
        if (focus != null) Fx.at(c, FxKind.HOLO, focus.getBoundingBox().getCenter()).dir(focus.position().subtract(c.pos())).aux(2).ticks(40).noAnim().send();
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
        LivingEntity[] focused = {focus};
        float lance = c.step(5) ? c.dmg() * 2f * c.num(5, "lance", 1f) : 0;
        Running r = Casting.start(c, life);
        r.tick(rr -> {
            // Retarget (4): the moment the focus dies the swarm turns on the nearest foe to it, at once
            if (c.step(4) && focused[0] != null && !focused[0].isAlive()) {
                LivingEntity next = StepHooks.nearestFoe(c.player, focused[0].position(), 14, focused[0]);
                focused[0] = next;
                for (PowerDrone d : made) d.focus(next);
                if (next != null) Fx.at(c, FxKind.HOLO, next.getBoundingBox().getCenter()).dir(next.position().subtract(c.pos())).aux(2).ticks(30)
                        .plain().send();
            }
            // Swarm Lance (5): every few seconds the drones join into one beam through the focus and everything behind it
            if (lance > 0 && rr.age > 0 && rr.age % 60 == 0 && focused[0] != null && focused[0].isAlive()) {
                Vec3 eye = c.player.getEyePosition();
                Vec3 dir = focused[0].getBoundingBox().getCenter().subtract(eye).normalize();
                BlockHitResult wall = Combat.clip(c.player, eye, eye.add(dir.scale(32)));
                Vec3 end = wall.getType() == HitResult.Type.MISS ? eye.add(dir.scale(32)) : wall.getLocation();
                for (LivingEntity e : Combat.alongLine(c.player, eye, end, 0.7)) c.hurt(e, lance, Forces.Force.CHARGE);
                Fx.beam(c, c.player, c.player, end, 0xFF000000 | TEAL, 10, 0.3f, (float) eye.distanceTo(end), false, false);
                c.sound(PowerSounds.RAIL, 1f, 1.6f);
            }
            if (rr.age % 20 != 0) return;
            LivingEntity t = c.player.getLastHurtMob();
            if (t != null && t.isAlive() && c.player.tickCount - c.player.getLastHurtMobTimestamp() < 60) {
                focused[0] = t;
                for (PowerDrone d : made) d.focus(t);
            }
        });
        return Result.OK;
    }
}
