package dev.awaken.armour;

import net.neoforged.bus.api.IEventBus;

/**
 * The Forged Armour (SPEC §44, docs/armour/PLAN.md): 29 sets drawn as sculpted meshes on the body's bones by
 * {@code armour.client.ArmourLayer}, in place of vanilla's flat shell. P0: the six Hunter sets (module {@code gear}) in
 * their new look.
 */
public final class ArmourModule {
    private ArmourModule() {}

    public static void init(IEventBus modBus) {
        ArmourTests.register();
    }
}
