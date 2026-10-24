package dev.awaken.body.client;

import dev.awaken.body.net.BodyPayloads;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

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

    static void start(int what, int delay) {
        if (what < 0 || what >= BodyPayloads.TRIES.length) return;
        release();
        script = BodyPayloads.TRIES[what];
        age = -Math.max(0, delay);
        ClientMoves.trace("try " + script + (delay > 0 ? " after " + delay : ""));
        if (script.equals("release")) script = "";
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
            default -> end = 0;
        }
        if (age >= end) release();
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
        for (KeyMapping k : new KeyMapping[]{o.keyUp, o.keySprint, o.keyShift, o.keyJump}) k.setDown(false);
        holding = false;
        script = "";
    }
}
