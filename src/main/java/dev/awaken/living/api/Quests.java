package dev.awaken.living.api;

import dev.awaken.living.quest.QuestEngine;
import dev.awaken.living.quest.QuestState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * The Wayfarer chains for other modules (SPEC §21, Phase C). Owners report what a player did that a step may be waiting
 * for. The Ascent: {@code awaken:use/ascent_zip} (a zip landed), {@code awaken:use/ascent_glide} (a glide ended, amount =
 * blocks), {@code awaken:discover/spire} (a spire's crown), {@code awaken:discover/hero_crown}, {@code awaken:use/windway}
 * (ridden end to end), {@code awaken:use/gauntlet}, {@code awaken:use/sky_trial}. Others: {@code awaken:use/veil},
 * {@code awaken:use/dream_lucid}, {@code awaken:use/mount}.
 */
public final class Quests {
    private Quests() {}

    /** Something a player did that a step may be waiting for: {@code awaken:<type>/<target>}. */
    public static void advance(ServerPlayer player, Identifier objective) {
        advance(player, objective, 1);
    }

    /** As {@link #advance(ServerPlayer, Identifier)} with an amount (a glide's blocks, say): a step counts it toward its count. */
    public static void advance(ServerPlayer player, Identifier objective, int amount) {
        String path = objective.getPath();
        int slash = path.indexOf('/');
        if (slash <= 0 || amount <= 0) return;
        if (path.equals("use/temper")) dev.awaken.living.craft.CraftEvents.tempered(player); // the Forge's temper is a smithing action too
        QuestEngine.advance(player, path.substring(0, slash), path.substring(slash + 1), amount);
    }

    /**
     * Another module's person is talked to ({@code who}: "kestrel", "oren", "bram"): call this first from your own
     * interaction. True when a Wayfarer chain used the talk (a step, a delivery, an offer card); then say nothing of your
     * own. False: nothing in the chains wanted them, carry on with your own dialogue.
     */
    public static boolean talk(ServerPlayer player, String who) {
        return dev.awaken.living.wayfarer.Wayfarers.talkAs(player, who, 0);
    }

    /** The chains a player has taken (ids), oldest first. */
    public static List<String> active(Player player) {
        return player.getData(dev.awaken.living.LivingContent.QUESTS.get()).active().stream().map(QuestState.Active::chain).toList();
    }

    /** The tracked chain's id, or empty. */
    public static String tracked(Player player) {
        return player.getData(dev.awaken.living.LivingContent.QUESTS.get()).tracked();
    }

    /**
     * (SPEC §28.7) One more row of the Journal from another module: a village's trouble you said Help to, or what a
     * village remembers of you. {@code tracked} rows lead, as a tracked chain does.
     */
    public record JournalRow(String title, String line, boolean tracked) {}

    private static final List<java.util.function.Function<ServerPlayer, List<JournalRow>>> JOURNAL = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Another module's rows in the one Journal (never a second Journal): asked each time the Journal is sent. */
    public static void addJournal(java.util.function.Function<ServerPlayer, List<JournalRow>> source) {
        JOURNAL.add(source);
    }

    /** Every other module's rows for a player (the Journal's sender reads them). */
    public static List<JournalRow> journalRows(ServerPlayer player) {
        List<JournalRow> out = new java.util.ArrayList<>();
        for (var s : JOURNAL) {
            try {
                out.addAll(s.apply(player));
            } catch (RuntimeException e) {
                dev.awaken.Awaken.LOG.warn("[living] a Journal source failed: {}", e.toString());
            }
        }
        return out;
    }

    /** Something another module keeps in the Journal changed: send the player's Journal again. */
    public static void journalChanged(ServerPlayer player) {
        QuestEngine.journal(player);
    }
}
