package dev.awaken.gates.world;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.content.FloorWorld;
import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.content.Layout;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.content.Twist;
import dev.awaken.gates.worlds.GateAtlas;
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

    public static final String BELL = "awaken:bell_crypt/";
    /** Stage 2's floor worlds (SPEC §38.10), theme/world. */
    public static final List<String> CAVE_WORLDS = List.of(
            "fungal_hollow/glowing_deep", "fungal_hollow/root_warrens", "fungal_hollow/spore_canopy", "fungal_hollow/inside_the_bloom",
            "ember_mines/working_mine", "ember_mines/open_chasm", "ember_mines/forge_below", "ember_mines/cooling_sea",
            "hive/the_comb", "hive/brood_hall", "hive/honey_lake", "hive/the_swarm",
            "overgrown_temple/jungle_courts", "overgrown_temple/root_halls", "overgrown_temple/waterfall_temple", "overgrown_temple/great_tree");
    /** Stage 3's floor worlds (SPEC §38.11): the D themes, open lands among them. */
    public static final List<String> D_WORLDS = List.of(
            "drowned_archive/stacks", "drowned_archive/atrium", "drowned_archive/sunken_shelves", "drowned_archive/inkwell",
            "clockwork_foundry/works_floor", "clockwork_foundry/pipe_maze", "clockwork_foundry/great_engine", "clockwork_foundry/stopped_hour",
            "glass_desert/buried_colossus", "glass_desert/glass_caves", "glass_desert/sunken_city", "glass_desert/mirage",
            "lantern_night_market/lantern_street", "lantern_night_market/rooftops", "lantern_night_market/floating_market",
            "lantern_night_market/festival");
    /** Stage 4's floor worlds (SPEC §38.12): the C themes, an endless plain, islands in the dark and a fold among them. */
    public static final List<String> C_WORLDS = List.of(
            "frost_monastery/cloisters", "frost_monastery/ice_caves", "frost_monastery/cliff_stair", "frost_monastery/still_snow",
            "mirror_palace/gallery", "mirror_palace/mirror_maze", "mirror_palace/inverted_palace", "mirror_palace/the_white",
            "precursor_lab/facility", "precursor_lab/server_abyss", "precursor_lab/containment", "precursor_lab/simulation",
            "nightmare/the_house", "nightmare/endless_hall", "nightmare/upside_down", "nightmare/sleepers_dream");
    /** Stage 5's floor worlds (SPEC §38.13): the sky's open lands, the abyss's silent ones, and the rare worlds. */
    public static final List<String> E_WORLDS = List.of(
            "sky_archipelago/the_isles", "sky_archipelago/sky_ruins", "sky_archipelago/wind_stair", "sky_archipelago/above_the_clouds",
            "abyssal_shrine/black_mirror", "abyssal_shrine/dark_steps", "abyssal_shrine/starfall", "abyssal_shrine/the_last_room",
            "rare/the_quiet", "rare/upward_rain", "rare/the_aurora_steppe", "rare/the_lantern_orchard", "rare/the_last_campfire",
            "rare/the_cartographers_table", "rare/the_reflection", "rare/the_other_gate", "rare/the_hollow_sky", "rare/the_endless_stair",
            "rare/the_gates_heart", "rare/the_tide_room", "rare/the_library_of_doors", "rare/the_dollhouse", "rare/the_glass_sea");

    public static GatePlan plan(long seed, int i) {
        return plan(seed, i, Theme.BELL_CRYPT);
    }

    public static GatePlan plan(long seed, int i, Theme t) {
        Layout lay = Layout.values()[i % Layout.values().length];
        Twist tw = i % 4 == 3 ? Twist.GATE_WITHIN : Twist.NONE;
        return new GatePlan(seed, GatePlan.CONTENT_VERSION, Rank.of(i % 5), t, lay, List.of(), tw, 3 + i % 4,
                t.bossId(), t == Theme.HIVE ? Theme.FUNGAL_HOLLOW : Theme.HIVE, 0);
    }

    /** Every floor of the main gate in one world and mood (where its roles allow). */
    public static GatePlan dressed(GatePlan p, String world, String mood) {
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
        walkAll(w, f, e.x(), sy, e.z());
        if (!w.reached(e.x(), sy, e.z())) {
            String why = "the arrival isn't somewhere you can stand (" + e.x() + " " + sy + " " + e.z() + ": " + w.at(e.x(), sy, e.z()).getBlock().getDescriptionId()
                    + " on " + w.at(e.x(), sy - 1, e.z()).getBlock().getDescriptionId() + ")";
            String dir = System.getenv("AWAKEN_WORLD_DUMP");
            if (dir != null) try {
                w.dump(java.nio.file.Path.of(dir), "FAIL-" + f.world.replace(':', '_').replace('/', '_') + "-" + f.floor, e.x(), sy, e.z());
            } catch (java.io.IOException ex) {
                why += " dump failed";
            }
            return why;
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
                // the Mirror Hall's cache is in its pavilion, which only a spirit walks into (SPEC §17)
                if (ft.kind() == Room.FeatureKind.CACHE && r.rig != null && r.rig.kind == RigRoom.Kind.MIRROR_HALL) continue;
                if (w.near(ft.x(), y + ft.y(), ft.z(), 2) == null)
                    bad.append(" room ").append(r.id).append(' ').append(ft.kind()).append(" at ").append(ft.x()).append(' ').append(ft.z()).append(" out of reach;");
            }
            if (r.spawnSpots.isEmpty() && r.features.isEmpty() && w.near(r.centerX(), y, r.centerZ(), 7) == null)
                bad.append(" room ").append(r.id).append(" (").append(r.type).append(") can't be walked into;");
        }
        bad.append(marks(w, f));
        List<int[]> leaks = w.leaks();
        if (!leaks.isEmpty()) {
            int[] lk = leaks.get(0);
            bad.append(" water would flow at ").append(lk[0]).append(' ').append(lk[1]).append(' ').append(lk[2]).append(" (").append(leaks.size()).append(") into");
            for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}}) {
                var n = w.at(lk[0] + d[0], lk[1] + d[1], lk[2] + d[2]);
                if (n.getBlock() != net.minecraft.world.level.block.Blocks.WATER && w.pass(lk[0] + d[0], lk[1] + d[1], lk[2] + d[2]))
                    bad.append(' ').append(n.getBlock().getDescriptionId()).append(" at ").append(d[0]).append(',').append(d[1]).append(',').append(d[2]);
            }
            bad.append(';');
        }
        // the way to the end on foot (swimming only where there's no other way), lit to 7 or more outside the built
        // rooms (today's painter lights those)
        w.light();
        Room end = f.room(f.end);
        int[] goal = goal(w, f, end);
        if (goal != null) {
            w.dry = true;
            walkAll(w, f, e.x(), sy, e.z());
            int[] dryGoal = goal(w, f, end);
            w.dry = false;
            if (dryGoal != null) goal = dryGoal;
            else walkAll(w, f, e.x(), sy, e.z());
        }
        if (goal == null) {
            bad.append(" the end can't be reached;");
            for (Room r : f.rooms) {
                if (r.rig == null || r.pathIn == null || r.pathOut == null) continue;
                int[] in = outside(r.pathIn, f.baseY), out = outside(r.pathOut, f.baseY);
                if (w.near(in[0], in[1], in[2], 2) == null || w.reached(out[0], out[1], out[2])) continue;
                bad.append(" the way out of Rig room ").append(r.id).append(" at ").append(out[0]).append(' ').append(out[1]).append(' ').append(out[2])
                        .append(" is ").append(w.at(out[0], out[1], out[2]).getBlock().getDescriptionId()).append(" over ")
                        .append(w.at(out[0], out[1] - 1, out[2]).getBlock().getDescriptionId()).append(" under ")
                        .append(w.at(out[0], out[1] + 1, out[2]).getBlock().getDescriptionId()).append(';');
            }
        }
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
                    .append(darkest[1]).append(' ').append(darkest[2]).append(": ").append(darkest[3]).append(", in ")
                    .append(w.at(darkest[0], darkest[1], darkest[2]).getBlock().getDescriptionId()).append(" on ")
                    .append(w.at(darkest[0], darkest[1] - 1, darkest[2]).getBlock().getDescriptionId()).append(" under ")
                    .append(w.at(darkest[0], darkest[1] + 1, darkest[2]).getBlock().getDescriptionId()).append(");");
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
                if (goal != null) {
                    rooms.append("route:");
                    for (int[] n : w.route(goal[0], goal[1], goal[2])) rooms.append(' ').append(n[0]).append(',').append(n[1]).append(',').append(n[2]);
                    rooms.append('\n');
                }
                java.nio.file.Files.writeString(java.nio.file.Path.of(dir, name + "-rooms.txt"), rooms + bad.toString());
            } catch (java.io.IOException ex) {
                bad.append(" dump failed: ").append(ex.getMessage());
            }
        }
        return bad.toString();
    }

    /**
     * The live layer's marks hold (SPEC §38.8): every handbell stands where it's marked, every secret has its handbell (or
     * its rope), and a basin the Flood fills is dry and walled in, so the risen water can't run out of it.
     */
    static String marks(WorldCheck w, FloorLayout f) {
        StringBuilder bad = new StringBuilder();
        int missing = 0, hands = 0;
        String first = "";
        for (FloorLayout.Mark m : f.marks) {
            if (!m.kind().equals(FloorLayout.Mark.BELL) || !m.id().equals(FloorLayout.Mark.HANDBELL)) continue;
            hands++;
            net.minecraft.world.level.block.state.BlockState there = w.at(m.x0(), f.baseY + m.y0(), m.z0());
            if (there.is(GatesRegistry.CRYPT_HANDBELL.get())) continue;
            if (missing++ == 0) first = " (" + there.getBlock().getDescriptionId() + " at " + m.x0() + " " + m.y0() + " " + m.z0()
                    + ", under it " + w.at(m.x0(), f.baseY + m.y0() - 1, m.z0()).getBlock().getDescriptionId() + ")";
        }
        if (missing > 0) bad.append(' ').append(missing).append(" of ").append(hands).append(" handbells aren't there").append(first).append(';');
        // a world's touchstones (its rule's blocks, 38.10) stand where they're marked; its nooks can be walked into
        int touches = 0, gone = 0, shut = 0;
        String where = "";
        for (FloorLayout.Mark m : f.marks(FloorLayout.Mark.TOUCH)) {
            touches++;
            var there = w.at(m.x0(), f.baseY + m.y0(), m.z0());
            if (there.getBlock() instanceof dev.awaken.gates.block.ThemeBlocks.Touchstone) continue;
            if (gone++ == 0) where = " (" + there.getBlock().getDescriptionId() + " at " + m.x0() + " " + m.y0() + " " + m.z0() + ")";
        }
        if (gone > 0) bad.append(' ').append(gone).append(" of ").append(touches).append(" touchstones aren't there").append(where).append(';');
        for (FloorLayout.Mark m : f.marks(FloorLayout.Mark.NOOK)) {
            if (w.near((m.x0() + m.x1()) / 2, f.baseY + m.y0(), (m.z0() + m.z1()) / 2, 2) == null) {
                if (shut++ == 0) bad.append(" the nook ").append(m.id()).append(" at ").append(m.x0()).append(' ').append(m.y0()).append(' ')
                        .append(m.z0()).append(" can't be walked into;");
            }
        }
        for (FloorLayout.Mark m : f.marks(FloorLayout.Mark.SECRET)) {
            boolean has = false;
            for (int x = m.x0(); x <= m.x1() && !has; x++)
                for (int y = m.y0(); y <= m.y1() && !has; y++)
                    for (int z = m.z0(); z <= m.z1() && !has; z++) {
                        var st = w.at(x, f.baseY + y, z);
                        has = st.is(GatesRegistry.CRYPT_HANDBELL.get()) || (m.id().equals("odd_rope") && st.is(GatesRegistry.CRYPT_BELL_ROPE.get()))
                                || st.getBlock() instanceof dev.awaken.gates.block.ThemeBlocks.Touchstone;
                    }
            if (!has) bad.append(" the secret ").append(m.id()).append(" has nothing to find at ").append(m.x0()).append(' ').append(m.z0()).append(';');
        }
        int wet = 0, open = 0;
        int[] leak = null;
        for (FloorLayout.Mark m : f.marks(FloorLayout.Mark.BASIN)) {
            for (int x = m.x0(); x <= m.x1(); x++) {
                for (int y = m.y0(); y <= m.y1(); y++) {
                    for (int z = m.z0(); z <= m.z1(); z++) {
                        var st = w.at(x, f.baseY + y, z);
                        if (st.getBlock() == net.minecraft.world.level.block.Blocks.WATER) wet++;
                        if (!st.isAir() && !st.is(GatesRegistry.CRYPT_LILY_CANDLE.get())) continue;
                        open++;
                        for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}}) {
                            int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                            if (inBasin(f, nx, ny, nz)) continue;
                            var n = w.at(nx, f.baseY + ny, nz);
                            if ((n.isAir() || n.is(GatesRegistry.CRYPT_LILY_CANDLE.get())) && leak == null) leak = new int[]{nx, f.baseY + ny, nz};
                        }
                    }
                }
            }
        }
        if (wet > 0) bad.append(" the Flood's basins already hold ").append(wet).append(" water;");
        if (!f.marks(FloorLayout.Mark.BASIN).isEmpty() && open == 0) bad.append(" the Flood's basins have no room for water;");
        if (leak != null) bad.append(" the Flood would run out at ").append(leak[0]).append(' ').append(leak[1]).append(' ').append(leak[2]).append(';');
        return bad.toString();
    }

    static boolean inBasin(FloorLayout f, int x, int y, int z) {
        for (FloorLayout.Mark m : f.marks(FloorLayout.Mark.BASIN)) if (m.contains(x, y, z)) return true;
        return false;
    }

    /** Walks from the arrival; a Rig room is crossed by its own mechanism: reach its way in, and you come out at its way on. */
    static void walkAll(WorldCheck w, FloorLayout f, int x, int y, int z) {
        w.walk(x, y, z);
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
    }

    /** Where the floor ends: at its shrine, else its first monster spot. */
    static int[] goal(WorldCheck w, FloorLayout f, Room end) {
        Room.Feature shrine = end.feature(Room.FeatureKind.SHRINE);
        int[] goal = null;
        if (shrine != null) goal = w.near(shrine.x(), f.baseY + end.dy + shrine.y(), shrine.z(), 2);
        if (goal == null && !end.spawnSpots.isEmpty()) goal = w.near(end.spawnSpots.get(0)[0], f.baseY + end.dy, end.spawnSpots.get(0)[1], 1);
        return goal;
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

    /**
     * One world's test: plans over many gates and every mood, every one of its floors checked, and every secret it keeps
     * placed somewhere: a mark (a handbell, a touchstone, a nook), the secret place it leads to, or one of its events.
     * The secret world it leads to has its own secrets placed inside that place (a thought is said aloud, not placed).
     */
    static void world(String test, String id, int plans) {
        AwakenTests.register(test, 2400, h -> {
            GateWorld w = GateWorlds.get(id);
            h.assertTrue(w != null, "the recipe " + id + " loads");
            String world = id.substring(id.indexOf('/') + 1);
            List<String> moods = w.moods().stream().map(GateWorld.Mood::id).toList();
            double[] ms = new double[2];
            double planWorst = 0;
            int floors = 0;
            java.util.Set<String> secrets = new java.util.HashSet<>(), places = new java.util.HashSet<>();
            for (int i = 0; i < plans; i++) {
                // a rare world turns up in any theme's gate, and the Reflection wears the gate it's in: try several
                Theme gate = w.tier() == GateWorld.Tier.RARE ? Theme.values()[(w.theme().ordinal() + i * 5) % Theme.values().length] : w.theme();
                GatePlan p = dressed(plan(0x5E11L + i * 104729L, i, gate), id, moods.get(i % moods.size()));
                long t = System.nanoTime();
                GateLayout l = FloorBuilder.build(p);
                double planMs = (System.nanoTime() - t) / 1e6 / Math.max(1, p.worlds().size());
                // the first plan warms the code up (class loading, the JIT): budgets are measured from the second on
                if (i > 0) planWorst = Math.max(planWorst, planMs);
                for (FloorLayout f : l.main) {
                    if (!f.isWorld()) {
                        h.assertTrue(p.worldFor(f.area, f.floor) == null, world + " gave no floor " + f.floor + " (" + WorldEngine.WHY.get() + ") of " + p + " (today's rooms instead)");
                        continue;
                    }
                    floors++;
                    for (FloorLayout.Mark m : f.marks(FloorLayout.Mark.SECRET)) secrets.add(m.id());
                    for (FloorLayout.Mark m : f.marks(FloorLayout.Mark.NOOK)) secrets.add(m.id());
                    places.addAll(f.placeWorlds.values());
                    String why = check(p, f, i > 0 ? ms : new double[2], i < 2 ? world + "-" + i + "-f" + f.floor : null);
                    h.assertTrue(why.isEmpty(), world + " floor " + f.floor + " (" + f.mood + ") of " + p + ":" + why);
                }
            }
            h.assertTrue(floors > 0, "some floors were " + world);
            for (GateWorld.Secret sc : w.dressing().secrets()) {
                boolean placed = secrets.contains(sc.id()) || w.dressing().events().contains(sc.id())
                        || w.dressing().events().stream().anyMatch(ev -> sc.id().equals(dev.awaken.gates.worlds.WorldThemes.GIVES.get(ev)))
                        || (!sc.leadsTo().isEmpty() && places.contains(sc.leadsTo()));
                h.assertTrue(placed, world + "'s secret " + sc.id() + " is never placed");
                GateWorld inner = sc.leadsTo().isEmpty() ? null : GateWorlds.get(sc.leadsTo());
                if (inner != null) for (GateWorld.Secret in : inner.dressing().secrets())
                    h.assertTrue(secrets.contains(in.id()) || in.id().equals("the_thought") || in.id().equals("choir_hymn") || in.id().equals("odd_rope")
                            || places.isEmpty(), sc.leadsTo() + "'s secret " + in.id() + " is never placed in its place");
            }
            h.assertTrue(ms[0] < 4 && ms[1] < 40, String.format("chunks carve inside the budget: average %.2f ms, worst %.2f ms", ms[0], ms[1]));
            h.assertTrue(planWorst < 50, String.format("floors plan inside the budget: worst %.1f ms", planWorst));
            dev.awaken.Awaken.LOG.info("[gates] {}: {} floors, chunks {} ms average, {} ms worst, plans {} ms worst", world, floors,
                    String.format("%.2f", ms[0]), String.format("%.2f", ms[1]), String.format("%.1f", planWorst));
            h.succeed();
        });
    }

    public static void register() {
        AwakenTests.register("gateworlds_bell_recipes", 200, h -> {
            // every theme's recipes load, have their engine, and show their own biomes (the Bell Crypt's five and the rest)
            ServerLevel gates = h.getLevel().getServer().getLevel(GatesRegistry.GATES);
            GateBiomeSource src = gates == null ? null : (GateBiomeSource) gates.getChunkSource().getGenerator().getBiomeSource();
            for (String b : List.of("sunken_belfry", "ossuary_ways", "bell_shaft", "field_of_bells", "bellmakers_rest"))
                h.assertTrue(GateWorlds.get(BELL + b) != null, "the Bell Crypt's " + b + " loads");
            for (String id : GateWorlds.all().keySet()) {
                GateWorld w = GateWorlds.get(id);
                h.assertTrue(w != null && w.problem().isEmpty(), "the recipe " + id + " loads");
                h.assertTrue(w.tier() == GateWorld.Tier.SECRET || WorldEngine.of(w.kind()) != null, id + " has its engine");
                if (src != null) {
                    h.assertTrue(src.has(w.biome()), id + " shows its own biome " + w.biome());
                    for (GateWorld.Mood m : w.moods()) h.assertTrue(m.biome().isEmpty() || src.has(m.biome()), id + "'s mood " + m.id() + " has its biome");
                }
            }
            h.succeed();
        });
        AwakenTests.register("gateworlds_atlas_view", 200, h -> {
            // what the Codex's Atlas shows: found worlds in full, unfound ones as outlines, secret ones only once found
            GateAtlas atlas = new GateAtlas();
            atlas.arrive(BELL + "sunken_belfry", "tolling", 3);
            atlas.secret(BELL + "sunken_belfry", "odd_rope");
            atlas.met("the_ringer");
            var v = dev.awaken.gates.worlds.WorldsLive.view(atlas);
            long bells = GateWorlds.all().values().stream().filter(w -> w.theme() == dev.awaken.gates.content.Theme.BELL_CRYPT).count();
            h.assertTrue(v.found() == 1 && v.total() >= bells, "found 1 of every world: " + v.found() + " of " + v.total());
            var belfry = v.cards().stream().filter(c -> c.world().equals(BELL + "sunken_belfry")).findFirst().orElse(null);
            h.assertTrue(belfry != null && belfry.found() && belfry.name().equals("The Sunken Belfry"), "the Belfry's card is filled in");
            h.assertTrue(belfry.moods().equals(List.of("Tolling")) && belfry.moodsAll() == 4, "moods seen " + belfry.moods());
            h.assertTrue(belfry.secrets().equals(List.of("The odd rope")) && belfry.secretsAll() == 2, "secrets " + belfry.secrets());
            h.assertTrue(belfry.met().equals("The Ringer") && belfry.visits() == 1, "met " + belfry.met());
            var field = v.cards().stream().filter(c -> c.world().equals(BELL + "field_of_bells")).findFirst().orElse(null);
            h.assertTrue(field != null && !field.found() && field.name().isEmpty() && field.rarity().equals("Rare"),
                    "an unfound world is an outline with its rarity");
            h.assertTrue(v.cards().stream().noneMatch(c -> c.world().equals(BELL + "bellmakers_rest")), "a secret world has no card until found");
            atlas.arrive(BELL + "bellmakers_rest", "", 4);
            var v2 = dev.awaken.gates.worlds.WorldsLive.view(atlas);
            h.assertTrue(v2.found() == 2 && v2.total() == v.total(), "finding the secret world counts it");
            h.assertTrue(v2.cards().stream().anyMatch(c -> c.world().equals(BELL + "bellmakers_rest") && c.found()), "and gives it a card");
            h.succeed();
        });
        world("gateworlds_belfry", BELL + "sunken_belfry", 8);
        world("gateworlds_ossuary", BELL + "ossuary_ways", 8);
        world("gateworlds_field", BELL + "field_of_bells", 8);
        world("gateworlds_shaft", BELL + "bell_shaft", 8);
        // the cave themes (38.10): every world a floor can be (a secret world is a place on its host's floors)
        for (String id : CAVE_WORLDS) world("gateworlds_" + id.substring(id.indexOf('/') + 1), "awaken:" + id, 6);
        // the D themes (38.11)
        for (String id : D_WORLDS) world("gateworlds_" + id.substring(id.indexOf('/') + 1), "awaken:" + id, 6);
        // the C themes (38.12)
        for (String id : C_WORLDS) world("gateworlds_" + id.substring(id.indexOf('/') + 1), "awaken:" + id, 6);
        // the sky, the abyss and the rare worlds (38.13)
        for (String id : E_WORLDS) world("gateworlds_" + id.substring(id.indexOf('/') + 1), "awaken:" + id, 6);
        AwakenTests.register("gateworlds_repeats", 1200, h -> {
            // a world that moves you has to look the same where it puts you (38.12): the fold's three slots are one slot
            // again and again, each the same turned about its middle; the endless plain's outer band repeats every 32
            int folds = 0, plains = 0;
            for (int i = 0; i < 4; i++) {
                GatePlan p = dressed(plan(0xF01DL + i * 7919L, i, Theme.NIGHTMARE), "awaken:nightmare/endless_hall", "deep_sleep");
                for (FloorLayout f : FloorBuilder.build(p).main) {
                    if (!f.isWorld()) continue;
                    for (FloorLayout.Mark m : f.marks(FloorLayout.Mark.FOLD)) {
                        WorldCheck w = new WorldCheck(p, f);
                        folds++;
                        for (int x = m.x0(); x <= m.x1(); x++) for (int z = m.z0() + 4; z <= m.z0() + 11; z++) for (int y = m.y0() - 1; y <= m.y1() + 1; y++) {
                            var b = w.at(x, f.baseY + y, z).getBlock();
                            var next = x + 16 <= m.x1() ? w.at(x + 16, f.baseY + y, z).getBlock() : b;
                            var turned = w.at(m.x0() + m.x1() - x, f.baseY + y, m.z0() + m.z1() - z).getBlock();
                            String at = " at " + x + " " + y + " " + z + " (" + b.getDescriptionId() + ") of " + p;
                            h.assertTrue(b == next, "the fold's slots differ: " + next.getDescriptionId() + at);
                            h.assertTrue(b == turned, "the fold isn't the same turned round: " + turned.getDescriptionId() + at);
                        }
                    }
                }
            }
            for (int i = 0; i < 3; i++) {
                GatePlan p = dressed(plan(0x3417EL + i * 7919L, i, Theme.MIRROR_PALACE), "awaken:mirror_palace/the_white", "bright");
                for (FloorLayout f : FloorBuilder.build(p).main) {
                    if (!f.isWorld()) continue;
                    WorldCheck w = new WorldCheck(p, f);
                    plains++;
                    int side = WorldCheck.SIDE;
                    for (int a = 1; a < 32; a++) for (int c = 1; c < side - 1; c++) for (int y = -2; y <= CaveEngine.Cave.PLAIN_H; y++) {
                        int yy = f.baseY + y;
                        String at = " at " + a + "/" + c + " " + y + " of " + p;
                        h.assertTrue(w.at(a, yy, c) == w.at(a + 32, yy, c) && w.at(c, yy, a) == w.at(c, yy, a + 32), "the plain's near band doesn't repeat" + at);
                        h.assertTrue(w.at(side - 1 - a, yy, c) == w.at(side - 33 - a, yy, c) && w.at(c, yy, side - 1 - a) == w.at(c, yy, side - 33 - a),
                                "the plain's far band doesn't repeat" + at);
                    }
                }
            }
            h.assertTrue(folds > 0 && plains > 0, "folds and plains were built (" + folds + ", " + plains + ")");
            h.succeed();
        });
        AwakenTests.register("gateworlds_sky", 400, h -> {
            // an open land stands under the open sky (its floor is built in that dimension) and only while the gate has a
            // whole column to spare for it; without the open sky's dimension no gate picks one (the GameTest server
            // has no datapack dimensions: gates_dimension_json reads the sky's file instead)
            java.util.Set<String> open = new java.util.HashSet<>();
            for (var e : GateWorlds.all().entrySet()) if (e.getValue().open()) open.add(e.getKey());
            h.assertTrue(open.size() >= 7, "the Glass Desert's and the Market's open lands load (" + open.size() + ")");
            int seen = 0;
            for (int i = 0; i < 48; i++) {
                Theme t = i % 2 == 0 ? Theme.GLASS_DESERT : Theme.LANTERN_NIGHT_MARKET;
                GatePlan p = GateWorlds.dress(plan(0x5C1L + i * 7919L, i, t), new GateAtlas(), java.util.Set.of());
                GateLayout l = FloorBuilder.build(p);
                List<FloorLayout> all = new ArrayList<>(l.main);
                all.addAll(l.annex);
                int columns = Math.min(4, 8 - all.size()), talls = 0;
                for (FloorLayout f : all) {
                    FloorWorld fw = p.worldFor(f.area, f.floor);
                    boolean isOpen = fw != null && open.contains(fw.world());
                    h.assertTrue(f.sky == isOpen, "floor " + f.area + "/" + f.floor + " of " + p + " is under the sky only when it's an open land");
                    if (isOpen) {
                        seen++;
                        talls++;
                        h.assertTrue(fw.tall() && f.plot.tall(), "an open land gets a whole column: " + fw.world());
                    }
                }
                h.assertTrue(talls <= columns, talls + " open lands in a gate with " + columns + " columns to spare: " + p);
                GatePlan none = GateWorlds.dress(plan(0x5C1L + i * 7919L, i, t), new GateAtlas(), open);
                for (FloorWorld fw : none.worlds()) h.assertTrue(!open.contains(fw.world()), "with no open sky, no open land: " + fw.world());
            }
            h.assertTrue(seen > 0, "open lands turn up in their themes' gates");
            h.succeed();
        });
    }
}
