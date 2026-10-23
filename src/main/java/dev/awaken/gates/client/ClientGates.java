package dev.awaken.gates.client;

import dev.awaken.gates.net.GatePayloads;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** What the client knows about gates: the beacons it was told about, the run HUD, the last Satchel view. */
public final class ClientGates {
    private ClientGates() {}

    static volatile List<GatePayloads.Beacon> beacons = List.of();
    static volatile GatePayloads.RunHud hud = GatePayloads.RunHud.NONE;
    static volatile long hudAt;
    static volatile List<ItemStack> satchel = List.of();
    static volatile int satchelXp;

    public static List<GatePayloads.Beacon> beacons() {
        return beacons;
    }

    public static GatePayloads.RunHud hud() {
        return hud;
    }

    public static boolean inRun() {
        return hud.gateId() >= 0;
    }

    static void clear() {
        beacons = List.of();
        hud = GatePayloads.RunHud.NONE;
        satchel = List.of();
        satchelXp = 0;
    }

    static final int[] RANK_COLOURS = {0xFFF4F6FB, 0xFF5FD08F, 0xFF6FB3FF, 0xFFB48CFF, 0xFFF2A541, 0xFFFF5A6A, 0xFFD4AF37};
    static final String[] RANK_LABELS = {"E", "D", "C", "B", "A", "S", "Sov"};

    static int rankColour(int rank) {
        return RANK_COLOURS[Math.max(0, Math.min(6, rank))];
    }

    static String rankLabel(int rank) {
        return RANK_LABELS[Math.max(0, Math.min(6, rank))];
    }
}
