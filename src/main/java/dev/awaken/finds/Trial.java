package dev.awaken.finds;

import dev.awaken.finds.verb.Ctx;
import dev.awaken.finds.verb.Script;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * A scripted player at a find (SPEC §58.9): what a kind's {@link Kind.Play} drives. It does to the heart's script
 * exactly what the listeners would do for a real player (a use, a strike, a shot, a dug block), so a walkthrough that
 * passes here is one a player can do. Time and the hour are pretended through {@link Ctx}.
 */
public final class Trial {
    private final FindHeartBlockEntity heart;
    public final ServerPlayer player;

    Trial(FindHeartBlockEntity heart, ServerPlayer player) {
        this.heart = heart;
        this.player = player;
    }

    public Ctx c() {
        Ctx c = heart.ctx();
        if (c == null) throw new IllegalStateException("the heart has no site");
        return c;
    }

    private Script s() {
        return heart.script();
    }

    public Trial hold(ItemLike item) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        return this;
    }

    public Trial use(String part, int i) {
        s().use(c(), player, part, i, player.getMainHandItem());
        return this;
    }

    public Trial hit(String part, int i) {
        s().hit(c(), player, part, i, player.getMainHandItem());
        return this;
    }

    public Trial shoot(String part, int i) {
        s().shot(c(), player, part, i);
        return this;
    }

    /** Digs a part out, if the script lets a survival player. */
    public Trial dig(String part, int i) {
        Ctx c = c();
        if (!s().mayBreak(c, player, part, i)) throw new IllegalStateException(c.kind().id() + ": " + part + " " + i + " can't be dug");
        c.level.setBlock(c.pos(part, i), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        s().broke(c(), player, part, i);
        return this;
    }

    /** Puts a block at a part, as a player placing it would. */
    public Trial set(String part, int i, net.minecraft.world.level.block.state.BlockState state) {
        Ctx c = c();
        c.level.setBlock(c.pos(part, i), state, Block.UPDATE_ALL);
        return this;
    }

    /** Stands the player at a part. */
    public Trial stand(String part, int i) {
        BlockPos p = c().pos(part, i);
        player.snapTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
        return this;
    }

    /** Runs the script's tick {@code n} times. */
    public Trial ticks(int n) {
        for (int k = 0; k < n; k++) s().tick(c());
        return this;
    }

    /** Lets game time pass without the world ticking. */
    public Trial pass(int ticks) {
        Ctx.testTicks += ticks;
        return this;
    }

    public Trial hour(long h) {
        Ctx.testHour = h;
        return this;
    }

    /** 1 clear, 2 rain, 3 a storm. */
    public Trial weather(int w) {
        Ctx.testWeather = w;
        return this;
    }

    public int count(String part) {
        return c().count(part);
    }

    public long seed() {
        return c().seed();
    }
}
