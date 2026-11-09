package dev.awaken.riding.client;

import dev.awaken.riding.RidingNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** What this client knows of the threads (SPEC §55): sent by the server whenever one changes. */
final class ClientRiding {
    private ClientRiding() {}

    /** A rider in someone's shadow: from where they fell, for how long (client game time). */
    record Folding(UUID rider, UUID anchor, Vec3 from, long start, long end) {}

    /** A rider coming back. */
    record Burst(UUID anchor, long start) {}

    static final int BURST_TICKS = 12;
    /** If the server's word that a fold is over never comes, the view lets go by itself this long after its end. */
    private static final int GIVE_UP_TICKS = 40;

    private static List<RidingNet.Entry> threads = List.of();
    static final Map<UUID, Folding> FOLDS = new HashMap<>();
    static final List<Burst> BURSTS = new ArrayList<>();

    static List<RidingNet.Entry> threads() {
        return threads;
    }

    static RidingNet.@Nullable Entry of(UUID player) {
        for (RidingNet.Entry e : threads) if (e.rider().equals(player) || e.anchor().equals(player)) return e;
        return null;
    }

    static boolean folding(UUID player) {
        return FOLDS.containsKey(player);
    }

    static void receive(RidingNet.Threads m) {
        Minecraft mc = Minecraft.getInstance();
        threads = m.entries();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        Set<UUID> still = new HashSet<>();
        for (RidingNet.Entry e : m.entries()) {
            if (e.fold() <= 0) continue;
            still.add(e.rider());
            if (FOLDS.containsKey(e.rider())) continue;
            Player r = mc.level.getPlayerByUUID(e.rider());
            Vec3 from = r != null ? r.getBoundingBox().getCenter() : Vec3.ZERO;
            FOLDS.put(e.rider(), new Folding(e.rider(), e.anchor(), from, now, now + e.fold()));
        }
        for (Iterator<Folding> it = FOLDS.values().iterator(); it.hasNext(); ) {
            Folding f = it.next();
            if (still.contains(f.rider())) continue;
            BURSTS.add(new Burst(f.anchor(), now));
            it.remove();
        }
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        BURSTS.removeIf(b -> now - b.start() > BURST_TICKS + 1);
        FOLDS.values().removeIf(f -> now > f.end() + GIVE_UP_TICKS);
    }

    static String nameOf(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(id);
            if (info != null) return info.getProfile().name();
        }
        return "your partner";
    }

    static void clear() {
        threads = List.of();
        FOLDS.clear();
        BURSTS.clear();
    }
}
