package dev.awaken.finds.verb;

import dev.awaken.finds.FindsContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Run (SPEC §58.5): touch the start and reach every gate in order before the time is up. The gates are lamps: all of
 * them light when you start and go out one by one from the first, so the dark chases you down the course. Touching
 * the ground below {@code floor} blocks under the start ends the run, for a course you mustn't fall from. Your best
 * time on each kind of run is kept. Numbers: 0 the next gate, 1 running, 2 who (a hash). The clock is the start.
 */
public final class Course implements Script {
    private final String start, gate;
    private final int seconds;
    private int floor = Integer.MIN_VALUE;

    private Course(String start, String gate, int seconds) {
        this.start = start;
        this.gate = gate;
        this.seconds = seconds;
    }

    public static Course run(String start, String gate, int seconds) {
        return new Course(start, gate, seconds);
    }

    /** Dropping this far below the start stone ends the run. */
    public Course dontFall(int below) {
        floor = below;
        return this;
    }

    @Override
    public int every() {
        return 2;
    }

    private void lamps(Ctx c, int from, boolean on) {
        for (int i = from; i < c.count(gate); i++) {
            BlockState s = c.at(gate, i);
            if (Hands.lit(s) != on) c.set(gate, i, Hands.lit(s, on));
        }
    }

    @Override
    public boolean use(Ctx c, ServerPlayer p, String name, int i, ItemStack held) {
        if (!name.equals(start)) return false;
        c.v(0, 0);
        c.v(1, 1);
        c.v(2, p.getUUID().hashCode());
        c.clock(c.now());
        lamps(c, 0, true);
        c.sound(c.pos(start, 0), SoundEvents.FLINTANDSTEEL_USE, 1.0F, 1.2F);
        c.puff(c.pos(start, 0).above(), ParticleTypes.SMALL_FLAME, 8, 0.2, 0.02);
        return true;
    }

    private void end(Ctx c) {
        c.v(1, 0);
        lamps(c, 0, false);
        c.sound(c.pos(start, 0), SoundEvents.FIRE_EXTINGUISH, 0.5F, 1.1F);
    }

    @Override
    public void tick(Ctx c) {
        if (c.v(1) == 0) return;
        ServerPlayer runner = null;
        for (ServerPlayer p : c.level.players()) if (p.getUUID().hashCode() == c.v(2) && p.isAlive()) runner = p;
        long t = c.now() - c.clock();
        int n = c.count(gate), next = c.v(0);
        if (runner == null || t > seconds * 20L || (floor != Integer.MIN_VALUE && runner.getY() < c.pos(start, 0).getY() - floor)) {
            end(c);
            return;
        }
        // the lamps behind the clock go dark
        int dark = (int) (t * n / (seconds * 20L));
        for (int i = 0; i < Math.min(dark, next); i++) {
            BlockState s = c.at(gate, i);
            if (Hands.lit(s)) c.set(gate, i, Hands.lit(s, false));
        }
        BlockPos g = c.pos(gate, next);
        if (runner.distanceToSqr(g.getX() + 0.5, g.getY(), g.getZ() + 0.5) < 3.2 * 3.2) {
            c.sound(g, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 0.8F + 0.1F * next);
            c.puff(g.above(), ParticleTypes.WAX_ON, 5, 0.25, 0.02);
            next++;
            c.v(0, next);
            if (next == n) {
                c.v(1, 0);
                lamps(c, 0, true);
                runner.setData(FindsContent.FINDS, runner.getData(FindsContent.FINDS).withTime(c.kind().id(), (int) t));
                c.complete(runner);
            }
        }
    }
}
