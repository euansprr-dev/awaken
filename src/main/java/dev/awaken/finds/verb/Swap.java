package dev.awaken.finds.verb;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pull, and something moves (SPEC §58.5): each use of the trigger takes the next block away from the parts named
 * {@code from} and, where there's a matching part named {@code to}, stands {@code becomes} there (a bridge lowered
 * plank by plank, a bank that comes away). With {@link #atOnce}, one pull does the lot. Numbers: 0 how many have gone.
 */
public final class Swap implements Script {
    private final String trigger, from, to;
    private final BlockState becomes;
    private boolean atOnce;
    private SoundEvent sound = SoundEvents.WOOD_PLACE;

    private Swap(String trigger, String from, String to, BlockState becomes) {
        this.trigger = trigger;
        this.from = from;
        this.to = to;
        this.becomes = becomes;
    }

    public static Swap pull(String trigger, String from, String to, BlockState becomes) {
        return new Swap(trigger, from, to, becomes);
    }

    public Swap atOnce() {
        atOnce = true;
        return this;
    }

    public Swap sound(SoundEvent e) {
        sound = e;
        return this;
    }

    @Override
    public boolean use(Ctx c, ServerPlayer p, String name, int i, ItemStack held) {
        if (!name.equals(trigger)) return false;
        int n = c.v(0), all = c.count(from);
        if (n >= all) return true;
        do {
            c.puff(c.pos(from, n), ParticleTypes.POOF, 4, 0.25, 0.02);
            c.set(from, n, Blocks.AIR.defaultBlockState());
            if (!to.isEmpty() && n < c.count(to)) c.set(to, n, c.turned(becomes));
            n++;
        } while (atOnce && n < all);
        c.v(0, n);
        c.sound(c.pos(trigger, i), sound, 1.0F, 0.7F + 0.08F * n);
        if (n >= all) c.complete(p);
        return true;
    }
}
