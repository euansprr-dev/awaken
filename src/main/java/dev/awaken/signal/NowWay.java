package dev.awaken.signal;

/**
 * How far and which way a place is, height included (SPEC §27.1): "here" only when it's within reach of where you stand,
 * else how high it is above or below you. Pure, so the Now line, the band and the call-back toast say it the same way and a
 * test can pin it. Nothing in reach is never "here" just because the place is straight over your head.
 */
public final class NowWay {
    private NowWay() {}

    /** Within this horizontally (blocks) a place is at your feet. */
    public static final double NEAR = 4;
    /** And within this vertically it is in reach; further up or down it is said to be above or below. */
    public static final double REACH = 6;
    /** A height difference from here is worth saying beside a distance. */
    public static final double SAY_HEIGHT = 8;

    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private static final String[] NAMES = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    /** The Now line's compass: "here", "58 m above you", "↗ 58 m", "↗ 58 m · 30 above". {@code yawDeg} is where the player faces. */
    public static String arrow(double dx, double dy, double dz, double yawDeg) {
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat < NEAR) return overhead(dy);
        double rel = wrap(Math.toDegrees(Math.atan2(-dx, dz)) - yawDeg);
        int idx = Math.floorMod((int) Math.round(rel / 45.0), 8);
        return ARROWS[idx] + " " + Math.round(flat) + " m" + height(dy);
    }

    /** The band's and the toast's way: "here", "58 m above you", "640 m NE", "640 m NE · 30 above" (north is -Z). */
    public static String named(double dx, double dy, double dz) {
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat < NEAR) return overhead(dy);
        double ang = (Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360;
        return String.format("%,d m %s", Math.round(flat), NAMES[(int) Math.round(ang / 45.0) % 8]) + height(dy);
    }

    private static String overhead(double dy) {
        if (Math.abs(dy) <= REACH) return "here";
        return step(Math.abs(dy)) + " m " + (dy > 0 ? "above you" : "below you");
    }

    private static String height(double dy) {
        if (Math.abs(dy) < SAY_HEIGHT) return "";
        return " · " + step(Math.abs(dy)) + (dy > 0 ? " above" : " below");
    }

    /** Heights round to the nearest 5, so the line doesn't flicker as you walk. */
    private static long step(double dy) {
        return Math.max(5, Math.round(dy / 5.0) * 5);
    }

    private static double wrap(double deg) {
        double d = deg % 360;
        if (d >= 180) d -= 360;
        if (d < -180) d += 360;
        return d;
    }
}
