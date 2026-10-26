package dev.awaken.hearth.player;

/**
 * The local player's own home as their client last saw it (a copy of the synced {@link HearthView}'s home, kept fresh by
 * the hearth client once a second). Plain data, safe to read from common code: {@code HearthHooks.greenhouse} answers a
 * client's herb glint from it so the glint and the server's harvest agree. Empty on a dedicated server.
 */
public final class ClientHome {
    private ClientHome() {}

    private static volatile HearthView.Home home = HearthView.Home.NONE;

    public static HearthView.Home home() {
        return home;
    }

    public static void set(HearthView.Home h) {
        home = h == null ? HearthView.Home.NONE : h;
    }
}
