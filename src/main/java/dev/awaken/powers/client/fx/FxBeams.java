package dev.awaken.powers.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Beams: three camera-facing layers (a wide faint halo, the coloured body, a thin white core) that breathe, with
 * glows at both ends. The caster's own channelled beam follows their live aim: the hit distance is traced once a
 * tick, the direction every frame from the camera, so the beam never lags behind the crosshair.
 */
final class FxBeams {
    private FxBeams() {}

    /** Shape fields for beams: a = width, b = traced length of an aim beam, c = time offset, c0 = body, c1 = core. */
    static void draw(VertexConsumer vc, Shape sh, float fade) {
        float ax = (float) (sh.rx - FxRender.camX()), ay = (float) (sh.ry - FxRender.camY()), az = (float) (sh.rz - FxRender.camZ());
        float bx = (float) (sh.rex - FxRender.camX()), by = (float) (sh.rey - FxRender.camY()), bz = (float) (sh.rez - FxRender.camZ());
        float tt = sh.age + FxRender.pt;
        float breathe = 1 + 0.12f * Mth.sin(tt * 1.7f + sh.c) + 0.06f * Mth.sin(tt * 4.3f);
        if ((sh.flags & Shape.FLICKER) != 0) breathe *= 0.8f + 0.4f * FxRender.flick(sh.seed, sh.age);
        float w = sh.a * breathe;
        int body = sh.c0, core = sh.c1 == 0 ? 0xFFFFFFFF : sh.c1;
        // the halo stays faint so day scenes keep their colour; the body carries the family colour
        FxRender.segment(vc, ax, ay, az, bx, by, bz, w * 3.2f, w * 3.6f, FxWorld.alpha(body, 0.35f * fade), FxWorld.alpha(body, 0.28f * fade));
        // the body and core leave the hand visibly even in first person; only the wide halo fades near the camera
        FxRender.keepNear = true;
        try {
            FxRender.segment(vc, ax, ay, az, bx, by, bz, w, w * 1.1f, FxWorld.alpha(body, fade), FxWorld.alpha(body, 0.9f * fade));
            FxRender.segment(vc, ax, ay, az, bx, by, bz, w * 0.34f, w * 0.38f, FxWorld.alpha(core, fade), FxWorld.alpha(core, 0.9f * fade));
        } finally {
            FxRender.keepNear = false;
        }
        if (sh.helix > 0) FxLadderDraw.helix(vc, sh, fade, ax, ay, az, bx, by, bz, tt);
    }

    /** Per tick: how far the local player's aim beam reaches (blocks, then living things). */
    static void tickAim(Shape sh, LocalPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1f);
        double range = Math.max(1, sh.range);
        Vec3 far = eye.add(look.scale(range));
        BlockHitResult hit = p.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 to = hit.getType() == HitResult.Type.MISS ? far : hit.getLocation();
        AABB box = p.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        double best = eye.distanceTo(to);
        for (Entity en : p.level().getEntities(p, box, x -> x instanceof LivingEntity && x.isAlive())) {
            Optional<Vec3> c = en.getBoundingBox().inflate(0.3).clip(eye, to);
            if (c.isPresent()) best = Math.min(best, eye.distanceTo(c.get()));
        }
        sh.b = (float) best;
    }

    /** Per frame: the end of the local player's aim beam along the live view direction. */
    static void aim(Shape sh, Minecraft mc, float pt) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        float yaw = p.getViewYRot(pt) * Mth.DEG_TO_RAD, pitch = p.getViewXRot(pt) * Mth.DEG_TO_RAD;
        float cp = Mth.cos(pitch);
        double lx = -Mth.sin(yaw) * cp, ly = -Mth.sin(pitch), lz = Mth.cos(yaw) * cp;
        double ex = Mth.lerp(pt, p.xo, p.getX()), ey = Mth.lerp(pt, p.yo, p.getY()) + p.getEyeHeight(), ez = Mth.lerp(pt, p.zo, p.getZ());
        double len = sh.b > 0 ? sh.b : sh.range;
        sh.ex = ex + lx * len;
        sh.ey = ey + ly * len;
        sh.ez = ez + lz * len;
    }
}
