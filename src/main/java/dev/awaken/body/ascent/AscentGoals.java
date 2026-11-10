package dev.awaken.body.ascent;

import dev.awaken.core.api.Goals;
import dev.awaken.core.api.Goals.Goal;
import dev.awaken.core.api.Goals.Need;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * The moves the rope and the wings earn, on the Tasks page (SPEC §62; every progression step shows there: SPEC §27.13).
 * One row at a time and only for a rung the player holds: what the next move is and how far the mastery that opens it
 * has come, then how to make it, until it has been made once. New ways of moving speak as leads (SPEC §53): the kind of
 * thing, never a list of what comes after.
 */
final class AscentGoals {
    private AscentGoals() {}

    /** Counters (Progression's): each move made at least once. */
    static final String SLUNG = "ascent_slung", SPRUNG = "ascent_sprung", STRUCK = "ascent_struck", FLARED = "ascent_flared",
            SKIMMED = "ascent_skimmed", TOOK_OFF = "ascent_took_off";

    static void register() {
        Goals.register(AscentGoals::steps);
    }

    static List<Goals.Step> steps(ServerPlayer p) {
        Profile profile = Progression.profile(p);
        if (!profile.awakened()) return List.of();
        AscentMemory mem = p.getData(AscentContent.MEMORY);
        List<Goals.Step> out = new ArrayList<>(2);
        if (Ascent.has(p, Ascent.Tier.TETHER)) {
            int rope = mem.level(AscentMemory.Track.TETHER);
            if (profile.counter(SPRUNG) == 0) {
                out.add(move("ascent/spring", "Spring off a rooftop", "The rope pulls you onto a roof or a treetop and you leave it at a run.",
                        rope, AirRules.SPRING_LEVEL, "Rope mastery", "Hold forward as the rope pulls you up onto a top", 0));
            } else if (profile.counter(STRUCK) == 0) {
                out.add(move("ascent/strike", "Strike from the rope", "The rope catches a creature out of reach and pulls you into it.",
                        rope, AirRules.STRIKE_LEVEL, "Rope mastery", "On the rope or in the air, attack a creature that is out of reach", 1));
            }
        }
        if (Ascent.has(p, Ascent.Tier.SKYBORN) && profile.counter(TOOK_OFF) == 0) {
            out.add(move("ascent/takeoff", "Take off from the ground", "Your wings throw you straight up into flight.",
                    1, 1, "Flight", "Look up, sneak for a moment, then jump", 0));
        } else if (Ascent.has(p, Ascent.Tier.WINGS) && profile.counter(SKIMMED) == 0) {
            out.add(move("ascent/skim", "Skim the ground on your wings", "Low and fast, the air under your wings carries you much further.",
                    mem.level(AscentMemory.Track.WINGS), AirRules.SKIM_LEVEL, "Wings mastery", "Glide fast within a few blocks of the ground, the water or a wall", 1));
        }
        return out;
    }

    private static Goals.Step move(String key, String title, String why, int level, int needed, String mastery, String how, int order) {
        boolean rope = key.equals("ascent/spring") || key.equals("ascent/strike");
        Goals.Builder b = Goals.Step.of(key, Goal.OUT, title).why(why).optional().lead().thread(rope ? "ascent/rope" : "ascent/wings", order);
        // the mastery first (it grows as you use the rope or the wings); once it is there, the move itself
        if (level < needed) b.need(new Need("", mastery + " " + needed, level, needed));
        else b.need(Need.of(how, false));
        return b.build();
    }
}
