package dev.awaken.finds.verb;

import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Carvings (SPEC §61.4): a monument's steps, with the parts named {@code carving} added. Using one has the System read
 * it, in brackets, the way it reads the stone people. Reading is never a step: it works before, during and after, and
 * nothing waits on it.
 */
public final class Lore implements Script {
    private final Script inner;
    private final String part;
    private final String[] lines;

    private Lore(Script inner, String part, String[] lines) {
        this.inner = inner;
        this.part = part;
        this.lines = lines;
    }

    /** {@code inner}'s steps, and a line for each part named {@code part} (the first for index 0). */
    public static Script with(Script inner, String part, String... lines) {
        return new Lore(inner, part, lines);
    }

    @Override public int length() { return inner.length(); }
    @Override public void wake(Ctx c) { inner.wake(c); }
    @Override public void tick(Ctx c) { inner.tick(c); }
    @Override public int every() { return inner.every(); }

    @Override
    public boolean use(Ctx c, ServerPlayer p, String name, int i, ItemStack held) {
        if (!name.equals(part)) return inner.use(c, p, name, i, held);
        String line = lines[Math.min(i, lines.length - 1)];
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("[" + line + "]")).topic("monument").ttl(20 * 14).build());
        c.sound(c.pos(part, i), SoundEvents.BOOK_PAGE_TURN, 0.7F, 0.7F);
        c.puff(c.pos(part, i), ParticleTypes.ENCHANT, 10, 0.4, 0.2);
        return true;
    }

    @Override public boolean hit(Ctx c, ServerPlayer p, String name, int i, ItemStack held) { return inner.hit(c, p, name, i, held); }
    @Override public void shot(Ctx c, @Nullable ServerPlayer p, String name, int i) { inner.shot(c, p, name, i); }
    @Override public boolean mayBreak(Ctx c, ServerPlayer p, String name, int i) { return inner.mayBreak(c, p, name, i); }
    @Override public void broke(Ctx c, ServerPlayer p, String name, int i) { inner.broke(c, p, name, i); }
    @Override public boolean mayPlace(Ctx c, ServerPlayer p, BlockPos rel, BlockState state) { return inner.mayPlace(c, p, rel, state); }
    @Override public void placed(Ctx c, ServerPlayer p, BlockPos rel, BlockState state) { inner.placed(c, p, rel, state); }
    @Override public void reset(Ctx c) { inner.reset(c); }
}
