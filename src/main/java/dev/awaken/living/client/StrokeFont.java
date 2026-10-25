package dev.awaken.living.client;

/**
 * Numbers written in strokes of light (SPEC §21.1): each glyph a few line segments on a 4×7 grid, drawn as glowing
 * ribbons by {@link LivingFx}, slanted a little forward. Digits, '+', '-', '.', and the letters of "XP" and "LV".
 */
final class StrokeFont {
    private StrokeFont() {}

    static final float WIDTH = 4, HEIGHT = 7, ADVANCE = 5.6F, SLANT = 0.14F;

    // segments: x0 y0 x1 y1 (y up)
    private static final float[] A = {0, 7, 4, 7}, B = {4, 7, 4, 3.5F}, C = {4, 3.5F, 4, 0}, D = {0, 0, 4, 0},
            E = {0, 0, 0, 3.5F}, F = {0, 3.5F, 0, 7}, G = {0, 3.5F, 4, 3.5F};

    private static float[][] g(float[]... s) {
        return s;
    }

    private static final float[][][] DIGITS = {
            g(A, B, C, D, E, F),        // 0
            g(new float[]{2.5F, 7, 2.5F, 0}, new float[]{1, 5.5F, 2.5F, 7}), // 1
            g(A, B, G, E, D),           // 2
            g(A, B, G, C, D),           // 3
            g(F, G, B, C),              // 4
            g(A, F, G, C, D),           // 5
            g(A, F, G, E, C, D),        // 6
            g(A, B, C),                 // 7
            g(A, B, C, D, E, F, G),     // 8
            g(A, B, C, D, F, G),        // 9
    };
    private static final float[][] PLUS = g(new float[]{2, 1.5F, 2, 5.5F}, new float[]{0, 3.5F, 4, 3.5F});
    private static final float[][] MINUS = g(G);
    private static final float[][] DOT = g(new float[]{1.7F, 0, 2.3F, 0.6F});
    private static final float[][] X = g(new float[]{0, 0, 4, 7}, new float[]{0, 7, 4, 0});
    private static final float[][] P = g(F, E, A, B, G);
    private static final float[][] L = g(F, E, D);
    private static final float[][] V = g(new float[]{0, 7, 2, 0}, new float[]{2, 0, 4, 7});
    private static final float[][] NONE = new float[0][];

    static float[][] glyph(char c) {
        if (c >= '0' && c <= '9') return DIGITS[c - '0'];
        return switch (c) {
            case '+' -> PLUS;
            case '-' -> MINUS;
            case '.' -> DOT;
            case 'X' -> X;
            case 'P' -> P;
            case 'L' -> L;
            case 'V' -> V;
            default -> NONE;
        };
    }

    /** Width of a string in grid units. */
    static float width(CharSequence s) {
        int n = s.length();
        if (n == 0) return 0;
        float w = 0;
        for (int i = 0; i < n; i++) w += s.charAt(i) == ' ' ? ADVANCE * 0.6F : ADVANCE;
        return w - (ADVANCE - WIDTH);
    }
}
