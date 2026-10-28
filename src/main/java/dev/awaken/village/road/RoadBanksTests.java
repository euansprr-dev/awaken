package dev.awaken.village.road;

import dev.awaken.Awaken;
import dev.awaken.city.SettlementPaint;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.village.world.GenTerrain;
import dev.awaken.village.world.VillagePiece;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.List;
import java.util.Set;

/** One regression per mechanism: planned ownership; frozen natural heights and soft cut/fill profiles. */
final class RoadBanksTests {
    private RoadBanksTests() {}

    static void register() {
        AwakenTests.register("ground_road_bank_ownership", 100, RoadBanksTests::ownership);
        AwakenTests.register("ground_road_bank_profile", 100, RoadBanksTests::profile);
    }

    private static RoadPiece road(String key, int x0, int z0, int x1, int z1, int y) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0)) / 4 + 1;
        float[] xs = new float[n], zs = new float[n];
        int[] ys = new int[n];
        for (int i = 0; i < n; i++) {
            xs[i] = x0 + (x1 - x0) * (float) i / (n - 1);
            zs[i] = z0 + (z1 - z0) * (float) i / (n - 1);
            ys[i] = y;
        }
        Road.End end = new Road.End("", Road.CROSSROADS, "", x0, z0, -1);
        Road r = new Road(key, Road.HIGHROAD, xs, zs, ys, new byte[n], new byte[n], end, end, List.of(), -1, -1);
        return new RoadPiece(r.legs().getFirst());
    }

    private static ProtoChunk chunk(GameTestHelper h, int cx, int cz, int top) {
        ProtoChunk c = new ProtoChunk(new ChunkPos(cx, cz), UpgradeData.EMPTY, h.getLevel(), h.getLevel().palettedContainerFactory(), null);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
            for (int y = 48; y <= top; y++) c.setBlockState(pos.set(cx * 16 + x, y, cz * 16 + z),
                    (y == top ? Blocks.GRASS_BLOCK : Blocks.STONE).defaultBlockState());
        Heightmap.primeHeightmaps(c, Set.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
        c.setPersistedStatus(ChunkStatus.TERRAIN);
        return c;
    }

    private static void paint(RoadPiece road, ProtoChunk c, List<StructurePiece> pieces) {
        SettlementPaint.paintChunk(road.paint(), c.getPos().x(), c.getPos().z(), 42L, c, RoadBanks.land(road, c, pieces));
    }

    private static VillagePiece yard() {
        CompoundTag tag = new CompoundTag();
        tag.store("BB", BoundingBox.CODEC, new BoundingBox(5, 48, 14, 9, 90, 18));
        return new VillagePiece(tag);
    }

    private static void yard(ProtoChunk c, VillagePiece yard) {
        for (int x = c.getPos().getMinBlockX(); x <= c.getPos().getMaxBlockX(); x++)
            for (int z = c.getPos().getMinBlockZ(); z <= c.getPos().getMaxBlockZ(); z++)
                if (yard.getBoundingBox().intersects(x, z, x, z)) {
                    for (int y = 59; y <= 67; y++) c.setBlockState(new BlockPos(x, y, z),
                            (y == 60 ? Blocks.OAK_PLANKS : y == 61 && x == 5 ? Blocks.OAK_LOG : y < 60 ? Blocks.STONE : Blocks.AIR).defaultBlockState());
                }
    }

    private static void ownership(GameTestHelper h) {
        RoadPiece north = road("north", 0, -24, 0, 48, 64), cross = road("cross", -24, 32, 48, 32, 64);
        // Exercise persisted pieces too: clearance cannot depend on an in-memory road-planner cache.
        var context = StructurePieceSerializationContext.fromLevel(h.getLevel());
        cross = new RoadPiece(cross.createTag(context));
        VillagePiece yard = yard();
        List<StructurePiece> pieces = List.of(north, cross, yard);
        List<StructurePiece> reverse = List.of(yard, cross, north);
        long columns = GenTerrain.COLUMNS.sum();
        int protectedCells = 0, bankCells = 0;
        for (int cz : new int[]{0, 1, 2}) {
            ProtoChunk a = chunk(h, 0, cz, 68), b = chunk(h, 0, cz, 68);
            // Same chunk, opposite structure order; the yard straddles z=16 and the crossing straddles z=32.
            paint(cross, a, pieces); yard(a, yard); paint(north, a, pieces);
            paint(north, b, reverse); yard(b, yard); paint(cross, b, reverse);
            for (int z = cz * 16; z < cz * 16 + 16; z++) for (int x = 5; x <= 10; x++) {
                boolean free = RoadBanks.free(north, pieces, x, z);
                h.assertTrue(free == RoadBanks.free(north, reverse, x, z), "clearance ignores reference order");
                if (yard.getBoundingBox().intersects(x, z, x, z) || z >= 28 && z <= 35) {
                    h.assertFalse(free, "the planned yard/crossing is protected at " + x + "," + z);
                    protectedCells++;
                } else if (free) bankCells++;
                for (int y = 52; y <= 74; y++) {
                    BlockPos at = new BlockPos(x, y, z);
                    h.assertTrue(a.getBlockState(at) == b.getBlockState(at), "paint order agrees at " + at);
                }
            }
        }
        // Competing banks at equal distance have exactly one owner.
        RoadPiece parallel = road("parallel", 15, -24, 15, 48, 64);
        List<StructurePiece> pair = List.of(north, parallel);
        h.assertTrue(RoadBanks.free(north, pair, 7, 0) != RoadBanks.free(parallel, pair, 7, 0), "one owner between parallel roads");
        h.assertTrue(protectedCells > 40 && bankCells > 40, "protect crossings/yards while leaving ordinary banks free");
        h.assertTrue(GenTerrain.COLUMNS.sum() == columns, "clearance and painting read no extra noise columns");
        h.succeed();
    }

    private static void profile(GameTestHelper h) {
        RoadPiece road = road("profile", 0, -24, 0, 48, 64);
        RoadPaint p = road.paint();
        List<StructurePiece> pieces = List.of(road);
        int slabs = 0;
        for (int natural : new int[]{60, 68}) {
            // Both orders of chunk painting give identical output, including the shared z=16 edge.
            ProtoChunk[] forward = {chunk(h, 0, 0, natural), chunk(h, 0, 1, natural)};
            ProtoChunk[] backward = {chunk(h, 0, 0, natural), chunk(h, 0, 1, natural)};
            for (int i = 0; i < 2; i++) paint(road, forward[i], pieces);
            for (int i = 1; i >= 0; i--) paint(road, backward[i], pieces);
            for (int cz = 0; cz < 2; cz++) {
                ProtoChunk c = forward[cz];
                var fresh = RoadBanks.land(road, c, pieces);
                h.assertTrue(fresh.natural(6 - p.ox, cz * 16 - p.oz) == natural - p.baseY,
                        "a new snapshot after painting still reads the original WG height");
                for (int z = cz * 16; z < cz * 16 + 16; z++) {
                    int previous = 2 * (64 + 1);
                    for (int x = 4; x <= 10; x++) {
                        int want = RoadBanks.profile(64, natural, x + 0.5f - p.hw - RoadPaint.VERGE);
                        int y = Math.floorDiv(want - 1, 2);
                        var state = c.getBlockState(new BlockPos(x, y, z));
                        h.assertFalse(state.isAir(), "bank surface exists at " + x + "," + y + "," + z);
                        h.assertTrue(((want & 1) != 0) == (state.getBlock() instanceof SlabBlock), "half steps follow the profile");
                        h.assertTrue(Math.abs(want - previous) <= 2, "a shallow bank steps at most one block across");
                        previous = want;
                        if (state.getBlock() instanceof SlabBlock) slabs++;
                    }
                    for (int x = 0; x < 16; x++) for (int y = 48; y <= 75; y++) {
                        BlockPos at = new BlockPos(x, y, z);
                        h.assertTrue(c.getBlockState(at) == backward[cz].getBlockState(at), "chunk order agrees at " + at);
                    }
                    for (int x = 0; x <= 3; x++)
                        h.assertFalse(c.getBlockState(new BlockPos(x, 65, z)).getBlock() instanceof SlabBlock, "no bank slabs inside a level road or its verge");
                }
            }
        }
        h.assertTrue(slabs > 50, "cuts and fills both use natural half slabs");
        h.assertFalse(RoadBanks.enabled(FeatureFlags.DEFAULT_FLAGS), "old worlds keep the existing painter");
        var flags = FeatureFlags.REGISTRY.fromNames(Set.of(Awaken.id("awakened_ground")), id -> {});
        h.assertTrue(RoadBanks.enabled(flags), "the new-world ground flag enables banks");
        ProtoChunk flat = chunk(h, 0, 0, 64);
        var flatLand = RoadBanks.land(road, flat, pieces);
        h.assertFalse(RoadBanks.grades(p, flatLand, 8 - p.ox, -p.oz), "land already level with the road keeps its vegetation");
        h.assertFalse(p.clears(8 - p.ox, -p.oz, flatLand), "no unnecessary clearing on level land");
        ProtoChunk wet = chunk(h, 0, 0, 60);
        wet.setPersistedStatus(ChunkStatus.BIOMES);
        for (int y = 61; y <= 68; y++) wet.setBlockState(new BlockPos(8, y, 0), Blocks.WATER.defaultBlockState());
        wet.setPersistedStatus(ChunkStatus.TERRAIN);
        h.assertFalse(RoadBanks.grades(p, RoadBanks.land(road, wet, pieces), 8 - p.ox, -p.oz), "water is never graded");
        ProtoChunk legacy = chunk(h, 0, 0, 68), explicitOff = chunk(h, 0, 0, 68);
        SettlementPaint.paintChunk(p, 0, 0, 42L, legacy);
        SettlementPaint.paintChunk(p, 0, 0, 42L, explicitOff, null);
        h.assertTrue(hash(legacy) == hash(explicitOff), "flag-off paint is exactly the legacy path");
        ProtoChunk absent = new ProtoChunk(new ChunkPos(0, 0), UpgradeData.EMPTY, h.getLevel(), h.getLevel().palettedContainerFactory(), null);
        var noHeight = RoadBanks.land(road, absent, pieces);
        h.assertTrue(noHeight.natural(6 - p.ox, -p.oz) == RoadBanks.UNKNOWN && !RoadBanks.grades(p, noHeight, 6 - p.ox, -p.oz),
                "missing WG maps fail closed instead of sampling painted blocks");
        h.assertFalse(absent.hasPrimedHeightmap(Heightmap.Types.OCEAN_FLOOR_WG), "no lazy WG priming");
        h.succeed();
    }

    private static long hash(ProtoChunk c) {
        long hash = 1;
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) for (int y = 48; y <= 75; y++)
            hash = hash * 31 + Block.getId(c.getBlockState(new BlockPos(x, y, z)));
        return hash;
    }
}
