package dev.awaken.gates.realms.kit.land;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.realms.Blueprint;
import dev.awaken.gates.realms.Land;
import dev.awaken.gates.realms.LandPrograms;
import dev.awaken.gates.realms.check.FloatCheck;
import dev.awaken.gates.realms.check.RouteCheck;
import dev.awaken.gates.realms.check.WalkCheck;
import dev.awaken.progression.Rank;

import java.util.List;
import java.util.Map;

/**
 * The rolled lands' own tests (SPEC §50.9, "Rolled lands"): a land rolled from a seed has to play whatever the seed, so
 * each is walked from the way in to every site and leg, held to the same route check a designed run is, and scanned for
 * anything floating. The seeds are the ones the suite uses.
 */
public final class LandTests {
    private LandTests() {}

    /** A rolled land as a gate would make it: the skin's program, the landform by its place in {@link Landform.Kind}. */
    static Land land(String program, String theme, Landform.Kind kind, String lord, Rank rank, long seed) {
        Blueprint bp = new Blueprint(1, "t", "land", theme, List.of("E"), new Blueprint.LandSpec(program, GeneratedLand.WATER, Map.of("landform", (double) kind.ordinal())),
                new Blueprint.Sky(List.of("awaken:realm/cinder_crown"), "", List.of()), new Blueprint.Life(List.of(), List.of(), List.of()), new Blueprint.Folk("none", 0),
                new Blueprint.Law("none", Map.of()), new Blueprint.Sites(1, 0, 6), new Blueprint.Heart(lord, ""), List.of(), "");
        return LandPrograms.make(new Land.Ctx(bp, program, rank, seed, 1));
    }

    private static void walks(net.minecraft.gametest.framework.GameTestHelper h, String program, String theme, Landform.Kind kind, String lord) {
        for (long seed : new long[]{1, 2}) {
            Land land = land(program, theme, kind, lord, Rank.E, seed);
            WalkCheck.Walk walk = WalkCheck.walk(land);
            if (!walk.unreachable().isEmpty()) h.fail("seed " + seed + ": can't reach " + walk.unreachable());
            RouteCheck.Report route = RouteCheck.check(land, walk);
            if (!route.problems().isEmpty()) h.fail("seed " + seed + ": " + route.summary() + ": " + route.problems());
            if (land.change("heart_open").isEmpty() || land.change("heart_shut").isEmpty()) h.fail("seed " + seed + ": the Lord's door doesn't open and shut");
        }
        h.succeed();
    }

    private static void floats(net.minecraft.gametest.framework.GameTestHelper h, String program, String theme, Landform.Kind kind, String lord) {
        Land land = land(program, theme, kind, lord, Rank.E, 1);
        int half = land.halfSize();
        FloatCheck.Report r = FloatCheck.scan(land, -half, -half, half, half);
        if (!r.clean()) h.fail(r.summary());
        h.succeed();
    }

    public static void register() {
        AwakenTests.register("realm_cinder_crown_walks_to_everything", 4800, h -> walks(h, "awaken:ember_country", "ember_mines", Landform.Kind.ASCENT, "ember_tyrant"));
        AwakenTests.register("realm_cinder_crown_nothing_floats", 4800, h -> floats(h, "awaken:ember_country", "ember_mines", Landform.Kind.ASCENT, "ember_tyrant"));
        AwakenTests.register("realm_fallwater_vale_walks_to_everything", 4800, h -> walks(h, "awaken:temple_country", "overgrown_temple", Landform.Kind.VALLEY, "root_saint"));
        AwakenTests.register("realm_fallwater_vale_nothing_floats", 4800, h -> floats(h, "awaken:temple_country", "overgrown_temple", Landform.Kind.VALLEY, "root_saint"));
    }
}
