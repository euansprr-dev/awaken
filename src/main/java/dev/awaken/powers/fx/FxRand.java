package dev.awaken.powers.fx;

/**
 * A tiny reseedable random (xorshift64*). Recipes reseed it from the event's seed, so every client builds the same
 * shape, and reuse one instance, so building an effect allocates nothing.
 */
public final class FxRand {
    private long s = 1;

    public FxRand seed(long seed) {
        // splitmix64 scramble so nearby seeds diverge at once
        long z = seed + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        s = (z ^ (z >>> 31)) | 1;
        return this;
    }

    public long nextLong() {
        s ^= s >>> 12;
        s ^= s << 25;
        s ^= s >>> 27;
        return s * 0x2545F4914F6CDD1DL;
    }

    /** [0, 1). */
    public float f() {
        return (nextLong() >>> 40) / (float) (1L << 24);
    }

    /** [a, b). */
    public float f(float a, float b) {
        return a + (b - a) * f();
    }

    /** [-1, 1). */
    public float s() {
        return f() * 2 - 1;
    }

    public int i(int bound) {
        return bound <= 0 ? 0 : (int) ((nextLong() >>> 33) % bound);
    }

    public boolean chance(float p) {
        return f() < p;
    }
}
