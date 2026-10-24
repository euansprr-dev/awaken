package dev.awaken.wilds.ward;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenNet;
import dev.awaken.wilds.WildsContent;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * What a ward looks and sounds like. The server sends one small event per block ({@link Event}); each client builds the
 * particles itself from its own sprites ({@code wilds.client.WardFxClient}), tinted by the block's colour. The sound is
 * played in the world, so everyone near hears it. Never vanilla particles or sounds.
 */
public final class WardFx {
    private WardFx() {}

    /** MARK: the ward took note of a placed block. CRUMBLE: it came apart. HOLD: a site's wall refused a pick. */
    public enum Kind { MARK, CRUMBLE, HOLD }

    /** Players this close see the effect. */
    static final double RANGE = 48;

    /** One effect at a block. {@code state} is the block's state id (the client tints the grains by it). */
    public record Event(int kind, BlockPos pos, int state) implements CustomPacketPayload {
        public static final Type<Event> TYPE = new Type<>(Awaken.id("ward_fx"));
        public static final StreamCodec<ByteBuf, Event> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Event::kind,
                BlockPos.STREAM_CODEC, Event::pos,
                ByteBufCodecs.VAR_INT, Event::state,
                Event::new);

        public Kind what() {
            Kind[] v = Kind.values();
            return v[Math.floorMod(kind, v.length)];
        }

        @Override
        public Type<Event> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent e) {
        e.registrar("1").playToClient(Event.TYPE, Event.STREAM_CODEC);
    }

    static void play(ServerLevel level, Kind kind, BlockPos pos, BlockState state) {
        Event ev = new Event(kind.ordinal(), pos, Block.getId(state));
        double r2 = RANGE * RANGE;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= r2) AwakenNet.send(p, ev);
        }
        SoundEvent sound = switch (kind) {
            case MARK -> WildsContent.WARD_MARK.get();
            case CRUMBLE -> WildsContent.WARD_CRUMBLE.get();
            case HOLD -> WildsContent.WARD_HOLD.get();
        };
        float pitch = 0.92f + level.getRandom().nextFloat() * 0.16f;
        level.playSound(null, pos, sound, SoundSource.BLOCKS, kind == Kind.MARK ? 0.45f : 0.8f, pitch);
    }
}
