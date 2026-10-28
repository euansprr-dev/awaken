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

/** An unregistered client camera entity, like the existing cinematic camera; the real player never moves. */
final class StationCamera extends Entity {
    private final EntityDimensions dimensions;
    private static StationCamera camera;
    private static Vec3 lowered;
    private static final double[] EYE = new double[3];

    private StationCamera(ClientLevel level, float eyeHeight) {
        super(EntityTypes.MARKER, level);
        dimensions = EntityDimensions.fixed(0, 0).withEyeHeight(eyeHeight);
        noPhysics = true;
        refreshDimensions();
    }

    static boolean begin(LocalPlayer player, StationView.Station station) {
        Minecraft mc = Minecraft.getInstance();
        if (active()) end();
        if (mc.getCameraEntity() != player || mc.level == null) return false;
        Vec3 eye = player.getEyePosition(1);
        if (!StationView.furnaceEye(station, eye.x, eye.y, eye.z, EYE)) return false;
        Vec3 target = new Vec3(EYE[0], EYE[1], EYE[2]);
        if (!StationView.clearEyePath(mc.level, player, eye, target)) return false;
        camera = new StationCamera(mc.level, player.getEyeHeight());
        lowered = target;
        camera.place(eye, player.getYRot(), player.getXRot());
        mc.setCameraEntity(camera);
        return true;
    }

    static Vec3 eye(LocalPlayer player) { return lowered == null ? player.getEyePosition(1) : lowered; }
    static boolean active() { return camera != null && Minecraft.getInstance().getCameraEntity() == camera; }
    static boolean lost() { return camera != null && (!active() || camera.level() != Minecraft.getInstance().level); }

    static void place(float weight, float partial, float yaw, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.player == null || lowered == null) return;
        Vec3 real = mc.player.getEyePosition(partial);
        // A second player can place a block during the interaction. Give the view back instead of looking through it.
        if (!StationView.clearEyePath(mc.level, mc.player, real, lowered)) { end(); mc.player.closeContainer(); return; }
        camera.place(real.lerp(lowered, weight), yaw, pitch);
    }

    static void end() {
        Minecraft mc = Minecraft.getInstance();
        if (camera != null && mc.getCameraEntity() == camera && mc.player != null) mc.setCameraEntity(mc.player);
        camera = null;
        lowered = null;
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
