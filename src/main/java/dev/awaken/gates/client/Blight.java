package dev.awaken.gates.client;

import dev.awaken.Awaken;
import dev.awaken.gates.net.GatePayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.List;

/**
 * The grey around a broken gate (SPEC section 7): within 96 blocks the fog turns grey and closes in and a desaturating
 * post effect fades in, until the Gate Lord dies. Inside a Lightless gate the fog closes in hard.
 */
public final class Blight {
    private Blight() {}

    public static final Identifier EFFECT = Awaken.id("blight");
    private static float strength, shown;

    /** 0 outside, 1 within 64 blocks of a broken gate, fading out to 96. */
    static float target() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.level.dimension() != Level.OVERWORLD) return 0;
        Vec3 p = mc.player.position();
        float best = 0;
        for (GatePayloads.Beacon b : ClientGates.beacons()) {
            if (!b.broken()) continue;
            double d = Math.sqrt(b.pos().distToCenterSqr(p));
            float f = (float) Math.max(0, Math.min(1, (96 - d) / 32.0));
            best = Math.max(best, f);
        }
        return best;
    }

    static void onTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float t = target();
        strength += (t - strength) * 0.05F;
        if (Math.abs(strength - t) < 0.01F) strength = t;
        shown = strength;
        List<Identifier> effects = mc.player.getActivePostEffects();
        boolean want = strength > 0.3F;
        if (want && !effects.contains(EFFECT)) effects.add(EFFECT);
        else if (!want) effects.remove(EFFECT);
    }

    static void onFogColour(ViewportEvent.ComputeFogColor e) {
        float s = shown * 0.85F;
        if (s > 0) {
            float lum = e.getRed() * 0.3F + e.getGreen() * 0.59F + e.getBlue() * 0.11F;
            float grey = lum * 0.8F + 0.12F;
            e.setRed(e.getRed() + (grey - e.getRed()) * s);
            e.setGreen(e.getGreen() + (grey - e.getGreen()) * s);
            e.setBlue(e.getBlue() + (grey - e.getBlue()) * s);
        }
        if (ClientGates.inRun() && (ClientGates.hud().flags() & GatePayloads.RunHud.LIGHTLESS) != 0) {
            e.setRed(e.getRed() * 0.25F);
            e.setGreen(e.getGreen() * 0.25F);
            e.setBlue(e.getBlue() * 0.3F);
        }
    }

    static void onFog(ViewportEvent.RenderFog e) {
        if (shown > 0) {
            float far = e.getFarPlaneDistance();
            float to = Math.min(far, 56F);
            e.setFarPlaneDistance(far + (to - far) * shown);
            e.setNearPlaneDistance(e.getNearPlaneDistance() * (1 - shown));
        }
        if (ClientGates.inRun() && (ClientGates.hud().flags() & GatePayloads.RunHud.LIGHTLESS) != 0) {
            e.setNearPlaneDistance(0F);
            e.setFarPlaneDistance(Math.min(e.getFarPlaneDistance(), 22F));
        }
    }

    public static float strength() {
        return shown;
    }
}
