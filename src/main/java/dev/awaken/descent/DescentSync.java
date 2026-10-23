package dev.awaken.descent;

import dev.awaken.core.AwakenNet;
import dev.awaken.descent.net.DescentPayloads;
import dev.awaken.descent.player.DeepPack;
import dev.awaken.descent.player.DescentData;
import dev.awaken.progression.Progression;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** Server → client state of the descent module. */
public final class DescentSync {
    private DescentSync() {}

    public static DescentData data(ServerPlayer player) {
        return player.getData(DescentContent.DATA.get());
    }

    public static DeepPack pack(ServerPlayer player) {
        return player.getData(DescentContent.DEEP_PACK.get());
    }

    public static void packChanged(ServerPlayer player) {
        if (AwakenNet.canReceive(player, DescentPayloads.Sync.TYPE)) player.syncData(DescentContent.DEEP_PACK.get());
    }

    /** Deepest depth index this player's rank opens (for the Codex and the Day's End line). */
    public static int openTo(ServerPlayer player) {
        DescentData d = data(player);
        var p = Progression.profile(player);
        int open = 1;
        for (int n = 2; n <= Strata.COUNT; n++) {
            if (Strata.access(p.rank(), p.level(), d.seedCleared, n) == Strata.Access.OPEN) open = n;
            else break;
        }
        if (open == Strata.COUNT && d.seedCleared) open = Strata.ENDLESS_BASE + 1;
        return open;
    }

    public static void push(ServerPlayer player) {
        DescentData d = data(player);
        List<DescentPayloads.GraveMark> graves = new ArrayList<>();
        for (DescentData.Grave g : d.graves) graves.add(new DescentPayloads.GraveMark(g.dimension(), g.pos(), g.depth()));
        AwakenNet.send(player, new DescentPayloads.Sync(new ArrayList<>(d.visited), new ArrayList<>(d.lit), graves,
                d.deepest, openTo(player), d.seedCleared, d.checkpoint));
        packChanged(player);
    }

    public static void hud(ServerPlayer player, int depth, Component status, int noise, int rules) {
        AwakenNet.send(player, new DescentPayloads.Hud(depth, status, noise, rules));
    }
}
