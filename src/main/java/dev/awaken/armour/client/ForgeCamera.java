package dev.awaken.armour.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jspecify.annotations.Nullable;

/**
 * The Forge Song's cinematic camera (SPEC §44.4): wide on the Forge, over the shoulder at the fire, close on the metal for
 * each strike, low for the quench, a turn round the stand for the fit. A client-only camera entity the view follows (as
 * the opening's does), placed every frame; each new shot blends in from the last. Your hands, the block outline and your
 * own body are hidden while it runs; the real player never moves. It also projects points of the world onto the screen
 * for the marks, with the same pose and field of view it sets.
 */
final class ForgeCamera {
    private ForgeCamera() {}

    /** A shot: where the eye is, where it looks and its field of view at t ticks since the shot began. */
    interface Shot {
        void pose(float t, double[] out); // x, y, z, yaw, pitch, fov
    }

    private static @Nullable Cam cam;
    private static @Nullable CameraType before;
    private static @Nullable Shot shot, last;
    private static float shotAt, lastAt, blend = 1;
    private static final double[] A = new double[6], B = new double[6];
    /** The pose this frame (for the marks' projection). */
    static final double[] NOW = new double[6];

    static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return cam != null && mc.getCameraEntity() == cam;
    }

    /** Cuts (blend 0) or eases (blend ticks) to a new shot. */
    static void cut(Shot s, float now, float blendTicks) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        if (cam == null || !active()) {
            cam = new Cam(mc.level, p.getEyeHeight());
            before = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            Vec3 eye = p.getEyePosition(1);
            NOW[0] = eye.x;
            NOW[1] = eye.y;
            NOW[2] = eye.z;
            NOW[3] = p.getYRot();
            NOW[4] = p.getXRot();
            NOW[5] = mc.options.fov().get();
            cam.place(NOW[0], NOW[1], NOW[2], (float) NOW[3], (float) NOW[4]);
            mc.setCameraEntity(cam);
            last = frozen(NOW.clone());
        } else {
            last = frozen(NOW.clone());
        }
        lastAt = now;
        shot = s;
        shotAt = now;
        blend = Math.max(0.01F, blendTicks);
    }

    private static Shot frozen(double[] pose) {
        return (t, out) -> System.arraycopy(pose, 0, out, 0, 6);
    }

    static void end() {
        Minecraft mc = Minecraft.getInstance();
        if (cam != null && mc.getCameraEntity() == cam && mc.player != null) mc.setCameraEntity(mc.player);
        if (cam != null && before != null) mc.options.setCameraType(before);
        cam = null;
        before = null;
        shot = null;
        last = null;
    }

    static void angles(ViewportEvent.ComputeCameraAngles e, float now) {
        Cam c = cam;
        Shot s = shot;
        if (c == null || s == null || e.getCamera().entity() != c) return;
        s.pose(now - shotAt, A);
        float w = Mth.clamp((now - shotAt) / blend, 0F, 1F);
        w = w * w * (3 - 2 * w);
        if (w < 1F && last != null) {
            last.pose(now - lastAt, B);
            for (int i = 0; i < 6; i++) NOW[i] = i == 3 ? B[3] + Mth.wrapDegrees(A[3] - B[3]) * w : Mth.lerp(w, B[i], A[i]);
        } else System.arraycopy(A, 0, NOW, 0, 6);
        c.place(NOW[0], NOW[1], NOW[2], (float) NOW[3], (float) NOW[4]);
        e.setYaw((float) NOW[3]);
        e.setPitch((float) NOW[4]);
        e.setRoll(0F);
    }

    static void fov(ViewportEvent.ComputeFov e) {
        if (cam != null && e.getCamera().entity() == cam) e.setFOV((float) NOW[5]);
    }

    static void hand(RenderHandEvent e) {
        if (active()) e.setCanceled(true);
    }

    static void outline(ExtractBlockOutlineRenderStateEvent e) {
        if (active()) e.setCanceled(true);
    }

    static void player(RenderPlayerEvent.Pre<?> e) {
        Minecraft mc = Minecraft.getInstance();
        if (active() && mc.player != null && e.getRenderState().id == mc.player.getId()) e.setCanceled(true);
    }

    // ---- shots ---------------------------------------------------------------------------------------------------------

    /** A fixed eye looking at a point. */
    static Shot look(Vec3 eye, Vec3 at, double fov) {
        double[] p = pose(eye, at, fov);
        return (t, out) -> System.arraycopy(p, 0, out, 0, 6);
    }

    /** A slow push toward a point (the eye drifts {@code drift} of the way in over {@code ticks}). */
    static Shot push(Vec3 eye, Vec3 at, double fov, double drift, float ticks) {
        return (t, out) -> {
            double k = Mth.clamp(t / ticks, 0F, 1F) * drift;
            System.arraycopy(pose(eye.lerp(at, k), at, fov), 0, out, 0, 6);
        };
    }

    /** A turn round a point at a radius and height, from one bearing to another (degrees) over {@code ticks}. */
    static Shot orbit(Vec3 centre, double radius, double up, double from, double to, float ticks, double fov) {
        return (t, out) -> {
            double k = Mth.clamp(t / ticks, 0F, 1F);
            k = k * k * (3 - 2 * k);
            double a = Math.toRadians(from + (to - from) * k);
            Vec3 eye = centre.add(Math.sin(a) * radius, up, Math.cos(a) * radius);
            System.arraycopy(pose(eye, centre, fov), 0, out, 0, 6);
        };
    }

    static double[] pose(Vec3 eye, Vec3 at, double fov) {
        double dx = at.x - eye.x, dy = at.y - eye.y, dz = at.z - eye.z;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        return new double[]{eye.x, eye.y, eye.z, yaw, pitch, fov};
    }

    /**
     * A world point on the screen (GUI pixels), with the pose this frame: false when behind the eye. {@code out[2]} is
     * pixels per block at that depth (to size a mark).
     */
    static boolean project(double x, double y, double z, int width, int height, float[] out) {
        double yaw = Math.toRadians(NOW[3]), pitch = Math.toRadians(NOW[4]);
        double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        // up = right × forward
        double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;
        double vx = x - NOW[0], vy = y - NOW[1], vz = z - NOW[2];
        double cz = vx * fx + vy * fy + vz * fz;
        if (cz < 0.05) return false;
        double cx = vx * rx + vz * rz, cy = vx * ux + vy * uy + vz * uz;
        double tan = Math.tan(Math.toRadians(NOW[5]) / 2);
        double aspect = width / (double) height;
        out[0] = (float) ((cx / (cz * tan * aspect)) * 0.5 + 0.5) * width;
        out[1] = (float) (0.5 - (cy / (cz * tan)) * 0.5) * height;
        out[2] = (float) (height / (2 * cz * tan));
        return true;
    }

    /** The camera entity (never added to the level). */
    static final class Cam extends Entity {
        private final EntityDimensions dims;

        Cam(ClientLevel level, float eyeHeight) {
            super(EntityTypes.MARKER, level);
            this.dims = EntityDimensions.fixed(0.0F, 0.0F).withEyeHeight(eyeHeight);
            this.noPhysics = true;
            refreshDimensions();
        }

        @Override
        public EntityDimensions getDimensions(Pose pose) {
            return dims != null ? dims : super.getDimensions(pose);
        }

        void place(double x, double y, double z, float yaw, float pitch) {
            double fy = y - getEyeHeight();
            setPosRaw(x, fy, z);
            xo = x;
            yo = fy;
            zo = z;
            xOld = x;
            yOld = fy;
            zOld = z;
            setYRot(yaw);
            setXRot(pitch);
            yRotO = yaw;
            xRotO = pitch;
        }

        @Override
        public void tick() {}

        @Override
        protected void defineSynchedData(SynchedEntityData.Builder entityData) {}

        @Override
        protected void readAdditionalSaveData(ValueInput input) {}

        @Override
        protected void addAdditionalSaveData(ValueOutput output) {}

        @Override
        public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
            return false;
        }

        @Override
        public boolean isPickable() {
            return false;
        }
    }
}
