package dev.awaken.descent.player;

import dev.awaken.descent.DescentContent;
import dev.awaken.progression.Progression;
import dev.awaken.progression.ProgressionModule;
import dev.awaken.signal.Signal;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/**
 * The Strain of climbing (Made in Abyss): going up past strata on foot costs 5% of all stats per stratum climbed,
 * stacking, capped at 40%. Progression applies and decays it; the Deep Lift ({@code Descent.ride}) never strains.
 */
public final class Strain {
    private Strain() {}

    public static final double PER_STRATUM = 0.05;
    public static final double CAP = 0.4;

    public static double next(double current, int strataClimbed) {
        if (strataClimbed <= 0) return Math.min(CAP, Math.max(0, current));
        return Math.min(CAP, Math.max(0, current) + PER_STRATUM * strataClimbed);
    }

    /** Applies Strain for climbing from one depth index to a shallower one. Returns the new value. */
    public static double climb(ServerPlayer player, int fromDepth, int toDepth) {
        int climbed = fromDepth - toDepth;
        double before = player.getData(ProgressionModule.STRAIN.get());
        if (climbed <= 0) return before;
        double after = next(before, climbed);
        player.setData(ProgressionModule.STRAIN.get(), after);
        Progression.applyAttributes(player);
        player.level().playSound(null, player.blockPosition(), DescentContent.SND_STRAIN.get(), SoundSource.PLAYERS, 0.8F, 0.9F);
        Signal.toast(player, Component.literal("The Strain of climbing · −" + Math.round(after * 100) + "% stats"));
        return after;
    }

    public static void clear(ServerPlayer player) {
        player.setData(ProgressionModule.STRAIN.get(), 0.0);
        Progression.applyAttributes(player);
    }
}
