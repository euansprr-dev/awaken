package dev.awaken.city;

import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.city.CityBlocks.*;
import static dev.awaken.city.CityPlan.*;

/**
 * The Commons step's GameTests, one per mechanism: pennant lines whole and tied to their poles (nothing floats), the
 * city's lines ending at a wall or a pole, the road's cross-section and its culverts, every painted water still (nothing
 * to flow into), the roadside farms joined to their roads, and the monsters thinned round the city.
 */
final class CityCommonsTests {
    private CityCommonsTests() {}

    static final long SEED = 20261024L;
    static final int YLO = -2, YHI = 14;

    /** A painted rectangle of the commons (plan coordinates), rows -2..14, painted chunk by chunk as worldgen does. */
    static final class Area {
        final int x0, z0, x1, z1, ylo, yhi;
        final BlockState[] cells;

        Area(CityLayout l, int x0, int z0, int x1, int z1) {
            this(l, x0, z0, x1, z1, YLO, YHI);
        }

        Area(CityLayout l, int x0, int z0, int x1, int z1, int ylo, int yhi) {
            this.x0 = x0;
            this.z0 = z0;
            this.x1 = x1;
            this.z1 = z1;
            this.ylo = ylo;
            this.yhi = yhi;
            cells = new BlockState[(x1 - x0 + 1) * (z1 - z0 + 1) * (yhi - ylo + 1)];
            CityBuffer buf = new CityBuffer(16, 16);
            for (int cx = (l.cx + x0) >> 4; cx <= (l.cx + x1) >> 4; cx++)
                for (int cz = (l.cz + z0) >> 4; cz <= (l.cz + z1) >> 4; cz++) {
                    if (!l.touches(cx, cz)) continue;
                    CityPainter.paintChunk(l, cx, cz, SEED, buf);
                    for (int x = Math.max(x0, buf.px0); x <= Math.min(x1, buf.x1()); x++)
                        for (int z = Math.max(z0, buf.pz0); z <= Math.min(z1, buf.z1()); z++)
                            for (int y = ylo; y <= yhi; y++) cells[index(x, y, z)] = buf.get(x, y, z);
                }
        }

        int index(int x, int y, int z) {
            return ((x - x0) * (z1 - z0 + 1) + (z - z0)) * (yhi - ylo + 1) + (y - ylo);
        }

        BlockState get(int x, int y, int z) {
            if (x < x0 || x > x1 || z < z0 || z > z1 || y < ylo || y > yhi) return null;
            return cells[index(x, y, z)];
        }

        boolean is(int x, int y, int z, String id) {
            BlockState s = get(x, y, z);
            return s != null && net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString().equals(id);
        }

        boolean solid(int x, int y, int z) {
            BlockState s = get(x, y, z);
            return s != null && !s.isAir() && s.getFluidState().isEmpty()
                    && !s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
        }
    }

    /**
     * Every rope cell inside the area (its edge cells aside) runs on into the next along its axis, or ends at a sag-0
     * rope against a pole top whose rope is tied out toward it, or against something solid (a wall, an eave). Returns
     * the first loose end, or null; {@code ends[0]} counts the ends met at poles.
     */
    static String ropeEnds(Area a, int[] ends) {
        var lineAxis = dev.awaken.city.kit.StreetBlocks.HangLine.AXIS;
        var rope = dev.awaken.city.kit.StreetBlocks.LinePole.ROPE;
        for (int x = a.x0 + 1; x < a.x1; x++)
            for (int z = a.z0 + 1; z < a.z1; z++)
                for (int y = a.ylo; y <= a.yhi; y++) {
                    if (!a.is(x, y, z, "awaken:hang_line")) continue;
                    BlockState s = a.get(x, y, z);
                    boolean alongX = s.getValue(lineAxis) == net.minecraft.core.Direction.Axis.X;
                    for (int d = -1; d <= 1; d += 2) {
                        int nx = alongX ? x + d : x, nz = alongX ? z : z + d;
                        BlockState n = a.get(nx, y, nz);
                        if (n == null) continue;
                        if (a.is(nx, y, nz, "awaken:hang_line") && n.getValue(lineAxis) == s.getValue(lineAxis)) continue;
                        String at = " at " + x + " " + y + " " + z + (alongX ? (d > 0 ? " east" : " west") : (d > 0 ? " south" : " north"));
                        if (a.is(nx, y, nz, "awaken:line_pole")) {
                            var want = alongX ? (d > 0 ? dev.awaken.city.kit.StreetBlocks.LinePole.Rope.WEST : dev.awaken.city.kit.StreetBlocks.LinePole.Rope.EAST)
                                    : (d > 0 ? dev.awaken.city.kit.StreetBlocks.LinePole.Rope.NORTH : dev.awaken.city.kit.StreetBlocks.LinePole.Rope.SOUTH);
                            if (n.getValue(rope) != want) return "the pole's rope is tied " + n.getValue(rope) + ", not to the line" + at;
                            if (s.getValue(dev.awaken.city.kit.StreetBlocks.HangLine.SAG) != 0) return "the line meets its pole sagging" + at;
                            ends[0]++;
                            continue;
                        }
                        // a shutter or a banner on the facade, the wall right behind it
                        boolean onWall = (a.is(nx, y, nz, "awaken:shutter") || a.is(nx, y, nz, "awaken:banner_cloth"))
                                && a.solid(alongX ? nx + d : nx, y, alongX ? nz : nz + d);
                        if (!a.solid(nx, y, nz) && !onWall) return "the line ends in the air" + at + " (by " + n + ")";
                    }
                }
        return null;
    }

    static void register() {
        // every pennant line across a road and down the yard: a pole under each end to the line's row, the line whole
        // between them, and a banner only under a line (nothing hangs from the air)
        AwakenTests.register("city_commons_poles", 400, h -> {
            CityLayout l = CityTests.layoutAt(0, 0);
            Area a = new Area(l, -20, -335, 20, -240);
            int lines = 0;
            for (int z = a.z0; z <= a.z1; z++) {
                boolean any = false;
                for (int x = -4; x <= 3; x++) any |= a.is(x, CityCommons.LINE_ROW, z, "awaken:hang_line");
                if (!any) continue;
                lines++;
                for (int x = -5; x <= 4; x++)
                    h.assertTrue(a.is(x, CityCommons.LINE_ROW, z, "awaken:hang_line"), "the line across at z " + z + " is whole at x " + x);
                for (int x : new int[]{-6, 5}) {
                    for (int y = 1; y < CityCommons.LINE_ROW; y++)
                        h.assertTrue(a.is(x, y, z, "awaken:line_pole"), "a pole under the line's end at " + x + " " + y + " " + z);
                    h.assertTrue(a.is(x, CityCommons.LINE_ROW, z, "awaken:line_pole"), "the pole's head at the line at " + x + " " + z);
                }
            }
            h.assertTrue(lines >= 2, "two or more pennant lines across the High Gate road (" + lines + ")");
            // both ends of every line meet their anchors (Euan: "ropes still don't connect to poles"): the road's lines
            // and the yard's, each end at a pole whose rope is tied out toward it (or a wall)
            int[] ends = new int[1];
            String miss = ropeEnds(a, ends);
            h.assertTrue(miss == null, miss == null ? "" : miss);
            Area yard = new Area(l, -18, -257, 17, -243);
            miss = ropeEnds(yard, ends);
            h.assertTrue(miss == null, miss == null ? "" : miss);
            h.assertTrue(ends[0] >= 8, "line ends checked at poles (" + ends[0] + ")");
            for (int x = a.x0; x <= a.x1; x++)
                for (int z = a.z0; z <= a.z1; z++)
                    for (int y = 0; y < YHI; y++) {
                        if (!a.is(x, y, z, "awaken:banner_cloth")) continue;
                        int top = y;
                        while (a.is(x, top + 1, z, "awaken:banner_cloth")) top++;
                        h.assertTrue(a.is(x, top + 1, z, "awaken:hang_line") || a.solid(x, top + 1, z) || a.is(x, top + 1, z, "awaken:flagpole"),
                                "the banner at " + x + " " + y + " " + z + " hangs from something");
                        if (a.is(x, top + 1, z, "awaken:hang_line"))
                            h.assertTrue(a.get(x, top, z).getValue(dev.awaken.city.kit.StreetBlocks.BannerCloth.MOUNT)
                                    == dev.awaken.city.kit.StreetBlocks.BannerCloth.Mount.LINE, "the banner at " + x + " " + top + " " + z + " hangs on cords from the line");
                        h.assertFalse(a.is(x, y, z, "awaken:flagpole"), "no flagpole arm out of the air");
                    }
            h.succeed();
        });

        // the city's own lines across its lanes and yards (every one, all heights): both ends of each meet a
        // wall or eave, or a pole whose rope is tied out to it (Euan: the ropes don't connect, "also in the city")
        AwakenTests.register("city_lines_anchored", 900, h -> {
            CityLayout l = CityTests.layoutAt(0, 0);
            int[] ends = new int[1];
            int checked = 0;
            for (CityPlan.Prop pr : l.plan.props) {
                if (!pr.kind().equals("line")) continue;
                int m = CityProps.REACH + 2;
                Area a = new Area(l, pr.x0() - m, pr.z0() - m, pr.x1() + m, pr.z1() + m, pr.ry() - 1, pr.ry() + 1);
                String miss = ropeEnds(a, ends);
                h.assertTrue(miss == null, miss == null ? "" : "line " + pr.x0() + " " + pr.z0() + ".." + pr.x1() + " " + pr.z1() + ": " + miss);
                checked++;
            }
            h.assertTrue(checked >= 200, "city lines checked (" + checked + ")");
            h.succeed();
        });

        // the High Gate road: kerbs whole from the setts out, the ditch's water either side from 26 out, a culvert (water
        // under a slab) under every field gate, and a lamp every 28
        AwakenTests.register("city_commons_road", 400, h -> {
            CityLayout l = CityTests.layoutAt(0, 0);
            Area a = new Area(l, -10, -330, 10, -255);
            int culverts = 0, lamps = 0;
            for (int z = -320; z <= -264; z++) {
                double along = -238 - (z + 0.5);
                for (int x : new int[]{-4, 3}) h.assertTrue(a.solid(x, 0, z), "the kerb at " + x + " " + z);
                if (along >= CityCommons.HEDGES_FROM)
                    for (int x : new int[]{-7, 6}) {
                        if (Math.hypot(x, z) > l.plan.fieldsTo) continue; // the hedged stretch ends with the fields
                        h.assertTrue(a.is(x, -1, z, "minecraft:water"), "the ditch's water at " + x + " " + z);
                        if (a.is(x, 0, z, "minecraft:stone_brick_slab")) culverts++;
                    }
                for (int x : new int[]{-6, 5}) if (a.is(x, 4, z, "minecraft:lantern")) lamps++;
            }
            h.assertTrue(culverts >= 3, "culverts under the field gates (" + culverts + " cells)");
            h.assertTrue(lamps >= 2, "lamps along the road (" + lamps + ")");
            h.succeed();
        });

        // water painted outside the wall never flows: below and beside each water cell is solid ground or more water
        AwakenTests.register("city_commons_water_still", 600, h -> {
            CityLayout l = CityTests.layoutAt(0, 0);
            int[][] areas = {{-60, -330, 60, -250}, {250, -60, 320, 60}, {-60, 250, 60, 320}, {150, -240, 230, -160}};
            int n = 0;
            for (int[] r : areas) {
                Area a = new Area(l, r[0], r[1], r[2], r[3]);
                for (int x = a.x0 + 1; x < a.x1; x++)
                    for (int z = a.z0 + 1; z < a.z1; z++)
                        for (int y = -1; y <= 0; y++) {
                            if (!a.is(x, y, z, "minecraft:water") || l.plan.kind(x, z) != VOID) continue;
                            if (CityPainter.riverDistance(l.plan, x, z) < 16) continue;
                            n++;
                            h.assertTrue(a.solid(x, y - 1, z) || a.is(x, y - 1, z, "minecraft:water"), "under the water at " + x + " " + y + " " + z);
                            int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                            for (int[] d : nb) {
                                int nx = x + d[0], nz = z + d[1];
                                h.assertTrue(a.solid(nx, y, nz) || a.is(nx, y, nz, "minecraft:water") || a.is(nx, y, nz, "minecraft:stone_brick_slab"),
                                        "beside the water at " + x + " " + y + " " + z + ": " + a.get(nx, y, nz));
                            }
                        }
            }
            h.assertTrue(n > 200, "irrigation channels and ditches painted (" + n + " water cells)");
            h.succeed();
        });

        // the three roadside farms: their track runs from the road's kerb over the culvert, through the field gate, to
        // the yard; the barn's doors are open to the yard
        AwakenTests.register("city_commons_farms", 400, h -> {
            CityLayout l = CityTests.layoutAt(0, 0);
            for (int i = 0; i < 3; i++) {
                CityFarmsteads.Farm f = CityFarmsteads.FARMS.get(i);
                Area a = new Area(l, Math.min(f.x0(), -12) - 2, Math.min(f.z0(), -12) - 2, Math.max(f.x1(), 12) + 2, Math.max(f.z1(), 12) + 2);
                // walk from the farm's yard back toward its road along the track's middle (u 13)
                int x = f.x(13, 9), z = f.z(13, 9);
                int dx = Integer.signum(f.x(13, 0) - f.x(13, 1)), dz = Integer.signum(f.z(13, 0) - f.z(13, 1));
                boolean road = false;
                for (int k = 0; k < 24 && !road; k++, x += dx, z += dz) {
                    h.assertTrue(a.solid(x, 0, z) || a.is(x, 0, z, "minecraft:dirt_path") || a.is(x, 0, z, "minecraft:stone_brick_slab"),
                            "farm " + i + ": ground on the track at " + x + " " + z);
                    BlockState over = a.get(x, 1, z);
                    boolean gate = over != null && over.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock;
                    h.assertTrue(gate || over == null || over.isAir() || over.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty(),
                            "farm " + i + ": the track is open at " + x + " " + z + " (" + over + ")");
                    road = a.is(x, 0, z, "minecraft:cobblestone") || a.is(x, 0, z, "minecraft:mossy_cobblestone") || a.is(x, 0, z, "minecraft:stone");
                }
                h.assertTrue(road, "farm " + i + ": the track reaches the road's kerb");
                for (int v = 15; v <= 18; v++)
                    for (int y = 1; y <= 4; y++) {
                        BlockState s = a.get(f.x(9, v), y, f.z(9, v));
                        h.assertTrue(s == null || s.isAir(), "farm " + i + ": the barn doors open at v " + v + " row " + y);
                    }
            }
            h.succeed();
        });

        // monsters round the city: one in ten goes ahead over the fields, about one in two by the fire, all of them far
        // out; never inside the wall's own rule, never a peaceful creature
        AwakenTests.register("city_commons_monsters", 200, h -> {
            CityLayout l = CityTests.layoutAt(0, 0);
            Mob zombie = EntityTypes.ZOMBIE.create(h.getLevel(), EntitySpawnReason.EVENT);
            Mob cow = EntityTypes.COW.create(h.getLevel(), EntitySpawnReason.EVENT);
            h.assertTrue(zombie != null && cow != null, "test creatures");
            h.assertTrue(CityMonsters.hostile(zombie) && !CityMonsters.hostile(cow), "a zombie is hostile, a cow is not");
            zombie.setPos(l.cx + 30.5, 71, l.cz - 290.5);
            int kept = 0;
            for (int i = 0; i < 1000; i++) if (!CityMonsters.thinned(l, zombie, (i + 0.5) / 1000.0)) kept++;
            h.assertTrue(kept >= 85 && kept <= 115, "about one in ten over the fields (" + kept + " of 1000)");
            cow.setPos(l.cx + 30.5, 71, l.cz - 290.5);
            h.assertFalse(CityMonsters.thinned(l, cow, 0.99), "a cow is never turned away");
            zombie.setPos(l.cx + 0.5, 71, l.cz - 150.5);
            h.assertFalse(CityMonsters.thinned(l, zombie, 0.99), "inside the wall the lamps decide, not the fields");
            h.assertTrue(Math.abs(CityMonsters.pass(450) - 0.40) < 0.06, "about two in five by the Wayfarers' fire");
            h.assertTrue(CityMonsters.pass(700) == 1.0, "every spawn far out");
            // and through the hook the natural spawner really posts (26.3 skips FinalizeSpawnEvent for natural spawns)
            BlockPos abs = h.absolutePos(new BlockPos(0, 1, 0));
            CityLayout before = City.install(CityTests.layoutAt(abs.getX(), abs.getZ()));
            try {
                zombie.setPos(abs.getX() + 30.5, abs.getY(), abs.getZ() - 290.5);
                int failed = 0;
                for (int i = 0; i < 400; i++) {
                    var ev = new net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck(zombie, h.getLevel(), EntitySpawnReason.NATURAL, null);
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(ev);
                    if (ev.getResult() == net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck.Result.FAIL) failed++;
                }
                // about 360 of 400 (one in ten kept), sd 6: five sd either side, so a fair roll never fails (339 did, 30 Oct)
                h.assertTrue(failed >= 330 && failed <= 390, "the natural spawner's position check turns most away over the fields (" + failed + " of 400)");
            } finally {
                City.install(before);
            }
            zombie.discard();
            cow.discard();
            h.succeed();
        });
    }
}
