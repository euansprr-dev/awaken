package dev.awaken.powers.content;

import dev.awaken.powers.Loadout;
import dev.awaken.powers.PowerRegistry;
import dev.awaken.powers.PowerType;
import dev.awaken.powers.Powers;
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
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/** A Power Core: use it to learn its power, or to level up a power you already know. */
public final class PowerCoreItem extends Item {
    public PowerCoreItem(Item.Properties props) {
        super(props);
    }

    public static @Nullable PowerType typeOf(ItemStack stack) {
        PowerCoreData d = stack.get(PowerContent.CORE_DATA.get());
        return d == null ? null : PowerRegistry.get(d.power());
    }

    public static int variantOf(ItemStack stack) {
        PowerCoreData d = stack.get(PowerContent.CORE_DATA.get());
        return d == null ? 0 : d.variant();
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        PowerType type = typeOf(stack);
        if (type == null) return InteractionResult.FAIL;
        if (player instanceof ServerPlayer sp) {
            if (!Powers.useCore(sp, type, variantOf(stack))) return InteractionResult.FAIL;
            if (!sp.getAbilities().instabuild) stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getName(ItemStack stack) {
        PowerType type = typeOf(stack);
        if (type == null) return super.getName(stack);
        int v = variantOf(stack);
        String variant = v == 0 ? "" : " (" + type.variant(v).name() + ")";
        return Component.translatable("item.awaken.power_core.named", type.name + variant).withColor(type.family.colour);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        PowerType type = typeOf(stack);
        return type != null && type.hidden;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        PowerType type = typeOf(stack);
        if (type == null) {
            out.accept(Component.literal("An empty core. Its light has gone out.").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        String slot = switch (type.slot) {
            case ACTIVE -> "Active";
            case ULTIMATE -> "Ultimate";
            case MOVEMENT -> "Movement";
            case PASSIVE -> "Passive";
        };
        out.accept(Component.literal(type.family.label + " · " + slot + (type.forces.isEmpty() ? "" : " · " + forces(type)))
                .withColor(type.family.colour));
        PowerType.Variant v = type.variant(variantOf(stack));
        if (variantOf(stack) != 0) out.accept(Component.literal(v.name() + ": " + v.blurb()).withStyle(ChatFormatting.GOLD));
        out.accept(Component.literal(type.description).withStyle(ChatFormatting.GRAY));
        Loadout l = Powers.clientLoadoutOrNull();
        Loadout.Owned owned = l == null ? null : l.get(type.id.toString());
        if (owned == null) {
            out.accept(Component.literal("Use to learn").withStyle(ChatFormatting.AQUA));
        } else if (owned.level() < Loadout.MAX_LEVEL) {
            out.accept(Component.literal("Known · use to level up (Lv " + owned.level() + " → " + (owned.level() + 1) + ")").withStyle(ChatFormatting.AQUA));
        } else {
            out.accept(Component.literal("Known at max level · use to swap to this variant").withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    private static String forces(PowerType type) {
        StringBuilder sb = new StringBuilder();
        for (var f : type.forces) {
            if (!sb.isEmpty()) sb.append('+');
            sb.append(f.name().charAt(0)).append(f.name().substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
