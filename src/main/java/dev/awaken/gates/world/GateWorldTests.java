package dev.awaken.gates.world;

import com.mojang.serialization.JsonOps;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.GateService;
import dev.awaken.gates.content.FloorWorld;
import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.content.Layout;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.content.Twist;
import dev.awaken.gates.worlds.GateAtlas;
import dev.awaken.gates.worlds.GateWorld;
import dev.awaken.gates.worlds.GateWorlds;
import dev.awaken.progression.Rank;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gate Worlds' foundation GameTests (SPEC §38, "How we'll know"), one per mechanism: plots (every floor in its own plot,
 * found again by position; older plans keep their bands), today's rooms unchanged inside the new frame, the place map,
 * every recipe loading with its biomes, and the world director.
 */
public final class GateWorldTests {
    private GateWorldTests() {}

    static final int PLANS = 400;

    /** Varied plans: every theme, rank, layout, floor count and both annex twists, plus the story's and trials' plans. */
    static GatePlan plan(int i, int version) {
        Theme t = Theme.values()[i % Theme.values().length];
        if (i % 97 == 0) return new GatePlan(0x38L + i, version, Rank.E, Theme.BELL_CRYPT, Layout.LABYRINTH, List.of(), Twist.NONE, 1,
                GatePlan.RITUAL_BOSS, Theme.HIVE, 0);
        if (i % 97 == 1) return new GatePlan(0x38L + i, version, Rank.D, Theme.CLOCKWORK_FOUNDRY, Layout.LABYRINTH, List.of(), Twist.NONE, 1,
                GatePlan.LEAPER_TRIAL, Theme.HIVE, 0);
        if (i % 97 == 2) return new GatePlan(0x38L + i, version, Rank.B, Theme.SKY_ARCHIPELAGO, Layout.LABYRINTH, List.of(), Twist.NONE, 1,
                GatePlan.SKYBORN_TRIAL, Theme.HIVE, 0);
        Twist tw = i % 5 == 0 ? Twist.DOUBLE_GATE : i % 5 == 1 ? Twist.GATE_WITHIN : Twist.NONE;
        Layout lay = Layout.values()[(i / 3) % Layout.values().length];
        Theme annex = Theme.values()[(i + 3) % Theme.values().length];
        return new GatePlan(0x38L + i * 7919L, version, Rank.of(i % 5), t, lay, List.of(), tw, 1 + i % 6, t.bossId(), annex, i % 3);
    }

    static List<FloorLayout> floors(GateLayout l) {
        List<FloorLayout> all = new ArrayList<>(l.main);
        all.addAll(l.annex);
        return all;
    }

    static boolean overlap(Plots.Plot a, Plots.Plot b) {
        return a.quadrant() == b.quadrant() && a.minY() <= b.maxY() && b.minY() <= a.maxY();
    }

    public static void register() {
        AwakenTests.register("gateworlds_plots", 600, h -> {
            for (int i = 0; i < PLANS; i++) {
                GatePlan p = plan(i, GatePlan.CONTENT_VERSION);
                GateLayout l = FloorBuilder.build(p);
                List<FloorLayout> all = floors(l);
                for (FloorLayout f : all) {
                    Plots.Plot pl = f.plot;
                    h.assertTrue(pl != null, "every version 6 floor has a plot: " + p);
                    int x0 = (f.originSlotX - 1) * 16, x1 = (f.originSlotX + f.w + 1) * 16 - 1;
                    int z0 = (f.originSlotZ - 1) * 16, z1 = (f.originSlotZ + f.h + 1) * 16 - 1;
                    h.assertTrue(x0 >= pl.x0() && x1 < pl.x0() + Plots.SIDE && z0 >= pl.z0() && z1 < pl.z0() + Plots.SIDE,
                            "floor " + f.area + ":" + f.floor + " (and its rock margin) stays inside its quadrant: " + p);
                    h.assertTrue(f.yLo >= pl.minY() && f.yHi <= pl.maxY(), "and inside its tier: " + f.yLo + ".." + f.yHi + " in " + pl);
                    Room s = f.room(f.start);
                    int lx = f.originSlotX * 16 + s.centerX(), lz = f.originSlotZ * 16 + s.centerZ(), y = f.baseY + s.dy + 1;
                    h.assertTrue(l.floorAt(lx, lz, y) == f && f.roomAt(lx, y, lz) == s,
                            "where you arrive is found again as this floor's start room: " + f.area + ":" + f.floor + " of " + p);
                }
                for (int a = 0; a < all.size(); a++) for (int b = a + 1; b < all.size(); b++)
                    h.assertFalse(overlap(all.get(a).plot, all.get(b).plot), "no two floors share a plot: " + p);
                // version 5 keeps its stacked bands exactly
                GateLayout old = FloorBuilder.build(p.rebuilt(5));
                for (FloorLayout f : floors(old)) {
                    int ox = f.area == 0 ? Cells.GRID_ORIGIN : Cells.ANNEX_SLOT_X + Cells.GRID_ORIGIN;
                    h.assertTrue(f.plot == null && f.baseY == Cells.floorY(f.floor) && f.originSlotX == ox && f.originSlotZ == Cells.GRID_ORIGIN
                            && f.yLo == f.baseY - Cells.BAND_BELOW && f.yHi == f.baseY + Cells.BAND_ABOVE - 1, "version 5 floors keep their bands");
                    Room s = f.room(f.start);
                    h.assertTrue(old.floorAt(f.originSlotX * 16 + s.centerX(), f.originSlotZ * 16 + s.centerZ(), f.baseY + s.dy + 1) == f,
                            "and are found by height as before");
                }
            }
            // tall worlds get a quadrant column only while every floor still fits
            boolean[][] asks = {{true, false, false}, {false, true, true, true, true}, {true, true, true, true, true, true, true, true},
                    {true}, {false, false, false, false, false, false, true, true}};
            int[] granted = {1, 3, 0, 1, 0};
            for (int k = 0; k < asks.length; k++) {
                Plots.Plot[] ps = Plots.assign(asks[k]);
                int tall = 0;
                for (int a = 0; a < ps.length; a++) {
                    if (ps[a].tall()) tall++;
                    for (int b = a + 1; b < ps.length; b++) h.assertFalse(overlap(ps[a], ps[b]), "assigned plots never overlap");
                }
                h.assertTrue(tall == granted[k], "tall plots granted " + tall + ", expected " + granted[k]);
            }
            // a Double Gate's annex floors show the annex theme, found by height and quadrant
            GatePlan dg = new GatePlan(77, GatePlan.CONTENT_VERSION, Rank.D, Theme.HIVE, Layout.LABYRINTH, List.of(), Twist.DOUBLE_GATE, 3,
                    Theme.HIVE.bossId(), Theme.EMBER_MINES, 0);
            int cell = Cells.MAX_CELLS - 1;
            GateCells.put(cell, dg);
            try {
                for (FloorLayout f : floors(FloorBuilder.build(dg))) {
                    Room s = f.room(f.start);
                    int bx = Cells.originX(cell) + f.originSlotX * 16 + s.centerX(), bz = Cells.originZ(cell) + f.originSlotZ * 16 + s.centerZ();
                    h.assertTrue(GateCells.themeAt(bx, f.baseY + 2, bz) == (f.area == 1 ? Theme.EMBER_MINES : Theme.HIVE),
                            "floor " + f.area + ":" + f.floor + " shows its own theme");
                }
            } finally {
                GateCells.remove(cell);
            }
            h.succeed();
        });

        // "Today's rooms run inside the new frame unchanged": the same rooms, painted block for block, only moved
        AwakenTests.register("gateworlds_same_rooms", 900, h -> {
            for (int i = 0; i < PLANS; i++) {
                GatePlan p = plan(i, GatePlan.CONTENT_VERSION);
                List<FloorLayout> a = floors(FloorBuilder.build(p.rebuilt(5))), b = floors(FloorBuilder.build(p));
                h.assertTrue(a.size() == b.size(), "the same floors");
                for (int fi = 0; fi < a.size(); fi++) h.assertTrue(signature(a.get(fi)).equals(signature(b.get(fi))),
                        "floor " + a.get(fi).area + ":" + a.get(fi).floor + " has the same rooms on version 6: " + p);
            }
            for (int i = 3; i < 3 + 40; i += 3) {
                GatePlan p = plan(i, GatePlan.CONTENT_VERSION);
                GateLayout la = FloorBuilder.build(p.rebuilt(5)), lb = FloorBuilder.build(p);
                FloorLayout fa = la.main.get(0), fb = lb.main.get(0);
                Map<Long, BlockState> ba = InteriorTests.paintFloor(la, fa, 21), bb = InteriorTests.paintFloor(lb, fb, 21);
                int dy = fb.baseY - fa.baseY;
                int same = 0;
                for (var e : ba.entrySet()) {
                    // keys pack y in bits 20..39: moving every block dy up moves only that field
                    BlockState o = bb.get(e.getKey() + ((long) dy << 20));
                    // a few pieces pick a block's variant by its height (shelves, ore, obsidian, honey): only those may differ
                    if (o != null && (e.getValue().equals(o) || variants(e.getValue(), o))) same++;
                }
                h.assertTrue(ba.size() == bb.size() && same == ba.size(),
                        "floor 1 of " + p.theme().id + " paints the same blocks, moved " + dy + " up: " + same + " of " + ba.size());
            }
            h.succeed();
        });

        AwakenTests.register("gateworlds_place_map", 300, h -> {
            for (int i = 0; i < 120; i++) {
                for (FloorLayout f : floors(FloorBuilder.build(plan(i, GatePlan.CONTENT_VERSION)))) {
                    PlaceMap m = PlaceMap.ofSlots(f);
                    for (int x = 0; x < f.w * 16; x += 2) for (int z = 0; z < f.h * 16; z += 2) {
                        Room r = f.roomAtSlot(x >> 4, z >> 4);
                        h.assertTrue(m.at(x, 3, z) == (r == null ? -1 : r.id), "the place map answers as the slot grid at " + x + "," + z);
                    }
                }
            }
            // a gallery over an aisle: the ground below the split, the gallery from it up
            PlaceMap m = new PlaceMap(64, 64);
            m.ground(0, 0, 64, 64, 0);
            m.upper(16, 0, 32, 64, 12, 1);
            m.groundDisc(48, 48, 6, 2);
            h.assertTrue(m.at(20, 3, 10) == 0 && m.at(20, 12, 10) == 1 && m.at(20, 30, 10) == 1 && m.at(8, 30, 10) == 0,
                    "stacked places split at their height");
            h.assertTrue(m.at(48, 0, 48) == 2 && m.at(36, 0, 36) == 0 && m.at(-1, 0, 0) == -1 && m.at(64, 0, 0) == -1, "discs and edges");
            h.succeed();
        });

        AwakenTests.register("gateworlds_recipes", 200, h -> {
            MinecraftServer server = h.getLevel().getServer();
            Map<Identifier, ?> files = server.getResourceManager().listResources("gate_worlds", id -> id.getPath().endsWith(".json"));
            h.assertTrue(files.size() == GateWorlds.all().size(),
                    "every recipe file loads (" + GateWorlds.all().size() + " of " + files.size() + "; the log says why one is skipped)");
            ServerLevel gl = GateService.gateLevel(server);
            GateBiomeSource src = gl != null && gl.getChunkSource().getGenerator().getBiomeSource() instanceof GateBiomeSource s ? s : null;
            var biomes = server.registryAccess().lookupOrThrow(Registries.BIOME);
            for (var e : GateWorlds.all().entrySet()) {
                GateWorld w = e.getValue();
                h.assertTrue(w.problem().isEmpty() && w.schema() == GateWorld.SCHEMA, e.getKey() + ": " + w.problem());
                h.assertTrue(e.getKey().contains(w.theme().id + "/"), e.getKey() + " lives in its theme's folder");
                List<String> ids = new ArrayList<>();
                ids.add(w.biome());
                for (GateWorld.Mood m : w.moods()) ids.add(w.biomeFor(m.id()));
                for (String b : ids) {
                    if (b.isEmpty()) continue;
                    Identifier id = Identifier.parse(b);
                    h.assertTrue(biomes.get(net.minecraft.resources.ResourceKey.create(Registries.BIOME, id)).isPresent(), e.getKey() + ": no biome " + b);
                    h.assertTrue(src == null || src.has(b), e.getKey() + ": the gates dimension doesn't list " + b);
                }
            }
            h.succeed();
        });

        AwakenTests.register("gateworlds_director", 300, h -> {
            Map<String, GateWorld> saved = GateWorlds.all();
            try {
                Map<String, GateWorld> cat = new HashMap<>();
                String a = "awaken_test:bell_crypt/a", b = "awaken_test:bell_crypt/b", deep = "awaken_test:bell_crypt/deep",
                        wonder = "awaken_test:bell_crypt/wonder", secret = "awaken_test:bell_crypt/secret", lord = "awaken_test:bell_crypt/lord";
                cat.put(a, world(GateWorld.Tier.EVERYDAY, List.of("floor"), false));
                cat.put(b, world(GateWorld.Tier.EVERYDAY, List.of("floor"), false));
                cat.put(deep, world(GateWorld.Tier.DEEP, List.of("floor"), true));
                cat.put(wonder, world(GateWorld.Tier.WONDER, List.of("floor"), false));
                cat.put(secret, world(GateWorld.Tier.SECRET, List.of("floor"), false));
                cat.put(lord, world(GateWorld.Tier.EVERYDAY, List.of("boss"), false));
                GateWorlds.replace(cat);
                GateAtlas atlas = new GateAtlas();
                Set<String> met = new HashSet<>();
                String last = "";
                boolean deepAt3 = false, wonderAt9 = false;
                for (int g = 0; g < 40; g++) {
                    GatePlan p = new GatePlan(0xB311L + g * 31L, GatePlan.CONTENT_VERSION, Rank.E, Theme.BELL_CRYPT, Layout.LABYRINTH, List.of(),
                            Twist.NONE, 3, Theme.BELL_CRYPT.bossId(), Theme.HIVE, 0);
                    GatePlan d = GateWorlds.dress(p, atlas, Set.of());
                    h.assertTrue(d.equals(GateWorlds.dress(p, copy(atlas), Set.of())), "the same plan and Atlas pick the same worlds");
                    Set<String> inGate = new HashSet<>();
                    for (int f = 1; f <= 3; f++) {
                        FloorWorld fw = d.worldFor(0, f);
                        h.assertTrue(fw != null, "every floor of a Bell Crypt gets a world while one fits");
                        GateWorld w = cat.get(fw.world());
                        h.assertTrue(f == 3 ? fw.world().equals(lord) : w.ordinary(), "the Lord's floor gets a world that holds a Lord");
                        h.assertFalse(fw.world().equals(secret), "secret worlds are found, never rolled");
                        h.assertFalse(fw.world().equals(last), "never the same world back to back: " + fw.world());
                        h.assertTrue(f == 3 || inGate.add(fw.world()), "never the same world twice in a gate while another fits");
                        h.assertTrue(w.moods().stream().anyMatch(m -> m.id().equals(fw.mood())) && fw.tall() == w.tall(), "a mood of its own, and its height saved");
                        if (fw.world().equals(deep) && atlas.themeFloors("bell_crypt") < 4) h.fail("the deep world opens after 4 floors of the theme at E");
                        if (fw.world().equals(wonder) && atlas.themeFloors("bell_crypt") < 10) h.fail("the wonder opens after 10");
                        atlas.arrive(fw.world(), fw.mood(), g);
                        met.add(fw.world() + "#" + fw.mood());
                        last = fw.world();
                        if (f < 3) atlas.floorCleared("bell_crypt");
                    }
                    if (g == 6) deepAt3 = atlas.visits(deep) > 0;
                    if (g == 20) wonderAt9 = atlas.visits(wonder) > 0;
                }
                h.assertTrue(deepAt3 && wonderAt9, "mastery opens the deep world and the wonder, and the director goes to them: "
                        + atlas.visits(deep) + ", " + atlas.visits(wonder));
                h.assertTrue(met.size() >= 16, "40 gates meet most worlds in most moods: " + met.size());
                GatePlan p = new GatePlan(5, GatePlan.CONTENT_VERSION, Rank.E, Theme.BELL_CRYPT, Layout.LABYRINTH, List.of(), Twist.NONE, 2,
                        Theme.BELL_CRYPT.bossId(), Theme.HIVE, 0);
                for (int s = 0; s < 30; s++) {
                    GatePlan d = GateWorlds.dress(new GatePlan(s, 6, Rank.E, Theme.BELL_CRYPT, Layout.LABYRINTH, List.of(), Twist.NONE, 2,
                            Theme.BELL_CRYPT.bossId(), Theme.HIVE, 0), new GateAtlas(), Set.of(a));
                    h.assertTrue(d.worldFor(0, 1) != null && !d.worldFor(0, 1).world().equals(a), "a world switched off is never picked");
                }
                h.assertTrue(GateWorlds.dress(p.rebuilt(5), new GateAtlas(), Set.of()).worlds().isEmpty(), "version 5 plans are never dressed");
                h.assertTrue(GateWorlds.dress(plan(0, 6), new GateAtlas(), Set.of()).worlds().isEmpty(), "nor is the story's ritual");
                // saved and loaded
                GatePlan d = GateWorlds.dress(p, new GateAtlas(), Set.of());
                var json = GatePlan.CODEC.encodeStart(JsonOps.INSTANCE, d).getOrThrow();
                h.assertTrue(GatePlan.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().equals(d), "a plan's worlds are saved");
                json.getAsJsonObject().remove("worlds");
                h.assertTrue(GatePlan.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().worlds().isEmpty(), "an older plan loads with none");
                GateAtlas back = GateAtlas.MAP_CODEC.codec().parse(JsonOps.INSTANCE,
                        GateAtlas.MAP_CODEC.codec().encodeStart(JsonOps.INSTANCE, atlas).getOrThrow()).getOrThrow();
                h.assertTrue(back.worlds.keySet().equals(atlas.worlds.keySet()) && back.visits(a) == atlas.visits(a)
                        && back.themeFloors("bell_crypt") == atlas.themeFloors("bell_crypt") && back.lastWorld().equals(atlas.lastWorld()),
                        "the Atlas is saved");
            } finally {
                GateWorlds.replace(saved);
            }
            h.succeed();
        });
    }

    static GateWorld world(GateWorld.Tier tier, List<String> roles, boolean tall) {
        List<GateWorld.Mood> moods = List.of(new GateWorld.Mood("still", "", 2, ""), new GateWorld.Mood("tolling", "", 1, ""),
                new GateWorld.Mood("requiem", "", 1, ""), new GateWorld.Mood("storm", "", 1, ""));
        return new GateWorld(1, Theme.BELL_CRYPT, "Test", "", tier, GateWorld.Rarity.COMMON, "test", tall, roles, -1, 1, true, "", moods,
                Map.of(), GateWorld.Dressing.NONE);
    }

    static GateAtlas copy(GateAtlas a) {
        return GateAtlas.MAP_CODEC.codec().parse(JsonOps.INSTANCE, GateAtlas.MAP_CODEC.codec().encodeStart(JsonOps.INSTANCE, a).getOrThrow())
                .getOrThrow();
    }

    /** Block pairs a room piece picks between by height (a hash of x + y), so a floor that moves may swap them. */
    static final List<Set<String>> VARIANTS = List.of(
            Set.of("minecraft:bookshelf", "awaken:gate_sodden_shelf"), Set.of("minecraft:gold_ore", "minecraft:gilded_blackstone"),
            Set.of("minecraft:obsidian", "minecraft:crying_obsidian"), Set.of("minecraft:honey_block", "minecraft:yellow_terracotta"));

    static boolean variants(BlockState a, BlockState b) {
        String x = BuiltInRegistries.BLOCK.getKey(a.getBlock()).toString(), y = BuiltInRegistries.BLOCK.getKey(b.getBlock()).toString();
        for (Set<String> v : VARIANTS) if (v.contains(x) && v.contains(y)) return true;
        return false;
    }

    /** A floor's rooms as text: everything the builder decides, nothing about where the floor stands. */
    static String signature(FloorLayout f) {
        StringBuilder sb = new StringBuilder();
        sb.append(f.w).append('x').append(f.h).append(' ').append(f.start).append('>').append(f.end).append(f.bossFloor).append(f.plannedMobs);
        for (Room r : f.rooms) {
            sb.append('|').append(r.type).append(r.shape).append(r.gx).append(',').append(r.gz).append(',').append(r.gw).append(',')
                    .append(r.gh).append(',').append(r.dy).append(',').append(r.height).append(r.piece).append(r.dark).append(r.onPath)
                    .append(r.normals).append(r.elites).append(r.wave2Normals).append(r.wave2Elites)
                    .append(r.rig == null ? "-" : Long.toString(r.rig.signature()));
            for (Room.Door d : r.doors) sb.append('d').append(d.gx).append(d.gz).append(d.side).append(d.y).append(d.toRoom).append(d.sealed).append(d.onward);
            for (Room.Feature x : r.features) sb.append('f').append(x);
            for (int[] s : r.spawnSpots) sb.append('s').append(s[0]).append(',').append(s[1]);
        }
        return sb.toString();
    }
}
