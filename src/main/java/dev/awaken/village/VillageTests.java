package dev.awaken.village;

import dev.awaken.Awaken;
import dev.awaken.city.CityPlan;
import dev.awaken.city.SettlementPaint;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.village.plan.Terrain;
import dev.awaken.village.plan.VillagePlan;
import dev.awaken.village.plan.VillagePlanner;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;

import java.util.Locale;

/**
 * Living villages' GameTests (prefix {@code village_}), one per mechanism: the planner and painter are deterministic
 * and within their budgets ({@code village_plan_deterministic}); a village's day runs on the people engine
 * ({@code village_day}); trade in emeralds, standing and the inn ({@code village_trade}).
 */
final class VillageTests {
    private VillageTests() {}

    static void register() {
        AwakenTests.register("village_plan_deterministic", 400, VillageTests::deterministic);
        AwakenTests.registerIsolated("village_trade", 400, VillageTests::trade);
        dev.awaken.village.road.RoadTests.register();
        AwakenTests.register("wild_tamers", 400, WildTamers::test);
    }

    /**
     * Trade in emeralds (decision 5): EconomyHooks counts, takes and pays emeralds once per claim; a trader's prices move
     * with standing (a stranger over, kin under); trades earn standing a few a day; a notice is filled from what you
     * carry and pays once; a room at the inn makes its bed where you wake, once.
     */
    private static void trade(GameTestHelper h) {
        var level = h.getLevel();
        net.minecraft.core.BlockPos o = h.absolutePos(net.minecraft.core.BlockPos.ZERO);
        VillagePlan plan = TestLand.at(57L, o.getX(), o.getY(), o.getZ()).plan("test:village_trade", 777L, true);
        h.assertTrue(plan != null, "a village fits the test valley");
        AwakenTests.Live live = AwakenTests.survivalPlayer(h, "w3-trade", net.minecraft.core.BlockPos.ZERO.above(2));
        var p = live.player();
        VillageService.Live l = null;
        try {
            l = VillageService.register(level, plan);
            h.assertTrue(l != null, "the village's people start");
            var em = dev.awaken.core.api.EconomyHooks.installed();
            p.getInventory().clearContent();
            p.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, 5));
            h.assertTrue(em.balance(p) == 5, "five emeralds in hand: " + em.balance(p));
            h.assertTrue(em.charge(p, 3) && em.balance(p) == 2, "three taken");
            h.assertTrue(!em.charge(p, 5) && em.balance(p) == 2, "five can't be taken from two");
            var claim = dev.awaken.Awaken.id("test/village_trade/" + level.getGameTime());
            h.assertTrue(em.pay(p, 4, claim) && em.balance(p) == 6, "paid four");
            h.assertTrue(!em.pay(p, 4, claim) && em.balance(p) == 6, "never paid twice for one claim");
            // the smith's pickaxe: a stranger pays over, kin under
            var deals = Trade.deals("smith", plan.family, plan.seed, 0, level);
            h.assertTrue(deals != null, "the smith trades");
            Trade.Deal pick = deals.stream().filter(d -> d.id().equals("pickaxe")).findFirst().orElseThrow();
            int stranger = new Trade.Shop(p, l, 0, deals, 0).getOffers().get(deals.indexOf(pick)).getCostA().getCount();
            int kin = new Trade.Shop(p, l, 0, deals, 3).getOffers().get(deals.indexOf(pick)).getCostA().getCount();
            h.assertTrue(stranger > pick.costCount() && kin < pick.costCount(), "prices show standing: stranger " + stranger + ", kin " + kin
                    + " (base " + pick.costCount() + ")");
            // trades earn standing, four a day
            for (int k = 0; k < 6; k++) VillageService.traded(p, l, 0, pick);
            h.assertTrue(l.rec().points(p.getUUID()) == 4, "four trades a day count toward standing: " + l.rec().points(p.getUUID()));
            // a notice filled from what you carry pays once and counts toward standing
            var board = Requests.board(level.getServer(), l);
            h.assertTrue(!board.isEmpty(), "the board has notices");
            VillageState.Request q = board.getFirst();
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(q.item));
            p.getInventory().add(new net.minecraft.world.item.ItemStack(item, q.need));
            long before = em.balance(p);
            int pts = l.rec().points(p.getUUID());
            Requests.deliver(p, l, q.id);
            h.assertTrue(em.balance(p) == before + q.pay, "filled, it pays " + q.pay + ": " + before + " -> " + em.balance(p));
            h.assertTrue(l.rec().points(p.getUUID()) == pts + q.standing, "and counts toward standing");
            h.assertTrue(!l.rec().requests.contains(q), "and comes off the board");
            // a room at the inn: its bed is where you wake, once
            CityPlanCheck inn = CityPlanCheck.inn(plan);
            h.assertTrue(inn != null, "the village has an inn");
            var bed = new net.minecraft.core.BlockPos(plan.ox + inn.x, plan.baseY + inn.y, plan.oz + inn.z);
            var head = net.minecraft.world.level.block.Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState().setValue(net.minecraft.world.level.block.AbstractBedBlock.PART,
                    net.minecraft.world.level.block.state.properties.BedPart.HEAD);
            level.setBlock(bed, head, 3);
            Inn.grant(p, l, 0);
            var cfg = p.getRespawnConfig();
            h.assertTrue(cfg != null && cfg.respawnData().globalPos().pos().equals(bed), "the inn's bed is where you wake: " + cfg);
            Inn.onRespawn(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent(p, false));
            var after = p.getRespawnConfig();
            h.assertTrue(after == null || !after.respawnData().globalPos().pos().equals(bed), "once: after waking there it's your own again");
            h.assertTrue(!Inn.has(p, level.getServer()), "the room is spent");
        } finally {
            if (l != null) VillageService.stop(l);
            live.leave();
        }
        h.succeed();
    }

    /**
     * Studio (when {@code village-survey.request} exists in the server's folder): the settlement grid planned over the
     * real overworld's noise for a seed (no chunks generated: the generator's own estimate, as world generation reads
     * it), with how many anchors found a site, what the planning costs, and dumps of the first few for
     * {@code tools/city/render.py} with their land under them.
     */
    static void survey(GameTestHelper h, long seed, int radius) throws java.io.IOException {
        var level = h.getLevel();
        var reg = level.registryAccess();
        var ns = reg.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE_SETTINGS)
                .getOrThrow(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD);
        var preset = reg.lookupOrThrow(net.minecraft.core.registries.Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getOrThrow(net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        var biomes = net.minecraft.world.level.biome.MultiNoiseBiomeSource.createFromPreset(preset);
        var gen = new net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator(biomes, ns);
        var rs = net.minecraft.world.level.levelgen.RandomState.create(reg.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE), seed, ns.value());
        var t = dev.awaken.village.world.GenTerrain.of(gen, level, rs);
        java.nio.file.Path dir = level.getServer().getServerDirectory();
        int[] centre = {0, 0};
        int planned = 0, none = 0, dumped = 0;
        long ns0 = 0;
        StringBuilder sb = new StringBuilder();
        var found = dev.awaken.village.world.FirstHamlet.search(seed, centre, t);
        sb.append("the first hamlet's spot up the road: ").append(found == null ? "none" : found.x() + "," + found.z()).append('\n');
        if (found != null) {
            VillagePlan fp = VillagePlanner.plan(dev.awaken.village.world.Plans.request(found), t);
            sb.append("  ").append(fp == null ? "no plan: " + VillagePlanner.WHY.get() : fp.toString()).append('\n');
            if (fp != null) SettlementPaint.dump(dir.resolve("survey-first.bin"), fp, seed, fp.chunks(), (x, z) -> {
                int top = t.top(fp.ox + x, fp.oz + z) - fp.baseY;
                return t.water(fp.ox + x, fp.oz + z) > 0 ? top - 2000 : top;
            });
        }
        for (var a : dev.awaken.village.world.SettlementGrid.near(seed, 0, 0, radius, centre)) {
            long t0 = System.nanoTime();
            VillagePlan p = VillagePlanner.plan(dev.awaken.village.world.Plans.request(a), t);
            long dt = System.nanoTime() - t0;
            ns0 += dt;
            if (p == null) {
                none++;
                sb.append(a.key()).append(' ').append(a.kind()).append(" at ").append(a.x()).append(',').append(a.z()).append(": no site (").append(VillagePlanner.WHY.get()).append(")\n");
                continue;
            }
            planned++;
            sb.append(String.format(Locale.ROOT, "%s in %.0f ms%n", p, dt / 1e6));
            if (dumped < 8) {
                final VillagePlan fp = p;
                SettlementPaint.dump(dir.resolve("survey-" + dumped + ".bin"), p, seed, p.chunks(), (x, z) -> {
                    int top = t.top(fp.ox + x, fp.oz + z) - fp.baseY;
                    return t.water(fp.ox + x, fp.oz + z) > 0 ? top - 2000 : top;
                });
                dumped++;
            }
        }
        // the land round the first hamlet's anchor (top, ~ marks water), sixteen blocks a cell
        var first = dev.awaken.village.world.SettlementGrid.first(seed, centre);
        sb.append("\nround the first hamlet's anchor ").append(first.x()).append(',').append(first.z()).append(":\n");
        for (int dz = -112; dz <= 112; dz += 16) {
            for (int dx = -112; dx <= 112; dx += 16) {
                int top = t.top(first.x() + dx, first.z() + dz);
                sb.append(String.format(Locale.ROOT, "%4d%s", top, t.water(first.x() + dx, first.z() + dz) > 0 ? "~" : " "));
            }
            sb.append('\n');
        }
        String head = String.format(Locale.ROOT, "survey seed %d within %d: %d planned, %d no site, %.0f ms a plan on average%n", seed, radius, planned, none,
                (planned + none) == 0 ? 0 : ns0 / 1e6 / (planned + none));
        java.nio.file.Files.writeString(dir.resolve("village-survey.txt"), head + sb);
        Awaken.LOG.info("[village] {}", head.trim());
    }

    /** Where to put a bed in a plan's inn for the test (its first room cell, a row over the floor). */
    record CityPlanCheck(int x, int y, int z) {
        static CityPlanCheck inn(VillagePlan p) {
            for (CityPlan.Plot q : p.plots) if (q.use().equals("inn")) return new CityPlanCheck(q.x0() + 2, q.lift() + 1, q.z0() + 2);
            return null;
        }
    }

    static VillagePlanner.Request request(String key, long seed, VillagePlan.Kind kind, boolean first) {
        return new VillagePlanner.Request(key, seed, 0, 0, kind, first, -124, -124, 139, 139, (x, z) -> false);
    }

    /** A canonical text of a plan: everything the painter and the people read, in order. */
    static String text(VillagePlan p) {
        StringBuilder sb = new StringBuilder(p.toString());
        for (CityPlan.Plot q : p.plots) sb.append('|').append(q);
        for (VillagePlan.Street s : p.streets) sb.append('|').append(s.rank()).append(java.util.Arrays.toString(s.x())).append(java.util.Arrays.toString(s.lift()));
        for (CityPlan.Prop q : p.props) sb.append('|').append(q);
        for (VillagePlan.Person q : p.people) sb.append('|').append(q);
        for (VillagePlan.Lamp q : p.lamps) sb.append('|').append(q);
        for (VillagePlan.Field q : p.fields) sb.append('|').append(q);
        return sb.toString();
    }

    private static void deterministic(GameTestHelper h) {
        Terrain land = new TestLand(7);
        long t0 = System.nanoTime();
        VillagePlan v = VillagePlanner.plan(request("test:village", 1234L, VillagePlan.Kind.VILLAGE, false), land);
        long planNs = System.nanoTime() - t0;
        h.assertTrue(v != null, "a village fits the test valley");
        VillagePlan again = VillagePlanner.plan(request("test:village", 1234L, VillagePlan.Kind.VILLAGE, false), land);
        h.assertTrue(again != null && text(v).equals(text(again)), "the same request over the same land makes the same plan");
        VillagePlan hamlet = VillagePlanner.plan(request("test:hamlet", 99L, VillagePlan.Kind.HAMLET, true), land);
        h.assertTrue(hamlet != null, "a hamlet fits the test valley");
        // the studio: a dump for tools/city/render.py when asked
        java.nio.file.Path dir = h.getLevel().getServer().getServerDirectory();
        if (java.nio.file.Files.exists(dir.resolve("village-survey.request"))) {
            try {
                survey(h, 26102610L, 2600);
            } catch (java.io.IOException e) {
                h.fail("survey: " + e);
                return;
            }
        }
        if (java.nio.file.Files.exists(dir.resolve("road-survey.request"))) {
            try {
                String[] ask = java.nio.file.Files.readString(dir.resolve("road-survey.request")).trim().split("\\s+");
                long seed = ask.length > 0 && !ask[0].isEmpty() ? Long.parseLong(ask[0]) : 26102610L;
                int radius = ask.length > 1 ? Integer.parseInt(ask[1]) : 2400;
                dev.awaken.village.road.RoadStudio.survey(h.getLevel(), seed, radius, dir);
            } catch (java.io.IOException | RuntimeException e) {
                Awaken.LOG.error("[road] survey failed", e);
                h.fail("road survey: " + e);
                return;
            }
        }
        if (java.nio.file.Files.exists(dir.resolve("village-dump.request"))) {
            try {
                for (VillagePlan p : new VillagePlan[]{v, hamlet}) {
                    SettlementPaint.dump(dir.resolve(p == v ? "village-dump.bin" : "hamlet-dump.bin"), p, 42L, p.chunks(), (x, z) -> {
                        int top = land.top(p.ox + x, p.oz + z) - p.baseY;
                        return land.water(p.ox + x, p.oz + z) > 0 ? top - 2000 : top;
                    });
                }
                SettlementPaint.dump(dir.resolve("village-rooms.bin"), v, 42L, v.chunks(), (x, z) -> land.top(v.ox + x, v.oz + z) - v.baseY, 4);
                java.nio.file.Files.writeString(dir.resolve("village-dump.txt"), text(v).replace("|", "\n") + "\n\n" + text(hamlet).replace("|", "\n"));
            } catch (java.io.IOException e) {
                h.fail("dump: " + e);
                return;
            }
        }
        int homes = (int) v.buildings.stream().filter(b -> b.use().equals("house")).count();
        int hHomes = (int) hamlet.buildings.stream().filter(b -> b.use().equals("house")).count();
        h.assertTrue(homes >= 6 && homes <= 20, "a village has 8-20 homes (a few may not fit): " + homes);
        h.assertTrue(v.people.size() >= 16 && v.people.size() <= 60, "a village houses 20-50 people: " + v.people.size());
        h.assertTrue(hHomes >= 3 && hHomes <= 6, "a hamlet has 3-6 homes: " + hHomes);
        h.assertTrue(hamlet.people.size() >= 6 && hamlet.people.size() <= 20, "a hamlet houses 8-16 people: " + hamlet.people.size());
        h.assertTrue(v.buildings.stream().anyMatch(b -> b.use().equals("inn")) && v.forge != null, "a village has its inn and its forge");
        h.assertTrue(v.bell != null && v.bell.tower() && hamlet.bell != null && !hamlet.bell.tower(), "a tower in the village, a post in the hamlet");
        // painted twice, chunk by chunk, the same blocks; never more than a few lights a chunk
        long[] chunks = v.chunks();
        int maxLights = 0, cells = 0;
        long p0 = System.nanoTime();
        for (long c : chunks) {
            int cx = ChunkPos.getX(c), cz = ChunkPos.getZ(c);
            long a = SettlementPaint.fingerprint(v, cx, cz, 42L), b = SettlementPaint.fingerprint(again, cx, cz, 42L);
            h.assertTrue(a == b, "chunk " + cx + "," + cz + " paints the same twice");
            int[] census = SettlementPaint.census(v, cx, cz, 42L);
            maxLights = Math.max(maxLights, census[1]);
            cells += census[0];
        }
        long paintNs = (System.nanoTime() - p0) / Math.max(1, chunks.length * 3);
        Awaken.LOG.info(String.format(Locale.ROOT, "[village] test plan: %s | %s | planned in %.1f ms (test land), %d chunks, %.2f ms a chunk, "
                + "%d cells, at most %d lights a chunk", v, hamlet, planNs / 1e6, chunks.length, paintNs / 1e6, cells, maxLights));
        h.assertTrue(maxLights <= 6, "at most a handful of light sources a chunk: " + maxLights);
        h.succeed();
    }
}
