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

    public static final float ELITE_CHANCE = 0.04F, WARDEN_CHANCE = 0.10F, SECOND_MARK = 0.2F;

    static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity().level() instanceof ServerLevel level) || !(e.getSource().getEntity() instanceof ServerPlayer p)) return;
        MobRole role = MobRole.of(e.getEntity());
        float chance = role.role() == MobRole.Role.ELITE ? ELITE_CHANCE : role.role() == MobRole.Role.WARDEN ? WARDEN_CHANCE : 0F;
        if (chance <= 0 || level.getRandom().nextFloat() >= chance) return;
        ItemStack stack = roll(role.contentLevel(), level.getRandom());
        if (stack.isEmpty()) return;
        var at = e.getEntity().position();
        ItemEntity drop = new ItemEntity(level, at.x, at.y + 0.5, at.z, stack);
        drop.setGlowingTag(true);
        level.addFreshEntity(drop);
        List<String> marks = Marks.of(stack).marks();
        Signal.toast(p, Component.literal("MARKED · ").withColor(0x6FB3FF).append(stack.getHoverName().copy().withColor(0xD6ECFF))
                .append(Component.literal(" · " + String.join(", ", marks.stream().map(id -> {
                    Marks.Mark m = Marks.get(id);
                    return m == null ? id : m.name();
                }).toList())).withColor(0xC8C2B0)));
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
