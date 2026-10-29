package dev.awaken.gates.world;

import dev.awaken.Awaken;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.content.Layout;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.content.Twist;
import dev.awaken.gates.worlds.GateWorld;
import dev.awaken.gates.worlds.GateWorlds;
import dev.awaken.progression.Rank;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Depth inside the rooms (SPEC §38.14): the ground, walls, roofs and wild growth that came with content version 7, and
 * the promise that a gate made before them (version 6) carves exactly what it always did.
 */
public final class GateDepthTests {
    private GateDepthTests() {}

    /** Every world a floor can be, one plan each (the rare worlds in their own theme's gate). */
    static List<String> allWorlds() {
        List<String> out = new ArrayList<>();
        for (String w : List.of("sunken_belfry", "ossuary_ways", "field_of_bells", "bell_shaft")) out.add(GateWorldBellTests.BELL + w);
        for (List<String> l : List.of(GateWorldBellTests.CAVE_WORLDS, GateWorldBellTests.D_WORLDS, GateWorldBellTests.C_WORLDS, GateWorldBellTests.E_WORLDS))
            for (String id : l) out.add("awaken:" + id);
        return out;
    }

    /** The first world floor of a version-{@code version} gate in that world: its blocks hashed in carve order. */
    static long carveHash(String id, int version) {
        GateWorld w = GateWorlds.get(id);
        if (w == null) return 0;
        Theme t = w.theme();
        int i = Math.floorMod(id.hashCode(), 5);
        GatePlan base = new GatePlan(0xDE97L + Math.floorMod(id.hashCode(), 9973), version, Rank.of(i), t, Layout.values()[i % Layout.values().length],
                List.of(), Twist.NONE, 3, t.bossId(), t == Theme.HIVE ? Theme.FUNGAL_HOLLOW : Theme.HIVE, 0);
        String mood = w.moods().isEmpty() ? "" : w.moods().get(0).id();
        GatePlan p = GateWorldBellTests.dressed(base, id, mood);
        GateLayout l = FloorBuilder.build(p);
        for (FloorLayout f : l.main) {
            if (!f.isWorld()) continue;
            long[] h = {0xcbf29ce484222325L};
            Map<BlockState, Integer> ids = new IdentityHashMap<>();
            for (int gx = 0; gx < Plots.SIDE / 16; gx++) {
                for (int gz = 0; gz < Plots.SIDE / 16; gz++) {
                    final int bx = gx * 16, bz = gz * 16;
                    f.model.carve(p, f, f.originSlotX + gx, f.originSlotZ + gz, (lx, y, lz, s) -> {
                        int sid = ids.computeIfAbsent(s, k -> k.toString().hashCode());
                        long v = ((long) (bx + lx) * 73856093L) ^ ((long) y * 19349663L) ^ ((long) (bz + lz) * 83492791L) ^ ((long) sid << 20);
                        h[0] = (h[0] ^ v) * 0x100000001b3L;
                    });
                }
            }
            return h[0];
        }
        return 0;
    }

    /** Version 6's carves, taken from the build before content version 7 (29 Oct 2026): world id to hash. */
    static final Map<String, Long> GOLDEN_V6 = new LinkedHashMap<>();

    static {
        GOLDEN_V6.put("awaken:bell_crypt/sunken_belfry", 0x65587be8f1394cd8L);
        GOLDEN_V6.put("awaken:bell_crypt/ossuary_ways", 0xeaad89ada29470eeL);
        GOLDEN_V6.put("awaken:bell_crypt/field_of_bells", 0xc39ba9b9d4a9d2a8L);
        GOLDEN_V6.put("awaken:bell_crypt/bell_shaft", 0x6b17fa41cb905fc3L);
        GOLDEN_V6.put("awaken:fungal_hollow/glowing_deep", 0xdfacc1f3f587b00L);
        GOLDEN_V6.put("awaken:fungal_hollow/root_warrens", 0xfb261ea08d559796L);
        GOLDEN_V6.put("awaken:fungal_hollow/spore_canopy", 0xc5ef84d25914cb16L);
        GOLDEN_V6.put("awaken:fungal_hollow/inside_the_bloom", 0x9d037d996fd855acL);
        GOLDEN_V6.put("awaken:ember_mines/working_mine", 0x543600c9bc2c8192L);
        GOLDEN_V6.put("awaken:ember_mines/open_chasm", 0xdfd8ca080a8e0b53L);
        GOLDEN_V6.put("awaken:ember_mines/forge_below", 0xc5587f58142516daL);
        GOLDEN_V6.put("awaken:ember_mines/cooling_sea", 0xe75ac4ec056fb3a9L);
        GOLDEN_V6.put("awaken:hive/the_comb", 0xa209fb71f329c92aL);
        GOLDEN_V6.put("awaken:hive/brood_hall", 0x56ebb01594f50042L);
        GOLDEN_V6.put("awaken:hive/honey_lake", 0x33f52b1b72b37f56L);
        GOLDEN_V6.put("awaken:hive/the_swarm", 0x99d47de68b8f9283L);
        GOLDEN_V6.put("awaken:overgrown_temple/jungle_courts", 0x3a4585ee0d24ac62L);
        GOLDEN_V6.put("awaken:overgrown_temple/root_halls", 0x6df5a339a91ceb60L);
        GOLDEN_V6.put("awaken:overgrown_temple/waterfall_temple", 0x3b7871c78ddea4e5L);
        GOLDEN_V6.put("awaken:overgrown_temple/great_tree", 0x81eb0c5f460f841aL);
        GOLDEN_V6.put("awaken:drowned_archive/stacks", 0x818ffeefcc848149L);
        GOLDEN_V6.put("awaken:drowned_archive/atrium", 0x5149745ef5f207L);
        GOLDEN_V6.put("awaken:drowned_archive/sunken_shelves", 0xdc1c44edcf39d34L);
        GOLDEN_V6.put("awaken:drowned_archive/inkwell", 0x9829b0f2e25fe4e5L);
        GOLDEN_V6.put("awaken:clockwork_foundry/works_floor", 0x827d44d5b3a4802L);
        GOLDEN_V6.put("awaken:clockwork_foundry/pipe_maze", 0x3c6520f8c9a26db6L);
        GOLDEN_V6.put("awaken:clockwork_foundry/great_engine", 0x83de90aaa37fa957L);
        GOLDEN_V6.put("awaken:clockwork_foundry/stopped_hour", 0x2fa71a57f7c4fa86L);
        GOLDEN_V6.put("awaken:glass_desert/buried_colossus", 0x257f49392c8b3bcbL);
        GOLDEN_V6.put("awaken:glass_desert/glass_caves", 0x97fea311701f6be0L);
        GOLDEN_V6.put("awaken:glass_desert/sunken_city", 0x9e2ad6b60a7753a4L);
        GOLDEN_V6.put("awaken:glass_desert/mirage", 0xdb49f534f2a72547L);
        GOLDEN_V6.put("awaken:lantern_night_market/lantern_street", 0x9e1214c9d6ea4316L);
        GOLDEN_V6.put("awaken:lantern_night_market/rooftops", 0x6c6c6aea2942eabaL);
        GOLDEN_V6.put("awaken:lantern_night_market/floating_market", 0xfc8d3394b0710f3cL);
        GOLDEN_V6.put("awaken:lantern_night_market/festival", 0x43c206c1ccac808cL);
        GOLDEN_V6.put("awaken:frost_monastery/cloisters", 0xe0f081731485aa25L);
        GOLDEN_V6.put("awaken:frost_monastery/ice_caves", 0x3ae3efeb31404f12L);
        GOLDEN_V6.put("awaken:frost_monastery/cliff_stair", 0x24f98fb467645bf6L);
        GOLDEN_V6.put("awaken:frost_monastery/still_snow", 0x8824da46b0f00b01L);
        GOLDEN_V6.put("awaken:mirror_palace/gallery", 0xe561b98fd90afdf7L);
        GOLDEN_V6.put("awaken:mirror_palace/mirror_maze", 0xcae27c13a94c95cdL);
        GOLDEN_V6.put("awaken:mirror_palace/inverted_palace", 0x81c071763f974c2fL);
        GOLDEN_V6.put("awaken:mirror_palace/the_white", 0x90bab322a6d815efL);
        GOLDEN_V6.put("awaken:precursor_lab/facility", 0x715b2c30077b7dc5L);
        GOLDEN_V6.put("awaken:precursor_lab/server_abyss", 0x564762af33051365L);
        GOLDEN_V6.put("awaken:precursor_lab/containment", 0xf5ab7aae0151f06L);
        GOLDEN_V6.put("awaken:precursor_lab/simulation", 0x3108a591687ed931L);
        GOLDEN_V6.put("awaken:nightmare/the_house", 0xad0de10592a2f756L);
        GOLDEN_V6.put("awaken:nightmare/endless_hall", 0xc070a9274e0cb728L);
        GOLDEN_V6.put("awaken:nightmare/upside_down", 0x602da29706cf913bL);
        GOLDEN_V6.put("awaken:nightmare/sleepers_dream", 0xe63c81b4f40bfa81L);
        GOLDEN_V6.put("awaken:sky_archipelago/the_isles", 0x7d969049e147dc8eL);
        GOLDEN_V6.put("awaken:sky_archipelago/sky_ruins", 0xa66006fb6ba48c66L);
        GOLDEN_V6.put("awaken:sky_archipelago/wind_stair", 0xead12a805af3db5cL);
        GOLDEN_V6.put("awaken:sky_archipelago/above_the_clouds", 0x65aba90f63e80d5aL);
        GOLDEN_V6.put("awaken:abyssal_shrine/black_mirror", 0x694f3a7dbbc650bcL);
        GOLDEN_V6.put("awaken:abyssal_shrine/dark_steps", 0x212e64aa000a043dL);
        GOLDEN_V6.put("awaken:abyssal_shrine/starfall", 0x919aaef78ea478fbL);
        GOLDEN_V6.put("awaken:abyssal_shrine/the_last_room", 0xe2627e2cd88b6ba1L);
        GOLDEN_V6.put("awaken:rare/the_quiet", 0xa1780a05917eb399L);
        GOLDEN_V6.put("awaken:rare/upward_rain", 0xf44351fdf75ba2c0L);
        GOLDEN_V6.put("awaken:rare/the_aurora_steppe", 0x4c08acf18d459a62L);
        GOLDEN_V6.put("awaken:rare/the_lantern_orchard", 0x99521e27c86cab0aL);
        GOLDEN_V6.put("awaken:rare/the_last_campfire", 0x2ff159189bb2d04L);
        GOLDEN_V6.put("awaken:rare/the_cartographers_table", 0x2ccd76c38dc1fea4L);
        GOLDEN_V6.put("awaken:rare/the_reflection", 0xfb74d9742e85c168L);
        GOLDEN_V6.put("awaken:rare/the_other_gate", 0x262f6faa8d37a6f3L);
        GOLDEN_V6.put("awaken:rare/the_hollow_sky", 0x4a7084f5df7a9781L);
        GOLDEN_V6.put("awaken:rare/the_endless_stair", 0xaeb2f34549d039fdL);
        GOLDEN_V6.put("awaken:rare/the_gates_heart", 0xc8649f8c9cdec075L);
        GOLDEN_V6.put("awaken:rare/the_tide_room", 0xe7e643818da6a605L);
        GOLDEN_V6.put("awaken:rare/the_library_of_doors", 0xa977dad693930ef6L);
        GOLDEN_V6.put("awaken:rare/the_dollhouse", 0x3863fe68ce640362L);
        GOLDEN_V6.put("awaken:rare/the_glass_sea", 0x2d8f262633250380L);
    }

    /**
     * The same carves as Linux on x86 (CI) makes them, where they differ from the Mac's: FrostDress places by Math.sin and
     * cos, which the two can round differently in the last digit, enough to move a block. Either hash is version 6 unchanged.
     */
    static final Map<String, Long> GOLDEN_V6_X86 = Map.of("awaken:frost_monastery/cloisters", 0xea1d0bb95632b0a3L);

    /** How deep a world's places are (SPEC §38.14, "How we'll know"), over its first two world floors. */
    record Depth(int cols, double flat, int heights, double partial, double plumb, double roofSpread, double detail, double planMs, int jumps, int climbs) {
        @Override
        public String toString() {
            return String.format("cols %d flat %.1f%% heights %d partial %.1f%% plumb %.1f%% roofSD %.1f detail %.1f plan %.1f ms jumps %d climbs %d",
                    cols, flat, heights, partial, plumb, roofSpread, detail, planMs, jumps, climbs);
        }
    }

    /** The plan a world's depth is measured on (the same for every version, so the numbers compare). */
    static GatePlan measured(String id, int version) {
        GateWorld w = GateWorlds.get(id);
        Theme gate = w.tier() == GateWorld.Tier.RARE ? Theme.values()[(w.theme().ordinal() + 5) % Theme.values().length] : w.theme();
        GatePlan base = new GatePlan(0xD3E7L, version, Rank.of(1), gate, Layout.values()[1], List.of(), Twist.NONE, 4, gate.bossId(),
                gate == Theme.HIVE ? Theme.FUNGAL_HOLLOW : Theme.HIVE, 0);
        String mood = w.moods().isEmpty() ? "" : w.moods().get(0).id();
        return GateWorldBellTests.dressed(base, id, mood);
    }

    /**
     * Every column of a place you can walk to (its feet within ten blocks of the floor line), measured as the plan's
     * page measured today's rooms: the share at the floor line, the standing heights in half steps, the surfaces that
     * aren't full blocks, the walls plumb from feet to head, the roof's spread and what stands in the space.
     */
    static Depth depth(GatePlan p) {
        long t0 = System.nanoTime();
        GateLayout l = FloorBuilder.build(p);
        double planMs = (System.nanoTime() - t0) / 1e6;
        long cols = 0, flat = 0, surf = 0, partial = 0, detail = 0, edge = 0, plumb = 0;
        List<Integer> roofs = new ArrayList<>();
        Map<Integer, java.util.Set<Integer>> heights = new java.util.HashMap<>();
        Map<Integer, Integer> placeCols = new java.util.HashMap<>();
        int floors = 0, jumps = 0, climbs = 0;
        int[][] n4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (FloorLayout f : l.main) {
            if (!f.isWorld() || floors >= 2) continue;
            floors++;
            WorldCheck wc = new WorldCheck(p, f);
            // only where you can get to on foot from the arrival (not the shell's top over a low roof)
            Room arrival = f.room(f.start);
            Room.Feature e = arrival.feature(Room.FeatureKind.ENTRANCE);
            if (e == null) continue;
            int ey = f.baseY + arrival.dy + f.ground(e.x(), e.z()) + e.y();
            // the way stays easy: from the arrival to the floor's end without a single jump
            wc.noJump = true;
            GateWorldBellTests.walkAll(wc, f, e.x(), ey, e.z());
            if (GateWorldBellTests.goal(wc, f, f.room(f.end)) == null) jumps++;
            wc.noJump = false;
            GateWorldBellTests.walkAll(wc, f, e.x(), ey, e.z());
            for (Room r : f.rooms) {
                if (r.rig != null || r.type == Room.Type.BOSS) continue;
                int base = f.baseY + r.dy;
                for (int x = r.gx * 16; x < (r.gx + r.gw) * 16; x++) for (int z = r.gz * 16; z < (r.gz + r.gh) * 16; z++) {
                    if (f.places != null && f.places.groundAt(x, z) != r.id) continue;
                    int feet = Integer.MIN_VALUE;
                    for (int y = base - 6; y <= base + 10 && feet == Integer.MIN_VALUE; y++) if (wc.reached(x, y, z)) feet = y;
                    if (feet == Integer.MIN_VALUE) continue;
                    // got to more than 4 over the ground there: up a wall, onto a pillar's flare (the Brood Hall's ledges too)
                    for (int y = base + f.ground(x, z) + 5; y <= base + 48; y++) if (wc.reached(x, y, z)) {
                        climbs++;
                        break;
                    }
                    cols++;
                    BlockState under = wc.at(x, feet - 1, z);
                    double top = under.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO).max(net.minecraft.core.Direction.Axis.Y);
                    int half = (int) Math.round((feet - 1 + (top <= 0 ? 1 : top) - base) * 2);
                    if (half == 0) flat++;
                    heights.computeIfAbsent(floors * 1000 + r.id, k -> new java.util.HashSet<>()).add(half);
                    placeCols.merge(floors * 1000 + r.id, 1, Integer::sum);
                    int roof = 0;
                    while (roof < 60 && wc.pass(x, feet + roof, z)) roof++;
                    if (roof < 60) roofs.add(roof);
                    for (int y = feet; y < feet + roof; y++) {
                        BlockState s = wc.at(x, y, z);
                        if (!s.isAir() && s.getFluidState().isEmpty()) detail++;
                    }
                    for (int[] d : n4) {
                        if (!wc.pass(x + d[0], feet + 1, z + d[1])) {
                            edge++;
                            if (!wc.pass(x + d[0], feet + 2, z + d[1]) && !wc.pass(x + d[0], feet, z + d[1])) plumb++;
                        }
                    }
                    for (BlockState s : new BlockState[]{under, wc.at(x, feet + roof, z)}) {
                        if (s.isAir() || !s.getFluidState().isEmpty()) continue;
                        surf++;
                        if (!full(s)) partial++;
                    }
                    for (int[] d : n4) for (int y = feet; y < feet + Math.min(roof, 6); y++) {
                        BlockState s = wc.at(x + d[0], y, z + d[1]);
                        if (wc.pass(x + d[0], y, z + d[1]) || s.isAir()) continue;
                        surf++;
                        if (!full(s)) partial++;
                    }
                }
            }
        }
        double mean = roofs.stream().mapToInt(i -> i).average().orElse(0);
        double sd = Math.sqrt(roofs.stream().mapToDouble(i -> (i - mean) * (i - mean)).average().orElse(0));
        // the distinct half-step heights a place stands at, the median over places of a chunk or more
        List<Integer> per = new ArrayList<>();
        heights.forEach((k, v) -> {
            if (placeCols.get(k) >= 256) per.add(v.size());
        });
        per.sort(null);
        int hs = per.isEmpty() ? 0 : per.get(per.size() / 2);
        return new Depth((int) cols, 100.0 * flat / Math.max(1, cols), hs, 100.0 * partial / Math.max(1, surf), 100.0 * plumb / Math.max(1, edge), sd,
                100.0 * detail / Math.max(1, cols), planMs, jumps, climbs);
    }

    static boolean full(BlockState s) {
        return net.minecraft.world.level.block.Block.isShapeFullBlock(s.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO));
    }

    /**
     * The worlds whose places have their depth so far (38.14): each is held to the plan's targets for dry caves. A
     * world joins when its theme's ground is built.
     */
    static final List<String> DEEP = List.of("awaken:hive/the_comb", "awaken:hive/brood_hall", "awaken:hive/the_swarm");

    public static void register() {
        AwakenTests.register("gateworlds_depth", 6000, h -> {
            // how deep every world's places are, and the deep ones held to the targets (38.14, "How we'll know")
            StringBuilder log = new StringBuilder();
            List<String> short_ = new ArrayList<>();
            for (String id : allWorlds()) {
                GateWorld w = GateWorlds.get(id);
                if (w == null || w.tier() == GateWorld.Tier.SECRET) continue;
                Depth d = depth(measured(id, GatePlan.CONTENT_VERSION));
                log.append("DEPTH ").append(id).append(' ').append(w.kind()).append(' ').append(d).append('\n');
                if (!DEEP.contains(id)) continue;
                if (d.flat() > 55 || d.heights() < 8 || d.partial() < 12 || d.plumb() > 65 || d.roofSpread() < 6 || d.detail() < 35 || d.jumps() > 0
                        || "caves".equals(w.kind()) && d.climbs() > 0)
                    short_.add(id + " (" + d + ")");
            }
            Awaken.LOG.info("[gates] depth of the worlds' places:\n{}", log);
            h.assertTrue(short_.isEmpty(), "places short of their depth: " + short_);
            h.succeed();
        });
        AwakenTests.register("gateworlds_depth_v6_golden", 2400, h -> {
            // a gate made before the ground came keeps the rooms it was made with, block for block (38.14)
            List<String> wrong = new ArrayList<>();
            StringBuilder log = new StringBuilder();
            for (String id : allWorlds()) {
                long got = carveHash(id, 6);
                log.append("DEPTHGOLD ").append(id).append(' ').append(Long.toHexString(got)).append('\n');
                Long want = GOLDEN_V6.get(id);
                if ((want == null || want != got) && !Long.valueOf(got).equals(GOLDEN_V6_X86.get(id))) wrong.add(id);
            }
            Awaken.LOG.info("[gates] version 6 carves:\n{}", log);
            h.assertTrue(wrong.isEmpty(), "version 6 floors carve differently now: " + wrong);
            h.succeed();
        });
    }
}
