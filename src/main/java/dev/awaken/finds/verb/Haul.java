package dev.awaken.finds.verb;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pull (SPEC §58.5): a rope you haul, hand over hand. Each pull moves the load one place along the parts named
 * {@code load} (the scene lists them from where it starts to where it ends); at the last, it's done. Gently: a pull
 * within a second of the last one slips a place back. Numbers: 0 pulls so far. The clock is the last pull.
 */
public final class Haul implements Script {
    private final String rope, load;
    private boolean gently;
    private BlockState trail = Blocks.AIR.defaultBlockState();

    private Haul(String rope, String load) {
        this.rope = rope;
        this.load = load;
    }

    public static Haul on(String rope, String load) {
        return new Haul(rope, load);
    }

    /** What's left where the load was (the rope it hangs on). */
    public Haul trail(BlockState s) {
        trail = s;
        return this;
    }

    public Haul gently() {
        gently = true;
        return this;
    }

    @Override
    public boolean use(Ctx c, ServerPlayer p, String name, int i, ItemStack held) {
        if (!name.equals(rope)) return false;
        int at = c.v(0), last = c.count(load) - 1;
        if (at >= last) return true;
        boolean slip = gently && at > 0 && c.now() - c.clock() < 20;
        int to = slip ? at - 1 : at + 1;
        BlockState thing = c.at(load, at);
        c.set(load, at, slip ? Blocks.AIR.defaultBlockState() : trail);
        c.set(load, to, thing);
        c.v(0, to);
        c.clock(c.now());
        c.sound(c.pos(rope, i), slip ? SoundEvents.WOOL_BREAK : SoundEvents.LEAD_TIED, 0.9F, slip ? 0.7F : 0.8F + 0.06F * to);
        if (to == last) c.complete(p);
        return true;
    }
}
