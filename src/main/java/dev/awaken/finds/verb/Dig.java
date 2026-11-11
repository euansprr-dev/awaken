package dev.awaken.finds.verb;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * Dig (SPEC §58.5): the parts named {@code spot} may be dug out; when the last one goes, what's under it is yours.
 * Parts named {@code decoy} may be dug too and hold nothing but a puff of dust.
 */
public final class Dig implements Script {
    private final String spot, decoy;

    private Dig(String spot, String decoy) {
        this.spot = spot;
        this.decoy = decoy;
    }

    public static Dig at(String spot) {
        return new Dig(spot, "decoy");
    }

    @Override
    public boolean mayBreak(Ctx c, ServerPlayer p, String part, int i) {
        return part.equals(spot) || part.equals(decoy);
    }

    @Override
    public void broke(Ctx c, ServerPlayer p, String part, int i) {
        if (part.equals(decoy)) {
            c.puff(c.pos(decoy, i), ParticleTypes.POOF, 8, 0.25, 0.03);
            c.sound(c.pos(decoy, i), SoundEvents.ROOTED_DIRT_BREAK, 0.7F, 0.7F);
            return;
        }
        if (!part.equals(spot)) return;
        c.heart.touch();
        for (int k = 0; k < c.count(spot); k++) if (!c.at(spot, k).isAir()) return;
        c.complete(p);
    }
}
