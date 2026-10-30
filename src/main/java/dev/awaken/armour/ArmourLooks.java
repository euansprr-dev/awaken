package dev.awaken.armour;

import dev.awaken.gear.HunterGearItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorType;
import org.jspecify.annotations.Nullable;

/**
 * Which set's meshes a worn item is drawn with (SPEC §44.1): the six Hunter sets by their items, the others by their own;
 * {@link #drawn} with the look the piece wears (SPEC §44.8), which never changes what it does.
 */
public final class ArmourLooks {
    private ArmourLooks() {}

    /** A set's look and the piece it is (its meshes: assets/awaken/armour/&lt;set&gt;/&lt;piece&gt;_&lt;bone&gt;.mesh). */
    public record Look(ArmourSet set, ArmorType piece) {}

    private static final Look[][] HUNTER = new Look[dev.awaken.gear.HunterTier.values().length][ArmorType.values().length];
    private static final Look[][] OWN = new Look[ArmourSet.values().length][4];
    private static final Look[][] ANY = new Look[ArmourSet.values().length][4];

    /** A set's piece as a look (cached). */
    public static Look look(ArmourSet set, ArmorType piece) {
        Look[] row = ANY[set.ordinal()];
        Look l = row[ForgeSong.slot(piece)];
        if (l == null) row[ForgeSong.slot(piece)] = l = new Look(set, piece);
        return l;
    }

    /** The look a worn item is drawn with: the set whose look it wears (one made, of the same piece), else its own. */
    public static @Nullable Look drawn(ItemStack stack) {
        Look own = of(stack);
        if (own == null) return null;
        Forged f = stack.get(ArmourContent.FORGED.get());
        if (f == null || f.look().isEmpty() || f.look().equals(own.set().id)) return own;
        ArmourSet other = ArmourSet.byId(f.look());
        return other != null && other.made() ? look(other, own.piece()) : own;
    }

    public static @Nullable Look of(ItemStack stack) {
        if (stack.isEmpty()) return null;
        Item item = stack.getItem();
        if (item instanceof HunterGearItem g && g.piece.armorType != null) {
            Look[] row = HUNTER[g.tier.ordinal()];
            Look look = row[g.piece.armorType.ordinal()];
            if (look == null) row[g.piece.armorType.ordinal()] = look = new Look(ArmourSet.of(g.tier), g.piece.armorType);
            return look;
        }
        if (item instanceof ForgedArmourItem f) {
            Look[] row = OWN[f.set.ordinal()];
            Look look = row[ForgeSong.slot(f.piece)];
            if (look == null) row[ForgeSong.slot(f.piece)] = look = new Look(f.set, f.piece);
            return look;
        }
        return null;
    }
}
