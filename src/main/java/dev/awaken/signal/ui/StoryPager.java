package dev.awaken.signal.ui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;

/**
 * Splits a story line (the Signal's voice, a speaker's line) into subtitles. The title comes first on its own, then the
 * body, page by page. A page is whole sentences: as many as fit in the lines the screen has room for, and never a
 * sentence cut in two (SPEC §5, "Story lines"). Only a sentence too long for a whole page is split, and then after a
 * comma, colon or dash where it can be. Pages are cut at the width in force when each one starts, so a subtitle that
 * has to make room for a card simply wraps narrower. No word is ever dropped or ellipsized. Pure logic.
 */
public final class StoryPager {
    /** A page is at least this many lines when the screen is short, at most {@link #MAX_LINES}. */
    public static final int MIN_LINES = 2, MAX_LINES = 3;

    private static final Pattern SENTENCES = Pattern.compile("(?<=[.!?…]|[.!?…][\"'”’)])\\s+");

    private final Deque<String> title = new ArrayDeque<>();
    private final Deque<String> body = new ArrayDeque<>();
    private int chunks;
    // what the last next() took, so pushBack() can undo it
    private final List<String> taken = new ArrayList<>();
    private boolean takenTitle, takenSplit;

    public StoryPager(String title, String body) {
        add(this.title, title);
        add(this.body, body);
        if (this.body.isEmpty()) { // a lone title is the text
            this.body.addAll(this.title);
            this.title.clear();
        }
    }

    private static void add(Deque<String> into, String s) {
        if (s == null) return;
        for (String para : s.split("\n")) {
            for (String sentence : SENTENCES.split(para.strip())) {
                String t = sentence.strip().replaceAll("\\s+", " ");
                if (!t.isEmpty()) into.add(t);
            }
        }
    }

    public boolean done() {
        return title.isEmpty() && body.isEmpty();
    }

    /** Whether the next chunk is the title. */
    public boolean titleNext() {
        return !title.isEmpty();
    }

    public int chunksTaken() {
        return chunks;
    }

    /**
     * The next subtitle: whole sentences wrapped to {@code width}, in at most {@code maxLines} lines (a page holds at
     * least one sentence, split only when it is longer than a page). Empty when done.
     */
    public List<String> next(int width, int maxLines, ToIntFunction<String> measure) {
        int max = Math.max(1, maxLines);
        Deque<String> from = title.isEmpty() ? body : title;
        taken.clear();
        takenTitle = from == title;
        takenSplit = false;
        String text = "";
        List<String> lines = List.of();
        while (!from.isEmpty()) {
            String s = from.peekFirst();
            String candidate = text.isEmpty() ? s : text + " " + s;
            List<String> wrapped = SignalText.wrap(candidate, width, measure);
            if (wrapped.size() <= max) {
                text = candidate;
                lines = wrapped;
                taken.add(from.removeFirst());
                continue;
            }
            if (!text.isEmpty()) break; // the next sentence starts the next page
            String head = head(s, width, max, measure); // one sentence longer than a page
            from.removeFirst();
            String rest = s.substring(head.length()).strip();
            if (!rest.isEmpty()) from.addFirst(rest);
            taken.add(head);
            takenSplit = !rest.isEmpty();
            lines = SignalText.wrap(head, width, measure);
            break;
        }
        if (!lines.isEmpty()) chunks++;
        return lines;
    }

    /** The longest start of a too-long sentence that fits a page: ending on a clause break if one fits, else a word. */
    private static String head(String s, int width, int max, ToIntFunction<String> measure) {
        String[] words = s.split(" ");
        String best = "", clause = "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length - 1; i++) {
            if (i > 0) sb.append(' ');
            sb.append(words[i]);
            String prefix = sb.toString();
            if (SignalText.wrap(prefix, width, measure).size() > max) break;
            best = prefix;
            char last = words[i].charAt(words[i].length() - 1);
            if (last == ',' || last == ';' || last == ':' || words[i + 1].equals("-") || words[i + 1].equals("—")) clause = prefix;
        }
        if (!clause.isEmpty() && clause.length() * 3 >= best.length()) return clause; // unless it leaves a stub of a page
        return best.isEmpty() ? words[0] : best;
    }

    /** Puts the last chunk back (the width changed under it) so the next call cuts it again. */
    public void pushBack() {
        if (taken.isEmpty()) return;
        Deque<String> into = takenTitle ? title : body;
        if (takenSplit) into.addFirst(taken.removeLast() + " " + into.removeFirst());
        for (int i = taken.size() - 1; i >= 0; i--) into.addFirst(taken.get(i));
        taken.clear();
        chunks--;
    }

    /** A spoken line fades out over this many ticks after its last page. */
    public static final int FADE_TICKS = 10;

    /** How long a subtitle stays after it has typed out: long enough to read at an easy pace, never a wait. */
    public static int holdTicks(int chars) {
        return Math.max(50, Math.min(400, 30 + chars * 6 / 5));
    }

    /** Ticks a page of {@code chars} characters takes to type at {@code typeSpeed} characters a tick. */
    public static int typeTicks(int chars, double typeSpeed) {
        return (int) Math.ceil(chars / Math.max(0.5, typeSpeed));
    }

    /**
     * A generous estimate of how long {@code lines} take to say, one after another, at the default type speed (3
     * characters a tick) on any screen: typing, holding and fading each page. Servers use it so a card asked after a
     * speech still has its time once the speech is over.
     */
    public static int readTicks(List<String> lines) {
        int t = 0;
        for (String l : lines) {
            int chars = l.length();
            t += typeTicks(chars, 3.0) + 30 + chars * 6 / 5 + 30 * (1 + chars / 60) + FADE_TICKS;
        }
        return t;
    }

    /** Lines a page may take on this screen: what fits between the crosshair and the hotbar, 2 to 3. */
    public static int maxLines(int w, int h, double scale) {
        return Math.max(MIN_LINES, Math.min(MAX_LINES, SignalLayout.storyLinesThatFit(h, scale)));
    }
}
