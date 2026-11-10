package dev.awaken.core.client.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * What can be clicked on a sheet this frame. A screen clears it, and every part that draws something clickable adds its
 * rectangle with what a click does, so what is drawn and what takes the click can never disagree. The last one added is on
 * top. Reused every frame.
 */
public final class Hot {
    private record Zone(float x0, float y0, float x1, float y1, Runnable act) {}

    private final List<Zone> zones = new ArrayList<>();

    public void clear() {
        zones.clear();
        unclip();
    }

    private float cx0 = -1e9F, cy0 = -1e9F, cx1 = 1e9F, cy1 = 1e9F;

    /** Until {@link #unclip}: only the part of a zone inside this rectangle takes clicks (a list that scrolls under an edge). */
    public void clip(float x0, float y0, float x1, float y1) {
        cx0 = x0;
        cy0 = y0;
        cx1 = x1;
        cy1 = y1;
    }

    public void unclip() {
        cx0 = cy0 = -1e9F;
        cx1 = cy1 = 1e9F;
    }

    public void add(float x0, float y0, float x1, float y1, Runnable act) {
        x0 = Math.max(x0, cx0);
        y0 = Math.max(y0, cy0);
        x1 = Math.min(x1, cx1);
        y1 = Math.min(y1, cy1);
        if (x1 > x0 && y1 > y0) zones.add(new Zone(x0, y0, x1, y1, act));
    }

    /** Whether anything clickable is under the point. */
    public boolean over(float mx, float my) {
        for (Zone z : zones) if (Sheet.in(mx, my, z.x0, z.y0, z.x1, z.y1)) return true;
        return false;
    }

    /** Runs what is under the point, the topmost first. */
    public boolean click(float mx, float my) {
        for (int i = zones.size() - 1; i >= 0; i--) {
            Zone z = zones.get(i);
            if (!Sheet.in(mx, my, z.x0, z.y0, z.x1, z.y1)) continue;
            z.act.run();
            return true;
        }
        return false;
    }
}
