package dev.awaken.hearth.client;

import dev.awaken.core.AwakenConfig;
import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.player.Aura;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/**
 * Rank auras: a few motes of the rank's (or Path's) colour drifting around a player, more of them the higher the
 * rank. Client only, cheap (a handful of particles per player per second), and off when the player turns auras off.
 */
final class AuraFx {
    private AuraFx() {}

    private static int tick;

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        boolean enabled;
        try {
            enabled = AwakenConfig.Client.AURA.get();
        } catch (RuntimeException e) {
            enabled = true;
        }
        if (!enabled || ++tick % 2 != 0) return;
        RandomSource r = mc.level.getRandom();
        for (Player p : mc.level.players()) {
            if (p.isSpectator() || p.isInvisible() || p.distanceToSqr(mc.player) > 48 * 48) continue;
            Aura a = p.getData(HearthContent.AURA.get());
            if (a.rank() <= 0) continue;
            // D: one mote every few ticks ... S/Sovereign: a steady glow.
            float chance = 0.15f + 0.12f * a.rank();
            if (r.nextFloat() > chance) continue;
            boolean firstPerson = p == mc.player && mc.options.getCameraType().isFirstPerson();
            double angle = r.nextDouble() * Math.PI * 2;
            double radius = 0.45 + r.nextDouble() * 0.25;
            double y = firstPerson ? p.getY() + 0.05 + r.nextDouble() * 0.3 : p.getY() + 0.1 + r.nextDouble() * 1.6;
            float size = 0.5f + 0.08f * a.rank();
            mc.level.addParticle(new DustParticleOptions(a.colour(), size), p.getX() + Math.cos(angle) * radius, y,
                    p.getZ() + Math.sin(angle) * radius, 0, 0.02, 0);
            if (a.rank() >= 5 && r.nextFloat() < 0.3f) {
                mc.level.addParticle(HearthContent.HEARTH_EMBER.get(), p.getX(), p.getY() + 1.0, p.getZ(), 0, 0.03, 0);
            }
        }
    }
}
