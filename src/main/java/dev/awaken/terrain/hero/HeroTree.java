package dev.awaken.terrain.hero;

import com.mojang.serialization.Codec;
import dev.awaken.terrain.LandsBlocks;
import dev.awaken.terrain.mire.MireBlocks;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HangingMossBlock;
import net.minecraft.world.level.block.LeafLitterBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The hero trees (docs/modules/lands.md): giants built as structures. Each knows its shape (a {@link Blueprint} from a
 * seed), its blocks, the ground it needs (a flat enough footprint and root ring, checked before it is placed, and how
 * deep water may stand there) and the ground cover it lays round its foot.
 */
public enum HeroTree implements StringRepresentable {
    ELDER_COLOSSUS("elder_colossus", 5, 3, 13, 10, 20, 46, 0) {
        @Override
        Blueprint shape(long seed) {
            return ElderColossus.build(seed);
        }

        @Override
        BlockState[] blocks() {
            return palette(LandsBlocks.ELDER_LOG.get(), LandsBlocks.ELDER_WOOD.get(), LandsBlocks.ELDER_LEAVES.get(), LandsBlocks.ELDER_LEAVES_GOLD.get(), null,
                    Blocks.MOSS_CARPET.defaultBlockState());
        }

        /** Moss and podzol in patches near the trunk, gold litter thinning outward, a fern now and then. */
        @Override
        BlockState[] cover(double dist, double n, double patch) {
            double near = 1 - dist / groundRadius;
            BlockState ground = null, top = null;
            if (dist < 8 && patch > 0.45) ground = patch > 0.62 ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.PODZOL.defaultBlockState();
            else if (patch > 0.7) ground = Blocks.MOSS_BLOCK.defaultBlockState();
            if (ground != null && ground.is(Blocks.MOSS_BLOCK) && n < 0.4) top = Blocks.MOSS_CARPET.defaultBlockState();
            else if (n < 0.25 + 0.5 * near) {
                int segs = 1 + (int) (n * 97) % 4;
                Direction f = Direction.from2DDataValue((int) (n * 1013) % 4);
                top = Blocks.LEAF_LITTER.defaultBlockState().setValue(BlockStateProperties.SEGMENT_AMOUNT, segs).setValue(BlockStateProperties.HORIZONTAL_FACING, f);
            } else if (n > 0.93) top = Blocks.FERN.defaultBlockState();
            return new BlockState[]{ground, top};
        }
    },
    LUMEN_SPIRE("lumen_spire", 3, 2, 8, 6, 10, 20, 0) {
        @Override
        Blueprint shape(long seed) {
            Blueprint bp = LumenSpire.build(seed);
            return bp;
        }

        @Override
        void finish(Blueprint bp, long seed) {
            LumenSpire.vines(bp, seed, 0);
        }

        @Override
        BlockState[] blocks() {
            return palette(LandsBlocks.VEINED_LUMEN_LOG.get(), LandsBlocks.LUMEN_WOOD.get(), LandsBlocks.LUMEN_LEAVES.get(), LandsBlocks.LUMEN_LEAVES.get(),
                    LandsBlocks.LUMEN_VINES.get(),
                    LandsBlocks.LUMEN_MOSS.get().defaultBlockState());
        }

        /** Moss in patches, lumen moss thickening toward the trunk, caps here and there. */
        @Override
        BlockState[] cover(double dist, double n, double patch) {
            double near = 1 - dist / groundRadius;
            BlockState ground = patch > 0.35 ? Blocks.MOSS_BLOCK.defaultBlockState() : null;
            BlockState top = null;
            if (n < 0.35 + 0.55 * near) top = LandsBlocks.LUMEN_MOSS.get().defaultBlockState();
            else if (n > 0.9) top = LandsBlocks.LUMEN_CAP.get().defaultBlockState();
            return new BlockState[]{ground, top};
        }
    },
    /** Stands in shallow water: up to three blocks of it over the ground at its site. */
    WEEPING_MATRIARCH("weeping_matriarch", 4, 3, 10, 6, 22, 34, 3) {
        @Override
        Blueprint shape(long seed) {
            return WeepingMatriarch.build(seed);
        }

        @Override
        void finish(Blueprint bp, long seed) {
            WeepingMatriarch.tendrils(bp, seed);
        }

        @Override
        BlockState[] blocks() {
            return palette(MireBlocks.WEEPWOOD_LOG.get(), MireBlocks.WEEPWOOD_WOOD.get(), MireBlocks.WEEPWOOD_LEAVES.get(), MireBlocks.WEEPWOOD_LEAVES.get(),
                    MireBlocks.WEEPWOOD_TENDRILS.get(), Blocks.MOSS_CARPET.defaultBlockState());
        }

        /** Mirepeat round its feet where they stand dry; the water under its crown is the moonblooms'. */
        @Override
        BlockState[] cover(double dist, double n, double patch) {
            return new BlockState[]{patch > 0.4 || dist < 7 ? MireBlocks.MIREPEAT.get().defaultBlockState() : null, null};
        }

        @Override
        BlockState onWater(double dist, double n) {
            return n < 0.16 * (1 - dist / groundRadius) + 0.02 ? MireBlocks.MOONBLOOM.get().defaultBlockState() : null;
        }
    };

    public static final Codec<HeroTree> CODEC = StringRepresentable.fromEnum(HeroTree::values);

    private final String id;
    /** Half-width of the footprint that must be nearly level (at most {@code maxStep} between its corners). */
    final int footprint, maxStep;
    /** The root ring: eight points this far out must be dry land within {@code maxRing} of the centre's height. */
    final int ring, maxRing;
    /** How far round the foot the tree lays its own ground cover. */
    final int groundRadius;
    /** About how far its crown reaches from the trunk (kept clear of the city reserve). */
    final int reach;
    /** How deep water may stand over the ground at its site (0: dry land only). */
    final int wetDepth;
    private volatile BlockState[] states;

    HeroTree(String id, int footprint, int maxStep, int ring, int maxRing, int groundRadius, int reach, int wetDepth) {
        this.id = id;
        this.footprint = footprint;
        this.maxStep = maxStep;
        this.ring = ring;
        this.maxRing = maxRing;
        this.groundRadius = groundRadius;
        this.reach = reach;
        this.wetDepth = wetDepth;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    /** How far from its foot any block of the tree can lie (a circle; the city reserve is kept clear of it). */
    public int reach() {
        return reach;
    }

    abstract Blueprint shape(long seed);

    abstract BlockState[] blocks();

    /** Anything hung on the settled tree (vines from leaves that stay). */
    void finish(Blueprint bp, long seed) {}

    /** The whole blueprint for a seed: shape, leaves settled (distances, loose leaves gone), finishing, sealed. */
    public Blueprint build(long seed) {
        Blueprint bp = shape(seed);
        bp.settleLeaves();
        finish(bp, seed);
        bp.seal();
        return bp;
    }

    /** The block for a blueprint cell. */
    BlockState state(byte code) {
        BlockState[] s = states;
        if (s == null) states = s = blocks();
        return s[code];
    }

    private static BlockState[] palette(Block log, Block wood, Block leaves, Block leaves2, Block vines, BlockState moss) {
        BlockState[] s = new BlockState[Blueprint.CODES];
        BlockState air = Blocks.AIR.defaultBlockState();
        java.util.Arrays.fill(s, air);
        s[Blueprint.LOG] = log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        s[Blueprint.LOG_X] = log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        s[Blueprint.LOG_Z] = log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
        s[Blueprint.WOOD] = wood.defaultBlockState();
        for (int d = 1; d <= 6; d++) {
            s[Blueprint.LEAF_D1 + d - 1] = leaves.defaultBlockState().setValue(LeavesBlock.DISTANCE, d).setValue(LeavesBlock.PERSISTENT, false);
        }
        s[Blueprint.LEAF_PERSISTENT] = leaves.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int d = 1; d <= 6; d++) {
            s[Blueprint.LEAF2_D1 + d - 1] = leaves2.defaultBlockState().setValue(LeavesBlock.DISTANCE, d).setValue(LeavesBlock.PERSISTENT, false);
        }
        s[Blueprint.LEAF2_PERSISTENT] = leaves2.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        if (vines != null) {
            s[Blueprint.VINE] = vines.defaultBlockState().setValue(HangingMossBlock.TIP, false);
            s[Blueprint.VINE_TIP] = vines.defaultBlockState().setValue(HangingMossBlock.TIP, true);
        }
        s[Blueprint.MOSS] = moss;
        return s;
    }

    /**
     * Ground cover at {@code dist} blocks from the trunk, on a column whose top is soil: the block for the ground itself
     * (or null to keep it) and the cover on it (or null), chosen by a stable hash {@code n} and patch value {@code patch}.
     */
    abstract BlockState[] cover(double dist, double n, double patch);

    /** What floats on open water at {@code dist} from the trunk (or null): the hash {@code n} decides. */
    BlockState onWater(double dist, double n) {
        return null;
    }
}
