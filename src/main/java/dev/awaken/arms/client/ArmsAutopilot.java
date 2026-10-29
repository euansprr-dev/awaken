package dev.awaken.arms.client;

import dev.awaken.arms.Archetype;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Development only: the core autopilot's {@code arms} lines, so a build's fight can be checked in screenshots without
 * a person at the keys. Every verb goes through the real input path (the key mappings the player would press), so
 * what is shot is what a player gets. {@code pose} holds the hands still at a point of a move for a review frame.
 *
 * <pre>
 * arms strike                         a tap of the attack key (a real click, held 2 ticks as a finger does)
 * arms press                          Arms' press alone, without the key (the old line)
 * arms trace &lt;ticks&gt;                  logs the move, its clock and the hand's pose every tick for that long
 * arms trailfreeze on|off             blade ribbons stop ageing once laid (a still frame shows the whole ribbon)
 * arms heavy &lt;ticks&gt;                  the attack key held that long, then let go
 * arms chop &lt;ticks&gt;                   a click held that long, as vanilla mines (a log with an axe, a web with a blade)
 * arms guard on|off                   the use key held (the guard up) or let go
 * arms dodge left|right|back|none     the sprint key pressed with that direction for a tick
 * arms pose &lt;move&gt; &lt;0..1&gt; | off       the hands held at that point of the move (strike_1, heavy, riposte...)
 * arms state                          logs the move, rhythm, charge, dodges and Resonance
 * </pre>
 */
public final class ArmsAutopilot {
    private ArmsAutopilot() {}

    /** Keys held by a line, and the client tick each is let go. */
    private static final Map<KeyMapping, Integer> HELD = new HashMap<>();

    public static String run(String args) {
        String[] a = args.trim().toLowerCase(Locale.ROOT).split("\\s+");
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return "no player";
        switch (a[0]) {
            case "strike" -> {
                if (!ArmsPredict.fights(p)) return "not fighting (no style in hand, or Arms off)";
                KeyMapping.click(mc.options.keyAttack.getKey());
                hold(mc.options.keyAttack, 2);
                return "strike clicked";
            }
            case "chop" -> {
                if (!ArmsPredict.fights(p)) return "not fighting (no style in hand, or Arms off)";
                int ticks = a.length > 1 ? Integer.parseInt(a[1]) : 20;
                KeyMapping.click(mc.options.keyAttack.getKey());
                hold(mc.options.keyAttack, ticks);
                return "attack key held for " + ticks;
            }
            case "press" -> {
                if (!ArmsPredict.fights(p)) return "not fighting (no style in hand, or Arms off)";
                ArmsPredict.press(p, mc);
                return "strike: " + ArmsPredict.move;
            }
            case "ribbonmode" -> {
                BladeTrail.mode = a.length > 1 ? Integer.parseInt(a[1]) : 0;
                return "ribbon mode " + BladeTrail.mode;
            }
            case "ribbonstate" -> {
                return BladeTrail.state();
            }
            case "ribbonlog" -> {
                BladeTrail.debug = a.length > 1 ? Integer.parseInt(a[1]) : 12;
                return "logging ribbon samples";
            }
            case "trailfreeze" -> {
                BladeTrail.frozen = a.length < 2 || a[1].equals("on");
                return "trails " + (BladeTrail.frozen ? "held" : "live");
            }
            case "trace" -> {
                traceUntil = ArmsPredict.ticks + (a.length > 1 ? Integer.parseInt(a[1]) : 40);
                return "tracing";
            }
            case "heavy" -> {
                if (!ArmsPredict.fights(p)) return "not fighting";
                int ticks = a.length > 1 ? Integer.parseInt(a[1]) : 16;
                hold(mc.options.keyAttack, ticks);
                ArmsPredict.press(p, mc);
                return "heavy held for " + ticks;
            }
            case "guard" -> {
                boolean on = a.length < 2 || a[1].equals("on");
                mc.options.keyUse.setDown(on);
                if (on && mc.gameMode != null && !p.isUsingItem()) mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
                if (!on && p.isUsingItem() && mc.gameMode != null) mc.gameMode.releaseUsingItem(p);
                return "guard " + (on ? "up" : "down");
            }
            case "dodge" -> {
                String way = a.length > 1 ? a[1] : "none";
                hold(mc.options.keySprint, 2);
                switch (way) {
                    case "left" -> hold(mc.options.keyLeft, 2);
                    case "right" -> hold(mc.options.keyRight, 2);
                    case "back" -> hold(mc.options.keyDown, 2);
                    default -> { }
                }
                return "dodge " + way;
            }
            case "pose" -> {
                if (a.length < 3 || a[1].equals("off")) {
                    ArmsHands.preview = null;
                    return "pose off";
                }
                Archetype.Move m = Archetype.Move.byKey(a[1]);
                if (m == null) return "no move " + a[1];
                ArmsHands.preview = m;
                ArmsHands.previewAt = Float.parseFloat(a[2]);
                return "pose " + m.key + " at " + ArmsHands.previewAt;
            }
            case "state" -> {
                return "move " + ArmsPredict.move + " cadence " + ArmsPredict.cadence + " charge " + ArmsPredict.charge()
                        + " dodges " + ArmsPredict.dodgeCharges + " resonance " + ArmsPredict.resonance;
            }
            default -> {
                return "unknown: " + args.trim();
            }
        }
    }

    private static void hold(KeyMapping key, int ticks) {
        key.setDown(true);
        HELD.put(key, ArmsPredict.ticks + ticks);
    }

    private static int traceUntil;
    private static final float[] POSE = new float[5];

    /** Lets go of the keys whose time is up (each client tick). */
    static void tick() {
        if (ArmsPredict.ticks < traceUntil) {
            Minecraft mc = Minecraft.getInstance();
            ArmsHands.pose(POSE);
            dev.awaken.Awaken.LOG.info("[arms trace] t={} move={} clock={} pose=[{} {} {} {} {}] press={} held={} charging={} key={} presses={} hit={} chop={}",
                    ArmsPredict.ticks, ArmsPredict.move, String.format(Locale.ROOT, "%.2f", ArmsPredict.moveTime()),
                    String.format(Locale.ROOT, "%.2f", POSE[0]), String.format(Locale.ROOT, "%.2f", POSE[1]),
                    String.format(Locale.ROOT, "%.0f", POSE[2]), String.format(Locale.ROOT, "%.2f", POSE[3]),
                    String.format(Locale.ROOT, "%.0f", POSE[4]), ArmsPredict.pressHeld, ArmsPredict.heldTicks, ArmsPredict.charging,
                    mc.options.keyAttack.isDown(), ArmsPredict.presses, mc.hitResult == null ? "-" : mc.hitResult.getType(), ArmsChop.state());
        }
        if (HELD.isEmpty()) return;
        HELD.entrySet().removeIf(e -> {
            if (ArmsPredict.ticks < e.getValue()) return false;
            e.getKey().setDown(false);
            return true;
        });
    }
}
