package dev.awaken.arms;

import dev.awaken.core.Later;
import dev.awaken.core.api.CreatureTag;
import dev.awaken.core.api.MobRole;
import dev.awaken.progression.Rank;
import dev.awaken.signal.Signal;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Boss weapons (docs/arms-progression/PLAN.md §3.7; Euan, 2026-10-27: "I don't think EVERY boss fight should drop a
 * weapon ... really cool ones should still be a bit of luck"): the cooler the weapon, the rarer. A Gate Lord's kill rolls
 * <b>Spoils</b> for each fighter in its room, a Marked weapon of the Lord's style at the gate's rank with two Marks, by
 * rank (60% at E down to 15% at A, 40% at S), beside the Lord-forged roll (35%, certain by the third) that stays as it
 * was; a Lord-forged weapon replaces the Spoils. A pity makes the sixth Lord without any weapon certain. Guardians,
 * Sovereigns and named beasts roll a lower chance. It goes straight into the pack, never the Satchel.
 */
final class ArmsSpoils {
    private ArmsSpoils() {}

    /** The fighters who were handed a Lord-forged weapon this tick (a Lord-forged one replaces the Spoils). */
    private static final Map<UUID, Long> FORGED = new HashMap<>();

    static void noteForged(ServerPlayer p) {
        FORGED.put(p.getUUID(), p.level().getGameTime());
    }

    /** Whether this Lord's Spoils fall: the roll, or the pity once {@code without} Lords have passed with nothing. */
    static boolean falls(int without, float chance, float roll) {
        return without + 1 >= ArmsHomes.spoilsPity() || roll < chance;
    }

    // ---- a Gate Lord ------------------------------------------------------------------------------------------------

    /** A fighter in a main gate's Lord room (the hoard's moment): their Spoils roll, once the Lord-forged roll has settled. */
    static void lordKill(ServerPlayer p, String rankLabel, String themeId) {
        Rank rank = rank(rankLabel);
        Later.run(p.level().getServer(), 3, () -> {
            if (p.isRemoved()) return;
            ArmsProgress g = ArmsProgress.of(p);
            Long forged = FORGED.get(p.getUUID());
            if (forged != null && p.level().getGameTime() - forged <= 6) {
                g.lordsWithoutWeapon = 0; // the Lord-forged weapon is the day's weapon
                p.setData(ArmsContent.PROGRESS, g);
                return;
            }
            if (!falls(g.lordsWithoutWeapon, ArmsHomes.spoilChance(rank), p.getRandom().nextFloat())) {
                g.lordsWithoutWeapon++;
                p.setData(ArmsContent.PROGRESS, g);
                return;
            }
            g.lordsWithoutWeapon = 0;
            p.setData(ArmsContent.PROGRESS, g);
            give(p, rank, ArmsHomes.ofTheme(themeId));
        });
    }

    private static Rank rank(String label) {
        for (Rank r : Rank.values()) if (r.label.equals(label)) return r;
        return Rank.E;
    }

    // ---- the other bosses -------------------------------------------------------------------------------------------

    /** A guardian, a Sovereign or a named beast fell: each fighter near it rolls once. */
    static void onDeath(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (!(dead.level() instanceof ServerLevel level)) return;
        MobRole role = MobRole.of(dead);
        if (role.role() != MobRole.Role.BOSS || role.bossId().isEmpty() || LordForged.byBoss(role.bossId().get()) != null) return;
        if (!(e.getSource().getEntity() instanceof ServerPlayer) && !(dead.getKillCredit() instanceof ServerPlayer)) return;
        boolean beast = CreatureTag.of(dead).kind() == CreatureTag.Kind.BEAST;
        float chance = beast ? ArmsHomes.beastChance() : role.bossId().get().contains("guardian") ? ArmsHomes.guardianChance() : ArmsHomes.sovereignChance();
        Rank rank = ArmsHomes.rankOfLevel(role.contentLevel());
        ArmsHomes.Home home = ArmsHomes.ofTheme(role.theme());
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, dead.getBoundingBox().inflate(48))) {
            if (p.getRandom().nextFloat() < chance) give(p, rank, home);
        }
    }

    // ---- the weapon -------------------------------------------------------------------------------------------------

    /** A forged weapon of the rank in the home's style (or any style of that rank), with two distinct Marks. */
    static ItemStack stack(Rank rank, ArmsHomes.@Nullable Home home, RandomSource r) {
        String style;
        if (home != null) {
            style = home.style();
        } else {
            List<ArmsHomes.Home> pool = ArmsHomes.atRank(rank);
            if (pool.isEmpty()) pool = ArmsHomes.all();
            style = pool.get(r.nextInt(pool.size())).style();
        }
        Item item = ArmsItems.item(ArmsArmoury.PREFIX[Math.min(5, rank.ordinal())] + "_" + style);
        if (item == null) return ItemStack.EMPTY;
        ItemStack s = new ItemStack(item);
        Marks.Mark a = Marks.random(r), b = Marks.random(r);
        for (int i = 0; i < 8 && b.id().equals(a.id()); i++) b = Marks.random(r);
        s.set(ArmsContent.INSTANCE.get(), Marks.Instance.EMPTY.withMark(a.id()).withMark(b.id()));
        return s;
    }

    private static void give(ServerPlayer p, Rank rank, ArmsHomes.@Nullable Home home) {
        ItemStack stack = stack(rank, home, p.getRandom());
        if (stack.isEmpty()) return;
        ArmsIssue.giveItem(p, stack);
        ArmsNotices.reward(p, "weapon/spoils/" + java.util.UUID.randomUUID(), "Gate spoils", stack,
                "You found a weapon with two Marks. Each one adds something extra at a certain moment in a fight.",
                List.of("It's in your inventory, or at your feet if your inventory was full."));
        p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2F, 0.9F);
        dev.awaken.progression.Progression.markNewThing(p, "spoils");
    }

    // ---- the Lord holds its own weapon ------------------------------------------------------------------------------

    /** A Gate Lord with a Lord-forged weapon holds it, glinting, so you can see what you fight for (a look, never strength). */
    static void wield(LivingEntity lord) {
        MobRole role = MobRole.of(lord);
        if (role.bossId().isEmpty()) return;
        LordForged.Forged f = LordForged.byBoss(role.bossId().get());
        Item item = f == null ? null : ArmsItems.item(f.weapon());
        if (item == null) return;
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        lord.setItemSlot(EquipmentSlot.MAINHAND, stack);
        if (lord instanceof Mob m) m.setDropChance(EquipmentSlot.MAINHAND, 0F);
    }
}
