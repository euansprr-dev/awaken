package dev.awaken.enchant;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.GrindstoneEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Vanilla enchanting folded into ours (SPEC §45.8). Any vanilla enchanting table you use turns into an Inscribing Table
 * in place, with a flare, and opens. The anvil repairs and renames but never adds or merges enchantments; the grindstone
 * strips them and gives back a third of the experience it used to.
 */
final class Fold {
    private Fold() {}

    /** An old enchanting table, used: it becomes an Inscribing Table, facing you, and opens as one. */
    static void onUseBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!e.getLevel().getBlockState(e.getPos()).is(Blocks.ENCHANTING_TABLE)) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (!(e.getLevel() instanceof ServerLevel level) || !(e.getEntity() instanceof ServerPlayer p) || e.getHand() != InteractionHand.MAIN_HAND) return;
        swap(level, e.getPos(), p);
        if (level.getBlockEntity(e.getPos()) instanceof InscribingTableBlockEntity be && !p.getMainHandItem().isEmpty()
                && !(p.getMainHandItem().getItem() instanceof PageItem)) Inscribe.open(p, be);
    }

    /** Turns the vanilla table at {@code pos} into an Inscribing Table facing the player. */
    static void swap(ServerLevel level, BlockPos pos, ServerPlayer p) {
        BlockState state = EnchantContent.TABLE.get().defaultBlockState()
                .setValue(InscribingTableBlock.FACING, p.getDirection().getOpposite());
        level.setBlock(pos, state, Block.UPDATE_ALL);
        EnchantFx.flare(level, pos);
    }

    /** The anvil: no enchanted books on the right; two of a kind repair, but the left keeps only its own enchantments. */
    static void onAnvil(AnvilUpdateEvent e) {
        ItemStack right = e.getRight();
        if (right.isEmpty()) return;
        if (right.is(Items.ENCHANTED_BOOK) || !right.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty()) {
            e.setCanceled(true);
            return;
        }
        ItemStack out = e.getOutput();
        if (out.isEmpty() || right.getEnchantments().isEmpty()) return;
        ItemStack fixed = out.copy();
        EnchantmentHelper.setEnchantments(fixed, e.getLeft().getEnchantments());
        e.setOutput(fixed);
    }

    /** The grindstone gives back a third of what it would. */
    static void onGrindstoneTake(GrindstoneEvent.OnTakeItem e) {
        if (e.getXp() > 0) e.setXp(e.getXp() / 3);
    }
}
