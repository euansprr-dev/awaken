package dev.awaken.world.landmark;

import dev.awaken.core.api.GateHooks;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.core.api.HorizonHooks;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The landmarks' sites for the Horizon line (SPEC §17; until then they were a Now line that gates and requests
 * outranked): every landmark the player hasn't reached and has a reason to visit ("The Well" from level 10, since its
 * first stratum plays at content level 17; a sealed vault from level 4, the spawn vault from awakening...). A sky
 * platform needs a way up ({@link HorizonHooks#WINGS}) rather than a level: until the player has one it is only ever a
 * tease. A landmark a grove's spirit whispered about is raised for ten minutes ("Whispered: Sealed Vault"). Never at
 * home inside a Hearth, never in a gate. The search is cached per player (10 s, or 48 blocks walked).
 */
final class LandmarkHorizon {
    private LandmarkHorizon() {}

    static final int CACHE_TICKS = 200, CACHE_MOVE = 48;

    private record Cached(long at, BlockPos from, List<Landmarks.Landmark> sites, String whispered) {}

    private static final Map<UUID, Cached> CACHE = new HashMap<>();

    static void register() {
        HorizonHooks.register(LandmarkHorizon::sites);
    }

    static void forget(UUID id) {
        CACHE.remove(id);
    }

    static void clear() {
        CACHE.clear();
    }

    static List<HorizonHooks.Site> sites(ServerPlayer p) {
        if (p.level().dimension() != Level.OVERWORLD || !Landmarks.enabled()) return List.of();
        Profile prof = Progression.profile(p);
        if (!prof.awakened() || GateHooks.inGate(p)) return List.of();
        BlockPos me = p.blockPosition();
        if (HearthHooks.inside(p.level(), me)) return List.of(); // nothing calls you away from home
        long now = p.level().getGameTime();
        Cached c = CACHE.get(p.getUUID());
        if (c == null || now - c.at() > CACHE_TICKS || now < c.at() || c.from().distSqr(me) > CACHE_MOVE * CACHE_MOVE) {
            c = build(p, now);
            CACHE.put(p.getUUID(), c);
        }
        List<HorizonHooks.Site> out = new ArrayList<>(c.sites().size());
        for (Landmarks.Landmark l : c.sites()) {
            if (Landmarks.reached(p, l.id())) continue; // reached since the cache was built
            boolean whispered = l.id().equals(c.whispered());
            String need = need(l.kind());
            String why = l.kind().why();
            out.add(new HorizonHooks.Site("landmark:" + l.id(), (whispered ? "Whispered: " : "") + l.kind().display + (why.isEmpty() ? "" : " · " + why),
                    l.pos(), need, whispered));
        }
        return out;
    }

    /** What a kind of site asks: a sky platform a way up, a Tether Spire the rope, the rest nothing. */
    static String need(LandmarkKind k) {
        return switch (k) {
            case SKY_PLATFORM -> HorizonHooks.WINGS;
            case TETHER_SPIRE -> HorizonHooks.TETHER;
            default -> "";
        };
    }

    private static Cached build(ServerPlayer p, long now) {
        LandmarkMemory m = Landmarks.memory(p);
        int level = Progression.profile(p).level();
        BlockPos me = p.blockPosition();
        String whispered = !m.whispered.isEmpty() && now - m.whisperedAt < Landmarks.WHISPER_LIFE && !m.reached(m.whispered) ? m.whispered : "";
        List<Landmarks.Landmark> sites = new ArrayList<>();
        for (Landmarks.Landmark l : Landmarks.around(p, Landmarks.NEAR, 6)) {
            if (m.reached(l.id()) || !Landmarks.inRange(me, l)) continue;
            // a site that asks a way of moving is teased until you have it, whatever your level
            boolean needs = !need(l.kind()).isEmpty();
            if (needs || level >= l.reasonLevel() || l.id().equals(whispered)) sites.add(l);
        }
        // the nearest one you can go to now is one you've heard of: it goes on the Gate Map
        Landmarks.pick(sites, me, m::reached, level, true).filter(l -> HorizonHooks.can(p, need(l.kind())))
                .ifPresent(l -> {
                    if (m.place(l.id(), l.kind(), l.pos())) Landmarks.sync(p);
                });
        return new Cached(now, me, List.copyOf(sites), whispered);
    }
}
