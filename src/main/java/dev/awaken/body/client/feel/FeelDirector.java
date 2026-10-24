package dev.awaken.body.client.feel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.Awaken;
import dev.awaken.body.ascent.AscentContent;
import dev.awaken.body.ascent.AscentState;
import dev.awaken.body.ascent.client.AscentMoves;
import dev.awaken.body.ascent.client.AscentSounds;
import dev.awaken.core.AwakenConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The Feel Director (SPEC §17): the one client voice for how moving through the air feels. It owns camera roll, FOV,
 * shake, the edge streaks and the boom's flash for every Ascent state, from one budget, and starts each on your input
 * (it reads the predicted state, never the server's answer). The wind lives in {@link AscentSounds}; the light in the
 * world (ropes, trails, the shock ring) in the Ascent's renderer.
 * <p>
 * Speed is felt in layers: the view widens, the wind climbs, streaks gather at the frame's edge, and at the very top
 * a faint tremor. Each moment has its own beat: a rope biting tugs the view, a slack rope snapping taut dips it, a
 * launch lifts it, a landing drops it. Comfort first: roll is capped (5° here; Phase 1 makes it a 0-7.5° setting), the
 * FOV push follows vanilla's FOV-effects slider, and shake follows Awaken's camera-shake setting.
 * <p>
 * Values are stepped once a tick and drawn between the last two ticks (partial ticks), so they move smoothly at any
 * frame rate without allocating.
 */
public final class FeelDirector {
    private FeelDirector() {}

    private static final Identifier LAYER = Awaken.id("ascent_feel");
    /** Camera Overhaul is in the pack: it pauses while an Ascent state owns the camera (decision 6). */
    private static final boolean CAMERA_OVERHAUL = net.neoforged.fml.ModList.get().isLoaded("cameraoverhaul");

    // stepped each tick: [previous, current]
    private static float roll0, roll1, pitch0, pitch1, fov0, fov1, streak0, streak1, tremor0, tremor1;
    // impulses (decaying each tick)
    private static float pitchKick, pitchKickV, fovPunch, flash, shake;
    private static int crackIn = -1;
    private static float lastYaw;
    private static float yawRate;

    public static void init(IEventBus modBus) {
        FeelConfig.register();
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, LAYER, FeelDirector::render));
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ViewportEvent.ComputeCameraAngles.class, FeelDirector::onAngles);
        bus.addListener(ComputeFovModifierEvent.class, FeelDirector::onFov);
        bus.addListener(RenderHandEvent.class, FeelDirector::onHand);
    }

    private static boolean shakeOn() {
        if (!FeelConfig.shake()) return false;
        try {
            return AwakenConfig.Client.CAMERA_SHAKE.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    // ---- moments --------------------------------------------------------------------------------------------------

    /** The rope bit (a tug toward it). */
    public static void bite(float strength) {
        kick(0.9F * strength);
    }

    /** A slack rope snapped taut under you: the view dips with the weight, the frame squeezes a touch. */
    public static void ropeCatch(float strength) {
        kick(3.2F * strength);
        fovPunch -= 0.035F * strength;
        shake = Math.max(shake, 0.25F * strength);
    }

    public static void release(float quality) {
        kick(-1.2F - 1.5F * quality);
        fovPunch += 0.03F + 0.04F * quality;
    }

    public static void launch(float quality) {
        kick(-3.5F);
        fovPunch += 0.07F + 0.03F * quality;
    }

    public static void deploy() {
        kick(-2.2F);
        fovPunch += 0.045F;
    }

    public static void flap() {
        kick(-1.4F);
    }

    /** The sonic boom: the frame flashes and widens, the wind cuts out, then the crack and the shake. */
    public static void boom() {
        if (!FeelConfig.reduced()) flash = Math.max(flash, 0.55F);
        fovPunch += 0.16F;
        AscentSounds.duck(7);
        crackIn = 6;
    }

    /** "Your body remembers": the view lifts and the frame breathes out as the wings open. */
    public static void remember() {
        kick(-2.6F);
        if (!FeelConfig.reduced()) fovPunch += 0.07F;
    }

    /** An air dash: a sharp shove forward (the frame widens, the view dips into it). */
    public static void dash() {
        kick(1.6F);
        fovPunch += 0.09F;
    }

    /** A dive slam starting: the view tips down as you fold. */
    public static void dive() {
        kick(4F);
        fovPunch += 0.05F;
    }

    /** A dive slam's landing: the heaviest landing there is. */
    public static void slam(float speed) {
        kick(Math.min(9F, 4F + speed * 3F));
        shake = Math.max(shake, Math.min(1.6F, 0.8F + speed * 0.4F));
        if (!FeelConfig.reduced()) flash = Math.max(flash, 0.12F);
    }

    public static void land(float speed) {
        kick(Math.min(7F, 2.5F + speed * 3.5F));
        if (speed > 0.8F) shake = Math.max(shake, Math.min(0.9F, speed * 0.4F));
    }

    public static void bonk(float lost) {
        kick(Math.min(6F, lost * 5F));
        shake = Math.max(shake, Math.min(1F, lost * 0.8F));
        fovPunch -= 0.04F;
    }

    private static void kick(float degrees) {
        // a spring: the view is shoved, overshoots a little and settles
        pitchKickV += degrees * 0.55F;
    }

    // ---- ticking ------------------------------------------------------------------------------------------------------

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        roll0 = roll1;
        pitch0 = pitch1;
        fov0 = fov1;
        streak0 = streak1;
        tremor0 = tremor1;
        float yaw = p.getYRot();
        float dy = Mth.wrapDegrees(yaw - lastYaw);
        lastYaw = yaw;
        yawRate += (dy - yawRate) * 0.35F;
        AscentState st = AscentMoves.state();
        if (CAMERA_OVERHAUL) CameraOverhaulPause.hold(st.airborne());
        double s = AscentMoves.speed();
        float norm = (float) Mth.clamp((s - 0.4) / 2.0, 0, 1);
        float smooth = norm * norm * (3 - 2 * norm);
        // roll: banked into turns in the air, leaning with the rope on a swing
        float maxRoll = FeelConfig.roll();
        float target = 0;
        if (st == AscentState.TETHER) target = ropeLean(p) + Mth.clamp(yawRate * 0.6F, -2F, 2F);
        else if (st.airborne()) target = Mth.clamp(yawRate * 1.5F * (float) Math.min(1, s / 0.9), -maxRoll, maxRoll);
        target = Mth.clamp(target, -maxRoll, maxRoll);
        roll1 += (target - roll1) * 0.2F;
        // pitch kicks: a damped spring
        pitchKickV += -pitchKick * 0.28F;
        pitchKickV *= 0.62F;
        pitchKick += pitchKickV;
        pitch1 = pitchKick;
        // FOV: wider with speed, plus punches that fade
        fovPunch *= 0.84F;
        float wide = st.airborne() ? 0.17F * smooth * FeelConfig.speedFov() : 0;
        fov1 += (1 + wide + fovPunch - fov1) * 0.3F;
        boolean calm = FeelConfig.reduced();
        streak1 += ((st.airborne() && !calm ? (float) Mth.clamp((s - 1.0) / 1.4, 0, 1) : 0) - streak1) * 0.25F;
        tremor1 = st.airborne() && !calm ? (float) Mth.clamp((s - 1.7) / 1.0, 0, 1) : 0;
        flash *= 0.7F;
        shake *= 0.86F;
        if (crackIn >= 0 && crackIn-- == 0) {
            AscentSounds.local(p, AscentContent.BOOM.get(), 1F, 0.95F + p.getRandom().nextFloat() * 0.1F);
            shake = Math.max(shake, 1.4F);
            kick(2.5F);
        }
    }

    /** The rope's angle to the side of your view: the body hangs toward it, so the camera tilts with it (degrees). */
    private static float ropeLean(LocalPlayer p) {
        Vec3 hand = p.position().add(0, dev.awaken.body.ascent.AscentRules.HAND_HEIGHT, 0);
        double dx = AscentMoves.anchorX() - hand.x, dy = AscentMoves.anchorY() - hand.y, dz = AscentMoves.anchorZ() - hand.z;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 1e-3) return 0;
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        // your right, facing +z, is -x
        double rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);
        double side = (dx * rx + dz * rz) / d;
        return (float) (-side * 9.0);
    }

    public static void reset() {
        roll0 = roll1 = pitch0 = pitch1 = streak0 = streak1 = tremor0 = tremor1 = 0;
        fov0 = fov1 = 1;
        pitchKick = pitchKickV = fovPunch = flash = shake = 0;
        crackIn = -1;
    }

    private static float lerp(float pt, float a, float b) {
        return a + (b - a) * pt;
    }

    // ---- the camera ---------------------------------------------------------------------------------------------------

    static void onAngles(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float pt = (float) e.getPartialTick();
        boolean first = mc.options.getCameraType().isFirstPerson();
        float roll = lerp(pt, roll0, roll1) * (first ? 1F : 0.5F);
        float pitch = lerp(pt, pitch0, pitch1);
        float tremor = lerp(pt, tremor0, tremor1);
        float t = AscentMoves.clock() + pt;
        float yaw = 0;
        if (shakeOn()) {
            float amp = shake * 1.1F + tremor * 0.16F;
            if (amp > 1e-3F) {
                pitch += amp * (Mth.sin(t * 2.9F) * 0.6F + Mth.sin(t * 7.3F + 1.1F) * 0.4F);
                yaw += amp * 0.6F * Mth.sin(t * 3.7F + 2.3F);
                roll += amp * 0.5F * Mth.sin(t * 5.1F + 0.7F);
            }
        }
        if (pitch != 0) e.setPitch(e.getPitch() + pitch);
        if (yaw != 0) e.setYaw(e.getYaw() + yaw);
        if (roll != 0) e.setRoll(e.getRoll() + roll);
    }

    static void onFov(ComputeFovModifierEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.getPlayer() != mc.player) return;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float f = lerp(pt, fov0, fov1);
        if (Math.abs(f - 1) > 1e-4F) e.setNewFovModifier(e.getNewFovModifier() * (1 + (f - 1) * e.getFovScale()));
    }

    /** First person: the rope hand is raised toward the anchor while a rope is out. */
    static void onHand(RenderHandEvent e) {
        if (!AscentMoves.state().rope || e.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        float up = Math.min(1F, (AscentMoves.clock() - AscentMoves.fireTick() + e.getPartialTick()) / 3F);
        PoseStack ps = e.getPoseStack();
        ps.translate(0.05F * up, 0.32F * up, -0.12F * up);
        ps.rotateDegrees(Axis.XP, 58F * up);
        ps.rotateDegrees(Axis.ZP, -8F * up);
    }

    // ---- the frame ----------------------------------------------------------------------------------------------------

    private static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden()) return;
        float pt = dt.getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth(), h = g.guiHeight();
        if (flash > 0.01F) {
            int a = Mth.clamp((int) (flash * 255), 0, 255);
            g.fill(0, 0, w, h, (a << 24) | 0xF4F8FF);
        }
        float streak = lerp(pt, streak0, streak1);
        if (streak > 0.02F && mc.options.getCameraType().isFirstPerson()) streaks(g, w, h, streak, AscentMoves.clock() + pt);
    }

    /**
     * Light streaks at the frame's edge, flowing outward: thin, faint, many, never at the centre. They pull past you
     * the faster you go, and thicken with speed.
     */
    private static void streaks(GuiGraphicsExtractor g, int w, int h, float strength, float t) {
        int count = 10 + (int) (22 * strength);
        float cx = w / 2F, cy = h / 2F;
        float reach = (float) Math.sqrt(cx * cx + cy * cy);
        for (int i = 0; i < count; i++) {
            int hash = i * 0x9E3779B1;
            float ang = ((hash >>> 8) & 1023) / 1024F * Mth.TWO_PI;
            // each streak runs outward on a loop of its own length, so they stream rather than blink
            float period = 9F + ((hash >>> 20) & 7);
            float phase = ((t + ((hash >>> 3) & 63)) % period) / period;
            float inner = reach * (0.48F + 0.5F * phase);
            int len = (int) (reach * (0.06F + 0.16F * strength) * (0.6F + 0.4F * phase));
            float fade = Mth.sin(phase * Mth.PI);
            int a = (int) (70 * strength * fade);
            if (a < 3) continue;
            g.pose().pushMatrix();
            g.pose().translate(cx + Mth.cos(ang) * inner, cy + Mth.sin(ang) * inner);
            g.pose().rotate(ang);
            g.fill(0, 0, len, 1, (a << 24) | 0xE8F2FF);
            g.pose().popMatrix();
        }
    }
}
