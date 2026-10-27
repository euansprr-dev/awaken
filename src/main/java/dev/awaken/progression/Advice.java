package dev.awaken.progression;

import net.minecraft.server.level.ServerPlayer;

/**
 * "Anything for me?" (SPEC §27.8, after Terraria's Guide): the one next step that fits this player now, in a person's
 * voice. Asked of the Registry's clerks. Never a list: the most relevant thing, in this order: a Reassessment waiting, a
 * lock that now answers them, points to spend, a Directive taken, the next rank's promise, how hunters grow.
 */
public final class Advice {
    private Advice() {}

    public static String next(ServerPlayer p) {
        Profile prof = Progression.profile(p);
        if (!prof.awakened()) return "Not yet. The Lens hasn't assessed you.";
        Rank next = prof.rank().next();
        if (prof.reassessmentReady() && next != null) {
            return "You've outgrown " + prof.rank().label + ". Take the " + next.label + "-rank Reassessment at a Monolith, or the Spire if you've a Hearth.";
        }
        for (PromiseBook.Entry e : PromiseKeeper.book(p).entries()) {
            if (e.answered()) return PromiseKeeper.ready(e) + ". You know where to find it.";
        }
        if (prof.unspent() > 0) return "You have points you haven't spent yet. Open your band to spend them.";
        for (Profile.DirectiveState d : prof.directives()) {
            var def = d.accepted() ? dev.awaken.progression.content.Content.DIRECTIVES.get(d.id()) : null;
            if (def != null) return "The Signal has you working on this: " + def.text();
        }
        String trail = dev.awaken.core.api.TrailsApi.suggest(p);
        if (trail != null) return "You have a trail with a next step. " + trail;
        if (next != null) {
            var opens = new java.util.ArrayList<String>();
            for (PromiseBook.Entry e : PromiseKeeper.book(p).entries()) {
                if (e.kind().equals(dev.awaken.core.api.Promises.RANK) && e.value().equalsIgnoreCase(next.label)) opens.add(e.what().toLowerCase(java.util.Locale.ROOT));
            }
            if (!opens.isEmpty()) return next.label + "-rank at level " + next.levelRequired + ". It opens " + opens.getFirst() + " for you, and more.";
            return next.label + "-rank at level " + next.levelRequired + ". Clearing gate floors is how you get there.";
        }
        return "I don't have anything new for you right now.";
    }
}
