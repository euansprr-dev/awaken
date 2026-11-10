package dev.awaken.finds.verb;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Light (SPEC §58.5): bring a flame to every lamp. With a time, all of them have to burn at once: the first to be lit
 * burns that long, and when it dies they all go out and you start again. In order: each lamp only takes when the one
 * before it burns. With a draught: while the hole stands open, the lamps it reaches blow out every few seconds.
 * Numbers: 0 how many burn.
 */
public final class Light implements Script {
    private final String part;
    private int seconds;
    private boolean ordered;
    private String hole = "";
    private int gustFrom, gustTo;

    private Light(String part) {
        this.part = part;
    }

    public static Light all(String part) {
        return new Light(part);
    }

    /** They only stay lit this long from the first. */
    public Light within(int seconds) {
        this.seconds = seconds;
        return this;
    }

    public Light inOrder() {
        ordered = true;
        return this;
    }

    /** Lamps {@code from..to} blow out while nothing stands at the part {@code hole}. */
    public Light draught(String hole, int from, int to) {
        this.hole = hole;
        gustFrom = from;
        gustTo = to;
        return this;
    }

    private int burning(Ctx c) {
        int n = 0;
        for (int i = 0; i < c.count(part); i++) if (Hands.lit(c.at(part, i))) n++;
        return n;
    }

    @Override
    public boolean use(Ctx c, ServerPlayer p, String name, int i, ItemStack held) {
        if (!name.equals(part) || !Hands.flame(held)) return false;
        BlockState s = c.at(part, i);
        if (Hands.lit(s)) return true;
        if (ordered && i > 0 && !Hands.lit(c.at(part, i - 1))) {
            c.sound(c.pos(part, i), SoundEvents.FIRE_EXTINGUISH, 0.35F, 1.6F);
            c.puff(c.pos(part, i), ParticleTypes.SMOKE, 4, 0.1, 0.01);
            return true;
        }
        if (burning(c) == 0) c.clock(c.now());
        c.set(part, i, Hands.lit(s, true));
        c.sound(c.pos(part, i), SoundEvents.FLINTANDSTEEL_USE, 0.8F, 0.9F + i * 0.06F);
        c.puff(c.pos(part, i), ParticleTypes.SMALL_FLAME, 5, 0.12, 0.01);
        if (burning(c) == c.count(part)) c.complete(p);
        return true;
    }

    @Override
    public void tick(Ctx c) {
        int n = burning(c);
        if (n == 0 || n == c.count(part)) return;
        if (seconds > 0) {
            long left = seconds * 20L - (c.now() - c.clock());
            if (left <= 0) {
                out(c, 0, c.count(part) - 1);
                return;
            }
            // the first lamp gutters through its last quarter, so you can see the time going
            if (left < seconds * 5L && c.now() % 10 == 0) c.puff(c.pos(part, 0), ParticleTypes.SMOKE, 1, 0.05, 0.005);
        }
        if (!hole.isEmpty() && c.now() % 80 < 5 && c.level.getBlockState(c.pos(hole, 0)).isAir()) out(c, gustFrom, gustTo);
    }

    private void out(Ctx c, int from, int to) {
        boolean any = false;
        for (int i = from; i <= to && i < c.count(part); i++) {
            BlockState s = c.at(part, i);
            if (!Hands.lit(s)) continue;
            c.set(part, i, Hands.lit(s, false));
            c.puff(c.pos(part, i), ParticleTypes.SMOKE, 6, 0.1, 0.02);
            any = true;
        }
        if (any) c.sound(c.pos(part, from), SoundEvents.FIRE_EXTINGUISH, 0.4F, 1.3F);
    }

    @Override
    public boolean mayPlace(Ctx c, ServerPlayer p, BlockPos rel, BlockState state) {
        return !hole.isEmpty() && c.scene.part(hole).contains(rel);
    }
}
