package dev.awaken.anims;

import net.neoforged.bus.api.IEventBus;

/**
 * Hands, the first-person system (SPEC §24): its public face is core.api.FirstPerson, its client anims.client. The
 * common side holds the clip data ({@link FpClipSet}), the curves ({@link Ease}) and the GameTest.
 */
public final class AnimsModule {
    private AnimsModule() {}

    public static void init(IEventBus modBus) {
        AnimsTests.register();
    }
}
