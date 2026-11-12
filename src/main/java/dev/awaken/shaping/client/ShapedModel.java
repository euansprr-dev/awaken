package dev.awaken.shaping.client;

import dev.awaken.shaping.Shape;
import dev.awaken.shaping.ShapedBlock;
import dev.awaken.shaping.ShapedBlockEntity;
import dev.awaken.shaping.ShapingConfig;
import net.minecraft.client.model.geom.builders.UVPair;
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
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import net.neoforged.neoforge.client.model.quad.BakedColors;
import net.neoforged.neoforge.client.model.quad.BakedNormals;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A cut block's mesh (SPEC §52.6): the cut's quads in the look's own textures. Runs on the meshing threads, Sodium's
 * included, with a snapshot of the world; each look, cut, facing and corner is built once and shared by every block that
 * has it. The corner is in the block state, so it's never read from the world here.
 */
final class ShapedModel extends DelegateBlockStateModel {
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int SLOTS = Shape.values().length * Shape.VARIANTS;
    private static final ConcurrentHashMap<BlockState, BlockStateModelPart[]> PARTS = new ConcurrentHashMap<>();

    ShapedModel(BlockStateModel delegate) {
        super(delegate);
    }

    static void clear() {
        PARTS.clear();
        LookMaterials.clear();
    }

    /** What the block here wears. The block entity first: it can't be out of date. */
    static @Nullable BlockState lookAt(BlockAndTintGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ShapedBlockEntity be && be.look() != null) return be.look();
        return level.getModelData(pos).get(ShapedBlockEntity.LOOK);
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
        BlockState look = lookAt(level, pos);
        Shape shape = ShapingConfig.drawShapes() ? state.getValue(ShapedBlock.SHAPE) : Shape.FULL;
        BlockStateModelPart part = look == null ? null : part(look, shape, state.getValue(ShapedBlock.FACING), state.getValue(ShapedBlock.CORNER));
        if (part == null) this.delegate.collectParts(level, pos, state, random, parts);
        else parts.add(part);
    }

    @Override
    public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        BlockState look = lookAt(level, pos);
        LookMaterials.Look m = look == null ? null : LookMaterials.of(look);
        return m == null ? super.particleMaterial(level, pos, state) : m.particle();
    }

    private static @Nullable BlockStateModelPart part(BlockState look, Shape shape, Direction facing, StairsShape corner) {
        LookMaterials.Look m = LookMaterials.of(look);
        if (m == null) return null;
        BlockStateModelPart[] slots = PARTS.computeIfAbsent(look, k -> new BlockStateModelPart[SLOTS]);
        int at = shape.ordinal() * Shape.VARIANTS + shape.variant(facing, corner);
        BlockStateModelPart p = slots[at];
        if (p == null) {
            p = new Part(m, shape.faces(facing, corner));       // the same every time: a race only builds it twice
            slots[at] = p;
        }
        return p;
    }

    private static final class Part implements BlockStateModelPart {
        private final List<BakedQuad>[] quads;     // 0..5 per Direction, 6 unculled
        private final LookMaterials.Look look;

        @SuppressWarnings("unchecked")
        Part(LookMaterials.Look look, List<Shape.Face> faces) {
            this.look = look;
            List<BakedQuad>[] lists = new List[DIRECTIONS.length + 1];
            for (int i = 0; i < lists.length; i++) lists[i] = new ArrayList<>();
            for (Shape.Face f : faces) lists[f.cull() == null ? DIRECTIONS.length : f.cull().ordinal()].add(quad(f, look.side(f.dominant())));
            this.quads = new List[lists.length];
            for (int i = 0; i < lists.length; i++) this.quads[i] = List.copyOf(lists[i]);
        }

        private static BakedQuad quad(Shape.Face f, LookMaterials.Side side) {
            Direction d = f.dominant();
            long[] uv = new long[4];
            Vector3f[] p = new Vector3f[4];
            for (int i = 0; i < 4; i++) {
                float x = f.x(i), y = f.y(i), z = f.z(i);
                float a = LookMaterials.a(d, x, y, z), b = LookMaterials.b(d, x, y, z);
                p[i] = new Vector3f(x, y, z);
                uv[i] = UVPair.pack(side.u(a, b), side.v(a, b));
            }
            return new BakedQuad(p[0], p[1], p[2], p[3], uv[0], uv[1], uv[2], uv[3], d, side.info(),
                    BakedNormals.of(BakedNormals.pack(f.nx(), f.ny(), f.nz())), BakedColors.DEFAULT);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable Direction direction) {
            return this.quads[direction == null ? DIRECTIONS.length : direction.ordinal()];
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
            return this.look.particle();
        }

        @Override
        public int materialFlags() {
            return this.look.flags();
        }
    }
}
