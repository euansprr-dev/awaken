package dev.awaken.lucidium.dream;

import dev.awaken.core.AwakenNet;
import dev.awaken.core.PlayDays;
import dev.awaken.lucidium.data.DreamRecord;
import dev.awaken.lucidium.data.Dreamer;
import dev.awaken.lucidium.data.Snapshot;
import dev.awaken.lucidium.net.LucidiumNet;
import dev.awaken.lucidium.plan.DreamPlanner;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * The Dream Journal: set tonight's intention (MILD) and write down the dream you just woke from (Recall, within two
 * minutes of waking). Each written dream raises Recall, and Recall raises the lucidity you start dreams with.
 */
public final class JournalActions {
    private JournalActions() {}

    public static final int RECALL_WINDOW = 2400;

    public static String intentionText(String id) {
        return switch (id) {
            case DreamPlanner.FIND_THE_DOOR -> "find the door";
            case DreamPlanner.FACE_MY_FEAR -> "face my fear";
            case DreamPlanner.FLY -> "fly";
            default -> "";
        };
    }

    public static boolean canRecall(ServerPlayer sp) {
        Dreamer d = Dreams.dreamer(sp);
        if (d.lastWoke < 0 || Dreams.now(sp.level().getServer()) - d.lastWoke > RECALL_WINDOW) return false;
        for (DreamRecord r : d.history) if (r.dreamId() == d.lastWokeDream) return !r.written();
        return false;
    }

    public static void open(ServerPlayer sp) {
        Dreamer d = Dreams.dreamer(sp);
        long day = PlayDays.current(sp.level().getServer());
        List<String> dreams = new ArrayList<>();
        for (int i = d.history.size() - 1; i >= 0 && dreams.size() < 8; i--) {
            DreamRecord r = d.history.get(i);
            dreams.add("Day " + r.day() + (r.lucid() ? " · lucid" : "") + (r.written() ? " · written" : "") + "|" + r.summary());
        }
        List<String> snaps = new ArrayList<>();
        for (int i = d.snapshots.size() - 1; i >= 0 && snaps.size() < 12; i--) {
            Snapshot s = d.snapshots.get(i);
            snaps.add("Day " + s.day() + "|" + s.label());
        }
        AwakenNet.send(sp, new LucidiumNet.OpenJournal(d.intention(day), canRecall(sp), d.recall, dreams, snaps));
    }

    public static void handle(ServerPlayer sp, String kind, String arg) {
        Dreamer d = Dreams.dreamer(sp);
        long day = PlayDays.current(sp.level().getServer());
        switch (kind) {
            case "intent" -> {
                if (!arg.isEmpty() && !DreamPlanner.INTENTIONS.contains(arg)) return;
                if (Dreams.isDreaming(sp)) return;
                d.setIntention(arg, day);
                Planner.invalidate(sp);
                if (!arg.isEmpty()) {
                    Signal.toast(sp, Component.literal("Tonight, I will " + intentionText(arg) + "."));
                    if (sp.level().isDarkOutside()) Planner.request(sp, "intention");
                }
            }
            case "recall" -> {
                if (!canRecall(sp)) return;
                for (int i = 0; i < d.history.size(); i++) {
                    DreamRecord r = d.history.get(i);
                    if (r.dreamId() == d.lastWokeDream) d.history.set(i, r.withWritten());
                }
                d.recall++;
                Progression.count(sp, "awaken:dreams_written");
                Signal.toast(sp, Component.literal("You write it down before it fades. Recall " + d.recall + "."));
            }
            default -> {
                return;
            }
        }
        open(sp);
    }
}
