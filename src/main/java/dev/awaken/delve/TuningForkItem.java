package dev.awaken.delve;

import dev.awaken.delve.api.Lodes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * The Tuning Fork (PLAN 7.5): touch it to an ore and it takes that ore's note; strike it on stone and it rings, and the
 * ring climbs in pitch as you turn to face the nearest lode of that ore within {@link #RANGE} blocks (the client sings
 * it, with a soft arc on the band). Prospecting by ear. The server answers from the lodes' plan (no scan).
 */
public final class TuningForkItem extends Item {
    public static final int RANGE = 48, COOLDOWN = 30;

    public TuningForkItem(Item.Properties p) {
        super(p);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level) || !(ctx.getPlayer() instanceof ServerPlayer p)) return InteractionResult.SUCCESS;
        ItemStack fork = ctx.getItemInHand();
        BlockPos pos = ctx.getClickedPos();
        BlockState s = level.getBlockState(pos);
        Ore ore = Ore.of(s);
        if (ore != null) {
            fork.set(DelveContent.TUNED.get(), ore.id());
            level.playSound(null, pos, DelveContent.FORK_TUNE.get(), SoundSource.PLAYERS, 0.9F, pitch(ore));
            p.sendOverlayMessage(Component.literal("Tuned to " + ore.word()).withColor(ore.tint));
            return InteractionResult.SUCCESS_SERVER;
        }
        Ore tuned = tuned(fork);
        if (!rings(s)) return InteractionResult.PASS;
        if (tuned == null) {
            p.sendOverlayMessage(Component.literal("Touch it to an ore first: it takes that ore's note").withColor(0xC8C2B0));
            return InteractionResult.SUCCESS_SERVER;
        }
        if (p.getCooldowns().isOnCooldown(fork)) return InteractionResult.PASS;
        strike(level, p, fork, pos, tuned);
        fork.hurtAndBreak(1, p, ctx.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        return InteractionResult.SUCCESS_SERVER;
    }

    /** Strikes a fork tuned to {@code tuned} on the stone at {@code pos}: it rings, and the client is told where to sing toward. */
    static Optional<BlockPos> strike(ServerLevel level, ServerPlayer p, ItemStack fork, BlockPos pos, Ore tuned) {
        p.getCooldowns().addCooldown(fork, COOLDOWN);
        level.playSound(null, pos, DelveContent.FORK_STRIKE.get(), SoundSource.PLAYERS, 1.0F, pitch(tuned));
        int reach = RANGE + (int) dev.awaken.progression.Progression.perk(p, REACH_PERK);
        Optional<BlockPos> target = target(level, pos, tuned, reach);
        DelveNet.send(p, new DelveNet.ForkRing(tuned.ordinal(), target.isPresent(), target.map(BlockPos::asLong).orElse(0L)));
        dev.awaken.progression.Progression.count(p, "awaken:fork_strikes");
        return target;
    }

    /** The block of the nearest lode of {@code ore} within reach of where the fork was struck, if any. */
    public static Optional<BlockPos> target(ServerLevel level, BlockPos struck, Ore ore) {
        return target(level, struck, ore, RANGE);
    }

    /** As {@link #target(ServerLevel, BlockPos, Ore)}, within {@code reach} (a Delver's old pick carried adds to it). */
    public static Optional<BlockPos> target(ServerLevel level, BlockPos struck, Ore ore, int reach) {
        return Lodes.nearest(level, struck, ore, reach).filter(v -> !v.heartBroken() || v.size() > 1).map(Lodes.View::nearest);
    }

    /** A perk that lengthens the fork's reach (blocks; W5: the Delver's old pick, carried). */
    public static final String REACH_PERK = "awaken:fork_reach";

    /** Stone rings it: overworld rock, deepslate and their kin, and ore. */
    static boolean rings(BlockState s) {
        return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.STONE_ORE_REPLACEABLES) || s.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)
                || s.is(net.minecraft.world.level.block.Blocks.COBBLESTONE) || s.is(net.minecraft.world.level.block.Blocks.COBBLED_DEEPSLATE);
    }

    public static Ore tuned(ItemStack fork) {
        String id = fork.get(DelveContent.TUNED.get());
        return id == null ? null : Ore.byId(id);
    }

    /** Each ore's own note: coal lowest, Deepglass highest. */
    public static float pitch(Ore o) {
        return 0.7F + 0.08F * o.ordinal();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        Ore o = tuned(stack);
        out.accept(o == null ? Component.literal("Not tuned: touch it to an ore").withStyle(ChatFormatting.GRAY)
                : Component.literal("Tuned to " + o.word()).withColor(o.tint));
        out.accept(Component.literal("Strike it on stone: it sings toward a lode within " + RANGE).withStyle(ChatFormatting.DARK_GRAY));
    }
}
