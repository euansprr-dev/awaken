package dev.awaken.descent.player;

import dev.awaken.core.AwakenConfig;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * What a death in the Descent costs, by difficulty (SPEC section 11). STORY keeps half the Deep Pack straight away and
 * leaves the other half in the Grave Echo; HUNTER leaves all of it in the Grave Echo; HARDLINE and HARDCORE leave all of
 * it and keep only one Grave Echo, so dying again before you reach it loses the first.
 */
public final class DeathRules {
    private DeathRules() {}

    public record Split(List<ItemStack> kept, List<ItemStack> echo) {}

    public static Split split(AwakenConfig.Difficulty difficulty, List<ItemStack> pack) {
        List<ItemStack> kept = new ArrayList<>(), echo = new ArrayList<>();
        if (difficulty != AwakenConfig.Difficulty.STORY) {
            for (ItemStack s : pack) if (!s.isEmpty()) echo.add(s.copy());
            return new Split(kept, echo);
        }
        boolean keepNextSingle = true;
        for (ItemStack s : pack) {
            if (s.isEmpty()) continue;
            if (s.getCount() == 1) {
                // unstackable things alternate, starting with "kept"
                (keepNextSingle ? kept : echo).add(s.copy());
                keepNextSingle = !keepNextSingle;
                continue;
            }
            int keep = (s.getCount() + 1) / 2;
            kept.add(s.copyWithCount(keep));
            echo.add(s.copyWithCount(s.getCount() - keep));
        }
        return new Split(kept, echo);
    }

    /** How many Grave Echoes a player may have at once; the oldest crumbles when a new one would exceed it. */
    public static int maxEchoes(AwakenConfig.Difficulty difficulty) {
        return switch (difficulty) {
            case STORY, HUNTER -> 3;
            case HARDLINE, HARDCORE -> 1;
        };
    }
}
