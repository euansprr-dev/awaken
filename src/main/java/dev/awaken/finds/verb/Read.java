package dev.awaken.finds.verb;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * Read (SPEC §58.5): every part named {@code stone} has to be looked at closely (used). What you learn is what the
 * Journal keeps for the kind. Numbers: 0 a bit per stone read.
 */
public final class Read implements Script {
    private final String part;
    private boolean ordered;

    private Read(String part) {
        this.part = part;
    }

    public static Read all(String part) {
        return new Read(part);
    }

    /** They have to be read first to last. */
    public Read inOrder() {
        ordered = true;
        return this;
    }

    @Override
    public boolean use(Ctx c, ServerPlayer p, String name, int i, ItemStack held) {
        if (!name.equals(part)) return false;
        if (ordered && Integer.bitCount(c.v(0)) != i) return (c.v(0) & (1 << i)) != 0;
        int bits = c.v(0) | (1 << i);
        c.v(0, bits);
        c.sound(c.pos(part, i), SoundEvents.BOOK_PAGE_TURN, 0.8F, 0.9F);
        c.puff(c.pos(part, i), ParticleTypes.ENCHANT, 8, 0.3, 0.2);
        if (Integer.bitCount(bits) >= c.count(part)) c.complete(p);
        return true;
    }
}
