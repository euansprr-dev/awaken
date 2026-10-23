package dev.awaken.hearth.echo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/** Attached to a live Echo entity: whose it is, which roster entry it embodies, and whether it is on duty at home. */
public record EchoLink(UUID owner, UUID echoId, boolean duty) {
    private static final UUID ZERO = new UUID(0, 0);
    public static final EchoLink NONE = new EchoLink(ZERO, ZERO, false);

    public static final MapCodec<EchoLink> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            UUIDUtil.CODEC.fieldOf("owner").forGetter(EchoLink::owner),
            UUIDUtil.CODEC.fieldOf("echo").forGetter(EchoLink::echoId),
            Codec.BOOL.optionalFieldOf("duty", false).forGetter(EchoLink::duty)
    ).apply(i, EchoLink::new));

    public static final StreamCodec<ByteBuf, EchoLink> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, EchoLink::owner, UUIDUtil.STREAM_CODEC, EchoLink::echoId, ByteBufCodecs.BOOL, EchoLink::duty, EchoLink::new);

    public boolean isEcho() {
        return !owner.equals(ZERO);
    }
}
