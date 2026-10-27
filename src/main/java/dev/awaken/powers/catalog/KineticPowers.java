package dev.awaken.powers.catalog;

import dev.awaken.core.Forces;
import dev.awaken.powers.Cast;
import dev.awaken.powers.Casting;
import dev.awaken.powers.Combat;
import dev.awaken.powers.FlyingBlocks;
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
import dev.awaken.powers.TerrainLedger;
import dev.awaken.powers.fx.CastAnim;
import dev.awaken.powers.fx.CastProfiles;
import dev.awaken.powers.fx.FxEvent;
import dev.awaken.powers.fx.FxKind;
import dev.awaken.powers.fx.FxStyle;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Kinetic: punch a mob into the sky. Scales with STR. */
final class KineticPowers {
    private KineticPowers() {}

    static final int ORANGE = 0xE08F1F;

    static void register() {
        PowerType.builder("ground_slam", "Ground Slam", Family.KINETIC, Slot.ACTIVE)
                .cost(22).cooldown(120).damage(6).unlock(Rank.E).icon("slam")
                .desc("Slam the ground: a shockwave hurts and throws everything around you. In the air you crash down first, harder the further you fall.")
                .variant(Variant.of("Quake", "50% wider, a little weaker").radius(1.5f).dmg(0.85f))
                .variant(Variant.of("Shatter", "Tighter and harder; stuns for a second").radius(0.8f).dmg(1.3f))
                .cast(KineticPowers::groundSlam).register();

        PowerType.builder("leap", "Leap", Family.KINETIC, Slot.MOVEMENT)
                .cost(15).cooldown(80).damage(3).unlock(Rank.E).icon("leap")
                .desc("A huge bound forward. You land without fall damage and the landing knocks back what's around you.")
                .variant(Variant.of("Bound", "Further and flatter").radius(1.4f))
                .variant(Variant.of("Pounce", "Higher; the landing hits far harder").dmg(1.8f))
                .predict((p, variant, level) -> {
                    Vec3 f = Moves.flat(p.getLookAngle());
                    double h = variant == 1 ? 1.5 : 1.1;
                    double y = variant == 2 ? 1.15 : variant == 1 ? 0.8 : 0.95;
                    return new Vec3(f.x * h, y, f.z * h);
                })
                .cast(KineticPowers::leap).register();

        PowerType.builder("haymaker", "Haymaker", Family.KINETIC, Slot.ACTIVE)
                .cost(18).cooldown(100).damage(7).unlock(Rank.E).icon("fist").channel(0, 24)
                .desc("Hold to wind up, release to punch. A full charge launches the target across the room.")
                .variant(Variant.of("Uppercut", "Launches straight up into the sky"))
                .variant(Variant.of("Piledriver", "Drives the target into the ground and stuns it").dmg(1.1f))
                .cast(KineticPowers::haymaker).register();

        PowerType.builder("terrain_rip", "Terrain Rip", Family.KINETIC, Slot.ACTIVE)
                .cost(30).cooldown(200).damage(14).unlock(Rank.C).icon("rip")
                .desc("Tear a chunk of ground out and throw it. The crater fills back in after a few seconds.")
                .variant(Variant.of("Boulder", "One huge rock: harder, wider splash").dmg(1.4f).radius(1.3f))
                .variant(Variant.of("Scatter", "Five smaller rocks in a spread").dmg(0.6f))
                .cast(KineticPowers::terrainRip).register();

        PowerType.builder("shockwave", "Shockwave", Family.KINETIC, Slot.ACTIVE)
                .cost(20).cooldown(140).damage(5).unlock(Rank.D).icon("wave")
                .desc("Stamp a rolling wave of force forward that bowls over everything in its path.")
                .variant(Variant.of("Wide", "A broad, shorter wave").radius(0.75f))
                .variant(Variant.of("Lance", "A narrow, long, harder wave").radius(1.5f).dmg(1.2f))
                .cast(KineticPowers::shockwave).register();

        PowerType.builder("brace", "Brace", Family.KINETIC, Slot.ACTIVE)
                .cost(15).cooldown(240).damage(3).unlock(Rank.D).icon("brace")
                .desc("Plant your feet for 3 s: far less damage, no knockback, and melee attackers take a share back.")
                .variant(Variant.of("Iron", "Takes even less damage but reflects nothing"))
                .variant(Variant.of("Thorns", "Takes more damage but reflects all of it"))
                .cast(KineticPowers::brace).register();

        PowerType.builder("thunderclap", "Thunderclap", Family.KINETIC, Slot.ACTIVE)
                .forces(Forces.Force.CHARGE).cost(40).cooldown(280).damage(12).unlock(Rank.C).hidden().icon("clap")
                .desc("Stormbreaker's signature. Clap your hands and the sky answers: lightning and a stunning shock all around you.")
                .variant(Variant.of("Rolling", "Three rings of thunder rolling outward").dmg(0.6f))
                .variant(Variant.of("Focused", "One bolt on your target that forks to three more").dmg(2.0f))
                .cast(KineticPowers::thunderclap).register();
    }

    // ---- behaviours -------------------------------------------------------------------------------------------------

    static Result groundSlam(Cast c) {
        BlockPos ground = findGround(c, 24);
        double height = ground == null ? 0 : c.player.getY() - (ground.getY() + 1);
        if (Combat.airborne(c.player) && height > 1.5) {
            Moves.launch(c.player, new Vec3(0, -2.4, 0));
            Moves.noFall(c, 80);
            double startY = c.player.getY();
            Running r = Casting.start(c, 70);
            // the dive: a comet trail and both fists raised, then the slam on landing
            Fx.at(c, FxKind.TRAIL, c.pos()).aux(2).ticks(70).send();
            r.tick(rr -> {
                if (rr.age > 1 && (c.player.onGround() || c.player.isInWater())) {
                    slamImpact(c, Math.min(14, Math.max(0, startY - c.player.getY()) * 0.6));
                    rr.stop();
                }
            });
            c.sound(PowerSounds.WHOOSH, 1f, 0.6f);
            return Result.OK;
        }
        slamImpact(c, 0);
        return Result.OK;
    }

    static void slamImpact(Cast c, double bonus) {
        Vec3 at = c.pos();
        double radius = c.radius(4.5);
        float dmg = c.dmg(c.type.damage + (float) bonus);
        int hit = 0;
        LivingEntity first = null;
        for (LivingEntity e : Combat.foesAround(c.player, at.add(0, 0.5, 0), radius)) {
            if (c.hurt(e, dmg, null)) {
                hit++;
                if (first == null) first = e;
                if (hit <= 4) Fx.at(c, FxKind.IMPACT, e.getBoundingBox().getCenter()).dir(e.position().subtract(at)).scale(0.9).noAnim().send();
            }
            Combat.knockFrom(e, at, 0.6, 0.55);
            if (c.is(2)) Combat.stun(e, 20);
        }
        for (LivingEntity e : Combat.bystandersAround(c.player, at.add(0, 0.5, 0), radius)) Combat.knockFrom(e, at, 0.45, 0.4);
        // shockwave ring, cracks, the ground's own chunks thrown up, dust; a falling slam is bigger and hotter
        Fx.at(c, FxKind.SLAM, at).scale(radius).flag(FxEvent.REACH).flag(FxEvent.FULL, bonus > 4).send();
        c.sound(PowerSounds.SLAM, 1.2f, bonus > 0 ? 0.7f : 0.9f);
        Fx.quake(c.level, at, 14, 2.5f + (float) bonus * 0.2f);
        c.feel(first, hit > 0 ? 60 : 0, 3f);
    }

    static BlockPos findGround(Cast c, int depth) {
        BlockPos p = c.player.blockPosition();
        for (int i = 0; i < depth; i++) {
            BlockPos q = p.below(i);
            if (!c.level.getBlockState(q).getCollisionShape(c.level, q).isEmpty()) return q;
        }
        return null;
    }

    static Result leap(Cast c) {
        if (!c.predicted) Moves.launch(c.player, c.type.predictor.motion(c.player, c.variant, c.powerLevel));
        Moves.noFall(c, 120);
        c.sound(PowerSounds.WHOOSH, 1f, 0.8f);
        Fx.at(c, FxKind.DASH, c.pos()).dir(c.player.getDeltaMovement()).ticks(20).send();
        Running r = Casting.start(c, 100);
        r.tick(rr -> {
            if (rr.age > 4 && (c.player.onGround() || c.player.isInWater())) {
                Vec3 at = c.pos();
                double landing = 3.0;
                Moves.blast(c, at.add(0, 0.5, 0), landing, c.dmg(), null, 0.8, 0.4);
                Fx.at(c, FxKind.SLAM, at).scale(landing).flag(FxEvent.REACH).flag(FxEvent.FULL, c.is(2)).anim(CastAnim.SLAM).send();
                c.sound(at, PowerSounds.IMPACT, 0.8f, 1.1f);
                Fx.quake(c.level, at, 8, 1.2f);
                rr.stop();
            }
        });
        return Result.OK;
    }

    static Result haymaker(Cast c) {
        c.sound(PowerSounds.CHARGE_UP, 0.7f, 0.8f);
        // wind-up: the fist cocked back over the shoulder, energy gathering at the knuckles
        Fx.at(c, FxKind.CHARGE, c.player.getEyePosition()).ticks(c.type.maxDuration).scale(1.1).send();
        Running r = Casting.channel(c, c.type.maxDuration);
        r.tick(rr -> {
            if (rr.age == 20) c.sound(PowerSounds.CHARGE_UP, 0.6f, 1.6f);
        }).end((rr, why) -> {
            double charge = Math.min(1.0, rr.age / 20.0);
            Fx.end(c, c.player, 0);
            if (why == Running.Why.CANCELLED) return;
            LivingEntity t = Combat.aimEntity(c.player, c.player.getEyePosition(), c.player.getLookAngle(), 4.5, 0.6, true);
            if (t == null) {
                // a punch into the air still throws a ring of force
                Vec3 front = c.player.getEyePosition().add(c.player.getLookAngle().scale(1.6));
                Fx.at(c, FxKind.IMPACT, front).dir(c.player.getLookAngle()).scale(0.5 + 0.5 * charge).send();
                c.sound(PowerSounds.WHOOSH, 0.8f, 1.3f);
                return;
            }
            float dmg = c.dmg(c.type.damage + 9f * (float) charge);
            c.hurt(t, dmg, null);
            Vec3 at = t.position().add(0, t.getBbHeight() * 0.6, 0);
            switch (c.variant) {
                case 1 -> Combat.knockFrom(t, c.pos(), 0.25, 0.9 + 1.1 * charge);
                case 2 -> {
                    Combat.push(t, new Vec3(0, -1.6, 0));
                    Combat.stun(t, 30 + (int) (20 * charge));
                }
                default -> Combat.knockFrom(t, c.pos(), 0.8 + 1.4 * charge, 0.45 + 0.6 * charge);
            }
            Fx.at(c, FxKind.IMPACT, at).dir(c.player.getLookAngle()).scale(1.1 + 1.6 * charge).flag(FxEvent.FULL, charge >= 1).send();
            if (c.is(2)) Fx.at(c, FxKind.SLAM, t.position()).scale(2.2).noAnim().send();
            c.sound(at, PowerSounds.PUNCH, 1.2f, 1.1f - 0.3f * (float) charge);
            c.feel(t, (int) (40 + 40 * charge), 1.5f + 3f * (float) charge);
        });
        return Result.OK;
    }

    static Result terrainRip(Cast c) {
        BlockHitResult aim = Combat.aimBlock(c, 6);
        BlockPos centre = aim != null ? aim.getBlockPos() : null;
        if (centre == null) {
            BlockPos ahead = BlockPos.containing(c.pos().add(c.flatLook().scale(2.5))).below();
            if (!c.level.getBlockState(ahead).isAir()) centre = ahead;
        }
        if (centre == null) return Result.fail("no_ground");
        // Only the surface: blocks with air (or a plant) above them.
        List<BlockPos> lifted = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 1; dy >= -1; dy--) {
                    BlockPos p = centre.offset(dx, dy, dz);
                    if (!c.level.getBlockState(p.above()).isAir() && !c.level.getBlockState(p.above()).canBeReplaced()) continue;
                    BlockState s = TerrainLedger.lift(c.level, c.player, p, 300);
                    if (s != null) {
                        lifted.add(p);
                        states.add(s);
                        break;
                    }
                }
            }
        }
        if (lifted.size() < 3) {
            return lifted.isEmpty() ? Result.fail("no_ground") : rockOut(c, centre, states);
        }
        return rockOut(c, centre, states);
    }

    private static Result rockOut(Cast c, BlockPos centre, List<BlockState> states) {
        Vec3 origin = Vec3.atCenterOf(centre).add(0, 1.2, 0);
        Vec3 dir = c.look();
        Vec3 vel = dir.scale(1.5).add(0, 0.35, 0);
        float dmg = c.dmg();
        float radius = (float) c.radius(2.5);
        BlockState main = states.get(0);
        switch (c.variant) {
            case 1 -> {
                var f = FlyingBlocks.launch(c.level, c.player, main, origin, vel.scale(0.9), dmg, null, radius, 2.2f);
                if (f != null) f.stun = 20;
                Fx.trailOn(c, f, 40);
            }
            case 2 -> {
                for (int i = 0; i < 5; i++) {
                    double yaw = (i - 2) * 0.18;
                    Vec3 d = dir.yRot((float) yaw).scale(1.6).add(0, 0.3 + 0.05 * i, 0);
                    Fx.trailOn(c, FlyingBlocks.launch(c.level, c.player, states.get(i % states.size()), origin.add(0, i * 0.15, 0), d, dmg, null,
                            radius * 0.6f, 0.7f), 30);
                }
            }
            default -> {
                for (int i = 0; i < Math.min(3, states.size()); i++) {
                    Vec3 offset = new Vec3((i - 1) * 0.5, i * 0.35, 0).yRot(-c.yRot() * net.minecraft.util.Mth.DEG_TO_RAD);
                    Fx.trailOn(c, FlyingBlocks.launch(c.level, c.player, states.get(i), origin.add(offset), vel, dmg, null, radius, 1.1f), 30);
                }
            }
        }
        Vec3 at = Vec3.atCenterOf(centre);
        Fx.at(c, FxKind.SLAM, at.add(0, 0.5, 0)).scale(2.6).send();
        c.sound(at, PowerSounds.RIP, 1.2f, 0.8f);
        Fx.quake(c.level, at, 12, 2f);
        return Result.OK;
    }

    /**
     * The stamp: the key press starts the knee lifting (everyone sees it), and the wave leaves when the foot lands, a
     * short wind-up later (CastProfiles "shockwave": energy and cooldown are spent on the press). The wave goes where
     * you look when the foot lands.
     */
    static Result shockwave(Cast c) {
        CastProfiles.Profile prof = CastProfiles.byPower(c.type.path);
        int wait = prof == null ? 1 : Math.max(1, prof.windup());
        Fx.pose(c);
        c.sound(PowerSounds.STOMP_LIFT, 0.8f, 1f);
        Running w = Casting.start(c, "shockwave_windup", wait);
        w.tick(rr -> {
            if (rr.age != wait - 1) return;
            if (!c.player.isAlive() || c.player.isSpectator()) return;
            shockwaveRelease(new Cast(c.player, c.type, c.variant, c.powerLevel, c.slot, c.predicted, c.player.getYRot(), c.player.getXRot()));
        });
        return Result.OK;
    }

    static void shockwaveRelease(Cast c) {
        double range = c.is(1) ? 7 : c.is(2) ? 14 : 9;
        double half = c.is(1) ? 50 : c.is(2) ? 15 : 30;
        Vec3 origin = c.pos().add(0, 0.5, 0);
        Vec3 dir = c.flatLook();
        float dmg = c.dmg();
        c.sound(PowerSounds.STOMP_PLANT, 1.3f, 1f);
        // a curved wall of force rolls out from the planted foot, tearing dust and chunks out of the ground at its front
        Fx.at(c, FxKind.CONE, c.pos()).dir(dir).scale(range).aux(half).ticks(10).flag(FxEvent.REACH).send();
        Running r = Casting.start(c, 10);
        r.tick(rr -> {
            double front = range * (rr.age + 1) / 10.0;
            double back = range * rr.age / 10.0;
            for (LivingEntity e : Combat.foesInCone(c.player, origin, dir, front, half)) {
                if (rr.hit.contains(e.getId())) continue;
                if (e.position().distanceTo(origin) < back - 1) continue;
                rr.hit.add(e.getId());
                c.hurt(e, dmg, null);
                Combat.knockFrom(e, origin, 1.4, 0.35);
                if (rr.hit.size() <= 5) Fx.at(c, FxKind.IMPACT, e.getBoundingBox().getCenter()).dir(dir).scale(0.8).noAnim().send();
                if (rr.hit.size() == 1) c.feel(e, 50, 0f); // the first body struck freezes a beat
            }
            for (LivingEntity e : Combat.bystandersInCone(c.player, origin, dir, front, half)) {
                if (rr.hit.add(e.getId())) Combat.knockFrom(e, origin, 0.9, 0.3);
            }
        });
        Fx.quake(c.level, origin, 10, 1.5f);
    }

    static Result brace(Cast c) {
        double reduction = c.is(1) ? 0.75 : c.is(2) ? 0.45 : 0.6;
        double reflect = c.is(1) ? 0 : c.is(2) ? 1.0 : 0.5;
        int ticks = c.ticks(60);
        PowerState st = PowerState.of(c.player);
        st.buff(PowerState.Buffs.BRACE, c.now() + ticks, reduction);
        if (reflect > 0) st.buff(PowerState.Buffs.BRACE_REFLECT, c.now() + ticks, reflect);
        Moves.addMod(c.player, Attributes.KNOCKBACK_RESISTANCE, "brace", 1.0, AttributeModifier.Operation.ADD_VALUE);
        c.sound(PowerSounds.BRACE, 1f, 0.8f);
        Fx.at(c, FxKind.RING, c.pos()).scale(1.8).ticks(9).send();
        Fx.at(c, FxKind.AURA, c.pos()).aux(FxKind.Aura.BRACE.ordinal()).ticks(ticks).noAnim().send();
        Running r = Casting.start(c, ticks);
        r.end((rr, why) -> {
            Fx.end(c, c.player, FxKind.Aura.BRACE.ordinal() + 1);
            Moves.removeMod(c.player, Attributes.KNOCKBACK_RESISTANCE, "brace");
            st.clearBuff(PowerState.Buffs.BRACE);
            st.clearBuff(PowerState.Buffs.BRACE_REFLECT);
            if (why == Running.Why.EXPIRED) {
                double burst = 3.0;
                Moves.blast(c, c.pos().add(0, 0.5, 0), burst, c.dmg(), null, 0.9, 0.3);
                Fx.at(c, FxKind.SLAM, c.pos()).scale(burst).flag(FxEvent.REACH).anim(CastAnim.STOMP).send();
                c.sound(PowerSounds.IMPACT, 0.8f, 1.2f);
            }
        });
        return Result.OK;
    }

    static Result thunderclap(Cast c) {
        Vec3 at = c.pos();
        if (c.is(2)) {
            LivingEntity t = Combat.aimEntity(c, 16, 1.0);
            if (t == null) return Result.fail("no_target");
            Fx.at(c, FxKind.STRIKE, t.position()).scale(1.3).send();
            c.hurt(t, c.dmg(), Forces.Force.CHARGE);
            Combat.stun(t, 30);
            LivingEntity from = t;
            int forks = 0;
            for (LivingEntity o : Combat.foesAround(c.player, t.position(), 7)) {
                if (o == t || forks >= 3) continue;
                Fx.at(c, FxKind.LIGHTNING, from.position().add(0, 1, 0)).to(o.position().add(0, 1, 0)).ticks(7).noAnim().send();
                c.hurt(o, c.dmg() * 0.3f, Forces.Force.CHARGE);
                Combat.stun(o, 15);
                forks++;
            }
            c.sound(t.position(), PowerSounds.THUNDER, 1.4f, 1.1f);
            c.feel(t, 70, 4f);
            return Result.OK;
        }
        int rings = c.is(1) ? 3 : 1;
        Fx.at(c, FxKind.CAST, c.player.getEyePosition()).anim(CastAnim.CLAP).send();
        Running r = Casting.start(c, rings == 1 ? 1 : 17);
        r.tick(rr -> {
            if (rr.age % 8 != 0) return;
            int ring = rr.age / 8;
            double radius = rings == 1 ? c.radius(7) : 4 + ring * 3;
            Fx.at(c, FxKind.STRIKE, at).scale(1.2).noAnim().send();
            Fx.at(c, FxKind.RING, at).scale(radius).ticks(10).style(FxStyle.CHARGE).flag(FxEvent.REACH, ring == 0).noAnim().send();
            for (LivingEntity e : Combat.foesAround(c.player, at.add(0, 0.5, 0), radius)) {
                if (rings > 1 && rr.hit.contains(e.getId() * 10 + ring)) continue;
                rr.hit.add(e.getId() * 10 + ring);
                c.hurt(e, c.dmg(), Forces.Force.CHARGE);
                Combat.stun(e, 30);
                Combat.knockFrom(e, at, 0.5, 0.3);
                Fx.at(c, FxKind.LIGHTNING, at.add(0, 1.5, 0)).to(e.position().add(0, e.getBbHeight() * 0.5, 0)).ticks(6).noAnim().send();
            }
            if (ring == 0) ForceInteractions.chargeEchoes(c.player, at, radius);
            c.sound(at, PowerSounds.THUNDER, 1.5f, 1.0f - ring * 0.1f);
            Fx.quake(c.level, at, 20, 4f);
        });
        return Result.OK;
    }
}
