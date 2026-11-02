package dev.awaken.enchant;

import net.neoforged.bus.api.IEventBus;

/**
 * Enchanting (SPEC §45, docs/enchanting/PLAN.md): enchantments found as pages in the world, learned for good and written
 * onto your own gear at an Inscribing Table. Vanilla's enchantment engine stays (effects, tooltips, the item component);
 * how you get them, how you write them and what they may do are ours. S0: the sculpted armour sets take enchantments
 * (tags only, in {@code src/main/merge/enchant}).
 */
public final class EnchantModule {
    private EnchantModule() {}

    public static void init(IEventBus modBus) {
        EnchantTests.register();
    }
}
