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

    /**
     * Where the things that stand on the land start inside a rectangle of blocks, as the middles of their start chunks
     * ({x, z}): the sparse structures (a ruin, a temple, a spire), or with {@code settlements} the wide ones (our own
     * settlements, villages, outposts, mansions). A monument's spot is tried against these (SPEC §62.3).
     */
    default java.util.List<int[]> starts(int x0, int z0, int x1, int z1, boolean settlements) {
        return java.util.List.of();
    }

    /**
     * The same land, remembering every column it was asked for: for a search that asks the same columns over and over
     * (a monument tried at forty-nine spots in four turns asks about a thousand columns ten thousand times). One
     * search's own; never shared between threads.
     */
    static Terrain memo(Terrain t) {
        return new Terrain() {
            private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap floors = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap(), tops = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();

            {
                floors.defaultReturnValue(Integer.MIN_VALUE);
                tops.defaultReturnValue(Integer.MIN_VALUE);
            }

            @Override
            public int floor(int x, int z) {
                long k = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
                int v = floors.get(k);
                if (v == Integer.MIN_VALUE) floors.put(k, v = t.floor(x, z));
                return v;
            }

            @Override
            public int surface(int x, int z) {
                long k = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
                int v = tops.get(k);
                if (v == Integer.MIN_VALUE) tops.put(k, v = t.surface(x, z));
                return v;
            }

            @Override
            public int seaLevel() {
                return t.seaLevel();
            }

            @Override
            public boolean structureNear(int x, int z, int chunks) {
                return t.structureNear(x, z, chunks);
            }

            @Override
            public boolean settlementNear(int x, int z, int chunks) {
                return t.settlementNear(x, z, chunks);
            }

            @Override
            public java.util.List<int[]> starts(int x0, int z0, int x1, int z1, boolean settlements) {
                return t.starts(x0, z0, x1, z1, settlements);
            }
        };
    }

    /**
     * A hillside falling to the south with a roll in it, far above any sea: what the picture maker stands a monument on to
     * see how it meets land that is not level (SPEC §62.3). About sixteen blocks from top to bottom across a monument.
     */
    static Terrain hill(int y) {
        return new Terrain() {
            @Override
            public int floor(int x, int z) {
                return y - (int) Math.round(z * 0.15 + 2.4 * Math.sin(x * 0.09) * Math.cos(z * 0.07) + 1.5 * Math.sin((x + z) * 0.05));
            }

            @Override
            public int surface(int x, int z) {
                return floor(x, z);
            }

            @Override
            public int seaLevel() {
                return y - 60;
            }

            @Override
            public boolean structureNear(int x, int z, int chunks) {
                return false;
            }
        };
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
                return scan((x >> 4) - chunks, (z >> 4) - chunks, (x >> 4) + chunks, (z >> 4) + chunks, false, null);
            }

            @Override
            public boolean settlementNear(int x, int z, int chunks) {
                return scan((x >> 4) - chunks, (z >> 4) - chunks, (x >> 4) + chunks, (z >> 4) + chunks, true, null);
            }

            @Override
            public java.util.List<int[]> starts(int x0, int z0, int x1, int z1, boolean settlements) {
                java.util.List<int[]> out = new java.util.ArrayList<>(4);
                scan(x0 >> 4, z0 >> 4, x1 >> 4, z1 >> 4, settlements, out);
                return out;
            }

            /**
             * The start chunks in a rectangle of chunks: into {@code out}, or true at the first where there is none.
             * Only the sparse sets (one start every 16 chunks or more): the dense ones (the roads' lattice) would rule
             * out everywhere. And only what stands on the land: a set asks "may one start here", whatever the biome, so
             * two dozen sets under the ground, under the sea and in the sky ruled out nearly every place (the first census).
             */
            private boolean scan(int cx0, int cz0, int cx1, int cz1, boolean settlements, java.util.@org.jspecify.annotations.Nullable List<int[]> out) {
                var gs = level.getChunkSource().getGeneratorState();
                for (var set : gs.possibleStructureSets()) {
                    var placement = set.value().placement();
                    String id = set.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
                    // "settlement" is our own (awaken:settlement_grid): the first worlds made without a client had a mast in
                    // the middle of a walled village, because only Minecraft's own set of villages was asked, which our
                    // villages' pack empties
                    boolean settlement = id.equals("settlement") || id.equals("villages") || id.equals("pillager_outposts") || id.equals("woodland_mansions");
                    if (settlement != settlements || UNSEEN.contains(id)) continue;
                    if (!(placement instanceof RandomSpreadStructurePlacement rs)) {
                        // a placement of our own answers for any chunk by the seed alone: the settlements' grid, the vault by the start
                        if (!settlement && !id.equals("precursor_spawn_vault")) continue;
                        for (int ox = cx0; ox <= cx1; ox++)
                            for (int oz = cz0; oz <= cz1; oz++) {
                                if (!placement.isStructureChunk(gs, ox, oz)) continue;
                                if (out == null) return true;
                                out.add(new int[] {ox * 16 + 8, oz * 16 + 8});
                            }
                        continue;
                    }
                    if (rs.spacing() < 16) continue;
                    // a set says "one may start in this chunk" whatever the land: asked that way, a desert temple's set ruled
                    // out forest sites and one site in five was lost to nothing (the count of 11 Nov). So: the set's own
                    // start chunk within range, and a biome there that one of its structures stands in.
                    // (a set has one start in each square of its spacing: asked a square at a time, not a chunk at a time)
                    int sp = rs.spacing();
                    for (int gx = Math.floorDiv(cx0, sp); gx <= Math.floorDiv(cx1, sp); gx++)
                        for (int gz = Math.floorDiv(cz0, sp); gz <= Math.floorDiv(cz1, sp); gz++) {
                            var p = rs.getPotentialStructureChunk(gs.getLevelSeed(), gx * sp, gz * sp);
                            int ox = p.x(), oz = p.z();
                            if (ox < cx0 || ox > cx1 || oz < cz0 || oz > cz1 || !rs.isStructureChunk(gs, ox, oz)) continue;
                            var biome = level.getUncachedNoiseBiome((ox * 16 + 8) >> 2, 16, (oz * 16 + 8) >> 2);
                            for (var e : set.value().structures())
                                if (e.structure().value().biomes().contains(biome)) {
                                    if (out == null) return true;
                                    out.add(new int[] {ox * 16 + 8, oz * 16 + 8});
                                    break;
                                }
                        }
                }
                return false;
            }
        };
    }
}
