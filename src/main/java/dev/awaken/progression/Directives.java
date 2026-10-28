package dev.awaken.progression;

import dev.awaken.core.DirectorLog;
import dev.awaken.core.PlayDays;
import dev.awaken.progression.content.Content;
import dev.awaken.progression.content.Defs;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Whisper: at most three optional, expiring suggestions. Never a list, never chores (SPEC section 4.8).
 */
public final class Directives {
    private Directives() {}

    public static final String ACTION = "directive";

    /** Active Directive slots for this player (3 + perks + runtime sources such as a linked Signal Relay). */
    public static int slots(ServerPlayer player) {
        return 3 + (int) Progression.perk(player, Perks.EXTRA_DIRECTIVE_SLOT) + Progression.extraDirectiveSlots(player);
    }

    /** The Registry's own Directive (SPEC §27.6): the first floor of a gate, already taken on. */
    static final String OPENING = "awaken:first_floor";

    /** Each in-game morning, and after long gaps without anything new. */
    public static void tryOffer(ServerPlayer player, String reason) {
        Profile p = Progression.profile(player);
        if (!p.awakened()) return;
        long today = PlayDays.current(player.level().getServer());
        expire(player, p, today);
        if (p.directives.size() >= slots(player)) return;
        if (reason.equals("opening") && opening(player, p, today)) return;
        boolean wantQuick = Progression.minutesSinceNewThing(player) > 20;
        long offers = p.counter("awaken:directive_offers");
        boolean mustBeFresh = offers % 4 == 3;
        List<Map.Entry<Identifier, Defs.DirectiveDef>> pool = new ArrayList<>();
        Set<String> active = new HashSet<>();
        p.directives.forEach(d -> active.add(d.id()));
        for (var e : Content.DIRECTIVES.map.entrySet()) {
            String key = e.getKey().toString();
            var d = e.getValue();
            if (active.contains(key)) continue;
            if (p.rank().ordinal() < d.minRank().ordinal() || p.rank().ordinal() > d.maxRank().ordinal()) continue;
            if (d.path().isPresent() && !d.path().get().equals(p.path)) continue;
            if (!Requirements.met(player, d.requires())) continue; // never ask for a power, place or skill you don't have
            long lastOffered = p.counter("awaken:directive_offered/" + key);
            if (lastOffered > 0 && today - (lastOffered - 1) < 5) continue;
            if (mustBeFresh && p.counter("awaken:directive_done/" + key) > 0) continue;
            pool.add(e);
        }
        pool.sort(Map.Entry.comparingByKey()); // map order isn't stable: keep the roll reproducible
        if (pool.isEmpty()) {
            DirectorLog.log(player, "directive", "no valid offer (" + reason + ")");
            return;
        }
        int total = 0;
        for (var e : pool) total += weight(e.getValue(), wantQuick);
        int roll = player.getRandom().nextInt(Math.max(1, total));
        Map.Entry<Identifier, Defs.DirectiveDef> pick = pool.get(0);
        for (var e : pool) {
            roll -= weight(e.getValue(), wantQuick);
            if (roll < 0) { pick = e; break; }
        }
        String key = pick.getKey().toString();
        var def = pick.getValue();
        p.directives.add(new Profile.DirectiveState(key, false, today, today + def.expiresDays(), Map.of()));
        p.counters.put("awaken:directive_offered/" + key, today + 1);
        p.counters.merge("awaken:directive_offers", 1L, Long::sum);
        DirectorLog.log(player, "directive", "offered " + key + " (" + reason + ", pool " + pool.size()
                + (wantQuick ? ", quick bias" : "") + (mustBeFresh ? ", fresh only" : "") + ")");
        // an optional task never interrupts: it waits folded (tracker + Status screen) with a small toast
        Signal.offerQuietly(player, SignalWindow.builder(SignalWindow.Kind.DIRECTIVE, Component.literal(def.text()))
                .body(Component.literal(describe(def.reward()) + " · fades in " + def.expiresDays() + " day" + (def.expiresDays() == 1 ? "" : "s")))
                .choice(ACTION + ":" + key, "Take it on", "Let it pass").build(),
                Component.literal("New directive · " + def.text() + " · K"));
        Progression.markDirty(player);
    }

    /**
     * The Registry's Directive fits the moment instead of a random roll: clear a floor of a gate (your first gate is
     * placed on purpose soon after: SPEC §27.6), accepted for you and on the Now line with its progress. False if it
     * can't be (no such Directive, already held or done), and the ordinary roll takes over.
     */
    static boolean opening(ServerPlayer player, Profile p, long today) {
        var def = Content.DIRECTIVES.get(OPENING);
        if (def == null || p.counter("awaken:directive_done/" + OPENING) > 0) return false;
        for (var d : p.directives) if (d.id().equals(OPENING)) return false;
        Set<String> cs = new HashSet<>();
        def.condition().counters(cs);
        p.directives.add(new Profile.DirectiveState(OPENING, true, today, today + Math.max(4, def.expiresDays()), Progression.snapshot(p, cs)));
        p.counters.put("awaken:directive_offered/" + OPENING, today + 1);
        p.counters.merge("awaken:directive_offers", 1L, Long::sum);
        ProgressionNotices.tell(player, "directive/" + OPENING + "/accepted/" + today, "Directive accepted", def.text(),
                "Your first gate will open somewhere outside the city walls.", dev.awaken.core.api.NoticeEvents.Kind.TASK,
                "Clear the first floor of a gate, then follow your task card.", "Reward: " + RewardWords.describe(def.reward()) + ".");
        DirectorLog.log(player, "directive", "the Registry's own: " + OPENING + ", accepted");
        Progression.markDirty(player);
        return true;
    }

    private static int weight(Defs.DirectiveDef d, boolean wantQuick) {
        return d.weight() * (wantQuick && d.quick() ? 4 : 1);
    }

    static String describe(Reward r) {
        List<String> parts = new ArrayList<>();
        if (r.levels() > 0) parts.add(String.format("%.0f%% of a level", r.levels() * 100));
        if (r.statPoints() > 0) parts.add("+" + r.statPoints() + " point" + (r.statPoints() > 1 ? "s" : ""));
        r.stat().ifPresent(s -> parts.add("+" + Math.max(1, r.statAmount()) + " " + s.displayName));
        r.power().ifPresent(pw -> parts.add("a power"));
        r.item().ifPresent(i -> parts.add("an item"));
        return parts.isEmpty() ? "Recognition" : String.join(", ", parts);
    }

    /** How long is left, said plainly (the last day still counts: a directive expires the day after it). */
    static String timeLeft(long days) {
        return days <= 0 ? "Finish it today." : days == 1 ? "You have until tomorrow to finish it." : "You have " + days + " days to finish it.";
    }

    /** Signal answer: Y accepts (baseline starts now), N dismisses. */
    static void answer(ServerPlayer player, String action, boolean accepted) {
        if (action.length() <= ACTION.length() + 1) return;
        String key = action.substring(ACTION.length() + 1);
        Profile p = Progression.profile(player);
        for (int i = 0; i < p.directives.size(); i++) {
            var d = p.directives.get(i);
            if (!d.id().equals(key) || d.accepted()) continue;
            if (!accepted) {
                p.directives.remove(i);
                p.counters.merge("awaken:directives_dismissed", 1L, Long::sum);
            } else {
                var def = Content.DIRECTIVES.get(key);
                if (def == null) { p.directives.remove(i); break; }
                Set<String> cs = new HashSet<>();
                def.condition().counters(cs);
                p.directives.set(i, new Profile.DirectiveState(key, true, d.offeredDay(), d.expiresDay(), Progression.snapshot(p, cs)));
                ProgressionNotices.tell(player, "directive/" + key + "/accepted/" + d.offeredDay(), "Directive accepted", def.text(),
                        "It's on your task list. " + timeLeft(d.expiresDay() - PlayDays.current(player.level().getServer())), dev.awaken.core.api.NoticeEvents.Kind.TASK,
                        "Only what you do from now on counts. Your Journal shows how far along you are.", "Reward: " + RewardWords.describe(def.reward()) + ".");
            }
            Progression.markDirty(player);
            return;
        }
    }

    static void evaluate(ServerPlayer player, Profile p) {
        for (int i = p.directives.size() - 1; i >= 0; i--) {
            var d = p.directives.get(i);
            if (!d.accepted()) continue;
            var def = Content.DIRECTIVES.get(d.id());
            if (def == null) { p.directives.remove(i); continue; }
            if (def.condition().test(p, d.baseline())) {
                p.directives.remove(i);
                p.counters.merge("awaken:directive_done/" + d.id(), 1L, Long::sum);
                p.counters.merge(Counters.DIRECTIVES_KEPT_STREAK, 1L, Long::sum);
                dev.awaken.core.audio.AudioCues.ui(player, dev.awaken.core.audio.UiSound.QUEST_DONE);
                ProgressionNotices.tell(player, "directive/" + d.id() + "/" + p.counter("awaken:directive_done/" + d.id()),
                        "Directive complete", def.text(), "Done, and the reward is yours.", dev.awaken.core.api.NoticeEvents.Kind.TASK,
                        "Reward: " + RewardWords.describe(def.reward()) + ".", "It's off your task list now.");
                RewardHooks.apply(player, def.reward(), Progression.XpSource.DIRECTIVE);
                Progression.evaluate(player, Counters.DIRECTIVES_KEPT_STREAK);
                Progression.markDirty(player);
                return; // re-entrant evaluate already ran for the rest
            }
        }
    }

    static void expire(ServerPlayer player, Profile p, long today) {
        boolean brokeStreak = false;
        for (int i = p.directives.size() - 1; i >= 0; i--) {
            var d = p.directives.get(i);
            if (today > d.expiresDay()) {
                if (d.accepted()) brokeStreak = true;
                p.directives.remove(i);
            }
        }
        if (brokeStreak) p.counters.put(Counters.DIRECTIVES_KEPT_STREAK, 0L);
    }

    /** Progress of an accepted directive in [0,1] (for the Status screen). */
    public static double progress(Profile p, Profile.DirectiveState d) {
        var def = Content.DIRECTIVES.get(d.id());
        return def == null || !d.accepted() ? 0 : def.condition().progress(p, d.baseline());
    }
}
