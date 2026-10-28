package dev.awaken.hearth.block;

import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.HearthService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The heart of a home. One per player; defines the Hearth's radius, respawn point and the warmth that mends Fracture. A
 * hearthstone brazier whose fire shows the Hearth's tier (SPEC §28.8, §28.11): {@link #TIER} 1-5, set whenever the tier
 * changes. Light 15 at every tier, and a warm pool of light on the ground round it (the city's light pass, HomeClient).
 */
public class HearthAnchorBlock extends Block {
    public static final IntegerProperty TIER = IntegerProperty.create("tier", 1, 5);
    /** The brazier as built (models/block/hearth_anchor_t*): plinth, step, stem, the bowl's foot and its rim. */
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 2, 16), Block.box(1, 2, 1, 15, 3, 15),
            Block.box(3, 3, 3, 13, 8, 13), Block.box(1, 8, 1, 15, 10, 15), Block.box(0, 10, 0, 16, 13, 16));
    /** The rim's top, in blocks: the fire and its particles start here. */
    private static final double RIM = 13 / 16.0;

    // ---- the Anchors this client has seen burning lately (filled by animateTick, client only; read by the glow pass) ---
    public static final int SEEN_MAX = 16;
    public static final long[] SEEN_POS = new long[SEEN_MAX];
    public static final long[] SEEN_AT = new long[SEEN_MAX];
    public static int seen;

    public HearthAnchorBlock(BlockBehaviour.Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(TIER, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(TIER);
    }

    /** The Anchor as it burns at a tier (1-5). */
    public BlockState atTier(int tier) {
        return defaultBlockState().setValue(TIER, Math.max(1, Math.min(5, tier)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        if (ctx.getLevel() instanceof ServerLevel level && ctx.getPlayer() instanceof ServerPlayer sp
                && !HearthService.canPlaceAnchor(sp, level, ctx.getClickedPos())) {
            return null;
        }
        return defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (level instanceof ServerLevel sl && by instanceof ServerPlayer sp) HearthService.onAnchorPlaced(sp, sl, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) HearthService.onAnchorUsed(sp, sl, pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        HearthService.onAnchorRemoved(level, pos);
    }

    /** How far each tier's flame reaches above the rim, in blocks (the models' flame textures; index = tier). */
    private static final double[] FLAME_TIP = {0, 0.8, 0.9, 1.0, 1.1, 1.2};
    /** Tier V's crown points on the rim, as x, z within the block: the four corners and the middle of each side. */
    private static final double[] CROWN_POINTS = {
            0.07, 0.07, 0.93, 0.07, 0.07, 0.93, 0.93, 0.93, 0.5, 0.07, 0.5, 0.93, 0.07, 0.5, 0.93, 0.5};

    /**
     * The flame by tier, alongside the model's (models/block/hearth_anchor_t1..t5). Vanilla calls this for a random
     * sample of nearby blocks, about once every 5-20 ticks for one block (more often the nearer you stand), so each
     * call spends a handful of particles: tongues of flame out of the bowl and embers off the tips, more of both up the
     * tiers; sparks flying up from Tier IV and a soft glow round the flame; at V light lifting off the crown's points.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int tier = state.getValue(TIER);
        if (level.isClientSide()) sawBurning(pos.asLong(), level.getGameTime());
        double cx = pos.getX() + 0.5, cz = pos.getZ() + 0.5, rim = pos.getY() + RIM, tip = FLAME_TIP[tier];
        // tongues of flame out of the whole mouth of the bowl (12 texels across), two or three a call
        for (int i = tier >= 4 ? 3 : 2; i > 0; i--) {
            if (random.nextFloat() < 0.45f + 0.1f * tier) {
                level.addParticle(HearthContent.HEARTH_FLAME.get(), cx + (random.nextDouble() - 0.5) * 0.6, rim - 0.06 + random.nextDouble() * 0.12,
                        cz + (random.nextDouble() - 0.5) * 0.6, 0, 0.025 + 0.008 * tier + random.nextDouble() * 0.02, 0);
            }
        }
        int embers = random.nextFloat() < 0.45f + 0.12f * tier ? (tier >= 3 && random.nextBoolean() ? 2 : 1) : 0;
        for (int i = 0; i < embers; i++) {
            level.addParticle(HearthContent.HEARTH_EMBER.get(), cx + (random.nextDouble() - 0.5) * 0.5, rim + tip * (0.4 + random.nextDouble() * 0.6),
                    cz + (random.nextDouble() - 0.5) * 0.5, (random.nextDouble() - 0.5) * 0.03, 0.015 + random.nextDouble() * 0.03,
                    (random.nextDouble() - 0.5) * 0.03);
        }
        if (tier < 4 && random.nextFloat() < 0.12f) {
            // now and then a spark thrown up off the coals, even at Tier I
            level.addParticle(HearthContent.HEARTH_EMBER.get(), cx + (random.nextDouble() - 0.5) * 0.3, rim + 0.1,
                    cz + (random.nextDouble() - 0.5) * 0.3, (random.nextDouble() - 0.5) * 0.05, 0.07 + random.nextDouble() * 0.05,
                    (random.nextDouble() - 0.5) * 0.05);
        }
        if (tier >= 4) {
            // sparks: embers thrown up hard off the Heart, slowing as they climb a block or three
            if (random.nextFloat() < (tier == 5 ? 0.4f : 0.25f)) {
                for (int i = tier == 5 && random.nextBoolean() ? 2 : 1; i > 0; i--) {
                    level.addParticle(HearthContent.HEARTH_EMBER.get(), cx + (random.nextDouble() - 0.5) * 0.2, rim + 0.05,
                            cz + (random.nextDouble() - 0.5) * 0.2, (random.nextDouble() - 0.5) * 0.08, 0.09 + random.nextDouble() * 0.07,
                            (random.nextDouble() - 0.5) * 0.08);
                }
            }
            // a soft glow round the flame's heart (the warmth sprite, its brightness from ya)
            if (random.nextFloat() < (tier == 5 ? 0.16f : 0.08f)) {
                level.addParticle(HearthContent.HEARTH_WARMTH.get(), cx, rim + tip * 0.35, cz, 0, tier == 5 ? 0.24 : 0.16, 0);
            }
        }
        if (tier == 5 && random.nextFloat() < 0.5f) {
            // the crown of light: a mote lifting slowly off one of its points
            int p = random.nextInt(CROWN_POINTS.length / 2) * 2;
            level.addParticle(HearthContent.HEARTH_RING.get(), pos.getX() + CROWN_POINTS[p], rim + 0.4, pos.getZ() + CROWN_POINTS[p + 1], 0, 0.004, 0);
        }
        if (random.nextInt(16) == 0) {
            // the fire's crackle (the Hearthfire's own), a little fuller and deeper as it grows
            level.playLocalSound(cx, rim, cz, HearthContent.SND_FIRE.get(), SoundSource.BLOCKS, 0.45F + 0.07F * tier + random.nextFloat() * 0.2F,
                    0.98F - 0.04F * tier + random.nextFloat() * 0.25F, false);
        }
    }

    /** Remembers an Anchor burning near this client (no allocation: a fixed table, the stalest entry gives way). */
    private static void sawBurning(long pos, long now) {
        int stalest = 0;
        for (int i = 0; i < seen; i++) {
            if (SEEN_POS[i] == pos) {
                SEEN_AT[i] = now;
                return;
            }
            if (SEEN_AT[i] < SEEN_AT[stalest]) stalest = i;
        }
        int at = seen < SEEN_MAX ? seen++ : stalest;
        SEEN_POS[at] = pos;
        SEEN_AT[at] = now;
    }

    /** Forgets entry i (the glow pass found it gone or long unseen). */
    public static void forget(int i) {
        seen--;
        SEEN_POS[i] = SEEN_POS[seen];
        SEEN_AT[i] = SEEN_AT[seen];
    }
}
