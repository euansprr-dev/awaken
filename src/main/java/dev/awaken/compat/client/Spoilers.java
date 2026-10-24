package dev.awaken.compat.client;

import dev.awaken.compat.Discovered;
import dev.awaken.progression.Progression;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/**
 * The spoiler guard on the client (SPEC §14): one answer for JEI and Jade. Off in creative and spectator (the creative
 * tabs already list everything) and when the player turns {@code spoiler_guard} off.
 */
public final class Spoilers {
    private Spoilers() {}

    /** True while the guard applies to the local player. */
    public static boolean active() {
        Player p = Minecraft.getInstance().player;
        return p != null && CompatClientConfig.spoilerGuard() && !p.isCreative() && !p.isSpectator();
    }

    /** True when this item may be shown: not Awaken's, or discovered, or the guard is off. */
    public static boolean shows(Item item) {
        if (!Discovered.isAwaken(item) || !active()) return true;
        Player p = Minecraft.getInstance().player;
        return p != null && Discovered.of(p).has(item);
    }

    /** A creature is known once the Codex has it (its first sighting). */
    public static boolean knowsCreature(String defId) {
        Player p = Minecraft.getInstance().player;
        if (p == null || !active()) return true;
        return Progression.profile(p).counter("awaken:discovered/creature/" + defId) > 0;
    }
}
