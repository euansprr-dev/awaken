package dev.awaken.terrain.client;

import dev.awaken.terrain.PlantSway;
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
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * A leaf's or a plant's model with the sway marker baked into its vertex colours' alpha ({@link PlantSway}): 254 on
 * every leaf vertex, a per-vertex weight on plants. Sodium keeps a quad's baked alpha all the way into its vertex
 * buffer (the tint is forced opaque, AO and shade scale RGB only), and Awaken's terrain shader sways exactly the marked
 * vertices, then puts their alpha back to 255. The textures, shape, tint and lighting are the original model's; only
 * the alpha byte of the baked colour changes.
 *
 * <p>Stalks and strands (kelp, bamboo, sugar cane, vines) look up to {@link PlantSway#CHAIN_REACH} blocks along
 * themselves while meshing (Sodium's snapshot of the section and 2 blocks round it), so each block knows how far it
 * is from its root or anchor and its shared corners agree with its neighbours'.
 */
final class WavingModel extends DelegateBlockStateModel {
    /** The leaf marker; 255 is every other block. */
    static final int LEAF_ALPHA = PlantSway.LEAF_ALPHA;

    private static final ThreadLocal<BlockPos.MutableBlockPos> CURSOR = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);
    private static final Marker LEAVES = new Marker(null);
    private static final IdentityHashMap<PlantSway.Rule, Marker> PLANTS = new IdentityHashMap<>();

    static {
        for (PlantSway.Rule r : PlantSway.RULES) PLANTS.put(r, new Marker(r));
    }

    private final Marker marker;

    private WavingModel(BlockStateModel delegate, Marker marker) {
        super(delegate);
        this.marker = marker;
    }

    static WavingModel leaves(BlockStateModel delegate) {
        return new WavingModel(delegate, LEAVES);
    }

    static WavingModel plant(BlockStateModel delegate, PlantSway.Rule rule) {
        return new WavingModel(delegate, PLANTS.get(rule));
    }

    boolean isLeaf() {
        return this.marker == LEAVES;
    }

    /** Forget converted parts (a resource reload bakes new ones). */
    static void clearCache() {
        LEAVES.clear();
        for (Marker m : PLANTS.values()) m.clear();
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
        int from = parts.size();
        this.delegate.collectParts(level, pos, state, random, parts);
        PlantSway.Rule rule = this.marker.rule;
        int variant = 0;
        if (rule != null && rule.anchor().chain()) {
            int below = rule.anchor().readsBelow ? run(level, pos, -1, rule.family()) : 0;
            int above = rule.anchor().readsAbove ? run(level, pos, 1, rule.family()) : 0;
            variant = PlantSway.Rule.variant(below, above);
        }
        this.marker.mark(parts, from, variant);
    }

    @Override
    @Deprecated
    public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
        int from = parts.size();
        this.delegate.collectParts(random, parts);
        this.marker.mark(parts, from, 0); // no world: a stalk or strand of one
    }

    /** Blocks of the same run directly below (dy -1) or above (dy 1), up to {@link PlantSway#CHAIN_REACH}. */
    private static int run(BlockAndTintGetter level, BlockPos pos, int dy, int family) {
        BlockPos.MutableBlockPos p = CURSOR.get();
        int n = 0;
        while (n < PlantSway.CHAIN_REACH) {
            p.set(pos.getX(), pos.getY() + dy * (n + 1), pos.getZ());
            if (PlantSway.family(level.getBlockState(p).getBlock()) != family) break;
            n++;
        }
        return n;
    }

    /**
     * Converted parts for one way of marking (leaves, or one plant rule). Parts are shared between many states and
     * models; each is converted once per variant, by whichever meshing thread meets it first. Keyed by identity
     * (vanilla parts are records: hashing one hashes all its quads), copy-on-write so the lookup every meshed block
     * makes takes no lock and allocates nothing.
     */
    private static final class Marker {
        final PlantSway.@Nullable Rule rule; // null: leaves
        final int variants;
        private volatile IdentityHashMap<BlockStateModelPart, AtomicReferenceArray<BlockStateModelPart>> cache = new IdentityHashMap<>();

        Marker(PlantSway.@Nullable Rule rule) {
            this.rule = rule;
            this.variants = rule == null ? 1 : rule.variants();
        }

        synchronized void clear() {
            this.cache = new IdentityHashMap<>();
        }

        void mark(List<BlockStateModelPart> out, int from, int variant) {
            for (int i = from; i < out.size(); i++) {
                BlockStateModelPart part = out.get(i);
                if (!(part instanceof MarkedPart)) out.set(i, marked(part, variant));
            }
        }

        private BlockStateModelPart marked(BlockStateModelPart part, int variant) {
            AtomicReferenceArray<BlockStateModelPart> slots = this.cache.get(part);
            if (slots == null) slots = slots(part);
            BlockStateModelPart m = slots.get(variant);
            if (m == null) {
                m = new MarkedPart(part, this.rule, variant); // deterministic: a race only converts twice
                slots.set(variant, m);
            }
            return m;
        }

        private synchronized AtomicReferenceArray<BlockStateModelPart> slots(BlockStateModelPart part) {
            AtomicReferenceArray<BlockStateModelPart> s = this.cache.get(part);
            if (s == null) {
                s = new AtomicReferenceArray<>(this.variants);
                IdentityHashMap<BlockStateModelPart, AtomicReferenceArray<BlockStateModelPart>> next = new IdentityHashMap<>(this.cache);
                next.put(part, s);
                this.cache = next;
            }
            return s;
        }
    }

    /** One model part with every quad's baked colour re-made with its marker alpha. */
    private static final class MarkedPart implements BlockStateModelPart {
        private static final Direction[] DIRECTIONS = Direction.values();
        private final BlockStateModelPart delegate;
        private final List<BakedQuad>[] quads; // index 0..5 per Direction, 6 for unculled

        @SuppressWarnings("unchecked")
        MarkedPart(BlockStateModelPart delegate, PlantSway.@Nullable Rule rule, int variant) {
            this.delegate = delegate;
            this.quads = new List[DIRECTIONS.length + 1];
            int below = variant / (PlantSway.CHAIN_REACH + 1), above = variant % (PlantSway.CHAIN_REACH + 1);
            for (int i = 0; i <= DIRECTIONS.length; i++) {
                List<BakedQuad> source = delegate.getQuads(i < DIRECTIONS.length ? DIRECTIONS[i] : null);
                List<BakedQuad> marked = new ArrayList<>(source.size());
                for (BakedQuad q : source) marked.add(rule == null ? markLeaf(q) : markPlant(q, rule, below, above));
                this.quads[i] = List.copyOf(marked);
            }
        }

        private static BakedQuad markLeaf(BakedQuad q) {
            BakedColors c = q.bakedColors();
            return withColors(q, BakedColors.of(withAlpha(c.color(0), LEAF_ALPHA), withAlpha(c.color(1), LEAF_ALPHA),
                    withAlpha(c.color(2), LEAF_ALPHA), withAlpha(c.color(3), LEAF_ALPHA)));
        }

        /** Each vertex's own weight, from its height in the model (and the run around it); still vertices keep 255. */
        private static BakedQuad markPlant(BakedQuad q, PlantSway.Rule rule, int below, int above) {
            int a0 = PlantSway.alpha(rule, q.direction(), q.position0().y(), below, above);
            int a1 = PlantSway.alpha(rule, q.direction(), q.position1().y(), below, above);
            int a2 = PlantSway.alpha(rule, q.direction(), q.position2().y(), below, above);
            int a3 = PlantSway.alpha(rule, q.direction(), q.position3().y(), below, above);
            if ((a0 & a1 & a2 & a3) == 255) return q; // nothing on this quad moves
            BakedColors c = q.bakedColors();
            return withColors(q, BakedColors.of(withAlpha(c.color(0), a0), withAlpha(c.color(1), a1),
                    withAlpha(c.color(2), a2), withAlpha(c.color(3), a3)));
        }

        private static BakedQuad withColors(BakedQuad q, BakedColors colors) {
            return new BakedQuad(q.position0(), q.position1(), q.position2(), q.position3(),
                    q.packedUV0(), q.packedUV1(), q.packedUV2(), q.packedUV3(),
                    q.direction(), q.materialInfo(), q.bakedNormals(), colors);
        }

        private static int withAlpha(int argb, int alpha) {
            return (alpha << 24) | (argb & 0x00FFFFFF);
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
