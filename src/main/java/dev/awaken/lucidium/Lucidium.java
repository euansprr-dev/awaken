package dev.awaken.lucidium;

import dev.awaken.lucidium.data.DreamRecord;
import dev.awaken.lucidium.data.Dreamer;
import dev.awaken.lucidium.dream.ActiveDream;
import dev.awaken.lucidium.dream.Dreams;
import dev.awaken.lucidium.dream.Planner;
import dev.awaken.lucidium.dream.Sleep;
import dev.awaken.lucidium.plan.DreamPlanner;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Public lucidium API for other modules (stable). Server side unless noted.
 */
public final class Lucidium {
    private Lucidium() {}

    /** True while the player is inside a dream. Works on both sides (the client checks the dimension). */
    public static boolean isDreaming(Player player) {
        if (player.level().isClientSide()) return player.level().dimension() == LucidiumContent.LUCIDIUM;
        return Dreams.isDreaming(player);
    }

    /** Current lucidity 0..100, or 0 when awake. */
    public static float lucidity(Player player) {
        return isDreaming(player) && !player.level().isClientSide() ? Dreams.state(player).lucidity : 0f;
    }

    /** Ends the player's dream now and returns them to their bed (no-op when awake). */
    public static void wakeUp(ServerPlayer player) {
        Dreams.wake(player, Dreams.Wake.API);
    }

    /**
     * Marks the player's current dream, or the last one they had, as recorded (Above's Dream Recorder). Returns its
     * index for {@link #replay}, or -1 if there is nothing to record. Recorded dreams are kept longest.
     */
    public static int recordDream(ServerPlayer player) {
        Dreamer d = Dreams.dreamer(player);
        if (d.history.isEmpty()) return -1;
        int idx = d.history.size() - 1;
        d.history.set(idx, d.history.get(idx).withRecorded());
        return idx;
    }

    /** One line per remembered dream, oldest first (index = replay index). */
    public static List<String> dreams(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        for (DreamRecord r : Dreams.dreamer(player).history) out.add("Day " + r.day() + ": " + r.summary() + (r.recorded() ? " [recorded]" : ""));
        return out;
    }

    /**
     * Re-enters a remembered dream from its stored plan (the Dream Recorder). The player returns to where they are
     * now when it ends; time doesn't skip. Places whose snapshot has been forgotten are dreamed as new places.
     */
    public static boolean replay(ServerPlayer player, int dreamIndex) {
        Dreamer d = Dreams.dreamer(player);
        if (dreamIndex < 0 || dreamIndex >= d.history.size() || Dreams.isDreaming(player)) return false;
        Planner.Prepared p = Planner.buildNow(player.level().getServer(), player.getUUID(), d.history.get(dreamIndex).plan());
        return Dreams.enter(player, p, Dreams.Entry.awake()) != null;
    }

    /**
     * Starts a shared dream: everyone listed dreams the first player's dream (built now if tonight's isn't ready).
     * Returns false if the host can't dream (nothing to dream of yet, or already dreaming). Sleepers in the same
     * room share dreams automatically; this is for machines and scripted moments.
     */
    public static boolean startShared(List<ServerPlayer> players) {
        if (players.isEmpty()) return false;
        ServerPlayer host = players.getFirst();
        Optional<Planner.Prepared> prepared = Planner.take(host);
        if (prepared.isEmpty()) {
            var plan = DreamPlanner.plan(Planner.input(host));
            if (plan.isEmpty()) return false;
            prepared = Optional.of(Planner.buildNow(host.level().getServer(), host.getUUID(), plan.get()));
        }
        ActiveDream ad = Dreams.enter(host, prepared.get(), Dreams.Entry.awake());
        if (ad == null) return false;
        for (int i = 1; i < players.size(); i++) Dreams.join(players.get(i), ad, Dreams.Entry.awake());
        return true;
    }

    /** Whether the player chose a dreamless night (sneaking as they lay down). */
    public static boolean dreamlessTonight(ServerPlayer player) {
        return Sleep.session(player).map(s -> s.dreamless).orElse(false);
    }
}
