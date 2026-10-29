package dev.awaken.armour;

import dev.awaken.core.api.NowHooks;
import dev.awaken.progression.Progression;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorType;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * The Now tracker's armour line (low priority, an idea; SPEC §44.4): "You can forge a Wildhide Vest at a Fitting Stand"
 * when the pack holds what a piece of a set your rank can forge takes, you don't own that piece or better, and it would
 * beat what you wear there. One line at most, worked out at most once a second per player.
 */
final class ArmourNow {
    private ArmourNow() {}

    static final int PRIORITY = 25;
    private static final int CACHE_TICKS = 20;
    /** What changes a fight most first. */
    private static final ArmorType[] ORDER = {ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.HELMET, ArmorType.BOOTS};

    static void register() {
        NowHooks.register(ArmourNow::suggest);
    }

    static List<NowHooks.Suggestion> suggest(ServerPlayer p) {
        if (!Progression.profile(p).awakened()) return List.of();
        ArmouryMemory mem = ArmouryMemory.of(p);
        long now = p.level().getGameTime();
        if (now >= mem.nowAt && now - mem.nowAt < CACHE_TICKS) return mem.now;
        mem.nowAt = now;
        ItemStack next = next(p);
        if (next == null) mem.now = List.of();
        else {
            ArmorType piece = ArmourLooks.of(next).piece();
            boolean plural = piece == ArmorType.LEGGINGS || piece == ArmorType.BOOTS;
            Component text = Component.literal(plural ? "You can forge " : "You can forge a ").append(next.getHoverName()).append(" at a Fitting Stand");
            mem.now = List.of(new NowHooks.Suggestion("armour:forge/" + ArmourLooks.of(next).set().id + "_" + ForgeSong.slot(piece), PRIORITY, text,
                    Optional.empty()));
        }
        return mem.now;
    }

    /** The piece to suggest, or null: the highest set first, then {@link #ORDER}. */
    static @Nullable ItemStack next(ServerPlayer p) {
        Inventory inv = p.getInventory();
        int[] owned = {-1, -1, -1, -1};
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ArmourLooks.Look look = ArmourLooks.of(inv.getItem(i));
            if (look != null) owned[ForgeSong.slot(look.piece())] = Math.max(owned[ForgeSong.slot(look.piece())], look.set().rank.ordinal());
        }
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ArmourLooks.Look look = ArmourLooks.of(p.getItemBySlot(slot));
            if (look != null) owned[ForgeSong.slot(look.piece())] = Math.max(owned[ForgeSong.slot(look.piece())], look.set().rank.ordinal());
        }
        int rank = Progression.rank(p).ordinal();
        ArmouryMemory mem = ArmouryMemory.of(p);
        ArmourSet[] sets = ArmourSet.values();
        for (int s = sets.length - 1; s >= 0; s--) {
            ArmourSet set = sets[s];
            if (set.rank.ordinal() > rank || !ArmourForge.known(mem, set)) continue;
            for (ArmorType piece : ORDER) {
                if (owned[ForgeSong.slot(piece)] >= set.rank.ordinal()) continue;
                List<ForgeSong.Need> cost = ForgeSong.cost(set, piece);
                if (cost == null || !afford(inv, cost)) continue;
                ItemStack made = ArmourForge.make(set, piece);
                if (made.isEmpty()) continue;
                EquipmentSlot slot = piece.getSlot();
                if (armour(made, slot) < armour(p.getItemBySlot(slot), slot)) continue;
                return made;
            }
        }
        return null;
    }

    private static boolean afford(Inventory inv, List<ForgeSong.Need> cost) {
        for (ForgeSong.Need n : cost) if (ArmourForge.count(inv, ArmourForge.matcher(n)) < n.count()) return false;
        return true;
    }

    private static double armour(ItemStack s, EquipmentSlot slot) {
        double sum = 0;
        for (ItemAttributeModifiers.Entry e : s.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers())
            if (e.attribute().value() == Attributes.ARMOR.value() && e.slot().test(slot)) sum += e.modifier().amount();
        return sum;
    }
}
