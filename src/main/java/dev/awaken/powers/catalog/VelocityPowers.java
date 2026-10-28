package dev.awaken.powers.catalog;

import dev.awaken.powers.fx.CastAnim;
import dev.awaken.powers.fx.FxEvent;
import dev.awaken.powers.fx.FxKind;

import dev.awaken.core.Forces;
import dev.awaken.powers.Cast;
import dev.awaken.powers.Casting;
import dev.awaken.powers.Combat;
import dev.awaken.powers.Fx;
import dev.awaken.powers.ladder.Riders;
import dev.awaken.powers.ladder.Steps;
import dev.awaken.powers.Moves;
import dev.awaken.powers.PowerMath;
import dev.awaken.powers.PowerSounds;
import dev.awaken.powers.PowerState;
import dev.awaken.powers.Powers;
import dev.awaken.powers.PowerType;
import dev.awaken.powers.PowerType.Family;
import dev.awaken.powers.PowerType.Result;
import dev.awaken.powers.PowerType.Slot;
import dev.awaken.powers.PowerType.Variant;
import dev.awaken.powers.Running;
import dev.awaken.powers.Scheduler;
import dev.awaken.powers.TimeSlow;
import dev.awaken.progression.Rank;
import dev.awaken.progression.Stat;
import dev.awaken.progression.Progression;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Velocity: your Redline speedster. Scales with AGI. */
public final class VelocityPowers {
    private VelocityPowers() {}

    static final int RED = 0xFF4A3D;
    /** Lightning Step shocks everything within this of its path (the streak's halo is drawn as wide). */
    static final double STEP_WIDTH = 1.2;

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

        // Wall Stride (SPEC §16.3): the body's wall-run, longer and flatter; the id stays wall_run so saves keep it
        PowerType.builder("wall_run", "Wall Stride", Family.VELOCITY, Slot.PASSIVE)
                .unlock(Rank.D).icon("wall_run")
                .desc("Your wall-runs last four seconds and barely sink: run the length of a street along its walls.")
                .passive(c -> {
                }).register();

        PowerType.builder("water_run", "Water Run", Family.VELOCITY, Slot.PASSIVE)
                .unlock(Rank.D).icon("water_run")
                .desc("Sprint and you run across water instead of sinking.")
                .passive(c -> {
                    if (c.player.isSprinting() && c.level.getFluidState(c.player.blockPosition().below()).is(net.minecraft.tags.FluidTags.WATER)) {
                        c.player.resetFallDistance();
                        if (c.player.tickCount % 2 == 0) c.particles(ParticleTypes.SPLASH, c.pos(), 2, 0.2, 0.05);
                        // running on water pays Water Run's mastery (SPEC 36.8), a second at a time
                        if (c.player.tickCount % 20 < PowerType.PASSIVE_PERIOD) dev.awaken.powers.Mastery.use(c.player, "awaken:water_run", dev.awaken.powers.Mastery.WATER_SECOND);
                        // a red wake that lasts while you keep running
                        Fx.at(c, FxKind.TRAIL, c.pos()).ticks(12).noAnim().send();
                        // Tidewalker (5): the bow wave shoves foes in the water out of your way
                        if (c.step(5)) {
                            for (LivingEntity e : Combat.foesAround(c.player, c.pos(), 3.5)) {
                                if (e.isInWater()) Combat.knockFrom(e, c.pos(), c.num(5, "wave", 0.8f), 0.2);
                            }
                        }
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

    /**
     * The Dash goes the way you are moving (SPEC §13 "Dash direction"): forward with no key or W, back with S, sideways with
     * A or D, diagonals with two. Every variant and level steers alike; only a forward dash lifts with the look's pitch.
     */
    static Vec3 dashMotion(Player p, int variant, int level, float strafe, float forward) {
        if (variant == 1) return null;
        // air has less drag than ground, so an air dash is a little gentler to travel about as far
        double speed = (variant == 2 ? 1.45 : 1.9) * (1 + 0.04 * (level - 1)) * (p.onGround() ? 1.0 : 0.8);
        Vec3 way = Moves.steer(p.getLookAngle(), strafe, forward);
        Vec3 f = Moves.flat(way);
        double y = p.onGround() ? 0.12 : Math.max(0.28, way.y * speed * 0.5);
        return new Vec3(f.x * speed, Math.min(0.9, y), f.z * speed);
    }

    static Result dash(Cast c) {
        PowerState st = PowerState.of(c.player);
        boolean chained = !c.player.onGround() && st.dashChain() >= 2;
        if (c.is(1)) {
            Vec3 to = Moves.blinkTarget(c.player, c.steered(), 6 + c.powerLevel * 0.5);
            Vec3 from = c.pos();
            if (to.distanceToSqr(from) < 1) return Result.fail("blocked");
            Moves.teleport(c.player, to);
            Fx.at(c, FxKind.BLINK, from).to(to).anim(CastAnim.DASH).send();
            c.sound(PowerSounds.WHOOSH, 1f, 1.6f);
            dashSteps(c, st, chained, from.add(0, 0.9, 0), to.add(0, 0.9, 0), 0);
            return Result.OK;
        }
        Vec3 motion = dashMotion(c.player, c.variant, c.powerLevel, c.strafe(), c.forward());
        if (!c.predicted) Moves.launch(c.player, motion);
        Moves.noFall(c, 30);
        // sonic ring, speed lines, red afterimages and a wind ribbon
        Vec3 dir = motion == null ? c.flatLook() : motion;
        Fx.at(c, FxKind.DASH, c.pos()).dir(dir).ticks(9).send();
        c.sound(PowerSounds.WHOOSH, 0.9f, 1.2f + c.player.getRandom().nextFloat() * 0.2f);
        Running r = Casting.start(c, 7);
        float dmg = c.dmg();
        // Sonic Ring (2): foes you pass through are shoved aside, off the line you run
        double shove = c.num(2, "shove", 0f);
        Vec3 start = c.pos().add(0, 0.9, 0);
        r.tick(rr -> {
            Vec3 p = c.pos().add(0, 0.9, 0);
            for (LivingEntity e : Combat.foesAround(c.player, p, 1.6)) {
                if (rr.hit.add(e.getId())) {
                    c.hurt(e, dmg, null);
                    Combat.knockFrom(e, c.pos(), 0.4, 0.2);
                    if (shove > 0) {
                        Vec3 side = new Vec3(-dir.z, 0, dir.x).normalize();
                        if (side.dot(e.position().subtract(c.pos())) < 0) side = side.scale(-1);
                        Combat.push(e, side.scale(shove).add(0, 0.15, 0));
                    }
                    Fx.at(c, FxKind.IMPACT, e.getBoundingBox().getCenter()).dir(c.player.getDeltaMovement()).scale(0.7).noAnim().send();
                }
            }
        }).end((rr, why) -> dashSteps(c, st, chained, start, c.pos().add(0, 0.9, 0), (int) c.num(5, "delay", 12) - 7));
        return Result.OK;
    }

    /** Dash's ladder past its own move (SPEC 36): Momentum's next blow, Chain Charge's energy back, Redline's snap. */
    private static void dashSteps(Cast c, PowerState st, boolean chained, Vec3 from, Vec3 to, int snapDelay) {
        if (c.step(3)) st.buff(PowerState.Buffs.MOMENTUM, c.now() + (long) c.num(3, "ticks", 20), 1 + c.num(3, "bonus", 0.25f));
        if (c.step(4) && chained) Powers.restoreEnergy(c.player, PowerMath.cost(c.type.cost, c.v.cost()) * c.num(4, "share", 0.5f));
        if (c.step(5) && from.distanceToSqr(to) > 1) {
            Steps.lineAfter(c, from, to, Math.max(1, snapDelay), c.dmg() * c.num(5, "snap", 0.6f), 0.9, null, FxKind.LIGHTNING);
        }
    }

    static Result flow(Cast c) {
        PowerState st = PowerState.of(c.player);
        int agi = Progression.stat(c.player, Stat.AGI);
        // Ripple (2): a little longer
        int ticks = PowerMath.flowTicks(agi, flowPerk(c), c.v.duration()) + (int) c.num(2, "extra", 0);
        int factor = c.is(1) ? 10 : c.is(2) ? 3 : 4;
        TimeSlow.Zone zone = TimeSlow.add(c.player, 18, factor);
        Moves.addMod(c.player, Attributes.MOVEMENT_SPEED, "flow_speed", 0.35, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        Moves.addMod(c.player, Attributes.ATTACK_SPEED, "flow_attack", 0.4, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        Moves.postOn(c.player, Moves.FX_FLOW);
        c.sound(PowerSounds.FLOW_START, 1f, 1f);
        // a ripple of slowed time, then trailing afterimages for as long as it lasts
        Fx.at(c, FxKind.RING, c.pos()).scale(5).ticks(14).send();
        Fx.at(c, FxKind.AURA, c.pos()).aux(FxKind.Aura.FLOW.ordinal()).ticks(ticks).noAnim().send();
        Running r = Casting.start(c, ticks);
        // Red Hour (5): what you deal during Flow, by foe (StepHooks.dealt adds to it)
        if (c.step(5)) r.data = new java.util.HashMap<Integer, Float>();
        r.tick(rr -> st.flowTick()).end((rr, why) -> {
            Fx.end(c, c.player, FxKind.Aura.FLOW.ordinal() + 1);
            TimeSlow.remove(zone);
            Moves.removeMod(c.player, Attributes.MOVEMENT_SPEED, "flow_speed");
            Moves.removeMod(c.player, Attributes.ATTACK_SPEED, "flow_attack");
            Moves.postOff(c.player, Moves.FX_FLOW);
            c.sound(PowerSounds.FLOW_END, 0.9f, 1f);
            if (why == Running.Why.CANCELLED || !c.player.isAlive()) return;
            // started a tick later: a Running can't start another from inside its own end
            if (c.step(4)) Scheduler.after(1, () -> tempo(c));
            if (c.step(5) && rr.data instanceof java.util.Map<?, ?> dealt) redHour(c, dealt);
        });
        return Result.OK;
    }

    /** Afterimages (3): a kill during Flow adds to it, up to a cap. */
    public static void flowKill(ServerPlayer killer) {
        PowerState st = PowerState.peek(killer);
        Running r = st == null ? null : st.running("awaken:flow");
        if (r == null || r.stopping() || r.c.powerLevel < 3) return;
        int add = (int) r.c.num(3, "kill", 10);
        int cap = (int) r.c.num(3, "cap", 40);
        int given = Math.min(add, cap - r.count);
        if (given <= 0) return;
        r.count += given;
        r.duration += given;
        Fx.at(r.c, FxKind.AURA, killer.position()).aux(FxKind.Aura.FLOW.ordinal()).ticks(r.duration - r.age).noAnim().plain().send();
    }

    /** Tempo (4): for a few seconds after, you keep a step of Flow's speed. */
    private static void tempo(Cast c) {
        if (!c.player.isAlive()) return;
        double up = c.num(4, "speed", 0.2f);
        Running t = Casting.start(c, "flow_tempo", (int) c.num(4, "ticks", 60));
        Moves.addMod(c.player, Attributes.MOVEMENT_SPEED, "flow_tempo_speed", up, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        Moves.addMod(c.player, Attributes.ATTACK_SPEED, "flow_tempo_attack", up, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        Fx.at(c, FxKind.TRAIL, c.pos()).aux(2).ticks(t.duration).plain().send();
        t.end((rr, why) -> {
            Moves.removeMod(c.player, Attributes.MOVEMENT_SPEED, "flow_tempo_speed");
            Moves.removeMod(c.player, Attributes.ATTACK_SPEED, "flow_tempo_attack");
        });
    }

    /** Red Hour (5): when time comes back, every foe you struck takes a share of what you dealt it. */
    private static void redHour(Cast c, java.util.Map<?, ?> dealt) {
        float share = c.num(5, "snap", 0.3f);
        int struck = 0;
        for (var en : dealt.entrySet()) {
            if (!(en.getKey() instanceof Integer id) || !(en.getValue() instanceof Float amount)) continue;
            if (!(c.level.getEntity(id) instanceof LivingEntity e) || !e.isAlive() || e.distanceToSqr(c.player) > 48 * 48) continue;
            Combat.hurtFrom(c.player, null, e, amount * share, null, true);
            Fx.at(c, FxKind.IMPACT, e.getBoundingBox().getCenter()).scale(1.2).plain().send();
            struck++;
        }
        if (struck > 0) c.sound(PowerSounds.IMPACT, 1.2f, 0.7f);
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
        List<Mannequin> made = new ArrayList<>();
        boolean first = true;
        for (Vec3 s : spots) {
            Mannequin m = Moves.decoy(c, s, ticks, pop, 3.0, 14, "afterimage");
            if (m != null) made.add(m);
            var fx = Fx.at(c, FxKind.AFTERIMAGE, s).ticks(Math.min(ticks, 60));
            if (!first) fx.noAnim();
            fx.send();
            first = false;
        }
        Fx.at(c, FxKind.TRAIL, c.pos()).aux(2).ticks(50).noAnim().send();
        Moves.effect(c.player, MobEffects.SPEED, 50, 1);
        c.sound(PowerSounds.WHOOSH, 1f, 0.7f);
        c.sound(PowerSounds.SHADOW, 0.6f, 1.4f);
        // Mimic (3) and Snare (4) answer through the damage hooks while the copies stand (StepHooks)
        if (c.step(3) && !made.isEmpty()) Casting.start(c, ticks).data = made;
        // Phantom Rush (5): press again to swap places with it
        if (c.step(5) && !made.isEmpty()) c.followUp((int) c.num(5, "swap", 40), () -> phantomSwap(c, made));
        return Result.OK;
    }

    private static void phantomSwap(Cast c, List<Mannequin> made) {
        Mannequin m = null;
        for (Mannequin x : made) if (x.isAlive()) { m = x; break; }
        if (m == null || !c.player.isAlive() || m.level() != c.player.level()) return;
        Vec3 me = c.player.position();
        Vec3 it = m.position();
        Moves.teleportFacing(c.player, it, it.add(c.player.getLookAngle()));
        m.teleportTo(me.x, me.y, me.z);
        Fx.at(c, FxKind.BLINK, me.add(0, 0.9, 0)).to(it.add(0, 0.9, 0)).aux(0.6).plain().send();
        Fx.at(c, FxKind.AFTERIMAGE, me).ticks(14).noAnim().send();
        c.sound(PowerSounds.WHOOSH, 1f, 1.5f);
        c.sound(PowerSounds.SHADOW, 0.7f, 1.8f);
    }

    static Result lightningStep(Cast c) {
        Result r = flash(c, c.look());
        // Double Flash (4): a second press within the window flashes again, free
        if (r == Result.OK && c.step(4)) c.followUp((int) c.num(4, "window", 20), () -> flash(c, c.player.getLookAngle()));
        return r;
    }

    private static Result flash(Cast c, Vec3 look) {
        // Bright Path (2): further
        double max = 14 * c.v.radius() + c.num(2, "range", 0);
        Vec3 from = c.pos();
        Vec3 to = Moves.blinkTarget(c.player, look, max);
        if (to.distanceToSqr(from) < 2.25) return Result.fail("blocked");
        Moves.teleport(c.player, to);
        Moves.noFall(c, 40);
        float dmg = c.dmg();
        LivingEntity last = null;
        for (LivingEntity e : Combat.alongLine(c.player, from.add(0, 1, 0), to.add(0, 1, 0), STEP_WIDTH)) {
            c.hurt(e, dmg, Forces.Force.CHARGE);
            Combat.stun(e, 12);
            last = e;
        }
        if (c.step(5)) {
            // Thunder Step: everything on and beside the path is held longer
            int stun = 12 + (int) c.num(5, "stun", 10);
            for (LivingEntity e : Combat.alongLine(c.player, from.add(0, 1, 0), to.add(0, 1, 0), STEP_WIDTH * 2)) Combat.stun(e, stun);
            Fx.at(c, FxKind.STRIKE, from).scale(1.1).plain().flag(FxEvent.QUIET).send();
            Fx.at(c, FxKind.STRIKE, to).scale(1.1).plain().flag(FxEvent.QUIET).send();
        }
        // Static Field (3): the ground where you land crackles and slows
        if (c.step(3)) Riders.lingerAt(c, to, "slow", (int) c.num(3, "field", 40), c.num(3, "radius", 2.5f), 0);
        // the body becomes a bolt: a streak and a crackling arc along the path, a burst where you land
        Fx.at(c, FxKind.BLINK, from).to(to).aux(STEP_WIDTH).send();
        Fx.at(c, FxKind.LIGHTNING, from.add(0, 1, 0)).to(to.add(0, 1, 0)).ticks(8).scale(1.4).noAnim().send();
        dev.awaken.powers.ForceInteractions.chargeEchoes(c.player, to, 3);
        Fx.at(c, FxKind.IMPACT, to.add(0, 1, 0)).dir(to.subtract(from)).scale(1.4).noAnim().send();
        c.sound(from, PowerSounds.ZAP, 1.1f, 1.5f);
        c.sound(to, PowerSounds.THUNDER, 0.8f, 1.6f);
        if (c.is(1)) {
            Vec3 origin = last != null ? last.position().add(0, 1, 0) : to.add(0, 1, 0);
            int forks = 0;
            for (LivingEntity o : Combat.foesAround(c.player, origin, 8)) {
                if (forks >= 3 || o == last) continue;
                Fx.at(c, FxKind.LIGHTNING, origin).to(o.position().add(0, 1, 0)).ticks(6).noAnim().send();
                c.hurt(o, dmg * 0.6f, Forces.Force.CHARGE);
                forks++;
            }
        }
        c.feel(last, last != null ? 40 : 0, 1.5f);
        return Result.OK;
    }
}
