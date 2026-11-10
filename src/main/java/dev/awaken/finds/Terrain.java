package dev.awaken.finds;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/**
 * The land as the chunk generator's own noise has it (SPEC §58.4): the same answer from any chunk while the world is
 * made and from the server afterwards, with no chunk loaded. That is what lets a site and everything in it be a pure
 * function of the seed.
 */
public interface Terrain {
    /** Structure sets that never stand where a place does: under the ground, under the sea, in the sky, in other worlds. */
    java.util.Set<String> UNSEEN = java.util.Set.of("ancient_cities", "trial_chambers", "ocean_ruins", "ocean_monuments", "shipwrecks", "buried_treasures",
            "end_cities", "nether_complexes", "nether_fossils", "mineshafts", "strongholds", "precursor_ocean_labs", "precursor_sky_platforms", "precursor_vaults");

    /** The top solid block of a column (under water, the bed). */
    int floor(int x, int z);

    /** The top block of a column, water included. */
    int surface(int x, int z);

    int seaLevel();

    /** Whether a sparse structure that stands on the land (a ruin, a temple, a spire) may start within {@code chunks} chunks. */
    boolean structureNear(int x, int z, int chunks);

    /** Whether a settlement (a village, an outpost, a mansion: the wide ones) may start within {@code chunks} chunks. */
    default boolean settlementNear(int x, int z, int chunks) {
        return false;
    }

    /** Level ground at one height, far above any sea: what a site made by hand stands on. */
    static Terrain flat(int y) {
        return new Terrain() {
            @Override
            public int floor(int x, int z) {
                return y;
            }

            @Override
            public int surface(int x, int z) {
                return y;
            }

            @Override
            public int seaLevel() {
                return y - 40;
            }

            @Override
            public boolean structureNear(int x, int z, int chunks) {
                return false;
            }
        };
    }

    static Terrain of(ServerLevel level) {
        return of(level, level.getChunkSource().getGenerator());
    }

    static Terrain of(ServerLevel level, ChunkGenerator gen) {
        var state = level.getChunkSource().randomState();
        return new Terrain() {
            @Override
            public int floor(int x, int z) {
                return gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, state) - 1;
            }

            @Override
            public int surface(int x, int z) {
                return gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, state) - 1;
            }

            @Override
            public int seaLevel() {
                return gen.getSeaLevel();
            }

            @Override
            public boolean structureNear(int x, int z, int chunks) {
                return near(x, z, chunks, false);
            }

            @Override
            public boolean settlementNear(int x, int z, int chunks) {
                return near(x, z, chunks, true);
            }

            /**
             * Only the sparse sets (one start every 16 chunks or more): the dense ones (the roads' lattice) would rule out
             * everywhere. And only what stands on the land: a set asks "may one start here", whatever the biome, so two
             * dozen sets under the ground, under the sea and in the sky ruled out nearly every place (the first census).
             */
            private boolean near(int x, int z, int chunks, boolean settlements) {
                var gs = level.getChunkSource().getGeneratorState();
                for (var set : gs.possibleStructureSets()) {
                    if (!(set.value().placement() instanceof RandomSpreadStructurePlacement rs) || rs.spacing() < 16) continue;
                    String id = set.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
                    boolean settlement = id.equals("villages") || id.equals("pillager_outposts") || id.equals("woodland_mansions");
                    if (settlement != settlements || UNSEEN.contains(id)) continue;
                    if (gs.hasStructureChunkInRange(set, x >> 4, z >> 4, chunks)) return true;
                }
                return false;
            }
        };
    }
}
