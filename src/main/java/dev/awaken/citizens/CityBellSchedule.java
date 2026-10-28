package dev.awaken.citizens;

/** One city peal at a time. Transient: reconnect/reload never replays missed hours. */
final class CityBellSchedule {
    static final int SPACING = 160, ALARM_GAP = 1200;
    private int bell = -1, left;
    private long next, alarmAfter;
    private boolean alarm;

    boolean routine(long now, int tod, int tower) {
        int count = switch (tod) { case 0 -> 1; case 6000 -> 2; case 12000 -> 3; default -> 0; };
        if (count == 0 || tower < 0 || left > 0 || now < next) return false;
        start(now, tower, count, false);
        return true;
    }

    boolean alarm(long now, int tower, int requested) {
        if (tower < 0 || requested <= 0 || now < alarmAfter) return false;
        alarmAfter = now + ALARM_GAP;
        // Replace pending routine strokes, but let the current recording finish.
        start(Math.max(now + 10, next), tower, Math.min(3, requested), true);
        return true;
    }

    private void start(long when, int tower, int count, boolean urgent) {
        bell = tower;
        next = when;
        left = count;
        alarm = urgent;
    }

    long[] next(long now) {
        if (left == 0 || now < next) return null;
        left--;
        next = now + SPACING;
        return new long[]{bell, now, 1, SPACING, alarm ? 1 : 0};
    }

    boolean pealing(int tower, long now) { return left > 0 && bell == tower && next <= now + 40; }
    void clear() { bell = -1; left = 0; next = 0; alarmAfter = 0; }
}
