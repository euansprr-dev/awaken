package dev.awaken.arms.client;

import dev.awaken.arms.Archetypes;
import dev.awaken.arms.Arms;
import dev.awaken.arms.ArmsHomes;
import dev.awaken.arms.ArmsItems;
import dev.awaken.arms.ArmsMastery;
import dev.awaken.arms.ArmsSources;
import dev.awaken.arms.Marks;
import dev.awaken.progression.Rank;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A weapon's Marks, its attunement and your mastery of its style, under its name (SPEC §25); and the plain words that
 * say what an item is and where it comes from (docs/arms-progression/PLAN.md §3.5): the Hunter blades, the Blueprints,
 * the Essence Shard, the Forge, and a source line on every ingredient of Hunter gear and Arms weapons (Shift opens it).
 */
final class ArmsTooltip {
    private ArmsTooltip() {}

    private static final String[] NUMERAL = {"", "I", "II", "III", "IV", "V"};

    static void onTooltip(ItemTooltipEvent e) {
        ItemStack stack = e.getItemStack();
        List<Component> lines = e.getToolTip();
        Identifier item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (item.getNamespace().equals("awaken") || ArmsSources.isIngredient(item)) plain(stack, item, lines);
        Archetypes.Loaded style = Arms.styleOf(stack);
        if (style == null) return;
        int at = Math.min(1, lines.size());
        at = found(stack, item, lines, at);
        Marks.Instance inst = Marks.of(stack);
        for (String id : inst.marks()) {
            Marks.Mark m = Marks.get(id);
            if (m == null) continue;
            lines.add(at++, Component.literal("◆ " + m.name()).withColor(0x8FC8FF));
            lines.add(at++, Component.literal("   " + Marks.describe(m, inst.attunement())).withStyle(ChatFormatting.GRAY));
        }
        if (inst.attunement().isPresent()) {
            String n = inst.attunement().get().name();
            lines.add(at++, Component.literal("Attuned · " + n.charAt(0) + n.substring(1).toLowerCase(java.util.Locale.ROOT)).withColor(0xE2B450));
        }
        int step = ArmsPredict.mastery(style);
        if (step > 0) lines.add(at, Component.literal(style.def().name() + " mastery " + NUMERAL[Math.min(5, step)]).withColor(0xC8C2B0));
    }

    // ---- plain words ------------------------------------------------------------------------------------------------

    /** The "what it is, what it's for, where to get more" lines of this item, if it is one of the ones that has them. */
    private static void plain(ItemStack stack, Identifier id, List<Component> lines) {
        int at = Math.min(1, lines.size());
        Map<String, Integer> blade = ArmsSources.blade(id);
        String blueprint = ArmsItems.blueprintStyle(stack.getItem());
        if (blade != null) {
            lines.add(at++, gray("awaken.tooltip.blade.what"));
            lines.add(at, Component.translatable("awaken.tooltip.made_from", madeFrom(blade)).withStyle(ChatFormatting.GRAY));
        } else if (blueprint != null) {
            lines.add(at++, gray("awaken.tooltip.blueprint.what"));
            ArmsHomes.Home home = ArmsHomes.of(blueprint);
            if (home != null) {
                Rank r = home.rank();
                lines.add(at++, Component.translatable("awaken.tooltip.blueprint.found", r.label, ArmsHomes.band(r), ArmsHomes.band(r)).withColor(0x8FC8FF));
                lines.add(at, Component.translatable("awaken.tooltip.blueprint.hale", r.label, ArmsHomes.rackPrice(r)).withStyle(ChatFormatting.GRAY));
            }
        } else if (id.equals(ESSENCE)) {
            lines.add(at++, gray("awaken.tooltip.essence.what"));
            lines.add(at, gray("awaken.tooltip.essence.used"));
        } else if (id.equals(FORGE)) {
            lines.add(at++, gray("awaken.tooltip.forge.temper"));
            lines.add(at++, gray("awaken.tooltip.forge.attune"));
            lines.add(at, gray("awaken.tooltip.forge.blueprint"));
        }
        if (blueprint == null && ArmsSources.isIngredient(id)) source(id, lines);
    }

    /** Where a found weapon comes from, in words: a Lord-forged one names its Lord, a Marked one says what drops them; and what Raise did to it. */
    private static int found(ItemStack stack, Identifier item, List<Component> lines, int at) {
        var forged = dev.awaken.arms.LordForged.byWeapon(item.getPath());
        if (forged != null) {
            var home = ArmsHomes.of(Arms.styleOf(stack) == null ? "" : Arms.styleOf(stack).id());
            String rank = home == null ? "" : home.rank().label + "-rank ";
            lines.add(at++, Component.translatable("awaken.tooltip.lord_forged", Component.translatable("entity.awaken." + forged.lord()), rank).withStyle(ChatFormatting.GRAY));
        } else if (!Marks.of(stack).marks().isEmpty()) {
            lines.add(at++, gray("awaken.tooltip.marked"));
        }
        var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        int raised = data == null ? 0 : data.copyTag().getIntOr("awaken_raised", 0);
        if (raised > 0) lines.add(at++, Component.translatable("awaken.tooltip.raised", raised).withStyle(ChatFormatting.GRAY));
        return at;
    }

    private static final Identifier ESSENCE = Identifier.fromNamespaceAndPath("awaken", "essence_shard");
    private static final Identifier FORGE = Identifier.fromNamespaceAndPath("awaken", "forge");

    private static MutableComponent gray(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.GRAY);
    }

    /** "2 Parts, 1 Binding, 1 Essence Shard", in the order a player reads a recipe. */
    private static Component madeFrom(Map<String, Integer> counts) {
        List<Component> parts = new ArrayList<>();
        for (String kind : new String[] {"hide", "part", "binding", "metal", "essence"}) {
            int n = counts.getOrDefault(kind, 0);
            if (n > 0) parts.add(Component.translatable("awaken.count." + kind + (n == 1 ? ".one" : ".many"), n));
        }
        return ComponentUtils.formatList(parts, c -> c);
    }

    /** An ingredient's source: a dim hint, and with Shift held where it comes from. */
    private static void source(Identifier id, List<Component> lines) {
        if (!Minecraft.getInstance().hasShiftDown()) {
            lines.add(Component.translatable("awaken.tooltip.ingredient").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        String key = ArmsSources.langKey(id);
        if (Language.getInstance().has(key)) lines.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        List<Identifier> who = ArmsSources.creatures(id);
        if (!who.isEmpty()) {
            Component names = ComponentUtils.formatList(who, c -> Component.translatable("entity." + c.getNamespace() + "." + c.getPath()));
            lines.add(Component.translatable("awaken.tooltip.dropped_by", names).withStyle(ChatFormatting.GRAY));
        }
    }
}
