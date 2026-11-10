package dev.awaken.finds.verb;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.IntFunction;
import java.util.function.Predicate;

/**
 * Put the right thing in the right place (SPEC §58.5): the one script under carry, stack, mend and offer. A socket
 * takes what its test accepts and becomes what its maker says. With loose parts, the things to carry are lying about
 * the site: using one picks it up as its own item, and it goes down again only on a socket. In order: sockets fill
 * first to last. Numbers: 0 a bit per socket filled.
 */
public final class Sockets implements Script {
    private final String socket;
    private String loose = "";
    private Predicate<ItemStack> accepts = s -> false;
    private IntFunction<BlockState> filled = i -> Blocks.AIR.defaultBlockState();
    private boolean ordered, keep, onTop;

    private Sockets(String socket) {
        this.socket = socket;
    }

    /** Every part named {@code socket} wants one of what {@code accepts} passes; it then shows as {@code filled}. */
    public static Sockets need(String socket, Predicate<ItemStack> accepts, IntFunction<BlockState> filled) {
        Sockets s = new Sockets(socket);
        s.accepts = accepts;
        s.filled = filled;
        return s;
    }

    /** The parts named {@code loose} can be picked up (they give their block's item). */
    public Sockets carry(String loose) {
        this.loose = loose;
        return this;
    }

    /** What's fitted goes on top of the socket, not in its place (a blade laid on an anvil). */
    public Sockets onTop() {
        onTop = true;
        return this;
    }

    public Sockets inOrder() {
        ordered = true;
        return this;
    }

    /** What's offered isn't used up (a tool you fit and keep). */
    public Sockets keep() {
        keep = true;
        return this;
    }

    @Override
    public boolean use(Ctx c, ServerPlayer p, String name, int i, ItemStack held) {
        if (!loose.isEmpty() && name.equals(loose)) return Fit.pickUp(c, p, loose, i);
        if (!name.equals(socket)) return false;
        int bits = c.v(0);
        if ((bits & (1 << i)) != 0) return true;
        if (!accepts.test(held)) return false;
        if (ordered && Integer.bitCount(bits) != i) {
            c.sound(c.pos(socket, i), SoundEvents.STONE_HIT, 0.6F, 0.7F);
            c.puff(c.pos(socket, i), ParticleTypes.SMOKE, 3, 0.15, 0.01);
            return true;
        }
        if (!keep && !p.isCreative()) held.shrink(1);
        if (onTop) c.set(c.pos(socket, i).above(), c.turned(filled.apply(i)));
        else c.set(socket, i, c.turned(filled.apply(i)));
        bits |= 1 << i;
        c.v(0, bits);
        c.sound(c.pos(socket, i), SoundEvents.STONE_PLACE, 0.8F, 0.85F + 0.07F * Integer.bitCount(bits));
        c.puff(c.pos(socket, i), ParticleTypes.CRIT, 4, 0.2, 0.02);
        if (Integer.bitCount(bits) == c.count(socket)) c.complete(p);
        return true;
    }
}
