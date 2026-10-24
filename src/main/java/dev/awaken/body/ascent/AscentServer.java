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
        AscentMemory mem = p.getData(AscentContent.MEMORY);
        AwakenNet.send(p, new AscentPayloads.Tiers(mem.tiers(), mem.packedLevels()));
    }

    /** The client heard its tiers (Ascent.has on the client reads them). */
    public static void clientTiers(int bits) {
        Ascent.clientTiers = bits;
    }

    // ---- chains, style XP and mastery ------------------------------------------------------------------------------

    /** Links further apart than this end the chain. */
    static final int CHAIN_GAP = 50;
    /** Style XP per link of a finished chain (×xpToNext), the most links paid, and the most paid a minute (×xpToNext). */
    static final double STYLE_PER_LINK = 0.0012, STYLE_CAP_MINUTE = 0.015;
    static final int STYLE_MAX_LINKS = 25;
    /** A new longest chain is told to the player from this many links (shorter ones are just the start). */
    static final int RECORD_FROM = 6;

    /**
     * A move went off (a rope's bite, a launch, wings, a boom, an air move, a wall-run, a wall jump, a ledge grab, a
     * slide): mastery points on its track, and a link in the chain if it came soon enough after the last one.
     */
    public static void link(ServerPlayer p, AscentMemory.Track track, int points) {
        AscentMotion m = motion(p);
        long now = now(p);
        if (m.chain > 0 && now - m.lastLinkAt > CHAIN_GAP) payChain(p, m, now);
        m.chain++;
        m.lastLinkAt = now;
        AscentMemory before = p.getData(AscentContent.MEMORY);
        AscentMemory after = before.withPoints(track, points);
        p.setData(AscentContent.MEMORY, after);
        if (after.level(track) != before.level(track)) {
            syncTiers(p);
            dev.awaken.core.DirectorLog.log(p, "ascent", track + " mastery " + after.level(track));
        }
    }

    /** A chain ended: style XP for its links (three or more), within the minute's cap; the record if it's the longest. */
    static void payChain(ServerPlayer p, AscentMotion m, long now) {
        int chain = m.chain;
        m.chain = 0;
        if (chain < 3) return;
        AscentMemory mem = p.getData(AscentContent.MEMORY);
        if (chain > mem.chainBest() && chain >= RECORD_FROM) AwakenNet.send(p, new AscentPayloads.Record(chain));
        p.setData(AscentContent.MEMORY, mem.withChain(chain));
        // style is paid once awake (before that the body is learning, not performing)
        if (!dev.awaken.progression.Progression.profile(p).awakened()) return;
        double toNext = dev.awaken.progression.Curve.xpToNext(dev.awaken.progression.Progression.profile(p).level());
        if (now - m.styleMinuteAt >= 1200) {
            m.styleMinuteAt = now;
            m.stylePaid = 0;
        }
        double want = toNext * STYLE_PER_LINK * Math.min(chain, STYLE_MAX_LINKS);
        double room = toNext * STYLE_CAP_MINUTE - m.stylePaid;
        double xp = Math.min(want, Math.max(0, room));
        if (xp <= 0) return;
        m.stylePaid += xp;
        dev.awaken.progression.Progression.grantXp(p, xp, dev.awaken.progression.Progression.XpSource.WORLD);
        dev.awaken.progression.Progression.count(p, "awaken:ascent_chains");
    }

    static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        int given = settle(p);
        syncTiers(p);
        if (given != 0) {
            // a moment after arriving (the client plays it once the player is free: no screen, on the ground)
            dev.awaken.core.Later.run(p.level().getServer(), 60, () -> {
                if (!p.hasDisconnected()) AwakenNet.send(p, new AscentPayloads.Remember(given, AscentPayloads.REMEMBER));
            });
        }
    }

    /** The tier an old world's player is given back on first load: Wings from rank B, Skyborn from A; none before. */
    static Ascent.Tier remembered(boolean awakened, dev.awaken.progression.Rank rank) {
        if (!awakened) return null;
        if (rank.ordinal() >= dev.awaken.progression.Rank.A.ordinal()) return Ascent.Tier.SKYBORN;
        return rank.ordinal() >= dev.awaken.progression.Rank.B.ordinal() ? Ascent.Tier.WINGS : null;
    }

    /**
     * The first load in a world since the Ascent (once per player): an awakened player of rank B or above is given
     * Wings (and the tiers under it), A or above Skyborn (SPEC §17.6). Returns the tier bits given (0 for none).
     */
    public static int settle(ServerPlayer p) {
        AscentMemory mem = p.getData(AscentContent.MEMORY);
        if ((mem.flags() & AscentMemory.SETTLED) != 0) return 0;
        p.setData(AscentContent.MEMORY, mem.withFlag(AscentMemory.SETTLED));
        var prof = dev.awaken.progression.Progression.profile(p);
        Ascent.Tier tier = remembered(prof.awakened(), prof.rank());
        if (tier == null) return 0;
        int before = tiers(p);
        Ascent.grant(p, tier, "your body remembers");
        int given = tiers(p) & ~before;
        if (given != 0) dev.awaken.core.DirectorLog.log(p, "ascent", "your body remembers: " + tier + " (rank " + prof.rank().label + ")");
        return given;
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

    /** Aether's overdraft the server allows before it folds a flight (the client's sun and updraft can differ a little). */
    static final double AETHER_TOLERANCE = 0.15;

    /**
     * The server's Aether: drained at the cheapest rate while flying, refilled as the client's is. A flight past empty
     * by more than the tolerance is folded back to a glide (the client folds its own at empty first). Time aloft is
     * Skyborn mastery (a point every 5 s).
     */
    static void aether(ServerPlayer p, AscentMotion m) {
        if (!Ascent.has(p, Ascent.Tier.SKYBORN)) return;
        boolean flying = m.state == AscentState.SOAR;
        net.minecraft.core.BlockPos eye = net.minecraft.core.BlockPos.containing(p.getEyePosition());
        boolean sun = p.level().isBrightOutside() && p.level().canSeeSky(eye);
        boolean updraft = Updrafts.liftAt(p.level(), p.getX(), p.getY(), p.getZ()) > 0;
        int lvl = p.getData(AscentContent.MEMORY).level(AscentMemory.Track.SKYBORN);
        m.aether = AscentRules.aetherStep(m.aether, flying, AscentRules.AETHER_CRUISE, sun, updraft, lvl);
        if (flying && m.aether < -AETHER_TOLERANCE) {
            AwakenNet.send(p, new AscentPayloads.Stop(AscentState.SOAR.ordinal()));
            m.state = AscentState.GLIDE;
            m.aether = 0;
            show(p, new AscentPayloads.Seen(p.getId(), AscentState.GLIDE.ordinal(), 0, 0, 0));
        }
        if (flying && ++m.aloft % 100 == 0) link(p, AscentMemory.Track.SKYBORN, 1);
    }

    /** States refused since the server started (tests and the bench read it). */
    static int refusals;

    static void onState(ServerPlayer p, AscentPayloads.State msg) {
        AscentState s = AscentState.byId(msg.state());
        if (s == null) return;
        AscentMotion m = motion(p);
        boolean refuse = !allowed(p, s) || p.isPassenger() || p.isSpectator() || !dev.awaken.body.BodyConfig.ascent()
                || s.rope && !anchorReal(p, msg.x(), msg.y(), msg.z())
                || s == AscentState.SOAR && m.state != AscentState.SOAR && m.aether < AscentRules.AETHER_START - AETHER_TOLERANCE;
        if (refuse && s != AscentState.NONE) {
            refusals++;
            AwakenNet.send(p, new AscentPayloads.Stop(s.ordinal()));
            dev.awaken.Awaken.LOG.debug("[ascent] refused {} for {}", s, p.getPlainTextName());
            s = AscentState.NONE;
        }
        if (s == AscentState.NONE && m.state != AscentState.NONE) m.endedAt = now(p);
        // a rope that bit is a link (counted here, once the server has checked its anchor)
        boolean newRope = s.rope && !m.state.rope;
        m.state = s;
        if (newRope) link(p, AscentMemory.Track.TETHER, 2);
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
     * A rope's anchor is real: within the rope's longest reach (mastery 10; plus the ground the body covered while the
     * packet travelled) of
     * where the server has the body, and the point sits on a block's collision (it is placed a hair off the face it bit).
     */
    static boolean anchorReal(ServerPlayer p, double ax, double ay, double az) {
        double reach = AscentRules.TETHER_RANGE + AscentRules.MASTERY_REACH * 10 + 6;
        if (p.distanceToSqr(ax, ay, az) > reach * reach) return false;
        net.minecraft.world.phys.AABB probe = new net.minecraft.world.phys.AABB(ax - 0.12, ay - 0.12, az - 0.12, ax + 0.12, ay + 0.12, az + 0.12);
        for (var shape : p.level().getBlockCollisions(null, probe)) {
            if (!shape.isEmpty()) return true;
        }
        return false;
    }

    static void onEvent(ServerPlayer p, AscentPayloads.Event msg) {
        if (msg.what() == AscentPayloads.EV_SLAM_LAND) slam(p, Math.min(3F, Math.max(0F, msg.speed())));
        // the moves that count as links, each only with the tier that has it and from a state it can come from (a
        // rope's is counted when its state lands)
        AscentState st = motion(p).state;
        switch (msg.what()) {
            case AscentPayloads.EV_LAUNCH -> {
                if (st == AscentState.TETHER) link(p, AscentMemory.Track.TETHER, 3);
            }
            case AscentPayloads.EV_DEPLOY -> {
                if (Ascent.has(p, Ascent.Tier.WINGS)) link(p, AscentMemory.Track.WINGS, 2);
            }
            case AscentPayloads.EV_BOOM -> {
                if (Ascent.has(p, Ascent.Tier.WINGS) && st.wings) link(p, AscentMemory.Track.WINGS, 6);
            }
            case AscentPayloads.EV_DOUBLE, AscentPayloads.EV_DASH, AscentPayloads.EV_DIVE -> {
                if (Ascent.has(p, Ascent.Tier.LEAPER)) link(p, AscentMemory.Track.LEAPER, 2);
            }
            case AscentPayloads.EV_SLAM_LAND -> {
                if (Ascent.has(p, Ascent.Tier.LEAPER)) link(p, AscentMemory.Track.LEAPER, 3);
            }
            case AscentPayloads.EV_FLAP -> {
                if (Ascent.has(p, Ascent.Tier.SKYBORN)) link(p, AscentMemory.Track.SKYBORN, 1);
            }
            case AscentPayloads.EV_ROLL -> {
                if (Ascent.has(p, Ascent.Tier.SKYBORN) && st == AscentState.SOAR) {
                    motion(p).aether -= AscentRules.ROLL_AETHER;
                    link(p, AscentMemory.Track.SKYBORN, 2);
                }
            }
            default -> {
            }
        }
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
        TetherVow.tick(p, now(p));
        aether(p, m);
        // the trail behind Ascent.arrivedByMovement: was this tick spent moving by rope, wings or Runner move
        boolean moving = Ascent.moving(p, m);
        m.mark(moving);
        // the Wilds' "how you arrived" hears the ladder's states (a swing, a leap, a glide, flight, a wall-run, a hang)
        if (moving) dev.awaken.core.api.ArrivalHooks.report(p, dev.awaken.core.api.ArrivalHooks.Way.ASCENT, 5);
        if (m.chain > 0 && now(p) - m.lastLinkAt > CHAIN_GAP) payChain(p, m, now(p));
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
