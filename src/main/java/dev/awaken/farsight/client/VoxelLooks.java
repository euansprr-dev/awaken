package dev.awaken.farsight.client;

import dev.awaken.farsight.voxel.VoxelMesher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * City Sight's looks (SPEC §20): each block face's sprite in the block atlas, as the game's own baked model gives it
 * (the quad facing that way, else the model's particle sprite), with its tint index, cut-out and glow. Looks are
 * numbered as first met and kept in a table the shaders read: per look the sprite's rectangle in the atlas, its
 * flags and its average colour (what a cut-out texture's holes take at a distance). Worker threads ask, the render
 * thread uploads the table when it grows. Rebuilt with the models (a resource reload).
 */
final class VoxelLooks implements VoxelMesher.Looks {
    static final int CUTOUT = 1, WATER = 2, GLOW = 4, FILL = 8, WINDOW = 16;
    static final int MAX = 16384;

    /** Per look: u0, v0, u1, v1, then flags, average r, g, b (0..1). */
    final float[] table = new float[MAX * 8];
    private int count;
    /** How many looks the table on the GPU holds (the render thread uploads the rest). */
    int uploaded;
    private final ConcurrentHashMap<Long, Integer> bySprite = new ConcurrentHashMap<>();
    /** Per block-state id: six faces' look | (tint index + 1) << 16, or -1; computed on first ask. */
    private final ConcurrentHashMap<Integer, int[]> byState = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> tints = new ConcurrentHashMap<>();
    private final int waterId = Block.BLOCK_STATE_REGISTRY.getId(Blocks.WATER.defaultBlockState());

    synchronized int count() {
        return count;
    }

    @Override
    public int face(int id, int dir) {
        int[] faces = byState.get(id);
        if (faces == null) {
            faces = compute(id);
            byState.put(id, faces);
        }
        return faces[dir];
    }

    private final ConcurrentHashMap<Long, Integer> windows = new ConcurrentHashMap<>();

    @Override
    public int window(int id, int dir) {
        long key = (long) id << 3 | dir;
        Integer w = windows.get(key);
        if (w != null) return w;
        int look = -1;
        try {
            BlockState state = Block.BLOCK_STATE_REGISTRY.byId(id);
            if (state != null) {
                BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
                List<BlockStateModelPart> parts = new ArrayList<>();
                model.collectParts(RandomSource.create(42L), parts);
                Direction d = Direction.from3DDataValue(dir);
                BakedQuad q = quad(parts, d);
                TextureAtlasSprite sprite = q != null ? q.materialInfo().sprite() : model.particleMaterial().sprite();
                look = look(sprite, WINDOW, 0);
            }
        } catch (RuntimeException e) {
            look = -1;
        }
        windows.put(key, look);
        return look;
    }

    @Override
    public int tint(int id, int tintIndex, @Nullable Holder<Biome> biome) {
        long key = (long) id << 32 | (long) tintIndex << 24 | (biome == null ? 0 : System.identityHashCode(biome) & 0xFFFFFF);
        Integer c = tints.get(key);
        if (c != null) return c;
        int colour = 0xFFFFFF;
        try {
            if (id == waterId) {
                colour = biome == null ? 0x3F76E4 : biome.value().getWaterColor();
            } else {
                BlockState state = Block.BLOCK_STATE_REGISTRY.byId(id);
                BlockTintSource source = state == null ? null : Minecraft.getInstance().getBlockColors().getTintSource(state, tintIndex);
                if (source != null) colour = source.colorInWorld(state, new BiomeTint(biome), BlockPos.ZERO);
            }
        } catch (RuntimeException e) {
            colour = 0xFFFFFF;
        }
        colour &= 0xFFFFFF;
        tints.put(key, colour);
        return colour;
    }

    private int[] compute(int id) {
        int[] faces = {-1, -1, -1, -1, -1, -1};
        try {
            if (id == waterId) {
                FluidState water = Fluids.WATER.defaultFluidState();
                TextureAtlasSprite still = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(water).stillMaterial().sprite();
                int look = look(still, WATER, 0) | 1 << 16;
                java.util.Arrays.fill(faces, look);
                return faces;
            }
            BlockState state = Block.BLOCK_STATE_REGISTRY.byId(id);
            if (state == null) return faces;
            BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
            List<BlockStateModelPart> parts = new ArrayList<>();
            model.collectParts(RandomSource.create(42L), parts);
            int glow = state.getLightEmission() > 0 ? GLOW : 0;
            boolean leaves = state.is(net.minecraft.tags.BlockTags.LEAVES);
            TextureAtlasSprite particle = model.particleMaterial().sprite();
            for (Direction d : Direction.values()) {
                BakedQuad q = quad(parts, d);
                int dir = d.get3DDataValue();
                if (q == null) {
                    faces[dir] = look(particle, glow, 0);
                    continue;
                }
                var info = q.materialInfo();
                int flags = glow | (info.layer() == ChunkSectionLayer.SOLID ? 0 : leaves ? FILL : CUTOUT);
                if (info.lightEmission() > 0) flags |= GLOW;
                faces[dir] = look(info.sprite(), flags, 0) | (info.isTinted() ? (info.tintIndex() + 1) << 16 : 0);
            }
        } catch (RuntimeException e) {
            // a model that can't be read here: nothing drawn for it
        }
        return faces;
    }

    /** The quad a model shows toward d: its culled face there, else an unculled one facing d. */
    private static @Nullable BakedQuad quad(List<BlockStateModelPart> parts, Direction d) {
        for (BlockStateModelPart p : parts) {
            List<BakedQuad> qs = p.getQuads(d);
            if (!qs.isEmpty()) return qs.getFirst();
        }
        for (BlockStateModelPart p : parts) {
            for (BakedQuad q : p.getQuads(null)) if (q.direction() == d) return q;
        }
        return null;
    }

    private synchronized int look(TextureAtlasSprite sprite, int flags, int unused) {
        long key = (long) System.identityHashCode(sprite) << 8 | flags;
        Integer known = bySprite.get(key);
        if (known != null) return known;
        if (count >= MAX) return 0;
        int i = count++;
        int at = i * 8;
        table[at] = sprite.getU0();
        table[at + 1] = sprite.getV0();
        table[at + 2] = sprite.getU1();
        table[at + 3] = sprite.getV1();
        table[at + 4] = flags;
        int avg = average(sprite);
        table[at + 5] = (avg >> 16 & 255) / 255f;
        table[at + 6] = (avg >> 8 & 255) / 255f;
        table[at + 7] = (avg & 255) / 255f;
        bySprite.put(key, i);
        return i;
    }

    /** The average colour of a sprite's opaque texels (its first frame), 0xRRGGBB. */
    private static int average(TextureAtlasSprite sprite) {
        try {
            var contents = sprite.contents();
            var image = contents.getOriginalImage();
            int w = contents.width(), h = contents.height();
            long r = 0, g = 0, b = 0, n = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int c = image.getPixel(x, y);
                    if ((c >>> 24) < 128) continue;
                    r += c >> 16 & 255;
                    g += c >> 8 & 255;
                    b += c & 255;
                    n++;
                }
            }
            if (n == 0) return 0x808080;
            return (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
        } catch (RuntimeException e) {
            return 0x808080;
        }
    }

    /** A tint getter that answers only the biome (what grass, leaves and water ask). */
    private record BiomeTint(@Nullable Holder<Biome> biome) implements BlockAndTintGetter {
        @Override
        public int getBlockTint(BlockPos pos, ColorResolver color) {
            return biome == null ? -1 : color.getColor(biome.value(), pos.getX(), pos.getZ());
        }

        @Override
        public net.minecraft.world.level.CardinalLighting cardinalLighting() {
            return BlockAndTintGetter.EMPTY.cardinalLighting();
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return LevelLightEngine.EMPTY;
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return Blocks.AIR.defaultBlockState();
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return Fluids.EMPTY.defaultFluidState();
        }

        @Override
        public int getHeight() {
            return 0;
        }

        @Override
        public int getMinY() {
            return 0;
        }
    }
}
