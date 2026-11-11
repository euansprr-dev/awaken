package dev.awaken.arms.client;

import dev.awaken.arms.Archetype;
import dev.awaken.core.api.Strikes;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Development only: the core autopilot's {@code arms} lines, so a build's fight can be checked in screenshots without
 * a person at the keys. Every verb goes through the real input path (the key mappings the player would press), so
 * what is shot is what a player gets. {@code pose} holds the hands still at a point of a move for a review frame.
 *
 * <pre>
 * arms strike [hold]                  a tap of the attack key (a real click, held 2 ticks as a finger does, or hold)
 * arms press                          Arms' press alone, without the key (the old line)
 * arms film &lt;name&gt; &lt;ticks&gt; [gap] [scale]  every frame's numbers and a picture every gap ticks (ArmsFilm)
 * arms studio &lt;item id&gt;               the fight studio built on the server's thread, you armed, a husk (waits)
 * arms waitserver [ticks]             waits until the server has ticked that often (a stalled server holds the line)
 * arms rate &lt;ticks a second&gt;          the world's pace, waiting until this client runs at it
 * arms trace &lt;ticks&gt;                  logs the move, its clock and the hand's pose every tick for that long
 * arms trailfreeze on|off             blade ribbons stop ageing once laid (a still frame shows the whole ribbon)
 * arms heavy &lt;ticks&gt;                  the attack key held that long, then let go
 * arms chop &lt;ticks&gt;                   a click held that long, as vanilla mines (a log with an axe, a web with a blade)
 * arms guard on|off                   the use key held (the guard up) or let go
 * arms parry &lt;ticks&gt; | off             the guard raised as a blow that reaches you is that many ticks off (the ring's
 *                                     own clock, Strikes.left), held 8 ticks, for every blow until "off"
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
                hold(mc.options.keyAttack, a.length > 1 ? Integer.parseInt(a[1]) : 2);
                return "strike clicked";
            }
            case "chop" -> {
                if (!ArmsPredict.fights(p)) return "not fighting (no style in hand, or Arms off)";
                int ticks = a.length > 1 ? Integer.parseInt(a[1]) : 20;
                KeyMapping.click(mc.options.keyAttack.getKey());
                hold(mc.options.keyAttack, ticks);
                return "attack key held for " + ticks;
            }
            case "studio" -> {
                return studio(mc, p, a.length > 1 ? a[1] : "");
            }
            case "waitserver" -> {
                IntegratedServer server = mc.getSingleplayerServer();
                if (server == null) return "single player only";
                int n = a.length > 1 ? Integer.parseInt(a[1]) : 20;
                if (serverFrom < 0) serverFrom = server.getTickCount();
                if (server.getTickCount() - serverFrom < n && ++serverTries < 20 * 180) return null;
                String said = serverTries >= 20 * 180 ? "gave up: the server ticked " + (server.getTickCount() - serverFrom) : "the server ticked " + n;
                serverFrom = -1;
                serverTries = 0;
                return said;
            }
            case "rate" -> {
                IntegratedServer server = mc.getSingleplayerServer();
                if (server == null || a.length < 2) return "arms rate <ticks a second> (single player)";
                float rate = Float.parseFloat(a[1]);
                if (!rateAsked) {
                    rateAsked = true;
                    server.execute(() -> server.tickRateManager().setTickRate(rate));
                }
                if ((mc.level == null || Math.abs(mc.level.tickRateManager().tickrate() - rate) > 0.01F) && ++serverTries < 20 * 180) return null;
                rateAsked = false;
                serverTries = 0;
                return "the world at " + rate + " ticks a second";
            }
            case "film" -> {
                if (a.length < 3) return "arms film <name> <ticks> [gap] [downscale]";
                return ArmsFilm.start(a[1], Integer.parseInt(a[2]), a.length > 3 ? Float.parseFloat(a[3]) : 0.15F,
                        a.length > 4 ? Integer.parseInt(a[4]) : 2);
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
            case "walkhold" -> { // forward held that long while the script goes on (a fight on the move)
                hold(mc.options.keyUp, a.length > 1 ? Integer.parseInt(a[1]) : 40);
                return "walking";
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
                guard(mc, p, on);
                return "guard " + (on ? "up" : "down");
            }
            case "parry" -> {
                if (a.length < 2 || a[1].equals("off")) {
                    parryAt = Double.NaN;
                    if (guardUntil > 0) guard(mc, p, false);
                    guardUntil = 0;
                    return "parry off: the guard went up " + parried + " times";
                }
                parryAt = Double.parseDouble(a[1]);
                parried = 0;
                return "the guard goes up as a blow is " + parryAt + " ticks off";
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

    private static void guard(Minecraft mc, LocalPlayer p, boolean on) {
        mc.options.keyUse.setDown(on);
        if (on && mc.gameMode != null && !p.isUsingItem()) mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
        if (!on && p.isUsingItem() && mc.gameMode != null) mc.gameMode.releaseUsingItem(p);
    }

    // the timed guard: how many ticks off a blow is as the guard goes up (NaN: off), and the tick it comes down again
    private static double parryAt = Double.NaN;
    private static int guardUntil, parried;

    /**
     * The guard raised on the ring's own clock: at the first tick a deflectable blow that reaches you is within the asked
     * time, held 8 ticks. A film's check that what the ring shows is what the server counts (SPEC §68.2).
     */
    private static void parry(Minecraft mc, LocalPlayer p) {
        if (guardUntil > 0) {
            if (ArmsPredict.ticks >= guardUntil) {
                guard(mc, p, false);
                guardUntil = 0;
            }
            return;
        }
        if (mc.level == null) return;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == p || !(e instanceof LivingEntity) || e.distanceToSqr(p) > 32 * 32) continue;
            double left = Strikes.left(e, 1F);
            if (Double.isNaN(left) || left > parryAt || left <= parryAt - 1) continue;
            if (!Strikes.comingKind(e).deflectable() || !Strikes.reaches(e) || !Strikes.lands(e)) continue;
            guard(mc, p, true);
            guardUntil = ArmsPredict.ticks + 8;
            parried++;
            dev.awaken.Awaken.LOG.info("[arms parry] guard up with the blow {} ticks off", String.format(Locale.ROOT, "%.2f", left));
            return;
        }
    }

    private static void hold(KeyMapping key, int ticks) {
        key.setDown(true);
        HELD.put(key, ArmsPredict.ticks + ticks);
    }

    private static int serverFrom = -1, serverTries;
    private static boolean rateAsked;
    private static volatile int studioState; // 0 none, 1 being built on the server, 2 built

    /**
     * The fight studio, built on the server's own thread (the autopilot's {@code cmd} lines wait on client ticks, so
     * when the server stalls they pile up and run in one tick, before the ground they fill has loaded): its chunks
     * loaded first, a stone floor at y 199 with the air cleared over it, a deepslate wall at z 4, you at 0.5 200 0.9
     * facing south with the item in hand and resistance, and a husk at 0.5 200 3.5 that can't walk or hurt (1000 health).
     * Not ready (null) until it stands.
     */
    private static String studio(Minecraft mc, LocalPlayer p, String item) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || item.isEmpty()) return "arms studio <item id> (single player)";
        if (studioState == 2) {
            studioState = 0;
            return "studio built with " + item;
        }
        if (studioState == 0) {
            studioState = 1;
            java.util.UUID id = p.getUUID();
            server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayer(id);
                if (sp != null) {
                    ServerLevel level = sp.level();
                    for (int cx = -1; cx <= 0; cx++) for (int cz = -1; cz <= 0; cz++) level.getChunk(cx, cz);
                    CommandSourceStack src = sp.createCommandSourceStack().withSuppressedOutput();
                    for (String c : new String[] {"tp @s 0.5 201 0.5 0 0", "fill -10 199 -8 10 199 12 minecraft:smooth_stone",
                            "fill -10 200 -8 10 210 12 minecraft:air", "fill -10 200 4 10 203 4 minecraft:deepslate_tiles",
                            "tp @s 0.5 200 0.9 0 0", "kill @e[type=minecraft:husk]",
                            "summon minecraft:husk 0.5 200 3.5 {PersistenceRequired:1b,Rotation:[180f,0f],Health:1000f,attributes:["
                                    + "{id:\"minecraft:max_health\",base:1000d},{id:\"minecraft:movement_speed\",base:0d},"
                                    + "{id:\"minecraft:attack_damage\",base:0d}]}",
                            "item replace entity @s weapon.mainhand with " + item, "effect give @s minecraft:resistance 100000 4 true"}) {
                        server.getCommands().performPrefixedCommand(src, c);
                    }
                }
                studioState = 2;
            });
        }
        return null;
    }

    private static int traceUntil;
    private static final float[] POSE = new float[5];

    /** Lets go of the keys whose time is up (each client tick). */
    static void tick() {
        if (!Double.isNaN(parryAt)) {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) parry(client, client.player);
        }
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
