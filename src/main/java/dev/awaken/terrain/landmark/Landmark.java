package dev.awaken.terrain.landmark;

import com.mojang.serialization.Codec;
import dev.awaken.terrain.fault.FaultBlocks;
import dev.awaken.terrain.frost.FrostBlocks;
import dev.awaken.terrain.glass.GlassBlocks;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Awakened landmarks that are not trees (docs/modules/lands.md): a Gate Scar in the Glass Steppe, a Shard Drift
 * over the Shattered Highlands' faults and an Aurora Spire on its frozen tarn in the Frost Spires. Each knows its
 * palette (voxel code to block) and how far round its centre it reaches, which the site search keeps clear of the city
 * reserve.
 */
public enum Landmark implements StringRepresentable {
    GATE_SCAR("gate_scar", 48) {
        @Override
        BlockState[] palette() {
            return new BlockState[]{Blocks.AIR.defaultBlockState(), GlassBlocks.SUNSHARD.get().defaultBlockState(),
                    GlassBlocks.FUSED_GLASS.get().defaultBlockState(), GlassBlocks.SCORCHED_GLASS.get().defaultBlockState(),
                    Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
                    GlassBlocks.SUNSHARD_CLUSTER.get().defaultBlockState(), Blocks.CHISELED_DEEPSLATE.defaultBlockState()};
        }
    },
    SHARD_DRIFT("shard_drift", 44) {
        @Override
        BlockState[] palette() {
            return new BlockState[]{Blocks.AIR.defaultBlockState(),
                    FaultBlocks.RIVEN_SHALE.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y),
                    FaultBlocks.FAULTSTONE.get().defaultBlockState(), FaultBlocks.FAULT_SEAM.get().defaultBlockState(),
                    Blocks.GRASS_BLOCK.defaultBlockState().setValue(GrassBlock.SNOWY, false), Blocks.DIRT.defaultBlockState(),
                    Blocks.HANGING_ROOTS.defaultBlockState(), Blocks.COARSE_DIRT.defaultBlockState()};
        }
    },
    AURORA_SPIRE("aurora_spire", 40) {
        @Override
        BlockState[] palette() {
            return new BlockState[]{Blocks.AIR.defaultBlockState(), FrostBlocks.FROSTSPAR.get().defaultBlockState(),
                    FrostBlocks.FROSTSPAR_CLUSTER.get().defaultBlockState()};
        }
    };

    public static final Codec<Landmark> CODEC = StringRepresentable.fromEnum(Landmark::values);

    private final String id;
    /** About how far it reaches from its centre (kept clear of the city reserve). */
    final int reach;
    private volatile BlockState[] states;

    Landmark(String id, int reach) {
        this.id = id;
        this.reach = reach;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    /** How far from its centre any block of the landmark can lie (a circle; the city reserve is kept clear of it). */
    public int reach() {
        return reach;
    }

    abstract BlockState[] palette();

    /** The block for a voxel code. */
    BlockState state(byte code) {
        BlockState[] s = states;
        if (s == null) states = s = palette();
        return s[code];
    }
}
