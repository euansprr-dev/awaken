package dev.awaken.anims.station;

import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class FurnaceTests {
    private FurnaceTests() {}
    public static void register() {
        AwakenTests.register("anims_furnace_camera_pick", 40, FurnaceTests::camera);
        AwakenTests.register("anims_furnace_contents", 60, FurnaceTests::contents);
        AwakenTests.register("anims_furnace_recess_geometry", 20, FurnaceTests::geometry);
        AwakenTests.register("stations_mouth_view", 20, FurnaceTests::mouthView);
    }

    /**
     * A furnace's own view (SPEC §63.6): straight out from the middle of its face at the height of what cooks, the same
     * from wherever you stand before it; its three slots inside the room at four window sizes, each picking itself; a
     * stack is held on an upright plane just out from the face; from the side or behind there is no scene.
     */
    private static void mouthView(GameTestHelper h) {
        int[][] screens = {{320, 240}, {424, 238}, {640, 360}, {1280, 720}};
        double[] eye = new double[3], point = new double[3], screen = new double[2], held = new double[3];
        float[] angles = new float[3];
        Quaternionf rotation = new Quaternionf();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            var station = new StationView.Station(new BlockPos(0, 64, 0), StationView.Kind.FURNACE, -d.getStepX(), -d.getStepZ());
            for (double distance : new double[]{0.7, 1.5, 3.5, 4.5}) for (double side : new double[]{-.5, 0, .5}) {
                h.assertTrue(StationView.beforeFace(station, .5 + d.getStepX() * distance + station.rx() * side, .5 + d.getStepZ() * distance + station.rz() * side),
                        "before the face at " + distance + " (" + side + ") the scene may open");
            }
            h.assertTrue(!StationView.beforeFace(station, .5 - d.getStepX() * 1.5, .5 - d.getStepZ() * 1.5), "from behind there is no scene");
            h.assertTrue(!StationView.beforeFace(station, .5 + station.rx() * 1.5, .5 + station.rz() * 1.5), "from the side there is no scene");
            double last = 9;
            for (int v = 0; v < StationView.MOUTHS; v++) {
                StationView.mouthEye(station, v, eye);
                double out = (eye[0] - .5) * d.getStepX() + (eye[2] - .5) * d.getStepZ(), off = (eye[0] - .5) * station.rx() + (eye[2] - .5) * station.rz();
                h.assertTrue(out > 0.5 && out < last && Math.abs(off) < 1e-9 && Math.abs(eye[1] - 64.57) < 1e-9, "view " + v + " is straight out from the face, each nearer than the last");
                last = out;
                for (int[] size : screens) {
                    var layout = StationView.layout(size[0], size[1], 4, true);
                    float aspect = (float) size[0] / size[1];
                    StationView.frame(station, eye[0], eye[1], eye[2], layout, size[0], size[1], aspect, StationView.MAX_FOV, angles);
                    StationView.rotation(angles[0], angles[1], rotation);
                    for (int slot = 0; slot < 3; slot++) {
                        StationView.spot(station, slot, point, 0);
                        h.assertTrue(StationView.project(point[0], point[1], point[2], eye[0], eye[1], eye[2], rotation, angles[2], aspect, size[0], size[1], screen)
                                && layout.inRoom(screen[0], screen[1]), "slot " + slot + " is inside the room from view " + v + " at " + size[0] + "x" + size[1]);
                        h.assertTrue(StationView.pick(station, layout, eye[0], eye[1], eye[2], rotation, angles[2], aspect, screen[0], screen[1], size[0], size[1]) == slot,
                                "slot " + slot + " picks itself from view " + v);
                        // a stack held over the slot: on the pointer's own line, just out from the face
                        h.assertTrue(StationView.onFace(station, eye[0], eye[1], eye[2], rotation, angles[2], aspect, screen[0], screen[1], size[0], size[1], held),
                                "the pointer's line meets the plane a stack is held in");
                        double outHeld = (held[0] - .5) * d.getStepX() + (held[2] - .5) * d.getStepZ();
                        h.assertTrue(Math.abs(outHeld - (0.5 + StationView.FURNACE_HOLD)) < 1e-6, "a carried stack is held just out from the face (" + outHeld + ")");
                        h.assertTrue(StationView.project(held[0], held[1], held[2], eye[0], eye[1], eye[2], rotation, angles[2], aspect, size[0], size[1], point)
                                && Math.abs(point[0] - screen[0]) < 0.01 && Math.abs(point[1] - screen[1]) < 0.01, "and is drawn exactly on the pointer");
                    }
                }
            }
        }
        h.succeed();
    }

    private static void camera(GameTestHelper h) {
        int[][] screens = {{320, 240}, {424, 238}, {640, 360}, {1280, 720}};
        double[] eye = new double[3], point = new double[3], screen = new double[2];
        float[] angles = new float[3];
        Quaternionf rotation = new Quaternionf();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            var station = new StationView.Station(new BlockPos(0, 64, 0), StationView.Kind.FURNACE, -d.getStepX(), -d.getStepZ());
            for (double distance : new double[]{1, 1.5, 3.5}) for (double side : new double[]{-.3, 0, .3}) {
                double x = .5 + d.getStepX() * distance + station.rx() * side, z = .5 + d.getStepZ() * distance + station.rz() * side;
                for (double height : new double[]{.3, 1.62, 2.6}) {
                    h.assertTrue(StationView.furnaceEye(station, x, 64 + height, z, eye) && eye[0] == x && eye[2] == z && eye[1] == 64.57,
                            "all facings and player heights reach the mouth without moving horizontally");
                    for (int[] size : screens) {
                        var layout = StationView.layout(size[0], size[1], 4, true);
                        float aspect = (float) size[0] / size[1];
                        StationView.frame(station, eye[0], eye[1], eye[2], layout, size[0], size[1], aspect, StationView.MAX_FOV, angles);
                        StationView.rotation(angles[0], angles[1], rotation);
                        for (int slot = 0; slot < 3; slot++) {
                            StationView.spot(station, slot, point, 0);
                            h.assertTrue(StationView.project(point[0], point[1], point[2], eye[0], eye[1], eye[2], rotation, angles[2], aspect, size[0], size[1], screen), "slot is in front");
                            h.assertTrue(layout.inRoom(screen[0], screen[1]), "slot fits above inventory at " + d + " " + distance);
                            h.assertTrue(StationView.pick(station, layout, eye[0], eye[1], eye[2], rotation, angles[2], aspect, screen[0], screen[1], size[0], size[1]) == slot,
                                    "every recessed slot picks itself at " + d + " " + distance + " side " + side + " slot " + slot);
                        }
                    }
                }
            }
            h.assertTrue(!StationView.furnaceEye(station, .5 + d.getStepX() * .85 + station.rx() * .6, 65.62,
                    .5 + d.getStepZ() * .85 + station.rz() * .6, eye), "oblique access cannot hide a slot behind the stone frame");
            h.assertTrue(!StationView.furnaceEye(station, .5 - d.getStepX(), 65.62, .5 - d.getStepZ(), eye), "rear access keeps vanilla menu");
        }
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        BlockPos column = h.absolutePos(new BlockPos(4, 3, 4));
        Vec3 high = Vec3.atCenterOf(column.above(2)), low = Vec3.atCenterOf(column);
        for (int y = 0; y <= 3; y++) h.getLevel().setBlockAndUpdate(column.above(y), Blocks.AIR.defaultBlockState());
        h.assertTrue(StationView.clearEyePath(h.getLevel(), p, high, low), "clear lowering path");
        h.getLevel().setBlockAndUpdate(column.above(), Blocks.STONE.defaultBlockState());
        h.assertTrue(!StationView.clearEyePath(h.getLevel(), p, high, low), "camera cannot cross a floor or wall");
        h.getLevel().setBlockAndUpdate(column.above(), Blocks.AIR.defaultBlockState());
        h.getLevel().setBlockAndUpdate(column.above().east(), Blocks.STONE.defaultBlockState());
        Vec3 edgeHigh = high.add(.45, 0, 0), edgeLow = low.add(.45, 0, 0);
        h.assertTrue(!StationView.clearEyePath(h.getLevel(), p, edgeHigh, edgeLow), "near-plane corners cannot enter the adjacent wall");
        h.succeed();
    }

    private static void contents(GameTestHelper h) {
        ServerPlayer first = h.makeMockServerPlayerInLevel(), second = h.makeMockServerPlayerInLevel();
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2));
        first.snapTo(pos.getX() + .5, pos.getY(), pos.getZ() - 2, 0, 0);
        second.snapTo(pos.getX() + .5, pos.getY(), pos.getZ() - 3, 0, 0);
        var a = new FurnaceContents.Watch(); var b = new FurnaceContents.Watch();
        h.assertTrue(a.tick(first, -1).changed().isEmpty(), "discovery before placement has no furnace");
        h.getLevel().setBlockAndUpdate(pos, Blocks.FURNACE.defaultBlockState());
        AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) h.getLevel().getBlockEntity(pos);
        furnace.setItem(0, new ItemStack(Items.RAW_IRON)); furnace.setItem(1, new ItemStack(Items.COAL));
        // A discovery just before this newly placed furnace would otherwise leave a quick interaction unseeded.
        var one = a.interacted(first, 0); var two = b.tick(second, 0);
        h.assertTrue(one != null && two != null && !one.reset() && two.reset() && one.changed().size() == 1 && two.changed().size() == 1, "both nearby viewers receive authoritative contents without a menu");
        one.changed().getFirst().input().shrink(1);
        h.assertTrue(furnace.getItem(0).getCount() == 1, "a view is a copy and cannot consume a real item");
        for (int i = 0; i < 201; i++) AbstractFurnaceBlockEntity.serverTick(h.getLevel(), pos, h.getLevel().getBlockState(pos), furnace);
        h.assertTrue(furnace.getItem(2).is(Items.IRON_INGOT) && furnace.getItem(0).isEmpty(), "vanilla smelting rules produce the real result");
        var cookedA = a.tick(first, 5); var cookedB = b.tick(second, 5);
        h.assertTrue(cookedA != null && cookedB != null && cookedA.changed().getFirst().output().is(Items.IRON_INGOT)
                && cookedB.changed().getFirst().output().is(Items.IRON_INGOT), "cooking updates both viewers while menus remain closed");
        var saved = furnace.saveWithFullMetadata(h.getLevel().registryAccess());
        var restored = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(pos, h.getLevel().getBlockState(pos), saved, h.getLevel().registryAccess());
        h.assertTrue(restored instanceof AbstractFurnaceBlockEntity, "vanilla furnace save reloads");
        h.getLevel().setBlockEntity(restored);
        furnace = (AbstractFurnaceBlockEntity) restored;
        h.assertTrue(a.tick(first, 10).removed().contains(pos), "unloaded/replaced block entity retires its old view");
        h.assertTrue(a.tick(first, 40).changed().getFirst().output().is(Items.IRON_INGOT), "discovery restores actual saved contents");
        furnace.removeItem(2, 1); // same authoritative container mutation used by hopper extraction
        h.assertTrue(a.tick(first, 45).changed().getFirst().output().isEmpty(), "hopper-style extraction clears the visible result");
        h.assertTrue(a.tick(first, 50) == null, "unchanged inventories send no packets");
        first.snapTo(pos.getX() + 40, pos.getY(), pos.getZ(), 0, 0);
        h.assertTrue(a.tick(first, 55).removed().contains(pos), "leaving range removes the view");
        first.snapTo(pos.getX(), pos.getY(), pos.getZ() - 2, 0, 0);
        h.assertTrue(a.tick(first, 80).changed().size() == 1, "returning reseeds the current contents");
        h.assertTrue(new FurnaceContents.Watch().tick(first, 80).changed().getFirst().output().isEmpty(), "rejoin starts from live inventory rather than cached loot");
        h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        h.assertTrue(a.tick(first, 85).removed().contains(pos), "breaking the furnace removes its view");
        h.succeed();
    }

    private static void geometry(GameTestHelper h) {
        for (String kind : new String[]{"furnace", "smoker", "blast_furnace"}) for (String lit : new String[]{"", "_lit"}) {
            String path = "/assets/awaken/models/block/furnace_interior/" + kind + lit + ".json";
            try (var stream = FurnaceTests.class.getResourceAsStream(path)) {
                h.assertTrue(stream != null, "recess model is packaged: " + path);
                var model = com.google.gson.JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                boolean frontBlocksMouth = false, backStopsRay = false, frameStopsRay = false;
                for (var raw : model.getAsJsonArray("elements")) {
                    var e = raw.getAsJsonObject(); if (!e.getAsJsonObject("faces").has("north")) continue;
                    var from = e.getAsJsonArray("from"); var to = e.getAsJsonArray("to");
                    double x0 = from.get(0).getAsDouble(), x1 = to.get(0).getAsDouble(), y0 = from.get(1).getAsDouble(), y1 = to.get(1).getAsDouble(), z = from.get(2).getAsDouble();
                    boolean middle = x0 <= 8 && x1 >= 8 && y0 <= 9 && y1 >= 9;
                    frontBlocksMouth |= z == 0 && middle;
                    backStopsRay |= z > 3 && middle;
                    frameStopsRay |= z == 0 && x0 <= 1 && x1 >= 1 && y0 <= 9 && y1 >= 9;
                }
                h.assertTrue(!frontBlocksMouth && backStopsRay && frameStopsRay, "the mouth is physically open, backed by stone, and surrounded by a frame: " + path);
            } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        }
        h.succeed();
    }
}
