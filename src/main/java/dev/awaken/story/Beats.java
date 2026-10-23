package dev.awaken.story;

import dev.awaken.core.DirectorLog;
import dev.awaken.progression.Counters;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;

/**
 * Revelations (fired once by their trigger) and the Signal's one-line reactions to firsts. Triggers:
 * {@code counter:awaken:x>=N}, {@code rank:D}, {@code flag:awaken:x}, {@code trigger:name} (fired by code only).
 */
final class Beats {
    private Beats() {}

    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 100 != 37) return;
        Profile prof = Progression.profile(p);
        if (!prof.awakened()) return;
        StoryProgress s = Prologue.progress(p);
        for (Map.Entry<Identifier, StoryContent.Revelation> e : StoryContent.REVELATIONS.entrySet()) {
            String key = "revelation:" + e.getKey().getPath();
            if (s.seen.contains(key)) continue;
            if (triggered(prof, e.getValue().trigger())) {
                s.seen.add(key);
                Prologue.sync(p);
                show(p, e.getValue());
                DirectorLog.log(p, "story", "revelation " + e.getKey().getPath());
                return; // one per check, so they never stack up
            }
        }
        lineOnce(p, s, "first_echo", prof.counter(Counters.ECHOES_BOUND) >= 1);
        lineOnce(p, s, "first_stratum", prof.counter(Counters.DEPTH_RECORD) >= 1);
        lineOnce(p, s, "night_awake", prof.counter("awaken:nights_awake") >= 1);
        lineOnce(p, s, "directive_dismissed_many", prof.counter("awaken:directives_dismissed") >= 5);
        lineOnce(p, s, "sovereign_ready", prof.rank() == Rank.SOVEREIGN);
        lineOnce(p, s, "break_cleared", prof.counter(Counters.BREAKS_HELD_AT_HEARTH) >= 1);
    }

    static boolean triggered(Profile p, String trigger) {
        try {
            if (trigger.startsWith("counter:")) {
                String body = trigger.substring("counter:".length());
                int at = body.indexOf(">=");
                return p.counter(body.substring(0, at)) >= Long.parseLong(body.substring(at + 2));
            }
            if (trigger.startsWith("rank:")) {
                return p.rank().ordinal() >= Rank.valueOf(trigger.substring(5)).ordinal();
            }
            if (trigger.startsWith("flag:")) {
                return p.counter(trigger.substring(5)) >= 1;
            }
        } catch (RuntimeException bad) {
            return false;
        }
        return false; // "trigger:" revelations fire from code
    }

    static void show(ServerPlayer p, StoryContent.Revelation r) {
        p.level().getServer().execute(() -> StoryModule.sendSeen(p));
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.REVELATION, Component.literal(r.title()))
                .body(Component.literal(r.body())).priority(8).ttl(20 * 12).build());
    }

    /** Fire a revelation by its file name (for "trigger:" ones and code-driven moments). */
    static void fire(ServerPlayer p, String key) {
        StoryProgress s = Prologue.progress(p);
        if (!s.seen.add("revelation:" + key)) return;
        Prologue.sync(p);
        StoryContent.Revelation r = StoryContent.revelation(key);
        if (r != null) show(p, r);
    }

    static void lineOnce(ServerPlayer p, StoryProgress s, String key, boolean condition) {
        if (!condition || !s.seen.add("line:" + key)) return;
        Prologue.sync(p);
        String line = StoryContent.SIGNAL_LINES.get(key);
        if (line != null) Signal.toast(p, Component.literal(line));
    }

    /** Progression "new thing" moments that have their own line. */
    static void onNewThing(ServerPlayer p, String what) {
        StoryProgress s = Prologue.progress(p);
        String kind = what.indexOf(' ') > 0 ? what.substring(0, what.indexOf(' ')) : what; // "power Haymaker" -> "power"
        switch (kind) {
            case "power" -> lineOnce(p, s, "first_power", true);
            case "title" -> lineOnce(p, s, "first_title", true);
            case "latent" -> lineOnce(p, s, "first_latent", true);
            default -> {}
        }
    }
}
