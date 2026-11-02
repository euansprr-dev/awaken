package dev.awaken.mobs.net;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenNet;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.List;

/** The mobs module's payloads (server to client; handled in {@code mobs.client}). */
public final class MobsNet {
    private MobsNet() {}

    /**
     * The server's clock while a boss fights near you (SPEC §46.1): sent at the end of each of its ticks, so the
     * client plays boss moves at the server's real pace. In a stall the boss holds instead of running ahead of its blows.
     */
    public record Pace(long gameTime) implements CustomPacketPayload {
        public static final Type<Pace> TYPE = new Type<>(Awaken.id("mobs_pace"));
        public static final StreamCodec<ByteBuf, Pace> STREAM_CODEC = ByteBufCodecs.VAR_LONG.map(Pace::new, Pace::gameTime);

        @Override
        public Type<Pace> type() {
            return TYPE;
        }
    }

    /** Players this far from a fighting boss get its pace. */
    public static final double PACE_RANGE = 96;

    // the bosses that fought this tick (at most a handful)
    private static final List<Entity> FIGHTING = new ArrayList<>();

    public static void init(IEventBus modBus) {
        modBus.addListener(MobsNet::register);
        NeoForge.EVENT_BUS.addListener(MobsNet::onPost);
    }

    private static void register(RegisterPayloadHandlersEvent e) {
        var r = e.registrar("1");
        r.playToClient(Pace.TYPE, Pace.STREAM_CODEC);
    }

    /** A boss fought this tick: players near it get the server's clock at the tick's end. */
    public static void fighting(Entity boss) {
        if (FIGHTING.size() < 16 && !FIGHTING.contains(boss)) FIGHTING.add(boss);
    }

    private static void onPost(ServerTickEvent.Post e) {
        if (FIGHTING.isEmpty()) return;
        MinecraftServer server = e.getServer();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            for (Entity b : FIGHTING) {
                if (b.level() != p.level() || b.distanceToSqr(p) > PACE_RANGE * PACE_RANGE) continue;
                AwakenNet.send(p, new Pace(((ServerLevel) p.level()).getGameTime()));
                break;
            }
        }
        FIGHTING.clear();
    }
}
