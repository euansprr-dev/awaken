package dev.awaken.compat;

import dev.awaken.Awaken;
import dev.awaken.core.test.AwakenTests;
import net.minecraft.server.level.ServerLevel;

/** The far-view policy covers every dimension this build registers: vanilla and the Stillpoint open, the rest closed. */
final class CompatTests {
    private CompatTests() {}

    static void register() {
        AwakenTests.register("compat_far_view_policy", 20, h -> {
            int closed = 0;
            for (ServerLevel level : h.getLevel().getServer().getAllLevels()) {
                String id = level.dimension().identifier().toString();
                boolean awaken = id.startsWith(Awaken.MODID + ":");
                boolean open = FarView.OPEN.contains(id);
                h.assertTrue(FarView.allowed(id) == (!awaken || open), "far view for " + id);
                if (awaken && !open) closed++;
            }
            h.assertTrue(FarView.allowed("minecraft:overworld") && FarView.allowed("awaken:stillpoint")
                    && !FarView.allowed("awaken:gates"), "far view basics");
            h.assertTrue(closed >= 10, "instanced Awaken dimensions found: " + closed);
            h.succeed();
        });
    }
}
