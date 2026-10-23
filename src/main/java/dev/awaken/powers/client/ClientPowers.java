package dev.awaken.powers.client;

import dev.awaken.powers.Loadout;
import dev.awaken.powers.PowerSounds;
import dev.awaken.powers.content.PowerContent;
import dev.awaken.powers.net.PowerPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Client-side power state: the synced loadout plus local prediction (energy spent and cooldowns started before the
 * server confirms), refusal flashes, entity marks (outlines) and the hit-stop table.
 */
public final class ClientPowers {
    private ClientPowers() {}

    // ---- loadout + prediction ---------------------------------------------------------------------------------------

    private static @Nullable Loadout lastSeen;
    private static float predictedSpend;
    private static final long[] predictedCooldownEnd = new long[Loadout.SLOTS];

    public static @Nullable Loadout loadout() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        Loadout l = mc.player.getData(PowerContent.LOADOUT.get());
        if (l != lastSeen) {
            // a fresh sync from the server replaces every local guess
            lastSeen = l;
            predictedSpend = 0;
            java.util.Arrays.fill(predictedCooldownEnd, 0);
        }
        return l;
    }

    public static long now() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime();
    }

    public static float energy() {
        Loadout l = loadout();
        return l == null ? 0 : Math.max(0, l.energyAt(now()) - predictedSpend);
    }

    static void predictSpend(float amount, int slot, long cooldownEnd) {
        predictedSpend += amount;
        if (slot >= 0 && slot < Loadout.SLOTS) predictedCooldownEnd[slot] = cooldownEnd;
    }

    static boolean predictedCooling(int slot, long now) {
        return slot >= 0 && slot < Loadout.SLOTS && predictedCooldownEnd[slot] > now;
    }

    // ---- feedback ---------------------------------------------------------------------------------------------------

    static final int[] flashUntil = new int[Loadout.SLOTS];
    static String message = "";
    static int messageUntil;
    static int clientTicks;

    static void onFeedback(PowerPayloads.Feedback msg) {
        flash(msg.slot(), reasonText(msg.reason()));
        if (msg.undoMotion()) {
            var p = Minecraft.getInstance().player;
            if (p != null) p.setDeltaMovement(Vec3.ZERO);
        }
        predictedSpend = 0;
        if (msg.slot() >= 0 && msg.slot() < Loadout.SLOTS) predictedCooldownEnd[msg.slot()] = 0;
    }

    static void flash(int slot, String text) {
        if (slot >= 0 && slot < Loadout.SLOTS) flashUntil[slot] = clientTicks + 12;
        message = text;
        messageUntil = clientTicks + 40;
    }

    static String reasonText(String reason) {
        return switch (reason) {
            case "energy" -> "Not enough energy";
            case "cooldown" -> "Not ready";
            case "no_target" -> "No target";
            case "no_ground" -> "No ground to tear up";
            case "too_heavy" -> "Too heavy to lift";
            case "too_strong" -> "Its will is too strong";
            case "blocked" -> "No room";
            case "no_summon" -> "No summon to swap with";
            case "no_constructs" -> "No constructs nearby";
            case "empty" -> "Nothing on this key · K to equip";
            case "busy" -> "";
            default -> "Can't do that now";
        };
    }

    // ---- marks (outlines) -------------------------------------------------------------------------------------------

    record Mark(int colour, int until) {}

    private static final Map<Integer, Mark> MARKS = new HashMap<>();
    private static int dangerPingAt = -100;

    static void onMarks(PowerPayloads.Marks msg) {
        int colour = switch (msg.kind()) {
            case PowerPayloads.Marks.DANGER -> 0xFFFF3B3B;
            case PowerPayloads.Marks.THIRD_EYE -> 0xFFB48CFF;
            case PowerPayloads.Marks.INTENT_HUNTING -> 0xFFFF6A4A;
            case PowerPayloads.Marks.INTENT_OTHER -> 0xFFF2C14E;
            default -> 0xFF7BFF9A;
        };
        boolean fresh = false;
        for (int id : msg.ids()) {
            Mark old = MARKS.get(id);
            if (old == null || old.until() < clientTicks) fresh = true;
            if (old == null || msg.kind() == PowerPayloads.Marks.DANGER || old.until() < clientTicks) MARKS.put(id, new Mark(colour, clientTicks + msg.ticks()));
        }
        if (fresh && msg.kind() == PowerPayloads.Marks.DANGER && clientTicks - dangerPingAt > 15) {
            dangerPingAt = clientTicks;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(PowerSounds.DANGER.get(), 1.4f, 0.35f));
        }
    }

    static int markColour(int entityId) {
        if (MARKS.isEmpty()) return 0;
        Mark m = MARKS.get(entityId);
        return m == null || m.until() < clientTicks ? 0 : m.colour();
    }

    // ---- hit-stop ---------------------------------------------------------------------------------------------------

    static final class Freeze {
        final long untilNanos;
        boolean captured;
        double x, y, z;
        float age, walkPos, walkSpeed;

        Freeze(long untilNanos) {
            this.untilNanos = untilNanos;
        }
    }

    static final Map<Integer, Freeze> FROZEN = new HashMap<>();

    static void onFeel(PowerPayloads.Feel msg) {
        if (msg.entityId() >= 0 && msg.hitstopMs() > 0) {
            FROZEN.put(msg.entityId(), new Freeze(System.nanoTime() + msg.hitstopMs() * 1_000_000L));
        }
        if (msg.shake() > 0) CombatFeel.kick(msg.shake(), 8 + (int) (msg.shake() * 2));
        dev.awaken.powers.client.fx.FxClient.onFeel(msg.entityId(), msg.hitstopMs());
    }

    // ---- housekeeping -----------------------------------------------------------------------------------------------

    static void tick() {
        clientTicks++;
        if (clientTicks % 20 == 0) {
            MARKS.values().removeIf(m -> m.until() < clientTicks);
            long t = System.nanoTime();
            for (Iterator<Freeze> it = FROZEN.values().iterator(); it.hasNext(); ) if (it.next().untilNanos < t) it.remove();
        }
    }

    static void reset() {
        MARKS.clear();
        FROZEN.clear();
        predictedSpend = 0;
        java.util.Arrays.fill(predictedCooldownEnd, 0);
        lastSeen = null;
        message = "";
    }
}
