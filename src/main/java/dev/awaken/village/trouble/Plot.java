package dev.awaken.village.trouble;

import dev.awaken.village.VillageService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * How one kind of trouble plays (SPEC §28.7, PLAN 6.1): it {@link #begin begins} from its cause with a call that is in
 * the world (a bell, smoke, a runner, a parent in the square), is {@link #ask asked} for by a person (a choice card),
 * runs its deed while the village is awake ({@link #tick}), and ends: well, with whoever helped paid, or without you on
 * its own clock ({@link #miss}), never in a dead end or a named death.
 */
interface Plot {
    /** What a plot works with: the world, the saved state, the village if it's running now, the clocks. */
    record Ctx(ServerLevel level, TroubleState st, VillageService.@Nullable Live live, long now, long day, int tod) {}

    /** Sets the trouble up from its cause (who it's about, where the deed is, the call). False: it can't start here. */
    boolean begin(Ctx c, Trouble t);

    /** Every second while its village is awake. */
    void tick(Ctx c, Trouble t);

    /** Someone the trouble stands in the square was spoken to (the parent, the elder): true when it answered. */
    boolean ask(Ctx c, Trouble t, ServerPlayer p, int rec);

    /** A player said Help on its card (their party near them too). */
    void help(Ctx c, Trouble t, ServerPlayer p);

    /** Its line in the Journal for a player who said Help. */
    String journal(Ctx c, Trouble t, ServerPlayer p);

    /** Where the Now tracker points a helper (null: nowhere particular). */
    net.minecraft.core.@Nullable BlockPos goal(Ctx c, Trouble t, ServerPlayer p);

    /** Its clock ran out: how it ends without you (worse, never a dead end). */
    void miss(Ctx c, Trouble t);

    /** Whether it holds past its clock while someone is in the middle of the deed (in the lair, carrying someone home). */
    default boolean holds(Ctx c, Trouble t) {
        return false;
    }

    /** The person it's about was touched (cut free, picked up, asked to follow). */
    default void touched(Ctx c, Trouble t, ServerPlayer p, Rescuee r) {}

    /** It's over (well or not): anything it still holds goes. */
    default void cleanup(Ctx c, Trouble t) {}
}
