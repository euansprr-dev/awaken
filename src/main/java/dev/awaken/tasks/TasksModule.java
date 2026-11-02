package dev.awaken.tasks;

import dev.awaken.core.AwakenNet;
import dev.awaken.core.api.Goals;
import dev.awaken.progression.Checklist;
import dev.awaken.tasks.net.TaskPayloads;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tasks (SPEC §27.13; Euan 2026-10-30: "one single tasks tab which contains all the things you can do right now"): one
 * page on the band with a tab per standing goal ({@link Goals}). Every module's steps and the Now suggestions as one
 * list, the one step on top, a pin. The pinned step is the task card; with nothing else on, so is the step on top.
 */
public final class TasksModule {
    private TasksModule() {}

    /** Ticks between looks while the page is open, and otherwise. */
    static final int OPEN_PERIOD = 20, PERIOD = 100;

    private static final Map<UUID, TaskPayloads.View> SENT = new HashMap<>();
    private static final Set<UUID> OPEN = new HashSet<>();

    public static void init(IEventBus modBus) {
        TasksContent.register(modBus);
        modBus.addListener(TasksModule::registerPayloads);
        // every step on the page, swept with every other place a goal shows (SPEC §27.12)
        dev.awaken.core.api.Shown.register("tasks", p -> {
            List<String> out = new ArrayList<>();
            TaskBook.forget(p.getUUID()); // the sweep reads what shows now, not the book of up to 10 ticks ago (tests only)
            for (Goals.Step s : TaskBook.of(p).steps()) out.add(s.title());
            return out;
        });
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(TasksModule::onPlayerTick);
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> forget(e.getEntity().getUUID()));
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> forget(e.getEntity().getUUID()));
        Goals.installPin(new Goals.Pin() {
            @Override
            public String pinned(ServerPlayer p) {
                return TaskState.of(p).pinned();
            }

            @Override
            public boolean pin(ServerPlayer p, String key, boolean onlyIfFree) {
                if (onlyIfFree && !TaskState.of(p).pinned().isEmpty()) return false;
                TaskBook.forget(p.getUUID());
                if (TaskBook.of(p).get(key) == null) return false;
                act(p, new TaskPayloads.Act(TaskPayloads.PIN, key));
                return true;
            }
        });
        // what you pinned leads the card; the step on top shows there when nothing else is on
        Checklist.registerLeading(p -> card(p, true));
        Checklist.registerLast(p -> card(p, false));
        TasksCommands.register();
        TasksTests.register();
    }

    private static void forget(UUID id) {
        TaskBook.forget(id);
        SENT.remove(id);
        OPEN.remove(id);
    }

    private static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        int period = OPEN.contains(p.getUUID()) ? OPEN_PERIOD : PERIOD;
        if ((p.tickCount + p.getId()) % period == 0) update(p);
    }

    /** Builds the page and sends it if it changed. Returns whether it sent. */
    static boolean update(ServerPlayer p) {
        if (!AwakenNet.canReceive(p, TaskPayloads.View.TYPE)) return false;
        TaskPayloads.View v = view(p, TaskBook.of(p));
        if (v.equals(SENT.get(p.getUUID()))) return false;
        SENT.put(p.getUUID(), v);
        AwakenNet.send(p, v);
        return true;
    }

    static TaskPayloads.View view(ServerPlayer p, TaskBook.Book book) {
        TaskState st = TaskState.of(p);
        List<TaskPayloads.StepRow> rows = new ArrayList<>();
        for (Goals.Step s : book.steps()) {
            List<TaskPayloads.ChoiceRow> choices = new ArrayList<>();
            for (Goals.Choice c : s.choices()) choices.add(new TaskPayloads.ChoiceRow(c.id(), c.title(), c.sub(), c.show(), needs(c.needs())));
            rows.add(new TaskPayloads.StepRow(s.key(), s.goal().ordinal(), s.title(), s.why(), needs(s.needs()), s.where(), s.path() != Goals.NO_PATH,
                    s.optional(), !st.seen.contains(s.key()), choices, chosen(st, s) == null ? "" : chosen(st, s).id()));
        }
        return new TaskPayloads.View(rows, book.top(), book.pinned());
    }

    private static List<TaskPayloads.NeedRow> needs(List<Goals.Need> needs) {
        List<TaskPayloads.NeedRow> out = new ArrayList<>(needs.size());
        for (Goals.Need n : needs) out.add(new TaskPayloads.NeedRow(n.item(), n.label(), Math.min(n.have(), 9999), n.need()));
        return out;
    }

    static void act(ServerPlayer p, TaskPayloads.Act a) {
        TaskState st = TaskState.of(p);
        switch (a.op()) {
            case TaskPayloads.PIN -> {
                if (TaskBook.of(p).get(a.key()) == null) return; // a step that's gone can't be pinned
                st.pinned = a.key();
                dev.awaken.core.api.TrailsApi.unfollow(p); // one pin for everything (SPEC §27.13)
            }
            case TaskPayloads.UNPIN -> st.pinned = "";
            case TaskPayloads.OPEN -> OPEN.add(p.getUUID());
            case TaskPayloads.CLOSED -> OPEN.remove(p.getUUID());
            case TaskPayloads.SEEN -> {
                int goal;
                try {
                    goal = Integer.parseInt(a.key());
                } catch (NumberFormatException e) {
                    if (TaskBook.of(p).get(a.key()) == null) return;
                    st.see(a.key()); // one step, seen where it shows (the Now tab)
                    break;
                }
                for (Goals.Step s : TaskBook.of(p).steps()) if (goal < 0 || s.goal().ordinal() == goal) st.see(s.key());
            }
            case TaskPayloads.CHOOSE -> {
                int bar = a.key().indexOf('|');
                if (bar < 0) return;
                Goals.Step s = TaskBook.of(p).get(a.key().substring(0, bar));
                String id = a.key().substring(bar + 1);
                if (s == null || s.choices().stream().noneMatch(c -> c.id().equals(id))) return;
                st.chosen.put(s.key(), id);
            }
            default -> {
                return;
            }
        }
        st.save(p);
        TaskBook.forget(p.getUUID());
        update(p);
    }

    /** The choice picked on a step, if it's still one of the step's. */
    static Goals.@Nullable Choice chosen(TaskState st, Goals.Step s) {
        String id = st.chosen(s.key());
        if (id.isEmpty()) return null;
        for (Goals.Choice c : s.choices()) if (c.id().equals(id)) return c;
        return null;
    }

    /** What a step's card lists: its own needs, or the needs of what was picked on it (the armour set). */
    static List<Goals.Need> needsOf(TaskState st, Goals.Step s) {
        Goals.Choice c = s.needs().isEmpty() ? chosen(st, s) : null;
        return c == null ? s.needs() : c.needs();
    }

    /** The card for the pinned step ({@code pinned}) or for the step on top: its needs as rows, or the step itself. */
    static Checklist.@Nullable Task card(ServerPlayer p, boolean pinned) {
        TaskBook.Book book = TaskBook.of(p);
        Goals.Step s = pinned ? (book.pinned().isEmpty() ? null : book.get(book.pinned())) : book.topStep();
        if (s == null) return null;
        List<Goals.Need> needs = needsOf(TaskState.of(p), s);
        if (!pinned && needs.isEmpty()) return null; // a plain step already has its line in the corner
        List<Checklist.Row> rows = new ArrayList<>();
        var at = s.where().orElse(null);
        for (Goals.Need n : needs) {
            boolean count = !n.item().isEmpty() && n.need() > 1;
            rows.add(new Checklist.Row(n.label(), "", n.met(), count ? Math.min(n.have(), n.need()) : 0, count ? n.need() : 0, null));
        }
        if (rows.isEmpty()) rows.add(new Checklist.Row(s.title(), "", false, 0, 0, at, null));
        else if (at != null) rows.add(new Checklist.Row(s.title(), "", false, 0, 0, at, ""));
        Goals.Choice c = chosen(TaskState.of(p), s);
        return new Checklist.Task("tasks:" + s.key(), c == null ? s.title() : s.title() + ": " + c.title(), rows);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToServer(TaskPayloads.Act.TYPE, TaskPayloads.Act.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) act(sp, msg);
        });
        r.playToClient(TaskPayloads.View.TYPE, TaskPayloads.View.STREAM_CODEC);
    }
}
