package dev.awaken.finds.verb;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Wait (SPEC §58.5): the right hour or the right weather. {@link #stay} completes when somebody has stood by a part
 * long enough while the condition holds. {@link #only} wraps another script so that it answers only then; at any
 * other time a use gets a thread of dust and nothing else, which is the world saying "not now".
 * Numbers (stay): 0 ticks stood so far.
 */
public final class When implements Script {
    public static final Predicate<Ctx> NIGHT = Ctx::night, DAY = c -> !c.night(), RAIN = Ctx::raining, STORM = Ctx::storm,
            DAWN = c -> c.hour() >= 22300 || c.hour() <= 600, DUSK = c -> c.hour() >= 11600 && c.hour() <= 13200,
            MIDNIGHT = c -> c.hour() >= 17000 && c.hour() <= 19500, CLEAR_NIGHT = c -> c.night() && !c.raining(),
            SNOWING = c -> c.raining() && (Ctx.testWeather != 0 || c.level.getBiome(c.site.ground()).value().coldEnoughToSnow(c.site.ground(), c.level.getSeaLevel()));

    private final Predicate<Ctx> cond;
    private final @Nullable Script inner;
    private final String seat;
    private final int ticks;

    private When(Predicate<Ctx> cond, @Nullable Script inner, String seat, int ticks) {
        this.cond = cond;
        this.inner = inner;
        this.seat = seat;
        this.ticks = ticks;
    }

    /** Stand within two blocks of the part for {@code seconds} while the condition holds. */
    public static When stay(String seat, Predicate<Ctx> cond, int seconds) {
        return new When(cond, null, seat, seconds * 20);
    }

    /** {@code inner} answers only while the condition holds. */
    public static When only(Predicate<Ctx> cond, Script inner) {
        return new When(cond, inner, "", 0);
    }

    @Override
    public int every() {
        return inner == null ? 5 : inner.every();
    }

    @Override
    public void tick(Ctx c) {
        if (inner != null) {
            if (cond.test(c)) inner.tick(c);
            return;
        }
        BlockPos at = c.pos(seat, 0);
        ServerPlayer who = null;
        if (cond.test(c)) for (ServerPlayer p : c.players(c.kind().reach() + 4)) if (p.distanceToSqr(at.getX() + 0.5, at.getY(), at.getZ() + 0.5) < 2.2 * 2.2) who = p;
        int stood = c.v(0);
        if (who == null) {
            if (stood > 0) c.v(0, Math.max(0, stood - 10));
            return;
        }
        stood += 5;
        c.v(0, stood);
        if (stood % 40 == 0) c.puff(at.above(), ParticleTypes.WAX_ON, 2, 0.3, 0.01);
        if (stood >= ticks) c.complete(who);
    }

    private boolean notNow(Ctx c, String part, int i) {
        if (c.has(part)) c.puff(c.pos(part, i), ParticleTypes.ASH, 6, 0.2, 0.01);
        return false;
    }

    @Override public boolean use(Ctx c, ServerPlayer p, String part, int i, ItemStack held) {
        if (inner == null) return false;
        return cond.test(c) ? inner.use(c, p, part, i, held) : notNow(c, part, i);
    }

    @Override public boolean hit(Ctx c, ServerPlayer p, String part, int i, ItemStack held) {
        return inner != null && cond.test(c) && inner.hit(c, p, part, i, held);
    }

    @Override public void shot(Ctx c, @Nullable ServerPlayer p, String part, int i) {
        if (inner != null && cond.test(c)) inner.shot(c, p, part, i);
    }

    @Override public boolean mayBreak(Ctx c, ServerPlayer p, String part, int i) {
        return inner != null && cond.test(c) && inner.mayBreak(c, p, part, i);
    }

    @Override public void broke(Ctx c, ServerPlayer p, String part, int i) {
        if (inner != null) inner.broke(c, p, part, i);
    }

    @Override public boolean mayPlace(Ctx c, ServerPlayer p, BlockPos rel, BlockState s) {
        return inner != null && inner.mayPlace(c, p, rel, s);
    }

    @Override public void placed(Ctx c, ServerPlayer p, BlockPos rel, BlockState s) {
        if (inner != null) inner.placed(c, p, rel, s);
    }

    @Override public void wake(Ctx c) {
        if (inner != null) inner.wake(c);
    }

    @Override public void reset(Ctx c) {
        if (inner != null) inner.reset(c);
    }
}
