package dev.awaken.arms;

import dev.awaken.core.api.MobRole;
import dev.awaken.progression.Rank;
import dev.awaken.signal.Signal;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Marked weapons (SPEC §25 "Marks"): until the named weapons come (S9), an Elite or a Warden a player kills sometimes
 * drops a forged weapon of its rank in a style at random, carrying a Mark (two, now and then). The System names it.
 */
final class MarkedDrops {
    private MarkedDrops() {}

    public static final float SECOND_MARK = 0.2F;

    /** Whether this kill drops one: the roll, or the pity once {@code without} have passed with nothing. */
    static boolean falls(int without, float chance, float roll) {
        return without + 1 >= ArmsHomes.markedPity() || roll < chance;
    }

    static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity().level() instanceof ServerLevel level) || !(e.getSource().getEntity() instanceof ServerPlayer p)) return;
        MobRole role = MobRole.of(e.getEntity());
        var tag = dev.awaken.core.api.CreatureTag.of(e.getEntity());
        boolean heart = tag.kind() == dev.awaken.core.api.CreatureTag.Kind.CAMP && tag.elite();
        float chance = heart ? ArmsHomes.markedCampHeart() : role.role() == MobRole.Role.ELITE ? ArmsHomes.markedElite()
                : role.role() == MobRole.Role.WARDEN ? ArmsHomes.markedWarden() : 0F;
        if (chance <= 0) return;
        // a first one is certain from the first Warden or camp heart; a long dry run ends on the 25th (docs/arms-progression/PLAN.md §3.7)
        ArmsProgress g = ArmsProgress.of(p);
        boolean first = !g.firstMarked && (heart || role.role() == MobRole.Role.WARDEN);
        if (!falls(g.markedWithout, chance, level.getRandom().nextFloat()) && !first) {
            g.markedWithout++;
            p.setData(ArmsContent.PROGRESS, g);
            return;
        }
        g.markedWithout = 0;
        g.firstMarked = true;
        p.setData(ArmsContent.PROGRESS, g);
        ItemStack stack = roll(role.contentLevel(), level.getRandom());
        if (stack.isEmpty()) return;
        var at = e.getEntity().position();
        ItemEntity drop = new ItemEntity(level, at.x, at.y + 0.5, at.z, stack);
        drop.setGlowingTag(true);
        level.addFreshEntity(drop);
        List<String> details = new java.util.ArrayList<>();
        details.add("It's on the ground where the enemy fell. Pick it up and hold it to use its Marks.");
        details.addAll(ArmsNotices.marks(stack));
        dev.awaken.core.api.NoticeEvents.tell(p, new dev.awaken.core.api.NoticeEvents.Event("marked_drop/" + drop.getUUID(),
                "Marked weapon", stack.getHoverName().getString(), "The enemy dropped a weapon with Marks on it.", details,
                dev.awaken.core.api.NoticeEvents.Kind.REWARD, 0x6FB3FF));
    }

    /** The forged tier (a rank's letter) whose gates sit at this content level. */
    static String tierFor(int contentLevel) {
        Rank rank = Rank.E;
        for (Rank k : Rank.values()) if (k != Rank.SOVEREIGN && k.contentLevel <= contentLevel) rank = k;
        return rank.label;
    }

    /** A forged weapon of the rank whose gates sit at this content level, in a style at random, with its Mark(s). */
    static ItemStack roll(int contentLevel, RandomSource r) {
        String tier = tierFor(contentLevel);
        List<ArmsData.Weapon> rows = ArmsData.WEAPONS.stream().filter(w -> w.tier().equals(tier) && LordForged.byWeapon(w.id()) == null).toList();
        if (rows.isEmpty()) return ItemStack.EMPTY;
        ArmsData.Weapon w = rows.get(r.nextInt(rows.size()));
        @Nullable Item item = ArmsItems.item(w.id());
        if (item == null) return ItemStack.EMPTY;
        ItemStack s = new ItemStack(item);
        Marks.Instance inst = Marks.Instance.EMPTY.withMark(Marks.random(r).id());
        if (r.nextFloat() < SECOND_MARK) inst = inst.withMark(Marks.random(r).id());
        s.set(ArmsContent.INSTANCE.get(), inst);
        return s;
    }
}
