package dev.awaken.compat.client.jei;

import dev.awaken.Awaken;
import dev.awaken.above.AboveItems;
import dev.awaken.above.MachineType;
import dev.awaken.above.frame.FrameForge;
import dev.awaken.above.frame.FrameModule;
import dev.awaken.above.machine.MachineBlockEntity;
import dev.awaken.above.power.EssenceCellItem;
import dev.awaken.core.api.CoreApi;
import dev.awaken.gear.GearItems;
import dev.awaken.gear.HunterTier;
import dev.awaken.gear.Piece;
import dev.awaken.hearth.Costs;
import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.Upgrades;
import dev.awaken.hearth.request.CoreShardItem;
import dev.awaken.hearth.request.RequestContent;
import dev.awaken.lucidium.LucidiumContent;
import dev.awaken.powers.content.PowerContent;
import dev.awaken.arms.Archetypes;
import dev.awaken.arms.ArmsForge;
import dev.awaken.arms.ArmsHomes;
import dev.awaken.arms.ArmsItems;
import dev.awaken.progression.Rank;
import dev.awaken.signal.client.Glass;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.ArmorType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Awaken's own ways of making things, as JEI recipes. Everything is read from the systems' public constants (the
 * Fabricator's blueprints, the Frame Forge's pieces, the Hearth's prices), so JEI always shows
 * what the game does. This is the one place compat reads those modules; a rename breaks here and nowhere else.
 */
final class Recipes {
    private Recipes() {}

    // ---- the Fabricator and the Frame Forge: parts in, one thing out ----------------------------------------------

    /** A build from parts: a machine at the Fabricator, a Frame piece or module at the Frame Forge. */
    record Build(Identifier id, ItemStack output, List<ItemStack> parts, int essence, String name, String text, String chip) {}

    static List<Build> machines() {
        List<Build> out = new ArrayList<>();
        for (MachineType t : MachineType.values()) {
            out.add(new Build(Awaken.id("fabricator/" + t.id), new ItemStack(AboveItems.machine(t)), stacks(t.parts()), 0,
                    t.displayName, t.blurb, "BLUEPRINT"));
        }
        return out;
    }

    static List<Build> frames() {
        List<Build> out = new ArrayList<>();
        for (FrameForge.Piece p : FrameForge.PIECES) {
            out.add(new Build(Awaken.id("frame_forge/" + p.name().toLowerCase(Locale.ROOT).replace(' ', '_')), new ItemStack(p.item().get()),
                    stacks(p.parts()), FrameForge.BUILD_COST, p.name(),
                    "Precursor exosuit plating. Modules socket into it here: the helm takes one, every other piece two.", "FRAME"));
        }
        for (FrameModule m : FrameModule.values()) {
            out.add(new Build(Awaken.id("frame_forge/" + m.id), new ItemStack(AboveItems.MODULES.get(m).get()), stacks(m.parts()),
                    FrameForge.BUILD_COST, m.displayName, m.description + " Fits: " + fits(m) + ".", "MODULE"));
        }
        return out;
    }

    private static String fits(FrameModule m) {
        List<String> names = new ArrayList<>();
        for (ArmorType t : new ArmorType[]{ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS}) {
            if (m.fitsAll().contains(t)) names.add(switch (t) {
                case HELMET -> "helm";
                case CHESTPLATE -> "chest";
                case LEGGINGS -> "legs";
                default -> "boots";
            });
        }
        return String.join(", ", names);
    }

    private static List<ItemStack> stacks(List<MachineType.Part> parts) {
        List<ItemStack> out = new ArrayList<>(parts.size());
        for (MachineType.Part p : parts) out.add(p.stack());
        return out;
    }

    // ---- the Hearth -------------------------------------------------------------------------------------------------

    /** Tempering to one level at the Forge (Oren brings the essence himself). */
    /** One Gate Lord's weapon and how often it falls, in words (docs/arms-progression/PLAN.md §3.7). */
    record Boss(String lordId, ItemStack weapon, Rank rank, String style, String spoils) {}

    static List<Boss> bosses() {
        List<Boss> out = new ArrayList<>();
        for (var f : dev.awaken.arms.LordForged.ALL) {
            var item = ArmsItems.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Awaken.id(f.weapon())));
            if (item == null) continue;
            Rank rank = Rank.E;
            for (Rank r : Rank.values()) if (r.label.equals(item.tier())) rank = r;
            float c = ArmsHomes.spoilChance(rank);
            out.add(new Boss(f.lord(), new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Awaken.id(f.weapon()))), rank, item.style(), c >= 0.5F ? "Often" : c >= 0.25F ? "Sometimes" : "Rarely"));
        }
        return out;
    }

    /** A Blueprint and every way to it: the Forge's draw, Hale's price, the gate and the level band (PLAN §3.4). */
    record Pattern(String name, Rank rank, ItemStack blueprint, List<ItemStack> draw, ArmsHomes.Home home) {}

    static List<Pattern> patterns() {
        List<Pattern> out = new ArrayList<>();
        for (ArmsHomes.Home h : ArmsHomes.all()) {
            var item = ArmsItems.blueprintItem(h.style());
            var style = Archetypes.get(h.style());
            if (item == null || style == null) continue;
            List<ItemStack> draw = List.of(new ItemStack(Items.PAPER, ArmsForge.BLUEPRINT_PAPER), new ItemStack(Items.LAPIS_LAZULI, ArmsForge.BLUEPRINT_LAPIS),
                    new ItemStack(CoreApi.ESSENCE_SHARD.get(), ArmsForge.BLUEPRINT_ESSENCE));
            out.add(new Pattern(style.def().name(), h.rank(), new ItemStack(item), draw, h));
        }
        return out;
    }

    record Temper(int level, List<ItemStack> price, List<ItemStack> examples, List<ItemStack> tempered, Rank tier) {}

    static List<Temper> tempers() {
        List<ItemStack> examples = List.of(
                GearItems.stack(HunterTier.E, Piece.BLADE), GearItems.stack(HunterTier.D, Piece.CHESTPLATE),
                new ItemStack(Items.IRON_SWORD), new ItemStack(Items.DIAMOND_HELMET), GearItems.stack(HunterTier.C, Piece.FOCUS));
        List<Temper> out = new ArrayList<>();
        for (int level = 1; level <= Upgrades.MAX_TEMPER; level++) {
            Upgrades.Def def = Upgrades.temper(level, false);
            List<ItemStack> tempered = new ArrayList<>();
            for (ItemStack e : examples) {
                ItemStack t = e.copy();
                Upgrades.applyTemper(t, level);
                tempered.add(t);
            }
            out.add(new Temper(level, parts(def.base()), examples, tempered, def.tier()));
        }
        return out;
    }

    /** Raising the Hearth Anchor or the Forge a tier. */
    record Upgrade(String what, int target, Rank rank, ItemStack station, List<ItemStack> price) {}

    static List<Upgrade> upgrades() {
        List<Upgrade> out = new ArrayList<>();
        for (int t = 2; t <= 5; t++) {
            Upgrades.Def d = Upgrades.anchorTier(t);
            out.add(new Upgrade(d.name(), t, d.tier(), new ItemStack(HearthContent.HEARTH_ANCHOR_ITEM.get()), parts(d.base())));
        }
        for (int t = 2; t <= 3; t++) {
            Upgrades.Def d = Upgrades.forgeTier(t);
            out.add(new Upgrade(d.name(), t, d.tier(), new ItemStack(HearthContent.FORGE_ITEM.get()), parts(d.base())));
        }
        return out;
    }

    private static List<ItemStack> parts(List<Costs.Part> parts) {
        List<ItemStack> out = new ArrayList<>(parts.size());
        for (Costs.Part p : parts) out.add(new ItemStack(p.item(), p.count()));
        return out;
    }

    // ---- essence and cores ------------------------------------------------------------------------------------------

    /** One of the essence economy's transformations. */
    record Essence(String name, List<ItemStack> inputs, ItemStack output, String text, int accent) {}

    static List<Essence> essence() {
        ItemStack shard = new ItemStack(CoreApi.ESSENCE_SHARD.get());
        ItemStack empty = new ItemStack(AboveItems.CELL.get());
        ItemStack charged = new ItemStack(AboveItems.CELL.get());
        EssenceCellItem.setCharge(charged, EssenceCellItem.PER_SHARD);
        ItemStack full = new ItemStack(AboveItems.CELL.get());
        EssenceCellItem.setCharge(full, EssenceCellItem.CAPACITY);
        ItemStack reactor = new ItemStack(AboveItems.machine(MachineType.REACTOR));
        int fill = EssenceCellItem.CAPACITY / EssenceCellItem.PER_SHARD;
        return List.of(
                new Essence("Charge a cell", List.of(shard, empty), charged,
                        "Use a shard on a cell: +" + EssenceCellItem.PER_SHARD + " essence. Sneak to pour in as many as fit ("
                                + fill + " fill one). Frames draw from the cells you carry.", Glass.VIOLET),
                new Essence("Feed the reactor", List.of(new ItemStack(CoreApi.ESSENCE_SHARD.get(), 16), full), reactor,
                        "Shards add " + EssenceCellItem.PER_SHARD + " each and a cell empties in, up to "
                                + String.format(Locale.ROOT, "%,d", MachineBlockEntity.REACTOR_CAPACITY) + ". It powers every machine within "
                                + dev.awaken.above.power.Power.RANGE + " blocks; sneak-use fills a cell from it.", Glass.VIOLET));
    }

    /** Fusing Power Core Shards into a Power Core. */
    record Fuse(List<ItemStack> inputs, ItemStack output, String text) {}

    static List<Fuse> fuses() {
        return List.of(new Fuse(List.of(new ItemStack(RequestContent.CORE_SHARD.get(), CoreShardItem.FUSE)),
                new ItemStack(PowerContent.POWER_CORE.get()),
                "Use " + CoreShardItem.FUSE + " shards together and they fuse into one Power Core, its power drawn from those of your rank."
                        + " The Anchor pays them for requests."));
    }

    // ---- the Codex: what key items are for --------------------------------------------------------------------------

    /** An info page: the item, a title, and what the System knows about it (lang keys awaken.jei.codex.<key>). */
    record Page(ItemStack item, String key, int accent, List<String> extra) {}

    static List<Page> pages() {
        List<Page> out = new ArrayList<>();
        out.add(new Page(new ItemStack(CoreApi.ESSENCE_SHARD.get()), "essence_shard", Glass.VIOLET, List.of()));
        out.add(new Page(new ItemStack(CoreApi.LORD_HEART.get()), "lord_heart", Glass.WARN, List.of()));
        out.add(new Page(new ItemStack(PowerContent.POWER_CORE.get()), "power_core", Glass.EDGE, List.of()));
        out.add(new Page(new ItemStack(RequestContent.CORE_SHARD.get()), "core_shard", Glass.EDGE, List.of()));
        out.add(new Page(new ItemStack(AboveItems.CELL.get()), "essence_cell", Glass.VIOLET, List.of()));
        out.add(new Page(new ItemStack(AboveItems.SCANNER.get()), "precursor_scanner", Glass.EDGE, List.of()));
        out.add(new Page(new ItemStack(AboveItems.FABRICATOR.get()), "fabricator", Glass.EDGE, List.of()));
        out.add(new Page(new ItemStack(HearthContent.HEARTH_ANCHOR_ITEM.get()), "hearth_anchor", Glass.LANTERN, List.of()));
        out.add(new Page(new ItemStack(HearthContent.FORGE_ITEM.get()), "forge", Glass.LANTERN, List.of()));
        out.add(new Page(new ItemStack(HearthContent.TROPHY_ITEM.get()), "trophy", Glass.LANTERN, List.of()));
        out.add(new Page(new ItemStack(LucidiumContent.DREAM_JOURNAL.get()), "dream_journal", Glass.VIOLET, List.of()));
        out.add(new Page(new ItemStack(LucidiumContent.DREAM_RESIDUE.get()), "dream_residue", Glass.VIOLET, List.of()));
        // the six Hunter sets: each page lists its set bonus as the game states it
        for (HunterTier t : HunterTier.values()) {
            List<String> lines = new ArrayList<>();
            for (var line : dev.awaken.gear.SetBonus.lines(t)) lines.add(line.pieces() + " worn: " + line.text());
            out.add(new Page(GearItems.stack(t, Piece.CHESTPLATE), "gear_" + t.id, t.rank.colour, lines));
        }
        return out;
    }
}
