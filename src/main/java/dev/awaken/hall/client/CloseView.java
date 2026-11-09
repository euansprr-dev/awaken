package dev.awaken.hall.client;

import dev.awaken.hall.HallNet;

/** Whatever has been brought close (a weapon, a suit, your looks): how it flies in and out, turns, and takes input. */
abstract class CloseView {
    private static final float FLY_SECONDS = .5F, BACK_SECONDS = .3F;

    float fly;
    boolean closing, dragging, holdKey, holdMouse;
    float yaw = -24F, vel = 20F;
    private boolean wentBack;

    private static float ease(float t) {
        float u = 1F - t;
        return 1F - u * u * u;
    }

    final float eased() {
        return ease(fly);
    }

    /** True once it has flown all the way back. */
    final boolean advance(float dt) {
        if (closing && !wentBack) {
            wentBack = true;
            HallSounds.back();
        }
        if (Stage.calm()) dt = 1F;
        fly = closing ? Math.max(0F, fly - dt / BACK_SECONDS) : Math.min(1F, fly + dt / FLY_SECONDS);
        return closing && fly <= 0F;
    }

    /** One turn of the slow spin, unless it is being dragged. */
    final void spin(float dt) {
        if (!dragging && !Stage.calm()) {
            vel += (12F - vel) * Math.min(1F, dt * 2.2F);
            yaw += vel * dt;
        }
        if (yaw > 180F) yaw -= 360F;
        if (yaw < -180F) yaw += 360F;
    }

    void drag(float dx) {
        yaw += dx * .4F;
        vel = dx * 6F;
    }

    abstract float zoomX();

    abstract float zoomY();

    /** The rank whose colour the embers and the air take. */
    abstract int tier();

    abstract void draw(Stage s, HallNet.Wall w, float dt, float time, double mouseX, double mouseY);

    /** A press on the sheet: true if something there took it (otherwise it starts a drag). */
    abstract boolean click(float x, float y);

    abstract void step(int dc, int dt);

    abstract boolean key(int key);

    abstract void space(boolean down);

    /** Who is shown, for the "what you carry changed" refresh. */
    void refresh() {}
}
