package dev.awaken.arms.anvil.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The smith's view at the War Anvil (SPEC §46.3): while you forge, the view leaves your eyes for a place over the work,
 * as a smith bends over the anvil, looking down on the metal so your hands come up from the bottom of the frame rather
 * than across it. A client-only camera entity (as the armour forge's), placed every frame by the scene, gliding between
 * each look's place; your own body is hidden while it's the view and the real player never moves. A place inside a
 * block, or one your eyes can't see, leaves that look at your eyes.
 */
final class AnvilCamera {
    private AnvilCamera() {}

    /** Each look's place, in the anvil's own blocks (the smith's side is -z): the Forge, the work, the trough, the slot. */
    private static final float[][] PLACES = {{0.5F, 1.7F, -0.35F}, {0.5F, 1.75F, 0.05F}, {0.62F, 1.75F, 0.05F}, {0.5F, 1.7F, -0.4F}};

    private static @Nullable Cam cam;
    private static @Nullable CameraType before;
    private static final Vector3f P = new Vector3f();
    /** Where this look's place is (world) and whether the view can be there. */
    private static final double[] TO = new double[3];
    private static boolean toOk;

    static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return cam != null && mc.getCameraEntity() == cam;
    }

    /** The view is over the work, not at your eyes. */
    static boolean away() {
        return toOk && active();
    }

    /** The view leaves your eyes (it starts where they are). */
    static void begin() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || active()) return;
        cam = new Cam(mc.level, p.getEyeHeight());
        before = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        Vec3 eye = p.getEyePosition(1F);
        cam.place(eye.x, eye.y, eye.z, p.getYRot(), p.getXRot());
        mc.setCameraEntity(cam);
    }

    static void end() {
        Minecraft mc = Minecraft.getInstance();
        if (cam != null && mc.getCameraEntity() == cam && mc.player != null) mc.setCameraEntity(mc.player);
        if (cam != null && before != null) mc.options.setCameraType(before);
        cam = null;
        before = null;
    }

    /** A new look: where its place is, and whether the view can go there (read once, as the look changes). */
    static void aim(@Nullable Level level, BlockPos pos, Quaternionf frame, int kind) {
        float[] at = PLACES[Math.max(0, Math.min(PLACES.length - 1, kind))];
        AnvilScene.model(frame, at[0], at[1], at[2], P);
        TO[0] = pos.getX() + P.x;
        TO[1] = pos.getY() + P.y;
        TO[2] = pos.getZ() + P.z;
        LocalPlayer p = Minecraft.getInstance().player;
        toOk = level != null && p != null && clear(level, p, TO[0], TO[1], TO[2]);
    }

    /** The eye this frame, {@code k} of the way from {@code from} to the look's place (or your eyes): out x, y, z. */
    static void eye(double[] from, float k, float partialTick, double[] out) {
        LocalPlayer p = Minecraft.getInstance().player;
        double tx = TO[0], ty = TO[1], tz = TO[2];
        if (!toOk && p != null) {
            Vec3 e = p.getEyePosition(partialTick);
            tx = e.x;
            ty = e.y;
            tz = e.z;
        }
        out[0] = from[0] + (tx - from[0]) * k;
        out[1] = from[1] + (ty - from[1]) * k;
        out[2] = from[2] + (tz - from[2]) * k;
    }

    /** Puts the view there for the next frame (the camera entity is read before this frame's angles). */
    static void place(double[] eye, float yaw, float pitch) {
        if (active() && cam != null) cam.place(eye[0], eye[1], eye[2], yaw, pitch);
    }

    static void player(RenderPlayerEvent.Pre<?> e) {
        Minecraft mc = Minecraft.getInstance();
        if (active() && mc.player != null && e.getRenderState().id == mc.player.getId()) e.setCanceled(true);
    }

    /** The place is open air, and the view can glide there from your eyes without its near plane crossing a block. */
    private static boolean clear(Level level, LocalPlayer p, double x, double y, double z) {
        BlockPos b = BlockPos.containing(x, y, z);
        if (!level.getBlockState(b).getCollisionShape(level, b).isEmpty()) return false;
        Vec3 from = p.getEyePosition(1F), to = new Vec3(x, y, z);
        for (int i = 0; i < 8; i++) {
            double dx = (i & 1) == 0 ? -0.12 : 0.12, dy = (i & 2) == 0 ? -0.12 : 0.12, dz = (i & 4) == 0 ? -0.12 : 0.12;
            var hit = level.clip(new ClipContext(from.add(dx, dy, dz), to.add(dx, dy, dz), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (hit.getType() != HitResult.Type.MISS) return false;
        }
        return true;
    }

    /** The camera entity (never added to the level). */
    private static final class Cam extends Entity {
        private final EntityDimensions dims;

        Cam(ClientLevel level, float eyeHeight) {
            super(EntityTypes.MARKER, level);
            this.dims = EntityDimensions.fixed(0F, 0F).withEyeHeight(eyeHeight);
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
            xo = xOld = x;
            yo = yOld = fy;
            zo = zOld = z;
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
