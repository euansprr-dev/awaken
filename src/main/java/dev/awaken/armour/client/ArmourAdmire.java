package dev.awaken.armour.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.armour.ArmourLooks;
import dev.awaken.core.client.AwakenKeys;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Admire (SPEC §44.13): hold the key and the camera leaves your eyes, starts in front of you and circles you once, close,
 * in a light that shows the set whatever the hour. Let go, move, take a blow or open anything and you are back behind
 * your own eyes. The Forge Song's camera carries it ({@link ForgeCamera}); you stand still while it runs.
 */
final class ArmourAdmire {
    private ArmourAdmire() {}

    static final KeyMapping KEY = new KeyMapping("key.awaken.admire", KeyConflictContext.IN_GAME, InputConstants.Type.KEYBOARD,
            InputConstants.KEY_O, AwakenKeys.CATEGORY);

    /** One circle's length in ticks, how far off the eye stands, how high above your middle, and its field of view. */
    private static final float TURN = 120F;
    private static final double RADIUS = 3.1, UP = 0.35, FOV = 42;

    private static boolean on, command;
    private static float startedAt;
    private static Vec3 stood = Vec3.ZERO;

    static void init(IEventBus modBus) {
        modBus.addListener(RegisterKeyMappingsEvent.class, e -> e.register(KEY));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> tick());
    }

    /** Whether the camera is circling you now (your own body is drawn, in its hero light). */
    static boolean on() {
        return on && ForgeCamera.active();
    }

    /** Wearing something of ours, on your feet, with nothing open. */
    private static boolean can(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gui.screen() != null || !p.isAlive() || p.isSpectator() || p.isPassenger() || p.isSleeping()) return false;
        for (EquipmentSlot slot : EquipmentSlot.values())
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && ArmourLooks.of(p.getItemBySlot(slot)) != null) return true;
        return false;
    }

    /** One whole circle without holding the key (the command; tours). */
    static boolean once() {
        Minecraft mc = Minecraft.getInstance();
        if (on || ForgeCamera.active() || !can(mc)) return false;
        start(mc, true);
        return true;
    }

    private static void start(Minecraft mc, boolean byCommand) {
        LocalPlayer p = mc.player;
        float now = ForgeClient.now(0F);
        Vec3 middle = p.position().add(0, p.getBbHeight() * 0.55, 0);
        // from in front of you, round by your right, and back to the front
        double front = -p.yBodyRot;
        var level = mc.level;
        ForgeCamera.cut((t, out) -> {
            double k = Mth.clamp(t / TURN, 0F, 1F);
            k = k * k * (3 - 2 * k);
            double a = Math.toRadians(front + 360.0 * k);
            Vec3 eye = middle.add(Math.sin(a) * RADIUS, UP, Math.cos(a) * RADIUS);
            // never through a wall: the eye stops a little short of whatever stands between it and you
            var hit = level.clip(new ClipContext(middle, eye, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
            if (hit.getType() != HitResult.Type.MISS) eye = middle.lerp(hit.getLocation(), 0.85);
            System.arraycopy(ForgeCamera.pose(eye, middle, FOV), 0, out, 0, 6);
        }, now, 10F);
        on = true;
        command = byCommand;
        startedAt = now;
        stood = p.position();
    }

    static void stop() {
        if (!on) return;
        on = false;
        ForgeCamera.end();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!on) {
            if (KEY.isDown() && !ForgeCamera.active() && can(mc)) start(mc, false);
            return;
        }
        LocalPlayer p = mc.player;
        float t = ForgeClient.now(0F) - startedAt;
        boolean held = command || KEY.isDown();
        if (p == null || !ForgeCamera.active() || !can(mc) || !held || t > TURN + 12F || p.hurtTime > 0 || p.position().distanceToSqr(stood) > 0.04) stop();
    }
}
