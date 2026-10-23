package dev.awaken.descent.net;

import dev.awaken.Awaken;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** Network messages of the descent module. */
public final class DescentPayloads {
    private DescentPayloads() {}

    public record GraveMark(String dimension, BlockPos pos, int depth) {
        public static final StreamCodec<RegistryFriendlyByteBuf, GraveMark> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, GraveMark::dimension, BlockPos.STREAM_CODEC, GraveMark::pos,
                ByteBufCodecs.VAR_INT, GraveMark::depth, GraveMark::new);
    }

    /** The player's Descent record, for the HUD, the Codex and the travel list. */
    public record Sync(List<Integer> visited, List<Integer> lit, List<GraveMark> graves, int deepest, int openTo,
                       boolean seedCleared, int checkpoint) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(Awaken.id("descent_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Sync::visited,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Sync::lit,
                GraveMark.STREAM_CODEC.apply(ByteBufCodecs.list()), Sync::graves,
                ByteBufCodecs.VAR_INT, Sync::deepest,
                ByteBufCodecs.VAR_INT, Sync::openTo,
                ByteBufCodecs.BOOL, Sync::seedCleared,
                ByteBufCodecs.VAR_INT, Sync::checkpoint,
                Sync::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Live HUD state while inside the Descent (depth 0 = hide). noise is -1 outside the Silent Halls rule. */
    public record Hud(int depth, Component status, int noise, int rules) implements CustomPacketPayload {
        public static final Type<Hud> TYPE = new Type<>(Awaken.id("descent_hud"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Hud> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Hud::depth,
                ComponentSerialization.TRUSTED_STREAM_CODEC, Hud::status,
                ByteBufCodecs.VAR_INT, Hud::noise,
                ByteBufCodecs.VAR_INT, Hud::rules,
                Hud::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Destination(int depth, String label, int strainPercent) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Destination> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Destination::depth, ByteBufCodecs.STRING_UTF8, Destination::label,
                ByteBufCodecs.VAR_INT, Destination::strainPercent, Destination::new);
    }

    /** Opens the Waystone travel list. */
    public record OpenTravel(int here, List<Destination> destinations) implements CustomPacketPayload {
        public static final Type<OpenTravel> TYPE = new Type<>(Awaken.id("descent_open_travel"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenTravel> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, OpenTravel::here,
                Destination.STREAM_CODEC.apply(ByteBufCodecs.list()), OpenTravel::destinations,
                OpenTravel::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The player picked a destination in the travel list. */
    public record Travel(int depth) implements CustomPacketPayload {
        public static final Type<Travel> TYPE = new Type<>(Awaken.id("descent_travel"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Travel> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Travel::depth, Travel::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
