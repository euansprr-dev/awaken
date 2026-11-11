package dev.awaken.mobs.entity;

import dev.awaken.Awaken;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The world's autosave is held while a boss fights a player (SPEC §68.5). An autosave stalls the server for a tenth of a
 * second (Euan's Ringer fight, 2026-11-11: one tick of 101 ms, "an autosave"), and a boss's body and the ring that closes
 * on its blow hold still with it. While a boss has a player for its target, each level's chunks are kept from the
 * autosave ({@code ServerLevel.noSave}, the flag {@code /save-off} sets); {@link #AFTER} ticks after the last such tick
 * they are let go and saved once, at a moment nobody is watching a blow.
 * <p>
 * Kept safe: only levels this turned off are turned back on (an admin's {@code /save-off} stays off); a fight longer
 * than {@link #MAX_HOLD} lets a save through; the server stopping lets go first, so its own last save writes
 * everything. What it costs: a crash in the middle of a boss fight loses that fight's minutes of the world.
 */
public final class FightSaves {
    private FightSaves() {}

    /** The hold lasts this long after a boss last fought a player (15 s: past a win's own moment), and the save comes then. */
    static final int AFTER = 300;
    /** A fight this long (ten minutes) lets one save through. */
    static final int MAX_HOLD = 12_000;
    /** The rule's switch ({@code /awaken bosses saves hold|free}). */
    public static volatile boolean enabled = true;

    private static int fightAt = -1_000_000, heldAt = -1;
    private static final List<ResourceKey<Level>> HELD = new ArrayList<>();

    public static void init() {
        NeoForge.EVENT_BUS.addListener(FightSaves::onPost);
        // before the server's own last save, which skips a level whose saving is off
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> release(e.getServer(), false));
    }

    /** A boss has a player for its target this tick (or waits, beaten, for one's finishing blow). */
    static void fighting(ServerLevel level) {
        fightAt = level.getServer().getTickCount();
    }

    private static void onPost(ServerTickEvent.Post e) {
        MinecraftServer server = e.getServer();
        int now = server.getTickCount();
        boolean fight = enabled && now - fightAt <= AFTER;
        if (heldAt < 0) {
            if (fight) hold(server, now);
        } else if (!fight || now - heldAt > MAX_HOLD) {
            release(server, true);
        }
    }

    private static void hold(MinecraftServer server, int now) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.noSave) continue; // off already (an admin's /save-off): not ours to turn back on
            level.noSave = true;
            HELD.add(level.dimension());
        }
        heldAt = now;
    }

    private static void release(MinecraftServer server, boolean save) {
        if (heldAt < 0) return;
        boolean any = !HELD.isEmpty();
        for (ResourceKey<Level> key : HELD) {
            ServerLevel level = server.getLevel(key);
            if (level != null) level.noSave = false;
        }
        HELD.clear();
        heldAt = -1;
        fightAt = Math.min(fightAt, server.getTickCount() - AFTER - 1); // a fight still on takes a fresh hold next tick
        if (!save || !any) return;
        try {
            server.saveAllChunks(true, false, false);
        } catch (RuntimeException ex) {
            Awaken.LOG.warn("[fights] the save held for a boss fight failed; the next autosave will write it", ex);
        }
    }
}
