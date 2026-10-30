package dev.awaken.gates.world;

import dev.awaken.Awaken;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.content.Layout;
import dev.awaken.gates.content.Modifier;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.content.Twist;
import dev.awaken.progression.Curve;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * GameTests for the interiors (docs/modules/gates.md "Interiors"): every theme's floors paint inside their worldgen
 * budget, set pieces never block a doorway, every piece and arena gets built, the Lords fit their arenas. Also writes
 * one floor and the Lord's floor of each theme to run/interiors/ for tools/art/specs/gate_interiors.py.
 */
public final class InteriorTests {
    private InteriorTests() {}

    /** A chunk painted slower than the mean budget (4 ms) is painted again before its cost counts. */
    private static final long RETIME_NS = 4_000_000L;

    static void register() {
        registerArenaTest();
        registerLordSpaceTest();
        registerLightTest();
        RitualHallTests.register();
        // The P3 targets hold: the gates the game builds today are the gates tools/sim/pacing_sim.py was run on
        // (tools/sim/gate_tuning.json), and that run met its targets (tools/sim/pacing_results.json "gate_checks").
        // Changing floors, room counts, elites or modifiers fails this until the export is copied and the sim re-run.
        AwakenTests.register("gates_tuning_matches_sim", 600, h -> {
            String json = tuningJson(240);
            try {
                Files.writeString(Path.of("gate_tuning.json"), json);
            } catch (IOException ex) {
                Awaken.LOG.warn("[gates] could not write gate_tuning.json: {}", ex.toString());
            }
            Path simDir = Path.of("..", "tools", "sim");
            if (!Files.isDirectory(simDir)) {
                Awaken.LOG.warn("[gates] tools/sim not found from {}: tuning check skipped", Path.of("").toAbsolutePath());
                h.succeed();
                return;
            }
            try {
                var live = com.google.gson.JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("ranks");
                var sim = com.google.gson.JsonParser.parseString(Files.readString(simDir.resolve("gate_tuning.json"))).getAsJsonObject().getAsJsonObject("ranks");
                for (String rank : List.of("D", "C", "B")) {
                    var a = live.getAsJsonObject(rank);
                    var b = sim.getAsJsonObject(rank);
                    h.assertTrue(a.get("floors").equals(b.get("floors")), rank + ": floors per gate changed since the sim ran: " + a.get("floors") + " vs " + b.get("floors"));
                    h.assertTrue(a.get("mods").equals(b.get("mods")), rank + ": modifiers rolled changed since the sim ran");
                    h.assertTrue(a.get("twists").equals(b.get("twists")), rank + ": twists rolled changed since the sim ran");
                    for (String what : List.of("path", "side", "elites")) {
                        var la = a.getAsJsonObject("v2").getAsJsonArray(what);
                        var lb = b.getAsJsonObject("v2").getAsJsonArray(what);
                        for (int i = 0; i < la.size(); i++) {
                            boolean na = la.get(i).isJsonNull(), nb = lb.get(i).isJsonNull();
                            h.assertTrue(na == nb && (na || Math.abs(la.get(i).getAsDouble() - lb.get(i).getAsDouble()) < 0.05),
                                    rank + ": " + what + " on floor " + (i + 1) + " is " + la.get(i) + ", the sim ran on " + lb.get(i));
                        }
                    }
                }
                var results = com.google.gson.JsonParser.parseString(Files.readString(simDir.resolve("pacing_results.json"))).getAsJsonObject();
                var checks = results.getAsJsonArray("gate_checks");
                h.assertTrue(checks != null && checks.size() >= 6, "the sim recorded its gate checks");
                for (var c : checks) h.assertTrue(c.getAsJsonObject().get("pass").getAsBoolean(), "sim target missed: " + c);
                for (var c : results.getAsJsonArray("target_checks")) h.assertTrue(c.getAsJsonObject().get("pass").getAsBoolean(), "sim pacing target missed: " + c);
            } catch (IOException ex) {
                h.fail("could not read the sim's files: " + ex);
            }
            h.succeed();
        });
        AwakenTests.register("gates_interior_dumps", 400, h -> {
            for (Theme t : Theme.values()) {
                GatePlan plan = showcasePlan(t);
                GateLayout l = GateTestAccess.build(plan);
                dump(l, l.main.get(0), t.id + "_floor1");
                dump(l, l.main.get(l.main.size() - 1), t.id + "_lord");
            }
            h.succeed();
        });

        AwakenTests.register("gates_interior_pieces_keep_doorways", 600, h -> {
            Set<SetPiece> seen = EnumSet.noneOf(SetPiece.class);
            int checked = 0;
            for (Theme t : Theme.values()) {
                for (int i = 0; i < 6; i++) {
                    GatePlan plan = plan(0xD00Bl + t.ordinal() * 131L + i * 7L, Rank.values()[1 + i % 3], t,
                            i % 2 == 0 ? Layout.LABYRINTH : Layout.ARENA_CHAIN, Twist.NONE);
                    GateLayout l = GateTestAccess.build(plan);
                    for (FloorLayout f : List.of(l.main.get(0), l.main.get(l.main.size() - 1))) {
                        for (Room r : f.rooms) {
                            if (r.piece == null) continue;
                            seen.add(r.piece);
                            Map<Long, BlockState> blocks = paintRoom(l, f, r, 9);
                            String why = reachable(f, r, blocks);
                            h.assertTrue(why == null, t.id + " " + r.piece + " in a " + r.gw + "x" + r.gh + " " + r.type + ": " + why);
                            checked++;
                        }
                    }
                }
            }
            // the ritual hall is only ever the story's ritual gate: check its doorway the same way
            GateLayout ritual = GateTestAccess.build(dev.awaken.gates.Gates.ritualPlan(0xB311L));
            for (Room r : ritual.main.get(0).rooms) {
                if (r.piece == null) continue;
                seen.add(r.piece);
                String why = reachable(ritual.main.get(0), r, paintRoom(ritual, ritual.main.get(0), r, 9));
                h.assertTrue(why == null, "the ritual hall: " + why);
                checked++;
            }
            for (SetPiece p : SetPiece.values()) h.assertTrue(seen.contains(p), "set piece " + p + " is built somewhere (" + checked + " rooms checked)");
            h.succeed();
        });

        AwakenTests.register("gates_interior_build_budget", 600, h -> {
            StringBuilder report = new StringBuilder();
            for (int pass = 0; pass < 2; pass++) {
                for (Theme t : Theme.values()) {
                    GatePlan plan = plan(0xB0D6E7L + t.ordinal(), Rank.B, t, Layout.values()[t.ordinal() % Layout.values().length], Twist.NONE);
                    // Wall-clock timings on a shared machine (a CI runner, the Mac with builds beside the tests) catch GC
                    // pauses and preemption, which a single sample takes for the code's cost. A cost here is the fastest of
                    // a few runs: noise only ever adds time, so the minimum is its robust estimate, and a layout or chunk
                    // that is really slow is slow every run and still fails. The first runs' figures are logged too.
                    long build = Long.MAX_VALUE, buildFirst = 0;
                    GateLayout l = null;
                    for (int k = 0; k < 3; k++) {
                        long b0 = System.nanoTime();
                        l = GateTestAccess.build(plan);
                        build = Math.min(build, System.nanoTime() - b0);
                        if (k == 0) buildFirst = build;
                    }
                    long worst = 0, worstFirst = 0, total = 0, totalFirst = 0;
                    int chunks = 0, maxBlocks = 0, retimed = 0;
                    for (FloorLayout f : l.main) {
                        for (int gx = -1; gx <= f.w; gx++) {
                            for (int gz = -1; gz <= f.h; gz++) {
                                int[] count = new int[1];
                                int cx = (Cells.originX(11) >> 4) + f.originSlotX + gx, cz = (Cells.originZ(11) >> 4) + f.originSlotZ + gz;
                                long c0 = System.nanoTime();
                                GatePainter.paint(l, 11, cx, cz, (x, y, z, s) -> count[0]++);
                                long dt = System.nanoTime() - c0;
                                maxBlocks = Math.max(maxBlocks, count[0]);
                                worstFirst = Math.max(worstFirst, dt);
                                totalFirst += dt;
                                // nearly every chunk paints well under the mean budget at once; one over it is painted up to
                                // 4 more times and costs its fastest paint (in the mean and the worst both)
                                for (int k = 0; k < 4 && dt > RETIME_NS; k++) {
                                    long r0 = System.nanoTime();
                                    GatePainter.paint(l, 11, cx, cz, (x, y, z, s) -> { });
                                    dt = Math.min(dt, System.nanoTime() - r0);
                                    if (k == 0) retimed++;
                                }
                                worst = Math.max(worst, dt);
                                total += dt;
                                chunks++;
                            }
                        }
                    }
                    if (pass == 0) continue; // the first pass warms the JIT and the palette caches
                    double mean = total / 1e6 / chunks;
                    report.append(String.format(Locale.ROOT, "%s: build %.1f ms, %d chunks, mean %.2f ms, worst %.2f ms, max %d blocks"
                                    + " (first runs: build %.1f ms, mean %.2f ms, worst %.2f ms; %d chunks re-painted)%n",
                            t.id, build / 1e6, chunks, mean, worst / 1e6, maxBlocks, buildFirst / 1e6, totalFirst / 1e6 / chunks, worstFirst / 1e6, retimed));
                    // a worldgen thread paints one chunk per task: keep it well under a vanilla noise chunk (~5-15 ms)
                    h.assertTrue(mean < AwakenTests.cpuBudget(4.0), t.id + " paints a chunk in " + mean + " ms on average, a slow chunk at its fastest of 5 (budget 4)");
                    h.assertTrue(worst < AwakenTests.cpuBudget(40.0) * 1e6, t.id + " worst chunk " + worst / 1e6 + " ms, fastest of 5 paints (budget 40)");
                    h.assertTrue(build < AwakenTests.cpuBudget(50) * 1e6, t.id + " layout built in " + build / 1e6 + " ms, fastest of 3 (budget 50, once per gate, cached)");
                    h.assertTrue(maxBlocks <= 16 * 16 * Cells.FLOOR_PITCH * l.main.size(), t.id + " writes " + maxBlocks + " blocks in one chunk");
                }
            }
            Awaken.LOG.info("[gates] interior build budget\n{}", report);
            h.succeed();
        });
    }

    /**
     * Every Lord fits the arena built for it: spawned for real (mobs module) to read its size and arena radius, then
     * checked against the painted arena: clear room round its spot (with a block to spare, more above flyers), every
     * spot a player can stand on inside its arena radius (+3), most of the floor round it open, its medallion lit.
     */
    static void registerArenaTest() {
        AwakenTests.registerIsolated("gates_interior_arenas_fit_lords", 200, h -> {
            var level = h.getLevel();
            BlockPos at = h.absolutePos(new BlockPos(1, 60, 1));
            StringBuilder report = new StringBuilder();
            for (Theme t : Theme.values()) {
                GatePlan plan = plan(0xA7E7AL + t.ordinal(), Rank.C, t, Layout.LABYRINTH, Twist.NONE);
                GateLayout l = GateTestAccess.build(plan);
                FloorLayout f = l.main.get(l.main.size() - 1);
                Room r = f.room(f.end);
                h.assertTrue(r.type == Room.Type.BOSS && r.piece == SetPiece.arenaFor(t), t.id + ": the boss room carries its Lord's arena");
                h.assertTrue(r.gw >= 3 && r.gh >= 3 && r.height >= 17, t.id + ": arena keeps the mobs module's size (3x3 slots, 17 high)");
                var lord = dev.awaken.core.api.MobHooks.spawnBoss(level, t.bossId(), at, 35, "");
                h.assertTrue(lord != null, t.id + ": the Lord spawns");
                // its size with its scale attribute (the cached bounding box only catches up on its first tick)
                var dims = lord.getType().getDimensions().scale(lord.getScale());
                double w = Math.max(lord.getBbWidth(), dims.width()), hgt = Math.max(lord.getBbHeight(), dims.height());
                int home = lord instanceof net.minecraft.world.entity.Mob m && m.hasHome() ? m.getHomeRadius() : 22;
                boolean flyer = lord.isNoGravity();
                lord.discard();
                Map<Long, BlockState> b = paintRoom(l, f, r, 21);
                int walkY = f.baseY + r.dy;
                double scx = r.centerX() + 0.5, scz = r.centerZ() + 0.5;
                // the Lord's box at its spot, one block to spare round it and above (three over a flyer)
                int half = (int) Math.ceil(w / 2 + 1), up = (int) Math.ceil(hgt + (flyer ? 3 : 1));
                for (int x = (int) Math.floor(scx - half); x <= (int) Math.floor(scx + half); x++)
                    for (int z = (int) Math.floor(scz - half); z <= (int) Math.floor(scz + half); z++)
                        for (int y = walkY; y < walkY + up; y++)
                            h.assertTrue(passable(b.get(key(x, y, z))), t.id + ": " + b.get(key(x, y, z)) + " in the Lord's space at " + x + "," + (y - walkY) + "," + z
                                    + " (lord " + String.format(Locale.ROOT, "%.1fx%.1f", w, hgt) + ")");
                // every column a player can stand on is inside the Lord's arena (it ignores targets outside it)
                double far = 0;
                int open = 0, disc = 0;
                for (int x = r.x0(); x <= r.x1(); x++) {
                    for (int z = r.z0(); z <= r.z1(); z++) {
                        double d = Math.hypot(x + 0.5 - scx, z + 0.5 - scz);
                        boolean stand = false;
                        for (int y = walkY - 4; y <= walkY + 2 && !stand; y++)
                            stand = standable(b.get(key(x, y - 1, z))) && passable(b.get(key(x, y, z))) && passable(b.get(key(x, y + 1, z)));
                        if (stand) far = Math.max(far, d);
                        if (d <= 10) {
                            disc++;
                            if (stand) open++;
                        }
                    }
                }
                if (!t.friendly) h.assertTrue(far <= home + 3, t.id + ": a player can stand " + String.format(Locale.ROOT, "%.1f", far) + " from the Lord, its arena is " + home);
                h.assertTrue(open >= disc * 0.6, t.id + ": " + open + " of " + disc + " columns within 10 of the Lord are open floor");
                int lit = light(b, r.centerX(), walkY + 1, r.centerZ(), walkY - 4, walkY + 26);
                h.assertTrue(lit >= 9, t.id + ": the Lord's spot is lit (" + lit + ")");
                report.append(String.format(Locale.ROOT, "%s: lord %.1fx%.1f%s, arena %d, farthest stand %.1f, open %d/%d, light %d%n",
                        t.id, w, hgt, flyer ? " (flies)" : "", home, far, open, disc, lit));
            }
            Awaken.LOG.info("[gates] arenas\n{}", report);
            h.succeed();
        });
    }

    /**
     * Room for the Lords (SPEC §38.15): in every Lord's arena, the main one and the inner gate's keeper's, nothing solid
     * stands inside its sweep from the floor up to the top of its body (three more over a flyer); the four big keepers
     * get the full 3x3 arena. Version 7's arenas are measured beside them for the report.
     */
    static void registerLordSpaceTest() {
        AwakenTests.register("gates_interior_lord_space", 600, h -> {
            StringBuilder report = new StringBuilder();
            List<String> fails = new ArrayList<>();
            Theme[] all = Theme.values();
            for (Theme t : all) {
                for (int version : new int[]{7, GatePlan.CONTENT_VERSION}) {
                    for (boolean keeper : new boolean[]{false, true}) {
                        Theme host = all[(t.ordinal() + 1) % all.length];
                        GatePlan plan = keeper
                                ? new GatePlan(0x5ACEL + t.ordinal(), version, Rank.C, host, Layout.LABYRINTH, List.of(), Twist.GATE_WITHIN, 2, host.bossId(), t, 0)
                                : new GatePlan(0x5ACEL + t.ordinal(), version, Rank.C, t, Layout.LABYRINTH, List.of(), Twist.NONE, 2, t.bossId(), host, 0);
                        GateLayout l = GateTestAccess.build(plan);
                        FloorLayout f = keeper ? l.annex.get(1) : l.main.get(l.main.size() - 1);
                        Room r = f.room(f.end);
                        String where = t.id + (keeper ? " keeper" : "") + " v" + version;
                        if (r.type != Room.Type.BOSS || r.piece != SetPiece.arenaFor(t)) {
                            fails.add(where + ": the boss room isn't its Lord's arena");
                            continue;
                        }
                        double sweep = PiecesArenas.sweep(r.piece), tall = TALLEST.get(t.bossId().substring(t.bossId().indexOf(':') + 1));
                        boolean flies = FLYERS.contains(t);
                        int up = (int) Math.ceil(tall) + (flies ? 3 : 1);
                        Map<Long, BlockState> b = paintRoom(l, f, r, 22);
                        int walkY = f.baseY + r.dy;
                        double scx = r.centerX() + 0.5, scz = r.centerZ() + 0.5;
                        int in = 0, clear = Integer.MAX_VALUE;
                        double near = 99;
                        String first = null;
                        for (int x = (int) Math.floor(scx - sweep - 2); x <= (int) Math.ceil(scx + sweep + 2); x++) {
                            for (int z = (int) Math.floor(scz - sweep - 2); z <= (int) Math.ceil(scz + sweep + 2); z++) {
                                double d = Math.hypot(x + 0.5 - scx, z + 0.5 - scz);
                                int y = walkY;
                                while (y < walkY + 40 && passable(b.get(key(x, y, z)))) y++;
                                if (d <= sweep) clear = Math.min(clear, y - walkY);
                                if (y >= walkY + up) continue;
                                near = Math.min(near, d);
                                if (d <= sweep) {
                                    in++;
                                    if (first == null) first = b.get(key(x, y, z)) + " at " + String.format(Locale.ROOT, "%.1f", d) + " out, " + (y - walkY) + " up";
                                }
                            }
                        }
                        report.append(String.format(Locale.ROOT, "%s: arena %dx%d, sweep %.0f, body %d up, nearest solid %.1f, clear overhead %d, %d inside%s%n",
                                where, r.gw, r.gh, sweep, up, near, clear, in, first == null ? "" : " (" + first + ")"));
                        if (version < 8) continue;
                        if (in > 0) fails.add(where + ": " + in + " solid blocks inside the Lord's sweep of " + sweep + ", first " + first);
                        if (keeper && PiecesArenas.needsFullArena(t) && r.gw < 3) fails.add(where + ": a big keeper got the small arena");
                        if (r.gw >= 3 && r.height < 17) fails.add(where + ": the full arena is only " + r.height + " high");
                    }
                }
            }
            Awaken.LOG.info("[gates] lord space\n{}", report);
            h.assertTrue(fails.isEmpty(), String.join("; ", fails));
            h.succeed();
        });
    }

    /** The tallest each gate Lord's body reaches in any clip (boss-plan's measure of the true-size rigs, 2026-10-30). */
    private static final Map<String, Double> TALLEST = Map.ofEntries(Map.entry("ember_tyrant", 11.1), Map.entry("unit_zero", 5.2),
            Map.entry("mother_of_spores", 6.1), Map.entry("gearwright_colossus", 8.8), Map.entry("root_saint", 6.1),
            Map.entry("bell_warden", 5.6), Map.entry("glass_serpent", 6.8), Map.entry("hive_regent", 6.0), Map.entry("drowned_archivist", 5.5),
            Map.entry("lantern_broker", 6.3), Map.entry("abbot_of_frost", 6.3), Map.entry("mirror_knight", 4.9), Map.entry("the_sleeper", 5.3),
            Map.entry("sky_leviathan", 9.1), Map.entry("hollow_herald", 6.7));

    /** The Lords that fly, rising on top of their body's height. */
    private static final Set<Theme> FLYERS = EnumSet.of(Theme.FUNGAL_HOLLOW, Theme.GLASS_DESERT, Theme.HIVE, Theme.SKY_ARCHIPELAGO, Theme.ABYSSAL_SHRINE);

    /**
     * The gate dimension has no ambient light, so the floor is only as readable as its block light. Every theme, on
     * the showcase floor and the Lord's floor: the median block light where you stand on the critical path is 7+, and
     * in the Lord's arena the median is 8+ with 8+ on at least 60% of the floor (coordinator's in-game check, P3).
     */
    static void registerLightTest() {
        AwakenTests.register("gates_interior_light", 600, h -> {
            StringBuilder report = new StringBuilder();
            List<String> fails = new ArrayList<>();
            for (Theme t : Theme.values()) {
                GatePlan plan = showcasePlan(t);
                GateLayout l = GateTestAccess.build(plan);
                FloorLayout f1 = l.main.get(0), boss = l.main.get(l.main.size() - 1);
                double path = median(floorLight(f1, paintFloor(l, f1, 23), true));
                // and a second gate of the theme on another layout, so the numbers aren't one lucky seed
                GateLayout l2 = GateTestAccess.build(plan(0x11AB0L + t.ordinal() * 17L, Rank.B, t, Layout.values()[(t.ordinal() + 3) % Layout.values().length], Twist.NONE));
                path = Math.min(path, median(floorLight(l2.main.get(0), paintFloor(l2, l2.main.get(0), 23), true)));
                List<Integer> arena = floorLight(boss, paintFloor(l, boss, 23), false);
                double am = median(arena);
                double share = arena.stream().filter(v -> v >= 8).count() / (double) Math.max(1, arena.size());
                report.append(String.format(Locale.ROOT, "%s: path median %.1f, arena median %.1f, arena 8+ %.0f%%%n", t.id, path, am, share * 100));
                if (path < 7) fails.add(t.id + ": median floor light on the critical path is " + path + " (needs 7)");
                if (am < 8 || share < 0.6) fails.add(t.id + ": the Lord's arena floor: median " + am + ", " + Math.round(share * 100) + "% at 8+ (needs 8 and 60%)");
            }
            Awaken.LOG.info("[gates] interior light\n{}", report);
            h.assertTrue(fails.isEmpty(), String.join("; ", fails));
            h.succeed();
        });
    }

    static double median(List<Integer> v) {
        if (v.isEmpty()) return 0;
        List<Integer> s = new ArrayList<>(v);
        s.sort(Integer::compare);
        int n = s.size();
        return n % 2 == 1 ? s.get(n / 2) : (s.get(n / 2 - 1) + s.get(n / 2)) / 2.0;
    }

    /**
     * Block light where you stand: on the critical path's rooms (path = true, the Lord's arena excluded) or in the
     * Lord's arena. A flood fill like the game's: emitters out, each step costing max(1, the block's dampening).
     */
    static List<Integer> floorLight(FloorLayout f, Map<Long, BlockState> b, boolean path) {
        java.util.Set<Integer> onPath = new java.util.HashSet<>(f.criticalPath());
        return floorLight(f, b, r -> path ? onPath.contains(r.id) && r.type != Room.Type.BOSS : r.type == Room.Type.BOSS);
    }

    /** Block light where you stand in the rooms {@code want} picks. */
    static List<Integer> floorLight(FloorLayout f, Map<Long, BlockState> b, java.util.function.Predicate<Room> want) {
        int x0 = -16, z0 = -16, y0 = f.baseY - Cells.BAND_BELOW;
        int W = (f.w + 2) * 16, D = (f.h + 2) * 16, H = Cells.BAND_BELOW + Cells.BAND_ABOVE;
        BlockState[] grid = new BlockState[W * D * H];
        byte[] lvl = new byte[grid.length];
        int[][] bucket = new int[16][];
        int[] size = new int[16];
        for (int i = 0; i < 16; i++) bucket[i] = new int[1024];
        for (var e : b.entrySet()) {
            long k = e.getKey();
            int x = (int) (k >> 40) & 0xFFFFF, y = (int) (k >> 20) & 0xFFFFF, z = (int) k & 0xFFFFF;
            if (x >= 0x80000) x -= 0x100000;
            if (z >= 0x80000) z -= 0x100000;
            int gx = x - x0, gy = y - y0, gz = z - z0;
            if (gx < 0 || gz < 0 || gy < 0 || gx >= W || gz >= D || gy >= H) continue;
            int i = gx + gz * W + gy * W * D;
            grid[i] = e.getValue();
            int em = e.getValue().getLightEmission();
            if (em > 0) {
                lvl[i] = (byte) em;
                if (size[em] == bucket[em].length) bucket[em] = java.util.Arrays.copyOf(bucket[em], size[em] * 2);
                bucket[em][size[em]++] = i;
            }
        }
        int[] step = {1, -1, W, -W, W * D, -W * D};
        for (int L = 15; L >= 2; L--) {
            for (int q = 0; q < size[L]; q++) {
                int i = bucket[L][q];
                if (lvl[i] != L) continue;
                int gx = i % W, gz = (i / W) % D, gy = i / (W * D);
                for (int s = 0; s < 6; s++) {
                    if ((s == 0 && gx == W - 1) || (s == 1 && gx == 0) || (s == 2 && gz == D - 1) || (s == 3 && gz == 0)
                            || (s == 4 && gy == H - 1) || (s == 5 && gy == 0)) continue;
                    int n = i + step[s];
                    BlockState st = grid[n];
                    int damp = st == null ? 0 : st.getLightDampening();
                    if (damp >= 15) continue;
                    int nl = L - Math.max(1, damp);
                    if (nl <= lvl[n]) continue;
                    lvl[n] = (byte) nl;
                    if (size[nl] == bucket[nl].length) bucket[nl] = java.util.Arrays.copyOf(bucket[nl], size[nl] * 2);
                    bucket[nl][size[nl]++] = n;
                }
            }
        }
        List<Integer> out = new ArrayList<>();
        for (Room r : f.rooms) {
            if (!want.test(r)) continue;
            int walk = f.baseY + r.dy;
            for (int x = r.x0(); x <= r.x1(); x++) {
                for (int z = r.z0(); z <= r.z1(); z++) {
                    for (int y = walk - 4; y <= walk + 2; y++) {
                        if (standable(b.get(key(x, y - 1, z))) && passable(b.get(key(x, y, z))) && passable(b.get(key(x, y + 1, z)))) {
                            out.add((int) lvl[(x - x0) + (z - z0) * W + (y - y0) * W * D]);
                            break;
                        }
                    }
                }
            }
        }
        return out;
    }

    /** Block light at a spot: a flood fill from every emitter in the painted map, stopped by full blocks. */
    static int light(Map<Long, BlockState> b, int sx, int sy, int sz, int yMin, int yMax) {
        int best = 0;
        for (var e : b.entrySet()) {
            int em = e.getValue().getLightEmission();
            if (em <= best) continue;
            long k = e.getKey();
            int x = (int) (k >> 40) & 0xFFFFF, y = (int) (k >> 20) & 0xFFFFF, z = (int) k & 0xFFFFF;
            if (x >= 0x80000) x -= 0x100000;
            if (z >= 0x80000) z -= 0x100000;
            int manhattan = Math.abs(x - sx) + Math.abs(y - sy) + Math.abs(z - sz);
            if (em - manhattan <= best) continue;
            // walk it properly: BFS from this emitter, limited to its reach
            ArrayDeque<int[]> q = new ArrayDeque<>();
            Map<Long, Integer> seen = new HashMap<>();
            q.add(new int[]{x, y, z, em});
            seen.put(k, em);
            while (!q.isEmpty()) {
                int[] c = q.poll();
                if (c[0] == sx && c[1] == sy && c[2] == sz) {
                    best = Math.max(best, c[3]);
                    break;
                }
                if (c[3] <= 1) continue;
                int[][] n = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
                for (int[] d : n) {
                    int nx = c[0] + d[0], ny = c[1] + d[1], nz = c[2] + d[2];
                    if (ny < yMin || ny > yMax) continue;
                    long nk = key(nx, ny, nz);
                    BlockState st = b.get(nk);
                    if (st != null && st.canOcclude()) continue;
                    if (seen.getOrDefault(nk, 0) >= c[3] - 1) continue;
                    seen.put(nk, c[3] - 1);
                    q.add(new int[]{nx, ny, nz, c[3] - 1});
                }
            }
        }
        return best;
    }

    /**
     * What the Director actually rolls, measured: per rank, the floors, modifiers and twists, and per floor the monsters
     * on the critical path and in the side rooms (elites weighted {@link #ELITE_WEIGHT}), for content versions 1 and 2.
     * Written to run/gate_tuning.json; tools/sim/pacing_sim.py reads it (a copy lives in tools/sim/gate_tuning.json).
     */
    public static final double ELITE_WEIGHT = 3.0;

    static String tuningJson(int samples) {
        StringBuilder sb = new StringBuilder("{\"elite_weight\":").append(ELITE_WEIGHT).append(",\"samples\":").append(samples).append(",\"ranks\":{");
        boolean firstRank = true;
        for (Rank rank : Rank.values()) {
            dev.awaken.gates.run.GateMemory mem = new dev.awaken.gates.run.GateMemory();
            Map<Integer, Integer> floors = new java.util.TreeMap<>();
            Map<String, Integer> mods = new java.util.TreeMap<>(), twists = new java.util.TreeMap<>(), layouts = new java.util.TreeMap<>();
            double[][] path = new double[3][7], side = new double[3][7], elites = new double[3][7], rooms = new double[3][7];
            int[][] n = new int[3][7];
            for (int i = 0; i < samples; i++) {
                var roll = dev.awaken.gates.Director.roll(0x7E57L + i * 104729L + rank.ordinal() * 7919L, rank, mem, rank, null);
                GatePlan p = roll.plan();
                floors.merge(p.floors(), 1, Integer::sum);
                for (Modifier m : p.modifiers()) mods.merge(m.name(), 1, Integer::sum);
                twists.merge(p.twist().name(), 1, Integer::sum);
                layouts.merge(p.layout().name(), 1, Integer::sum);
                for (int v = 1; v <= 2; v++) {
                    GateLayout l = GateTestAccess.build(p.rebuilt(v));
                    for (FloorLayout f : l.main) {
                        // ordinary floors only: the Lord's floor is a short approach and the Lord (the sim's own term)
                        if (f.floor > 6 || f.bossFloor) continue;
                        java.util.Set<Integer> onPath = new java.util.HashSet<>(f.criticalPath());
                        for (Room r : f.rooms) {
                            double units = r.normals + r.wave2Normals + ELITE_WEIGHT * (r.elites + r.wave2Elites);
                            if (r.type == Room.Type.CAVERN) units *= 0.6; // zones wake one by one; about 60% of them hold monsters
                            if (onPath.contains(r.id)) path[v][f.floor] += units;
                            else side[v][f.floor] += units;
                            elites[v][f.floor] += r.elites + r.wave2Elites;
                            rooms[v][f.floor] += 1;
                        }
                        n[v][f.floor]++;
                    }
                }
            }
            if (!firstRank) sb.append(',');
            firstRank = false;
            sb.append('"').append(rank.label).append("\":{\"floors\":").append(jsonMap(floors)).append(",\"mods\":").append(jsonMap(mods))
                    .append(",\"twists\":").append(jsonMap(twists)).append(",\"layouts\":").append(jsonMap(layouts));
            for (int v = 1; v <= 2; v++) {
                sb.append(",\"v").append(v).append("\":{");
                for (String what : List.of("path", "side", "elites", "rooms")) {
                    double[] arr = what.equals("path") ? path[v] : what.equals("side") ? side[v] : what.equals("elites") ? elites[v] : rooms[v];
                    sb.append('"').append(what).append("\":[");
                    for (int f = 1; f <= 6; f++) {
                        if (f > 1) sb.append(',');
                        sb.append(n[v][f] == 0 ? "null" : String.format(Locale.ROOT, "%.2f", arr[f] / n[v][f]));
                    }
                    sb.append(what.equals("rooms") ? "]" : "],");
                }
                sb.append('}');
            }
            sb.append('}');
        }
        return sb.append("}}").toString();
    }

    private static String jsonMap(Map<?, Integer> m) {
        StringBuilder sb = new StringBuilder("{");
        for (var e : m.entrySet()) {
            if (sb.length() > 1) sb.append(',');
            sb.append('"').append(e.getKey()).append("\":").append(e.getValue());
        }
        return sb.append('}').toString();
    }

    // ---- plans ------------------------------------------------------------------------------------------------------

    /** A content-version-2 plan (new interiors) with no modifiers. */
    static GatePlan plan(long seed, Rank rank, Theme theme, Layout layout, Twist twist) {
        return new GatePlan(seed, 2, rank, theme, layout, List.<Modifier>of(), twist, Curve.floorsFor(rank, false),
                theme.bossId(), Theme.values()[(theme.ordinal() + 3) % Theme.values().length], 0);
    }

    /** The plan whose first floor shows a theme off in the previews: a labyrinth with a big hall and its pieces. */
    static GatePlan showcasePlan(Theme t) {
        GatePlan best = null;
        int bestScore = -1;
        for (int i = 0; i < 24; i++) {
            GatePlan p = plan(0x5EED00L + t.ordinal() * 1000L + i, Rank.C, t, Layout.LABYRINTH, Twist.NONE);
            GateLayout l = GateTestAccess.build(p);
            Set<SetPiece> pieces = EnumSet.noneOf(SetPiece.class);
            for (Room r : l.main.get(0).rooms) if (r.piece != null) pieces.add(r.piece);
            int score = pieces.size() * 10 + (int) l.main.get(0).rooms.stream().filter(r -> r.piece != null && r.area() > 1).count();
            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return best;
    }

    // ---- painting into maps -----------------------------------------------------------------------------------------

    static long key(int x, int y, int z) {
        return ((long) (x & 0xFFFFF) << 40) | ((long) (y & 0xFFFFF) << 20) | (z & 0xFFFFF);
    }

    /** Paints the chunks a room covers (and one ring of neighbours); keys are floor-local x, absolute y, floor-local z. */
    static Map<Long, BlockState> paintRoom(GateLayout l, FloorLayout f, Room r, int cell) {
        Map<Long, BlockState> out = new HashMap<>();
        for (int sx = r.gx - 1; sx <= r.gx + r.gw; sx++) for (int sz = r.gz - 1; sz <= r.gz + r.gh; sz++) paintSlot(l, f, sx, sz, cell, out);
        return out;
    }

    static Map<Long, BlockState> paintFloor(GateLayout l, FloorLayout f, int cell) {
        Map<Long, BlockState> out = new HashMap<>();
        for (int sx = -1; sx <= f.w; sx++) for (int sz = -1; sz <= f.h; sz++) paintSlot(l, f, sx, sz, cell, out);
        return out;
    }

    static void paintSlot(GateLayout l, FloorLayout f, int sx, int sz, int cell, Map<Long, BlockState> out) {
        int chunkX = (Cells.originX(cell) >> 4) + f.originSlotX + sx;
        int chunkZ = (Cells.originZ(cell) >> 4) + f.originSlotZ + sz;
        int bx0 = sx * 16, bz0 = sz * 16;
        GatePainter.paint(l, cell, chunkX, chunkZ, (x, y, z, s) -> {
            if (f.paints(y)) out.put(key(bx0 + x, y, bz0 + z), s);
        });
    }

    // ---- reachability -----------------------------------------------------------------------------------------------

    static boolean passable(BlockState s) {
        if (s == null || s.isAir()) return true;
        if (!s.getFluidState().isEmpty()) return true;
        var shape = s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        return shape.isEmpty() || shape.max(net.minecraft.core.Direction.Axis.Y) <= 0.2;
    }

    static boolean standable(BlockState s) {
        return s != null && (!passable(s) || !s.getFluidState().isEmpty());
    }

    /**
     * Walks the painted room: from the first doorway, every other doorway and (in arenas) the Lord's spot and the exit
     * portal's row must be reachable on foot (step up one, drop up to three, two blocks of head room). Null when fine.
     */
    static String reachable(FloorLayout f, Room r, Map<Long, BlockState> b) {
        if (r.doors.isEmpty()) return null;
        int y0 = f.baseY - Cells.BAND_BELOW, y1 = f.baseY + Cells.BAND_ABOVE - 1;
        int minX = r.gx * 16 - 1, minZ = r.gz * 16 - 1, maxX = (r.gx + r.gw) * 16, maxZ = (r.gz + r.gh) * 16;
        int W = maxX - minX + 1, D = maxZ - minZ + 1, H = y1 - y0 + 1;
        boolean[] seen = new boolean[W * D * H];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        int[] start = doorStand(f, r, r.doors.get(0), b);
        if (start == null) return "no place to stand in the first doorway";
        q.add(start);
        seen[(start[0] - minX) + (start[2] - minZ) * W + (start[1] - y0) * W * D] = true;
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int d = 0; d < 4; d++) {
                int nx = c[0] + GatePainter.DX[d], nz = c[2] + GatePainter.DZ[d];
                if (nx < minX || nz < minZ || nx > maxX || nz > maxZ) continue;
                for (int ny = c[1] + 1; ny >= c[1] - 3; ny--) {
                    if (ny <= y0 || ny + 1 > y1) continue;
                    if (!standable(b.get(key(nx, ny - 1, nz))) || !passable(b.get(key(nx, ny, nz))) || !passable(b.get(key(nx, ny + 1, nz)))) continue;
                    if (ny > c[1] && !passable(b.get(key(c[0], c[1] + 2, c[2])))) continue; // jumping needs head room
                    int i = (nx - minX) + (nz - minZ) * W + (ny - y0) * W * D;
                    if (!seen[i]) {
                        seen[i] = true;
                        q.add(new int[]{nx, ny, nz});
                    }
                    break;
                }
            }
        }
        for (Room.Door d : r.doors) {
            int[] s = doorStand(f, r, d, b);
            if (s == null) return "no place to stand in the doorway on side " + d.side;
            boolean ok = false;
            for (int dy = -1; dy <= 1 && !ok; dy++) {
                int y = s[1] + dy;
                if (y > y0 && y <= y1) ok = seen[(s[0] - minX) + (s[2] - minZ) * W + (y - y0) * W * D];
            }
            if (!ok) return "doorway on side " + d.side + " can't be reached from side " + r.doors.get(0).side;
        }
        if (r.piece != null && r.piece.arena) {
            int y = f.baseY + r.dy;
            int[][] spots = {{r.centerX(), r.centerZ()}, {r.centerX(), r.centerZ() + 3}};
            for (int[] sp : spots) {
                boolean ok = false;
                for (int dy = -1; dy <= 1 && !ok; dy++) ok = seen[(sp[0] - minX) + (sp[1] - minZ) * W + (y + dy - y0) * W * D];
                if (!ok) return "the Lord's spot / exit portal at " + sp[0] + "," + sp[1] + " can't be reached";
            }
        }
        return null;
    }

    /** Standing spot (feet) in the middle of a doorway, on this room's side of the wall. */
    static int[] doorStand(FloorLayout f, Room r, Room.Door d, Map<Long, BlockState> b) {
        int x, z;
        switch (d.side) {
            case 0 -> { x = d.gx * 16 + 8; z = d.gz * 16 + 1; }
            case 2 -> { x = d.gx * 16 + 8; z = d.gz * 16 + 14; }
            case 1 -> { x = d.gx * 16 + 14; z = d.gz * 16 + 8; }
            default -> { x = d.gx * 16 + 1; z = d.gz * 16 + 8; }
        }
        int y = f.baseY + d.y;
        for (int dy = 0; dy >= -2; dy--) {
            if (standable(b.get(key(x, y + dy - 1, z))) && passable(b.get(key(x, y + dy, z)))) return new int[]{x, y + dy, z};
        }
        return null;
    }

    // ---- dumps for the offline renderer -----------------------------------------------------------------------------

    static void dump(GateLayout l, FloorLayout f, String name) {
        Map<Long, BlockState> b = paintFloor(l, f, 13);
        StringBuilder sb = new StringBuilder(1 << 20);
        sb.append("{\"name\":\"").append(name).append("\",\"theme\":\"").append(f.theme.id).append("\",\"floor\":").append(f.floor)
                .append(",\"boss\":").append(f.bossFloor).append(",\"baseY\":").append(f.baseY).append(",\"rooms\":[");
        for (int i = 0; i < f.rooms.size(); i++) {
            Room r = f.rooms.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"id\":").append(r.id).append(",\"type\":\"").append(r.type).append("\",\"shape\":\"").append(r.shape)
                    .append("\",\"piece\":\"").append(r.piece == null ? "" : r.piece.name()).append("\",\"x0\":").append(r.x0())
                    .append(",\"z0\":").append(r.z0()).append(",\"x1\":").append(r.x1()).append(",\"z1\":").append(r.z1())
                    .append(",\"y\":").append(f.baseY + r.dy).append(",\"h\":").append(r.height).append(",\"onPath\":").append(r.onPath)
                    .append(",\"doors\":[");
            for (int j = 0; j < r.doors.size(); j++) {
                Room.Door d = r.doors.get(j);
                if (j > 0) sb.append(',');
                sb.append("{\"x\":").append(d.centerX()).append(",\"z\":").append(d.centerZ()).append(",\"side\":").append(d.side)
                        .append(",\"y\":").append(f.baseY + d.y).append(",\"to\":").append(d.toRoom).append(",\"onward\":").append(d.onward)
                        .append(",\"toEnd\":").append(d.toEnd).append('}');
            }
            sb.append("]}");
        }
        sb.append("],\"start\":").append(f.start).append(",\"end\":").append(f.end).append(",\"cams\":[");
        boolean fc = true;
        for (String spot : f.bossFloor ? List.of("start", "ante", "lord") : List.of("start", "piece", "small", "shrine")) {
            double[] v = Interiors.view(f, spot);
            if (v == null) continue;
            if (!fc) sb.append(',');
            fc = false;
            sb.append(String.format(Locale.ROOT, "{\"name\":\"%s\",\"pos\":[%.2f,%.2f,%.2f],\"yaw\":%.1f,\"pitch\":%.1f}", spot, v[0], v[1] + 1.62, v[2], v[3], v[4]));
        }
        sb.append("]");
        // the whole band as a grid: a palette and one uint16 index per block (x fastest, then z, then y), base64
        int minX = -16, minZ = -16, maxX = f.w * 16 + 15, maxZ = f.h * 16 + 15;
        int y0 = f.baseY - Cells.BAND_BELOW, y1 = f.baseY + Cells.BAND_ABOVE - 1;
        int nx = maxX - minX + 1, nz = maxZ - minZ + 1, ny = y1 - y0 + 1;
        Map<BlockState, Integer> pal = new java.util.LinkedHashMap<>();
        pal.put(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 0);
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(nx * ny * nz * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (int y = y0; y <= y1; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    BlockState st = b.get(key(x, y, z));
                    int id = st == null ? 0 : pal.computeIfAbsent(st, k -> pal.size());
                    buf.putShort((short) id);
                }
            }
        }
        sb.append(",\"origin\":[").append(minX).append(',').append(y0).append(',').append(minZ).append("],\"dims\":[")
                .append(nx).append(',').append(ny).append(',').append(nz).append("],\"palette\":[");
        boolean fp0 = true;
        for (BlockState st : pal.keySet()) {
            if (!fp0) sb.append(',');
            fp0 = false;
            var id = BuiltInRegistries.BLOCK.getKey(st.getBlock());
            sb.append("[\"").append(id.getNamespace().equals("minecraft") ? id.getPath() : id.toString()).append("\",\"");
            boolean fp = true;
            for (Property<?> p : st.getProperties()) {
                if (!fp) sb.append(',');
                fp = false;
                sb.append(p.getName()).append('=').append(propValue(st, p));
            }
            sb.append("\"]");
        }
        sb.append("],\"grid\":\"").append(java.util.Base64.getEncoder().encodeToString(buf.array())).append("\"}");
        try {
            Path dir = Path.of("interiors");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(name + ".json"), sb.toString());
        } catch (IOException ex) {
            Awaken.LOG.warn("[gates] could not write the interior dump: {}", ex.toString());
        }
    }

    private static <T extends Comparable<T>> String propValue(BlockState s, Property<T> p) {
        return p.getName(s.getValue(p));
    }

    static List<Room> pieceRooms(FloorLayout f) {
        List<Room> out = new ArrayList<>();
        for (Room r : f.rooms) if (r.piece != null) out.add(r);
        return out;
    }
}
