package dev.awaken.atlas.client;

import dev.awaken.Awaken;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jspecify.annotations.Nullable;

/**
 * The map scene's camera (SPEC §30.5), as the opening's (spike S2, {@code OpeningCamera}): a client-only marker entity,
 * never added to the level, that the game's camera follows through {@code Minecraft.setCameraEntity}. {@link MapScene}
 * places it every frame from {@code ComputeCameraAngles} at LOWEST priority, so the move is as smooth as the frame rate.
 * While it holds the view, you are seen in third person (the game draws the player when the camera is another entity),
 * the hands, the block outline, your name tag and the HUD stand aside. {@link #finish} is the one way back.
 */
final class MapCamera {
    private MapCamera() {}

    private static @Nullable Cam cam;
    private static @Nullable CameraType before;
    private static final double[] P = new double[5];

    static boolean active() {
        return cam != null;
    }

    /** Takes the view; false (nothing changed) when the level isn't there or it fails. */
    static boolean take() {
        Minecraft mc = Minecraft.getInstance();
        var p = mc.player;
        if (p == null || mc.level == null) return false;
        if (active()) return true;
        try {
            cam = new Cam(mc.level, p.getEyeHeight());
            MapScene.pose(0f, P);
            cam.place(P[0], P[1], P[2], (float) P[3], (float) P[4]);
            before = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.setCameraEntity(cam);
            return true;
        } catch (RuntimeException e) {
            Awaken.LOG.error("[atlas] map camera failed to start", e);
            finish();
            return false;
        }
    }

    /** Gives the view back to your own eyes. */
    static void finish() {
        Minecraft mc = Minecraft.getInstance();
        Cam c = cam;
        try {
            if (c != null && mc.getCameraEntity() == c && mc.player != null) mc.setCameraEntity(mc.player);
            if (before != null && c != null) mc.options.setCameraType(before);
        } catch (RuntimeException e) {
            Awaken.LOG.error("[atlas] map camera cleanup", e);
        } finally {
            cam = null;
            before = null;
        }
    }

    /** Logout: the level and the camera are going away anyway. */
    static void reset() {
        cam = null;
        before = null;
    }

    /** Once a tick: hand the view back if something else took it, or the level changed under it. */
    static boolean held() {
        Minecraft mc = Minecraft.getInstance();
        Cam c = cam;
        if (c == null) return false;
        if (mc.player == null || mc.level == null || c.level() != mc.level) {
            reset();
            return false;
        }
        if (mc.getCameraEntity() != c) {
            finish();
            return false;
        }
        // F5 while the scene holds the view would orbit the camera entity
        if (!mc.options.getCameraType().isFirstPerson()) mc.options.setCameraType(CameraType.FIRST_PERSON);
        return true;
    }

    // ---- every frame ------------------------------------------------------------------------------------------------

    static void angles(ViewportEvent.ComputeCameraAngles e) {
        Cam c = cam;
        if (c == null || e.getCamera().entity() != c) return;
        try {
            MapScene.pose((float) e.getPartialTick(), P);
            c.place(P[0], P[1], P[2], (float) P[3], (float) P[4]);
            e.setYaw((float) P[3]);
            e.setPitch((float) P[4]);
            e.setRoll(0f);
        } catch (RuntimeException ex) {
            Awaken.LOG.error("[atlas] map camera frame failed; giving the view back", ex);
            MapScene.abandon();
        }
    }

    static void hand(RenderHandEvent e) {
        if (active()) e.setCanceled(true);
    }

    static void outline(ExtractBlockOutlineRenderStateEvent e) {
        if (active()) e.setCanceled(true);
    }

    static void nameTag(RenderNameTagEvent.CanRender e) {
        if (active() && e.getEntity() == Minecraft.getInstance().player) e.setCanRender(net.minecraft.util.TriState.FALSE);
    }

    /** The HUD steps aside for the scene (chat, subtitles and the action bar stay: the world goes on). */
    static void hud(RenderGuiLayerEvent.Pre e) {
        if (!active()) return;
        String path = e.getName().getPath();
        if (path.equals("chat") || path.equals("subtitle_overlay") || path.equals("overlay_message")) return;
        e.setCanceled(true);
    }

    /** The camera body: never ticked, rendered, saved or sent; its eye height is the player's. */
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
