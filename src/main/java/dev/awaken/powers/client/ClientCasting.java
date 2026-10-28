package dev.awaken.powers.client;

import dev.awaken.core.client.AwakenKeys;
import dev.awaken.powers.Loadout;
import dev.awaken.powers.PowerMath;
import dev.awaken.powers.PowerRegistry;
import dev.awaken.powers.PowerType;
import dev.awaken.powers.net.PowerPayloads;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Power keys and client prediction. A press sends the slot and the current aim to the server; movement powers apply
 * their motion locally first so they feel instant (the server validates and stops the motion if it refuses).
 * Channelled powers send a release when the key comes up. Wall Run and Water Run are client physics.
 */
final class ClientCasting {
    private ClientCasting() {}

    private static final boolean[] channeling = new boolean[Loadout.SLOTS];

    static KeyMapping key(int slot) {
        return switch (slot) {
            case 0 -> AwakenKeys.POWER_1;
            case 1 -> AwakenKeys.POWER_2;
            case 2 -> AwakenKeys.POWER_3;
            case 3 -> AwakenKeys.POWER_4;
            case 4 -> AwakenKeys.ULTIMATE;
            default -> AwakenKeys.MOVEMENT;
        };
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        boolean blocked = mc.gui.screen() != null || p.isSpectator();
        for (int slot = 0; slot < Loadout.SLOTS; slot++) {
            KeyMapping k = key(slot);
            boolean pressed = false;
            while (k.consumeClick()) pressed = true;
            // the body's rope owns the movement key once the Ascent's Tether is held (core.api.MovementHooks)
            if (blocked || k == AwakenKeys.MOVEMENT && dev.awaken.core.api.MovementHooks.clientOwnsMovementKey(p)) {
                if (channeling[slot]) release(slot);
                continue;
            }
            if (pressed && !channeling[slot]) press(p, slot);
            if (channeling[slot] && !k.isDown()) release(slot);
        }
    }

    private static void press(LocalPlayer p, int slot) {
        Loadout l = ClientPowers.loadout();
        if (l == null) return;
        String id = l.slot(slot);
        PowerType t = PowerRegistry.get(id);
        if (t == null) {
            if (!l.owned().isEmpty()) ClientPowers.flash(slot, ClientPowers.reasonText("empty"));
            return;
        }
        Loadout.Owned o = l.get(id);
        if (o == null) return;
        long now = ClientPowers.now();
        boolean running = l.isActive(id);
        float cost = PowerMath.cost(t.cost, t.variant(o.variant()).cost());
        boolean cooling = l.cooldownLeft(id, now) > 0 || ClientPowers.predictedCooling(slot, now);
        boolean toggleOff = running && t.mode == PowerType.Mode.TOGGLE;
        if (!toggleOff && cooling) {
            ClientPowers.flash(slot, ClientPowers.reasonText("cooldown"));
            return;
        }
        if (!toggleOff && ClientPowers.energy() + 0.5f < cost) {
            ClientPowers.flash(slot, ClientPowers.reasonText("energy"));
            return;
        }
        var keyed = p.input.keyPresses;
        int keys = PowerPayloads.keysOf(keyed.forward(), keyed.backward(), keyed.left(), keyed.right());
        boolean predicted = false;
        if (!toggleOff && t.predictor != null) {
            Vec3 v = t.predictor.motion(p, o.variant(), o.level(), PowerPayloads.strafeOf(keys), PowerPayloads.forwardOf(keys));
            if (v != null) {
                p.setDeltaMovement(v);
                p.resetFallDistance();
                predicted = true;
            }
        }
        // aimed a little toward the locked or marked creature (SPEC §40.3); a cast that moves you keeps your own look
        if (predicted) {
            AIM[0] = p.getYRot();
            AIM[1] = p.getXRot();
        } else {
            dev.awaken.core.api.Aim.bend(p.getYRot(), p.getXRot(), AIM);
        }
        ClientPacketDistributor.sendToServer(new PowerPayloads.Cast(slot, PowerPayloads.PRESS, predicted, AIM[0], AIM[1], keys));
        dev.awaken.core.api.MovementHooks.clientCastStarting(p, t.id.toString(), t.family.name(), predicted); // the body: sprint ends, a slide yields to motion
        if (!toggleOff) dev.awaken.powers.client.fx.FxClient.predictCast(p, t);
        if (t.mode == PowerType.Mode.CHANNEL) channeling[slot] = true;
        if (!toggleOff && t.mode == PowerType.Mode.INSTANT) {
            int cd = PowerMath.cooldown(t.cooldown, t.variant(o.variant()).cooldown(), o.level(), 0);
            ClientPowers.predictSpend(cost, slot, now + cd);
        }
        // powers with a designed cast pose animate the body themselves; the rest get a plain arm swing
        var def = dev.awaken.powers.fx.PowerFxDefs.get(t.path);
        if (def == null || def.anim() == dev.awaken.powers.fx.CastAnim.NONE || !dev.awaken.powers.client.fx.FxClient.animations()) {
            p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
        }
    }

    private static final float[] AIM = new float[2];

    private static void release(int slot) {
        channeling[slot] = false;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        dev.awaken.core.api.Aim.bend(p.getYRot(), p.getXRot(), AIM);
        ClientPacketDistributor.sendToServer(new PowerPayloads.Cast(slot, PowerPayloads.RELEASE, false, AIM[0], AIM[1], 0));
        dev.awaken.powers.client.fx.FxClient.predictRelease(p);
    }

    static void reset() {
        java.util.Arrays.fill(channeling, false);
    }

    // ---- client physics for passive movement ------------------------------------------------------------------------

    static void onPlayerTick(PlayerTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getEntity() != mc.player || mc.player == null) return;
        LocalPlayer p = mc.player;
        Loadout l = ClientPowers.loadout();
        if (l == null || l.owned().isEmpty() || p.getAbilities().flying || p.isSpectator()) return;
        // awaken:wall_run is Wall Stride now: the body's wall-run reads it (body.Body.wallStride)
        if (l.owns("awaken:water_run")) waterRun(p);
    }

    /** Sprinting across water: the surface holds you like ground. */
    private static void waterRun(LocalPlayer p) {
        if (!p.isSprinting() || p.input.keyPresses.shift() || p.isUnderWater()) return;
        BlockPos feet = BlockPos.containing(p.getX(), p.getY() - 0.05, p.getZ());
        FluidState fluid = p.level().getFluidState(feet);
        if (!fluid.is(FluidTags.WATER)) return;
        if (!p.level().getFluidState(feet.above()).isEmpty()) return;
        double surface = feet.getY() + fluid.getHeight(p.level(), feet);
        if (p.getY() < surface - 0.6) return;
        Vec3 v = p.getDeltaMovement();
        double lift = Math.max(0, surface - p.getY()) * 0.6;
        p.setDeltaMovement(v.x, Math.max(v.y, 0) * 0.5 + lift, v.z);
        p.resetFallDistance();
        if (p.tickCount % 2 == 0) p.level().addParticle(ParticleTypes.SPLASH, p.getX(), surface, p.getZ(), 0, 0.1, 0);
    }
}
