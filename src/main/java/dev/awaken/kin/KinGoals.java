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
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The beasts on the Tasks page's Hearth tab (SPEC §27.13; Euan 2026-10-30: "The kin should be part of the hearth tab"):
 * the home's first two beast needs, in the order a home meets them, each with the rumour you already have, and every
 * want you've read by watching a beast, as a plain step, until one of its kind lives with you. A kind you haven't met or
 * heard of stays unnamed (the need is the title); nothing here asks for a rumour, so nothing is learned by looking.
 */
final class KinGoals {
    private KinGoals() {}

    /** Beast needs on the tab at once (wants read are shown besides). */
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
        Set<KinKind> living = EnumSet.noneOf(KinKind.class);
        for (KinWorld.Homed m : homed) living.add(m.kind());
        Kinship ks = Kinship.of(p);
        List<Step> out = new ArrayList<>();
        int needs = 0;
        for (KinKind k : KinPage.NEEDS) {
            if (needs >= SHOWN) break;
            if (living.contains(k)) continue;
            String need = KinPage.need(level, info, k);
            if (need == null) continue;
            out.add(step(p, ks, k, need, home, "kin/need/" + k.id(), 32 - needs));
            needs++;
        }
        // a want read by watching is a plain step, whether or not the home needs that kind yet
        for (KinKind k : KinKind.values()) {
            Kinship.Kind s = ks.kinds.get(k);
            if (s == null || s.watched < 3 || living.contains(k) || k.watch.size() < 3) continue;
            boolean shown = false;
            for (Step o : out) shown |= o.key().equals("kin/need/" + k.id());
            if (!shown) out.add(step(p, ks, k, "", home, "kin/want/" + k.id(), 28));
        }
        return out;
    }

    /** One beast's step: what it wants once you've read it, else what to do to find one, else the need itself. */
    static Step step(ServerPlayer p, Kinship ks, KinKind k, String need, BlockPos home, String key, int priority) {
        Kinship.Kind s = ks.kinds.get(k);
        boolean lesson = k == KinKind.LAMPWING && ks.lesson == 1;
        String title, why;
        BlockPos at = null;
        if (s != null && s.watched >= 3) {
            title = "Give the " + k.title + " what it wants";
            why = k.watch.get(2) + " Help one three times and it trusts you; then it follows you home.";
        } else if (lesson) {
            title = "Watch what's been watching your fire";
            why = "Something has been watching your fire from the trees. Go out at night and watch it for a moment.";
        } else if (s != null && (s.met || s.heard)) {
            String where = "";
            if (s.met) where = "You've met one: " + k.where + ".";
            else if (s.rumour) {
                at = new BlockPos(s.rumourX, 64, s.rumourZ);
                where = KinSpawner.rumourLine(p, k, at, home, true);
            }
            title = "Bring " + article(k.title) + " home";
            why = need + (where.isEmpty() ? "" : " " + where) + (s.met ? " Watch one for a while to see what it wants." : "");
        } else { // the need's first sentence is the step; what follows says what the home still lacks for it
            int cut = need.indexOf(". ");
            title = cut < 0 ? need : need.substring(0, cut + 1);
            why = cut < 0 ? "" : need.substring(cut + 2);
        }
        Goals.Builder b = Step.of(key, Goal.HEARTH, title).why(why.trim()).priority(priority);
        if (at != null) b.at(KinSpawner.rough(p, k, at)); // roughly, as Track points: never the spot
        return b.build();
    }

    static String article(String title) {
        return ("AEIOU".indexOf(Character.toUpperCase(title.charAt(0))) >= 0 ? "an " : "a ") + title;
    }
}
