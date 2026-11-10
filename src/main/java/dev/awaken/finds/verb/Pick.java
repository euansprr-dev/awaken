package dev.awaken.finds.verb;

import dev.awaken.finds.kit.PropBlock;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The right one of several (SPEC §58.5): parts that look alike, one of which is the one to use. Which is the site's
 * own (from its seed), and the scene marks it from the same number ({@link #right}). A wrong one goes slack (a prop
 * moves to its state 1) and stays that way. Numbers: 0 a bit per wrong one tried.
 */
public final class Pick implements Script {
    private final String part;

    private Pick(String part) {
        this.part = part;
    }

    public static Pick oneOf(String part) {
        return new Pick(part);
    }

    public static int right(long seed, int n) {
        return RandomSource.create(seed ^ 0x91C7L).nextInt(n);
    }

    @Override
    public boolean use(Ctx c, ServerPlayer p, String name, int i, ItemStack held) {
        if (!name.equals(part)) return false;
        if (i == right(c.seed(), c.count(part))) {
            c.complete(p);
            return true;
        }
        int bits = c.v(0);
        if ((bits & (1 << i)) != 0) return true;
        c.v(0, bits | (1 << i));
        BlockState s = c.at(part, i);
        if (s.getBlock() instanceof PropBlock) c.set(part, i, s.setValue(PropBlock.STATE, 1));
        c.sound(c.pos(part, i), SoundEvents.WOOL_BREAK, 1.0F, 0.6F);
        c.puff(c.pos(part, i), ParticleTypes.SMOKE, 4, 0.2, 0.01);
        return true;
    }
}
