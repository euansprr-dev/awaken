package dev.awaken.anims.client.station;

import dev.awaken.anims.station.StationView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * An unregistered client camera entity, like the existing cinematic camera; the real player never moves. It carries the
 * view to a station's own place: a furnace's mouth, a table's bench view (SPEC §24, §63.1).
 */
final class StationCamera extends Entity {
    private final EntityDimensions dimensions;
    private static StationCamera camera;
    private static Vec3 lowered;
    private static @org.jspecify.annotations.Nullable Vec3 start;
    private static final double[] EYE = new double[3];

    /** The lock's owner and failsafe (SPEC §27.6): you stand still while the view is at the station. */
    private static final Object HOLD = "station camera";
    private static final int HOLD_MAX = 20 * 600;

    private StationCamera(ClientLevel level, float eyeHeight) {
        super(EntityTypes.MARKER, level);
        dimensions = EntityDimensions.fixed(0, 0).withEyeHeight(eyeHeight);
        noPhysics = true;
        refreshDimensions();
    }

    /**
     * The view leaves your eyes for a place of the station's own (a furnace's mouth, a table's bench view, SPEC §63.1),
     * when the way there is clear. {@code from}: where the view is now when it isn't at your eyes (a third-person
     * camera), else null; the view glides from there and goes back there.
     */
    static boolean begin(LocalPlayer player, Vec3 target, @org.jspecify.annotations.Nullable Vec3 from) {
        Minecraft mc = Minecraft.getInstance();
        if (active()) end();
        if (mc.getCameraEntity() != player || mc.level == null) return false;
        Vec3 eye = player.getEyePosition(1);
        if (!StationView.clearEyePath(mc.level, player, eye, target)) return false;
        camera = new StationCamera(mc.level, player.getEyeHeight());
        lowered = target;
        start = from;
        camera.place(from != null ? from : eye, player.getYRot(), player.getXRot());
        mc.setCameraEntity(camera);
        dev.awaken.core.client.InputLock.hold(HOLD, HOLD_MAX);
        return true;
    }

    static Vec3 eye(LocalPlayer player) { return lowered == null ? player.getEyePosition(1) : lowered; }
    static boolean active() { return camera != null && Minecraft.getInstance().getCameraEntity() == camera; }
    static boolean lost() { return camera != null && (!active() || camera.level() != Minecraft.getInstance().level); }

    /**
     * Where the view is: {@code across} of the way to the station's place over the ground and {@code down} of the way
     * in height (a body goes down first and then in: SPEC §63.6), less {@code dip} (blocks: the settle as it arrives).
     */
    static void place(float across, float down, float partial, float yaw, float pitch, double dip) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.player == null || lowered == null) return;
        Vec3 real = start != null ? start : mc.player.getEyePosition(partial);
        camera.place(new Vec3(net.minecraft.util.Mth.lerp(across, real.x, lowered.x), net.minecraft.util.Mth.lerp(down, real.y, lowered.y) - dip,
                net.minecraft.util.Mth.lerp(across, real.z, lowered.z)), yaw, pitch);
    }

    /** A block now between your eyes and the lowered view (checked each tick, not each frame: eight ray casts). */
    static boolean blocked() {
        Minecraft mc = Minecraft.getInstance();
        return active() && mc.player != null && lowered != null && !StationView.clearEyePath(mc.level, mc.player, mc.player.getEyePosition(1), lowered);
    }

    static void end() {
        dev.awaken.core.client.InputLock.release(HOLD);
        Minecraft mc = Minecraft.getInstance();
        if (camera != null && mc.getCameraEntity() == camera && mc.player != null) mc.setCameraEntity(mc.player);
        camera = null;
        lowered = null;
        start = null;
    }

    private void place(Vec3 eye, float yaw, float pitch) {
        double feet = eye.y - getEyeHeight();
        setPosRaw(eye.x, feet, eye.z);
        xo = xOld = eye.x; yo = yOld = feet; zo = zOld = eye.z;
        setYRot(yaw); setXRot(pitch); yRotO = yaw; xRotO = pitch;
    }
    @Override public EntityDimensions getDimensions(Pose pose) { return dimensions == null ? super.getDimensions(pose) : dimensions; }
    @Override public void tick() {}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {}
    @Override protected void readAdditionalSaveData(ValueInput in) {}
    @Override protected void addAdditionalSaveData(ValueOutput out) {}
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) { return false; }
    @Override public boolean isPickable() { return false; }
}
