package dev.awaken.finds;

import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import java.util.ArrayList;
import java.util.List;
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

    private static boolean joins(BlockState b) {
        Block k = b.getBlock();
        return k instanceof net.minecraft.world.level.block.CrossCollisionBlock || k instanceof net.minecraft.world.level.block.WallBlock
                || k instanceof net.minecraft.world.level.block.StairBlock;
    }

    /** The land's own top: what a cut or filled column is finished with. */
    /** How far past a chunk its clearing reaches: as far as a tree's crown hangs over a chunk's edge. */
    private static final int SPILL = 7;

    private static boolean skin(BlockState b) {
        return b.is(Blocks.GRASS_BLOCK) || b.is(Blocks.PODZOL) || b.is(Blocks.MYCELIUM) || b.is(BlockTags.SAND) || b.is(Blocks.SNOW_BLOCK) || b.is(Blocks.COARSE_DIRT)
                || b.is(Blocks.MUD) || b.is(Blocks.MOSS_BLOCK) || b.is(BlockTags.TERRACOTTA) || b.is(Blocks.GRAVEL);
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
        for (long k : sc.shaped) {
            int x = s.x() + BlockPos.getX(k), z = s.z() + BlockPos.getZ(k);
            if (x >= x0 && x <= x1 && z >= z0 && z <= z1) cols.add(k);
        }
        int cr = sc.clear;
        // a site stood by hand passes the whole world as its rectangle: nothing to add to that
        int cx0 = x0 == Integer.MIN_VALUE ? x0 : x0 - SPILL, cx1 = x1 == Integer.MAX_VALUE ? x1 : x1 + SPILL, cz0 = z0 == Integer.MIN_VALUE ? z0 : z0 - SPILL, cz1 = z1 == Integer.MAX_VALUE ? z1 : z1 + SPILL;
        for (int dx = -cr; dx <= cr; dx++)
            for (int dz = -cr; dz <= cr; dz++) {
                int x = s.x() + dx, z = s.z() + dz;
                // eight blocks past this chunk too: a tree in the next chunk hangs its leaves over this one after this one
                // was cleared, then loses its trunk to its own chunk's clearing, and the leaves were left in the air
                if (x < cx0 || x > cx1 || z < cz0 || z > cz1 || dx * dx + dz * dz > cr * cr) continue;
                // only what grows goes here; the ground is left as it is
                for (int y = s.y() + sc.groundAt(dx, dz) + 46; y > s.y() + sc.groundAt(dx, dz); y--) {
                    m.set(x, y, z);
                    BlockState b = level.getBlockState(m);
                    // never what the place itself planted: past this chunk's edge the neighbour has already written its share
                    if (!b.isAir() && b.getFluidState().isEmpty() && soft(b) && !blocks.containsKey(BlockPos.asLong(dx, y - s.y(), dz)))
                        level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        // a tree felled at the clearing's edge leaves its crown hanging past it: leaves in a ring outside the clearing go
        // too, unless a trunk still stands beside them (the first tours had treetops floating over the stilt market)
        if (cr > 0) {
            BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
            int or = cr + 6;
            for (int dx = -or; dx <= or; dx++)
                for (int dz = -or; dz <= or; dz++) {
                    int x = s.x() + dx, z = s.z() + dz, d2 = dx * dx + dz * dz;
                    if (x < cx0 || x > cx1 || z < cz0 || z > cz1 || d2 <= cr * cr || d2 > or * or) continue;
                    int g = s.y() + sc.groundAt(dx, dz);
                    for (int y = g + 46; y > g; y--) {
                        m.set(x, y, z);
                        if (!level.getBlockState(m).is(BlockTags.LEAVES) || blocks.containsKey(BlockPos.asLong(dx, y - s.y(), dz))) continue;
                        boolean trunk = false;
                        for (int ox = -2; ox <= 2 && !trunk; ox++)
                            for (int oz = -2; oz <= 2 && !trunk; oz++) {
                                int ex = dx + ox, ez = dz + oz;
                                if (ex * ex + ez * ez <= cr * cr) continue; // inside the clearing every trunk has gone
                                for (int oy = -6; oy <= 1 && !trunk; oy++) trunk = level.getBlockState(n.set(x + ox, y + oy, z + oz)).is(BlockTags.LOGS);
                            }
                        if (!trunk) level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
        }
        for (long c : cols) {
            int dx = BlockPos.getX(c), dz = BlockPos.getZ(c), x = s.x() + dx, z = s.z() + dz;
            int want = s.y() + sc.groundAt(dx, dz);
            // find the real ground: the first block from above that isn't air, water or something that grew
            // (down to 48 under it: a ravine or a cave's mouth under a monument is filled from its own floor, never bridged)
            int real = want + 20;
            BlockState top = Blocks.DIRT.defaultBlockState();
            for (; real > want - 48; real--) {
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
                // the cut face takes the land's own skin (grass, sand, snow), not bare rock
                m.set(x, want, z);
                if (skin(top) && !level.getBlockState(m).isAir()) level.setBlock(m, top, Block.UPDATE_CLIENTS);
            } else if (real < want) {
                // made ground is the land's own: its skin on top, three of what lies under that skin, and rock below
                BlockState under = top.is(Blocks.RED_SAND) ? Blocks.RED_SANDSTONE.defaultBlockState() : top.is(BlockTags.SAND) ? Blocks.SANDSTONE.defaultBlockState()
                        : top.is(Blocks.SNOW_BLOCK) || skin(top) ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState();
                for (int y = real + 1; y <= want; y++) {
                    m.set(x, y, z);
                    level.setBlock(m, y == want ? (skin(top) ? top : under) : y >= want - 3 ? under : Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                if (top.is(Blocks.GRASS_BLOCK)) {
                    m.set(x, real, z);
                    level.setBlock(m, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        boolean any = false;
        List<BlockPos> joins = level instanceof ServerLevel ? new ArrayList<>() : null;
        for (var e : blocks.long2ObjectEntrySet()) {
            long k = e.getLongKey();
            int x = s.x() + BlockPos.getX(k), z = s.z() + BlockPos.getZ(k);
            if (x < x0 || x > x1 || z < z0 || z > z1) continue;
            m.set(x, s.y() + BlockPos.getY(k), z);
            BlockState st = e.getValue();
            level.setBlock(m, st, Block.UPDATE_CLIENTS);
            // fences, walls, panes, bars and stairs take their shape from their neighbours: in a new chunk the game does
            // that when the chunk is finished, on a pad stood by hand it is done here once everything is down
            if (joins(st)) {
                if (joins != null) joins.add(m.immutable());
                else level.getChunk(m).markPosForPostProcessing(m);
            }
            any = true;
        }
        if (joins != null) for (BlockPos p : joins) {
            BlockState was = level.getBlockState(p), now = Block.updateFromNeighbourShapes(was, level, p);
            if (now != was) level.setBlock(p, now, Block.UPDATE_CLIENTS);
        }
        for (BlockPos f : sc.flows) {
            int x = s.x() + f.getX(), z = s.z() + f.getZ();
            if (x < x0 || x > x1 || z < z0 || z > z1) continue;
            m.set(x, s.y() + f.getY(), z);
            if (!level.getFluidState(m).isEmpty()) level.scheduleTick(m, net.minecraft.world.level.material.Fluids.WATER, 2);
        }
        BlockPos heart = s.heart();
        if (heart.getX() >= x0 && heart.getX() <= x1 && heart.getZ() >= z0 && heart.getZ() <= z1) {
            level.setBlock(heart, FindsContent.HEART.get().defaultBlockState(), Block.UPDATE_CLIENTS);
            any = true;
        }
        // In snow country the snow comes back: on the ground the clearing bared and on top of what was built, so a find
        // isn't a bare patch in a white wood. Never where the scene keeps a place for something (a part, a cut).
        for (int dx = -cr; dx <= cr; dx++)
            for (int dz = -cr; dz <= cr; dz++)
                if (dx * dx + dz * dz <= cr * cr) cols.add(BlockPos.asLong(dx, 0, dz));
        int sea = level.getSeaLevel();
        BlockState snow = Blocks.SNOW.defaultBlockState();
        // (not for one stood by hand: a test pad in the sky is above the snow line whatever the land below is)
        if (s.layer() != Sites.HAND) for (long c : cols) {
            int dx = BlockPos.getX(c), dz = BlockPos.getZ(c), x = s.x() + dx, z = s.z() + dz;
            if (x < x0 || x > x1 || z < z0 || z > z1) continue;
            int base = s.y() + sc.groundAt(dx, dz);
            for (int y = base + 36; y > base - 6; y--) {
                m.set(x, y, z);
                BlockState b = level.getBlockState(m);
                if (b.isAir()) continue;
                BlockPos above = m.above();
                if (b.getBlock() instanceof dev.awaken.finds.kit.PropBlock || blocks.containsKey(BlockPos.asLong(dx, y + 1 - s.y(), dz))) break;
                if (!level.getBiome(above).value().coldEnoughToSnow(above, sea) || !snow.canSurvive(level, above)) break;
                level.setBlock(above, snow, Block.UPDATE_CLIENTS);
                if (b.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.SNOWY))
                    level.setBlock(m, b.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SNOWY, true), Block.UPDATE_CLIENTS);
                break;
            }
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
