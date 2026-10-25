package dev.awaken.atlas.client;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/**
 * One frame of the Atlas table view. {@code zoom} is GUI pixels per block; {@code tilt} the view's angle above the
 * horizon (90 = straight down); {@code yaw} orbits round the centre; {@code animate}: something moves (clouds drift,
 * land rises), so the picture must be drawn again; {@code age}: seconds since the table opened (the land prints in).
 */
record AtlasRenderState(int x0, int y0, int x1, int y1, float yaw, float tilt, float zoom, double cx, double cy, double cz,
                        float time, float night, boolean animate, int lod, float age, int meshVersion,
                        @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements PictureInPictureRenderState {

    AtlasRenderState(int x0, int y0, int x1, int y1, float yaw, float tilt, float zoom, double cx, double cy, double cz,
                     float time, float night, boolean animate, int lod, float age, int meshVersion, @Nullable ScreenRectangle scissorArea) {
        this(x0, y0, x1, y1, yaw, tilt, zoom, cx, cy, cz, time, night, animate, lod, age, meshVersion, scissorArea,
                PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
    }

    @Override
    public float scale() {
        return 1.0F;
    }

    /**
     * World (relative to the map centre) to view pixels: x right, y down, z toward the viewer (reversed-Z ortho keeps
     * the larger z). Looks north at yaw 0. The centre sits at (w/2, 0.55 h).
     */
    static Matrix4f view(Matrix4f m, float w, float h, float pxPerBlock, float yawDeg, float tiltDeg) {
        float t = (float) Math.toRadians(tiltDeg), c = (float) Math.cos(t), s = (float) Math.sin(t);
        // rows [1,0,0] [0,-c,s] [0,s,c]: up goes up the screen and toward the viewer, south goes down and toward it
        Matrix4f tiltM = new Matrix4f(1, 0, 0, 0, 0, -c, s, 0, 0, s, c, 0, 0, 0, 0, 1);
        return m.identity()
                .translate(w * 0.5f, h * 0.55f, 0)
                .scale(pxPerBlock)
                .mul(tiltM)
                .rotateY((float) Math.toRadians(yawDeg));
    }
}
