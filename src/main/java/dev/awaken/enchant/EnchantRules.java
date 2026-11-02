package dev.awaken.enchant;

import dev.awaken.core.api.ArmsApi;
import dev.awaken.core.api.ForgeHooks;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;

import java.util.OptionalInt;

/**
 * Room, depth and price (SPEC §45.5): how many enchantments a piece holds (from how well it was forged), how deep each
 * can go (from its rank, two at most outside your Study, one deeper for its archetype's kind), and what writing one costs.
 * Common: the book shows the same numbers the server charges.
 */
public final class EnchantRules {
    private EnchantRules() {}

    /** The depth anything reaches outside a Study. */
    public static final int OUTSIDE_STUDY = 2;
    /** Levels and lapis per level of depth; Mending and Silk Touch's single level. */
    public static final int LEVELS_PER = 3, LAPIS_PER = 1, DEAR_LEVELS = 10, DEAR_LAPIS = 4;

    private static final String PICK = "awaken:waking_pick";

    // ---- room ---------------------------------------------------------------------------------------------------------

    /**
     * How many enchantments the piece holds: forged pieces by quality (Plain 1 to Flawless 4), found weapons 3, our forged
     * weapons 2 until the War Anvil gives them a quality, bows, crossbows, tridents and rods 2, everything else 1.
     */
    public static int room(ItemStack s) {
        if (s.isEmpty()) return 0;
        OptionalInt q = ForgeHooks.quality(s);
        if (q.isPresent()) return q.getAsInt() + 1;
        if (ArmsApi.found(s)) return 3;
        if (s.is(ItemTags.BOW_ENCHANTABLE) || s.is(ItemTags.CROSSBOW_ENCHANTABLE) || s.is(ItemTags.TRIDENT_ENCHANTABLE)
                || s.is(ItemTags.FISHING_ENCHANTABLE)) return 2;
        if (ForgeHooks.rank(s) != null && !armour(s)) return 2;
        return 1;
    }

    /** How many enchantments are on it now. */
    public static int used(ItemStack s) {
        return s.getEnchantments().size();
    }

    public static boolean armour(ItemStack s) {
        var eq = s.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
        return eq != null && eq.slot().isArmor();
    }

    // ---- depth --------------------------------------------------------------------------------------------------------

    /** The piece's rank for depth: its forge's rank, else its material, else its rarity. */
    public static String rank(ItemStack s) {
        String r = ForgeHooks.rank(s);
        if (r != null) return r;
        String path = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
        if (path.startsWith("netherite_")) return "A";
        if (path.startsWith("diamond_")) return "C";
        if (path.startsWith("iron_")) return "D";
        if (path.startsWith("leather_") || path.startsWith("golden_") || path.startsWith("chainmail_") || path.startsWith("copper_")
                || path.startsWith("wooden_") || path.startsWith("stone_") || path.startsWith("turtle_")) return "E";
        if (s.is(ItemTags.BOW_ENCHANTABLE) || s.is(ItemTags.CROSSBOW_ENCHANTABLE) || s.is(ItemTags.TRIDENT_ENCHANTABLE)
                || s.is(ItemTags.FISHING_ENCHANTABLE) || s.is(ItemTags.MACE_ENCHANTABLE)) return "C";
        Rarity rarity = s.getRarity();
        if (rarity == Rarity.EPIC) return "B";
        if (rarity == Rarity.RARE) return "C";
        return rarity == Rarity.UNCOMMON ? "D" : "E";
    }

    /** The depth a rank allows before the top: E I, D II, C III, B and up the enchantment's top. */
    public static int depth(String rank) {
        return switch (rank) {
            case "E" -> 1;
            case "D" -> 2;
            case "C" -> 3;
            default -> 99;
        };
    }

    /** Whether the piece's archetype writes this enchantment one deeper (Bulwark wards, Strider carrying, Channeler keeping, Lord-forged any one). */
    static boolean affinity(ItemStack s, Inscriptions.Entry e) {
        String a = ForgeHooks.archetype(s);
        if (a == null) return false;
        return switch (a) {
            case "Bulwark" -> e.family() == Family.WARD;
            case "Strider" -> e.family() == Family.CARRYING;
            case "Channeler" -> e.family() == Family.KEEPING;
            case "Lord-forged" -> !otherPastDepth(s, e);
            default -> false;
        };
    }

    /** A Lord-forged piece's one deeper enchantment: whether another one already took it. */
    private static boolean otherPastDepth(ItemStack s, Inscriptions.Entry e) {
        int d = depth(rank(s));
        for (Object2IntMap.Entry<Holder<Enchantment>> x : s.getEnchantments().entrySet()) {
            Inscriptions.Entry o = Inscriptions.of(x.getKey());
            if (o != null && o != e && x.getIntValue() > d) return true;
        }
        return false;
    }

    /** The deepest this enchantment can be written on this piece, with or without the Study; never past its top. */
    public static int deepest(ItemStack s, Holder<Enchantment> h, Inscriptions.Entry e, boolean study) {
        int top = Inscriptions.top(h);
        int d = Math.min(top, depth(rank(s)) + (affinity(s, e) ? 1 : 0));
        if (!study) d = Math.min(d, OUTSIDE_STUDY);
        return Math.max(1, Math.min(d, top));
    }

    /** The deepest it could go with the Study (to say "deeper than II needs your Study"). */
    public static int deepestAnywhere(ItemStack s, Holder<Enchantment> h, Inscriptions.Entry e) {
        return deepest(s, h, e, true);
    }

    // ---- price --------------------------------------------------------------------------------------------------------

    /** What going from {@code from} (0: not on it) to {@code to} costs: {levels, lapis}. */
    public static int[] price(Inscriptions.Entry e, int from, int to) {
        if (to <= from) return new int[]{0, 0};
        if (e.dear()) return new int[]{DEAR_LEVELS, DEAR_LAPIS};
        int n = to - from;
        return new int[]{LEVELS_PER * n, LAPIS_PER * n};
    }

    /** A clean hand's refund: a third of the levels. */
    public static int refund(int levels) {
        return levels / 3;
    }

    /** "6 levels and 2 lapis". */
    public static String priceLine(int[] p) {
        if (p[0] == 0 && p[1] == 0) return "Free";
        return p[0] + (p[0] == 1 ? " level" : " levels") + " and " + p[1] + " lapis";
    }

    // ---- what stops it ------------------------------------------------------------------------------------------------

    /** The level this enchantment has on the piece now (0: none). */
    public static int level(ItemStack s, Holder<Enchantment> h) {
        return s.getEnchantments().getLevel(h);
    }

    /**
     * Why this enchantment can't be written on this piece at {@code to}, in plain words, or null when it can. {@code to}
     * 0 asks about the next level up.
     */
    public static @Nullable String why(ItemStack s, Holder<Enchantment> h, int to, boolean study) {
        Inscriptions.Entry e = Inscriptions.of(h);
        if (e == null) return "It can't be written any more.";
        if (!fits(s, h)) return "It doesn't fit this.";
        ItemEnchantments on = s.getEnchantments();
        int now = on.getLevel(h);
        if (now == 0) {
            for (Holder<Enchantment> other : on.keySet()) {
                if (!Enchantment.areCompatible(other, h))
                    return "It can't go with " + other.value().description().getString() + ".";
            }
            if (used(s) >= room(s)) return used(s) > room(s) ? "Full: scrape one off first." : "No room left: scrape one off first.";
        }
        int deepest = deepest(s, h, e, study);
        int want = to <= 0 ? now + 1 : to;
        if (now >= Inscriptions.top(h)) return "It's as deep as it goes.";
        if (want > deepest) {
            if (!study && want <= deepestAnywhere(s, h, e)) return "Deeper than II needs your Study.";
            return now >= deepest ? "It's as deep as this piece allows." : "This piece takes it to " + Inscriptions.numeral(deepest) + " at most.";
        }
        return null;
    }

    /** Whether it goes on this kind of piece at all: vanilla's own fit, and never Efficiency on the Delver's Pick (its verses are its speed). */
    public static boolean fits(ItemStack s, Holder<Enchantment> h) {
        if (s.isEmpty() || !s.supportsEnchantment(h)) return false;
        if (h.is(net.minecraft.world.item.enchantment.Enchantments.EFFICIENCY) && BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(PICK)) return false;
        return true;
    }

    /** "Room for two more", "No room left", "Full". */
    public static String roomLine(ItemStack s) {
        int room = room(s), used = used(s), free = room - used;
        if (free > 0) {
            if (used == 0) return "Room for " + count(room) + (room == 1 ? " enchantment" : " enchantments");
            return "Room for " + count(free) + " more";
        }
        return used > room ? "Full" : "No room left";
    }

    public static String count(int n) {
        return switch (n) {
            case 1 -> "one";
            case 2 -> "two";
            case 3 -> "three";
            case 4 -> "four";
            default -> String.valueOf(n);
        };
    }
}
