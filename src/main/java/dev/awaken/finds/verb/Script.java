package dev.awaken.finds.verb;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * What a find does (SPEC §58.5). The heart hands a script everything that happens at its site: a part used, struck,
 * shot, broken, something placed, the passing of time. A script keeps nothing itself; all it knows is in the heart's
 * state through {@link Ctx}, so it survives a restart and is the same for everyone. When its work is done it calls
 * {@link Ctx#complete}. Scripts chain with {@link Steps}; each link gets its own four numbers and one clock.
 * <p>
 * Every method answers on the server. "Handled" (true) means the click is used up: nothing else happens with it.
 */
public interface Script {
    /** The first time a player comes near since the site last slept. */
    default void wake(Ctx c) {}

    /** Every five ticks while a player is within reach of the site. */
    default void tick(Ctx c) {}

    /** A part was used (right click), with what was in the hand. */
    default boolean use(Ctx c, ServerPlayer p, String part, int index, ItemStack held) {
        return false;
    }

    /** A part was struck (left click). */
    default boolean hit(Ctx c, ServerPlayer p, String part, int index, ItemStack held) {
        return false;
    }

    /** A projectile landed on a part; {@code p} is who loosed it, when a player did. */
    default void shot(Ctx c, @Nullable ServerPlayer p, String part, int index) {}

    /** Whether a survival player may break this part. Nothing else of a site can be broken. */
    default boolean mayBreak(Ctx c, ServerPlayer p, String part, int index) {
        return false;
    }

    default void broke(Ctx c, ServerPlayer p, String part, int index) {}

    /** Whether a survival player may place this block at a scene offset inside the site. */
    default boolean mayPlace(Ctx c, ServerPlayer p, BlockPos rel, BlockState state) {
        return false;
    }

    default void placed(Ctx c, ServerPlayer p, BlockPos rel, BlockState state) {}

    /** The site goes back to how the world made it. Parts are restored by the heart; this is for anything else. */
    default void reset(Ctx c) {}

    /** How many links this script is. One, unless it's {@link Steps}. */
    default int length() {
        return 1;
    }
}
