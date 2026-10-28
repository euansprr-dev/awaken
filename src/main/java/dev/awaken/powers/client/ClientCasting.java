package dev.awaken.powers.client;

import dev.awaken.core.client.AwakenKeys;
import dev.awaken.powers.Loadout;
import dev.awaken.powers.ladder.PowerLadder;
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
        if (l.again(id, now)) {
            // a ladder step's second press (SPEC 36.3): free and unpredicted; the server runs the step's follow-up
            var keyed = p.input.keyPresses;
            int keys = PowerPayloads.keysOf(keyed.forward(), keyed.backward(), keyed.left(), keyed.right());
            ClientPacketDistributor.sendToServer(new PowerPayloads.Cast(slot, PowerPayloads.PRESS, false, p.getYRot(), p.getXRot(), keys));
            return;
        }
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
        ClientPacketDistributor.sendToServer(new PowerPayloads.Cast(slot, PowerPayloads.PRESS, predicted, p.getYRot(), p.getXRot(), keys));
        dev.awaken.core.api.MovementHooks.clientCastStarting(p, t.id.toString(), t.family.name(), predicted); // the body: sprint ends, a slide yields to motion
        if (!toggleOff) dev.awaken.powers.client.fx.FxClient.predictCast(p, t, o.level());
        if (t.mode == PowerType.Mode.CHANNEL) channeling[slot] = true;
        if (!toggleOff && t.mode == PowerType.Mode.INSTANT) {
            int cd = PowerMath.cooldown(t.cooldown, t.variant(o.variant()).cooldown(), o.level(), 0);
            cd = Math.max(1, Math.round(cd * PowerLadder.cdScale(t.path, o.level())));
            ClientPowers.predictSpend(cost, slot, now + cd);
        }
        // powers with a designed cast pose animate the body themselves; the rest get a plain arm swing
        var def = dev.awaken.powers.fx.PowerFxDefs.get(t.path);
        if (def == null || def.anim() == dev.awaken.powers.fx.CastAnim.NONE || !dev.awaken.powers.client.fx.FxClient.animations()) {
            p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
        }
    }

    private static void release(int slot) {
        channeling[slot] = false;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        ClientPacketDistributor.sendToServer(new PowerPayloads.Cast(slot, PowerPayloads.RELEASE, false, p.getYRot(), p.getXRot(), 0));
        dev.awaken.powers.client.fx.FxClient.predictRelease(p);
    }

    static void reset() {
        java.util.Arrays.fill(channeling, false);
        waterStand = 0;
    }

    // ---- client physics for passive movement ------------------------------------------------------------------------

    static void onPlayerTick(PlayerTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getEntity() != mc.player || mc.player == null) return;
        LocalPlayer p = mc.player;
        Loadout l = ClientPowers.loadout();
        if (l == null || l.owned().isEmpty() || p.getAbilities().flying || p.isSpectator()) return;
        // awaken:wall_run is Wall Stride now: the body's wall-run reads it (body.Body.wallStride)
        Loadout.Owned water = l.get("awaken:water_run");
        if (water != null) waterRun(p, water.level());
        else waterStand = 0;
    }

    /** Still Water: ticks the surface still holds you after you stop sprinting. */
    private static int waterStand;

    /** Sprinting across water: the surface holds you like ground. The ladder (SPEC 36) adds Wake, Skip and Still Water. */
    private static void waterRun(LocalPlayer p, int level) {
        if (p.input.keyPresses.shift() || p.isUnderWater()) {
            waterStand = 0;
            return;
        }
        boolean sprint = p.isSprinting();
        if (!sprint && waterStand <= 0) return;
        BlockPos feet = BlockPos.containing(p.getX(), p.getY() - 0.05, p.getZ());
        FluidState fluid = p.level().getFluidState(feet);
        if (!fluid.is(FluidTags.WATER)) return;
        if (!p.level().getFluidState(feet.above()).isEmpty()) return;
        double surface = feet.getY() + fluid.getHeight(p.level(), feet);
        if (p.getY() < surface - 0.6) return;
        if (sprint) waterStand = level >= 4 ? (int) PowerLadder.num("water_run", 4, "stand", 40) : 0;
        else waterStand--;
        Vec3 v = p.getDeltaMovement();
        double vx = v.x, vz = v.z;
        if (sprint && level >= 2) {
            // Wake: the water's drag never takes you below a ground sprint
            double h = Math.sqrt(vx * vx + vz * vz);
            if (h > 0.05 && h < SPRINT_SPEED) {
                vx *= SPRINT_SPEED / h;
                vz *= SPRINT_SPEED / h;
            }
        }
        if (sprint && level >= 3 && p.input.keyPresses.jump() && v.y < 0.2 && p.getY() >= surface - 0.2) {
            // Skip: a jump off the surface is a sprint-jump off ground, speed kept
            Vec3 boost = Vec3.directionFromRotation(0, p.getYRot()).scale(0.2);
            p.setDeltaMovement(vx + boost.x, 0.42, vz + boost.z);
            return;
        }
        double lift = Math.max(0, surface - p.getY()) * 0.6;
        p.setDeltaMovement(vx, Math.max(v.y, 0) * 0.5 + lift, vz);
        p.resetFallDistance();
        if (p.tickCount % 2 == 0) p.level().addParticle(ParticleTypes.SPLASH, p.getX(), surface, p.getZ(), 0, 0.1, 0);
    }

    /** A ground sprint's speed in blocks per tick. */
    private static final double SPRINT_SPEED = 0.28;
}
