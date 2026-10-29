package dev.awaken.gates;

import dev.awaken.core.PlayDays;
import dev.awaken.core.api.NowHooks;
import dev.awaken.gates.run.RunState;
import dev.awaken.gates.run.Runs;
import dev.awaken.progression.Progression;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** What the gates suggest right now (the Now tracker): your floor's goal inside, a gate worth walking to outside. */
final class GatesNow {
    private GatesNow() {}

    static void register() {
        NowHooks.register(GatesNow::suggest);
    }

    private static List<NowHooks.Suggestion> suggest(ServerPlayer p) {
        List<NowHooks.Suggestion> out = new ArrayList<>();
        RunState run = Runs.run(p);
        GateRecord inGate = Runs.gateOf(p);
        if (run.active() && inGate != null) {
            if (inGate.plan.ritual()) return out; // the story's hall: the story says what's next
            if (inGate.plan.skybornTrial()) {
                out.add(NowHooks.Suggestion.of("gate:trial", 80, inGate.bossDead ? "The Sky Leviathan is down: the way out is open"
                        : "The Sky Leviathan's trial: fly out past the antechamber and bring it down"));
                return out;
            }
            if (inGate.plan.leaperTrial()) {
                out.add(NowHooks.Suggestion.of("gate:trial", 80, inGate.bossDead ? "The way out is open past the course"
                        : "The Leaper's Gauntlet: run the course to the far side"));
                return out;
            }
            int floors = run.area == 0 ? inGate.plan.floors() : 2;
            if (run.clearedFloor(run.area, run.floor)) {
                out.add(NowHooks.Suggestion.of("gate:shrine", 80, "Right-click the Rest Shrine to leave with your loot, or the Seal to go deeper"));
            } else {
                boolean last = run.area == 0 && run.floor >= floors;
                out.add(NowHooks.Suggestion.of("gate:floor", 78, "Floor " + run.floor + " of " + floors
                        + (last ? " · the Lord is on this floor" : " · find the Rest Shrine")));
            }
            return out;
        }
        if (p.level().dimension() != Level.OVERWORLD || !Progression.profile(p).awakened()) return out;
        long today = PlayDays.current(p.level().getServer());
        GateRecord best = null;
        double bestD = Double.MAX_VALUE;
        for (GateRecord g : GateRegistry.get(p.level().getServer()).near(p.blockPosition(), 400, false)) {
            // the story's own gate is suggested by the story; released and building gates aren't news
            if (g.scripted || g.state == GateState.RELEASED || g.state == GateState.BUILDING || !GateExterior.shown(g)) continue;
            double d = g.pos.distSqr(p.blockPosition());
            if (g.state == GateState.BROKEN) {
                if (d < 300 * 300) out.add(NowHooks.Suggestion.at("gate:broken:" + g.id, 72,
                        "A Lord got out of a broken gate · " + (int) Math.sqrt(d) + " m", g.mouth()));
                continue;
            }
            if (!g.state.enterable()) continue;
            if (g.wandering()) {
                long left = Math.max(0, g.expiresAt - GateService.now(p.level().getServer())) / 1200;
                out.add(NowHooks.Suggestion.at("gate:wandering:" + g.id, 70, "Wandering gate · " + (int) Math.sqrt(d) + " m · fades in "
                        + (left < 1 ? "under a minute" : left + " min"), g.mouth()));
                continue;
            }
            if (d < bestD) {
                bestD = d;
                best = g;
            }
        }
        if (best != null) {
            boolean counting = best.counting(p.level().getServer());
            long in = counting ? best.breaksIn(today) : Long.MAX_VALUE;
            String when = in <= 1 ? " · breaks " + (in == 0 ? "today" : "tomorrow") : "";
            out.add(NowHooks.Suggestion.at("gate:near:" + best.id, in <= 1 ? 65 : 45,
                    best.plan.rank().label + "-rank gate · " + (int) Math.sqrt(bestD) + " m" + when, best.mouth()));
        }
        return out;
    }
}
