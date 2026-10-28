package dev.awaken.powers.catalog;

import dev.awaken.powers.fx.CastAnim;
import dev.awaken.powers.fx.FxEvent;
import dev.awaken.powers.fx.FxKind;
import dev.awaken.powers.fx.FxStyle;

import dev.awaken.core.Forces;
import dev.awaken.powers.Bolts;
import dev.awaken.powers.Cast;
import dev.awaken.powers.PowerNumbers;
import dev.awaken.powers.Casting;
import dev.awaken.powers.Combat;
import dev.awaken.powers.ForceInteractions;
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
        double range = c.is(2) ? 32 : 24;
        c.sound(PowerSounds.BEAM_GATHER, 0.9f, 1f);
        // the light gathers at the palm while the arm comes up, then the layered beam itself (see beamRay)
        Fx.at(c, FxKind.CHARGE, hand(c.player)).ticks(c.type.maxDuration).scale(0.7).send();
        Running r = Casting.channel(c, c.type.maxDuration);
        r.end((rr, why) -> {
            Fx.end(c, c.player, 0);
            c.sound(PowerSounds.BEAM_END, 0.8f, 1f);
        });
        r.tick(rr -> {
            Vec3 eye = c.player.getEyePosition();
            Vec3 look = c.player.getLookAngle();
            boolean pulse = rr.age % 4 == 0;
            beamRay(c, rr, eye, look, range, c.dmg(), pulse, true, c.is(2) ? 0.07f : 0.12f);
            if (c.is(1)) {
                for (int s = -1; s <= 1; s += 2) {
                    Vec3 side = look.yRot((float) Math.toRadians(12 * s));
                    beamRay(c, rr, eye, side, range * 0.8, PowerNumbers.hit(c, PowerNumbers.Hit.BEAM_SIDE), pulse, false, 0.08f);
                }
            }
            // the hum starts when the arm is up (tick 5) and is re-laid every half second over a 0.62 s file
            if (rr.age % 10 == 5) c.sound(PowerSounds.BEAM_LOOP, 0.6f, 1f);
        });
        return Result.OK;
    }

    /** One beam ray: damage on pulse ticks, burn, steam from ice, visuals every 4 ticks. */
    static void beamRay(Cast c, Running rr, Vec3 eye, Vec3 dir, double range, float dmg, boolean pulse, boolean follow, float width) {
        BeamHit hit = trace(c.player, eye, dir, range);
        if (pulse) {
            if (hit.target() != null) {
                c.hurt(hit.target(), dmg, Forces.Force.HEAT);
                if (rr.age % 20 == 0) hit.target().igniteForSeconds(2);
            } else if (hit.block() != null) {
                BlockState s = c.level.getBlockState(hit.block().getBlockPos());
                if ((s.is(BlockTags.ICE) || s.is(BlockTags.SNOW) || s.is(Blocks.POWDER_SNOW)) && rr.age - rr.count > 40) {
                    rr.count = rr.age;
                    Zones.steam(c.level, hit.end(), 3.5, 140);
                    ForceInteractions.count(c.player, ForceInteractions.Reaction.STEAM);
                }
            }
            Fx.beam(c.player, c.player, hit.end(), BEAM, 6, width, (float) range, follow);
        }
        // embers, splash glow and scorch marks at the end of the beam are drawn by the client from the beam itself
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
                if (rr.age == 8) novaBlast(c, at, radius);
            });
            return Result.OK;
        }
        if (c.is(1)) {
            Running r = Casting.start(c, 12);
            r.tick(rr -> {
                if (rr.age == 0 || rr.age == 10) novaBlast(c, at, radius * (rr.age == 0 ? 0.8 : 1.0));
            });
            return Result.OK;
        }
        novaBlast(c, at, radius);
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
    }

    static Result barrier(Cast c) {
        float amount = PowerNumbers.shield(c);
        int ticks = PowerNumbers.effectTicks(c);
        Moves.addMod(c.player, Attributes.MAX_ABSORPTION, "barrier", amount, AttributeModifier.Operation.ADD_VALUE);
        c.player.setAbsorptionAmount(Math.max(c.player.getAbsorptionAmount(), amount));
        if (c.is(1)) PowerState.of(c.player).buff(PowerState.Buffs.BRACE_REFLECT, c.now() + ticks, 0.33);
        c.sound(PowerSounds.BARRIER, 1f, 1.2f);
        // a bubble with a bright rim and a hex skin that follows you
        Fx.at(c, FxKind.DOME, c.pos().add(0, 1, 0)).scale(1.35).ticks(ticks).flag(FxEvent.FOLLOW).send();
        Running r = Casting.start(c, ticks);
        r.tick(rr -> {
            if (rr.age > 2 && c.player.getAbsorptionAmount() <= 0.01f) rr.stop(Running.Why.DONE);
        }).end((rr, why) -> {
            Fx.end(c, c.player, -2);
            Moves.removeMod(c.player, Attributes.MAX_ABSORPTION, "barrier");
            if (c.is(1)) PowerState.of(c.player).clearBuff(PowerState.Buffs.BRACE_REFLECT);
            if (why == Running.Why.DONE) {
                Vec3 at = c.pos().add(0, 1, 0);
                Moves.blast(c, at, 4, c.dmg(), Forces.Force.CHARGE, 1.0, 0.3);
                ForceInteractions.chargeEchoes(c.player, at, 4);
                Fx.at(c, FxKind.NOVA, at).scale(4).style(FxStyle.CHARGE).flag(FxEvent.REACH).noAnim().send();
                c.sound(PowerSounds.BARRIER_BREAK, 1.2f, 1f);
            }
        });
        return Result.OK;
    }

    static Result chargedShot(Cast c) {
        if (c.is(2)) {
            Fx.at(c, FxKind.CAST, hand(c.player)).anim(CastAnim.PUSH2).send();
            for (int i = -1; i <= 1; i++) {
                Vec3 d = c.look().yRot((float) Math.toRadians(i * 6));
                Bolts.fire(c.level, c.player, c.player, hand(c.player), d, 2.4f, c.dmg(), Forces.Force.CHARGE, 0xFF9AD8FF, 40, 0f).stun = 6;
            }
            c.sound(PowerSounds.SHOT, 1f, 1.4f);
            return Result.OK;
        }
        c.sound(PowerSounds.CHARGE_UP, 0.7f, 1.2f);
        // crackling orb at the hand; the bolt itself is drawn as it flies (Bolts)
        Fx.at(c, FxKind.CHARGE, hand(c.player)).ticks(c.type.maxDuration).scale(0.9).send();
        Running r = Casting.channel(c, c.type.maxDuration);
        r.tick(rr -> {
            if (rr.age == 29) c.sound(PowerSounds.CHARGE_UP, 0.6f, 2f);
        }).end((rr, why) -> {
            double charge = Math.min(1.0, rr.age / 30.0);
            Fx.at(c, FxKind.CHARGE_END, hand(c.player)).flag(FxEvent.FULL, charge > 0.95).noAnim().send();
            if (why == Running.Why.CANCELLED) return;
            Fx.at(c, FxKind.CAST, hand(c.player)).anim(CastAnim.AIM).send();
            float dmg = PowerNumbers.chargedDamage(c, charge);
            var b = Bolts.fire(c.level, c.player, c.player, hand(c.player), c.player.getLookAngle(), 2.6f, dmg, Forces.Force.CHARGE,
                    0xFF9AD8FF, 40, charge > 0.95 ? 2.5f : 0f);
            b.stun = 10 + (int) (20 * charge);
            if (c.is(1)) b.bounces = 2;
            // a muzzle burst at the palm, and the bolt's landing where the aim meets something (it flies 2.6 blocks a tick)
            Fx.at(c, FxKind.IMPACT, hand(c.player)).dir(c.player.getLookAngle()).scale(1 + charge).noAnim().send();
            BeamHit aim = trace(c.player, c.player.getEyePosition(), c.player.getLookAngle(), 48);
            int flight = Math.max(1, (int) Math.ceil(hand(c.player).distanceTo(aim.end()) / 2.6));
            Vec3 lands = aim.end();
            Vec3 look = c.player.getLookAngle();
            float landing = (float) (1.2 + 1.4 * charge);
            boolean full = charge > 0.95;
            dev.awaken.powers.Scheduler.after(flight, () -> {
                if (c.player.isRemoved()) return;
                Fx.at(c, FxKind.IMPACT, lands).dir(look).scale(landing).flag(FxEvent.FULL, full).noAnim().send();
                if (full) Fx.at(c, FxKind.NOVA, lands).scale(2.5).noAnim().send();
            });
            c.sound(PowerSounds.SHOT, 1f, 1.4f - 0.5f * (float) charge);
            c.feel(null, 0, 0.8f + 1.5f * (float) charge);
        });
        return Result.OK;
    }

    static Result overload(Cast c) {
        PowerState st = PowerState.of(c.player);
        if (c.is(2)) {
            int ticks = PowerNumbers.effectTicks(c);
            st.buff(PowerState.Buffs.CONDUIT, c.now() + ticks, 1);
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
            if (c.is(0)) {
                int ticks = PowerNumbers.effectTicks(c);
                st.buff(PowerState.Buffs.OVERLOAD, c.now() + ticks, 0.25);
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
                    Fx.beam(c.player, c.player, end, PRISM, 6, 0.22f, 40, true);
                }
                return;
            }
            BeamHit hit = trace(c.player, eye, look, 32);
            if (!pulse) return;
            Fx.beam(c.player, c.player, hit.end(), PRISM, 6, 0.18f, 32, true);
            if (hit.target() == null) return;
            c.hurt(hit.target(), c.dmg(), Forces.Force.HEAT);
            int n = 0;
            for (LivingEntity o : Combat.foesAround(c.player, hit.end(), 9)) {
                if (o == hit.target() || n >= splits) continue;
                n++;
                c.hurt(o, PowerNumbers.hit(c, PowerNumbers.Hit.PRISM_FORK), Forces.Force.HEAT);
                Fx.beam(c.player, hit.target(), o.getBoundingBox().getCenter(), rainbow(n), 6, 0.08f, 9, false);
            }
            if (rr.age % 12 == 0) c.sound(hit.end(), PowerSounds.BEAM, 0.6f, 1.6f);
        });
        return Result.OK;
    }

    static int rainbow(int i) {
        int[] c = {0xFFFF6B6B, 0xFFFFC56B, 0xFFFFF36B, 0xFF7BFF8A, 0xFF6BD8FF, 0xFFB98BFF};
        return c[i % c.length];
    }
}
