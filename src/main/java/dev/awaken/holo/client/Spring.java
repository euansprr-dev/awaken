package dev.awaken.holo.client;

import net.minecraft.util.Mth;

/**
 * A critically damped second-order spring on view yaw and pitch: how far something trails the camera, in degrees.
 * Each frame the camera's own turn is fed in ({@link #follow}), the offset is pulled back to zero with the exact
 * solution of {@code o'' = -w²o - 2wo'} (stable at any frame time), and the result is soft-clamped so it never shows
 * more than {@link #maxLag} degrees. Settles in about {@code 5.8 / w} seconds. Plain floats, nothing allocated.
 */
public final class Spring {
    /** Angular frequency; 29 rad/s settles (2%) in 200 ms. */
    private final float w;
    private final float maxLag;
    private float yaw, pitch, vYaw, vPitch;
    private float lastCamYaw, lastCamPitch;
    private boolean primed;

    public Spring(float settleSeconds, float maxLag) {
        this.w = 5.83f / settleSeconds;
        this.maxLag = maxLag;
    }

    /** Snap to the camera (on open, after a teleport or a big jump). */
    public void reset(float camYaw, float camPitch) {
        yaw = pitch = vYaw = vPitch = 0;
        lastCamYaw = camYaw;
        lastCamPitch = camPitch;
        primed = true;
    }

    /**
     * Advances by {@code dt} seconds toward a camera that now looks at {@code camYaw}/{@code camPitch} (vanilla
     * degrees). With {@code direct} the offset is dropped (reduced motion).
     */
    public void follow(float camYaw, float camPitch, float dt, boolean direct) {
        if (!primed) reset(camYaw, camPitch);
        float dy = Mth.wrapDegrees(camYaw - lastCamYaw), dp = camPitch - lastCamPitch;
        lastCamYaw = camYaw;
        lastCamPitch = camPitch;
        if (direct || Math.abs(dy) > 60 || Math.abs(dp) > 60) { // a teleport or respawn, not a turn
            yaw = pitch = vYaw = vPitch = 0;
            return;
        }
        // the camera moved, the trailing thing didn't (yet)
        yaw -= dy;
        pitch -= dp;
        dt = Math.min(dt, 0.1f);
        float e = (float) Math.exp(-w * dt);
        float ky = vYaw + w * yaw, kp = vPitch + w * pitch;
        yaw = (yaw + ky * dt) * e;
        vYaw = (vYaw - w * ky * dt) * e;
        pitch = (pitch + kp * dt) * e;
        vPitch = (vPitch - w * kp * dt) * e;
        // never let the tail grow past twice what is shown
        float hard = maxLag * 2;
        if (Math.abs(yaw) > hard) {
            yaw = Math.copySign(hard, yaw);
            vYaw = 0;
        }
        if (Math.abs(pitch) > hard) {
            pitch = Math.copySign(hard, pitch);
            vPitch = 0;
        }
    }

    /** Shown yaw offset in degrees (negative: trailing to the left of a camera that turned right). */
    public float yaw() {
        return soft(yaw);
    }

    /** Shown pitch offset in degrees (negative: trailing above a camera that looked down). */
    public float pitch() {
        return soft(pitch);
    }

    private float soft(float v) {
        return maxLag * (float) Math.tanh(v / maxLag);
    }
}
