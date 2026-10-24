package dev.awaken.opening;

import dev.awaken.Awaken;
import dev.awaken.city.CityPlan;
import dev.awaken.core.Later;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The opening's stage in the standing city (SPEC §16.4), built once per world after the city and versioned: the
 * Association's ground floor fitted out (the infirmary west of the great door, the atrium, the Registry east of it, which
 * M1 left as solid cores) and the run's furniture from {@code routes.json} (the handholds, planks and beams the routes
 * need where the grammar's roofs don't give them). Also the Bell Bridge's fall in Act 2: world state, it stays down.
 */
final class OpeningStage {
    private OpeningStage() {}

    /** Bump when the stage changes: a world built with an older stage gets the new pieces (never the old ones twice). */
    static final int VERSION = 1;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /** Builds what this world's stage is missing (its chunks are loaded on the way: the city is pregenerated). */
    static void ensure(ServerLevel level, OpeningPlaces at) {
        OpeningWorld w = OpeningWorld.get(level.getServer());
        if (w.stage >= VERSION) return;
        long t0 = System.nanoTime();
        int n = association(level, at);
        n += OpeningRoutes.get().buildStage(level, at);
        w.stage = VERSION;
        w.setDirty();
        Awaken.LOG.info("[opening] stage v{} built: {} blocks in {} ms", VERSION, n, (System.nanoTime() - t0) / 1_000_000);
    }

    static int set(ServerLevel level, OpeningPlaces at, int px, int ry, int pz, BlockState s) {
        BlockPos p = at.at(px, ry, pz);
        level.getChunk(p.getX() >> 4, p.getZ() >> 4);
        level.setBlock(p, s, FLAGS);
        return 1;
    }

    // ---- the Association's ground floor ---------------------------------------------------------------------------

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    static int association(ServerLevel level, OpeningPlaces at) {
        int g = OpeningPlaces.TERRACE, n = 0;
        int z0 = OpeningPlaces.HALL_Z0, z1 = OpeningPlaces.HALL_Z1;
        // the three rooms: the infirmary (west), the atrium (the great door's hall), the Registry (east)
        n += room(level, at, OpeningPlaces.INF_X0 + 1, z0 + 1, OpeningPlaces.INF_X1 - 1, z1 - 1, g, 7,
                S("birch_planks"), S("pale_oak_planks"));
        n += room(level, at, OpeningPlaces.ATRIUM_X0 + 1, z0 + 1, OpeningPlaces.ATRIUM_X1 - 1, z1 - 1, g, 10,
                S("polished_diorite"), S("smooth_quartz"));
        n += room(level, at, OpeningPlaces.REG_X0 + 1, z0 + 1, OpeningPlaces.REG_X1 - 1, z1 - 1, g, 7,
                S("polished_deepslate"), S("dark_oak_planks"));
        // doorways through the double walls (each hall keeps its own shell), three high
        for (int z = OpeningPlaces.DOOR_Z0; z <= OpeningPlaces.DOOR_Z1; z++) {
            for (int ry = g + 1; ry <= g + 3; ry++) {
                n += set(level, at, OpeningPlaces.INF_X1, ry, z, AIR) + set(level, at, OpeningPlaces.ATRIUM_X0, ry, z, AIR);
                n += set(level, at, OpeningPlaces.ATRIUM_X1, ry, z, AIR) + set(level, at, OpeningPlaces.REG_X0, ry, z, AIR);
            }
            n += set(level, at, OpeningPlaces.INF_X1, g + 4, z, S("chiseled_stone_bricks"));
            n += set(level, at, OpeningPlaces.REG_X0, g + 4, z, S("chiseled_stone_bricks"));
        }
        n += infirmary(level, at, g);
        n += atrium(level, at, g);
        n += registry(level, at, g);
        return n;
    }

    /** Hollows a room: a floor, air, a ceiling with dark beams every third row. */
    private static int room(ServerLevel level, OpeningPlaces at, int x0, int z0, int x1, int z1, int g, int height, BlockState floor, BlockState ceiling) {
        int n = 0;
        BlockState beam = S("dark_oak_planks");
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                n += set(level, at, x, g, z, floor);
                for (int ry = g + 1; ry <= g + height; ry++) n += set(level, at, x, ry, z, AIR);
                n += set(level, at, x, g + height + 1, z, (x - x0) % 3 == 1 ? beam : ceiling);
            }
        }
        return n;
    }

    private static BlockState S(String spec) {
        return StageBlocks.of(spec);
    }

    private static BlockState bed(Direction toHead, BedPart part) {
        return S("white_bed[facing=" + toHead.getSerializedName() + ",part=" + part.getSerializedName() + "]");
    }

    private static BlockState candles(int count) {
        return S("white_candle[candles=" + count + ",lit=true]");
    }

    private static BlockState hanging() {
        return S("lantern[hanging=true]");
    }

    /** White linen, bells far off: eight beds (north and south walls), bedside tables, lamps, a washstand. */
    private static int infirmary(ServerLevel level, OpeningPlaces at, int g) {
        int n = 0;
        int zN = OpeningPlaces.HALL_Z0 + 1, zS = OpeningPlaces.HALL_Z1 - 1;
        for (int i = 0; i < 4; i++) {
            int x = OpeningPlaces.BED_X[i];
            n += set(level, at, x, g + 1, zN, bed(Direction.NORTH, BedPart.HEAD));
            n += set(level, at, x, g + 1, zN + 1, bed(Direction.NORTH, BedPart.FOOT));
            n += set(level, at, x, g + 1, zS, bed(Direction.SOUTH, BedPart.HEAD));
            n += set(level, at, x, g + 1, zS - 1, bed(Direction.SOUTH, BedPart.FOOT));
            // a bedside table with a candle west of each bed, a linen screen east of it
            n += set(level, at, x - 1, g + 1, zN, S("spruce_planks"));
            n += set(level, at, x - 1, g + 2, zN, candles(1 + i % 2));
            n += set(level, at, x - 1, g + 1, zS, S("spruce_planks"));
            n += set(level, at, x - 1, g + 2, zS, candles(1 + (i + 1) % 2));
            if (i < 3) {
                for (int ry = g + 1; ry <= g + 3; ry++) {
                    n += set(level, at, x + 2, ry, zN, S("white_wool"));
                    n += set(level, at, x + 2, ry, zS, S("white_wool"));
                }
            }
            // a lamp over each pair of beds
            n += set(level, at, x, g + 7, (zN + zS) / 2 - 2, S("iron_chain[axis=y]"));
            n += set(level, at, x, g + 6, (zN + zS) / 2 - 2, hanging());
            n += set(level, at, x, g + 7, (zN + zS) / 2 + 2, S("iron_chain[axis=y]"));
            n += set(level, at, x, g + 6, (zN + zS) / 2 + 2, hanging());
        }
        // a white runner down the middle toward the doorway
        for (int x = OpeningPlaces.INF_X0 + 2; x <= OpeningPlaces.INF_X1 - 1; x++) {
            n += set(level, at, x, g + 1, OpeningPlaces.DOOR_Z0, S("white_carpet"));
            n += set(level, at, x, g + 1, OpeningPlaces.DOOR_Z1, S("white_carpet"));
        }
        // the healer's washstand and shelf on the west wall
        int xw = OpeningPlaces.INF_X0 + 1;
        n += set(level, at, xw, g + 1, OpeningPlaces.DOOR_Z0 - 1, S("water_cauldron[level=3]"));
        n += set(level, at, xw, g + 1, OpeningPlaces.DOOR_Z0, S("brewing_stand"));
        n += set(level, at, xw, g + 1, OpeningPlaces.DOOR_Z1, S("spruce_planks"));
        n += set(level, at, xw, g + 2, OpeningPlaces.DOOR_Z1, S("potted_lily_of_the_valley"));
        n += set(level, at, xw, g + 1, OpeningPlaces.DOOR_Z1 + 1, S("spruce_planks"));
        n += set(level, at, xw, g + 2, OpeningPlaces.DOOR_Z1 + 1, candles(3));
        return n;
    }

    /** The great door's hall: four columns, a bronze compass in the floor, a chandelier, the Association's banners. */
    private static int atrium(ServerLevel level, OpeningPlaces at, int g) {
        int n = 0;
        int cx0 = OpeningPlaces.ATRIUM_X0, cx1 = OpeningPlaces.ATRIUM_X1;
        int mx = (cx0 + cx1) / 2, mz = (OpeningPlaces.HALL_Z0 + OpeningPlaces.HALL_Z1) / 2;
        for (int dx : new int[]{-3, 4}) {
            for (int dz : new int[]{-3, 4}) {
                for (int ry = g + 1; ry <= g + 10; ry++) n += set(level, at, mx + dx, ry, mz + dz, S("polished_diorite"));
                n += set(level, at, mx + dx, g + 10, mz + dz, S("chiseled_quartz_block"));
            }
        }
        // the compass: bronze cross and ring
        for (int d = -2; d <= 3; d++) {
            n += set(level, at, mx + d, g, mz, S("waxed_exposed_cut_copper"));
            n += set(level, at, mx, g, mz + d, S("waxed_exposed_cut_copper"));
        }
        n += set(level, at, mx, g, mz, S("waxed_copper_block"));
        // the chandelier
        for (int ry = g + 8; ry <= g + 10; ry++) n += set(level, at, mx, ry, mz, S("iron_chain[axis=y]"));
        n += set(level, at, mx, g + 7, mz, hanging());
        for (int[] o : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            n += set(level, at, mx + o[0], g + 8, mz + o[1], S("iron_chain[axis=y]"));
            n += set(level, at, mx + o[0], g + 7, mz + o[1], hanging());
        }
        // banners either side of the inner doorways (blue and white: the Association)
        for (int z : new int[]{OpeningPlaces.DOOR_Z0 - 2, OpeningPlaces.DOOR_Z1 + 2}) {
            n += set(level, at, cx0 + 1, g + 4, z, S("blue_wall_banner[facing=east]"));
            n += set(level, at, cx1 - 1, g + 4, z, S("blue_wall_banner[facing=west]"));
        }
        return n;
    }

    /** The Registry: the Assessment Lens on its pedestal, the clerk's counter, shelves of ledgers, a lectern. */
    private static int registry(ServerLevel level, OpeningPlaces at, int g) {
        int n = 0;
        int lx = OpeningPlaces.LENS_X, lz = OpeningPlaces.LENS_Z;
        // the pedestal: a dark base, a quartz drum, a bronze rim; the shard floats over it (the client draws it)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                n += set(level, at, lx + dx, g + 1, lz + dz, dx == 0 && dz == 0 ? S("chiseled_quartz_block")
                        : S("polished_deepslate_slab[type=bottom]"));
            }
        }
        n += set(level, at, lx, g + 2, lz, S("waxed_exposed_cut_copper_slab[type=bottom]"));
        n += set(level, at, lx, g + 6, lz, S("light[level=12]"));
        // a ring of bronze in the floor where you stand to be read
        Vec3 mark = at.lensMark();
        int mx = (int) Math.floor(mark.x) - at.cx, mz = (int) Math.floor(mark.z) - at.cz;
        n += set(level, at, mx, g, mz, S("waxed_copper_block"));
        // the clerk's counter along the east of the room, a lectern with the register on it
        for (int z = lz - 2; z <= lz + 4; z++) n += set(level, at, OpeningPlaces.REG_X1 - 3, g + 1, z, S("spruce_planks"));
        n += set(level, at, OpeningPlaces.REG_X1 - 3, g + 2, lz + 1, S("lectern[facing=west]"));
        n += set(level, at, OpeningPlaces.REG_X1 - 3, g + 2, lz - 1, candles(2));
        // ledgers on the east wall
        for (int z = OpeningPlaces.HALL_Z0 + 1; z <= OpeningPlaces.HALL_Z1 - 1; z++) {
            for (int ry = g + 1; ry <= g + 4; ry++) n += set(level, at, OpeningPlaces.REG_X1 - 1, ry, z, S("bookshelf"));
        }
        // lamps
        for (int z : new int[]{lz - 1, lz + 6}) {
            for (int x : new int[]{OpeningPlaces.REG_X0 + 3, OpeningPlaces.REG_X1 - 5}) {
                n += set(level, at, x, g + 7, z, S("iron_chain[axis=y]"));
                n += set(level, at, x, g + 6, z, hanging());
            }
        }
        return n;
    }

    // ---- Act 2: the Bell Bridge goes into the river -----------------------------------------------------------------

    /** The bridge cracks in the middle, its bell rings once as it drops, then the spans go outward into the river. */
    static void dropBridge(ServerLevel level, OpeningPlaces at) {
        CityPlan.Bridge b = at.bellBridgeBox();
        if (b == null) return;
        int len = b.x1() - b.x0() + 1;
        int mid = (b.x0() + b.x1()) / 2;
        Vec3 c = at.bellBridge();
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 6F, 0.55F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
        // spans fall from the middle outward, a column pair every two ticks
        for (int k = 0; k <= len / 2 + 1; k++) {
            int dx = k;
            Later.run(level.getServer(), 4 + k * 2, () -> {
                fallColumn(level, at, b, mid - dx);
                if (dx > 0) fallColumn(level, at, b, mid + dx);
            });
        }
        Later.run(level.getServer(), 4 + len + 10, () -> level.playSound(null, c.x, c.y - 4, c.z, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 5F, 0.6F));
    }

    /** One slice of the bridge across its width: what stands above the water drops (it lands as rubble on the bed). */
    private static void fallColumn(ServerLevel level, OpeningPlaces at, CityPlan.Bridge b, int px) {
        if (px < b.x0() || px > b.x1()) return;
        for (int pz = b.z0(); pz <= b.z1(); pz++) {
            // only over the water: the quays at either end stay
            byte k = at.plan().kind(px, pz);
            if (!CityPlan.water(k)) continue;
            for (int ry = 8; ry >= -8; ry--) {
                BlockPos p = at.at(px, ry, pz);
                BlockState s = level.getBlockState(p);
                if (s.isAir() || !s.getFluidState().isEmpty() && s.getBlock() == Blocks.WATER) continue;
                if (ry < -1) {
                    // the arch under the water line: gone (it was the river's before)
                    level.setBlock(p, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
                    continue;
                }
                if (s.hasBlockEntity() || s.is(Blocks.BELL)) {
                    level.setBlock(p, AIR, Block.UPDATE_ALL);
                    continue;
                }
                FallingBlockEntity f = FallingBlockEntity.fall(level, p, s);
                f.dropItem = false;
                f.setDeltaMovement((level.getRandom().nextDouble() - 0.5) * 0.12, -0.05, (level.getRandom().nextDouble() - 0.5) * 0.12);
                if (level.getRandom().nextInt(3) == 0) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.4, 0.3, 0.4, 0.05);
                }
            }
            if (level.getRandom().nextInt(2) == 0) {
                BlockPos w = at.at(px, -1, pz);
                level.sendParticles(ParticleTypes.CLOUD, w.getX() + 0.5, w.getY() + 1, w.getZ() + 0.5, 3, 0.5, 0.4, 0.5, 0.02);
            }
        }
        BlockPos m = at.at(px, 0, (b.z0() + b.z1()) / 2);
        level.playSound(null, m, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 2.5F, 0.6F + level.getRandom().nextFloat() * 0.2F);
    }

    /** A list of stage blocks, plan coordinates (from routes.json). */
    record Piece(int x, int ry, int z, BlockState state) {}

    static int place(ServerLevel level, OpeningPlaces at, List<Piece> pieces) {
        int n = 0;
        for (Piece p : pieces) n += set(level, at, p.x(), p.ry(), p.z(), p.state());
        return n;
    }

    static List<Piece> none() {
        return new ArrayList<>();
    }
}
