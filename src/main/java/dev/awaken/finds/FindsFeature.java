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

    private static final net.minecraft.tags.TagKey<Block> SOFT_SLABS = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, dev.awaken.Awaken.id("ground/soft_slabs"));
    private static final java.util.Set<Block> STONE_STEPS = java.util.Set.of(Blocks.STONE_SLAB, Blocks.ANDESITE_SLAB, Blocks.GRANITE_SLAB, Blocks.DIORITE_SLAB, Blocks.TUFF_SLAB,
            Blocks.SANDSTONE_SLAB, Blocks.RED_SANDSTONE_SLAB);

    /** The full block a half step of the soft land stands for (SPEC §31.2: turf on grass, a sand slab on sand, a stone slab on stone), or null where this isn't one. */
    private static @org.jspecify.annotations.Nullable BlockState stepOf(BlockState b) {
        if (!(b.getBlock() instanceof net.minecraft.world.level.block.SlabBlock) || b.getValue(net.minecraft.world.level.block.SlabBlock.TYPE) != net.minecraft.world.level.block.state.properties.SlabType.BOTTOM
                || !(b.is(SOFT_SLABS) || STONE_STEPS.contains(b.getBlock()))) return null;
        String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b.getBlock()).getPath();
        if (path.equals("turf_slab")) return Blocks.GRASS_BLOCK.defaultBlockState();
        Block full = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(path.substring(0, path.length() - 5)));
        return full == Blocks.AIR ? null : full.defaultBlockState();
    }

    /** One of the land's own blocks, whole: what the ground of this country is made of, never something built. */
    private static boolean own(LevelAccessor level, BlockPos at, BlockState b) {
        return (skin(b) || b.is(BlockTags.DIRT) || b.is(BlockTags.BASE_STONE_OVERWORLD) || b.is(LANDS_GROUND) || b.is(Blocks.SANDSTONE) || b.is(Blocks.RED_SANDSTONE)
                || b.is(Blocks.CALCITE) || b.is(Blocks.PACKED_ICE) || b.is(Blocks.CLAY)) && b.isCollisionShapeFullBlock(level, at);
    }

    /** The grounds of our own lands (hoarfrost, mirepeat, riven shale and the rest): the terrain module lists them here. */
    private static final net.minecraft.tags.TagKey<Block> LANDS_GROUND = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
            net.minecraft.resources.Identifier.withDefaultNamespace("overworld_carver_replaceables"));

    /** The skin of the land beside a column of bare earth: the first of its eight neighbours whose top, within a block of this one's, is skin. */
    private static @org.jspecify.annotations.Nullable BlockState skinNear(LevelAccessor level, int x, int y, int z) {
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 8; i++) {
            int ox = i < 4 ? (i == 0 ? 1 : i == 1 ? -1 : 0) : (i % 2 == 0 ? 1 : -1), oz = i < 4 ? (i == 2 ? 1 : i == 3 ? -1 : 0) : (i < 6 ? 1 : -1);
            for (int oy = 1; oy >= -1; oy--) {
                BlockState b = level.getBlockState(n.set(x + ox, y + oy, z + oz));
                if (skin(b)) return b;
                BlockState of = stepOf(b);
                if (of != null && skin(of)) return of;
            }
        }
        return null;
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
                int foot = Integer.MIN_VALUE;
                for (int y = s.y() + sc.groundAt(dx, dz) + 46; y > s.y() + sc.groundAt(dx, dz); y--) {
                    m.set(x, y, z);
                    BlockState b = level.getBlockState(m);
                    // never what the place itself planted: past this chunk's edge the neighbour has already written its share
                    if (!b.isAir() && b.getFluidState().isEmpty() && soft(b) && !blocks.containsKey(BlockPos.asLong(dx, y - s.y(), dz))) {
                        level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        if (b.is(BlockTags.LOGS)) foot = y;
                    }
                }
                // a tree stands on bare earth: felled, it left a square of dirt in the grass (four under every great
                // tree, some 160 in the glade of one monument in a pale wood). The earth takes its neighbours' skin.
                if (foot != Integer.MIN_VALUE && level.getBlockState(m.set(x, foot - 1, z)).is(Blocks.DIRT) && !blocks.containsKey(BlockPos.asLong(dx, foot - 1 - s.y(), dz))) {
                    BlockState skin = skinNear(level, x, foot - 1, z);
                    if (skin != null) {
                        if (skin.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.SNOWY))
                            skin = skin.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SNOWY, false);
                        level.setBlock(m, skin, Block.UPDATE_CLIENTS);
                    }
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
                        if (!trunk) {
                            level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                            if (level.getBlockState(n.set(x, y + 1, z)).is(Blocks.SNOW)) level.setBlock(n, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);   // the snow that lay on it
                        }
                    }
                }
        }
        BlockState lastSkin = null;
        for (long c : cols) {
            int dx = BlockPos.getX(c), dz = BlockPos.getZ(c), x = s.x() + dx, z = s.z() + dz;
            int want = s.y() + sc.groundAt(dx, dz);
            // Find the real ground, from above. Everything over the floor goes on the way down, ground or not, and the
            // search carries on to the floor and under it: taken as "the first thing that isn't air or a plant", a
            // tree's side branch or a nest over a column was its ground, the column was cut to the floor and nothing
            // was looked for beneath, so a pit one block wide was left down to wherever the land really was (the
            // first worlds made without a client found two in most lawns, SPEC §62.9). Down to 48 under the floor: a
            // ravine or a cave's mouth under a monument is filled from its own floor, never bridged.
            // (from the land's real top, not from twenty over the floor: where a hillside stood higher than that the
            // search began inside the hill, took its earth or rock for the land's skin and floored the terrace with
            // it, 160 squares of bare earth round one monument, and left the hillside above hanging)
            int real = Math.min(want + 72, Math.max(want + 20, level.getHeight(level instanceof ServerLevel
                    ? net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE : net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, x, z)));
            BlockState ground = null, land = null, stepped = null;
            boolean cut = false;
            for (; real > want - 48; real--) {
                m.set(x, real, z);
                BlockState b = level.getBlockState(m);
                if (b.isAir() || !b.getFluidState().isEmpty()) continue;
                if (soft(b)) {
                    level.setBlock(m, b.getFluidState().createLegacyBlock(), Block.UPDATE_CLIENTS);
                    continue;
                }
                // a half step of the soft land (SPEC §31.2) is the land's skin half a block up, not its ground: it goes,
                // and the skin it stood for is what this column is made of
                BlockState of = stepOf(b);
                if (of != null) {
                    if (stepped == null) stepped = of;
                    level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    continue;
                }
                if (land == null && own(level, m, b)) land = b;         // the first of the land's own blocks met is its skin
                if (real > want) {
                    level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    cut = true;
                    continue;
                }
                ground = b;
                break;
            }
            // What the column is finished with: the land's own top. Under a soft step and under a tree that top is bare
            // earth, and taken as it lies it left a square of dirt in the lawn for every tree felled and every step
            // lifted, and a square of stone wherever such a column was filled (the first game pictures of monuments
            // where a world put them): it takes the skin it had, or its neighbours'.
            BlockState top = land != null ? land : Blocks.DIRT.defaultBlockState();
            boolean bare = top.is(Blocks.DIRT);
            if (bare) {
                BlockState skin = stepped != null && !stepped.is(Blocks.DIRT) ? stepped : skinNear(level, x, Math.max(real, want), z);
                if (skin == null) skin = lastSkin;                  // deep in a cut no neighbour shows its skin yet: the last column's
                if (skin != null) top = skin;
            } else if (skin(top)) lastSkin = top;
            if (top.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.SNOWY))
                top = top.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SNOWY, false);   // (the snow that comes back sets it)
            m.set(x, want, z);
            if (real == want) {
                // cut to the floor, or bare earth at it: the face takes the land's top, not what lay under it (never over something built)
                if ((cut || bare) && own(level, m, ground) && !ground.is(top.getBlock())) level.setBlock(m, top, Block.UPDATE_CLIENTS);
            } else {
                // made ground is the land's own: its top block on top, three of what lies under that, and rock below
                BlockState under = top.is(Blocks.RED_SAND) ? Blocks.RED_SANDSTONE.defaultBlockState() : top.is(BlockTags.SAND) ? Blocks.SANDSTONE.defaultBlockState()
                        : top.is(Blocks.SNOW_BLOCK) || skin(top) ? Blocks.DIRT.defaultBlockState() : top;
                for (int y = real + 1; y <= want; y++) {
                    m.set(x, y, z);
                    level.setBlock(m, y == want ? top : y >= want - 3 ? under : Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                if (ground != null && (ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.PODZOL) || ground.is(Blocks.MYCELIUM))) {
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
            // water is never swapped for something that grows: grass on a stream's bed would leave a hole in the water
            if (soft(st) && st.getFluidState().isEmpty() && !level.getFluidState(m).isEmpty()) continue;
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
        // In snow country the snow comes back wherever the sky sees (SPEC §62.3): by the world's own rule on the ground
        // the clearing bared and on the land the terrace made, and in drifts on what was built, so a find is neither a
        // bare patch in a white wood nor a sheet of white. The drifts are a noise of the site's own: the first game
        // pictures had snow lying in the outlines of rooms and cuts, wherever the scene had not written air.
        // Never on a part (a carving, a seal, a gate), a prop or a lamp.
        // The clearing reaches a few blocks into the chunks beside this one, and snow is among what it takes: so the
        // snow is laid back over all it touched, not this chunk's share alone (laid by the chunk only, each chunk kept
        // its snow just where no later neighbour's clearing reached, and a monument stood in squares of white on green).
        for (int dx = -cr; dx <= cr; dx++)
            for (int dz = -cr; dz <= cr; dz++)
                if (dx * dx + dz * dz <= cr * cr) cols.add(BlockPos.asLong(dx, 0, dz));
        int sea = level.getSeaLevel();
        BlockState snow = Blocks.SNOW.defaultBlockState();
        // (not for one stood by hand: a test pad in the sky is above the snow line whatever the land below is)
        if (s.layer() != Sites.HAND) {
            int roof = Math.max(sc.box()[4], 8) + 2;
            LongOpenHashSet kept = null;
            for (long c : cols) {
                int dx = BlockPos.getX(c), dz = BlockPos.getZ(c), x = s.x() + dx, z = s.z() + dz;
                if (x < cx0 || x > cx1 || z < cz0 || z > cz1) continue;
                int base = sc.groundAt(dx, dz), from = Math.max(roof, base + 36);
                // it is colder the higher: where it is too warm for snow at the top it is too warm all the way down
                m.set(x, s.y() + from, z);
                if (!level.getBiome(m).value().coldEnoughToSnow(m, sea)) continue;
                if (kept == null) {
                    kept = new LongOpenHashSet();
                    for (List<BlockPos> l : sc.parts().values()) for (BlockPos p : l) kept.add(p.asLong());
                }
                for (int dy = from; dy > base - 6; dy--) {
                    m.set(x, s.y() + dy, z);
                    BlockState b = level.getBlockState(m);
                    if (b.isAir()) continue;
                    BlockPos above = m.above();
                    if (b.getBlock() instanceof dev.awaken.finds.kit.PropBlock || b.getLightEmission() > 0 || kept.contains(BlockPos.asLong(dx, dy, dz))
                            || kept.contains(BlockPos.asLong(dx, dy + 1, dz))) break;
                    if (!level.getBiome(above).value().coldEnoughToSnow(above, sea) || !snow.canSurvive(level, above)) break;
                    BlockState built = blocks.get(BlockPos.asLong(dx, dy, dz));
                    // (what the place planted is the land's, not masonry: its trees take snow as the wood's own do)
                    if (built != null && !built.isAir() && !built.is(BlockTags.LEAVES) && !built.is(BlockTags.LOGS) && !drift(sc, dx, dz)) break;
                    level.setBlock(above, snow, Block.UPDATE_CLIENTS);
                    if (b.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.SNOWY))
                        level.setBlock(m, b.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SNOWY, true), Block.UPDATE_CLIENTS);
                    break;
                }
            }
        }
        return any;
    }

    /** Where snow lies on what was built: about half of it, in drifts some six blocks across with ragged edges. */
    private static boolean drift(Scene sc, int dx, int dz) {
        return dev.awaken.finds.kit.B.n(sc, dx + 40.5, dz - 17.5, 6.0) * 0.7 + dev.awaken.finds.kit.B.n(sc, dx - 9.5, dz + 23.5, 2.6) * 0.3 > 0.5;
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
