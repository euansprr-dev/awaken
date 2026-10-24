package dev.awaken.compat.client;

import dev.awaken.compat.SpoilerRule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * The spoiler guard on the client (SPEC §14): {@link SpoilerRule} for the local player and their {@code spoiler_guard}
 * setting, one answer for JEI and Jade. Without a player (menus, loading) nothing is guarded.
 */
public final class Spoilers {
    private Spoilers() {}

    /** True while the guard applies to the local player. */
    public static boolean active() {
        Player p = Minecraft.getInstance().player;
        return p != null && SpoilerRule.applies(p, CompatClientConfig.spoilerGuard());
    }

    /** A creature is known once the Codex has it (its first sighting). */
    public static boolean knowsCreature(String defId) {
        Player p = Minecraft.getInstance().player;
        return p == null || SpoilerRule.knowsCreature(p, defId, active());
    }
}
