package dev.awaken.armour.client;

import dev.awaken.armour.net.ArmourPayloads;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * The set abilities on the armour itself (SPEC §44.5, "The look"): as a charge fills the set's glowing parts brighten and
 * its rim takes the accent; full, they breathe; an ability's release flares the whole set for a moment; a Channeler's runes
 * circle its wearer. Everyone near sees the same. Per entity, from the server's {@link ArmourPayloads.Charge}.
 */
final class ArmourGlow {
    private ArmourGlow() {}

    static final class Glow {
        int charge, runes, flash;
        long flashAt;
        /** When each rune joined the circle, so a new one grows in rather than popping. */
        final long[] runeAt = new long[4];
        /** When the set became whole on them (0: not lately): it wakes feet to crown (SPEC §44.11). */
        long wakeAt;
    }

    /** The wake: each piece lights this long after the one below it, rises this fast and fades this slowly. */
    private static final long WAKE_STEP_MS = 130, WAKE_RISE_MS = 90, WAKE_FADE_MS = 620;
    private static final long WAKE_MS = 3 * WAKE_STEP_MS + WAKE_RISE_MS + WAKE_FADE_MS;

    /** A set became whole on someone in view: their armour wakes. */
    static void whole(ArmourPayloads.Whole w) {
        BY_ENTITY.computeIfAbsent(w.entity(), k -> new Glow()).wakeAt = Util.getMillis();
        ArmourSoundsClient.whole(w.entity(), w.set());
    }

    private static final Int2ObjectOpenHashMap<Glow> BY_ENTITY = new Int2ObjectOpenHashMap<>();
    /** How long a release's flare lasts. */
    private static final long FLARE_MS = 450;

    static void on(ArmourPayloads.Charge c) {
        if (c.charge() <= 0 && c.runes() <= 0 && c.flash() <= 0) {
            Glow g = BY_ENTITY.get(c.entity());
            long now = Util.getMillis();
            if (g != null && now - g.flashAt > FLARE_MS && now - g.wakeAt > WAKE_MS) BY_ENTITY.remove(c.entity());
            else if (g != null) g.charge = g.runes = 0;
            return;
        }
        Glow g = BY_ENTITY.computeIfAbsent(c.entity(), k -> new Glow());
        long now = Util.getMillis();
        for (int i = g.runes; i < Math.min(c.runes(), g.runeAt.length); i++) g.runeAt[i] = now;
        g.charge = c.charge();
        g.runes = c.runes();
        if (c.flash() > 0) {
            g.flash = c.flash();
            g.flashAt = now;
        }
    }

    static @Nullable Glow of(int entity) {
        return BY_ENTITY.get(entity);
    }

    static void clear() {
        BY_ENTITY.clear();
    }

    /** The shader's energy, 0-255: up to two thirds as the charge fills, then breathing near full once it is. */
    static int energy(@Nullable Glow g) {
        if (g == null || g.charge <= 0) return 0;
        if (g.charge < 100) return g.charge * 170 / 100;
        float t = (Util.getMillis() % 1400L) / 1400F;
        return (int) (205 + 50 * Mth.sin(t * Mth.TWO_PI));
    }

    /**
     * A piece's flash, 0-255: a release's flare, or the wake rising through it (the boots first, then the greaves, the
     * cuirass, the helm: each a quick rise and a slow fade, so the light climbs the body).
     */
    static int flash(@Nullable Glow g, net.minecraft.world.item.equipment.ArmorType piece) {
        int flare = flash(g);
        if (g == null || g.wakeAt == 0) return flare;
        int up = switch (piece) {
            case BOOTS -> 0;
            case LEGGINGS -> 1;
            case CHESTPLATE -> 2;
            default -> 3;
        };
        long t = Util.getMillis() - g.wakeAt - up * WAKE_STEP_MS;
        if (t < 0 || t > WAKE_RISE_MS + WAKE_FADE_MS) return flare;
        float k = t < WAKE_RISE_MS ? t / (float) WAKE_RISE_MS : 1F - (t - WAKE_RISE_MS) / (float) WAKE_FADE_MS;
        return Math.max(flare, (int) (255 * k * k));
    }

    /** How long a living part answers a release (SPEC §44.13): longer than the flare, so a wingbeat is seen. */
    private static final long KICK_MS = 700;

    /**
     * The kick a release gives the set's living parts, 0..1: at once to full, then easing out (a wing thrown open
     * folds slowly; a halo struck rings down).
     */
    static float kick(@Nullable Glow g) {
        if (g == null || g.flash <= 0) return 0F;
        long age = Util.getMillis() - g.flashAt;
        if (age >= KICK_MS || age < 0) return 0F;
        float k = 1F - age / (float) KICK_MS;
        return k * k * (3F - 2F * k);
    }

    /** The shader's flash, 0-255: a release's flare fading out. */
    static int flash(@Nullable Glow g) {
        if (g == null || g.flash <= 0) return 0;
        long age = Util.getMillis() - g.flashAt;
        if (age >= FLARE_MS) return 0;
        float k = 1F - age / (float) FLARE_MS;
        return (int) (g.flash * k * k);
    }
}
