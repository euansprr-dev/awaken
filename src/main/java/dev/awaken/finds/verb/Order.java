package dev.awaken.finds.verb;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.function.BiConsumer;

/**
 * One after another (SPEC §62.4): the parts of a name are worked in their own order, first to last (sluices from the top
 * down). Each, in its turn, does what the kind says; one worked out of turn gives a dull knock and nothing else.
 * {@link #times}: one part worked that many times instead (a capstan, a bar pushed round). Numbers: 0 how many are done.
 */
public final class Order implements Script {
    private final String part;
    private int times;
    private BiConsumer<Ctx, Integer> each = (c, i) -> {};

    private Order(String part) {
        this.part = part;
    }

    public static Order of(String part) {
        return new Order(part);
    }

    /** The same part, this many times. */
    public Order times(int n) {
        times = n;
        return this;
    }

    /** What happens as each is worked; it gets how many were done before it. */
    public Order each(BiConsumer<Ctx, Integer> each) {
        this.each = each;
        return this;
    }

    @Override
    public boolean use(Ctx c, ServerPlayer p, String name, int i, ItemStack held) {
        if (!name.equals(part)) return false;
        int done = c.v(0), all = times > 0 ? times : c.count(part);
        if (done >= all) return true;
        if (times == 0 && i != done) {
            c.sound(c.pos(part, i), SoundEvents.STONE_HIT, 0.7F, 0.6F);
            c.puff(c.pos(part, i), ParticleTypes.SMOKE, 3, 0.15, 0.01);
            return true;
        }
        each.accept(c, done);
        c.v(0, done + 1);
        if (done + 1 >= all) c.complete(p);
        return true;
    }
}
