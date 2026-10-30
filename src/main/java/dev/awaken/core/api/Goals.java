package dev.awaken.core.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The Tasks page's model (SPEC §27.13; Euan 2026-10-30: "core goals that then naturally make you know what you need to
 * do next"). A few standing goals, each a tab; every module adds the steps it knows for a player through
 * {@link #register}, each with a key, why it matters, what it needs (have/need, where each comes from is worked out by
 * the Tasks page) and where it's done. The Tasks module merges them with the Now suggestions ({@link NowHooks}), drops
 * anything already done, and picks the one step on top by the main path ({@link #PATH_FIRE} on). Server side; a source
 * runs at most a few times a second per player and returns nothing it has already seen met.
 */
public final class Goals {
    private Goals() {}

    /** The standing goals, in the tab order. The kin belong to the Hearth (Euan 2026-10-30). OUT is "out there": what's near you that belongs to no goal, shown on Now only. */
    public enum Goal {
        HEARTH("Hearth"), ARMOUR("Armour"), WEAPONS("Weapons"), BELLS("Bells"), RANK("Gates and rank"), OUT("Out there");

        public final String label;

        Goal(String label) {
            this.label = label;
        }
    }

    /** Not on the main path. */
    public static final int NO_PATH = Integer.MAX_VALUE;
    /**
     * The main path, in order (the top step on Now is the lowest present): your first power, your fire, a first room, the
     * Armoury, your first armour, your first gate (its Lord's Heart), the Anchor, the Forge, your rank's trial.
     */
    public static final int PATH_START = 0, PATH_FIRE = 10, PATH_SHELTER = 20, PATH_ARMOURY = 30, PATH_FIRST_ARMOUR = 40,
            PATH_FIRST_GATE = 50, PATH_ANCHOR = 55, PATH_FORGE = 60, PATH_TRIAL = 70;

    /**
     * One thing a step needs: an item ({@code item} an item id, or "#" and a tag id) counted in the pack, or a plain
     * condition ({@code item} empty: have 1 of 1 when it holds).
     */
    public record Need(String item, String label, int have, int need) {
        public boolean met() {
            return have >= need;
        }

        public static Need of(String label, boolean met) {
            return new Need("", label, met ? 1 : 0, 1);
        }

        /** {@code need} of an item, counted in the pack. */
        public static Need item(ServerPlayer p, Item item, int need) {
            return new Need(BuiltInRegistries.ITEM.getKey(item).toString(), item.getName(new ItemStack(item)).getString(), count(p.getInventory(), item), need);
        }

        /** {@code need} of anything in an item tag, counted in the pack; {@code label} names it ("Hide"). */
        public static Need tag(ServerPlayer p, TagKey<Item> tag, String label, int need) {
            return new Need("#" + tag.location(), label, count(p.getInventory(), tag), need);
        }

        /** The item this need counts, or empty for a condition or a tag. */
        public Optional<Item> asItem() {
            if (item.isEmpty() || item.startsWith("#")) return Optional.empty();
            Identifier id = Identifier.tryParse(item);
            return id == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(id);
        }

        /** The tag this need counts, or empty. */
        public Optional<TagKey<Item>> asTag() {
            if (!item.startsWith("#")) return Optional.empty();
            Identifier id = Identifier.tryParse(item.substring(1));
            return id == null ? Optional.empty() : Optional.of(TagKey.create(Registries.ITEM, id));
        }
    }

    /** A pick a step offers (the first armour's sets): its id, name, one line, the items that show it (a set's pieces) and what it needs. */
    public record Choice(String id, String title, String sub, List<String> show, List<Need> needs) {}

    /**
     * One step toward a goal. {@code key} is stable for the same underlying thing ("hearth/armoury"); two sources giving
     * the same key make one row. {@code why} is one plain line of why it matters ("Your first armour is made there").
     * {@code path} is its place on the main path ({@link #NO_PATH} when it has none); {@code optional} marks an upgrade
     * off the main path (a Sanctuary over a Bedroom), never the top step. {@code covers} lists Now suggestion ids (or id
     * prefixes ending in ':' or '/') this step stands for, so they don't show twice.
     */
    public record Step(String key, Goal goal, String title, String why, List<Need> needs, Optional<BlockPos> where, int path,
                       boolean optional, int priority, List<Choice> choices, List<String> covers) {
        public static Builder of(String key, Goal goal, String title) {
            return new Builder(key, goal, title);
        }

        /** Whether every need holds (the step is to be done now: "you have everything"). */
        public boolean ready() {
            for (Need n : needs) if (!n.met()) return false;
            return true;
        }
    }

    public static final class Builder {
        private final String key, title;
        private final Goal goal;
        private String why = "";
        private final List<Need> needs = new ArrayList<>();
        private Optional<BlockPos> where = Optional.empty();
        private int path = NO_PATH, priority = 50;
        private boolean optional;
        private final List<Choice> choices = new ArrayList<>();
        private final List<String> covers = new ArrayList<>();

        private Builder(String key, Goal goal, String title) {
            this.key = key;
            this.goal = goal;
            this.title = title;
        }

        public Builder why(String s) {
            why = s;
            return this;
        }

        public Builder need(Need n) {
            needs.add(n);
            return this;
        }

        public Builder needs(List<Need> n) {
            needs.addAll(n);
            return this;
        }

        public Builder at(BlockPos pos) {
            where = Optional.ofNullable(pos);
            return this;
        }

        public Builder path(int p) {
            path = p;
            return this;
        }

        public Builder priority(int p) {
            priority = p;
            return this;
        }

        public Builder optional() {
            optional = true;
            return this;
        }

        public Builder choice(Choice c) {
            choices.add(c);
            return this;
        }

        public Builder covers(String... ids) {
            covers.addAll(List.of(ids));
            return this;
        }

        public Step build() {
            return new Step(key, goal, title, why, List.copyOf(needs), where, path, optional, priority, List.copyOf(choices), List.copyOf(covers));
        }
    }

    @FunctionalInterface
    public interface Source {
        List<Step> steps(ServerPlayer p);
    }

    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();

    public static void register(Source source) {
        SOURCES.add(source);
    }

    /** Every step every source offers this player now, in registration order; a failing source is skipped with a warning. */
    public static List<Step> all(ServerPlayer p) {
        List<Step> out = new ArrayList<>();
        for (Source s : SOURCES) {
            try {
                out.addAll(s.steps(p));
            } catch (RuntimeException e) {
                dev.awaken.Awaken.LOG.warn("[goals] a step source failed: {}", e.toString());
            }
        }
        return out;
    }

    /** How many of an item the pack holds. */
    public static int count(Inventory inv, Item item) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    /** How many of anything in a tag the pack holds. */
    public static int count(Inventory inv, TagKey<Item> tag) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(tag)) n += s.getCount();
        }
        return n;
    }
}
