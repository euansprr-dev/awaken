package dev.awaken.turbo.pixels;

import com.mojang.blaze3d.pipeline.RenderTarget;

/** The mixins' way in (they live in their own package, PixelsFrame stays package-private). Render thread only. */
public final class PixelsHooks {
    private PixelsHooks() {}

    private static @org.jspecify.annotations.Nullable RenderTarget handTarget;

    public static void beforeLevel(RenderTarget main, RenderTarget hand) {
        handTarget = hand;
        PixelsFrame.begin(main, hand);
    }

    public static void afterPostEffects(RenderTarget main, RenderTarget hand) {
        PixelsFrame.end(main, hand);
    }

    /**
     * The game's depth target for the hand and what it holds (GameRenderer's {@code hud_3d_depth}), as this frame draws
     * into it (Pixels' own lent texture while it scales the world); null until a frame has begun with the hook in
     * place. Lens reads it to tell the hand from a wall at arm's length (SPEC §22 "Reach and cover", the hand). Read
     * only: nothing here changes what the game draws.
     */
    public static @org.jspecify.annotations.Nullable RenderTarget handTarget() {
        return handTarget;
    }
}
