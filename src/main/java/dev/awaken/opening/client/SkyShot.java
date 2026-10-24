package dev.awaken.opening.client;

import dev.awaken.Awaken;
import dev.awaken.citizens.CitizensContent;
import dev.awaken.compat.client.FarTerrain;
import dev.awaken.lens.client.LensClient;
import dev.awaken.opening.net.OpeningPayloads;
import dev.awaken.story.RitualSounds;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jspecify.annotations.Nullable;

/**
 * The sky shot (SPEC §16.4): the card (black, one bell, CARILLON in the System's type with a flicker of [ERROR] you only
 * half see) while the flight warms up behind it, then the flight over the city at dawn under Lens's Carillon dawn look,
 * the wind and the district bells far below, into a landing behind your shoulder. The first time, two cards ask how
 * you'll see the city; first person pushes on into your eyes. After the first viewing, holding jump for a second skips
 * to the landing. The client tells the server when it has landed (the bell rings wrong then).
 */
final class SkyShot implements OpeningCamera.Shot {
    /** The card, the flight and the push into the eyes, in ticks (the server's OpeningDirector.CARD is the same). */
    static final int CARD = 90, FLIGHT = (int) Math.round(SkyPath.SECONDS * 20), PUSH = 16;
    /** Holding jump this long skips (after the first viewing). */
    static final int SKIP_HOLD = 20;

    static @Nullable SkyShot current;

    final SkyPath path;
    final Vec3 eye;
    final boolean canSkip, shots, ask;
    private final double[] s = new double[6], s2 = new double[6];
    private int jumpHeld, pushFrom = -1, endAt = -1;
    private boolean landFirst;
    private boolean landed, skipped, reported, asked;
    private OpeningSounds.@Nullable Bed wind;
    float bars, barsO;
    /** The black over the card (1) lifting into the sky (0); and a quick black for a skip. */
    float black = 1F, blackO = 1F;
    int skipFlash;

    private SkyShot(OpeningPayloads.Scene sc) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        eye = sc.eye();
        double dist = p == null ? 4.0 : thirdPersonDistance(mc, p, sc.yaw(), 0F);
        path = new SkyPath(sc.origin(), eye, sc.yaw(), 0F, dist);
        canSkip = sc.flag(OpeningPayloads.F_SEEN) || OpeningConfig.skySeen();
        shots = sc.flag(OpeningPayloads.F_SHOTS);
        ask = !sc.flag(OpeningPayloads.F_NO_QUESTIONS) && OpeningConfig.view().isEmpty();
    }

    /** The server's word: the sky from this age (a relog resumes mid-flight). */
    static void start(OpeningPayloads.Scene sc) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        // the vanilla tutorial's "Move with W, A, S and D" would sit over the whole flight: Carillon teaches by itself
        mc.getTutorial().setStep(TutorialSteps.NONE);
        p.setYRot(sc.yaw());
        p.setXRot(0F);
        p.yRotO = sc.yaw();
        p.xRotO = 0F;
        p.setYHeadRot(sc.yaw());
        SkyShot shot = new SkyShot(sc);
        current = shot;
        int from = Math.min(sc.age(), CARD + FLIGHT);
        if (from > 0) shot.black = shot.blackO = from < CARD ? 1F : 0F;
        OpeningCamera.take(shot, from);
        Awaken.LOG.info("[opening] sky shot from {} (skip {}, ask {})", from, shot.canSkip, shot.ask);
    }

    // ---- the timeline -----------------------------------------------------------------------------------------------

    @Override
    public int length() {
        return endAt;
    }

    @Override
    public void tick(int age) {
        Minecraft mc = Minecraft.getInstance();
        barsO = bars;
        blackO = black;
        if (skipFlash > 0) skipFlash--;
        // the card: one bell in the dark
        if (age == 6) {
            OpeningSounds.ui(CitizensContent.BELL_HOUR.get(), 0.71F, 1.0F);
            OpeningSounds.ui(RitualSounds.BELL.get(), 0.5F, 0.45F);
        }
        if (age == CARD - 14) {
            wind = OpeningSounds.bed(dev.awaken.body.ascent.AscentContent.WIND.get(), 0.55F, 0.8F, 50, 40);
            LensClient.override("awaken:carillon_dawn", 40);
        }
        // the morning peal far below: the district bells answering each other
        if (age == CARD + 120) bell(-150, 50, -62, 1.12F);
        if (age == CARD + 132) bell(-108, 44, 64, 0.94F);
        if (age == CARD + 146) bell(72, 52, -128, 1.26F);
        if (age == CARD + 162) bell(-150, 50, -62, 1.0F);
        // the wind lowers as the camera comes down to the roofs
        if (wind != null) wind.gain = age < CARD + 200 ? 1F : Math.max(0.35F, 1F - (age - CARD - 200) / 160F);
        // the black lifts as the flight begins; the letterbox closes in, and opens over the last second and a half
        black = age < CARD - 10 ? 1F : Math.max(0F, 1F - (age - (CARD - 10)) / 24F);
        float want = age >= CARD - 6 && age < CARD + FLIGHT - 30 ? 1F : 0F;
        bars += (want - bars) * (want > bars ? 0.16F : 0.1F);
        // hold jump to skip (after the first viewing)
        if (canSkip && !landed) {
            jumpHeld = mc.options.keyJump.isDown() ? jumpHeld + 1 : 0;
            if (jumpHeld >= SKIP_HOLD) {
                skipped = true;
                skipFlash = 12;
                OpeningCamera.seek(CARD + FLIGHT);
                Awaken.LOG.info("[opening] sky shot skipped at {}", age);
            }
        }
        if (shots) {
            if (age == CARD + 40) shot("m2_sky_a_top");
            else if (age == CARD + 140) shot("m2_sky_b_river");
            else if (age == CARD + 230) shot("m2_sky_c_market");
            else if (age == CARD + 300) shot("m2_sky_d_choir");
            else if (age == CARD + FLIGHT - 2) shot("m2_sky_e_handoff");
        }
        if (!landed && OpeningCamera.age() >= CARD + FLIGHT) land();
    }

    private void bell(double px, double ry, double pz, float pitch) {
        Vec3 o = OpeningClient.origin();
        OpeningSounds.at(CitizensContent.BELL_DISTRICT.get(), new Vec3(o.x + px, o.y + ry, o.z + pz), 6F, pitch);
    }

    /** At the end of the flight: ask once, or go on into the view already chosen. */
    private void land() {
        landed = true;
        if (wind != null) wind.end();
        String view = OpeningConfig.view();
        if (ask && !asked) {
            asked = true;
            Minecraft.getInstance().gui.setScreen(new ViewChoiceScreen(this::chosen));
            return;
        }
        chosen(view.equals("first") || view.isEmpty() && Minecraft.getInstance().options.getCameraType().isFirstPerson());
    }

    /** The view is chosen: first person pushes on into the eyes, third person stays over the shoulder. */
    void chosen(boolean first) {
        if (ask) OpeningConfig.view(first ? "first" : "third");
        landFirst = first;
        int now = OpeningCamera.age();
        if (first) pushFrom = now;
        endAt = now + (first ? PUSH : 1);
    }

    @Override
    public @Nullable CameraType landIn() {
        return landFirst ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_BACK;
    }

    @Override
    public void finished(String reason) {
        if (wind != null) wind.end();
        current = null;
        if (reason.equals("done")) {
            if (!skipped) OpeningConfig.skySeen(true);
            report(skipped ? OpeningPayloads.READY_SKY_SKIPPED : OpeningPayloads.READY_SKY);
            if (shots) OpeningClient.later(12, () -> shot("m2_sky_f_eyes"));
        } else if (reason.equals("taken") || reason.equals("error")) {
            report(OpeningPayloads.READY_SKY);
        }
    }

    private void report(int what) {
        if (reported) return;
        reported = true;
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new OpeningPayloads.Ready(what, 0));
    }

    // ---- the camera, every frame ------------------------------------------------------------------------------------

    @Override
    public void pose(float t, double[] out) {
        double seconds;
        if (t < CARD) {
            // behind the card: sweep the whole flight once so the city's meshes are built, then wait at its start
            double f = t / (CARD * 0.7);
            seconds = f < 1 ? f * SkyPath.SECONDS : 0;
        } else {
            seconds = Math.min(SkyPath.SECONDS, (t - CARD) / 20.0);
        }
        path.sample(seconds, s);
        double x = s[0], y = s[1], z = s[2];
        float yaw = yaw(s), pitch = pitch(s);
        // bank into the turns a little (from how fast the heading turns), level for the landing
        double roll = 0;
        if (t >= CARD && seconds < SkyPath.SECONDS) {
            path.sample(Math.min(SkyPath.SECONDS, seconds + 0.2), s2);
            float turn = Mth.wrapDegrees(yaw(s2) - yaw) / 0.2F;
            double fade = Math.min(1, seconds / 1.5) * Math.min(1, (SkyPath.SECONDS - seconds) / 3.0);
            roll = Mth.clamp(-turn * 0.06F, -6F, 6F) * fade;
        }
        // into the eyes (first person): the last push, eased
        if (pushFrom >= 0 && t > pushFrom) {
            double k = Math.min(1, (t - pushFrom) / PUSH);
            k = k * k * (3 - 2 * k);
            x = Mth.lerp(k, path.endX, eye.x);
            y = Mth.lerp(k, path.endY, eye.y);
            z = Mth.lerp(k, path.endZ, eye.z);
            yaw = path.endYaw;
            pitch = path.endPitch;
        }
        out[0] = x;
        out[1] = y;
        out[2] = z;
        out[3] = yaw;
        out[4] = pitch;
        out[5] = roll;
        // a little wider up high: the city is big
        double high = Math.clamp((y - eye.y) / 120.0, 0, 1);
        out[6] = 1.0 + 0.1 * high;
    }

    private static float yaw(double[] s) {
        return (float) Math.toDegrees(Math.atan2(-(s[3] - s[0]), s[5] - s[2]));
    }

    private static float pitch(double[] s) {
        double dx = s[3] - s[0], dy = s[4] - s[1], dz = s[5] - s[2];
        return (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
    }

    @Override
    public boolean hideBody(double camDistSq) {
        return pushFrom >= 0 && camDistSq < 1.6;
    }

    /** Vanilla's fog is sized for the camera: from high up push it out by the height (Distant Horizons fogs its own). */
    static void fog(ViewportEvent.RenderFog e) {
        SkyShot shot = current;
        if (shot == null || !OpeningCamera.active() || FarTerrain.active()) return;
        double h = Math.max(0, e.getCamera().position().y - shot.eye.y);
        var fog = e.getFogData();
        fog.renderDistanceStart += (float) h;
        fog.renderDistanceEnd += (float) h;
    }

    /** Where vanilla's third-person camera will sit (Camera.alignWithEntity + getMaxZoom): back along the view, clipped. */
    static double thirdPersonDistance(Minecraft mc, LocalPlayer p, float yaw, float pitch) {
        double dist = p.getScale() * p.getAttributeValue(Attributes.CAMERA_DISTANCE);
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        double dx = -Math.sin(yr) * Math.cos(pr), dy = -Math.sin(pr), dz = Math.cos(yr) * Math.cos(pr);
        Vec3 eye = p.getEyePosition();
        if (mc.level == null) return dist;
        for (int i = 0; i < 8; i++) {
            Vec3 from = eye.add(((i & 1) * 2 - 1) * 0.1, ((i >> 1 & 1) * 2 - 1) * 0.1, ((i >> 2 & 1) * 2 - 1) * 0.1);
            Vec3 to = from.add(-dx * dist, -dy * dist, -dz * dist);
            HitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
            if (hit.getType() != HitResult.Type.MISS) dist = Math.min(dist, hit.getLocation().distanceTo(eye));
        }
        return dist;
    }

    static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                msg -> Awaken.LOG.info("[opening] screenshot {}", name));
    }
}
