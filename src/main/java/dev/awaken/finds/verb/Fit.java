package dev.awaken.finds.verb;

import dev.awaken.finds.kit.PropBlock;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

/**
 * Fit (SPEC §58.5): one thing that takes several of the same piece, and grows by a state for each (a cairn a stone
 * taller, a signpost an arm fuller). The pieces can be lying about the site as loose parts to pick up. Numbers: 0 how
 * many fitted.
 */
public final class Fit implements Script {
    private final String part;
    private final Predicate<ItemStack> accepts;
    private final int times;
    private String loose = "";

    private Fit(String part, Predicate<ItemStack> accepts, int times) {
        this.part = part;
        this.accepts = accepts;
        this.times = times;
    }

    public static Fit on(String part, Predicate<ItemStack> accepts, int times) {
        return new Fit(part, accepts, times);
    }

    public Fit carry(String loose) {
        this.loose = loose;
        return this;
    }

    /** Picks a loose prop up as its own item. Shared by every script that lets things be carried. */
    static boolean pickUp(Ctx c, ServerPlayer p, String loose, int i) {
        BlockState s = c.at(loose, i);
        if (s.isAir()) return false;
        ItemStack give = new ItemStack(s.getBlock());
        if (give.isEmpty()) return false;
        c.set(loose, i, Blocks.AIR.defaultBlockState());
        if (!p.getInventory().add(give)) c.pop(c.pos(loose, i), give);
        c.sound(c.pos(loose, i), SoundEvents.ITEM_PICKUP, 0.6F, 0.8F);
        return true;
    }

    @Override
    public boolean use(Ctx c, ServerPlayer p, String name, int i, ItemStack held) {
        if (!loose.isEmpty() && name.equals(loose)) return pickUp(c, p, loose, i);
        if (!name.equals(part) || !accepts.test(held)) return false;
        int n = c.v(0) + 1;
        c.v(0, n);
        if (!p.isCreative()) held.shrink(1);
        BlockState s = c.at(part, i);
        if (s.getBlock() instanceof PropBlock) c.set(part, i, s.setValue(PropBlock.STATE, Math.min(3, s.getValue(PropBlock.STATE) + 1)));
        c.sound(c.pos(part, i), SoundEvents.STONE_PLACE, 0.8F, 0.8F + 0.1F * n);
        c.puff(c.pos(part, i).above(), ParticleTypes.CRIT, 4, 0.2, 0.02);
        if (n >= times) c.complete(p);
        return true;
    }
}
