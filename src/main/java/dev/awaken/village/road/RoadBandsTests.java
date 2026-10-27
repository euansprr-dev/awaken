package dev.awaken.village.road;

import dev.awaken.city.VillageStyles;
import dev.awaken.core.test.AwakenTests;
import net.minecraft.world.level.block.state.BlockState;

/** The road's surface reads as bands along it, not a coin toss a block (SPEC §31, P4). */
public final class RoadBandsTests {
    private RoadBandsTests() {}

    public static void register() {
        AwakenTests.register("ground_road_bands", 20, h -> {
            for (int family : new int[] {VillageStyles.DESERT, VillageStyles.ELDER, VillageStyles.HIGHLAND, VillageStyles.MIRE}) {
                // the crown (d 0.3) and a rut (d 1.0) along x: how often the ground changes between neighbouring blocks
                for (float d : new float[] {0.3f, 1.0f}) {
                    int changes = 0, n = 800;
                    BlockState prev = RoadPaint.paving(family, d, false, 1000, 77);
                    int[] counts = new int[3];
                    for (int i = 1; i < n; i++) {
                        BlockState s = RoadPaint.paving(family, d, false, 1000 + i, 77);
                        if (s != prev) changes++;
                        prev = s;
                    }
                    // a coin toss between three or four grounds changes about half the time; four-block patches with ragged edges well under a third
                    h.assertTrue(changes < n * 0.30, "family " + family + " d " + d + ": changes " + changes + " of " + n + " are patches, not a coin toss");
                    h.assertTrue(changes > 10, "family " + family + " d " + d + ": it still varies (" + changes + ")");
                }
            }
            // a town's setts stay a mix of every block
            int changes = 0;
            BlockState prev = RoadPaint.paving(VillageStyles.HIGHLAND, 0.3f, true, 2000, 5);
            for (int i = 1; i < 400; i++) {
                BlockState s = RoadPaint.paving(VillageStyles.HIGHLAND, 0.3f, true, 2000 + i, 5);
                if (s != prev) changes++;
                prev = s;
            }
            h.assertTrue(changes > 400 * 0.4, "town paving is a mix of stones block by block: " + changes);
            h.succeed();
        });
    }
}
