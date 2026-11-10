package dev.awaken.finds.verb;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.function.Predicate;

/**
 * Break (SPEC §58.5): something shut that opens only to the right thing. Strike or use the parts named {@code seal}
 * with what one of the tests passes; after enough of that the seal is gone. With several tests and {@link #allOf}, it
 * wants every one of them inside ten seconds (heat, cold and a blow). Numbers: 0 hits so far, 1 a bit per test passed.
 * The clock is the first of them.
 */
public final class Seal implements Script {
    private final String part;
    private final int hits;
    private final Predicate<ItemStack>[] tests;
    private boolean all;

    @SafeVarargs
    private Seal(String part, int hits, Predicate<ItemStack>... tests) {
        this.part = part;
        this.hits = hits;
        this.tests = tests;
    }

    @SafeVarargs
    public static Seal opens(String part, int hits, Predicate<ItemStack>... to) {
        return new Seal(part, hits, to);
    }

    public Seal allOf() {
        all = true;
        return this;
    }

    private boolean strike(Ctx c, ServerPlayer p, String name, int i, ItemStack held) {
        if (!name.equals(part)) return false;
        int which = -1;
        for (int k = 0; k < tests.length; k++) if (tests[k].test(held)) which = k;
        if (which < 0) {
            c.sound(c.pos(part, i), SoundEvents.STONE_HIT, 0.5F, 0.6F);
            return true;
        }
        c.sound(c.pos(part, i), SoundEvents.STONE_BREAK, 0.9F, 0.7F + 0.1F * c.v(0));
        c.puff(c.pos(part, i), ParticleTypes.CRIT, 8, 0.3, 0.1);
        if (all) {
            if (c.v(1) == 0 || c.now() - c.clock() > 200) {
                c.v(1, 0);
                c.clock(c.now());
            }
            int bits = c.v(1) | (1 << which);
            c.v(1, bits);
            if (Integer.bitCount(bits) < tests.length) return true;
        } else {
            int n = c.v(0) + 1;
            c.v(0, n);
            if (n < hits) return true;
        }
        for (int k = 0; k < c.count(part); k++) {
            c.puff(c.pos(part, k), ParticleTypes.POOF, 6, 0.3, 0.04);
            c.set(part, k, Blocks.AIR.defaultBlockState());
        }
        c.complete(p);
        return true;
    }

    @Override public boolean use(Ctx c, ServerPlayer p, String name, int i, ItemStack held) {
        return strike(c, p, name, i, held);
    }

    @Override public boolean hit(Ctx c, ServerPlayer p, String name, int i, ItemStack held) {
        return strike(c, p, name, i, held);
    }
}
