package dev.awaken.finds.verb;

import dev.awaken.finds.kit.PropBlock;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Turn (SPEC §58.5): dials that go round a quarter at a use, and one arrangement that's right. The right one is the
 * site's own (from its seed); the scene draws the clue from the same numbers ({@link #targets}). With hours: it only
 * counts while the game's clock is inside them (a sundial's shadow, a star).
 */
public final class Turn implements Script {
    private final String part;
    private long from = -1, to = -1;

    private Turn(String part) {
        this.part = part;
    }

    public static Turn dials(String part) {
        return new Turn(part);
    }

    /** Only right between these hours of the game day (0..23999, sunrise 0); {@code from} may be later than {@code to} to span midnight. */
    public Turn between(long from, long to) {
        this.from = from;
        this.to = to;
        return this;
    }

    /** What each of {@code n} dials must show, 0..3. Never all zero, so a site is never solved as made. */
    public static int[] targets(long seed, int n) {
        RandomSource r = RandomSource.create(seed ^ 0x7D1A15L);
        int[] t = new int[n];
        boolean any = false;
        for (int i = 0; i < n; i++) {
            t[i] = r.nextInt(4);
            any |= t[i] != 0;
        }
        if (!any) t[0] = 2;
        return t;
    }

    private boolean inHours(Ctx c) {
        if (from < 0) return true;
        long h = c.hour();
        return from <= to ? h >= from && h <= to : h >= from || h <= to;
    }

    private boolean right(Ctx c) {
        int[] t = targets(c.seed(), c.count(part));
        for (int i = 0; i < t.length; i++) {
            BlockState s = c.at(part, i);
            if (!(s.getBlock() instanceof PropBlock) || s.getValue(PropBlock.STATE) != t[i]) return false;
        }
        return true;
    }

    @Override
    public boolean use(Ctx c, ServerPlayer p, String name, int i, ItemStack held) {
        if (!name.equals(part)) return false;
        BlockState s = c.at(part, i);
        if (!(s.getBlock() instanceof PropBlock)) return false;
        c.set(part, i, s.setValue(PropBlock.STATE, (s.getValue(PropBlock.STATE) + 1) & 3));
        c.sound(c.pos(part, i), SoundEvents.GRINDSTONE_USE, 0.5F, 1.3F);
        if (right(c) && inHours(c)) {
            c.puff(c.pos(part, i).above(), ParticleTypes.WAX_ON, 8, 0.3, 0.02);
            c.complete(p);
        }
        return true;
    }

    @Override
    public void tick(Ctx c) {
        // set right and left alone: it completes when the hour comes round
        if (from >= 0 && c.now() % 20 == 0 && right(c) && inHours(c)) {
            var near = c.players(10);
            if (!near.isEmpty()) c.complete(near.get(0));
        }
    }
}
