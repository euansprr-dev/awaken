package dev.awaken.signal.client;

import dev.awaken.signal.net.SignalPayloads;
import net.minecraft.client.KeyMapping;
import org.jspecify.annotations.Nullable;

/**
 * What the Tab glance reads from the System (SPEC §40.7): the task as the server last sent it, its current and next
 * step in words (a key named as the player has it bound), and the place you're heading. Client only.
 */
public final class SignalGlance {
    private SignalGlance() {}

    /** The tracked task's title, "" when there is none. */
    public static String taskTitle() {
        return TaskHud.task().title();
    }

    /** Steps done and in all. */
    public static int done() {
        int n = 0;
        for (SignalPayloads.TaskRow r : TaskHud.task().rows()) if (r.done()) n++;
        return n;
    }

    public static int steps() {
        return TaskHud.task().rows().size();
    }

    /** The {@code k}th step not yet done (0 the current one) in words, with its count, or null. */
    public static @Nullable String step(int k) {
        for (SignalPayloads.TaskRow r : TaskHud.task().rows()) {
            if (r.done()) continue;
            if (k-- > 0) continue;
            String text = r.text();
            if (text.contains("{key}")) {
                KeyMapping m = r.key().isEmpty() ? null : KeyMapping.get(r.key());
                text = text.replace("{key}", "[" + (m == null ? "?" : m.getTranslatedKeyMessage().getString()) + "]");
            }
            return r.need() > 0 ? text + " " + r.have() + "/" + r.need() : text;
        }
        return null;
    }

    /** Where you're heading (the Now line's place), or null. */
    public static dev.awaken.signal.NowCorner.@Nullable Place heading() {
        return NowClient.current();
    }
}
