package dev.awaken.delve.client;

import dev.awaken.core.api.CombatFeedback;
import dev.awaken.delve.DelveContent;
import dev.awaken.delve.DelveNet;
import dev.awaken.delve.HeavyStrikes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The heavy strike's charge (PLAN 7.4): sneak and hold the attack with a pick or mattock. The pick draws back: grit
 * trickles off its head and a low strain rises from {@link HeavyStrikes#MIN} ticks; on the glint ({@link HeavyStrikes#GLINT})
 * the head flashes and rings. Let go to swing: the server bores three blocks and every client near sees the rock burst
 * ({@link #landed}). A tap released before the charge starts is an ordinary bite. Sneaking without a mining tool, or
 * standing, is vanilla mining as ever.
 */
final class HeavyCharge {
    private HeavyCharge() {}

    private static boolean charging;
    private static int ticks;
    private static SimpleSoundInstance strain;
    // the tour's demonstration
    private static int demoTicks = -1;

    static boolean wants(LocalPlayer p) {
        return p.isShiftKeyDown() && !p.isSpectator() && HeavyStrikes.isMiningTool(p.getMainHandItem());
    }

    /** The attack key while sneaking with a pick: no vanilla mining, the charge instead (each tick it is asked again). */
    static void onKey(InputEvent.InteractionKeyMappingTriggered e) {
        if (!e.isAttack()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.gui.screen() != null || !wants(p)) return;
        if (!charging && mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult) return;   // a sneaking swing at a creature stays a swing
        e.setCanceled(true);
        e.setSwingHand(false);
        if (!charging) {
            charging = true;
            ticks = 0;
        }
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (demoTicks >= 0) demoTick(mc, p);
        if (!charging) return;
        if (p == null || mc.level == null) {
            reset();
            return;
        }
        if (mc.options.keyAttack.isDown() && wants(p)) {
            ticks = Math.min(HeavyStrikes.MAX, ticks + 1);
            charge(mc, p, mc.level, ticks);
            return;
        }
        // let go
        int held = ticks;
        charging = false;
        stopStrain(mc);
        if (held >= HeavyStrikes.MIN && wants(p)) {
            boolean glint = Math.abs(held - HeavyStrikes.GLINT) <= 2;
            ClientPacketDistributor.sendToServer(new DelveNet.HeavyStrike(held, glint));
            p.swing(InteractionHand.MAIN_HAND, p.getMainHandItem().getAttackAnimation(), false);
            CombatFeedback.kick(0F, 2.4F, 0.8F, 8);
        } else if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK && mc.gameMode != null) {
            mc.gameMode.startDestroyBlock(hit.getBlockPos(), hit.getDirection());   // a tap: an ordinary bite
            p.swing(InteractionHand.MAIN_HAND, p.getMainHandItem().getAttackAnimation(), false);
        }
    }

    /** One tick of the draw-back: the strain, grit off the head, the glint. */
    private static void charge(Minecraft mc, LocalPlayer p, ClientLevel level, int t) {
        RandomSource r = level.getRandom();
        Vec3 head = head(p);
        if (t == HeavyStrikes.MIN) {
            stopStrain(mc);
            strain = new SimpleSoundInstance(DelveContent.HEAVY_CHARGE.get(), SoundSource.PLAYERS, 0.7F, 1.0F, r, p.getX(), p.getEyeY(), p.getZ());
            mc.getSoundManager().play(strain);
        }
        if (t >= HeavyStrikes.MIN && StrikeFeel.density(mc) > 0 && t % 2 == 0) {
            DelveParticles.spawn(level, DelveContent.GRIT.get(), 0x8A8378, 0.8F, head.x, head.y, head.z,
                    (r.nextDouble() - 0.5) * 0.02, -0.02, (r.nextDouble() - 0.5) * 0.02);
        }
        if (t == HeavyStrikes.GLINT) {
            level.playLocalSound(p.getX(), p.getEyeY(), p.getZ(), DelveContent.HEAVY_GLINT.get(), SoundSource.PLAYERS, 0.8F, 1.0F, false);
            for (int i = 0; i < 6; i++) {
                DelveParticles.spawn(level, DelveContent.GLINT.get(), 0xFFE3A0, 1.3F, head.x + (r.nextDouble() - 0.5) * 0.12,
                        head.y + (r.nextDouble() - 0.5) * 0.12, head.z + (r.nextDouble() - 0.5) * 0.12, 0, 0, 0);
            }
        }
    }

    /** Where the pick's head is, drawn back over the shoulder: a little up, right and behind the eye's line. */
    private static Vec3 head(LocalPlayer p) {
        Vec3 look = p.getViewVector(1F);
        Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
        return p.getEyePosition().add(look.scale(0.45)).add(right.scale(0.42)).add(0, 0.12, 0);
    }

    private static void stopStrain(Minecraft mc) {
        if (strain != null) mc.getSoundManager().stop(strain);
        strain = null;
    }

    /** A heavy strike landed (anyone's): the bored rock bursts, sparks off its faces; the striker's hands hold. */
    static void landed(DelveNet.HeavyFx m) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        RandomSource r = level.getRandom();
        int density = StrikeFeel.density(mc);
        for (int i = 0; i < m.bored().length; i++) {
            BlockPos b = BlockPos.of(m.bored()[i]);
            BlockState s = Block.stateById(i < m.states().size() ? m.states().get(i) : 0);
            Vec3 c = Vec3.atCenterOf(b);
            if (density > 0) {
                StrikeFeel.grit(level, c, Direction.UP, 8 * density, s.getMapColor(level, b).col, r);
                StrikeFeel.sparks(level, c, Direction.getApproximateNearest(mc.gameRenderer.mainCamera().position().subtract(c)), (m.perfect() ? 9 : 6) * density / 2,
                        0xFFC98A, 1.2F, r);
            }
        }
        if (mc.player != null && mc.player.getId() == m.player()) {
            CombatFeedback.hold(m.perfect() ? 110 : 80);
            CombatFeedback.kick((r.nextFloat() - 0.5F) * 1.2F, 2.8F, (r.nextFloat() - 0.5F) * 1.0F, 9);
        }
    }

    /** The tour's demonstration: a full charge to the glint, without the key. */
    static void demo() {
        demoTicks = 0;
    }

    private static void demoTick(Minecraft mc, LocalPlayer p) {
        if (p == null || mc.level == null) {
            demoTicks = -1;
            return;
        }
        demoTicks++;
        charge(mc, p, mc.level, demoTicks);
        if (demoTicks >= HeavyStrikes.GLINT + 4) {
            demoTicks = -1;
            stopStrain(mc);
        }
    }

    static void reset() {
        charging = false;
        ticks = 0;
        demoTicks = -1;
        stopStrain(Minecraft.getInstance());
    }
}
