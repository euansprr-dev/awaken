package dev.awaken.core.client;

import dev.awaken.core.CoreModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The edge hold's cue (SPEC §28.9, "The edge hold"): while fast movement is eased at the edge of the land this client
 * has, a soft headwind rises (its own loop, {@code edge.headwind}) and the air thickens into a pale mist that closes in
 * toward the edge, so the slowdown reads as the world still forming ahead, not as lag. Both follow
 * {@link EdgeHoldClient#shown()}: in over about half a second, out over about a second once the land comes. In air only
 * (water, lava and snow keep their own fog). Client only; nothing allocated a frame.
 */
public final class EdgeHoldCue {
    private EdgeHoldCue() {}

    /** The mist is whole this far past the edge, and clear for this share of the way to it. */
    private static final float MIST_PAST = 4F, MIST_CLEAR = 0.25F;
    /** At full strength the mist's colour leans this far to a pale, faintly cool glow of the sky's own light. */
    private static final float PALE = 0.35F;

    private static Headwind wind;

    static void init() {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> tick());
        NeoForge.EVENT_BUS.addListener(ViewportEvent.RenderFog.class, EdgeHoldCue::fog);
        NeoForge.EVENT_BUS.addListener(ViewportEvent.ComputeFogColor.class, EdgeHoldCue::fogColour);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || EdgeHoldClient.shown() < 0.02F || (wind != null && !wind.isStopped())) return;
        wind = new Headwind();
        mc.getSoundManager().play(wind);
    }

    private static void fog(ViewportEvent.RenderFog e) {
        float s = EdgeHoldClient.shown();
        if (s <= 0F || e.getType() != FogType.NONE) return;
        float edge = EdgeHoldClient.edgeShown();
        float n0 = e.getNearPlaneDistance(), f0 = Math.max(e.getFarPlaneDistance(), 2F);
        float far = Math.min(f0, edge + MIST_PAST), near = Math.min(far - 1F, edge * MIST_CLEAR);
        // the far plane comes in geometrically: clear air ends at 1,024 blocks, so a straight blend would show only at the end
        float f = (float) (f0 * Math.pow(far / f0, s));
        e.setFarPlaneDistance(f);
        e.setNearPlaneDistance(Math.min(Mth.lerp(s, n0, near), f - 1F));
    }

    private static void fogColour(ViewportEvent.ComputeFogColor e) {
        float s = EdgeHoldClient.shown() * PALE;
        if (s <= 0F || Minecraft.getInstance().gameRenderer.mainCamera().getFluidInCamera() != FogType.NONE) return;
        float r = e.getRed(), g = e.getGreen(), b = e.getBlue();
        float lum = r * 0.3F + g * 0.59F + b * 0.11F;
        e.setRed(Mth.lerp(s, r, Math.min(1F, lum * 1.08F + 0.06F)));
        e.setGreen(Mth.lerp(s, g, Math.min(1F, lum * 1.12F + 0.07F)));
        e.setBlue(Mth.lerp(s, b, Math.min(1F, lum * 1.2F + 0.09F)));
    }

    /** The headwind at the listener: up with the hold, a little higher as it presses, gone a moment after it lets go. */
    static final class Headwind extends AbstractTickableSoundInstance {
        private float level;

        Headwind() {
            super(CoreModule.EDGE_HEADWIND.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.volume = 0.001F;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.x = this.y = this.z = 0;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            float s = Minecraft.getInstance().player == null ? 0F : EdgeHoldClient.shown();
            float target = (float) Math.pow(s, 1.3) * 0.6F;
            level += (target - level) * 0.2F;
            volume = Math.max(0.0005F, level);
            pitch = 0.92F + 0.14F * s;
            if (s <= 0F && level < 0.002F) stop();
        }
    }
}
