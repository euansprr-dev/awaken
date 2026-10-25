package dev.awaken.compat.client;

import dev.awaken.core.audio.client.Soundscape;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import team.creative.ambientsounds.AmbientSounds;

/**
 * AmbientSounds (the sound profile's nature ambience; Euan, 2026-10-25) steps back while AWAKEN takes the stage: its
 * master volume is the player's own setting × {@link Soundscape#natureLevel()} (a gate's hush, combat or Lord music, a
 * stage cue, a rift or the Well near). AmbientSounds reads {@code CONFIG.volume} on every sound update and eases toward
 * it over a few seconds, so the change is a fade, never a cut. The player's value is remembered: a change made in its
 * own menu becomes the new base, and the base is put back on shutdown so a saved config never keeps a lowered value.
 * Loaded only when AmbientSounds is present ({@link CompatClient}).
 */
final class AmbientSoundsBridge {
    private AmbientSoundsBridge() {}

    private static double base = -1, written = -1;

    static void init() {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> tick());
        NeoForge.EVENT_BUS.addListener(GameShuttingDownEvent.class, e -> restore());
    }

    private static void tick() {
        double now = AmbientSounds.CONFIG.volume;
        if (base < 0 || Math.abs(now - written) > 1e-6) base = now; // first look, or the player changed it
        double want = base * Soundscape.natureLevel();
        if (Math.abs(want - now) > 1e-4) AmbientSounds.CONFIG.volume = want;
        written = AmbientSounds.CONFIG.volume;
    }

    private static void restore() {
        if (base >= 0) AmbientSounds.CONFIG.volume = base;
    }
}
