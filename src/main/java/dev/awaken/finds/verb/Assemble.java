package dev.awaken.finds.verb;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

/**
 * Put it back together (SPEC §58.5): a thing rebuilt a piece at a time. The parts named {@code build} are the places
 * its pieces go, in order (a place may come twice, when a piece grows what's already there), and {@code states} is
 * what each becomes. Using any place that's already built, with a piece in hand, sets the next. The pieces can be
 * lying about the site as loose parts to pick up. Numbers: 0 how many pieces are in.
 */
public final class Assemble implements Script {
    private final String build;
    private final Predicate<ItemStack> piece;
    private final BlockState[] states;
    private String loose = "", base = "";

    private Assemble(String build, Predicate<ItemStack> piece, BlockState[] states) {
        this.build = build;
        this.piece = piece;
        this.states = states;
    }

    public static Assemble of(String build, Predicate<ItemStack> piece, BlockState... states) {
        return new Assemble(build, piece, states);
    }

    /** A part that can be used in place of the build itself, for when the first piece goes where there's only air. */
    public Assemble base(String base) {
        this.base = base;
        return this;
    }

    public Assemble carry(String loose) {
        this.loose = loose;
        return this;
    }

    @Override
    public boolean use(Ctx c, ServerPlayer p, String name, int i, ItemStack held) {
        if (!loose.isEmpty() && name.equals(loose)) return Fit.pickUp(c, p, loose, i);
        if (!(name.equals(build) || (!base.isEmpty() && name.equals(base))) || !piece.test(held)) return false;
        int n = c.v(0);
        if (n >= states.length) return true;
        if (!p.isCreative()) held.shrink(1);
        c.set(build, n, c.turned(states[n]));
        c.v(0, n + 1);
        c.sound(c.pos(build, n), SoundEvents.STONE_PLACE, 0.9F, 0.75F + 0.08F * n);
        c.puff(c.pos(build, n), ParticleTypes.CRIT, 4, 0.25, 0.02);
        if (n + 1 >= states.length) c.complete(p);
        return true;
    }
}
