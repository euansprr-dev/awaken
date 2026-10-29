package dev.awaken.world.stilled;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/** What the Stilled carry (SPEC §43.3): a letter to read, a key whose tag names a place, a keepsake with a family's name. */
final class StilledItems {
    private StilledItems() {}

    /** A letter: opens to read (any time); reading it is kept for the bells' Book (stage 4). */
    static final class LetterItem extends Item {
        LetterItem(Item.Properties p) {
            super(p);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            ItemStack st = player.getItemInHand(hand);
            var d = st.get(StilledContent.LETTER.get());
            if (player instanceof ServerPlayer sp) {
                if (d != null) Stilled.readLetter(sp, d);
                sp.openItemGui(st, hand);
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
            var d = stack.get(StilledContent.LETTER.get());
            String from = d == null ? "" : StilledText.LETTERS[Math.floorMod(d.n(), StilledText.LETTERS.length)].from();
            out.accept(Component.literal(from.isEmpty() ? "Somebody carried this a long way." : "From " + from + ". Somebody carried this a long way.")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /** A key with a tag: reading it says the place aloud and Track points there; it opens that place's left chest. */
    static final class KeyItem extends Item {
        KeyItem(Item.Properties p) {
            super(p);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            ItemStack st = player.getItemInHand(hand);
            var d = st.get(StilledContent.KEY.get());
            if (player instanceof ServerPlayer sp && d != null) Stilled.readKey(sp, st, d);
            return InteractionResult.SUCCESS;
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
            var d = stack.get(StilledContent.KEY.get());
            String tag = d == null || d.name().isEmpty() ? "The tag says \"By the well.\"" : "The tag says \"" + d.name() + ", by the well.\"";
            out.accept(Component.literal(tag).withStyle(ChatFormatting.GRAY));
            out.accept(Component.literal("Use it to read the tag.").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** A keepsake: named for what it is; a Carillon family's name is scratched into it. */
    static final class KeepsakeItem extends Item {
        KeepsakeItem(Item.Properties p) {
            super(p);
        }

        @Override
        public Component getName(ItemStack stack) {
            var d = stack.get(StilledContent.KEEPSAKE.get());
            if (d == null) return super.getName(stack);
            return Component.literal(StilledText.cap(StilledText.KEEPSAKES[Math.floorMod(d.n(), StilledText.KEEPSAKES.length)].thing()));
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            var d = player.getItemInHand(hand).get(StilledContent.KEEPSAKE.get());
            if (player instanceof ServerPlayer sp && d != null) Stilled.readKeepsake(sp, d);
            return InteractionResult.SUCCESS;
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
            var d = stack.get(StilledContent.KEEPSAKE.get());
            if (d == null) return;
            out.accept(Component.literal(StilledText.family(d.family()) + " is scratched into it.").withStyle(ChatFormatting.GRAY));
            out.accept(Component.literal("Somebody in Carillon might know this name.").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
