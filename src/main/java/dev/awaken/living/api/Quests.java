package dev.awaken.living.api;

import dev.awaken.living.quest.QuestEngine;
import dev.awaken.living.quest.QuestState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * The Wayfarer chains for other modules (SPEC §21, Phase C). Owners report what a player did that a step may be waiting
 * for: {@code advance(player, Identifier.parse("awaken:use/ascent_zip"))} (a zip landed), {@code awaken:use/veil}
 * (spirit projection), {@code awaken:use/dream_lucid}, {@code awaken:use/mount}, {@code awaken:use/spire}.
 */
public final class Quests {
    private Quests() {}

    /** Something a player did that a step may be waiting for: {@code awaken:<type>/<target>}. */
    public static void advance(ServerPlayer player, Identifier objective) {
        String path = objective.getPath();
        int slash = path.indexOf('/');
        if (slash <= 0) return;
        QuestEngine.advance(player, path.substring(0, slash), path.substring(slash + 1), 1);
    }

    /** The chains a player has taken (ids), oldest first. */
    public static List<String> active(Player player) {
        return player.getData(dev.awaken.living.LivingContent.QUESTS.get()).active().stream().map(QuestState.Active::chain).toList();
    }

    /** The tracked chain's id, or empty. */
    public static String tracked(Player player) {
        return player.getData(dev.awaken.living.LivingContent.QUESTS.get()).tracked();
    }
}
