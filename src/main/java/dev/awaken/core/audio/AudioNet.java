package dev.awaken.core.audio;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.List;

/** The audio module's payloads (server to client; handled in {@code core.audio.client.AudioClient}). */
public final class AudioNet {
    private AudioNet() {}

    /** At most this many ambience emitters go to one player. */
    public static final int MAX_EMITTERS = 8;

    /** Play a music cue (its {@link Cue} ordinal); the client decides whether the music slot is free. */
    public record PlayCue(int cue) implements CustomPacketPayload {
        public static final Type<PlayCue> TYPE = new Type<>(Awaken.id("audio_cue"));
        public static final StreamCodec<ByteBuf, PlayCue> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(PlayCue::new, PlayCue::cue);

        @Override
        public Type<PlayCue> type() {
            return TYPE;
        }
    }

    /** Play one of the System's UI sounds (its {@link UiSound} ordinal) for this player only. */
    public record PlayUi(int sound) implements CustomPacketPayload {
        public static final Type<PlayUi> TYPE = new Type<>(Awaken.id("audio_ui"));
        public static final StreamCodec<ByteBuf, PlayUi> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(PlayUi::new, PlayUi::sound);

        @Override
        public Type<PlayUi> type() {
            return TYPE;
        }
    }

    /** Emitter kinds: the Well's shaft, a Precursor ruin (hums at night), a fallen star still cooling. */
    public static final int WELL = 0, RUIN = 1, STAR = 2;

    /** One place that hums: kind, block position. */
    public record Emitter(int kind, int x, int y, int z) {
        public static final StreamCodec<ByteBuf, Emitter> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Emitter::kind,
                ByteBufCodecs.INT, Emitter::x,
                ByteBufCodecs.INT, Emitter::y,
                ByteBufCodecs.INT, Emitter::z,
                Emitter::new);
    }

    /** The full set of ambience emitters near the player (replaces the last; empty clears). Sent only on change. */
    public record Emitters(List<Emitter> emitters) implements CustomPacketPayload {
        public static final Type<Emitters> TYPE = new Type<>(Awaken.id("audio_emitters"));
        public static final StreamCodec<ByteBuf, Emitters> STREAM_CODEC = Emitter.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_EMITTERS))
                .map(Emitters::new, Emitters::emitters);

        @Override
        public Type<Emitters> type() {
            return TYPE;
        }
    }

    /**
     * The layered music for one slot ({@link MusicLayers.Slot} ordinal): the context id ("" for none), the intensity
     * level ({@link Intensity}) and flags. Sent only when something changed (a few times a minute in a fight).
     */
    public record LayerState(int slot, String context, int level, int flags) implements CustomPacketPayload {
        /** A run or a floor began: the bed comes back even from its rest. */
        public static final int ARRIVAL = 1;
        /** A Lord or boss near the player fell: its layer and the fight's let go quickly. */
        public static final int VICTORY = 2;
        /** The player died: everything fades fast. */
        public static final int DEATH = 4;
        public static final int MAX_CONTEXT = 256;

        public static final Type<LayerState> TYPE = new Type<>(Awaken.id("audio_layers"));
        public static final StreamCodec<ByteBuf, LayerState> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, LayerState::slot,
                ByteBufCodecs.stringUtf8(MAX_CONTEXT), LayerState::context,
                ByteBufCodecs.VAR_INT, LayerState::level,
                ByteBufCodecs.VAR_INT, LayerState::flags,
                LayerState::new);

        @Override
        public Type<LayerState> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent e) {
        var r = e.registrar("2"); // 2: the layered music (audio_layers)
        r.playToClient(PlayCue.TYPE, PlayCue.STREAM_CODEC);
        r.playToClient(PlayUi.TYPE, PlayUi.STREAM_CODEC);
        r.playToClient(Emitters.TYPE, Emitters.STREAM_CODEC);
        r.playToClient(LayerState.TYPE, LayerState.STREAM_CODEC);
    }
}
