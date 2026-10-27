package dev.awaken.trails;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.NowHooks;
import dev.awaken.core.api.Notices;
import dev.awaken.core.api.OpeningHooks;
import dev.awaken.core.api.Promises;
import dev.awaken.core.api.TipHooks;
import dev.awaken.core.api.TrailsApi;
import dev.awaken.progression.Checklist;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.trails.net.TrailPayloads;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Trails' brain (docs/trails/PLAN.md §6): looks at one player's world every two seconds, opens the trails whose moment has
 * come, ticks the steps they have done (and keeps them ticked), finishes a trail when its last step is done, and tells
 * the player's screen what changed. Nothing here ever adds anything to the HUD: only a followed trail reaches the task
 * card and the Now line, and only the player follows.
 */
public final class TrailEngine {
    private TrailEngine() {}

    /** At most this many new trails are announced a play-day; the rest open quietly. */
    static final int ANNOUNCE_PER_DAY = 2;
    /** The task card's rows, and the Now line's priority (under danger, rifts and a tracked chain at 68; over bookkeeping). */
    static final int CARD_ROWS = 5, NOW_PRIORITY = 55, NOW_ROWS = 3;

    private static final Map<UUID, TrailPayloads.View> SENT = new HashMap<>();

    static void forget(UUID player) {
        SENT.remove(player);
    }

    // ---- looking ---------------------------------------------------------------------------------------------------

    /** Re-reads the player's trails, saves what changed and sends the screen's copy if it did. False if they can't have trails yet. */
    public static boolean update(ServerPlayer p) {
        if (!Progression.profile(p).awakened() || OpeningHooks.inOpening(p)) return false;
        TrailState st = TrailState.of(p);
        boolean dirty = false;
        for (var e : Trails.all().entrySet()) {
            String id = e.getKey();
            TrailDef def = e.getValue();
            if (st.finished.contains(id)) continue;
            if (!st.opened.contains(id)) {
                if (!canOpen(p, st, def)) continue;
                open(p, st, id, def, true);
                dirty = true;
            }
            boolean all = true;
            for (TrailDef.Step s : def.steps()) {
                String k = TrailState.key(id, s.id());
                if (st.latched.contains(k)) continue;
                if (!s.done().isEmpty() && Conditions.allMet(p, s.done())) {
                    st.latched.add(k);
                    dirty = true;
                } else {
                    all = false;
                }
            }
            if (all) {
                finish(p, st, id, def);
                dirty = true;
            }
        }
        if (dirty) st.save(p);
        offerTip(p, st);
        send(p, view(p, st));
        return true;
    }

    static boolean canOpen(ServerPlayer p, TrailState st, TrailDef def) {
        for (String a : def.after()) if (!st.finished.contains(a)) return false;
        return Conditions.allMet(p, def.opens());
    }

    static void open(ServerPlayer p, TrailState st, String id, TrailDef def, boolean announce) {
        st.opened.add(id);
        Promises.kept(p, "trail:" + id);
        if (!announce) return;
        long day = PlayDays.current(p.level().getServer());
        if (st.newDay != day) {
            st.newDay = day;
            st.newToday = 0;
        }
        if (st.newToday >= ANNOUNCE_PER_DAY) return;
        st.newToday++;
        Signal.toast(p, Component.literal("A new trail: " + def.title() + ". Open the Journal (K) to follow it."), Notices.Tier.IMPORTANT, "trail");
    }

    static void finish(ServerPlayer p, TrailState st, String id, TrailDef def) {
        st.finished.add(id);
        if (id.equals(st.followed)) st.followed = "";
        if (def.finish().levels() > 0) Progression.grantLevels(p, def.finish().levels(), Progression.XpSource.DISCOVERY);
        String line = def.finish().line();
        Signal.toast(p, Component.literal("Trail finished: " + def.title() + (line.isEmpty() ? "." : ". " + line)), Notices.Tier.AMBIENT, "trail");
        Progression.markNewThing(p, "trail:" + id);
    }

    /** The followed trail's current step offers its tip once. */
    private static void offerTip(ServerPlayer p, TrailState st) {
        TrailDef def = st.followed.isEmpty() ? null : Trails.get(st.followed);
        if (def == null) return;
        TrailDef.Step s = current(p, st, st.followed, def);
        if (s == null || s.tip().isEmpty()) return;
        String k = "tip|" + TrailState.key(st.followed, s.id());
        if (!st.latched.add(k)) return;
        st.save(p);
        var id = Awaken.id(s.tip());
        TipHooks.offer(p, id);
    }

    // ---- what a step is --------------------------------------------------------------------------------------------

    static boolean done(TrailState st, String trail, TrailDef.Step s) {
        return st.latched.contains(TrailState.key(trail, s.id()));
    }

    /** Whether a step is listed now: its {@code show} tokens hold, or it is already done. (Tokens are read by the caller's player.) */
    static boolean listed(ServerPlayer p, TrailState st, String trail, TrailDef.Step s) {
        return s.show().isEmpty() || done(st, trail, s) || Conditions.allMet(p, s.show());
    }

    /** The first listed step not done, or null. */
    static TrailDef.@Nullable Step current(ServerPlayer p, TrailState st, String trail, TrailDef def) {
        for (TrailDef.Step s : def.steps()) if (!done(st, trail, s) && listed(p, st, trail, s)) return s;
        return null;
    }

    // ---- the screen's copy -----------------------------------------------------------------------------------------

    static TrailPayloads.View view(ServerPlayer p, TrailState st) {
        List<TrailPayloads.TrailRow> rows = new ArrayList<>();
        List<Candidate> now = new ArrayList<>();
        int order = 0;
        for (var e : Trails.all().entrySet()) {
            String id = e.getKey();
            TrailDef def = e.getValue();
            order++;
            int total = def.steps().size(), done = 0;
            for (TrailDef.Step s : def.steps()) if (done(st, id, s)) done++;
            boolean followed = id.equals(st.followed);
            if (st.finished.contains(id)) {
                rows.add(new TrailPayloads.TrailRow(id, def.title(), def.area(), def.blurb(), TrailPayloads.FINISHED, false, "", total, total, List.of()));
            } else if (st.opened.contains(id)) {
                boolean hidden = st.hidden.contains(id);
                List<TrailPayloads.StepRow> steps = new ArrayList<>();
                TrailDef.Step cur = current(p, st, id, def);
                for (TrailDef.Step s : def.steps()) {
                    boolean d = done(st, id, s);
                    if (!d && !listed(p, st, id, s)) continue;
                    int flags = (d ? TrailPayloads.DONE : 0) | (s == cur ? TrailPayloads.CURRENT : 0)
                            | (!s.where().isEmpty() ? TrailPayloads.WHERE : 0) | (s.manual() || s.done().isEmpty() ? TrailPayloads.MANUAL : 0);
                    steps.add(new TrailPayloads.StepRow(s.id(), s.text(), flags, s.hint()));
                }
                rows.add(new TrailPayloads.TrailRow(id, def.title(), def.area(), def.blurb(), hidden ? TrailPayloads.HIDDEN : TrailPayloads.OPEN, followed, "",
                        done, total, steps));
                if (!hidden && cur != null) now.add(new Candidate(id, def, cur, followed, total == 0 ? 0 : (double) done / total, order));
            } else {
                String need = silhouette(p, st, def);
                if (need != null) rows.add(new TrailPayloads.TrailRow(id, def.title(), def.area(), "", TrailPayloads.SILHOUETTE, false, need, 0, total, List.of()));
            }
        }
        now.sort((a, b) -> a.followed != b.followed ? (a.followed ? -1 : 1) : a.progress != b.progress ? Double.compare(b.progress, a.progress) : Integer.compare(a.order, b.order));
        List<TrailPayloads.NowRow> nowRows = new ArrayList<>();
        for (Candidate c : now) {
            if (nowRows.size() >= NOW_ROWS) break;
            nowRows.add(new TrailPayloads.NowRow(c.id, c.def.title(), c.step.text(), c.followed));
        }
        return new TrailPayloads.View(List.copyOf(rows), List.copyOf(nowRows));
    }

    private record Candidate(String id, TrailDef def, TrailDef.Step step, boolean followed, double progress, int order) {}

    /**
     * The need of a trail the player has met the lock of but not opened ("Opens at D-rank"), or null when the lock is
     * something they have not met (an unmet token that is not a level or a rank): then the trail is not shown at all.
     */
    static @Nullable String silhouette(ServerPlayer p, TrailState st, TrailDef def) {
        String need = null;
        for (String t : def.opens()) {
            if (Conditions.met(p, t)) continue;
            if (t.startsWith("rank:") && need == null) need = "Opens at " + t.substring(5) + "-rank";
            else if (t.startsWith("level:") && need == null) need = "Opens at level " + t.substring(6);
            else return null;
        }
        if (need == null) return null;
        for (String a : def.after()) if (!st.finished.contains(a)) return null;
        return need;
    }

    private static void send(ServerPlayer p, TrailPayloads.View v) {
        if (v.equals(SENT.get(p.getUUID()))) return;
        SENT.put(p.getUUID(), v);
        AwakenNet.send(p, v);
    }

    // ---- the buttons -----------------------------------------------------------------------------------------------

    public static void act(ServerPlayer p, TrailPayloads.Act a) {
        TrailState st = TrailState.of(p);
        TrailDef def = Trails.get(a.trail());
        if (def == null) return;
        switch (a.op()) {
            case TrailPayloads.FOLLOW -> {
                if (!st.opened.contains(a.trail()) || st.finished.contains(a.trail())) return;
                st.hidden.remove(a.trail());
                st.followed = a.trail();
            }
            case TrailPayloads.UNFOLLOW -> {
                if (a.trail().equals(st.followed)) st.followed = "";
            }
            case TrailPayloads.HIDE -> {
                if (!st.opened.contains(a.trail())) return;
                st.hidden.add(a.trail());
                if (a.trail().equals(st.followed)) st.followed = "";
            }
            case TrailPayloads.UNHIDE -> st.hidden.remove(a.trail());
            case TrailPayloads.MARK -> {
                if (!st.opened.contains(a.trail())) return;
                for (TrailDef.Step s : def.steps()) if (s.id().equals(a.step()) && (s.manual() || s.done().isEmpty())) st.latched.add(TrailState.key(a.trail(), s.id()));
            }
            default -> {
                return;
            }
        }
        st.save(p);
        update(p);
    }

    // ---- the task card and the Now line ----------------------------------------------------------------------------

    /** The followed trail as the task card: the last step done for context, then up to four to do (a linear trail shows two). */
    public static Checklist.@Nullable Task task(ServerPlayer p) {
        TrailState st = TrailState.of(p);
        if (st.followed.isEmpty()) return null;
        TrailDef def = Trails.get(st.followed);
        if (def == null || st.finished.contains(st.followed)) return null;
        List<Checklist.Row> rows = new ArrayList<>();
        TrailDef.Step lastDone = null;
        int open = 0, room = def.linear() ? 2 : CARD_ROWS - 1;
        TrailDef.Step cur = current(p, st, st.followed, def);
        for (TrailDef.Step s : def.steps()) {
            boolean d = done(st, st.followed, s);
            if (d) {
                lastDone = s;
                continue;
            }
            if (!listed(p, st, st.followed, s) || open >= room) continue;
            BlockPos at = s == cur && !s.where().isEmpty() ? TrailsApi.placeOf(s.where(), p) : null;
            rows.add(new Checklist.Row(s.text(), "", false, 0, 0, at));
            open++;
        }
        if (lastDone != null) rows.add(0, new Checklist.Row(lastDone.text(), "", true, 0, 0, null));
        return rows.isEmpty() ? null : new Checklist.Task("trail:" + st.followed, def.title(), List.copyOf(rows));
    }

    /** The followed trail's current step on the Now line. */
    public static List<NowHooks.Suggestion> now(ServerPlayer p) {
        TrailState st = TrailState.of(p);
        if (st.followed.isEmpty()) return List.of();
        TrailDef def = Trails.get(st.followed);
        if (def == null) return List.of();
        TrailDef.Step cur = current(p, st, st.followed, def);
        if (cur == null) return List.of();
        String id = "trail:" + st.followed + ":" + cur.id();
        BlockPos at = cur.where().isEmpty() ? null : TrailsApi.placeOf(cur.where(), p);
        return List.of(at == null ? NowHooks.Suggestion.of(id, NOW_PRIORITY, cur.text()) : NowHooks.Suggestion.at(id, NOW_PRIORITY, cur.text(), at));
    }

    /** "Anything for me?": the best next step across the player's trails, in one line, or null. */
    public static @Nullable String suggest(ServerPlayer p) {
        var v = view(p, TrailState.of(p));
        return v.now().isEmpty() ? null : v.now().getFirst().title() + ": " + v.now().getFirst().step();
    }

    static Optional<TrailDef> followed(ServerPlayer p) {
        TrailState st = TrailState.of(p);
        return st.followed.isEmpty() ? Optional.empty() : Optional.ofNullable(Trails.get(st.followed));
    }
}
