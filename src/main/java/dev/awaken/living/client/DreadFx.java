package dev.awaken.living.client;

import dev.awaken.lens.client.LensClient;
import dev.awaken.living.LivingContent;
import dev.awaken.living.LivingNet;
import dev.awaken.living.combat.DreadRules;
import dev.awaken.progression.Progression;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/**
 * The land speaks before a creature does (SPEC §21.10, decision 11). The server says what level the land under you is
 * ({@link LivingNet.LandMsg}); against your own level that is a gap, and {@link DreadRules} turns the gap into a strength
 * that eases in over a few seconds and out a little faster. The strength is a place look over the world's own (Lens layer 2,
 * {@code awaken:dread}: the colour cools, an ember haze gathers at the edges) and the volume of a low bed. Per player and
 * client side, so friends each feel their own gap. Off with the setting {@code dread}.
 */
final class DreadFx {
    private DreadFx() {}

    static final String LOOK = "awaken:dread";
    /** The land's level under the player, or -1 (nothing weighs). */
    static int land = -1;
    static float weight;
    private static Bed bed;

    static void onLand(LivingNet.LandMsg m) {
        land = m.level();
    }

    static void tick(Minecraft mc, Player p) {
        float target = LivingClientConfig.dread && land >= 0 ? DreadRules.strength(land - Progression.profile(p).level()) : 0F;
        // in over about four seconds, out a little faster
        weight += (target - weight) * (target > weight ? 0.035F : 0.05F);
        if (Math.abs(target - weight) < 0.004F) weight = target;
        LensClient.placeLook(2, weight > 0F ? LOOK : null, weight);
        float vol = weight * 0.85F;
        if (vol > 0.01F && (bed == null || bed.isStopped())) {
            bed = new Bed();
            mc.getSoundManager().play(bed);
        }
        if (bed != null) bed.level = vol;
    }

    /** The bed: a looping low sound whose volume is the strength. */
    private static final class Bed extends AbstractTickableSoundInstance {
        float level;

        Bed() {
            super(LivingContent.DREAD.get(), SoundSource.AMBIENT, RandomSource.create());
            this.looping = true;
            this.delay = 0;
            this.relative = true;
            this.attenuation = Attenuation.NONE;
            this.volume = 0.001F;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            volume = Math.max(0.001F, level);
            if (level <= 0.005F && weight <= 0F) stop();
        }
    }
}
