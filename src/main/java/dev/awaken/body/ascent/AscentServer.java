package dev.awaken.body.ascent;

import dev.awaken.core.AwakenNet;
import net.minecraft.util.Mth;
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
        syncKit(p, false);
    }

    /** The fittings on this player's Tether, to its client ({@code fitted}: one was just fitted, so it's told). */
    static void syncKit(ServerPlayer p, boolean fitted) {
        AwakenNet.send(p, new AscentPayloads.Kit(kit(p).bits(), fitted));
    }

    static RopeKit kit(Player p) {
        return p.getData(AscentContent.ROPE_KIT);
    }

    /** The player's rope at its longest (mastery 10's reach for the anchor check: levels can land mid-swing). */
    static double reach(ServerPlayer p, int tetherLevel) {
        return AscentRules.reach(tetherLevel, dev.awaken.progression.Progression.perk(p, AscentRules.ROPE_REACH_PERK), kit(p).line());
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

    /** Skyborn mastery for a kill made in flight (SPEC §17.18: "Mastery: time aloft, aerial kills"). */
    static final int AERIAL_KILL = 3;

    /** A hostile killed by someone flying (soaring, gliding or carried, holding Skyborn): an aerial kill. */
    static void onKill(net.neoforged.neoforge.event.entity.living.LivingDeathEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || !(e.getEntity() instanceof net.minecraft.world.entity.monster.Enemy)) return;
        AscentState s = motion(p).state;
        if (!Ascent.has(p, Ascent.Tier.SKYBORN) || !(s == AscentState.SOAR || s == AscentState.GLIDE || s == AscentState.CARRY)) return;
        link(p, AscentMemory.Track.SKYBORN, AERIAL_KILL);
        dev.awaken.progression.Progression.count(p, "awaken:ascent_aerial_kills");
    }

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

    /** Mastery from outside a move (a dream's finish, SPEC §9.4): points on the track, no link in the chain. */
    public static void addMastery(ServerPlayer p, AscentMemory.Track track, int points) {
        AscentMemory before = p.getData(AscentContent.MEMORY);
        AscentMemory after = before.withPoints(track, points);
        p.setData(AscentContent.MEMORY, after);
        if (after.level(track) != before.level(track)) {
            syncTiers(p);
            dev.awaken.core.DirectorLog.log(p, "ascent", track + " mastery " + after.level(track) + " (from outside a move)");
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
            case TETHER, ZIP, RAPPEL -> AscentPayloads.Tier.TETHER.in(t);
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

    /** States refused since the server started (tests and the bench read it), and why the last one was. */
    static int refusals;
    static String lastRefusal = "";

    /** Why an anchor isn't real, in words (the log's; SPEC §62: a refusal used to say nothing). */
    private static String anchorWhy(ServerPlayer p, double ax, double ay, double az) {
        double reach = reach(p, 10) + 6;
        return p.distanceToSqr(ax, ay, az) > reach * reach
                ? String.format("the anchor is %.1f from where the server has the body (reach %.1f)", Math.sqrt(p.distanceToSqr(ax, ay, az)), reach)
                : String.format("no block's collision at the anchor (%.2f %.2f %.2f)", ax, ay, az);
    }

    static void onState(ServerPlayer p, AscentPayloads.State msg) {
        AscentState s = AscentState.byId(msg.state());
        if (s == null) return;
        AscentMotion m = motion(p);
        String why = !allowed(p, s) ? "the tier isn't held" : p.isPassenger() ? "riding" : p.isSpectator() ? "spectating"
                : !dev.awaken.body.BodyConfig.ascent() ? "the Ascent is off in this world"
                : s.rope && !anchorReal(p, msg.x(), msg.y(), msg.z()) && !(s == AscentState.ZIP && strikeAnchor(p, m, msg.x(), msg.y(), msg.z()))
                        ? anchorWhy(p, msg.x(), msg.y(), msg.z())
                : s == AscentState.SOAR && m.state != AscentState.SOAR && m.aether < AscentRules.AETHER_START - AETHER_TOLERANCE ? "too little Aether"
                : s == AscentState.RAPPEL && !rappelOk(p, m, msg) ? "the rappel's length" : null;
        if (why != null && s != AscentState.NONE) {
            refusals++;
            lastRefusal = why;
            AwakenNet.send(p, new AscentPayloads.Stop(s.ordinal()));
            dev.awaken.Awaken.LOG.info("[ascent] refused {} for {}: {}", s, p.getPlainTextName(), why);
            s = AscentState.NONE;
        }
        if (s == AscentState.NONE && m.state != AscentState.NONE) m.endedAt = now(p);
        // a rope that bit is a link (counted here, once the server has checked its anchor)
        boolean newRope = s.rope && !m.state.rope;
        boolean update = s == m.state;
        if (!update) {
            AscentTips.used(p, s);
            AscentQuests.state(p, m.state, s, m.ax, m.ay, m.az);
        }
        m.state = s;
        if (newRope) link(p, AscentMemory.Track.TETHER, 2);
        boolean anchored = Math.abs(m.ax - msg.x()) + Math.abs(m.ay - msg.y()) + Math.abs(m.az - msg.z()) < 0.05;
        m.ax = msg.x();
        m.ay = msg.y();
        m.az = msg.z();
        m.len = msg.len();
        m.lenAt = now(p);
        m.lenAtMs = net.minecraft.util.Util.getMillis();
        if (!update) m.since = now(p);
        m.groundTicks = 0;
        if (s.airborne()) p.resetFallDistance();
        // a rappel's length heard again (the rope paying out, same anchor) changes nothing anyone else draws
        if (update && anchored && s == AscentState.RAPPEL) return;
        show(p, new AscentPayloads.Seen(p.getId(), s.ordinal(), m.ax, m.ay, m.az, s.rope ? kit(p).bits() : 0));
    }

    /**
     * A rappel's checks (SPEC §28.5), on each length the client sends while it pays out: the rope no longer than the
     * player's real reach (and a block), paid out no faster than the fitted line allows (a bank refilled at its rate,
     * which lag bursts draw on), and the hand within the rope's length of the anchor. Draws on the bank when it holds.
     */
    static boolean rappelOk(ServerPlayer p, AscentMotion m, AscentPayloads.State msg) {
        double len = msg.len();
        int level = p.getData(AscentContent.MEMORY).level(AscentMemory.Track.TETHER);
        if (!(len <= reach(p, level) + AscentRules.RAPPEL_LENGTH_TOLERANCE) || len < AscentRules.TETHER_MIN - 0.5) return false;
        double hx = p.getX() - msg.x(), hy = p.getY() + AscentRules.HAND_HEIGHT - msg.y(), hz = p.getZ() - msg.z();
        if (Math.sqrt(hx * hx + hy * hy + hz * hz) > len + AscentRules.RAPPEL_BODY_TOLERANCE) return false;
        boolean smooth = kit(p).smooth();
        boolean same = m.state.rope && Math.abs(m.ax - msg.x()) + Math.abs(m.ay - msg.y()) + Math.abs(m.az - msg.z()) < 0.05;
        // a new rope (or a new anchor: a wrap) starts the bank afresh
        double bank = m.state == AscentState.RAPPEL && same ? AscentRules.payBank(m.payBank, sinceLength(p, m), smooth) : AscentRules.RAPPEL_SLACK;
        double grow = same ? len - m.len : 0;
        if (grow > bank + 1e-6) return false;
        m.payBank = bank - Math.max(0, grow);
        return true;
    }

    /** A rappel's hand gone further from its anchor than its rope could have paid out since the server last heard it. */
    static boolean rappelAstray(ServerPlayer p, AscentMotion m) {
        double hx = p.getX() - m.ax, hy = p.getY() + AscentRules.HAND_HEIGHT - m.ay, hz = p.getZ() - m.az;
        return Math.sqrt(hx * hx + hy * hy + hz * hz) > AscentRules.rappelBodyLimit(m.len, sinceLength(p, m), kit(p).smooth());
    }

    /**
     * Ticks since the rope's length was last heard: the game's, or the clock's when the server runs slow (the client
     * pays out at its own 20 a second, so a lagging server mustn't take that for paying too fast).
     */
    static long sinceLength(ServerPlayer p, AscentMotion m) {
        return Math.max(now(p) - m.lenAt, (net.minecraft.util.Util.getMillis() - m.lenAtMs) / 50);
    }

    /**
     * A rope's anchor is real: within the rope's longest reach (mastery 10, the line; plus the ground the body covered
     * while the packet travelled) of where the server has the body, and the point sits on a block's collision (it is
     * placed a hair off the face it bit).
     */
    static boolean anchorReal(ServerPlayer p, double ax, double ay, double az) {
        double reach = reach(p, 10) + 6;
        if (p.distanceToSqr(ax, ay, az) > reach * reach) return false;
        net.minecraft.world.phys.AABB probe = new net.minecraft.world.phys.AABB(ax - 0.12, ay - 0.12, az - 0.12, ax + 0.12, ay + 0.12, az + 0.12);
        for (var shape : p.level().getBlockCollisions(null, probe)) {
            if (!shape.isEmpty()) return true;
        }
        return false;
    }

    // ---- the rope strike (SPEC §62) ----------------------------------------------------------------------------------

    /**
     * A strike begins: the rope may bite this creature if the Tether's mastery has the move, the creature is one the
     * look could mean (alive, seen, within the rope's reach and the ground covered while the packet travelled) and no
     * player. The ZIP state that follows is then let through with the creature for an anchor.
     */
    static void onStrike(ServerPlayer p, AscentPayloads.Strike msg) {
        AscentMotion m = motion(p);
        int level = p.getData(AscentContent.MEMORY).level(AscentMemory.Track.TETHER);
        net.minecraft.world.entity.Entity t = p.level().getEntity(msg.target());
        boolean ok = Ascent.has(p, Ascent.Tier.TETHER) && level >= AirRules.STRIKE_LEVEL && now(p) - m.strikeAt >= AirRules.STRIKE_COOLDOWN - 6
                && t instanceof net.minecraft.world.entity.LivingEntity && !(t instanceof Player)
                && dev.awaken.core.api.Aim.meant(p, t, p.getYRot(), p.getXRot(), reach(p, level) + 6);
        m.strikeTarget = ok ? msg.target() : -1;
        if (ok) m.strikeAt = now(p);
    }

    /** A strike's anchor is its creature: the point sits in (or a little round) the creature the server let the rope bite. */
    static boolean strikeAnchor(ServerPlayer p, AscentMotion m, double x, double y, double z) {
        if (m.strikeTarget < 0 || now(p) - m.strikeAt > AirRules.STRIKE_TICKS + 20) return false;
        net.minecraft.world.entity.Entity t = p.level().getEntity(m.strikeTarget);
        return t != null && t.isAlive() && t.getBoundingBox().inflate(3).contains(x, y, z);
    }

    /**
     * The blow. The pull was the client's; whether it lands and how hard is decided here: the creature the rope bit,
     * still alive, and the body really at it (its packets may trail the pull by a few ticks). The hand's own damage and
     * more for the speed it came in at, as a player's attack, and a hit for everything that answers one.
     */
    static void strikeBlow(ServerPlayer p, AscentMotion m, float speed) {
        int id = m.strikeTarget;
        m.strikeTarget = -1;
        if (id < 0 || now(p) - m.strikeAt > AirRules.STRIKE_TICKS + 20) return;
        if (!(p.level().getEntity(id) instanceof net.minecraft.world.entity.LivingEntity t) || !t.isAlive() || dev.awaken.core.api.Aim.body(t)) return;
        if (t instanceof net.minecraft.world.entity.OwnableEntity own && own.getOwnerReference() != null && p.getUUID().equals(own.getOwnerReference().getUUID())) return;
        if (dev.awaken.core.api.Aim.gap(p.getEyePosition(), t.getBoundingBox()) > AirRules.STRIKE_ARRIVE + 4.5) return;
        ServerLevel level = p.level();
        double came = Mth.clamp(speed, 0, AirRules.STRIKE_SPEED + 0.6);
        float damage = AirRules.strikeDamage(p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE), came);
        var source = p.damageSources().playerAttack(p);
        if (!t.hurtServer(level, source, damage)) return;
        double dx = t.getX() - p.getX(), dz = t.getZ() - p.getZ(), d = Math.sqrt(dx * dx + dz * dz);
        if (d > 1e-3) t.knockback(0.5 + 0.3 * came, -dx / d, -dz / d, source, damage);
        dev.awaken.core.api.FightMoments.report(p, dev.awaken.core.api.FightMoments.Moment.HIT, t);
        link(p, AscentMemory.Track.TETHER, 3);
        made(p, AscentGoals.STRUCK, AscentTips.STRIKE);
    }

    /** A move of §62 made: counted (its row leaves the Tasks page after the first) and its tip a step nearer retiring. */
    private static void made(ServerPlayer p, String counter, net.minecraft.resources.Identifier tip) {
        dev.awaken.progression.Progression.count(p, counter);
        dev.awaken.core.api.TipHooks.done(p, tip);
        // The Way Up (SPEC §63.8): the move's lesson is over for good
        WayUp.moveMade(p);
    }

    // ---- a push carries (SPEC §62) -----------------------------------------------------------------------------------

    /**
     * A push on a body the Ascent holds, said plainly: its client moves it, so the push is sent there to join its
     * state's velocity. False when the Ascent doesn't hold this body: the caller then pushes it the game's way. (A
     * motion the game itself syncs, a knock or a module's own packet, reaches a held body too: the server's copy rests at
     * nothing between ticks and so does the client's, so whatever arrives is a push there.)
     */
    public static boolean push(ServerPlayer p, double x, double y, double z) {
        AscentMotion m = p.getExistingDataOrNull(AscentContent.MOTION);
        if (m == null || !m.state.airborne()) return false;
        AwakenNet.send(p, new AscentPayloads.Push((float) x, (float) y, (float) z));
        return true;
    }

    static void onEvent(ServerPlayer p, AscentPayloads.Event msg) {
        if (msg.what() == AscentPayloads.EV_ASCEND) {
            Sovereign.ascend(p);
            return;
        }
        if (msg.what() == AscentPayloads.EV_SLAM_LAND) slam(p, Math.min(3F, Math.max(0F, msg.speed())));
        // the moves that count as links, each only with the tier that has it and from a state it can come from (a
        // rope's is counted when its state lands)
        AscentState st = motion(p).state;
        switch (msg.what()) {
            case AscentPayloads.EV_LAUNCH -> {
                if (st == AscentState.TETHER) link(p, AscentMemory.Track.TETHER, 3);
            }
            // Rope and Wings (SPEC §62): a sling's let-go and a spring off a top are the rope's moves
            case AscentPayloads.EV_SLING -> {
                if (st == AscentState.ZIP) {
                    link(p, AscentMemory.Track.TETHER, 2);
                    made(p, AscentGoals.SLUNG, AscentTips.SLING);
                }
            }
            case AscentPayloads.EV_SPRING -> {
                if (Ascent.has(p, Ascent.Tier.TETHER) && st == AscentState.CARRY
                        && p.getData(AscentContent.MEMORY).level(AscentMemory.Track.TETHER) >= AirRules.SPRING_LEVEL) {
                    link(p, AscentMemory.Track.TETHER, 2);
                    made(p, AscentGoals.SPRUNG, AscentTips.SPRING);
                }
            }
            // the moves the air earned (SPEC §62): each a link, once in a while, only out of the state it comes from
            case AscentPayloads.EV_FLARE -> {
                AscentMotion m = motion(p);
                if (Ascent.has(p, Ascent.Tier.WINGS) && now(p) - m.endedAt < SOFT_LANDING && now(p) - m.flareAt >= 60) {
                    m.flareAt = now(p);
                    link(p, AscentMemory.Track.WINGS, 2);
                    made(p, AscentGoals.FLARED, AscentTips.FLARE);
                }
            }
            case AscentPayloads.EV_SKIM -> {
                AscentMotion m = motion(p);
                if (Ascent.has(p, Ascent.Tier.WINGS) && st == AscentState.GLIDE && now(p) - m.skimAt >= 100
                        && p.getData(AscentContent.MEMORY).level(AscentMemory.Track.WINGS) >= AirRules.SKIM_LEVEL) {
                    m.skimAt = now(p);
                    link(p, AscentMemory.Track.WINGS, 3);
                    made(p, AscentGoals.SKIMMED, AscentTips.SKIM);
                }
            }
            case AscentPayloads.EV_TAKEOFF -> {
                AscentMotion m = motion(p);
                if (Ascent.has(p, Ascent.Tier.SKYBORN) && st == AscentState.SOAR && now(p) - m.since <= 10) {
                    m.aether = Math.max(0, m.aether - AirRules.TAKEOFF_AETHER);
                    link(p, AscentMemory.Track.SKYBORN, 2);
                    made(p, AscentGoals.TOOK_OFF, AscentTips.TAKEOFF);
                }
            }
            case AscentPayloads.EV_STRIKE -> strikeBlow(p, motion(p), msg.speed());
            // a rappel's bound off the wall: a link, as any move on the rope
            case AscentPayloads.EV_RAPPEL_KICK -> {
                if (st == AscentState.RAPPEL) link(p, AscentMemory.Track.TETHER, 1);
            }
            case AscentPayloads.EV_DEPLOY -> {
                if (Ascent.has(p, Ascent.Tier.WINGS)) link(p, AscentMemory.Track.WINGS, 2);
            }
            case AscentPayloads.EV_BOOM -> {
                if (Ascent.has(p, Ascent.Tier.WINGS) && st.wings) link(p, AscentMemory.Track.WINGS, 6);
            }
            case AscentPayloads.EV_DOUBLE, AscentPayloads.EV_DASH, AscentPayloads.EV_DIVE -> {
                if (Ascent.has(p, Ascent.Tier.LEAPER)) {
                    link(p, AscentMemory.Track.LEAPER, 2);
                    dev.awaken.core.api.TipHooks.done(p, AscentTips.LEAPER);
                }
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
        // in the Sovereign form a dive lands as the Judgement Fall (SPEC §17.19)
        if ((m.state.airborne() || now(p) - m.endedAt < SOFT_LANDING) && Sovereign.judgement(p, speed)) return;
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
        AscentTrials.tick(p, now(p));
        WindwaysServer.tick(p, now(p));
        AscentQuests.ride(p, m.state);
        Sovereign.tick(p, now(p));
        aether(p, m);
        // the trail behind Ascent.arrivedByMovement: was this tick spent moving by rope, wings or Runner move
        boolean moving = Ascent.moving(p, m);
        m.mark(moving);
        // the Wilds' "how you arrived" hears the ladder's states: the line itself (a rope, a zip, the carry, wings)
        // apart from a Runner move (a wall-run, a hang), since a climb site's payoff answers only the line (SPEC §17.29)
        if (moving) dev.awaken.core.api.ArrivalHooks.report(p, m.state.airborne() ? dev.awaken.core.api.ArrivalHooks.Way.LINE
                : dev.awaken.core.api.ArrivalHooks.Way.ASCENT, 5);
        if (m.chain > 0 && now(p) - m.lastLinkAt > CHAIN_GAP) payChain(p, m, now(p));
        if (!m.state.airborne()) return;
        if (p.isPassenger() || p.isSpectator() || !p.isAlive()) {
            end(p, m);
            return;
        }
        // a rappel is only free while the body is really on its rope (SPEC §28.5): a hand gone further than the rope could
        // have paid out is refused (the client lets go into the carry; nothing is sent back)
        if (m.state == AscentState.RAPPEL && rappelAstray(p, m)) {
            refusals++;
            AwakenNet.send(p, new AscentPayloads.Stop(AscentState.RAPPEL.ordinal()));
            dev.awaken.Awaken.LOG.debug("[ascent] refused a rappel astray for {}", p.getPlainTextName());
            end(p, m);
            return;
        }
        // on a line over a long drop: the rappel's tip (once a second; the tip system keeps its own pace)
        if (m.state == AscentState.TETHER && now(p) % 20 == 0
                && p.getY() - AscentAnchors.floorUnder(p.level(), p, p.getX(), p.getY() + 0.5, p.getZ(), 24) >= 12) {
            dev.awaken.core.api.TipHooks.offer(p, AscentTips.RAPPEL);
        }
        p.resetFallDistance();
        p.connection.resetFlyingTicks();
        // the client moves this body; the server's own physics between packets (gravity on a hover, a hang on a rope)
        // would sink its copy here and show everyone else a bob. A real push set this tick that has yet to be synced is
        // left to go out first (SPEC §62: the client carries what arrives into its state's velocity)
        if (!p.needsSync || p.getDeltaMovement().lengthSqr() < 0.04) p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
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
