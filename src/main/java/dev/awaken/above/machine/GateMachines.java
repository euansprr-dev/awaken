package dev.awaken.above.machine;

import dev.awaken.above.Above;
import dev.awaken.above.AboveData;
import dev.awaken.above.MachineType;
import dev.awaken.above.net.AboveNet;
import dev.awaken.above.net.Panel;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.GateHooks;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Gate Scanner (reads gates within 2,000 blocks) and Gate Stabiliser (holds a scanned gate open longer). */
final class GateMachines {
    private GateMachines() {}

    static final int SCAN_RANGE = 2000;
    static final int SCAN_COST = 25;
    static final int HOLD_COST_PER_DAY = 100;
    static final int HOLD_DAYS = 3;
    private static final Set<String> GONE = Set.of("CLEARED", "BROKEN", "RELEASED");

    static String breaks(ServerLevel level, GateHooks.GateView g, long today) {
        if (GONE.contains(g.state())) return g.state().toLowerCase();
        // no clock: it never breaks, or it waits for someone to come close before it starts (SPEC §7)
        if (g.breaksOnPlayDay() == Long.MAX_VALUE) return GateHooks.waiting(level, g.id()) ? "won't break until someone goes near it" : "never breaks";
        long in = g.breaksOnPlayDay() - today;
        if (in <= 0) return "breaks today";
        return "breaks in " + in + " play-day" + (in == 1 ? "" : "s");
    }

    static String name(GateHooks.GateView g) {
        return Rank.of(g.rank()).label + "-rank · " + (g.themeName().isEmpty() ? "Unknown gate" : g.themeName());
    }

    // ---- Gate Scanner ---------------------------------------------------------------------------------------------

    static Panel scannerPanel(ServerPlayer player, MachineBlockEntity be) {
        MachineType t = MachineType.GATE_SCANNER;
        if (be.poweredEssence() < SCAN_COST) return Machines.noPower(be, t);
        ServerLevel level = (ServerLevel) be.getLevel();
        BlockPos pos = be.getBlockPos();
        be.draw(SCAN_COST);
        long today = PlayDays.current(level.getServer());
        List<GateHooks.GateView> gates = new ArrayList<>(GateHooks.near(level, pos, SCAN_RANGE));
        gates.sort(Comparator.comparingDouble(g -> g.pos().distSqr(pos)));
        Panel.Builder b = Machines.base(be, t, true);
        AboveData d = Above.data(player);
        List<AboveNet.GateMark> marks = new ArrayList<>();
        if (gates.isEmpty()) {
            b.text("Nothing", "No gates within 2,000 blocks. The sky is quiet.", Machines.LABEL);
        } else {
            b.header(gates.size() + " gate" + (gates.size() == 1 ? "" : "s") + " in range");
            for (GateHooks.GateView g : gates.subList(0, Math.min(16, gates.size()))) {
                String detail = Travel.bearing(pos, g.pos()) + " · " + breaks(level, g, today) + (g.omen().isEmpty() ? "" : " · “" + g.omen() + "”");
                b.text(name(g), detail, Rank.of(g.rank()).colour);
                d.addScannedGate(g.id());
                marks.add(new AboveNet.GateMark(g.id(), g.pos().getX(), g.pos().getZ(), g.rank(), g.themeName(), g.omen(),
                        g.breaksOnPlayDay() == Long.MAX_VALUE ? (GateHooks.waiting(level, g.id()) ? AboveNet.GateMark.WAITING : AboveNet.GateMark.NEVER)
                                : (int) Math.max(0, g.breaksOnPlayDay() - today)));
            }
        }
        AwakenNet.send(player, new AboveNet.GateMarks(marks));
        Above.sync(player);
        Progression.count(player, "awaken:gate_scans", 1);
        DirectorLog.log(player, "gate-scanner", marks.size() + " gates within " + SCAN_RANGE);
        if (!gates.isEmpty()) {
            SignalWindow.Builder w = SignalWindow.builder(SignalWindow.Kind.GATE, Component.literal("Gate Scanner"))
                    .footer(Component.literal("Marked on your Gate Map · K"));
            for (GateHooks.GateView g : gates.subList(0, Math.min(3, gates.size()))) {
                w.row(Component.literal(name(g)), Component.literal(Travel.bearing(pos, g.pos()) + " · " + breaks(level, g, today)));
            }
            Signal.send(player, w.ttl(20 * 8).build());
        }
        return b.footer("Each reading costs " + SCAN_COST + " essence. A Gate Stabiliser can hold any gate read here.").build();
    }

    static void scannerAction(ServerPlayer player, MachineBlockEntity be, String action, int index) {
        // The scanner has no buttons: reopening the panel takes a new reading.
    }

    // ---- Gate Stabiliser ------------------------------------------------------------------------------------------

    private static List<GateHooks.GateView> holdable(ServerPlayer player, ServerLevel level) {
        List<GateHooks.GateView> out = new ArrayList<>();
        for (String id : List.copyOf(Above.data(player).scannedGates())) {
            Optional<GateHooks.GateView> g = GateHooks.get(level, id);
            if (g.isEmpty() || GONE.contains(g.get().state())) {
                Above.data(player).removeScannedGate(id);
                continue;
            }
            out.add(g.get());
        }
        out.sort(Comparator.comparing(GateHooks.GateView::id));
        return out;
    }

    static Panel stabiliserPanel(ServerPlayer player, MachineBlockEntity be) {
        MachineType t = MachineType.GATE_STABILISER;
        int stored = be.poweredEssence();
        if (stored <= 0) return Machines.noPower(be, t);
        ServerLevel level = (ServerLevel) be.getLevel();
        long today = PlayDays.current(level.getServer());
        Panel.Builder b = Machines.base(be, t, true);
        List<GateHooks.GateView> gates = holdable(player, level);
        if (gates.isEmpty()) {
            b.text("No targets", "Read gates with a Gate Scanner first. Any gate it found can be held here.", Machines.AMBER);
        }
        AboveData d = Above.data(player);
        int cost = HOLD_COST_PER_DAY * HOLD_DAYS;
        for (int i = 0; i < gates.size(); i++) {
            GateHooks.GateView g = gates.get(i);
            boolean usedToday = d.stabilisedDay(g.id()) == today;
            String detail = Travel.bearing(be.getBlockPos(), g.pos()) + " · " + breaks(level, g, today)
                    + (usedToday ? " · held today already" : "");
            b.button(name(g), detail, Rank.of(g.rank()).colour, "hold", i, "Hold (" + cost + ")", !usedToday && stored >= cost);
        }
        return b.footer("One charge per gate per play-day. Holding a gate delays its break by up to " + HOLD_DAYS + " play-days.").build();
    }

    static void stabiliserAction(ServerPlayer player, MachineBlockEntity be, String action, int index) {
        if (!action.equals("hold")) return;
        ServerLevel level = (ServerLevel) be.getLevel();
        List<GateHooks.GateView> gates = holdable(player, level);
        if (index < 0 || index >= gates.size()) return;
        GateHooks.GateView g = gates.get(index);
        long today = PlayDays.current(level.getServer());
        AboveData d = Above.data(player);
        if (d.stabilisedDay(g.id()) == today) return;
        for (int days = HOLD_DAYS; days >= 1; days--) {
            int cost = HOLD_COST_PER_DAY * days;
            if (be.poweredEssence() < cost) continue;
            if (GateHooks.stabilise(level, g.id(), days)) {
                be.draw(cost);
                d.setStabilised(g.id(), today);
                Progression.count(player, Above.GATES_STABILISED, 1);
                DirectorLog.log(player, "gate-stabiliser", "held " + g.id() + " +" + days + " days");
                Signal.toast(player, Component.literal(name(g) + " held · +" + days + " play-day" + (days == 1 ? "" : "s")));
                Above.sync(player);
                return;
            }
        }
        player.sendOverlayMessage(Component.literal("The gate won't hold any longer."));
    }
}
