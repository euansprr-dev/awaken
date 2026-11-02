package dev.awaken.core.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What a forge says about the things it made (SPEC §45.5; agreed between the weapon forge, the armour and enchanting on
 * 2026-11-02, docs/enchanting/PLAN.md §8.1). Each forge registers a {@link Reader} for its own item component (arms for
 * {@code awaken:weapon_forged}, armour for {@code awaken:forged}); anyone may then ask how well a thing was forged and its
 * rank without reading another module's component. A forge fires {@link #forged} after every forge and reforge. Common:
 * both sides may read.
 */
public final class ForgeHooks {
    private ForgeHooks() {}

    /** One forge's reading of its own items. */
    public interface Reader {
        /** How well it was forged, 0-3 (Plain, Fine, Masterwork, Flawless); empty when this forge didn't make it. */
        OptionalInt quality(ItemStack stack);

        /** Its rank, "E" to "S", or null when this forge doesn't know it. */
        @Nullable String rank(ItemStack stack);

        /** An armour piece's archetype ("Bulwark", "Strider", "Channeler", "Duelist", "Lord-forged"), or null. */
        default @Nullable String archetype(ItemStack stack) {
            return null;
        }
    }

    /** After a forge or a reforge: who, the finished piece, its quality 0-3, and which station ("war_anvil", "fitting_stand"). */
    public interface Listener {
        void forged(ServerPlayer player, ItemStack stack, int quality, String station);
    }

    /** One line under a forge's result card ("Room for 3 enchantments"), or null for nothing. */
    public interface CardLine {
        @Nullable String line(ItemStack stack);
    }

    private static final List<Reader> READERS = new CopyOnWriteArrayList<>();
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<CardLine> CARD_LINES = new CopyOnWriteArrayList<>();

    public static void reader(Reader r) {
        READERS.add(r);
    }

    public static void onForged(Listener l) {
        LISTENERS.add(l);
    }

    public static void cardLine(CardLine c) {
        CARD_LINES.add(c);
    }

    /** How well this was forged (0-3), by the first forge that knows; empty if no forge made it. */
    public static OptionalInt quality(ItemStack stack) {
        if (stack.isEmpty()) return OptionalInt.empty();
        for (Reader r : READERS) {
            OptionalInt q = r.quality(stack);
            if (q.isPresent()) return OptionalInt.of(Math.clamp(q.getAsInt(), 0, 3));
        }
        return OptionalInt.empty();
    }

    /** Its rank "E" to "S", or null. */
    public static @Nullable String rank(ItemStack stack) {
        if (stack.isEmpty()) return null;
        for (Reader r : READERS) {
            String s = r.rank(stack);
            if (s != null) return s;
        }
        return null;
    }

    /** An armour piece's archetype label, or null. */
    public static @Nullable String archetype(ItemStack stack) {
        if (stack.isEmpty()) return null;
        for (Reader r : READERS) {
            String s = r.archetype(stack);
            if (s != null) return s;
        }
        return null;
    }

    /** A forge finished a piece. */
    public static void forged(ServerPlayer player, ItemStack stack, int quality, String station) {
        for (Listener l : LISTENERS) l.forged(player, stack, quality, station);
    }

    /** The lines to show under a result card for this piece (empty: none). */
    public static List<String> cardLines(ItemStack stack) {
        if (CARD_LINES.isEmpty()) return List.of();
        List<String> out = new java.util.ArrayList<>(1);
        for (CardLine c : CARD_LINES) {
            String s = c.line(stack);
            if (s != null && !s.isEmpty()) out.add(s);
        }
        return out;
    }
}
