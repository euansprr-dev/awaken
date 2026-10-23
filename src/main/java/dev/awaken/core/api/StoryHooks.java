package dev.awaken.core.api;

import net.minecraft.server.MinecraftServer;

/**
 * World-level story state other modules read: after the "Break the Signal" ending gates stop breaking; after "Wake up"
 * blight is gone for good. Installed by the story module.
 */
public final class StoryHooks {
    private StoryHooks() {}

    public interface State {
        boolean gatesCalm(MinecraftServer server);
        boolean colourRestored(MinecraftServer server);
        int ascension(MinecraftServer server);
    }

    private static State state = new State() {
        @Override public boolean gatesCalm(MinecraftServer server) { return false; }
        @Override public boolean colourRestored(MinecraftServer server) { return false; }
        @Override public int ascension(MinecraftServer server) { return 0; }
    };

    public static void install(State s) { state = s; }
    public static boolean gatesCalm(MinecraftServer server) { return state.gatesCalm(server); }
    public static boolean colourRestored(MinecraftServer server) { return state.colourRestored(server); }
    public static int ascension(MinecraftServer server) { return state.ascension(server); }
}
