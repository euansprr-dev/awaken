package dev.awaken.finds.verb;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sneak (SPEC §58.5): something asleep, and a thing you want near it. Inside its hearing, noise builds: running is
 * loud, walking is some, creeping is nearly none, and standing still lets it settle. When the noise tops out the
 * sleeper stirs: you're pushed back to the edge, unhurt, and it settles again. Take the prize to finish.
 * Numbers: 0 the noise, 0..100.
 */
public final class Quiet implements Script {
    private final String sleeper, prize;
    private final double radius;
    private final Map<UUID, Vec3> last = new HashMap<>();

    private Quiet(String sleeper, String prize, double radius) {
        this.sleeper = sleeper;
        this.prize = prize;
        this.radius = radius;
    }

    public static Quiet near(String sleeper, String prize, double radius) {
        return new Quiet(sleeper, prize, radius);
    }

    @Override
    public int every() {
        return 2;
    }

    @Override
    public void tick(Ctx c) {
        BlockPos s = c.pos(sleeper, 0);
        Vec3 mid = Vec3.atCenterOf(s);
        int noise = c.v(0);
        boolean anyone = false;
        for (ServerPlayer p : c.players(c.kind().reach() + 6)) {
            Vec3 now = p.position(), was = last.put(p.getUUID(), now);
            if (now.distanceToSqr(mid) > radius * radius || was == null) continue;
            anyone = true;
            double moved = now.subtract(was).horizontalDistance();
            if (moved > 0.5) noise += 14;            // a run or a jump
            else if (moved > 0.2) noise += p.isCrouching() ? 1 : 6;
            else if (moved > 0.02) noise += p.isCrouching() ? 0 : 2;
            if (!p.onGround() && !p.isInWater()) noise += 3;
        }
        if (!anyone || c.now() % 4 == 0) noise = Math.max(0, noise - 1);
        if (noise >= 100) {
            c.sound(s, SoundEvents.POLAR_BEAR_WARNING, 1.0F, 0.6F);
            c.puff(s.above(), ParticleTypes.ANGRY_VILLAGER, 3, 0.4, 0);
            for (ServerPlayer p : c.players(c.kind().reach() + 6)) {
                Vec3 away = p.position().subtract(mid);
                if (away.lengthSqr() > radius * radius) continue;
                away = away.horizontalDistanceSqr() < 0.01 ? new Vec3(1, 0, 0) : away.multiply(1, 0, 1).normalize();
                p.push(away.x * 1.4, 0.45, away.z * 1.4);
                p.hurtMarked = true;
            }
            noise = 0;
        } else if (noise > 55 && c.now() % 20 == 0) {
            c.sound(s, SoundEvents.POLAR_BEAR_WARNING, 0.25F, 0.5F);
            c.puff(s.above(), ParticleTypes.SMOKE, 2, 0.3, 0.01);
        } else if (c.now() % 60 == 0) c.puff(s.above(), ParticleTypes.POOF, 1, 0.2, 0.005); // breathing
        c.v(0, noise);
    }

    @Override
    public boolean use(Ctx c, ServerPlayer p, String name, int i, ItemStack held) {
        if (!name.equals(prize)) return false;
        c.complete(p);
        return true;
    }
}
