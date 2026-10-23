package dev.awaken.world.landmark;

import dev.awaken.above.ruin.PrecursorRuinPiece;
import dev.awaken.above.ruin.RuinBuilder;
import dev.awaken.above.ruin.RuinKind;
import dev.awaken.above.scan.Scanning;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.GateHooks;
import dev.awaken.core.api.NowHooks;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.progression.Curve;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** GameTests for landmarks (tools/gradle.sh 1200 runGameTestServer, or /test run awaken:landmark_*). */
final class LandmarkTests {
    private LandmarkTests() {}

    static void register() {
        discoveryOnce();
        scannerPoints();
        nowLineGating();
        markersAndLight();
        outerCaches();
    }

    static ServerPlayer awakened(GameTestHelper h, BlockPos at) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        Progression.awaken(p);
        p.teleportTo(h.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), 0, 0, true);
        return p;
    }

    static double totalXp(Profile p) {
        double t = p.xp();
        for (int lv = 1; lv < p.level(); lv++) t += Curve.xpToNext(lv);
        return t;
    }

    // ---- discovery ----------------------------------------------------------------------------------------------------

    static void discoveryOnce() {
        AwakenTests.registerIsolated("landmark_discovery_fires_once", 200, h -> {
            ServerLevel level = h.getLevel();
            BlockPos at = h.absolutePos(new BlockPos(1, 2, 1));
            ServerPlayer p = awakened(h, at);
            Profile prof = Progression.profile(p);
            Landmarks.testOverrides(at.offset(12, 0, 5), true);
            try {
                // the Well
                double before = totalXp(prof);
                h.assertTrue(LandmarkWatch.check(p, false) == 1, "standing at the Well counts");
                double gained = totalXp(prof) - before;
                h.assertTrue(prof.counter("awaken:discovered/landmark/well") == 1 && prof.counter("awaken:landmarks_reached") == 1, "a discovery, counted");
                h.assertTrue(gained > 0, "it pays: " + gained);
                h.assertTrue(Landmarks.reached(p, "well"), "remembered");
                h.assertTrue(LandmarkWatch.check(p, false) == 0, "only once");
                h.assertTrue(totalXp(prof) - before == gained && prof.counter("awaken:landmarks_reached") == 1, "no second payment");
                // away from it: nothing
                Landmarks.testOverrides(at.offset(200, 0, 0), true);
                Landmarks.memory(p).reached.clear();
                h.assertTrue(LandmarkWatch.check(p, false) == 0, "a Well 200 blocks off isn't reached");
                Landmarks.memory(p).reached.add("well");
                // a gate type: once per theme, whichever gate of it
                GateHooks.GateView crypt = new GateHooks.GateView("t1", at, 0, "awaken:bell_crypt", "Bell Crypt", "", "", List.of(), "", 1, "", "READY",
                        Long.MAX_VALUE, at);
                GateHooks.GateView crypt2 = new GateHooks.GateView("t2", at.offset(5, 0, 0), 0, "awaken:bell_crypt", "Bell Crypt", "", "", List.of(), "", 1,
                        "", "READY", Long.MAX_VALUE, at.offset(5, 0, 0));
                h.assertTrue(LandmarkWatch.reachGate(p, crypt), "the first Bell Crypt gate is a discovery");
                h.assertFalse(LandmarkWatch.reachGate(p, crypt2), "another Bell Crypt gate isn't");
                h.assertTrue(prof.counter("awaken:discovered/landmark/gate/bell_crypt") == 1, "keyed by theme");
                // a ruin, through the real path: a structure start referenced by the chunk the player stands in
                ruinPath(h, level, p, prof, at);
            } finally {
                Landmarks.testOverrides(null, false);
            }
            h.succeed();
        });
    }

    private static void ruinPath(GameTestHelper h, ServerLevel level, ServerPlayer p, Profile prof, BlockPos at) {
        Structure monolith = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(RuinKind.MONOLITH.structure).value();
        ChunkPos cp = ChunkPos.containing(at);
        LevelChunk chunk = level.getChunk(cp.x(), cp.z());
        Map<Structure, StructureStart> starts = new HashMap<>(chunk.getAllStarts());
        Map<Structure, LongSet> refs = new HashMap<>(chunk.getAllReferences());
        try {
            PrecursorRuinPiece piece = new PrecursorRuinPiece(RuinKind.MONOLITH, at.offset(-4, -3, -4), 0, 7L, 1);
            chunk.setStartForStructure(monolith, new StructureStart(monolith, cp, 0, new PiecesContainer(List.of(piece))));
            chunk.addReferenceForStructure(monolith, cp.pack());
            h.assertTrue(LandmarkWatch.check(p, false) == 1, "standing in a monolith counts");
            h.assertTrue(Landmarks.reached(p, Landmarks.ruinId(cp)) && prof.counter("awaken:discovered/landmark/monolith") == 1, "remembered, a discovery");
            h.assertTrue(LandmarkWatch.check(p, false) == 0, "only once");
            h.assertTrue(prof.counter("awaken:landmarks_reached") == 2, "two landmarks reached in all");
        } finally {
            chunk.setAllStarts(starts);
            chunk.setAllReferences(refs);
        }
    }

    // ---- the Scanner --------------------------------------------------------------------------------------------------

    static void scannerPoints() {
        AwakenTests.registerIsolated("landmark_scanner_points_at_nearest_unknown", 200, h -> {
            BlockPos at = h.absolutePos(new BlockPos(1, 2, 1));
            ServerPlayer p = awakened(h, at);
            BlockPos well = at.offset(48, 0, 36); // 60 blocks: nearer than any ruin the placement grid can put here
            Landmarks.testOverrides(well, false);
            try {
                Scanning.forget(p.getUUID());
                List<Scanning.Heard> heard = Scanning.heard(p);
                for (int i = 1; i < heard.size(); i++) h.assertTrue(heard.get(i - 1).distance() <= heard.get(i).distance(), "nearest first: " + heard);
                h.assertTrue(heard.size() <= 4, "a handful at most");
                h.assertTrue(heard.stream().anyMatch(x -> x.omen().equals(LandmarkModule.WELL_OMEN) && x.pos().equals(well)), "the Scanner hears the Well: " + heard);
                // the Well is 60 blocks off: it leads the list unless a ruin happens to lie even nearer
                h.assertTrue(heard.get(0).pos().equals(well) || heard.get(0).distance() < 60.0,
                        "the Scanner points at the nearest unknown landmark: " + heard);
                // reached: the Scanner stops hearing it
                LandmarkWatch.reach(p, LandmarkKind.WELL, "well");
                List<Scanning.Heard> after = Scanning.heard(p);
                h.assertTrue(after.stream().noneMatch(x -> x.omen().equals(LandmarkModule.WELL_OMEN)), "a landmark you've stood at goes quiet: " + after);
                // a ruin walked into goes quiet too (if the placement grid puts one in range here)
                if (!after.isEmpty()) {
                    Scanning.Heard ruin = after.get(0);
                    Landmarks.memory(p).reach(Landmarks.ruinIdAt(ruin.pos()));
                    Scanning.forget(p.getUUID());
                    h.assertTrue(Scanning.heard(p).stream().noneMatch(x -> x.pos().equals(ruin.pos())), "a visited ruin goes quiet");
                }
            } finally {
                Landmarks.testOverrides(null, false);
                Scanning.forget(p.getUUID());
            }
            h.succeed();
        });
    }

    // ---- the Now line -------------------------------------------------------------------------------------------------

    static void nowLineGating() {
        AwakenTests.registerIsolated("landmark_now_line_gating", 200, h -> {
            BlockPos at = h.absolutePos(new BlockPos(1, 2, 1));
            ServerPlayer p = awakened(h, at);
            Landmarks.testOverrides(at.offset(400, 0, 300), true);
            try {
                Progression.topUpToLevel(p, 9, Progression.XpSource.COMMAND);
                LandmarkNow.forget(p.getUUID());
                h.assertTrue(LandmarkNow.suggest(p).isEmpty(), "level 9: no reason to go to the Well yet (its first stratum plays at 17)");
                h.assertTrue(Landmarks.rumour(p).isEmpty() && Landmarks.whisper(p).isEmpty(), "nor rumours or whispers about it");
                Progression.topUpToLevel(p, 10, Progression.XpSource.COMMAND);
                LandmarkNow.forget(p.getUUID());
                List<NowHooks.Suggestion> s = LandmarkNow.suggest(p);
                h.assertTrue(s.size() == 1 && s.get(0).id().equals("landmark:well") && s.get(0).priority() == LandmarkNow.PRIORITY
                        && s.get(0).text().getString().equals("The Well · 500 m SE") && s.get(0).where().isPresent(), "level 10: " + describe(s));
                // the town's rumour, told once
                var rumour = Landmarks.rumour(p);
                h.assertTrue(rumour.isPresent() && rumour.get().title().equals("The Well, to the southeast") && rumour.get().body().contains("about 500 blocks"),
                        "a rumour: " + rumour);
                h.assertTrue(Landmarks.rumour(p).isEmpty(), "never the same rumour twice");
                // a spirit's whisper raises it
                var w = Landmarks.whisper(p);
                h.assertTrue(w.isPresent() && w.get().contains("500 m SE"), "the spirit whispers: " + w);
                List<NowHooks.Suggestion> ws = LandmarkNow.suggest(p);
                h.assertTrue(ws.size() == 1 && ws.get(0).priority() == LandmarkNow.WHISPERED && ws.get(0).text().getString().startsWith("Whispered: The Well"),
                        "whispered: " + describe(ws));
                // reached: gone
                LandmarkWatch.reach(p, LandmarkKind.WELL, "well");
                h.assertTrue(LandmarkNow.suggest(p).isEmpty(), "once you've been there, the line goes");
                h.assertTrue(Landmarks.memory(p).whispered().isEmpty(), "and the whisper is spent");
            } finally {
                Landmarks.testOverrides(null, false);
            }
            h.succeed();
        });
    }

    private static String describe(List<NowHooks.Suggestion> s) {
        List<String> out = new ArrayList<>();
        for (NowHooks.Suggestion x : s) out.add(x.id() + " " + x.priority() + " \"" + x.text().getString() + "\"");
        return out.toString();
    }

    // ---- markers ------------------------------------------------------------------------------------------------------

    static void markersAndLight() {
        AwakenTests.registerIsolated("landmark_markers_and_light", 100, h -> {
            // light by time of day: nothing by day, ruins at dusk (faint at night), the Well's pillars at night
            h.assertTrue(MarkerLight.ruinShaft(6000) == 0F && MarkerLight.wellGlow(6000) == 0F, "nothing by day (the vanilla look)");
            h.assertTrue(MarkerLight.ruinShaft(12800) == 1F && MarkerLight.wellGlow(12800) > 0F, "dusk: ruin shafts at full, the Well warming up");
            h.assertTrue(MarkerLight.ruinShaft(18000) == 0.3F && MarkerLight.wellGlow(18000) == 1F, "midnight: the Well glows, ruins faintly");
            h.assertTrue(MarkerLight.ruinShaft(23700) == 0F && MarkerLight.wellGlow(23700) == 0F, "gone by morning");
            for (long t = 0; t < 24000; t += 250) {
                float a = MarkerLight.ruinShaft(t), b = MarkerLight.wellGlow(t);
                h.assertTrue(a >= 0F && a <= 1F && b >= 0F && b <= 1F, "within 0..1 at " + t);
            }
            h.assertTrue(MarkerLight.distanceFade(10) == 0F && MarkerLight.distanceFade(30) == 0.5F && MarkerLight.distanceFade(80) == 1F, "fades up close");
            // what a player is told
            BlockPos at = h.absolutePos(new BlockPos(1, 2, 1));
            ServerPlayer p = awakened(h, at);
            ServerPlayer asleep = h.makeMockServerPlayerInLevel(); // not awakened: the world hasn't revealed itself
            Landmarks.testOverrides(at.offset(100, 0, 0), true);
            try {
                List<LandmarkNet.Marker> m = LandmarkMarkers.compute(p);
                h.assertTrue(m.size() == 1 && m.get(0).landmark() == LandmarkKind.WELL && m.get(0).x() == at.getX() + 100, "the Well is marked: " + m);
                h.assertTrue(LandmarkMarkers.compute(asleep).isEmpty(), "nothing before awakening");
                Landmarks.testOverrides(at.offset(1000, 0, 0), true);
                h.assertTrue(LandmarkMarkers.compute(p).isEmpty(), "nothing beyond sight");
            } finally {
                Landmarks.testOverrides(null, false);
            }
            // the payload survives the wire
            LandmarkNet.Markers sent = new LandmarkNet.Markers(List.of(new LandmarkNet.Marker(LandmarkKind.WELL.ordinal(), -1200, 64, 345, 7),
                    new LandmarkNet.Marker(LandmarkKind.SKY_PLATFORM.ordinal(), 99, 262, -40000, 11)));
            ByteBuf buf = Unpooled.buffer();
            LandmarkNet.Markers.STREAM_CODEC.encode(buf, sent);
            h.assertTrue(LandmarkNet.Markers.STREAM_CODEC.decode(buf).equals(sent), "markers round-trip");
            // memory saves
            LandmarkMemory mem = new LandmarkMemory();
            mem.reach("well");
            mem.reach("ruin:3,-7");
            mem.told.add("ruin:3,-7");
            mem.whispered = "ruin:9,9";
            mem.whisperedAt = 1234;
            var tag = LandmarkMemory.MAP_CODEC.codec().encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, mem).getOrThrow();
            LandmarkMemory back = LandmarkMemory.MAP_CODEC.codec().parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).getOrThrow();
            h.assertTrue(back.describe().equals(mem.describe()), "memory survives: " + back.describe());
            LandmarkMemory fresh = LandmarkMemory.MAP_CODEC.codec().parse(net.minecraft.nbt.NbtOps.INSTANCE, new net.minecraft.nbt.CompoundTag()).getOrThrow();
            h.assertTrue(fresh.reached().isEmpty() && fresh.whispered().isEmpty(), "an old player loads empty");
            h.succeed();
        });
    }

    // ---- loot ---------------------------------------------------------------------------------------------------------

    static void outerCaches() {
        AwakenTests.register("landmark_outer_caches", 40, h -> {
            ResourceKey<LootTable> outer = RuinBuilder.loot("landmark_outer");
            for (RuinKind kind : RuinKind.values()) {
                for (long seed = 1; seed <= 3; seed++) {
                    List<BlockPos> caches = new ArrayList<>();
                    RuinBuilder.build(kind, seed * 7919, 1, new RuinBuilder.Sink() {
                        @Override public void set(int x, int y, int z, BlockState state) {}
                        @Override public void fillDown(int x, int y, int z, BlockState state, int depth) {}
                        @Override public void chest(int x, int y, int z, Direction facing, ResourceKey<LootTable> table) {
                            if (table.equals(outer)) caches.add(new BlockPos(x, y, z));
                        }
                    });
                    h.assertTrue(caches.size() == 1, kind.id + " has one outer cache: " + caches);
                    // vaults: on the antechamber's side of the rank door (the door wall is z = 4)
                    if (kind.isVault()) h.assertTrue(caches.get(0).getZ() < 4, kind.id + "'s cache is outside the door: " + caches.get(0));
                }
            }
            ServerLevel level = h.getLevel();
            LootTable table = level.getServer().reloadableRegistries().getLootTable(outer);
            h.assertTrue(table != LootTable.EMPTY, "the outer cache's loot table loads");
            int shards = 0, kinds = 0;
            for (int i = 0; i < 20; i++) {
                List<ItemStack> items = new ArrayList<>();
                table.getRandomItems(new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(h.absolutePos(BlockPos.ZERO)))
                        .create(LootContextParamSets.CHEST), items::add);
                int s = 0;
                for (ItemStack it : items) if (it.is(CoreApi.ESSENCE_SHARD.get())) s += it.getCount();
                h.assertTrue(s >= 2, "always some essence for an E-rank finder: " + s);
                shards += s;
                kinds += items.size();
            }
            h.assertTrue(kinds >= 20 * 4, "and a few useful things besides: " + kinds + " stacks in 20 caches");
            h.succeed();
        });
    }
}
