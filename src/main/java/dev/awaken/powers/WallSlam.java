package dev.awaken.powers;

import dev.awaken.powers.fx.FxKind;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A body thrown into a wall takes a second hit and cracks it (SPEC §50.3). Follows the body for a moment after a throw; only one
 * still travelling at speed counts, so a mob walking into a step is left alone. Never a player's body, never a boss's.
 */
public final class WallSlam {
    private WallSlam() {}

    private static final double MIN_SPEED = 0.32;

    public static void watch(Cast c, LivingEntity body, int ticks, float dmg, int stance, float crack, Holder<SoundEvent> sound) {
        if (body instanceof Player || Combat.isHeavy(body) || !body.isAlive()) return;
        Running r = Casting.start(c, c.type.id + "_slam_" + body.getId(), ticks);
        r.pos = body.getDeltaMovement();
        r.tick(rr -> {
            if (!body.isAlive() || body.isRemoved() || body.level() != c.level) {
                rr.stop();
                return;
            }
            Vec3 last = rr.pos;
            double speed = Math.sqrt(last.x * last.x + last.z * last.z);
            if (rr.age >= 1 && body.horizontalCollision && speed >= MIN_SPEED) {
                slam(c, body, last, speed, dmg, stance, crack, sound);
                rr.stop();
                return;
            }
            Vec3 now = body.getDeltaMovement();
            rr.pos = now;
            if (rr.age >= 4 && body.onGround() && now.x * now.x + now.z * now.z < 0.0064) rr.stop(); // it has come to rest
        });
    }

    private static void slam(Cast c, LivingEntity body, Vec3 last, double speed, float dmg, int stance, float crack, Holder<SoundEvent> sound) {
        Vec3 dir = new Vec3(last.x / speed, 0, last.z / speed);
        AABB box = body.getBoundingBox();
        double reach = box.getXsize() * 0.5 + 1.2;
        Vec3 mid = box.getCenter();
        BlockHitResult hit = ray(c, body, mid, dir, reach);
        if (hit.getType() == HitResult.Type.MISS) hit = ray(c, body, new Vec3(mid.x, box.minY + 0.4, mid.z), dir, reach); // a low wall
        boolean wall = hit.getType() != HitResult.Type.MISS;
        Vec3 at = wall ? hit.getLocation() : mid.add(dir.scale(box.getXsize() * 0.5));
        Vec3 out = wall ? Vec3.atLowerCornerOf(hit.getDirection().getUnitVec3i()) : dir.reverse();
        Combat.hurtFrom(c.player, null, body, dmg, null, true);
        PowerStance.add(c.player, body, stance);
        Combat.stun(body, 12);
        Combat.push(body, out.scale(0.16).add(0, 0.12, 0)); // it comes off the wall and drops
        Fx.at(c, FxKind.WALL_CRACK, at).dir(out).scale(crack).noAnim().send();
        c.voice(at, dev.awaken.powers.fx.Voice.Beat.EXTRA_A, sound, 1.15f, 0.92f + c.level.getRandom().nextFloat() * 0.14f);
        Fx.quake(c.level, at, 12, 1.4f * crack);
    }

    private static BlockHitResult ray(Cast c, LivingEntity body, Vec3 from, Vec3 dir, double reach) {
        return c.level.clip(new ClipContext(from, from.add(dir.scale(reach)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, body));
    }
}
