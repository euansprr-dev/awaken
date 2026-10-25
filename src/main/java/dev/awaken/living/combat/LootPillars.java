package dev.awaken.living.combat;

import dev.awaken.Awaken;
import dev.awaken.living.LivingContent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * Rare drops stand in a light pillar of their colour until picked up (SPEC §21.1): RARE blue, EPIC violet, a Power Core
 * white-gold; {@code #awaken:loot_pillar} lifts anything else to at least blue.
 */
public final class LootPillars {
    private LootPillars() {}

    public static final TagKey<Item> PILLAR = TagKey.create(Registries.ITEM, Awaken.id("loot_pillar"));

    public static void onDrops(LivingDropsEvent e) {
        if (e.getEntity() instanceof Player || !(e.getSource().getEntity() instanceof Player)) return;
        for (ItemEntity drop : e.getDrops()) {
            int tier = tier(drop.getItem());
            if (tier > 0) drop.setData(LivingContent.LOOT_PILLAR.get(), tier);
        }
    }

    static int tier(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (path.equals("power_core")) return 3;
        Rarity r = stack.getRarity();
        if (r == Rarity.EPIC) return 2;
        if (r == Rarity.RARE || stack.is(PILLAR)) return 1;
        return 0;
    }
}
