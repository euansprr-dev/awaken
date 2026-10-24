package dev.awaken.body.ascent;

import dev.awaken.core.AwakenNet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The server's half of the Ascent (Phase 0, the feel spike): it takes the client's predicted state, keeps a body in
 * an airborne state from fall damage and from the floating kick (vanilla kicks a player who hovers over air on a
 * server without allow-flight; {@code resetFlyingTicks} each tick keeps the count at 0 without granting mayFly,
 * which would hand the client creative flight on a double jump), and shows the state to everyone watching.
 * Phase 1 adds the checks: anchors that are really there, speed caps per tier, history-based position checks.
 */
public final class AscentServer {
    private AscentServer() {}

    static final double SEEN_RANGE = 160;
    /** A landing this many ticks after an airborne state ended is still part of it (the packets can cross). */
    static final int SOFT_LANDING = 15;

    static long now(Player p) {
        return p.level().getServer().overworld().getGameTime();
    }

    static AscentMotion motion(ServerPlayer p) {
        return p.getData(AscentContent.MOTION);
    }

    public static int tiers(Player p) {
        return p.getData(AscentContent.MEMORY).tiers();
    }

    public static void setTiers(ServerPlayer p, int bits) {
        p.setData(AscentContent.MEMORY, p.getData(AscentContent.MEMORY).withTiers(bits));
        syncTiers(p);
    }

    static void syncTiers(ServerPlayer p) {
        AwakenNet.send(p, new AscentPayloads.Tiers(tiers(p)));
    }

    /** The client heard its tiers (Ascent.has on the client reads them). */
    public static void clientTiers(int bits) {
        Ascent.clientTiers = bits;
    }

    static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) syncTiers(p);
    }

    static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) syncTiers(p);
    }

    static void onDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            syncTiers(p);
            AscentMotion m = p.getExistingDataOrNull(AscentContent.MOTION);
            if (m != null) end(p, m);
        }
    }

    /** Whether the tiers this player holds allow a state. */
    static boolean allowed(ServerPlayer p, AscentState s) {
        int t = tiers(p);
        return switch (s) {
            case NONE, CARRY -> true;
            case TETHER, ZIP -> AscentPayloads.Tier.TETHER.in(t);
            case GLIDE -> AscentPayloads.Tier.WINGS.in(t);
            case SOAR -> AscentPayloads.Tier.SKYBORN.in(t);
        };
    }

    static void onState(ServerPlayer p, AscentPayloads.State msg) {
        AscentState s = AscentState.byId(msg.state());
        if (s == null) return;
        AscentMotion m = motion(p);
        boolean refuse = !allowed(p, s) || p.isPassenger() || p.isSpectator() || !dev.awaken.body.BodyConfig.ascent()
                || s.rope && !anchorReal(p, msg.x(), msg.y(), msg.z());
        if (refuse && s != AscentState.NONE) {
            AwakenNet.send(p, new AscentPayloads.Stop(s.ordinal()));
            dev.awaken.Awaken.LOG.debug("[ascent] refused {} for {}", s, p.getPlainTextName());
            s = AscentState.NONE;
        }
        if (s == AscentState.NONE && m.state != AscentState.NONE) m.endedAt = now(p);
        m.state = s;
        m.ax = msg.x();
        m.ay = msg.y();
        m.az = msg.z();
        m.len = msg.len();
        m.since = now(p);
        m.groundTicks = 0;
        if (s.airborne()) p.resetFallDistance();
        show(p, new AscentPayloads.Seen(p.getId(), s.ordinal(), m.ax, m.ay, m.az));
    }

    /**
     * A rope's anchor is real: within the rope's reach (plus the ground the body covered while the packet travelled) of
     * where the server has the body, and the point sits on a block's collision (it is placed a hair off the face it bit).
     */
    static boolean anchorReal(ServerPlayer p, double ax, double ay, double az) {
        double reach = AscentRules.TETHER_RANGE + 6;
        if (p.distanceToSqr(ax, ay, az) > reach * reach) return false;
        net.minecraft.world.phys.AABB probe = new net.minecraft.world.phys.AABB(ax - 0.12, ay - 0.12, az - 0.12, ax + 0.12, ay + 0.12, az + 0.12);
        for (var shape : p.level().getBlockCollisions(null, probe)) {
            if (!shape.isEmpty()) return true;
        }
        return false;
    }

    static void onEvent(ServerPlayer p, AscentPayloads.Event msg) {
        if (msg.what() == AscentPayloads.EV_SLAM_LAND) slam(p, Math.min(3F, Math.max(0F, msg.speed())));
        show(p, new AscentPayloads.SeenEvent(p.getId(), msg.what(), msg.speed()));
    }

    /**
     * A dive slam lands: creatures within reach are hurt by how hard you came down and thrown out from you. Only with
     * the Leaper tier and out of an airborne state a moment ago; players only where the world allows PvP.
     */
    static void slam(ServerPlayer p, float speed) {
        AscentMotion m = motion(p);
        if (!Ascent.has(p, Ascent.Tier.LEAPER) || !(m.state.airborne() || now(p) - m.endedAt < SOFT_LANDING)) return;
        ServerLevel level = p.level();
        double r = AscentRules.SLAM_RADIUS;
        float damage = 3F + 3F * speed;
        for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, p.getBoundingBox().inflate(r, 1.5, r), e -> e != p && e.isAlive())) {
            if (e instanceof net.minecraft.world.entity.player.Player other && (!level.isPvpAllowed() || other.isSpectator())) continue;
            if (e instanceof net.minecraft.world.entity.OwnableEntity own && p.getUUID().equals(own.getOwnerReference() == null ? null : own.getOwnerReference().getUUID())) continue;
            double dx = e.getX() - p.getX(), dz = e.getZ() - p.getZ(), d = Math.sqrt(dx * dx + dz * dz);
            if (d > r) continue;
            float falloff = (float) (1 - d / r * 0.6);
            var source = p.damageSources().playerAttack(p);
            e.hurtServer(level, source, damage * falloff);
            if (d > 1e-3) e.knockback(0.6 + 0.5 * speed * falloff, -dx / d, -dz / d, source, damage * falloff);
        }
    }

    static void end(ServerPlayer p, AscentMotion m) {
        if (m.state == AscentState.NONE) return;
        m.state = AscentState.NONE;
        m.endedAt = now(p);
        show(p, new AscentPayloads.Seen(p.getId(), AscentState.NONE.ordinal(), 0, 0, 0));
    }

    /** Everyone near this player but the player (whose own client predicted it). */
    static void show(ServerPlayer p, net.minecraft.network.protocol.common.custom.CustomPacketPayload msg) {
        for (ServerPlayer o : ((ServerLevel) p.level()).players()) {
            if (o != p && o.distanceToSqr(p) < SEEN_RANGE * SEEN_RANGE) AwakenNet.send(o, msg);
        }
    }

    static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        AscentMotion m = motion(p);
        // the trail behind Ascent.arrivedByMovement: was this tick spent moving by rope, wings or Runner move
        m.mark(Ascent.moving(p, m));
        if (!m.state.airborne()) return;
        if (p.isPassenger() || p.isSpectator() || !p.isAlive()) {
            end(p, m);
            return;
        }
        p.resetFallDistance();
        p.connection.resetFlyingTicks();
        // the client moves this body; the server's own physics between packets (gravity on a hover, a hang on a rope)
        // would sink its copy here and show everyone else a bob
        p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        // a body that has stood on the ground for a while is not in the air, whatever its client last said
        m.groundTicks = p.onGround() ? m.groundTicks + 1 : 0;
        if (m.groundTicks > 20) end(p, m);
    }

    /** A block placed: for 20 seconds nothing counts as arriving by movement (a tower is not a route). */
    static void onPlace(net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && !e.isCanceled()) motion(p).placedAt = now(p);
    }

    /** No fall damage out of an airborne state: chaining never hurts (SPEC §17; Phase 1 turns hard landings into rolls). */
    static void onFall(LivingFallEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        AscentMotion m = p.getExistingDataOrNull(AscentContent.MOTION);
        if (m == null) return;
        if (m.state.airborne() || now(p) - m.endedAt < SOFT_LANDING) {
            e.setDistance(0);
            e.setDamageMultiplier(0);
        }
    }
}
