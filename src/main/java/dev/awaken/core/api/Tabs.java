package dev.awaken.core.api;

import dev.awaken.Awaken;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Awaken's creative menu (SPEC §1, "Creative tabs"): ten tabs, owned and laid out here, each a run of shelves in the
 * order a player browses. A module never registers a tab of its own: it fills its shelves ({@link #fill}) with its items
 * in their order (sets together, tiers in order). Every Awaken item sits in exactly one tab, and JEI lists exactly what
 * the tabs hold, so an item in no tab can't be found there at all. An item no module placed still lands on the last
 * shelf of Materials (so JEI keeps it) and the check names it ({@link #audit}: {@code /awaken core tabs}, GameTest
 * {@code core_creative_tabs}). A filler that throws loses only its own shelf, and a stack placed twice is kept once.
 */
public final class Tabs {
    private Tabs() {}

    /** The tabs, in the menu's order (they follow vanilla's, on the menu's second page). */
    public enum Tab {
        WEAPONS("weapons", Items.IRON_SWORD),
        ARMOUR("armour", Items.IRON_CHESTPLATE),
        TOOLS("tools", Items.IRON_PICKAXE),
        STATIONS("stations", Items.CRAFTING_TABLE),
        MATERIALS("materials", Items.AMETHYST_SHARD),
        BUILDING("building", Items.STONE_BRICKS),
        GATE_WORLDS("gate_worlds", Items.CRYING_OBSIDIAN),
        NATURE("nature", Items.OAK_SAPLING),
        RELICS("relics", Items.BELL),
        CREATURES("creatures", Items.ALLAY_SPAWN_EGG);

        public final String id;
        private final Item fallbackIcon;
        private Supplier<ItemStack> icon;

        Tab(String id, Item fallbackIcon) {
            this.id = id;
            this.fallbackIcon = fallbackIcon;
        }

        public ResourceKey<CreativeModeTab> key() {
            return ResourceKey.create(Registries.CREATIVE_MODE_TAB, Awaken.id(id));
        }

        /** The lang key of its title ("Awaken Weapons"). */
        public String titleKey() {
            return "itemGroup.awaken." + id;
        }

        private ItemStack iconStack() {
            ItemStack s = icon == null ? ItemStack.EMPTY : icon.get();
            return s.isEmpty() ? new ItemStack(fallbackIcon) : s;
        }
    }

    /** A tab's shelves, in browsing order: each says what goes on it, and the modules that own those things fill it. */
    public enum Shelf {
        // Weapons
        /** Arms' forged weapons: a style at a time, tiers E to S; the Lords' own weapons after. */
        FORGED_WEAPONS(Tab.WEAPONS),
        /** The Hunter sets' blades and foci, tier by tier. */
        HUNTER_WEAPONS(Tab.WEAPONS),
        /** The weapon Blueprints, one a style. */
        WEAPON_BLUEPRINTS(Tab.WEAPONS),

        // Armour
        /** The forged armour sets, a set at a time (helmet to boots), each with its pattern. */
        FORGED_ARMOUR(Tab.ARMOUR),
        /** The Hunter sets' armour, tier by tier. */
        HUNTER_ARMOUR(Tab.ARMOUR),
        /** The Precursor Frame (the exosuit) and its modules. */
        FRAMES(Tab.ARMOUR),

        // Tools and gear
        /** Picks: the Waking Pick, the Delver's old head and the forged picks form by form (tiers in order), the Delver's pick. */
        PICKS(Tab.TOOLS),
        /** The Hunter sets' mattocks, tier by tier. */
        HUNTER_TOOLS(Tab.TOOLS),
        /** Rope, its fittings, pitons and runner rope. */
        CLIMBING(Tab.TOOLS),
        /** Things you carry and use: binoculars, the band, lamps, the lode map, the Burin, the Mallet, the handbell... */
        GEAR(Tab.TOOLS),

        // Stations and machines
        /** The Hearth and what stands in it: the Anchor, the Ember, the mat, the Forge, the trophy mount, the pads. */
        HEARTH(Tab.STATIONS),
        /** The workshops and stands: the War Anvil, the Inscribing Table, the Fitting and pattern stands. */
        HOME_STATIONS(Tab.STATIONS),
        /** The hooks your bells hang on. */
        BELL_HOOKS(Tab.STATIONS),
        /** The beasts' works. */
        KIN_WORKS(Tab.STATIONS),
        /** The Precursor machines and what goes with them. */
        MACHINES(Tab.STATIONS),
        /** Courses and the movement blocks: trial gates, rope rings, updraft vents, spire crowns, wing cores. */
        COURSES(Tab.STATIONS),

        // Materials
        /** Essence, Lord Hearts, dream residue, core shards. */
        ESSENCE(Tab.MATERIALS),
        /** A power core of every power. */
        POWER_CORES(Tab.MATERIALS),
        /** An enchantment page of every inscription. */
        PAGES(Tab.MATERIALS),
        /** Precursor parts: scrap, circuits, alloy, cells. */
        PRECURSOR_PARTS(Tab.MATERIALS),
        /** The deep's metals: starmetal and bell bronze. */
        METALS(Tab.MATERIALS),
        /** What the gates' creatures and their Lords leave, and the Hearth's trophies. */
        BOSS_TROPHIES(Tab.MATERIALS),
        /** What the wild's beasts leave. */
        BEAST_PARTS(Tab.MATERIALS),
        /** Never filled by a module: Awaken items no shelf placed land here, and the check names them. */
        UNSORTED(Tab.MATERIALS),

        // Building blocks
        /** Precursor plating, panels, glass and ruin blocks. */
        PRECURSOR_BLOCKS(Tab.BUILDING),
        /** The old world's monument stone and the finds' pieces. */
        MONUMENTS(Tab.BUILDING),
        /** Things for the outside of a home: drainpipes, window boxes, trellises. */
        HOME_KIT(Tab.BUILDING),
        /** The Descent's strata. */
        DESCENT_BLOCKS(Tab.BUILDING),
        /** The Veil's and the Stillpoint's blocks. */
        VEIL_BLOCKS(Tab.BUILDING),
        /** Lucidium's dream blocks. */
        DREAM_BLOCKS(Tab.BUILDING),

        // Gate worlds
        /** A gate's own pieces: frames, shrines, seals, caches, set pieces, the rig. */
        GATE_PIECES(Tab.GATE_WORLDS),
        /** Every gate world's blocks, a world at a time, each block with its slabs and stairs. */
        GATE_WORLD_BLOCKS(Tab.GATE_WORLDS),

        // Nature and food
        /** The Awakened Lands' trees, stone and crystal, a land at a time. */
        LANDS(Tab.NATURE),
        /** Ground cover, wild plants and soft slabs. */
        GROUND(Tab.NATURE),
        /** Deepglass and the lode hearts, then the caves' rock, rubble, growths and ores. */
        CAVES(Tab.NATURE),
        /** Herbs, berries and the meals made from them. */
        HERBS_AND_MEALS(Tab.NATURE),

        // Relics and keepsakes
        /** Relics: the finds' and the wayfarers'. */
        RELICS(Tab.RELICS),
        /** The hundred bells, the Great Belfry's two needs, a village's bell. */
        BELLS(Tab.RELICS),
        /** Keepsakes, letters, keys and logs: the Hunter's Licence, the inn key, the Delver's logs... */
        KEEPSAKES(Tab.RELICS),
        /** The townsfolk's things: what they carry at work. */
        TOWNSFOLK(Tab.RELICS),

        // Creatures
        /** The gates' creatures, bosses and the story's. */
        GATE_CREATURES(Tab.CREATURES),
        /** The wild's beasts. */
        WILD_CREATURES(Tab.CREATURES);

        public final Tab tab;

        Shelf(Tab tab) {
            this.tab = tab;
        }
    }

    // ---- the modules' side -----------------------------------------------------------------------------------------

    private static final Map<Shelf, List<CreativeModeTab.DisplayItemsGenerator>> FILLERS = new EnumMap<>(Shelf.class);
    private static final List<Variant> VARIANTS = new ArrayList<>();

    /** One item shown in several kinds, and what tells its kinds apart (JEI keeps each kind; SPEC §14). */
    private record Variant(Supplier<? extends Item> item, Function<ItemStack, @Nullable Object> kind) {}

    /**
     * Puts things on a shelf. The filler runs each time the menu is made (joining a world, a permission or feature
     * change), with the menu's parameters (registries for enchantments and the like); it accepts stacks of one. Several
     * fillers of one shelf run in the order they were added.
     */
    public static synchronized void fill(Shelf shelf, CreativeModeTab.DisplayItemsGenerator filler) {
        FILLERS.computeIfAbsent(shelf, k -> new ArrayList<>()).add(filler);
    }

    /** Puts these items on a shelf, in this order (the collection is read each time, so it may be filled later). */
    public static void fill(Shelf shelf, Collection<? extends ItemLike> items) {
        fill(shelf, (p, out) -> {
            for (ItemLike i : items) out.accept(i);
        });
    }

    /** Puts these items on a shelf, in this order. */
    public static void fill(Shelf shelf, ItemLike... items) {
        fill(shelf, List.of(items));
    }

    /**
     * Says an item comes in kinds the menu shows one by one (a page of each inscription, a bell of each number), told
     * apart by these components. JEI then lists each kind instead of folding them into one; the check fails on an item
     * shown in several kinds that wasn't declared, or whose kinds these components don't tell apart.
     */
    @SafeVarargs
    public static void variants(Supplier<? extends Item> item, Supplier<? extends DataComponentType<?>>... by) {
        List<Supplier<? extends DataComponentType<?>>> types = List.of(by);
        variants(item, stack -> {
            List<Object> key = new ArrayList<>(types.size());
            for (var t : types) key.add(stack.get(t.get()));
            return key;
        });
    }

    /** The same, when the kinds are told apart by part of a component (a power core by its power, not its variant). */
    public static synchronized void variants(Supplier<? extends Item> item, Function<ItemStack, @Nullable Object> kind) {
        VARIANTS.add(new Variant(item, kind));
    }

    /** The declared kinds: each item and what tells its kinds apart (for JEI's plugin: equal answers, one entry). */
    public static synchronized Map<Item, Function<ItemStack, @Nullable Object>> variants() {
        Map<Item, Function<ItemStack, @Nullable Object>> out = new LinkedHashMap<>();
        for (Variant v : VARIANTS) out.put(v.item().get(), v.kind());
        return out;
    }

    // ---- core's side: the tabs themselves ----------------------------------------------------------------------------

    private static final Map<Tab, DeferredHolder<CreativeModeTab, CreativeModeTab>> HOLDERS = new EnumMap<>(Tab.class);

    /** Registers the ten tabs, each after the one before (the first after vanilla's last). Core calls this once. */
    public static void register(DeferredRegister<CreativeModeTab> tabs) {
        ResourceKey<CreativeModeTab> before = CreativeModeTabs.SPAWN_EGGS;
        for (Tab t : Tab.values()) {
            ResourceKey<CreativeModeTab> after = before;
            HOLDERS.put(t, tabs.register(t.id, () -> CreativeModeTab.builder()
                    .title(Component.translatable(t.titleKey()))
                    .icon(t::iconStack)
                    .displayItems((p, out) -> build(t, p, out))
                    .withTabsBefore(after)
                    .build()));
            before = t.key();
        }
    }

    /** The tab's icon: a stack of one of its own things, set by the module that owns it. */
    public static void icon(Tab tab, Supplier<ItemStack> icon) {
        tab.icon = icon;
    }

    public static CreativeModeTab tab(Tab tab) {
        return HOLDERS.get(tab).get();
    }

    private static void build(Tab tab, CreativeModeTab.ItemDisplayParameters p, CreativeModeTab.Output out) {
        Set<ItemStack> seen = ItemStackLinkedSet.createTypeAndComponentsSet();
        CreativeModeTab.Output once = (stack, vis) -> {
            if (!stack.isEmpty() && seen.add(stack)) out.accept(stack.getCount() == 1 ? stack : stack.copyWithCount(1), vis);
        };
        for (Shelf s : Shelf.values()) {
            if (s.tab != tab) continue;
            if (s == Shelf.UNSORTED) {
                for (Item item : unplaced(p)) once.accept(item);
                continue;
            }
            for (CreativeModeTab.DisplayItemsGenerator f : fillers(s)) {
                try {
                    f.accept(p, once);
                } catch (RuntimeException e) {
                    Awaken.LOG.error("[core] creative tabs: a filler of {} failed; the rest of {} still shows", s, tab.id, e);
                }
            }
        }
    }

    private static synchronized List<CreativeModeTab.DisplayItemsGenerator> fillers(Shelf s) {
        return List.copyOf(FILLERS.getOrDefault(s, List.of()));
    }

    /** Awaken items no shelf places, in registry order. */
    private static List<Item> unplaced(CreativeModeTab.ItemDisplayParameters p) {
        Set<Item> placed = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (Shelf s : Shelf.values()) {
            for (CreativeModeTab.DisplayItemsGenerator f : fillers(s)) {
                try {
                    f.accept(p, (stack, vis) -> placed.add(stack.getItem()));
                } catch (RuntimeException ignored) {
                    // build() reports it
                }
            }
        }
        List<Item> out = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(Awaken.MODID) && !placed.contains(item)) out.add(item);
        }
        return out;
    }

    // ---- the check ---------------------------------------------------------------------------------------------------

    /**
     * What the menu holds and what's wrong with it. {@code unplaced}: Awaken items no shelf places (they show on the
     * Unsorted shelf). {@code twice}: Awaken items in more than one tab. {@code repeated}: a stack a shelf gave twice.
     * {@code folded}: items in several kinds with no {@link #variants} declaration, or kinds it doesn't tell apart (JEI
     * would show only the first).
     * {@code failed}: shelves whose filler threw.
     */
    public record Report(Map<Tab, Integer> sizes, List<String> unplaced, List<String> twice, List<String> repeated,
                         List<String> folded, List<String> failed, int items) {
        public boolean clean() {
            return unplaced.isEmpty() && twice.isEmpty() && repeated.isEmpty() && folded.isEmpty() && failed.isEmpty();
        }

        public String summary() {
            StringBuilder b = new StringBuilder();
            sizes.forEach((t, n) -> b.append(b.isEmpty() ? "" : ", ").append(t.id).append(' ').append(n));
            b.append(" | ").append(items).append(" Awaken items");
            if (clean()) return b.append(", each in exactly one tab").toString();
            if (!unplaced.isEmpty()) b.append(" | in no tab (").append(unplaced.size()).append("): ").append(String.join(", ", unplaced));
            if (!twice.isEmpty()) b.append(" | in two tabs: ").append(String.join(", ", twice));
            if (!repeated.isEmpty()) b.append(" | placed twice: ").append(String.join(", ", repeated));
            if (!folded.isEmpty()) b.append(" | in several kinds, undeclared: ").append(String.join(", ", folded));
            if (!failed.isEmpty()) b.append(" | fillers that threw: ").append(String.join(", ", failed));
            return b.toString();
        }
    }

    /** Builds every tab's contents the way the menu does and checks them. Runs on either side. */
    public static Report audit(CreativeModeTab.ItemDisplayParameters p) {
        Map<Tab, Integer> sizes = new EnumMap<>(Tab.class);
        Map<Item, Tab> tabOf = new IdentityHashMap<>();
        Map<Item, List<ItemStack>> kinds = new IdentityHashMap<>();
        Set<String> twice = new TreeSet<>(), repeated = new TreeSet<>(), failed = new TreeSet<>();
        for (Tab t : Tab.values()) {
            Set<ItemStack> seen = ItemStackLinkedSet.createTypeAndComponentsSet();
            for (Shelf s : Shelf.values()) {
                if (s.tab != t || s == Shelf.UNSORTED) continue;
                for (CreativeModeTab.DisplayItemsGenerator f : fillers(s)) {
                    try {
                        f.accept(p, (stack, vis) -> {
                            if (stack.isEmpty()) return;
                            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                            if (!seen.add(stack)) {
                                repeated.add(id);
                                return;
                            }
                            Tab was = tabOf.putIfAbsent(stack.getItem(), t);
                            if (was != null && was != t) twice.add(id + " (" + was.id + ", " + t.id + ")");
                            kinds.computeIfAbsent(stack.getItem(), k -> new ArrayList<>()).add(stack);
                        });
                    } catch (RuntimeException e) {
                        failed.add(s + ": " + e);
                    }
                }
            }
            sizes.put(t, seen.size());
        }
        List<String> unplaced = new ArrayList<>();
        int items = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            var key = BuiltInRegistries.ITEM.getKey(item);
            if (!key.getNamespace().equals(Awaken.MODID)) continue;
            items++;
            if (!tabOf.containsKey(item)) unplaced.add(key.getPath());
        }
        Map<Item, Function<ItemStack, @Nullable Object>> declared = variants();
        Set<String> folded = new TreeSet<>();
        kinds.forEach((item, stacks) -> {
            if (stacks.size() < 2) return;
            var kind = declared.get(item);
            String id = BuiltInRegistries.ITEM.getKey(item).getPath();
            if (kind == null) {
                folded.add(id + " x" + stacks.size());
                return;
            }
            Set<Object> told = new java.util.HashSet<>();
            for (ItemStack st : stacks) told.add(java.util.Objects.requireNonNullElse(kind.apply(st), "none"));
            if (told.size() < stacks.size()) folded.add(id + " (" + stacks.size() + " shown, " + told.size() + " told apart)");
        });
        return new Report(sizes, unplaced, List.copyOf(twice), List.copyOf(repeated), List.copyOf(folded), List.copyOf(failed), items);
    }
}
