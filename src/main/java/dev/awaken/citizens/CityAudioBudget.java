package dev.awaken.citizens;

/** Per-listener admission, shared by all city ambient emitters. No saved state. */
public final class CityAudioBudget {
    public static final int VOICES = 3;
    private static final int[] GAPS = {156, 1800, 160, 40, 100, 600};
    private final long[] next = new long[GAPS.length];
    private long detailAfter, last = Long.MIN_VALUE;

    public static int group(String path) {
        if (path.startsWith("city.bell.") || path.startsWith("city.carillon.")) return 0;
        if (path.equals("city.handbell")) return 1;
        if (path.startsWith("city.pigeon.") || path.equals("city.songbird") || path.equals("city.gull.call")) return 2;
        if (path.equals("city.cart.roll")) return 4;
        if (path.equals("city.cart.bells")) return 5;
        return 3;
    }

    public boolean accept(int group, long now, int voices) {
        if (now < last) clear();
        last = now;
        if (now < next[group]) return false;
        if (group != 0 && (voices >= VOICES || now < detailAfter)) return false;
        next[group] = now + GAPS[group];
        detailAfter = now + (group == 0 ? 156 : 30);
        return true;
    }

    public void clear() { java.util.Arrays.fill(next, 0); detailAfter = 0; last = Long.MIN_VALUE; }
}
