package dev.awaken.progression;

import dev.awaken.core.api.NowHooks;
import dev.awaken.core.api.NowHooks.Suggestion;

import java.util.ArrayList;
import java.util.List;

/** Progression's lines for the Now tracker: points to spend, a Reassessment waiting, the next level-road unlock. */
final class ProgressionNow {
    private ProgressionNow() {}

    /** Game ticks without a new capability before the Now line offers the nearest one (75 minutes). */
    static final long CAPABILITY_GAP = 20L * 60 * 75;

    /** The nearest new thing to do: a lock that is open to you now (with its place), else the next rank and how to get there. */
    static Suggestion capability(net.minecraft.server.level.ServerPlayer player, Profile p) {
        String dim = player.level().dimension().identifier().toString();
        for (PromiseBook.Entry e : PromiseKeeper.book(player).entries()) {
            // a land you already stand in has nothing to walk to; a place high up shows its height (NowWay), never "here"
            if (e.answered() && e.where().isPresent() && e.dim().equals(dim) && !PromiseKeeper.inside(player, e)) {
                return Suggestion.at("progression:capability", 57, PromiseKeeper.ready(e), e.where().get());
            }
        }
        Rank next = p.rank().next();
        if (next == null || p.reassessmentReady()) return null;
        return Suggestion.of("progression:capability", 50, next.label + "-rank opens at level " + next.levelRequired + ": gate floors give the most XP");
    }

    static void register() {
        NowHooks.register(player -> {
            Profile p = Progression.profile(player);
            if (!p.awakened()) return List.of();
            List<Suggestion> out = new ArrayList<>(3);
            if (p.reassessmentReady() && p.rank().next() != null) {
                // where to take it (SPEC §27.9): the Spire at your Hearth, else a Monolith you craft
                var hearth = dev.awaken.core.api.HearthHooks.hearthOf(player);
                String text = "Your " + p.rank().next().label + "-rank trial is ready";
                out.add(hearth.isPresent() ? Suggestion.at("progression:reassessment", 72, text + ": take it at the Spire in your town", hearth.get())
                        : Suggestion.of("progression:reassessment", 72, text + ": craft a Reassessment Monolith and use it"));
            }
            // the capability clock (SPEC §27.9): 75 minutes with nothing new to do, outdoors: the nearest new thing in reach
            // set or not (a young world's clock can make "75 minutes ago" negative, so never test it against 0)
            Long cap = p.counters.get(Progression.CAPABILITY_AT);
            long now = player.level().getGameTime();
            if (cap != null && now - cap > CAPABILITY_GAP && !dev.awaken.core.api.GateHooks.inGate(player)) {
                Suggestion near = capability(player, p);
                if (near != null) out.add(near);
            }
            // the Registry's Directive, with how far along (SPEC §27.6; the only one left since §27.13)
            for (Profile.DirectiveState d : p.directives()) {
                var def = d.accepted() ? dev.awaken.progression.content.Content.DIRECTIVES.get(d.id()) : null;
                if (def == null) continue;
                int pct = (int) Math.round(Directives.progress(p, d) * 100);
                out.add(Suggestion.of("progression:directive/" + d.id(), 58, def.text() + (pct > 0 ? " · " + pct + "%" : "")));
            }
            if (p.unspent() > 0) {
                out.add(Suggestion.of("progression:points", 55,
                        "Press {key.awaken.status} to spend " + p.unspent() + " point" + (p.unspent() == 1 ? "" : "s")));
            }
            LevelRoad.Step next = LevelRoad.next(p);
            if (next != null) {
                out.add(Suggestion.of("progression:road/" + next.id(), 15,
                        "At level " + next.def().level() + ": " + next.def().title()));
            }
            return out;
        });
    }
}
