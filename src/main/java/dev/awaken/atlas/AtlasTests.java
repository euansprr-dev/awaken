package dev.awaken.atlas;

import com.mojang.serialization.JsonOps;
import dev.awaken.core.api.AtlasHooks;
import dev.awaken.core.api.CityHooks;
import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** The Atlas's silent rules (SPEC §16.8): one test per mechanism. */
final class AtlasTests {
    private AtlasTests() {}

    static void register() {
        // the map scene's camera (SPEC §30.5): behind you, up and over the shoulder, looking your way 38° down; the left
        // shoulder mirrors it; the move eases from your eyes to the shot, the short way round; the MAP relay kind exists
        AwakenTests.register("atlas_map_shot", 20, h -> {
            double[] r = new double[5], l = new double[5], hi = new double[5];
            for (float yaw : new float[]{0f, 90f, -135f, 300f}) {
                MapShot.pose(10, 64, -20, yaw, 1, false, r);
                MapShot.pose(10, 64, -20, yaw, -1, false, l);
                MapShot.pose(10, 64, -20, yaw, 1, true, hi);
                double fx = -Math.sin(Math.toRadians(yaw)), fz = Math.cos(Math.toRadians(yaw));
                double along = (r[0] - 10) * fx + (r[2] + 20) * fz, across = (r[0] - 10) * fz - (r[2] + 20) * fx;
                h.assertTrue(Math.abs(along + MapShot.BACK) < 1e-6, "behind you at yaw " + yaw + ": " + along);
                h.assertTrue(Math.abs(Math.abs(across) - MapShot.SIDE) < 1e-6, "to one side at yaw " + yaw);
                h.assertTrue(Math.abs((l[0] - 10) + (r[0] - 10) - 2 * fx * -MapShot.BACK) < 1e-6 && Math.abs((l[2] + 20) + (r[2] + 20) - 2 * fz * -MapShot.BACK) < 1e-6,
                        "the left shoulder mirrors the right at yaw " + yaw);
                h.assertTrue(r[1] == 64 + MapShot.UP && r[3] == yaw && r[4] == MapShot.PITCH && hi[1] > r[1] && hi[4] > r[4], "up, your way, 38° down; the high shot steeper");
            }
            h.assertTrue(MapShot.ease(0) == 0 && MapShot.ease(1) == 1 && Math.abs(MapShot.ease(0.5) - 0.5) < 1e-9 && MapShot.ease(-1) == 0 && MapShot.ease(2) == 1, "ease 0..1, clamped");
            double[] a = {0, 0, 0, 170, 0}, b = {4, 2, -4, -170, 38}, out = new double[5];
            MapShot.blend(a, b, 0.5, out);
            h.assertTrue(Math.abs(out[3] - 180) < 1e-6 && out[0] == 2 && out[4] == 19, "half way, the short way round: " + out[3]);
            h.assertTrue(MapShot.CLOSE_MS <= 250 && MapShot.FOLD_MS <= 150 && MapShot.DELAY_MS + MapShot.MOVE_MS <= 420
                    && MapShot.QUICK_DELAY_MS + MapShot.QUICK_MOVE_MS <= 220, "the timings SPEC §30.5 promises");
            h.assertTrue(dev.awaken.holo.HoloKind.byId(3) == dev.awaken.holo.HoloKind.MAP && dev.awaken.holo.HoloKind.byId(4) == null, "the MAP relay kind");
            h.succeed();
        });
        // the diorama at every zoom: the level follows screen pixels, a coarse column is read from its whole square (a road
        // a block wide keeps its colour in the texture, a wall still rises), and the finest level is the blocks exactly
        AwakenTests.register("atlas_coarse_columns", 20, h -> {
            String failed = dev.awaken.atlas.client.SliceSampler.selfTest();
            h.assertTrue(failed == null, String.valueOf(failed));
            h.succeed();
        });
        // the record: cells mark once, tiles merge, and the saved form round-trips with its schema
        AwakenTests.register("atlas_bits_codec", 20, h -> {
            Explored e = new Explored();
            h.assertTrue(e.mark(3, -5) && !e.mark(3, -5), "a cell marks once");
            h.assertTrue(e.explored(3, -5) && !e.explored(4, -5), "only that cell");
            e.mark(-40, 100);
            Explored other = new Explored();
            other.mark(3, -5);
            other.mark(64, 64);
            h.assertTrue(e.merge(other) == 1 && e.count() == 3, "merge adds only what was new, was " + e.count());
            h.assertTrue(e.countIn(0, -16, 16) == 1 && e.countIn(64, 64, 4) == 1 && e.countIn(-48, 96, 16) == 1 && e.countIn(16, 16, 16) == 0,
                    "a square's count is exact (zoomed out, clouds keep off known land)");
            AtlasData d = new AtlasData();
            d.dim("minecraft:overworld").merge(e);
            d.trail.add(1);
            d.trail.add(70);
            d.trail.add(-2);
            d.regions.add(new AtlasData.Region(7L, "The Ashen Reach", 900, -400, 8, 14));
            d.pins.add(new AtlasData.Pin("minecraft:overworld", 5, 70, -9));
            var json = AtlasData.CODEC.encodeStart(JsonOps.INSTANCE, d).getOrThrow();
            h.assertTrue(json.getAsJsonObject().get("schemaVersion").getAsInt() == AtlasData.SCHEMA, "schemaVersion saved");
            AtlasData back = AtlasData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
            Explored ow = back.peek("minecraft:overworld");
            h.assertTrue(ow != null && ow.count() == 3 && ow.explored(-40, 100) && ow.explored(64, 64), "cells round-trip");
            h.assertTrue(back.trail().length == 3 && back.trail()[1] == 70, "trail round-trips");
            h.assertTrue(back.regions().size() == 1 && back.regions().getFirst().name().equals("The Ashen Reach"), "regions round-trip");
            h.assertTrue(back.pins().size() == 1 && back.pins().getFirst().z() == -9, "pins round-trip");
            h.assertTrue(Discovery.mapped("minecraft:overworld") && !Discovery.mapped("awaken:gate_cells"), "instanced dimensions are never mapped");
            h.succeed();
        });

        // the path: a circle of 64 blocks clears, a cell past it stays clouded; the hook reads the same record
        AwakenTests.register("atlas_path_clears", 40, h -> {
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            try {
                BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
                Discovery.data(p).dim(Discovery.dim(p.level())).clear();
                int n = Discovery.reveal(p, at, Discovery.RADIUS);
                h.assertTrue(n >= 49, "a 64-block circle clears dozens of cells, was " + n);
                h.assertTrue(AtlasHooks.explored(p, at.offset(60, 0, 0)), "60 blocks east is known");
                h.assertTrue(AtlasHooks.explored(p, at.offset(-40, 0, 40)), "a diagonal within the circle is known");
                h.assertFalse(AtlasHooks.explored(p, at.offset(100, 0, 0)), "100 blocks east isn't");
                h.assertTrue(Discovery.reveal(p, at, Discovery.RADIUS) == 0, "walking the same ground clears nothing new");
                h.succeed();
            } finally {
                Discovery.forget(p);
                h.getLevel().getServer().getPlayerList().remove(p);
            }
        });

        // pins: only on ground you know, twelve at most (the oldest goes), and a pin is taken back by its owner
        AwakenTests.register("atlas_pins", 40, h -> {
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            try {
                BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
                AtlasData d = Discovery.data(p);
                d.dim(Discovery.dim(p.level())).clear();
                d.pins.clear();
                h.assertFalse(Discovery.pin(p, at.getX(), at.getY(), at.getZ(), false), "no pin on ground you haven't seen");
                Discovery.reveal(p, at, 200);
                for (int i = 0; i < AtlasData.PINS_MAX + 2; i++) Discovery.pin(p, at.getX() + i * 10, at.getY(), at.getZ(), false);
                h.assertTrue(d.pins().size() == AtlasData.PINS_MAX && d.pins().getFirst().x() == at.getX() + 20, "twelve pins, the oldest gone");
                h.assertTrue(Discovery.pin(p, at.getX() + 52, at.getY(), at.getZ(), true), "your own pin nearby comes back");
                h.assertTrue(d.pins().size() == AtlasData.PINS_MAX - 1 && d.pins().stream().noneMatch(q -> q.x() == at.getX() + 50), "the nearest one");
                h.succeed();
            } finally {
                Discovery.forget(p);
                h.getLevel().getServer().getPlayerList().remove(p);
            }
        });

        // regions: the Living Wilds region you stand in joins your Atlas once
        AwakenTests.register("atlas_regions", 40, h -> {
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            try {
                AtlasData d = Discovery.data(p);
                d.regions.clear();
                Discovery.region(p);
                Discovery.region(p);
                if (dev.awaken.living.api.Regions.on(p.level())) {
                    h.assertTrue(d.regions().size() == 1 && !d.regions().getFirst().name().isBlank(), "one named region, was " + d.regions());
                } else {
                    h.assertTrue(d.regions().isEmpty(), "no regions outside the overworld");
                }
                h.succeed();
            } finally {
                Discovery.forget(p);
                h.getLevel().getServer().getPlayerList().remove(p);
            }
        });

        // zones: every explored cell goes out named by its Living Wilds region (1-based into the known regions)
        AwakenTests.register("atlas_zones", 40, h -> {
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            try {
                AtlasData d = Discovery.data(p);
                d.dim(Discovery.dim(p.level())).clear();
                d.regions.clear();
                BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
                Discovery.reveal(p, at, 64);
                Explored e = d.peek(Discovery.dim(p.level()));
                Explored.TileBits t = e.tiles().getFirst();
                byte[] z = Discovery.zones(p, Discovery.live(p), t.tx(), t.tz(), t.bits());
                if (dev.awaken.living.api.Regions.on(p.level())) {
                    int named = 0, unnamed = 0;
                    for (int i = 0; i < z.length; i++) {
                        if ((t.bits()[i >> 6] & (1L << (i & 63))) == 0) continue;
                        if (z[i] > 0 && z[i] <= d.regions().size()) named++;
                        else unnamed++;
                    }
                    h.assertTrue(named > 0 && unnamed == 0, "every explored cell has its region: " + named + " named, " + unnamed + " not");
                } else {
                    h.assertTrue(z.length == 0, "no zones outside the overworld");
                }
                h.succeed();
            } finally {
                Discovery.forget(p);
                h.getLevel().getServer().getPlayerList().remove(p);
            }
        });

        // bells: a district's bell clears that district only; the Choir's clears the whole city
        AwakenTests.register("atlas_bell_clears_district", 40, h -> {
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            BlockPos sq = h.absolutePos(new BlockPos(1, 1, 1));
            CityHooks.Access real = CityHooks.installed();
            CityHooks.install(new CityHooks.Access() {
                @Override public boolean inCity(Level level, BlockPos pos) {
                    return pos.distSqr(new BlockPos(sq.getX(), pos.getY(), sq.getZ())) < 240 * 240;
                }

                @Override public @Nullable String district(Level level, BlockPos pos) {
                    if (!inCity(level, pos)) return null;
                    return pos.getX() < sq.getX() ? "quay" : "garden";
                }

                @Override public @Nullable BlockPos square(Level level) {
                    return sq;
                }
            });
            try {
                Discovery.data(p).dim(Discovery.dim(p.level())).clear();
                Bells.Bell quay = null, city = null;
                for (Bells.Bell b : Bells.all()) {
                    if ("quay".equals(b.district())) quay = b;
                    if (b.wholeCity()) city = b;
                }
                h.assertTrue(quay != null && city != null, "the plan has Lantern Quay's bell and the Choir's");
                int n = Discovery.ring(p, quay);
                h.assertTrue(n > 100, "the Quay's bell clears its district, was " + n);
                h.assertTrue(AtlasHooks.explored(p, sq.offset(-150, 0, 20)), "deep in the Quay is known");
                h.assertFalse(AtlasHooks.explored(p, sq.offset(150, 0, 20)), "the Garden Ward isn't");
                int m = Discovery.ring(p, city);
                h.assertTrue(m > 100 && AtlasHooks.explored(p, sq.offset(150, 0, 20)), "the Choir's bell clears the rest of the city");
                h.assertFalse(AtlasHooks.explored(p, sq.offset(400, 0, 0)), "outside the wall stays clouded");
                h.succeed();
            } finally {
                CityHooks.install(real);
                Discovery.forget(p);
                h.getLevel().getServer().getPlayerList().remove(p);
            }
        });
    }
}
