package dev.awaken.farsight.client;

import dev.awaken.farsight.FarColumns;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Far Sight's answer to {@code core.api.FarGround} (SPEC §39.8): the ground of the far land it draws, for the binoculars'
 * readout and marking past loaded chunks. One {@link FarColumns} per calling thread (they aren't shared), rebuilt when
 * the level changes; single player only, NaN otherwise. Ready while Far Sight drew in the last quarter second.
 */
final class FarGroundSampler {
    private FarGroundSampler() {}

    private record Bound(ServerLevel level, FarColumns columns) {}

    private static final ThreadLocal<Bound> BOUND = new ThreadLocal<>();

    static void install() {
        dev.awaken.core.api.FarGround.install(FarGroundSampler::ground, FarSightClient::drawing);
    }

    private static double ground(int x, int z) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        ClientLevel client = mc.level;
        if (server == null || client == null) return Double.NaN;
        ServerLevel level = server.getLevel(client.dimension());
        if (level == null) return Double.NaN;
        Bound b = BOUND.get();
        if (b == null || b.level() != level) BOUND.set(b = new Bound(level, FarColumns.of(level)));
        return b.columns().ground(x, z);
    }
}
