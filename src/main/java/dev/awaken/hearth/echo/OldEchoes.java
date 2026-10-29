package dev.awaken.hearth.echo;

import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobRole;
import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.HearthService;
import dev.awaken.hearth.player.HearthPlayer;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Echoes are gone (SPEC §43.4: nothing a player owns fights for them). What an older world still holds is let go: an
 * Echo's body loading from disk never joins the world, and a player whose save listed Echoes is told once, at login.
 */
public final class OldEchoes {
    private OldEchoes() {}

    static final String LINE = "Your Echoes have gone back to the Deep.";

    public static void init() {
        NeoForge.EVENT_BUS.addListener(OldEchoes::onJoin);
        NeoForge.EVENT_BUS.addListener(OldEchoes::onLogin);
    }

    /** An old Echo's body: the Echo link it was bound with, or the Echo mob role. */
    public static boolean isOldEcho(Entity e) {
        if (e.getExistingData(HearthContent.ECHO_LINK.get()).map(EchoLink::isEcho).orElse(false)) return true;
        return e.getExistingData(CoreApi.MOB_ROLE.get()).map(r -> r.role() == MobRole.Role.ECHO).orElse(false);
    }

    static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel() instanceof ServerLevel && e.loadedFromDisk() && isOldEcho(e.getEntity())) e.setCanceled(true);
    }

    static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) release(sp);
    }

    /** Tells a player whose save listed Echoes that they've gone (once). True when there were any. */
    public static boolean release(ServerPlayer sp) {
        HearthPlayer hp = HearthService.player(sp);
        if (hp.echoesToRelease <= 0) return false;
        hp.echoesToRelease = 0;
        Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(LINE)).ttl(20 * 8).build());
        return true;
    }
}
