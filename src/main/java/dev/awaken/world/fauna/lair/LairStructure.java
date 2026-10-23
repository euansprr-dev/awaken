package dev.awaken.world.fauna.lair;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * A named beast's lair (docs/modules/fauna.md, "Lairs"). Rare: one structure set per beast with a wide random spread
 * (about one candidate per 1,800 blocks), and only where the ground suits it. The start chunk is the placement's; the
 * lair itself may sit up to six chunks away from it, where the biome is right, the ground dry (or shallow bog for the
 * Mire Mother) and flat enough. The biome check runs first because it is the cheap one.
 */
public class LairStructure extends Structure {
    public static final MapCodec<LairStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            LairStyle.CODEC.fieldOf("lair").forGetter(LairStructure::style)
    ).apply(i, LairStructure::new));

    /** Sites tried per candidate chunk, and how far (in chunks) from it they may be. */
    static final int ATTEMPTS = 12, SEARCH = 6;

    private final LairStyle style;

    public LairStructure(Structure.StructureSettings settings, LairStyle style) {
        super(settings);
        this.style = style;
    }

    public LairStyle style() {
        return style;
    }

    @Override
    protected Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext ctx) {
        Site site = findSite(ctx, style);
        if (site == null) return Optional.empty();
        long seed = site.seed;
        return Optional.of(new Structure.GenerationStub(site.centre, builder -> builder.addPiece(new LairPiece(style, site.centre, seed, site.variant))));
    }

    /** Where a lair goes in this candidate chunk (or null): the floor layer's centre, the look and the build seed. */
    record Site(BlockPos centre, int variant, long seed) {}

    static Site findSite(Structure.GenerationContext ctx, LairStyle style) {
        ChunkPos c = ctx.chunkPos();
        WorldgenRandom rnd = ctx.random();
        long seed = rnd.nextLong();
        int sea = ctx.chunkGenerator().getSeaLevel();
        int r = style.radius;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            int x = c.getMiddleBlockX(), z = c.getMiddleBlockZ();
            if (attempt > 0) {
                x += (rnd.nextInt(SEARCH * 2 + 1) - SEARCH) * 16 + rnd.nextInt(9) - 4;
                z += (rnd.nextInt(SEARCH * 2 + 1) - SEARCH) * 16 + rnd.nextInt(9) - 4;
            }
            if (style.placement == LairStyle.Placement.SEABED) {
                Site s = seabed(ctx, style, x, z, sea, seed);
                if (s != null) return s;
                continue;
            }
            if (style.placement == LairStyle.Placement.CAVE) {
                Site s = cave(ctx, style, x, z, sea, seed, rnd);
                if (s != null) return s;
                continue;
            }
            // the cheap test first: is this the beast's biome at all (sampled just above sea level; on the peaks, where the
            // mountain biomes only start high up, at the surface itself)
            int top = -1;
            if (style.highland) top = height(ctx, x, z, Heightmap.Types.WORLD_SURFACE_WG);
            Holder<Biome> b = ctx.biomeResolver().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(style.highland ? top : sea + 4), QuartPos.fromBlock(z));
            if (!ctx.validBiome().test(b)) continue;
            if (top < 0) top = height(ctx, x, z, Heightmap.Types.WORLD_SURFACE_WG);
            int floor = height(ctx, x, z, Heightmap.Types.OCEAN_FLOOR_WG);
            int water = top - floor;
            if (water > (style.wet ? 2 : 0)) continue;
            int min = top, max = top, sum = top;
            int[][] corners = {{r, r}, {-r, r}, {r, -r}, {-r, -r}};
            boolean wet = false;
            for (int[] k : corners) {
                int h = height(ctx, x + k[0], z + k[1], Heightmap.Types.WORLD_SURFACE_WG);
                if (!style.wet && h != height(ctx, x + k[0], z + k[1], Heightmap.Types.OCEAN_FLOOR_WG)) wet = true;
                min = Math.min(min, h);
                max = Math.max(max, h);
                sum += h;
            }
            if (wet || max - min > style.maxSlope) continue;
            int y = style.wet ? Math.max(floor, sea - 1) : Math.round(sum / 5.0F);
            if (y <= sea - 3 || y >= ctx.heightAccessor().getMaxY() - LairStyle.HEIGHT - 2) continue;
            Holder<Biome> surface = ctx.biomeResolver().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
            if (!ctx.validBiome().test(surface)) continue;
            return new Site(new BlockPos(x, y, z), style.variant(surface), seed);
        }
        return null;
    }

    /** P3: the sea floor of a deep ocean, under at least {@value #SEA_DEPTH} blocks of water, flat enough. */
    static final int SEA_DEPTH = 12;

    private static Site seabed(Structure.GenerationContext ctx, LairStyle style, int x, int z, int sea, long seed) {
        Holder<Biome> b = ctx.biomeResolver().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(sea - 20), QuartPos.fromBlock(z));
        if (!ctx.validBiome().test(b)) return null;
        int top = height(ctx, x, z, Heightmap.Types.WORLD_SURFACE_WG);
        int floor = height(ctx, x, z, Heightmap.Types.OCEAN_FLOOR_WG);
        if (top - floor < SEA_DEPTH) return null;
        int r = style.radius, min = floor, max = floor, sum = floor;
        int[][] corners = {{r, r}, {-r, r}, {r, -r}, {-r, -r}};
        for (int[] k : corners) {
            int h = height(ctx, x + k[0], z + k[1], Heightmap.Types.OCEAN_FLOOR_WG);
            if (top - h < SEA_DEPTH - 3) return null;
            min = Math.min(min, h);
            max = Math.max(max, h);
            sum += h;
        }
        if (max - min > style.maxSlope) return null;
        int y = Math.round(sum / 5.0F);
        if (y <= ctx.heightAccessor().getMinY() + LairStyle.DEPTH + 2 || y + LairStyle.HEIGHT >= top) return null;
        Holder<Biome> here = ctx.biomeResolver().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
        if (!ctx.validBiome().test(here)) return null;
        return new Site(new BlockPos(x, y, z), style.variant(here), seed);
    }

    /** P3: deep underground (20 to 50 blocks under the sea level), where the cave biome is the beast's, under a thick roof. */
    private static Site cave(Structure.GenerationContext ctx, LairStyle style, int x, int z, int sea, long seed, WorldgenRandom rnd) {
        int y = sea - 20 - rnd.nextInt(30);
        int minY = ctx.heightAccessor().getMinY() + LairStyle.DEPTH + 4;
        if (y < minY) y = minY;
        Holder<Biome> b = ctx.biomeResolver().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y + 3), QuartPos.fromBlock(z));
        if (!ctx.validBiome().test(b)) return null;
        int top = height(ctx, x, z, Heightmap.Types.WORLD_SURFACE_WG);
        if (top - y < 24) return null;
        return new Site(new BlockPos(x, y, z), style.variant(b), seed);
    }

    private static int height(Structure.GenerationContext ctx, int x, int z, Heightmap.Types type) {
        return ctx.chunkGenerator().getFirstOccupiedHeight(x, z, type, ctx.heightAccessor(), ctx.randomState());
    }

    @Override
    public StructureType<?> type() {
        return LairWorldgen.TYPE.get();
    }
}
