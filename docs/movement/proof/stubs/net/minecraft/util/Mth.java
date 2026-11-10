package net.minecraft.util;
public final class Mth {
    public static final float DEG_TO_RAD = (float) (Math.PI / 180), RAD_TO_DEG = (float) (180 / Math.PI), PI = (float) Math.PI, HALF_PI = (float) (Math.PI / 2);
    public static double clamp(double v, double lo, double hi) { return v < lo ? lo : Math.min(v, hi); }
    public static float clamp(float v, float lo, float hi) { return v < lo ? lo : Math.min(v, hi); }
    public static int clamp(int v, int lo, int hi) { return v < lo ? lo : Math.min(v, hi); }
    public static float sin(float v) { return (float) Math.sin(v); }
    public static float cos(float v) { return (float) Math.cos(v); }
    public static double lerp(double t, double a, double b) { return a + t * (b - a); }
    public static float sqrt(float v) { return (float) Math.sqrt(v); }
}
