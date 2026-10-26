package dev.awaken.hearth;

import dev.awaken.core.DirectorLog;
import dev.awaken.hearth.player.HearthPlayer;
import dev.awaken.hearth.room.RoomType;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

/**
 * Rested (SPEC §28.2, PLAN 4.5): waking in your own bed at home, in a Bedroom the Hearth has read, you're Rested for
 * 8 minutes and one more for every point of the home's Comfort. While it lasts energy comes back a quarter faster
 * ({@code awaken:energy_regen}) and crafts learn a quarter faster ({@code awaken:craft_rate}, read by the crafts). You
 * feel it; it never gates anything. Shown on the band.
 */
public final class Rested {
    private Rested() {}

    public static final String ENERGY_REGEN = "awaken:energy_regen", CRAFT_RATE = "awaken:craft_rate";
    public static final double ENERGY = 0.25, CRAFT = 0.25;

    static void init() {
        Progression.addPerkSource(Rested::perk);
    }

    /** The perk source (both sides: the client reads its synced view, the server the same view it sent). No allocation. */
    static double perk(Player p, String perkId) {
        boolean energy = ENERGY_REGEN.equals(perkId);
        if (!energy && !CRAFT_RATE.equals(perkId)) return 0;
        if (!active(p)) return 0;
        return energy ? ENERGY : CRAFT;
    }

    public static boolean active(Player p) {
        long until = p.getData(HearthContent.VIEW.get()).home().restedUntil();
        return until > 0 && p.level().getGameTime() < until;
    }

    /** Ticks of Rested left (0 when not). */
    public static long left(Player p) {
        long until = p.getData(HearthContent.VIEW.get()).home().restedUntil();
        return Math.max(0, until - p.level().getGameTime());
    }

    /** Woke up (not shaken awake): in your own bed at home, you're Rested. */
    static void onWake(ServerPlayer sp, ServerLevel level) {
        BlockPos bed = sp.getSleepingPos().orElse(sp.blockPosition());
        Optional<HearthRecord> ho = HearthService.sharedAt(sp, bed);
        if (ho.isEmpty()) return;
        HearthRecord h = ho.get();
        if (h.roomAt(bed, RoomType.BEDROOM).isEmpty() && h.roomAt(bed.above(), RoomType.BEDROOM).isEmpty()) return;
        rest(sp, level, h);
    }

    /** Sets Rested from the home's Comfort now. Public for the tests. */
    public static void rest(ServerPlayer sp, ServerLevel level, HearthRecord h) {
        HearthPlayer hp = HearthService.player(sp);
        int minutes = Comfort.restedMinutes(h.comfort);
        hp.restedUntil = level.getGameTime() + 20L * 60 * minutes;
        hp.restedComfort = h.comfort;
        HearthViews.refresh(sp);
        DirectorLog.log(sp, "hearth", "rested " + minutes + " min (comfort " + h.comfort + ")");
        Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Rested · " + minutes + " min"))
                .body(Component.literal("You slept well at home. Energy comes back a quarter faster, and crafts learn a quarter faster."
                        + (h.comfort < Comfort.MAX ? " A more comfortable home rests you longer." : "")))
                .topic("rested").ttl(20 * 6).build());
    }
}
