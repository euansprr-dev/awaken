package dev.awaken.finds.verb;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Scripts in a row (SPEC §58.5): the first runs until it completes, then the second, and so on; the find is done when
 * the last is. Each link has its own four numbers and its own clock, so up to four links.
 */
public final class Steps implements Script {
    private final Script[] links;

    private Steps(Script[] links) {
        if (links.length > 4) throw new IllegalArgumentException("a find has room for four steps");
        this.links = links;
    }

    public static Script of(Script... links) {
        return links.length == 1 ? links[0] : new Steps(links);
    }

    private @Nullable Script now(Ctx c) {
        int s = c.stage();
        if (s >= links.length) return null;
        c.slot = s;
        return links[s];
    }

    @Override public int length() { return links.length; }

    @Override public void wake(Ctx c) {
        for (int i = 0; i < links.length; i++) {
            c.slot = i;
            links[i].wake(c);
        }
    }

    @Override public void tick(Ctx c) {
        Script s = now(c);
        if (s != null) s.tick(c);
    }

    @Override public int every() {
        int e = 5;
        for (Script s : links) e = Math.min(e, s.every());
        return e;
    }

    @Override public boolean use(Ctx c, ServerPlayer p, String part, int i, ItemStack held) {
        Script s = now(c);
        return s != null && s.use(c, p, part, i, held);
    }

    @Override public boolean hit(Ctx c, ServerPlayer p, String part, int i, ItemStack held) {
        Script s = now(c);
        return s != null && s.hit(c, p, part, i, held);
    }

    @Override public void shot(Ctx c, @Nullable ServerPlayer p, String part, int i) {
        Script s = now(c);
        if (s != null) s.shot(c, p, part, i);
    }

    @Override public boolean mayBreak(Ctx c, ServerPlayer p, String part, int i) {
        Script s = now(c);
        return s != null && s.mayBreak(c, p, part, i);
    }

    @Override public void broke(Ctx c, ServerPlayer p, String part, int i) {
        Script s = now(c);
        if (s != null) s.broke(c, p, part, i);
    }

    @Override public boolean mayPlace(Ctx c, ServerPlayer p, BlockPos rel, BlockState state) {
        Script s = now(c);
        return s != null && s.mayPlace(c, p, rel, state);
    }

    @Override public void placed(Ctx c, ServerPlayer p, BlockPos rel, BlockState state) {
        Script s = now(c);
        if (s != null) s.placed(c, p, rel, state);
    }

    @Override public void reset(Ctx c) {
        for (int i = 0; i < links.length; i++) {
            c.slot = i;
            links[i].reset(c);
        }
    }
}
