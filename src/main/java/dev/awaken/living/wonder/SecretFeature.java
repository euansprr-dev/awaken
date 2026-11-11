package dev.awaken.living.wonder;

import com.mojang.serialization.MapCodec;
import dev.awaken.core.api.GroundPiece;
import dev.awaken.living.region.RegionMap;
import dev.awaken.living.region.RegionService;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkDependencies;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code awaken:secrets} (SPEC §21.11): each new chunk writes its share of the surface secrets near it, and fills the caches from their rank's loot table.
 * A secret is written whole or not at all: every chunk it touches asks {@link #keepsOff} the same question of the same land and gets the same answer.
 */
public record SecretFeature() implements Feature {
    public static final MapCodec<SecretFeature> CODEC = MapCodec.unit(SecretFeature::new);

    /**
     * What the region decorating a chunk holds round it (vanilla's pyramid): references out to {@link #REFERENCES} (the
     * chunks beside it), starts out to {@link #STARTS}. Every chunk a secret touches lies beside every other (it is 13
     * blocks wide at most), so each of them reads the same references.
     */
    private static final ChunkDependencies HELD = ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES).directDependencies();
    private static final int REFERENCES = HELD.getRadiusOf(ChunkStatus.STRUCTURE_REFERENCES), STARTS = HELD.getRadiusOf(ChunkStatus.STRUCTURE_STARTS);
    /** How far round its blocks a structure's box keeps a secret off (its banks, a rim). */
    private static final int MARGIN = 2;

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
        var terrain = WonderFeature.terrain(sl, gen);
        var sites = Secrets.near(level.getSeed(), map, terrain, x0, z0, x0 + 15, z0 + 15, Secrets.REACH);
        boolean any = false;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (Secrets.Site site : sites) {
            // only a chunk it is written into asks (the land it reads is held round those, not round every chunk near it)
            if (!touches(site, x0, z0)) continue;
            String off = keepsOff(level, gen, map, terrain, site);
            if (off != null) {
                // said once, by the chunk holding its heart
                if (site.x() >> 4 == x0 >> 4 && site.z() >> 4 == z0 >> 4) dev.awaken.Awaken.LOG.info("[living] secret {} at {} {} keeps off: {}", site.key(), site.x(), site.y(), off);
                continue;
            }
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

    /** Whether any of the secret's blocks lies in the chunk whose corner is (x0, z0). */
    private static boolean touches(Secrets.Site site, int x0, int z0) {
        for (long k : site.plan().blocks().keySet()) {
            int x = site.x() + BlockPos.getX(k), z = site.z() + BlockPos.getZ(k);
            if (x >= x0 && x <= x0 + 15 && z >= z0 && z <= z0 + 15) return true;
        }
        return false;
    }

    /**
     * Why the secret can't stand where its plan puts it, or null when it can (SPEC §21.11, "Ground others stand on"). The noise says ground
     * there, but a structure may cut it first (a cave place open to the sky, at {@code underground_structures}) or paint,
     * clear or grade it later (a road, a settlement, a lair), a carver may have opened a pit there, and a wonder clears
     * and fills its own ground: on any of those it keeps off. Only what every chunk under it reads alike is asked (the
     * references of the chunks under it, starts every one of them holds, those chunks' carved blocks), so the answer is
     * the same from each, in any order: a start one of them can't read keeps it off as well.
     */
    static @Nullable String keepsOff(WorldGenLevel level, ChunkGenerator gen, RegionMap map, Wonders.Terrain terrain, Secrets.Site site) {
        int fx0 = Integer.MAX_VALUE, fz0 = Integer.MAX_VALUE, fx1 = Integer.MIN_VALUE, fz1 = Integer.MIN_VALUE, dy0 = 0, dy1 = 0;
        for (long k : site.plan().blocks().keySet()) {
            fx0 = Math.min(fx0, site.x() + BlockPos.getX(k));
            fz0 = Math.min(fz0, site.z() + BlockPos.getZ(k));
            fx1 = Math.max(fx1, site.x() + BlockPos.getX(k));
            fz1 = Math.max(fz1, site.z() + BlockPos.getZ(k));
            dy0 = Math.min(dy0, BlockPos.getY(k));
            dy1 = Math.max(dy1, BlockPos.getY(k));
        }
        if (fx0 > fx1) return "no blocks";
        // a wonder's ground (its blocks and the land it clears) is its own
        if (!Wonders.near(level.getSeed(), map, terrain, fx0, fz0, fx1, fz1, Wonders.REACH + MARGIN).isEmpty()) return "a wonder's ground";
        // the chunks it is written into, and those beside its blocks that every one of them holds references for
        int wx0 = fx0 >> 4, wx1 = fx1 >> 4, wz0 = fz0 >> 4, wz1 = fz1 >> 4;
        int sx0 = Math.max((fx0 - MARGIN) >> 4, wx1 - REFERENCES), sx1 = Math.min((fx1 + MARGIN) >> 4, wx0 + REFERENCES);
        int sz0 = Math.max((fz0 - MARGIN) >> 4, wz1 - REFERENCES), sz1 = Math.min((fz1 + MARGIN) >> 4, wz0 + REFERENCES);
        BoundingBox near = new BoundingBox(fx0 - MARGIN, site.y() + dy0 - Wonders.CUT_BELOW, fz0 - MARGIN,
                fx1 + MARGIN, site.y() + dy1 + Wonders.CUT_ABOVE, fz1 + MARGIN);
        int reach = Math.max(Math.max(site.x() - fx0, fx1 - site.x()), Math.max(site.z() - fz0, fz1 - site.z())) + MARGIN;
        List<StructureStart> seen = new ArrayList<>(4);
        for (int cx = sx0; cx <= sx1; cx++) {
            for (int cz = sz0; cz <= sz1; cz++) {
                for (Map.Entry<Structure, LongSet> refs : level.getChunk(cx, cz, ChunkStatus.STRUCTURE_REFERENCES).getAllReferences().entrySet()) {
                    for (LongIterator it = refs.getValue().iterator(); it.hasNext(); ) {
                        long key = it.nextLong();
                        int sx = ChunkPos.getX(key), sz = ChunkPos.getZ(key);
                        // a start one of its chunks can't read: what it does to the land can't be known alike, so keep off
                        if (Math.max(Math.max(Math.abs(sx - wx0), Math.abs(sx - wx1)), Math.max(Math.abs(sz - wz0), Math.abs(sz - wz1))) > STARTS
                                || !level.hasChunk(sx, sz)) return name(level, refs.getKey()) + " starting past what its chunks hold (" + sx + ", " + sz + ")";
                        StructureStart start = level.getChunk(sx, sz, ChunkStatus.STRUCTURE_STARTS).getStartForStructure(refs.getKey());
                        if (start == null || !start.isValid() || seen.contains(start)) continue;
                        seen.add(start);
                        for (StructurePiece piece : start.getPieces()) {
                            // a road or a settlement says which land it uses (its box is its whole raster); anything else, its box
                            if (piece instanceof GroundPiece g ? g.usesGround(level, gen, site.x(), site.z(), reach)
                                    : piece.getBoundingBox().intersects(near)) return name(level, refs.getKey()) + "'s ground (start " + sx + ", " + sz + ")";
                        }
                    }
                }
            }
        }
        // a carver's pit or a pond under a column (carved with the terrain, which every chunk beside this one is): the
        // column's ground block is gone, or water (ice too: a pond may freeze before a neighbour asks), or hangs over air
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (long col : site.plan().columns()) {
            int x = site.x() + BlockPos.getX(col), z = site.z() + BlockPos.getZ(col), y = site.y() + site.plan().foot(BlockPos.getX(col), BlockPos.getZ(col));
            if (!firm(level.getBlockState(m.set(x, y, z)), true) || !firm(level.getBlockState(m.set(x, y - 1, z)), false)) return "cut or wet ground at " + x + " " + y + " " + z;
        }
        return null;
    }

    private static String name(WorldGenLevel level, Structure s) {
        var key = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).getKey(s);
        return key == null ? "a structure" : key.toString();
    }

    /** Ground a secret can stand on: not air, water or ice, and (its top block) a whole block. */
    private static boolean firm(BlockState s, boolean top) {
        return !s.isAir() && s.getFluidState().isEmpty() && !s.is(net.minecraft.tags.BlockTags.ICE) && (!top || s.isSolidRender());
    }
}
