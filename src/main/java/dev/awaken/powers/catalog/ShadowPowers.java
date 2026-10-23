package dev.awaken.powers.catalog;

import dev.awaken.powers.fx.FxKind;

import dev.awaken.core.Forces;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobRole;
import dev.awaken.powers.Allies;
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
import dev.awaken.powers.content.PowerContent;
import dev.awaken.powers.content.PowerSummon;
import dev.awaken.powers.content.ShadowSoldier;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.progression.Stat;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Shadow: the shadow army fantasy, in its own words. Scales with SPI. */
public final class ShadowPowers {
    private ShadowPowers() {}

    static final int SHADE = 0x8F6BFF;

    static void register() {
        PowerType.builder("shadow_step", "Shadow Step", Family.SHADOW, Slot.ACTIVE)
                .cost(15).cooldown(120).unlock(Rank.E).icon("shadow_step")
                .desc("Step through the shadows to just behind your target. Your next hit within 3 s lands as a backstab (x1.6).")
                .variant(Variant.of("Assassin", "Backstabs hit x2.2, a little longer to recover").cd(1.3f))
                .variant(Variant.of("Phantom", "You stay unseen for 2 s after the step"))
                .cast(ShadowPowers::shadowStep).register();

        PowerType.builder("cloak", "Cloak", Family.SHADOW, Slot.ACTIVE)
                .cost(25).cooldown(400).damage(5).unlock(Rank.D).icon("cloak")
                .desc("Vanish for 6 s. Enemies lose you and can't find you unless you strike; the first strike from the dark hits x1.8.")
                .variant(Variant.of("Long", "Vanish for 10 s").dur(1.67f))
                .variant(Variant.of("Ambush", "Striking from the cloak bursts shadow around your target"))
                .cast(ShadowPowers::cloak).register();

        PowerType.builder("echo_swap", "Echo Swap", Family.SHADOW, Slot.ACTIVE)
                .forces(Forces.Force.SPIRIT).cost(18).cooldown(160).damage(5).unlock(Rank.C).icon("swap")
                .desc("Trade places with one of your summons, decoys or Echoes. Enemies around where you appear are struck and blinded.")
                .variant(Variant.of("Shroud", "Both places fill with blinding smoke"))
                .variant(Variant.of("Relay", "No strike, but back twice as fast").cd(0.5f))
                .cast(ShadowPowers::echoSwap).register();

        PowerType.builder("legion", "Legion", Family.SHADOW, Slot.ULTIMATE)
                .forces(Forces.Force.SPIRIT).cost(60).cooldown(1200).damage(3.5f).unlock(Rank.C).hidden().icon("legion")
                .desc("Shadowbinder's signature. Raise shadow soldiers from your own shadow (4, plus 1 per 15 SPI) to fight beside you for 20 s.")
                .variant(Variant.of("Knights", "Two great knights, far tougher").dmg(1.8f))
                .variant(Variant.of("Horde", "Eight frail shades").dmg(0.45f))
                .cast(ShadowPowers::legion).register();
    }

    // ---- behaviours -------------------------------------------------------------------------------------------------

    /** Inky smoke with violet glints, tendrils and a shadow pool. */
    static void shadowPuff(Cast c, Vec3 at) {
        Fx.at(c, FxKind.SMOKE, at).scale(1).noAnim().send();
    }

    static Result shadowStep(Cast c) {
        LivingEntity t = Combat.aimEntity(c, 16, 1.0);
        Vec3 from = c.pos();
        Vec3 to;
        if (t != null) {
            Vec3 back = Moves.flat(t.getLookAngle()).scale(-(t.getBbWidth() * 0.5 + 0.9));
            to = t.position().add(back);
            if (!c.level.noCollision(c.player, c.player.getBoundingBox().move(to.subtract(from)))) {
                to = Moves.blinkTarget(c.player, t.position().subtract(from), from.distanceTo(t.position()) - 1.0);
            }
            Moves.teleportFacing(c.player, to, t.getEyePosition());
        } else {
            to = Moves.blinkTarget(c.player, c.look(), 8);
            if (to.distanceToSqr(from) < 1) return Result.fail("blocked");
            Moves.teleport(c.player, to);
        }
        PowerState.of(c.player).buff(PowerState.Buffs.BACKSTAB, c.now() + 60, c.is(1) ? 2.2 : 1.6);
        if (c.is(2)) Moves.effect(c.player, MobEffects.INVISIBILITY, 40, 0);
        // vanish in ink, reappear behind them with a slash of shadow; the mark shows the backstab is ready
        Fx.at(c, FxKind.BLINK, from).to(to).send();
        if (t != null) Fx.at(c, FxKind.AURA, t.position()).caster(t).aux(FxKind.Aura.MARK.ordinal()).ticks(60).noAnim().send();
        c.sound(from, PowerSounds.SHADOW, 0.9f, 1f);
        c.sound(to, PowerSounds.SHADOW, 0.9f, 1.3f);
        return Result.OK;
    }

    static Result cloak(Cast c) {
        int ticks = c.ticks(120);
        PowerState st = PowerState.of(c.player);
        st.buff(PowerState.Buffs.CLOAK, c.now() + ticks, c.is(2) ? 2 : 1);
        Moves.effect(c.player, MobEffects.INVISIBILITY, ticks, 0);
        for (Mob m : c.level.getEntitiesOfClass(Mob.class, c.player.getBoundingBox().inflate(32), m -> m.getTarget() == c.player)) m.setTarget(null);
        Moves.postOn(c.player, Moves.FX_CLOAK);
        Fx.at(c, FxKind.SMOKE, c.pos()).scale(1.4).send();
        Fx.at(c, FxKind.AURA, c.pos()).aux(FxKind.Aura.CLOAK.ordinal()).ticks(ticks).noAnim().send();
        c.sound(PowerSounds.CLOAK, 1f, 1f);
        Running r = Casting.start(c, ticks);
        r.tick(rr -> {
            if (!st.has(PowerState.Buffs.CLOAK, c.now())) rr.stop(Running.Why.DONE);
        }).end((rr, why) -> {
            Fx.end(c, c.player, FxKind.Aura.CLOAK.ordinal() + 1);
            st.clearBuff(PowerState.Buffs.CLOAK);
            c.player.removeEffect(MobEffects.INVISIBILITY);
            Moves.postOff(c.player, Moves.FX_CLOAK);
            shadowPuff(c, c.pos());
        });
        return Result.OK;
    }

    /** Called from the damage hook when a cloaked player strikes: ends the cloak (Ambush bursts). */
    public static void breakCloak(net.minecraft.server.level.ServerPlayer player, LivingEntity victim, double mode) {
        PowerState st = PowerState.of(player);
        st.clearBuff(PowerState.Buffs.CLOAK);
        if (mode >= 2) {
            var type = dev.awaken.powers.PowerRegistry.get("cloak");
            if (type != null) {
                Cast c = Cast.of(player, type);
                Moves.blast(c, victim.position().add(0, 1, 0), 3, c.dmg(), Forces.Force.SPIRIT, 0.6, 0.2);
                Fx.at(c, FxKind.SMOKE, victim.position()).scale(1.6).noAnim().send();
                Fx.at(c, FxKind.RING, victim.position()).scale(3).ticks(10).flag(dev.awaken.powers.fx.FxEvent.REACH).noAnim().send();
                Fx.at(c, FxKind.IMPACT, victim.getBoundingBox().getCenter()).scale(1.6).noAnim().send();
            }
        }
    }

    static @Nullable Entity nearestSummon(Cast c, double range) {
        Entity best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3 look = c.look();
        AABB box = c.player.getBoundingBox().inflate(range);
        for (Entity e : c.level.getEntities(c.player, box, x -> isMine(c, x))) {
            Vec3 to = e.position().subtract(c.pos());
            double d = to.length();
            if (d > range || d < 1) continue;
            double score = d * (1.6 - Math.max(0, to.normalize().dot(look))); // prefer what you're looking at
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    static boolean isMine(Cast c, Entity e) {
        if (!e.isAlive()) return false;
        if (e instanceof PowerSummon s) return s.getOwner() == c.player;
        var ally = Allies.get(e);
        if (ally != null && ally.owner().equals(c.player.getUUID()) && ally.kind() == Allies.Kind.DECOY) return true;
        var role = e.getExistingData(CoreApi.MOB_ROLE.get());
        return role.isPresent() && role.get().role() == MobRole.Role.ECHO && e.distanceToSqr(c.player) < 32 * 32;
    }

    static Result echoSwap(Cast c) {
        Entity other = nearestSummon(c, 32);
        if (other == null) return Result.fail("no_summon");
        Vec3 mine = c.pos();
        Vec3 theirs = other.position();
        other.teleportTo(mine.x, mine.y, mine.z);
        Moves.teleport(c.player, theirs);
        Fx.at(c, FxKind.BLINK, mine).to(theirs).send();
        c.sound(mine, PowerSounds.SHADOW, 0.8f, 0.8f);
        c.sound(theirs, PowerSounds.SHADOW, 1f, 1.2f);
        if (!c.is(2)) {
            Fx.at(c, FxKind.RING, theirs).scale(3.5).ticks(10).flag(dev.awaken.powers.fx.FxEvent.REACH).noAnim().send();
            for (LivingEntity e : Combat.foesAround(c.player, theirs.add(0, 1, 0), 3.5)) {
                c.hurt(e, c.dmg(), Forces.Force.SPIRIT);
                Moves.effect(e, MobEffects.BLINDNESS, 40, 0);
            }
        }
        if (c.is(1)) {
            for (Vec3 p : new Vec3[]{mine, theirs}) {
                Fx.at(c, FxKind.SMOKE, p).scale(2.6).noAnim().send();
                for (LivingEntity e : Combat.foesAround(c.player, p, 4)) {
                    Moves.effect(e, MobEffects.BLINDNESS, 60, 0);
                    Moves.effect(e, MobEffects.SLOWNESS, 60, 1);
                }
            }
        }
        return Result.OK;
    }

    static Result legion(Cast c) {
        int spi = Progression.stat(c.player, Stat.SPI);
        int count = c.is(1) ? 2 : c.is(2) ? 8 : Math.min(8, 4 + Math.max(0, spi - 5) / 15);
        double hpMul = c.is(1) ? 2.5 : c.is(2) ? 0.45 : 1.0;
        float dmg = c.dmg();
        int life = c.ticks(400);
        double hp = 20 * PowerMath.levelScale(Progression.profile(c.player).level()) * 0.8 * hpMul;
        Moves.makeRoom(c.player, count);
        // a circle of shadow opens under you and the soldiers climb out of it
        Fx.at(c, FxKind.GLYPH, c.pos()).scale(3.2).ticks(60).send();
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2 * i / count + c.yRot() * Math.PI / 180;
            Vec3 at = c.pos().add(Math.cos(a) * 2.2, 0, Math.sin(a) * 2.2);
            ShadowSoldier s = PowerContent.SHADOW_SOLDIER.get().create(c.level, EntitySpawnReason.MOB_SUMMONED);
            if (s == null) continue;
            Vec3 spot = c.level.noCollision(s, s.getType().getDimensions().makeBoundingBox(at)) ? at : c.pos();
            s.snapTo(spot.x, spot.y, spot.z, c.yRot(), 0);
            s.setup(c.player, life, dmg);
            var maxHp = s.getAttribute(Attributes.MAX_HEALTH);
            if (maxHp != null) maxHp.setBaseValue(hp);
            s.setHealth((float) hp);
            if (c.is(1)) {
                var scale = s.getAttribute(Attributes.SCALE);
                if (scale != null) scale.setBaseValue(1.3);
            }
            if (c.is(2)) {
                var scale = s.getAttribute(Attributes.SCALE);
                if (scale != null) scale.setBaseValue(0.8);
            }
            c.level.addFreshEntity(s);
            var rise = Fx.at(c, FxKind.SUMMON, spot).scale(c.is(1) ? 1.4 : 1);
            if (spawned > 0) rise.noAnim().range(48);
            rise.send();
            spawned++;
        }
        if (spawned == 0) return Result.fail("blocked");
        c.sound(PowerSounds.SUMMON, 1.3f, 0.7f);
        return Result.OK;
    }
}
