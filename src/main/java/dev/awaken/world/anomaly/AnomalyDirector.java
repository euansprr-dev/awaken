package dev.awaken.world.anomaly;

import dev.awaken.Awaken;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.Later;
import dev.awaken.core.Playtest;
import dev.awaken.core.api.Notices;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The anomaly director: per awakened player in the overworld, a countdown of outdoor play (6–12 minutes, 1.5× faster
 * at night, see {@link Pacing}) that starts one small event near them when it runs out. Never inside a Hearth's reach,
 * while asleep, in the first two minutes after awakening, or during a gate run ({@link Eligibility}); at most one at a
 * time per player; on a spot {@link Spots} accepts, or not at all. Every decision is logged for {@code /awaken why}.
 */
public final class AnomalyDirector {
    private AnomalyDirector() {}

    /** Per-player check interval (spread by UUID so players don't all check on the same tick). */
    static final int PERIOD = 20;
    /** Active anomalies are driven this often. */
    static final int UPDATE = 10;
    static final int REMOVALS = 100;

    public record Decision(String outcome, @Nullable Anomaly started) {
        static Decision of(String outcome) {
            return new Decision(outcome, null);
        }

        public boolean blockedBy(String reasonStart) {
            return outcome.startsWith("blocked: " + reasonStart);
        }
    }

    static AnomalyMemory memory(ServerPlayer p) {
        return p.getData(AnomalyContent.MEMORY);
    }

    /** The kinds this player has met (their first-sighting discoveries), for the novelty boost in {@link Pacing#weights}. */
    static Set<String> met(ServerPlayer p) {
        Profile prof = Progression.profile(p);
        Set<String> out = new HashSet<>();
        for (AnomalyKind k : AnomalyKind.values()) if (prof.counter("awaken:discovered/" + k.discoveryKey()) > 0) out.add(k.id);
        return out;
    }

    // ---- ticking ------------------------------------------------------------------------------------------------------

    static void tick(MinecraftServer server) {
        int t = server.getTickCount();
        if (t % UPDATE == 0) updateAll(server);
        if (t % REMOVALS == 0) processRemovals(server);
        // GameTests drive the countdown themselves: mock players from other tests must never get a rift next to them
        if (server instanceof GameTestServer) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (Math.floorMod(t + p.getUUID().hashCode(), PERIOD) == 0) upkeep(p, PERIOD);
        }
    }

    /** One countdown step of {@code ticks} for one player; starts an anomaly when it runs out. */
    public static Decision upkeep(ServerPlayer p, int ticks) {
        MinecraftServer s = p.level().getServer();
        AnomalyData data = AnomalyData.get(s);
        AnomalyMemory mem = memory(p);
        long now = s.overworld().getGameTime();
        String blocked = Eligibility.blocked(Eligibility.of(p, data, mem, now));
        if (blocked != null) {
            noteBlocked(p, mem, blocked);
            return Decision.of("blocked: " + blocked);
        }
        mem.lastBlock = "";
        if (mem.timer < 0) {
            mem.timer = mem.started == 0 ? Pacing.firstDelay(p.getRandom()) : Pacing.nextDelay(p.getRandom());
            DirectorLog.log(p, "anomaly", String.format("countdown set: ~%.1f min of outdoor play", mem.timer / Pacing.MINUTE));
        }
        boolean night = p.level().isDarkOutside();
        mem.timer = Pacing.step(mem.timer, ticks, night);
        if (mem.timer > 0) return Decision.of("waiting");
        int[] w = Pacing.weights(mem.started == 0, night, p.level().getDifficulty() == Difficulty.PEACEFUL, Progression.profile(p).level(),
                mem.lastKind, met(p));
        AnomalyKind kind = Pacing.pick(w, p.getRandom());
        if (kind == null) {
            mem.timer = Pacing.nextDelay(p.getRandom());
            DirectorLog.log(p, "anomaly", "due, but nothing fits → next in ~" + minutes(mem.timer));
            return Decision.of("nothing fits");
        }
        StringBuilder why = new StringBuilder();
        Anomaly a = start(p, kind, false, null, why);
        if (a == null) {
            mem.timer = Pacing.RETRY;
            DirectorLog.log(p, "anomaly", "due (" + kind.id + "), skipped quietly: " + why + " → try again in 1 min");
            return Decision.of("no spot: " + why);
        }
        mem.timer = Pacing.nextDelay(p.getRandom());
        DirectorLog.log(p, "anomaly", String.format("rolled %s from weights %s%s; next countdown ~%s", kind.id, Pacing.describe(w),
                night ? " (night, clock ×1.5)" : "", minutes(mem.timer)));
        return new Decision("started", a);
    }

    private static void noteBlocked(ServerPlayer p, AnomalyMemory mem, String reason) {
        if (reason.equals(mem.lastBlock)) return;
        mem.lastBlock = reason;
        // the everyday reasons aren't news; the ones that keep an anomaly away are
        if (reason.startsWith("not awakened") || reason.startsWith("not in the overworld") || reason.startsWith("spectating")) return;
        DirectorLog.log(p, "anomaly", "paused: " + reason);
    }

    public static @Nullable Anomaly start(ServerPlayer p, AnomalyKind kind, boolean inFront, StringBuilder why) {
        return start(p, kind, inFront, null, why);
    }

    /**
     * Starts {@code kind} for this player now on a validated spot. {@code inFront} (commands) tries 14–26 blocks where
     * the player is looking first. {@code variant} picks a shimmer grove's (null: random, not the last one). Returns null
     * with the reason in {@code why}.
     */
    public static @Nullable Anomaly start(ServerPlayer p, AnomalyKind kind, boolean inFront, @Nullable GroveVariant variant, StringBuilder why) {
        ServerLevel level = p.level();
        MinecraftServer s = level.getServer();
        AnomalyData data = AnomalyData.get(s);
        if (level.dimension() != Level.OVERWORLD) {
            why.append("not in the overworld");
            return null;
        }
        if (data.activeFor(p.getUUID()) != null) {
            why.append("one anomaly is already active for you");
            return null;
        }
        if (kind == AnomalyKind.RIFT_TEAR && level.getDifficulty() == Difficulty.PEACEFUL) {
            why.append("no rifts on Peaceful");
            return null;
        }
        Map<String, Integer> rejects = Spots.tally();
        Spots.Rules rules = kind == AnomalyKind.FALLEN_STAR ? Spots.Rules.NORMAL.sky(true) : Spots.Rules.NORMAL;
        int maxFirst = switch (kind) {
            case RIFT_TEAR -> 72;            // a short walk first, the full range if nothing near fits
            case SPIRIT_PROCESSION -> Processions.START_MAX;
            case SHIMMER_GROVE -> Groves.START_MAX;
            case FALLEN_STAR -> 80;
            case WANDERING_GATE -> Spots.MAX;
        };
        BlockPos ground = null;
        if (inFront) {
            Vec3 look = p.getLookAngle();
            ground = Spots.find(level, p.blockPosition(), p.getRandom(), rules.distance(14, 26), data, Math.atan2(look.z, look.x), Math.PI / 3, rejects);
        }
        if (ground == null) ground = Spots.find(level, p.blockPosition(), p.getRandom(), rules.distance(Spots.MIN, maxFirst), data, Double.NaN, 0, rejects);
        if (ground == null && maxFirst < Spots.MAX && kind != AnomalyKind.SPIRIT_PROCESSION) {
            ground = Spots.find(level, p.blockPosition(), p.getRandom(), rules, data, Double.NaN, 0, rejects);
        }
        if (ground == null) {
            why.append("no valid spot ").append(rejects);
            return null;
        }
        AnomalyMemory mem = memory(p);
        boolean first = mem.started == 0;
        Anomaly a = switch (kind) {
            case RIFT_TEAR -> Rifts.open(level, p, ground, data, first);
            case WANDERING_GATE -> {
                Anomaly g = WanderingGates.open(level, p, ground, data);
                if (g == null && level.getDifficulty() != Difficulty.PEACEFUL) {
                    DirectorLog.log(p, "anomaly", "no wandering gate could form (no gate world or free cell) → a rift tear instead");
                    g = Rifts.open(level, p, ground, data, first);
                }
                if (g == null) why.append("no wandering gate could form");
                yield g;
            }
            case SPIRIT_PROCESSION -> {
                BlockPos to = Processions.findTarget(level, p, ground, data, p.getRandom(), rules, rejects);
                if (to == null) {
                    why.append("no spot for the cache ").append(rejects);
                    yield null;
                }
                yield Processions.open(level, p, ground, to, data);
            }
            case FALLEN_STAR -> Stars.open(level, p, ground, data);
            case SHIMMER_GROVE -> Groves.open(level, p, ground, data, variant != null ? variant : Groves.pickVariant(p.getRandom(), mem.lastGrove));
        };
        if (a == null) return null;
        long now = level.getGameTime();
        mem.started++;
        mem.lastKind = a.kind.id;
        mem.lastStartedAt = now;
        announce(p, a, now);
        DirectorLog.log(p, "anomaly", String.format("%s #%d at %s (%s)%s%s", a.kind.display, a.id, Spots.where(p.blockPosition(), a.focus()),
                a.focus().toShortString(), detail(a), rejects.isEmpty() ? "" : "; spots refused " + rejects));
        Playtest.log(p, "anomaly_start", "kind", a.kind.id);
        return a;
    }

    private static String detail(Anomaly a) {
        return switch (a.kind) {
            case RIFT_TEAR -> ", " + a.theme + " level " + a.contentLevel + ", " + a.maxHits + " hits";
            case FALLEN_STAR -> ", guardian " + a.theme + " level " + a.contentLevel;
            case SHIMMER_GROVE -> ", " + Groves.variant(a).id;
            default -> "";
        };
    }

    /** The world speaks first (the tear's sound, the star's whistle): the Signal's word follows this many ticks later. */
    static final int WORD_AFTER = 50;

    /**
     * The Signal's word on it (SPEC §26.4): after the world's own cue, a paced toast in the "nearby" topic (never a
     * choice card, never several at once). The first rift a player ever meets is a moment instead.
     */
    private static void announce(ServerPlayer p, Anomaly a, long now) {
        boolean firstRift = a.kind == AnomalyKind.RIFT_TEAR && Progression.profile(p).counter("awaken:discovered/" + a.kind.discoveryKey()) == 0;
        java.util.UUID id = p.getUUID();
        Later.run(p.level().getServer(), WORD_AFTER, () -> {
            ServerPlayer q = p.level().getServer().getPlayerList().getPlayer(id);
            AnomalyData data = AnomalyData.get(p.level().getServer());
            if (q == null || data.find(a.id) == null) return; // gone, or it ended in the meantime: nothing to tell
            word(q, a, q.level().getGameTime(), firstRift);
        });
    }

    static void word(ServerPlayer p, Anomaly a, long now, boolean firstRift) {
        String where = Spots.where(p.blockPosition(), a.focus());
        String title, body;
        switch (a.kind) {
            case RIFT_TEAR -> {
                title = "Something tore open nearby";
                body = where + " · break its core";
            }
            case WANDERING_GATE -> {
                title = "A wandering gate formed";
                body = where + " · fades in " + WanderingGates.minutesLeft(a, now) + " min unless entered";
            }
            case FALLEN_STAR -> {
                title = "A star fell nearby";
                body = where + " · something guards it";
            }
            case SHIMMER_GROVE -> {
                title = "The air shimmers nearby";
                body = where + " · the rules bend there";
            }
            default -> {
                title = "Lights gather nearby";
                body = where + " · follow them";
            }
        }
        if (firstRift) {
            Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Something tore open").withColor(0xC9A0FF))
                    .body(Component.literal(where + ". A rift: monsters spill from it until its core breaks. Strike the light at its foot."))
                    .footer(Component.literal("Rift")).presentation(SignalWindow.Presentation.TOAST)
                    .tier(Notices.Tier.IMPORTANT).moment().topic("nearby").ttl(20 * 8).build());
            return;
        }
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(title)).body(Component.literal(body))
                .presentation(SignalWindow.Presentation.TOAST).tier(Notices.Tier.IMPORTANT).topic("nearby").ttl(20 * 6).build());
    }

    /** The first meeting with a kind pays a discovery (a tenth of a level, once). */
    static void discovered(ServerPlayer p, AnomalyKind kind) {
        if (Progression.discover(p, kind.discoveryKey(), 0.1)) {
            Progression.markNewThing(p, kind.display);
            Progression.count(p, "awaken:anomalies_seen");
            DirectorLog.log(p, "anomaly", "first " + kind.display.toLowerCase() + ": discovery (+0.1 level)");
        }
    }

    // ---- driving and ending -------------------------------------------------------------------------------------------

    static void updateAll(MinecraftServer s) {
        AnomalyData data = AnomalyData.get(s);
        if (data.active().isEmpty()) return;
        ServerLevel level = s.overworld();
        long now = level.getGameTime();
        for (Anomaly a : data.active().toArray(new Anomaly[0])) {
            String ended;
            try {
                ended = switch (a.kind) {
                    case RIFT_TEAR -> Rifts.update(level, data, a, now);
                    case SPIRIT_PROCESSION -> Processions.update(level, data, a, now);
                    case WANDERING_GATE -> WanderingGates.update(level, data, a, now);
                    case FALLEN_STAR -> Stars.update(level, data, a, now);
                    case SHIMMER_GROVE -> Groves.update(level, data, a, now);
                };
            } catch (RuntimeException e) {
                Awaken.LOG.warn("[anomaly] #{} failed, ending it: {}", a.id, e.toString());
                ended = "failed (" + e.getClass().getSimpleName() + ")";
            }
            if (ended != null) end(s, data, a, ended, true);
        }
    }

    /** Ends an anomaly: kind cleanup (the tear collapses, lights fade, an unopened cache or star comes down), then it's forgotten. */
    static void end(MinecraftServer s, AnomalyData data, Anomaly a, String reason, boolean removeBlocks) {
        if (!data.remove(a)) return;
        ServerLevel level = s.overworld();
        switch (a.kind) {
            case RIFT_TEAR -> Rifts.cleanup(level, a);
            case SPIRIT_PROCESSION -> Processions.cleanup(level, data, a, removeBlocks);
            case WANDERING_GATE -> { } // the gates module fades its own gate
            case FALLEN_STAR -> Stars.cleanup(level, data, a, removeBlocks);
            case SHIMMER_GROVE -> Groves.cleanup(level, a);
        }
        ServerPlayer owner = s.getPlayerList().getPlayer(a.owner);
        if (owner != null) DirectorLog.log(owner, "anomaly", a.kind.display + " #" + a.id + " ended: " + reason);
    }

    /** Ends every active anomaly (command); returns how many. */
    static int clearAll(MinecraftServer s) {
        AnomalyData data = AnomalyData.get(s);
        int n = 0;
        for (Anomaly a : data.active().toArray(new Anomaly[0])) {
            end(s, data, a, "cleared by command", true);
            n++;
        }
        return n;
    }

    static void processRemovals(MinecraftServer s) {
        processRemovals(s, AnomalyData.get(s));
    }

    /** Blocks an anomaly may leave behind that are taken down once it's over (only if they are still ours). */
    static boolean ours(BlockState state) {
        return state.is(AnomalyContent.SPIRIT_CACHE.get()) || state.is(AnomalyContent.FALLEN_STAR.get());
    }

    static void processRemovals(MinecraftServer s, AnomalyData data) {
        if (data.removals().isEmpty()) return;
        Iterator<AnomalyData.Removal> it = data.removals().iterator();
        while (it.hasNext()) {
            AnomalyData.Removal r = it.next();
            Identifier dim = Identifier.tryParse(r.dimension());
            ServerLevel level = dim == null ? null : s.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
            if (level != null && !level.hasChunkAt(r.pos())) continue; // wait for the chunk
            if (level != null && ours(level.getBlockState(r.pos()))) level.removeBlock(r.pos(), false);
            it.remove();
            data.setDirty();
        }
    }

    /**
     * After a restart (server start): anomalies that were running are interrupted, so each is cleaned up and dropped.
     * Their entities were never saved; an unopened cache or meteor is taken down (now or when its chunk loads); a
     * wandering gate fades on its own; a star's guardian is turned away when its chunk loads ({@link Stars#onJoin}).
     * Returns how many were cleaned up.
     */
    public static int recover(MinecraftServer s) {
        return recover(s, AnomalyData.get(s));
    }

    static int recover(MinecraftServer s, AnomalyData data) {
        List<Anomaly> was = new ArrayList<>(data.active());
        for (Anomaly a : was) {
            data.remove(a);
            if (a.kind == AnomalyKind.SPIRIT_PROCESSION && a.cachePlaced) {
                data.removals().add(new AnomalyData.Removal(Level.OVERWORLD.identifier().toString(), a.focus()));
            }
            if (a.kind == AnomalyKind.FALLEN_STAR && a.landed) {
                data.removals().add(new AnomalyData.Removal(Level.OVERWORLD.identifier().toString(), a.target));
            }
        }
        if (!was.isEmpty()) {
            data.setDirty();
            Awaken.LOG.info("[anomaly] cleaned up {} anomalies interrupted by the restart", was.size());
        }
        processRemovals(s, data);
        return was.size();
    }

    static void onAwakened(ServerPlayer p) {
        AnomalyMemory mem = memory(p);
        mem.awakenedAt = p.level().getServer().overworld().getGameTime();
        mem.timer = Pacing.firstDelay(p.getRandom());
        DirectorLog.log(p, "anomaly", "awakened: the first anomaly comes after ~" + minutes(mem.timer) + " of outdoor play (never in the first 2 min)");
    }

    /** For {@code /awaken anomaly why}: the countdown as the director sees it right now. */
    static List<String> status(ServerPlayer p) {
        List<String> out = new ArrayList<>();
        MinecraftServer s = p.level().getServer();
        AnomalyData data = AnomalyData.get(s);
        AnomalyMemory mem = memory(p);
        long now = s.overworld().getGameTime();
        String blocked = Eligibility.blocked(Eligibility.of(p, data, mem, now));
        Anomaly mine = data.activeFor(p.getUUID());
        if (mine != null) out.add("Active: " + mine.kind.display + " #" + mine.id + " · " + Spots.where(p.blockPosition(), mine.focus())
                + " · ends in " + Math.max(0, (mine.endsAt - now) / 20) + " s");
        if (blocked != null) out.add("Paused: " + blocked);
        else if (mem.timer < 0) out.add("The countdown starts on the next check");
        else out.add(String.format("Next anomaly in ~%s of outdoor play%s", minutes(mem.timer),
                p.level().isDarkOutside() ? " (night: the clock runs 1.5× faster)" : ""));
        out.add("Seen " + mem.started + " · rifts closed " + mem.riftsClosed + " · caches " + mem.caches + " · stars " + mem.stars
                + " · groves " + mem.groves);
        return out;
    }

    static String minutes(double ticks) {
        return String.format("%.1f min", Math.max(0, ticks) / Pacing.MINUTE);
    }
}
