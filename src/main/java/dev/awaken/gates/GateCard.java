package dev.awaken.gates;

import dev.awaken.core.PlayDays;
import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.content.Modifier;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Stat;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The gate card (SPEC section 7): within 24 blocks of a gate you see its rank, when it breaks and its omen. PER 20 adds
 * the layout, PER 40 the modifiers, PER 60 the floor count; a gate scouted from the Veil shows everything. A gate of
 * another rank says how it stands to yours: above it, or below it and paying less (×0.3, {@code Curve.BELOW_RANK_FACTOR}).
 */
public final class GateCard {
    private GateCard() {}

    public static final int RANGE = 24, RESET = 48;
    private static final Map<UUID, Integer> SHOWN = new HashMap<>();

    /** Server start/stop: nothing carries over to another world. */
    static void resetAll() {
        SHOWN.clear();
    }

    static void reset(ServerPlayer p) {
        SHOWN.remove(p.getUUID());
    }

    static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 7) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level().dimension() != Level.OVERWORLD) continue;
            Integer shown = SHOWN.get(p.getUUID());
            GateRecord near = null;
            double best = Double.MAX_VALUE;
            for (GateRecord g : GateRegistry.get(server).near(p.blockPosition(), RESET, false)) {
                if (!g.structurePlaced) continue;
                double d = g.pos.distSqr(p.blockPosition());
                if (shown != null && shown == g.id) {
                    if (d > RESET * RESET) SHOWN.remove(p.getUUID());
                }
                if (d < best) {
                    best = d;
                    near = g;
                }
            }
            if (near == null) {
                SHOWN.remove(p.getUUID());
                continue;
            }
            if (best > RANGE * RANGE || (shown != null && shown == near.id)) continue;
            SHOWN.put(p.getUUID(), near.id);
            send(p, near);
        }
    }

    /** The card's line for a gate of {@code gate} rank to a hunter of {@code hunter} rank, or null at their own rank. */
    static String compare(dev.awaken.progression.Rank gate, dev.awaken.progression.Rank hunter) {
        if (gate.ordinal() > hunter.ordinal()) return "above your rank";
        if (gate.ordinal() < hunter.ordinal()) return "below your rank · pays less";
        return null;
    }

    public static void send(ServerPlayer p, GateRecord g) {
        GatePlan plan = g.plan;
        // the story's hall says nothing once its ritual is done (the bell's last toll and its collapse say it)
        if (plan.ritual() && (!g.state.enterable() || Progression.profile(p).awakened())) return;
        long today = PlayDays.current(p.level().getServer());
        int per = Progression.stat(p, Stat.PER);
        boolean scouted = Gates.isScouted(p, g.id);
        var w = SignalWindow.builder(SignalWindow.Kind.GATE, Component.literal(plan.rank().label + "-rank gate")
                .withColor(plan.rank().colour));
        switch (g.state) {
            case BROKEN -> w.body(Component.literal("Broken open. Its Lord walks the land.").withStyle(ChatFormatting.RED));
            case CLEARED -> w.body(Component.literal("Spent. Whatever lived here is gone."));
            case BUILDING -> w.body(Component.literal("Still forming."));
            default -> {}
        }
        // how the gate stands to the hunter: a lock that says what it is (the progression plan §5.5, item 19)
        if (g.state.enterable() && !plan.ritual()) {
            String compare = compare(plan.rank(), Progression.rank(p));
            if (compare != null) w.row("For you", compare);
        }
        if (g.state.standing()) {
            long in = g.breaksIn(today);
            if (g.wandering()) {
                long left = Math.max(0, g.expiresAt - GateService.now(p.level().getServer())) / 1200;
                w.row("Fades in", left < 1 ? "under a minute" : left + " min");
            } else if (!g.scripted) {
                // a scripted gate never breaks and has no clock to show: its card tells its story instead
                w.row("Breaks in", g.neverBreaks(p.level().getServer()) ? "never · the gates are calm"
                        : g.waiting(p.level().getServer()) ? "not yet · it starts once someone comes close"
                        : in == 0 ? "today" : in + " play-day" + (in > 1 ? "s" : ""));
            }
        }
        if (plan.ritual()) {
            // the story's gate: no layout, no modifiers, no floors to count. Only a bell, somewhere far down.
            w.body(Component.literal("A bell tolls, far below. It sounds like it's waiting."));
            w.footer(Component.literal("Walk in to enter"));
            Signal.send(p, w.ttl(20 * 9).priority(3).build());
            return;
        }
        if (plan.skybornTrial()) {
            // an Ascension trial: the Lord of the sky, fought on the wing
            w.body(Component.literal("The Sky Leviathan's trial. Past the antechamber the floor gives out to open sky, and the Leviathan circles in it."));
            w.row("Trial", "Fight it in the air");
            w.footer(Component.literal("Walk in to enter"));
            Signal.send(p, w.ttl(20 * 9).priority(3).build());
            return;
        }
        if (plan.leaperTrial()) {
            // an Ascension trial: a course, not a dungeon; what it asks and what it gives
            w.body(Component.literal("The Leaper's Gauntlet. A course of the Foundry, and at its end a chasm no running jump will cross."));
            w.row("Trial", "Run it to the far side");
            w.footer(Component.literal("Walk in to enter"));
            Signal.send(p, w.ttl(20 * 9).priority(3).build());
            return;
        }
        w.row("Omen", plan.theme().omen);
        if (scouted) w.row("Theme", plan.theme().displayName);
        if (scouted) {
            String pattern = dev.awaken.core.api.ArmsApi.patternLine(p, plan.theme().id);
            if (pattern != null) w.row("Pattern", pattern);
        }
        if (per >= 20 || scouted) w.row("Layout", plan.layout().displayName);
        if (per >= 40 || scouted) {
            w.row("Modifiers", plan.modifiers().isEmpty() ? "None" : String.join(", ", plan.modifiers().stream().map((Modifier m) -> m.displayName).toList()));
        }
        if (per >= 60 || scouted) w.row("Floors", Integer.toString(plan.floors()));
        if (scouted) {
            w.row("Gate Lord", dev.awaken.gates.run.Rooms.capital(plan.theme().bossName));
            w.row("Twist", plan.twist() == dev.awaken.gates.content.Twist.NONE ? "None" : plan.twist().displayName + ": " + plan.twist().description);
            if (!plan.combos().isEmpty()) w.body(Component.literal(String.join("\n", plan.combos())));
        }
        w.footer(Component.literal(g.state.enterable() ? (scouted ? "Scouted · walk in to enter" : "Walk in to enter · scout it from the Veil to learn more")
                : ""));
        Signal.send(p, w.ttl(20 * 9).priority(3).build());
    }
}
