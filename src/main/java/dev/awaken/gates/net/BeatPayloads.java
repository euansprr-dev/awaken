package dev.awaken.gates.net;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** What a gate's lines tell the client (SPEC §72.6): a family shown for the first time, and a line crossed clean. */
public final class BeatPayloads {
    private BeatPayloads() {}

    /** The gold figure walks this path once (feet points in the world, in order). The words go on the Now line from the server. */
    public record Lesson(List<Vec3> path) implements CustomPacketPayload {
        public static final Type<Lesson> TYPE = new Type<>(Awaken.id("gate_beat_lesson"));
        public static final StreamCodec<ByteBuf, Lesson> STREAM_CODEC = StreamCodec.composite(
                Vec3.STREAM_CODEC.apply(ByteBufCodecs.list(32)), Lesson::path, Lesson::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** A whole line crossed without a fall or a stop: a quiet sound and a little Flow, more for a longer line. */
    public record Clean(int beats) implements CustomPacketPayload {
        public static final Type<Clean> TYPE = new Type<>(Awaken.id("gate_beat_clean"));
        public static final StreamCodec<ByteBuf, Clean> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Clean::beats, Clean::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
