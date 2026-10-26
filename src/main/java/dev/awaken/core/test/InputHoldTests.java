package dev.awaken.core.test;

import dev.awaken.core.InputHolds;

/**
 * The shared input lock's bookkeeping (SPEC §27.6, "Control comes back"): a stand-in "player" whose input is a string,
 * driven through every way a scene ends. The client's lock is this with the real player and input.
 */
final class InputHoldTests {
    private InputHoldTests() {}

    /** A player object with one input slot. */
    private static final class Body {
        String input;

        Body(String input) {
            this.input = input;
        }
    }

    static void register() {
        AwakenTests.register("core_input_holds", 20, h -> {
            InputHolds<Body, String> holds = new InputHolds<>(b -> b.input, (b, i) -> b.input = i, () -> "still");
            Body p = new Body("keys");
            Object camera = "camera", ember = "ember", registry = "registry";
            // two scenes overlap and let go in either order: the real input comes back once, at the end
            holds.hold(camera, p, 0, 200);
            h.assertTrue(p.input.equals("still"), "held: the player stands still");
            holds.hold(ember, p, 5, 900);
            holds.release(camera);
            h.assertTrue(p.input.equals("still"), "the camera let go first; the hand-over still holds");
            holds.release(ember);
            h.assertTrue(p.input.equals("keys"), "the last one let go: the player's own keys again, never a stand-in");
            // the bug Euan hit: a second holder that took over mid-shot never puts a stand-in back
            holds.hold(camera, p, 10, 200);
            holds.hold(ember, p, 11, 900);
            holds.release(camera);
            holds.release(ember);
            holds.release(ember);
            h.assertTrue(p.input.equals("keys") && !holds.held(), "let go twice, in any order: still the player's own keys");
            // a respawn (a new player object): the hold moves to it, and its own keys come back
            holds.hold(registry, p, 20, 100);
            Body respawned = new Body("new keys");
            holds.tick(respawned, 21);
            h.assertTrue(respawned.input.equals("still"), "the respawned body is held in turn");
            holds.release(registry);
            h.assertTrue(respawned.input.equals("new keys"), "and given its own keys back");
            // the failsafe: a scene that never lets go is let go past its time, and named
            holds.hold(ember, respawned, 30, 50);
            h.assertTrue(holds.tick(respawned, 80).isEmpty() && respawned.input.equals("still"), "within its time it holds");
            var late = holds.tick(respawned, 81);
            h.assertTrue(late.contains(ember) && respawned.input.equals("new keys"), "past it, it lets go: " + late);
            // a logout lets everything go
            holds.hold(camera, respawned, 90, 200);
            holds.hold(ember, respawned, 90, 200);
            holds.clear();
            h.assertTrue(!holds.held() && respawned.input.equals("new keys"), "a logout: every hold gone, the keys back");
            h.succeed();
        });
    }
}
