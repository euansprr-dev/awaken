package dev.awaken.mobs.entity;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

/**
 * The lag watch (SPEC §46.1 "Hits land now"): while a fight is on, any server tick over {@link #SLOW_MS} logs one line
 * with its length and what took it, so a play session names its stalls instead of us guessing (Euan 2026-11-02:
 * "sometimes there is server lag when fighting"). Timed: the bosses' fight scripts, the creatures' abilities and the
 * hazards (ours); the garbage collector's pauses; chunks loaded; an autosave. What's left is the rest of the game.
 * Costs two clock reads per timed part, and nothing at all out of a fight.
 */
public final class FightWatch {
    private FightWatch() {}

    private static final Logger LOGGER = LogUtils.getLogger();
    /** A tick this long is a stall a player feels (a whole tick late). */
    public static final int SLOW_MS = 50;
    /** A fight is on for this many ticks after a creature last fought a player. */
    static final int FIGHT_TICKS = 100;
    /** At most one line per this many ticks (a long stall run logs its first and then once a second). */
    static final int LOG_GAP = 20;

    private static final List<GarbageCollectorMXBean> GC = ManagementFactory.getGarbageCollectorMXBeans();

    private static long kit, abilities, hazards;
    private static int fightAt = -1_000_000, loggedAt = -1_000_000, slowTicks;
    private static long gcAt;
    private static int chunksAt;
    private static boolean saved, watching;

    /** The longest tick logged this session and the ticks over {@link #SLOW_MS} (the fight-cost test reads them). */
    public static long worstNanos;

    public static void init() {
        NeoForge.EVENT_BUS.addListener(FightWatch::onPre);
        NeoForge.EVENT_BUS.addListener(FightWatch::onPost);
        NeoForge.EVENT_BUS.addListener(FightWatch::onSave);
    }

    /** A creature fought a player this tick (its target is one). */
    static void fighting(ServerLevel level) {
        fightAt = level.getServer().getTickCount();
    }

    static void kit(long nanos) {
        kit += nanos;
    }

    static void abilities(long nanos) {
        abilities += nanos;
    }

    public static void hazards(long nanos) {
        hazards += nanos;
    }

    /** How many fight ticks went over {@link #SLOW_MS} this session. */
    public static int slowTicks() {
        return slowTicks;
    }

    private static void onPre(ServerTickEvent.Pre e) {
        MinecraftServer server = e.getServer();
        kit = abilities = hazards = 0;
        saved = false;
        watching = server.getTickCount() - fightAt <= FIGHT_TICKS;
        if (!watching) return;
        gcAt = gcMillis();
        chunksAt = chunks(server);
    }

    private static void onSave(LevelEvent.Save e) {
        saved = true;
    }

    private static void onPost(ServerTickEvent.Post e) {
        if (!watching) return;
        MinecraftServer server = e.getServer();
        long t = server.getTickTimesNanos()[server.getTickCount() % 100];
        if (t < SLOW_MS * 1_000_000L) return;
        slowTicks++;
        worstNanos = Math.max(worstNanos, t);
        int now = server.getTickCount();
        if (now - loggedAt < LOG_GAP) return;
        loggedAt = now;
        long gc = gcMillis() - gcAt;
        int chunks = chunks(server) - chunksAt;
        int entities = 0, levels = 0;
        for (ServerLevel l : server.getAllLevels()) {
            levels++;
            for (var ignored : l.getAllEntities()) entities++;
        }
        double ours = (kit + abilities + hazards) / 1e6;
        LOGGER.warn("[fights] slow tick {} ms in a fight: ours {} ms (fight scripts {}, abilities {}, hazards {}); garbage collection {} ms; "
                        + "chunks loaded {}{}; {} entities in {} levels; the rest {} ms",
                ms(t / 1e6), ms(ours), ms(kit / 1e6), ms(abilities / 1e6), ms(hazards / 1e6), gc, signed(chunks),
                saved ? "; an autosave" : "", entities, levels, ms(Math.max(0, t / 1e6 - ours - gc)));
    }

    private static long gcMillis() {
        long n = 0;
        for (GarbageCollectorMXBean b : GC) n += Math.max(0, b.getCollectionTime());
        return n;
    }

    private static int chunks(MinecraftServer server) {
        int n = 0;
        for (ServerLevel l : server.getAllLevels()) n += l.getChunkSource().getLoadedChunksCount();
        return n;
    }

    private static String ms(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    private static String signed(int n) {
        return n > 0 ? "+" + n : Integer.toString(n);
    }
}
