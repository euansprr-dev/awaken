package dev.awaken.kin;

import dev.awaken.core.api.Goals;
import dev.awaken.core.api.Goals.Goal;
import dev.awaken.core.api.Goals.Step;
import dev.awaken.hearth.Hearth;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The beasts on the Tasks page's Hearth tab (SPEC §27.13; Euan 2026-10-30: "The kin should be part of the hearth tab"):
 * the home's first two beast needs, in the order a home meets them, each with the rumour you already have. A kind you
 * haven't met or heard of stays unnamed (the need is the title); nothing here asks for a rumour, so nothing is learned by
 * looking. A need goes the moment a beast of its kind lives with you (SPEC §27.12).
 */
final class KinGoals {
    private KinGoals() {}

    /** Beast needs on the tab at once. */
    static final int SHOWN = 2;

    static void register() {
        Goals.register(KinGoals::steps);
    }

    static List<Step> steps(ServerPlayer p) {
        if (!Progression.profile(p).awakened()) return List.of();
        Optional<Hearth.HearthInfo> h = Hearth.home(p);
        if (h.isEmpty()) return List.of();
        ServerLevel level = p.level();
        Hearth.HearthInfo info = h.get();
        BlockPos home = info.anchor();
        List<KinWorld.Homed> homed = KinWorld.get(level.getServer()).at(home);
        if (homed.size() >= KinHome.nooks(level, info)) return List.of(); // every nook taken: the next tier's step says so
        Map<KinKind, Integer> living = new EnumMap<>(KinKind.class);
        for (KinWorld.Homed m : homed) living.merge(m.kind(), 1, Integer::sum);
        Kinship ks = Kinship.of(p);
        List<Step> out = new ArrayList<>();
        for (KinKind k : KinPage.NEEDS) {
            if (out.size() >= SHOWN) break;
            if (living.containsKey(k)) continue;
            String need = KinPage.need(level, info, k);
            if (need == null) continue;
            Kinship.Kind s = ks.kinds.get(k);
            boolean known = s != null && (s.met || s.heard);
            String where = "";
            BlockPos at = null;
            if (k == KinKind.LAMPWING && ks.lesson == 1) where = "The one that's been watching your fire, from the trees nearby.";
            else if (s != null && s.met) where = "You've met one: " + k.where + ".";
            else if (s != null && s.rumour) {
                at = new BlockPos(s.rumourX, 64, s.rumourZ);
                where = KinSpawner.rumourLine(p, k, at, home, true);
            }
            String title, why;
            if (known) {
                title = "Bring " + article(k.title) + " home";
                why = need + (where.isEmpty() ? "" : " " + where);
            } else { // the need's first sentence is the step; what follows says what the home still lacks for it
                int cut = need.indexOf(". ");
                title = cut < 0 ? need : need.substring(0, cut + 1);
                why = cut < 0 ? where : need.substring(cut + 2) + (where.isEmpty() ? "" : " " + where);
            }
            Goals.Builder b = Step.of("kin/need/" + k.id(), Goal.HEARTH, title).why(why).priority(32 - out.size());
            if (at != null) b.at(at);
            out.add(b.build());
        }
        return out;
    }

    static String article(String title) {
        return ("AEIOU".indexOf(Character.toUpperCase(title.charAt(0))) >= 0 ? "an " : "a ") + title;
    }
}
