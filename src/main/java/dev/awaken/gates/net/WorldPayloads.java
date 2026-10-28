package dev.awaken.gates.net;

import dev.awaken.Awaken;
import dev.awaken.gates.world.FloorAir;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Gate Worlds payloads (SPEC §38). Server → client through {@link dev.awaken.core.AwakenNet#send}. */
public final class WorldPayloads {
    private WorldPayloads() {}

    /**
     * You arrived on a floor: which world and mood it is (the title card and the Atlas), whether it's a first-ever find,
     * and its Gate Air data with the world position of its floor-local origin. {@code world} "" = a rooms floor (clears).
     */
    public record Arrival(int gateId, int area, int floor, String world, String name, String mood, String moodName, String rarity,
                          String tier, String line, boolean first, BlockPos origin, FloorAir air) implements CustomPacketPayload {
        public static final Type<Arrival> TYPE = new Type<>(Awaken.id("gate_world_arrival"));
        public static final StreamCodec<ByteBuf, Arrival> STREAM_CODEC = StreamCodec.of((buf, a) -> {
            ByteBufCodecs.VAR_INT.encode(buf, a.gateId);
            ByteBufCodecs.VAR_INT.encode(buf, a.area);
            ByteBufCodecs.VAR_INT.encode(buf, a.floor);
            for (String s : new String[]{a.world, a.name, a.mood, a.moodName, a.rarity, a.tier, a.line}) ByteBufCodecs.STRING_UTF8.encode(buf, s);
            ByteBufCodecs.BOOL.encode(buf, a.first);
            BlockPos.STREAM_CODEC.encode(buf, a.origin);
            FloorAir.STREAM_CODEC.encode(buf, a.air);
        }, buf -> new Arrival(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.BOOL.decode(buf), BlockPos.STREAM_CODEC.decode(buf),
                FloorAir.STREAM_CODEC.decode(buf)));

        public static Arrival none(int gateId, int area, int floor) {
            return new Arrival(gateId, area, floor, "", "", "", "", "", "", "", false, BlockPos.ZERO, FloorAir.NONE);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
