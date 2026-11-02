package dev.awaken.mobs.entity;

import dev.awaken.core.api.MobRole;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.def.Tier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.profiling.ProfileResults;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What a fight costs the server (docs/fights/PLAN.md §2.6, the late-hits audit): a real survival player stands in a
 * walled-off floor while a boss, a pack, or both fight it, and every server tick is timed. The busiest case is also run
 * under the server's own section profiler. A measurement, not a pass/fail check: it writes
 * {@code fights-tick-cost.txt} and {@code fights-tick-profile.txt} in the server's folder and logs the table.
 */
public final class FightCostTests {
    private FightCostTests() {}

    /** Ticks a phase runs, and how many at its start are left out of the numbers (spawning, the intro roar). */
    static final int PHASE = 200, SETTLE = 40;
    static final String[] BOSSES = {"mother_of_spores", "signals_shadow", "drowned_archivist", "gearwright_colossus", "bell_warden"};
    static final int PACK = 12;

    record Phase(String name, List<String> bosses, int pack, boolean profile) {}

    public static void register() {
        AwakenTests.registerIsolated("fights_tick_cost", 2600, FightCostTests::tickCost);
    }

    static void tickCost(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        MinecraftServer server = level.getServer();
        AwakenTests.site(h, new BlockPos(-14, 0, -14), new BlockPos(14, 12, 14), Blocks.STONE.defaultBlockState());
        AwakenTests.Live live = AwakenTests.survivalPlayer(h, "fight_cost", new BlockPos(0, 1, 0));
        ServerPlayer p = live.player();
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1_000_000);
        p.setHealth(p.getMaxHealth());

        List<Phase> phases = new ArrayList<>();
        phases.add(new Phase("the player alone", List.of(), 0, false));
        for (String b : BOSSES) phases.add(new Phase(b, List.of(b), 0, false));
        phases.add(new Phase(PACK + " gate monsters", List.of(), PACK, false));
        phases.add(new Phase("mother_of_spores + " + PACK + " gate monsters", List.of("mother_of_spores"), PACK, true));
        List<MobDef> normals = Bestiary.all().stream().filter(d -> d.tier == Tier.NORMAL).sorted(Comparator.comparing(d -> d.id)).toList();

        List<Mob> fighters = new ArrayList<>();
        List<String> rows = new ArrayList<>();
        long[] times = new long[PHASE];
        int[] packets = new int[PHASE];
        Map<String, Integer> kinds = new HashMap<>();
        int[] at = {-1, 0}; // phase, tick in it
        ProfileResults[] profile = {null};
        BlockPos lo = h.absolutePos(new BlockPos(-14, 0, -14)), hi = h.absolutePos(new BlockPos(14, 12, 14));

        h.onEachTick(() -> {
            p.setHealth(p.getMaxHealth());
            p.setPos(h.absoluteVec(new Vec3(0.5, 1, 0.5)));
            int phase = at[0], t = at[1];
            if (phase >= 0 && phase < phases.size() && t > 0) {
                // the tick that just ended: the server's own measure, the one `/tick query` reads
                times[t - 1] = server.getTickTimesNanos()[(server.getTickCount() - 1 + 100) % 100];
                List<Object> sent = live.sent();
                packets[t - 1] = sent.size();
                if (t > SETTLE) for (Object o : sent) kinds.merge(o.getClass().getSimpleName(), 1, Integer::sum);
            } else live.sent();
            for (Mob m : fighters) if (m.isAlive()) m.setTarget(p);
            if (phase >= 0 && phase < phases.size() && t == PHASE) {
                rows.add(row(phases.get(phase), times, packets, kinds, fighters.size()));
                if (phases.get(phase).profile()) server.finishRecordingMetrics();
                fighters.forEach(Mob::discard);
                fighters.clear();
                AwakenTests.clearEntities(level, lo, hi.above(20));
                kinds.clear();
                at[0]++;
                at[1] = 0;
                return;
            }
            if (phase < 0 || (phase < phases.size() && t == 0)) {
                if (phase < 0) at[0] = phase = 0;
                Phase ph = phases.get(phase);
                for (int i = 0; i < ph.bosses().size(); i++) {
                    MobDef def = Bestiary.get(ph.bosses().get(i));
                    Mob m = Mobs.spawn(level, def, h.absoluteVec(new Vec3(0.5, 1, 9.5)), 10,
                            new MobRole(MobRole.Role.BOSS, 10, def.theme.id, Optional.of(def.bossId()), ""), Set.of());
                    if (m != null) fighters.add(m);
                }
                for (int i = 0; i < ph.pack(); i++) {
                    MobDef def = normals.get((i * 7) % normals.size());
                    double a = Math.PI * 2 * i / ph.pack();
                    Mob m = Mobs.spawn(level, def, h.absoluteVec(new Vec3(0.5 + Math.cos(a) * 11, 1, 0.5 + Math.sin(a) * 11)), 10,
                            new MobRole(MobRole.Role.NORMAL, 10, def.theme.id, Optional.empty(), ""), Set.of());
                    if (m != null) fighters.add(m);
                }
                if (ph.profile()) server.startRecordingMetrics(r -> profile[0] = r, path -> {});
            }
            if (phase < phases.size()) {
                at[1]++;
                return;
            }
            // every phase measured: wait for the profile (it arrives at the end of the tick after it was finished)
            if (profile[0] == null && at[1]++ < 40) return;
            String table = String.join("\n", rows);
            System.out.println("[fights_tick_cost]\n" + table);
            write(server.getServerDirectory().resolve("fights-tick-cost.txt"), table);
            if (profile[0] != null) write(server.getServerDirectory().resolve("fights-tick-profile.txt"), profile[0].getProfilerResults());
            live.leave();
            h.succeed();
        });
    }

    /** One phase's line: tick times over its settled ticks (mean, p95, worst), packets a tick to the player, the commonest. */
    static String row(Phase ph, long[] times, int[] packets, Map<String, Integer> kinds, int alive) {
        long[] t = Arrays.copyOfRange(times, SETTLE, PHASE);
        Arrays.sort(t);
        double mean = Arrays.stream(t).average().orElse(0) / 1e6, p95 = t[(int) (t.length * 0.95)] / 1e6, worst = t[t.length - 1] / 1e6;
        double pk = Arrays.stream(packets, SETTLE, PHASE).average().orElse(0);
        String top = kinds.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(5)
                .map(e -> e.getKey() + " " + Math.round(e.getValue() / (double) (PHASE - SETTLE)))
                .reduce((a, b) -> a + ", " + b).orElse("-");
        return String.format("%-44s fighters %2d | tick ms mean %6.2f p95 %6.2f worst %6.2f | packets/tick %6.1f (%s)",
                ph.name(), alive, mean, p95, worst, pk, top);
    }

    static void write(Path file, String text) {
        try {
            Files.writeString(file, text);
        } catch (IOException e) {
            System.out.println("[fights_tick_cost] couldn't write " + file + ": " + e);
        }
    }
}
