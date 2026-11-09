package dev.awaken.hall.client;

import java.util.concurrent.ThreadLocalRandom;

/** Slow sparks in the air of the Hall. */
final class Embers {
    private static final int N = 46;
    private final float[] x = new float[N], y = new float[N], v = new float[N], phase = new float[N];
    private final int[] size = new int[N];

    Embers() {
        var r = ThreadLocalRandom.current();
        for (int i = 0; i < N; i++) {
            x[i] = r.nextFloat() * Stage.W;
            y[i] = r.nextFloat() * Stage.H;
            v[i] = 8F + r.nextFloat() * 26F;
            phase[i] = r.nextFloat() * 6.28F;
            size[i] = 2 + r.nextInt(3);
        }
    }

    void draw(Stage s, float dt, float time, int rgb, float strength) {
        for (int i = 0; i < N; i++) {
            y[i] -= v[i] * dt;
            x[i] += (float) Math.sin(time * .8F + phase[i]) * .28F;
            if (y[i] < -8F) {
                y[i] = Stage.H + 8F;
                x[i] = ThreadLocalRandom.current().nextFloat() * Stage.W;
            }
            float a = strength * (.3F + .5F * Math.abs((float) Math.sin(time * 1.4F + phase[i])));
            s.fill(x[i], y[i], x[i] + size[i], y[i] + size[i], Stage.argb(rgb, a));
        }
    }
}
