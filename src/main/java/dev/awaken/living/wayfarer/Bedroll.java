package dev.awaken.living.wayfarer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * A Wayfarer's bedroll by the camp's fire (SPEC §27.6): a wool roll laid out on the ground, its head rolled up for a
 * pillow. Sleeps the night through like a bed when it's dark, but never moves your spawn (that's what a Hearth is for);
 * and when a step you're on needs the night it says so in a line and lets you stay up. Placed by the camp, never dropped.
 */
public final class Bedroll extends AbstractBedBlock {
    private static final VoxelShape BASE = Block.column(14.0, 0.0, 2.0);
    private static final VoxelShape PILLOW = Block.box(1.0, 0.0, 9.0, 15.0, 5.0, 16.0);
    private static final Map<Direction, VoxelShape> FOOT = Util.make(() -> Shapes.rotateHorizontal(BASE));
    private static final Map<Direction, VoxelShape> HEAD = Util.make(() -> Shapes.rotateHorizontal(Shapes.or(BASE, PILLOW)));

    /** Sleep when it's dark; never the spawn. */
    static final BedRule RULE = new BedRule(BedRule.Rule.WHEN_DARK, BedRule.Rule.NEVER, false, false,
            Optional.of(Component.literal("The Wayfarers sleep when it's dark. Sit by the fire till then.")));

    public Bedroll(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Map<Direction, VoxelShape> shapes = state.getValue(PART) == BedPart.HEAD ? HEAD : FOOT;
        return shapes.get(getConnectedDirection(state).getOpposite());
    }

    @Override
    protected EnvironmentAttribute<BedRule> getBedEnvironmentAttribute() {
        return EnvironmentAttributes.BED_RULE;
    }

    @Override
    public BedRule getBedRule(Level level, BlockPos pos) {
        return RULE;
    }

    /** Lying on the roll, not on the pillow: the head half sleeps at the foot half's height. */
    @Override
    public OptionalDouble getSleepHeight(BlockState state, Level level, BlockPos pos) {
        if (state.getValue(PART) == BedPart.HEAD) {
            BlockPos foot = pos.relative(getConnectedDirection(state));
            BlockState f = level.getBlockState(foot);
            if (!f.is(this) || f.getValue(PART) != BedPart.FOOT) return OptionalDouble.empty();
            return super.getSleepHeight(f, level, foot);
        }
        return super.getSleepHeight(state, level, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            String night = Camp.nightNeeded(sp);
            if (night != null && level.isDarkOutside()) {
                sp.sendOverlayMessage(Component.literal("Not tonight: " + night));
                return InteractionResult.SUCCESS_SERVER;
            }
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    protected InteractionResult destroyOnUse(BlockState state, Level level, BlockPos pos, Player player) {
        return InteractionResult.PASS; // the rule never asks
    }

    @Override
    protected void destroyOnLeave(Level level, BlockPos pos) {}
}
