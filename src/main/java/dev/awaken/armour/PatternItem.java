package dev.awaken.armour;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * A set's pattern (SPEC §44.4, "Patterns"): carried by gate creatures of the set's rank (a Lord's own set by the Lord).
 * Using it learns the set for good; it's a drawing on hide, so anyone can carry it, trade it or give it away.
 */
public class PatternItem extends Item {
    public final ArmourSet set;

    public PatternItem(Item.Properties p, ArmourSet set) {
        super(p);
        this.set = set;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        ArmouryMemory mem = ArmouryMemory.of(sp);
        if (mem.patterns.contains(set.id)) {
            sp.sendSystemMessage(Component.literal("You know the " + set.setName + " pattern already. Someone else could use this one."), true);
            return InteractionResult.FAIL;
        }
        mem.patterns.add(set.id);
        ItemStack stack = player.getItemInHand(hand);
        stack.consume(1, player);
        level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1F, 0.8F);
        sp.sendSystemMessage(Component.literal("You learned the " + set.setName + " pattern. Its pieces are forged at a Fitting Stand.")
                .withColor(set.rank.colour), false);
        dev.awaken.progression.Progression.count(sp, ArmourPatterns.COUNTER);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.literal(set.rank.label + "-rank " + set.archetype.label + " armour").withColor(set.rank.colour));
        out.accept(Component.literal("Use it to learn the set, then forge it at a Fitting Stand").withColor(ForgedArmourItem.GREY));
    }
}
