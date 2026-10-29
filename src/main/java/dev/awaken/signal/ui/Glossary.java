package dev.awaken.signal.ui;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Plain-word explanations of AWAKEN's own words, shown on hover in the Status screen. A word matches its term plus a
 * simple ending ("Echoes", "Fractured", "play-days", "Extraction").
 */
public final class Glossary {
    private Glossary() {}

    private static final Map<String, String> TERMS = new LinkedHashMap<>();

    static {
        add("Satchel", "What you carry inside a gate: the loot you pick up and the floor XP you've earned. It's only yours once you leave through a Rest Shrine (Extract) or beat the Lord. Die and you lose it.");
        add("Extract", "Leave a gate at a Rest Shrine with everything in your Satchel. The floors you cleared stay cleared.");
        add("Seal", "The door to the next floor of a gate. Break it to go deeper: more loot and XP, stronger monsters.");
        add("play-day", "An in-game day that passed while you were playing. Timers in AWAKEN count these, never real-world time.");
        add("Veil", "The spirit side of the world. Sneak and hold G to leave your body: secrets and ore glow, spirits talk, and you can scout a gate from inside.");
        add("Fracture", "What a death leaves behind: for a few minutes you're about 15% weaker and a little slower. Resting at your Hearth or meditating heals it faster.");
        add("Reassessment", "The trial that raises your rank. When your level is high enough, take it at the Spire in your town or at a Reassessment Monolith.");
        add("Flow", "A power that slows the world around you while you keep your speed. AGI makes it last longer.");
        add("Kinetic", "Raw physical force: punches, slams, shockwaves. Kinetic powers grow with STR.");
        add("Truth", "A statement about who you're becoming. Declare up to three; when you've lived one it becomes TRUE for good and gives its reward.");
        add("Directive", "A short, optional task the Signal suggests, with a reward. Accept it or let it fade; it never punishes you.");
        add("Latent", "A hidden ability asleep in you. It wakes by itself when you do something unusual enough. Nobody tells you how.");
    }

    private static void add(String term, String text) {
        TERMS.put(term.toLowerCase(Locale.ROOT), text);
    }

    private static final String[] ENDINGS = {"", "s", "es", "d", "ed", "ion", "ing"};

    /** The explanation for a word as written in text, if it is a glossary term. */
    public static Optional<String> lookup(String word) {
        if (word.length() < 4) return Optional.empty();
        String w = word.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> e : TERMS.entrySet()) {
            String t = e.getKey();
            if (!w.startsWith(t)) continue;
            String rest = w.substring(t.length());
            for (String end : ENDINGS) if (rest.equals(end)) return Optional.of(e.getValue());
        }
        return Optional.empty();
    }

    public static Map<String, String> terms() {
        return TERMS;
    }

    /** A word character for matching: letters and the hyphen in "play-day". */
    public static boolean wordChar(char c) {
        return Character.isLetter(c) || c == '-';
    }
}
