package dev.awaken.arms.client;

import dev.awaken.arms.Archetypes;
import dev.awaken.arms.Arms;
import dev.awaken.arms.ArmsMastery;
import dev.awaken.arms.Marks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/** A weapon's Marks, its attunement and your mastery of its style, under its name (SPEC §25). */
final class ArmsTooltip {
    private ArmsTooltip() {}

    private static final String[] NUMERAL = {"", "I", "II", "III", "IV", "V"};

    static void onTooltip(ItemTooltipEvent e) {
        ItemStack stack = e.getItemStack();
        Archetypes.Loaded style = Arms.styleOf(stack);
        if (style == null) return;
        List<Component> lines = e.getToolTip();
        int at = Math.min(1, lines.size());
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
}
