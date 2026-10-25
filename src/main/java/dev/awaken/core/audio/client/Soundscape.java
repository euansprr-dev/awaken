package dev.awaken.core.audio.client;

import dev.awaken.core.audio.client.world.Conductor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * The world sound Conductor's public API (client; SPEC §12, docs/sound/PLAN.md §6.6). Modules don't play world
 * ambience themselves: they give the Conductor data ({@code assets/<ns>/soundscape/*.json}) and use these hooks.
 */
public final class Soundscape {
    private Soundscape() {}

    /**
     * Quiets birds and insects (every event marked {@code hush}) within {@code radius} of {@code at} for {@code ticks}:
     * {@code depth} is what is left (0 silent, 1 untouched), easing back over the last two seconds. A newer hush from the
     * same owner replaces its older one (a predator that moves just hushes again).
     */
    public static void hush(Object owner, Vec3 at, float radius, float depth, int ticks) {
        Conductor.hush(owner, at, radius, depth, ticks);
    }

    /** Another module's ambient voices, counted in the Conductor's voice budget (16 ambient voices in all). */
    public static void register(Conductor.Source source) {
        Conductor.register(source);
    }

    /**
     * How much of the world's nature ambience to let through right now (1 all, 0 none): lower while something of
     * AWAKEN's takes the stage (a hush, combat or Lord music, a stage cue, a rift or the Well near). The AmbientSounds
     * bridge (compat) scales that mod's volume by it.
     */
    public static float natureLevel() {
        return Conductor.natureLevel();
    }

    /**
     * Lays the soundscape {@code id} ({@code assets/<ns>/soundscape/<path>.json}) over everything at {@code weight} for
     * {@code ticks}; renew it while it should hold (the Veil's choir while you project). At most two at once.
     */
    public static void overlay(Object owner, Identifier id, float weight, int ticks) {
        Conductor.overlay(owner, id, weight, ticks);
    }

    /**
     * Closes the air's filter to {@code cutoffHz} (20000 open) and lets only {@code natureLeft} (0..1) of the nature
     * through for {@code ticks}; renew it while it should hold (the Veil, a dream at low lucidity).
     */
    public static void muffle(Object owner, float cutoffHz, float natureLeft, int ticks) {
        Conductor.muffle(owner, cutoffHz, natureLeft, ticks);
    }

    /** How much of the world bed to give way at a point (0 none, 1 all): the city reports its walls. */
    public static void yieldWeight(Conductor.Yield weight) {
        Conductor.yieldWeight(weight);
    }
}
