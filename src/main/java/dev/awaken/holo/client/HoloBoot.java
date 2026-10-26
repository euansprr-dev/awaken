package dev.awaken.holo.client;

import dev.awaken.core.api.HoloBand;
import dev.awaken.holo.HoloSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * The band boots on your wrist (SPEC §16.4, "The Registry ceremony"): the moment the Holo band goes on, once no
 * screen is open, the wrist comes up into view with the band dark on it; it clicks home, a line of light runs round it,
 * its marks light one by one from the middle out, the System's diamond ignites with a ring of light spreading off the
 * wrist, it breathes for a moment and the arm goes down. The first boot of a session plays in full (about four
 * seconds: the Registry), later ones (putting it back on) in under two. Taking it off plays a short power-down.
 * Drawn by {@link Wrist} while no panel holds the wrist; tells {@link HoloBand#fireClient} when it is done.
 */
public final class HoloBoot {
    private HoloBoot() {}

    // ticks of each phase: raise, seated (dark), the light runs round, the marks light, ignition, breathe, lower
    private static final int[] FULL = {10, 8, 20, 18, 0, 22, 12}, SHORT = {6, 3, 8, 6, 0, 6, 8};

    private static int[] phases = FULL;
    /** Client ticks since the boot began (-1: none playing); a boot waiting for the screen to close. */
    private static int age = -1;
    private static boolean pending, playedOnce, wasWorn, known;
    private static int knownEntity = -1;

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) {
            reset();
            return;
        }
        boolean worn = HoloBand.worn(p);
        // a new player entity (login, respawn, dimension change) learns where the wrist stands without a boot (the
        // wrist's sync lands a few ticks after the entity)
        if (!known || knownEntity != p.getId() || p.tickCount < 20) {
            known = true;
            knownEntity = p.getId();
            wasWorn = worn;
            return;
        }
        if (worn != wasWorn) {
            wasWorn = worn;
            if (worn) {
                pending = true;
                phases = playedOnce ? SHORT : FULL;
            } else {
                pending = false;
                age = -1;
                play(HoloSounds.CLOSE.get(), 0.75f, 0.6f);
                play(SoundEvents.BEACON_DEACTIVATE, 1.6f, 0.25f);
                HoloBand.fireClient(false);
            }
        }
        if (pending && mc.gui.screen() == null) {
            pending = false;
            age = 0;
            playedOnce = true;
        }
        if (age < 0) return;
        cue(age);
        if (age == lowerAt()) Wrist.handsBack(); // vanilla's re-equip raises the hands as the forearm goes down
        age++;
        if (age >= total()) {
            age = -1;
            HoloBand.fireClient(true);
        }
    }

    /** The boot's sounds, on its beats. */
    private static void cue(int t) {
        int seat = phases[0], run = seat + phases[1], lights = run + phases[2], ignite = lights + phases[3];
        if (t == seat) {
            play(SoundEvents.ARMOR_EQUIP_CHAIN.value(), 1.5f, 0.55f);
            play(HoloSounds.HOVER.get(), 0.7f, 0.5f);
        }
        if (t == run) play(HoloSounds.CARD.get(), 0.9f, 0.7f);
        for (int j = 0; j < 3; j++) if (t == lights + j * Math.max(1, phases[3] / 3)) play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f + j * 0.18f, 0.35f);
        if (t == ignite) {
            play(HoloSounds.OPEN.get(), 1.0f, 0.9f);
            play(SoundEvents.BEACON_POWER_SELECT, 1.8f, 0.35f);
            play(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 0.5f);
        }
    }

    private static void play(SoundEvent e, float pitch, float vol) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(e, pitch, vol));
    }

    private static int total() {
        int n = 0;
        for (int k : phases) n += k;
        return n;
    }

    static void reset() {
        age = -1;
        pending = false;
        known = false;
        knownEntity = -1;
    }

    /** Playing now (the wrist is ours to draw). */
    static boolean active() {
        return age >= 0;
    }

    private static float t(float pt) {
        return age + pt;
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    private static int lowerAt() {
        return total() - phases[6];
    }

    /** The raise, 0 down .. 1 up. */
    static float arm(float pt) {
        float t = t(pt);
        if (t < phases[0]) return HoloTimelineEase.out(t / phases[0]);
        if (t < lowerAt()) return 1f;
        return 1f - HoloTimelineEase.inOut(clamp01((t - lowerAt()) / phases[6]));
    }

    /** Whether the arm is going down (vanilla's hands come back from the next tick). */
    static boolean lowering() {
        return age >= lowerAt();
    }

    static boolean handsBack() {
        return age > lowerAt();
    }

    /** The light running round the band, 0..1 (four faces in turn). */
    static float sweep(float pt) {
        int from = phases[0] + phases[1];
        return clamp01((t(pt) - from) / phases[2]);
    }

    /** The marks lighting from the middle out, 0..1; the diamond at 1. */
    static float lit(float pt) {
        int from = phases[0] + phases[1] + phases[2];
        return clamp01((t(pt) - from) / Math.max(1, phases[3]));
    }

    /** How bright the band is (0 dark .. 1): rising with the sweep, full once ignited. */
    static float glow(float pt) {
        return 0.25f + 0.45f * sweep(pt) + 0.3f * lit(pt);
    }

    /** The ignition's flare, 1 at the moment and fading over the breath. */
    static float flare(float pt) {
        int at = phases[0] + phases[1] + phases[2] + phases[3];
        float t = t(pt) - at;
        if (t < 0) return 0;
        return (float) Math.exp(-t / Math.max(2f, phases[5] * 0.35f));
    }

    /** The ring spreading off the wrist at ignition: 0 at the moment .. 1 gone. */
    static float ring(float pt) {
        int at = phases[0] + phases[1] + phases[2] + phases[3];
        return clamp01((t(pt) - at) / Math.max(4f, phases[5] * 0.7f));
    }

    /** Easing with the K menu's curves, kept here so the boot needs no timeline. */
    private static final class HoloTimelineEase {
        static float out(float v) {
            return dev.awaken.holo.ui.HoloTimeline.easeOut(clamp01(v));
        }

        static float inOut(float v) {
            return dev.awaken.holo.ui.HoloTimeline.easeInOut(clamp01(v));
        }
    }
}
