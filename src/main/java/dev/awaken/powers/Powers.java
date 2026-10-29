package dev.awaken.powers;

import dev.awaken.core.DirectorLog;
import dev.awaken.core.Forces;
import dev.awaken.powers.content.PowerContent;
import dev.awaken.powers.fx.FxKind;
import dev.awaken.powers.fx.FxStyle;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Stat;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.ChatFormatting;
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
            if (announce) announce(player, type, v, 1, "New power", slot);
            PowerNotices.learned(player, type, v, slot);
            markDirty(player);
            return LearnResult.LEARNED;
        }
        if (swapVariant && o.variant() != v) {
            l.owned.put(id, new Loadout.Owned(id, v, o.level()));
            announce(player, type, v, o.level(), "Variant changed", l.slotOf(id));
            PowerNotices.swapped(player, type, v, o.level());
            markDirty(player);
            return LearnResult.SWAPPED_VARIANT;
        }
        if (o.level() >= Loadout.MAX_LEVEL) {
            if (o.variant() != v) {
                l.owned.put(id, new Loadout.Owned(id, v, o.level()));
                announce(player, type, v, o.level(), "Variant changed", l.slotOf(id));
            PowerNotices.swapped(player, type, v, o.level());
                markDirty(player);
                return LearnResult.SWAPPED_VARIANT;
            }
            return LearnResult.MAXED;
        }
        l.owned.put(id, new Loadout.Owned(id, o.variant(), o.level() + 1));
        Mastery.coreLevelled(l, id, o.level(), o.level() + 1);
        // the ladder's moment: a sting in a fight, the step's window and bloom when calm (SPEC 36.6); the Journal keeps it
        dev.awaken.powers.ladder.Evolution.levelled(player, type, o.level(), o.level() + 1);
        PowerNotices.levelled(player, type, o.variant(), o.level(), o.level() + 1, false);
        markDirty(player);
        return LearnResult.LEVELLED;
    }

    /** An older save's one window about ladders is still to show (Loadout schema 3, SPEC 36.6). */
    public static boolean ladderIntroPending(Loadout l) {
        return l.migratedFrom > 0;
    }

    public static void clearLadderIntro(Loadout l) {
        l.migratedFrom = 0;
    }

    /**
     * Sets a power's level outright (admin, showcases, tests): learns it if needed and marks the level's moment seen, so no
     * window or bloom plays. Returns false for an unknown power.
     */
    public static boolean setLevel(ServerPlayer player, PowerType type, int level, int variant) {
        Loadout l = loadout(player);
        String id = type.id.toString();
        int lv = Math.max(1, Math.min(Loadout.MAX_LEVEL, level));
        int v = Math.max(0, Math.min(type.variants.size() - 1, variant));
        boolean fresh = !l.owned.containsKey(id);
        l.owned.put(id, new Loadout.Owned(id, v, lv));
        if (fresh) autoSlot(l, type);
        l.seen.put(id, lv);
        markDirty(player);
        return true;
    }

    /** A power's ladder moment was shown for this level. */
    public static void markSeen(Loadout l, String id, int level) {
        l.seen.put(id, level);
    }

    /** Power Core use. Returns false when nothing happened (core kept). */
    public static boolean useCore(ServerPlayer player, PowerType type, int variant) {
        LearnResult r = learnOrLevel(player, type.id, variant, player.isShiftKeyDown());
        if (r == LearnResult.MAXED) {
            Signal.toast(player, Component.literal(type.name + " is already mastered"));
            return false;
        }
        if (r == LearnResult.UNKNOWN) return false;
        if (r != LearnResult.LEVELLED) {
            // a new power (or a variant swap): the family's bloom and the learn chord; a level-up has its own moment (Evolution)
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), PowerSounds.LEARN.get(), SoundSource.PLAYERS, 1f, 1f);
            Fx.at(player.level(), FxKind.EVOLVE, FxStyle.of(type.family), player.position()).caster(player).aux(1).level(1).powerOf(type).send();
        }
        return true;
    }

    private static void announce(ServerPlayer player, PowerType type, int variant, int level, String title, int slot) {
        PowerType.Variant v = type.variant(variant);
        var b = SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(title))
                .row(Component.literal("Power"), Component.literal(type.name).withColor(type.family.colour))
                .row(Component.literal("Family"), Component.literal(type.family.label));
        if (variant != 0) b.row(Component.literal("Variant"), Component.literal(v.name()));
        if (level > 1) b.row(Component.literal("Level"), Component.literal(String.valueOf(level)));
        b.row(Component.literal("Key"), Component.literal(slot >= 0 ? Casting.slotLabel(slot) : type.isPassive() ? "Always on" : "Not equipped"));
        b.body(Component.literal(type.description));
        b.footer(Component.literal(slot >= 0 || type.isPassive() ? "Status · K to rearrange" : "Equip it from Status · K").withStyle(ChatFormatting.GRAY));
        Signal.send(player, b.ttl(20 * 8).build());
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
