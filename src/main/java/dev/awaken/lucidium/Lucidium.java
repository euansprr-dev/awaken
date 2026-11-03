package dev.awaken.lucidium;

import dev.awaken.lucidium.data.DreamLedger;
import dev.awaken.lucidium.data.Dreamer;
import dev.awaken.lucidium.dream.ActiveDream;
import dev.awaken.lucidium.dream.DreamOffers;
import dev.awaken.lucidium.dream.Dreams;
import dev.awaken.lucidium.dream.Planner;
import dev.awaken.lucidium.dream.Sleep;
import dev.awaken.lucidium.plan.DreamPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    /** How many dream signs the player has noticed in the dream they're in ("That's not right."), or 0 when awake. */
    public static int signsNoticed(Player player) {
        return isDreaming(player) && !player.level().isClientSide() ? Integer.bitCount(Dreams.state(player).spotted) : 0;
    }

    /** Ends the player's dream now and returns them to their bed (no-op when awake). */
    public static void wakeUp(ServerPlayer player) {
        Dreams.wake(player, Dreams.Wake.API);
    }

    /**
     * The Dream Recorder's button: every trial dream is kept for replay once finished (SPEC §9.6), so this only marks
     * the latest Dream Journal entry as recorded. Returns 0, the index of the dream finished last, or -1 if none.
     */
    public static int recordDream(ServerPlayer player) {
        Dreamer d = Dreams.dreamer(player);
        if (!d.history.isEmpty()) d.history.set(d.history.size() - 1, d.history.getLast().withRecorded());
        return DreamOffers.replays(d).isEmpty() ? -1 : 0;
    }

    /** One line per dream you can dream again, the one finished last first (index = replay index). */
    public static List<String> dreams(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        for (DreamLedger.Finished f : DreamOffers.replays(Dreams.dreamer(player))) out.add(DreamOffers.replayLabel(f));
        return out;
    }

    /**
     * Dreams a finished trial again (the Dream Recorder, the Stillpoint's In door; SPEC §9.6), 0 = the one finished last.
     * Its time, medal and ghost count; nothing it gave the first time comes again. The player returns to where they are
     * now when it ends; time doesn't skip.
     */
    public static boolean replay(ServerPlayer player, int dreamIndex) {
        List<DreamLedger.Finished> list = DreamOffers.replays(Dreams.dreamer(player));
        if (dreamIndex < 0 || dreamIndex >= list.size() || Dreams.isDreaming(player) || Dreams.dreamLevel(player.level().getServer()) == null)
            return false;
        Planner.Prepared p = Planner.buildNow(player.level().getServer(), player.getUUID(), DreamOffers.replayPlan(player, list.get(dreamIndex).trial()));
        if (Dreams.enter(player, p, Dreams.Entry.awake()) != null) return true;
        Planner.discard(player.level().getServer(), p);
        return false;
    }

    /**
     * Starts a shared dream: everyone listed dreams the first player's dream (SPEC §9.6): the trial waiting for them,
     * built now, or else the one they finished last, dreamt again with everyone's best runs as ghosts. Returns false if
     * the host can't dream (nothing waits and nothing's finished, or already dreaming). Sleepers in the same room share
     * dreams automatically; this is for machines and scripted moments.
     */
    public static boolean startShared(List<ServerPlayer> players) {
        if (players.isEmpty()) return false;
        ServerPlayer host = players.getFirst();
        Optional<Planner.Prepared> prepared = Planner.take(host);
        if (prepared.isEmpty()) {
            Optional<DreamPlan> plan = DreamOffers.plan(host);
            if (plan.isEmpty()) {
                List<DreamLedger.Finished> done = DreamOffers.replays(Dreams.dreamer(host));
                if (done.isEmpty()) return false;
                plan = Optional.of(DreamOffers.replayPlan(host, done.getFirst().trial()));
            }
            prepared = Optional.of(Planner.buildNow(host.level().getServer(), host.getUUID(), plan.get()));
        }
        ActiveDream ad = Dreams.enter(host, prepared.get(), Dreams.Entry.awake());
        if (ad == null) {
            Planner.discard(host.level().getServer(), prepared.get());
            return false;
        }
        for (int i = 1; i < players.size(); i++) Dreams.join(players.get(i), ad, Dreams.Entry.awake());
        return true;
    }

    /**
     * A trial dream's bell (SPEC §9.3, §9.5): which one; where it stands now (null while the dream's rule hides it); and
     * the fear carrying it in the Chase (null when nothing does). A Room's bell moves: ask again.
     */
    public record DreamBell(String id, @Nullable BlockPos at, @Nullable UUID carrier) {}

    /** The bell this dreamer's trial holds, or null (awake, or a dream without one). */
    public static @Nullable DreamBell dreamBell(ServerPlayer player) {
        ActiveDream ad = Dreams.active(player);
        if (ad == null || ad.bp.course == null) return null;
        String ref = ad.bp.plan.trialRef();
        return ref.startsWith("bell:") ? new DreamBell(ref.substring(5), Dreams.trialBellAt(ad), Dreams.trialBellCarrier(ad)) : null;
    }

    /** Whether the player is dreaming a trial (SPEC §9.3): the bells keep their old dream puzzles out of it. */
    public static boolean inTrial(ServerPlayer player) {
        return Dreams.isDreaming(player) && !Dreams.state(player).trial.isEmpty();
    }

    /** The bell at the end of a trial dream is in this dreamer's hands: the dream is won (the bells call this). */
    public static void bellTaken(ServerPlayer player) {
        Dreams.trialBellTaken(player);
    }

    /** Whether the player chose a dreamless night (sneaking as they lay down). */
    public static boolean dreamlessTonight(ServerPlayer player) {
        return Sleep.session(player).map(s -> s.dreamless).orElse(false);
    }
}
