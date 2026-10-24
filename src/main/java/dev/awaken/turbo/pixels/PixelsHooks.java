package dev.awaken.turbo.pixels;

import com.mojang.blaze3d.pipeline.RenderTarget;

/** The mixins' way in (they live in their own package, PixelsFrame stays package-private). Render thread only. */
public final class PixelsHooks {
    private PixelsHooks() {}

    public static void beforeLevel(RenderTarget main, RenderTarget hand) {
        PixelsFrame.begin(main, hand);
    }

    public static void afterPostEffects(RenderTarget main, RenderTarget hand) {
        PixelsFrame.end(main, hand);
    }
}
