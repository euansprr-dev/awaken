package dev.awaken.opening.client;

import dev.awaken.citizens.CitizensContent;
import dev.awaken.lens.client.LensClient;
import dev.awaken.opening.net.OpeningPayloads;
import dev.awaken.story.RitualSounds;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;

/**
 * The opening on the client (SPEC §16.4): the server sends where the player is ({@link OpeningPayloads.Scene}, with the
 * age the beat has reached) and its moments ({@link OpeningPayloads.Cue}); everything seen and heard is built here.
 * The sky shot, the arrival and the infirmary are {@link OpeningCamera} shots; the run keeps the player's own camera
 * with the light in the square, the bell, the tremors and the Runner's eye. The View key (vanilla's Toggle Perspective)
 * flips first and third person and skips the front view.
 */
public final class OpeningClient {
    private OpeningClient() {}

    private static long ticks;
    private static int beat = OpeningPayloads.NONE;
    private static long beatAt;
    private static OpeningPayloads.Scene scene = OpeningPayloads.Scene.none();
    private static Vec3 origin = Vec3.ZERO;

    static long ticks() {
        return ticks;
    }

    static int beat() {
        return beat;
    }

    static OpeningPayloads.Scene scene() {
        return scene;
    }

    /** Carillon's centre at street height (the plan's points are offsets from it). */
    static Vec3 origin() {
        return origin;
    }

    /** The age the current beat has reached on this client. */
    static int beatAge() {
        return (int) (ticks - beatAt);
    }

    static boolean holdsMouse() {
        return false;
    }

    public static void init(IEventBus modBus) {
        OpeningConfig.register();
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, e -> {
            e.register(OpeningPayloads.Scene.TYPE, (msg, ctx) -> onScene(msg));
            e.register(OpeningPayloads.Cue.TYPE, (msg, ctx) -> onCue(msg));
            e.register(OpeningPayloads.Drive.TYPE, (msg, ctx) -> RouteDriver.start(msg));
        });
        modBus.addListener(RegisterGuiLayersEvent.class, OpeningHud::register);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ClientTickEvent.Post.class, e -> tick());
        bus.addListener(ClientTickEvent.Pre.class, e -> {
            viewKey();
            RouteDriver.tick();
        });
        // last, so the shots' angles win over shakes and rolls from elsewhere
        bus.addListener(EventPriority.LOWEST, ViewportEvent.ComputeCameraAngles.class, OpeningCamera::angles);
        bus.addListener(EventPriority.HIGH, ViewportEvent.ComputeCameraAngles.class, OpeningClient::shake);
        bus.addListener(EventPriority.LOWEST, ViewportEvent.ComputeFov.class, OpeningCamera::fov);
        // before Lens (LOWEST), so Lens hazes toward the fog the frame really has
        bus.addListener(ViewportEvent.RenderFog.class, SkyShot::fog);
        bus.addListener(RenderGuiLayerEvent.Pre.class, OpeningHud::hideHud);
        bus.addListener(RenderHandEvent.class, OpeningCamera::hand);
        bus.addListener(RenderPlayerEvent.Pre.class, OpeningCamera::player);
        bus.addListener(ExtractBlockOutlineRenderStateEvent.class, OpeningCamera::outline);
        bus.addListener(InputEvent.InteractionKeyMappingTriggered.class, OpeningCamera::interact);
        bus.addListener(CalculatePlayerTurnEvent.class, OpeningCamera::turn);
        bus.addListener(RenderNameTagEvent.CanRender.class, OpeningCamera::nameTag);
        bus.addListener(SubmitCustomGeometryEvent.class, OpeningFx::submit);
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> reset());
        bus.addListener(net.neoforged.neoforge.client.event.ToastAddEvent.class, OpeningClient::onToast);
        bus.addListener(net.neoforged.neoforge.client.event.ScreenEvent.Render.Post.class, OpeningClient::overLoading);
        RegistryScene.init();
    }

    static void reset() {
        RouteDriver.stop("logout");
        OpeningCamera.reset();
        OpeningHud.reset();
        OpeningFx.reset();
        WakeShot.reset();
        RunnerEye.reset();
        RegistryScene.reset();
        beat = OpeningPayloads.NONE;
        scene = OpeningPayloads.Scene.none();
        LATER.clear();
        shake = 0;
    }

    // ---- network ----------------------------------------------------------------------------------------------------

    static void onScene(OpeningPayloads.Scene s) {
        int was = beat;
        boolean fresh = s.beat() != was;
        scene = s;
        origin = s.origin();
        beat = s.beat();
        beatAt = ticks - s.age();
        switch (beat) {
            case OpeningPayloads.SKY -> {
                if (fresh || SkyShot.current == null) SkyShot.start(s);
            }
            case OpeningPayloads.RUN -> {
                if (SkyShot.current != null && OpeningCamera.active()) OpeningCamera.finish("replaced");
                OpeningFx.column = s.at();
                OpeningFx.columnOn = true;
                if (was != OpeningPayloads.RUN) {
                    LensClient.override("awaken:carillon_dawn", 40);
                    // a relog mid-run: the light is already standing
                    if (s.age() > 10) OpeningFx.fellAt = ticks - 100;
                }
                OpeningHud.waiting(-1);
            }
            case OpeningPayloads.ARRIVAL -> {
                OpeningFx.column = s.at();
                OpeningFx.columnOn = true;
                LensClient.override("awaken:carillon_dawn", 40);
                if (s.flag(OpeningPayloads.F_CINEMATIC)) {
                    OpeningHud.waiting(-1);
                    ArrivalShot.start(s);
                } else {
                    OpeningHud.waiting(s.n());
                    if (fresh && s.n() > 0) OpeningHud.title("CHOIR SQUARE", "a gate is opening", 70);
                }
            }
            case OpeningPayloads.WAKE -> {
                OpeningFx.columnOn = false;
                OpeningHud.waiting(-1);
                // from the Void: the ritual's scene (its black, its look, its loops) hands the frame over to the closed eyes
                if (fresh) dev.awaken.story.client.RitualClient.handOff();
                if (fresh || !OpeningCamera.active()) WakeShot.start(s);
            }
            case OpeningPayloads.REGISTRY -> {
                OpeningFx.columnOn = false;
                if (was == OpeningPayloads.WAKE && OpeningCamera.active()) OpeningCamera.finish("replaced");
                WakeShot.clearLook();
                RegistryScene.onScene(s, fresh);
            }
            default -> {
                // the opening is over (or never was): everything of ours lets go
                if (OpeningCamera.active()) OpeningCamera.finish("replaced");
                OpeningFx.columnOn = false;
                OpeningHud.waiting(-1);
                RegistryScene.onScene(s, fresh);
                if (was == OpeningPayloads.RUN || was == OpeningPayloads.ARRIVAL || was == OpeningPayloads.REGISTRY) LensClient.clearOverride(80);
            }
        }
    }

    static void onCue(OpeningPayloads.Cue c) {
        switch (c.kind()) {
            case OpeningPayloads.CUE_BELL_WRONG -> bellWrong(c.a());
            case OpeningPayloads.CUE_TREMOR -> tremor(c.a(), c.f());
            case OpeningPayloads.CUE_BRIDGE -> bridge(c.a());
            case OpeningPayloads.CUE_RUSH -> rush(c.a());
            case OpeningPayloads.CUE_TOLL -> toll(c.n());
            case OpeningPayloads.CUE_LINE -> {
                String[] p = c.text().split("\\|", 2);
                if (p.length == 2) OpeningHud.line(p[0], p[1], c.n());
            }
            case OpeningPayloads.CUE_TITLE -> {
                String[] p = c.text().split("\\|", 2);
                OpeningHud.title(p[0], p.length > 1 ? p[1] : "", c.n());
            }
            case OpeningPayloads.CUE_PULLED -> {
                OpeningHud.flash(0xCFE8FF, 0.9F);
                OpeningSounds.ui(RitualSounds.TEAR.get(), 1.2F, 0.8F);
            }
            default -> {}
        }
    }

    // ---- the bell rings wrong, and what the run hears ---------------------------------------------------------------

    /** The great bell, out of time: two strikes a semitone apart beating against each other, then the light falls. */
    private static void bellWrong(Vec3 square) {
        Vec3 belfry = new Vec3(origin.x, origin.y + 68.5, origin.z - 46);
        OpeningSounds.ui(CitizensContent.BELL_HOUR.get(), 0.94F, 0.9F);
        OpeningSounds.ui(CitizensContent.BELL_HOUR.get(), 0.89F, 0.7F);
        OpeningSounds.ui(RitualSounds.BELL.get(), 0.47F, 0.55F);
        OpeningSounds.at(CitizensContent.BELL_HOUR.get(), belfry, 12F, 0.92F);
        shake(0.6F, 30);
        LensClient.ritual(0.15F, 0.25F);
        later(8, () -> {
            OpeningFx.fall(square);
            OpeningSounds.ui(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.55F, 0.5F);
            OpeningSounds.at(SoundEvents.LIGHTNING_BOLT_IMPACT, square, 8F, 0.5F);
            OpeningSounds.ui(RitualSounds.EYES.get(), 0.8F, 0.35F);
            OpeningHud.flash(0xDCEEFF, 0.35F);
            shake(1.2F, 24);
            LensClient.ritual(0.1F, 0.6F);
        });
        later(40, () -> LensClient.ritual(0F, 0F));
        // it keeps ringing, wrong, slower each time
        later(64, () -> OpeningSounds.ui(CitizensContent.BELL_HOUR.get(), 0.9F, 0.6F));
        later(66, () -> OpeningSounds.ui(CitizensContent.BELL_HOUR.get(), 0.85F, 0.45F));
        later(150, () -> OpeningSounds.ui(CitizensContent.BELL_HOUR.get(), 0.87F, 0.45F));
        later(70, () -> OpeningHud.title("SOMETHING IS WRONG WITH THE BELL", "", 60));
    }

    private static void tremor(Vec3 square, float strength) {
        shake(strength, 30);
        OpeningFx.surge = Math.max(OpeningFx.surge, 0.6F * strength);
        OpeningSounds.ui(RitualSounds.RUMBLE.get(), 0.8F, Math.min(1F, 0.45F + strength * 0.4F));
        OpeningFx.ring(square.add(0, 1, 0), 2F, 1.6F, 110F, 0xA09ED2FF, 70);
        if (strength > 0.7F) later(4, () -> OpeningSounds.ui(CitizensContent.BELL_HOUR.get(), 0.88F, 0.35F));
    }

    /** The gate pulses; the shockwave crosses the Quay and the Bell Bridge goes (the server drops it as it arrives). */
    private static void bridge(Vec3 mid) {
        Vec3 c = OpeningFx.column;
        OpeningFx.surge = 1.2F;
        OpeningFx.ring(c.add(0, 1, 0), 2F, 3.2F, 180F, 0xD0C8E6FF, 60);
        OpeningSounds.ui(RitualSounds.GAZE_FIRE.get(), 0.6F, 0.6F);
        shake(0.8F, 20);
        later(22, () -> {
            shake(1.6F, 40);
            OpeningSounds.at(RitualSounds.IMPACT.get(), mid, 10F, 0.6F);
            OpeningSounds.at(CitizensContent.BELL_ALARM.get(), mid, 6F, 0.7F);
        });
    }

    private static void rush(Vec3 square) {
        OpeningSounds.at(CitizensContent.BELL_ALARM.get(), square.add(0, 20, 0), 10F, 1.0F);
        later(20, () -> OpeningSounds.at(CitizensContent.BELL_ALARM.get(), square.add(0, 20, 0), 10F, 0.95F));
        later(40, () -> OpeningSounds.at(CitizensContent.BELL_ALARM.get(), square.add(0, 20, 0), 10F, 1.0F));
        OpeningFx.surge = Math.max(OpeningFx.surge, 0.8F);
    }

    /** A true toll of the great bell (the arrival's three). */
    static void toll(int n) {
        Vec3 belfry = new Vec3(origin.x, origin.y + 68.5, origin.z - 46);
        OpeningSounds.ui(CitizensContent.BELL_HOUR.get(), 0.75F, 1F);
        OpeningSounds.ui(RitualSounds.BELL.get(), 0.5F, 0.6F);
        OpeningSounds.at(CitizensContent.BELL_HOUR.get(), belfry, 14F, 0.75F);
        shake(0.5F + n * 0.25F, 26);
        OpeningFx.surge = Math.max(OpeningFx.surge, 0.7F + n * 0.2F);
    }

    // ---- camera shake (the player's own camera; the shots carry theirs in their poses) ------------------------------

    private static float shake;
    private static int shakeTicks, shakeLen;

    static void shake(float strength, int ticks) {
        if (strength >= shake * Math.max(0.0F, shakeTicks / (float) Math.max(1, shakeLen))) {
            shake = strength;
            shakeTicks = ticks;
            shakeLen = ticks;
        }
    }

    private static void shake(ViewportEvent.ComputeCameraAngles e) {
        if (shakeTicks <= 0 || OpeningCamera.active()) return;
        float t = (float) (ticks + e.getPartialTick());
        float k = shake * (shakeTicks / (float) shakeLen);
        e.setYaw(e.getYaw() + Mth.sin(t * 2.1F) * 0.5F * k);
        e.setPitch(e.getPitch() + Mth.sin(t * 2.9F + 1.3F) * 0.4F * k);
        e.setRoll(e.getRoll() + Mth.sin(t * 1.7F + 0.4F) * 0.6F * k);
    }

    // ---- the tick -----------------------------------------------------------------------------------------------------

    private record Later(long at, Runnable r) {}

    private static final List<Later> LATER = new ArrayList<>();

    static void later(int ticksFromNow, Runnable r) {
        LATER.add(new Later(ticks + ticksFromNow, r));
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            if (beat != OpeningPayloads.NONE) reset();
            return;
        }
        ticks++;
        if (shakeTicks > 0) shakeTicks--;
        if (!LATER.isEmpty()) {
            List<Later> due = new ArrayList<>();
            LATER.removeIf(l -> {
                if (l.at() <= ticks) {
                    due.add(l);
                    return true;
                }
                return false;
            });
            for (Later l : due) l.r().run();
        }
        OpeningCamera.tick();
        OpeningHud.tick();
        OpeningFx.tick();
        WakeShot.tickPost();
        RegistryScene.tick();
        if (beat == OpeningPayloads.RUN) RunnerEye.tick(mc);
        holdOthers();
        releaseToasts(mc);
    }

    /**
     * The View key: vanilla's Toggle Perspective flips first and third person and skips the front view. With Shoulder
     * Surfing installed it flips first person and the shoulder view instead (CameraHooks).
     */
    private static void viewKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !OpeningConfig.skipFront() || OpeningCamera.active()) return;
        boolean flipped = false;
        while (mc.options.keyTogglePerspective.consumeClick()) {
            // with Shoulder Surfing the key flips first person and the shoulder view (core.api.CameraHooks, SPEC §17.13)
            if (dev.awaken.core.api.CameraHooks.toggleView()) continue;
            CameraType now = mc.options.getCameraType();
            mc.options.setCameraType(now.isFirstPerson() ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON);
            flipped = true;
        }
        if (flipped) mc.gameRenderer.checkEntityPostEffect(mc.options.getCameraType().isFirstPerson() ? mc.getCameraEntity() : null);
    }

    // ---- quiet: nothing else on screen while a shot plays (toasts wait and come after) -------------------------------

    private static final java.util.ArrayDeque<net.minecraft.client.gui.components.toasts.Toast> TOASTS = new java.util.ArrayDeque<>();
    private static long nextToast;

    static boolean quiet() {
        // the infirmary and the Registry are one scene: nothing else talks over it (Euan, 2026-10-26)
        return OpeningCamera.active() || OpeningHud.black > 0.4F || OpeningHud.lidsOn || inScene();
    }

    static boolean inScene() {
        return beat == OpeningPayloads.WAKE || beat == OpeningPayloads.REGISTRY;
    }

    private static final Object SCENE = new Object();
    private static boolean sceneHeld;

    /** The System's notices and the tip system hold for the scene; held toasts come back one at a time after it. */
    private static void holdOthers() {
        boolean want = inScene();
        if (want == sceneHeld) return;
        sceneHeld = want;
        dev.awaken.core.api.Notices.client().hold(SCENE, want);
        dev.awaken.core.api.TipHooks.client().hold(SCENE, want);
    }

    private static void onToast(net.neoforged.neoforge.client.event.ToastAddEvent e) {
        if (!quiet()) return;
        e.setCanceled(true);
        if (TOASTS.size() < 12) TOASTS.addLast(e.getToast());
    }

    private static void releaseToasts(Minecraft mc) {
        if (TOASTS.isEmpty() || quiet() || ticks < nextToast) return;
        mc.gui.toastManager().addToast(TOASTS.removeFirst());
        nextToast = ticks + 40; // one at a time, room to read each
    }

    /** A dimension change shows the loading screen: under the arrival's black it stays black. */
    private static void overLoading(net.neoforged.neoforge.client.event.ScreenEvent.Render.Post e) {
        if (!(e.getScreen() instanceof net.minecraft.client.gui.screens.LevelLoadingScreen) || OpeningHud.black < 0.5F) return;
        var g = e.getGuiGraphics();
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), 0xFF000000);
    }

    static LocalPlayer player() {
        return Minecraft.getInstance().player;
    }
}
