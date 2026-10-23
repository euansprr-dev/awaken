package dev.awaken.core.api;

import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.function.Function;

/** Things the overworld layer (world module) tells others: what the town has heard about a landmark. */
public final class WorldHooks {
    private WorldHooks() {}

    /** A rumour Mira can pass on: a stable id (told once), a short title and her line. */
    public record Rumour(String id, String title, String body) {}

    private static Function<ServerPlayer, Optional<Rumour>> rumours = p -> Optional.empty();

    /** Installed by the world module (landmarks). */
    public static void installRumours(Function<ServerPlayer, Optional<Rumour>> source) {
        rumours = source;
    }

    /** The nearest landmark this player has a reason to visit and hasn't been told about; marks it told. */
    public static Optional<Rumour> landmarkRumour(ServerPlayer player) {
        return rumours.apply(player);
    }
}
