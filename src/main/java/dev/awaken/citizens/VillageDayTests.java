package dev.awaken.citizens;

import dev.awaken.Awaken;
import dev.awaken.citizens.api.SettlementSpec;
import dev.awaken.citizens.api.Settlements;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.village.TestLand;
import dev.awaken.village.VillageRoster;
import dev.awaken.village.plan.VillagePlan;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A village's day on the people engine (SPEC §28.6, test {@code village_day}): a village planned on the test valley is
 * registered with W0's {@code Settlements} from its roster (households, jobs, places, rounds) and its day is run: at ten
 * the trades are at their work (the farmers in their fields, the smith at the forge, the washer at the well), at noon
 * they walk home for the meal (every walk has a route), in the evening the inn has its guests and the lamps are lit, at
 * midnight the village is asleep but for the Watch. The ledger alone (no bodies: the test world isn't the village's
 * ground); its cost logged.
 */
final class VillageDayTests {
    private VillageDayTests() {}

    static void register() {
        AwakenTests.registerIsolated("village_day", 3000, VillageDayTests::day);
    }

    private static GameTestAssertException running(long el) {
        return new GameTestAssertException(Component.literal("running, tick " + el), (int) el);
    }

    private static void day(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        VillagePlan plan = TestLand.at(31L, o.getX(), o.getY(), o.getZ()).plan("test:village_day", 4242L, true);
        h.assertTrue(plan != null, "a village fits the test valley");
        var spec = new SettlementSpec("test:village_day", plan.name, new BlockPos(plan.ox, plan.baseY, plan.oz), plan.radius(),
                VillageRoster.of(plan, level.getSeed()), false);
        Settlement s = Settlements.register(level, spec).map(x -> (Settlement) x).orElse(null);
        h.assertTrue(s != null, "the village's roster registers (its indices check out)");
        s.embody = false;
        s.work.edits = false;
        AwakenTests.Live[] player = {AwakenTests.survivalPlayer(h, "w3-day", BlockPos.ZERO.above(2))};
        Citizens.onlyPlayers = List.of(player[0].player());
        List<String> problems = new ArrayList<>();
        long[] cost = new long[2];
        boolean[] done = new boolean[1];
        long start = level.getGameTime();
        s.setClock(3800);
        h.succeedWhen(() -> {
            long el = level.getGameTime() - start;
            if (!done[0]) {
                try {
                    step(s, el, problems, cost);
                } catch (RuntimeException e) {
                    problems.add("tick " + el + ": " + e);
                    el = 2600;
                }
                if (el < 2500) throw running(el);
                done[0] = true;
                Citizens.onlyPlayers = null;
                player[0].leave();
                Settlements.unregister(s.id);
                Awaken.LOG.info(String.format(Locale.ROOT, "[village] day test: %s, %d people, %d places, ledger avg %.4f ms a tick, %d arrivals, %d no route",
                        plan.name, s.roster.citizens, s.roster.places, cost[1] == 0 ? 0 : cost[0] / 1e6 / cost[1], s.ledger.arrivals, s.ledger.noRoute));
            }
            h.assertTrue(problems.isEmpty(), String.join("; ", problems));
        });
    }

    /** How many of a trade are standing at places of a kind now. */
    private static int at(Settlement s, String job, byte kind) {
        int n = 0;
        for (int i = 0; i < s.roster.citizens; i++) {
            if (!Roster.JOBS[s.roster.job[i]].equals(job)) continue;
            int p = s.data.place[i];
            if (s.data.state[i] == CitizensData.STAND && p >= 0 && s.roster.pKind[p] == kind) n++;
        }
        return n;
    }

    private static int count(Settlement s, String job) {
        int n = 0;
        for (int i = 0; i < s.roster.citizens; i++) if (Roster.JOBS[s.roster.job[i]].equals(job)) n++;
        return n;
    }

    private static void step(Settlement s, long el, List<String> problems, long[] cost) {
        if (el > 40 && el < 2400) {
            cost[0] += s.ledgerNs;
            cost[1]++;
        }
        // 10:00, everyone seated where the hour wants them (a clock jump): at work
        if (el == 60) {
            if (!s.awake) problems.add("the village didn't wake for its player");
            int farmers = count(s, "farmer"), inField = at(s, "farmer", Roster.P_STAND);
            if (farmers == 0 || inField * 2 < farmers) problems.add("farmers in their fields at ten: " + inField + " of " + farmers);
            if (count(s, "smith") > 0 && at(s, "smith", Roster.P_ANVIL) == 0) problems.add("the smith isn't at the forge at ten");
            if (count(s, "washer") > 0 && at(s, "washer", Roster.P_WASH) == 0) problems.add("the washer isn't at the well at ten");
            // to just before the midday meal: they walk home over the next minute
            s.setClock(5200);
        }
        if (el == 1200) {
            if (s.ledger.arrivals == 0) problems.add("nobody walked home for the midday meal");
            if (s.ledger.noRoute > 0) problems.add(s.ledger.noRoute + " walks found no route");
            s.setClock(13600);
        }
        // 19:36: the inn has its guests, the lamps are lit
        if (el == 1300) {
            int guests = 0;
            for (int i = 0; i < s.roster.citizens; i++) {
                int p = s.data.place[i];
                if (p >= 0 && s.roster.pKind[p] == Roster.P_TAVERN_OUT && s.data.state[i] != CitizensData.INSIDE) guests++;
            }
            if (guests < 2) problems.add("the inn has " + guests + " guests at half past seven");
            int lit = 0;
            for (byte b : s.data.lamps) if (b == CitizensData.LAMP_LIT) lit++;
            if (s.data.lamps.length > 0 && lit == 0) problems.add("no lamp lit in the evening");
            s.setClock(18000);
        }
        // midnight: asleep but for the Watch
        if (el == 1400) {
            int inside = 0;
            for (int i = 0; i < s.roster.citizens; i++) if (s.data.state[i] == CitizensData.INSIDE) inside++;
            int watch = count(s, "watch");
            if (inside < (s.roster.citizens - watch) * 8 / 10) problems.add(inside + " of " + s.roster.citizens + " inside at midnight");
            if (watch > 0) {
                int out = 0;
                for (int i = 0; i < s.roster.citizens; i++)
                    if (Roster.JOBS[s.roster.job[i]].equals("watch") && s.data.state[i] != CitizensData.INSIDE) out++;
                if (out == 0) problems.add("the Watch is abed at midnight");
            }
        }
    }
}
