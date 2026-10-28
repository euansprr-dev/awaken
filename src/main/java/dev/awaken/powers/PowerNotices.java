package dev.awaken.powers;

import dev.awaken.core.api.NoticeEvents;
import dev.awaken.powers.ladder.PowerLadder;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.List;

/**
 * What the Journal keeps about your powers (SPEC 26.3c). Each of these already has its own moment on screen (the New
 * power window, the ladder's sting or window, SPEC 36.6), so they are remembered quietly rather than told again.
 */
final class PowerNotices {
    private PowerNotices() {}

    static void learned(ServerPlayer p, PowerType t, int variant, int slot) {
        String use = t.isPassive() ? "It works on its own while you have it."
                : slot >= 0 ? "It's on power key " + (slot + 1) + ". You can move it on the Powers page."
                : "Put it on a key on the Powers page to use it.";
        NoticeEvents.remember(p, new NoticeEvents.Event("power/" + t.path + "/learned", "Power learned", t.name, t.description,
                List.of(use, t.variant(variant).name() + ": " + t.variant(variant).blurb(),
                        "It grows as you use it in fights, or when you use another Power Core of it. Hover a core to see what the next level brings."),
                NoticeEvents.Kind.PERMANENT, t.family.colour));
    }

    static void levelled(ServerPlayer p, PowerType t, int variant, int before, int after, boolean throughUse) {
        String step = PowerLadder.step(t.path, after) == null ? "" : PowerLadder.text(t.path, after).getString();
        List<String> details = new ArrayList<>();
        details.add((throughUse ? "It grew through use" : "A Power Core raised it") + ", from level " + before + " to " + after + ".");
        if (!step.isEmpty()) {
            String look = PowerLadder.look(t.path, after).getString();
            details.add(look.isEmpty() ? step : step + " " + look);
        }
        String numbers = PowerPreviews.numberSentence(p, t, variant, before, after);
        if (!numbers.isEmpty()) details.add(numbers);
        NoticeEvents.remember(p, new NoticeEvents.Event("power/" + t.path + "/level/" + after + "/" + variant,
                throughUse ? "Power grew" : "Power upgraded", t.name,
                "Level " + after + (step.isEmpty() ? "." : ". " + step), details, NoticeEvents.Kind.PERMANENT, t.family.colour));
    }

    static void swapped(ServerPlayer p, PowerType t, int variant, int level) {
        NoticeEvents.remember(p, new NoticeEvents.Event("power/" + t.path + "/variant", "Variant changed", t.name,
                "Now " + t.variant(variant).name() + ", still level " + level + ".",
                List.of(t.variant(variant).name() + ": " + t.variant(variant).blurb(), "It kept its level; only how it works changed."),
                NoticeEvents.Kind.PERMANENT, t.family.colour));
    }
}
