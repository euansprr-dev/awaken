package dev.awaken.body.client;

import dev.awaken.body.net.BodyPayloads;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.util.Mth;

/**
 * Development only ({@code /awaken body try <move>}, game masters): presses this client's own movement keys the way
 * a player would, so the autopilot (which can't hold keys) can film each move through exactly the code path real
 * input takes. A script holds keys for some ticks, then lets go of everything it pressed.
 */
final class TryDriver {
    private TryDriver() {}

    private static String script = "";
    private static int age;
    private static boolean holding;
    // spike S3: the runner scripts' own state (the view they started with, a jump held from the edge, chain timing)
    private static float baseYaw;
    private static boolean edgeJumped;
    private static int jumpAt = -1, links, edgeAt = -1;

    static void start(int what, int delay) {
        if (what < 0 || what >= BodyPayloads.TRIES.length) return;
        release();
        RunnerMoves.reset(); // every script starts with the hands free
        script = BodyPayloads.TRIES[what];
        age = -Math.max(0, delay);
        edgeJumped = false;
        jumpAt = -1;
        edgeAt = -1;
        links = 0;
        var pl = Minecraft.getInstance().player;
        baseYaw = pl == null ? 0 : pl.getYRot();
        ClientMoves.trace("try " + script + (delay > 0 ? " after " + delay : ""));
        if (script.equals("release")) script = "";
        if (script.equals("runheld") || script.equals("runpress")) { // the trigger switch, nothing pressed
            RunnerMoves.airPress = script.equals("runpress");
            ClientMoves.trace("wall-run trigger: " + (RunnerMoves.airPress ? "press in the air" : "jump held"));
            script = "";
        }
    }

    static void tick() {
        if (script.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            release();
            return;
        }
        Options o = mc.options;
        age++;
        if (age <= 0) return;
        holding = true;
        int end;
        switch (script) {
            case "roll" -> { // hold crouch through the fall the tour set up
                set(o.keyShift, true);
                end = 100;
            }
            case "freerun" -> { // hold forward and sprint: the free-run picks every move
                set(o.keyUp, true);
                set(o.keySprint, true);
                end = 300;
            }
            case "sprint" -> { // run, double-tap sprint, keep running
                set(o.keyUp, true);
                set(o.keySprint, age <= 2 || (age >= 5 && age <= 7));
                end = 70;
            }
            case "slide" -> { // run, then crouch
                set(o.keyUp, true);
                set(o.keySprint, true);
                set(o.keyShift, age >= 10 && age <= 12);
                end = 34;
            }
            case "tacslide" -> { // tactical sprint, then crouch out of it
                set(o.keyUp, true);
                set(o.keySprint, age <= 2 || (age >= 5 && age <= 7));
                set(o.keyShift, age >= 18 && age <= 20);
                end = 44;
            }
            case "slidejump" -> { // slide, then jump out of it (the slide-cancel)
                set(o.keyUp, true);
                set(o.keySprint, true);
                set(o.keyShift, age >= 10 && age <= 12);
                set(o.keyJump, age >= 17 && age <= 19);
                end = 40;
            }
            case "mantle" -> { // walk at the ledge and jump
                set(o.keyUp, true);
                set(o.keyJump, age >= 3 && age <= 5);
                end = 14;
            }
            case "hit" -> { // run in and swing at whatever is under the crosshair
                set(o.keyUp, true);
                set(o.keySprint, true);
                if (age == 12) KeyMapping.click(o.keyAttack.getKey());
                end = 16;
            }
            // ---- spike S3: the Runner's Kit ----
            case "wallrun", "walljump", "chain" -> { // sprint off the platform's end along the wall, jump held from the edge
                set(o.keyUp, true);
                set(o.keySprint, true);
                var p = mc.player;
                if (!edgeJumped && p.onGround() && !supportAhead(p, 0.6)) {
                    edgeJumped = true;
                    edgeAt = age;
                }
                boolean run = RunnerMoves.state == RunnerMoves.State.RUN;
                boolean jumpKey;
                if (run && !script.equals("wallrun")) {
                    // kick off the wall during the grip: once for walljump, every run for chain (up to 4 links)
                    jumpKey = false;
                    boolean want = script.equals("chain") || links == 0;
                    if (want && jumpAt < 0 && RunnerMoves.runAge >= (script.equals("walljump") ? 9 : 7)) jumpAt = age;
                    if (jumpAt >= 0 && age - jumpAt < 2) jumpKey = true;
                } else if (RunnerMoves.airPress) {
                    // press mode: the jump off the edge, let go, then a second press in the air beside the wall
                    int since = age - edgeAt;
                    jumpKey = edgeJumped && links == 0 && jumpAt < 0 && (since < 2 || since >= 4 && since < 6);
                } else {
                    jumpKey = edgeJumped && (script.equals("wallrun") || links == 0 && jumpAt < 0);
                }
                if (jumpAt >= 0 && age - jumpAt >= 2) {
                    links++;
                    jumpAt = -1;
                }
                set(o.keyJump, jumpKey);
                end = script.equals("chain") ? 118 : 72;
            }
            case "hang", "shimmy", "ledgeup", "climb", "ledgeside", "ledgeback" -> { // walk-jump at the edge, then the move
                set(o.keyUp, age <= 6);
                set(o.keyJump, age <= 2 || (age >= 15 && age <= 16 && !script.equals("hang") && !script.equals("shimmy")));
                set(o.keyLeft, script.equals("shimmy") && age >= 12 && age <= 36 || script.equals("ledgeside") && age >= 14 && age <= 19);
                set(o.keyDown, script.equals("ledgeback") && age >= 14 && age <= 18);
                if (script.equals("ledgeback")) set(o.keyUp, age <= 6 || age >= 19 && age <= 26);
                int drop = switch (script) {
                    case "hang" -> 30;
                    case "climb" -> 1000;
                    default -> 42;
                };
                set(o.keyShift, age >= drop && age <= drop + 1);
                end = script.equals("hang") ? 38 : 50;
            }
            case "lower" -> { // sneak forward at the roof's edge until the body lowers into a hang, then stay
                set(o.keyShift, age <= 16);
                set(o.keyUp, age <= 16);
                end = 30;
            }
            case "crane" -> { // jump from the crate to the crane arm, shimmy to its end and round it
                set(o.keyUp, age <= 6);
                set(o.keyJump, age <= 2);
                set(o.keyLeft, age >= 12 && age <= 90);
                end = 94;
            }
            case "alley", "alleyspam" -> { // sprint and hold jump (or tap it every 5 ticks), the view wobbling: nothing should start
                set(o.keyUp, true);
                set(o.keySprint, true);
                set(o.keyJump, script.equals("alley") || age % 5 < 2);
                var p = mc.player;
                float yaw = baseYaw + 9F * Mth.sin(age * 0.35F);
                p.setYRot(yaw);
                p.setYHeadRot(yaw);
                end = 52;
            }
            default -> end = 0;
        }
        if (age >= end) release();
    }

    /** Whether there is ground under the body a little ahead of its facing. */
    private static boolean supportAhead(net.minecraft.client.player.LocalPlayer p, double ahead) {
        double fx = -Mth.sin(p.getYRot() * Mth.DEG_TO_RAD), fz = Mth.cos(p.getYRot() * Mth.DEG_TO_RAD);
        double x = p.getX() + fx * ahead, z = p.getZ() + fz * ahead, y = p.getY();
        return dev.awaken.body.BodyRules.blocked(p.level(), net.minecraft.world.phys.shapes.CollisionContext.of(p), new net.minecraft.core.BlockPos.MutableBlockPos(),
                x - 0.29, y - 0.5, z - 0.29, x + 0.29, y - 1e-3, z + 0.29);
    }

    private static void set(KeyMapping k, boolean down) {
        k.setDown(down);
    }

    static void release() {
        if (!holding) {
            script = "";
            return;
        }
        Options o = Minecraft.getInstance().options;
        for (KeyMapping k : new KeyMapping[]{o.keyUp, o.keySprint, o.keyShift, o.keyJump, o.keyLeft, o.keyRight, o.keyDown}) k.setDown(false);
        holding = false;
        script = "";
    }
}
