package dev.awaken.progression;

import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The Day's End card: every session can end cleanly, with a reason to come back that doesn't depend on the clock.
 * Other modules contribute "waiting for you" lines (a gate about to break, an unsolved vision).
 */
public final class DaysEnd {
    private DaysEnd() {}

    private static final List<Function<ServerPlayer, String>> WAITING = new ArrayList<>();

    /** Add a provider of one "waiting for you" line (return null for nothing). */
    public static void addWaiting(Function<ServerPlayer, String> provider) {
        WAITING.add(provider);
    }

    /** Shows the Day's End card now (other modules call this, e.g. after a dream ends). */
    public static void show(ServerPlayer player) {
        Profile p = Progression.profile(player);
        if (p.awakened() && p.day().day() >= 0) send(player, p);
    }

    static void send(ServerPlayer player, Profile p) {
        var d = p.day();
        int levels = p.level() - d.levelStart();
        var w = SignalWindow.builder(SignalWindow.Kind.DAYS_END, Component.literal("Day " + (d.day() + 1) + " ends"));
        List<String> today = new ArrayList<>();
        if (levels > 0) today.add("+" + levels + " level" + (levels > 1 ? "s" : ""));
        if (d.gates() > 0) today.add(d.gates() + " gate" + (d.gates() > 1 ? "s" : ""));
        if (d.kills() > 0) today.add(d.kills() + " defeated");
        w.row(Component.literal("Today"), Component.literal(today.isEmpty() ? "A quiet day" : String.join(" · ", today)));
        if (!d.best().isEmpty()) w.row(Component.literal("Best moment"), Component.literal(d.best()));
        int shown = 0;
        for (var provider : WAITING) {
            String line = provider.apply(player);
            if (line != null && shown < 2) {
                w.row(Component.literal(shown == 0 ? "Waiting" : ""), Component.literal(line));
                shown++;
            }
        }
        if (p.reassessmentReady()) w.row(Component.literal("Ready"), Component.literal("Your Reassessment"));
        w.footer(Component.literal("A good place to stop · or keep going"));
        Signal.send(player, w.ttl(20 * 12).priority(2).build());
    }
}
