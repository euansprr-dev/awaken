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
        int rank = RegionMap.rankOf(Regions.creatureLevel(level, e.getEntity().blockPosition(), Regions.Role.NEUTRAL));
        int extra = extra(rank);
        if (extra == 0) return;
        for (ItemEntity d : e.getDrops()) {
            ItemStack s = d.getItem();
            if (!s.has(DataComponents.FOOD)) continue;
            ItemStack more = s.copyWithCount(extra);
            ItemEntity add = new ItemEntity(level, d.getX(), d.getY(), d.getZ(), more);
            add.setDeltaMovement(d.getDeltaMovement());
            e.getDrops().add(add);
            break; // one food drop gets more, not every stack
        }
        alphaHide(level, e, rank);
    }

    /**
     * An alpha is its land's own trophy-bearer (SPEC §21.10, decision 9: the land owns the hunt's parts): it always drops hide of its
     * land's tier (one, two from B land, three from S), picked from the tier's hide tag, so going somewhere harder is where the next
     * tier's hide is. The named beasts' trophies stay as they are (species parts, nothing removed).
     */
    static void alphaHide(ServerLevel level, LivingDropsEvent e, int rank) {
        if (!CreatureTag.of(e.getEntity()).elite() || rank == 0) return;
        net.minecraft.tags.TagKey<net.minecraft.world.item.Item> key = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                dev.awaken.Awaken.id("hunter_hide_" + hideTier(rank)));
        java.util.List<net.minecraft.world.item.Item> pool = new java.util.ArrayList<>();
        for (var h : net.minecraft.core.registries.BuiltInRegistries.ITEM.getTagOrEmpty(key)) pool.add(h.value());
        if (pool.isEmpty()) return;
        var at = e.getEntity().position();
        ItemStack hide = new ItemStack(pool.get(e.getEntity().getRandom().nextInt(pool.size())), 1 + rank / 2);
        e.getDrops().add(new ItemEntity(level, at.x, at.y + 0.2, at.z, hide));
    }

    /** The gear tier letter of a land rank (E, D, C, B, A, S), lower case, for its hide tag. */
    public static String hideTier(int rank) {
        return String.valueOf("edcbas".charAt(Math.max(0, Math.min(5, rank))));
    }
}
