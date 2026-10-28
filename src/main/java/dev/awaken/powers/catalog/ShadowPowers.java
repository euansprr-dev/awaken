package dev.awaken.powers.catalog;

import dev.awaken.powers.fx.FxEvent;
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

import java.util.List;

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
        PowerState st = PowerState.of(c.player);
        double backstab = c.is(1) ? 2.2 : 1.6;
        // Ink Wake (2) holds the backstab longer; Blinding (3) makes it blind
        long window = c.now() + 60 + (long) c.num(2, "window", 0);
        st.buff(PowerState.Buffs.BACKSTAB, window, backstab);
        if (c.step(3)) st.buff(PowerState.Buffs.BACKSTAB_BLIND, window, c.num(3, "blind", 20));
        if (c.is(2)) Moves.effect(c.player, MobEffects.INVISIBILITY, 40, 0);
        // vanish in ink, reappear behind them with a slash of shadow; the mark shows the backstab is ready
        Fx.at(c, FxKind.BLINK, from).to(to).send();
        if (t != null) Fx.at(c, FxKind.AURA, t.position()).caster(t).aux(FxKind.Aura.MARK.ordinal()).ticks(60).noAnim().send();
        c.sound(from, PowerSounds.SHADOW, 0.9f, 1f);
        c.sound(to, PowerSounds.SHADOW, 0.9f, 1.3f);
        if (c.step(5)) {
            // Night Cut: the crescent lands the backstab by itself; tendrils from the pool hold the foes round you
            if (t != null) {
                st.clearBuff(PowerState.Buffs.BACKSTAB);
                float cut = (float) (c.player.getAttributeValue(Attributes.ATTACK_DAMAGE) * backstab);
                c.hurt(t, Math.max(cut, c.dmg(4f)), null);
                if (st.has(PowerState.Buffs.BACKSTAB_BLIND, c.now())) {
                    Moves.effect(t, MobEffects.BLINDNESS, (int) st.buff(PowerState.Buffs.BACKSTAB_BLIND, c.now()), 0);
                    st.clearBuff(PowerState.Buffs.BACKSTAB_BLIND);
                }
            }
            for (LivingEntity e : Combat.foesAround(c.player, to, c.num(5, "radius", 3f))) {
                if (e == t) continue;
                Moves.effect(e, MobEffects.SLOWNESS, (int) c.num(5, "hold", 20), 6);
                Fx.at(c, FxKind.TETHER, to.add(0, 0.2, 0)).to(e.position().add(0, 0.3, 0)).ticks(12).plain().flag(FxEvent.QUIET).send();
            }
        }
        if (c.step(4)) {
            // Return: the ink pool stays where you left; press again within 2 s to step back to it
            Vec3 pool = from;
            c.followUp((int) c.num(4, "back", 40), () -> {
                Vec3 now = c.player.position();
                Moves.teleport(c.player, pool);
                Fx.at(c, FxKind.BLINK, now).to(pool).plain().send();
                c.sound(pool, PowerSounds.SHADOW, 0.9f, 0.8f);
            });
        }
        return Result.OK;
    }

    static Result cloak(Cast c) {
        int ticks = c.ticks(120);
        PowerState st = PowerState.of(c.player);
        st.buff(PowerState.Buffs.CLOAK, c.now() + ticks, c.is(2) ? 2 : 1);
        if (c.step(3)) st.buff(PowerState.Buffs.CLOAK_FREE, c.now() + ticks, 1);
        Moves.effect(c.player, MobEffects.INVISIBILITY, ticks, 0);
        for (Mob m : c.level.getEntitiesOfClass(Mob.class, c.player.getBoundingBox().inflate(32), m -> m.getTarget() == c.player)) m.setTarget(null);
        Moves.postOn(c.player, Moves.FX_CLOAK);
        Fx.at(c, FxKind.SMOKE, c.pos()).scale(1.4).send();
        Fx.at(c, FxKind.AURA, c.pos()).aux(FxKind.Aura.CLOAK.ordinal()).ticks(ticks).noAnim().send();
        c.sound(PowerSounds.CLOAK, 1f, 1f);
        Running r = Casting.start(c, ticks);
        // Nightfall (5): a dome of dusk round you where foes lose your allies too
        double dusk = c.num(5, "dusk", 0f);
        if (dusk > 0) Fx.at(c, FxKind.SMOKE, c.pos()).scale(dusk).noAnim().send();
        r.tick(rr -> {
            if (!st.has(PowerState.Buffs.CLOAK, c.now())) rr.stop(Running.Why.DONE);
            if (dusk > 0 && rr.age % 10 == 0) {
                for (Mob m : c.level.getEntitiesOfClass(Mob.class, c.player.getBoundingBox().inflate(dusk), m -> m.getTarget() != null)) {
                    LivingEntity t = m.getTarget();
                    if (t == c.player || t instanceof net.minecraft.world.entity.player.Player || Combat.isAlly(c.player, t)) m.setTarget(null);
                }
            }
        }).end((rr, why) -> {
            Fx.end(c, c.player, FxKind.Aura.CLOAK.ordinal() + 1);
            st.clearBuff(PowerState.Buffs.CLOAK);
            st.clearBuff(PowerState.Buffs.CLOAK_FREE);
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
        st.clearBuff(PowerState.Buffs.CLOAK_FREE);
        var cloak = dev.awaken.powers.PowerRegistry.get("cloak");
        if (cloak != null) {
            Cast mark = Cast.of(player, cloak);
            // Ambush Mark (4): the first strike leaves a crescent that bleeds
            if (mark.step(4)) {
                Fx.at(mark, FxKind.SMOKE, victim.position()).scale(0.9).noAnim().send();
                float dps = mark.num(4, "dps", 1.0f) * mark.dmg(1f);
                int bleed = (int) mark.num(4, "bleed", 60);
                for (int t = 10; t <= bleed; t += 10) {
                    dev.awaken.powers.Scheduler.after(t, () -> {
                        if (!victim.isAlive() || player.isRemoved()) return;
                        Combat.hurtFrom(player, null, victim, dps * 0.5f, Forces.Force.SPIRIT, true);
                        Fx.at(mark, FxKind.IMPACT, victim.getBoundingBox().getCenter()).scale(0.5).plain().flag(FxEvent.QUIET).send();
                    });
                }
            }
        }
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
        // Ink Line (2): it reaches further
        Entity other = nearestSummon(c, 32 + c.num(2, "range", 0));
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
        // Left Behind (5): a shadow copy stays where you were and draws them
        if (c.step(5)) {
            Vec3 side = c.flatLook().yRot((float) (Math.PI / 2)).scale(1.2);
            Moves.decoy(c, mine.add(side), (int) c.num(5, "copy", 60), 0, 0, 14, "echo_swap");
            Fx.at(c, FxKind.AFTERIMAGE, mine.add(side)).ticks(20).plain().send();
        }
        // Double Swap (4): press again to swap back, free
        if (c.step(4)) c.followUp((int) c.num(4, "back", 40), () -> swapBack(c, other));
        return Result.OK;
    }

    private static void swapBack(Cast c, Entity other) {
        if (!other.isAlive() || !c.player.isAlive() || other.level() != c.player.level()) return;
        Vec3 mine = c.pos();
        Vec3 theirs = other.position();
        other.teleportTo(mine.x, mine.y, mine.z);
        Moves.teleport(c.player, theirs);
        Fx.at(c, FxKind.BLINK, mine).to(theirs).noAnim().send();
        c.sound(theirs, PowerSounds.SHADOW, 0.9f, 1.4f);
    }

    static Result legion(Cast c) {
        int spi = Progression.stat(c.player, Stat.SPI);
        // Rim Light (2): one more soldier
        int count = (c.is(1) ? 2 : c.is(2) ? 8 : Math.min(8, 4 + Math.max(0, spi - 5) / 15)) + (int) c.num(2, "extra", 0);
        boolean captain = c.step(5);
        double hpMul = c.is(1) ? 2.5 : c.is(2) ? 0.45 : 1.0;
        float dmg = c.dmg();
        int life = c.ticks(400);
        double hp = 20 * PowerMath.levelScale(Progression.profile(c.player).level()) * 0.8 * hpMul;
        Moves.makeRoom(c.player, count + (captain ? 1 : 0));
        List<ShadowSoldier> risen = new java.util.ArrayList<>();
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
            risen.add(s);
            var rise = Fx.at(c, FxKind.SUMMON, spot).scale(c.is(1) ? 1.4 : 1);
            if (spawned > 0) rise.noAnim().range(48);
            rise.send();
            spawned++;
        }
        // The Risen Guard (5): a captain twice their size rises at the ring's heart, built to last the whole time
        if (captain) {
            ShadowSoldier k = PowerContent.SHADOW_SOLDIER.get().create(c.level, EntitySpawnReason.MOB_SUMMONED);
            if (k != null) {
                Vec3 at = c.pos().add(c.flatLook().scale(1.5));
                Vec3 spot = c.level.noCollision(k, k.getType().getDimensions().makeBoundingBox(at).inflate(0.5)) ? at : c.pos();
                k.snapTo(spot.x, spot.y, spot.z, c.yRot(), 0);
                k.setup(c.player, life, dmg * 1.5f);
                var maxHp = k.getAttribute(Attributes.MAX_HEALTH);
                if (maxHp != null) maxHp.setBaseValue(hp * 4);
                k.setHealth((float) hp * 4);
                var scale = k.getAttribute(Attributes.SCALE);
                if (scale != null) scale.setBaseValue(2.0);
                c.level.addFreshEntity(k);
                risen.add(k);
                Fx.at(c, FxKind.SUMMON, spot).scale(2.2).noAnim().send();
                spawned++;
            }
        }
        if (spawned == 0) return Result.fail("blocked");
        c.sound(PowerSounds.SUMMON, 1.3f, 0.7f);
        // Ink Death (4) reads the risen through this (soldierFell); Charge (3) sends them in with a lunge
        if (c.step(3) || c.step(4)) Casting.start(c, life).data = risen;
        if (c.step(3)) {
            for (ShadowSoldier s : risen) {
                LivingEntity foe = Combat.foesAround(c.player, s.position(), 14).stream()
                        .min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(s))).orElse(null);
                if (foe == null) continue;
                s.setTarget(foe);
                Vec3 to = foe.position().subtract(s.position());
                Combat.push(s, Moves.flat(to).normalize().scale(Math.min(1.4, 0.35 + to.length() * 0.12)).add(0, 0.25, 0));
                dev.awaken.powers.Scheduler.after(7, () -> {
                    if (!s.isAlive() || !foe.isAlive() || s.distanceToSqr(foe) > 3.5 * 3.5) return;
                    Moves.swing(s, net.minecraft.world.InteractionHand.MAIN_HAND);
                    Combat.hurtFrom(c.player, s, foe, dmg, Forces.Force.SPIRIT, true);
                    Fx.at(c, FxKind.IMPACT, foe.getBoundingBox().getCenter()).dir(to).scale(0.9).plain().send();
                });
            }
        }
        return Result.OK;
    }

    /** Ink Death (4): a fallen soldier spills a pool of ink that slows foes. */
    public static void soldierFell(ShadowSoldier s) {
        if (!(s.getOwner() instanceof net.minecraft.server.level.ServerPlayer owner)) return;
        PowerState st = PowerState.peek(owner);
        Running r = st == null ? null : st.running("awaken:legion");
        if (r == null || r.c.powerLevel < 4 || !(r.data instanceof List<?> risen) || !risen.contains(s)) return;
        dev.awaken.powers.ladder.Riders.lingerAt(r.c, s.position(), "slow", (int) r.c.num(4, "ink", 60), 2.5f, 0);
        Fx.at(r.c, FxKind.SMOKE, s.position()).scale(1.6).noAnim().send();
    }
}
