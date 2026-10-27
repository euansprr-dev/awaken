package dev.awaken.living.combat;

import dev.awaken.core.api.CreatureTag;
import dev.awaken.living.api.Regions;
import dev.awaken.living.region.RegionMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * The game is richer in higher lands (SPEC §21.10, decision 8). An animal that can't hurt you is a pig, not "Lv 68": it carries no
 * danger level and no scaling. What the land gives instead is more of it: one more of its ordinary food drop per rank above E (a pig
 * in B land, three more chops than anywhere else), which is also what makes a long walk out worth the hide and the meat.
 */
public final class GameDrops {
    private GameDrops() {}

    /** The most the land adds to one animal. */
    public static final int MAX_EXTRA = 5;

    /** How many more of its food an animal gives on land of this rank (0 E, 1 D ... 5 S). */
    public static int extra(int rank) {
        return Math.max(0, Math.min(MAX_EXTRA, rank));
    }

    public static void onDrops(LivingDropsEvent e) {
        if (!(e.getEntity().level() instanceof ServerLevel level) || !Regions.on(level)) return;
        var kind = CreatureTag.of(e.getEntity()).kind();
        // an animal that can't hurt you, or a wild one that fights back (WildKin): both carry the land's game
        if (kind != CreatureTag.Kind.PASSIVE && !(kind == CreatureTag.Kind.NEUTRAL && e.getEntity() instanceof net.minecraft.world.entity.Mob m && WildKin.isKin(m))) return;
        int extra = extra(RegionMap.rankOf(Regions.creatureLevel(level, e.getEntity().blockPosition(), Regions.Role.NEUTRAL)));
        if (extra == 0) return;
        for (ItemEntity d : e.getDrops()) {
            ItemStack s = d.getItem();
            if (!s.has(DataComponents.FOOD)) continue;
            ItemStack more = s.copyWithCount(extra);
            ItemEntity add = new ItemEntity(level, d.getX(), d.getY(), d.getZ(), more);
            add.setDeltaMovement(d.getDeltaMovement());
            e.getDrops().add(add);
            return; // one food drop gets more, not every stack
        }
    }
}
