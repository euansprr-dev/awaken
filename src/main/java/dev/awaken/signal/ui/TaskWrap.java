package dev.awaken.signal.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Lays one step of the task card out over a few lines instead of cutting it short (Euan, 2026-10-27: "we need to fix
 * truncation"): words fill each line up to {@code room}; a {@code {key}} in the text becomes a key cap that sits in the
 * line like a word; the count and compass ({@code tail}) go on the last line, or on a line of their own if they don't
 * fit. A step longer than {@code maxLines} lines ends in an ellipsis. Pure (a width function in, segments out) so a
 * GameTest can check it; the card draws the segments.
 */
public final class TaskWrap {
    private TaskWrap() {}

    public static final int TEXT = 0, KEY = 1, TAIL = 2;

    /** A run of text, a key cap (its name) or the tail, and the width it advances the line by. */
    public record Seg(String text, int w, int kind) {}

    private static final String KEY_MARK = "{key}";

    /**
     * @param keyName the name to draw in the cap for {@code {key}}, or null to drop the marker
     * @param keyAdvance how far a cap moves the line (its width and gap)
     * @param tail the count and compass after the words ("2/5  ↗ 9 m"), or empty
     */
    public static List<List<Seg>> wrap(String text, String keyName, int keyAdvance, String tail, int room, int maxLines, ToIntFunction<String> width) {
        List<List<Seg>> lines = new ArrayList<>();
        List<Seg> cur = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        int space = width.applyAsInt(" "), x = 0;
        boolean afterKey = false, cut = false;
        for (String token : text.replace(KEY_MARK, " " + KEY_MARK + " ").trim().split("\\s+")) {
            if (token.isEmpty()) continue;
            boolean key = token.equals(KEY_MARK);
            if (key && keyName == null) continue;
            if (!key && width.applyAsInt(token) > room) { // one word wider than the card: cut it, so a line never overflows
                String t = token;
                while (t.length() > 1 && width.applyAsInt(t + "…") > room) t = t.substring(0, t.length() - 1);
                token = t + "…";
            }
            int tw = key ? keyAdvance : width.applyAsInt(token);
            int add = x == 0 ? tw : tw + space;
            if (x > 0 && x + add > room) {
                flush(cur, run, width);
                lines.add(cur);
                if (lines.size() >= maxLines) {
                    cut = true;
                    cur = lines.removeLast();
                    break;
                }
                cur = new ArrayList<>();
                x = 0;
                add = tw;
                afterKey = false;
            }
            if (key) {
                if (run.length() > 0) run.append(' ');
                flush(cur, run, width);
                cur.add(new Seg(keyName, keyAdvance, KEY));
                x += add;
                afterKey = true;
            } else {
                if (run.length() > 0 || afterKey) run.append(' ');
                afterKey = false;
                run.append(token);
                x += add;
            }
        }
        flush(cur, run, width);
        if (cut) ellipsis(cur, room, width);
        if (!cur.isEmpty() || lines.isEmpty()) lines.add(cur);
        if (!tail.isEmpty()) {
            int tailW = width.applyAsInt(tail);
            List<Seg> last = lines.getLast();
            if (used(last) + tailW > room) lines.add(new ArrayList<>(List.of(new Seg(tail, tailW, TAIL))));
            else last.add(new Seg(tail, tailW, TAIL));
        }
        return lines;
    }

    private static void flush(List<Seg> line, StringBuilder run, ToIntFunction<String> width) {
        if (run.length() == 0) return;
        String s = run.toString();
        line.add(new Seg(s, width.applyAsInt(s), TEXT));
        run.setLength(0);
    }

    static int used(List<Seg> line) {
        int w = 0;
        for (Seg s : line) w += s.w();
        return w;
    }

    /** A step that runs past its last line ends in an ellipsis that fits. */
    private static void ellipsis(List<Seg> line, int room, ToIntFunction<String> width) {
        for (int i = line.size() - 1; i >= 0; i--) {
            Seg s = line.get(i);
            if (s.kind() != TEXT) continue;
            int others = used(line) - s.w();
            String t = s.text().stripTrailing();
            while (!t.isEmpty() && others + width.applyAsInt(t + "…") > room) t = t.substring(0, t.length() - 1).stripTrailing();
            String out = t + "…";
            line.set(i, new Seg(out, width.applyAsInt(out), TEXT));
            return;
        }
        line.add(new Seg("…", width.applyAsInt("…"), TEXT));
    }
}
