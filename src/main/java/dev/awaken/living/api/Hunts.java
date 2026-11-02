package dev.awaken.living.api;

import dev.awaken.core.api.CreatureTag;
import dev.awaken.living.population.Ledger;
import net.minecraft.world.entity.LivingEntity;

/** The hunts for other modules (SPEC §41): a herd's or pack's leader. Enchanting pays a Harvest page for one (§45.14). */
public final class Hunts {
    private Hunts() {}

    /** A ledger population's alpha: a deep-wild herd's bull or a deep pack's leader (never a named beast). */
    public static boolean alpha(LivingEntity e) {
        return CreatureTag.of(e).elite() && e.entityTags().contains(Ledger.TAG);
    }
}
