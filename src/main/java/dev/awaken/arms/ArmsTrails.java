package dev.awaken.arms;

import dev.awaken.core.api.CityHooks;
import dev.awaken.core.api.TrailsApi;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Arms' words for trails (docs/trails/PLAN.md §3.3, §4.2):
 * <pre>
 * style:spear     the fighter knows that style's pattern      styles:3   knows that many beside the Longsword
 * arms:issued     Hale's issue is taken                       arms:marked  has had a Marked weapon drop
 * arms:lordforged holds a Lord-forged weapon                  armstier:1   holds a weapon of that tier (0 Wildhide to 5 Sovereign) or higher
 * </pre>
 * and the place "quartermaster" (Hale) for a step to point at.
 */
final class ArmsTrails {
    private ArmsTrails() {}

    static void init() {
        TrailsApi.condition("style", (p, rest) -> ArmsIssue.INSTANCE.knows(p, rest));
        TrailsApi.condition("styles", (p, rest) -> {
            try {
                return ArmsProgress.of(p).styles.stream().filter(s -> !s.equals(Arms.LONGSWORD)).count() >= Integer.parseInt(rest);
            } catch (NumberFormatException e) {
                return false;
            }
        });
        TrailsApi.condition("arms", (p, rest) -> switch (rest) {
            case "issued" -> ArmsProgress.of(p).issued;
            case "marked" -> ArmsProgress.of(p).firstMarked;
            case "lordforged" -> holdsLordForged(p);
            default -> false;
        });
        TrailsApi.condition("armstier", (p, rest) -> {
            try {
                return bestTier(p) >= Integer.parseInt(rest);
            } catch (NumberFormatException e) {
                return false;
            }
        });
        TrailsApi.place("quartermaster", p -> CityHooks.spot(p.level(), "quartermaster"));
    }

    static boolean holdsLordForged(ServerPlayer p) {
        for (LordForged.Forged f : LordForged.ALL) {
            var item = ArmsItems.item(f.weapon());
            if (item != null && ArmsForge.count(p, item) > 0) return true;
        }
        return false;
    }

    /** The highest tier of a tier-named weapon (Wildhide to Sovereign) the fighter holds, or -1. */
    static int bestTier(ServerPlayer p) {
        int best = -1;
        for (ItemStack s : p.getInventory().getNonEquipmentItems()) best = Math.max(best, tier(s));
        for (EquipmentSlot slot : EquipmentSlot.values()) best = Math.max(best, tier(p.getItemBySlot(slot)));
        return best;
    }

    private static int tier(ItemStack s) {
        if (s.isEmpty() || ArmsItems.of(s.getItem()) == null) return -1;
        String path = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
        for (int i = ArmsStands.PREFIX.length - 1; i >= 0; i--) if (path.startsWith(ArmsStands.PREFIX[i] + "_")) return i;
        return -1;
    }
}
