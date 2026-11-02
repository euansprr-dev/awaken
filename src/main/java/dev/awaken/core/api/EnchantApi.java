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

    /**
     * The enchantment light on our own weapons and armour (SPEC §45.11), read by arms' and armour's renderers each frame:
     * 7 bits for bits 8-14 of their packed overlay's first half (a palette index in the low 4, 0 none; how brightly it
     * shows, 0-7, in the high 3), cycling through the piece's enchantments so each lights in turn. Installed by the
     * enchant client; 0 (nothing) without it.
     */
    public interface Glow {
        int bits(ItemStack stack);
    }

    private static Glow glow = s -> 0;

    public static void glow(Glow g) {
        glow = g;
    }

    public static int glowBits(ItemStack stack) {
        return stack.isEmpty() ? 0 : glow.bits(stack) & 127;
    }

    /**
     * A Precursor Word Stone for a ruin's builder (SPEC §45.14): its carved face toward {@code facing}, its enchantment
     * drawn from the stone's set ("sea" for the ocean lab, "sky" for the sky platform and the spire's crown, "" for the
     * vaults and the monolith) by the structure's seed. False when enchanting is absent or nothing could be placed.
     */
    public interface Stones {
        boolean place(net.minecraft.world.level.LevelAccessor level, net.minecraft.core.BlockPos pos, net.minecraft.core.Direction facing,
                      String set, long seed);
    }

    private static Stones stones = (level, pos, facing, set, seed) -> false;

    public static void stones(Stones s) {
        stones = s;
    }

    public static boolean placeStone(net.minecraft.world.level.LevelAccessor level, net.minecraft.core.BlockPos pos,
                                     net.minecraft.core.Direction facing, String set, long seed) {
        return stones.place(level, pos, facing, set, seed);
    }

    /** How many pages a market shows each buyer. */
    public static final int MARKET_SHOWN = 3;
}
