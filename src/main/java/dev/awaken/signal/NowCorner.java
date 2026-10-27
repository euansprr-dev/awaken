package dev.awaken.signal;

import dev.awaken.core.api.Destinations;
import dev.awaken.signal.net.SignalPayloads;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * What the Now line, top left, shows (SPEC §27.11; Euan 2026-10-27: "This should only appear when a checklist is active
 * and requires me to go somewhere, or if I choose a destination on the map"): at most one place. The destination you
 * chose wins; otherwise the current task step's place, when it sends you somewhere; otherwise nothing. The rule lives
 * here, in common code, so a GameTest can hold it; the client's {@code NowClient} asks it.
 */
public final class NowCorner {
    private NowCorner() {}

    /** The one place: {@code destination} (chosen on the Atlas) or a task step's; its name as the line says it. */
    public record Place(boolean destination, String name, BlockPos at, Identifier dimension) {}

    public static @Nullable Place of(Destinations.@Nullable Target destination, SignalPayloads.Task task) {
        if (destination != null) return new Place(true, destination.name(), destination.at(), destination.dimension());
        SignalPayloads.TaskRow row = taskPlace(task);
        return row == null ? null : new Place(false, row.place(), row.at(), task.dimension());
    }

    /**
     * The current step (the first not done), when it has a place that sends you somewhere: a step whose mark is only a
     * hint nearby (the ledge to try a move on) has no place name, and stays the card's own.
     */
    public static SignalPayloads.@Nullable TaskRow taskPlace(SignalPayloads.Task task) {
        for (SignalPayloads.TaskRow r : task.rows()) {
            if (r.done()) continue;
            return r.marked() && !r.place().isEmpty() ? r : null;
        }
        return null;
    }
}
