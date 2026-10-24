package dev.awaken.body.ascent.client;

import dev.awaken.Awaken;
import dev.awaken.body.ascent.AscentRules;
import dev.awaken.body.ascent.AscentState;
import dev.awaken.core.client.AwakenKeys;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Development only ({@code /awaken ascent pilot <line>}, game masters): presses this client's keys the way a player
 * would, so tours (whose autopilot can't hold keys) film every move through exactly the path real input takes.
 * <pre>
 * hold &lt;keys&gt; &lt;ticks&gt;      keys: forward,back,left,right,jump,sneak,sprint,tether (comma separated)
 * aim &lt;yaw&gt; &lt;pitch&gt; &lt;ticks&gt; turn smoothly to a view
 * run &lt;script&gt; [n]          a manoeuvre that watches the state: swing (n swings down the lane), glide, boom, soar
 * rec &lt;name&gt; &lt;ticks&gt; [n]     film a clip (every n-th frame)
 * route rec|play|stop [name] record a run's keys and view, or play one back from its start ({@link AscentRoutes})
 * stop                       let go of everything
 * </pre>
 */
public final class AscentPilot {
    private AscentPilot() {}

    private static final int FORWARD = 0, BACK = 1, LEFT = 2, RIGHT = 3, JUMP = 4, SNEAK = 5, SPRINT = 6, TETHER = 7;
    private static final int[] HOLD = new int[8];
    private static float aimYaw, aimPitch, aimStepYaw, aimStepPitch;
    private static int aimTicks;
    /** Keys were pressed last tick (released once when the holds run out). */
    private static boolean holding;
    private static String script = "";
    private static int age, count, target, phase, mark, misses;
    private static float laneYaw;

    static void line(String line) {
        String[] a = line.trim().split("\\s+");
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || a.length == 0) return;
        Awaken.LOG.info("[ascent] pilot {}", line);
        switch (a[0]) {
            case "hold" -> {
                int t = Integer.parseInt(a[2]);
                for (String k : a[1].split(",")) {
                    int i = key(k);
                    if (i >= 0) HOLD[i] = t;
                }
            }
            case "aim" -> {
                float yaw = Float.parseFloat(a[1]), pitch = Float.parseFloat(a[2]);
                aimTicks = Math.max(1, Integer.parseInt(a[3]));
                aimStepYaw = Mth.wrapDegrees(yaw - p.getYRot()) / aimTicks;
                aimStepPitch = (pitch - p.getXRot()) / aimTicks;
                aimYaw = yaw;
                aimPitch = pitch;
            }
            case "run" -> {
                script = a[1];
                target = a.length > 2 ? Integer.parseInt(a[2]) : 4;
                age = count = phase = mark = misses = 0;
                laneYaw = p.getYRot();
            }
            case "rec" -> ClipRecorder.start(a[1], Integer.parseInt(a[2]), a.length > 3 ? Integer.parseInt(a[3]) : 2);
            case "route" -> AscentRoutes.line(a);
            case "stop" -> stop();
            default -> Awaken.LOG.warn("[ascent] pilot: unknown line {}", line);
        }
    }

    private static int key(String k) {
        return switch (k) {
            case "forward" -> FORWARD;
            case "back" -> BACK;
            case "left" -> LEFT;
            case "right" -> RIGHT;
            case "jump" -> JUMP;
            case "sneak" -> SNEAK;
            case "sprint" -> SPRINT;
            case "tether" -> TETHER;
            default -> -1;
        };
    }

    static void stop() {
        script = "";
        java.util.Arrays.fill(HOLD, 0);
        aimTicks = 0;
        apply();
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        if (!script.isEmpty()) {
            age++;
            switch (script) {
                case "swing" -> swing(p);
                case "glide" -> glide(p, false);
                case "boom" -> glide(p, true);
                case "soar" -> soar(p);
                default -> script = "";
            }
        }
        if (aimTicks > 0) {
            aimTicks--;
            p.setYRot(p.getYRot() + aimStepYaw);
            p.setYHeadRot(p.getYRot());
            p.setXRot(Mth.clamp(p.getXRot() + aimStepPitch, -90, 90));
        }
        boolean any = false;
        for (int i = 0; i < HOLD.length; i++) if (HOLD[i] > 0) any = true;
        if (!any && script.isEmpty()) {
            // the last hold ran out: let go of its keys once (a later press is then a new press)
            if (holding) apply();
            holding = false;
            return;
        }
        apply();
        holding = true;
        for (int i = 0; i < HOLD.length; i++) if (HOLD[i] > 0) HOLD[i]--;
    }

    private static void apply() {
        Options o = Minecraft.getInstance().options;
        set(o.keyUp, HOLD[FORWARD] > 0);
        set(o.keyDown, HOLD[BACK] > 0);
        set(o.keyLeft, HOLD[LEFT] > 0);
        set(o.keyRight, HOLD[RIGHT] > 0);
        set(o.keyJump, HOLD[JUMP] > 0);
        set(o.keyShift, HOLD[SNEAK] > 0);
        set(o.keySprint, HOLD[SPRINT] > 0);
        set(AwakenKeys.MOVEMENT, HOLD[TETHER] > 0);
    }

    private static void set(KeyMapping k, boolean down) {
        k.setDown(down);
    }

    private static void press(int key, int ticks) {
        HOLD[key] = Math.max(HOLD[key], ticks);
    }

    // ---- manoeuvres -----------------------------------------------------------------------------------------------------

    /**
     * Swinging down the lane: run, jump, fire at the trees ahead (the rope's aim assist picks them), pump through the
     * bottom, let go in the release window (every third one jumps off instead), fall a little, fire again; after
     * {@code n} swings, fly on and land.
     */
    private static void swing(LocalPlayer p) {
        AscentState st = AscentMoves.state();
        Minecraft mc = Minecraft.getInstance();
        // keep looking down the lane, a little up, so the assist finds the next trunk ahead
        p.setYRot(Mth.approachDegrees(p.getYRot(), laneYaw, 3F));
        p.setYHeadRot(p.getYRot());
        switch (st) {
            case NONE -> {
                if (count >= target) {
                    if (p.onGround() && age - mark > 10) script = "";
                    return;
                }
                p.setXRot(Mth.approach(p.getXRot(), -10F, 4F));
                press(FORWARD, 2);
                press(SPRINT, 2);
                if (p.onGround() && phase == 0 && age > 12) {
                    press(JUMP, 2);
                    phase = 1;
                    mark = age;
                }
                if (phase == 1 && !p.onGround() && age - mark > 5) {
                    press(TETHER, 2);
                    phase = 2;
                }
                if (phase == 2 && p.onGround()) {
                    // landed without a swing (a missed rope, a trunk in the face): turn off the line, as a player would
                    if (AscentMoves.missAt > AscentMoves.biteAt) laneYaw += (count % 2 == 0 ? 35F : -35F) * (++misses % 2 == 0 ? -1 : 1);
                    phase = 0;
                }
            }
            case TETHER -> {
                p.setXRot(Mth.approach(p.getXRot(), -8F, 3F));
                press(TETHER, 2);
                press(FORWARD, 2);
                double q = AscentRules.releaseQuality(AscentMoves.K, AscentMoves.ax, AscentMoves.ay, AscentMoves.az);
                if (AscentMoves.stateAge > 12 && q > 0.75) {
                    count++;
                    mark = age;
                    HOLD[TETHER] = 0;
                    if (count % 3 == 0) press(JUMP, 2);
                    phase = 3;
                }
                if (AscentMoves.stateAge > 90) {
                    count++;
                    HOLD[TETHER] = 0;
                    phase = 3;
                }
            }
            case ZIP -> press(TETHER, 2);
            case CARRY -> {
                p.setXRot(Mth.approach(p.getXRot(), -12F, 4F));
                press(FORWARD, 2);
                if (count < target && AscentMoves.K.vy < -0.05 && age - mark > 6) press(TETHER, 2);
            }
            default -> {
            }
        }
        // never carried into the next scene: about 3.5 s a swing at most
        if (age > Math.max(320, 70 * target)) stop();
    }

    /**
     * Off the tower: walk out, open the wings once falling, then a shallow glide; with {@code boom}, a steep dive until
     * the boom, then a pull-up and a glide out.
     */
    private static void glide(LocalPlayer p, boolean boom) {
        AscentState st = AscentMoves.state();
        switch (phase) {
            case 0 -> {
                press(FORWARD, 2);
                if (!p.onGround() && p.getDeltaMovement().y < -0.25) {
                    press(JUMP, 2);
                    phase = 1;
                    mark = age;
                }
            }
            case 1 -> {
                if (st == AscentState.GLIDE) {
                    phase = 2;
                    mark = age;
                } else if (age - mark > 6) {
                    press(JUMP, 2);
                    mark = age;
                }
            }
            case 2 -> {
                float want = boom ? 62F : 14F;
                p.setXRot(Mth.approach(p.getXRot(), want, boom ? 3F : 1F));
                if (boom && AscentMoves.boomAt > 0 && AscentMoves.ticks - AscentMoves.boomAt >= 1) {
                    phase = 3;
                    mark = age;
                }
                if (!boom && age - mark > 60) {
                    phase = 3;
                    mark = age;
                }
                if (st == AscentState.NONE) phase = 9;
            }
            case 3 -> {
                // pull up, then settle into a glide
                p.setXRot(Mth.approach(p.getXRot(), age - mark < 22 ? -30F : 8F, 6F));
                if (st == AscentState.NONE) phase = 9;
            }
            default -> script = "";
        }
        if (age > 1200) script = "";
    }

    /** Off the tower into flight: wings, take flight, cruise, boost, bank through a turn, slow to a hover. */
    private static void soar(LocalPlayer p) {
        AscentState st = AscentMoves.state();
        switch (phase) {
            case 0 -> {
                press(FORWARD, 2);
                if (!p.onGround() && p.getDeltaMovement().y < -0.25) {
                    press(JUMP, 2);
                    phase = 1;
                    mark = age;
                }
            }
            case 1 -> {
                if (st == AscentState.GLIDE && age - mark > 8) {
                    press(JUMP, 2);
                    phase = 2;
                    mark = age;
                } else if (st != AscentState.GLIDE && age - mark > 6) {
                    // the first air jump is the Leaper's double jump: the next one opens the wings
                    press(JUMP, 2);
                    mark = age;
                }
            }
            case 2 -> {
                press(FORWARD, 2);
                p.setXRot(Mth.approach(p.getXRot(), -6F, 2F));
                if (age - mark > 30) {
                    phase = 3;
                    mark = age;
                }
            }
            case 3 -> {
                press(FORWARD, 2);
                press(SPRINT, 2);
                if (age - mark > 50) {
                    phase = 4;
                    mark = age;
                }
            }
            case 4 -> {
                // a banked turn
                press(FORWARD, 2);
                press(SPRINT, 2);
                p.setYRot(p.getYRot() + 3.2F);
                p.setYHeadRot(p.getYRot());
                if (age - mark > 45) {
                    phase = 5;
                    mark = age;
                }
            }
            case 5 -> {
                // let go of forward: slow to a hover
                if (age - mark > 50) script = "";
            }
            default -> script = "";
        }
    }
}
