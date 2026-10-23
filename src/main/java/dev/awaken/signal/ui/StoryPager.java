package dev.awaken.signal.ui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Splits a story line (the Signal's voice) into subtitles of at most two lines. The title comes first on its own,
 * then the body, chunk by chunk. Chunks are cut at the width in force when each one starts, so a subtitle that has to
 * make room for a card simply wraps narrower. Pure logic.
 */
public final class StoryPager {
    public static final int MAX_LINES = 2;

    private final Deque<String> titleWords = new ArrayDeque<>();
    private final Deque<String> bodyWords = new ArrayDeque<>();
    private int chunks;

    public StoryPager(String title, String body) {
        add(titleWords, title);
        add(bodyWords, body);
        if (bodyWords.isEmpty()) { // a lone title is the text
            bodyWords.addAll(titleWords);
            titleWords.clear();
        }
    }

    private static void add(Deque<String> into, String s) {
        if (s == null) return;
        for (String w : s.replace('\n', ' ').split(" ")) if (!w.isBlank()) into.add(w);
    }

    public boolean done() {
        return titleWords.isEmpty() && bodyWords.isEmpty();
    }

    /** Whether the next chunk is the title. */
    public boolean titleNext() {
        return !titleWords.isEmpty();
    }

    public int chunksTaken() {
        return chunks;
    }

    /** The next subtitle: up to {@link #MAX_LINES} lines that fit {@code width}. Empty when done. */
    public List<String> next(int width, ToIntFunction<String> measure) {
        Deque<String> from = titleWords.isEmpty() ? bodyWords : titleWords;
        List<String> lines = new ArrayList<>();
        String line = "";
        while (!from.isEmpty() && lines.size() < MAX_LINES) {
            String word = from.peekFirst();
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (measure.applyAsInt(candidate) <= width) {
                line = candidate;
                from.removeFirst();
                continue;
            }
            if (line.isEmpty()) { // one word wider than the line: split it
                int cut = word.length() - 1;
                while (cut > 1 && measure.applyAsInt(word.substring(0, cut)) > width) cut--;
                lines.add(word.substring(0, cut));
                from.removeFirst();
                from.addFirst(word.substring(cut));
                continue;
            }
            lines.add(line);
            line = "";
        }
        if (!line.isEmpty() && lines.size() < MAX_LINES) lines.add(line);
        if (!lines.isEmpty()) chunks++;
        return lines;
    }

    /** Puts a chunk back (the width changed under it) so the next call cuts it again. */
    public void pushBack(List<String> lines, boolean title) {
        Deque<String> into = title ? titleWords : bodyWords;
        List<String> words = new ArrayList<>();
        for (String l : lines) for (String w : l.split(" ")) if (!w.isBlank()) words.add(w);
        for (int i = words.size() - 1; i >= 0; i--) into.addFirst(words.get(i));
        if (!lines.isEmpty()) chunks--;
    }

    /** How long a subtitle stays after it has typed out: long enough to read, never a wait. */
    public static int holdTicks(int chars) {
        return Math.max(50, Math.min(180, 30 + chars * 2));
    }
}
