package dev.awaken.world.landmark;

import dev.awaken.core.api.GateHooks;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.core.api.NowHooks;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
import dev.awaken.world.anomaly.Spots;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The landmarks' line in the Now tracker: the nearest landmark the player hasn't reached and has a reason to visit
 * ("The Well · 600 m SE" from level 10, since its first stratum plays at content level 17; a sealed vault from level 4,
 * the spawn vault from awakening for the Scanner in its antechamber...), as an idea (30). A landmark a grove's spirit
 * whispered about comes first and a little higher (38) for ten minutes. Never at home inside a Hearth, never in a gate.
 * The search is cached per player (10 s, or 48 blocks walked).
 */
final class LandmarkNow {
    private LandmarkNow() {}

    static final int PRIORITY = 30, WHISPERED = 38;
    static final int CACHE_TICKS = 200, CACHE_MOVE = 48;

    private record Pick(Landmarks.Landmark landmark, boolean whispered) {}

    private record Cached(long at, BlockPos from, Optional<Pick> pick) {}

    private static final Map<UUID, Cached> CACHE = new HashMap<>();

    static void register() {
        NowHooks.register(LandmarkNow::suggest);
    }

    static void forget(UUID id) {
        CACHE.remove(id);
    }

    static void clear() {
        CACHE.clear();
    }

    static List<NowHooks.Suggestion> suggest(ServerPlayer p) {
        if (p.level().dimension() != Level.OVERWORLD || !Landmarks.enabled()) return List.of();
        Profile prof = Progression.profile(p);
        if (!prof.awakened() || GateHooks.inGate(p)) return List.of();
        BlockPos me = p.blockPosition();
        if (HearthHooks.inside(p.level(), me)) return List.of(); // nothing calls you away from home
        long now = p.level().getGameTime();
        Cached c = CACHE.get(p.getUUID());
        if (c == null || now - c.at() > CACHE_TICKS || now < c.at() || c.from().distSqr(me) > CACHE_MOVE * CACHE_MOVE) {
            c = new Cached(now, me, pick(p, now));
            CACHE.put(p.getUUID(), c);
            // a landmark the System points you at is one you've heard of: it goes on the Gate Map
            c.pick().ifPresent(k -> {
                if (Landmarks.memory(p).place(k.landmark().id(), k.landmark().kind(), k.landmark().pos())) Landmarks.sync(p);
            });
        }
        if (c.pick().isEmpty()) return List.of();
        Pick k = c.pick().get();
        Landmarks.Landmark l = k.landmark();
        if (Landmarks.reached(p, l.id())) return List.of(); // reached since the cache was built
        String text = (k.whispered() ? "Whispered: " : "") + l.kind().display + " · " + Spots.where(me, l.pos());
        return List.of(NowHooks.Suggestion.at("landmark:" + l.id(), k.whispered() ? WHISPERED : PRIORITY, text, l.pos()));
    }

    private static Optional<Pick> pick(ServerPlayer p, long now) {
        LandmarkMemory m = Landmarks.memory(p);
        List<Landmarks.Landmark> all = Landmarks.around(p, Landmarks.NEAR, 6);
        if (!m.whispered.isEmpty() && now - m.whisperedAt < Landmarks.WHISPER_LIFE && !m.reached(m.whispered)) {
            for (Landmarks.Landmark l : all) if (l.id().equals(m.whispered)) return Optional.of(new Pick(l, true));
        }
        return Landmarks.pick(all, p.blockPosition(), m::reached, Progression.profile(p).level(), true).map(l -> new Pick(l, false));
    }
}
