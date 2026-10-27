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

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** The ground's seam rules (SPEC §31): the masks the client draws from, and the drift's range. */
final class GroundTests {
    private GroundTests() {}

    static void register() {
        softTests();
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
}
