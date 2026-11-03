package dev.awaken.core.api;

import net.minecraft.server.level.ServerPlayer;

/** Lucidium access for other modules (the Above's Dream Recorder, the Veil's Stillpoint door). */
public final class DreamHooks {
    private DreamHooks() {}

    public interface Access {
        boolean isDreaming(ServerPlayer player);
        /** Re-enter a recorded dream (0 = the one finished last). Returns false if none. */
        boolean replay(ServerPlayer player, int index);
        /** Number of dreams this player can replay. */
        int recordedCount(ServerPlayer player);
        /** One line per dream this player can replay, by replay index ("The wall: best 0:42, gold"). */
        default java.util.List<String> recorded(ServerPlayer player) { return java.util.List.of(); }
    }

    private static Access access = new Access() {
        @Override public boolean isDreaming(ServerPlayer player) { return false; }
        @Override public boolean replay(ServerPlayer player, int index) { return false; }
        @Override public int recordedCount(ServerPlayer player) { return 0; }
    };

    public static void install(Access a) { access = a; }
    public static boolean isDreaming(ServerPlayer p) { return access.isDreaming(p); }
    public static boolean replay(ServerPlayer p, int index) { return access.replay(p, index); }
    public static int recordedCount(ServerPlayer p) { return access.recordedCount(p); }
    public static java.util.List<String> recorded(ServerPlayer p) { return access.recorded(p); }
}
