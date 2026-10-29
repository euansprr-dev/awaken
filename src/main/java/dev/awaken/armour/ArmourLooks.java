package dev.awaken.armour;

import dev.awaken.gear.HunterGearItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorType;
import org.jspecify.annotations.Nullable;

/** Which set's meshes a worn item is drawn with (SPEC §44.1): the six Hunter sets by their items. */
public final class ArmourLooks {
    private ArmourLooks() {}

    /** A set's look and the piece it is (its meshes: assets/awaken/armour/&lt;set&gt;/&lt;piece&gt;_&lt;bone&gt;.mesh). */
    public record Look(ArmourSet set, ArmorType piece) {}

    private static final Look[][] HUNTER = new Look[dev.awaken.gear.HunterTier.values().length][ArmorType.values().length];

    public static @Nullable Look of(ItemStack stack) {
        if (stack.isEmpty()) return null;
        Item item = stack.getItem();
        if (item instanceof HunterGearItem g && g.piece.armorType != null) {
            Look[] row = HUNTER[g.tier.ordinal()];
            Look look = row[g.piece.armorType.ordinal()];
            if (look == null) row[g.piece.armorType.ordinal()] = look = new Look(ArmourSet.of(g.tier), g.piece.armorType);
            return look;
        }
        return null;
    }
}
