package dev.awaken.village.trouble;

import dev.awaken.citizens.Lines;
import net.minecraft.server.level.ServerLevel;

/**
 * What people say in a trouble ({@code data/awaken/village/village_lines.json} under {@code trouble}: read by the
 * citizens' {@link Lines} under {@code village.trouble.*}), picked by a seed (never at random) for the hour, and filled:
 * {person} the one it's about, {kin} who asks, {village}, {place} where the deed is, {dir} the compass way there,
 * {name} the player, {beast} what took them.
 */
final class TroubleText {
    private TroubleText() {}

    static int tod(ServerLevel level) {
        return (int) Math.floorMod(level.getOverworldClockTime(), 24000L);
    }

    /** A line from a pool ("" when it's empty). */
    static String line(ServerLevel level, String key, long seed) {
        return Lines.get().pick(seed * 0x9E3779B97F4A7C15L + key.hashCode(), tod(level), key);
    }

    /** Fills a line's names: pairs of placeholder (without braces) and value. */
    static String fill(String line, String... pairs) {
        if (line.indexOf('{') < 0) return line;
        for (int i = 0; i + 1 < pairs.length; i += 2) line = line.replace("{" + pairs[i] + "}", pairs[i + 1]);
        return line;
    }

    static String given(String fullName) {
        int sp = fullName.indexOf(' ');
        return sp < 0 ? fullName : fullName.substring(0, sp);
    }

    static String compass(double dx, double dz) {
        String[] dirs = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        double deg = (Math.toDegrees(Math.atan2(dx, -dz)) + 360 + 22.5) % 360;
        return dirs[(int) (deg / 45) % 8];
    }
}
