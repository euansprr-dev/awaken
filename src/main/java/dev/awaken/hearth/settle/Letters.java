package dev.awaken.hearth.settle;

import dev.awaken.hearth.HearthRecord;
import dev.awaken.village.api.Villages;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Pell's post (PLAN 4.7: "a letter and a rumour each play-day"): a letter from the village that knows you best (its own
 * news, from its history: what happened there, what you did), else from the city. The words are the villages' record,
 * never invented events.
 */
final class Letters {
    private Letters() {}

    /** The village that knows this player best, among the running ones (Known or better), or null. */
    static Villages.@Nullable View dearest(ServerLevel level, ServerPlayer p) {
        Villages.View best = null;
        int bs = Villages.KNOWN - 1;
        for (Villages.View v : Villages.all(level)) {
            int s = Villages.standing(p, v.key());
            if (s > bs) {
                bs = s;
                best = v;
            }
        }
        return best;
    }

    static String title(ServerLevel level, ServerPlayer p) {
        Villages.View v = dearest(level, p);
        return "A letter from " + (v == null ? "Carillon" : v.name());
    }

    static String body(ServerLevel level, ServerPlayer p, HearthRecord h) {
        Villages.View v = dearest(level, p);
        if (v == null) {
            return "“The bells hold the city, they say, and you hold the wilds. Mind the north road after dark. The Registry asks after "
                    + p.getName().getString() + ".” · from the Association, by Pell's hand";
        }
        List<String> past = Villages.history(level.getServer(), v.key());
        String news = past.isEmpty() ? "Nothing's burned and nobody's lost, which round here counts as news." : past.getLast();
        int s = Villages.standing(p, v.key());
        String warmth = s >= Villages.KIN ? "There's always a bed here, and you know it." : s >= Villages.FRIEND ? "Come by when the road allows."
                : "We hear you've a hearth of your own now.";
        return "“" + news + " " + warmth + "” · from " + v.name() + ", for " + h.displayName();
    }
}
