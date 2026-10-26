package dev.awaken.signal.client;

import net.minecraft.client.KeyMapping;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Text with key placeholders ("Hold {key.awaken.movement} at a ring") in the player's own bindings ("Hold Left Alt"). */
public final class KeyText {
    private KeyText() {}

    private static final Pattern KEY = Pattern.compile("\\{(key\\.[a-z0-9_.]+)}");

    public static String resolve(String text) {
        Matcher m = KEY.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            KeyMapping k = KeyMapping.get(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(k == null ? m.group(1) : k.getTranslatedKeyMessage().getString()));
        }
        m.appendTail(out);
        return out.toString();
    }
}
