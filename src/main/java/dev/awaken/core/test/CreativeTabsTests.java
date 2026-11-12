package dev.awaken.core.test;

import dev.awaken.Awaken;
import dev.awaken.core.api.Tabs;
import net.minecraft.world.item.CreativeModeTab;

/**
 * The creative menu's check (SPEC §1, "Creative tabs"): every Awaken item in exactly one tab, no stack placed twice,
 * every item shown in several kinds declared so JEI keeps the kinds apart, and no shelf whose filler throws. The same
 * report as {@code /awaken core tabs}.
 */
final class CreativeTabsTests {
    private CreativeTabsTests() {}

    static void register() {
        AwakenTests.register("core_creative_tabs", 20, h -> {
            var level = h.getLevel();
            Tabs.Report r = Tabs.audit(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), true, level.registryAccess()));
            Awaken.LOG.info("[core] creative tabs: {}", r.summary());
            if (!r.clean()) h.fail(r.summary());
            h.succeed();
        });
    }
}
