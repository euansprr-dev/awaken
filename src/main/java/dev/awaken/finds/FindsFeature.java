package dev.awaken.finds;

import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * {@code awaken:finds} (SPEC §58.4): each new overworld chunk writes its share of every find within reach of it. Only
 * blocks inside the chunk; the rest of a site comes with its other chunks, and it all agrees because a site and its
 * scene are pure functions of the seed. It runs after trees and plants (top layer), so what grew where the find stands
 * is cleared and the land round it is left alone.
 */
public record FindsFeature() implements Feature {
    public static final MapCodec<FindsFeature> CODEC = MapCodec.unit(FindsFeature::new);

    @Override
    public MapCodec<FindsFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator gen, RandomSource random, BlockPos origin) {
        ServerLevel sl = level.getLevel();
        int x0 = origin.getX() & ~15, z0 = origin.getZ() & ~15;
        var sites = Sites.near(sl, x0, z0, x0 + 15, z0 + 15, 0);
        boolean any = false;
        for (Site s : sites) any |= write(level, s, Sites.scene(sl, s), x0, z0, x0 + 15, z0 + 15);
        return any;
    }

    private static boolean soft(BlockState b) {
        return b.is(BlockTags.LEAVES) || b.is(BlockTags.LOGS) || b.canBeReplaced() || b.is(Blocks.SNOW) || b.is(BlockTags.FLOWERS) || b.is(Blocks.BAMBOO) || b.is(Blocks.MUSHROOM_STEM)
                || b.is(Blocks.BROWN_MUSHROOM_BLOCK) || b.is(Blocks.RED_MUSHROOM_BLOCK);
    }

    /**
     * Writes the part of a scene that lies in a rectangle (a chunk in worldgen, everything for a site made by hand).
     * Under each column the scene builds on, the ground is brought to the height the scene expects: filled where the
     * real land is lower, cut where it's higher, cleared of what grew there.
     */
    public static boolean write(LevelAccessor level, Site s, Scene sc, int x0, int z0, int x1, int z1) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        LongOpenHashSet cols = new LongOpenHashSet();
        var blocks = sc.blocks();
        for (long k : blocks.keySet()) {
            int x = s.x() + BlockPos.getX(k), z = s.z() + BlockPos.getZ(k);
            if (x < x0 || x > x1 || z < z0 || z > z1) continue;
            cols.add(BlockPos.asLong(BlockPos.getX(k), 0, BlockPos.getZ(k)));
        }
        int cr = sc.clear;
        for (int dx = -cr; dx <= cr; dx++)
            for (int dz = -cr; dz <= cr; dz++) {
                int x = s.x() + dx, z = s.z() + dz;
                if (x < x0 || x > x1 || z < z0 || z > z1 || dx * dx + dz * dz > cr * cr) continue;
                // only what grows goes here; the ground is left as it is
                for (int y = s.y() + sc.groundAt(dx, dz) + 30; y > s.y() + sc.groundAt(dx, dz); y--) {
                    m.set(x, y, z);
                    BlockState b = level.getBlockState(m);
                    if (!b.isAir() && b.getFluidState().isEmpty() && soft(b)) level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        for (long c : cols) {
            int dx = BlockPos.getX(c), dz = BlockPos.getZ(c), x = s.x() + dx, z = s.z() + dz;
            int want = s.y() + sc.groundAt(dx, dz);
            // find the real ground: the first block from above that isn't air, water or something that grew
            int real = want + 12;
            BlockState top = Blocks.DIRT.defaultBlockState();
            for (; real > want - 8; real--) {
                m.set(x, real, z);
                BlockState b = level.getBlockState(m);
                if (b.isAir() || !b.getFluidState().isEmpty()) continue;
                if (soft(b)) {
                    level.setBlock(m, b.getFluidState().createLegacyBlock(), Block.UPDATE_CLIENTS);
                    continue;
                }
                top = b;
                break;
            }
            if (real > want) {
                for (int y = real; y > want; y--) {
                    m.set(x, y, z);
                    level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                m.set(x, want, z);
                if (level.getBlockState(m).is(Blocks.DIRT)) level.setBlock(m, top.is(Blocks.GRASS_BLOCK) ? top : level.getBlockState(m), Block.UPDATE_CLIENTS);
            } else if (real < want) {
                for (int y = real + 1; y <= want; y++) {
                    m.set(x, y, z);
                    level.setBlock(m, y == want ? top : Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                if (top.is(Blocks.GRASS_BLOCK)) {
                    m.set(x, real, z);
                    level.setBlock(m, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        boolean any = false;
        for (var e : blocks.long2ObjectEntrySet()) {
            long k = e.getLongKey();
            int x = s.x() + BlockPos.getX(k), z = s.z() + BlockPos.getZ(k);
            if (x < x0 || x > x1 || z < z0 || z > z1) continue;
            m.set(x, s.y() + BlockPos.getY(k), z);
            level.setBlock(m, e.getValue(), Block.UPDATE_CLIENTS);
            any = true;
        }
        BlockPos heart = s.heart();
        if (heart.getX() >= x0 && heart.getX() <= x1 && heart.getZ() >= z0 && heart.getZ() <= z1) {
            level.setBlock(heart, FindsContent.HEART.get().defaultBlockState(), Block.UPDATE_CLIENTS);
            any = true;
        }
        return any;
    }

    /**
     * Stands a kind up by hand on a level pad (the gallery, {@code /awaken finds place}, tests): lays the pad, writes
     * the whole scene and tells the heart what it is. Returns the site.
     */
    public static Site stamp(ServerLevel level, Kind k, BlockPos ground, int rot, long seed) {
        Site s = Sites.forced(k, ground.getX(), ground.getY(), ground.getZ(), rot, seed);
        Scene sc = Sites.scene(level, s);
        int[] b = sc.box();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = b[0] - 3; dx <= b[3] + 3; dx++)
            for (int dz = b[2] - 3; dz <= b[5] + 3; dz++) {
                for (int y = ground.getY() + Math.max(b[4], 8) + 2; y > ground.getY(); y--) level.setBlock(m.set(ground.getX() + dx, y, ground.getZ() + dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(m.set(ground.getX() + dx, ground.getY(), ground.getZ() + dz), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int y = ground.getY() - 1; y >= ground.getY() + Math.min(b[1], -3) - 1; y--) level.setBlock(m.set(ground.getX() + dx, y, ground.getZ() + dz), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        write(level, s, sc, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        if (level.getBlockEntity(s.heart()) instanceof FindHeartBlockEntity be) be.force(k, rot, seed);
        return s;
    }
}
