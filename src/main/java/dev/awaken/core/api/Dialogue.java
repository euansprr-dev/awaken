package dev.awaken.core.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/** Client speech sources share one use press; choices and server-owned rewards are never answered here. */
public final class Dialogue {
    private Dialogue() {}

    public interface Source {
        boolean active();
        void advance();
    }
    private record Entry(int priority, Source source) {}
    private static final List<Entry> SOURCES = new ArrayList<>();
    private static final List<Consumer<String>> FINISHED = new ArrayList<>();
    private static final Press PRESS = new Press();
    /**
     * A conversation the player started (a use on an NPC, or a press that spoke): only then does speech that needs
     * one ({@link #talking()}) take the use key. Speech that simply arrives (a quest step done in the field, a timed
     * scene line) never costs a shield block, a meal or a bow draw. It lasts while speech is up and 2 s after.
     */
    private static int talking;
    static final int REPLY_TICKS = 100, LINGER_TICKS = 40;

    /** Install once at client startup. Higher priority wins when two speech layers overlap. */
    public static void register(int priority, Source source) {
        SOURCES.add(new Entry(priority, source));
        SOURCES.sort(Comparator.comparingInt(Entry::priority).reversed());
    }

    /** A server-paged speaker can request its next line after the last displayed chunk is dismissed. */
    public static void onFinished(Consumer<String> listener) { FINISHED.add(listener); }
    public static void finished(String topic) {
        for (var listener : FINISHED) listener.accept(topic);
    }

    /** True consumes this use event, including held repeats after the last line has gone. */
    public static boolean use(boolean mainHand) {
        Source source = null;
        for (int i = 0; i < SOURCES.size(); i++) {
            if (SOURCES.get(i).source.active()) { source = SOURCES.get(i).source; break; }
        }
        Press.Result result = PRESS.use(mainHand, source != null);
        if (result == Press.Result.ADVANCE) source.advance();
        if (result != Press.Result.PASS && source != null) talking = Math.max(talking, LINGER_TICKS);
        return result != Press.Result.PASS;
    }
    /** The opening NPC interaction went to the server; repeats wait for a fresh physical press. */
    public static void capture() { PRESS.capture(); talking = Math.max(talking, REPLY_TICKS); }
    public static void release() { PRESS.release(); }
    /** Whether the player is in a conversation they started (see {@link #talking}). */
    public static boolean talking() { return talking > 0; }
    public static void tick() {
        PRESS.tick();
        if (talking <= 0) return;
        for (int i = 0; i < SOURCES.size(); i++) {
            if (SOURCES.get(i).source.active()) { talking = Math.max(talking, LINGER_TICKS); break; }
        }
        talking--;
    }
    public static void reset() { PRESS.reset(); talking = 0; }

    /** Physical press latch, independent of Minecraft so repeat/dual-hand/race cases are testable. */
    public static final class Press {
        public enum Result { PASS, CONSUME, ADVANCE }
        private boolean down, consumed;
        private int awaiting;
        public Result use(boolean mainHand, boolean active) {
            if (!mainHand) return consumed || active ? Result.CONSUME : Result.PASS;
            if (active) awaiting = 0;
            boolean first = !down;
            down = true;
            if (consumed) return Result.CONSUME;
            if (!active) {
                if (awaiting == 0) return Result.PASS;
                consumed = true;
                return Result.CONSUME;
            }
            consumed = true;
            return first ? Result.ADVANCE : Result.CONSUME;
        }
        public void capture() { down = consumed = true; awaiting = 20; }
        public void tick() { if (awaiting > 0) awaiting--; }
        public void reset() { release(); awaiting = 0; }
        public void release() { down = consumed = false; }
    }
}
