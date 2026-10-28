package dev.awaken.village.road;

import dev.awaken.Awaken;
import dev.awaken.city.SettlementPaint;
import dev.awaken.city.VillageStyles;
import dev.awaken.ground.SoftSlabs;
import dev.awaken.village.world.VillageMarker;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** Soft Land banks: the planner's persisted footprints decide ownership, never already painted blocks. */
final class RoadBanks {
    private RoadBanks() {}

    static final float WIDTH = 7;
    static final int UNKNOWN = Integer.MIN_VALUE;

    static boolean enabled(FeatureFlagSet features) {
        var flag = FeatureFlags.REGISTRY.fromNames(Set.of(Awaken.id("awakened_ground")), id -> {});
        return !flag.isEmpty() && flag.isSubsetOf(features);
    }

    /**
     * References are complete before TERRAIN: every structure that can paint this chunk is present, even if its start
     * is elsewhere or its paint has not run. Other structures reserve their entire boxes; no settlement replanning.
     */
    static SettlementPaint.Land land(RoadPiece owner, ChunkAccess chunk, StructureManager manager) {
        List<StructurePiece> pieces = new ArrayList<>();
        for (var start : manager.startsForStructure(chunk.getPos().x(), chunk.getPos().z(), s -> true)) pieces.addAll(start.getPieces());
        return land(owner, chunk, pieces);
    }

    static SettlementPaint.Land land(RoadPiece owner, ChunkAccess chunk, List<StructurePiece> pieces) {
        RoadPaint p = owner.paint();
        int x0 = chunk.getPos().getMinBlockX() - p.ox, z0 = chunk.getPos().getMinBlockZ() - p.oz;
        int[] top = new int[256];
        boolean[] dry = new boolean[256], free = new boolean[256];
        Arrays.fill(top, UNKNOWN);
        // Since TERRAIN, ProtoChunk writes and CityWriter refresh FINAL_HEIGHTMAPS only, never these two WG maps.
        // A missing WG map must not be lazily primed from another painter's work (old/full chunks, dev repaints).
        boolean natural = chunk.hasPrimedHeightmap(Heightmap.Types.OCEAN_FLOOR_WG)
                && chunk.hasPrimedHeightmap(Heightmap.Types.WORLD_SURFACE_WG);
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int k = z * 16 + x, px = x0 + x, pz = z0 + z;
            free[k] = free(owner, pieces, p.ox + px, p.oz + pz);
            if (natural) {
                int y = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
                top[k] = y - p.baseY;
                dry[k] = y == chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
            }
        }
        return new SettlementPaint.Land() {
            private int cell(int x, int z) {
                int dx = x - x0, dz = z - z0;
                return dx < 0 || dz < 0 || dx >= 16 || dz >= 16 ? -1 : dz * 16 + dx;
            }
            @Override public int natural(int x, int z) { int k = cell(x, z); return k < 0 ? UNKNOWN : top[k]; }
            @Override public boolean dry(int x, int z) { int k = cell(x, z); return k >= 0 && dry[k]; }
            @Override public boolean free(int x, int z) { int k = cell(x, z); return k >= 0 && free[k]; }
        };
    }

    /** No bank over any road/verge or other structure; competing banks have one owner, independent of iteration order. */
    static boolean free(RoadPiece owner, List<StructurePiece> pieces, int wx, int wz) {
        RoadPaint p = owner.paint();
        int k = p.cell(wx - p.ox, wz - p.oz);
        if (k < 0 || !p.mine[k] || p.bank[k] == 0) return false;
        float edge = p.dist[k] - p.hw;
        for (StructurePiece piece : pieces) {
            if (piece instanceof VillageMarker) continue; // terrain markers paint nothing
            if (piece instanceof RoadPiece other) {
                RoadPaint q = other.paint();
                for (Road.Marker m : q.markers)
                    if (Math.abs(wx - m.x()) <= 2 && Math.abs(wz - m.z()) <= 2) return false;
                if (owner.key().equals(other.key()) && owner.legIndex() == other.legIndex()) continue;
                int j = q.cell(wx - q.ox, wz - q.oz);
                if (j < 0) continue;
                float otherEdge = q.dist[j] - q.hw;
                if (otherEdge <= RoadPaint.VERGE) return false;
                if (!q.mine[j] || q.bank[j] == 0) continue;
                int order = owner.key().compareTo(other.key());
                if (order == 0) order = Integer.compare(owner.legIndex(), other.legIndex());
                if (otherEdge < edge || otherEdge == edge && order > 0) return false;
            } else if (piece.getBoundingBox().intersects(wx, wz, wx, wz)) return false;
        }
        return true;
    }

    /** Steep cliffs retain the existing rocky embankment; shallow cuts and fills reach natural land within seven blocks. */
    static boolean grades(RoadPaint p, SettlementPaint.Land land, int x, int z) {
        int k = p.cell(x, z);
        if (k < 0 || !p.mine[k] || p.bank[k] == 0 || !land.free(x, z) || !land.dry(x, z)) return false;
        float e = p.dist[k] - p.hw - RoadPaint.VERGE;
        int y = land.natural(x, z);
        return e > 0 && e <= WIDTH && y != UNKNOWN && Math.abs(y - p.lifts[k]) <= WIDTH
                && y >= SettlementPaint.Canvas.YMIN + 3 && y <= SettlementPaint.Canvas.YMAX - SettlementPaint.CLEAR
                && profile(p.lifts[k], y, e) != 2 * (y + 1);
    }

    /** The desired surface (twice its height), including the full block under the player's feet. */
    static int profile(int road, int natural, float out) {
        float t = Math.clamp(out / WIDTH, 0, 1);
        return Math.round(2 * (road + 1 + (natural - road) * t));
    }

    static boolean grade(RoadPaint p, SettlementPaint.Canvas c, int x, int z, int k) {
        SettlementPaint.Land land = c.land();
        if (land == null || !grades(p, land, x, z)) return false;
        int natural = land.natural(x, z);
        int top2 = profile(p.lifts[k], natural, p.dist[k] - p.hw - RoadPaint.VERGE);
        int top = Math.floorDiv(top2 - 1, 2);
        int s = p.seg[k], family = p.fam[p.along[k] < 0.5f ? s : s + 1];
        boolean half = (top2 & 1) != 0;
        RoadPaint.bed(c, x, top, z, family);
        c.set(x, top, z, surface(family, half));
        c.column(x, top + 1, Math.max(natural + 2, top + 4), z, Blocks.AIR.defaultBlockState());
        c.plate(x, z);
        return true;
    }

    private static BlockState surface(int family, boolean half) {
        return switch (family) {
            case VillageStyles.DESERT -> half ? SoftSlabs.SAND_SLAB.get().defaultBlockState() : Blocks.SAND.defaultBlockState();
            case VillageStyles.ELDER -> half ? SoftSlabs.PODZOL_SLAB.get().defaultBlockState() : Blocks.PODZOL.defaultBlockState();
            case VillageStyles.FROST, VillageStyles.HIGHLAND -> half ? SoftSlabs.GRAVEL_SLAB.get().defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
            case VillageStyles.MIRE, VillageStyles.JUNGLE -> half ? SoftSlabs.MUD_SLAB.get().defaultBlockState() : Blocks.MUD.defaultBlockState();
            default -> half ? SoftSlabs.TURF_SLAB.get().defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
        };
    }
}
