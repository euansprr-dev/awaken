package dev.awaken.finds.verb;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * Reach (SPEC §58.5): the whole of it is getting your hand on the part named {@code prize}. Where it stands is the
 * puzzle. With a test, the hand has to hold the right thing (a page to trade, a bell to ring).
 */
public final class Prize implements Script {
    private final String part;
    private Predicate<ItemStack> with = s -> true;
    private boolean takes;
    private java.util.function.BiConsumer<Ctx, Integer> then = (c, i) -> {};

    private Prize(String part) {
        this.part = part;
    }

    public static Prize at(String part) {
        return new Prize(part);
    }

    public Prize holding(Predicate<ItemStack> with) {
        this.with = with;
        return this;
    }

    /** Something the world does when the prize is taken (a state changes, a sound). */
    public Prize then(java.util.function.BiConsumer<Ctx, Integer> then) {
        this.then = then;
        return this;
    }

    /** One of what's held is given up. */
    public Prize takes() {
        takes = true;
        return this;
    }

    @Override
    public boolean use(Ctx c, ServerPlayer p, String name, int i, ItemStack held) {
        if (!name.equals(part) || !with.test(held)) return false;
        if (takes && !p.isCreative()) held.shrink(1);
        then.accept(c, i);
        c.complete(p);
        return true;
    }
}
