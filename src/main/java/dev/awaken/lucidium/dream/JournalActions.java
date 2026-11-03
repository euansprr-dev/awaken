package dev.awaken.lucidium.dream;

import dev.awaken.core.AwakenNet;
import dev.awaken.core.PlayDays;
import dev.awaken.lucidium.data.DreamLedger;
import dev.awaken.lucidium.data.DreamRecord;
import dev.awaken.lucidium.data.Dreamer;
import dev.awaken.lucidium.data.Snapshot;
import dev.awaken.lucidium.net.LucidiumNet;
import dev.awaken.lucidium.plan.DreamPlanner;
import dev.awaken.lucidium.trial.Trial;
import dev.awaken.signal.Signal;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * The Dream Journal (SPEC §9.3): set tonight's intention (MILD) and read back what it wrote by itself: the dreams you
 * finished with your best time and medal, the latest dreams, the places the Day Journal remembered. Every finished dream
 * is a second more in each dream after it (up to ten; an old save keeps the Recall it wrote).
 */
public final class JournalActions {
    private JournalActions() {}

    public static String intentionText(String id) {
        return switch (id) {
            case DreamPlanner.FIND_THE_DOOR -> "find the door";
            case DreamPlanner.FACE_MY_FEAR -> "face my fear";
            case DreamPlanner.FLY -> "fly";
            case DreamPlanner.LEARN_A_MOVE -> "learn a move";
            case DreamPlanner.FIND_HIDDEN -> "find something hidden";
            case DreamPlanner.FIND_A_BELL -> "find a bell";
            default -> "";
        };
    }

    /** What a Journal choice asks of tonight's waiting dreams ({@link DreamLedger#kindOf}), or "" for none. */
    public static String kindOf(String choice) {
        return switch (choice) {
            case DreamPlanner.LEARN_A_MOVE -> "run";
            case DreamPlanner.FIND_HIDDEN -> "room";
            case DreamPlanner.FACE_MY_FEAR -> "chase";
            case DreamPlanner.FIND_A_BELL -> "bell";
            default -> "";
        };
    }

    public static void open(ServerPlayer sp) {
        Dreamer d = Dreams.dreamer(sp);
        long day = PlayDays.current(sp.level().getServer());
        List<String> finished = new ArrayList<>();
        for (DreamLedger.Finished f : d.ledger.finished.values()) {
            Trial t = Trial.byId(f.trial());
            if (t == null) continue;
            String medal = f.medal() > 0 ? " · " + Trials.medalName(f.medal()) : "";
            finished.add(t.title + "|Best " + Trials.time(f.best()) + medal + (f.times() > 1 ? " · " + f.times() + " times" : ""));
        }
        List<String> dreams = new ArrayList<>();
        for (int i = d.history.size() - 1; i >= 0 && dreams.size() < 8; i--) {
            DreamRecord r = d.history.get(i);
            dreams.add("Day " + r.day() + (r.lucid() ? " · lucid" : "") + "|" + r.summary());
        }
        List<String> snaps = new ArrayList<>();
        for (int i = d.snapshots.size() - 1; i >= 0 && snaps.size() < 12; i--) {
            Snapshot s = d.snapshots.get(i);
            snaps.add("Day " + s.day() + "|" + s.label());
        }
        int seconds = Math.round(Lucidity.PER_RECALL * Math.min(Lucidity.RECALL_CAP, Trials.recall(d)));
        boolean bell = false;
        for (DreamLedger.Waiting w : d.ledger.waiting) bell |= "bell".equals(DreamLedger.kindOf(w));
        AwakenNet.send(sp, new LucidiumNet.OpenJournal(d.intention(day), !d.ledger.waiting.isEmpty(), bell, seconds, finished, dreams, snaps));
    }

    public static void handle(ServerPlayer sp, String kind, String arg) {
        Dreamer d = Dreams.dreamer(sp);
        long day = PlayDays.current(sp.level().getServer());
        switch (kind) {
            case "intent" -> {
                if (!arg.isEmpty() && !DreamPlanner.INTENTIONS.contains(arg) && !DreamPlanner.CHOICES.contains(arg)) return;
                if (Dreams.isDreaming(sp)) return;
                d.setIntention(arg, day);
                // the choice of tonight's dream when more than one waits (and the asking for one, a night after the last)
                d.ledger.choice = kindOf(arg);
                d.ledger.choiceDay = day;
                DreamOffers.changed(sp);
                if (!arg.isEmpty()) {
                    Signal.toast(sp, Component.literal("Tonight I want to " + intentionText(arg) + "."));
                    if (sp.level().isDarkOutside()) Planner.request(sp, "intention");
                }
            }
            default -> {
                return;
            }
        }
        open(sp);
    }
}
