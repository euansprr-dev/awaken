package dev.awaken.wilds;

import dev.awaken.core.api.CityHooks;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
import dev.awaken.wilds.arrival.ArrivalTests;
import dev.awaken.wilds.ward.WardTests;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Set;

/**
 * The Wilds' GameTests (SPEC §17), one per mechanism: the ward's rules ({@link WardTests}), how you arrived
 * ({@link ArrivalTests}), and discovery that pays (here). The Horizon line's pick and its landmark sites are in
 * {@code landmark_horizon_line}; its box in the System's layout test.
 */
final class WildsTests {
    private WildsTests() {}

    static void register() {
        WardTests.register();
        ArrivalTests.register();
        // SPEC §26.3 (Euan, 2026-10-26): inside Carillon's walls nothing is a discovery; one step outside, the biome is.
        // The city is a stand-in here (the walls end 8 blocks east of the start), put back in finally; isolated, so no
        // other test sees it.
        AwakenTests.registerIsolated("notice_city_discovers_nothing", 100, h -> {
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            BlockPos at = h.absolutePos(new BlockPos(1, 2, 1));
            int wall = at.getX() + 8;
            var real = CityHooks.installed();
            CityHooks.install(new CityHooks.Access() {
                @Override public boolean inCity(net.minecraft.world.level.Level level, BlockPos pos) {
                    return level == h.getLevel() && pos.getX() < wall;
                }
            });
            try {
                Progression.awaken(p);
                Profile prof = Progression.profile(p);
                BiomeDiscovery.forget(p.getUUID());
                for (int step = 0; step < 6; step++) {
                    p.teleportTo(h.getLevel(), at.getX() + step + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), 0, 0, true);
                    h.assertFalse(BiomeDiscovery.check(p), "walking the city discovers no biome (step " + step + ")");
                }
                h.assertTrue(prof.counter(BiomeDiscovery.COUNTER) == 0 && prof.counter("awaken:discoveries") == 0,
                        "nothing counted inside the walls: " + prof.counter("awaken:discoveries"));
                // the same biome, one step past the wall: now it is a first
                p.teleportTo(h.getLevel(), wall + 1.5, at.getY(), at.getZ() + 0.5, Set.of(), 0, 0, true);
                h.assertTrue(BiomeDiscovery.check(p), "one step outside the walls, the biome underfoot is a first");
                h.assertTrue(prof.counter(BiomeDiscovery.COUNTER) == 1, "counted once outside");
            } finally {
                CityHooks.install(real);
            }
            h.succeed();
        });

        AwakenTests.register("wilds_discovery_pays", 100, h -> {
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            BlockPos at = h.absolutePos(new BlockPos(1, 2, 1));
            p.teleportTo(h.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), 0, 0, true);
            Progression.awaken(p);
            Profile prof = Progression.profile(p);

            // a biome is a first once: the toast, a tenth of a level, the count, and the ledger sees where the XP came from
            BiomeDiscovery.forget(p.getUUID());
            h.assertTrue(BiomeDiscovery.check(p), "the biome underfoot is a first");
            BiomeDiscovery.forget(p.getUUID());
            h.assertFalse(BiomeDiscovery.check(p), "only once per biome");
            h.assertTrue(prof.counter(BiomeDiscovery.COUNTER) == 1, "counted: " + prof.counter(BiomeDiscovery.COUNTER));
            h.assertTrue(Progression.xpLedger(p).of(Progression.XpSource.DISCOVERY) > 0, "the ledger records discovery XP");
            Progression.worldXp(p, "kill", 0.02);
            h.assertTrue(Progression.xpLedger(p).ofWorld("kill") > 0
                    && Math.abs(Progression.xpLedger(p).ofWorld("kill") - Progression.xpLedger(p).of(Progression.XpSource.WORLD)) < 1e-6,
                    "world XP is split by its own source: " + Progression.xpLedger(p).world());

            // the counters that fed nothing now feed Titles...
            Progression.setCounter(p, "awaken:rifts_closed", 10);
            h.assertTrue(prof.titles().contains("awaken:riftwarden"), "ten rifts closed: Riftwarden");
            // ...Truths...
            String truth = "awaken:i_go_where_the_map_ends";
            prof.truthsOffered().add(truth);
            h.assertTrue(Progression.declareTruth(p, truth) && !prof.truthsTrue().contains(truth), "declared, not true yet");
            Progression.count(p, "awaken:landmarks_reached", 8);
            h.assertTrue(prof.truthsTrue().contains(truth), "eight landmarks: it's true now");
            // ...and exploration directives, counted from the moment one is taken on
            String directive = "awaken:explore_landmark";
            prof.directives().add(new Profile.DirectiveState(directive, true, 0, 99, Map.of("awaken:landmarks_reached", prof.counter("awaken:landmarks_reached"))));
            Progression.count(p, "awaken:landmarks_reached");
            h.assertTrue(prof.counter("awaken:directive_done/" + directive) == 1 && prof.directives().stream().noneMatch(d -> d.id().equals(directive)),
                    "a new landmark completes the directive");
            h.succeed();
        });
    }
}
