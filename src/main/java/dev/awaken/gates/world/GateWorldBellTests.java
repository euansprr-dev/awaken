package dev.awaken.gates.world;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.content.FloorWorld;
import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.content.Layout;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.content.Twist;
import dev.awaken.gates.worlds.GateWorld;
import dev.awaken.gates.worlds.GateWorlds;
import dev.awaken.progression.Rank;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * The Bell Crypt's worlds, one GameTest per world (SPEC §38.6, "How we'll know"): over many gates and every mood, each
 * floor is planned, carved chunk by chunk inside the budget, walked from the arrival to every room, cache, key and the
 * end (ropes and stairs included), its way lit to 7 or more, and its water held where it was put.
 */
public final class GateWorldBellTests {
    private GateWorldBellTests() {}

    static final String BELL = "awaken:bell_crypt/";
    static final List<String> MOODS = List.of("still", "tolling", "requiem", "storm");

    static GatePlan plan(long seed, int i) {
        Layout lay = Layout.values()[i % Layout.values().length];
        Twist tw = i % 4 == 3 ? Twist.GATE_WITHIN : Twist.NONE;
        return new GatePlan(seed, GatePlan.CONTENT_VERSION, Rank.of(i % 5), Theme.BELL_CRYPT, lay, List.of(), tw, 3 + i % 4,
                Theme.BELL_CRYPT.bossId(), Theme.HIVE, 0);
    }

    /** Every floor of the main gate in one world and mood (where its roles allow). */
    static GatePlan dressed(GatePlan p, String world, String mood) {
        GateWorld w = GateWorlds.get(world);
        List<FloorWorld> fw = new ArrayList<>();
        for (int f = 1; f <= p.floors(); f++) {
            boolean boss = f == p.floors();
            if (boss ? w.boss() : w.ordinary()) fw.add(new FloorWorld(0, f, world, mood, w.tall()));
        }
        return p.withWorlds(fw);
    }

    /** What's wrong with one world floor on foot, in its light and its water ("" when nothing). */
    static String check(GatePlan p, FloorLayout f, double[] worstMs, String dumpName) {
        WorldCheck w = new WorldCheck(p, f);
        double avg = w.carveNanosTotal / 1e6 / w.chunks, worst = w.carveNanosWorst / 1e6;
        worstMs[0] = Math.max(worstMs[0], avg);
        worstMs[1] = Math.max(worstMs[1], worst);
        Room s = f.room(f.start);
        Room.Feature e = s.feature(Room.FeatureKind.ENTRANCE);
        if (e == null) return "no arrival";
        int sy = f.baseY + s.dy + e.y();
        w.walk(e.x(), sy, e.z());
        if (!w.reached(e.x(), sy, e.z())) return "the arrival isn't somewhere you can stand (" + e.x() + " " + sy + " " + e.z() + ")";
        // a Rig room is crossed by its own mechanism: reach its way in, and you come out at its way on
        for (boolean grew = true; grew; ) {
            grew = false;
            for (Room r : f.rooms) {
                if (r.rig == null || r.pathIn == null || r.pathOut == null) continue;
                int[] in = outside(r.pathIn, f.baseY), out = outside(r.pathOut, f.baseY);
                int[] at = w.near(in[0], in[1], in[2], 2);
                if (at == null || w.reached(out[0], out[1], out[2])) continue;
                w.more(out[0], out[1], out[2], w.idx(at[0], at[1], at[2]));
                grew = w.reached(out[0], out[1], out[2]) || grew;
            }
        }
        StringBuilder bad = new StringBuilder();
        for (Room r : f.rooms) {
            int y = f.baseY + r.dy;
            int missed = 0;
            // a Rig room's floor is its mechanism's (wells, presses, collapsing tiles): only its doors are checked
            if (r.rig == null) for (int[] sp : r.spawnSpots) if (w.near(sp[0], y, sp[1], 1) == null) missed++;
            if (missed > 0) bad.append(" room ").append(r.id).append(' ').append(r.type).append(": ").append(missed).append(" of ")
                    .append(r.spawnSpots.size()).append(" spawn spots out of reach;");
            for (Room.Feature ft : r.features) {
                if (ft.kind() == Room.FeatureKind.UPDRAFT || ft.kind() == Room.FeatureKind.RIFT_WALL) continue;
                if (w.near(ft.x(), y + ft.y(), ft.z(), 2) == null)
                    bad.append(" room ").append(r.id).append(' ').append(ft.kind()).append(" at ").append(ft.x()).append(' ').append(ft.z()).append(" out of reach;");
            }
            if (r.spawnSpots.isEmpty() && r.features.isEmpty() && w.near(r.centerX(), y, r.centerZ(), 7) == null)
                bad.append(" room ").append(r.id).append(" (").append(r.type).append(") can't be walked into;");
        }
        List<int[]> leaks = w.leaks();
        if (!leaks.isEmpty()) bad.append(" water would flow at ").append(leaks.get(0)[0]).append(' ').append(leaks.get(0)[1]).append(' ')
                .append(leaks.get(0)[2]).append(" (").append(leaks.size()).append(");");
        // the way to the end, lit to 7 or more outside the built rooms (today's painter lights those)
        w.light();
        Room end = f.room(f.end);
        int[] goal = null;
        Room.Feature shrine = end.feature(Room.FeatureKind.SHRINE);
        if (shrine != null) goal = w.near(shrine.x(), f.baseY + end.dy + shrine.y(), shrine.z(), 2);
        if (goal == null && !end.spawnSpots.isEmpty()) goal = w.near(end.spawnSpots.get(0)[0], f.baseY + end.dy, end.spawnSpots.get(0)[1], 1);
        if (goal == null) bad.append(" the end can't be reached;");
        else {
            int dark = 0;
            int[] darkest = null;
            for (int[] n : w.route(goal[0], goal[1], goal[2])) {
                Room r = f.roomAtSlot(n[0] >> 4, n[2] >> 4);
                if (r == null || WorldPlan.built(r)) continue;
                int l = w.lightAt(n[0], n[1], n[2]);
                if (l < 7) {
                    dark++;
                    if (darkest == null) darkest = new int[]{n[0], n[1], n[2], l};
                }
            }
            if (dark > 0) bad.append(" the way is dark (").append(dark).append(" steps under 7, first at ").append(darkest[0]).append(' ')
                    .append(darkest[1]).append(' ').append(darkest[2]).append(": ").append(darkest[3]).append(");");
        }
        String dir = System.getenv("AWAKEN_WORLD_DUMP");
        if (dir != null && (dumpName != null || !bad.isEmpty())) {
            try {
                String name = dumpName != null ? dumpName : "FAIL-" + f.world.replace(':', '_').replace('/', '_') + "-" + f.floor;
                w.dump(java.nio.file.Path.of(dir), name, e.x(), sy, e.z());
                StringBuilder rooms = new StringBuilder();
                for (Room r : f.rooms) {
                    rooms.append(r.id).append(' ').append(r.type).append(" slots ").append(r.gx).append(',').append(r.gz).append(' ')
                            .append(r.gw).append('x').append(r.gh).append(" dy ").append(r.dy).append(r.rig != null ? " rig" : "").append(" doors");
                    for (Room.Door d : r.doors) rooms.append(" [").append(d.gx).append(',').append(d.gz).append(" side ").append(d.side).append(" to ").append(d.toRoom).append(']');
                    rooms.append('\n');
                }
                java.nio.file.Files.writeString(java.nio.file.Path.of(dir, name + "-rooms.txt"), rooms + bad.toString());
            } catch (java.io.IOException ex) {
                bad.append(" dump failed: ").append(ex.getMessage());
            }
        }
        return bad.toString();
    }

    /** Just outside a doorway, in the room it leads to: x, feet y, z. */
    static int[] outside(Room.Door d, int baseY) {
        int y = baseY + d.y;
        return switch (d.side) {
            case 0 -> new int[]{d.centerX(), y, d.centerZ() - 3};
            case 2 -> new int[]{d.centerX(), y, d.centerZ() + 2};
            case 1 -> new int[]{d.centerX() + 2, y, d.centerZ()};
            default -> new int[]{d.centerX() - 3, y, d.centerZ()};
        };
    }

    /** One world's test: plans over many gates and every mood, every one of its floors checked. */
    static void world(String test, String world, int plans) {
        AwakenTests.register(test, 2400, h -> {
            GateWorld w = GateWorlds.get(BELL + world);
            h.assertTrue(w != null, "the recipe " + world + " loads");
            double[] ms = new double[2];
            double planWorst = 0;
            int floors = 0;
            for (int i = 0; i < plans; i++) {
                GatePlan p = dressed(plan(0x5E11L + i * 104729L, i), BELL + world, MOODS.get(i % MOODS.size()));
                long t = System.nanoTime();
                GateLayout l = FloorBuilder.build(p);
                double planMs = (System.nanoTime() - t) / 1e6 / Math.max(1, p.worlds().size());
                planWorst = Math.max(planWorst, planMs);
                for (FloorLayout f : l.main) {
                    if (!f.isWorld()) {
                        h.assertTrue(p.worldFor(f.area, f.floor) == null, world + " gave no floor " + f.floor + " (" + WorldEngine.WHY.get() + ") of " + p + " (today's rooms instead)");
                        continue;
                    }
                    floors++;
                    String why = check(p, f, ms, i < 2 ? world + "-" + i + "-f" + f.floor : null);
                    h.assertTrue(why.isEmpty(), world + " floor " + f.floor + " (" + f.mood + ") of " + p + ":" + why);
                }
            }
            h.assertTrue(floors > 0, "some floors were " + world);
            h.assertTrue(ms[0] < 4 && ms[1] < 40, String.format("chunks carve inside the budget: average %.2f ms, worst %.2f ms", ms[0], ms[1]));
            h.assertTrue(planWorst < 50, String.format("floors plan inside the budget: worst %.1f ms", planWorst));
            dev.awaken.Awaken.LOG.info("[gates] {}: {} floors, chunks {} ms average, {} ms worst, plans {} ms worst", world, floors,
                    String.format("%.2f", ms[0]), String.format("%.2f", ms[1]), String.format("%.1f", planWorst));
            h.succeed();
        });
    }

    public static void register() {
        AwakenTests.register("gateworlds_bell_recipes", 200, h -> {
            ServerLevel gates = h.getLevel().getServer().getLevel(GatesRegistry.GATES);
            GateBiomeSource src = gates == null ? null : (GateBiomeSource) gates.getChunkSource().getGenerator().getBiomeSource();
            for (String id : List.of("sunken_belfry", "ossuary_ways", "bell_shaft", "field_of_bells", "bellmakers_rest")) {
                GateWorld w = GateWorlds.get(BELL + id);
                h.assertTrue(w != null && w.problem().isEmpty(), "the recipe " + id + " loads");
                h.assertTrue(w.tier() == GateWorld.Tier.SECRET || WorldEngine.of(w.kind()) != null, id + " has its engine");
                if (src != null) {
                    h.assertTrue(src.has(w.biome()), id + " shows its own biome " + w.biome());
                    for (GateWorld.Mood m : w.moods()) h.assertTrue(m.biome().isEmpty() || src.has(m.biome()), id + "'s mood " + m.id() + " has its biome");
                }
            }
            h.succeed();
        });
        world("gateworlds_belfry", "sunken_belfry", 8);
        world("gateworlds_ossuary", "ossuary_ways", 8);
    }
}
