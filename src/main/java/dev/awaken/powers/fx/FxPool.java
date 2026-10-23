package dev.awaken.powers.fx;

/**
 * A fixed pool of effect sprites stored as parallel arrays: no objects per sprite, no allocation after construction.
 * The client ticks it and draws it; it lives in common code so tests can check its caps. When the pool is full, a new
 * sprite replaces the most-spent one near the cursor, so a fresh impact always shows up.
 */
public final class FxPool {
    /** Additive glow (full-bright, adds light). */
    public static final int ADD = 1;
    /** Alpha-blended and lit by the world (smoke, dust). */
    public static final int LIT = 2;
    /** Alpha-blended but full-bright (ink with a glow, holograms). */
    public static final int BRIGHT = 4;
    /** Stretched along its velocity (sparks, speed lines). */
    public static final int STRETCH = 8;
    /** Frame chosen by age (otherwise the frame is fixed). */
    public static final int ANIM = 16;
    /** Lies flat on the ground instead of facing the camera. */
    public static final int FLAT = 32;

    public final int capacity;
    private int cap;
    private int live;
    private int cursor;

    public final double[] x, y, z, px, py, pz;
    public final float[] vx, vy, vz;
    public final float[] size0, size1, rot, prot, rotV, drag, grav, floor, stretch;
    public final int[] age, life, c0, c1, sprite, frame, flags, light;

    public FxPool(int capacity) {
        this.capacity = capacity;
        this.cap = capacity;
        x = new double[capacity];
        y = new double[capacity];
        z = new double[capacity];
        px = new double[capacity];
        py = new double[capacity];
        pz = new double[capacity];
        vx = new float[capacity];
        vy = new float[capacity];
        vz = new float[capacity];
        size0 = new float[capacity];
        size1 = new float[capacity];
        rot = new float[capacity];
        prot = new float[capacity];
        rotV = new float[capacity];
        drag = new float[capacity];
        grav = new float[capacity];
        floor = new float[capacity];
        stretch = new float[capacity];
        age = new int[capacity];
        life = new int[capacity];
        c0 = new int[capacity];
        c1 = new int[capacity];
        sprite = new int[capacity];
        frame = new int[capacity];
        flags = new int[capacity];
        light = new int[capacity];
    }

    public int live() {
        return live;
    }

    public int cap() {
        return cap;
    }

    /** Lowers or raises the live cap (setting changes); sprites above a lowered cap are dropped. */
    public void setCap(int newCap) {
        cap = Math.max(1, Math.min(capacity, newCap));
        if (live > cap) live = cap;
        if (cursor >= cap) cursor = 0;
    }

    public void clear() {
        live = 0;
        cursor = 0;
    }

    /** Adds a sprite at world coordinates and returns its slot. */
    public int add(double x, double y, double z, float vx, float vy, float vz, int life, float size0, float size1,
                   int c0, int c1, int sprite, int flags) {
        int i;
        if (live < cap) {
            i = live++;
        } else {
            // full: reuse the most-spent of a few slots near the cursor
            i = cursor;
            float worst = -1;
            for (int k = 0; k < 8; k++) {
                int j = (cursor + k) % cap;
                float spent = age[j] / (float) Math.max(1, this.life[j]);
                if (spent > worst) {
                    worst = spent;
                    i = j;
                }
            }
            cursor = (cursor + 8) % cap;
        }
        this.x[i] = this.px[i] = x;
        this.y[i] = this.py[i] = y;
        this.z[i] = this.pz[i] = z;
        this.vx[i] = vx;
        this.vy[i] = vy;
        this.vz[i] = vz;
        this.life[i] = Math.max(1, life);
        this.age[i] = 0;
        this.size0[i] = size0;
        this.size1[i] = size1;
        this.c0[i] = c0;
        this.c1[i] = c1;
        this.sprite[i] = sprite;
        this.frame[i] = 0;
        this.flags[i] = flags;
        this.light[i] = 0xF000F0;
        this.rot[i] = this.prot[i] = 0;
        this.rotV[i] = 0;
        this.drag[i] = 0.98f;
        this.grav[i] = 0;
        this.floor[i] = -Float.MAX_VALUE;
        this.stretch[i] = 1;
        return i;
    }

    /** One game tick of motion and ageing. */
    public void tick() {
        int i = 0;
        while (i < live) {
            if (++age[i] >= life[i]) {
                removeAt(i);
                continue;
            }
            px[i] = x[i];
            py[i] = y[i];
            pz[i] = z[i];
            prot[i] = rot[i];
            vy[i] -= grav[i];
            float d = drag[i];
            vx[i] *= d;
            vy[i] *= d;
            vz[i] *= d;
            x[i] += vx[i];
            y[i] += vy[i];
            z[i] += vz[i];
            if (y[i] < floor[i]) {
                y[i] = floor[i];
                vy[i] = 0;
                vx[i] *= 0.6f;
                vz[i] *= 0.6f;
            }
            rot[i] += rotV[i];
            i++;
        }
    }

    private void removeAt(int i) {
        int last = --live;
        if (i == last) return;
        x[i] = x[last];
        y[i] = y[last];
        z[i] = z[last];
        px[i] = px[last];
        py[i] = py[last];
        pz[i] = pz[last];
        vx[i] = vx[last];
        vy[i] = vy[last];
        vz[i] = vz[last];
        size0[i] = size0[last];
        size1[i] = size1[last];
        rot[i] = rot[last];
        prot[i] = prot[last];
        rotV[i] = rotV[last];
        drag[i] = drag[last];
        grav[i] = grav[last];
        floor[i] = floor[last];
        stretch[i] = stretch[last];
        age[i] = age[last];
        life[i] = life[last];
        c0[i] = c0[last];
        c1[i] = c1[last];
        sprite[i] = sprite[last];
        frame[i] = frame[last];
        flags[i] = flags[last];
        light[i] = light[last];
    }

    /** 0..1 progress of sprite i at a partial tick. */
    public float progress(int i, float pt) {
        return Math.min(1f, (age[i] + pt) / life[i]);
    }
}
