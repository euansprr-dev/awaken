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
            AtlasData d = new AtlasData();
            d.dim("minecraft:overworld").merge(e);
            d.trail.add(1);
            d.trail.add(70);
            d.trail.add(-2);
            var json = AtlasData.CODEC.encodeStart(JsonOps.INSTANCE, d).getOrThrow();
            h.assertTrue(json.getAsJsonObject().get("schemaVersion").getAsInt() == AtlasData.SCHEMA, "schemaVersion saved");
            AtlasData back = AtlasData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
            Explored ow = back.peek("minecraft:overworld");
            h.assertTrue(ow != null && ow.count() == 3 && ow.explored(-40, 100) && ow.explored(64, 64), "cells round-trip");
            h.assertTrue(back.trail().length == 3 && back.trail()[1] == 70, "trail round-trips");
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
