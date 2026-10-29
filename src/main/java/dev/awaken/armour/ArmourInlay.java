package dev.awaken.armour;

import dev.awaken.arms.ArmsForge;
import dev.awaken.core.Forces;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * C's verb, Inlay (SPEC §44.4, "Inlay"): a catalyst chosen at the start (the weapons' five: Sunshard, Frostspar, Fault
 * Seam, copper, Deepglass) is turned into the finished piece's socket. Its Force sets the piece's accent colour and gives
 * a small stat, a piece at a time, whatever set it is on.
 */
public final class ArmourInlay {
    private ArmourInlay() {}

    /** A piece's share: Heat melee damage, Frost toughness, Gravity footing, Charge speed, Spirit health. */
    public static final double HEAT_DAMAGE = 0.015, FROST_TOUGHNESS = 0.25, GRAVITY_FOOTING = 0.03, CHARGE_SPEED = 0.015, SPIRIT_HEALTH = 0.5;

    public static Forces.Force[] forces() {
        return Forces.Force.values();
    }

    /** The catalyst item of a Force (air if its module isn't loaded). */
    public static Item catalyst(Forces.Force f) {
        return ArmsForge.catalystItem(f);
    }

    /** The Force whose catalyst this stack is, or null. */
    public static Forces.@Nullable Force of(ItemStack stack) {
        for (Forces.Force f : Forces.Force.values()) {
            Item c = catalyst(f);
            if (c != Items.AIR && stack.is(c)) return f;
        }
        return null;
    }

    /** The accent an inlay gives (the Forces' own colours). */
    public static int colour(Forces.Force f) {
        return switch (f) {
            case HEAT -> 0xFFFF9A3C;
            case CHARGE -> 0xFF9AD8FF;
            case GRAVITY -> 0xFF9A6BFF;
            case SPIRIT -> 0xFF86E0A0;
            case FROST -> 0xFFD8F0FF;
        };
    }

    public static String catalystName(Forces.Force f) {
        return switch (f) {
            case HEAT -> "Sunshard";
            case CHARGE -> "copper";
            case GRAVITY -> "Fault Seam";
            case SPIRIT -> "Deepglass";
            case FROST -> "Frostspar";
        };
    }

    /** What one inlaid piece gives, plainly. */
    public static String stat(Forces.Force f) {
        return switch (f) {
            case HEAT -> "+1.5% melee damage";
            case CHARGE -> "+1.5% speed";
            case GRAVITY -> "blows push you back 3% less";
            case SPIRIT -> "+a quarter heart";
            case FROST -> "+0.25 toughness";
        };
    }

    public static String line(Forces.Force f) {
        return "Inlaid with " + catalystName(f) + ": " + stat(f);
    }
}
