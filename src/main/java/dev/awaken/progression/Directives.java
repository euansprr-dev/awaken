package dev.awaken.progression;

import dev.awaken.core.DirectorLog;
import dev.awaken.core.PlayDays;
import dev.awaken.progression.content.Content;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;

/**
 * What's left of the Directives (SPEC §27.13): the morning offers went with the Tasks page. Only the Registry's own stays,
 * "clear the first floor of a gate" (SPEC §27.6), taken on for you at the opening, shown on Now and the Tasks page with
 * how far along it is until it's done, its reward unchanged. It never fades. An old save's other Directives drop on login.
 */
public final class Directives {
    private Directives() {}

    /** The Registry's own Directive: the first floor of a gate. */
    static final String OPENING = "awaken:first_floor";

    /** The opening hands you the Registry's Directive; any other reason is a morning offer, and those are gone. */
    public static void tryOffer(ServerPlayer player, String reason) {
        Profile p = Progression.profile(player);
        if (!p.awakened() || !reason.equals("opening")) return;
        opening(player, p, PlayDays.current(player.level().getServer()));
    }

    /** The Registry's Directive, accepted for you and on the Now line with its progress. False when it's held or done. */
    static boolean opening(ServerPlayer player, Profile p, long today) {
        var def = Content.DIRECTIVES.get(OPENING);
        if (def == null || p.counter("awaken:directive_done/" + OPENING) > 0) return false;
        for (var d : p.directives) if (d.id().equals(OPENING)) return false;
        Set<String> cs = new HashSet<>();
        def.condition().counters(cs);
        p.directives.add(new Profile.DirectiveState(OPENING, true, today, Long.MAX_VALUE, Progression.snapshot(p, cs)));
        ProgressionNotices.tell(player, "directive/" + OPENING + "/accepted/" + today, "Your first job", def.text(),
                "Your first gate will open somewhere outside the city walls.", dev.awaken.core.api.NoticeEvents.Kind.TASK,
                "Clear the first floor of a gate. Your Tasks page has it.", "Reward: " + RewardWords.describe(def.reward()) + ".");
        DirectorLog.log(player, "directive", "the Registry's own: " + OPENING + ", accepted");
        Progression.markDirty(player);
        return true;
    }

    /** On login: an old save's Directives other than the Registry's drop quietly, and so does its Daily Regimen. */
    static void retire(ServerPlayer player) {
        Profile p = Progression.profile(player);
        boolean changed = p.directives.removeIf(d -> !d.id().equals(OPENING));
        if (!p.regimen.drills().isEmpty() || p.regimen.day() >= 0) {
            p.regimen = Profile.RegimenState.EMPTY;
            changed = true;
        }
        if (changed) Progression.markDirty(player);
    }

    static void evaluate(ServerPlayer player, Profile p) {
        for (int i = p.directives.size() - 1; i >= 0; i--) {
            var d = p.directives.get(i);
            var def = Content.DIRECTIVES.get(d.id());
            if (def == null || !d.id().equals(OPENING)) { p.directives.remove(i); continue; }
            if (!def.condition().test(p, d.baseline())) continue;
            p.directives.remove(i);
            p.counters.merge("awaken:directive_done/" + d.id(), 1L, Long::sum);
            dev.awaken.core.audio.AudioCues.ui(player, dev.awaken.core.audio.UiSound.QUEST_DONE);
            ProgressionNotices.tell(player, "directive/" + d.id() + "/" + p.counter("awaken:directive_done/" + d.id()),
                    "First job done", def.text(), "Done, and the reward is yours.", dev.awaken.core.api.NoticeEvents.Kind.TASK,
                    "Reward: " + RewardWords.describe(def.reward()) + ".");
            RewardHooks.apply(player, def.reward(), Progression.XpSource.DIRECTIVE);
            Progression.markDirty(player);
            return;
        }
    }

    /** Progress of the Registry's Directive in [0,1] (for Now and the Tasks page). */
    public static double progress(Profile p, Profile.DirectiveState d) {
        var def = Content.DIRECTIVES.get(d.id());
        return def == null || !d.accepted() ? 0 : def.condition().progress(p, d.baseline());
    }
}
