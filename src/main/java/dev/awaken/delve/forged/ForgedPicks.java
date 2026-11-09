package dev.awaken.delve.forged;

import dev.awaken.delve.api.Pick;
import dev.awaken.progression.Progression;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The forged picks (SPEC §49): 24 items, one per form and rank, and everything other code asks about them. Their ids are
 * {@code <prefix>_pick_<form>}; the old {@code <prefix>_mattock} ids and {@code waking_pick} stay registered for the saves
 * that hold them.
 */
public final class ForgedPicks {
    private ForgedPicks() {}

    /** The rank's prefix. The same six as the War Anvil's. */
    public static final String[] PREFIX = {"wildhide", "beastbound", "gatewrought", "veilwoven", "lordscale", "sovereign"};
    private static final Rarity[] RARITY = {Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE, Rarity.EPIC, Rarity.EPIC, Rarity.EPIC};
    private static final int[] ENCHANTABLE = {15, 12, 12, 15, 15, 18};

    /** The best a player has held: tier + 1 (0 never), the form's ordinal + 1 (0 none). */
    public static final String BEST_TIER = "awaken:delve/pick_best_tier", BEST_FORM = "awaken:delve/pick_best_form";

    private static final Map<PickForm, List<DeferredItem<ForgedPickItem>>> TABLE = new EnumMap<>(PickForm.class);

    /** Registers the 24 (the Delve module's item register). */
    public static void register(DeferredRegister.Items items) {
        for (PickForm f : PickForm.values()) {
            List<DeferredItem<ForgedPickItem>> row = new ArrayList<>();
            for (int t = 0; t < PickStats.TIERS; t++) {
                final int tier = t;
                row.add(items.registerItem(id(f, t), p -> new ForgedPickItem(p, f, tier), p -> properties(p, f, tier)));
            }
            TABLE.put(f, row);
        }
    }

    private static Item.Properties properties(Item.Properties p, PickForm f, int tier) {
        HolderGetter<Block> blocks = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK);
        List<Tool.Rule> rules = new ArrayList<>(List.of(
                Tool.Rule.deniesDrops(blocks.getOrThrow(PickStats.INCORRECT.get(tier))),
                Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_PICKAXE), PickStats.speed(f, tier))));
        if (f == PickForm.MATTOCK) {
            rules.add(Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_AXE), PickStats.softSpeed(f, tier)));
            rules.add(Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_SHOVEL), PickStats.softSpeed(f, tier)));
        }
        Item.Properties out = p.rarity(RARITY[tier]).durability(PickStats.baseUses(f, tier)).enchantable(ENCHANTABLE[tier])
                .component(DataComponents.TOOL, new Tool(rules, 1.0F, 1, true))
                .component(DataComponents.WEAPON, new Weapon(2, 0F))
                .attributes(ItemAttributeModifiers.builder()
                        .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, PickStats.damage(f, tier), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                        .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -2.8, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                        .build());
        return tier >= 4 ? out.fireResistant() : out;
    }

    public static String id(PickForm f, int tier) {
        return PREFIX[tier] + "_pick_" + f.id;
    }

    public static Item get(PickForm f, int tier) {
        return TABLE.get(f).get(tier).get();
    }

    /** All 24, for the ledger, the creative tab and the tests. */
    public static List<Item> all() {
        List<Item> out = new ArrayList<>();
        for (PickForm f : PickForm.values()) for (DeferredItem<ForgedPickItem> i : TABLE.get(f)) out.add(i.get());
        return out;
    }

    public static boolean is(ItemStack s) {
        return s.getItem() instanceof ForgedPickItem;
    }

    public static @Nullable ForgedPickItem item(ItemStack s) {
        return s.getItem() instanceof ForgedPickItem i ? i : null;
    }

    /** Its story; a pick with none (a command gave it) reads as Plain peacock at its own rank. */
    public static PickForged data(ItemStack s) {
        PickForged d = s.get(DelveForged.PICK_FORGED.get());
        if (d != null) return d;
        ForgedPickItem i = item(s);
        return PickForged.plain(i == null ? 0 : i.tier);
    }

    /** The factor everything but the rank's own speed adds up to, for this stack on this block. */
    public static float speedFactor(ItemStack s) {
        PickForged d = data(s);
        float f = PickStats.smithSpeed(d);
        if (d.tally().keen() > 0) f *= PickStats.KEEN;
        if (blunt(s)) f *= PickStats.BLUNT;
        return f;
    }

    public static boolean blunt(ItemStack s) {
        return s.isDamageableItem() && s.getDamageValue() >= s.getMaxDamage() - 1;
    }

    /** Which end of a Mattock meets this block: the point for rock, the blade for wood and earth. */
    public static boolean softSide(BlockState s) {
        return !s.is(BlockTags.MINEABLE_WITH_PICKAXE) && (s.is(BlockTags.MINEABLE_WITH_SHOVEL) || s.is(BlockTags.MINEABLE_WITH_AXE));
    }

    /**
     * Sets a pick's story and every number that follows from it. The rank's own numbers are on the item; only its uses can
     * differ per stack, and only when the smith did something about them.
     */
    public static void write(ItemStack s, PickForged d) {
        ForgedPickItem i = item(s);
        if (i == null) return;
        s.set(DelveForged.PICK_FORGED.get(), d);
        s.set(DataComponents.MAX_DAMAGE, PickStats.uses(i.form, i.tier, d));
    }

    // ---- gifts --------------------------------------------------------------------------------------------------------

    /** What each gift's deed is (counter name), and the rank it asks for. Gifts not listed come with the rank or the form. */
    public static @Nullable String deed(Pick.Gift g) {
        return switch (g) {
            case KINDLE -> "awaken:delve/deed_heart";
            case SOUNDING -> "awaken:delve/deed_lamp";
            case LONG_SOUNDING -> "awaken:delve/deed_deepglass";
            case HUSH -> "awaken:delve/deed_seep";
            default -> null;
        };
    }

    /** The lowest rank that can hold it. */
    public static int rankOf(Pick.Gift g) {
        return switch (g) {
            case HUM -> 0;
            case KINDLE -> 1;
            case SOUNDING, WIDE_SEAM -> 2;
            case LONG_SOUNDING -> 3;
            case HUSH -> 4;
            case CALL -> 5;
        };
    }

    /** The gifts this player's deeds give a pick of this rank, as bits. */
    public static int earned(ServerPlayer p, int tier) {
        int bits = 0;
        for (Pick.Gift g : Pick.Gift.values()) {
            String deed = deed(g);
            if (deed != null && tier >= rankOf(g) && Progression.profile(p).counter(deed) > 0) bits |= g.bit();
        }
        return bits;
    }

    /** Deeds done that this pick hasn't been worked in with yet. */
    public static int waiting(ServerPlayer p, ItemStack s) {
        ForgedPickItem i = item(s);
        return i == null ? 0 : earned(p, i.tier) & ~data(s).gifts();
    }

    /** Whether this player has done what opens a form. */
    public static boolean open(ServerPlayer p, PickForm f) {
        return f.opens == null || Progression.profile(p).counter(f.opens) > 0;
    }

    // ---- what the player has held ---------------------------------------------------------------------------------------

    /** Remembers the best pick a player has made, for Make it again. */
    public static void remember(ServerPlayer p, PickForm f, int tier) {
        if (tier + 1 < Progression.profile(p).counter(BEST_TIER)) return;
        Progression.setCounter(p, BEST_TIER, tier + 1);
        Progression.setCounter(p, BEST_FORM, f.ordinal() + 1);
    }
}
