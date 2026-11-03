package dev.awaken.arms;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Development only: the server's half of an arms film (the client's {@code ArmsFilm}, autopilot {@code arms film}). In
 * single player the server runs in the client's JVM, so what its clock did (a press heard, a strike begun, a blow
 * swept) is stamped on the same nanos as the client's frames, and the film lines them up. Nothing is kept while off.
 */
public final class FilmLog {
    private FilmLog() {}

    /** Set by the client while it films. */
    public static volatile boolean on;
    /** "nanos\twhat" rows, drained by the client's film. */
    public static final ConcurrentLinkedQueue<String> SERVER = new ConcurrentLinkedQueue<>();

    public static void server(String what) {
        if (on) SERVER.add(System.nanoTime() + "\t" + what);
    }
}
