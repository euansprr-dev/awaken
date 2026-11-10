package dev.awaken.finds.verb;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Follow (SPEC §58.5): something that moves off when you come near, waits when you fall behind, and goes home if you
 * lose it. It travels the parts named {@code way}, first to last. By default it's a light (a few particles a tick);
 * with a role, it's the creature the scene put there, walked along the same way. Reaching the end completes.
 * Numbers: 0 the waypoint it has reached, 1 how far to the next (in hundredths), 2 ticks since anyone was near.
 */
public final class Guide implements Script {
    private final String way;
    private String role = "";
    private ParticleOptions look = ParticleTypes.END_ROD;
    private double speed = 0.16;
    private net.minecraft.sounds.SoundEvent note;

    private Guide(String way) {
        this.way = way;
    }

    public static Guide along(String way) {
        return new Guide(way);
    }

    public Guide as(ParticleOptions look) {
        this.look = look;
        return this;
    }

    /** The scene's creature with this role is the guide. */
    public Guide creature(String role) {
        this.role = role;
        return this;
    }

    /** A sound it makes each time it reaches a waypoint, a little higher each time. */
    public Guide note(net.minecraft.sounds.SoundEvent e) {
        note = e;
        return this;
    }

    /** Blocks a tick. */
    public Guide speed(double s) {
        speed = s;
        return this;
    }

    @Override
    public int every() {
        return 1;
    }

    private Vec3 at(Ctx c, int i) {
        BlockPos p = c.pos(way, Math.min(i, c.count(way) - 1));
        return new Vec3(p.getX() + 0.5, p.getY() + 0.4, p.getZ() + 0.5);
    }

    @Override
    public void tick(Ctx c) {
        int n = c.count(way);
        if (n < 2) return;
        int i = c.v(0), f = c.v(1);
        Vec3 a = at(c, i), b = at(c, i + 1);
        Vec3 pos = a.lerp(b, f / 100.0);
        ServerPlayer nearest = null;
        double best = 40 * 40;
        for (ServerPlayer p : c.level.players()) {
            double d = p.distanceToSqr(pos);
            if (d < best && p.isAlive() && !p.isSpectator()) {
                best = d;
                nearest = p;
            }
        }
        if (nearest == null) {
            int away = c.v(2) + 1;
            if (away > 240 && (i > 0 || f > 0)) { // lost it: it goes home
                c.v(0, 0);
                c.v(1, 0);
                away = 0;
            }
            c.v(2, away);
        } else {
            c.v(2, 0);
            if (best < 7 * 7 && i < n - 1) {
                double len = Math.max(0.5, a.distanceTo(b));
                f += (int) Math.max(1, Math.round(speed / len * 100));
                if (f >= 100) {
                    f = 0;
                    i++;
                    c.v(0, i);
                    if (note != null) c.sound(c.pos(way, i), note, 1.0F, 0.8F + 0.06F * i);
                    if (i == n - 1) {
                        c.sound(c.pos(way, i), SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 1.2F);
                        c.v(1, 0);
                        c.complete(nearest);
                        return;
                    }
                }
                c.v(1, f);
                pos = at(c, i).lerp(at(c, i + 1), f / 100.0);
            }
        }
        if (role.isEmpty()) {
            double bob = Math.sin(c.now() * 0.15) * 0.25 + 0.9;
            c.level.sendParticles(look, pos.x, pos.y + bob, pos.z, 2, 0.06, 0.06, 0.06, 0.002);
        } else if (c.now() % 10 == 0) {
            for (Entity e : c.life(role)) if (e instanceof Mob m) m.getNavigation().moveTo(pos.x, pos.y, pos.z, 1.1);
        }
    }
}
