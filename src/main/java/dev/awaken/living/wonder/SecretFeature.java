package dev.awaken.living.wonder;

import com.mojang.serialization.MapCodec;
import dev.awaken.living.region.RegionMap;
import dev.awaken.living.region.RegionService;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/** {@code awaken:secrets} (SPEC §21.11): each new chunk writes its share of the surface secrets near it, and fills the caches from their rank's loot table. */
public record SecretFeature() implements Feature {
    public static final MapCodec<SecretFeature> CODEC = MapCodec.unit(SecretFeature::new);

    @Override
    public MapCodec<SecretFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator gen, RandomSource random, BlockPos origin) {
        var sl = level.getLevel();
        if (sl.dimension() != Level.OVERWORLD) return false;
        RegionMap map = RegionService.mapIfReady(sl.getServer());
        if (map == null) return false;
        int x0 = (origin.getX() >> 4) << 4, z0 = (origin.getZ() >> 4) << 4;
        var sites = Secrets.near(level.getSeed(), map, WonderFeature.terrain(sl, gen), x0, z0, x0 + 15, z0 + 15, Secrets.REACH);
        boolean any = false;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (Secrets.Site site : sites) {
            for (var e : site.plan().blocks().long2ObjectEntrySet()) {
                long k = e.getLongKey();
                int x = site.x() + BlockPos.getX(k), z = site.z() + BlockPos.getZ(k);
                if (x < x0 || x > x0 + 15 || z < z0 || z > z0 + 15) continue;
                m.set(x, site.y() + BlockPos.getY(k), z);
                level.setBlock(m, e.getValue(), Block.UPDATE_CLIENTS);
                any = true;
            }
            for (Secrets.Cache c : site.plan().caches()) {
                BlockPos at = new BlockPos(site.x() + c.dx(), site.y() + c.dy(), site.z() + c.dz());
                if (at.getX() < x0 || at.getX() > x0 + 15 || at.getZ() < z0 || at.getZ() > z0 + 15) continue;
                var table = Secrets.table(site.rank(), c.brushable());
                BlockEntity be = level.getBlockEntity(at);
                if (be instanceof BrushableBlockEntity brush) brush.setLootTable(table, random.nextLong());
                else if (be != null) RandomizableContainer.setBlockEntityLootTable(level, random, at, table);
            }
        }
        return any;
    }
}
