package dev.awaken.above.logs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.above.Above;
import dev.awaken.above.AboveSounds;
import dev.awaken.above.net.AboveNet;
import dev.awaken.core.AwakenNet;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Precursor logs from {@code data/<ns>/awaken/precursor_log/*.json} (written by tools/content/story_content.py).
 * Terminals in ruins hold a pool (a faction); reading one gives the reader the first log of that pool they haven't read.
 */
public final class PrecursorLogs {
    private PrecursorLogs() {}

    public record LogDef(String faction, String title, String text) {
        public static final Codec<LogDef> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("faction").forGetter(LogDef::faction),
                Codec.STRING.fieldOf("title").forGetter(LogDef::title),
                Codec.STRING.fieldOf("text").forGetter(LogDef::text)
        ).apply(i, LogDef::new));
    }

    /** Which logs a terminal can show. HALO = the Ascendants' own archive (includes logs written up there). */
    public enum Pool implements StringRepresentable {
        ASCENDANT("ascendant"), KEEPER("keeper"), SLEEPER("sleeper"), UNKNOWN("unknown"), HALO("halo");

        public static final Codec<Pool> CODEC = StringRepresentable.fromEnum(Pool::values);
        public final String id;

        Pool(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }

    /** Ascendant logs written on the Halo itself: only its archive holds them. */
    public static final Set<String> HALO_ONLY = Set.of("awaken:a04_halo", "awaken:a05_regret");

    /**
     * Sorted by id so "first unread" is stable. Server: from datapacks; client: from the sync payload. Swapped whole
     * (never mutated) because in single player the client and server threads share this class.
     */
    private static volatile java.util.SortedMap<String, LogDef> LOGS = java.util.Collections.emptySortedMap();

    public static Map<String, LogDef> all() {
        return LOGS;
    }

    public static LogDef get(String id) {
        return LOGS.get(id);
    }

    public static void replace(Map<String, LogDef> logs) {
        LOGS = java.util.Collections.unmodifiableSortedMap(new TreeMap<>(logs));
    }

    public static List<String> pool(Pool pool) {
        List<String> out = new ArrayList<>();
        for (var e : LOGS.entrySet()) {
            String f = e.getValue().faction();
            boolean haloOnly = HALO_ONLY.contains(e.getKey());
            boolean ok = switch (pool) {
                case HALO -> f.equals("ascendant");
                case ASCENDANT -> f.equals("ascendant") && !haloOnly;
                default -> f.equals(pool.id);
            };
            if (ok) out.add(e.getKey());
        }
        if (pool == Pool.HALO) out.sort((a, b) -> Boolean.compare(!HALO_ONLY.contains(a), !HALO_ONLY.contains(b)));
        return out;
    }

    /** First unread log in the pool, else a random one from it, else null if the pool is empty. */
    public static String pick(Pool pool, Set<String> read, RandomSource random) {
        List<String> ids = pool(pool);
        if (ids.isEmpty()) return null;
        for (String id : ids) if (!read.contains(id)) return id;
        return ids.get(random.nextInt(ids.size()));
    }

    /** A player reads a terminal: shows the log, records it (Codex), counts it. Returns the log id or null. */
    public static String read(ServerPlayer player, Pool pool) {
        String id = pick(pool, Above.data(player).logs(), player.getRandom());
        if (id == null) {
            player.sendOverlayMessage(Component.literal("The terminal is blank."));
            return null;
        }
        LogDef def = LOGS.get(id);
        boolean fresh = Above.readLog(player, id);
        player.level().playSound(null, player.blockPosition(), AboveSounds.LOG.get(), SoundSource.BLOCKS, 0.8f, fresh ? 1.0f : 0.8f);
        Signal.send(player, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(def.title()))
                .body(Component.literal(def.text()))
                .footer(Component.literal("Precursor log · " + faction(def.faction()) + (fresh ? " · added to the Codex" : " · read before")))
                .ttl(20 * 14).priority(fresh ? 3 : 1).build());
        return id;
    }

    public static String faction(String f) {
        return switch (f) {
            case "ascendant" -> "Ascendant";
            case "keeper" -> "Keeper";
            case "sleeper" -> "Sleeper";
            default -> "Unsigned";
        };
    }

    // ---- loading and sync -----------------------------------------------------------------------------------------

    public static void init() {
        NeoForge.EVENT_BUS.addListener(PrecursorLogs::addReload);
        NeoForge.EVENT_BUS.addListener(PrecursorLogs::onSync);
    }

    private static void addReload(AddServerReloadListenersEvent event) {
        event.addListener(Awaken.id("above_precursor_logs"), new Loader());
        event.addListener(Awaken.id("above_revelations"), new Revelations.Loader());
    }

    private static void onSync(OnDatapackSyncEvent event) {
        var msg = new AboveNet.Logs(Map.copyOf(LOGS));
        event.getRelevantPlayers().forEach(p -> {
            AwakenNet.send(p, msg);
            Above.sync(p);
        });
    }

    static final class Loader extends SimpleJsonResourceReloadListener<LogDef> {
        Loader() {
            super(LogDef.CODEC, FileToIdConverter.json("awaken/precursor_log"));
        }

        @Override
        protected void apply(Map<Identifier, LogDef> loaded, ResourceManager manager, ProfilerFiller profiler) {
            TreeMap<String, LogDef> m = new TreeMap<>();
            loaded.forEach((k, v) -> m.put(k.toString(), v));
            replace(m);
            Awaken.LOG.info("AWAKEN loaded {} precursor logs", m.size());
        }
    }
}
