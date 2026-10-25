package dev.awaken.opening.client;

import dev.awaken.Awaken;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.ClientInput;
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
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jspecify.annotations.Nullable;

/**
 * The opening's one camera (SPEC §16.4, after spike S2): a client-only entity on the marker type, never added to the
 * level, carrying the player's eye height, that the game's camera follows through {@code Minecraft.setCameraEntity}. A
 * {@link Shot} places it every frame from {@code ComputeCameraAngles} at LOWEST priority (Camera.alignWithEntity fires
 * the event before it reads the entity), so the flight is as smooth as the frame rate. While a shot runs the hands, the
 * block outline, your own name tag and the interaction keys are held, the mouse doesn't turn you and your movement keys
 * are swapped for an empty input. {@link #finish} is the one way back: on completion, a skip, the camera taken
 * (respawn, dimension change, spectating), logout, a clone or any exception.
 */
public final class OpeningCamera {
    private OpeningCamera() {}

    /** A camera move: where the eyes are and where they look at time t (ticks since the shot began). */
    public interface Shot {
        /** out: x, y, z (the eyes), yaw, pitch, roll, fov scale. */
        void pose(float t, double[] out);

        /** Ticks the shot lasts (it finishes itself then), or -1 while it waits for something. */
        int length();

        /** Once a tick (age in ticks). */
        default void tick(int age) {}

        /** The shot ended: why ("done", "skip", "taken", "logout", "error", "replaced"). */
        default void finished(String reason) {}

        /** The camera type to leave the player in when the shot completes (null: as it was before). */
        default @Nullable CameraType landIn() {
            return null;
        }

        /** Hide the player's own body (a first-person landing comes down into the head). */
        default boolean hideBody(double camDistSq) {
            return false;
        }
    }

    private static @Nullable Cam cam;
    private static @Nullable Shot shot;
    private static int age;
    private static @Nullable CameraType before;
    private static @Nullable ClientInput heldInput;
    private static @Nullable LocalPlayer heldPlayer;
    private static final double[] P = new double[7];
    private static float fovScale = 1F;

    public static boolean active() {
        return cam != null;
    }

    public static @Nullable Shot shot() {
        return shot;
    }

    public static int age() {
        return age;
    }

    /** Jumps the running shot to a tick (a skip). */
    static void seek(int to) {
        if (active()) age = Math.max(0, to);
    }

    /** Starts a shot from its tick {@code from} (a relog resumes mid-flight); a running shot is replaced. */
    public static void take(Shot s, int from) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        if (active()) finish("replaced");
        try {
            shot = s;
            age = Math.max(0, from);
            cam = new Cam(mc.level, p.getEyeHeight());
            s.pose(age, P);
            cam.place(P[0], P[1], P[2], (float) P[3], (float) P[4]);
            before = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.setCameraEntity(cam);
            hold(p);
        } catch (RuntimeException e) {
            Awaken.LOG.error("[opening] camera failed to start", e);
            finish("error");
        }
    }

    private static void hold(LocalPlayer p) {
        if (heldInput != null) return;
        heldInput = p.input;
        heldPlayer = p;
        p.input = new ClientInput();
    }

    private static void release() {
        if (heldInput != null && heldPlayer != null && heldPlayer.input != heldInput) heldPlayer.input = heldInput;
        heldInput = null;
        heldPlayer = null;
    }

    /** The one way back to the player's own eyes. */
    public static void finish(String reason) {
        Minecraft mc = Minecraft.getInstance();
        Shot s = shot;
        Cam c = cam;
        try {
            LocalPlayer p = mc.player;
            if (c != null && mc.getCameraEntity() == c && p != null) mc.setCameraEntity(p);
            CameraType land = s == null ? null : s.landIn();
            if (reason.equals("done") && land != null) mc.options.setCameraType(land);
            else if (before != null && c != null) mc.options.setCameraType(before);
        } catch (RuntimeException e) {
            Awaken.LOG.error("[opening] camera cleanup", e);
        } finally {
            release();
            cam = null;
            shot = null;
            before = null;
            fovScale = 1F;
        }
        if (s != null) {
            try {
                s.finished(reason);
            } catch (RuntimeException e) {
                Awaken.LOG.error("[opening] shot end", e);
            }
        }
    }

    /** Disconnecting: the level and the camera are going away anyway. */
    static void reset() {
        Shot s = shot;
        release();
        cam = null;
        shot = null;
        before = null;
        fovScale = 1F;
        if (s != null) s.finished("logout");
    }

    static void tick() {
        Cam c = cam;
        Shot s = shot;
        if (c == null || s == null) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            LocalPlayer p = mc.player;
            if (p == null || mc.level == null || c.level() != mc.level) {
                reset();
                return;
            }
            if (mc.getCameraEntity() != c) {
                finish("taken");
                return;
            }
            if (heldPlayer != p) {
                // a respawn or clone gave us a new player object: hold its input too
                release();
                hold(p);
            }
            if (mc.isPaused()) return;
            // F5 mid-shot would orbit the camera entity
            if (!mc.options.getCameraType().isFirstPerson()) mc.options.setCameraType(CameraType.FIRST_PERSON);
            age++;
            s.tick(age);
            if (shot == s && s.length() >= 0 && age >= s.length()) finish("done");
        } catch (RuntimeException e) {
            Awaken.LOG.error("[opening] camera tick failed; giving the view back", e);
            finish("error");
        }
    }

    // ---- every frame ------------------------------------------------------------------------------------------------

    static void angles(ViewportEvent.ComputeCameraAngles e) {
        Cam c = cam;
        Shot s = shot;
        if (c == null || s == null || e.getCamera().entity() != c) return;
        try {
            float pt = Minecraft.getInstance().isPaused() ? 0 : e.getPartialTick();
            s.pose(age + pt, P);
            c.place(P[0], P[1], P[2], (float) P[3], (float) P[4]);
            e.setYaw((float) P[3]);
            e.setPitch((float) P[4]);
            e.setRoll((float) P[5]);
            fovScale = (float) P[6];
        } catch (RuntimeException ex) {
            Awaken.LOG.error("[opening] camera frame failed; giving the view back", ex);
            finish("error");
        }
    }

    static void fov(ViewportEvent.ComputeFov e) {
        if (cam == null || e.getCamera().entity() != cam) return;
        e.setFOV(e.getFOV() * fovScale);
    }

    static void hand(RenderHandEvent e) {
        if (active()) e.setCanceled(true);
    }

    static void player(RenderPlayerEvent.Pre<?> e) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.player == null || e.getRenderState().id != mc.player.getId() || shot == null || cam == null) return;
        double dx = cam.getX() - mc.player.getX(), dy = cam.getEyeY() - mc.player.getEyeY(), dz = cam.getZ() - mc.player.getZ();
        if (shot.hideBody(dx * dx + dy * dy + dz * dz)) e.setCanceled(true);
    }

    static void nameTag(RenderNameTagEvent.CanRender e) {
        if (active() && e.getEntity() == Minecraft.getInstance().player) e.setCanRender(net.minecraft.util.TriState.FALSE);
    }

    static void outline(ExtractBlockOutlineRenderStateEvent e) {
        if (active()) e.setCanceled(true);
    }

    static void interact(InputEvent.InteractionKeyMappingTriggered e) {
        if (!active()) return;
        e.setSwingHand(false);
        e.setCanceled(true);
    }

    static void turn(CalculatePlayerTurnEvent e) {
        // MouseHandler.turnPlayer: (s * 0.6 + 0.2)^3; s = -1/3 makes it zero (there is no "don't turn" switch)
        if (active() || OpeningClient.holdsMouse()) e.setMouseSensitivity(-1.0 / 3.0);
    }

    /** Where the camera's eyes are this frame (tours and the HUD), or null. */
    public static double @Nullable [] lastPose() {
        return active() ? P : null;
    }

    /** The camera body: never ticked, rendered, saved or sent; its eye height is the player's (no ease on hand-off). */
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
