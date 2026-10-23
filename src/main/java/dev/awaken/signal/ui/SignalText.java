package dev.awaken.signal.ui;

import dev.awaken.signal.SignalWindow;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;

/**
 * Text rules for System v2: plain words, no hand-written key hints, one detail line for a toast, word wrapping and
 * shortening with a pluggable width function (the client passes the font; tests pass a fixed width).
 */
public final class SignalText {
    private SignalText() {}

    public static final String ELLIPSIS = "…";
    public static final String FALLBACK_YES = "Accept";
    public static final String FALLBACK_NO = "Not now";

    /** "[Y] Extract", "[N] Stay", "[G] speak to the dead": a bracketed key and the words after it, up to a separator. */
    private static final Pattern KEY_HINT = Pattern.compile("\\[[A-Za-z0-9 ]{1,8}]\\s*[^·|\\[]*");
    private static final Pattern SEPARATORS = Pattern.compile("\\s*([·|])\\s*(?:[·|]\\s*)*");

    /** Removes hand-written key hints; the renderer draws real ones from the key bindings. */
    public static String stripKeyHints(String s) {
        if (s == null || s.indexOf('[') < 0) return s == null ? "" : s.trim();
        String out = KEY_HINT.matcher(s).replaceAll("");
        out = SEPARATORS.matcher(out).replaceAll(" $1 ");
        out = out.trim();
        while (out.startsWith("·") || out.startsWith("|")) out = out.substring(1).trim();
        while (out.endsWith("·") || out.endsWith("|")) out = out.substring(0, out.length() - 1).trim();
        return out.replaceAll("\\s{2,}", " ");
    }

    /** What Y does, in plain words. */
    public static String yesLabel(SignalWindow w) {
        return w.yesLabel().map(Component::getString).filter(s -> !s.isBlank()).orElse(FALLBACK_YES);
    }

    /** What N does. An accept-only question (the prologue) can't be refused, and says so. */
    public static String noLabel(SignalWindow w) {
        return w.noLabel().map(Component::getString).filter(s -> !s.isBlank())
                .orElse(acceptOnly(w) ? "Accept anyway" : FALLBACK_NO);
    }

    public static boolean acceptOnly(SignalWindow w) {
        return w.action().map(a -> a.endsWith("accept_only")).orElse(false);
    }

    /** Every row as "label value", joined into one line. */
    public static String collapseRows(List<SignalWindow.Row> rows) {
        StringBuilder sb = new StringBuilder();
        for (SignalWindow.Row r : rows) {
            if (!sb.isEmpty()) sb.append(" · ");
            String label = r.label().getString().trim();
            String value = r.value().getString().trim();
            sb.append(label.isEmpty() ? value : label + " " + value);
        }
        return sb.toString();
    }

    /** The first sentence (or the whole text if it has one). */
    public static String firstSentence(String s) {
        if (s == null) return "";
        s = s.replace('\n', ' ').trim();
        for (int i = 0; i < s.length() - 1; i++) {
            char c = s.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && s.charAt(i + 1) == ' ') return s.substring(0, i + 1);
        }
        return s;
    }

    /** The one detail line a toast shows: the body's first sentence, else the rows, else the footer. */
    public static String toastDetail(SignalWindow w) {
        String body = w.body().map(Component::getString).map(SignalText::firstSentence).orElse("");
        if (!body.isBlank()) return body;
        if (!w.rows().isEmpty()) return collapseRows(w.rows());
        return w.footer().map(Component::getString).map(SignalText::stripKeyHints).orElse("");
    }

    /** Shortens {@code s} to fit {@code width}, ending in "…" when cut. */
    public static String ellipsize(String s, int width, ToIntFunction<String> measure) {
        if (s == null) return "";
        if (measure.applyAsInt(s) <= width) return s;
        int ew = measure.applyAsInt(ELLIPSIS);
        if (ew > width) return "";
        int lo = 0, hi = s.length();
        while (lo < hi) { // longest prefix that fits with the ellipsis
            int mid = (lo + hi + 1) >>> 1;
            if (measure.applyAsInt(s.substring(0, mid)) + ew <= width) lo = mid; else hi = mid - 1;
        }
        return s.substring(0, lo).stripTrailing() + ELLIPSIS;
    }

    /** Greedy word wrap. Words longer than a line are split. Explicit newlines start a new line. */
    public static List<String> wrap(String s, int width, ToIntFunction<String> measure) {
        List<String> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        width = Math.max(1, width);
        for (String para : s.split("\n", -1)) {
            String line = "";
            for (String word : para.split(" ")) {
                if (word.isEmpty()) continue;
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (measure.applyAsInt(candidate) <= width) {
                    line = candidate;
                    continue;
                }
                if (!line.isEmpty()) out.add(line);
                line = word;
                while (measure.applyAsInt(line) > width && line.length() > 1) { // a word longer than the line
                    int cut = line.length() - 1;
                    while (cut > 1 && measure.applyAsInt(line.substring(0, cut)) > width) cut--;
                    out.add(line.substring(0, cut));
                    line = line.substring(cut);
                }
            }
            out.add(line);
        }
        while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) out.remove(out.size() - 1);
        return out;
    }

    /** At most {@code max} wrapped lines; the last one ends in "…" if text was left over. */
    public static List<String> wrapAtMost(String s, int width, int max, ToIntFunction<String> measure) {
        List<String> lines = wrap(s, width, measure);
        if (lines.size() <= max) return lines;
        List<String> out = new ArrayList<>(lines.subList(0, max));
        String rest = String.join(" ", lines.subList(max - 1, lines.size()));
        out.set(max - 1, ellipsize(rest, width, measure));
        return out;
    }

    /** Fixed-width measure for tests and server-side estimates: 6 px per character, like most of Minecraft's font. */
    public static int approxWidth(String s) {
        return s == null ? 0 : s.length() * 6;
    }
}
