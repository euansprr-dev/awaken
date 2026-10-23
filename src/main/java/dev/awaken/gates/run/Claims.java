package dev.awaken.gates.run;

import dev.awaken.core.DirectorLog;
import dev.awaken.gates.Claim;
import dev.awaken.gates.GateRegistry;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Claim delivery (plan contract "Gate run and reward claim"): 1 the PENDING claim already holds the Satchel's contents;
 * 2 move items to the inventory (overflow drops at your feet, outside the gate); 3 grant the Satchel XP; 4 mark DONE;
 * 5 show the Notice. Delivery happens only outside the gate dimension, so nothing loops back into a Satchel. A DONE
 * claim is never paid twice; replaying after a crash is safe.
 */
public final class Claims {
    private Claims() {}

    /** Writes a claim; false if this id was already claimed (the caller must then keep the loot where it was). */
    public static boolean write(ServerPlayer p, Claim c) {
        boolean ok = GateRegistry.get(p.level().getServer()).writeClaim(c);
        DirectorLog.log(p, "gate", "claim " + c.id() + (ok ? " written (" + c.items().size() + " stacks, " + Math.round(c.xp()) + " XP)" : " refused: already claimed"));
        return ok;
    }

    /** Pays every pending claim of this player, if they are outside a gate. Returns how many were paid. */
    public static int deliver(ServerPlayer p) {
        if (dev.awaken.gates.GateService.inGate(p) || !p.isAlive()) return 0;
        GateRegistry reg = GateRegistry.get(p.level().getServer());
        List<Claim> pending = reg.pendingFor(p.getUUID());
        int paid = 0;
        for (Claim c : pending) {
            if (reg.isDone(c.id())) {
                reg.markDone(c.id());
                continue;
            }
            pay(p, c);
            reg.markDone(c.id());
            paid++;
        }
        return paid;
    }

    private static void pay(ServerPlayer p, Claim c) {
        int stacks = 0, items = 0;
        for (ItemStack s : c.items()) {
            ItemStack give = s.copy(); // the claim's own copy is dropped with the claim: loot moves once
            items += give.getCount();
            stacks++;
            if (!p.addItem(give) && !give.isEmpty()) p.drop(give, false, Prediction.SERVER_ONLY);
        }
        int levels = 0;
        if (c.xp() > 0) {
            levels = Progression.grantXp(p, c.xp(), "boss".equals(c.kind()) ? Progression.XpSource.GATE_BOSS : Progression.XpSource.GATE_FLOOR);
        }
        if (!"silent".equals(c.kind())) {
            var w = SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(switch (c.kind()) {
                case "boss" -> "The hoard is yours";
                case "death" -> "Half your Satchel came back with you";
                case "recover" -> "Recovered your loot";
                default -> "Extracted";
            }));
            w.row("Items", items + " in " + stacks + " stack" + (stacks == 1 ? "" : "s"));
            if (c.xp() > 0) w.row("Satchel XP", "+" + Math.round(c.xp()) + (levels > 0 ? "  (+" + levels + " level" + (levels > 1 ? "s" : "") + ")" : ""));
            if (!c.note().isEmpty()) w.body(Component.literal(c.note()));
            Signal.send(p, w.ttl(20 * 7).build());
        }
    }

    // ---- Day's End --------------------------------------------------------------------------------------------------

    /** Adds one gate to today's Day's End tally. */
    public static void countGateToday(ServerPlayer p) {
        Progression.addGateToday(p);
    }
}
