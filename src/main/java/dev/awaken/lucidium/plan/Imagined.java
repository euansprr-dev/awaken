package dev.awaken.lucidium.plan;

import dev.awaken.lucidium.LucidiumContent;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

import java.util.List;

/**
 * Authored places (16x12x16, floor at y = 3, feet at y = 4) for dreams with a thin journal, plus the nightmare
 * arenas, the mentor's ring and the door of intention. Deterministic from their seed.
 */
public final class Imagined {
    private Imagined() {}

    public static final int SX = 16, SY = 12, SZ = 16, FLOOR = 3;

    public static final List<String> PLACES = List.of("stair_to_nowhere", "lone_room", "glass_garden", "pillars",
            "fallen_moon", "tall_library", "endless_corridor");

    public static String label(String name) {
        return switch (name) {
            case "stair_to_nowhere" -> "a stair that climbs to nowhere";
            case "lone_room" -> "a room you have never slept in";
            case "glass_garden" -> "a garden of glass";
            case "pillars" -> "pillars that do not touch the ground";
            case "fallen_moon" -> "a moon that fell into a field";
            case "tall_library" -> "a library taller than the sky";
            case "endless_corridor" -> "a corridor that should end";
            default -> "a place you have never been";
        };
    }

    public static Fragment place(String name, long seed) {
        RandomSource r = new XoroshiroRandomSource(seed ^ name.hashCode());
        Fragment.Builder b = ground(Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState());
        switch (name) {
            case "stair_to_nowhere" -> {
                BlockState step = Blocks.STONE_BRICKS.defaultBlockState();
                BlockState slab = Blocks.STONE_BRICK_SLAB.defaultBlockState();
                int[][] ring = {{7, 5}, {8, 5}, {9, 6}, {10, 7}, {10, 8}, {9, 9}, {8, 10}, {7, 10}, {6, 9}, {5, 8}, {5, 7}, {6, 6}};
                for (int i = 0; i < 16; i++) {
                    int[] c = ring[i % ring.length];
                    int y = FLOOR + 1 + i / 2;
                    if (y >= SY) break;
                    b.set(c[0], y, c[1], i % 2 == 0 ? slab : step);
                }
                b.set(7, SY - 1, 7, Blocks.LANTERN.defaultBlockState());
                flowers(b, r, 6);
            }
            case "lone_room" -> {
                BlockState wall = Blocks.OAK_PLANKS.defaultBlockState();
                BlockState log = Blocks.OAK_LOG.defaultBlockState();
                b.fill(4, FLOOR, 4, 11, FLOOR, 11, Blocks.SPRUCE_PLANKS.defaultBlockState());
                for (int y = FLOOR + 1; y <= FLOOR + 4; y++) {
                    for (int i = 4; i <= 11; i++) {
                        BlockState w = (i == 4 || i == 11) ? log : wall;
                        b.set(i, y, 4, w);
                        b.set(i, y, 11, w);
                        b.set(4, y, i, w);
                        b.set(11, y, i, w);
                    }
                }
                b.fill(4, FLOOR + 5, 4, 11, FLOOR + 5, 11, Blocks.DARK_OAK_PLANKS.defaultBlockState());
                b.set(7, FLOOR + 2, 4, Blocks.GLASS_PANE.defaultBlockState());
                b.set(8, FLOOR + 2, 4, Blocks.GLASS_PANE.defaultBlockState());
                door(b, 11, FLOOR + 1, 8, Direction.EAST, Blocks.OAK_DOOR);
                BlockState foot = Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT);
                b.set(6, FLOOR + 1, 9, foot);
                b.set(6, FLOOR + 1, 8, foot.setValue(BedBlock.PART, BedPart.HEAD));
                b.set(9, FLOOR + 1, 9, Blocks.OAK_FENCE.defaultBlockState());
                b.set(9, FLOOR + 2, 9, Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.LIT, true));
                b.set(9, FLOOR + 1, 6, Blocks.BOOKSHELF.defaultBlockState());
            }
            case "glass_garden" -> {
                b = ground(Blocks.MOSS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState());
                BlockState glass = LucidiumContent.DREAM_GLASS.get().defaultBlockState();
                for (int k = 0; k < 6; k++) {
                    int x = 2 + r.nextInt(12), z = 2 + r.nextInt(12), h = 2 + r.nextInt(6);
                    for (int y = FLOOR + 1; y <= FLOOR + h && y < SY; y++) b.set(x, y, z, glass);
                    if (FLOOR + h + 1 < SY) b.set(x, FLOOR + h + 1, z, Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.PINK).defaultBlockState());
                }
                flowers(b, r, 14);
            }
            case "pillars" -> {
                BlockState q = Blocks.QUARTZ_PILLAR.defaultBlockState();
                for (int k = 0; k < 8; k++) {
                    double a = k * Math.PI / 4;
                    int x = 7 + (int) Math.round(Math.cos(a) * 5), z = 7 + (int) Math.round(Math.sin(a) * 5);
                    int gap = 1 + r.nextInt(2);
                    for (int y = FLOOR + 1 + gap; y < SY - r.nextInt(3); y++) b.set(x, y, z, q);
                }
                b.fill(6, FLOOR, 6, 9, FLOOR, 9, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
            }
            case "fallen_moon" -> {
                BlockState moon = Blocks.END_STONE.defaultBlockState();
                for (int y = FLOOR - 2; y < SY; y++)
                    for (int z = 0; z < SZ; z++)
                        for (int x = 0; x < SX; x++) {
                            double d = Math.sqrt((x - 7.5) * (x - 7.5) + (y - 4.5) * (y - 4.5) * 1.2 + (z - 7.5) * (z - 7.5));
                            if (d < 5.2) b.set(x, y, z, (Fragment.hash(seed, x, y, z) & 7) == 0 ? Blocks.SEA_LANTERN.defaultBlockState() : moon);
                        }
            }
            case "tall_library" -> {
                b.fill(2, FLOOR, 2, 13, FLOOR, 13, Blocks.DARK_OAK_PLANKS.defaultBlockState());
                for (int row = 3; row <= 12; row += 3)
                    for (int y = FLOOR + 1; y < SY; y++)
                        for (int x = 3; x <= 12; x++) {
                            if (y > FLOOR + 6 && (x + y) % 3 == 0) continue;
                            b.set(x, y, row, Blocks.BOOKSHELF.defaultBlockState());
                        }
                b.set(7, FLOOR + 1, 4, Blocks.LECTERN.defaultBlockState());
                b.set(2, FLOOR + 1, 2, Blocks.LANTERN.defaultBlockState());
            }
            default -> { // endless_corridor
                BlockState wall = Blocks.STONE_BRICKS.defaultBlockState();
                for (int z = 0; z < SZ; z++) {
                    b.set(5, FLOOR, z, wall);
                    b.set(10, FLOOR, z, wall);
                    for (int y = FLOOR + 1; y <= FLOOR + 5; y++) {
                        b.set(5, y, z, wall);
                        b.set(10, y, z, wall);
                    }
                    b.fill(6, FLOOR + 6, z, 9, FLOOR + 6, z, Blocks.STONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
                    if (z % 4 == 1) {
                        door(b, 5, FLOOR + 1, z, Direction.WEST, Blocks.SPRUCE_DOOR);
                        door(b, 10, FLOOR + 1, z, Direction.EAST, Blocks.SPRUCE_DOOR);
                    }
                    if (z % 4 == 3) b.set(7, FLOOR + 5, z, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
                }
            }
        }
        return b.build();
    }

    /** Arena for a nightmare family. The falling arena has almost no ground on purpose. */
    public static Fragment nightmare(Nightmares.Family family, long seed) {
        RandomSource r = new XoroshiroRandomSource(seed ^ family.ordinal() * 7919L);
        Fragment.Builder b;
        switch (family) {
            case FALLING -> {
                b = new Fragment.Builder(SX, SY, SZ);
                BlockState stone = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
                b.fill(6, FLOOR, 6, 9, FLOOR, 9, Blocks.STONE_BRICKS.defaultBlockState());
                for (int k = 0; k < 22; k++) {
                    int x = r.nextInt(SX), z = r.nextInt(SZ);
                    b.set(x, FLOOR, z, stone);
                    if (r.nextInt(3) == 0) b.set(x + 1, FLOOR, z, stone);
                }
            }
            case BURNING -> {
                b = ground(Blocks.SOUL_SOIL.defaultBlockState(), Blocks.NETHERRACK.defaultBlockState());
                for (int k = 0; k < 30; k++) {
                    int x = r.nextInt(SX), z = r.nextInt(SZ);
                    b.set(x, FLOOR, z, Blocks.MAGMA_BLOCK.defaultBlockState());
                }
                for (int k = 0; k < 18; k++) {
                    int x = r.nextInt(SX), z = r.nextInt(SZ);
                    if (b.get(x, FLOOR, z) != null && b.get(x, FLOOR, z).is(Blocks.SOUL_SOIL)) b.set(x, FLOOR + 1, z, Blocks.SOUL_FIRE.defaultBlockState());
                }
                for (int k = 0; k < 5; k++) {
                    int x = 1 + r.nextInt(14), z = 1 + r.nextInt(14), h = 2 + r.nextInt(4);
                    for (int y = FLOOR + 1; y <= FLOOR + h; y++) b.set(x, y, z, Blocks.BASALT.defaultBlockState());
                }
            }
            case DROWNING -> {
                b = ground(Blocks.SAND.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState());
                BlockState glass = Blocks.GLASS.defaultBlockState();
                for (int y = FLOOR + 1; y <= FLOOR + 4; y++)
                    for (int i = 1; i <= 14; i++) {
                        b.set(i, y, 1, glass);
                        b.set(i, y, 14, glass);
                        b.set(1, y, i, glass);
                        b.set(14, y, i, glass);
                    }
                b.fill(2, FLOOR + 1, 2, 13, FLOOR + 3, 13, Blocks.WATER.defaultBlockState());
                for (int k = 0; k < 10; k++) b.set(2 + r.nextInt(12), FLOOR + 1, 2 + r.nextInt(12), Blocks.SEAGRASS.defaultBlockState());
                // a way in: steps over the rim
                b.set(7, FLOOR + 5, 0, Blocks.SANDSTONE_SLAB.defaultBlockState());
            }
            case UNDEAD -> {
                b = ground(Blocks.COARSE_DIRT.defaultBlockState(), Blocks.DIRT.defaultBlockState());
                for (int k = 0; k < 9; k++) {
                    int x = 1 + r.nextInt(14), z = 1 + r.nextInt(14);
                    b.set(x, FLOOR + 1, z, Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState());
                    b.set(x, FLOOR, z + 1, Blocks.SOUL_SOIL.defaultBlockState());
                }
                for (int k = 0; k < 8; k++) b.set(r.nextInt(SX), FLOOR + 1, r.nextInt(SZ), Blocks.COBWEB.defaultBlockState());
                for (int y = FLOOR + 1; y <= FLOOR + 5; y++) {
                    b.set(5, y, 13, Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
                    b.set(10, y, 13, Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
                }
                b.fill(5, FLOOR + 6, 13, 10, FLOOR + 6, 13, Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
                b.set(7, FLOOR + 1, 7, Blocks.SOUL_LANTERN.defaultBlockState());
            }
            case ARTHROPOD -> {
                b = ground(Blocks.MOSS_BLOCK.defaultBlockState(), Blocks.TUFF.defaultBlockState());
                for (int y = FLOOR + 1; y < SY - 2; y++)
                    for (int i = 0; i < SX; i++) {
                        if (r.nextInt(3) == 0) b.set(i, y, 0, Blocks.TUFF.defaultBlockState());
                        if (r.nextInt(3) == 0) b.set(0, y, i, Blocks.TUFF.defaultBlockState());
                    }
                for (int k = 0; k < 26; k++) b.set(r.nextInt(SX), FLOOR + 1 + r.nextInt(6), r.nextInt(SZ), Blocks.COBWEB.defaultBlockState());
            }
            case EXPLOSION -> {
                b = ground(Blocks.GRAVEL.defaultBlockState(), Blocks.STONE.defaultBlockState());
                for (int k = 0; k < 5; k++) {
                    int cx = 2 + r.nextInt(12), cz = 2 + r.nextInt(12), rad = 1 + r.nextInt(2);
                    for (int z = cz - rad; z <= cz + rad; z++)
                        for (int x = cx - rad; x <= cx + rad; x++) {
                            b.set(x, FLOOR, z, Blocks.COAL_BLOCK.defaultBlockState());
                            if (r.nextInt(2) == 0) b.set(x, FLOOR, z, null);
                        }
                }
                for (int k = 0; k < 4; k++) {
                    int x = 1 + r.nextInt(14), z = 1 + r.nextInt(14);
                    b.set(x, FLOOR + 1, z, Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState());
                    b.set(x, FLOOR + 2, z, Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState());
                    b.set(x, FLOOR + 3, z, Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState());
                }
            }
            default -> { // GENERIC
                b = ground(Blocks.POLISHED_BLACKSTONE.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState());
                for (int x = 2; x <= 13; x += 5)
                    for (int z = 2; z <= 13; z += 5)
                        for (int y = FLOOR + 1; y < SY - 1; y++) b.set(x, y, z, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                b.set(7, FLOOR + 1, 7, Blocks.SOUL_LANTERN.defaultBlockState());
            }
        }
        return b.build();
    }

    public static Fragment mentorRing(long seed) {
        Fragment.Builder b = ground(Blocks.SMOOTH_STONE.defaultBlockState(), Blocks.STONE.defaultBlockState());
        for (int z = 0; z < SZ; z++)
            for (int x = 0; x < SX; x++) {
                double d = Math.sqrt((x - 7.5) * (x - 7.5) + (z - 7.5) * (z - 7.5));
                if (d > 6 && d < 7.2) b.set(x, FLOOR, z, Blocks.POLISHED_ANDESITE.defaultBlockState());
                if (d >= 7.5) b.set(x, FLOOR, z, Blocks.GRASS_BLOCK.defaultBlockState());
            }
        int[][] posts = {{2, 2}, {13, 2}, {2, 13}, {13, 13}};
        for (int[] p : posts) {
            b.set(p[0], FLOOR + 1, p[1], Blocks.STONE_BRICK_WALL.defaultBlockState());
            b.set(p[0], FLOOR + 2, p[1], Blocks.STONE_BRICK_WALL.defaultBlockState());
            b.set(p[0], FLOOR + 3, p[1], Blocks.LANTERN.defaultBlockState());
        }
        return b.build();
    }

    /** The door you set out to find: a frame of dream glass on a small platform. */
    public static Fragment lonelyDoor(long seed) {
        Fragment.Builder b = new Fragment.Builder(SX, SY, SZ);
        b.fill(5, FLOOR, 5, 10, FLOOR, 10, LucidiumContent.DREAM_CLOUD.get().defaultBlockState());
        BlockState frame = LucidiumContent.DREAM_GLASS.get().defaultBlockState();
        for (int y = FLOOR + 1; y <= FLOOR + 4; y++) {
            b.set(6, y, 8, frame);
            b.set(9, y, 8, frame);
        }
        b.fill(6, FLOOR + 4, 8, 9, FLOOR + 4, 8, frame);
        door(b, 7, FLOOR + 1, 8, Direction.NORTH, Blocks.CHERRY_DOOR);
        door(b, 8, FLOOR + 1, 8, Direction.NORTH, Blocks.CHERRY_DOOR);
        b.set(6, FLOOR + 5, 8, Blocks.END_ROD.defaultBlockState());
        b.set(9, FLOOR + 5, 8, Blocks.END_ROD.defaultBlockState());
        return b.build();
    }

    // ---- helpers -----------------------------------------------------------------------------------------------------

    private static Fragment.Builder ground(BlockState top, BlockState fill) {
        Fragment.Builder b = new Fragment.Builder(SX, SY, SZ);
        b.fill(0, 0, 0, SX - 1, FLOOR - 1, SZ - 1, fill);
        b.fill(0, FLOOR, 0, SX - 1, FLOOR, SZ - 1, top);
        return b;
    }

    private static void flowers(Fragment.Builder b, RandomSource r, int n) {
        Block[] kinds = {Blocks.POPPY, Blocks.DANDELION, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.ALLIUM};
        for (int k = 0; k < n; k++) {
            int x = r.nextInt(SX), z = r.nextInt(SZ);
            BlockState below = b.get(x, FLOOR, z);
            if (below != null && b.get(x, FLOOR + 1, z) == null) b.set(x, FLOOR + 1, z, kinds[r.nextInt(kinds.length)].defaultBlockState());
        }
    }

    /** An open door (two halves) at x,y,z facing the given way. */
    static void door(Fragment.Builder b, int x, int y, int z, Direction facing, Block door) {
        BlockState lower = door.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.OPEN, true)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        b.set(x, y, z, lower);
        b.set(x, y + 1, z, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }
}
