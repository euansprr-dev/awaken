package dev.awaken.hearth;

import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.NowHooks;
import dev.awaken.progression.Progression;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

/** What the Hearth suggests right now (the Now tracker): found your home, and go home to mend. */
final class HearthNow {
    private HearthNow() {}

    static void register() {
        NowHooks.register(HearthNow::suggest);
    }

    private static List<NowHooks.Suggestion> suggest(ServerPlayer p) {
        List<NowHooks.Suggestion> out = new ArrayList<>();
        if (!Progression.profile(p).awakened()) return out;
        var home = HearthService.hearthOf(p);
        if (home.isEmpty()) {
            if (holds(p, HearthContent.HEARTH_ANCHOR_ITEM.get())) {
                out.add(NowHooks.Suggestion.of("hearth:place", 72, "Place your Hearth Anchor where you want your home"));
            } else if (holds(p, CoreApi.LORD_HEART.get())) {
                out.add(NowHooks.Suggestion.of("hearth:craft", 70, "Craft a Hearth Anchor: Lord's Heart, 4 stone bricks, 4 copper"));
            }
            return out;
        }
        var prof = Progression.profile(p);
        if (prof.fractureUntil() > p.level().getGameTime()) {
            out.add(NowHooks.Suggestion.at("hearth:mend", 60, "Rest at your Hearth to mend the Fracture", home.get().pos));
        }
        return out;
    }

    private static boolean holds(ServerPlayer p, Item item) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) return true;
        return false;
    }
}
