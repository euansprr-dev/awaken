package dev.awaken.world.landmark;

import dev.awaken.above.ruin.PrecursorRuinStructure;
import dev.awaken.above.ruin.RuinLocator;
import dev.awaken.core.api.GateHooks;
import dev.awaken.core.api.Spotting;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.Map;

/**
 * Landmarks and gates spotted through the binoculars (SPEC §39.10): the Well, a Precursor ruin of a kind you haven't
 * found, a gate of a theme you haven't stood at. Seeing one pays the same discovery as reaching it and puts it on the
 * Gate Map; it is still "reached" (the Horizon line stops, the explorer's count, a climb site's rope promise) only when
 * you stand there. Ruins are found by the placement maths ({@link RuinLocator}, no chunk generated) and drawn by their
 * real bounds where their start chunk is loaded.
 */
final class LandmarkSpotting {
    private LandmarkSpotting() {}

    /** The Well's ring and the stones round it (blocks from its centre). */
    static final int WELL_HALF = 8, WELL_BELOW = 4, WELL_ABOVE = 6;
    /** A gate's frame and vortex round its block. */
    static final int GATE_HALF = 4, GATE_BELOW = 1, GATE_ABOVE = 10;
    /** Ruins sampled per gather: the nearest few, and no more uncached terrain samples than the markers spend. */
    static final int RUIN_MAX = 8, RUIN_UNCACHED = 4;
    private static final long WELL_KEY = 1L, RUIN_KEYS = 2L << 56, GATE_KEYS = 3L << 56;

    record Ruin(LandmarkKind kind, String id, BlockPos centre) {}

    static void register() {
        Spotting.register(SOURCE);
    }

    static boolean known(ServerPlayer p, String discoveryKey) {
        return Progression.profile(p).counter("awaken:discovered/" + discoveryKey) > 0;
    }

    static final Spotting.Source SOURCE = new Spotting.Source() {
        @Override
        public boolean moves() {
            return false;
        }

        @Override
        public void gather(ServerPlayer p, double reach, Spotting.Sink out) {
            ServerLevel level = p.level();
            if (!Landmarks.enabled() || level.dimension() != Level.OVERWORLD) return;
            BlockPos me = p.blockPosition();
            double r2 = reach * reach;
            var well = Landmarks.wellPos(level.getServer());
            if (well.isPresent() && !known(p, LandmarkKind.WELL.discoveryKey()) && well.get().distSqr(me) <= r2) {
                BlockPos w = well.get();
                out.place(WELL_KEY, w.getX() - WELL_HALF, w.getY() - WELL_BELOW, w.getZ() - WELL_HALF,
                        w.getX() + 1 + WELL_HALF, w.getY() + WELL_ABOVE, w.getZ() + 1 + WELL_HALF, new Ruin(LandmarkKind.WELL, "well", w));
            }
            if (Landmarks.aboveOn()) ruins(p, level, me, (int) reach, out);
            for (GateHooks.GateView g : GateHooks.near(level, me, (int) reach)) {
                if ("RELEASED".equals(g.state()) || known(p, gateKey(g))) continue;
                BlockPos b = g.pos();
                out.place(GATE_KEYS | (b.asLong() & 0x00FF_FFFF_FFFF_FFFFL), b.getX() - GATE_HALF, b.getY() - GATE_BELOW, b.getZ() - GATE_HALF,
                        b.getX() + 1 + GATE_HALF, b.getY() + GATE_ABOVE, b.getZ() + 1 + GATE_HALF, g);
            }
        }

        @Override
        public boolean find(ServerPlayer p, Object ref) {
            if (ref instanceof Ruin r) return LandmarkWatch.spot(p, r.kind(), r.id(), r.centre());
            if (ref instanceof GateHooks.GateView g) {
                // gone or fallen since it was offered: nothing to find
                var now = GateHooks.get(p.level(), g.id());
                return now.isPresent() && !"RELEASED".equals(now.get().state()) && LandmarkWatch.reachGate(p, now.get());
            }
            return false;
        }
    };

    private static void ruins(ServerPlayer p, ServerLevel level, BlockPos me, int reach, Spotting.Sink out) {
        for (RuinLocator.Found f : RuinLocator.near(level, me, reach, Landmarks.RUINS, null, false, RUIN_MAX, RUIN_UNCACHED)) {
            LandmarkKind kind = LandmarkKind.of(f.kind());
            if (known(p, kind.discoveryKey())) continue;
            ChunkPos start = ChunkPos.containing(f.pos());
            LevelChunk chunk = level.getChunkSource().getChunkNow(start.x(), start.z());
            if (chunk == null) continue;
            for (Map.Entry<Structure, StructureStart> e : chunk.getAllStarts().entrySet()) {
                if (!(e.getKey() instanceof PrecursorRuinStructure) || !e.getValue().isValid()) continue;
                BoundingBox b = e.getValue().getBoundingBox();
                out.place(RUIN_KEYS | (start.pack() & 0x00FF_FFFF_FFFF_FFFFL), b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1,
                        new Ruin(kind, Landmarks.ruinId(start), b.getCenter()));
            }
        }
    }

    static String gateKey(GateHooks.GateView g) {
        String theme = g.theme();
        return LandmarkKind.GATE.discoveryKey() + "/" + (theme.indexOf(':') >= 0 ? theme.substring(theme.indexOf(':') + 1) : theme);
    }
}
