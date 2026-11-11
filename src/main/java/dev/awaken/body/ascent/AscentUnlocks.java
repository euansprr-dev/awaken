package dev.awaken.body.ascent;

import dev.awaken.Awaken;
import dev.awaken.core.api.Unlocks;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The ladder's Unlock titles (SPEC §71.2; {@link Unlocks}): each new way to move, by its plain name, with how to use it
 * in the player's own keys. Registered on both sides. The leap is three of them (and a fourth once the aimed dive
 * exists): in the Leaper's Gauntlet each is taken at its own stand (§71.3).
 */
public final class AscentUnlocks {
    private AscentUnlocks() {}

    public static final Identifier ROPE = Awaken.id("unlock/rope"), DOUBLE_JUMP = Awaken.id("unlock/double_jump"),
            AIR_DASH = Awaken.id("unlock/air_dash"), SLAM = Awaken.id("unlock/slam"), DIVE = Awaken.id("unlock/dive"),
            WINGS = Awaken.id("unlock/wings"), FLIGHT = Awaken.id("unlock/flight"), FORM = Awaken.id("unlock/sovereign_form");

    static void register() {
        Unlocks.register(Unlocks.Unlock.of(ROPE, "Rope", "{hold}{key.awaken.rope} to swing. Let go to fly."));
        Unlocks.register(Unlocks.Unlock.of(DOUBLE_JUMP, "Double Jump", "Press {key.jump} again in the air"));
        Unlocks.register(Unlocks.Unlock.of(AIR_DASH, "Air Dash", "Press {key.sprint} in the air"));
        Unlocks.register(Unlocks.Unlock.of(SLAM, "Slam", "Press {key.sneak} in the air, from high up"));
        Unlocks.register(Unlocks.Unlock.of(DIVE, "Dive", "Press {key.attack} in the air to dive where you look"));
        Unlocks.register(Unlocks.Unlock.of(WINGS, "Wings", "In a fall, press {key.jump} twice"));
        Unlocks.register(Unlocks.Unlock.of(FLIGHT, "Flight", "On open wings, press {key.jump} again"));
        Unlocks.register(Unlocks.Unlock.of(FORM, "The Sovereign's Form", "Press {key.awaken.ascend} when the gold line is full"));
    }

    /** The title of a rung handed down by a later one (a core's leap is its first move, the double jump). */
    static @Nullable Identifier of(WayUp.Rung r) {
        return switch (r) {
            case ROPE -> ROPE;
            case LEAP -> DOUBLE_JUMP;
            case WINGS -> WINGS;
            case SKY -> FLIGHT;
            case FORM -> FORM;
        };
    }

    /** The title of one of the leap's moves. */
    public static Identifier of(WayUp.Leap l) {
        return switch (l) {
            case DOUBLE -> DOUBLE_JUMP;
            case DASH -> AIR_DASH;
            case SLAM -> SLAM;
            case DIVE -> DIVE;
        };
    }
}
