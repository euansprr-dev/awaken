package dev.awaken.hearth.request;

import dev.awaken.progression.Rank;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The rules of Anchor requests, free of game state so they're tested directly: what can be asked of a player (only
 * what they can really get), how many, what a delivery pays, the streak and the town's regard (reputation).
 */
public final class RequestRules {
    private RequestRules() {}

    // ---- numbers (SPEC-worthy; docs/modules/requests.md) ------------------------------------------------------------
    /** Play-days a streak survives without a delivery. Ignoring or declining a request costs nothing. */
    public static final int STREAK_GAP = 3;
    public static final int STREAK_STEPS = 5;
    public static final double STREAK_STEP = 0.10;
    public static final int FORGE_CREDIT = 10, FORGE_CREDIT_BIG = 15, FORGE_CREDIT_CAP = 50;
    public static final double BIG_EFFORT = 12;
    public static final double LEVELS_PER_EFFORT = 0.02, LEVELS_CAP = 0.4;
    public static final double SHARD_PER_EFFORT = 0.04, SHARD_CAP = 0.6;
    public static final double RECIPE_CHANCE = 0.25;
    public static final double VALUED_ESSENCE = 1.25;
    /** Recipes up to this many levels ahead of the player can be taught early as a reward. */
    public static final int RECIPE_LOOKAHEAD = 6;

    /** What the player can do, for {@link #doable}. The server reads the real player; tests use a fixed set. */
    public interface Context {
        Rank rank();

        int level();

        boolean met(String token);
    }

    /** One request slot; a second at C-rank. */
    public static int slots(Rank rank) {
        return rank.ordinal() >= Rank.C.ordinal() ? 2 : 1;
    }

    /** Requirement tokens this module answers itself; anything else goes to progression's {@code Requirements}. */
    public static boolean ownToken(String token) {
        return token.startsWith("seen:") || token.startsWith("killed:") || token.startsWith("mined:") || token.startsWith("got:")
                || token.startsWith("depth:");
    }

    /** Whether a token is well formed (the content test checks every pool with this). */
    public static boolean known(String token) {
        if (token.startsWith("seen:")) return token.length() > 5 && !token.substring(5).contains(":");
        if (token.startsWith("killed:") || token.startsWith("mined:") || token.startsWith("got:")) {
            Identifier id = Identifier.tryParse(token.substring(token.indexOf(':') + 1));
            return id != null && token.indexOf(':', token.indexOf(':') + 1) > 0;
        }
        if (token.startsWith("depth:")) {
            try {
                Integer.parseInt(token.substring(6));
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return dev.awaken.progression.Requirements.known(token);
    }

    /** Whether the Anchor may ask this player for it: listed at their rank, every requirement met, one of any_of met. */
    public static boolean doable(RequestDef def, Context ctx) {
        if (def.countAt(ctx.rank()) <= 0 || def.weight() <= 0 || !resolves(def.target())) return false;
        for (String t : def.requires()) if (!ctx.met(t)) return false;
        if (def.anyOf().isEmpty()) return true;
        for (String t : def.anyOf()) if (ctx.met(t)) return true;
        return false;
    }

    public record Entry(String id, RequestDef def) {}

    /**
     * A weighted pick among the doable requests whose group isn't already asked for, avoiding the last few asked;
     * if only recent ones are left, they're allowed again. Empty when nothing at all is doable.
     */
    public static Optional<Entry> pick(Collection<Entry> pool, Context ctx, Set<String> busyGroups, List<String> recent, RandomSource rnd) {
        List<Entry> fresh = new ArrayList<>(), stale = new ArrayList<>();
        for (Entry e : pool) {
            if (!doable(e.def(), ctx) || busyGroups.contains(e.def().groupOr(e.id()))) continue;
            (recent.contains(e.id()) ? stale : fresh).add(e);
        }
        List<Entry> from = fresh.isEmpty() ? stale : fresh;
        if (from.isEmpty()) return Optional.empty();
        from.sort(java.util.Comparator.comparing(Entry::id));
        int total = 0;
        for (Entry e : from) total += e.def().weight();
        int roll = rnd.nextInt(Math.max(1, total));
        for (Entry e : from) {
            roll -= e.def().weight();
            if (roll < 0) return Optional.of(e);
        }
        return Optional.of(from.get(from.size() - 1));
    }

    public static ActiveRequest create(Entry e, Rank rank, String name, long day) {
        return new ActiveRequest(e.id(), e.def().target(), name, e.def().countAt(rank), 0, e.def().value(), day);
    }

    // ---- the streak -------------------------------------------------------------------------------------------------

    /**
     * The streak after a delivery today, counted in delivery days: one more if the last delivery was on an earlier day
     * within {@link #STREAK_GAP} play-days, the same on the same day, back to 1 after a longer gap.
     */
    public static int streakAfter(int streak, long lastDeliveryDay, long today) {
        if (streak <= 0 || lastDeliveryDay < 0 || today - lastDeliveryDay > STREAK_GAP) return 1;
        return today == lastDeliveryDay ? streak : streak + 1;
    }

    /** The streak as it stands today (0 once it has lapsed), for the Status screen and the offer text. */
    public static int liveStreak(int streak, long lastDeliveryDay, long today) {
        return lastDeliveryDay < 0 || today - lastDeliveryDay > STREAK_GAP ? 0 : streak;
    }

    /** Reward multiplier for a streak: +10% per delivery day in a row after the first, up to +50%. */
    public static double streakMultiplier(int streak) {
        return 1 + STREAK_STEP * Math.min(STREAK_STEPS, Math.max(0, streak - 1));
    }

    /** Streak days that also bring a Power Core Shard: 3, 5, then every fifth. */
    public static boolean milestone(int streak) {
        return streak == 3 || streak == 5 || (streak > 5 && streak % 5 == 0);
    }

    // ---- rewards ----------------------------------------------------------------------------------------------------

    /**
     * What one finished request pays. {@code levels} is a share of a level paid through progression's world XP
     * (diminishing returns per ~10 minutes, like kills and ore), so a burst of deliveries never outpaces playing.
     */
    public record Reward(int essence, double levels, int forgeCredit, int shards, boolean recipe, int reputation, int streak,
                         int streakBonusPercent) {}

    public static Reward reward(double effort, Rank rank, int streakAfter, int reputation, RandomSource rnd) {
        double m = streakMultiplier(streakAfter);
        boolean valued = Standing.of(reputation).ordinal() >= Standing.VALUED.ordinal();
        int essence = (int) Math.max(2, Math.round(effort * (0.6 + 0.2 * Math.min(rank.ordinal(), Rank.S.ordinal())) * m * (valued ? VALUED_ESSENCE : 1)));
        double levels = Math.min(LEVELS_CAP, LEVELS_PER_EFFORT * effort) * m;
        int forge = effort >= BIG_EFFORT ? FORGE_CREDIT_BIG : FORGE_CREDIT;
        int shards = (rnd.nextDouble() < Math.min(SHARD_CAP, SHARD_PER_EFFORT * effort) ? 1 : 0) + (milestone(streakAfter) ? 1 : 0);
        boolean recipe = rnd.nextDouble() < RECIPE_CHANCE;
        int rep = (int) Math.max(1, Math.round(effort / 2));
        return new Reward(essence, levels, forge, shards, recipe, rep, streakAfter, (int) Math.round((m - 1) * 100));
    }

    // ---- the town's regard ------------------------------------------------------------------------------------------

    /** Reputation tiers and the small town touch each one adds. */
    public enum Standing {
        NEWCOMER(0, "Newcomer", "The town is still getting to know you"),
        KNOWN(6, "Known", "Townsfolk gather to watch you bring a request home"),
        TRUSTED(18, "Trusted", "The town keeps the Anchor's lamps lit at night"),
        VALUED(40, "Valued", "The town chips in: +25% essence on every request"),
        BELOVED(80, "Beloved", "Each play-day the town leaves a gift at the Anchor");

        public final int at;
        public final String label;
        public final String touch;

        Standing(int at, String label, String touch) {
            this.at = at;
            this.label = label;
            this.touch = touch;
        }

        public static Standing of(int reputation) {
            Standing best = NEWCOMER;
            for (Standing s : values()) if (reputation >= s.at) best = s;
            return best;
        }

        public Standing next() {
            return this == BELOVED ? null : values()[ordinal() + 1];
        }
    }

    // ---- matching items ---------------------------------------------------------------------------------------------

    /** Shared by the server and (in single player) the client's Status screen, hence concurrent. */
    private static final Map<String, Predicate<ItemStack>> MATCHERS = new java.util.concurrent.ConcurrentHashMap<>();

    /** Whether a stack counts for a target ("awaken:mire_hide" or "#minecraft:logs"). Unknown targets match nothing. */
    public static boolean matches(String target, ItemStack stack) {
        if (stack.isEmpty()) return false;
        return MATCHERS.computeIfAbsent(target, RequestRules::matcher).test(stack);
    }

    private static Predicate<ItemStack> matcher(String target) {
        if (target.startsWith("#")) {
            Identifier id = Identifier.tryParse(target.substring(1));
            if (id == null) return s -> false;
            TagKey<Item> tag = TagKey.create(Registries.ITEM, id);
            return s -> s.is(tag);
        }
        Identifier id = Identifier.tryParse(target);
        Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
        if (item == null || item == Items.AIR) return s -> false;
        return s -> s.is(item);
    }

    /** Whether a target names something that exists right now (an item, or a tag with at least one item). */
    public static boolean resolves(String target) {
        if (target.startsWith("#")) {
            Identifier id = Identifier.tryParse(target.substring(1));
            return id != null && BuiltInRegistries.ITEM.get(TagKey.create(Registries.ITEM, id)).map(s -> s.size() > 0).orElse(false);
        }
        Identifier id = Identifier.tryParse(target);
        return id != null && BuiltInRegistries.ITEM.containsKey(id) && BuiltInRegistries.ITEM.getValue(id) != Items.AIR;
    }

    /** One item that shows a target (the item itself, or the tag's first). */
    public static ItemStack icon(String target) {
        if (target.startsWith("#")) {
            Identifier id = Identifier.tryParse(target.substring(1));
            if (id == null) return ItemStack.EMPTY;
            return BuiltInRegistries.ITEM.get(TagKey.create(Registries.ITEM, id))
                    .flatMap(s -> s.stream().findFirst()).map(h -> new ItemStack(h.value())).orElse(ItemStack.EMPTY);
        }
        Identifier id = Identifier.tryParse(target);
        return id == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.getValue(id));
    }

    /** Slots a delivery takes from: the 36 inventory slots and the off hand (never worn armour). */
    public static boolean carriedSlot(int slot) {
        return slot < Inventory.INVENTORY_SIZE || slot == Inventory.SLOT_OFFHAND;
    }

    /** How many of a target the player carries (inventory and off hand). */
    public static int carried(Player p, String target) {
        Inventory inv = p.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (!carriedSlot(i)) continue;
            ItemStack s = inv.getItem(i);
            if (matches(target, s)) n += s.getCount();
        }
        return n;
    }

    /** Clears the matcher cache (tags change on /reload). */
    static void forgetMatchers() {
        MATCHERS.clear();
    }
}
