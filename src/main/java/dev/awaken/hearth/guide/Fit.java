package dev.awaken.hearth.guide;

/**
 * Server-side text widths, so lines are chosen to fit before they are sent: a toast shows its title and the first
 * sentence of its text in 144 px; the Now tracker about 148 px after its "◇ " at the smallest auto GUI scale (378 wide), 100 when
 * it also draws a compass. Advance widths of Minecraft's default font (the table the level road's checks use).
 */
public final class Fit {
    private Fit() {}

    public static final int TOAST = 144, TRACKER = 148, TRACKER_WITH_COMPASS = 100;

    public static int px(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) w += width(s.charAt(i));
        return w;
    }

    private static int width(char c) {
        return switch (c) {
            case 'i', '!', '.', ',', ':', ';', '\'', '|' -> 2;
            case 'l' -> 3;
            case 't', 'I', '[', ']', ' ' -> 4;
            case 'f', 'k', '<', '>', '(', ')', '{', '}', '*', '"' -> 5;
            case '@', '~' -> 7;
            default -> c > 0x7F ? 9 : 6; // ✓, ✗, ◇ come from the wide fallback font
        };
    }

    /** The first of {@code options} that fits {@code max} px, else the last. */
    public static String first(int max, String... options) {
        for (String o : options) if (px(o) <= max) return o;
        return options[options.length - 1];
    }

    /** The first sentence of a text (what a toast shows under its title). */
    public static String firstSentence(String s) {
        for (int i = 0; i < s.length() - 1; i++) {
            char c = s.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && s.charAt(i + 1) == ' ') return s.substring(0, i + 1);
        }
        return s;
    }
}
