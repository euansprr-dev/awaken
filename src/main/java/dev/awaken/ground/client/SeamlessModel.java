package dev.awaken.ground.client;

import dev.awaken.ground.FreshSides;
import dev.awaken.ground.SeamlessRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TriState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowyBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import net.neoforged.neoforge.client.model.quad.BakedColors;
import net.neoforged.neoforge.client.model.quad.BakedNormals;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Seamless ground (SPEC §31, P0), drawn by wrapping a block's baked model the way {@code terrain.client.WavingModel}
 * wraps plants: {@code collectParts(level, pos, ...)} runs on Sodium's meshing threads with a snapshot of the world.
 * <ul>
 * <li>{@link Kind#WRAP}, grass, podzol and mycelium blocks: every side the rules name ({@link SeamlessRules#wrapMask}) is
 * redrawn with the block's top sprite, tinted as the top is, so a one-block step is green down its face. The converted
 * parts are cached per source part and mask (at most sixteen each).</li>
 * <li>{@link Kind#CREEP}, paths, dirt, gravel and sand: a few overlay quads on the top face where grass lies beside
 * it ({@link SeamlessRules#creepMask}), grass blades from our own sprites ({@code block/ground/creep_*}) coloured as
 * the neighbouring grass is.</li>
 * </ul>
 * With the setting off the delegate's parts are returned untouched.
 */
final class SeamlessModel extends DelegateBlockStateModel {
    enum Kind { WRAP, CREEP }

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final float LIFT = 0.002F;

    private final Kind kind;
    private final ConcurrentHashMap<Ident, AtomicReferenceArray<BlockStateModelPart>> wrapped = new ConcurrentHashMap<>();
    private volatile BakedQuad.@Nullable MaterialInfo template;

    private SeamlessModel(BlockStateModel delegate, Kind kind) {
        super(delegate);
        this.kind = kind;
    }

    static SeamlessModel of(BlockStateModel delegate, Kind kind) {
        return new SeamlessModel(delegate, kind);
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
        int from = parts.size();
        this.delegate.collectParts(level, pos, state, random, parts);
        if (!GroundClientConfig.seamless()) return;
        if (this.kind == Kind.WRAP) {
            if (state.hasProperty(SnowyBlock.SNOWY) && state.getValue(SnowyBlock.SNOWY)) return;
            int mask = SeamlessRules.wrapMask(level, pos);
            if (mask == 0) return;
            // a side bared a moment ago (SPEC §31.1, fresh sides) keeps its own face, with grass reaching down it by stage
            int stages = FreshView.stages(pos), grow1 = 0, grow2 = 0;
            if (stages != 0xFF) {
                for (int i = 0; i < 4; i++) {
                    int st = stages >> (i * 2) & 3;
                    if ((mask >> i & 1) == 0 || st == FreshSides.NONE) continue;
                    mask &= ~(1 << i);
                    if (st == 1) grow1 |= 1 << i;
                    else if (st == 2) grow2 |= 1 << i;
                }
            }
            if ((grow1 | grow2) != 0 && state.is(Blocks.GRASS_BLOCK) && parts.size() > from) {
                BlockStateModelPart grown = grown(parts.get(from), grow1, grow2);
                if (grown != null) parts.add(grown);
            }
            if (mask == 0) return;
            for (int i = from; i < parts.size(); i++) {
                if (!(parts.get(i) instanceof GrownPart)) parts.set(i, convert(parts.get(i), mask));
            }
        } else {
            int mask = SeamlessRules.creepMask(level, pos);
            if (mask == 0) return;
            BlockStateModelPart creep = creep(level, pos, state, mask, parts, from);
            if (creep != null) parts.add(creep);
        }
    }

    // ---- wrap -------------------------------------------------------------------------------------------------------------

    private BlockStateModelPart convert(BlockStateModelPart part, int mask) {
        AtomicReferenceArray<BlockStateModelPart> slots = this.wrapped.computeIfAbsent(new Ident(part), k -> new AtomicReferenceArray<>(16));
        BlockStateModelPart w = slots.get(mask);
        if (w == null) {
            w = new WrappedPart(part, mask);        // deterministic: a race only converts twice
            slots.set(mask, w);
        }
        return w;
    }

    void clear() {
        this.wrapped.clear();
        this.template = null;
    }

    private record Ident(BlockStateModelPart part) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Ident i && i.part == this.part;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this.part);
        }
    }

    /** A model part whose masked sides show the top sprite instead of the side's (and no overlay on them). */
    private static final class WrappedPart implements BlockStateModelPart {
        private final BlockStateModelPart delegate;
        private final List<BakedQuad>[] quads; // 0..5 per Direction, 6 unculled

        @SuppressWarnings("unchecked")
        WrappedPart(BlockStateModelPart delegate, int mask) {
            this.delegate = delegate;
            this.quads = new List[DIRECTIONS.length + 1];
            List<BakedQuad> ups = delegate.getQuads(Direction.UP);
            BakedQuad top = ups.isEmpty() ? null : ups.getFirst();
            for (int i = 0; i <= DIRECTIONS.length; i++) {
                Direction d = i < DIRECTIONS.length ? DIRECTIONS[i] : null;
                List<BakedQuad> source = delegate.getQuads(d);
                int bit = d == null ? -1 : bit(d);
                if (top != null && bit >= 0 && (mask >> bit & 1) != 0 && !source.isEmpty()
                        && source.getFirst().materialInfo().sprite() != top.materialInfo().sprite()) {
                    this.quads[i] = List.of(onSprite(source.getFirst(), top));
                } else {
                    this.quads[i] = source;
                }
            }
        }

        private static int bit(Direction d) {
            return switch (d) {
                case NORTH -> 0;
                case EAST -> 1;
                case SOUTH -> 2;
                case WEST -> 3;
                default -> -1;
            };
        }

        /** The side quad's shape with the top quad's sprite (and tint) over it: each corner keeps its place in the sprite. */
        private static BakedQuad onSprite(BakedQuad side, BakedQuad top) {
            TextureAtlasSprite from = side.materialInfo().sprite(), to = top.materialInfo().sprite();
            long[] uv = new long[4];
            float du = from.getU1() - from.getU0(), dv = from.getV1() - from.getV0();
            for (int i = 0; i < 4; i++) {
                float fu = du == 0F ? 0F : (UVPair.unpackU(side.packedUV(i)) - from.getU0()) / du;
                float fv = dv == 0F ? 0F : (UVPair.unpackV(side.packedUV(i)) - from.getV0()) / dv;
                uv[i] = UVPair.pack(to.getU0() + fu * (to.getU1() - to.getU0()), to.getV0() + fv * (to.getV1() - to.getV0()));
            }
            return new BakedQuad(side.position0(), side.position1(), side.position2(), side.position3(), uv[0], uv[1], uv[2], uv[3],
                    side.direction(), top.materialInfo(), side.bakedNormals(), side.bakedColors());
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

    // ---- fresh sides --------------------------------------------------------------------------------------------------------

    private static volatile TextureAtlasSprite @Nullable [] growing;

    /**
     * The grass growing down freshly bared sides: on each side in {@code grow1} (stage 1) or {@code grow2} (stage 2), one quad
     * over the side's own face, a hair out, with our sprite {@code block/ground/creep_side_0} or {@code _1} (grey, tinted as
     * the block's grass by tint index 0, drift included). Culled with the face.
     */
    private static @Nullable BlockStateModelPart grown(BlockStateModelPart part, int grow1, int grow2) {
        TextureAtlasSprite[] sp = growing;
        if (sp == null) {
            sp = sprites("side", 2);
            growing = sp;
        }
        @SuppressWarnings("unchecked")
        List<BakedQuad>[] quads = new List[DIRECTIONS.length];
        boolean any = false;
        for (int i = 0; i < 4; i++) {
            int stage = (grow1 >> i & 1) != 0 ? 1 : (grow2 >> i & 1) != 0 ? 2 : 0;
            if (stage == 0) continue;
            Direction d = SeamlessRules.SIDES[i];
            List<BakedQuad> source = part.getQuads(d);
            if (source.isEmpty()) continue;
            quads[d.ordinal()] = List.of(over(source.getFirst(), sp[stage - 1], d));
            any = true;
        }
        return any ? new GrownPart(quads, part.particleMaterial()) : null;
    }

    /** The side quad's shape, LIFT out along its face, with {@code sprite} over it (each corner keeps its place) on the cut-out layer, tinted. */
    private static BakedQuad over(BakedQuad side, TextureAtlasSprite sprite, Direction d) {
        TextureAtlasSprite from = side.materialInfo().sprite();
        float du = from.getU1() - from.getU0(), dv = from.getV1() - from.getV0();
        long[] uv = new long[4];
        Vector3f[] p = new Vector3f[4];
        for (int i = 0; i < 4; i++) {
            float fu = du == 0F ? 0F : (UVPair.unpackU(side.packedUV(i)) - from.getU0()) / du;
            float fv = dv == 0F ? 0F : (UVPair.unpackV(side.packedUV(i)) - from.getV0()) / dv;
            uv[i] = UVPair.pack(sprite.getU0() + fu * (sprite.getU1() - sprite.getU0()), sprite.getV0() + fv * (sprite.getV1() - sprite.getV0()));
            p[i] = new Vector3f(side.position(i)).add(d.getStepX() * LIFT, 0F, d.getStepZ() * LIFT);
        }
        BakedQuad.MaterialInfo m = side.materialInfo();
        BakedQuad.MaterialInfo info = new BakedQuad.MaterialInfo(sprite, ChunkSectionLayer.CUTOUT, m.itemRenderType(), m.itemGlintRenderType(),
                m.itemGlintSpecialRenderType(), 0, m.shadeDirectionOverride(), m.lightEmission(), m.ambientOcclusion());
        return new BakedQuad(p[0], p[1], p[2], p[3], uv[0], uv[1], uv[2], uv[3], side.direction(), info, side.bakedNormals(), BakedColors.DEFAULT);
    }

    /** The growing grass's quads, each on its side (culled with that face). */
    private static final class GrownPart implements BlockStateModelPart {
        private final List<BakedQuad>[] quads;
        private final Material.Baked particle;

        GrownPart(List<BakedQuad>[] quads, Material.Baked particle) {
            this.quads = quads;
            this.particle = particle;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable Direction direction) {
            if (direction == null) return List.of();
            List<BakedQuad> q = this.quads[direction.ordinal()];
            return q == null ? List.of() : q;
        }

        @Override
        @Deprecated
        public boolean useAmbientOcclusion() {
            return true;
        }

        @Override
        public TriState ambientOcclusion() {
            return TriState.DEFAULT;
        }

        @Override
        public Material.Baked particleMaterial() {
            return this.particle;
        }

        @Override
        public int materialFlags() {
            return 0;
        }
    }

    // ---- creep ------------------------------------------------------------------------------------------------------------

    private static volatile TextureAtlasSprite @Nullable [] edges, corners;

    /** Reloaded sprites after a resource reload (the atlas is new). */
    static void clearSprites() {
        edges = null;
        corners = null;
        growing = null;
    }

    private static TextureAtlasSprite[] sprites(String kind, int n) {
        TextureAtlasSprite[] s = new TextureAtlasSprite[n];
        for (int i = 0; i < n; i++) {
            Identifier id = Identifier.fromNamespaceAndPath("awaken", "block/ground/creep_" + kind + "_" + i);
            s[i] = Minecraft.getInstance().getAtlasManager().get(new SpriteId(TextureAtlas.LOCATION_BLOCKS, id));
        }
        return s;
    }

    private @Nullable BlockStateModelPart creep(BlockAndTintGetter level, BlockPos pos, BlockState state, int mask,
                                                List<BlockStateModelPart> parts, int from) {
        BakedQuad.MaterialInfo base = this.template;
        if (base == null) {
            for (int i = from; i < parts.size() && base == null; i++) {
                List<BakedQuad> ups = parts.get(i).getQuads(Direction.UP);
                if (!ups.isEmpty()) base = ups.getFirst().materialInfo();
            }
            if (base == null) return null;
            this.template = base;
        }
        TextureAtlasSprite[] e = edges, c = corners;
        if (e == null || c == null) {
            e = sprites("edge", 3);
            c = sprites("corner", 2);
            edges = e;
            corners = c;
        }
        float y = (state.is(Blocks.DIRT_PATH) ? 0.9375F : 1F) + LIFT;
        List<BakedQuad> quads = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            if ((mask >> i & 1) == 0) continue;
            BlockPos grass = pos.relative(SeamlessRules.SIDES[i]);
            int h = hash(pos.getX(), pos.getZ(), i);
            quads.add(quad(info(base, e[Math.floorMod(h, e.length)]), y, i, false, lawn(level, grass)));
        }
        for (int i = 0; i < 4; i++) {
            if ((mask >> (4 + i) & 1) == 0) continue;
            BlockPos grass = pos.offset(SeamlessRules.CORNER_STEP[i][0], 0, SeamlessRules.CORNER_STEP[i][1]);
            int h = hash(pos.getX(), pos.getZ(), 4 + i);
            quads.add(quad(info(base, c[Math.floorMod(h, c.length)]), y, i, true, lawn(level, grass)));
        }
        return quads.isEmpty() ? null : new CreepPart(List.copyOf(quads), this.delegate.particleMaterial());
    }

    private static int hash(int x, int z, int salt) {
        int h = x * 374761393 + z * 668265263 + salt * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return (h ^ (h >>> 16)) & 0x7FFFFFFF;
    }

    /** The overlay's material: the top face's, with our sprite, the cut-out layer and no tint (the colour is baked). */
    private static BakedQuad.MaterialInfo info(BakedQuad.MaterialInfo t, TextureAtlasSprite sprite) {
        return new BakedQuad.MaterialInfo(sprite, ChunkSectionLayer.CUTOUT, t.itemRenderType(), t.itemGlintRenderType(),
                t.itemGlintSpecialRenderType(), -1, null, 0, true);
    }

    /** The colour the grass at {@code at} is drawn in (drift included, as that block's own top uses it). */
    private static int lawn(BlockAndTintGetter level, BlockPos at) {
        BlockState g = level.getBlockState(at);
        BlockTintSource source = Minecraft.getInstance().getBlockColors().getTintSource(g, 0);
        int rgb = source == null ? 0x79C05A : source.colorInWorld(g, level, at);
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /**
     * An overlay quad on the top face. The sprite's blades lie along its north edge (or its north-west corner); side
     * {@code r} (north, east, south, west; corner r: north-east, south-east, south-west, north-west) turns it to that edge.
     */
    private static BakedQuad quad(BakedQuad.MaterialInfo info, float y, int r, boolean corner, int argb) {
        TextureAtlasSprite sp = info.sprite();
        float[][] xz = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};        // an up face's four corners, as vanilla orders them
        Vector3f[] p = new Vector3f[4];
        long[] uv = new long[4];
        for (int v = 0; v < 4; v++) {
            float x = xz[v][0], z = xz[v][1], s, t;
            if (!corner) {
                switch (r) {
                    case 0 -> { s = x; t = z; }
                    case 1 -> { s = z; t = 1 - x; }
                    case 2 -> { s = 1 - x; t = 1 - z; }
                    default -> { s = 1 - z; t = x; }
                }
            } else {
                switch (r) {
                    case 0 -> { s = 1 - x; t = z; }           // north-east: the sprite's corner turned to x = 1, z = 0
                    case 1 -> { s = 1 - x; t = 1 - z; }
                    case 2 -> { s = x; t = 1 - z; }
                    default -> { s = x; t = z; }
                }
            }
            p[v] = new Vector3f(x, y, z);
            uv[v] = UVPair.pack(sp.getU0() + s * (sp.getU1() - sp.getU0()), sp.getV0() + t * (sp.getV1() - sp.getV0()));
        }
        return new BakedQuad(p[0], p[1], p[2], p[3], uv[0], uv[1], uv[2], uv[3], Direction.UP, info, BakedNormals.UNSPECIFIED, BakedColors.of(argb));
    }

    /** A part of overlay quads, unculled (they lie on a face that is showing). */
    private static final class CreepPart implements BlockStateModelPart {
        private final List<BakedQuad> quads;
        private final Material.Baked particle;

        CreepPart(List<BakedQuad> quads, Material.Baked particle) {
            this.quads = quads;
            this.particle = particle;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable Direction direction) {
            return direction == null ? this.quads : List.of();
        }

        @Override
        @Deprecated
        public boolean useAmbientOcclusion() {
            return true;
        }

        @Override
        public TriState ambientOcclusion() {
            return TriState.DEFAULT;
        }

        @Override
        public Material.Baked particleMaterial() {
            return this.particle;
        }

        @Override
        public int materialFlags() {
            return 0;
        }
    }
}
