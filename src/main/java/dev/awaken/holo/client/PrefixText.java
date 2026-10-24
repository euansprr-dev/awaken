package dev.awaken.holo.client;

import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.StringDecomposer;

/**
 * A reusable text for a panel line: a String or a FormattedCharSequence, cut to its first {@link #limit} characters
 * (that's how lines type in). One per line slot, kept between frames, so drawing text allocates nothing of ours.
 */
final class PrefixText implements FormattedCharSequence, FormattedCharSink {
    Object source;
    int limit = Integer.MAX_VALUE;
    private FormattedCharSink downstream;
    private int count;

    PrefixText set(Object source, int limit) {
        this.source = source;
        this.limit = limit;
        return this;
    }

    @Override
    public boolean accept(FormattedCharSink output) {
        downstream = output;
        count = 0;
        try {
            if (source instanceof String s) return StringDecomposer.iterateFormatted(s, Style.EMPTY, this);
            if (source instanceof FormattedCharSequence f) return f.accept(this);
            return true;
        } finally {
            downstream = null;
        }
    }

    @Override
    public boolean accept(int position, Style style, int codepoint) {
        if (count++ >= limit) return false;
        return downstream.accept(position, style, codepoint);
    }

    /** Characters in the whole source (what typing counts toward). */
    int length() {
        if (source instanceof String s) return s.length();
        return 64;
    }
}
