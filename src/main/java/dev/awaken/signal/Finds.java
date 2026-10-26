package dev.awaken.signal;

import dev.awaken.core.api.CityHooks;
import dev.awaken.core.api.Notices;
import dev.awaken.progression.Progression;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * How a find is told (SPEC §26.3), for every module that discovers things (creatures, biomes, happenings, lairs,
 * landmarks, anomalies). The caller records the find itself ({@code Progression.discover}) and only calls this when
 * it was new. Then:
 * <ul>
 *   <li>the player's first find ever, made on purpose, is a moment that teaches the system: what a discovery is,
 *       where it's kept and why it matters;</li>
 *   <li>a big one (a named beast, a wonder, the first rift) is a moment of its own;</li>
 *   <li>the first find of each kind is a paced toast that says where these go;</li>
 *   <li>every later one is ambient: filed in the Journal with a soft cue, no toast (at the default density).</li>
 * </ul>
 */
public final class Finds {
    private Finds() {}

    /** Set once the player has had the lesson. */
    public static final String TAUGHT = "awaken:finds_taught";
    static final String PER_KIND = "awaken:finds/";

    /** The lesson of the first find (the moment's lines under the name). */
    static final String LESSON = "A discovery. You studied something new: it pays a little XP, and what you learned is kept in "
            + "your Journal and Codex (K). Look closely at what you meet.";

    /**
     * One find. {@code kind}: "creature", "biome", "happening", "lair", "landmark", "anomaly" (also the merge topic).
     * {@code title}: the toast's line ("New creature · Mossback"); {@code name}: the moment's big word ("Mossback");
     * {@code hint}: one line on what it is or what to do. {@code intent}: found by looking at it, touching it or going
     * to it (only such a find can carry the lesson). {@code big}: a moment whatever came before; {@code label} is its
     * small heading then ("Named beast", "Wonder").
     */
    public record Find(String kind, String title, String name, String hint, int colour, boolean intent, boolean big, String label) {
        public static Find of(String kind, String title, String name, String hint, int colour, boolean intent) {
            return new Find(kind, title, name, hint, colour, intent, false, "");
        }

        public Find big(String label) {
            return new Find(kind, title, name, hint, colour, intent, true, label);
        }
    }

    /**
     * Whether finds count where the player stands (SPEC §26.3, Euan 2026-10-26): never inside Carillon's walls. The
     * city is where you start, not something you discover; nothing there is a biome, a creature, a landmark or a
     * happening found. Sources check this before recording anything, so the first find outside is still a first.
     */
    public static boolean counts(ServerPlayer p) {
        return !CityHooks.inCity(p.level(), p.blockPosition());
    }

    /** What happened, for tests and the log. */
    public enum Told { LESSON, MOMENT, FIRST_OF_KIND, FILED }

    public static Told tell(ServerPlayer p, Find f) {
        boolean taught = Progression.profile(p).counter(TAUGHT) > 0;
        boolean firstOfKind = Progression.profile(p).counter(PER_KIND + f.kind) == 0;
        Progression.count(p, PER_KIND + f.kind);
        if (!taught && f.intent) {
            Progression.setCounter(p, TAUGHT, 1);
            moment(p, f, "Discovery", f.hint.isEmpty() ? LESSON : f.hint + ". " + LESSON);
            return Told.LESSON;
        }
        if (f.big) {
            moment(p, f, f.label.isEmpty() ? "Discovery" : f.label, f.hint);
            return Told.MOMENT;
        }
        if (firstOfKind) {
            String line = (f.hint.isEmpty() ? "" : f.hint + " · ") + "kept in your Journal (K)";
            Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(f.title).withColor(f.colour))
                    .body(Component.literal(line)).presentation(SignalWindow.Presentation.TOAST)
                    .tier(Notices.Tier.IMPORTANT).topic(f.kind).ttl(20 * 6).build());
            return Told.FIRST_OF_KIND;
        }
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(f.title).withColor(f.colour))
                .body(Component.literal(f.hint)).presentation(SignalWindow.Presentation.TOAST)
                .tier(Notices.Tier.AMBIENT).topic(f.kind).ttl(20 * 6).build());
        return Told.FILED;
    }

    private static void moment(ServerPlayer p, Find f, String label, String body) {
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(f.name).withColor(f.colour))
                .body(Component.literal(body)).footer(Component.literal(label))
                .presentation(SignalWindow.Presentation.TOAST).tier(Notices.Tier.IMPORTANT).moment().topic(f.kind).ttl(20 * 8).build());
    }
}
