package dev.awaken.core;

import dev.awaken.Awaken;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Play-days: in-game days that pass while at least one player is online. Every timer in AWAKEN counts these, never
 * real-world time, so being away from the game never costs anything.
 */
public final class PlayDays extends SavedData {
    public static final int DAY = 24000;
    /** Time of day (ticks into the day) we call "morning": just after sunrise. */
    public static final int MORNING = 1000;

    public static final Codec<PlayDays> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("play_ticks").forGetter(p -> p.playTicks),
            Codec.LONG.fieldOf("last_clock").forGetter(p -> p.lastClock)
    ).apply(i, PlayDays::new));

    public static final SavedDataType<PlayDays> TYPE = new SavedDataType<>(Awaken.id("play_days"), PlayDays::new, CODEC);

    private long playTicks;
    private long lastClock = -1;

    public PlayDays() {}

    private PlayDays(long playTicks, long lastClock) {
        this.playTicks = playTicks;
        this.lastClock = lastClock;
    }

    private static final List<BiConsumer<MinecraftServer, Long>> NEW_DAY = new ArrayList<>();
    private static final List<BiConsumer<MinecraftServer, Long>> MORNING_LISTENERS = new ArrayList<>();

    /** Called when the play-day counter increases. */
    public static void onNewPlayDay(BiConsumer<MinecraftServer, Long> listener) {
        NEW_DAY.add(listener);
    }

    /** Called once each in-game morning while players are online. */
    public static void onMorning(BiConsumer<MinecraftServer, Long> listener) {
        MORNING_LISTENERS.add(listener);
    }

    public static PlayDays get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public static long current(MinecraftServer server) {
        return get(server).playTicks / DAY;
    }

    public static long playTicks(MinecraftServer server) {
        return get(server).playTicks;
    }

    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        ServerLevel overworld = server.overworld();
        PlayDays data = get(server);
        long clock = overworld.getOverworldClockTime();
        boolean anyoneOnline = !server.getPlayerList().getPlayers().isEmpty();
        if (data.lastClock < 0 || !anyoneOnline) {
            data.lastClock = clock;
            data.setDirty();
            return;
        }
        long delta = clock - data.lastClock;
        data.lastClock = clock;
        if (delta <= 0 || delta > DAY * 2L) {
            data.setDirty();
            return; // time set backwards or a huge jump from a command: don't count it
        }
        long beforeDay = data.playTicks / DAY;
        long beforeTimeOfDay = (clock - delta) % DAY;
        data.playTicks += delta;
        data.setDirty();
        long afterDay = data.playTicks / DAY;
        if (afterDay > beforeDay) {
            for (var l : NEW_DAY) l.accept(server, afterDay);
        }
        long timeOfDay = clock % DAY;
        boolean crossedMorning = (beforeTimeOfDay < MORNING && timeOfDay >= MORNING) || (delta >= DAY)
                || (beforeTimeOfDay > timeOfDay && timeOfDay >= MORNING);
        if (crossedMorning) {
            for (var l : MORNING_LISTENERS) l.accept(server, afterDay);
        }
    }
}
