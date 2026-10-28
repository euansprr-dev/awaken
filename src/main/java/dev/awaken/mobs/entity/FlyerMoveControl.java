package dev.awaken.mobs.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;

/**
 * Straight-line flight for fliers (the Vex approach): steer the velocity towards the wanted point, no 3D pathfinding.
 * Cheap enough for a room full of wisps, and fliers in gates fly in open rooms anyway.
 */
final class FlyerMoveControl extends MoveControl<AwakenMob> {
    FlyerMoveControl(AwakenMob mob) {
        super(mob);
    }

    @Override
    public void setWantedPosition(double x, double y, double z, double speedModifier) {
        // A dive or orbit can point beyond a legal target, especially uphill. Never steer a Lord out of its own home.
        if (mob instanceof AwakenBoss && mob.hasHome()) {
            var home = mob.getHomePosition();
            double hx = home.getX() + 0.5, hy = home.getY(), hz = home.getZ() + 0.5;
            double dx = x - hx, dy = y - hy, dz = z - hz;
            double distance = dx * dx + dy * dy + dz * dz, radius = mob.getHomeRadius();
            if (distance > radius * radius && distance > 0) {
                double scale = radius / Math.sqrt(distance);
                x = hx + dx * scale;
                y = hy + dy * scale;
                z = hz + dz * scale;
            }
        }
        super.setWantedPosition(x, y, z, speedModifier);
    }

    @Override
    public void tick() {
        if (mob.grounded()) {
            // brought down (SPEC §40): gravity has it until it rises again
            operation = Operation.WAIT;
            return;
        }
        if (operation != Operation.MOVE_TO) {
            // Hover: bleed off speed and bob a little so fliers don't look frozen.
            Vec3 v = mob.getDeltaMovement();
            mob.setDeltaMovement(v.x * 0.8, v.y * 0.8 + Mth.sin(mob.tickCount * 0.15F) * 0.004, v.z * 0.8);
            return;
        }
        Vec3 delta = new Vec3(wantedX - mob.getX(), wantedY - mob.getY(), wantedZ - mob.getZ());
        double len = delta.length();
        if (len < Math.max(0.5, mob.getBoundingBox().getSize() * 0.5)) {
            operation = Operation.WAIT;
            mob.setDeltaMovement(mob.getDeltaMovement().scale(0.5));
            return;
        }
        double speed = mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * speedModifier * 0.16;
        mob.setDeltaMovement(mob.getDeltaMovement().add(delta.scale(speed / len)));
        LivingEntity target = mob.getTarget();
        double fx = target != null ? target.getX() - mob.getX() : mob.getDeltaMovement().x;
        double fz = target != null ? target.getZ() - mob.getZ() : mob.getDeltaMovement().z;
        if (fx * fx + fz * fz > 1.0E-4) {
            float yaw = (float) (Mth.atan2(fz, fx) * Mth.RAD_TO_DEG) - 90.0F;
            mob.setYRot(rotlerp(mob.getYRot(), yaw, 20.0F));
            mob.yBodyRot = mob.getYRot();
        }
    }
}
