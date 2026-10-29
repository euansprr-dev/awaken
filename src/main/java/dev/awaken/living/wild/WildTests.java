package dev.awaken.living.wild;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.living.api.Wildness;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/** The Wild Lands' Stage 0 (SPEC §41.1): the field's shape, and each chunk's record kept, saved and sent intact. */
public final class WildTests {
    private WildTests() {}

    public static void register() {
        // the field: tamed inside a tamer, fading smoothly to wild past it; tamed land stays tamed whatever the noise
        AwakenTests.register("wild_field", 20, h -> {
            h.assertTrue(WildField.touch(0, 60, 260) == 0 && WildField.touch(59, 60, 260) == 0, "a settlement's own ground is fully tamed");
            h.assertTrue(WildField.touch(60 + 260, 60, 260) > 0.6 && WildField.touch(60 + 260 * WildField.REACHES, 60, 260) > 0.985,
                    "its touch fades over its reach, and is gone by the edge of the look");
            double last = -1;
            for (int d = 0; d < 1200; d += 7) {
                double k = WildField.touch(d, 60, 260);
                h.assertTrue(k >= last - 1e-9, "the farther, the wilder: " + d);
                last = k;
            }
            long seed = 764383433107166945L;
            int high = 0;
            for (int i = 0; i < 400; i++) {
                int x = i * 397 - 80000, z = i * 211 - 40000;
                h.assertTrue(WildField.combine(0, 5, seed, x, z) == 0, "fully tamed land stays tamed in any land and patch");
                double w = WildField.combine(1, i % 6, seed, x, z);
                h.assertTrue(w >= 0 && w <= 1, "in range");
                if (w >= Wildness.WILD) high++;
                double n = WildField.noise(seed, x, z, WildField.SMALL), n1 = WildField.noise(seed, x + 1, z, WildField.SMALL);
                h.assertTrue(n >= 0 && n <= 1 && Math.abs(n - n1) < 0.02, "the patches change smoothly, block to block");
            }
            h.assertTrue(high > 200, "most land far from people is wild or deep: " + high + "/400");
            h.assertTrue(Wildness.band(0.1) == Wildness.Band.TAMED && Wildness.band(0.3) == Wildness.Band.PASTURE
                    && Wildness.band(0.6) == Wildness.Band.WILD && Wildness.band(0.9) == Wildness.Band.DEEP, "the bands");
            h.succeed();
        });

        // a chunk's record: filled from the land at its cells' centres, read back by block, saved and sent intact
        AwakenTests.register("wild_record", 40, h -> {
            ServerLevel level = h.getLevel();
            BlockPos o = h.absolutePos(BlockPos.ZERO);
            LevelChunk chunk = level.getChunkAt(o);
            WildRecord.Cells c = WildRecord.fill(level, chunk);
            h.assertTrue(WildRecord.existing(chunk) == c && WildRecord.of(level, chunk) == c, "kept on the chunk");
            int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
            for (int i = 0; i < 16; i++) {
                int x = bx + (i & 3) * 4 + 2, z = bz + (i >> 2) * 4 + 2;
                double land = Wildness.land(level, x, z);
                h.assertTrue(Math.abs(c.cell(i) - land) <= 0.5 / 255 + 1e-9 && Math.abs(c.at(x - 2, z + 1) - c.cell(i)) == 0,
                        "cell " + i + " holds the land at its centre and answers for its 4 by 4 blocks");
            }
            h.assertTrue(Wildness.land(level, bx + 3, bz + 9) == Wildness.land(level, bx + 3, bz + 9), "the land's value is the same each time");
            h.assertTrue(Math.abs(Wildness.at(level, bx + 6, bz + 6) - c.at(bx + 6, bz + 6) * Wildness.hearthKeep(level, bx + 6, bz + 6)) < 1e-9,
                    "a loaded chunk answers from its record");

            var saved = WildRecord.Cells.CODEC.codec().encodeStart(NbtOps.INSTANCE, c).getOrThrow();
            WildRecord.Cells back = WildRecord.Cells.CODEC.codec().parse(NbtOps.INSTANCE, saved).getOrThrow();
            h.assertTrue(java.util.Arrays.equals(back.cells, c.cells), "saved and loaded intact");
            var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
            var sync = new WildRecord.Sync();
            sync.write(buf, c, true);
            h.assertTrue(buf.readableBytes() == 16, "sixteen bytes to a player");
            h.assertTrue(java.util.Arrays.equals(sync.read(chunk, buf, null).cells, c.cells), "and read back intact");
            buf.release();
            h.succeed();
        });
    }
}
