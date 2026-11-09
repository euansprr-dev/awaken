package dev.awaken.hall.client;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

/**
 * A picture of weapons (SPEC §52). x and y are stage pixels, drawn through {@code pose}; {@code real} the texture's pixels
 * to a stage pixel. Its identity across frames is {@code key}, so the same renderer (and its texture) keeps serving it;
 * it is only drawn again when {@code version} moves.
 */
final class PicState implements PictureInPictureRenderState {
    final int key, x0, y0, x1, y1, color;
    final float real;
    final long version;
    final Placed[] placed;
    final RenderPipeline pipeline;
    private final Matrix3x2f pose;
    private final @Nullable ScreenRectangle bounds;

    PicState(int key, int x0, int y0, int x1, int y1, float real, long version, Placed[] placed, RenderPipeline pipeline, int color, Matrix3x2fc pose) {
        this.key = key;
        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;
        this.real = real;
        this.version = version;
        this.placed = placed;
        this.pipeline = pipeline;
        this.color = color;
        this.pose = new Matrix3x2f(pose);
        this.bounds = PictureInPictureRenderState.getBounds(x0, y0, x1, y1, this.pose, null);
    }

    @Override public int x0() { return x0; }
    @Override public int x1() { return x1; }
    @Override public int y0() { return y0; }
    @Override public int y1() { return y1; }
    @Override public float scale() { return 1F; }
    @Override public Matrix3x2fc pose() { return pose; }
    @Override public @Nullable ScreenRectangle scissorArea() { return null; }
    @Override public @Nullable ScreenRectangle bounds() { return bounds; }

    @Override public boolean equals(Object o) { return o instanceof PicState s && s.key == key; }
    @Override public int hashCode() { return key; }
}
