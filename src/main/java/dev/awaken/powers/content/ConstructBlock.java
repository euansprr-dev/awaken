package dev.awaken.powers.content;

import dev.awaken.powers.PowerSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Hard-light construct placed by Architect powers (and dream constructs from Waking Dream). It cracks through four
 * ages on scheduled ticks and vanishes after about 30 s. Scheduled ticks are saved with the chunk, so constructs decay
 * even across unloads and restarts. Drops nothing.
 */
public final class ConstructBlock extends Block {
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 3);
    public static final BooleanProperty SLAB = BooleanProperty.create("slab");
    /** Ticks per age: four ages = 30 s. */
    public static final int STAGE = 150;
    public static final int LIFETIME = STAGE * 4;

    private static final VoxelShape SLAB_SHAPE = Block.box(0, 0, 0, 16, 8, 16);

    public ConstructBlock(BlockBehaviour.Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(AGE, 0).setValue(SLAB, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE, SLAB);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(SLAB) ? SLAB_SHAPE : Shapes.block();
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return state.getValue(SLAB);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    /** The state and first delay for a construct that should live {@code ticks} ticks. */
    public BlockState stateFor(boolean slab, int ticks) {
        int stagesLeft = Math.max(1, Math.min(4, (ticks + STAGE - 1) / STAGE));
        return defaultBlockState().setValue(SLAB, slab).setValue(AGE, 4 - stagesLeft);
    }

    public static int firstDelay(int ticks) {
        int rem = ticks % STAGE;
        return ticks >= LIFETIME ? STAGE : (rem == 0 ? STAGE : rem);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int age = state.getValue(AGE);
        if (age < 3) {
            level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, this, STAGE);
            return;
        }
        fade(level, pos, state);
    }

    /** Removes a construct with its fade effect. */
    public static void fade(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                6, 0.3, 0.3, 0.3, 0.02);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1, 0.2, 0.2, 0.2, 0.01);
        if (level.getRandom().nextInt(4) == 0) {
            level.playSound(null, pos, PowerSounds.CONSTRUCT_FADE.get(), SoundSource.BLOCKS, 0.4f, 1.2f + level.getRandom().nextFloat() * 0.4f);
        }
    }
}
