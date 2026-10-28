package dev.awaken.powers;

import dev.awaken.core.DirectorLog;
import dev.awaken.core.Forces;
import dev.awaken.powers.content.PowerContent;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Stat;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Supplier;

/** Public API of the powers module (SPEC section 12). */
public final class Powers {
    private Powers() {}

    /** Set by the client module so common code (item tooltips) can read the local loadout. */
    public static Supplier<@Nullable Loadout> clientLoadout = () -> null;

    public static Loadout loadout(Player player) {
        return player.getData(PowerContent.LOADOUT.get());
    }

    public static @Nullable Loadout clientLoadoutOrNull() {
        return clientLoadout.get();
    }

    public static float energy(Player player) {
        return loadout(player).energy();
    }

    public static float maxEnergy(Player player) {
        return PowerMath.maxEnergy(Progression.stat(player, Stat.INT));
    }

    /** The forces a damage source carries (empty for plain damage). */
    public static Set<Forces.Force> forcesOf(DamageSource source) {
        Forces.Force f = Forces.forceOf(source);
        return f == null ? Set.of() : EnumSet.of(f);
    }

    /** Fighting right now: hit or hurt in the last 5 s, or something hostile is hunting the player close by. */
    public static boolean inCombat(ServerPlayer player) {
        PowerState st = PowerState.of(player);
        if (st.inCombat(Casting.now(player))) return true;
        return !player.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, player.getBoundingBox().inflate(8),
                m -> m.getTarget() == player).isEmpty();
    }

    public static boolean owns(Player player, String id) {
        return loadout(player).owns(id);
    }

    /** Queue a loadout sync to its owner (sent at the end of the player's tick). */
    public static void markDirty(ServerPlayer player) {
        PowerState.of(player).loadoutDirty = true;
    }

    // ---- learning ---------------------------------------------------------------------------------------------------

    public enum LearnResult { LEARNED, LEVELLED, SWAPPED_VARIANT, MAXED, UNKNOWN }

    /** Learns a power (or levels it up if already known). Used by Truths, Latents and Paths through RewardHooks. */
    public static boolean learn(ServerPlayer player, Identifier power, int variant) {
        LearnResult r = learnOrLevel(player, power, variant, false);
        return r == LearnResult.LEARNED || r == LearnResult.LEVELLED || r == LearnResult.SWAPPED_VARIANT;
    }

    /**
     * Learns or upgrades. A core of a different variant levels the power up; sneak-using it (swapVariant) takes the
     * new variant instead and keeps the level.
     */
    public static LearnResult learnOrLevel(ServerPlayer player, Identifier power, int variant, boolean swapVariant) {
        return learnOrLevel(player, power, variant, swapVariant, true);
    }

    /** As above; {@code announce} false skips the Signal window (bulk admin learning). */
    public static LearnResult learnOrLevel(ServerPlayer player, Identifier power, int variant, boolean swapVariant, boolean announce) {
        PowerType type = PowerRegistry.get(power);
        if (type == null) return LearnResult.UNKNOWN;
        Loadout l = loadout(player);
        String id = type.id.toString();
        int v = Math.max(0, Math.min(type.variants.size() - 1, variant));
        Loadout.Owned o = l.owned.get(id);
        if (o == null) {
            l.owned.put(id, new Loadout.Owned(id, v, 1));
            int slot = autoSlot(l, type);
            Progression.markNewThing(player, "power " + type.name);
            DirectorLog.log(player, "powers", "learned " + id + " variant " + type.variant(v).name() + (slot >= 0 ? " on slot " + slot : ""));
            if (announce) PowerNotices.learned(player, type, v, slot);
            markDirty(player);
            return LearnResult.LEARNED;
        }
        if (swapVariant && o.variant() != v) {
            l.owned.put(id, new Loadout.Owned(id, v, o.level()));
            if (announce) PowerNotices.swapped(player, type, v, o.level());
            markDirty(player);
            return LearnResult.SWAPPED_VARIANT;
        }
        if (o.level() >= Loadout.MAX_LEVEL) {
            if (o.variant() != v) {
                l.owned.put(id, new Loadout.Owned(id, v, o.level()));
                if (announce) PowerNotices.swapped(player, type, v, o.level());
                markDirty(player);
                return LearnResult.SWAPPED_VARIANT;
            }
            return LearnResult.MAXED;
        }
        l.owned.put(id, new Loadout.Owned(id, o.variant(), o.level() + 1));
        Mastery.coreLevelled(l, id, o.level(), o.level() + 1);
        if (announce) PowerNotices.upgraded(player, type, o.variant(), o.level(), o.level() + 1, false);
        markDirty(player);
        return LearnResult.LEVELLED;
    }

    /** Power Core use. Returns false when nothing happened (core kept). */
    public static boolean useCore(ServerPlayer player, PowerType type, int variant) {
        LearnResult r = learnOrLevel(player, type.id, variant, player.isShiftKeyDown());
        if (r == LearnResult.MAXED) {
            Signal.toast(player, Component.literal(type.name + " is already mastered"));
            return false;
        }
        if (r == LearnResult.UNKNOWN) return false;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), PowerSounds.LEARN.get(), SoundSource.PLAYERS, 1f, 1f);
        player.level().sendParticles(Fx.dust(type.family.colour, 1.6f), player.getX(), player.getY() + 1, player.getZ(), 30, 0.5, 0.8, 0.5, 0.1);
        player.level().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 16, 0.3, 0.6, 0.3, 0.12);
        return true;
    }

    /** Puts a new power on the first free key it fits. Returns the slot or -1. */
    static int autoSlot(Loadout l, PowerType type) {
        if (type.isPassive()) return -1;
        if (type.slot == PowerType.Slot.ULTIMATE) {
            if (l.slots[Loadout.ULTIMATE].isEmpty()) {
                l.slots[Loadout.ULTIMATE] = type.id.toString();
                return Loadout.ULTIMATE;
            }
            return -1;
        }
        if (type.slot == PowerType.Slot.MOVEMENT && l.slots[Loadout.MOVEMENT].isEmpty()) {
            l.slots[Loadout.MOVEMENT] = type.id.toString();
            return Loadout.MOVEMENT;
        }
        for (int i = 0; i < 4; i++) {
            if (l.slots[i].isEmpty()) {
                l.slots[i] = type.id.toString();
                return i;
            }
        }
        return -1;
    }

    /** Puts a power on a key (swapping with whatever was there). Empty id clears the key. */
    public static boolean equip(ServerPlayer player, int slot, String id) {
        if (slot < 0 || slot >= Loadout.SLOTS) return false;
        Loadout l = loadout(player);
        if (id.isEmpty()) {
            Casting.cancelOnSlot(player, slot);
            l.slots[slot] = "";
            markDirty(player);
            return true;
        }
        PowerType type = PowerRegistry.get(id);
        if (type == null || !l.owns(type.id.toString()) || !type.fitsSlot(slot)) return false;
        String key = type.id.toString();
        int from = l.slotOf(key);
        if (from == slot) return true;
        Casting.cancelOnSlot(player, slot);
        if (from >= 0) Casting.cancelOnSlot(player, from);
        String previous = l.slots[slot];
        l.slots[slot] = key;
        if (from >= 0) {
            PowerType prev = PowerRegistry.get(previous);
            l.slots[from] = prev != null && prev.fitsSlot(from) ? previous : "";
        }
        markDirty(player);
        return true;
    }

    /** Forget a power (admin). */
    public static void forget(ServerPlayer player, String id) {
        Loadout l = loadout(player);
        l.owned.remove(id);
        for (int i = 0; i < Loadout.SLOTS; i++) if (l.slots[i].equals(id)) l.slots[i] = "";
        l.cooldowns.remove(id);
        l.mastery.remove(id);
        markDirty(player);
    }

    /**
     * Gives the player energy back, never above their pool (gear, rewards, other modules). Returns what was actually
     * added. Restored energy is not "spent", so it never counts toward mastery.
     */
    public static float restoreEnergy(ServerPlayer player, float amount) {
        return Casting.restore(player, amount);
    }

    /** Mastery toward a power's next level, 0..1 (1 at the top level). Server and client (from the synced loadout). */
    public static float masteryProgress(Player player, String powerId) {
        return loadout(player).masteryProgress(powerId);
    }

    public static void fillEnergy(ServerPlayer player) {
        Loadout l = loadout(player);
        l.energy = maxEnergy(player);
        l.pauseUntil = 0;
        markDirty(player);
    }

    public static void resetCooldowns(ServerPlayer player) {
        loadout(player).cooldowns.clear();
        markDirty(player);
    }
}
