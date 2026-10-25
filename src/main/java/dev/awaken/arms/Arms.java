package dev.awaken.arms;

import net.minecraft.core.component.DataComponents;
import dev.awaken.core.api.Strikes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Arms' public face (SPEC §12, §24): which fighting style a weapon fights with, whether it guards, a creature's
 * stance, and the sound of a told blow. Any melee weapon gets the three verbs: a sword (vanilla's, the Hunter blades, other mods' "_sword" items)
 * fights as a plain Longsword until its own style arrives.
 */
public final class Arms {
    private Arms() {}

    public static final String LONGSWORD = "longsword";

    /** The style an item fights with, or null for anything that isn't a melee weapon (tools, bare hands stay vanilla). */
    public static Archetypes.@Nullable Loaded styleOf(ItemStack stack) {
        if (stack.isEmpty()) return null;
        return styleOf(stack.getItem());
    }

    public static Archetypes.@Nullable Loaded styleOf(Item item) {
        String style = plainStyle(BuiltInRegistries.ITEM.getKey(item));
        return style == null ? null : Archetypes.get(style);
    }

    /**
     * The style for an item id (the same rule the default components use before tags exist): our weapons fight with
     * their own row's style, every other sword as a plain Longsword.
     */
    static @Nullable String plainStyle(Identifier id) {
        ArmsData.Weapon own = ArmsItems.byId(id);
        if (own != null) return own.style();
        String path = id.getPath();
        if (path.endsWith("_sword") || (id.getNamespace().equals("awaken") && path.endsWith("_blade"))) return LONGSWORD;
        return null;
    }

    /** Whether this stack raises a guard on right mouse (a style and the blocking component). */
    public static boolean guards(ItemStack stack) {
        return styleOf(stack) != null && stack.has(DataComponents.BLOCKS_ATTACKS);
    }

    /** Whether Arms runs this player's melee (the world switch and a weapon with a style). */
    public static boolean fights(LivingEntity e) {
        return ArmsConfig.enabled() && styleOf(e.getMainHandItem()) != null;
    }

    /** A creature's stance as a share of its hold (0..1; server), 0 when untouched. */
    public static float posture(LivingEntity e) {
        Posture p = e.getExistingDataOrNull(ArmsContent.POSTURE);
        return p == null || p.max <= 0 ? 0 : p.value / p.max;
    }

    /** Whether a creature's stance is broken right now (server). */
    public static boolean broken(LivingEntity e) {
        Posture p = e.getExistingDataOrNull(ArmsContent.POSTURE);
        return p != null && p.broken(ArmsServer.now(e));
    }

    /**
     * The sound a creature makes telling a blow of this class (server; the mobs module plays it with the tell). Each
     * class has one voice on every creature, so the ear learns the answer before the eye does. Null for NONE.
     */
    public static @Nullable SoundEvent tellSound(Strikes.Kind kind) {
        return ArmsSounds.tell(kind);
    }
}
