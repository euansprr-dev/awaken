package dev.awaken.gates.realms.kit;

import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import static dev.awaken.gates.realms.kit.N.lerp;
import static dev.awaken.gates.realms.kit.N.sq;

/** A river down a smooth curve through its key points, narrowing as it goes, with banks that wander. */
public final class River {
    private final double[] ru, rv;
    private final SimplexNoise banks;
    private final double startHalf, endHalf;

    public River(double[][] keys, int seg, SimplexNoise banks, double startHalf, double endHalf) {
        this.banks = banks;
        this.startHalf = startHalf;
        this.endHalf = endHalf;
        int n = keys.length;
        ru = new double[(n - 1) * seg + 1];
        rv = new double[ru.length];
        for (int i = 0; i < n - 1; i++) for (int s = 0; s <= seg; s++) {
            double[] a = keys[Math.max(0, i - 1)], b = keys[i], c = keys[i + 1], d = keys[Math.min(n - 1, i + 2)];
            double t = s / (double) seg, t2 = t * t, t3 = t2 * t;
            ru[i * seg + s] = 0.5 * (2 * b[0] + (c[0] - a[0]) * t + (2 * a[0] - 5 * b[0] + 4 * c[0] - d[0]) * t2 + (3 * b[0] - a[0] - 3 * c[0] + d[0]) * t3);
            rv[i * seg + s] = 0.5 * (2 * b[1] + (c[1] - a[1]) * t + (2 * a[1] - 5 * b[1] + 4 * c[1] - d[1]) * t2 + (3 * b[1] - a[1] - 3 * c[1] + d[1]) * t3);
        }
    }

    /** Distance to the bank, negative in the water. */
    public double d(double u, double v) {
        double best = 1e12;
        int at = 0;
        for (int i = 0; i < ru.length; i += 3) {
            double d = sq(u - ru[i]) + sq(v - rv[i]);
            if (d < best) {
                best = d;
                at = i;
            }
        }
        double t = at / (double) (ru.length - 1);
        return Math.sqrt(best) - (lerp(startHalf, endHalf, t) + 2.4 * banks.get(u / 26.0, v / 26.0));
    }
}
