package dev.awaken.hearth.player;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** The rank aura everyone around a player sees: rank ordinal (0 = E, no aura) and its colour (rank or Path colour). */
public record Aura(int rank, int colour) {
    public static final Aura NONE = new Aura(0, 0);
    public static final StreamCodec<ByteBuf, Aura> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Aura::rank, ByteBufCodecs.INT, Aura::colour, Aura::new);
}
