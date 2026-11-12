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
 * kind you've met, as a plain step that says what to do for it (Euan 2026-11-12), until one of its kind lives with you.
 * A kind you haven't met or heard of stays unnamed (the need is the title); nothing here asks for a rumour, so nothing
 * is learned by looking.
 */
final class KinGoals {
    private KinGoals() {}

    /** Beast needs on the tab at once (wants read are shown besides). */
    static final int SHOWN = 2;

    static void register() {
        Goals.register(KinGoals::steps);
    }

    /** "beasts:2": that many beasts live at your home (the Your Village trail). */
    static void registerTrailTokens() {
        dev.awaken.core.api.TrailsApi.condition("beasts", (p, rest) -> {
            int want;
            try {
                want = Integer.parseInt(rest);
            } catch (NumberFormatException e) {
                return false;
            }
            return Hearth.home(p).map(h -> KinWorld.get(p.level().getServer()).at(h.anchor()).size() >= want).orElse(false);
        });
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
        List<KinKind> order = new ArrayList<>(List.of(KinPage.NEEDS));
        order.sort(java.util.Comparator.comparing(k -> !waitedFor(level, home, k))); // stable: a room with nobody to work it first (SPEC §53)
        for (KinKind k : order) {
            if (needs >= SHOWN) break;
            if (living.contains(k)) continue;
            String need = KinPage.need(level, info, k);
            if (need == null) continue;
            out.add(step(p, ks, k, need, home, "kin/need/" + k.id(), 32 - needs));
            needs++;
        }
        // a kind you've met is a plain step, whether or not the home needs that kind yet: what to do for it
        for (KinKind k : KinKind.values()) {
            Kinship.Kind s = ks.kinds.get(k);
            if (s == null || !s.met || !k.built || living.contains(k)) continue;
            boolean shown = false;
            for (Step o : out) shown |= o.key().equals("kin/need/" + k.id());
            if (!shown) out.add(step(p, ks, k, "", home, "kin/want/" + k.id(), 28));
        }
        return out;
    }

    /** Whether a room that stands at home needs this kind to work it. */
    private static boolean waitedFor(ServerLevel level, BlockPos home, KinKind k) {
        dev.awaken.hearth.room.RoomType room = switch (k) {
            case TOTTER -> dev.awaken.hearth.room.RoomType.STOREHOUSE;
            case PEBBLEPUP -> dev.awaken.hearth.room.RoomType.MINE_HEAD;
            case EMBERKIT -> dev.awaken.hearth.room.RoomType.SMELTERY;
            case CHIMEWREN -> dev.awaken.hearth.room.RoomType.ROOKERY;
            default -> null;
        };
        return room != null && dev.awaken.kin.works.WorkRooms.room(level, home, room) != null;
    }

    /** The first fire's Lampwing: what to do for it, plainly (Euan 2026-11-12: "how is that going to help you?"). */
    static final String LESSON = "It comes to warm light. At night, set a torch or a lantern on the ground near the trees, within "
            + KinHelp.NEAR + " blocks of it. Do that three times, a few seconds apart, and it trusts you and settles by your fire.";

    /**
     * One beast's step (SPEC §43.5, §53). Once you've met a kind or heard where one lives, the step says plainly what to do
     * for it ({@link KinHelp#howTo}), and how it comes home; watching one only adds its own lines on the page. Else what
     * the home lacks, the kind unnamed.
     */
    static Step step(ServerPlayer p, Kinship ks, KinKind k, String need, BlockPos home, String key, int priority) {
        Kinship.Kind s = ks.kinds.get(k);
        boolean lesson = k == KinKind.LAMPWING && ks.lesson == 1;
        String title, why;
        BlockPos at = null;
        if (lesson) {
            boolean met = s != null && s.met;
            title = met ? "Make friends with the Lampwing at your fire" : "Set out a light for what's watching your fire";
            why = (met ? "The Lampwing" : "Something") + " has been watching your fire from the trees. " + LESSON;
        } else if (s != null && (s.met || s.heard)) {
            String where = "";
            if (s.met) where = "You've met one: " + k.where + ".";
            else if (s.rumour) {
                at = new BlockPos(s.rumourX, 64, s.rumourZ);
                where = KinSpawner.rumourLine(p, k, at, home, true);
            }
            title = "Bring " + article(k.title) + " home";
            why = join(need, where, KinHelp.howTo(k), homeward(p, k, need));
            if (ks.afraid(k, p.level().getGameTime())) why += " Its kind is wary of you for now, after what happened. Give them a few days.";
        } else { // the need's first sentence is the step; what follows says what the home still lacks for it
            int cut = need.indexOf(". ");
            title = cut < 0 ? need : need.substring(0, cut + 1);
            why = cut < 0 ? "" : need.substring(cut + 2);
        }
        Goals.Builder b = Step.of(key, Goal.HEARTH, title).thread("hearth.beasts", 100 - priority).lead().why(why.trim()).priority(priority);
        if (at != null) b.at(KinSpawner.rough(p, k, at)); // roughly, as Track points: never the spot
        return b.build();
    }

    /**
     * How a helped beast comes home: three helps and it trusts you, then it follows a few steps behind. Where the home has
     * no nook it would like, what it needs, unless the need has said so already.
     */
    static String homeward(ServerPlayer p, KinKind k, String need) {
        String out = "Do that three times and it trusts you. Then it follows you home, a few steps behind; get too far ahead and it stops, so go back for it.";
        if (need.indexOf(". ") >= 0) return out; // the need says what the home still lacks for it
        var h = Hearth.home(p);
        if (h.isEmpty() || KinHome.nookFor(p.level(), h.get(), k) == null) out += " It will only stay if there's " + k.nook + ".";
        return out;
    }

    private static String join(String... parts) {
        StringBuilder out = new StringBuilder();
        for (String part : parts) if (!part.isEmpty()) out.append(out.isEmpty() ? "" : " ").append(part);
        return out.toString();
    }

    static String article(String title) {
        return ("AEIOU".indexOf(Character.toUpperCase(title.charAt(0))) >= 0 ? "an " : "a ") + title;
    }
}
