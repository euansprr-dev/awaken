package dev.awaken.delve;

import dev.awaken.Awaken;
import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

/**
 * Caves styled by the land above (SPEC §34.7): which land gives which style, and each style dressing a stone room's
 * roof and floor with its own blocks (roots and moss, the crystal lands' crystal, sandstone, ice).
 */
final class StyleTests {
    private StyleTests() {}

    static void register() {
        AwakenTests.register("delve_cave_styles", 40, StyleTests::styles);
    }

    private static CaveStyle of(ServerLevel level, ResourceKey<Biome> key) {
        return CaveStyle.of(level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(key));
    }

    private static void styles(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        h.assertTrue(of(level, Biomes.FOREST) == CaveStyle.ROOTS && of(level, Biomes.OLD_GROWTH_PINE_TAIGA) == CaveStyle.ROOTS, "roots under the woods");
        h.assertTrue(of(level, Biomes.DESERT) == CaveStyle.SAND && of(level, Biomes.BADLANDS) == CaveStyle.SAND, "sandstone under deserts and badlands");
        h.assertTrue(of(level, Biomes.SNOWY_PLAINS) == CaveStyle.ICE, "ice under snow");
        h.assertTrue(of(level, Biomes.PLAINS) == CaveStyle.NONE && of(level, Biomes.OCEAN) == CaveStyle.NONE, "plain caves under plains and the sea");
        h.assertTrue(of(level, Biomes.LUSH_CAVES) == CaveStyle.NONE, "a cave biome keeps its own");
        var biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
        var steppe = biomes.get(ResourceKey.create(Registries.BIOME, Awaken.id("glass_steppe")));
        if (steppe.isPresent()) {
            h.assertTrue(CaveStyle.of(steppe.get()) == CaveStyle.SUNSHARD && CaveStyle.SUNSHARD.crystal() != Blocks.AIR, "sunshard under the Glass Steppe");
            biomes.get(ResourceKey.create(Registries.BIOME, Awaken.id("elderwood")))
                    .ifPresent(b -> h.assertTrue(CaveStyle.of(b) == CaveStyle.ROOTS, "roots under the Elderwood"));
        }

        expect(h, level, new BlockPos(1, 1, 1), CaveStyle.ROOTS, s -> s.is(Blocks.ROOTED_DIRT) || s.is(Blocks.HANGING_ROOTS), s -> s.is(Blocks.MOSS_BLOCK), "roots and moss");
        expect(h, level, new BlockPos(1, 1, 1), CaveStyle.SAND, s -> s.is(Blocks.SANDSTONE), s -> s.is(Blocks.SMOOTH_SANDSTONE) || s.is(Blocks.SANDSTONE), "sandstone");
        expect(h, level, new BlockPos(1, 1, 1), CaveStyle.ICE, s -> s.is(Blocks.PACKED_ICE) || s.is(Blocks.ICE), s -> s.is(Blocks.PACKED_ICE) || s.is(Blocks.BLUE_ICE), "ice");
        if (CaveStyle.SUNSHARD.crystal() != Blocks.AIR) {
            Block sun = CaveStyle.SUNSHARD.crystal();
            expect(h, level, new BlockPos(1, 1, 1), CaveStyle.SUNSHARD, s -> true, s -> s.is(sun), "a sunshard growth");
        }
        h.succeed();
    }

    /** A 9 x 5 x 9 stone room with 3 of air inside; dressed from its middle, its roof and floor show the style. */
    private static void expect(GameTestHelper h, ServerLevel level, BlockPos rel, CaveStyle style, Predicate<BlockState> roof, Predicate<BlockState> floor, String what) {
        BlockPos o = h.absolutePos(rel);
        for (int dx = 0; dx <= 8; dx++) for (int dz = 0; dz <= 8; dz++) for (int dy = 0; dy <= 4; dy++) {
            boolean air = dx > 0 && dx < 8 && dz > 0 && dz < 8 && dy > 0 && dy < 4;
            level.setBlock(o.offset(dx, dy, dz), air ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        BlockPos mid = o.offset(4, 2, 4);
        h.assertTrue(CaveStyleFeature.dress(level, style, RandomSource.create(7), new BlockPos.MutableBlockPos(), mid.getX(), mid.getY(), mid.getZ()), what + ": dressed");
        int r = 0, f = 0;
        for (int dx = 1; dx < 8; dx++) for (int dz = 1; dz < 8; dz++) {
            for (int dy = 3; dy <= 4; dy++) if (roof.test(level.getBlockState(o.offset(dx, dy, dz))) && !level.getBlockState(o.offset(dx, dy, dz)).is(Blocks.STONE)) r++;
            for (int dy = 0; dy <= 3; dy++) if (floor.test(level.getBlockState(o.offset(dx, dy, dz)))) f++;
        }
        h.assertTrue(r > 0 && f > 0, what + " in the roof and the floor: " + r + " / " + f);
    }
}
