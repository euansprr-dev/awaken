package dev.awaken.caves.skin;

import com.mojang.serialization.MapCodec;
import dev.awaken.caves.CaveRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.neoforge.common.Tags;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
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
 * <p>
 * In a region (SPEC §42.7; the chunk's biomes say where) the rock is the region's first: the stone family and tuff turn to
 * its rock, the ores that live there to their twins in it, and any other ore keeps a shell of the stone it grew in. Then
 * the skin is cut from that rock, and the region adds its own: the Rootways' glowcaps and root curtains, the Ochre Deep's
 * rust crust, rust flowstone and ember lichen.
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
    /** The regions' own (SPEC §42.7): their rock stops this far under the ground; growth and glow by these chances. */
    public static final int REGION_UNDER = 10;
    static final float GLOWCAP = 0.12F, GLOW_FIELD = 0.2F, ROOTS = 0.14F, ROOT_FIELD = 0.1F, EMBER = 0.1F, RUST = 0.2F;
    /**
     * The six P3 regions' own (SPEC §42.8): the Pale Halls drip everywhere (straws) and grow rimstone in patches, a drop's
     * landing a pearl now and then; the Windcleft's scree, rime and ropes; the Drowned Galleries' silt, driftwood, weed
     * and algae; the Glimmer Hollows' crystals on every face; the Black Vaults' ash; the Violet Deep's growths and husks.
     */
    static final float PALE_DRIP = 0.3F, RIMSTONE = 0.1F, RIM_FIELD = 0.15F, PEARLS = 0.4F, SCREE = 0.35F, RIME_FIELD = 0.3F,
            ROPE = 0.012F, SILT = 0.35F, SILT_FIELD = 0.35F, DRIFTWOOD = 0.012F, WEED = 0.3F, WEED_FIELD = 0F, ALGAE = 0.5F,
            CRYSTAL = 0.07F, ASH = 0.3F, ASH_FIELD = 0F, VIOLET = 0.08F, VIOLET_FIELD = 0.1F, CHITIN = 0.03F;
    /** The longest old rope down a crack, and the Glimmer Hollows' frost line (the land's base temperature). */
    static final int ROPE_MAX = 12;
    static final float FROST = 0.3F;

    /** Which region a cell is in, or null (the chunk's biomes in the game; a test may say). */
    public interface Regions {
        @Nullable CaveRegion at(int x, int y, int z);

        Regions NONE = (x, y, z) -> null;
    }

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
        return dress(cells, cx, cz, level.getSeed(), chunk, regions(chunk)) > 0;
    }

    /** The chunk's regions from its biomes, a quart (4 x 4 x 4) at a time, each read once. */
    static Regions regions(ChunkAccess chunk) {
        byte[] seen = new byte[chunk.getSectionsCount() * 64];   // 0 not read yet, 1 no region, else the region's ordinal + 2
        CaveRegion[] all = CaveRegion.values();
        return (x, y, z) -> {
            int si = chunk.getSectionIndex(y);
            if (si < 0 || si >= chunk.getSectionsCount()) return null;
            int qi = si * 64 + (((y & 15) >> 2) << 4 | ((z & 15) >> 2) << 2 | (x & 15) >> 2);
            byte b = seen[qi];
            if (b == 0) {
                Holder<Biome> h = chunk.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
                b = 1;
                for (CaveRegion r : all) {
                    if (h.is(r.biome())) {
                        b = (byte) (r.ordinal() + 2);
                        break;
                    }
                }
                seen[qi] = b;
            }
            return b == 1 ? null : all[b - 2];
        };
    }

    /**
     * Dresses the chunk at (cx, cz), whose sections are {@code chunk}'s; returns the pieces placed. The chunk is read a
     * section at a time, layer by layer, straight from its palettes (a section without air is passed over whole); only
     * where rock meets air does the skin look closer, through {@code c}.
     */
    public static int dress(Cells c, int cx, int cz, long seed, ChunkAccess chunk) {
        return dress(c, cx, cz, seed, chunk, Regions.NONE);
    }

    /** As {@link #dress(Cells, int, int, long, ChunkAccess)}, the regions' rock first and their own pieces with the skin. */
    public static int dress(Cells c, int cx, int cz, long seed, ChunkAccess chunk, Regions regions) {
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
        if (regions != Regions.NONE) regionRock(chunk, cx, cz, tops, maxTop, regions);
        Block crystal = crystal(chunk, cx, cz, maxTop + UNDER_GROUND);
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
                            if (rock(below[i]) != null) {
                                placed += floor(c, seed, x, y, z, tops[i] + UNDER_GROUND, wet[i], below[i], regions.at(x, y, z), crystal);
                            } else if (below[i].is(Blocks.WATER) && regions.at(x, y, z) == CaveRegion.DROWNED_GALLERIES
                                    && chance(seed, x, y, z, 13) < ALGAE && clean(c, x, y, z)) {
                                placed += overlay(c, x, y, z, CaveSkin.GLOW_ALGAE.get());
                            }
                        } else if (here.is(Blocks.WATER) && rock(below[i]) != null) {
                            if (regions.at(x, y, z) == CaveRegion.DROWNED_GALLERIES) placed += weed(c, seed, x, y, z, here);
                        } else if (below[i].isAir() && rock(here) != null) {
                            placed += ceiling(c, seed, x, y - 1, z, here, regions.at(x, y - 1, z), wet[i], crystal);
                        }
                    }
                    below[i] = here;
                }
            }
        }
        return placed;
    }

    // ---- floors and the walls above them -------------------------------------------------------------------------

    private static int floor(Cells c, long seed, int x, int y, int z, int ground, float wet, BlockState floorRock, @Nullable CaveRegion region,
                             Block crystal) {
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
        if (wall != null && roll < (region == CaveRegion.WINDCLEFT ? SCREE : RUBBLE)) {
            c.set(x, y, z, kit.rubble().get().defaultBlockState().setValue(SkinBlocks.Rubble.FACING, wall).setValue(SkinBlocks.VARIANT, variant));
            placed++;
        } else if (wall == null && roll < PEBBLES) {
            c.set(x, y, z, kit.pebbles().get().defaultBlockState().setValue(SkinBlocks.VARIANT, variant));
            placed++;
        } else if (region != null && floorOwn(c, seed, x, y, z, region, roll, variant, crystal)) {
            placed++;
        } else if (wall != null && wet < DRY && roll > 1 - WEB && corner(c, x, y + 1, z)) {
            c.set(x, y + 1, z, CaveSkin.DUSTY_WEB.get().defaultBlockState());
            return placed + 1;
        }
        if (wall != null) placed += walls(c, seed, x, y, z, ground, wet, region, crystal);
        return placed;
    }

    /** A region's own piece on a floor cell (SPEC §42.7, §42.8); true if one went down. */
    private static boolean floorOwn(Cells c, long seed, int x, int y, int z, CaveRegion region, float roll, int variant, Block crystal) {
        BlockState s = switch (region) {
            case ROOTWAYS -> roll > 1 - GLOWCAP && field3(seed, x, y, z, 5, 0x6C0A) > GLOW_FIELD
                    ? CaveSkin.GLOWCAP.get().defaultBlockState().setValue(SkinBlocks.VARIANT, variant) : null;
            case PALE_HALLS -> roll > 1 - RIMSTONE && field3(seed, x, y, z, 6, 0x9A1E) > RIM_FIELD
                    ? CaveSkin.RIMSTONE.get().defaultBlockState().setValue(SkinBlocks.VARIANT, variant) : null;
            case DROWNED_GALLERIES -> roll > 1 - SILT && (shore(c, x, y, z) || field3(seed, x, y, z, 6, 0x5117) > SILT_FIELD)
                    ? CaveSkin.SILT.get().defaultBlockState().setValue(SkinBlocks.VARIANT, variant)
                    : roll > 1 - SILT - DRIFTWOOD ? CaveSkin.DRIFTWOOD.get().defaultBlockState()
                    .setValue(SkinBlocks.Driftwood.AXIS, variant == 0 ? Direction.Axis.Z : Direction.Axis.X) : null;
            case GLIMMER_HOLLOWS -> roll > 1 - CRYSTAL ? crystal.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP) : null;
            case BLACK_VAULTS -> roll > 1 - ASH && field3(seed, x, y, z, 8, 0x0A5E) > ASH_FIELD
                    ? CaveSkin.ASH.get().defaultBlockState().setValue(SkinBlocks.VARIANT, variant) : null;
            case VIOLET_DEEP -> roll > 1 - VIOLET && field3(seed, x, y, z, 5, 0x7101) > VIOLET_FIELD
                    ? CaveSkin.VIOLET_GROWTH.get().defaultBlockState().setValue(SkinBlocks.VARIANT, variant)
                    : roll > 1 - VIOLET - CHITIN ? CaveSkin.SHED_CHITIN.get().defaultBlockState().setValue(SkinBlocks.VARIANT, variant) : null;
            default -> null;
        };
        if (s == null) return false;
        c.set(x, y, z, s);
        return true;
    }

    /** Water beside the cell: a lake's or a sump's shore. */
    private static boolean shore(Cells c, int x, int y, int z) {
        for (Direction d : Direction.Plane.HORIZONTAL) if (c.get(x + d.getStepX(), y, z + d.getStepZ()).is(Blocks.WATER)) return true;
        return false;
    }

    /**
     * The walls of the two cells over a floor: lichen in patches, crust where it's dry, streaks where it drips. In the
     * Ochre Deep a patch of lichen is now and then ember lichen, the crust is rust (dry or not) and the streaks are rust
     * flowstone, down both cells.
     */
    private static int walls(Cells c, long seed, int x, int y0, int z, int ground, float wet, @Nullable CaveRegion region, Block crystal) {
        int placed = 0;
        boolean ochre = region == CaveRegion.OCHRE_DEEP;
        for (int y = y0 + 1; y <= y0 + 2; y++) {
            if (!c.get(x, y, z).isAir()) return placed;
            if (region == CaveRegion.GLIMMER_HOLLOWS && chance(seed, x, y, z, 12) < CRYSTAL) {
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    if (rock(c.get(x + d.getStepX(), y, z + d.getStepZ())) == null) continue;
                    placed += crystal(c, x, y, z, d.getOpposite(), crystal);
                    break;
                }
                continue;
            }
            MultifaceBlock kind = null;
            if (region == CaveRegion.WINDCLEFT && field3(seed, x, y, z, 5, 0x041E) > RIME_FIELD) {
                kind = CaveSkin.RIME.get();
            } else if (field3(seed, x, y, z, 6, 0x11C4) > LICHEN) {
                kind = ochre && field3(seed, x, y, z, 4, 0x0E3B) > EMBER ? CaveSkin.EMBER_LICHEN.get() : lichen(y, ground);
            } else if (ochre && field3(seed, x, y, z, 5, 0xC0A5) > RUST) {
                kind = CaveSkin.RUST_CRUST.get();
            } else if (wet < DRY && field3(seed, x, y, z, 5, 0xC0A5) > CRUST) {
                kind = CaveSkin.MINERAL_CRUST.get();
            } else if (wet > WET && (y == y0 + 2 || ochre) && field3(seed, x, y, z, 3, 0x57EA) > STREAKS) {
                kind = ochre ? CaveSkin.RUST_FLOWSTONE.get() : CaveSkin.WET_STREAKS.get();
            }
            if (kind == null || !clean(c, x, y, z)) continue;
            placed += overlay(c, x, y, z, kind);
        }
        return placed;
    }

    /** An overlay on every rock face round the cell (sides only); 1 if there was one. */
    private static int overlay(Cells c, int x, int y, int z, MultifaceBlock kind) {
        BlockState s = kind.defaultBlockState();
        boolean any = false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (rock(c.get(x + d.getStepX(), y, z + d.getStepZ())) != null) {
                s = s.setValue(MultifaceBlock.getFaceProperty(d), true);
                any = true;
            }
        }
        if (!any) return 0;
        c.set(x, y, z, s);
        return 1;
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

    /** What hangs from a roof cell: a region's own first (roots, ropes, crystals), else the drips where it's wet (anywhere in the Pale Halls). */
    private static int ceiling(Cells c, long seed, int x, int y, int z, BlockState roofRock, @Nullable CaveRegion r, float wet, Block crystal) {
        float roll = chance(seed, x, y, z, 9);
        if (r == CaveRegion.ROOTWAYS && roll < ROOTS && field(seed, x, z, 7, 0x2007) > ROOT_FIELD) return roots(c, seed, x, y, z);
        if (r == CaveRegion.WINDCLEFT && roll < ROPE) return rope(c, seed, x, y, z);
        if (r == CaveRegion.GLIMMER_HOLLOWS && roll < CRYSTAL) return clean(c, x, y, z) ? crystal(c, x, y, z, Direction.DOWN, crystal) : 0;
        boolean pale = r == CaveRegion.PALE_HALLS;
        if ((pale || wet > WET) && chance(seed, x, y, z, 1) < (pale ? PALE_DRIP : DRIP_ROOF)) return roof(c, seed, x, y, z, roofRock, pale);
        return 0;
    }

    private static int roof(Cells c, long seed, int x, int y, int z, BlockState roofRock, boolean pearls) {
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
            placed += puddle(c, seed, x + d.getStepX(), floor, z + d.getStepZ(), pearls);
        } else {
            placed += puddle(c, seed, x, floor, z, pearls);
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

    /** Where the drops land: a puddle, or in the Pale Halls now and then a nest of cave pearls. */
    private static int puddle(Cells c, long seed, int x, int y, int z, boolean pearls) {
        if (chance(seed, x, y, z, 8) >= PUDDLE) return 0;
        if (!c.get(x, y, z).isAir() || rock(c.get(x, y - 1, z)) == null || !clean(c, x, y, z)) return 0;
        c.set(x, y, z, pearls && chance(seed, x, y, z, 15) < PEARLS
                ? CaveSkin.CAVE_PEARLS.get().defaultBlockState().setValue(SkinBlocks.VARIANT, (int) (chance(seed, x, y, z, 16) * 3))
                : CaveSkin.PUDDLE.get().defaultBlockState());
        return 1;
    }

    // ---- the regions' own (SPEC §42.7) ------------------------------------------------------------------------------

    /** Root curtains hanging from a Rootways roof: one to three blocks, at most half the room under them. */
    private static int roots(Cells c, long seed, int x, int y, int z) {
        if (!clean(c, x, y, z)) return 0;
        int room = 0;
        while (room < 7 && c.get(x, y - room, z).isAir()) room++;
        if (room < 3) return 0;
        int n = Math.min(1 + (int) (chance(seed, x, y, z, 10) * 3), room / 2);
        BlockState root = CaveSkin.ROOT_CURTAIN.get().defaultBlockState();
        for (int k = 0; k < n; k++) c.set(x, y - k, z, root.setValue(SkinBlocks.RootCurtain.TIP, k == n - 1));
        return n;
    }

    /** An old Delvers' rope down a Windcleft crack: three to {@link #ROPE_MAX} blocks, never quite to the floor. */
    private static int rope(Cells c, long seed, int x, int y, int z) {
        if (!clean(c, x, y, z)) return 0;
        int room = 0;
        while (room < ROPE_MAX + 2 && c.get(x, y - room, z).isAir()) room++;
        if (room < 5) return 0;
        int n = Math.min(3 + (int) (chance(seed, x, y, z, 11) * (ROPE_MAX - 2)), room - 2);
        BlockState rope = CaveSkin.OLD_ROPE.get().defaultBlockState();
        for (int k = 0; k < n; k++) c.set(x, y - k, z, rope.setValue(SkinBlocks.RootCurtain.TIP, k == n - 1));
        return n;
    }

    /** A Glimmer Hollows crystal growing from the face behind it ({@code facing} points away from that face). */
    private static int crystal(Cells c, int x, int y, int z, Direction facing, Block crystal) {
        if (!c.get(x, y, z).isAir()) return 0;
        c.set(x, y, z, crystal.defaultBlockState().setValue(AmethystClusterBlock.FACING, facing));
        return 1;
    }

    /** The Glimmer Hollows' crystal for this chunk: glacial under the cold lands, amber under the rest. */
    private static Block crystal(ChunkAccess chunk, int cx, int cz, int top) {
        // a chunk filled without its biomes (the tests' bench) has no land to read
        if (!chunk.getHighestGeneratedStatus().isOrAfter(ChunkStatus.BIOMES)) return CaveSkin.AMBER_CRYSTAL.get();
        Holder<Biome> land = chunk.getNoiseBiome(QuartPos.fromBlock(cx + 8), QuartPos.fromBlock(top), QuartPos.fromBlock(cz + 8));
        return land.value().getBaseTemperature() < FROST ? CaveSkin.GLACIAL_CRYSTAL.get() : CaveSkin.AMBER_CRYSTAL.get();
    }

    /** Pale cave weed on a Drowned Galleries lake's floor, in patches. */
    private static int weed(Cells c, long seed, int x, int y, int z, BlockState here) {
        if (!here.getFluidState().isSource() || chance(seed, x, y, z, 14) >= WEED || field3(seed, x, y, z, 4, 0x3EED) <= WEED_FIELD) return 0;
        if (!clean(c, x, y, z)) return 0;
        c.set(x, y, z, CaveSkin.CAVE_WEED.get().defaultBlockState());
        return 1;
    }

    /** Host rock a region turns to its own: the stone family and tuff (deepslate stays, the Deep's floor under it). */
    private static boolean host(Block b) {
        return b == Blocks.STONE || b == Blocks.ANDESITE || b == Blocks.DIORITE || b == Blocks.GRANITE || b == Blocks.TUFF;
    }

    static boolean ore(BlockState s) {
        return s.is(Tags.Blocks.ORES);
    }

    /**
     * The regions' rock: in every quart of the chunk a region holds (with its kit), more than {@link #REGION_UNDER} under
     * the ground, the host rock becomes the region's rock and an ore with a twin there becomes its twin. An ore without one
     * keeps the 26 cells round it as they were (within its section), so it sits in a pocket of the stone it grew in.
     * Written straight to the sections: rock for rock, nothing the heightmaps or the light care about.
     */
    /** A section's states as read, one per worldgen thread (the rock rule reads each cell once). */
    private static final ThreadLocal<BlockState[]> SECTION = ThreadLocal.withInitial(() -> new BlockState[4096]);

    static int regionRock(ChunkAccess chunk, int cx, int cz, int[] tops, int maxTop, Regions regions) {
        CaveRegion[] q = new CaveRegion[64];
        long[] keep = new long[64];
        BlockState[] states = SECTION.get();
        int changed = 0;
        for (int si = 0; si < chunk.getSectionsCount(); si++) {
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(si));
            if (baseY >= maxTop - REGION_UNDER + UNDER_GROUND) break;
            LevelChunkSection section = chunk.getSection(si);
            if (section.hasOnlyAir()) continue;
            boolean any = false;
            for (int k = 0; k < 64; k++) {
                CaveRegion r = regions.at(cx + (k & 3) * 4, baseY + (k >> 4) * 4, cz + (k >> 2 & 3) * 4);
                q[k] = r != null && CaveSkin.kit(r) != null ? r : null;
                any |= q[k] != null;
            }
            if (!any) continue;
            boolean shells = false, read = section.getStates().maybeHas(CaveSkinFeature::ore);
            if (read) {
                // the whole section is read once here, and the rock pass below works from what was read
                Arrays.fill(keep, 0L);
                for (int idx = 0; idx < 4096; idx++) {
                    int x = idx & 15, z = idx >> 4 & 15, y = idx >> 8;
                    BlockState s = section.getBlockState(x, y, z);
                    states[idx] = s;
                    // nearly every cell is host rock, deepslate or air: skip those before the tag lookup
                    Block sb = s.getBlock();
                    if (host(sb) || sb == Blocks.DEEPSLATE || s.isAir() || !ore(s)) continue;
                    CaveRegion r = q[(y >> 2) << 4 | (z >> 2) << 2 | x >> 2];
                    if (r != null && CaveSkin.kit(r).twins().containsKey(s.getBlock())) continue;
                    for (int dy = Math.max(0, y - 1); dy <= Math.min(15, y + 1); dy++)
                        for (int dz = Math.max(0, z - 1); dz <= Math.min(15, z + 1); dz++)
                            for (int dx = Math.max(0, x - 1); dx <= Math.min(15, x + 1); dx++) {
                                int b = dy << 8 | dz << 4 | dx;
                                keep[b >> 6] |= 1L << (b & 63);
                            }
                    shells = true;
                }
            }
            for (int k = 0; k < 64; k++) {
                CaveRegion r = q[k];
                if (r == null) continue;
                CaveSkin.RegionKit kit = CaveSkin.kit(r);
                BlockState rock = kit.rock().get().defaultBlockState();
                int x0 = (k & 3) * 4, z0 = (k >> 2 & 3) * 4, y0 = (k >> 4) * 4;
                for (int y = y0; y < y0 + 4; y++) {
                    for (int z = z0; z < z0 + 4; z++) {
                        for (int x = x0; x < x0 + 4; x++) {
                            if (baseY + y >= tops[z << 4 | x] + UNDER_GROUND - REGION_UNDER) continue;
                            BlockState s = read ? states[y << 8 | z << 4 | x] : section.getBlockState(x, y, z);
                            Block b = s.getBlock();
                            if (host(b) || kit.deep() && b == Blocks.DEEPSLATE) {
                                int bit = y << 8 | z << 4 | x;
                                if (shells && (keep[bit >> 6] & 1L << (bit & 63)) != 0) continue;
                                section.setBlockState(x, y, z, rock, false);
                                changed++;
                            } else if (b != Blocks.DEEPSLATE && !s.isAir() && ore(s)) {
                                var twin = kit.twins().get(b);
                                if (twin != null) {
                                    section.setBlockState(x, y, z, twin.get().defaultBlockState(), false);
                                    changed++;
                                }
                            }
                        }
                    }
                }
            }
        }
        return changed;
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
                || b instanceof SkinBlocks.Puddle || b instanceof SkinBlocks.Glowcap || b instanceof SkinBlocks.RootCurtain
                || b instanceof SkinBlocks.Drift || b instanceof SkinBlocks.Driftwood || b instanceof SkinBlocks.CaveWeed
                || b == CaveSkin.AMBER_CRYSTAL.get() || b == CaveSkin.GLACIAL_CRYSTAL.get()
                || b == CaveSkin.DUSTY_WEB.get() || b instanceof MultifaceBlock && b != Blocks.GLOW_LICHEN
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
