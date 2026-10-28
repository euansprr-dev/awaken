package dev.awaken.powers.catalog;

import dev.awaken.powers.fx.CastAnim;
import dev.awaken.powers.fx.FxEvent;
import dev.awaken.powers.fx.FxKind;
import dev.awaken.powers.fx.FxStyle;

import dev.awaken.core.Forces;
import dev.awaken.powers.Bolts;
import dev.awaken.powers.Cast;
import dev.awaken.powers.Casting;
import dev.awaken.powers.Combat;
import dev.awaken.powers.ForceInteractions;
import dev.awaken.powers.Fx;
import dev.awaken.powers.ladder.Riders;
import dev.awaken.powers.Moves;
import dev.awaken.powers.PowerSounds;
import dev.awaken.powers.PowerState;
import dev.awaken.powers.PowerType;
import dev.awaken.powers.PowerType.Family;
import dev.awaken.powers.PowerType.Result;
import dev.awaken.powers.PowerType.Slot;
import dev.awaken.powers.PowerType.Variant;
import dev.awaken.powers.Running;
import dev.awaken.powers.ladder.Steps;
import dev.awaken.powers.Scheduler;
import dev.awaken.powers.Zones;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.progression.Stat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Energy: beams, novas, shields. Scales with INT. */
final class EnergyPowers {
    private EnergyPowers() {}

    static final int BEAM = 0xFFFF9A3C;
    static final int BLUE = 0x7FC3FF;
    static final int PRISM = 0xFFE9D8FF;

    static void register() {
        PowerType.builder("beam", "Beam", Family.ENERGY, Slot.ACTIVE)
                .forces(Forces.Force.HEAT).cost(6).cooldown(60).damage(2.2f).unlock(Rank.E).icon("beam").channel(18, 120)
                .desc("Hold to pour a searing beam from your hand. Burns what it touches; ice and snow turn to steam.")
                .variant(Variant.of("Prism", "The beam splits into three").dmg(1.0f))
                .variant(Variant.of("Focused", "Narrow and long: half again the damage, drains faster").dmg(1.5f).cost(1.3f))
                .cast(EnergyPowers::beam).register();

        PowerType.builder("nova", "Nova", Family.ENERGY, Slot.ACTIVE)
                .forces(Forces.Force.HEAT).cost(30).cooldown(200).damage(9).unlock(Rank.D).icon("nova")
                .desc("Release a sphere of burning light all around you that throws enemies back.")
                .variant(Variant.of("Pulse", "Two smaller pulses").dmg(0.6f))
                .variant(Variant.of("Implosion", "Pulls everything in, then detonates").dmg(1.2f))
                .cast(EnergyPowers::nova).register();

        PowerType.builder("barrier", "Barrier", Family.ENERGY, Slot.ACTIVE)
                .forces(Forces.Force.CHARGE).cost(25).cooldown(360).damage(4).unlock(Rank.E).icon("barrier")
                .desc("Wrap yourself in an energy shield (6 + INT/4 absorption) for 10 s. When it breaks it shocks everything near you.")
                .variant(Variant.of("Reflect", "A thinner shield that throws back a third of melee damage").dmg(0.7f))
                .variant(Variant.of("Overshield", "Half again as strong, but shorter").dmg(1.5f).dur(0.6f))
                .cast(EnergyPowers::barrier).register();

        PowerType.builder("charged_shot", "Charged Shot", Family.ENERGY, Slot.ACTIVE)
                .forces(Forces.Force.CHARGE).cost(15).cooldown(80).damage(6).unlock(Rank.C).icon("shot").channel(0, 30)
                .desc("Hold to charge a bolt of lightning, release to fire. Fully charged it explodes and stuns.")
                .variant(Variant.of("Ricochet", "Bounces off walls twice"))
                .variant(Variant.of("Volley", "No charge: three quick bolts at once").dmg(0.45f))
                .cast(EnergyPowers::chargedShot).register();

        PowerType.builder("overload", "Overload", Family.ENERGY, Slot.ULTIMATE)
                .forces(Forces.Force.HEAT, Forces.Force.CHARGE).cost(60).cooldown(1200).damage(28).unlock(Rank.C).icon("overload")
                .desc("Gather everything you have for a second, then explode. Afterwards your energy powers hit harder and cost half for 8 s.")
                .variant(Variant.of("Supernova", "A bigger blast, no afterglow").dmg(1.4f).radius(1.2f))
                .variant(Variant.of("Conduit", "No blast: energy regenerates three times as fast for 15 s"))
                .cast(EnergyPowers::overload).register();

        PowerType.builder("prism_lance", "Prism Lance", Family.ENERGY, Slot.ULTIMATE)
                .forces(Forces.Force.HEAT).cost(40).cooldown(900).damage(5).unlock(Rank.C).hidden().icon("prism").channel(10, 60)
                .desc("Prism's signature. A lance of light that splits at the first thing it hits into four beams that seek out more enemies.")
                .variant(Variant.of("Spectrum", "Splits into six weaker beams").dmg(0.7f))
                .variant(Variant.of("Lancer", "Never splits: pierces everything in a line").dmg(1.8f))
                .cast(EnergyPowers::prismLance).register();
    }

    // ---- behaviours -------------------------------------------------------------------------------------------------

    record BeamHit(Vec3 end, @Nullable LivingEntity target, @Nullable BlockHitResult block) {}

    static BeamHit trace(ServerPlayer p, Vec3 from, Vec3 dir, double range) {
        Vec3 to = from.add(dir.scale(range));
        BlockHitResult wall = Combat.clip(p, from, to);
        Vec3 end = wall.getType() == HitResult.Type.MISS ? to : wall.getLocation();
        LivingEntity t = Combat.aimEntity(p, from, dir, from.distanceTo(end), 0.3, true);
        if (t != null) return new BeamHit(t.getBoundingBox().getCenter(), t, null);
        return new BeamHit(end, null, wall.getType() == HitResult.Type.MISS ? null : wall);
    }

    static Vec3 hand(ServerPlayer p) {
        Vec3 look = p.getLookAngle();
        Vec3 right = Moves.flat(look).yRot((float) (-Math.PI / 2));
        return p.getEyePosition().add(look.scale(0.4)).add(right.scale(0.35)).add(0, -0.35, 0);
    }

    static Result beam(Cast c) {
        double range = c.range(c.is(2) ? 32 : 24);
        c.sound(PowerSounds.BEAM_GATHER, 0.9f, 1f);
        // the light gathers at the palm while the arm comes up, then the layered beam itself (see beamRay)
        Fx.at(c, FxKind.CHARGE, hand(c.player)).ticks(c.type.maxDuration).scale(0.7).send();
        Running r = Casting.channel(c, c.type.maxDuration);
        BeamHeld held = new BeamHeld();
        r.end((rr, why) -> {
            Fx.end(c, c.player, 0);
            c.sound(PowerSounds.BEAM_END, 0.8f, 1f);
            if (c.step(5) && why != Running.Why.CANCELLED && rr.age > 6) sunspear(c, held, range);
        });
        r.tick(rr -> {
            Vec3 eye = c.player.getEyePosition();
            Vec3 look = c.player.getLookAngle();
            boolean pulse = rr.age % 4 == 0;
            beamRay(c, rr, held, eye, look, range, c.dmg(), pulse, true, c.is(2) ? 0.07f : 0.12f);
            if (c.is(1)) {
                for (int s = -1; s <= 1; s += 2) {
                    Vec3 side = look.yRot((float) Math.toRadians(12 * s));
                    beamRay(c, rr, null, eye, side, range * 0.8, c.dmg() * 0.55f, pulse, false, 0.08f);
                }
            }
            // the hum starts when the arm is up (tick 5) and is re-laid every half second over a 0.62 s file
            if (rr.age % 10 == 5) c.sound(PowerSounds.BEAM_LOOP, 0.6f, 1f);
        });
        return Result.OK;
    }

    /** What a held beam remembers between pulses (SPEC 36, Beam's ladder): the body it has been on and since when, and what it dealt. */
    static final class BeamHeld {
        @Nullable LivingEntity on;
        int since;
        float dealt;
    }

    /**
     * One beam ray: damage on pulse ticks, burn, steam from ice, visuals every 4 ticks. {@code held} is the main ray's memory
     * (null for Prism's side rays, which draw without the ladder's layers): Helix builds up on one target, Lance goes through it.
     */
    static void beamRay(Cast c, Running rr, @Nullable BeamHeld held, Vec3 eye, Vec3 dir, double range, float dmg, boolean pulse, boolean follow,
                        float width) {
        BeamHit hit = trace(c.player, eye, dir, range);
        Vec3 end = hit.end();
        if (pulse) {
            LivingEntity t = hit.target();
            if (t != null) {
                float d = dmg;
                if (held != null && c.kit.ramp != null) {
                    if (held.on != t) {
                        held.on = t;
                        held.since = rr.age;
                    }
                    d *= 1 + c.kit.ramp.share() * Math.min(1f, (rr.age - held.since) / (float) Math.max(1, c.kit.ramp.ticks()));
                }
                if (c.hurt(t, d, Forces.Force.HEAT) && held != null) held.dealt += d;
                if (rr.age % 20 == 0) t.igniteForSeconds(2);
                if (held != null && c.kit.pierce != null) {
                    // Lance: a flare at the body and on through it to the next in the line
                    Vec3 at = t.getBoundingBox().getCenter();
                    Fx.at(c, FxKind.IMPACT, at).dir(dir).scale(0.7).plain().send();
                    Vec3 from = at.add(dir.scale(t.getBbWidth() * 0.75 + 0.3));
                    BeamHit next = trace(c.player, from, dir, Math.max(0, range - eye.distanceTo(from)));
                    if (next.target() != null && next.target() != t && c.hurt(next.target(), d * c.kit.pierce.share(), Forces.Force.HEAT))
                        held.dealt += d * c.kit.pierce.share();
                    end = next.end();
                }
            } else if (hit.block() != null) {
                BlockState s = c.level.getBlockState(hit.block().getBlockPos());
                if ((s.is(BlockTags.ICE) || s.is(BlockTags.SNOW) || s.is(Blocks.POWDER_SNOW)) && rr.age - rr.count > 40) {
                    rr.count = rr.age;
                    Zones.steam(c.level, hit.end(), 3.5, 140);
                    ForceInteractions.count(c.player, ForceInteractions.Reaction.STEAM);
                }
            }
            Fx.beam(c, c.player, c.player, end, BEAM, 6, width, (float) range, follow, held != null);
        }
        // embers, splash glow and scorch marks at the end of the beam are drawn by the client from the beam itself
    }

    /** Sunspear (Beam 5): on letting go, a last pulse runs down the beam and bursts at the end for a share of what the beam dealt. */
    static void sunspear(Cast c, BeamHeld held, double range) {
        Vec3 eye = c.player.getEyePosition();
        BeamHit hit = trace(c.player, eye, c.player.getLookAngle(), range);
        Fx.beam(c, c.player, c.player, hit.end(), BEAM, 5, 0.3f, (float) range, false, false);
        float share = c.num(5, "pulse", 0.2f) * held.dealt;
        Vec3 at = hit.end();
        int travel = Math.max(1, (int) Math.round(eye.distanceTo(at) / 12));
        dev.awaken.powers.Scheduler.after(travel, () -> {
            if (!c.player.isAlive()) return;
            Fx.at(c, FxKind.NOVA, at).scale(2.4).plain().send();
            c.sound(at, PowerSounds.tier(c.type.family, 5), 1f, 1f);
            if (share > 0) for (LivingEntity e : Combat.foesAround(c.player, at, 2.4)) c.hurt(e, share, Forces.Force.HEAT);
        });
    }

    static Result nova(Cast c) {
        Vec3 at = c.pos().add(0, 1, 0);
        double radius = c.radius(6);
        if (c.is(2)) {
            for (LivingEntity e : Combat.foesAround(c.player, at, radius + 2)) Combat.push(e, at.subtract(e.position()).normalize().scale(0.8).add(0, 0.2, 0));
            c.sound(PowerSounds.GRAVITY, 1f, 1.4f);
            Fx.at(c, FxKind.CHARGE, at).ticks(9).scale(1.6).anim(CastAnim.CHARGE2).send();
            Fx.at(c, FxKind.VORTEX, at).scale(radius + 2).ticks(9).noAnim().send();
            Running r = Casting.start(c, 9);
            r.tick(rr -> {
                if (rr.age == 8) novaBlast(c, c.pos().add(0, 1, 0), radius);
            });
            return Result.OK;
        }
        if (c.step(5)) {
            // Daystar: you rise a little as a sun forms at your chest, then it goes off
            Moves.launch(c.player, new Vec3(0, c.num(5, "rise", 0.35f), 0));
            Moves.noFall(c, 60);
            Fx.at(c, FxKind.CHARGE, at).ticks(6).scale(1.4).noAnim().send();
            c.sound(PowerSounds.NOVA, 0.7f, 1.6f);
        }
        int delay = c.step(5) ? 5 : 0;
        if (c.is(1)) {
            Running r = Casting.start(c, 12 + delay);
            r.tick(rr -> {
                if (rr.age == delay || rr.age == delay + 10) novaBlast(c, c.pos().add(0, 1, 0), radius * (rr.age == delay ? 0.8 : 1.0));
            });
            return Result.OK;
        }
        if (delay == 0) novaBlast(c, at, radius);
        else Casting.start(c, delay + 1).tick(rr -> {
            if (rr.age == delay) novaBlast(c, c.pos().add(0, 1, 0), radius);
        });
        return Result.OK;
    }

    static void novaBlast(Cast c, Vec3 at, double radius) {
        float dmg = c.dmg();
        for (LivingEntity e : Combat.foesAround(c.player, at, radius)) {
            c.hurt(e, dmg, Forces.Force.HEAT);
            e.igniteForSeconds(3);
            Combat.knockFrom(e, at, 1.2, 0.4);
        }
        // a sphere of burning light with a white flash at its heart, embers and sparks thrown out
        Fx.at(c, FxKind.NOVA, at).scale(radius).flag(FxEvent.REACH).send();
        c.sound(at, PowerSounds.NOVA, 1.3f, 1f);
        Fx.quake(c.level, at, 16, 3f);
        c.feel(null, 0, 2.5f);
        // Flare Arcs (4): plasma leaps from the shell to foes just past it
        if (c.step(4)) {
            int arcs = 0, max = (int) c.num(4, "arcs", 3);
            for (LivingEntity e : Steps.nearest(c, at, radius + c.num(4, "reach", 4f), 12, null)) {
                if (arcs >= max) break;
                if (e.distanceToSqr(at) <= radius * radius) continue;
                Vec3 edge = at.add(e.getBoundingBox().getCenter().subtract(at).normalize().scale(radius));
                Fx.at(c, FxKind.LIGHTNING, edge).to(e.getBoundingBox().getCenter()).ticks(7).plain().send();
                Combat.hurtFrom(c.player, null, e, dmg * c.num(4, "share", 0.5f), Forces.Force.HEAT, true);
                arcs++;
            }
        }
        // Searing Shell (3): the shell holds a moment and burns what touches it
        if (c.step(3)) {
            int hold = (int) c.num(3, "hold", 20);
            int burn = (int) c.num(3, "burn", 40);
            Casting.start(c, "nova_shell", hold).tick(rr -> {
                if (rr.age % 5 != 0) return;
                for (LivingEntity e : Combat.foesAround(c.player, at, radius + 0.8)) {
                    if (e.distanceToSqr(at) < (radius - 1.4) * (radius - 1.4)) continue;
                    e.igniteForSeconds(burn / 20f);
                    if (rr.hit.add(e.getId())) Combat.hurtFrom(c.player, null, e, c.dmg(1f), Forces.Force.HEAT, true);
                }
            });
        }
    }

    static Result barrier(Cast c) {
        int intStat = Progression.stat(c.player, Stat.INT);
        float amount = (float) ((6 + 0.25 * intStat) * c.v.dmg() * (1 + 0.15 * (c.powerLevel - 1)));
        int ticks = c.ticks(200);
        PowerState st = PowerState.of(c.player);
        Moves.addMod(c.player, Attributes.MAX_ABSORPTION, "barrier", amount, AttributeModifier.Operation.ADD_VALUE);
        c.player.setAbsorptionAmount(Math.max(c.player.getAbsorptionAmount(), amount));
        if (c.is(1)) st.buff(PowerState.Buffs.BRACE_REFLECT, c.now() + ticks, 0.33);
        // Halo Ward (5): plates that stop arrows outright (PowerEvents)
        if (c.step(5)) st.buff(PowerState.Buffs.BARRIER_PLATES, c.now() + ticks, c.num(5, "blocks", 3));
        c.sound(PowerSounds.BARRIER, 1f, 1.2f);
        // a bubble with a bright rim and a hex skin that follows you
        Fx.at(c, FxKind.DOME, c.pos().add(0, 1, 0)).scale(1.35).ticks(ticks).flag(FxEvent.FOLLOW).send();
        Running r = Casting.start(c, ticks);
        r.tick(rr -> {
            if (rr.age > 2 && c.player.getAbsorptionAmount() <= 0.01f) rr.stop(Running.Why.DONE);
        }).end((rr, why) -> {
            Fx.end(c, c.player, -2);
            Moves.removeMod(c.player, Attributes.MAX_ABSORPTION, "barrier");
            if (c.is(1)) st.clearBuff(PowerState.Buffs.BRACE_REFLECT);
            if (why == Running.Why.DONE) {
                Vec3 at = c.pos().add(0, 1, 0);
                // Stored Charge (3): the break shock carries part of what the shield took
                float shock = c.dmg() + amount * c.num(3, "stored", 0f);
                Moves.blast(c, at, 4, shock, Forces.Force.CHARGE, 1.0, 0.3);
                ForceInteractions.chargeEchoes(c.player, at, 4);
                Fx.at(c, FxKind.NOVA, at).scale(4).style(FxStyle.CHARGE).flag(FxEvent.REACH).noAnim().send();
                c.sound(PowerSounds.BARRIER_BREAK, 1.2f, 1f);
                // Second Skin (4): a thinner shell flickers back, once
                if (c.step(4)) Scheduler.after(4, () -> secondSkin(c, amount * c.num(4, "strength", 0.33f), (int) c.num(4, "reform", 60)));
            } else {
                st.clearBuff(PowerState.Buffs.BARRIER_PLATES);
            }
        });
        return Result.OK;
    }

    private static void secondSkin(Cast c, float amount, int ticks) {
        if (!c.player.isAlive()) return;
        Moves.addMod(c.player, Attributes.MAX_ABSORPTION, "barrier_skin", amount, AttributeModifier.Operation.ADD_VALUE);
        c.player.setAbsorptionAmount(Math.max(c.player.getAbsorptionAmount(), amount));
        Fx.at(c, FxKind.DOME, c.pos().add(0, 1, 0)).scale(1.25).ticks(ticks).flag(FxEvent.FOLLOW).plain().send();
        c.sound(PowerSounds.BARRIER, 0.7f, 1.6f);
        Running r = Casting.start(c, "barrier_skin", ticks);
        r.tick(rr -> {
            if (rr.age > 2 && c.player.getAbsorptionAmount() <= 0.01f) rr.stop(Running.Why.DONE);
        }).end((rr, why) -> {
            Fx.end(c, c.player, -2);
            Moves.removeMod(c.player, Attributes.MAX_ABSORPTION, "barrier_skin");
            PowerState.of(c.player).clearBuff(PowerState.Buffs.BARRIER_PLATES);
        });
    }

    static Result chargedShot(Cast c) {
        if (c.is(2)) {
            Fx.at(c, FxKind.CAST, hand(c.player)).anim(CastAnim.PUSH2).send();
            for (int i = -1; i <= 1; i++) {
                Vec3 d = c.look().yRot((float) Math.toRadians(i * 6));
                var b = Bolts.fire(c.level, c.player, c.player, hand(c.player), d, 2.4f, c.dmg(), Forces.Force.CHARGE, 0xFF9AD8FF, 40, 0f);
                b.stun = 6;
                b.cast = c;
            }
            c.sound(PowerSounds.SHOT, 1f, 1.4f);
            return Result.OK;
        }
        c.sound(PowerSounds.CHARGE_UP, 0.7f, 1.2f);
        // crackling orb at the hand; the bolt itself is drawn as it flies (Bolts)
        Fx.at(c, FxKind.CHARGE, hand(c.player)).ticks(c.type.maxDuration).scale(0.9).send();
        Running r = Casting.channel(c, c.type.maxDuration);
        int fullAt = c.chargeTicks(30); // Coil (2): charges sooner
        r.tick(rr -> {
            if (rr.age == fullAt - 1) c.sound(PowerSounds.CHARGE_UP, 0.6f, 2f);
        }).end((rr, why) -> {
            double charge = Math.min(1.0, rr.age / (double) fullAt);
            // Overcharge (4): held past full, the orb turns white and the bolt pierces
            boolean over = c.step(4) && rr.age >= fullAt + c.num(4, "over", 10);
            Fx.at(c, FxKind.CHARGE_END, hand(c.player)).flag(FxEvent.FULL, charge > 0.95).noAnim().send();
            if (why == Running.Why.CANCELLED) return;
            Fx.at(c, FxKind.CAST, hand(c.player)).anim(CastAnim.AIM).send();
            float dmg = c.dmg(c.type.damage + 14f * (float) charge);
            var b = Bolts.fire(c.level, c.player, c.player, hand(c.player), c.player.getLookAngle(), 2.6f, dmg, Forces.Force.CHARGE,
                    0xFF9AD8FF, 40, charge > 0.95 ? 2.5f : 0f);
            b.stun = 10 + (int) (20 * charge);
            b.cast = c;
            if (over) b.pierce = 1;
            if (c.is(1)) b.bounces = 2;
            // a muzzle burst at the palm, and the bolt's landing where the aim meets something (it flies 2.6 blocks a tick)
            Fx.at(c, FxKind.IMPACT, hand(c.player)).dir(c.player.getLookAngle()).scale(1 + charge).noAnim().send();
            BeamHit aim = trace(c.player, c.player.getEyePosition(), c.player.getLookAngle(), 48);
            int flight = Math.max(1, (int) Math.ceil(hand(c.player).distanceTo(aim.end()) / 2.6));
            Vec3 lands = aim.end();
            Vec3 look = c.player.getLookAngle();
            float landing = (float) (1.2 + 1.4 * charge);
            boolean full = charge > 0.95;
            Vec3 muzzle = hand(c.player);
            dev.awaken.powers.Scheduler.after(flight, () -> {
                if (c.player.isRemoved()) return;
                Fx.at(c, FxKind.IMPACT, lands).dir(look).scale(landing).flag(FxEvent.FULL, full).noAnim().send();
                if (full) Fx.at(c, FxKind.NOVA, lands).scale(2.5).noAnim().send();
                if (full && c.step(5)) thunderLance(c, muzzle, lands);
            });
            c.sound(PowerSounds.SHOT, 1f, 1.4f - 0.5f * (float) charge);
            c.feel(null, 0, 0.8f + 1.5f * (float) charge);
        });
        return Result.OK;
    }

    /** Thunder Lance (Charged Shot 5): a bolt from the sky on the landing, and a crackling line along the shot that shocks what crosses it. */
    static void thunderLance(Cast c, Vec3 from, Vec3 to) {
        int ticks = (int) c.num(5, "line", 40);
        float dps = c.num(5, "dps", 1.5f) * c.dmg(1f);
        Fx.at(c, FxKind.STRIKE, to).scale(1.2).plain().send();
        Fx.at(c, FxKind.LIGHTNING, from).to(to).ticks(ticks).scale(1.1).plain().flag(FxEvent.QUIET).send();
        double len = from.distanceTo(to);
        Vec3 step = to.subtract(from).normalize();
        for (double d = 1.5; d < len; d += 2.5) Riders.lingerAt(c, from.add(step.scale(d)), "shock", ticks, 1.2f, dps, false);
        c.sound(to, PowerSounds.tier(c.type.family, 5), 1f, 1f);
    }

    static Result overload(Cast c) {
        PowerState st = PowerState.of(c.player);
        if (c.is(2)) {
            int ticks = c.ticks(300) + (int) c.num(2, "glow", 0);
            st.buff(PowerState.Buffs.CONDUIT, c.now() + ticks, 1);
            if (c.step(4)) st.buff(PowerState.Buffs.LIVE_WIRE, c.now() + ticks, c.num(4, "live", 0.4f));
            st.buff(PowerState.Buffs.OVERLOAD_COST, c.now() + ticks, 1);
            c.sound(PowerSounds.OVERLOAD, 1f, 1.5f);
            Fx.at(c, FxKind.CAST, c.pos()).anim(CastAnim.SKYWARD).send();
            Fx.at(c, FxKind.AURA, c.pos()).aux(FxKind.Aura.OVERLOAD.ordinal()).ticks(ticks).noAnim().send();
            Running r = Casting.start(c, "overload_glow", ticks);
            Moves.postOn(c.player, Moves.FX_OVERLOAD);
            r.end((rr, why) -> {
                Moves.postOff(c.player, Moves.FX_OVERLOAD);
                Fx.end(c, c.player, FxKind.Aura.OVERLOAD.ordinal() + 1);
            });
            return Result.OK;
        }
        Moves.addMod(c.player, Attributes.MOVEMENT_SPEED, "overload_windup", -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        c.sound(PowerSounds.OVERLOAD, 1.2f, 0.8f);
        // a second of gathering everything: both hands pulled in, a growing orb, the air crackling
        Fx.at(c, FxKind.CHARGE, c.pos().add(0, 1, 0)).ticks(20).scale(1.8).send();
        Fx.at(c, FxKind.VORTEX, c.pos().add(0, 1, 0)).scale(4).ticks(20).style(FxStyle.CHARGE).noAnim().send();
        Running r = Casting.start(c, 20);
        // Critical Mass (5): you're lifted as you gather, and the gathering drags them in before it goes off
        if (c.step(5)) r.tick(rr -> {
            Vec3 v = c.player.getDeltaMovement();
            Moves.launch(c.player, new Vec3(v.x * 0.5, Math.max(v.y, rr.age < 12 ? 0.06 : 0.0), v.z * 0.5));
            c.player.resetFallDistance();
            if (rr.age == 12) {
                Vec3 at = c.pos().add(0, 1, 0);
                for (LivingEntity e : Combat.foesAround(c.player, at, c.radius(9) + 3)) {
                    Combat.push(e, at.subtract(e.position()).normalize().scale(c.num(5, "pull", 0.8f)).add(0, 0.15, 0));
                }
                c.sound(PowerSounds.GRAVITY, 1.2f, 0.6f);
            }
        });
        r.end((rr, why) -> {
            Moves.removeMod(c.player, Attributes.MOVEMENT_SPEED, "overload_windup");
            Fx.end(c, c.player, 0);
            if (why != Running.Why.EXPIRED) return;
            Vec3 at = c.pos().add(0, 1, 0);
            double radius = c.radius(9);
            float dmg = c.dmg();
            LivingEntity first = null;
            int arcs = 0;
            for (LivingEntity e : Combat.foesAround(c.player, at, radius)) {
                c.hurt(e, dmg, e.isInWater() ? Forces.Force.CHARGE : Forces.Force.HEAT);
                e.igniteForSeconds(4);
                Combat.stun(e, 20);
                Combat.knockFrom(e, at, 1.6, 0.6);
                if (first == null) first = e;
                if (arcs++ < 6) Fx.at(c, FxKind.LIGHTNING, at).to(e.getBoundingBox().getCenter()).ticks(8).scale(1.4).noAnim().send();
            }
            ForceInteractions.chargeEchoes(c.player, at, radius);
            // the release: an ultimate's screen beat rides on the nova (impact frame, flash, FOV punch)
            Fx.at(c, FxKind.NOVA, at).scale(radius).flag(FxEvent.REACH).send();
            c.sound(at, PowerSounds.BLAST, 2f, 0.6f);
            Fx.quake(c.level, at, 32, 6f);
            c.feel(first, first != null ? 80 : 0, 6f);
            // Fork (3): lightning thrown on to the foes just beyond the blast
            if (c.step(3)) {
                int forks = 0, max = (int) c.num(3, "forks", 5);
                for (LivingEntity e : Steps.nearest(c, at, radius + 7, 16, null)) {
                    if (forks >= max) break;
                    if (e.distanceToSqr(at) <= radius * radius) continue;
                    Fx.at(c, FxKind.LIGHTNING, at).to(e.getBoundingBox().getCenter()).ticks(8).scale(1.2).plain().send();
                    Combat.hurtFrom(c.player, null, e, dmg * c.num(3, "share", 0.4f), Forces.Force.CHARGE, true);
                    Combat.stun(e, 10);
                    forks++;
                }
            }
            if (c.is(0)) {
                int ticks = c.ticks(160) + (int) c.num(2, "glow", 0);
                st.buff(PowerState.Buffs.OVERLOAD, c.now() + ticks, 0.25);
                if (c.step(4)) st.buff(PowerState.Buffs.LIVE_WIRE, c.now() + ticks, c.num(4, "live", 0.4f));
                st.buff(PowerState.Buffs.OVERLOAD_COST, c.now() + ticks, 1);
                Moves.postOn(c.player, Moves.FX_OVERLOAD);
                Fx.at(c, FxKind.AURA, c.pos()).aux(FxKind.Aura.OVERLOAD.ordinal()).ticks(ticks).noAnim().send();
                Running glow = Casting.start(c, "overload_glow", ticks);
                glow.end((g, w) -> {
                    Moves.postOff(c.player, Moves.FX_OVERLOAD);
                    Fx.end(c, c.player, FxKind.Aura.OVERLOAD.ordinal() + 1);
                });
            }
        });
        return Result.OK;
    }

    static Result prismLance(Cast c) {
        c.sound(PowerSounds.BEAM, 1f, 0.7f);
        int splits = c.is(1) ? 6 : 4;
        // Refraction (2): the split beams reach further
        double splitReach = 9 + c.num(2, "reach", 0);
        boolean pierce = c.kit.pierce != null;
        boolean bounce = c.step(4);
        int crown = c.step(5) ? (int) c.num(5, "crown", 6) : 0;
        Fx.at(c, FxKind.CHARGE, hand(c.player)).ticks(c.type.maxDuration).scale(1.2).send();
        // an ultimate: the screen beat lands as the lance opens
        Fx.at(c, FxKind.ULT, c.pos()).scale(28).noAnim().send();
        Running r = Casting.channel(c, c.type.maxDuration);
        r.end((rr, why) -> Fx.end(c, c.player, 0));
        r.tick(rr -> {
            Vec3 eye = c.player.getEyePosition();
            Vec3 look = c.player.getLookAngle();
            boolean pulse = rr.age % 4 == 0;
            if (c.is(2)) {
                Vec3 to = eye.add(look.scale(40));
                BlockHitResult wall = Combat.clip(c.player, eye, to);
                Vec3 end = wall.getType() == HitResult.Type.MISS ? to : wall.getLocation();
                if (pulse) {
                    for (LivingEntity e : Combat.alongLine(c.player, eye, end, 0.6)) c.hurt(e, c.dmg(), Forces.Force.HEAT);
                    Fx.beam(c, c.player, c.player, end, PRISM, 6, 0.22f, 40, true, true);
                }
                if (pulse && crown > 0) crownBeams(c, rr.age, crown, null);
                return;
            }
            BeamHit hit = trace(c.player, eye, look, 32);
            if (!pulse) return;
            Fx.beam(c, c.player, c.player, hit.end(), PRISM, 6, 0.18f, 32, true, true);
            // Spectrum Crown (5): six prisms over your head, each on its own foe
            if (crown > 0) crownBeams(c, rr.age, crown, hit.target());
            if (hit.target() == null) return;
            c.hurt(hit.target(), c.dmg(), Forces.Force.HEAT);
            int n = 0;
            java.util.Set<Integer> lit = new java.util.HashSet<>();
            lit.add(hit.target().getId());
            for (LivingEntity o : Combat.foesAround(c.player, hit.end(), splitReach)) {
                if (o == hit.target() || n >= splits) continue;
                n++;
                lit.add(o.getId());
                c.hurt(o, c.dmg(3), Forces.Force.HEAT);
                Fx.beam(c, c.player, hit.target(), o.getBoundingBox().getCenter(), rainbow(n), 6, 0.08f, (float) splitReach, false, false);
                Vec3 from = hit.target().getBoundingBox().getCenter(), at = o.getBoundingBox().getCenter();
                // Pierce (3): the split beam carries on through to the next body behind
                if (pierce) {
                    Vec3 on = at.add(at.subtract(from).normalize().scale(6));
                    for (LivingEntity p : Combat.alongLine(c.player, at, on, 0.6)) {
                        if (!lit.add(p.getId())) continue;
                        Combat.hurtFrom(c.player, null, p, c.dmg(3) * c.kit.pierce.share(), Forces.Force.HEAT, true);
                        Fx.beam(c, c.player, o, p.getBoundingBox().getCenter(), rainbow(n), 6, 0.06f, 6, false, false);
                        break;
                    }
                }
                // Bounce (4): and glances on to one more foe near where it struck
                if (bounce) {
                    for (LivingEntity q : Steps.nearest(c, at, 5, 3, o)) {
                        if (!lit.add(q.getId())) continue;
                        Combat.hurtFrom(c.player, null, q, c.dmg(3) * 0.7f, Forces.Force.HEAT, true);
                        Fx.beam(c, c.player, o, q.getBoundingBox().getCenter(), rainbow(n + 1), 6, 0.06f, 5, false, false);
                        break;
                    }
                }
            }
            if (rr.age % 12 == 0) c.sound(hit.end(), PowerSounds.BEAM, 0.6f, 1.6f);
        });
        return Result.OK;
    }

    /** Spectrum Crown: a ring of prisms over your head, each throwing a beam at a different foe in front of you. */
    private static void crownBeams(Cast c, int age, int n, @Nullable LivingEntity skip) {
        Vec3 head = c.player.getEyePosition().add(0, 0.9, 0);
        Vec3 look = c.player.getLookAngle();
        int i = 0;
        for (LivingEntity e : Steps.nearest(c, head.add(look.scale(10)), 14, n + 1, skip)) {
            if (i >= n) break;
            if (e.getBoundingBox().getCenter().subtract(head).dot(look) < 0) continue;
            double a = Math.PI * 2 * i / n + age * 0.08;
            Vec3 prism = head.add(Math.cos(a) * 0.55, 0, Math.sin(a) * 0.55);
            Vec3 to = e.getBoundingBox().getCenter();
            Combat.hurtFrom(c.player, null, e, c.dmg(3), Forces.Force.HEAT, true);
            Fx.at(c, FxKind.BEAM, prism).to(to).colour(rainbow(i)).aux(0.07).scale(14).ticks(6).plain().send();
            i++;
        }
    }

    static int rainbow(int i) {
        int[] c = {0xFFFF6B6B, 0xFFFFC56B, 0xFFFFF36B, 0xFF7BFF8A, 0xFF6BD8FF, 0xFFB98BFF};
        return c[i % c.length];
    }
}
