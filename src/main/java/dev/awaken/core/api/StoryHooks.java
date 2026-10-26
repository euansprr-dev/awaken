package dev.awaken.core.api;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

/**
 * World-level story state other modules read: after the "Break the Signal" ending gates stop breaking; after "Wake up"
 * blight is gone for good. Installed by the story module. The story is the world's, but a player who joins late meets
 * only what their own story has reached (SPEC "Together"): use the per-player forms where a player is at hand.
 */
public final class StoryHooks {
    private StoryHooks() {}

    public interface State {
        boolean gatesCalm(MinecraftServer server);
        boolean colourRestored(MinecraftServer server);
        int ascension(MinecraftServer server);
        /**
         * Whether a gate rolled for {@code owner} never breaks: the world's gates are calm and the owner has seen the
         * world's ending (a gate with no owner follows the world).
         */
        default boolean gatesCalm(MinecraftServer server, Optional<UUID> owner) { return gatesCalm(server); }
        /** The Ascension tier this player meets: the world's tier, never above the loops the player took part in. */
        default int ascension(ServerPlayer player) { return ascension(player.level().getServer()); }
        /** Whether this player's story has reached an ending (any of them: Break, Wake or Become). */
        default boolean endingSeen(ServerPlayer player) { return gatesCalm(player.level().getServer(), Optional.of(player.getUUID())) || colourRestored(player.level().getServer()); }
        /** Why the Seed (stratum 10) is still sealed for this player, or empty if the story allows it. */
        default Optional<String> seedSealed(ServerPlayer player) { return Optional.empty(); }
        /** Why this player may not walk into this scripted gate (the ritual's hall is sealed, or not theirs), or empty. */
        default Optional<String> refusesScriptedGate(ServerPlayer player, String gateId) { return Optional.empty(); }
    }

    private static State state = new State() {
        @Override public boolean gatesCalm(MinecraftServer server) { return false; }
        @Override public boolean colourRestored(MinecraftServer server) { return false; }
        @Override public int ascension(MinecraftServer server) { return 0; }
    };

    public static void install(State s) { state = s; }
    /** The installed state (tests swap it in and out). */
    public static State installed() { return state; }
    public static boolean gatesCalm(MinecraftServer server) { return state.gatesCalm(server); }
    /** Whether gates rolled for this owner are calm (never break). */
    public static boolean gatesCalm(MinecraftServer server, Optional<UUID> owner) { return state.gatesCalm(server, owner); }
    public static boolean colourRestored(MinecraftServer server) { return state.colourRestored(server); }
    public static int ascension(MinecraftServer server) { return state.ascension(server); }
    /** min(world tier, the player's own tier), 0..10. */
    public static int ascensionOf(ServerPlayer player) { return Math.clamp(state.ascension(player), 0, 10); }
    public static Optional<String> seedSealed(ServerPlayer p) { return state.seedSealed(p); }
    public static boolean endingSeen(ServerPlayer p) { return state.endingSeen(p); }
    public static Optional<String> refusesScriptedGate(ServerPlayer p, String gateId) { return state.refusesScriptedGate(p, gateId); }
}
