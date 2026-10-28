package dev.awaken.powers.catalog;

import dev.awaken.powers.fx.CastAnim;
import dev.awaken.powers.fx.FxEvent;
import dev.awaken.powers.fx.FxKind;
import dev.awaken.powers.fx.FxStyle;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.Forces;
import dev.awaken.powers.Cast;
import dev.awaken.powers.Casting;
import dev.awaken.powers.Combat;
import dev.awaken.powers.Constructs;
import dev.awaken.powers.ForceInteractions;
import dev.awaken.powers.Fx;
import dev.awaken.powers.ladder.Riders;
import dev.awaken.powers.ladder.StepHooks;
import dev.awaken.powers.ladder.Steps;
import dev.awaken.powers.Moves;
import dev.awaken.powers.PowerSounds;
import dev.awaken.powers.PowerState;
import dev.awaken.powers.PowerType;
import dev.awaken.powers.PowerType.Family;
import dev.awaken.powers.PowerType.Result;
import dev.awaken.powers.PowerType.Slot;
import dev.awaken.powers.PowerType.Variant;
import dev.awaken.powers.Powers;
import dev.awaken.powers.Running;
import dev.awaken.powers.Scheduler;
import dev.awaken.powers.TerrainLedger;
import dev.awaken.powers.net.PowerPayloads;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.progression.Stat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Spirit: a calm power that hits very hard. Path signatures and latents live here too. Scales with SPI. */
public final class SpiritPowers {
    private SpiritPowers() {}

    static final int JADE = 0x86E0A0;
    static final int SPIRIT = 0x9FB0FF;

    /** Blocks the spirit can walk through (Spirit meets Mirror). Gates add their mirror blocks to this tag. */
    public static final TagKey<Block> MIRROR = TagKey.create(Registries.BLOCK, Awaken.id("mirror"));

    static void register() {
        PowerType.builder("aura", "Aura", Family.SPIRIT, Slot.PASSIVE)
                .forces(Forces.Force.SPIRIT).damage(1.2f).unlock(Rank.E).icon("aura")
                .desc("Your spirit leaks out. Hostiles right next to you take a little spirit damage, and out of combat you slowly heal.")
                .passive(SpiritPowers::aura).register();

        PowerType.builder("spirit_palm", "Spirit Palm", Family.SPIRIT, Slot.ACTIVE)
                .forces(Forces.Force.SPIRIT).cost(16).cooldown(100).damage(8).unlock(Rank.E).icon("palm")
                .desc("A palm strike that sends your spirit through the target: it's thrown back, and a second later the echo hits again.")
                .variant(Variant.of("Echo Palm", "A lighter strike with a doubled echo").dmg(0.7f))
                .variant(Variant.of("Push", "A wide push that throws everything in front of you").dmg(0.8f))
                .cast(SpiritPowers::spiritPalm).register();

        PowerType.builder("mend", "Mend", Family.SPIRIT, Slot.ACTIVE)
                .cost(25).cooldown(400).unlock(Rank.E).icon("mend")
                .desc("Heal over 2 s (more the more you're missing) and wash off poison, wither and weakness. Players near you heal half as much.")
                .variant(Variant.of("Surge", "All at once, just for you").dmg(0.8f))
                .variant(Variant.of("Circle", "Heals everyone near you fully as much").dmg(0.75f))
                .cast(SpiritPowers::mend).register();

        PowerType.builder("third_eye", "Third Eye", Family.SPIRIT, Slot.ACTIVE)
                .cost(15).cooldown(400).unlock(Rank.D).icon("eye")
                .desc("Open your third eye for 8 s: every hostile within 32 blocks shows through walls, even invisible ones.")
                .variant(Variant.of("Far", "Sees 64 blocks, for less time").dur(0.7f))
                .variant(Variant.of("Long", "Stays open 14 s").dur(1.75f))
                .cast(SpiritPowers::thirdEye).register();

        PowerType.builder("cord_lash", "Cord Lash", Family.SPIRIT, Slot.ACTIVE)
                .forces(Forces.Force.SPIRIT).cost(18).cooldown(120).damage(6).unlock(Rank.D).icon("lash")
                .desc("Lash a target up to 14 blocks away with your spirit cord and haul it to your feet, dazed.")
                .variant(Variant.of("Anchor", "Haul yourself to the target instead"))
                .variant(Variant.of("Bind", "Don't pull: bind it in place for longer"))
                .cast(SpiritPowers::cordLash).register();

        PowerType.builder("bulwark", "Bulwark", Family.SPIRIT, Slot.ACTIVE)
                .forces(Forces.Force.SPIRIT).cost(40).cooldown(600).unlock(Rank.C).hidden().icon("bulwark")
                .desc("Warden's signature. A dome of will for 8 s: allies inside take half damage, arrows dissolve against it, enemies are pushed out.")
                .variant(Variant.of("Aegis", "A wider dome").radius(1.4f))
                .variant(Variant.of("Fortress", "Lasts 12 s").dur(1.5f))
                .cast(SpiritPowers::bulwark).register();

        PowerType.builder("hundred_palms", "Hundred Palms", Family.SPIRIT, Slot.ULTIMATE)
                .forces(Forces.Force.SPIRIT).cost(50).cooldown(900).damage(1.6f).unlock(Rank.C).hidden().icon("palms")
                .desc("Monk's signature. Two seconds of palms too fast to see in front of you, ending in one that launches. Harder bare-handed.")
                .variant(Variant.of("Storm", "A wide, lighter flurry").dmg(0.7f))
                .variant(Variant.of("Focus", "Every palm on the one target in front of you").dmg(1.6f))
                .cast(SpiritPowers::hundredPalms).register();

        PowerType.builder("void_maw", "Void Maw", Family.SPIRIT, Slot.ULTIMATE)
                .forces(Forces.Force.GRAVITY, Forces.Force.SPIRIT).cost(55).cooldown(1200).damage(3).unlock(Rank.C).hidden().icon("maw")
                .desc("Hollow's signature. Tear open a maw where you aim. It drags everything in, chews, and swallows the weak whole, feeding you.")
                .variant(Variant.of("Abyss", "Wider and slower").radius(1.4f).dur(1.3f))
                .variant(Variant.of("Hunger", "Swallows anything below a quarter of its health"))
                .cast(SpiritPowers::voidMaw).register();

        PowerType.builder("waking_dream", "Waking Dream", Family.SPIRIT, Slot.ACTIVE)
                .cost(25).cooldown(200).unlock(Rank.C).hidden().icon("dream")
                .desc("Dreamwalker's signature. Shape the world like a lucid dream: look at a wall for a door, at a gap for a bridge, up a ledge for stairs.")
                .cast(SpiritPowers::wakingDream).register();

        PowerType.builder("storm_call", "Storm Call", Family.SPIRIT, Slot.ACTIVE)
                .forces(Forces.Force.CHARGE).cost(45).cooldown(600).damage(14).unlock(Rank.D).hidden().icon("storm")
                .desc("A latent gift from the storm. Call lightning down on up to five enemies around you. Stronger in the rain.")
                .variant(Variant.of("Tempest", "Up to eight weaker strikes").dmg(0.7f))
                .cast(SpiritPowers::stormCall).register();

        PowerType.builder("ember_step", "Ember Step", Family.SPIRIT, Slot.MOVEMENT)
                .forces(Forces.Force.HEAT).cost(14).cooldown(30).damage(2).unlock(Rank.D).hidden().icon("ember").dash()
                .desc("A latent gift from the lava. Dash forward leaving a trail of fire that burns whatever follows you.")
                .predict((p, variant, level, strafe, forward) -> {
                    Vec3 f = Moves.flat(p.getLookAngle());
                    return new Vec3(f.x * 1.7, p.onGround() ? 0.12 : 0.3, f.z * 1.7);
                })
                .cast(SpiritPowers::emberStep).register();

        PowerType.builder("frost_nova", "Frost Nova", Family.SPIRIT, Slot.ACTIVE)
                .forces(Forces.Force.FROST).cost(28).cooldown(240).damage(7).unlock(Rank.D).hidden().icon("frost")
                .desc("A latent gift from the cold. A ring of frost freezes everything around you: slowed, frosted, and ready to burst into steam.")
                .cast(SpiritPowers::frostNova).register();

        PowerType.builder("ghost_step", "Ghost Step", Family.SPIRIT, Slot.MOVEMENT)
                .forces(Forces.Force.SPIRIT).cost(14).cooldown(50).damage(3).unlock(Rank.D).hidden().icon("ghost")
                .desc("A latent gift from the Silent Halls. Slip 7 blocks forward through enemies and thin walls. Mirrors let you through any distance.")
                .cast(SpiritPowers::ghostStep).register();

        PowerType.builder("twin_strike", "Twin Strike", Family.SPIRIT, Slot.ACTIVE)
                .forces(Forces.Force.SPIRIT).cost(25).cooldown(400).unlock(Rank.C).hidden().icon("twin")
                .desc("A latent gift from the Double Gate. For 8 s your twin soul repeats every blow you land, half a second later.")
                .cast(SpiritPowers::twinStrike).register();
    }

    // ---- behaviours -------------------------------------------------------------------------------------------------

    static void aura(Cast c) {
        if (c.player.tickCount % 40 >= PowerType.PASSIVE_PERIOD) return;
        // Wisps (2) reach further; Soul Guard (5) keeps the wisps ready to strike back (PowerEvents)
        List<LivingEntity> near = Combat.foesAround(c.player, c.pos().add(0, 1, 0), 3.0 + c.num(2, "reach", 0f));
        if (c.step(5)) PowerState.of(c.player).buff(PowerState.Buffs.AURA_GUARD, c.now() + 50, c.num(5, "strike", 0.5f));
        int shown = 0;
        for (LivingEntity e : near) {
            dev.awaken.powers.Combat.hurtFrom(c.player, null, e, c.dmg(), Forces.Force.SPIRIT, false);
            // Drag (4): the pulse's riders (a slow)
            if (c.kit.onHit.length > 0) Riders.onHit(c, e, c.dmg(), Forces.Force.SPIRIT);
            if (shown++ < 3) Fx.at(c, FxKind.IMPACT, e.getBoundingBox().getCenter()).scale(0.45).style(FxStyle.SOUL).flag(FxEvent.QUIET).noAnim().send();
        }
        // Calm (3): the healing out of combat comes twice as often
        int calm = Math.round(80 / (1 + c.num(3, "calm", 0f)));
        if (near.isEmpty() && !Powers.inCombat(c.player) && c.player.tickCount % calm < PowerType.PASSIVE_PERIOD
                && c.player.getHealth() < c.player.getMaxHealth()) {
            c.player.heal(0.5f);
        }
        if (c.player.tickCount % 20 < PowerType.PASSIVE_PERIOD) {
            // jade motes drifting off you, kept alive while the passive runs
            Fx.at(c, FxKind.AURA, c.pos()).aux(FxKind.Aura.GENERIC.ordinal()).ticks(26).noAnim().range(32).send();
        }
    }

    static Result spiritPalm(Cast c) {
        if (c.is(2)) {
            List<LivingEntity> hit = Combat.foesInCone(c.player, c.pos(), c.look(), 5, 40);
            if (hit.isEmpty()) return Result.fail("no_target");
            int shown = 0;
            for (LivingEntity e : hit) {
                c.hurt(e, c.dmg(), Forces.Force.SPIRIT);
                Combat.knockFrom(e, c.pos(), 2.6, 0.4);
                if (shown++ < 4) Fx.at(c, FxKind.IMPACT, e.getBoundingBox().getCenter()).dir(c.look()).scale(1.1).noAnim().send();
            }
            Fx.at(c, FxKind.PALM, c.eye().add(c.look().scale(0.6)).add(0, -0.3, 0)).dir(c.look().scale(6)).scale(2.2).anim(CastAnim.PUSH2).send();
            Fx.at(c, FxKind.CONE, c.pos()).dir(c.look()).scale(5).aux(40).ticks(6).flag(FxEvent.REACH).noAnim().send();
            c.sound(PowerSounds.SPIRIT, 1.2f, 0.8f);
            c.feel(hit.get(0), 40, 2f);
            return Result.OK;
        }
        LivingEntity t = Combat.aimEntity(c, 4.5, 0.6);
        if (t == null) return Result.fail("no_target");
        c.hurt(t, c.dmg(), Forces.Force.SPIRIT);
        // Lotus (2): pushes harder
        Combat.knockFrom(t, c.pos(), 2.0 * (1 + c.num(2, "push", 0f)), 0.35);
        Vec3 at = t.getBoundingBox().getCenter();
        // the palm print flies from the hand, rings punch through the target
        Vec3 from = c.eye().add(c.look().scale(0.6)).add(0, -0.3, 0);
        Fx.at(c, FxKind.PALM, from).dir(at.subtract(from)).scale(1.3).send();
        Fx.at(c, FxKind.IMPACT, at).target(t).dir(c.look()).scale(1.6).noAnim().send();
        c.sound(at, PowerSounds.SPIRIT, 1.2f, 1f);
        c.feel(t, 60, 2.5f);
        if (c.kit.pierce != null) {
            // Through (3): the print carries on into the foe behind
            for (LivingEntity e : Steps.nearest(c, at.add(c.look().scale(2.2)), 2.4, 1, t)) {
                Combat.hurtFrom(c.player, null, e, c.dmg() * c.kit.pierce.share(), Forces.Force.SPIRIT, true);
                Combat.knockFrom(e, c.pos(), 1.6, 0.3);
                Fx.at(c, FxKind.IMPACT, e.getBoundingBox().getCenter()).dir(c.look()).scale(1.1).plain().send();
            }
        }
        if (c.step(5)) {
            // Guardian Palm: a giant jade hand slams forward behind the strike and hits everything in front of you
            float reach = c.num(5, "reach", 6f);
            Fx.at(c, FxKind.PALM, c.eye().add(0, -0.2, 0)).dir(c.look().scale(reach)).scale(3.4).plain().send();
            for (LivingEntity e : Combat.foesInCone(c.player, c.pos(), c.look(), reach, 32)) {
                if (e == t) continue;
                Combat.hurtFrom(c.player, null, e, c.dmg() * c.num(5, "giant", 0.6f), Forces.Force.SPIRIT, true);
                Combat.knockFrom(e, c.pos(), 1.8, 0.35);
            }
        }
        float echo = c.dmg() * (c.is(1) ? 1.0f : 0.5f);
        Scheduler.after(20, () -> {
            if (!t.isAlive() || c.player.isRemoved()) return;
            Combat.hurtFrom(c.player, null, t, echo, Forces.Force.SPIRIT, true);
            // Heavy Echo (4): the echo slows
            if (c.step(4)) Moves.effect(t, MobEffects.SLOWNESS, (int) c.num(4, "slow", 40), 1);
            Vec3 p = t.getBoundingBox().getCenter();
            Fx.at(c, FxKind.ECHO, p).dir(c.look()).scale(c.is(1) ? 1.4 : 1.1).style(FxStyle.SOUL).noAnim().send();
            c.level.playSound(null, p.x, p.y, p.z, PowerSounds.SPIRIT.get(), net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.5f);
        });
        return Result.OK;
    }

    static Result mend(Cast c) {
        int spi = Progression.stat(c.player, Stat.SPI);
        float missing = c.player.getMaxHealth() - c.player.getHealth();
        // Lotus Ring (2): heals more
        float total = (float) ((4 + 0.2 * spi + 0.2 * missing) * c.v.dmg() * (1 + 0.12 * (c.powerLevel - 1)) * (1 + c.num(2, "more", 0f)));
        if (c.step(5)) bloom(c, total);
        cleanse(c.player);
        c.sound(PowerSounds.HEAL, 1f, 1.1f);
        List<Player> allies = new ArrayList<>();
        if (!c.is(1)) {
            double r = c.is(2) ? 10 : 6;
            for (Player p : c.level.getEntitiesOfClass(Player.class, c.player.getBoundingBox().inflate(r), p -> p != c.player && p.isAlive())) allies.add(p);
        }
        // Shared (4): friends heal as much as you
        float allyShare = Math.max(c.is(2) ? 1.0f : 0.5f, c.num(4, "share", 0f));
        if (c.is(1)) {
            c.player.heal(total);
            Fx.at(c, FxKind.HEAL, c.pos()).ticks(14).send();
            return Result.OK;
        }
        // a lotus circle under you, motes and wisps rising; the same on every ally the circle reaches
        Fx.at(c, FxKind.HEAL, c.pos()).ticks(40).send();
        for (Player p : allies) Fx.at(c, FxKind.HEAL, p.position()).caster(p).ticks(40).noAnim().send();
        Running r = Casting.start(c, 40);
        r.tick(rr -> {
            if (rr.age % 5 != 0) return;
            float step = total / 8f;
            c.player.heal(step);
            for (Player p : allies) {
                if (!p.isAlive()) continue;
                p.heal(step * allyShare);
                if (rr.age == 0) cleanse(p);
            }
        });
        return Result.OK;
    }

    /** Bloom (Mend 5): the lotus stays open where you stood, and everyone on it heals for a few seconds more. */
    static void bloom(Cast c, float total) {
        Vec3 centre = c.pos();
        int ticks = (int) c.num(5, "bloom", 80);
        float step = total / 16f;
        Fx.at(c, FxKind.HEAL, centre).ticks(ticks).noAnim().send();
        Running r = Casting.start(c, c.type.id + ":bloom", ticks);
        r.tick(rr -> {
            if (rr.age % 10 != 5) return;
            for (Player p : c.level.players()) {
                if (p.isAlive() && !p.isSpectator() && p.position().distanceToSqr(centre) < 3.5 * 3.5) p.heal(step);
            }
        });
    }

    static void cleanse(LivingEntity e) {
        e.removeEffect(MobEffects.POISON);
        e.removeEffect(MobEffects.WITHER);
        e.removeEffect(MobEffects.WEAKNESS);
        e.removeEffect(MobEffects.BLINDNESS);
        e.removeEffect(MobEffects.SLOWNESS);
        e.removeEffect(dev.awaken.powers.content.PowerContent.STUNNED);
    }

    static Result thirdEye(Cast c) {
        int ticks = c.ticks(160);
        // All-Seeing (5): 64 blocks whatever the variant
        double range = Math.max(c.is(1) ? 64 : 32, c.num(5, "range", 0f));
        double shared = c.num(4, "shared", 0f);
        Moves.postOn(c.player, Moves.FX_THIRD_EYE);
        c.sound(PowerSounds.EYE, 1f, 1f);
        Fx.at(c, FxKind.PSI, c.player.getEyePosition()).ticks(12).send();
        Fx.at(c, FxKind.AURA, c.pos()).aux(FxKind.Aura.THIRD_EYE.ordinal()).ticks(ticks).noAnim().send();
        Running r = Casting.start(c, ticks);
        r.tick(rr -> {
            if (rr.age % 10 != 0) return;
            List<Integer> ids = new ArrayList<>();
            for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(c.pos(), range * 2, range, range * 2),
                    x -> x.isAlive() && x instanceof net.minecraft.world.entity.monster.Enemy && !Combat.isAlly(c.player, x))) {
                if (e.distanceToSqr(c.player) > range * range) continue;
                ids.add(e.getId());
                // Exposed (3): what you see takes more from you
                if (c.step(3)) Riders.mark(c.player, e, c.now() + 14);
            }
            if (ids.isEmpty()) return;
            var marks = new PowerPayloads.Marks(PowerPayloads.Marks.THIRD_EYE, ids, 14);
            AwakenNet.send(c.player, marks);
            // Shared Sight (4): friends near you see them too
            if (shared > 0) for (ServerPlayer p : c.level.players()) {
                if (p != c.player && p.distanceToSqr(c.player) < shared * shared) AwakenNet.send(p, marks);
            }
        }).end((rr, why) -> {
            Moves.postOff(c.player, Moves.FX_THIRD_EYE);
            Fx.end(c, c.player, FxKind.Aura.THIRD_EYE.ordinal() + 1);
        });
        return Result.OK;
    }

    static Result cordLash(Cast c) {
        // Braided (2): a longer cord
        double reach = 14 + c.num(2, "range", 0f);
        LivingEntity t = Combat.aimEntity(c, reach, 0.8);
        if (t == null) return Result.fail("no_target");
        // Twin Lash (3): a second cord takes the foe nearest the first
        LivingEntity second = c.step(3) && !c.is(1) ? StepHooks.nearestFoe(c.player, t.position(), 6, t) : null;
        if (second != null && second.distanceToSqr(c.player) > reach * reach) second = null;
        if (second != null) {
            c.hurt(second, c.dmg(), Forces.Force.SPIRIT);
            Fx.at(c, FxKind.TETHER, c.player.getEyePosition()).target(second).to(second.getBoundingBox().getCenter()).aux(0).ticks(c.is(2) ? 40 : 10)
                    .noAnim().send();
        }
        c.hurt(t, c.dmg(), Forces.Force.SPIRIT);
        // a rippling soul cord from the hand to the target
        Fx.at(c, FxKind.TETHER, c.player.getEyePosition()).target(t).to(t.getBoundingBox().getCenter()).aux(0).ticks(c.is(2) ? 40 : 10).send();
        Fx.at(c, FxKind.IMPACT, t.getBoundingBox().getCenter()).scale(1).noAnim().send();
        c.sound(PowerSounds.LASH, 1.1f, 1f);
        if (c.is(1)) {
            Vec3 d = t.position().subtract(c.pos());
            Moves.launch(c.player, d.normalize().scale(Math.min(2.2, 0.6 + d.length() * 0.14)).add(0, 0.35, 0));
            Moves.noFall(c, 60);
            return Result.OK;
        }
        if (c.is(2)) {
            Combat.stun(t, 60);
            if (second != null) Combat.stun(second, 60);
            return Result.OK;
        }
        // Soul Chain (5): with two on the cords they're hauled into each other at your feet
        Vec3 meet = c.pos().add(Moves.flat(c.look()).scale(2));
        boolean chain = second != null && c.step(5);
        for (LivingEntity e : second == null ? List.of(t) : List.of(t, second)) {
            if (Combat.isHeavy(e)) continue;
            Vec3 d = (chain ? meet : c.pos()).subtract(e.position());
            Combat.push(e, d.normalize().scale(Math.min(1.8, 0.5 + d.length() * 0.14)).add(0, 0.3, 0));
        }
        // Dazed (4): the daze lasts longer
        int daze = 20 + (c.step(4) ? 16 : 0);
        LivingEntity other = second;
        Scheduler.after(8, () -> {
            if (t.isAlive()) Combat.stun(t, daze);
            if (other != null && other.isAlive()) Combat.stun(other, daze);
            if (chain && t.isAlive() && other.isAlive() && t.distanceToSqr(other) < 3.5 * 3.5) {
                Vec3 at = t.getBoundingBox().getCenter().add(other.getBoundingBox().getCenter()).scale(0.5);
                c.hurt(t, c.dmg() * 0.6f, Forces.Force.SPIRIT);
                c.hurt(other, c.dmg() * 0.6f, Forces.Force.SPIRIT);
                Fx.at(c, FxKind.IMPACT, at).scale(1.6).noAnim().send();
                c.sound(at, PowerSounds.IMPACT, 1.1f, 0.8f);
                Fx.quake(c.level, at, 10, 1.4f);
            }
        });
        return Result.OK;
    }

    static Result bulwark(Cast c) {
        Vec3 centre = c.pos();
        double radius = c.radius(6);
        int ticks = c.ticks(160);
        c.sound(PowerSounds.WARD, 1.3f, 0.8f);
        // a dome of will rises from a lotus circle
        Fx.at(c, FxKind.DOME, centre).scale(radius).aux(1).ticks(ticks).flag(FxEvent.REACH).send();
        // Lotus Dome (5): the dome opens with a blast that throws foes away
        if (c.step(5)) {
            for (LivingEntity e : Combat.foesAround(c.player, centre.add(0, 1, 0), radius + 2)) Combat.knockFrom(e, centre, 1.6 * c.num(5, "open", 1f), 0.5);
            Fx.at(c, FxKind.RING, centre).scale(radius + 2).ticks(12).plain().send();
        }
        // Repel (3): foes are pushed out harder
        double push = 0.6 + pushRider(c);
        float rest = c.num(4, "rest", 0f);
        Running r = Casting.start(c, ticks).at(centre);
        r.tick(rr -> {
            AABB box = AABB.ofSize(centre, radius * 2, radius * 2, radius * 2);
            for (Projectile p : c.level.getEntitiesOfClass(Projectile.class, box,
                    p -> p.position().distanceToSqr(centre) < radius * radius && !(p.getOwner() instanceof Player))) {
                Fx.at(c, FxKind.IMPACT, p.position()).scale(0.6).flag(FxEvent.QUIET).noAnim().send();
                p.discard();
            }
            if (rr.age % 5 == 0) {
                for (Player p : c.level.players()) {
                    if (p.position().distanceToSqr(centre) > radius * radius) continue;
                    PowerState.of(p).buff(PowerState.Buffs.BULWARK, c.now() + 8, 0.5);
                    // Rest (4): friends inside heal slowly
                    if (rest > 0 && rr.age % 20 == 0) p.heal(rest);
                }
                for (LivingEntity e : Combat.foesAround(c.player, centre.add(0, 1, 0), radius)) {
                    Vec3 out = Moves.flat(e.position().subtract(centre));
                    Combat.push(e, out.scale(push).add(0, 0.15, 0));
                }
            }
        });
        return Result.OK;
    }

    /** The force of a step's push rider, 0 without one (Repel). */
    static double pushRider(Cast c) {
        for (dev.awaken.powers.ladder.Rider r : c.kit.onHit) if (r.kind() == dev.awaken.powers.ladder.Rider.Kind.PUSH) return r.force();
        return 0;
    }

    static Result hundredPalms(Cast c) {
        boolean bare = c.player.getMainHandItem().isEmpty();
        float mul = bare ? 1.3f : 1f;
        LivingEntity focus = c.is(2) ? Combat.aimEntity(c, 5, 0.8) : null;
        if (c.is(2) && focus == null) return Result.fail("no_target");
        // Seeking (3): the palms reach foes off to the side and a little further
        double range = (c.is(1) ? 6 : 4.5) + (c.step(3) ? 1 : 0);
        double half = (c.is(1) ? 60 : 30) + (c.step(3) ? 30 : 0);
        // Prints (2): a few more palms; Last Palm (4): the finisher throws further; Thousand Arms (5): the figure's palms add on
        boolean more = c.num(2, "more", 0f) > 0;
        double launch = Math.min(3.2, 1.8 * c.num(4, "last", 1f));
        float figure = 1 + c.num(5, "figure", 0f);
        c.sound(PowerSounds.SPIRIT, 1.2f, 1.4f);
        // two seconds of palm prints too fast to follow (the screen beat rides on the flurry), then one that launches
        Fx.at(c, FxKind.FLURRY, c.eye()).ticks(40).scale(range).aux(half).flag(FxEvent.REACH).send();
        Running r = Casting.start(c, 40);
        r.tick(rr -> {
            if (rr.age % 2 != 0 && !(more && rr.age % 20 == 11)) return;
            boolean last = rr.age >= 38;
            List<LivingEntity> targets = c.is(2) ? (focus.isAlive() ? List.of(focus) : List.of())
                    : Combat.foesInCone(c.player, c.pos(), c.player.getLookAngle(), range, half);
            int shown = 0;
            for (LivingEntity e : targets) {
                float dmg = c.dmg() * mul * figure * (last ? 3f : 1f);
                Combat.hurtFrom(c.player, null, e, dmg, Forces.Force.SPIRIT, true);
                Vec3 at = e.getBoundingBox().getCenter();
                if (last) {
                    Combat.knockFrom(e, c.pos(), launch, 0.8);
                    Fx.at(c, FxKind.IMPACT, at).dir(c.player.getLookAngle()).scale(2.4).noAnim().send();
                    Fx.at(c, FxKind.ECHO, at).dir(c.player.getLookAngle()).scale(1.8).noAnim().send();
                } else {
                    Combat.knockFrom(e, c.pos(), 0.05, 0.05);
                    if (rr.age % 4 == 0 && shown++ < 3) Fx.at(c, FxKind.IMPACT, at).scale(0.55).flag(FxEvent.QUIET).noAnim().send();
                }
            }
            if (last) Fx.at(c, FxKind.CAST, c.eye()).anim(CastAnim.PALM).send();
            if (rr.age % 8 == 0) c.sound(PowerSounds.PUNCH, 0.6f, 1.6f + c.player.getRandom().nextFloat() * 0.3f);
            if (last) {
                c.sound(PowerSounds.IMPACT, 1.3f, 0.8f);
                c.feel(targets.isEmpty() ? null : targets.get(0), 80, 4f);
            } else if (rr.age % 10 == 0 && !targets.isEmpty()) {
                c.feel(targets.get(0), 20, 0.8f);
            }
        });
        return Result.OK;
    }

    static Result voidMaw(Cast c) {
        Vec3 centre = FlightPowers.aimSpot(c, 20);
        double radius = c.radius(7);
        // Hunger (3): swallows at a little more health; Fangs (2): pulls harder
        float executeAt = (c.is(2) ? 0.25f : 0.15f) + c.num(3, "hunger", 0f);
        double pull = 1 + c.num(2, "pull", 0f);
        float debris = c.num(4, "debris", 0f);
        float bite = c.num(5, "bite", 0f);
        int ticks = c.ticks(80);
        boolean dark = c.player.position().distanceToSqr(centre) < 20 * 20;
        if (dark) Moves.postOn(c.player, Moves.FX_VOID);
        c.sound(centre, PowerSounds.VOID, 1.5f, 0.6f);
        // the maw: a void core in a violet rim, contracting rings, ink and debris dragged in
        Fx.at(c, FxKind.VORTEX, centre).scale(radius).ticks(ticks).flag(FxEvent.REACH).send();
        Running r = Casting.start(c, ticks).at(centre);
        r.tick(rr -> {
            for (LivingEntity e : Combat.foesAround(c.player, centre, radius)) {
                Vec3 d = centre.subtract(e.position().add(0, e.getBbHeight() * 0.5, 0));
                if (d.lengthSqr() > 0.01) Combat.push(e, e.getDeltaMovement().scale(0.5).add(d.normalize().scale((Combat.isHeavy(e) ? 0.03 : 0.13) * pull)));
                e.fallDistance = 0;
                if (rr.age % 10 == 0) {
                    c.hurt(e, c.dmg(), Forces.Force.GRAVITY);
                    if (!Combat.isBoss(e) && e.isAlive() && e.getHealth() < e.getMaxHealth() * executeAt && e.distanceToSqr(centre) < 9) {
                        Combat.hurtFrom(c.player, null, e, e.getMaxHealth() * 4, Forces.Force.SPIRIT, true);
                        c.player.heal(c.player.getMaxHealth() * 0.1f);
                        // The Hollow Mouth (5): the bite feeds you what it swallowed
                        if (bite > 0) c.player.heal(Math.min(c.player.getMaxHealth() * 0.3f, e.getMaxHealth() * 0.2f * bite));
                        Casting.restore(c.player, 10);
                        Fx.at(c, FxKind.NOVA, centre).scale(2.5).flag(FxEvent.REPULSE).style(FxStyle.SOUL).noAnim().send();
                        c.sound(centre, PowerSounds.VOID, 1f, 1.4f);
                    }
                }
            }
            if (rr.age % 20 == 0) c.sound(centre, PowerSounds.VOID, 0.8f, 0.5f);
            // Chew (4): what the maw chews spits debris at everything round it
            if (debris > 0 && rr.age % 20 == 10) {
                List<LivingEntity> round = Combat.foesAround(c.player, centre, radius);
                if (!round.isEmpty()) {
                    for (LivingEntity e : round) c.hurt(e, c.dmg() * debris, Forces.Force.GRAVITY);
                    Fx.at(c, FxKind.NOVA, centre).scale(radius * 0.6).noAnim().send();
                }
            }
        }).end((rr, why) -> {
            if (dark) Moves.postOff(c.player, Moves.FX_VOID);
        });
        return Result.OK;
    }

    static Result wakingDream(Cast c) {
        BlockHitResult hit = Combat.aimBlock(c, 5);
        Direction facing = Constructs.facing(c);
        // a wall in front at body height: a door through it
        if (hit != null && hit.getDirection().getAxis().isHorizontal() && Math.abs(c.xRot()) < 35) {
            BlockPos face = hit.getBlockPos();
            Direction into = hit.getDirection().getOpposite();
            int baseY = Constructs.feet(c.player).getY();
            List<BlockPos> opened = new ArrayList<>();
            for (int depth = 0; depth < 4; depth++) {
                BlockPos col = face.relative(into, depth);
                BlockPos lo = new BlockPos(col.getX(), baseY, col.getZ());
                boolean any = false;
                // Bigger (3): a door three high; Lasting (4): it stays open twice as long
                BlockPos[] cells = c.step(3) ? new BlockPos[]{lo, lo.above(), lo.above(2)} : new BlockPos[]{lo, lo.above()};
                for (BlockPos p : cells) {
                    if (c.level.getBlockState(p).isAir()) continue;
                    if (TerrainLedger.lift(c.level, c.player, p, Math.round(240 * c.num(4, "lasting", 1f))) != null) {
                        opened.add(p);
                        any = true;
                    } else {
                        return opened.isEmpty() ? Result.fail("blocked") : dreamDone(c, opened);
                    }
                }
                if (!any && depth > 0) break;
            }
            if (opened.isEmpty()) return Result.fail("blocked");
            return dreamDone(c, opened);
        }
        Set<BlockPos> slabs = new HashSet<>();
        List<BlockPos> shape;
        BlockPos feet = Constructs.feet(c.player);
        int width = c.step(3) ? 3 : 1;
        if (c.xRot() < -25) {
            // stairs up toward the ledge; Lucid Architecture (5): exactly as high as the ledge ahead
            int length = c.step(5) ? ledgeStairs(c, feet, facing) : 10;
            shape = Constructs.ramp(feet, facing, length, width, slabs);
        } else {
            shape = new ArrayList<>();
            Direction side = facing.getClockWise();
            // a bridge; Lucid Architecture (5): it runs on until it meets the far side of the gap
            int max = c.step(5) ? 24 : 10;
            boolean spanned = false;
            for (int d = 1; d <= max; d++) {
                BlockPos under = feet.relative(facing, d).below();
                boolean ground = !c.level.getBlockState(under).isAir();
                if (c.step(5) && ground && spanned) break;
                if (!ground) spanned = true;
                for (int w = -(width / 2); w <= width / 2; w++) shape.add(under.relative(side, w));
            }
        }
        int placed = Constructs.place(c.player, shape, slabs, true, Math.round(400 * c.num(4, "lasting", 1f)));
        if (placed == 0) return Result.fail("blocked");
        ArchitectPowers.assemble(c, shape);
        c.sound(PowerSounds.DREAM, 1.2f, 1f);
        return Result.OK;
    }

    /** The ramp length (it rises half a block a block) that tops out on the highest ledge within ten blocks ahead. */
    private static int ledgeStairs(Cast c, BlockPos feet, Direction facing) {
        int top = 2;
        for (int d = 1; d <= 10; d++) {
            BlockPos col = feet.relative(facing, d);
            for (int h = 12; h > 0; h--) {
                if (!c.level.getBlockState(col.above(h - 1)).isAir() && c.level.getBlockState(col.above(h)).isAir()) {
                    top = Math.max(top, h);
                    break;
                }
            }
        }
        return Math.min(24, top * 2);
    }

    private static Result dreamDone(Cast c, List<BlockPos> opened) {
        int shown = 0;
        for (BlockPos p : opened) {
            if (shown++ >= 4) break;
            Fx.at(c, FxKind.IMPACT, Vec3.atCenterOf(p)).scale(0.9).noAnim().send();
        }
        Fx.at(c, FxKind.CAST, c.player.getEyePosition()).anim(CastAnim.PUSH2).send();
        Fx.at(c, FxKind.GLYPH, c.pos()).scale(1.6).ticks(30).send();
        c.sound(PowerSounds.DREAM, 1.2f, 0.8f);
        return Result.OK;
    }

    static Result stormCall(Cast c) {
        // More (4): one more strike; Clouds (2): harder
        int max = (c.is(1) ? 8 : 5) + (int) c.num(4, "more", 0);
        float harder = 1 + c.num(2, "harder", 0f);
        List<LivingEntity> foes = Combat.foesAround(c.player, c.pos(), 20);
        if (foes.isEmpty()) return Result.fail("no_target");
        foes.sort((a, b) -> Double.compare(a.distanceToSqr(c.player), b.distanceToSqr(c.player)));
        int n = 0;
        for (LivingEntity e : foes) {
            if (n >= max) break;
            int delay = n * 3;
            float mul = c.level.isRainingAt(e.blockPosition()) ? 1.5f : 1f;
            float dmg = c.dmg() * mul * harder;
            boolean firstStrike = n == 0;
            Scheduler.after(1 + delay, () -> {
                if (!e.isAlive() || c.player.isRemoved()) return;
                var strike = Fx.at(c, FxKind.STRIKE, e.position()).scale(mul > 1 ? 1.4 : 1.1);
                if (!firstStrike) strike.noAnim();
                strike.send();
                // and the real sky: a (harmless) vanilla bolt for the flash across the world and the thunder
                Moves.visualLightning(c.level, e.position());
                // a cast's hit: Chain (3) arcs on from it
                c.hurt(e, dmg, Forces.Force.CHARGE);
                Combat.stun(e, 30);
            });
            n++;
        }
        if (c.step(5)) eyeOfStorm(c, c.dmg() * harder * 0.6f);
        c.sound(PowerSounds.THUNDER, 1.4f, 0.8f);
        Fx.at(c, FxKind.CAST, c.player.getEyePosition()).anim(CastAnim.RAISE).send();
        Fx.quake(c.level, c.pos(), 24, 3f);
        return Result.OK;
    }

    /** Eye of the Storm (Storm Call 5): for a while the sky keeps striking the foe nearest you. */
    static void eyeOfStorm(Cast c, float dmg) {
        int ticks = (int) c.num(5, "follow", 120);
        Running r = Casting.start(c, c.type.id + ":eye", ticks);
        r.tick(rr -> {
            if (rr.age < 20 || rr.age % 25 != 0) return;
            LivingEntity e = StepHooks.nearestFoe(c.player, c.pos(), 8, null);
            if (e == null) return;
            Fx.at(c, FxKind.STRIKE, e.position()).scale(1.0).noAnim().send();
            Moves.visualLightning(c.level, e.position());
            c.hurt(e, dmg, Forces.Force.CHARGE);
            Combat.stun(e, 20);
        });
    }

    static Result emberStep(Cast c) {
        if (!c.predicted) Moves.launch(c.player, c.type.predictor.motion(c.player, c.variant, c.powerLevel, c.strafe(), c.forward()));
        Moves.noFall(c, 30);
        c.sound(PowerSounds.EMBER, 1f, 1f);
        Fx.at(c, FxKind.DASH, c.pos()).dir(c.player.getDeltaMovement()).ticks(10).send();
        List<Vec3> trail = new ArrayList<>();
        float dmg = c.dmg();
        // Flame Ribbons (2): a longer trail; Lasting (3): burns longer; Flare (4), Phoenix Step (5) below
        int lay = Math.round(10 * (1 + c.num(2, "longer", 0f)));
        float burn = 3 + c.num(3, "burn", 0f) / 20f;
        float flare = c.num(4, "flare", 0f);
        float phoenix = c.num(5, "phoenix", 0f);
        Set<Integer> flared = new HashSet<>(), struck = new HashSet<>();
        Running r = Casting.start(c, 60);
        r.tick(rr -> {
            // Phoenix Step: the firebird you dash as burns what it passes
            if (phoenix > 0 && rr.age < 10) {
                for (LivingEntity e : Combat.foesAround(c.player, c.pos().add(0, 0.9, 0), 2.2)) {
                    if (!struck.add(e.getId())) continue;
                    c.hurt(e, dmg * 2 * phoenix, Forces.Force.HEAT);
                    e.igniteForSeconds(burn);
                }
            }
            if (rr.age < lay && rr.age % 2 == 0) {
                Vec3 here = c.pos();
                // each stretch of the path keeps burning until the trail goes out
                if (!trail.isEmpty()) Fx.at(c, FxKind.FIRE_TRAIL, trail.get(trail.size() - 1)).to(here).ticks(60 - rr.age).noAnim().send();
                trail.add(here);
            }
            if (rr.age % 5 == 0) {
                Set<Integer> hit = new HashSet<>();
                for (Vec3 p : trail) {
                    for (LivingEntity e : Combat.foesAround(c.player, p.add(0, 0.5, 0), 1.3)) {
                        if (hit.add(e.getId())) {
                            Combat.hurtFrom(c.player, null, e, dmg, Forces.Force.HEAT, true);
                            e.igniteForSeconds(burn);
                            // Flare: the first time the fire takes a foe, embers burst off it
                            if (flare > 0 && flared.add(e.getId())) {
                                Vec3 at = e.getBoundingBox().getCenter();
                                for (LivingEntity o : Combat.foesAround(c.player, at, 2.0)) Combat.hurtFrom(c.player, null, o, dmg * flare, Forces.Force.HEAT, true);
                                Fx.at(c, FxKind.IMPACT, at).scale(0.8).plain().send();
                            }
                        }
                    }
                }
            }
        });
        return Result.OK;
    }

    static Result frostNova(Cast c) {
        Vec3 at = c.pos().add(0, 1, 0);
        double radius = c.radius(6);
        long until = c.now() + 160;
        for (LivingEntity e : Combat.foesAround(c.player, at, radius)) {
            c.hurt(e, c.dmg(), Forces.Force.FROST);
            Moves.effect(e, MobEffects.SLOWNESS, 80, 2);
            e.setTicksFrozen(Math.max(e.getTicksFrozen(), 200));
            Forces.tag(e, Forces.Tag.FROSTED);
            Scheduler.after(160, () -> {
                if (e.isAlive() && c.level.getGameTime() >= until - 1) Forces.untag(e, Forces.Tag.FROSTED);
            });
        }
        // Shatter (3), Winter Bloom (5): a hit on what this froze bursts the frost (StepHooks.frostHit)
        if (c.step(3)) {
            PowerState st = PowerState.of(c.player);
            st.buff(PowerState.Buffs.FROST_SHATTER, until, c.dmg() * c.num(3, "shatter", 0.5f));
            if (c.step(5)) st.buff(PowerState.Buffs.FROST_BLOOM, until, c.num(5, "bloom", 0.5f));
        }
        // a frost ring, ice shards flung out, cold mist and a frosted crack in the ground
        Fx.at(c, FxKind.FROST, c.pos()).scale(radius).flag(FxEvent.REACH).send();
        c.sound(PowerSounds.FROST, 1.2f, 1f);
        Fx.quake(c.level, at, 12, 1.5f);
        return Result.OK;
    }

    static Result ghostStep(Cast c) {
        Vec3 from = c.pos();
        Vec3 dir = c.look();
        boolean mirror = false;
        // Soul Trail (2), Further (4): two more blocks each
        double reach = 7 + c.num(2, "range", 0f) + c.num(4, "range", 0f);
        BlockHitResult wall = Combat.aimBlock(c, reach);
        if (wall != null && c.level.getBlockState(wall.getBlockPos()).is(MIRROR)) mirror = true;
        Vec3 to = mirror ? Moves.phaseTarget(c.player, dir, reach, 10) : Moves.phaseTarget(c.player, dir, reach, 2);
        if (to.distanceToSqr(from) < 1) return Result.fail("blocked");
        // never phase into someone else's Hearth
        if (!Constructs.mayChange(c.player, BlockPos.containing(to))) return Result.fail("blocked");
        for (LivingEntity e : Combat.alongLine(c.player, from.add(0, 1, 0), to.add(0, 1, 0), 0.8)) {
            c.hurt(e, c.dmg(), Forces.Force.SPIRIT);
            Moves.effect(e, MobEffects.SLOWNESS, 40, 1);
        }
        Moves.teleport(c.player, to);
        // a trail of wisps through the wall, a soul afterimage left behind
        Fx.at(c, FxKind.BLINK, from).to(to).send();
        if (c.step(5)) veilDoor(c, from, to);
        c.sound(from, PowerSounds.SPIRIT, 0.8f, 1.6f);
        if (mirror) {
            Fx.at(c, FxKind.IMPACT, to.add(0, 1, 0)).scale(1.8).style(FxStyle.PRISM).noAnim().send();
            c.sound(to, PowerSounds.SHATTER, 0.6f, 1.6f);
            ForceInteractions.count(c.player, ForceInteractions.Reaction.MIRROR_SHATTER);
        }
        return Result.OK;
    }

    /** Through the Veil (Ghost Step 5): the way stays open a few seconds, and a friend who steps in comes out where you did. */
    static void veilDoor(Cast c, Vec3 from, Vec3 to) {
        int ticks = (int) c.num(5, "door", 60);
        Fx.at(c, FxKind.VORTEX, from.add(0, 1, 0)).scale(0.9).ticks(ticks).plain().flag(FxEvent.QUIET).send();
        Set<UUID> through = new HashSet<>();
        Running r = Casting.start(c, c.type.id + ":door", ticks);
        r.tick(rr -> {
            if (rr.age % 2 != 0) return;
            for (ServerPlayer p : c.level.players()) {
                if (p == c.player || !p.isAlive() || p.isSpectator() || p.position().distanceToSqr(from) > 1.3 * 1.3) continue;
                if (!through.add(p.getUUID())) continue;
                Moves.teleport(p, to);
                Fx.at(c, FxKind.BLINK, from).to(to).caster(p).plain().send();
                c.sound(to, PowerSounds.SPIRIT, 0.7f, 1.7f);
            }
        });
    }

    static Result twinStrike(Cast c) {
        int ticks = c.ticks(160);
        // Stronger (3): the echo hits harder; Twin Souls (5): it repeats your power hits too (Combat.hurtBy)
        PowerState.of(c.player).buff(PowerState.Buffs.TWIN, c.now() + ticks, c.num(3, "share", 0.6f));
        if (c.step(5)) PowerState.of(c.player).buff(PowerState.Buffs.TWIN_CASTS, c.now() + ticks, c.num(5, "casts", 0.5f));
        c.sound(PowerSounds.SPIRIT, 1f, 0.6f);
        // the twin soul: blue echoes of you trailing half a step behind
        Fx.at(c, FxKind.AURA, c.pos()).aux(FxKind.Aura.TWIN.ordinal()).ticks(ticks).send();
        Running r = Casting.start(c, ticks);
        r.end((rr, why) -> {
            PowerState.of(c.player).clearBuff(PowerState.Buffs.TWIN);
            PowerState.of(c.player).clearBuff(PowerState.Buffs.TWIN_CASTS);
            Fx.end(c, c.player, FxKind.Aura.TWIN.ordinal() + 1);
        });
        return Result.OK;
    }

    /** The twin soul repeats a melee blow 10 ticks later (called by the damage hook). */
    public static void twinEcho(ServerPlayer player, LivingEntity victim, float amount, double share) {
        Scheduler.after(10, () -> {
            if (!victim.isAlive() || player.isRemoved()) return;
            Combat.hurtFrom(player, null, victim, (float) (amount * share), Forces.Force.SPIRIT, true);
            Vec3 at = victim.getBoundingBox().getCenter();
            Fx.at(player.level(), FxKind.ECHO, FxStyle.SOUL, at).caster(player).dir(at.subtract(player.position())).scale(1).send();
        });
    }

    /** Is this damage a plain melee hit by the player (what Twin Strike repeats)? */
    public static boolean isMelee(DamageSource src, ServerPlayer player) {
        return src.getDirectEntity() == player && src.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK);
    }
}
