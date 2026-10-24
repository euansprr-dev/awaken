package dev.awaken.turbo;

import dev.awaken.core.test.AwakenTests;
import net.neoforged.bus.api.IEventBus;

/**
 * Turbo's common side: only its GameTests (Turbo itself is client only, see {@link Turbo}). No Minecraft internals here.
 */
public final class TurboModule {
    private TurboModule() {}

    public static void init(IEventBus modBus) {
        // The version lock names this build: after a Minecraft or NeoForge update this fails until Turbo's mixins, its
        // access transformer and its tours are re-verified on the new versions and they join TurboVersions.VERIFIED.
        AwakenTests.register("turbo_version_lock_current", 20, h -> {
            String problem = TurboVersions.problem();
            h.assertTrue(problem == null, "Turbo is version-locked: " + problem + " (re-verify Turbo, then add the versions)");
            h.succeed();
        });
    }
}
