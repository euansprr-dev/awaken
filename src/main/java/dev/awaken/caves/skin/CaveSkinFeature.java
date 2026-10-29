package dev.awaken.caves.skin;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * The skin (SPEC §42.4): once per chunk, last of all ({@code top_layer_modification}, so every other feature and structure
 * has had its say), the caves of the chunk are dressed from the rock they're cut in. Every column is read bottom to top
 * straight from the chunk's sections and only the cells where rock meets air are looked at: a floor takes rubble against a
 * wall or a few loose stones in the open; a wet roof (a 2D field) grows stalactites with stalagmites under them and a puddle
 * where the drops land; the walls just above a floor take the band's lichen, mineral crust where it's dry and wet streaks
 * where it drips; a dry dead end takes a dusty web. Every choice is a hash of the seed and the cell, so a chunk comes out
 * the same whatever order its neighbours were made in; only this chunk's cells are written.
 * <p>
 * Never touches anything but natural rock: a piece hangs from or lies on the stone family, deepslate or tuff, and a cell
 * next to anything a structure or a mine put there (planks, rails, bricks, webs, ores...) is left alone.
 */
public final class CaveSkinFeature implements Feature {
    public static final CaveSkinFeature INSTANCE = new CaveSkinFeature();
    public static final MapCodec<CaveSkinFeature> CODEC = MapCodec.unit(INSTANCE);

    private CaveSkinFeature() {}

    @Override
    public MapCodec<CaveSkinFeature> codec() {
        return CODEC;
    }

    /** Chances and fields (SPEC §42.4). */
    static final float RUBBLE = 0.18F, PEBBLES = 0.045F, DRIP_ROOF = 0.16F, STALAGMITE = 0.6F, PUDDLE = 0.75F,
            WEB = 0.2F, WET = 0.22F, DRY = -0.3F, LICHEN = 0.4F, CRUST = 0.45F, STREAKS = 0.25F;
    /** No skin within this many blocks of the ground (the mouths and the first metres stay the land's). */
    public static final int UNDER_GROUND = 6;
    /** Where the Shallows end below the ground, in blocks (the region rule's depth 0.34 at 128 blocks a unit). */
    static final int SHALLOWS_DEPTH = 43;

    /** The cells the skin reads and writes: the chunk at (cx, cz) and reads one block round it. */
    public interface Cells {
        BlockState get(int x, int y, int z);

        void set(int x, int y, int z, BlockState state);

        /** The y of the top solid block of the column (the ocean floor heightmap's). */
        int top(int x, int z);

        int minY();
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        int cx = origin.getX() & ~15, cz = origin.getZ() & ~15;
        ChunkAccess chunk = level.getChunk(cx >> 4, cz >> 4);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        Cells cells = new Cells() {
            @Override
            public BlockState get(int x, int y, int z) {
                if ((x & ~15) == cx && (z & ~15) == cz) {
                    int i = chunk.getSectionIndex(y);
                    if (i < 0 || i >= chunk.getSectionsCount()) return Blocks.AIR.defaultBlockState();
                    return chunk.getSection(i).getBlockState(x & 15, y & 15, z & 15);
                }
                return level.getBlockState(m.set(x, y, z));
            }

            @Override
            public void set(int x, int y, int z, BlockState state) {
                level.setBlock(m.set(x, y, z), state, Block.UPDATE_CLIENTS);
            }

            @Override
            public int top(int x, int z) {
                return chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x & 15, z & 15);
            }

            @Override
            public int minY() {
                return level.getMinY();
            }
        };
        return dress(cells, cx, cz, level.getSeed(), chunk) > 0;
    }

    /**
     * Dresses the chunk at (cx, cz), whose sections are {@code chunk}'s; returns the pieces placed. The chunk is read a
     * section at a time, layer by layer, straight from its palettes (a section without air is passed over whole); only
     * where rock meets air does the skin look closer, through {@code c}.
     */
    public static int dress(Cells c, int cx, int cz, long seed, ChunkAccess chunk) {
        int placed = 0;
        int minY = c.minY() + 2, maxTop = Integer.MIN_VALUE;
        int[] tops = new int[256];
        float[] wet = new float[256];
        BlockState[] below = new BlockState[256];
        for (int i = 0; i < 256; i++) {
            int x = cx + (i & 15), z = cz + (i >> 4);
            tops[i] = c.top(x, z) - UNDER_GROUND;
            maxTop = Math.max(maxTop, tops[i]);
            wet[i] = field(seed, x, z, 12, 0x5EED);
            below[i] = c.get(x, minY - 1, z);
        }
        for (int si = 0; si < chunk.getSectionsCount(); si++) {
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(si));
            if (baseY + 15 < minY) continue;
            if (baseY >= maxTop) break;
            LevelChunkSection section = chunk.getSection(si);
            if (section.hasOnlyAir() || !section.getStates().maybeHas(BlockState::isAir)) {
                for (int i = 0; i < 256; i++) below[i] = section.getBlockState(i & 15, 15, i >> 4);
                continue;
            }
            for (int ly = 0; ly < 16; ly++) {
                int y = baseY + ly;
                if (y < minY) continue;
                for (int i = 0; i < 256; i++) {
                    BlockState here = section.getBlockState(i & 15, ly, i >> 4);
                    if (y < tops[i]) {
                        int x = cx + (i & 15), z = cz + (i >> 4);
                        if (here.isAir()) {
                            if (rock(below[i]) != null) placed += floor(c, seed, x, y, z, tops[i] + UNDER_GROUND, wet[i], below[i]);
                        } else if (below[i].isAir() && wet[i] > WET && rock(here) != null && chance(seed, x, y - 1, z, 1) < DRIP_ROOF) {
                            placed += roof(c, seed, x, y - 1, z, here);
                        }
                    }
                    below[i] = here;
                }
            }
        }
        return placed;
    }

    // ---- floors and the walls above them -------------------------------------------------------------------------

    private static int floor(Cells c, long seed, int x, int y, int z, int ground, float wet, BlockState floorRock) {
        if (!clean(c, x, y, z)) return 0;
        int placed = 0;
        Direction wall = null;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (rock(c.get(x + d.getStepX(), y, z + d.getStepZ())) != null) {
                wall = d;
                break;
            }
        }
        SkinRock r = rock(floorRock);
        CaveSkin.Kit kit = CaveSkin.KITS.get(r);
        float roll = chance(seed, x, y, z, 2);
        int variant = (int) (chance(seed, x, y, z, 3) * 3);
        if (wall != null && roll < RUBBLE) {
            c.set(x, y, z, kit.rubble().get().defaultBlockState().setValue(SkinBlocks.Rubble.FACING, wall).setValue(SkinBlocks.VARIANT, variant));
            placed++;
        } else if (wall == null && roll < PEBBLES) {
            c.set(x, y, z, kit.pebbles().get().defaultBlockState().setValue(SkinBlocks.VARIANT, variant));
            placed++;
        } else if (wall != null && wet < DRY && roll > 1 - WEB && corner(c, x, y + 1, z)) {
            c.set(x, y + 1, z, CaveSkin.DUSTY_WEB.get().defaultBlockState());
            return placed + 1;
        }
        if (wall != null) placed += walls(c, seed, x, y, z, ground, wet);
        return placed;
    }

    /** The walls of the two cells over a floor: lichen in patches, crust where it's dry, streaks where it drips. */
    private static int walls(Cells c, long seed, int x, int y0, int z, int ground, float wet) {
        int placed = 0;
        for (int y = y0 + 1; y <= y0 + 2; y++) {
            if (!c.get(x, y, z).isAir()) return placed;
            MultifaceBlock kind = null;
            if (field3(seed, x, y, z, 6, 0x11C4) > LICHEN) kind = lichen(y, ground);
            else if (wet < DRY && field3(seed, x, y, z, 5, 0xC0A5) > CRUST) kind = CaveSkin.MINERAL_CRUST.get();
            else if (wet > WET && y == y0 + 2 && field3(seed, x, y, z, 3, 0x57EA) > STREAKS) kind = CaveSkin.WET_STREAKS.get();
            if (kind == null) continue;
            BlockState s = kind.defaultBlockState();
            boolean any = false;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (rock(c.get(x + d.getStepX(), y, z + d.getStepZ())) != null) {
                    s = s.setValue(MultifaceBlock.getFaceProperty(d), true);
                    any = true;
                }
            }
            if (any && clean(c, x, y, z)) {
                c.set(x, y, z, s);
                placed++;
            }
        }
        return placed;
    }

    static MultifaceBlock lichen(int y, int ground) {
        if (y < 0) return CaveSkin.LICHEN_DEEP.get();
        return ground - y < SHALLOWS_DEPTH ? CaveSkin.LICHEN_SHALLOWS.get() : CaveSkin.LICHEN_MIDDLE.get();
    }

    /** A dry dead end: rock on at least three of the four sides and close overhead. */
    private static boolean corner(Cells c, int x, int y, int z) {
        if (!c.get(x, y, z).isAir() || rock(c.get(x, y + 1, z)) == null) return false;
        int n = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) if (rock(c.get(x + d.getStepX(), y, z + d.getStepZ())) != null) n++;
        return n >= 3;
    }

    // ---- roofs: stalactites, the stalagmites under them, the puddle where the drops land --------------------------

    private static int roof(Cells c, long seed, int x, int y, int z, BlockState roofRock) {
        if (!clean(c, x, y, z)) return 0;
        int floor = Integer.MIN_VALUE;
        for (int yy = y - 1; yy >= y - 14 && yy > c.minY() + 1; yy--) {
            BlockState s = c.get(x, yy, z);
            if (!s.isAir()) {
                if (rock(s) != null) floor = yy + 1;
                break;
            }
        }
        if (floor == Integer.MIN_VALUE) return 0;
        int room = y - floor + 1;           // air cells from the floor to the roof
        if (room < 2) return 0;
        int placed = 0;
        int down = Math.min(1 + (int) (chance(seed, x, y, z, 4) * 3), Math.max(1, (room - 1) / 2));
        placed += column(c, x, y, z, rock(roofRock), Direction.DOWN, down);
        int free = room - down - 1;         // keep a gap: the two never quite meet
        if (free >= 1 && chance(seed, x, y, z, 5) < STALAGMITE) {
            int up = Math.min(1 + (int) (chance(seed, x, y, z, 6) * 3), free);
            if (clean(c, x, floor, z)) placed += column(c, x, floor, z, rock(c.get(x, floor - 1, z)), Direction.UP, up);
            // the drops land round its foot
            Direction d = AROUND[(int) (chance(seed, x, y, z, 7) * 4)];
            placed += puddle(c, seed, x + d.getStepX(), floor, z + d.getStepZ());
        } else {
            placed += puddle(c, seed, x, floor, z);
        }
        return placed;
    }

    private static final Direction[] AROUND = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private static int column(Cells c, int x, int from, int z, SkinRock r, Direction dir, int length) {
        if (r == null) return 0;
        SkinBlocks.Formation f = CaveSkin.KITS.get(r).formation().get();
        int step = dir == Direction.UP ? 1 : -1, n = 0;
        for (int k = 0; k < length; k++) {
            int y = from + k * step;
            if (!c.get(x, y, z).isAir()) break;
            n++;
        }
        for (int k = 0; k < n; k++) {
            c.set(x, from + k * step, z, f.defaultBlockState().setValue(SkinBlocks.Formation.DIRECTION, dir)
                    .setValue(SkinBlocks.Thickness.PROPERTY, SkinBlocks.Thickness.along(n - 1 - k, n)));
        }
        return n;
    }

    private static int puddle(Cells c, long seed, int x, int y, int z) {
        if (chance(seed, x, y, z, 8) >= PUDDLE) return 0;
        if (!c.get(x, y, z).isAir() || rock(c.get(x, y - 1, z)) == null || !clean(c, x, y, z)) return 0;
        c.set(x, y, z, CaveSkin.PUDDLE.get().defaultBlockState());
        return 1;
    }

    // ---- what the skin may touch ---------------------------------------------------------------------------------

    static SkinRock rock(BlockState s) {
        return SkinRock.of(s.getBlock());
    }

    private static final Set<Block> GROUND = Collections.newSetFromMap(new IdentityHashMap<>());

    static {
        for (Block b : new Block[] {Blocks.AIR, Blocks.CAVE_AIR, Blocks.WATER, Blocks.LAVA, Blocks.GRAVEL, Blocks.DIRT, Blocks.COARSE_DIRT,
                Blocks.CLAY, Blocks.SAND, Blocks.CALCITE, Blocks.SMOOTH_BASALT, Blocks.DRIPSTONE_BLOCK, Blocks.POINTED_DRIPSTONE,
                Blocks.MOSS_BLOCK, Blocks.MOSS_CARPET, Blocks.GLOW_LICHEN, Blocks.MAGMA_BLOCK, Blocks.BEDROCK}) GROUND.add(b);
    }

    /** Natural ground, the skin's own pieces, or a cave's water: what a skin cell may sit among. */
    static boolean natural(BlockState s) {
        Block b = s.getBlock();
        return GROUND.contains(b) || SkinRock.of(b) != null || s.is(BlockTags.BASE_STONE_OVERWORLD) || skin(b);
    }

    public static boolean skin(Block b) {
        return b instanceof SkinBlocks.Rubble || b instanceof SkinBlocks.Pebbles || b instanceof SkinBlocks.Formation
                || b instanceof SkinBlocks.Puddle || b == CaveSkin.DUSTY_WEB.get() || b instanceof MultifaceBlock && b != Blocks.GLOW_LICHEN
                && b != Blocks.SCULK_VEIN && b != Blocks.RESIN_CLUMP;
    }

    /** The cell and its six neighbours are natural: nothing built is near. */
    static boolean clean(Cells c, int x, int y, int z) {
        if (!natural(c.get(x, y, z))) return false;
        for (Direction d : Direction.values()) if (!natural(c.get(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ()))) return false;
        return true;
    }

    // ---- hashes and fields ---------------------------------------------------------------------------------------

    static long hash(int x, int y, int z, int salt) {
        long h = x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L ^ salt * 0xD6E8FEB86659FD93L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return h;
    }

    /** A fixed roll in [0, 1) for a cell. */
    static float chance(long seed, int x, int y, int z, int salt) {
        long h = hash(x, y, z, salt) ^ seed;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h >>> 40) / (float) (1L << 24);
    }

    /** A smooth 2D field in [-1, 1] at about {@code scale} blocks (value noise, smoothstep between lattice points). */
    static float field(long seed, int x, int z, int scale, int salt) {
        int gx = Math.floorDiv(x, scale), gz = Math.floorDiv(z, scale);
        float tx = smooth((x - gx * scale) / (float) scale), tz = smooth((z - gz * scale) / (float) scale);
        float a = lattice(seed, gx, 0, gz, salt), b = lattice(seed, gx + 1, 0, gz, salt);
        float c = lattice(seed, gx, 0, gz + 1, salt), d = lattice(seed, gx + 1, 0, gz + 1, salt);
        return lerp(lerp(a, b, tx), lerp(c, d, tx), tz);
    }

    static float field3(long seed, int x, int y, int z, int scale, int salt) {
        int gy = Math.floorDiv(y, scale);
        float ty = smooth((y - gy * scale) / (float) scale);
        float lo = field(seed ^ hash(0, gy, 0, salt), x, z, scale, salt), hi = field(seed ^ hash(0, gy + 1, 0, salt), x, z, scale, salt);
        return lerp(lo, hi, ty);
    }

    private static float lattice(long seed, int x, int y, int z, int salt) {
        return chance(seed, x, y, z, salt) * 2 - 1;
    }

    private static float smooth(float t) {
        return t * t * (3 - 2 * t);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
