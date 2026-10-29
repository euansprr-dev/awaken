package dev.awaken.armour;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorType;
import org.jspecify.annotations.Nullable;

/** Which set's meshes a worn item is drawn with (docs/armour/PLAN.md): today the probe's four pieces. */
public final class ArmourLooks {
    private ArmourLooks() {}

    /** A set's look and the piece it is (its meshes: assets/awaken/armour/<set>/<piece>_<bone>.mesh). */
    public record Look(String set, ArmorType piece) {}

    public static @Nullable Look of(ItemStack stack) {
        if (stack.isEmpty()) return null;
        Item item = stack.getItem();
        for (var e : ArmourModule.PROBE.entrySet()) {
            if (e.getValue().get() == item) return new Look("lordscale", e.getKey());
        }
        return null;
    }
}
