package dev.awaken.village.trouble;

import dev.awaken.Awaken;
import dev.awaken.city.CityPlan;
import dev.awaken.core.api.EconomyHooks;
import dev.awaken.core.api.Notices;
import dev.awaken.signal.Signal;
import dev.awaken.village.VillageService;
import dev.awaken.village.plan.VillagePlan;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The festival's rooftop race (SPEC §28.7): a route over the roofs round the green (the Ascent's movement is the way
 * up: mantle, wall-run, the rope), from the well over five roofs and back to the well, against the village's own best
 * runner's time. The marks glow for the runner only; beat the time and the prize is yours.
 */
final class Race {
    private Race() {}

    /** One runner: their festival, the route, the mark they're making for, when they set off (game time). */
    record Run(int trouble, long[] marks, int[] next, long start) {}

    static final Map<UUID, Run> RUNS = new HashMap<>();

    /** The racer at the well offers a run (or tells the time to beat while one runs). */
    static void offer(Plot.Ctx c, Trouble t, VillageService.Live l, ServerPlayer p, int rec) {
        long[] route = route(c, t, l);
        if (route.length < 3) {
            l.settlement().say(p, rec, "No race this year: the roofs are too far apart.", 100);
            return;
        }
        int record = (int) t.get("race_record", recordFor(route));
        String holder = recordHolder(l, t);
        if (RUNS.containsKey(p.getUUID())) {
            l.settlement().say(p, rec, "Go on, then! The marks are glowing.", 80);
            return;
        }
        RUNS.put(p.getUUID(), new Run(t.id, route, new int[]{0}, c.now()));
        TroubleNet.marks(p, route, 0, record);
        l.settlement().say(p, rec, "Over the roofs and back to the well. " + holder + "'s time is " + secs(record) + ". Go!", 120);
        c.level().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.5f, 1.2f);
        if (!t.helped(p.getUUID())) t.helpers.add(p.getUUID());
    }

    static String secs(int ticks) {
        return String.format(Locale.ROOT, "%.1f s", ticks / 20.0);
    }

    static String recordHolder(VillageService.Live l, Trouble t) {
        String by = t.text;
        if (!by.isEmpty()) return by;
        for (int i = 0; i < l.settlement().people(); i++) if (l.settlement().age(i) == 1) return TroubleText.given(l.settlement().personName(i));
        return "Tam";
    }

    /** The village's best: the route's length at a quick runner's pace (5.5 blocks a second over the roofs) and a breath. */
    static int recordFor(long[] route) {
        double len = 0;
        for (int i = 1; i < route.length; i++) len += Math.sqrt(BlockPos.of(route[i]).distSqr(BlockPos.of(route[i - 1])));
        return (int) (len / 5.5 * 20) + 60;
    }

    /** From the well, over the roofs of up to five houses round the green (in order round it), and back. */
    static long[] route(Plot.Ctx c, Trouble t, VillageService.Live l) {
        int n = (int) t.get("rt_n", 0);
        if (n > 0) {
            long[] out = new long[n];
            for (int i = 0; i < n; i++) out[i] = t.get("rt" + i, 0);
            return out;
        }
        VillagePlan p = l.plan();
        Vec3 w = TroubleDirector.well(l);
        List<double[]> roofs = new ArrayList<>();
        for (VillagePlan.Building b : p.buildings) {
            CityPlan.Plot q = p.plots.get(b.plot());
            double cx = p.ox + (q.x0() + q.x1()) / 2.0 + 0.5, cz = p.oz + (q.z0() + q.z1()) / 2.0 + 0.5;
            double d = Math.hypot(cx - w.x, cz - w.z);
            if (d > p.greenR + 26) continue;
            // the ridge's top over the plot's middle
            BlockPos top = new BlockPos((int) Math.floor(cx), p.baseY + q.lift() + q.top() + 4, (int) Math.floor(cz));
            if (!c.level().isLoaded(top)) continue;
            while (top.getY() > p.baseY + q.lift() + 2 && c.level().getBlockState(top).isAir()) top = top.below();
            roofs.add(new double[]{Math.atan2(cz - w.z, cx - w.x), top.above().asLong(), d});
        }
        roofs.sort((a, b) -> Double.compare(a[2], b[2]));
        List<double[]> pick = new ArrayList<>(roofs.subList(0, Math.min(5, roofs.size())));
        pick.sort((a, b) -> Double.compare(a[0], b[0]));
        List<Long> out = new ArrayList<>();
        for (double[] r : pick) out.add((long) r[1]);
        out.add(BlockPos.containing(w).asLong());
        long[] a = new long[out.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = out.get(i);
            t.put("rt" + i, a[i]);
        }
        t.put("rt_n", a.length);
        return a;
    }

    /** Every other tick: has a runner reached their mark? */
    static void tick(MinecraftServer server) {
        if (RUNS.isEmpty() || server.getTickCount() % 2 != 0) return;
        ServerLevel level = server.overworld();
        TroubleState st = TroubleState.get(server);
        for (var it = RUNS.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            Run run = e.getValue();
            Trouble t = st.byId(run.trouble());
            if (p == null || t == null || level.getGameTime() - run.start() > 20 * 180) {
                if (p != null) TroubleNet.marks(p, new long[0], 0, 0);
                it.remove();
                continue;
            }
            BlockPos mark = BlockPos.of(run.marks()[run.next()[0]]);
            boolean last = run.next()[0] == run.marks().length - 1;
            if (p.distanceToSqr(Vec3.atBottomCenterOf(mark)) > (last ? 3.2 * 3.2 : 2.4 * 2.4)) continue;
            run.next()[0]++;
            level.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.2f, 0.9f + run.next()[0] * 0.1f);
            if (run.next()[0] < run.marks().length) {
                TroubleNet.marks(p, run.marks(), run.next()[0], (int) t.get("race_record", recordFor(run.marks())));
                continue;
            }
            it.remove();
            finish(level, st, t, p, (int) (level.getGameTime() - run.start()));
        }
    }

    private static void finish(ServerLevel level, TroubleState st, Trouble t, ServerPlayer p, int ticks) {
        TroubleNet.marks(p, new long[0], 0, 0);
        VillageService.Live l = VillageService.live(t.village);
        long[] route = new long[(int) t.get("rt_n", 0)];
        for (int i = 0; i < route.length; i++) route[i] = t.get("rt" + i, 0);
        int record = (int) t.get("race_record", recordFor(route));
        String key = "race_" + p.getUUID().hashCode();
        long best = t.get(key, Long.MAX_VALUE);
        if (ticks < best) t.put(key, ticks);
        if (ticks < record) {
            String was = l != null ? recordHolder(l, t) : "the village";
            t.put("race_record", ticks);
            t.text = p.getName().getString();
            t.put("race_win_hi", p.getUUID().getMostSignificantBits());
            t.put("race_win_lo", p.getUUID().getLeastSignificantBits());
            EconomyHooks.pay(p, 6, Awaken.id("village/festival/" + t.id + "/race"));
            if (l != null) VillageService.standing(p, l, 6, "rooftop race");
            Signal.toast(p, Component.literal("Over the roofs in " + secs(ticks) + " · you beat " + was + "'s " + secs(record)), Notices.Tier.IMPORTANT,
                    "village_festival");
            level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f);
        } else {
            Signal.toast(p, Component.literal("Over the roofs in " + secs(ticks) + " · " + secs(record) + " to beat"), Notices.Tier.AMBIENT, "village_festival");
        }
        st.setDirty();
    }

    static @Nullable String winner(Plot.Ctx c, Trouble t, VillageService.Live l) {
        long hi = t.get("race_win_hi", 0), lo = t.get("race_win_lo", 0);
        if (hi == 0 && lo == 0) return null;
        return t.text;
    }

    static void stopAll(Plot.Ctx c, Trouble t) {
        for (var it = RUNS.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (e.getValue().trouble() != t.id) continue;
            ServerPlayer p = c.level().getServer().getPlayerList().getPlayer(e.getKey());
            if (p != null) TroubleNet.marks(p, new long[0], 0, 0);
            it.remove();
        }
    }
}
