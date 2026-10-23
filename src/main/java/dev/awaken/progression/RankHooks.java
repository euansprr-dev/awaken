package dev.awaken.progression;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** Other modules react to rank-ups here (town NPC arrivals, story beats, new gate tiers). */
public final class RankHooks {
    private RankHooks() {}

    private static final List<BiConsumer<ServerPlayer, Rank>> LISTENERS = new ArrayList<>();

    public static void onRankUp(BiConsumer<ServerPlayer, Rank> listener) {
        LISTENERS.add(listener);
    }

    static void fire(ServerPlayer player, Rank rank) {
        for (var l : LISTENERS) l.accept(player, rank);
    }
}
