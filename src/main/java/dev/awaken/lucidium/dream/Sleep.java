package dev.awaken.lucidium.dream;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenConfig;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.lucidium.LucidiumContent;
import dev.awaken.lucidium.block.DreamBellBlock;
import dev.awaken.lucidium.data.Dreamer;
import dev.awaken.lucidium.data.LucidiumWorld;
import dev.awaken.lucidium.journal.DayJournal;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Stat;
import dev.awaken.signal.Signal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.ClockAdjustment;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Falling asleep into a dream. A successful sleep rolls the dream chance once; if it hits and tonight's plan is
 * ready, the arrival island is loaded while the screen fades, and just before the night would be skipped the sleeper
 * (and anyone sleeping in the same room) slips into the Lucidium. Not ready means a normal night: "You slept deeply."
 * Sleep is never blocked.
 */
public final class Sleep {
    private Sleep() {}

    /** Enter the dream at this sleep timer (vanilla skips the night at 100). */
    public static final int ENTER_AT = 90;
    public static final int SAME_ROOM = 10;

    public enum Outcome { NONE, DISABLED, DREAMLESS, NO_DREAM, NOT_READY, DREAMING }

    public static final class Session {
        public BlockPos bed;
        public boolean dreamless;
        public boolean roll;
        public boolean wbtb;
        public boolean entered;
        public Outcome outcome = Outcome.NONE;
        Planner.Prepared prepared;
        /** The planner let {@link #prepared} go while this sleeper held it: the night gives its cell back unless dreamed. */
        boolean ownsPlan;
        boolean preloaded;
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static long lastTimeOfDay = -1;

    public static Optional<Session> session(ServerPlayer sp) {
        return Optional.ofNullable(SESSIONS.get(sp.getUUID()));
    }

    // ---- per player --------------------------------------------------------------------------------------------------

    static void onPlayerTick(ServerPlayer sp) {
        if (!sp.isSleeping()) {
            end(sp.level().getServer(), SESSIONS.remove(sp.getUUID()));
            return;
        }
        if (sp.level().dimension() == LucidiumContent.LUCIDIUM || Dreams.isDreaming(sp)) return;
        Session s = SESSIONS.get(sp.getUUID());
        if (s == null) {
            s = begin(sp);
            SESSIONS.put(sp.getUUID(), s);
        }
        if (s.entered || s.dreamless || !s.roll) return;
        if (s.prepared == null) {
            s.prepared = Planner.ready(sp).orElse(null);
            if (s.prepared != null) preload(sp, s);
            else if (!Planner.building(sp)) Planner.request(sp, "sleep");
        }
        if (sp.getSleepTimer() >= ENTER_AT) tryEnter(sp, s);
    }

    /**
     * The moment of falling asleep: into the dream if one rolled and its plan is ready; otherwise a normal night
     * ("You slept deeply" on waking). Never blocks sleep.
     */
    public static Outcome tryEnter(ServerPlayer sp, Session s) {
        if (s.entered || s.dreamless || !s.roll) return s.outcome;
        if (s.prepared == null || Dreams.dreamLevel(sp.level().getServer()) == null) {
            s.outcome = Outcome.NOT_READY;
            s.roll = false;
            DirectorLog.log(sp, "dream", "plan not ready at sleep: a normal night");
            return s.outcome;
        }
        enterFromSleep(sp, s);
        return s.outcome;
    }

    /** Decides tonight's sleep once, as the player lies down. */
    public static Session begin(ServerPlayer sp) {
        Session s = new Session();
        s.bed = sp.getSleepingPos().orElse(sp.blockPosition());
        MinecraftServer server = sp.level().getServer();
        LucidiumWorld.get(server).setBed(sp.getUUID(), GlobalPos.of(sp.level().dimension(), s.bed));
        DayJournal.onSleep(sp, s.bed);
        if (!AwakenConfig.LUCIDIUM.get()) {
            s.outcome = Outcome.DISABLED;
            return s;
        }
        if (sp.isShiftKeyDown()) {
            s.dreamless = true;
            s.outcome = Outcome.DREAMLESS;
            return s;
        }
        Dreamer d = Dreams.dreamer(sp);
        long now = Dreams.now(server);
        long day = PlayDays.current(server);
        s.wbtb = d.wbtbUntil > 0 && now <= d.wbtbUntil;
        boolean intention = !d.intention(day).isEmpty();
        boolean sanctuary = dev.awaken.hearth.Hearth.isSanctuary(sp.level(), s.bed) || blockNear((ServerLevel) sp.level(), s.bed, 6, 3, MEDITATION_MAT);
        boolean homeBed = HearthHooks.hearthsOf(sp).stream().anyMatch(h -> h.distSqr(s.bed) <= 48 * 48);
        double chance = Lucidity.dreamChance(Progression.stat(sp, Stat.SPI), sanctuary, homeBed, intention, s.wbtb);
        if (d.dreams == 0) chance = 1.0; // the first night always dreams
        s.roll = sp.getRandom().nextDouble() < chance;
        if (!s.roll) s.outcome = Outcome.NO_DREAM;
        DirectorLog.log(sp, "dream", String.format("sleep: chance %.0f%% (SPI %d%s%s%s%s) -> %s", chance * 100,
                Progression.stat(sp, Stat.SPI), sanctuary ? ", sanctuary" : "", homeBed ? ", home bed" : "",
                intention ? ", intention" : "", s.wbtb ? ", WBTB" : "", s.roll ? "dream" : "no dream"));
        return s;
    }

    /** Sneak held in bed (sent by the client, which can see the key while the bed screen is open). */
    public static void chooseDreamless(ServerPlayer sp) {
        Session s = SESSIONS.get(sp.getUUID());
        if (s == null || s.entered || s.dreamless) return;
        s.dreamless = true;
        s.outcome = Outcome.DREAMLESS;
        sp.sendOverlayMessage(Component.translatable("hud.awaken.dreamless"));
    }

    private static void preload(ServerPlayer sp, Session s) {
        ServerLevel level = Dreams.dreamLevel(sp.level().getServer());
        if (level == null || s.preloaded) return;
        s.preloaded = true;
        dev.awaken.lucidium.world.DreamChunkGenerator.register(s.prepared.blueprint());
        level.getChunkSource().addTicketWithRadius(LucidiumContent.PRELOAD.get(),
                ChunkPos.containing(BlockPos.containing(s.prepared.blueprint().spawn)), 2);
    }

    private static void enterFromSleep(ServerPlayer host, Session s) {
        MinecraftServer server = host.level().getServer();
        Planner.Prepared prepared = Planner.take(host).orElse(s.prepared);
        // a newer plan is ready than the one this sleeper was left holding: the old one is never dreamed
        if (s.ownsPlan && prepared != s.prepared) Planner.discard(server, s.prepared);
        s.ownsPlan = false; // the plan entered belongs to the dream now
        s.entered = true;
        List<ServerPlayer> guests = new ArrayList<>();
        for (ServerPlayer other : ((ServerLevel) host.level()).players()) {
            if (other == host || !other.isSleeping() || Dreams.isDreaming(other)) continue;
            Session os = SESSIONS.get(other.getUUID());
            if (os != null && (os.dreamless || os.entered)) continue;
            if (other.getSleepingPos().map(b -> b.distSqr(s.bed) <= SAME_ROOM * SAME_ROOM).orElse(false)) guests.add(other);
        }
        Map<ServerPlayer, BlockPos> guestBeds = new HashMap<>();
        for (ServerPlayer g : guests) guestBeds.put(g, g.getSleepingPos().orElse(g.blockPosition()));
        host.stopSleepInBed(true, true);
        ActiveDream ad = Dreams.enter(host, prepared, new Dreams.Entry(true, s.bed, null, s.wbtb));
        if (ad == null) {
            Planner.discard(server, prepared);
            s.outcome = Outcome.NOT_READY;
            Signal.toast(host, Component.translatable("hud.awaken.slept_deeply"));
            return;
        }
        s.outcome = Outcome.DREAMING;
        for (ServerPlayer g : guests) {
            Session gs = SESSIONS.computeIfAbsent(g.getUUID(), k -> new Session());
            gs.entered = true;
            gs.outcome = Outcome.DREAMING;
            g.stopSleepInBed(true, true);
            Dreamer gd = Dreams.dreamer(g);
            boolean wbtb = gd.wbtbUntil > 0 && Dreams.now(g.level().getServer()) <= gd.wbtbUntil;
            Dreams.join(g, ad, new Dreams.Entry(true, guestBeds.get(g), null, wbtb));
            Signal.toast(g, Component.literal("You dream someone else's dream."));
        }
    }

    // ---- vanilla wake and the Dream Bell -----------------------------------------------------------------------------

    static void onWake(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || event.wakeImmediately()) return;
        Session s = SESSIONS.get(sp.getUUID());
        if (s == null) return;
        switch (s.outcome) {
            case NOT_READY -> Signal.toast(sp, Component.translatable("hud.awaken.slept_deeply"));
            case DREAMLESS -> Signal.toast(sp, Component.translatable("hud.awaken.dreamless_morning"));
            default -> {}
        }
    }

    private static final java.util.function.Predicate<BlockState> ARMED_BELL =
            st -> st.is(LucidiumContent.DREAM_BELL.get()) && st.getValue(DreamBellBlock.ARMED);
    private static final java.util.function.Predicate<BlockState> MEDITATION_MAT = st -> {
        var id = BuiltInRegistries.BLOCK.getKey(st.getBlock());
        return Awaken.MODID.equals(id.getNamespace()) && id.getPath().contains("meditation");
    };

    /** The night is about to be skipped: an armed Dream Bell by a sleeper's bed stops it at midnight instead. */
    static void onSleepFinished(SleepFinishedTimeEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long tod = Math.floorMod(level.getDefaultClockTime(), 24000L);
        if (tod < 12000 || tod >= 18000) return;
        List<ServerPlayer> ringing = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (!p.isSleeping()) continue;
            BlockPos bed = p.getSleepingPos().orElse(null);
            if (bed != null && findNear(level, bed, 8, 4, ARMED_BELL).isPresent()) ringing.add(p);
        }
        if (ringing.isEmpty()) return;
        event.setAdjustment(new ClockAdjustment.Marker(ClockTimeMarkers.MIDNIGHT));
        long now = Dreams.now(level.getServer());
        for (ServerPlayer p : ringing) {
            Dreams.dreamer(p).wbtbUntil = now + 1000;
            findNear(level, p.getSleepingPos().orElse(p.blockPosition()), 8, 4, ARMED_BELL).ifPresent(b ->
                    level.playSound(null, b, LucidiumContent.BELL.get(), SoundSource.BLOCKS, 1.0f, 1.0f));
            Signal.toast(p, Component.translatable("hud.awaken.bell_midnight"));
            DirectorLog.log(p, "dream", "Dream Bell: woke at midnight, WBTB window open");
        }
    }

    /** Server tick: dusk planning and the bell ringing for dreamers at midnight. */
    static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        ServerLevel ow = server.overworld();
        long tod = Math.floorMod(ow.getOverworldClockTime(), 24000L);
        long last = lastTimeOfDay;
        lastTimeOfDay = tod;
        if (last < 0) return;
        boolean crossed = (last < 12000 && tod >= 12000) || (last > tod && tod >= 12000);
        if (crossed && AwakenConfig.LUCIDIUM.get()) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) Planner.request(p, "dusk");
        }
        boolean midnight = (last < 18000 && tod >= 18000) || (last > tod && tod >= 18000);
        if (midnight) {
            for (ServerPlayer p : List.copyOf(server.getPlayerList().getPlayers())) {
                var st = Dreams.state(p);
                if (!st.active || !st.fromSleep || st.bed == null) continue;
                ServerLevel bedLevel = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.Identifier.parse(st.returnDim)));
                if (bedLevel == null) continue;
                Optional<BlockPos> bell = findNear(bedLevel, st.bed, 8, 4, ARMED_BELL);
                if (bell.isEmpty()) continue;
                bedLevel.playSound(null, bell.get(), LucidiumContent.BELL.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
                Dreams.wake(p, Dreams.Wake.BELL);
            }
        }
    }

    /** After a dream started from sleep: bring the morning if everyone else is asleep too, then the Day's End card. */
    static void afterDream(ServerPlayer sp, BlockPos bed) {
        MinecraftServer server = sp.level().getServer();
        ServerLevel ow = server.overworld();
        if (ow.isDarkOutside()) {
            boolean othersDreaming = false;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p != sp && Dreams.isDreaming(p) && Dreams.state(p).fromSleep) othersDreaming = true;
            }
            boolean othersAsleep = ow.players().stream().filter(p -> p != sp && !p.isSpectator()).allMatch(Player::isSleeping);
            if (othersDreaming || !othersAsleep) {
                Signal.toast(sp, Component.translatable("hud.awaken.night_not_over"));
                return;
            }
            if (ow.getGameRules().get(GameRules.ADVANCE_TIME)) {
                ow.dimensionType().defaultClock().ifPresent(c -> server.clockManager().moveToTimeMarker(c, ClockTimeMarkers.WAKE_UP_FROM_SLEEP));
            }
            if (ow.getGameRules().get(GameRules.ADVANCE_WEATHER) && ow.isRaining()) ow.resetWeatherCycle();
            for (ServerPlayer p : List.copyOf(ow.players())) if (p.isSleeping()) p.stopSleepInBed(false, true);
        }
        // the dream counted as the night's sleep: let every module see the morning wake (Day's End card)
        NeoForge.EVENT_BUS.post(new PlayerWakeUpEvent(sp, false, false));
    }

    // ---- helpers -----------------------------------------------------------------------------------------------------

    static Optional<BlockPos> findNear(ServerLevel level, BlockPos center, int r, int ry, java.util.function.Predicate<BlockState> test) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dy = -ry; dy <= ry; dy++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (!level.isLoaded(m)) continue;
                    if (test.test(level.getBlockState(m))) return Optional.of(m.immutable());
                }
        return Optional.empty();
    }

    static boolean blockNear(ServerLevel level, BlockPos center, int r, int ry, java.util.function.Predicate<BlockState> test) {
        return findNear(level, center, r, ry, test).isPresent();
    }

    /** A sleeper lies down with this session (the player tick does it in play; tests call it). */
    static void lieDown(ServerPlayer sp, Session s) {
        SESSIONS.put(sp.getUUID(), s);
    }

    static void forget(ServerPlayer sp) {
        end(sp.level().getServer(), SESSIONS.remove(sp.getUUID()));
    }

    /** The planner drops a plan: a sleeper about to dream it takes it over (true), so it isn't erased under them. */
    static boolean adopt(Planner.Prepared p) {
        for (Session s : SESSIONS.values()) {
            if (!s.entered && s.prepared == p) {
                s.ownsPlan = true;
                return true;
            }
        }
        return false;
    }

    /** A night ends: a plan it took over and never dreamed gives its cell back. */
    private static void end(MinecraftServer server, Session s) {
        if (s != null && s.ownsPlan && s.prepared != null) Planner.discard(server, s.prepared);
    }

    static void reset() {
        SESSIONS.clear();
        lastTimeOfDay = -1;
    }
}
