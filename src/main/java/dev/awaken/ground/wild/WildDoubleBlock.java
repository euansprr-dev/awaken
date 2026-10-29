package dev.awaken.ground.wild;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jspecify.annotations.Nullable;

/**
 * A two-block wild plant (SPEC §41.2): long grass, cow parsley, foxglove, bracken, reeds, and the upper two blocks of wild
 * rye. A {@link DoublePlantBlock}, so it sways as the game's tall grass does (the halves share one seed, so their versions
 * match) and breaks as one. {@code look} picks a look where the plant has more than one (long grass: green, seeding, tawny;
 * bracken: green, turned; reeds: brown, pale; rye: gold, glass); both halves carry the same.
 */
public class WildDoubleBlock extends DoublePlantBlock {
    public static final IntegerProperty LOOK = IntegerProperty.create("look", 0, 2);

    private final WildKind kind;

    public WildDoubleBlock(WildKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER).setValue(LOOK, 0));
    }

    public WildKind kind() {
        return kind;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LOOK);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        int look = by == null ? 0 : WildLooks.look(level, this, pos);
        level.setBlock(pos, state.setValue(LOOK, look), 2);
        level.setBlockAndUpdate(pos.above(), copyWaterloggedFrom(level, pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER).setValue(LOOK, look)));
    }

    /** Pushing through it at a run presses it flat (a person's thin trail, SPEC §41.2); seeding heads shed fluff on the client. */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean isPrecise) {
        WildLife.pushThrough(state, level, pos, entity);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        WildLife.animate(state, level, pos, random);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return false;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
    }
}
