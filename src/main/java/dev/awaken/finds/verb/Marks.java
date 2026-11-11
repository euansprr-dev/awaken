package dev.awaken.finds.verb;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.function.IntFunction;

/**
 * Shoot (SPEC §58.5): marks that have to be hit from a distance. A mark that's struck becomes what {@code struck}
 * says (gone, by default). With a limit, a shot that lands anywhere else in the site counts against you, and past the
 * limit the marks come back. Numbers: 0 a bit per mark struck, 1 shots spent.
 */
public final class Marks implements Script {
    private final String part;
    private int limit;
    private IntFunction<BlockState> struck = i -> Blocks.AIR.defaultBlockState();

    private Marks(String part) {
        this.part = part;
    }

    public static Marks hit(String part) {
        return new Marks(part);
    }

    public Marks shots(int limit) {
        this.limit = limit;
        return this;
    }

    public Marks becomes(IntFunction<BlockState> struck) {
        this.struck = struck;
        return this;
    }

    @Override
    public void shot(Ctx c, @Nullable ServerPlayer p, String name, int i) {
        int spent = c.v(1) + 1;
        c.v(1, spent);
        if (name.equals(part)) {
            int bits = c.v(0);
            if ((bits & (1 << i)) == 0) {
                bits |= 1 << i;
                c.v(0, bits);
                c.sound(c.pos(part, i), SoundEvents.DECORATED_POT_SHATTER, 0.9F, 0.9F + 0.08F * Integer.bitCount(bits));
                c.puff(c.pos(part, i), ParticleTypes.CRIT, 12, 0.25, 0.1);
                c.set(part, i, c.turned(struck.apply(i)));
                if (Integer.bitCount(bits) == c.count(part)) {
                    c.complete(p);
                    return;
                }
            }
        }
        if (limit > 0 && spent >= limit) { // out of shots: the marks are hung again
            c.v(0, 0);
            c.v(1, 0);
            for (int k = 0; k < c.count(part); k++) c.set(part, k, c.made(part, k));
            c.sound(c.site.ground().above(), SoundEvents.WOOL_PLACE, 0.8F, 0.7F);
        }
    }
}
