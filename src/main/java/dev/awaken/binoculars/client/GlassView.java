package dev.awaken.binoculars.client;

import dev.awaken.Awaken;
import dev.awaken.binoculars.GlassFeel;
import dev.awaken.binoculars.GlassRules;
import dev.awaken.binoculars.GlassState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * What the binoculars do to the view (SPEC §39.3, §39.4): the narrower FOV, the breathing sway on the camera, the
 * matched look speed, the scroll wheel's steps, what attack and use do while up, and the HUD they leave.
 */
final class GlassView {
    private GlassView() {}

    /**
     * The camera asks for its angles, then the world's FOV, then the hand pass's (always 70): only the first FOV after
     * the angles (or the frame's start) is the world's, so the hands and the binoculars in them never zoom.
     */
    private static boolean worldFovNext;

    static void frameStart() {
        worldFovNext = true;
    }

    static void angles(ViewportEvent.ComputeCameraAngles e) {
        worldFovNext = true;
        if (!Glass.seeing() || !GlassConfig.sway() || Glass.reducedMotion()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        GlassFeel f = GlassFeel.get();
        double mag = Glass.zoom();
        double a = GlassRules.sway(mag, f.sway(), f.swayCrouch(), p.isCrouching()) * Math.min(1.0, (mag - 1.0) / 1.5);
        if (a <= 0) return;
        double t = System.nanoTime() / 1.0e9;
        // two slow, unrelated sines on each axis: a breath, never a wobble; aim (the player's look) never moves
        float yaw = (float) (a * (0.75 * Math.sin(t * 2 * Math.PI * 0.21) + 0.25 * Math.sin(t * 2 * Math.PI * 0.53 + 1.1)));
        float pitch = (float) (a * (0.7 * Math.sin(t * 2 * Math.PI * 0.27 + 0.6) + 0.3 * Math.sin(t * 2 * Math.PI * 0.61 + 2.3)));
        e.setYaw(e.getYaw() + yaw);
        e.setPitch(e.getPitch() + pitch);
    }

    static void fov(ViewportEvent.ComputeFov e) {
        boolean world = worldFovNext;
        worldFovNext = false;
        if (!world) return;
        double mag = Glass.zoom();
        if (mag > 1.0001) e.setFOV((float) GlassRules.fov(e.getFOV(), mag));
    }

    /** The slow walk's speed change stays out of the FOV (the speed-FOV effect is for real speed changes). */
    static void fovModifier(ComputeFovModifierEvent e) {
        if (!(e.getPlayer() instanceof LocalPlayer p)) return;
        float k = Glass.walkShare(p);
        if (k >= 1F) return;
        float ws = p.getAbilities().getWalkingSpeed();
        if (ws == 0) return;
        float v = (float) p.getAttributeValue(Attributes.MOVEMENT_SPEED) / ws;
        e.setNewFovModifier(e.getNewFovModifier() * (v / k + 1F) / (v + 1F));
    }

    static void turn(CalculatePlayerTurnEvent e) {
        double mag = Glass.zoom();
        if (mag <= 1.0001) return;
        e.setMouseSensitivity(GlassRules.sensitivity(e.getMouseSensitivity(), GlassRules.lookScale(mag, GlassConfig.lookSpeed())));
        e.setCinematicCameraEnabled(false);
    }

    static void scroll(InputEvent.MouseScrollingEvent e) {
        if (Glass.scroll(GlassRules.notches(e.getAccumulatedScrollY()))) e.setCanceled(true);
    }

    /** Attack lowers them and lands; use marks a spot when it can (stage 2), else lowers them and goes on. */
    static void interaction(InputEvent.InteractionKeyMappingTriggered e) {
        if (!Glass.active()) return;
        if (e.isUseItem() && Glass.seeing() && Rangefinder.mark()) {
            e.setCanceled(true);
            e.setSwingHand(false);
            return;
        }
        if (e.isAttack()) Glass.drop(GlassState.Drop.ATTACK);
        else if (e.isUseItem()) Glass.drop(GlassState.Drop.USE);
    }

    private static final Identifier SIGNAL = Awaken.id("signal");

    /** While at the eyes: chat, subtitles, titles, the action bar line and Awaken's notices stay; the rest goes. */
    static void hud(RenderGuiLayerEvent.Pre e) {
        if (!Glass.seeing()) return;
        Identifier id = e.getName();
        if (id.equals(GlassOverlay.LAYER) || id.equals(SIGNAL) || id.equals(VanillaGuiLayers.CHAT) || id.equals(VanillaGuiLayers.SUBTITLE_OVERLAY)
                || id.equals(VanillaGuiLayers.TITLE) || id.equals(VanillaGuiLayers.OVERLAY_MESSAGE) || id.equals(VanillaGuiLayers.TAB_LIST)) return;
        e.setCanceled(true);
    }
}
