package dev.awaken.together;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A second player the server plays from a script (SPEC §70.7), so a tour with one client can photograph two people:
 * the strip, a ping, a second wind, a lift. It is a real survival player on a connection nobody reads (the same one
 * the GameTests' test player uses), moved by {@code /awaken together partner ...}; everything else about it is set
 * through {@code /awaken as <name> ...} like any player. Game masters only. Never saved between sessions.
 */
final class TourPartner {
    private TourPartner() {}

    private record Held(ServerPlayer player, EmbeddedChannel channel) {}

    private static final Map<String, Held> PARTNERS = new HashMap<>();

    static @Nullable ServerPlayer get(String name) {
        Held h = PARTNERS.get(name);
        return h == null ? null : h.player();
    }

    /** A partner called {@code name} standing where {@code beside} stands, or the one there already is. */
    static ServerPlayer spawn(ServerPlayer beside, String name) {
        Held old = PARTNERS.get(name);
        if (old != null && old.player().isAlive() && !old.player().hasDisconnected()) return old.player();
        ServerLevel level = beside.level();
        MinecraftServer server = level.getServer();
        UUID id = UUID.nameUUIDFromBytes(("awaken-tour-partner:" + name).getBytes(StandardCharsets.UTF_8));
        ServerPlayer gone = server.getPlayerList().getPlayer(id);
        if (gone != null) server.getPlayerList().remove(gone);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(id, name), false);
        ServerPlayer p = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, p, cookie);
        p.setGameMode(GameType.SURVIVAL);
        p.connection.markClientLoaded();
        p.teleportTo(level, beside.getX() + 1.5, beside.getY(), beside.getZ(), java.util.Set.of(), beside.getYRot(), 0, true);
        PARTNERS.put(name, new Held(p, channel));
        return p;
    }

    static boolean remove(MinecraftServer server, String name) {
        Held h = PARTNERS.remove(name);
        if (h == null) return false;
        if (!h.player().hasDisconnected()) server.getPlayerList().remove(h.player());
        h.channel().close();
        return true;
    }

    static void removeAll(MinecraftServer server) {
        for (String name : java.util.List.copyOf(PARTNERS.keySet())) remove(server, name);
    }

    /** Turns to the nearest living thing that isn't a player within {@code reach}, and strikes it if it's in arm's length. */
    static Optional<LivingEntity> strike(ServerPlayer p, double reach) {
        Optional<LivingEntity> near = p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(reach),
                e -> e.isAlive() && !(e instanceof net.minecraft.world.entity.player.Player)).stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
        near.ifPresent(e -> {
            p.lookAt(EntityAnchorArgument.Anchor.EYES, e.getEyePosition());
            if (e.distanceToSqr(p) <= 4.5 * 4.5) {
                p.resetAttackStrengthTicker();
                p.attack(e);
            }
        });
        return near;
    }

    static void face(ServerPlayer p, Vec3 at) {
        p.lookAt(EntityAnchorArgument.Anchor.EYES, at);
    }

    /** Once a second: what was sent to a partner is thrown away, or a long tour would keep every packet. */
    static void tick(MinecraftServer server) {
        if (PARTNERS.isEmpty() || server.getTickCount() % 20 != 0) return;
        for (Held h : PARTNERS.values()) {
            Object m;
            while ((m = h.channel().readOutbound()) != null) io.netty.util.ReferenceCountUtil.release(m);
        }
        PARTNERS.values().removeIf(h -> h.player().hasDisconnected());
    }
}
