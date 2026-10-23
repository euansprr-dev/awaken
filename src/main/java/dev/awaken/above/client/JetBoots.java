package dev.awaken.above.client;

import dev.awaken.above.frame.FrameModule;
import dev.awaken.above.frame.FrameModules;
import dev.awaken.above.net.AboveNet;
import dev.awaken.above.power.EssenceCellItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Jet Boots are client-predicted like every movement power: holding jump in the air (after letting go of it once)
 * pushes you up; the server is told how many ticks fired so it can charge essence and forgive the fall.
 */
final class JetBoots {
    private JetBoots() {}

    private static boolean releasedInAir;
    private static int pending;
    private static int checkCooldown;
    private static boolean equipped;

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.gui.screen() != null) {
            flush();
            return;
        }
        if (--checkCooldown <= 0) {
            // reading armour components and the inventory is cheap, but there's no need to do it every tick
            checkCooldown = 10;
            equipped = FrameModules.has(FrameModules.mask(p), FrameModule.JET_BOOTS) && (p.isCreative() || EssenceCellItem.carried(p) > 0);
        }
        boolean jump = mc.options.keyJump.isDown();
        if (p.onGround() || p.isInWater() || p.getAbilities().flying || p.isPassenger() || p.isFallFlying()) {
            releasedInAir = false;
            flush();
            return;
        }
        if (!jump) {
            releasedInAir = true;
            flush();
            return;
        }
        if (!equipped || !releasedInAir) return;
        Vec3 v = p.getDeltaMovement();
        p.setDeltaMovement(v.x * 1.02, Math.min(0.42, v.y + 0.11), v.z * 1.02);
        p.resetFallDistance();
        if (p.tickCount % 2 == 0 && mc.level != null) {
            mc.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, p.getX() - 0.15, p.getY(), p.getZ(), 0, -0.15, 0);
            mc.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, p.getX() + 0.15, p.getY(), p.getZ(), 0, -0.15, 0);
        }
        if (++pending >= 4) flush();
    }

    private static void flush() {
        if (pending > 0) {
            ClientPacketDistributor.sendToServer(new AboveNet.Jet(pending));
            pending = 0;
        }
    }
}
