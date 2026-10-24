package dev.awaken.core.audio;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Public API of the layered music (SPEC "Plan v2 · audio" and §12): the server decides, for each player, which
 * <em>context</em> they are in and how intense it is; the client plays that context's stems
 * ({@code assets/<ns>/music_layers/<path>.json}, see {@link LayerManifest}) and crossfades them.
 *
 * <p>Two slots play at once: {@link Slot#MUSIC} (the Music slider; takes the music channel from vanilla while it
 * sounds) and {@link Slot#AMBIENCE} (the Ambient/Environment slider; never touches the music channel, for beds such
 * as a city district's crowd). A {@link Provider} names the context for its slot; the highest priority that answers
 * wins. Intensity comes from the player's surroundings ({@link Intensity}); a provider may also report the gate's
 * pressure or pin the level ({@link Intensity.Signals#forced}, e.g. a district's crowd density).
 *
 * <p>Built in: gate themes ({@code awaken:gate/<theme>}, {@link GateMusic}, priority 100). Register from mod init.
 */
public final class MusicLayers {
    private MusicLayers() {}

    public enum Slot {
        MUSIC, AMBIENCE;

        public static Slot byOrdinal(int i) {
            Slot[] v = values();
            return i >= 0 && i < v.length ? v[i] : null;
        }
    }

    /** Names the context a player is in for one slot, or null when it has nothing to say. Server thread, every 10 ticks. */
    @FunctionalInterface
    public interface Provider {
        @Nullable Identifier context(ServerPlayer player, Intensity.Signals signals);
    }

    private record Entry(Slot slot, int priority, String name, Provider provider) {}

    private static final List<Entry> PROVIDERS = new ArrayList<>();
    private static Entry[] music = new Entry[0], ambience = new Entry[0];

    /** Adds a provider (mod init). {@code name} shows in {@code /awaken audio layers}. */
    public static synchronized void register(Slot slot, int priority, String name, Provider provider) {
        PROVIDERS.add(new Entry(slot, priority, name, provider));
        PROVIDERS.sort(Comparator.comparingInt((Entry e) -> -e.priority));
        music = PROVIDERS.stream().filter(e -> e.slot == Slot.MUSIC).toArray(Entry[]::new);
        ambience = PROVIDERS.stream().filter(e -> e.slot == Slot.AMBIENCE).toArray(Entry[]::new);
    }

    /** The context for this player and slot right now, or null (no allocation of its own). */
    static @Nullable Identifier resolve(Slot slot, ServerPlayer p, Intensity.Signals s) {
        for (Entry e : slot == Slot.MUSIC ? music : ambience) {
            Identifier id = e.provider.context(p, s);
            if (id != null) return id;
        }
        return null;
    }

    /** Which provider answers for this player (diagnostics). */
    static String who(Slot slot, ServerPlayer p, Intensity.Signals s) {
        for (Entry e : slot == Slot.MUSIC ? music : ambience) {
            if (e.provider.context(p, s) != null) return e.name;
        }
        return "none";
    }

    /** The context a player's music slot is in, as last decided (null when none). */
    public static @Nullable Identifier current(ServerPlayer p, Slot slot) {
        return LayerWatch.current(p, slot);
    }

    /** The intensity level of a player's slot as last decided ({@link Intensity#CALM}..{@link Intensity#LORD}). */
    public static int level(ServerPlayer p, Slot slot) {
        return LayerWatch.level(p, slot);
    }
}
