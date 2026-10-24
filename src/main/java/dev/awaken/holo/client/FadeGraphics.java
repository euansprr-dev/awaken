package dev.awaken.holo.client;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/**
 * Draws a HUD layer at a fraction of its alpha (the calm HUD's tier 1, {@link CalmHud}) without the layer knowing: it
 * writes into the frame's own GUI render state, starting from the real graphics' pose (so the HUD drift still applies),
 * and multiplies the alpha of every fill, gradient, line of text, sprite and blit by {@link #begin}'s {@code alpha}.
 * Item icons can't fade (vanilla draws them opaque) and stay as they are. Overloads that call one another multiply
 * once. One instance, reused every frame; it allocates nothing of its own.
 */
final class FadeGraphics extends GuiGraphicsExtractor {
    private float alpha = 1f;
    /** How deep in our own overrides we are: only the outermost call fades its colour. */
    private int depth;

    private FadeGraphics(Minecraft mc) {
        super(mc, mc.gameRenderer.gameRenderState().guiRenderState, -10000, -10000);
    }

    static FadeGraphics create() {
        return new FadeGraphics(Minecraft.getInstance());
    }

    /** Ready to draw one layer like {@code real} would, at {@code alpha}. */
    FadeGraphics begin(GuiGraphicsExtractor real, float alpha) {
        this.alpha = alpha;
        depth = 0;
        pose().clear();
        pose().set(real.pose());
        return this;
    }

    private int fade(int argb) {
        if (depth > 0) return argb;
        return (Math.round((argb >>> 24) * alpha) << 24) | (argb & 0xFFFFFF);
    }

    @Override
    public void fill(RenderPipeline pipeline, int x0, int y0, int x1, int y1, int col) {
        int c = fade(col);
        depth++;
        try {
            super.fill(pipeline, x0, y0, x1, y1, c);
        } finally {
            depth--;
        }
    }

    @Override
    public void fillGradient(int x0, int y0, int x1, int y1, int col1, int col2) {
        int c1 = fade(col1), c2 = fade(col2);
        depth++;
        try {
            super.fillGradient(x0, y0, x1, y1, c1, c2);
        } finally {
            depth--;
        }
    }

    @Override
    public void text(Font font, FormattedCharSequence str, int x, int y, int color, boolean dropShadow) {
        int c = fade(color);
        depth++;
        try {
            super.text(font, str, x, y, c, dropShadow);
        } finally {
            depth--;
        }
    }

    @Override
    public void blitSprite(RenderPipeline pipeline, Identifier location, int x, int y, int width, int height, int color) {
        int c = fade(color);
        depth++;
        try {
            super.blitSprite(pipeline, location, x, y, width, height, c);
        } finally {
            depth--;
        }
    }

    @Override
    public void blitSprite(RenderPipeline pipeline, TextureAtlasSprite sprite, int x, int y, int width, int height, int color) {
        int c = fade(color);
        depth++;
        try {
            super.blitSprite(pipeline, sprite, x, y, width, height, c);
        } finally {
            depth--;
        }
    }

    @Override
    public void blitSprite(RenderPipeline pipeline, Identifier location, int spriteWidth, int spriteHeight, int textureX, int textureY,
                           int x, int y, int width, int height, int color) {
        int c = fade(color);
        depth++;
        try {
            super.blitSprite(pipeline, location, spriteWidth, spriteHeight, textureX, textureY, x, y, width, height, c);
        } finally {
            depth--;
        }
    }

    @Override
    public void blit(RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int width, int height, int srcWidth,
                     int srcHeight, int textureWidth, int textureHeight, int color) {
        int c = fade(color);
        depth++;
        try {
            super.blit(pipeline, texture, x, y, u, v, width, height, srcWidth, srcHeight, textureWidth, textureHeight, c);
        } finally {
            depth--;
        }
    }
}
