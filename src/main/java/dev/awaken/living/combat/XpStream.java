package dev.awaken.living.combat;

import dev.awaken.living.LivingModule;
import dev.awaken.living.LivingNet;
import dev.awaken.progression.Progression;
import dev.awaken.progression.XpBudget;
import dev.awaken.signal.Signal;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tells each client what XP it got and where from (SPEC §21.1), for the stream into the band. Kills, discoveries and
 * quest steps go at once with their origin; small grants (a placed block, an ore) gather for a second and a half and go
 * as one. Once per source per 10 minutes past its budget, a toast says the source is worn.
 */
public final class XpStream {
    private XpStream() {}

    static final int GATHER_TICKS = 30;
    static final long WORN_COOLDOWN = 20L * 60 * 10;

    private record Origin(Vec3 at, int kind) {}

    private static final Map<UUID, Origin> ORIGIN = new HashMap<>();
    private static final Map<UUID, double[]> PENDING = new HashMap<>(); // xp, first tick
    private static final Map<UUID, Map<String, Long>> WORN = new HashMap<>();

    /** Where the next grant comes from (set just before granting, cleared right after). */
    static void origin(ServerPlayer p, Vec3 at, int kind) {
        ORIGIN.put(p.getUUID(), new Origin(at, kind));
    }

    static void clearOrigin(ServerPlayer p) {
        ORIGIN.remove(p.getUUID());
    }

    /** Progression's listener: every grant after scaling. */
    public static void granted(ServerPlayer p, double xp, Progression.XpSource source, @Nullable String worldKey) {
        if (xp <= 0 || !LivingModule.shown(p.level().getServer())) return;
        Origin o = ORIGIN.get(p.getUUID());
        int kind = o != null ? o.kind : switch (source) {
            case DISCOVERY -> LivingNet.XP_DISCOVERY;
            case DIRECTIVE, TRUTH, STORY -> LivingNet.XP_QUEST;
            default -> LivingNet.XP_OTHER;
        };
        if (o != null || kind == LivingNet.XP_DISCOVERY || kind == LivingNet.XP_QUEST || xp >= 0.02 * dev.awaken.progression.Curve.xpToNext(Progression.profile(p).level())) {
            Vec3 at = o != null ? o.at : Vec3.ZERO;
            dev.awaken.core.AwakenNet.send(p, new LivingNet.XpFx((int) Math.max(1, Math.round(xp)), kind, o != null, (float) at.x, (float) at.y, (float) at.z));
        } else {
            double[] pend = PENDING.computeIfAbsent(p.getUUID(), k -> new double[]{0, p.level().getGameTime()});
            if (pend[0] == 0) pend[1] = p.level().getGameTime();
            pend[0] += xp;
        }
        if (worldKey != null) worn(p, worldKey);
    }

    private static void worn(ServerPlayer p, String key) {
        long now = p.level().getGameTime();
        if (XpBudget.wear(p.getUUID(), key, now) < 1.6) return;
        Map<String, Long> last = WORN.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
        Long at = last.get(key);
        if (at != null && now - at < WORN_COOLDOWN) return;
        last.put(key, now);
        String what = switch (key) {
            case LivingModule.HUNT -> "hunting";
            case XpBudget.ORE -> "mining";
            case XpBudget.BUILD -> "building";
            default -> key.replace('_', ' ');
        };
        Signal.toast(p, Component.literal("Worn · " + what + " pays less for a while. Try something else."));
    }

    /** Sends gathered small grants once they've waited long enough. */
    public static void tick(MinecraftServer server) {
        if (PENDING.isEmpty() || server.getTickCount() % 10 != 0) return;
        var it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            double[] pend = en.getValue();
            if (pend[0] <= 0) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            if (p == null) {
                it.remove();
                continue;
            }
            if (p.level().getGameTime() - (long) pend[1] < GATHER_TICKS) continue;
            if (pend[0] >= 1) dev.awaken.core.AwakenNet.send(p, new LivingNet.XpFx((int) Math.round(pend[0]), LivingNet.XP_OTHER, false, 0, 0, 0));
            pend[0] = 0;
        }
    }

    public static void forget(UUID id) {
        ORIGIN.remove(id);
        PENDING.remove(id);
        WORN.remove(id);
    }
}
