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
    }

    private static final Int2ObjectOpenHashMap<Glow> BY_ENTITY = new Int2ObjectOpenHashMap<>();
    /** How long a release's flare lasts. */
    private static final long FLARE_MS = 450;

    static void on(ArmourPayloads.Charge c) {
        if (c.charge() <= 0 && c.runes() <= 0 && c.flash() <= 0) {
            Glow g = BY_ENTITY.get(c.entity());
            if (g != null && Util.getMillis() - g.flashAt > FLARE_MS) BY_ENTITY.remove(c.entity());
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

    /** The shader's flash, 0-255: a release's flare fading out. */
    static int flash(@Nullable Glow g) {
        if (g == null || g.flash <= 0) return 0;
        long age = Util.getMillis() - g.flashAt;
        if (age >= FLARE_MS) return 0;
        float k = 1F - age / (float) FLARE_MS;
        return (int) (g.flash * k * k);
    }
}
