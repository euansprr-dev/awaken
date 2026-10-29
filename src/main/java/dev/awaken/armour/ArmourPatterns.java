package dev.awaken.armour;

import dev.awaken.core.api.ArmsApi;
import dev.awaken.core.api.MobRole;
import dev.awaken.progression.Rank;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Where armour patterns come from (SPEC §44.4, "Patterns"): a gate creature of a set's rank carries one, the strong ones
 * sometimes (an elite 1 in 5, a warden 1 in 2), a gate's Lord always, and a Lord's own Lord-forged set only from a Lord.
 * Only a pattern the killer doesn't know drops; it falls as an item anyone can pick up.
 */
public final class ArmourPatterns {
    private ArmourPatterns() {}

    public static final float ELITE = 0.2F, WARDEN = 0.5F;
    static final String COUNTER = "awaken:armour_patterns";

    static void onDeath(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (!(dead.level() instanceof ServerLevel level) || !(e.getSource().getEntity() instanceof ServerPlayer p)) return;
        MobRole role = MobRole.of(dead);
        if (role.gateId().isEmpty()) return;
        boolean lord = role.role() == MobRole.Role.BOSS;
        float chance = lord ? 1F : role.role() == MobRole.Role.WARDEN ? WARDEN : role.role() == MobRole.Role.ELITE ? ELITE : 0F;
        if (chance <= 0F || level.getRandom().nextFloat() >= chance) return;
        ArmourSet set = pick(ArmouryMemory.of(p), rank(role.contentLevel()), lord, level.getRandom());
        Item item = set == null ? null : ArmourItems.pattern(set);
        if (item == null) return;
        ItemEntity drop = new ItemEntity(level, dead.getX(), dead.getY() + 0.5, dead.getZ(), new ItemStack(item));
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);
    }

    /** A gate's content level as a rank. */
    static Rank rank(int contentLevel) {
        String label = ArmsApi.rankOfLevel(contentLevel);
        for (Rank r : Rank.values()) if (r.label.equals(label)) return r;
        return Rank.E;
    }

    /**
     * The pattern a kill gives: a set of the gate's rank with a pattern you don't know; a Lord gives its rank's
     * Lord-forged set first, other creatures never do.
     */
    static @Nullable ArmourSet pick(ArmouryMemory mem, Rank rank, boolean lord, RandomSource random) {
        List<ArmourSet> sets = new ArrayList<>();
        for (ArmourSet s : ArmourSet.values()) {
            if (s.rank != rank || ArmourItems.pattern(s) == null || mem.patterns.contains(s.id)) continue;
            if (s.archetype == Archetype.LORD && !lord) continue;
            if (s.archetype == Archetype.LORD) return s;
            sets.add(s);
        }
        return sets.isEmpty() ? null : sets.get(random.nextInt(sets.size()));
    }
}
