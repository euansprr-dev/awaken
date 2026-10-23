package dev.awaken.gates.run;

import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.PowerCoreHooks;
import dev.awaken.gates.content.Theme;
import dev.awaken.progression.Rank;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Gate loot, by rank and theme. Fortune doubles the rolls; loot bonus perks add a chance of an extra roll. */
public final class Loot {
    private Loot() {}

    private record Entry(Item item, int min, int max, int weight, int minRank) {}

    private static final List<Entry> TABLE = List.of(
            new Entry(Items.IRON_INGOT, 2, 6, 12, 0), new Entry(Items.GOLD_INGOT, 1, 4, 9, 0), new Entry(Items.ARROW, 8, 16, 7, 0),
            new Entry(Items.COOKED_BEEF, 3, 6, 8, 0), new Entry(Items.BREAD, 3, 6, 6, 0), new Entry(Items.EXPERIENCE_BOTTLE, 2, 5, 6, 0),
            new Entry(Items.EMERALD, 1, 4, 6, 1), new Entry(Items.GOLDEN_CARROT, 2, 5, 5, 1), new Entry(Items.ENDER_PEARL, 1, 3, 4, 1),
            new Entry(Items.DIAMOND, 1, 2, 4, 2), new Entry(Items.GOLDEN_APPLE, 1, 2, 3, 2), new Entry(Items.LAPIS_LAZULI, 4, 10, 4, 1),
            new Entry(Items.DIAMOND, 2, 4, 3, 4), new Entry(Items.NETHERITE_SCRAP, 1, 1, 2, 4), new Entry(Items.NETHERITE_SCRAP, 1, 2, 2, 5));

    static Item themeItem(Theme t) {
        return switch (t) {
            case BELL_CRYPT -> Items.BONE;
            case FUNGAL_HOLLOW -> Items.RED_MUSHROOM;
            case EMBER_MINES -> Items.COAL;
            case DROWNED_ARCHIVE -> Items.BOOK;
            case CLOCKWORK_FOUNDRY -> Items.COPPER_INGOT;
            case GLASS_DESERT -> Items.AMETHYST_SHARD;
            case HIVE -> Items.HONEYCOMB;
            case OVERGROWN_TEMPLE -> Items.MOSS_BLOCK;
            case LANTERN_NIGHT_MARKET -> Items.EMERALD;
            case FROST_MONASTERY -> Items.BLUE_ICE;
            case MIRROR_PALACE -> Items.QUARTZ;
            case PRECURSOR_LAB -> Items.REDSTONE;
            case NIGHTMARE -> Items.ENDER_PEARL;
            case SKY_ARCHIPELAGO -> Items.PHANTOM_MEMBRANE;
            case ABYSSAL_SHRINE -> Items.ECHO_SHARD;
        };
    }

    private static ItemStack rollOne(Rank rank, Theme theme, RandomSource r) {
        if (r.nextInt(6) == 0) return new ItemStack(themeItem(theme), 1 + r.nextInt(3 + rank.ordinal()));
        int tot = 0;
        for (Entry e : TABLE) if (e.minRank <= rank.ordinal()) tot += e.weight;
        int x = r.nextInt(Math.max(1, tot));
        for (Entry e : TABLE) {
            if (e.minRank > rank.ordinal()) continue;
            x -= e.weight;
            if (x < 0) return new ItemStack(e.item, e.min + r.nextInt(e.max - e.min + 1));
        }
        return new ItemStack(Items.IRON_INGOT, 2);
    }

    private static ItemStack shards(Rank rank, RandomSource r, int base) {
        return new ItemStack(CoreApi.ESSENCE_SHARD.get(), base + r.nextInt(3) + rank.ordinal());
    }

    /** A cache. */
    public static List<ItemStack> cache(Rank rank, Theme theme, RandomSource r, boolean fortune, double lootBonus) {
        List<ItemStack> out = new ArrayList<>();
        int rolls = 2 + rank.ordinal() / 2;
        if (r.nextDouble() < lootBonus) rolls++;
        if (fortune) rolls *= 2;
        for (int i = 0; i < rolls; i++) out.add(rollOne(rank, theme, r));
        out.add(shards(rank, r, 1));
        if (r.nextDouble() < 0.05) addCore(out, rank, r);
        return out;
    }

    /** What an elite leaves you (straight into the Satchel). */
    public static List<ItemStack> elite(Rank rank, Theme theme, RandomSource r, boolean fortune, boolean coreBias) {
        List<ItemStack> out = new ArrayList<>();
        out.add(shards(rank, r, 2));
        if (r.nextInt(3) == 0) out.add(rollOne(rank, theme, r));
        double core = 0.15 * (coreBias ? 1.5 : 1.0);
        if (r.nextDouble() < core) addCore(out, rank, r);
        if (fortune) out.add(rollOne(rank, theme, r));
        return out;
    }

    /** The Gate Lord's hoard, rolled per player. {@code pity} guarantees a Power Core. */
    public static List<ItemStack> boss(Rank rank, Theme theme, RandomSource r, boolean fortune, boolean pity, double lootBonus) {
        List<ItemStack> out = new ArrayList<>();
        out.add(new ItemStack(CoreApi.LORD_HEART.get()));
        out.add(shards(rank, r, 6 + rank.ordinal() * 2));
        int rolls = 3 + rank.ordinal() / 2 + (r.nextDouble() < lootBonus ? 1 : 0);
        if (fortune) rolls *= 2;
        for (int i = 0; i < rolls; i++) out.add(rollOne(rank, theme, r));
        out.add(new ItemStack(themeItem(theme), 2 + r.nextInt(4)));
        if (pity || r.nextDouble() < 0.5) addCore(out, rank, r);
        return out;
    }

    /** A broken gate's Lord pays better than the one inside. */
    public static List<ItemStack> breakLord(Rank rank, Theme theme, RandomSource r) {
        List<ItemStack> out = boss(rank, theme, r, false, false, 0.5);
        out.add(shards(rank, r, 8));
        out.add(rollOne(rank.above(), theme, r));
        out.add(rollOne(rank.above(), theme, r));
        return out;
    }

    /** The Treasure Gate's runaway hoard. */
    public static List<ItemStack> treasure(Rank rank, Theme theme, RandomSource r) {
        List<ItemStack> out = new ArrayList<>();
        out.add(new ItemStack(Items.EMERALD, 4 + r.nextInt(6) + rank.ordinal()));
        out.add(new ItemStack(Items.GOLD_INGOT, 3 + r.nextInt(5)));
        out.add(shards(rank, r, 4));
        for (int i = 0; i < 3; i++) out.add(rollOne(rank, theme, r));
        if (r.nextDouble() < 0.35) addCore(out, rank, r);
        return out;
    }

    private static void addCore(List<ItemStack> out, Rank rank, RandomSource r) {
        ItemStack core = PowerCoreHooks.roll(rank, r);
        if (!core.isEmpty()) out.add(core);
    }

    public static boolean isPowerCore(ItemStack s) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().equals("power_core");
    }
}
