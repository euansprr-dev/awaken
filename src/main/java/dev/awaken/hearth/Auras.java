package dev.awaken.hearth;

import dev.awaken.hearth.player.Aura;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
import dev.awaken.progression.content.Content;
import net.minecraft.server.level.ServerPlayer;

/** Keeps each player's visible rank aura in step with their rank and Path. Drawn by the client (can be turned off). */
public final class Auras {
    private Auras() {}

    public static void refresh(ServerPlayer sp) {
        Profile p = Progression.profile(sp);
        Aura next;
        if (!p.awakened() || p.rank().ordinal() == 0) {
            next = Aura.NONE;
        } else {
            int colour = p.rank().colour;
            var path = p.path().map(Content.PATHS::get).orElse(null);
            if (path != null) colour = path.colour();
            next = new Aura(p.rank().ordinal(), colour);
        }
        if (!next.equals(sp.getData(HearthContent.AURA.get()))) sp.setData(HearthContent.AURA.get(), next);
    }
}
