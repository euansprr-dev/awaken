package dev.awaken.village.trouble;

import dev.awaken.Awaken;
import dev.awaken.city.CityPlan;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.EconomyHooks;
import dev.awaken.core.api.MovementHooks;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.living.api.Quests;
import dev.awaken.village.Requests;
import dev.awaken.village.TestLand;
import dev.awaken.village.VillageService;
import dev.awaken.village.api.Troubles;
import dev.awaken.village.api.Villages;
import dev.awaken.village.plan.VillagePlan;
import dev.awaken.world.fauna.LairRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Village troubles' GameTests (prefix {@code village_}, SPEC §28.7), one per mechanism: Taken from the alarm to the
 * reunion ({@code village_trouble_taken}); a Burning missed and its Rebuild ({@code village_burning_rebuild}); the
 * director's rules: a young world, calm villages, one a village, two near a player, the Journal only after Help
 * ({@code village_director_caps}).
 */
final class TroubleTests {
    private TroubleTests() {}

    static void register() {
        AwakenTests.registerIsolated("village_trouble_taken", 600, TroubleTests::taken);
        AwakenTests.registerIsolated("village_burning_rebuild", 600, TroubleTests::burningRebuild);
        AwakenTests.registerIsolated("village_director_caps", 600, TroubleTests::caps);
    }

    private static boolean journalHas(ServerPlayer p, String title) {
        for (Quests.JournalRow r : TroubleDirector.journalRows(p)) if (r.title().startsWith(title)) return true;
        return false;
    }

    /**
     * Taken: a beast's lair near a hamlet takes someone (away from the village's life, the alarm, a trail to the lair);
     * the Journal holds it once you say Help; in the nest they're bound; cut free they're on your back (the body's moves
     * stop); home in the square they're in their parent's arms; it pays, the lair is cleared for good, the API says who
     * you brought home, and they're back in their village's day.
     */
    private static void taken(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        VillagePlan plan = TestLand.at(4242L, o.getX(), o.getY(), o.getZ()).plan("test:w4_taken", 4243L, false);
        h.assertTrue(plan != null, "a hamlet fits the test valley");
        AwakenTests.Live live = AwakenTests.survivalPlayer(h, "w4-taken", BlockPos.ZERO.above(2));
        ServerPlayer p = live.player();
        boolean quiet = TroubleDirector.quiet;
        TroubleDirector.quiet = true;
        VillageService.Live l = null;
        Trouble t = null;
        long key = 0x5A5A0000L ^ o.asLong();
        int[] forced = null;
        TroubleState st = TroubleState.get(level.getServer());
        try {
            l = VillageService.register(level, plan);
            h.assertTrue(l != null, "the hamlet's people start");
            // a beast's lair east of it, its ground loaded (its beast out)
            int dx = plan.radius() + 70;
            // its chunk ticking, as it is when a player comes near (the nest's body must be in the world to be found)
            forced = new int[]{(plan.ox + dx) >> 4, plan.oz >> 4};
            level.setChunkForced(forced[0], forced[1], true);
            level.getChunk(forced[0], forced[1]);
            long until = System.nanoTime() + 20_000_000_000L;
            while (!level.isPositionEntityTicking(new BlockPos(plan.ox + dx, 64, plan.oz)) && System.nanoTime() < until)
                if (!level.getChunkSource().pollTask()) java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, plan.ox + dx, plan.oz);
            BlockPos den = new BlockPos(plan.ox + dx, y, plan.oz);
            LairRegistry.get(level.getServer()).add(key, "old_antler", den);
            Plot.Ctx c = TroubleDirector.ctx(level, l.key());
            t = TroubleDirector.start(level, l, TroubleKind.TAKEN, "lair:" + key);
            h.assertTrue(t != null, "Taken begins from the lair near the hamlet");
            h.assertTrue(t.person >= 0 && t.kin >= 0 && l.settlement().isAway(t.person), "someone is taken (away from the village's day), someone asks");
            h.assertTrue(t.placeName.equals("the Antler Grove") && Taken.trailLength(t) >= 4, "a trail runs to the Antler Grove: " + Taken.trailLength(t));
            // found, never listed: the Journal holds it only once you've said Help
            h.assertTrue(!journalHas(p, "Taken"), "not in the Journal before Help");
            TroubleDirector.answer(p, TroubleDirector.ACTION + ":" + t.id, true);
            h.assertTrue(t.helped(p.getUUID()) && journalHas(p, "Taken"), "in the Journal once you said Help");
            // the nest: bound
            Taken.forceNest(c, t, l);
            Rescuee r = Taken.rescuee(c, t);
            h.assertTrue(r != null && r.mode() == Rescuee.BOUND, "bound in the nest");
            // cut free (holding use: about nine uses), and onto your back
            for (int i = 0; i < 14 && r.mode() == Rescuee.BOUND; i++) TroubleDirector.touched(level, p, r);
            h.assertTrue(r.carried() && r.carrier() == p.getId(), "cut free, they hold on to your back");
            h.assertTrue(Rescuee.carrying(p) && MovementHooks.burdened(p), "carrying them: slower, and the body's moves stop");
            // home: into the square, off your back, into their parent's arms
            Vec3 edge = TroubleDirector.green(l, 0.3, plan.greenR + 3);
            p.teleportTo(edge.x + 1, edge.y, edge.z);
            r.setDown(false);
            r.teleportTo(edge.x, edge.y, edge.z);
            h.assertTrue(!Rescuee.carrying(p), "set down: your pace back");
            Taken.homecoming(c, t, l, r);
            h.assertTrue(t.stage == Taken.HOME, "home in the square");
            long em = EconomyHooks.balance(p);
            r.mode(Rescuee.EMBRACE);
            t.put("reunion", level.getGameTime() - 60);
            TroubleDirector.plot(t).tick(c, t);
            h.assertTrue(EconomyHooks.balance(p) > em, "always paid: " + em + " -> " + EconomyHooks.balance(p));
            int person = t.person;
            h.assertTrue(Troubles.rescued(p).stream().anyMatch(x -> x.rec() == person && x.trouble().equals("taken")), "the API knows who you brought home");
            h.assertTrue(Troubles.lairSealed(level, den), "the lair is cleared for good");
            t.put("reunion", level.getGameTime() - 400);
            TroubleDirector.plot(t).tick(c, t);
            h.assertTrue(st.byId(t.id) == null, "it's over");
            h.assertTrue(!l.settlement().isAway(person), "they're back in their village's day");
            List<String> hist = Villages.history(level.getServer(), l.key());
            h.assertTrue(!hist.isEmpty() && hist.getLast().contains("back from the Antler Grove"), "the village remembers: " + (hist.isEmpty() ? "" : hist.getLast()));
        } finally {
            TroubleDirector.quiet = quiet;
            if (t != null && st.byId(t.id) != null) TroubleDirector.end(TroubleDirector.ctx(level, t.village), t);
            LairRegistry.get(level.getServer()).remove(key);
            if (forced != null) level.setChunkForced(forced[0], forced[1], false);
            if (l != null) {
                st.memory(l.key()).sealed.clear();
                VillageService.stop(l);
            }
            live.leave();
        }
        h.succeed();
    }

    /**
     * The Burning missed: the house on the raid's side is charred (every changed block into the village's damage record,
     * its household camped by the well); the Rebuild then posts its three notices, and filled, the masons raise
     * scaffolding and put the house back exactly, the camp struck and the village's history saying so.
     */
    private static void burningRebuild(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        VillagePlan plan = TestLand.at(5151L, o.getX(), o.getY(), o.getZ()).plan("test:w4_burning", 5152L, false);
        h.assertTrue(plan != null, "a hamlet fits the test valley");
        AwakenTests.Live live = AwakenTests.survivalPlayer(h, "w4-burning", BlockPos.ZERO.above(2));
        ServerPlayer p = live.player();
        boolean quiet = TroubleDirector.quiet;
        TroubleDirector.quiet = true;
        VillageService.Live l = null;
        TroubleState st = TroubleState.get(level.getServer());
        List<Trouble> made = new ArrayList<>();
        try {
            l = VillageService.register(level, plan);
            h.assertTrue(l != null, "the hamlet's people start");
            // a house where the plan puts its first home: walls of planks on log corners, a plank roof
            VillagePlan.Building home = plan.buildings.stream().filter(b -> b.use().equals("house")).findFirst().orElseThrow();
            CityPlan.Plot q = plan.plots.get(home.plot());
            int x0 = plan.ox + q.x0(), x1 = plan.ox + q.x1(), z0 = plan.oz + q.z0(), z1 = plan.oz + q.z1(), g = plan.baseY + q.lift();
            for (int cx = (x0 - 2) >> 4; cx <= (x1 + 2) >> 4; cx++) for (int cz = (z0 - 2) >> 4; cz <= (z1 + 2) >> 4; cz++) level.getChunk(cx, cz);
            AwakenTests.clearToSky(level, new BlockPos(x0 - 1, g, z0 - 1), new BlockPos(x1 + 1, g + q.top() + 4, z1 + 1));
            Map<BlockPos, BlockState> house = new HashMap<>();
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    boolean wall = x == x0 || x == x1 || z == z0 || z == z1, corner = (x == x0 || x == x1) && (z == z0 || z == z1);
                    house.put(new BlockPos(x, g, z), Blocks.OAK_PLANKS.defaultBlockState());
                    house.put(new BlockPos(x, g + 4, z), Blocks.SPRUCE_PLANKS.defaultBlockState());
                    if (wall) for (int yy = 1; yy <= 3; yy++) house.put(new BlockPos(x, g + yy, z), corner ? Blocks.OAK_LOG.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState());
                }
            }
            house.forEach((pos, s) -> level.setBlock(pos, s, Block.UPDATE_CLIENTS));
            // the raid came while nobody stood with them
            long day = PlayDays.current(level.getServer());
            h.assertTrue(Burning.coming(level, l.key(), -1, "Riftborn Camp", 4, day + 1), "the camp's raid becomes the village's Burning");
            Trouble burn = st.of(l.key());
            h.assertTrue(burn != null && burn.kind.equals(TroubleKind.BURNING.id), "the Burning is on");
            made.add(burn);
            Plot.Ctx c = TroubleDirector.ctx(level, l.key());
            Vec3 mid = new Vec3((x0 + x1) / 2.0, g, (z0 + z1) / 2.0);
            Burning.burnHouses(c, l, st.memory(l.key()), 1, BlockPos.containing(mid), "riftborn");
            for (int i = 0; i < 4; i++) Lasting.tick(c, l);
            int dmg = Villages.damage(level.getServer(), l.key()).size();
            int changed = 0;
            for (var e : house.entrySet()) if (!level.getBlockState(e.getKey()).equals(e.getValue())) changed++;
            h.assertTrue(dmg >= house.size() / 3 && changed >= house.size() / 3, "the house is charred, every block written down: " + dmg + " recorded, " + changed
                    + " of " + house.size() + " changed");
            h.assertTrue(!st.memory(l.key()).homeless.isEmpty(), "its household camps by the well");
            TroubleDirector.end(c, burn);
            // the Rebuild: three notices
            Trouble rebuild = TroubleDirector.start(level, l, TroubleKind.REBUILD, "damage:" + dmg);
            h.assertTrue(rebuild != null, "the Rebuild begins from the damage");
            made.add(rebuild);
            String[] notices = rebuild.text.split("\\|");
            h.assertTrue(notices.length == 3, "timber, stone and iron on the board");
            for (String id : notices) h.assertTrue(Requests.progress(l, id) != null, "posted: " + id);
            // filled: the masons begin, scaffolding round the house
            for (String id : notices) Requests.unpost(level.getServer(), l, id);
            TroubleDirector.plot(rebuild).tick(c, rebuild);
            h.assertTrue(rebuild.stage == Rebuild.BUILDING, "the masons begin");
            List<Long> scaffold = rebuild.laid.stream().filter(pos -> level.getBlockState(BlockPos.of(pos)).is(Blocks.SCAFFOLDING)).toList();
            h.assertTrue(!scaffold.isEmpty(), "scaffolding goes up round the house");
            // three play-days of work, all at once
            rebuild.since = level.getGameTime() - Rebuild.WORK - 10;
            for (int i = 0; i < 200 && st.byId(rebuild.id) != null; i++) TroubleDirector.plot(rebuild).tick(c, rebuild);
            h.assertTrue(st.byId(rebuild.id) == null, "rebuilt: it's over");
            TroubleState.Memory mem = st.memory(l.key());
            int wrong = 0;
            for (var e : house.entrySet()) {
                if (mem.beams.contains(e.getKey().asLong())) continue; // the beam of your colour over the door
                if (!level.getBlockState(e.getKey()).equals(e.getValue())) wrong++;
            }
            h.assertTrue(wrong == 0, "put back exactly: " + wrong + " blocks differ");
            h.assertTrue(Villages.damage(level.getServer(), l.key()).isEmpty() && mem.homeless.isEmpty() && mem.camp.isEmpty(), "no damage left, the camp struck");
            h.assertTrue(scaffold.stream().noneMatch(pos -> level.getBlockState(BlockPos.of(pos)).is(Blocks.SCAFFOLDING)), "the scaffolding's down");
            List<String> hist = Villages.history(level.getServer(), l.key());
            h.assertTrue(hist.stream().anyMatch(s -> s.contains("was rebuilt")), "the village remembers: " + hist);
        } finally {
            TroubleDirector.quiet = quiet;
            for (Trouble t : made) if (st.byId(t.id) != null) TroubleDirector.end(TroubleDirector.ctx(level, t.village), t);
            if (l != null) VillageService.stop(l);
            live.leave();
        }
        h.succeed();
    }

    /**
     * The director's rules: nothing in a world's first two hours; nothing with calm villages on, though a festival still
     * comes; with it off, at most one trouble a village and two near a player; and a trouble is in your Journal only once
     * you've said Help.
     */
    private static void caps(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var server = level.getServer();
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        AwakenTests.Live live = AwakenTests.survivalPlayer(h, "w4-caps", BlockPos.ZERO.above(2));
        ServerPlayer p = live.player();
        List<VillageService.Live> ls = new ArrayList<>();
        TroubleState st = TroubleState.get(server);
        boolean quiet = TroubleDirector.quiet;
        long hours = TroubleDirector.firstHours;
        boolean calm = TroubleContent.calm(level);
        TroubleDirector.quiet = true;
        p.teleportTo(o.getX() + 150.5, o.getY() + 2, o.getZ() + 0.5);
        try {
            long day = PlayDays.current(server);
            for (int i = 0; i < 3; i++) {
                // three hamlets 150 apart, the player at the middle one: all awake, all within 1,500 of them
                VillagePlan plan = TestLand.at(6161L + i, o.getX() + i * 150, o.getY(), o.getZ()).plan("test:w4_caps_" + i, 6170L + i, false);
                h.assertTrue(plan != null, "hamlet " + i + " fits");
                VillageService.Live l = VillageService.register(level, plan);
                h.assertTrue(l != null, "hamlet " + i + "'s people start");
                ls.add(l);
                // damage enough for a Rebuild: the cause in the world
                for (int k = 0; k < 30; k++) VillageService.damaged(l, new BlockPos(plan.ox + k, plan.baseY + 40, plan.oz + 3), Blocks.OAK_PLANKS.defaultBlockState(), day);
                st.memory(l.key()).lastEnd = -100;
                st.memory(l.key()).festival = day + 50;
            }
            // a young world: nothing yet
            TroubleDirector.firstHours = Long.MAX_VALUE;
            TroubleDirector.consider(level, st, day);
            h.assertTrue(count(st, ls) == 0, "none in a world's first two hours");
            // calm villages: no troubles, but the festival comes
            TroubleDirector.firstHours = 0;
            level.getGameRules().set(TroubleContent.CALM_VILLAGES.get(), true, server);
            TroubleDirector.consider(level, st, day);
            h.assertTrue(count(st, ls) == 0, "none with calm villages on");
            st.memory(ls.getFirst().key()).festival = day;
            Festival.consider(new Plot.Ctx(level, st, ls.getFirst(), level.getGameTime(), day, 12000), ls.getFirst());
            Trouble fest = st.festival(ls.getFirst().key());
            h.assertTrue(fest != null, "the festival still comes with calm villages on");
            TroubleDirector.end(TroubleDirector.ctx(level, fest.village), fest);
            // calm off: one a village, two near a player
            level.getGameRules().set(TroubleContent.CALM_VILLAGES.get(), false, server);
            TroubleDirector.consider(level, st, day);
            h.assertTrue(count(st, ls) == 2, "two troubles near a player, no more: " + count(st, ls));
            VillageService.Live busy = null;
            for (VillageService.Live l : ls) if (st.of(l.key()) != null) busy = l;
            h.assertTrue(busy != null && TroubleDirector.start(level, busy, TroubleKind.REBUILD, "damage") == null, "one trouble a village");
            // found, never listed: in the Journal only after Help
            Trouble t = st.of(busy.key());
            TroubleKind k = TroubleKind.of(t.kind);
            h.assertTrue(!journalHas(p, k.title), "not in the Journal before Help");
            TroubleDirector.answer(p, TroubleDirector.ACTION + ":" + t.id, true);
            h.assertTrue(journalHas(p, k.title), "in the Journal after Help");
            Awaken.LOG.info("[trouble] caps test: {} troubles at the three hamlets", count(st, ls));
        } finally {
            TroubleDirector.quiet = quiet;
            TroubleDirector.firstHours = hours;
            level.getGameRules().set(TroubleContent.CALM_VILLAGES.get(), calm, server);
            for (VillageService.Live l : ls) {
                for (Trouble t : st.troubles()) if (t.village.equals(l.key())) TroubleDirector.end(TroubleDirector.ctx(level, t.village), t);
                VillageService.stop(l);
            }
            live.leave();
        }
        h.succeed();
    }

    private static int count(TroubleState st, List<VillageService.Live> ls) {
        int n = 0;
        for (VillageService.Live l : ls) if (st.of(l.key()) != null) n++;
        return n;
    }
}
