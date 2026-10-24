package dev.awaken.terrain.client;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TriState;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import net.neoforged.neoforge.client.model.quad.BakedColors;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * A leaf block's model with the waving marker baked in: every vertex colour gets alpha 254. Sodium keeps a quad's
 * baked alpha all the way into its vertex buffer (the tint is forced opaque, AO and shade scale RGB only), and
 * Awaken's terrain shader sways exactly those vertices, then puts their alpha back to 255. The textures, shape, tint
 * and lighting are the original model's; only the alpha byte of the baked colour changes.
 */
final class WavingLeavesModel extends DelegateBlockStateModel {
    /** The marker alpha. 255 is every other block; the shader treats exactly 254 as a leaf. */
    static final int LEAF_ALPHA = 254;

    // Parts are shared between many states and models; each is converted once, by whichever meshing thread meets it
    // first. Keyed by identity (vanilla parts are records: hashing one hashes all its quads), copy-on-write so the
    // lookup every meshed leaf block makes takes no lock and allocates nothing.
    private static volatile IdentityHashMap<BlockStateModelPart, BlockStateModelPart> cache = new IdentityHashMap<>();

    WavingLeavesModel(BlockStateModel delegate) {
        super(delegate);
    }

    /** Forget converted parts (a resource reload bakes new ones). */
    static synchronized void clearCache() {
        cache = new IdentityHashMap<>();
    }

    private static BlockStateModelPart marked(BlockStateModelPart part) {
        BlockStateModelPart m = cache.get(part);
        return m != null ? m : convert(part);
    }

    private static synchronized BlockStateModelPart convert(BlockStateModelPart part) {
        BlockStateModelPart m = cache.get(part);
        if (m == null) {
            m = new MarkedPart(part);
            IdentityHashMap<BlockStateModelPart, BlockStateModelPart> next = new IdentityHashMap<>(cache);
            next.put(part, m);
            cache = next;
        }
        return m;
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
        int from = parts.size();
        this.delegate.collectParts(level, pos, state, random, parts);
        mark(parts, from);
    }

    @Override
    @Deprecated
    public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
        int from = parts.size();
        this.delegate.collectParts(random, parts);
        mark(parts, from);
    }

    private static void mark(List<BlockStateModelPart> out, int from) {
        for (int i = from; i < out.size(); i++) {
            BlockStateModelPart part = out.get(i);
            if (!(part instanceof MarkedPart)) out.set(i, marked(part));
        }
    }

    /** One model part with every quad's baked colour re-made with the marker alpha. */
    private static final class MarkedPart implements BlockStateModelPart {
        private static final Direction[] DIRECTIONS = Direction.values();
        private final BlockStateModelPart delegate;
        private final List<BakedQuad>[] quads; // index 0..5 per Direction, 6 for unculled

        @SuppressWarnings("unchecked")
        MarkedPart(BlockStateModelPart delegate) {
            this.delegate = delegate;
            this.quads = new List[DIRECTIONS.length + 1];
            for (int i = 0; i <= DIRECTIONS.length; i++) {
                List<BakedQuad> source = delegate.getQuads(i < DIRECTIONS.length ? DIRECTIONS[i] : null);
                List<BakedQuad> marked = new ArrayList<>(source.size());
                for (BakedQuad q : source) marked.add(mark(q));
                this.quads[i] = List.copyOf(marked);
            }
        }

        private static BakedQuad mark(BakedQuad q) {
            BakedColors c = q.bakedColors();
            BakedColors marked = BakedColors.of(withAlpha(c.color(0)), withAlpha(c.color(1)), withAlpha(c.color(2)), withAlpha(c.color(3)));
            return new BakedQuad(q.position0(), q.position1(), q.position2(), q.position3(),
                    q.packedUV0(), q.packedUV1(), q.packedUV2(), q.packedUV3(),
                    q.direction(), q.materialInfo(), q.bakedNormals(), marked);
        }

        private static int withAlpha(int argb) {
            return (LEAF_ALPHA << 24) | (argb & 0x00FFFFFF);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable Direction direction) {
            return this.quads[direction == null ? DIRECTIONS.length : direction.ordinal()];
        }

        @Override
        @Deprecated
        public boolean useAmbientOcclusion() {
            return this.delegate.useAmbientOcclusion();
        }

        @Override
        public TriState ambientOcclusion() {
            return this.delegate.ambientOcclusion();
        }

        @Override
        public Material.Baked particleMaterial() {
            return this.delegate.particleMaterial();
        }

        @Override
        public int materialFlags() {
            return this.delegate.materialFlags();
        }
    }
}
