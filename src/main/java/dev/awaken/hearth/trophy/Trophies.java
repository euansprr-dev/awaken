package dev.awaken.hearth.trophy;

import dev.awaken.Awaken;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobRole;
import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.hearth.HearthViews;
import dev.awaken.hearth.block.TrophyMountBlockEntity;
import dev.awaken.hearth.player.HearthPlayer;
import dev.awaken.hearth.town.Town;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/** Boss trophies: the drop, hanging them, and the Trophy Hall bonus (a little per different boss). */
public final class Trophies {
    private Trophies() {}

    public static final String COUNTER_PLACED = "awaken:trophies_placed";
    /** After a boss's first trophy, later kills of the same boss drop one this often. */
    public static final double REPEAT_CHANCE = 0.2;
    public static final int BONUS_CAP = 20;
    private static final Identifier HALL_DAMAGE = Awaken.id("trophy_hall_damage");
    private static final Identifier HALL_HEALTH = Awaken.id("trophy_hall_health");

    /** Called from the shared death listener when something dies to a player. */
    public static void onDeath(LivingDeathEvent e, ServerPlayer killer, ServerLevel level) {
        LivingEntity dead = e.getEntity();
        Optional<MobRole> role = dead.getExistingData(CoreApi.MOB_ROLE.get());
        if (role.isEmpty() || role.get().role() != MobRole.Role.BOSS) return;
        String bossId = role.get().bossId().orElse(BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType()).toString());
        HearthPlayer hp = HearthService.player(killer);
        boolean first = !hp.trophyBosses.contains(bossId);
        if (!first && killer.getRandom().nextDouble() >= REPEAT_CHANCE) return;
        hp.trophyBosses.add(bossId);
        ItemStack trophy = make(bossId, dead.getDisplayName().getString(), PlayDays.current(level.getServer()));
        dead.spawnAtLocation(level, trophy, 0.5f);
        if (first) Signal.toast(killer, Component.literal("A trophy fell. Hang it at home."));
    }

    public static ItemStack make(String bossId, String bossName, long day) {
        ItemStack s = new ItemStack(HearthContent.TROPHY_ITEM.get());
        s.set(HearthContent.TROPHY.get(), new TrophyData(bossId, bossName, day, false));
        return s;
    }

    public static void mount(ServerPlayer sp, ServerLevel level, BlockPos pos, TrophyMountBlockEntity be, ItemStack held) {
        TrophyData d = held.get(HearthContent.TROPHY.get());
        if (d == null) {
            sp.sendOverlayMessage(Component.literal("This trophy has no story. Blank plaques stay blank."));
            return;
        }
        ItemStack hung = held.copyWithCount(1);
        boolean firstHang = !d.placed();
        hung.set(HearthContent.TROPHY.get(), d.asPlaced());
        be.setTrophy(hung);
        if (!sp.hasInfiniteMaterials()) held.shrink(1);
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1f, 0.8f);
        Optional<HearthRecord> home = HearthService.hearthOf(sp).filter(h -> h.contains(pos));
        boolean newBoss = home.map(h -> !h.hallTrophies.contains(d.bossId())).orElse(false);
        if (firstHang) {
            Progression.count(sp, COUNTER_PLACED);
            if (newBoss) Progression.markNewThing(sp, "trophy");
        }
        HearthService.blockChanged(level, pos);
        Town.commentTrophy(sp, level, pos, d);
    }

    public static void unmount(ServerPlayer sp, ServerLevel level, BlockPos pos, TrophyMountBlockEntity be) {
        ItemStack out = be.take();
        if (out.isEmpty()) return;
        sp.getInventory().placeItemBackInInventory(out, Prediction.SERVER_ONLY);
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1f, 0.8f);
        HearthService.blockChanged(level, pos);
    }

    /** Distinct bosses counted toward the bonus (pure; capped). */
    public static int bonusCount(int distinctTrophies) {
        return Math.max(0, Math.min(BONUS_CAP, distinctTrophies));
    }

    /** +1% damage and +0.5 max health per different boss hung in a Trophy Hall. Re-applied on login, respawn and scans. */
    public static void applyHallBonus(ServerPlayer sp, @Nullable HearthRecord h) {
        int n = bonusCount(h == null ? 0 : h.hallTrophies.size());
        set(sp.getAttribute(Attributes.ATTACK_DAMAGE), HALL_DAMAGE, 0.01 * n, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(sp.getAttribute(Attributes.MAX_HEALTH), HALL_HEALTH, 0.5 * n, AttributeModifier.Operation.ADD_VALUE);
        if (sp.getHealth() > sp.getMaxHealth()) sp.setHealth(sp.getMaxHealth());
        HearthViews.refresh(sp);
    }

    private static void set(@Nullable AttributeInstance inst, Identifier id, double amount, AttributeModifier.Operation op) {
        if (inst == null) return;
        if (amount <= 0) inst.removeModifier(id);
        else inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
    }
}
