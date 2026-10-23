package dev.awaken.world.landmark;

import dev.awaken.above.ruin.RuinLocator;
import dev.awaken.core.AwakenNet;
import dev.awaken.progression.Progression;
import dev.awaken.world.anomaly.Spots;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tells each awakened player's client where the landmarks around them stand, so it can draw their faint markers (the
 * Well's pillars glow at night, ruins send up a light shaft at dusk). Every five seconds per player (spread by UUID),
 * only for ruins in loaded chunks (exact bounds from the structure start; anything further is beyond the view anyway),
 * and a packet only when the set changed.
 */
final class LandmarkMarkers {
    private LandmarkMarkers() {}

    static final int PERIOD = 100;
    static final int RANGE = 288;

    private static final Map<UUID, List<LandmarkNet.Marker>> SENT = new HashMap<>();

    static void tick(MinecraftServer s) {
        int t = s.getTickCount();
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (Math.floorMod(t + p.getUUID().hashCode() * 7, PERIOD) == 0) refresh(p);
        }
    }

    static void refresh(ServerPlayer p) {
        List<LandmarkNet.Marker> now = compute(p);
        if (now.equals(SENT.get(p.getUUID()))) return;
        SENT.put(p.getUUID(), now);
        AwakenNet.send(p, new LandmarkNet.Markers(now));
    }

    static void forget(UUID id) {
        SENT.remove(id);
    }

    static void clear() {
        SENT.clear();
    }

    /** The markers this player should see now (nothing before awakening or outside the overworld). */
    static List<LandmarkNet.Marker> compute(ServerPlayer p) {
        ServerLevel level = p.level();
        if (level.dimension() != Level.OVERWORLD || !Progression.profile(p).awakened()) return List.of();
        BlockPos me = p.blockPosition();
        List<LandmarkNet.Marker> out = new ArrayList<>();
        Landmarks.wellPos(level.getServer()).ifPresent(w -> {
            if (Spots.horizontal(me, w) <= RANGE) out.add(new LandmarkNet.Marker(LandmarkKind.WELL.ordinal(), w.getX(), w.getY(), w.getZ(), 7));
        });
        if (Landmarks.aboveOn() && !Landmarks.testNoRuins) {
            var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            for (RuinLocator.Found f : RuinLocator.near(level, me, RANGE, Landmarks.RUINS, null, false, 8, 4)) {
                if (out.size() >= LandmarkNet.MAX) break;
                ChunkPos c = ChunkPos.containing(f.pos());
                if (!level.hasChunk(c.x(), c.z())) continue;
                Structure structure = structures.get(f.kind().structure).map(Holder::value).orElse(null);
                if (structure == null) continue;
                StructureStart start = level.getChunk(c.x(), c.z()).getStartForStructure(structure);
                if (start == null || !start.isValid()) continue;
                BoundingBox b = start.getBoundingBox();
                BlockPos centre = b.getCenter();
                out.add(new LandmarkNet.Marker(LandmarkKind.of(f.kind()).ordinal(), centre.getX(), b.minY(), centre.getZ(), b.getYSpan()));
            }
        }
        return out;
    }
}
