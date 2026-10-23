package dev.awaken.above.frame;

import dev.awaken.above.AboveItems;
import dev.awaken.above.MachineType;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.ArmorType;

import java.util.List;
import java.util.Set;

/**
 * Frame modules: items socketed into Frame pieces at a Frame Forge. Each draws essence from the cells you carry.
 * {@link #fits} says which pieces take it.
 */
public enum FrameModule {
    JET_BOOTS("jet_boots", "Jet Boots", "Hold jump in the air to thrust upward. 1 essence per tick of thrust.",
            Set.of(ArmorType.BOOTS),
            List.of(part(() -> AboveItems.CIRCUIT.get(), 2), part(() -> AboveItems.SCRAP.get(), 4), part(() -> Items.BLAZE_POWDER, 2))),
    SHIELD_EMITTER("shield_emitter", "Shield Emitter", "Regrows a 4-heart shield every 20 seconds. 40 essence per charge.",
            Set.of(ArmorType.CHESTPLATE),
            List.of(part(() -> AboveItems.CIRCUIT.get(), 3), part(() -> Items.SHIELD, 1), part(() -> Items.AMETHYST_SHARD, 2))),
    STEALTH_FIELD("stealth_field", "Stealth Field", "Sneak and hold still to vanish. 1 essence per half second.",
            Set.of(ArmorType.LEGGINGS, ArmorType.CHESTPLATE),
            List.of(part(() -> AboveItems.CIRCUIT.get(), 2), part(() -> Items.PHANTOM_MEMBRANE, 2), part(() -> Items.FERMENTED_SPIDER_EYE, 1))),
    MAGNET("magnet", "Magnet", "Pulls nearby items to you. 1 essence per pull.",
            Set.of(ArmorType.CHESTPLATE, ArmorType.LEGGINGS),
            List.of(part(() -> AboveItems.CIRCUIT.get(), 2), part(() -> Items.IRON_INGOT, 4), part(() -> Items.REDSTONE, 2))),
    NIGHT_VISION("night_vision", "Night Vision", "See in the dark. 2 essence every 10 seconds.",
            Set.of(ArmorType.HELMET),
            List.of(part(() -> AboveItems.CIRCUIT.get(), 2), part(() -> Items.GOLDEN_CARROT, 1), part(() -> Items.GLOWSTONE_DUST, 2))),
    STEP_ASSIST("step_assist", "Step Assist", "Walk up full blocks without jumping. No essence.",
            Set.of(ArmorType.BOOTS, ArmorType.LEGGINGS),
            List.of(part(() -> AboveItems.CIRCUIT.get(), 2), part(() -> AboveItems.SCRAP.get(), 4), part(() -> Items.PISTON, 1)));

    public final String id;
    public final String displayName;
    public final String description;
    private final Set<ArmorType> fits;
    private final List<MachineType.Part> parts;

    FrameModule(String id, String displayName, String description, Set<ArmorType> fits, List<MachineType.Part> parts) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.fits = fits;
        this.parts = parts;
    }

    public boolean fits(ArmorType type) {
        return fits.contains(type);
    }

    public Set<ArmorType> fitsAll() {
        return fits;
    }

    public List<MachineType.Part> parts() {
        return parts;
    }

    public int bit() {
        return 1 << ordinal();
    }

    public static FrameModule byId(String id) {
        for (FrameModule m : values()) if (m.id.equals(id)) return m;
        return null;
    }

    /** Module slots per Frame piece. */
    public static int slots(ArmorType type) {
        return type == ArmorType.HELMET ? 1 : 2;
    }

    private static MachineType.Part part(java.util.function.Supplier<? extends net.minecraft.world.item.Item> item, int count) {
        return new MachineType.Part(item, count);
    }
}
