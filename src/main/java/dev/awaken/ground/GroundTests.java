package dev.awaken.ground;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.body.BodyRules;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.ground.api.GroundTop;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.awaken.ground.cover.PebblesBlock;
import dev.awaken.ground.cover.TwigsBlock;
import net.minecraft.core.Direction;
import dev.awaken.terrain.alpine.AlpineSurface;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** The ground's seam rules (SPEC §31): the masks the client draws from, and the drift's range. */
final class GroundTests {
    private GroundTests() {}

    /**
     * Fresh sides (SPEC §31.1): a dug hole beside a lawn is dirt on every grass side it bared, grows through its stages where
     * lit (and not in the dark), and loses its record when filled; a riser the world was made with wraps from the start; the
     * records save and load.
     */
    static void freshTests() {
        AwakenTests.register("ground_fresh_dig", 60, h -> {
            ServerLevel level = h.getLevel();
            // a lawn: stone, dirt, then grass at y 2, 9 by 5
            for (int x = 0; x < 9; x++) for (int z = 0; z < 5; z++) {
                h.setBlock(x, 0, z, Blocks.STONE);
                h.setBlock(x, 1, z, Blocks.DIRT);
                h.setBlock(x, 2, z, Blocks.GRASS_BLOCK);
                for (int y = 3; y < 6; y++) h.setBlock(x, y, z, Blocks.AIR);
            }
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            BlockPos hole = h.absolutePos(new BlockPos(2, 2, 2)), dark = h.absolutePos(new BlockPos(6, 2, 2));
            h.assertTrue(p.gameMode.destroyBlock(hole), "the mock player digs the lawn");
            // the four grass blocks round the hole: each one's side toward it is fresh (stage 0, dirt), though the shape rule says riser
            for (int i = 0; i < 4; i++) {
                BlockPos g = hole.relative(SeamlessRules.SIDES[i]);
                int side = (i + 2) & 3;
                h.assertTrue(SeamlessRules.sideWraps(level, g, side), "the shape is a riser's at " + g);
                h.assertTrue(GroundFresh.stage(level, g, side) == 0, "a bared side starts as dirt at " + g + ": " + GroundFresh.stage(level, g, side));
                h.assertTrue(GroundFresh.stage(level, g, (side + 1) & 3) == FreshSides.NONE, "its other sides have no record");
            }
            // a second hole, roofed over: no light reaches its sides
            h.assertTrue(p.gameMode.destroyBlock(dark), "the second hole");
            h.setBlock(6, 3, 2, Blocks.STONE);
            AwakenTests.awaitLight(h, new BlockPos(0, 1, 0), new BlockPos(8, 5, 4));
            BlockPos east = hole.east(), darkEast = dark.east();
            h.assertTrue(level.getMaxLocalRawBrightness(hole) >= GroundFresh.LIGHT, "the open hole is lit: " + level.getMaxLocalRawBrightness(hole));
            GroundFresh.grow(level, FreshSides.STAGE_1);
            h.assertTrue(GroundFresh.stage(level, east, 3) == 1, "lit, stage 1: " + GroundFresh.stage(level, east, 3));
            h.assertTrue(GroundFresh.stage(level, darkEast, 3) == 0, "dark, still dirt: " + GroundFresh.stage(level, darkEast, 3));
            GroundFresh.grow(level, FreshSides.STAGE_2 - FreshSides.STAGE_1);
            h.assertTrue(GroundFresh.stage(level, east, 3) == 2, "lit, stage 2: " + GroundFresh.stage(level, east, 3));
            GroundFresh.grow(level, FreshSides.WRAPPED - FreshSides.STAGE_2);
            h.assertTrue(GroundFresh.stage(level, east, 3) == FreshSides.NONE, "grown: no record, it wraps as a natural riser");
            h.assertTrue(GroundFresh.stage(level, darkEast, 3) == 0, "the dark one is still dirt");
            // filling the dark hole drops its records (the placing event), as soon as it is placed
            net.neoforged.neoforge.common.util.BlockSnapshot snap = net.neoforged.neoforge.common.util.BlockSnapshot.create(level.dimension(), level, dark);
            level.setBlock(dark, Blocks.DIRT.defaultBlockState(), 3);
            net.neoforged.neoforge.event.EventHooks.onBlockPlace(p, snap, Direction.UP);
            for (int i = 0; i < 4; i++) {
                BlockPos g = dark.relative(SeamlessRules.SIDES[i]);
                h.assertTrue(GroundFresh.stage(level, g, (i + 2) & 3) == FreshSides.NONE, "filled: no record at " + g);
            }
            // dig it again: fresh once more
            h.assertTrue(p.gameMode.destroyBlock(dark), "dug again");
            h.assertTrue(GroundFresh.stage(level, darkEast, 3) == 0, "dug again: dirt again");
            h.setBlock(6, 2, 2, Blocks.DIRT);
            GroundFresh.grow(level, GroundFresh.CHECK);
            h.assertTrue(GroundFresh.stage(level, darkEast, 3) == FreshSides.NONE, "filled without an event: the next growth drops it");
            h.succeed();
        });

        // A riser the world was made with (no break, no event) wraps on the first frame: no record, the shape rule alone
        AwakenTests.register("ground_fresh_natural", 20, h -> {
            for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
                h.setBlock(x, 0, z, Blocks.DIRT);
                h.setBlock(x, 1, z, Blocks.GRASS_BLOCK);
                h.setBlock(x, 2, z, Blocks.AIR);
            }
            h.setBlock(1, 2, 1, Blocks.GRASS_BLOCK);
            BlockPos g = h.absolutePos(new BlockPos(1, 2, 1));
            h.assertTrue(SeamlessRules.wrapMask(h.getLevel(), g) == 0b1111, "a made step wraps on every side");
            for (int i = 0; i < 4; i++) h.assertTrue(GroundFresh.stage(h.getLevel(), g, i) == FreshSides.NONE, "and none of them is fresh");
            h.succeed();
        });

        // Saved and loaded: the records through their codec, and a key's place (a negative y too) back from its bits
        AwakenTests.register("ground_fresh_saved", 20, h -> {
            FreshSides f = new FreshSides();
            BlockPos a = new BlockPos(-17, -40, 33), b = new BlockPos(30, 250, -1);
            f.sides.put(FreshSides.key(a, 2), 1234);
            f.sides.put(FreshSides.key(b, 0), 0);
            var tag = FreshSides.CODEC.codec().encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, f).getOrThrow();
            FreshSides back = FreshSides.CODEC.codec().parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).getOrThrow();
            h.assertTrue(back.sides.equals(f.sides), "the records come back as they went: " + back.sides + " vs " + f.sides);
            h.assertTrue(FreshSides.pos(net.minecraft.world.level.ChunkPos.containing(a), FreshSides.key(a, 2)).equals(a) && FreshSides.side(FreshSides.key(a, 2)) == 2,
                    "a key gives back its block and side");
            h.assertTrue(FreshSides.pos(net.minecraft.world.level.ChunkPos.containing(b), FreshSides.key(b, 0)).equals(b), "and at the top of the world");
            h.assertTrue(FreshSides.stage(0) == 0 && FreshSides.stage(FreshSides.STAGE_1) == 1 && FreshSides.stage(FreshSides.STAGE_2) == 2
                    && FreshSides.stage(FreshSides.WRAPPED) == FreshSides.NONE, "the stages by lit ticks");
            h.succeed();
        });
    }

    static void register() {
        softTests();
        freshTests();
        floorTests();
        mountainTests();
        // a one-block riser wraps; a two-block riser (a cliff) does not; a side against a solid block is hidden and stays 0;
        // a side against a plant counts as open
        AwakenTests.register("ground_wrap_mask", 20, h -> {
            // low ground one block up (y 1) with dirt under it, 12 wide
            for (int x = 0; x < 12; x++) for (int z = 0; z < 6; z++) {
                h.setBlock(x, 0, z, Blocks.DIRT);
                h.setBlock(x, 1, z, Blocks.GRASS_BLOCK);
            }
            // a step: one grass block on the low ground, its four sides open over ground one lower
            h.setBlock(2, 2, 2, Blocks.GRASS_BLOCK);
            h.assertTrue(SeamlessRules.wrapMask(h.getLevel(), h.absolutePos(new BlockPos(2, 2, 2))) == 0b1111, "a one-block step wraps on every side");
            // a plant beside it counts as open
            h.setBlock(3, 2, 2, Blocks.SHORT_GRASS);
            h.assertTrue(SeamlessRules.wrapMask(h.getLevel(), h.absolutePos(new BlockPos(2, 2, 2))) == 0b1111, "a plant beside it is open");
            // solid beside: that face is hidden, no bit
            h.setBlock(3, 2, 2, Blocks.STONE);
            int m = SeamlessRules.wrapMask(h.getLevel(), h.absolutePos(new BlockPos(2, 2, 2)));
            h.assertTrue(m == 0b1101, "the side against stone (east) stays 0, the rest wrap: " + Integer.toBinaryString(m));
            // a two-block riser: a pillar of two grass blocks, ground two below it
            h.setBlock(8, 2, 3, Blocks.DIRT);
            h.setBlock(8, 3, 3, Blocks.GRASS_BLOCK);
            h.assertTrue(SeamlessRules.wrapMask(h.getLevel(), h.absolutePos(new BlockPos(8, 3, 3))) == 0, "a two-block riser keeps its dirt face");
            // a grass block with something solid on it is not a lawn: nothing wraps
            h.setBlock(2, 3, 2, Blocks.STONE);
            h.assertTrue(SeamlessRules.wrapMask(h.getLevel(), h.absolutePos(new BlockPos(2, 2, 2))) == 0, "a covered block keeps its sides");
            h.setBlock(2, 3, 2, Blocks.AIR);
            // a flat lawn has nothing to wrap
            h.assertTrue(SeamlessRules.wrapMask(h.getLevel(), h.absolutePos(new BlockPos(5, 1, 5))) == 0, "a flat lawn: the tops are open but the ground beside is level, no riser");
            h.succeed();
        });

        // grass creeps over the edges of a path or dirt that meet grass at the same height, with the corner only where just
        // the diagonal is grass
        AwakenTests.register("ground_creep_mask", 20, h -> {
            for (int x = 0; x < 10; x++) for (int z = 0; z < 10; z++) {
                h.setBlock(x, 0, z, Blocks.DIRT);
                h.setBlock(x, 1, z, Blocks.GRASS_BLOCK);
            }
            // a path at (3,1,3) with grass to the east (4,1,3) and south (3,1,4): edges E and S
            h.setBlock(3, 1, 3, Blocks.DIRT_PATH);
            h.setBlock(2, 1, 3, Blocks.DIRT);
            h.setBlock(3, 1, 2, Blocks.DIRT);
            h.setBlock(2, 1, 2, Blocks.DIRT);
            int m = SeamlessRules.creepMask(h.getLevel(), h.absolutePos(new BlockPos(3, 1, 3)));
            h.assertTrue(m == 0b0110, "east and south edges, no corner between them: " + Integer.toBinaryString(m));
            // a dirt block whose only grass is on the diagonal (south-east): a corner
            for (int x = 4; x <= 7; x++) for (int z = 4; z <= 7; z++) h.setBlock(x, 1, z, Blocks.COARSE_DIRT);
            h.setBlock(7, 1, 7, Blocks.GRASS_BLOCK);
            m = SeamlessRules.creepMask(h.getLevel(), h.absolutePos(new BlockPos(6, 1, 6)));
            h.assertTrue(m == 1 << (4 + 1), "south-east corner only: " + Integer.toBinaryString(m));
            // a block under a roof shows no top, so nothing creeps
            h.setBlock(3, 2, 3, Blocks.STONE);
            h.assertTrue(SeamlessRules.creepMask(h.getLevel(), h.absolutePos(new BlockPos(3, 1, 3))) == 0, "a covered top has no creep");
            // snow on the grass: no creep from it
            h.setBlock(3, 2, 3, Blocks.AIR);
            h.setBlock(4, 1, 3, Blocks.GRASS_BLOCK.defaultBlockState().setValue(net.minecraft.world.level.block.SnowyBlock.SNOWY, true));
            m = SeamlessRules.creepMask(h.getLevel(), h.absolutePos(new BlockPos(3, 1, 3)));
            // the east side is no grass edge now, so the grass past it (north-east) gets a corner instead
            h.assertTrue(m == (0b0100 | 1 << 4), "a snowy grass block is not a grass edge: " + Integer.toBinaryString(m));
            h.succeed();
        });

        // the lawn drift is small, centred, smooth and the same every time
        AwakenTests.register("ground_drift_range", 20, h -> {
            double sum = 0;
            float lo = 9, hi = -9, worstStep = 0;
            int n = 0;
            for (int x = -300; x < 300; x += 3) for (int z = -300; z < 300; z += 3) {
                float t = LawnDrift.noise(x, z);
                sum += t;
                n++;
                lo = Math.min(lo, t);
                hi = Math.max(hi, t);
                worstStep = Math.max(worstStep, Math.abs(t - LawnDrift.noise(x + 1, z)));
                h.assertTrue(t == LawnDrift.noise(x, z), "deterministic");
            }
            h.assertTrue(lo >= -1f && hi <= 1f, "within -1..1");
            h.assertTrue(Math.abs(sum / n) < 0.2, "centred: " + sum / n);
            h.assertTrue(hi - lo > 1.0f, "it does vary: " + (hi - lo));
            h.assertTrue(worstStep < 0.3f, "smooth block to block: " + worstStep);
            // the colour moves a few per cent at the ends, never out of range, and keeps its alpha
            int grass = 0xFF79C05A;
            for (int x = -300; x < 300; x += 7) {
                int c = LawnDrift.apply(grass, x, x * 3);
                h.assertTrue((c >>> 24) == 0xFF, "alpha kept");
                int dr = Math.abs((c >> 16 & 255) - 0x79), dg = Math.abs((c >> 8 & 255) - 0xC0), db = Math.abs((c & 255) - 0x5A);
                h.assertTrue(dr <= 14 && dg <= 12 && db <= 9, "a small move: " + dr + " " + dg + " " + db);
            }
            h.succeed();
        });
    }

    // ---- P1: the soft steps ------------------------------------------------------------------------------------------------

    private static void softTests() {
        // the rule: the first column of each lower plateau of a gentle slope; none on a one-wide stair run, none by a cliff
        AwakenTests.register("ground_soft_rule", 20, h -> {
            int w = 12;
            int[] slope = new int[w * 3];
            for (int j = 0; j < 3; j++) for (int i = 0; i < w; i++) slope[j * w + i] = 10 + i / 2;      // 10 10 11 11 12 12 ...
            for (int i = 1; i < w - 1; i++) {
                boolean want = i % 2 == 1;                                                                 // the plateaus' second column: its east is a block up
                h.assertTrue(SoftRule.foot(slope, w, i, 1) == want, "gentle slope, column " + i + " foot=" + want);
            }
            int[] stair = new int[w * 3];
            for (int j = 0; j < 3; j++) for (int i = 0; i < w; i++) stair[j * w + i] = 10 + i;           // one block a column: 45 degrees
            for (int i = 1; i < w - 1; i++) h.assertFalse(SoftRule.foot(stair, w, i, 1), "a one-wide stair run takes none, column " + i);
            int[] cliff = new int[w * 3];
            for (int j = 0; j < 3; j++) for (int i = 0; i < w; i++) cliff[j * w + i] = i < 6 ? 10 : 13;  // a three-block wall
            for (int i = 1; i < w - 1; i++) h.assertFalse(SoftRule.foot(cliff, w, i, 1), "a cliff takes none, column " + i);
            // a pit's floor takes one; a column with a lower neighbour never does
            int[] pit = new int[w * 3];
            java.util.Arrays.fill(pit, 10);
            pit[1 * w + 5] = 9;
            h.assertFalse(SoftRule.foot(pit, w, 4, 1), "beside a deeper pit: a lower neighbour, no");
            // every column decides on its own: the whole grid and each cell alone agree (the same in any chunk order)
            int n = 0;
            for (int i = 1; i < w - 1; i++) if (SoftRule.foot(slope, w, i, 1)) n++;
            h.assertTrue(n == 5, "five foot columns on that slope: " + n);
            // the tufts: reproducible, about 45% of slabs carry one
            int with = 0;
            for (int x = 0; x < 200; x++) {
                int t = SoftRule.tuft(7L, x, x * 3);
                h.assertTrue(t >= 0 && t <= 5 && t == SoftRule.tuft(7L, x, x * 3), "a tuft is 0..5 and the same each time");
                if (t > 0) with++;
            }
            h.assertTrue(with > 60 && with < 120, "about 45% carry a tuft: " + with + " of 200");
            h.succeed();
        });

        // the pass on real blocks: a gentle slope laid in a chunk, the foot columns take their slabs (grass below turns to dirt),
        // the rest are untouched, nothing is placed over a plant, a block or a ground it has no slab for
        AwakenTests.registerIsolated("ground_soft_steps_paint", 60, h -> {
            ServerLevel level = h.getLevel();
            BlockPos o = h.absolutePos(BlockPos.ZERO);
            int cx = o.getX() & ~15, cz = o.getZ() & ~15, y0 = o.getY();
            int[][] top = new int[16][16];
            for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
                int y = y0 + dx / 2;
                top[dx][dz] = y;
                for (int yy = y0 - 2; yy <= y0 + 10; yy++) {
                    BlockPos p = new BlockPos(cx + dx, yy, cz + dz);
                    level.setBlock(p, yy < y - 1 ? Blocks.STONE.defaultBlockState() : yy == y - 1 ? Blocks.DIRT.defaultBlockState()
                            : yy == y ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                }
            }
            // a plant over one foot column (it stays), and a block over another
            level.setBlock(new BlockPos(cx + 5, top[5][4] + 1, cz + 4), Blocks.SHORT_GRASS.defaultBlockState(), 2);
            level.setBlock(new BlockPos(cx + 7, top[7][8] + 1, cz + 8), Blocks.STONE.defaultBlockState(), 2);
            SoftStepsFeature.Ground ground = new SoftStepsFeature.Ground() {
                @Override
                public int top(int x, int z) {
                    int dx = x - cx, dz = z - cz;
                    return dx < 0 || dz < 0 || dx > 15 || dz > 15 ? -10000 : top[dx][dz];
                }

                @Override
                public boolean dry(int x, int z) {
                    return true;
                }
            };
            int placed = SoftStepsFeature.paint(level, cx, cz, level.getSeaLevel(), 12345L, ground);
            // odd columns 1..13 of rows 1..14, minus the two covered ones
            int expected = 7 * 14 - 2;
            h.assertTrue(placed == expected, "the soft steps placed " + placed + ", expected " + expected);
            int tufted = 0;
            for (int dx = 1; dx <= 14; dx++) for (int dz = 1; dz <= 14; dz++) {
                boolean foot = dx % 2 == 1 && dx <= 13 && dz >= 1 && dz <= 14;
                boolean covered = (dx == 5 && dz == 4) || (dx == 7 && dz == 8);
                BlockPos g = new BlockPos(cx + dx, top[dx][dz], cz + dz), above = g.above();
                BlockState surface = level.getBlockState(g), over = level.getBlockState(above);
                if (foot && !covered) {
                    h.assertTrue(surface.is(Blocks.DIRT), "the grass under a slab is dirt at " + dx + "," + dz);
                    h.assertTrue(over.is(SoftSlabs.TURF_SLAB.get()) && over.getValue(SlabBlock.TYPE) == SlabType.BOTTOM, "a turf slab at " + dx + "," + dz + ": " + over);
                    if (over.getValue(TurfSlabBlock.TUFT) > 0) tufted++;
                } else {
                    h.assertTrue(!over.is(SoftSlabs.TURF_SLAB.get()), "no slab at " + dx + "," + dz);
                    if (!foot) h.assertTrue(surface.is(Blocks.GRASS_BLOCK), "other columns keep their grass at " + dx + "," + dz);
                }
            }
            h.assertTrue(level.getBlockState(new BlockPos(cx + 5, top[5][4] + 1, cz + 4)).is(Blocks.SHORT_GRASS), "the plant stays");
            h.assertTrue(level.getBlockState(new BlockPos(cx + 7, top[7][8] + 1, cz + 8)).is(Blocks.STONE), "the block stays");
            h.assertTrue(tufted > 10 && tufted < 70, "some slabs carry a tuft: " + tufted);
            // the standing height over a foot column is half a block up
            double stand = GroundTop.standingY(level, cx + 3, cz + 3);
            h.assertTrue(Math.abs(stand - (top[3][3] + 1.5)) < 1e-6, "a body stands half a block up on the slab: " + stand + " over " + (top[3][3] + 1));
            // clean up the chunk for whoever runs next
            for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) for (int yy = y0 - 2; yy <= y0 + 10; yy++)
                level.setBlock(new BlockPos(cx + dx, yy, cz + dz), Blocks.AIR.defaultBlockState(), 2);
            h.succeed();
        });

        // the same through the feature itself, with the real heightmaps the generator reads (the first version double-counted the "- 1"
        // of a chunk's heightmap and placed nothing in a real world): a sloped chunk, place(), the foot columns take their slabs
        AwakenTests.registerIsolated("ground_soft_steps_world", 60, h -> {
            ServerLevel level = h.getLevel();
            BlockPos o = h.absolutePos(BlockPos.ZERO);
            int cx = (o.getX() >> 4) + 7, cz = (o.getZ() >> 4) + 7;           // a chunk of its own, loaded for the test
            level.getChunk(cx, cz);
            int x0 = cx << 4, z0 = cz << 4, y0 = o.getY();
            for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) for (int yy = level.getMinY(); yy <= Math.min(level.getMaxY(), y0 + 12); yy++) {
                int y = y0 + dx / 2;
                level.setBlock(new BlockPos(x0 + dx, yy, z0 + dz), yy < y ? Blocks.STONE.defaultBlockState() : yy == y ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            }
            // the biome there is whatever the test level has: skip the cold ones by checking first
            if (level.getBiome(new BlockPos(x0 + 8, y0 + 1, z0 + 8)).value().coldEnoughToSnow(new BlockPos(x0 + 8, y0 + 1, z0 + 8), level.getSeaLevel())) {
                h.succeed();
                return;
            }
            SoftStepsFeature.INSTANCE.place(level, level.getChunkSource().getGenerator(), level.getRandom(), new BlockPos(x0, level.getMinY(), z0));
            int slabs = 0;
            for (int dx = 1; dx <= 14; dx++) for (int dz = 1; dz <= 14; dz++) {
                boolean foot = dx % 2 == 1 && dx <= 13;
                BlockState over = level.getBlockState(new BlockPos(x0 + dx, y0 + dx / 2 + 1, z0 + dz));
                if (over.is(SoftSlabs.TURF_SLAB.get())) slabs++;
                h.assertTrue(foot == over.is(SoftSlabs.TURF_SLAB.get()), "a turf slab on the foot columns only, at " + dx + "," + dz + ": " + over);
            }
            h.assertTrue(slabs == 7 * 14, "the feature placed " + slabs + " of " + 7 * 14 + " in a real chunk");
            for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) for (int yy = y0 - 2; yy <= y0 + 12; yy++)
                level.setBlock(new BlockPos(x0 + dx, yy, z0 + dz), Blocks.AIR.defaultBlockState(), 2);
            h.succeed();
        });

        // the slabs: spawn on, no plants grow on (nothing floats), grass smothered under a block goes to dirt, items and loot exist
        AwakenTests.register("ground_soft_slab_rules", 20, h -> {
            BlockState turf = SoftSlabs.TURF_SLAB.get().defaultBlockState();
            h.assertTrue(turf.is(BlockTags.ANIMALS_SPAWNABLE_ON), "animals spawn on turf");
            for (var slab : SoftSlabs.all()) {
                BlockState s = slab.get().defaultBlockState();
                h.assertFalse(s.is(BlockTags.SUPPORTS_VEGETATION), slab.getId() + " carries no vegetation: a plant would float");
                h.assertTrue(s.isValidSpawn(h.getLevel(), BlockPos.ZERO, net.minecraft.world.entity.EntityTypes.ZOMBIE), slab.getId() + " is ground a monster spawns on");
                h.assertFalse(s.setValue(SlabBlock.TYPE, SlabType.TOP).isValidSpawn(h.getLevel(), BlockPos.ZERO, net.minecraft.world.entity.EntityTypes.ZOMBIE), "not a top slab");
                h.assertTrue(s.is(BlockTags.SLABS), slab.getId() + " is a slab");
                h.assertTrue(s.is(SeamlessRules.SOFT_SLABS), slab.getId() + " is a soft slab");
            }
            // a turf slab under a solid block dies to a dirt slab; with open air over it, it stays
            h.setBlock(new BlockPos(1, 1, 1), turf);
            BlockPos abs = h.absolutePos(new BlockPos(1, 1, 1));
            turf.randomTick(h.getLevel(), abs, h.getLevel().getRandom());
            h.assertTrue(h.getLevel().getBlockState(abs).is(SoftSlabs.TURF_SLAB.get()), "open to the sky it stays");
            h.setBlock(new BlockPos(1, 2, 1), Blocks.STONE);
            turf.randomTick(h.getLevel(), abs, h.getLevel().getRandom());
            h.assertTrue(h.getLevel().getBlockState(abs).is(SoftSlabs.DIRT_SLAB.get()), "smothered it is a dirt slab");
            h.succeed();
        });

        // the order of generation holds: the soft steps run at local_modifications, before the painters (roads and settlements,
        // top_layer_modification), which overwrite their own ground, so no half-slab can stay in a street or a yard
        AwakenTests.register("ground_soft_before_painters", 20, h -> {
            String steps = read("/datapacks/awakened_ground/data/awaken/neoforge/biome_modifier/ground/soft_steps.json").get("step").getAsString();
            h.assertTrue(steps.equals("local_modifications"), "soft steps at " + steps);
            for (String s : new String[] {"road", "settlement"}) {
                String step = read("/datapacks/awakened_villages/data/awaken/worldgen/structure/" + s + ".json").get("step").getAsString();
                h.assertTrue(step.equals("top_layer_modification"), s + " paints at " + step);
            }
            h.succeed();
        });

        // the Ascent on soft ground: a descending run of half steps is a run for the stair slide; a half step needs no jump;
        // a wall topped with a slab has a ledge to mantle
        AwakenTests.registerIsolated("ground_soft_hill_body", 100, h -> {
            for (int x = 0; x <= 14; x++) for (int z = 0; z <= 16; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                for (int y = 1; y <= 10; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
            }
            BlockState slab = SoftSlabs.TURF_SLAB.get().defaultBlockState();
            // lane x 1: a platform at 6.0 (z 0..2), then ten half steps down: 5.5, 5.0, ... 1.0
            for (int k = -3; k < 10; k++) {
                double s = k < 0 ? 6.0 : 6.0 - 0.5 * (k + 1);
                int ti = (int) Math.floor(s + 1e-9);
                for (int y = 1; y < ti; y++) for (int x = 0; x <= 2; x++) h.setBlock(new BlockPos(x, y, 3 + k), Blocks.STONE);
                if (s - ti > 0.25) for (int x = 0; x <= 2; x++) h.setBlock(new BlockPos(x, ti, 3 + k), slab);
            }
            // lane x 8..10: a floor at 1.0 and a half step up at z 3
            for (int x = 8; x <= 10; x++) for (int z = 3; z <= 6; z++) h.setBlock(new BlockPos(x, 1, z), slab);
            // lane x 12: a wall of one stone with a slab on it at z 5
            h.setBlock(new BlockPos(12, 1, 5), Blocks.STONE);
            h.setBlock(new BlockPos(12, 2, 5), slab);
            h.startSequence().thenIdle(2).thenExecute(() -> {
                var level = h.getLevel();
                var ctx = CollisionContext.empty();
                BodyRules.StairProfile sp = new BodyRules.StairProfile();
                double[] fall = new double[2];
                Vec3 top = h.absoluteVec(new Vec3(1.5, 6.0, 2.8));
                h.assertTrue(BodyRules.stairStart(level, ctx, sp, top.x, top.y, top.z, 0, 1, fall), "a slide at the top of a soft hill finds the run down it");
                Vec3 across = h.absoluteVec(new Vec3(1.5, 5.5, 5.6));
                h.assertFalse(BodyRules.stairStart(level, ctx, sp, across.x, across.y, across.z, 1, 0, fall), "sliding across a soft hill is no run");
                // a half step is no jump
                ServerPlayer p = h.makeMockServerPlayerInLevel();
                p.setGameMode(GameType.SURVIVAL);
                Vec3 at = h.absoluteVec(new Vec3(9.5, 1.0, 2.0));
                p.snapTo(at.x, at.y, at.z, 0F, 0F);
                p.setOnGround(true);
                double y0 = p.getY();
                p.move(MoverType.SELF, new Vec3(0, 0, 0.9));
                h.assertTrue(p.getY() - y0 > 0.45, "a body walks up a half step with no jump: rose " + (p.getY() - y0));
                // the ledge: a stone block with a slab on it, feet at 1.0 on the floor in front of it (the top is 2.5, 1.5 up)
                Vec3 feet = h.absoluteVec(new Vec3(12.5, 1.0, 4.2));
                var ledge = BodyRules.findLedge(level, ctx, feet.x, feet.y, feet.z, 0F, 1.2);
                h.assertTrue(ledge != null && Math.abs(ledge.y() - h.absoluteVec(new Vec3(0, 2.5, 0)).y) < 0.2, "a wall topped with a slab has a ledge at its top: " + ledge);
                h.succeed();
            });
        });
    }

    private static JsonObject read(String path) {
        try (var in = GroundTests.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("no resource " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- P2: the floor -----------------------------------------------------------------------------------------------------

    private static boolean exists(String path) {
        return GroundTests.class.getResource(path) != null;
    }

    private static void floorTests() {
        // the palette is whole: every placed feature, own feature and biome modifier it names is in the pack, our boulder
        // features and every block state they place decode
        AwakenTests.register("ground_floor_data", 20, h -> {
            String base = "/datapacks/awakened_ground/data/awaken/";
            JsonObject table = read("/datapacks/awakened_ground/palette.json");
            int placed = 0;
            for (var group : table.entrySet()) {
                JsonObject g = group.getValue().getAsJsonObject();
                JsonArray entries = g.getAsJsonArray("entries");
                h.assertTrue(!entries.isEmpty() && g.getAsJsonArray("biomes").size() > 0, group.getKey() + " has biomes and entries");
                h.assertTrue(exists(base + "neoforge/biome_modifier/ground/floor_" + group.getKey() + ".json")
                        || exists(base + "neoforge/biome_modifier/ground/floor_" + group.getKey() + "_1.json"), group.getKey() + " has its biome modifier");
                for (JsonElement e : entries) {
                    String name = group.getKey() + "_" + e.getAsJsonObject().get("name").getAsString();
                    JsonObject pf = read(base + "worldgen/placed_feature/ground/" + name + ".json");
                    // and through the game's own codecs: the world loaded the pack (its flag is on in a new world), so the registry has it
                    h.assertTrue(h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.PLACED_FEATURE)
                            .get(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.PLACED_FEATURE, dev.awaken.Awaken.id("ground/" + name))).isPresent(),
                            name + " is a loaded placed feature");
                    String feature = pf.get("feature").getAsString();
                    h.assertTrue(!pf.getAsJsonArray("placement").isEmpty(), name + " places somewhere");
                    if (feature.startsWith("awaken:ground/")) {
                        JsonObject f = read(base + "worldgen/feature/ground/" + feature.substring("awaken:ground/".length()) + ".json");
                        if (f.get("type").getAsString().equals("awaken:boulder")) {
                            var parsed = BoulderFeature.CODEC.codec().parse(JsonOps.INSTANCE, f);
                            h.assertTrue(parsed.result().isPresent(), feature + " decodes: " + parsed.error());
                        } else {
                            for (JsonElement w : f.getAsJsonObject("to_place").getAsJsonArray("entries")) {
                                var st = BlockState.CODEC.parse(JsonOps.INSTANCE, w.getAsJsonObject().get("data"));
                                h.assertTrue(st.result().isPresent(), feature + " places a real state: " + st.error());
                            }
                        }
                    }
                    placed++;
                }
            }
            h.assertTrue(placed >= 25, "the palette places " + placed + " features");
            h.assertTrue(h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.PLACED_FEATURE)
                    .get(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.PLACED_FEATURE, dev.awaken.Awaken.id("ground/soft_steps"))).isPresent(), "the soft steps are a loaded placed feature");
            h.assertTrue(h.getLevel().getServer().getPackRepository().getSelectedIds().contains(GroundModule.PACK_ID), "a new world loads the ground pack");
            h.succeed();
        });

        // pebbles and twigs: no collision, a whole block under them, not on a half slab, gone when it goes; every state has a model
        AwakenTests.register("ground_floor_blocks", 20, h -> {
            BlockState pebbles = GroundCover.PEBBLES.get().defaultBlockState(), twigs = GroundCover.TWIGS.get().defaultBlockState();
            h.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
            h.setBlock(new BlockPos(3, 0, 1), SoftSlabs.TURF_SLAB.get());
            var level = h.getLevel();
            h.assertTrue(pebbles.canSurvive(level, h.absolutePos(new BlockPos(1, 1, 1))), "pebbles lie on stone");
            h.assertFalse(pebbles.canSurvive(level, h.absolutePos(new BlockPos(3, 1, 1))), "pebbles don't lie on a half slab (they'd float)");
            h.assertFalse(twigs.canSurvive(level, h.absolutePos(new BlockPos(5, 1, 1))), "twigs need ground");
            for (BlockState s : GroundCover.PEBBLES.get().getStateDefinition().getPossibleStates()) {
                h.assertTrue(s.getCollisionShape(level, BlockPos.ZERO).isEmpty() && !s.getShape(level, BlockPos.ZERO).isEmpty(), s + " has no collision and an outline");
            }
            for (BlockState s : GroundCover.TWIGS.get().getStateDefinition().getPossibleStates()) {
                h.assertTrue(s.getCollisionShape(level, BlockPos.ZERO).isEmpty(), s + " has no collision");
            }
            h.assertTrue(PebblesBlock.KIND.getPossibleValues().size() == 4 && TwigsBlock.WOOD.getPossibleValues().size() == 3, "four stones, three barks");
            h.assertTrue(twigs.setValue(TwigsBlock.FACING, Direction.NORTH).getShape(level, BlockPos.ZERO) != twigs.setValue(TwigsBlock.FACING, Direction.EAST).getShape(level, BlockPos.ZERO), "twigs lie along their way");
            // assets exist for every state (the generator wrote them)
            for (int k = 0; k < 4; k++) for (int n = 1; n <= 3; n++) {
                h.assertTrue(exists("/assets/awaken/models/block/pebbles_" + k + "_" + n + ".json"), "model pebbles_" + k + "_" + n);
            }
            for (int w = 0; w < 3; w++) h.assertTrue(exists("/assets/awaken/models/block/twigs_" + w + ".json"), "model twigs_" + w);
            h.succeed();
        });

        // a boulder: a rounded mass of its palette's rock, half sunk, crowned with slabs; none over water; none without natural ground
        AwakenTests.registerIsolated("ground_floor_boulder", 60, h -> {
            ServerLevel level = h.getLevel();
            BlockPos o = h.absolutePos(new BlockPos(0, 4, 0));
            int x0 = o.getX() - 8, z0 = o.getZ() - 8, y0 = o.getY();
            for (int dx = 0; dx <= 16; dx++) for (int dz = 0; dz <= 16; dz++) for (int y = y0 - 3; y <= y0 + 6; y++)
                level.setBlock(new BlockPos(x0 + dx, y, z0 + dz), y < y0 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            BoulderFeature forest = new BoulderFeature("forest", 3, 3);
            BlockPos at = new BlockPos(o.getX(), y0, o.getZ());
            h.assertTrue(forest.paint(level, at, 3, 99, level.getSeaLevel()), "a boulder grows on stone ground");
            int rock = 0, slabs = 0, high = 0, mossy = 0;
            for (int dx = 0; dx <= 16; dx++) for (int dz = 0; dz <= 16; dz++) for (int y = y0 - 3; y <= y0 + 6; y++) {
                BlockState s = level.getBlockState(new BlockPos(x0 + dx, y, z0 + dz));
                if (y >= y0 && !s.isAir()) {
                    rock++;
                    if (s.is(BlockTags.SLABS)) slabs++;
                    if (s.is(Blocks.MOSSY_COBBLESTONE)) mossy++;
                    if (y >= y0 + 1) high++;
                }
            }
            h.assertTrue(rock >= 15 && rock <= 140, "a mass of rock above the ground: " + rock);
            h.assertTrue(slabs > 0, "its crown is softened with slabs: " + slabs);
            h.assertTrue(high > 0, "it stands at least two blocks high");
            h.assertTrue(mossy > 0, "the forest palette is mossy: " + mossy);
            // a second one over water is refused, and a ground that isn't natural is refused
            for (int dx = 0; dx <= 16; dx++) for (int dz = 0; dz <= 16; dz++) for (int y = y0 - 3; y <= y0 + 6; y++)
                level.setBlock(new BlockPos(x0 + dx, y, z0 + dz), y < y0 ? Blocks.STONE.defaultBlockState() : y == y0 ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            h.assertFalse(forest.paint(level, at, 2, 5, level.getSeaLevel()), "no boulder in water");
            for (int dx = 0; dx <= 16; dx++) for (int dz = 0; dz <= 16; dz++) for (int y = y0 - 3; y <= y0 + 6; y++)
                level.setBlock(new BlockPos(x0 + dx, y, z0 + dz), y < y0 ? Blocks.GLASS.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            h.assertFalse(forest.paint(level, at, 2, 5, level.getSeaLevel()), "no boulder on glass");
            for (int dx = 0; dx <= 16; dx++) for (int dz = 0; dz <= 16; dz++) for (int y = y0 - 3; y <= y0 + 6; y++)
                level.setBlock(new BlockPos(x0 + dx, y, z0 + dz), Blocks.AIR.defaultBlockState(), 2);
            h.succeed();
        });
    }

    // ---- P3: mountains -----------------------------------------------------------------------------------------------------

    private static void mountainTests() {
        // the slope bands: grass, patches of coarse dirt and gravel, scree with grass pockets and rock showing, bare rock
        AwakenTests.register("ground_mountain_bands", 20, h -> {
            h.assertTrue(AlpineSurface.stone(0.5f, 0, 0.5f) == 0 && AlpineSurface.stone(1.3f, 0, 0.5f) == 1 && AlpineSurface.stone(1.7f, 0, 0.5f) == 2, "grass under, scree from 1.15, rock from 1.5");
            int coarse = 0, gravel = 0, none = 0, n = 4000;
            for (int k = 0; k < n; k++) {
                float x = k * 3.7f, z = k * 1.9f - 800;
                int l = AlpineSurface.loose(x, z, 0.9f, 0, 0.5f);
                if (l == 1) coarse++;
                else if (l == 2) gravel++;
                else none++;
                h.assertTrue(AlpineSurface.loose(x, z, 0.4f, 0, 0.5f) == 0, "no loose ground on gentle soil");
                h.assertTrue(AlpineSurface.loose(x, z, 1.3f, 0, 0.5f) == 0, "scree takes over from the loose ground");
            }
            h.assertTrue(coarse > n * 0.04 && coarse < n * 0.35, "coarse dirt in patches at 0.9: " + coarse + " of " + n);
            h.assertTrue(gravel > n * 0.03 && gravel < n * 0.30, "gravel in patches at 0.9: " + gravel + " of " + n);
            h.assertTrue(none > n * 0.4, "grass stays on most of it: " + none);
            int pockets = 0, rockShows = 0, scree = 0;
            for (int k = 0; k < n; k++) {
                float p = AlpineSurface.pocket(k * 2.3f, k * 5.1f);
                if (p < 0.22f) pockets++;
                else if (p > 0.78f) rockShows++;
                else scree++;
            }
            h.assertTrue(pockets > n * 0.05 && rockShows > n * 0.05 && scree > n * 0.5, "scree band: pockets " + pockets + ", rock " + rockShows + ", gravel " + scree);
            h.succeed();
        });

        // the strata: every layer occurs, stone the most, calcite thin, layers dip across the land, the same every time
        AwakenTests.register("ground_mountain_strata", 20, h -> {
            int[] count = new int[5];
            int total = 0, dips = 0;
            for (int x = -200; x < 200; x += 7) for (int z = -200; z < 200; z += 11) for (int y = 60; y < 260; y++) {
                int l = AlpineSurface.strata(x, y, z);
                h.assertTrue(l >= 0 && l <= 4 && l == AlpineSurface.strata(x, y, z), "a layer 0..4, the same each time");
                count[l]++;
                total++;
            }
            for (int l = 0; l < 5; l++) h.assertTrue(count[l] > 0, "layer " + l + " occurs");
            h.assertTrue(count[0] > total * 0.40, "stone is the commonest: " + count[0] + " of " + total);
            h.assertTrue(count[4] > total * 0.01 && count[4] < total * 0.08, "calcite is a thin band: " + count[4] + " of " + total);
            for (int x = 0; x < 200; x += 5) for (int y = 100; y < 130; y++) if (AlpineSurface.strata(x, y, 0) != AlpineSurface.strata(x + 40, y, 0)) dips++;
            h.assertTrue(dips > 200, "the layers dip across the land (they are not ruled lines): " + dips);
            h.succeed();
        });

        // snow in layers below the edge: none above the line (blocks lie there) or far under it or on cliffs; one to three between,
        // thicker toward the line
        AwakenTests.register("ground_mountain_drifts", 20, h -> {
            float x0 = 300, z0 = 300;
            float line = AlpineSurface.snowLine(x0, z0, 0.2f, 0);
            int nearSum = 0, farSum = 0, nearN = 0, farN = 0;
            for (int k = 0; k < 3000; k++) {
                float x = x0 + k * 1.3f, z = z0 + (k * 7 % 400);
                float ln = AlpineSurface.snowLine(x, z, 0.2f, 0), pa = AlpineSurface.patches(x, z);
                int above = AlpineSurface.driftLayers(x, ln - pa + 2, z, 0.2f, 0, 0.5f);
                h.assertTrue(above == 0, "none where the snow is already blocks");
                h.assertTrue(AlpineSurface.driftLayers(x, ln - pa - 30, z, 0.2f, 0, 0.5f) == 0, "none far under the line");
                h.assertTrue(AlpineSurface.driftLayers(x, ln - pa - 4, z, 1.6f, 0, 0.5f) == 0, "none on cliffs");
                int near = AlpineSurface.driftLayers(x, ln - pa - 1.5f, z, 0.2f, 0, 0.5f), far = AlpineSurface.driftLayers(x, ln - pa - 7.5f, z, 0.2f, 0, 0.5f);
                h.assertTrue(near >= 0 && near <= 3 && far >= 0 && far <= 3, "0..3 layers");
                nearSum += near;
                farSum += far;
                if (near > 0) nearN++;
                if (far > 0) farN++;
            }
            h.assertTrue(nearN > 600 && farN > 50, "drifts lie in the band: near " + nearN + ", far " + farN);
            h.assertTrue(nearSum > farSum * 1.5, "thicker and commoner toward the line: " + nearSum + " against " + farSum);
            h.assertTrue(line > 100, "the line is where it was");
            h.succeed();
        });

        // near and far use the same slopes: Far Sight's shader constants are the Java rule's
        AwakenTests.register("ground_mountain_far_parity", 20, h -> {
            String glsl;
            try (var in = GroundTests.class.getResourceAsStream("/assets/awaken/shaders/include/farsight.glsl")) {
                glsl = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
            String want = "const float SNOW_SLOPE = " + AlpineSurface.SNOW_SLOPE + ", SCREE_SLOPE = " + AlpineSurface.SCREE_SLOPE + ", ROCK_SLOPE = " + AlpineSurface.ROCK_SLOPE + ";";
            h.assertTrue(glsl.contains(want), "farsight.glsl has: " + want);
            var placed = h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.PLACED_FEATURE);
            h.assertTrue(placed.get(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.PLACED_FEATURE, dev.awaken.Awaken.id("alpine/snow_drifts"))).isPresent(), "the snow drifts load");
            h.succeed();
        });
    }
}
