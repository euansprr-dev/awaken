package dev.awaken.body.ascent;

import dev.awaken.core.AwakenNet;
import dev.awaken.core.DirectorLog;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Sovereign (SPEC §17.19), the top of the ladder: the god form, earned at the story's end (the world's ending seen:
 * "Break the Signal" or "Wake up"). The Ascend key, with the tier and the Aether full, takes the form for
 * {@link #FORM} ticks, once every {@link #COOLDOWN} (saved: {@code AscentMemory.sovereignAt}): flight that never tires,
 * every blow and power you land struck harder ({@link #AMPLIFY}), and the field round you slowed and fleeing. Ascend
 * again high up and the body falls as Judgement: the landing throws and strikes everything near. The server keeps the
 * form; the clients draw it (the gold of Lens, the pulled-back camera, the wings and the aura: SovereignClient).
 */
public final class Sovereign {
    private Sovereign() {}

    /** The form's length and its cooldown (ticks); how far the field round you reaches, and how much harder you strike. */
    public static final int FORM = 600, COOLDOWN = 12000;
    public static final double FIELD = 24, AMPLIFY = 1.5;
    /** The Judgement Fall: its reach and its blow (at a slam's landing speed of 1; scaled by the landing). */
    public static final double JUDGEMENT_RADIUS = 9, JUDGEMENT_DAMAGE = 20;
    /** Aether the form needs to begin (the meter full, give or take). */
    static final double AETHER_FULL = 0.95;

    private static final Map<UUID, Long> UNTIL = new ConcurrentHashMap<>();

    static long now(ServerPlayer p) {
        return AscentServer.now(p);
    }

    public static boolean active(ServerPlayer p) {
        Long u = UNTIL.get(p.getUUID());
        return u != null && now(p) < u;
    }

    /** Each player tick: the tier given at the story's end; while the form holds, the field round you, and its end. */
    static void tick(ServerPlayer p, long now) {
        if ((now + (p.getUUID().hashCode() & 127)) % 100 == 61 && !Ascent.has(p, Ascent.Tier.SOVEREIGN) && endingSeen(p)) give(p);
        Long u = UNTIL.get(p.getUUID());
        if (u == null) return;
        if (now >= u) {
            end(p);
            return;
        }
        AscentServer.motion(p).aether = 1;
        if (now % 10 == 0) field(p);
    }

    /** The world's ending, as this player has met it: the Signal broken (their gates calm) or the world awake. */
    static boolean endingSeen(ServerPlayer p) {
        var server = p.level().getServer();
        return dev.awaken.core.api.StoryHooks.gatesCalm(server, Optional.of(p.getUUID())) || dev.awaken.core.api.StoryHooks.colourRestored(server);
    }

    /** The tier, with its scene (once). */
    static void give(ServerPlayer p) {
        if (!Ascent.grant(p, Ascent.Tier.SOVEREIGN, "the story's end")) return;
        AwakenNet.send(p, new AscentPayloads.Remember(AscentPayloads.Tier.SOVEREIGN.bit(), AscentPayloads.GIFT_SOVEREIGN));
        AscentServer.show(p, new AscentPayloads.SeenEvent(p.getId(), AscentPayloads.EV_REMEMBER, 0));
        DirectorLog.log(p, "ascent", "the story's end: the Sovereign is theirs");
    }

    /** The Ascend key: the form, if the tier, the Aether and the hour allow it (the reason why not, as a toast). */
    static boolean ascend(ServerPlayer p) {
        if (!Ascent.has(p, Ascent.Tier.SOVEREIGN) || active(p)) return false;
        long now = now(p);
        AscentMemory mem = p.getData(AscentContent.MEMORY);
        long ready = mem.sovereignAt() == 0 ? 0 : mem.sovereignAt() + COOLDOWN;
        if (now < ready) {
            long min = Math.max(1, (ready - now + 1199) / 1200);
            Signal.toast(p, Component.translatable("awaken.ascent.sovereign.gathering", min));
            return false;
        }
        if (AscentServer.motion(p).aether < AETHER_FULL) {
            Signal.toast(p, Component.translatable("awaken.ascent.sovereign.aether"));
            return false;
        }
        UNTIL.put(p.getUUID(), now + FORM);
        p.setData(AscentContent.MEMORY, mem.withSovereignAt(now));
        var begun = new AscentPayloads.SeenEvent(p.getId(), AscentPayloads.EV_SOVEREIGN, FORM);
        AwakenNet.send(p, begun);
        AscentServer.show(p, begun);
        AscentServer.link(p, AscentMemory.Track.SOVEREIGN, 5);
        Progression.count(p, "awaken:ascent_sovereign_forms");
        DirectorLog.log(p, "ascent", "the Sovereign form taken");
        field(p);
        return true;
    }

    static void end(ServerPlayer p) {
        if (UNTIL.remove(p.getUUID()) == null) return;
        var ended = new AscentPayloads.SeenEvent(p.getId(), AscentPayloads.EV_SOVEREIGN_END, 0);
        AwakenNet.send(p, ended);
        AscentServer.show(p, ended);
    }

    static void forget(UUID id) {
        UNTIL.remove(id);
    }

    /** The field: every hostile near is slowed, loses you, and runs. */
    static void field(ServerPlayer p) {
        ServerLevel level = p.level();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(FIELD), x -> x instanceof Enemy && x.isAlive())) {
            if (e.distanceToSqr(p) > FIELD * FIELD) continue;
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2, false, false, false));
            if (e instanceof net.minecraft.world.entity.Mob m && m.getTarget() == p) m.setTarget(null);
            if (e instanceof PathfinderMob pm) {
                Vec3 away = e.position().subtract(p.position()).multiply(1, 0, 1);
                if (away.lengthSqr() < 1e-4) away = new Vec3(1, 0, 0);
                Vec3 to = e.position().add(away.normalize().scale(16));
                pm.getNavigation().moveTo(to.x, to.y, to.z, 1.3);
            }
        }
    }

    /** While the form holds, every hit you land lands harder (powers and blows alike). */
    static void onDamage(LivingIncomingDamageEvent e) {
        if (e.getSource().getEntity() instanceof ServerPlayer p && p != e.getEntity() && active(p)) e.setAmount((float) (e.getAmount() * AMPLIFY));
    }

    /**
     * A dive's landing in the form (AscentServer.slam): the Judgement Fall. Everything near is struck and thrown up and
     * away; others see the ground answer. False outside the form (the plain slam goes on).
     */
    static boolean judgement(ServerPlayer p, float speed) {
        if (!active(p)) return false;
        ServerLevel level = p.level();
        double r = JUDGEMENT_RADIUS;
        float force = Math.min(2.5F, Math.max(0.6F, speed));
        int hit = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r, 3, r), x -> x != p && x.isAlive())) {
            if (e instanceof net.minecraft.world.entity.player.Player other && (!level.isPvpAllowed() || other.isSpectator())) continue;
            if (e instanceof net.minecraft.world.entity.OwnableEntity own && p.getUUID().equals(own.getOwnerReference() == null ? null : own.getOwnerReference().getUUID())) continue;
            double dx = e.getX() - p.getX(), dz = e.getZ() - p.getZ(), d = Math.sqrt(dx * dx + dz * dz);
            if (d > r) continue;
            float falloff = (float) (1 - d / r * 0.5);
            // struck as itself (no amplify on top: the Judgement is already the form's blow), thrown up and away
            var source = p.damageSources().playerAttack(p);
            float damage = (float) (JUDGEMENT_DAMAGE * force * falloff / AMPLIFY);
            e.hurtServer(level, source, damage);
            if (d > 1e-3) e.knockback(1.2 * force * falloff, -dx / d, -dz / d, source, damage);
            e.push(0, 0.5 * falloff, 0);
            hit++;
        }
        AscentServer.show(p, new AscentPayloads.SeenEvent(p.getId(), AscentPayloads.EV_JUDGEMENT, force));
        AwakenNet.send(p, new AscentPayloads.SeenEvent(p.getId(), AscentPayloads.EV_JUDGEMENT, force));
        AscentServer.link(p, AscentMemory.Track.SOVEREIGN, 4);
        DirectorLog.log(p, "ascent", String.format("a Judgement Fall (force %.1f): %d struck", force, hit));
        return true;
    }

    /** Tests: the form's clock moved on. */
    static void expireForTests(ServerPlayer p) {
        UNTIL.computeIfPresent(p.getUUID(), (k, v) -> now(p));
    }
}
