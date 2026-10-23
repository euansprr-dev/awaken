package dev.awaken.gates.content;

import com.mojang.serialization.Codec;
import dev.awaken.core.Forces;

/**
 * The 14 gate modifiers with their budget cost and force tag (plan generator MODS). Behaviour lives in
 * {@code run.Modifiers}; generation-time effects (Lightless) in the painter.
 */
public enum Modifier {
    LIGHTLESS("Lightless", 1, null, "No light but what you bring"),
    LOW_GRAVITY("Low Gravity", 1, null, "Every step is a leap"),
    VOLATILE("Volatile", 1, Forces.Tag.VOLATILE, "The dead burst"),
    SWARM("Swarm", 1, null, "More of them, weaker each"),
    ELITE_PACK("Elite Pack", 2, null, "Elites travel together"),
    SILENCE("Silence", 1, null, "Noise draws the Listeners"),
    FLOODING("Flooding", 1, Forces.Tag.WATER, "The water rises while you linger"),
    HAUNTED("Haunted", 1, Forces.Tag.HAUNTED, "Veil things walk here"),
    MIRROR("Mirror", 2, Forces.Tag.MIRROR, "Your shadow hunts you"),
    COLLAPSE_TIMER("Collapse Timer", 2, null, "Each floor caves in on a timer"),
    NO_REGEN("No Regen", 2, null, "Your body will not mend here"),
    GLASS("Glass", 2, null, "Everyone deals double"),
    FORTUNE("Fortune", 1, null, "Double loot, more danger"),
    ECHOING("Echoing", 1, Forces.Tag.ECHO, "The dead get up once");

    public static final Codec<Modifier> CODEC = Codec.STRING.xmap(Modifier::valueOf, Modifier::name);

    public final String displayName;
    public final int cost;
    public final Forces.Tag tag;
    public final String hint;

    Modifier(String displayName, int cost, Forces.Tag tag, String hint) {
        this.displayName = displayName;
        this.cost = cost;
        this.tag = tag;
        this.hint = hint;
    }

    /** The force name used by the plan's combination table ("Gravity", "Volatile", ...). */
    public String forceName() {
        return switch (this) {
            case LOW_GRAVITY -> "Gravity";
            case VOLATILE -> "Volatile";
            case FLOODING -> "Water";
            case HAUNTED -> "Spirit";
            case MIRROR -> "Mirror";
            default -> "";
        };
    }

    public static Modifier parse(String s) {
        for (Modifier m : values()) if (m.name().equalsIgnoreCase(s) || m.displayName.equalsIgnoreCase(s)) return m;
        return null;
    }
}
