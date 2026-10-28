package dev.awaken.powers;

import dev.awaken.core.api.NoticeEvents;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.List;

final class PowerNotices {
    private PowerNotices() {}
    static void learned(ServerPlayer p, PowerType t, int variant, int slot) {
        String use = t.isPassive() ? "This passive works automatically while you own it."
                : slot >= 0 ? "It is equipped in power slot " + (slot + 1) + ". The Powers page and HUD show its current bound key."
                : "Equip it in a compatible slot on the Powers page; the HUD shows the current bound key.";
        NoticeEvents.tell(p, new NoticeEvents.Event("power/" + t.path + "/learned", "Power learned", t.name, t.description,
                List.of(use, "Variant: " + t.variant(variant).name() + ". " + t.variant(variant).blurb(),
                        "It grows through useful casts in combat or by consuming duplicate Power Cores. Hover a core before use for the exact next-level changes."),
                NoticeEvents.Kind.PERMANENT, t.family.colour));
    }
    static void upgraded(ServerPlayer p, PowerType t, int variant, int before, int after, boolean throughUse) {
        List<String> changes = PowerPreviews.upgradeLines(p, t, variant, before, after);
        List<String> details = new ArrayList<>();
        details.add("Level " + before + " → " + after + " · " + t.variant(variant).name() + ". "
                + (throughUse ? "Earned through useful combat casts." : "A duplicate Power Core raised this power's level.")
                + " These numbers record your stats and active effects when the upgrade happened.");
        // Keep each saved paragraph bounded while retaining every numerical change and contextual limit.
        StringBuilder paragraph = new StringBuilder();
        for (String line : changes) {
            if (paragraph.length() + line.length() > 1000) { details.add(paragraph.toString()); paragraph.setLength(0); }
            if (!paragraph.isEmpty()) paragraph.append("\n");
            paragraph.append(line);
        }
        if (!paragraph.isEmpty()) details.add(paragraph.toString());
        NoticeEvents.tell(p, new NoticeEvents.Event("power/" + t.path + "/level/" + after + "/" + variant,
                throughUse ? "Power mastery" : "Power upgraded", t.name, "Level " + after + ". " + changes.getFirst(),
                details, NoticeEvents.Kind.PERMANENT, t.family.colour));
    }
    static void swapped(ServerPlayer p, PowerType t, int variant, int level) {
        NoticeEvents.tell(p, new NoticeEvents.Event("power/" + t.path + "/variant", "Variant changed", t.name,
                "Now " + t.variant(variant).name() + " at level " + level + ". " + t.variant(variant).blurb(),
                List.of("The core changed this power's behaviour and kept its level.", t.description,
                        "Current variant: " + t.variant(variant).name() + ". " + t.variant(variant).blurb(),
                        "Hover another core to inspect the next level of the variant you now own."), NoticeEvents.Kind.PERMANENT, t.family.colour));
    }
}
