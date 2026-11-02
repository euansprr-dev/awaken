package dev.awaken.enchant;

import dev.awaken.armour.ArmourItems;
import dev.awaken.armour.ArmourSet;
import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;

/** Enchanting's GameTests (SPEC §45): one per mechanism, prefix {@code enchant_}. */
final class EnchantTests {
    private EnchantTests() {}

    static void register() {
        AwakenTests.register("enchant_forged_sets_take", 20, EnchantTests::forgedSetsTake);
    }

    /** Every sculpted set's piece is in vanilla's enchantable tags for its slot, so the armour enchantments and Unbreaking fit it. */
    private static void forgedSetsTake(GameTestHelper h) {
        var reg = h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var unbreaking = reg.getOrThrow(Enchantments.UNBREAKING);
        var respiration = reg.getOrThrow(Enchantments.RESPIRATION);
        var feather = reg.getOrThrow(Enchantments.FEATHER_FALLING);
        int n = 0;
        for (ArmourSet set : ArmourSet.values()) {
            if (set.hunter != null || !set.made()) continue;
            for (var type : dev.awaken.armour.ForgeSong.PIECES) {
                Item item = ArmourItems.item(set, type);
                h.assertTrue(item != null, set.id + " " + type + " is an item");
                ItemStack s = new ItemStack(item);
                h.assertTrue(s.is(ItemTags.ARMOR_ENCHANTABLE), s + " is in #enchantable/armor");
                h.assertTrue(s.supportsEnchantment(unbreaking), s + " takes Unbreaking");
                EquipmentSlot slot = type.getSlot();
                if (slot == EquipmentSlot.HEAD) h.assertTrue(s.supportsEnchantment(respiration), s + " takes Respiration");
                if (slot == EquipmentSlot.FEET) h.assertTrue(s.supportsEnchantment(feather), s + " takes Feather Falling");
                if (slot != EquipmentSlot.HEAD) h.assertFalse(s.supportsEnchantment(respiration), s + " takes no Respiration off the head");
                n++;
            }
        }
        h.assertTrue(n == 92, "23 sets of four pieces, got " + n);
        h.succeed();
    }
}
