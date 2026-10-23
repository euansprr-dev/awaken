package dev.awaken.above.power;

import dev.awaken.above.AboveComponents;
import dev.awaken.above.AboveItems;
import dev.awaken.above.machine.MachineBlockEntity;
import dev.awaken.above.machine.Machines;
import dev.awaken.core.api.CoreApi;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * Stores essence. Use it to feed it essence shards from your inventory (sneak to fill it). Frames draw from the cells
 * you carry; reactors accept them as fuel.
 */
public class EssenceCellItem extends Item {
    public static final int CAPACITY = 1000;
    /** Essence in one essence shard. */
    public static final int PER_SHARD = 100;

    public EssenceCellItem(Item.Properties p) {
        super(p);
    }

    // ---- pure charge maths (GameTested) ---------------------------------------------------------------------------

    public static int charge(ItemStack stack) {
        return stack.getOrDefault(AboveComponents.ESSENCE.get(), 0);
    }

    public static void setCharge(ItemStack stack, int value) {
        stack.set(AboveComponents.ESSENCE.get(), Math.max(0, Math.min(CAPACITY, value)));
    }

    /** Adds up to {@code amount}; returns how much was accepted. */
    public static int fill(ItemStack stack, int amount) {
        int c = charge(stack);
        int add = Math.max(0, Math.min(amount, CAPACITY - c));
        if (add > 0) setCharge(stack, c + add);
        return add;
    }

    /** Removes up to {@code amount}; returns how much was drawn. */
    public static int drain(ItemStack stack, int amount) {
        int c = charge(stack);
        int take = Math.max(0, Math.min(amount, c));
        if (take > 0) setCharge(stack, c - take);
        return take;
    }

    /** How many shards would fit right now. */
    public static int shardsThatFit(ItemStack stack) {
        return (CAPACITY - charge(stack)) / PER_SHARD;
    }

    // ---- the player's carried cells --------------------------------------------------------------------------------

    public static boolean isCell(ItemStack stack) {
        return stack.is(AboveItems.CELL.get());
    }

    /** Total essence in every cell the player carries. */
    public static int carried(Player player) {
        Inventory inv = player.getInventory();
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (isCell(s)) total += charge(s);
        }
        return total;
    }

    /** Draws exactly {@code amount} from carried cells, or nothing if there isn't enough. */
    public static boolean drawCarried(Player player, int amount) {
        if (amount <= 0) return true;
        if (carried(player) < amount) return false;
        Inventory inv = player.getInventory();
        int left = amount;
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (isCell(s)) left -= drain(s, left);
        }
        return true;
    }

    // ---- item behaviour -------------------------------------------------------------------------------------------

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack cell = player.getItemInHand(hand);
        int fit = shardsThatFit(cell);
        if (fit <= 0) {
            if (player instanceof ServerPlayer sp) sp.sendOverlayMessage(Component.literal("Cell full · " + charge(cell) + " / " + CAPACITY));
            return InteractionResult.FAIL;
        }
        int want = player.isShiftKeyDown() ? fit : 1;
        int shardsHeld = player.getInventory().countItem(CoreApi.ESSENCE_SHARD.get());
        int used = Math.min(want, shardsHeld);
        if (used <= 0) {
            if (player instanceof ServerPlayer sp) sp.sendOverlayMessage(Component.literal("No essence shards to charge it with"));
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            int removed = removeShards(player, used);
            fill(cell, removed * PER_SHARD);
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.6f, 1.5f + 0.02f * charge(cell) / PER_SHARD);
            if (player instanceof ServerPlayer sp) sp.sendOverlayMessage(Component.literal("Essence " + charge(cell) + " / " + CAPACITY));
        }
        return InteractionResult.SUCCESS;
    }

    private static int removeShards(Player player, int count) {
        Inventory inv = player.getInventory();
        int left = count;
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(CoreApi.ESSENCE_SHARD.get())) {
                int n = Math.min(left, s.getCount());
                s.shrink(n);
                left -= n;
            }
        }
        return count - left;
    }

    /** Sneak-use on a reactor fills the cell from it (the reactor's own click handles emptying a cell into it). */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && context.getLevel().getBlockEntity(context.getClickedPos()) instanceof MachineBlockEntity be && be.isReactor()) {
            return Machines.feedReactor(player, be, context.getItemInHand());
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0f * charge(stack) / CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x7FC3FF;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.literal("Essence " + charge(stack) + " / " + CAPACITY).withColor(0x7FC3FF));
        out.accept(Component.literal("Use: feed it a shard · Sneak: fill it").withColor(0x8FA7C4));
    }
}
