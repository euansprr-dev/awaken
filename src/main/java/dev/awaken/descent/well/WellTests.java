package dev.awaken.descent.well;

import dev.awaken.Awaken;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.descent.DescentContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The Well's new look built on a meadow in the test level (a short shaft): the shaft, its lining and pool, the lift and
 * the rim where climbers come out, the four lantern pylons whose light sits where the landmark glow is drawn, the ring of
 * great pillars with their crowns of light, the terraces, and a build next to it left alone. Written to
 * run/structures/well.json for {@code tools/art/specs/structures.py}.
 */
public final class WellTests {
    private WellTests() {}

    static final int UP = 200, R = Well.RING_OUTER + 2;

    public static void register() {
        AwakenTests.registerIsolated("descent_well_new_look", 400, h -> {
            ServerLevel level = h.getLevel();
            BlockPos c = h.absolutePos(new BlockPos(0, UP, 0));
            try {
                for (int dx = -R; dx <= R; dx++) {
                    for (int dz = -R; dz <= R; dz++) {
                        level.setBlock(c.offset(dx, 0, dz), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                        for (int y = 1; y <= 18; y++) level.setBlock(c.offset(dx, -y, dz), y < 3 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
                BlockPos plank = c.offset(20, 3, 3);
                level.setBlock(plank, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_CLIENTS);
                WellData d = new WellData();
                d.x = c.getX();
                d.y = c.getY();
                d.z = c.getZ();
                d.bottom = c.getY() - 14;
                d.state = WellData.BUILT;
                Well.buildNow(level, d);
                int y0 = d.y;
                h.assertTrue(level.getBlockState(new BlockPos(d.x, y0 - 6, d.z)).isAir(), "the shaft is open");
                BlockState lining = level.getBlockState(new BlockPos(d.x + Well.RING_INNER + 1, y0 - 8, d.z));
                h.assertTrue(lining.is(DescentContent.WELL_STONE.get()) || lining.is(DescentContent.WELL_GLYPH.get()), "the shaft is lined");
                h.assertTrue(level.getBlockState(new BlockPos(d.x, d.bottom, d.z)).is(Blocks.WATER), "a pool at the bottom");
                h.assertTrue(level.getBlockState(new BlockPos(d.x + Well.RING_INNER + 3, y0 + 1, d.z)).is(DescentContent.WELL_LIFT.get()), "the lift on the court");
                BlockPos rim = d.rim();
                h.assertTrue(level.getBlockState(rim).isAir() && level.getBlockState(rim.above()).isAir() && !level.getBlockState(rim.below()).isAir(),
                        "climbers come out standing on the court");
                for (int sx = -1; sx <= 1; sx += 2) {
                    for (int sz = -1; sz <= 1; sz += 2) {
                        h.assertTrue(level.getBlockState(new BlockPos(d.x + sx * 8, y0 + 7, d.z + sz * 8)).is(DescentContent.WELL_LIGHT.get()),
                                "a lantern in each corner pylon, where the landmark glow is drawn");
                    }
                }
                int crowns = 0, tall = 0, lights = 0;
                for (int k = 0; k < Well.PILLARS; k++) {
                    double a = Well.pillarAngle(k);
                    int px = d.x + (int) Math.round(Math.cos(a) * Well.PILLAR_RADIUS), pz = d.z + (int) Math.round(Math.sin(a) * Well.PILLAR_RADIUS);
                    int top = y0;
                    for (int y = y0 + 1; y <= y0 + 26; y++) if (!level.getBlockState(new BlockPos(px, y, pz)).isAir()) top = y;
                    if (top - y0 >= 16) tall++;
                    for (int y = y0; y <= y0 + 26; y++) if (level.getBlockState(new BlockPos(px, y, pz)).is(DescentContent.WELL_LIGHT.get())) crowns++;
                }
                for (BlockPos q : BlockPos.betweenClosed(c.offset(-R, 0, -R), c.offset(R, 26, R))) if (level.getBlockState(q).getLightEmission() >= 10) lights++;
                h.assertTrue(tall >= 8, "the great pillars stand 16+ high: " + tall);
                h.assertTrue(crowns >= 8, "and are crowned with light: " + crowns);
                h.assertTrue(lights >= 40, "it glows at night: " + lights + " bright blocks");
                h.assertTrue(level.getBlockState(new BlockPos(d.x + 14, y0 + 1, d.z)).isSolidRender(), "the first terrace");
                h.assertTrue(level.getBlockState(new BlockPos(d.x, y0 + 2, d.z + 17)).isSolidRender(), "the pillar terrace");
                h.assertTrue(level.getBlockState(plank).is(Blocks.OAK_PLANKS), "a build beside the Well is left alone");
                dump(level, c, c.offset(-R, -8, -R), c.offset(R, 26, R));
            } finally {
                for (BlockPos q : BlockPos.betweenClosed(c.offset(-R, -20, -R), c.offset(R, 30, R))) {
                    if (!level.getBlockState(q).isAir()) level.setBlock(q, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
            h.succeed();
        });
    }

    static void dump(ServerLevel level, BlockPos origin, BlockPos lo, BlockPos hi) {
        StringBuilder sb = new StringBuilder("{\"name\":\"well\",\"kind\":\"well\",\"blocks\":[");
        boolean first = true;
        for (BlockPos q : BlockPos.betweenClosed(lo, hi)) {
            BlockState st = level.getBlockState(q);
            if (st.isAir()) continue;
            if (!first) sb.append(',');
            first = false;
            var id = BuiltInRegistries.BLOCK.getKey(st.getBlock());
            sb.append('[').append(q.getX() - origin.getX()).append(',').append(q.getY() - origin.getY()).append(',').append(q.getZ() - origin.getZ())
                    .append(",\"").append(id.getNamespace().equals("minecraft") ? id.getPath() : id.toString()).append("\",\"");
            boolean firstProp = true;
            for (Property<?> p : st.getProperties()) {
                if (!firstProp) sb.append(',');
                firstProp = false;
                sb.append(p.getName()).append('=').append(value(st, p));
            }
            sb.append("\"]");
        }
        sb.append("]}");
        try {
            Path dir = Path.of("structures");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("well.json"), sb.toString());
        } catch (IOException e) {
            Awaken.LOG.warn("[descent] could not write the Well dump: {}", e.toString());
        }
    }

    private static <T extends Comparable<T>> String value(BlockState s, Property<T> p) {
        return p.getName(s.getValue(p));
    }
}
