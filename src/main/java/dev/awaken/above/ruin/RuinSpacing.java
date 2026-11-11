package dev.awaken.above.ruin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Room between the blue rope sites (SPEC §17.11 "Room between them"): the Tether Spires and the sky platforms stand on a
 * spread-out part of their sets' grids. A spot on the grid keeps its place unless a spot of the same kind whose land
 * would take one stands too near and outranks it (a hash of the seed and the chunk), or, for a spire, a sky platform
 * that keeps its own place stands too near. The grids themselves are unchanged, so no spot ever appears that today's
 * rule didn't have: land made before keeps what stands in it, and where it meets new land nothing can stand closer
 * than it could before. Pure maths plus the land's own height and biome at each spot, the same answer while chunks are
 * made ({@link PrecursorRuinStructure}) and from the server afterwards ({@link RuinLocator}).
 */
public final class RuinSpacing {
    private RuinSpacing() {}

    /** No two Tether Spires closer than this, in blocks (was 144 at the least, 284 at the median, in land they like). */
    public static final int SPIRE_APART = 320;
    /** No two sky platforms closer than this (was 274 at the least, 536 at the median). */
    public static final int SKY_APART = 600;
    /** No Tether Spire this near a sky platform (was nothing: one could stand under a platform's rings). */
    public static final int SPIRE_FROM_SKY = 250;

    private static volatile boolean on = true;

    /** Whether the rule is in force (always, but for a dev's seam check: /awaken above ruin spacing old|new; not saved). */
    public static boolean on() {
        return on;
    }

    public static void set(boolean value) {
        on = value;
        RuinLocator.forgetOldLand();
    }

    /** The kinds the rule spaces out. */
    public static boolean spaced(RuinKind kind) {
        return kind == RuinKind.SPIRE || kind == RuinKind.SKY;
    }

    static int apart(RuinKind kind) {
        return kind == RuinKind.SPIRE ? SPIRE_APART : SKY_APART;
    }

    /** Whether a ruin of this kind would stand at this start chunk by today's rule (its land and biome); null when not known yet. */
    @FunctionalInterface
    public interface Ground {
        @Nullable Boolean valid(RuinKind kind, ChunkPos chunk);
    }

    /** The two sets' grids, as the registry has them. */
    public record Grids(long seed, RandomSpreadStructurePlacement spire, RandomSpreadStructurePlacement sky) {
        public static @Nullable Grids of(RegistryAccess access, long seed) {
            Registry<net.minecraft.world.level.levelgen.structure.StructureSet> sets = access.lookupOrThrow(Registries.STRUCTURE_SET);
            var spire = sets.getValue(RuinKind.SPIRE.set);
            var sky = sets.getValue(RuinKind.SKY.set);
            if (spire == null || sky == null || !(spire.placement() instanceof RandomSpreadStructurePlacement a)
                    || !(sky.placement() instanceof RandomSpreadStructurePlacement b)) return null;
            return new Grids(seed, a, b);
        }

        RandomSpreadStructurePlacement of(RuinKind kind) {
            return kind == RuinKind.SPIRE ? spire : sky;
        }

        /** The grid's spot in the square of this cell. */
        ChunkPos spot(RuinKind kind, int gx, int gz) {
            RandomSpreadStructurePlacement p = of(kind);
            return p.getPotentialStructureChunk(seed, gx * p.spacing(), gz * p.spacing());
        }
    }

    /**
     * Whether a spot whose own land would take the ruin keeps its place: TRUE, FALSE, or null when the ground could not
     * say for a spot it had to ask about (a budget ran out; ask again later). Kinds the rule doesn't space always keep it.
     */
    public static @Nullable Boolean keeps(RuinKind kind, ChunkPos at, Grids grids, Ground ground) {
        return on ? rule(kind, at, grids, ground) : Boolean.TRUE;
    }

    /** {@link #keeps}, whether or not the rule is in force (the census counts both ways). */
    static @Nullable Boolean rule(RuinKind kind, ChunkPos at, Grids grids, Ground ground) {
        if (!spaced(kind)) return Boolean.TRUE;
        Boolean own = clearOfOwn(kind, at, grids, ground);
        if (!Boolean.TRUE.equals(own) || kind != RuinKind.SPIRE) return own;
        return clearOfSky(at, grids, ground);
    }

    /** No spot of its own kind that the land would take, too near and with the higher rank. */
    private static @Nullable Boolean clearOfOwn(RuinKind kind, ChunkPos at, Grids grids, Ground ground) {
        RandomSpreadStructurePlacement p = grids.of(kind);
        int d = apart(kind), reach = reach(p, d);
        int gx = Math.floorDiv(at.x(), p.spacing()), gz = Math.floorDiv(at.z(), p.spacing());
        long mine = rank(grids.seed(), kind, at);
        boolean unknown = false;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (dx == 0 && dz == 0) continue;
                ChunkPos n = grids.spot(kind, gx + dx, gz + dz);
                if (!within(at, n, d) || !outranks(rank(grids.seed(), kind, n), n, mine, at)) continue;
                Boolean v = ground.valid(kind, n);
                if (v == null) unknown = true;
                else if (v) return Boolean.FALSE;
            }
        }
        return unknown ? null : Boolean.TRUE;
    }

    /** No sky platform that keeps its place within {@link #SPIRE_FROM_SKY} of a spire's spot. */
    private static @Nullable Boolean clearOfSky(ChunkPos at, Grids grids, Ground ground) {
        RandomSpreadStructurePlacement p = grids.sky();
        int r = (SPIRE_FROM_SKY + 15) / 16 + 1;
        boolean unknown = false;
        for (int gx = Math.floorDiv(at.x() - r, p.spacing()); gx <= Math.floorDiv(at.x() + r, p.spacing()); gx++) {
            for (int gz = Math.floorDiv(at.z() - r, p.spacing()); gz <= Math.floorDiv(at.z() + r, p.spacing()); gz++) {
                ChunkPos n = grids.spot(RuinKind.SKY, gx, gz);
                if (!within(at, n, SPIRE_FROM_SKY)) continue;
                Boolean v = ground.valid(RuinKind.SKY, n);
                if (v == null) {
                    unknown = true;
                    continue;
                }
                if (!v) continue;
                Boolean k = clearOfOwn(RuinKind.SKY, n, grids, ground);
                if (k == null) unknown = true;
                else if (k) return Boolean.FALSE;
            }
        }
        return unknown ? null : Boolean.TRUE;
    }

    /** How many cells out a spot of the grid can come within {@code d} blocks of one in this cell. */
    static int reach(RandomSpreadStructurePlacement p, int d) {
        int spread = p.spacing() - p.separation() - 1, reach = 0;
        while (((reach + 1) * p.spacing() - spread) * 16 < d) reach++;
        return reach;
    }

    /** Whether two start chunks' middles are nearer than {@code d} blocks. */
    static boolean within(ChunkPos a, ChunkPos b, int d) {
        long dx = (long) (a.x() - b.x()) * 16, dz = (long) (a.z() - b.z()) * 16;
        return dx * dx + dz * dz < (long) d * d;
    }

    /** Blocks between two start chunks' middles. */
    public static double distance(ChunkPos a, ChunkPos b) {
        double dx = (a.x() - b.x()) * 16.0, dz = (a.z() - b.z()) * 16.0;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** A spot's rank: a hash of the seed, the kind and the chunk (the chunk itself breaks a tie). */
    static long rank(long seed, RuinKind kind, ChunkPos c) {
        long h = seed ^ (kind == RuinKind.SPIRE ? 0x7E51C0A1D3B5F00DL : 0x5C1F7A2B9E4D6031L);
        h = mix(h ^ c.x() * 0x9E3779B97F4A7C15L);
        return mix(h ^ c.z() * 0xC2B2AE3D27D4EB4FL);
    }

    private static boolean outranks(long rank, ChunkPos c, long other, ChunkPos o) {
        return rank != other ? rank > other : c.pack() > o.pack();
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 33)) * 0xFF51AFD7ED558CCDL;
        z = (z ^ (z >>> 33)) * 0xC4CEB9FE1A85EC53L;
        return z ^ (z >>> 33);
    }

    // ---- the land at a spot ------------------------------------------------------------------------------------------

    /**
     * Where the structure asks its biome for a start in this chunk (the generation stub's position in
     * {@link PrecursorRuinStructure}): the footprint's middle, on the land's surface (an ocean lab's: the sea floor).
     */
    static BlockPos stub(RuinKind kind, ChunkPos chunk, ChunkGenerator gen, RandomState rs, LevelHeightAccessor height) {
        RuinBuilder.Size size = RuinBuilder.size(kind);
        int minX = chunk.getMinBlockX() + Math.max(0, (16 - size.x()) / 2);
        int minZ = chunk.getMinBlockZ() + Math.max(0, (16 - size.z()) / 2);
        int cx = minX + size.x() / 2, cz = minZ + size.z() / 2;
        Heightmap.Types type = kind == RuinKind.OCEAN ? Heightmap.Types.OCEAN_FLOOR_WG : Heightmap.Types.WORLD_SURFACE_WG;
        return new BlockPos(cx, gen.getFirstOccupiedHeight(cx, cz, type, height, rs), cz);
    }

    /** Today's rule for one spot: outside the city's reserve, on a biome the structure takes (as generation asks it). */
    public static boolean validAt(RuinKind kind, ChunkPos chunk, Structure structure, ChunkGenerator gen, RandomState rs,
                                  LevelHeightAccessor height, BiomeResolver biomes) {
        if (dev.awaken.terrain.api.CityBasin.reserved(chunk.getMinBlockX() + 8, chunk.getMinBlockZ() + 8)) return false;
        BlockPos at = stub(kind, chunk, gen, rs, height);
        return structure.biomes().contains(biomes.getNoiseBiome(QuartPos.fromBlock(at.getX()), QuartPos.fromBlock(at.getY()), QuartPos.fromBlock(at.getZ())));
    }

    /** Whether the spot a structure is being made at keeps its place (while chunks are made: its own land is asked too). */
    static boolean keepsWhileMade(RuinKind kind, Structure.GenerationContext ctx) {
        if (!on || !spaced(kind)) return true;
        Grids grids = Grids.of(ctx.registryAccess(), ctx.seed());
        if (grids == null) return true;
        Registry<Structure> structures = ctx.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Map<Long, Boolean> seen = new HashMap<>();
        Ground ground = (k, c) -> seen.computeIfAbsent(c.pack() * 31 + k.ordinal(), key -> {
            Structure s = structures.getValue(k.structure);
            return s != null && validAt(k, c, s, ctx.chunkGenerator(), ctx.randomState(), ctx.heightAccessor(), ctx.biomeResolver());
        });
        // vanilla asks the stub's biome after this; asking first spares the neighbours' samples for a spot it would refuse
        if (!Boolean.TRUE.equals(ground.valid(kind, ctx.chunkPos()))) return false;
        return Boolean.TRUE.equals(keeps(kind, ctx.chunkPos(), grids, ground));
    }
}
