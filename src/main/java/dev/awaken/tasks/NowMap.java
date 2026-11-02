package dev.awaken.tasks;

import dev.awaken.core.api.Goals;
import dev.awaken.core.api.Goals.Goal;
import dev.awaken.core.api.NowHooks;

import java.util.List;

/**
 * Where each Now suggestion goes on the Tasks page (SPEC §27.13): its goal, its place on the main path, one line of why,
 * and whether it's an optional upgrade, by the start of its id. A module that knows more about a step registers it
 * through {@link Goals} and covers the suggestion instead; anything not listed here is "out there", shown on Now only.
 */
final class NowMap {
    private NowMap() {}

    record Rule(String prefix, Goal goal, int path, String why, boolean optional) {}

    private static Rule r(String prefix, Goal goal, int path, String why) {
        return new Rule(prefix, goal, path, why, false);
    }

    private static Rule r(String prefix, Goal goal, String why) {
        return new Rule(prefix, goal, Goals.NO_PATH, why, false);
    }

    /** First match wins, so the particular comes before the general. */
    static final List<Rule> RULES = List.of(
            r("opening:", Goal.RANK, Goals.PATH_START, ""),
            r("story:ritual", Goal.RANK, Goals.PATH_START, ""),
            r("powers:first_power", Goal.RANK, Goals.PATH_START, "Every hunter starts with one power. Pick yours on the band."),
            r("hearth:ember", Goal.HEARTH, Goals.PATH_FIRE, "Your home starts at your fire: resting there heals you, and the Hearth reads what you build round it."),
            r("hearth:shelter", Goal.HEARTH, Goals.PATH_SHELTER, "Walls and a roof round your fire make your first room."),
            r("hearth:place", Goal.HEARTH, Goals.PATH_ANCHOR, "The Anchor makes your fire a home: more rooms, more nooks for beasts."),
            r("hearth:craft", Goal.HEARTH, Goals.PATH_ANCHOR, "The Anchor makes your fire a home: more rooms, more nooks for beasts."),
            r("hearth:heart", Goal.HEARTH, Goals.PATH_ANCHOR, "The fire takes the Heart and stands as your Anchor."),
            new Rule("hearth:room/sanctuary", Goal.HEARTH, Goals.NO_PATH, "Sleeping there heals a Fracture.", true),
            r("hearth:mend", Goal.HEARTH, "A Fracture weakens you until you rest it off at home."),
            r("hearth:", Goal.HEARTH, ""),
            r("request:", Goal.HEARTH, ""),
            r("kin:", Goal.HEARTH, ""),
            r("armour:", Goal.ARMOUR, "Better armour than you wear."),
            r("arms:", Goal.WEAPONS, ""),
            r("gear:", Goal.WEAPONS, ""),
            r("story:bell_crypt", Goal.BELLS, ""),
            r("bells:", Goal.BELLS, ""),
            r("progression:reassessment", Goal.RANK, Goals.PATH_TRIAL, "Pass it to rise a rank: new gates, new armour, new powers."),
            r("progression:capability", Goal.RANK, "Gate floors give the most XP."),
            r("progression:points", Goal.RANK, "Points you haven't spent make you no stronger."),
            r("gate:near:", Goal.RANK, "Gate floors give the most XP, and every gate's Lord drops a Lord's Heart."),
            r("gate:", Goal.RANK, ""),
            r("progression:", Goal.RANK, ""));

    static Rule rule(String id) {
        for (Rule r : RULES) if (id.startsWith(r.prefix)) return r;
        return new Rule("", Goal.OUT, Goals.NO_PATH, "", false);
    }

    /** The suggestion as a step: its id is its key, its words its title. */
    static Goals.Step step(NowHooks.Suggestion s) {
        Rule r = rule(s.id());
        Goals.Builder b = Goals.Step.of(s.id(), r.goal, s.text().getString()).why(r.why).path(r.path).priority(s.priority());
        s.where().ifPresent(b::at);
        if (r.optional) b.optional();
        return b.build();
    }
}
