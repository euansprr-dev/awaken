package dev.awaken.terrain.hero;

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
 * A hero tree (lands pack {@code worldgen/structure/<tree>.json}, placed by a wide random spread). The start chunk is
 * the placement's; the tree may stand up to {@link #SEARCH} chunks from it, wherever its biome is, the land dry and its
 * footprint and root ring level enough. The biome check runs first because it is the cheap one.
 */
public class HeroStructure extends Structure {
    public static final MapCodec<HeroStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            HeroTree.CODEC.fieldOf("tree").forGetter(HeroStructure::tree)
    ).apply(i, HeroStructure::new));

    static final int ATTEMPTS = 12, SEARCH = 4;

    private final HeroTree tree;

    public HeroStructure(StructureSettings settings, HeroTree tree) {
        super(settings);
        this.tree = tree;
    }

    public HeroTree tree() {
        return tree;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        BlockPos site = findSite(ctx, tree);
        if (site == null) return Optional.empty();
        long seed = ctx.random().nextLong();
        return Optional.of(new GenerationStub(site, b -> b.addPiece(new HeroPiece(tree, site, seed))));
    }

    /** Where the tree's foot goes for this candidate chunk (the first air over the ground at its centre), or null. */
    static BlockPos findSite(GenerationContext ctx, HeroTree tree) {
        ChunkPos c = ctx.chunkPos();
        WorldgenRandom rnd = ctx.random();
        int sea = ctx.chunkGenerator().getSeaLevel();
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            int x = c.getMiddleBlockX(), z = c.getMiddleBlockZ();
            if (attempt > 0) {
                x += (rnd.nextInt(SEARCH * 2 + 1) - SEARCH) * 16 + rnd.nextInt(9) - 4;
                z += (rnd.nextInt(SEARCH * 2 + 1) - SEARCH) * 16 + rnd.nextInt(9) - 4;
            }
            // never in or over the city's basin (Carillon's site; a no-op unless the city is on)
            if (cityNear(x, z, tree.reach)) continue;
            int top = height(ctx, x, z, Heightmap.Types.WORLD_SURFACE_WG);
            Holder<Biome> b = ctx.biomeResolver().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(top), QuartPos.fromBlock(z));
            if (!ctx.validBiome().test(b)) continue;
            if (top <= sea || height(ctx, x, z, Heightmap.Types.OCEAN_FLOOR_WG) != top) continue;
            int f = tree.footprint, lo = top, hi = top;
            boolean ok = true;
            for (int[] k : new int[][]{{f, f}, {-f, f}, {f, -f}, {-f, -f}}) {
                int hgt = height(ctx, x + k[0], z + k[1], Heightmap.Types.WORLD_SURFACE_WG);
                if (hgt != height(ctx, x + k[0], z + k[1], Heightmap.Types.OCEAN_FLOOR_WG)) ok = false;
                lo = Math.min(lo, hgt);
                hi = Math.max(hi, hgt);
            }
            if (!ok || hi - lo > tree.maxStep) continue;
            for (int k = 0; k < 8 && ok; k++) {
                double a = k * Math.PI / 4;
                int rx = x + (int) Math.round(Math.cos(a) * tree.ring), rz = z + (int) Math.round(Math.sin(a) * tree.ring);
                int hgt = height(ctx, rx, rz, Heightmap.Types.OCEAN_FLOOR_WG);
                if (Math.abs(hgt - top) > tree.maxRing) ok = false;
            }
            if (!ok) continue;
            int foot = lo + 1;
            if (foot + 110 > ctx.heightAccessor().getMaxY()) continue;
            return new BlockPos(x, foot, z);
        }
        return null;
    }

    /** The city reserve under the tree's foot or under any edge of its crown. */
    private static boolean cityNear(int x, int z, int reach) {
        if (dev.awaken.terrain.api.CityBasin.reserved(x, z)) return true;
        for (int[] k : new int[][]{{reach, 0}, {-reach, 0}, {0, reach}, {0, -reach}}) {
            if (dev.awaken.terrain.api.CityBasin.reserved(x + k[0], z + k[1])) return true;
        }
        return false;
    }

    private static int height(GenerationContext ctx, int x, int z, Heightmap.Types type) {
        return ctx.chunkGenerator().getFirstOccupiedHeight(x, z, type, ctx.heightAccessor(), ctx.randomState());
    }

    @Override
    public StructureType<?> type() {
        return HeroWorldgen.TYPE.get();
    }
}
