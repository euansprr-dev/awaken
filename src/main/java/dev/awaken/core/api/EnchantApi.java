package dev.awaken.core.api;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * What the rest of the game may ask of enchanting (SPEC §45): the market's pages, whether a player could still learn
 * from a page, and a page of one enchantment (a reward, a Word Stone). Installed by the enchant module; before that, and
 * with it absent, there are no pages and nothing is learned.
 */
public final class EnchantApi {
    private EnchantApi() {}

    public interface Hooks {
        /** The day's pages a market sells, the same for everyone there (a few more than it shows: each buyer is shown the ones they don't know). */
        List<ItemStack> marketPages(ServerLevel level, long seed, long day);

        /** Whether this page would teach this player something. */
        boolean wanted(ServerPlayer player, ItemStack page);

        /** A page of one enchantment, with where it came from ("Bought at the market"); empty for an unknown one. */
        ItemStack page(Identifier enchantment, String where);
    }

    private static Hooks hooks;

    public static void install(Hooks h) {
        hooks = h;
    }

    public static List<ItemStack> marketPages(ServerLevel level, long seed, long day) {
        return hooks == null ? List.of() : hooks.marketPages(level, seed, day);
    }

    public static boolean wanted(ServerPlayer player, ItemStack page) {
        return hooks != null && hooks.wanted(player, page);
    }

    public static ItemStack page(Identifier enchantment, String where) {
        return hooks == null ? ItemStack.EMPTY : hooks.page(enchantment, where);
    }

    /** How many pages a market shows each buyer. */
    public static final int MARKET_SHOWN = 3;
}
