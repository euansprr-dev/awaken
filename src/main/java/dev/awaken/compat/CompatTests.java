package dev.awaken.compat;

import dev.awaken.Awaken;
import dev.awaken.core.test.AwakenTests;

/** The far-view policy covers every dimension the data packs define: vanilla and the Stillpoint open, the rest closed. */
final class CompatTests {
    private CompatTests() {}

    static void register() {
        AwakenTests.register("compat_far_view_policy", 20, h -> {
            int closed = 0;
            // every dimension the data packs define (the GameTest world itself only has the overworld)
            var files = h.getLevel().getServer().getResourceManager().listResources("dimension", f -> f.getPath().endsWith(".json"));
            for (var file : files.keySet()) {
                String path = file.getPath();
                String id = file.getNamespace() + ":" + path.substring("dimension/".length(), path.length() - ".json".length());
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
