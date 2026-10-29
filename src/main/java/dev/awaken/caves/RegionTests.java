package dev.awaken.caves;

import dev.awaken.Awaken;
import dev.awaken.caves.skin.CaveSkin;
import dev.awaken.core.api.NoticeEvents;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.delve.Lode;
import dev.awaken.delve.LodePlans;
import dev.awaken.delve.Ore;
import dev.awaken.delve.api.Lodes;
import dev.awaken.progression.Progression;
import dev.awaken.signal.NoticeHistory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** The Living Deep's mining ties (SPEC §42.9): lodes by region, the regions' Journal lines, Codex words and trails. */
final class RegionTests {
    private RegionTests() {}

    static void register() {
        AwakenTests.register("caves_lodes_by_region", 1200, RegionTests::lodesByRegion);
        AwakenTests.register("caves_region_journal", 200, RegionTests::journal);
    }

    /** Regions sampled for the shares: a square of this many on a side round the origin. */
    static final int SIDE = 20;

    /**
     * With the regions as homes (read from this world's noise, as a new regions world reads it): every region's plan keeps
     * its lodes of each ore, and about twice the lodes of each metal lie in its own regions; with no homes, or homes that
     * favour everywhere, the plan is the old one. The homes are the rock's twins.
     */
    private static void lodesByRegion(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        // the test world is flat: the regions of a studio seed's overworld, read as a new regions world reads its own
        var access = level.registryAccess();
        var settings = access.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        RandomState rs = RandomState.create(access.lookupOrThrow(Registries.NOISE), CavesTests.STUDIO_SEEDS[0], settings);
        RegionHomes homes = RegionHomes.of(access, rs, settings.noiseRouter());
        h.assertTrue(homes != null, "the test world has the pack's band fields");
        // the homes are the ores the region's rock has twins for (and the Violet Deep's Deepglass)
        for (CaveRegion r : CaveRegion.values()) {
            var twins = CaveSkin.kit(r).twins().keySet();
            for (Ore o : RegionHomes.ores(r)) {
                h.assertTrue(o == Ore.DEEPGLASS && r == CaveRegion.VIOLET_DEEP || twins.contains(o.block(false)) || twins.contains(o.block(true)),
                        r.id + " favours " + o + " and its rock has a twin for it");
            }
            for (Block b : twins) {
                h.assertTrue(RegionHomes.ores(r).stream().anyMatch(o -> o.block(false) == b || o.block(true) == b), r.id + "'s twin " + b + " is a home ore");
            }
        }
        // the studio seed's plans (this world's columns, its wall lodes, are for its own seed)
        long seed = CavesTests.STUDIO_SEEDS[0];
        h.assertTrue(seed != level.getSeed(), "a seed other than the test world's");
        Lodes.Homes everywhere = new Lodes.Homes() {
            @Override
            public boolean favours(Ore ore, int x, int y, int z) {
                return true;
            }

            @Override
            public @Nullable String place(int x, int y, int z) {
                return null;
            }
        };
        Map<Ore, int[]> none = new EnumMap<>(Ore.class), with = new EnumMap<>(Ore.class);
        String named = null;
        long t0 = System.nanoTime();
        for (int rx = -SIDE / 2; rx < SIDE / 2; rx++) {
            for (int rz = -SIDE / 2; rz < SIDE / 2; rz++) {
                List<Lode> before = LodePlans.planWith(seed, rx, rz, null), after = LodePlans.planWith(seed, rx, rz, homes);
                if (rx == 0 && rz == 0) {
                    h.assertTrue(same(before, LodePlans.region(seed, rx, rz)), "no homes: the plan a world without regions makes");
                    h.assertTrue(same(before, LodePlans.planWith(seed, rx, rz, everywhere)), "homes everywhere: nothing rolls twice");
                    h.assertTrue(same(after, LodePlans.planWith(seed, rx, rz, homes)), "the same plan twice");
                }
                for (Ore o : Ore.values()) {
                    long a = before.stream().filter(l -> l.ore() == o).count(), b = after.stream().filter(l -> l.ore() == o).count();
                    if (a != b) h.fail(o + " lodes in region " + rx + " " + rz + ": " + a + " without homes, " + b + " with");
                }
                tally(none, before, homes);
                for (Lode l : tally(with, after, homes)) {
                    if (named == null) named = homes.place(BlockPos.getX(l.heart()), BlockPos.getY(l.heart()), BlockPos.getZ(l.heart()));
                }
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        StringBuilder log = new StringBuilder();
        int homeNone = 0, homeWith = 0, all = 0;
        for (Ore o : Ore.values()) {
            int[] n = none.get(o), w = with.get(o);
            if (n == null) continue;
            log.append(String.format(" %s %d/%d -> %d/%d;", o.name().toLowerCase(java.util.Locale.ROOT), n[0], n[1], w[0], w[1]));
            homeNone += n[0];
            homeWith += w[0];
            all += n[1];
        }
        Awaken.LOG.info("[caves] lodes by region on {} regions ({} ms): lodes in their ore's home without -> with homes:{} all {}/{} -> {}/{}",
                SIDE * SIDE, ms, log, homeNone, all, homeWith, all);
        int[] iron = none.get(Ore.IRON), ironWith = with.get(Ore.IRON);
        h.assertTrue(iron[0] >= 30, "enough iron lodes fall in the iron regions to measure: " + iron[0]);
        h.assertTrue(ironWith[0] >= iron[0] * 1.6, "about twice the iron lodes in its regions: " + iron[0] + " -> " + ironWith[0]);
        h.assertTrue(homeWith >= homeNone * 1.4, "more lodes in their homes, all ores: " + homeNone + " -> " + homeWith);
        h.assertTrue(named != null && named.startsWith("the "), "a lode at home is in a named region: " + named);
        h.succeed();
    }

    /** Counts a plan's lodes (ore: [at home, all]) and returns those at home. */
    private static List<Lode> tally(Map<Ore, int[]> into, List<Lode> plan, RegionHomes homes) {
        List<Lode> home = new java.util.ArrayList<>();
        for (Lode l : plan) {
            int[] c = into.computeIfAbsent(l.ore(), k -> new int[2]);
            c[1]++;
            if (homes.favours(l.ore(), BlockPos.getX(l.heart()), BlockPos.getY(l.heart()), BlockPos.getZ(l.heart()))) {
                c[0]++;
                home.add(l);
            }
        }
        return home;
    }

    private static String trail(GameTestHelper h, String name) {
        var res = h.getLevel().getServer().getResourceManager().getResource(Awaken.id("awaken/trail/" + name + ".json"));
        h.assertTrue(res.isPresent(), "the trail " + name + " ships");
        try (var in = res.get().open()) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean same(List<Lode> a, List<Lode> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i).ore() != b.get(i).ore() || a.get(i).heart() != b.get(i).heart() || !Arrays.equals(a.get(i).cells(), b.get(i).cells())) return false;
        }
        return true;
    }

    /**
     * The first time in a region: counted once, filed in the Journal (its id, the region's name, its words within the
     * Journal's bounds); a second time nothing. Every region's keepsakes are items, and the two trails name every region.
     */
    @SuppressWarnings("removal")
    private static void journal(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        Progression.awaken(p);
        h.assertTrue(RegionFinds.enter(p, CaveRegion.OCHRE_DEEP), "the first time in the Ochre Deep is a first");
        h.assertFalse(RegionFinds.enter(p, CaveRegion.OCHRE_DEEP), "the second isn't");
        h.assertTrue(Progression.profile(p).counter("awaken:discovered/" + RegionFinds.KEY + "ochre_deep") == 1, "counted once");
        var filed = p.getData(NoticeHistory.HISTORY).entries().stream().filter(e -> e.event().id().equals(RegionFinds.KEY + "ochre_deep")).toList();
        h.assertTrue(filed.size() == 1 && filed.getFirst().event().name().equals("The Ochre Deep")
                && filed.getFirst().event().kind() == NoticeEvents.Kind.DISCOVERY, "one Journal line, under the region's name: " + filed);
        for (CaveRegion r : CaveRegion.values()) {
            RegionFinds.journal(r); // within the Journal's bounds, or it throws
            RegionFinds.Lore l = RegionFinds.lore(r);
            for (String id : l.keepsake()) {
                h.assertTrue(BuiltInRegistries.ITEM.containsKey(Identifier.parse(id)), r.id + "'s keepsake " + id + " is an item");
            }
            h.assertTrue(l.keepsake().isEmpty() == (l.keep() == null), r.id + " names its keepsake when it has one");
        }
        // the trails, read as shipped (the trails' own tests swap the loaded catalogue while they run)
        String regions = trail(h, "the_regions_below"), keepsakes = trail(h, "keepsakes_from_below");
        for (CaveRegion r : CaveRegion.values()) {
            h.assertTrue(regions.contains("\"counter:awaken:discovered/" + RegionFinds.KEY + r.id + "\""), r.id + " is a step");
            List<String> keep = RegionFinds.lore(r).keepsake();
            if (keep.isEmpty()) continue;
            String token = String.join("|", keep.stream().map(i -> "holds:" + i).toList());
            h.assertTrue(keepsakes.contains("\"" + token + "\""), r.id + "'s keepsake is a step: " + token);
        }
        h.succeed();
    }
}
