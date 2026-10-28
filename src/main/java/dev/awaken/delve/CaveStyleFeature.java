package dev.awaken.delve;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Caves styled by the land above (SPEC §34.7): the biome over the chunk's middle picks a {@link CaveStyle}, then
 * {@value #TRIES} tries each find a pocket of cave air between {@value #LOW} and {@value #UNDER} below the ground and
 * dress its roof, floor or a wall. Only bare stone, deepslate, tuff, granite, diorite and andesite are dressed, so
 * lodes, ores and places stay as they are.
 */
public record CaveStyleFeature() implements Feature {
    public static final MapCodec<CaveStyleFeature> CODEC = MapCodec.unit(CaveStyleFeature::new);
    static final int TRIES = 20, LOW = -56, UNDER = 10, REACH = 10;

    @Override
    public MapCodec<CaveStyleFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource r, BlockPos o) {
        if (level.getLevel().dimension() != Level.OVERWORLD) return false;
        int x0 = o.getX() & ~15, z0 = o.getZ() & ~15;
        int ground = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x0 + 8, z0 + 8);
        CaveStyle style = CaveStyle.of(level.getBiome(new BlockPos(x0 + 8, ground, z0 + 8)));
        if (style == CaveStyle.NONE) return false;
        if ((style == CaveStyle.SUNSHARD || style == CaveStyle.FROSTSPAR || style == CaveStyle.FAULT_SEAM) && style.crystal() == Blocks.AIR) return false;
        int top = ground - UNDER;
        if (top <= LOW) return false;
        boolean any = false;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int t = 0; t < TRIES; t++) {
            int x = x0 + 2 + r.nextInt(12), z = z0 + 2 + r.nextInt(12), y = LOW + r.nextInt(top - LOW);
            if (!level.getBlockState(m.set(x, y, z)).isAir()) continue;
            any |= dress(level, style, r, m, x, y, z);
        }
        return any;
    }

    /** Dresses the pocket of air at (x, y, z) in the style: its roof, its floor, or a wall. */
    static boolean dress(WorldGenLevel level, CaveStyle style, RandomSource r, BlockPos.MutableBlockPos m, int x, int y, int z) {
        int roof = y, floor = y;
        while (roof < y + REACH && level.getBlockState(m.set(x, roof + 1, z)).isAir()) roof++;
        while (floor > y - REACH && level.getBlockState(m.set(x, floor - 1, z)).isAir()) floor--;
        boolean hasRoof = bare(level.getBlockState(m.set(x, roof + 1, z))), hasFloor = bare(level.getBlockState(m.set(x, floor - 1, z)));
        if (!hasRoof && !hasFloor) return false;
        return switch (style) {
            case ROOTS -> roots(level, r, m, x, z, roof, floor, hasRoof, hasFloor);
            case SUNSHARD, FROSTSPAR, FAULT_SEAM -> crystal(level, style, r, m, x, z, roof, floor, hasFloor);
            case SAND -> sand(level, r, m, x, z, roof, floor, hasRoof, hasFloor);
            case ICE -> ice(level, r, m, x, z, roof, floor, hasRoof, hasFloor);
            case NONE -> false;
        };
    }

    /** Plain rock we may dress (a lode, an ore, a place's dressed stone or a build is left alone). */
    static boolean bare(BlockState s) {
        return s.is(Blocks.STONE) || s.is(Blocks.DEEPSLATE) || s.is(Blocks.TUFF) || s.is(Blocks.GRANITE) || s.is(Blocks.DIORITE)
                || s.is(Blocks.ANDESITE) || s.is(Blocks.CALCITE);
    }

    private static void set(WorldGenLevel level, BlockPos.MutableBlockPos m, int x, int y, int z, BlockState s) {
        level.setBlock(m.set(x, y, z), s, Block.UPDATE_CLIENTS);
    }

    private static boolean air(WorldGenLevel level, BlockPos.MutableBlockPos m, int x, int y, int z) {
        return level.getBlockState(m.set(x, y, z)).isAir();
    }

    /** Patches of a block over the bare rock round (x, y, z) in a rough disc of radius rad (the floor or roof). */
    private static void patch(WorldGenLevel level, RandomSource r, BlockPos.MutableBlockPos m, int x, int y, int z, int rad, BlockState s, int open) {
        for (int dx = -rad; dx <= rad; dx++) for (int dz = -rad; dz <= rad; dz++) {
            if (dx * dx + dz * dz > rad * rad + r.nextInt(3)) continue;
            if (!bare(level.getBlockState(m.set(x + dx, y, z + dz))) || !air(level, m, x + dx, y + open, z + dz)) continue;
            set(level, m, x + dx, y, z + dz, s);
        }
    }

    // ---- under the woods: the trees' roots through the roof, moss below, glow berries hanging -----------------------

    private static boolean roots(WorldGenLevel level, RandomSource r, BlockPos.MutableBlockPos m, int x, int z, int roof, int floor, boolean hasRoof, boolean hasFloor) {
        if (hasRoof) {
            patch(level, r, m, x, roof + 1, z, 2, Blocks.ROOTED_DIRT.defaultBlockState(), -1);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                if (r.nextFloat() < 0.55F && level.getBlockState(m.set(x + dx, roof + 1, z + dz)).is(Blocks.ROOTED_DIRT) && air(level, m, x + dx, roof, z + dz))
                    set(level, m, x + dx, roof, z + dz, Blocks.HANGING_ROOTS.defaultBlockState());
            }
            // glow berries: a vine 2-5 long, berries on every other
            if (r.nextFloat() < 0.6F) {
                int len = 2 + r.nextInt(4);
                int vx = x + r.nextInt(3) - 1, vz = z + r.nextInt(3) - 1;
                if (level.getBlockState(m.set(vx, roof + 1, vz)).isSolidRender()) {
                    for (int i = 0; i < len && roof - i > floor + 1 && air(level, m, vx, roof - i, vz); i++) {
                        boolean last = i == len - 1 || roof - i - 1 <= floor + 1;
                        BlockState v = (last ? Blocks.CAVE_VINES : Blocks.CAVE_VINES_PLANT).defaultBlockState().setValue(CaveVines.BERRIES, i % 2 == 1 || last);
                        set(level, m, vx, roof - i, vz, v);
                        if (last) break;
                    }
                }
            }
        }
        if (hasFloor) {
                patch(level, r, m, x, floor - 1, z, 2 + r.nextInt(2), Blocks.MOSS_BLOCK.defaultBlockState(), 1);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                if (r.nextFloat() < 0.3F && level.getBlockState(m.set(x + dx, floor - 1, z + dz)).is(Blocks.MOSS_BLOCK) && air(level, m, x + dx, floor, z + dz))
                    set(level, m, x + dx, floor, z + dz, Blocks.MOSS_CARPET.defaultBlockState());
            }
        }
        return true;
    }

    // ---- under the crystal lands: their crystal grown through the rock -------------------------------------------------

    private static boolean crystal(WorldGenLevel level, CaveStyle style, RandomSource r, BlockPos.MutableBlockPos m, int x, int z, int roof, int floor, boolean hasFloor) {
        BlockState crystal = style.crystal().defaultBlockState(), cluster = style.cluster().defaultBlockState();
        if (style == CaveStyle.FAULT_SEAM || !hasFloor) {
            // a seam in the nearest wall at chest height: four to seven blocks of it, a crack through the rock
            int y = Math.min(roof, floor + 1);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                int wx = x, wz = z, i = 0;
                while (i < 6 && air(level, m, wx + d.getStepX(), y, wz + d.getStepZ())) {
                    wx += d.getStepX();
                    wz += d.getStepZ();
                    i++;
                }
                if (i >= 6 || !bare(level.getBlockState(m.set(wx + d.getStepX(), y, wz + d.getStepZ())))) continue;
                int len = 4 + r.nextInt(4);
                Direction along = d.getClockWise();
                int sy = y;
                for (int k = -len / 2; k <= len / 2; k++) {
                    int cx = wx + d.getStepX() + along.getStepX() * k, cz = wz + d.getStepZ() + along.getStepZ() * k;
                    if (r.nextFloat() < 0.3F) sy += r.nextBoolean() ? 1 : -1;
                    if (bare(level.getBlockState(m.set(cx, sy, cz)))) set(level, m, cx, sy, cz, crystal);
                }
                return true;
            }
            return false;
        }
        // a growth on the floor: a crystal base in the rock, one to three standing on it, its cluster on top
        set(level, m, x, floor - 1, z, crystal);
        int tall = 1 + r.nextInt(3);
        int h = 0;
        while (h < tall && floor + h < roof - 1 && air(level, m, x, floor + h, z)) {
            set(level, m, x, floor + h, z, crystal);
            h++;
        }
        if (cluster.getBlock() != Blocks.AIR && air(level, m, x, floor + h, z) && floor + h < roof) set(level, m, x, floor + h, z, cluster);
        // a few small ones round it
        for (int k = 0; k < 4; k++) {
            int cx = x + r.nextInt(5) - 2, cz = z + r.nextInt(5) - 2;
            int fy = floor;
            while (fy > floor - 3 && air(level, m, cx, fy - 1, cz)) fy--;
            if (!air(level, m, cx, fy, cz) || !bare(level.getBlockState(m.set(cx, fy - 1, cz)))) continue;
            set(level, m, cx, fy - 1, cz, crystal);
            if (cluster.getBlock() != Blocks.AIR) set(level, m, cx, fy, cz, cluster);
        }
        return true;
    }

    // ---- under deserts: sandstone, the sand's colour in the rock ----------------------------------------------------

    private static boolean sand(WorldGenLevel level, RandomSource r, BlockPos.MutableBlockPos m, int x, int z, int roof, int floor, boolean hasRoof, boolean hasFloor) {
        if (hasFloor) {
            patch(level, r, m, x, floor - 1, z, 3, Blocks.SMOOTH_SANDSTONE.defaultBlockState(), 1);
            patch(level, r, m, x, floor - 1, z, 1, Blocks.SANDSTONE.defaultBlockState(), 1);
        }
        if (hasRoof) patch(level, r, m, x, roof + 1, z, 2, Blocks.SANDSTONE.defaultBlockState(), -1);
        // bands in the walls: a ring of sandstone at one height round the pocket
        int band = floor + 1 + r.nextInt(Math.max(1, roof - floor));
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int i = 1; i <= 5; i++) {
                int cx = x + d.getStepX() * i, cz = z + d.getStepZ() * i;
                BlockState s = level.getBlockState(m.set(cx, band, cz));
                if (s.isAir()) continue;
                if (bare(s)) {
                    set(level, m, cx, band, cz, Blocks.CUT_SANDSTONE.defaultBlockState());
                    if (bare(level.getBlockState(m.set(cx, band + 1, cz)))) set(level, m, cx, band + 1, cz, Blocks.SANDSTONE.defaultBlockState());
                }
                break;
            }
        }
        return true;
    }

    // ---- under snow: ice in the floor and the roof, blue ice in the seams ----------------------------------------------

    private static boolean ice(WorldGenLevel level, RandomSource r, BlockPos.MutableBlockPos m, int x, int z, int roof, int floor, boolean hasRoof, boolean hasFloor) {
        if (hasFloor) {
            patch(level, r, m, x, floor - 1, z, 2 + r.nextInt(2), Blocks.PACKED_ICE.defaultBlockState(), 1);
            if (r.nextFloat() < 0.4F && level.getBlockState(m.set(x, floor - 1, z)).is(Blocks.PACKED_ICE)) set(level, m, x, floor - 1, z, Blocks.BLUE_ICE.defaultBlockState());
        }
        if (hasRoof) {
            patch(level, r, m, x, roof + 1, z, 2, Blocks.PACKED_ICE.defaultBlockState(), -1);
            // ice hanging from the roof, one or two long
            for (int k = 0; k < 3; k++) {
                int cx = x + r.nextInt(5) - 2, cz = z + r.nextInt(5) - 2;
                if (!level.getBlockState(m.set(cx, roof + 1, cz)).is(Blocks.PACKED_ICE)) continue;
                int len = 1 + r.nextInt(2);
                for (int i = 0; i < len && roof - i > floor + 2 && air(level, m, cx, roof - i, cz); i++) set(level, m, cx, roof - i, cz, Blocks.ICE.defaultBlockState());
            }
        }
        return true;
    }
}
