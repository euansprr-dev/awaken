package dev.awaken.signal.ui;

/** A screen rectangle in GUI pixels. */
public record Rect(int x, int y, int w, int h) {
    public static final Rect NONE = new Rect(0, 0, 0, 0);

    public int right() { return x + w; }
    public int bottom() { return y + h; }
    public boolean empty() { return w <= 0 || h <= 0; }

    public boolean intersects(Rect o) {
        return !empty() && !o.empty() && x < o.right() && o.x < right() && y < o.bottom() && o.y < bottom();
    }

    /** Fully inside a screen of this size. */
    public boolean insideScreen(int screenW, int screenH) {
        return x >= 0 && y >= 0 && right() <= screenW && bottom() <= screenH;
    }

    public boolean contains(double px, double py) {
        return px >= x && py >= y && px < right() && py < bottom();
    }
}
