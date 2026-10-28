package dev.awaken.binoculars;

import dev.awaken.core.api.Destinations;
import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * GameTests for the binoculars (SPEC §39.9): the key and what lowers them, the steps and the zoom curve, the look speed,
 * the feel file, the relay, and marking a spot. The look and feel in the hands are Euan's to judge in game.
 */
final class BinocularsTests {
    private BinocularsTests() {}

    /** Records the players a relay went to without sending anything to the mock connections. */
    private static Predicate<ServerPlayer> recorder(List<ServerPlayer> into) {
        return p -> {
            into.add(p);
            return false;
        };
    }

    static void register() {
        AwakenTests.register("binoculars_state", 20, h -> {
            GlassKey k = new GlassKey();
            long tap = GlassFeel.DEFAULTS.tapMs();
            // tap and hold: a tap keeps them up, a second tap lowers; a long press is a peek
            h.assertTrue(k.press(0, false, true, GlassKey.Mode.TAP_HOLD) == GlassKey.Action.RAISE, "a press raises");
            h.assertTrue(k.release(120, true, GlassKey.Mode.TAP_HOLD, tap) == GlassKey.Action.NONE, "a tap keeps them up");
            h.assertTrue(k.press(900, true, true, GlassKey.Mode.TAP_HOLD) == GlassKey.Action.LOWER, "the next press lowers");
            h.assertTrue(k.release(1400, false, GlassKey.Mode.TAP_HOLD, tap) == GlassKey.Action.NONE, "its release does nothing");
            k.press(2000, false, true, GlassKey.Mode.TAP_HOLD);
            h.assertTrue(k.release(2000 + tap, true, GlassKey.Mode.TAP_HOLD, tap) == GlassKey.Action.LOWER, "a hold is a peek: down on release");
            // hold only and toggle only
            k.press(0, false, true, GlassKey.Mode.HOLD);
            h.assertTrue(k.release(50, true, GlassKey.Mode.HOLD, tap) == GlassKey.Action.LOWER, "hold only: even a tap lowers on release");
            h.assertTrue(k.press(0, false, true, GlassKey.Mode.TOGGLE) == GlassKey.Action.RAISE, "toggle: press raises");
            h.assertTrue(k.release(5000, true, GlassKey.Mode.TOGGLE, tap) == GlassKey.Action.NONE, "toggle: never on release");
            // busy hands (a spyglass, a screen, a climb): the key waits
            h.assertTrue(k.press(0, false, false, GlassKey.Mode.TAP_HOLD) == GlassKey.Action.NONE, "no raise while the hands are busy");
            h.assertTrue(k.release(900, false, GlassKey.Mode.TAP_HOLD, tap) == GlassKey.Action.NONE, "and its release does nothing");
            // lowered by something else mid-peek: the release doesn't act again
            k.press(0, false, true, GlassKey.Mode.TAP_HOLD);
            k.reset();
            h.assertTrue(k.release(900, false, GlassKey.Mode.TAP_HOLD, tap) == GlassKey.Action.NONE, "a reset press owns nothing");

            GlassFeel f = GlassFeel.DEFAULTS;
            GlassState s = new GlassState();
            s.raise();
            s.advance(f.raiseMs() * 0.5F, f.raiseMs(), f.lowerMs());
            h.assertTrue(s.phase() == GlassState.Phase.RAISING && !s.atEyes(f.eyecup()), "halfway up: not at the eyes yet");
            s.lower(GlassState.Drop.KEY);
            float half = s.progress();
            s.advance(10, f.raiseMs(), f.lowerMs());
            h.assertTrue(s.phase() == GlassState.Phase.LOWERING && s.progress() < half, "a raise turns back from where it was");
            s.raise();
            s.advance(f.raiseMs(), f.raiseMs(), f.lowerMs());
            h.assertTrue(s.phase() == GlassState.Phase.UP && s.atEyes(f.eyecup()), "up and at the eyes after the raise time");
            for (GlassState.Drop why : GlassState.Drop.values()) {
                GlassState t = new GlassState();
                t.raise();
                t.advance(f.raiseMs(), f.raiseMs(), f.lowerMs());
                t.lower(why);
                if (why.instant) h.assertTrue(t.phase() == GlassState.Phase.DOWN && t.progress() == 0F, why + " lowers them in the same frame");
                else {
                    h.assertTrue(!t.up() && !t.atEyes(f.eyecup()), why + " takes them from the eyes at once");
                    t.advance(f.lowerMs(), f.raiseMs(), f.lowerMs());
                    h.assertTrue(t.phase() == GlassState.Phase.DOWN, why + " is down after the lower time");
                }
            }
            for (GlassState.Drop why : new GlassState.Drop[]{GlassState.Drop.ATTACK, GlassState.Drop.USE, GlassState.Drop.SCREEN, GlassState.Drop.HURT,
                    GlassState.Drop.DEATH, GlassState.Drop.DIMENSION, GlassState.Drop.CAST, GlassState.Drop.GRIP, GlassState.Drop.SPYGLASS})
                h.assertTrue(why.instant, why + " must never leave you zoomed for a frame");
            h.succeed();
        });

        AwakenTests.register("binoculars_zoom", 20, h -> {
            GlassFeel f = GlassFeel.DEFAULTS;
            int i = f.stepIndex(f.firstStep());
            h.assertTrue(f.steps()[i] == 4F, "the first raise is 4x");
            h.assertTrue(f.steps()[f.stepBy(i, 99)] == 12F && f.steps()[f.stepBy(i, -99)] == 2F, "steps hold at 2x and 12x");
            h.assertTrue(f.steps()[f.stepIndex(7.9F)] == 8F, "a remembered 8x comes back as 8x");
            h.assertTrue(GlassRules.notches(5) == 1 && GlassRules.notches(-3) == -1 && GlassRules.notches(0) == 0, "one step a scroll event");
            // every FOV setting 30..110, with a speed effect up to 1.15: each step divides the view, never under 1 degree
            for (int base = 30; base <= 110; base += 10) {
                for (double effect : new double[]{0.9, 1.0, 1.15}) {
                    double fov = base * effect, last = fov;
                    for (float m : f.steps()) {
                        double z = GlassRules.fov(fov, m);
                        h.assertTrue(z < last && z >= GlassRules.MIN_FOV, "fov " + fov + " at " + m + "x narrows: " + z);
                        double tanRatio = Math.tan(Math.toRadians(fov) / 2) / Math.tan(Math.toRadians(z) / 2);
                        h.assertTrue(z == GlassRules.MIN_FOV || Math.abs(tanRatio - m) < 1e-6, "magnifies exactly " + m + "x: " + tanRatio);
                        last = z;
                    }
                }
            }
            h.assertTrue(GlassRules.fov(70, 1) == 70, "1x is your own view");
            // the spring: a step overshoots about 2% of itself and settles in about the settle time
            double zeta = GlassSpring.zeta(f.overshoot());
            GlassSpring sp = new GlassSpring();
            sp.snap(4);
            double peak = 0;
            float settledAt = -1;
            for (float ms = 0; ms < 600; ms += 1000F / 120F) {
                sp.step(1000F / 120F, 8, f.settleMs(), zeta);
                double over = Math.log(sp.mag() / 4) / Math.log(2) - 1;   // share of the step, on the log scale
                peak = Math.max(peak, over);
                if (settledAt < 0 && Math.abs(over) < 0.02 && ms > 30) settledAt = ms;
                if (settledAt >= 0 && Math.abs(over) >= 0.02) settledAt = -1;
            }
            h.assertTrue(peak > 0.005 && peak < 0.035, "a step overshoots about 2%: " + peak);
            h.assertTrue(settledAt > 0 && settledAt <= f.settleMs() * 1.25F, "settles in about " + f.settleMs() + " ms: " + settledAt);
            // the same at 30 fps, and with reduced motion (no overshoot)
            GlassSpring slow = new GlassSpring(), calm = new GlassSpring();
            slow.snap(4);
            calm.snap(4);
            double calmPeak = 0;
            for (int n = 0; n < 30; n++) {
                slow.step(1000.0 / 30.0, 8, f.settleMs(), zeta);
                calm.step(1000.0 / 30.0, 8, f.settleMs(), 1.0);
                calmPeak = Math.max(calmPeak, calm.mag());
            }
            h.assertTrue(Math.abs(slow.mag() - 8) < 0.01, "30 fps lands on 8x: " + slow.mag());
            h.assertTrue(calmPeak <= 8.0001, "reduced motion never overshoots: " + calmPeak);
            h.succeed();
        });

        AwakenTests.register("binoculars_sense", 20, h -> {
            for (double sens = 0; sens <= 1.0001; sens += 0.05) {
                double unzoomed = GlassRules.turn(sens) / 70.0;     // degrees a count over the view's height
                for (float m : GlassFeel.DEFAULTS.steps()) {
                    double s = GlassRules.sensitivity(sens, GlassRules.lookScale(m, 1.0));
                    double zoomedFov = GlassRules.fov(70, m);
                    // on screen, near the middle: a degree is tan-scaled; matched means the same pixels a count
                    double px = GlassRules.turn(s) / Math.tan(Math.toRadians(zoomedFov) / 2), px0 = GlassRules.turn(sens) / Math.tan(Math.toRadians(35));
                    h.assertTrue(unzoomed == 0 || Math.abs(px / px0 - 1) <= 0.02, "sensitivity " + sens + " at " + m + "x moves " + px / px0 + " as fast on screen");
                }
                double half = GlassRules.turn(GlassRules.sensitivity(sens, GlassRules.lookScale(4, 0.5))), full = GlassRules.turn(GlassRules.sensitivity(sens, GlassRules.lookScale(4, 1.0)));
                h.assertTrue(full == 0 || Math.abs(half / full - 0.5) < 1e-6, "the slider at 50% halves it");
            }
            h.succeed();
        });

        AwakenTests.register("binoculars_feel", 20, h -> {
            String text;
            try (var in = GlassFeel.class.getClassLoader().getResourceAsStream(GlassFeel.FILE)) {
                h.assertTrue(in != null, GlassFeel.FILE + " is in the jar");
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
            GlassFeel.Parsed real = GlassFeel.parse(text);
            h.assertTrue(real.ok(), "the shipped feel reads: " + real.error());
            GlassFeel d = GlassFeel.DEFAULTS, r = real.feel();
            h.assertTrue(r.raiseMs() == d.raiseMs() && r.lowerMs() == d.lowerMs() && r.settleMs() == d.settleMs() && r.tapMs() == d.tapMs()
                    && java.util.Arrays.equals(r.steps(), d.steps()), "the file and the built-in numbers agree");
            for (String broken : new String[]{"{", "[]", "{\"schemaVersion\": 2}", "{\"schemaVersion\": 1, \"steps\": [4, 2]}",
                    "{\"schemaVersion\": 1, \"raiseMs\": -5}", "{\"schemaVersion\": 1, \"steps\": [2, 40]}", "{\"schemaVersion\": 1, \"eyecup\": 0.9, \"eyecupEnd\": 0.5}"}) {
                GlassFeel.Parsed p = GlassFeel.parse(broken);
                h.assertTrue(!p.ok() && p.feel() == GlassFeel.DEFAULTS, "a broken file keeps the defaults: " + broken);
            }
            GlassFeel.Parsed partial = GlassFeel.parse("{\"schemaVersion\": 1, \"raiseMs\": 400}");
            h.assertTrue(partial.ok() && partial.feel().raiseMs() == 400F && partial.feel().lowerMs() == d.lowerMs(), "a missing field keeps its number");
            h.succeed();
        });

        AwakenTests.register("binoculars_relay", 200, h -> {
            ServerPlayer sender = h.makeMockServerPlayerInLevel();
            ServerPlayer near = h.makeMockServerPlayerInLevel();
            ServerPlayer far = h.makeMockServerPlayerInLevel();
            var base = h.absoluteVec(new net.minecraft.world.phys.Vec3(0.5, 1, 0.5));
            sender.snapTo(base.x, base.y, base.z);
            near.snapTo(base.x + 3, base.y, base.z);
            far.snapTo(base.x + 2000, base.y, base.z);
            ServerChunkCache cache = h.getLevel().getChunkSource();
            int[] phase = {0};
            int[] start = {0};
            h.succeedWhen(() -> {
                for (ServerPlayer p : List.of(sender, near, far)) cache.move(p);
                int now = h.getLevel().getServer().getTickCount();
                if (phase[0] == 0) {
                    h.assertTrue(cache.chunkMap.getPlayersWatching(sender).contains(near), "waiting for the near player to see the sender");
                    List<ServerPlayer> got = new ArrayList<>();
                    h.assertTrue(BinocularsRelay.receive(sender, true, recorder(got)) == BinocularsRelay.Result.RELAYED, "up is relayed");
                    h.assertTrue(got.contains(near) && !got.contains(far) && !got.contains(sender), "only the players who see the sender hear it");
                    h.assertTrue(BinocularsRelay.receive(sender, true, recorder(got)) == BinocularsRelay.Result.UNCHANGED, "up twice is said once");
                    // spam: four in a window, the rest wait, the last arrives
                    int deferred = 0;
                    for (int n = 0; n < 6; n++) if (BinocularsRelay.receive(sender, n % 2 == 1, recorder(got)) == BinocularsRelay.Result.DEFERRED) deferred++;
                    h.assertTrue(deferred >= 2, "spam waits for the window: " + deferred + " deferred");
                    start[0] = now;
                    phase[0] = 1;
                }
                if (phase[0] == 1) {
                    h.assertTrue(now - start[0] >= BinocularsRelay.WINDOW_TICKS, "waiting for the window");
                    BinocularsRelay.flush(h.getLevel().getServer(), recorder(new ArrayList<>()));
                    h.assertTrue(BinocularsRelay.up(sender), "the last word (up) arrives once the window frees");
                    // death puts them down and tells the watchers; a dead player's word is ignored
                    List<ServerPlayer> got = new ArrayList<>();
                    BinocularsRelay.reset(sender, recorder(got));
                    h.assertTrue(!BinocularsRelay.up(sender) && got.contains(near), "death lowers them for everyone who saw");
                    sender.setHealth(0);
                    h.assertTrue(BinocularsRelay.receive(sender, true, recorder(got)) == BinocularsRelay.Result.REJECTED, "the dead don't raise binoculars");
                    BinocularsRelay.forget(sender);
                    h.assertTrue(!BinocularsRelay.up(sender), "logout forgets");
                    for (ServerPlayer p : List.of(sender, near, far)) h.getLevel().getServer().getPlayerList().remove(p);
                }
            });
        });

        AwakenTests.register("binoculars_mark", 40, h -> {
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            var level = h.getLevel();
            BlockPos ground = h.absolutePos(new BlockPos(1, 0, 1));
            int minY = level.getMinY(), maxY = level.getMaxY();
            h.assertFalse(GlassRules.markable(false, ground, minY, maxY, 100, 384, true), "the sky can't be marked");
            h.assertFalse(GlassRules.markable(true, ground, minY, maxY, Double.NaN, 384, true), "nothing known can't be marked");
            h.assertFalse(GlassRules.markable(true, new BlockPos(ground.getX(), maxY + 40, ground.getZ()), minY, maxY, 100, 384, true), "above the world is refused");
            h.assertFalse(GlassRules.markable(true, ground, minY, maxY, 100, 384, false), "past the border is refused");
            h.assertFalse(GlassRules.markable(true, ground, minY, maxY, 900, 384, true), "past what the readout can tell is refused");
            h.assertTrue(GlassRules.markable(true, ground, minY, maxY, 100, 384, true), "a hit in loaded land is markable");
            h.assertTrue(GlassRules.SPOT_NAME.length() <= 48, "the name fits the server's 48");
            var dim = level.dimension().identifier();
            Destinations.set(p, new Destinations.Target(GlassRules.SPOT_NAME, dim, ground, "spot"));
            var got = Destinations.get(p);
            h.assertTrue(got.isPresent() && got.get().kind().equals("spot") && got.get().at().equals(ground), "the spot is the destination");
            Destinations.clear(p);
            // the readout's words
            h.assertTrue(GlassRules.readout(8F, 312.4, 225F).equals("8× · 312 m · NE"), "readout: " + GlassRules.readout(8F, 312.4, 225F));
            h.assertTrue(GlassRules.distance(5140).equals("5.1 km") && GlassRules.distance(Double.NaN).equals("far"), "km and far");
            h.assertTrue(GlassRules.bearing(0F).equals("S") && GlassRules.bearing(-90F).equals("E") && GlassRules.bearing(180F).equals("N"), "bearings");
            level.getServer().getPlayerList().remove(p);
            h.succeed();
        });
    }
}
