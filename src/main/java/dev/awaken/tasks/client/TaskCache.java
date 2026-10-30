package dev.awaken.tasks.client;

import dev.awaken.core.api.Goals.Goal;
import dev.awaken.tasks.net.TaskPayloads;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The client's copy of the Tasks page (SPEC §27.13) and what the page is showing: the tab picked, whether the page is
 * on screen (the server looks more often while it is), and which new steps the player has had in front of them.
 */
final class TaskCache {
    private TaskCache() {}

    /** The goal tabs after Now, in rail order. "Out there" has none: its steps show on Now only. */
    static final Goal[] TABS = {Goal.HEARTH, Goal.ARMOUR, Goal.WEAPONS, Goal.BELLS, Goal.RANK};
    /** A new step counts as seen once it has been on screen this long (ticks). */
    static final int SEEN_AFTER = 30;

    static volatile TaskPayloads.View view = TaskPayloads.View.EMPTY;
    /** 0 is Now; 1.. the goal tabs. */
    static int tab;

    private static long ticks, drawnAt = -100, tabSince;
    private static boolean told;
    /** New steps drawn this frame, and the ones already reported seen (never twice). */
    private static final List<String> drawnFresh = new ArrayList<>();
    private static final Set<String> reported = new HashSet<>();

    static void onView(TaskPayloads.View v) {
        view = v;
    }

    static void act(int op, String key) {
        ClientPacketDistributor.sendToServer(new TaskPayloads.Act(op, key));
    }

    static @Nullable Goal goal() {
        return tab <= 0 || tab > TABS.length ? null : TABS[tab - 1];
    }

    static void pick(int t) {
        if (t == tab) return;
        tab = t;
        tabSince = ticks;
        drawnFresh.clear(); // a glance on the way past isn't reading
    }

    static TaskPayloads.@Nullable StepRow step(String key) {
        if (key.isEmpty()) return null;
        for (var s : view.steps()) if (s.key().equals(key)) return s;
        return null;
    }

    static boolean out(TaskPayloads.StepRow s) {
        return s.goal() == Goal.OUT.ordinal();
    }

    /** Steps new to the player on a goal's tab (all of them for null): the dot on the tab and the badge on the rail. */
    static int fresh(@Nullable Goal g) {
        int n = 0;
        for (var s : view.steps()) {
            if (!s.fresh() || s.optional() || out(s) || reported.contains(s.key())) continue;
            if (g == null || s.goal() == g.ordinal()) n++;
        }
        return n;
    }

    /** The page drew this frame; {@code key} was a new step on it. */
    static void drawn() {
        drawnAt = ticks;
    }

    static void drawnFresh(String key) {
        if (!reported.contains(key) && !drawnFresh.contains(key)) drawnFresh.add(key);
    }

    /** Each client tick: tell the server the page opened or closed, and which new steps have been read. */
    static void tick() {
        ticks++;
        boolean showing = ticks - drawnAt <= 2;
        if (showing && !told) {
            told = true;
            tabSince = ticks;
            act(TaskPayloads.OPEN, "");
        } else if (!showing && told) {
            told = false;
            act(TaskPayloads.CLOSED, "");
            drawnFresh.clear();
        }
        if (showing && ticks - tabSince >= SEEN_AFTER && !drawnFresh.isEmpty()) {
            for (String key : drawnFresh) {
                reported.add(key);
                act(TaskPayloads.SEEN, key);
            }
            drawnFresh.clear();
        }
    }

    static void reset() {
        view = TaskPayloads.View.EMPTY;
        tab = 0;
        told = false;
        drawnAt = -100;
        drawnFresh.clear();
        reported.clear();
    }
}
