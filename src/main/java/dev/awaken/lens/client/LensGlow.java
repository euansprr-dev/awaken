package dev.awaken.lens.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.awaken.Awaken;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/**
 * Light over the Air (SPEC §22): soft light other parts of the mod draw in the world (the Wilds' windways and updrafts),
 * which Lens lays over its finished picture.
 *
 * <p>Why it can't simply be drawn into the world: Lens puts its own sky, far fog and clouds over the game's frame, and
 * tells sky from land by depth. Soft light that writes depth reads as something solid, so the game's own sky is left
 * showing behind every quad of it (the windways by night: hard-edged dark slabs, Euan 11 Nov, "they look glitchy ...
 * they kind of cut off"). Light that writes none is part of whatever lies behind it: it takes the far land's fog, goes
 * under any cloud drawn over that land, and on the sky survives only as what the frame holds over the game's sky colour,
 * which a daylight sky clips. So it is drawn after the level into a layer of its own, tested against the world's depth
 * and writing none, and the chain's last pass lays that layer over the picture ({@code lens_lay.fsh}).
 *
 * <p>The layer holds light as paint laid over what is behind it: colour premultiplied in rgb, how much it covers in
 * alpha (what {@code BlendFunction.TRANSLUCENT} leaves in a cleared target). The same draw onto the main target gives
 * the same picture, which is where {@link #open} sends it whenever no Lens chain will lay the layer (Lens off, Swift,
 * a story's tear from black). As big as the main target, made the first time something draws into it and given back
 * ten seconds after the last; on a frame nothing drew, the chain reads one clear texel instead.
 */
public final class LensGlow extends AbstractTexture {
    private static final Optional<Vector4fc> CLEAR = Optional.of(new Vector4f(0f, 0f, 0f, 0f));
    /** Frames the layer is kept after its last use (about ten seconds at 60). */
    private static final int KEEP = 600;
    private static @Nullable LensGlow it;

    private final GpuTexture placeholder;
    private final GpuTextureView placeholderView;
    private @Nullable GpuTexture layer;
    private @Nullable GpuTextureView layerView;
    /** Drawn into since the last {@link #settle}; laid by this frame's chain. */
    private boolean drawn, laid, failed;
    private int idle;

    private LensGlow() {
        var device = RenderSystem.getDevice();
        placeholder = device.createTexture(() -> "awaken lens glow placeholder",
                GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING, GpuFormat.RGBA8_UNORM, 1, 1, 1, 1);
        placeholderView = device.createTextureView(placeholder);
        // a new texture's contents are whatever the memory held: this one must read as "no light"
        device.createCommandEncoder().clearColorTexture(placeholder, CLEAR.get());
        sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        texture = placeholder;
        textureView = placeholderView;
    }

    /** Lens's own, made with its parameters and registered for its chains before the first of them loads. */
    static LensGlow make() {
        if (it == null) it = new LensGlow();
        return it;
    }

    /**
     * A pass to draw light in, tested against the world's depth and writing none: on Lens's layer while its chain will
     * lay it over the picture, otherwise straight on the main target. Call it after the level is drawn
     * ({@code RenderLevelStageEvent.AfterLevel}), draw alpha-blended ({@code BlendFunction.TRANSLUCENT}) with a pipeline
     * that writes no depth, and close it.
     */
    public static RenderPass open(Supplier<String> label, RenderTarget main) {
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        LensGlow g = it;
        if (g != null && !g.failed && LensClient.laysLight()) {
            GpuTextureView to = g.layerLike(main.getColorTextureView());
            if (to != null) {
                // the first to draw this frame clears it as it opens (no trip through memory for what was there)
                Optional<Vector4fc> clear = g.drawn ? Optional.empty() : CLEAR;
                g.drawn = true;
                return encoder.createRenderPass(label, to, clear, main.getDepthTextureView(), OptionalDouble.empty());
            }
        }
        return encoder.createRenderPass(label, main.getColorTextureView(), Optional.empty(), main.getDepthTextureView(), OptionalDouble.empty());
    }

    /** Whether this frame's chain has light to lay (Tiles: the picture can't skip its last pass then). */
    public static boolean laid() {
        LensGlow g = it;
        return g != null && g.laid;
    }

    /** Lens, last thing after the level and before the chain runs: what the chain reads this frame. */
    static void settle() {
        LensGlow g = it;
        if (g == null) return;
        g.laid = g.drawn;
        g.drawn = false;
        if (g.laid) g.idle = 0;
        else if (g.layer != null && ++g.idle > KEEP) g.free();
    }

    /** The layer, the size and format of the main target's colour (made anew when the window changes). */
    private @Nullable GpuTextureView layerLike(GpuTextureView like) {
        int w = like.getWidth(0), h = like.getHeight(0);
        if (layer == null || layerView == null || layer.isClosed() || layerView.getWidth(0) != w || layerView.getHeight(0) != h) {
            free();
            try {
                var device = RenderSystem.getDevice();
                layer = device.createTexture(() -> "awaken lens glow", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING,
                        like.texture().getFormat(), w, h, 1, 1);
                layerView = device.createTextureView(layer);
            } catch (RuntimeException e) {
                failed = true;
                free();
                Awaken.LOG.warn("[lens] no layer for light over the Air ({}x{}): it is drawn straight onto the frame", w, h, e);
                return null;
            }
        }
        return layerView;
    }

    private void free() {
        if (layerView != null) layerView.close();
        if (layer != null) layer.close();
        layerView = null;
        layer = null;
        idle = 0;
        laid = false;
    }

    @Override
    public GpuTextureView getTextureView() {
        return laid && layerView != null && !layerView.isClosed() ? layerView : placeholderView;
    }

    @Override
    protected void releaseTextures() {
        free();
        placeholderView.close();
        placeholder.close();
        texture = null;
        textureView = null;
        if (it == this) it = null;
    }
}
