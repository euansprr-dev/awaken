package dev.awaken.story.client;

import dev.awaken.lens.client.LensClient;
import dev.awaken.story.net.StoryPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ToastAddEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;

/**
 * The Ritual of the Bell as the player sees and hears it (docs/proposals/the-ritual.md). The server sends where the
 * scene is ({@link StoryPayloads.RitualScene}) and its moments ({@link StoryPayloads.RitualCue}); everything else is
 * built here: the Lens looks (awaken:ritual, awaken:void) and the tear, the camera's pull and shake, the words carved
 * in light, the telegraphs, the eyes, the drain into the palm, the sounds, and the black. Without Lens (off, or a
 * shaderpack on) the scene still plays, just without the look.
 */
public final class RitualClient {
    private RitualClient() {}

    // beats, as the server's Ritual.Beat ordinals
    static final int WAITING = 0, DOORS = 1, BOW = 2, RING = 3, JUDGED = 4, WAKING = 5, JUDGEMENT = 6, END = 7;
    // cues, as the server's Ritual.Cue ordinals
    static final int TOLL = 0, WORD = 1, CUE_DOORS = 2, LIGHTS_OUT = 3, CANDLE = 4, LIGHTS_ON = 5, PLATE = 6, PLATE_MISS = 7,
            GLARE = 8, RULE_STRIKE = 9, HIT = 10, SHAKE = 11, EYES = 12, RISE = 13, GAZE_CHARGE = 14, GAZE_FIRE = 15, SWING = 16,
            IMPACT = 17, PIT = 18, DRAIN = 19, ERROR = 20, DEATH = 21, HOLD = 22;

    /** The scene: beat (or NONE / VOID / TEAR), when it began on the client clock, and the hall's places. */
    static int beat = StoryPayloads.RitualScene.NONE;
    static long beatStart;
    static int seated = -1;
    static Vec3 throne = Vec3.ZERO, altar = Vec3.ZERO, words = Vec3.ZERO, door = Vec3.ZERO;
    static List<Vec3> plates = List.of();

    /** Client ticks since joining (the scene's clock), and this frame's partial tick. */
    static long ticks;
    private static net.minecraft.client.multiplayer.ClientLevel sceneLevel;

    /** One moment being drawn or heard. */
    static final class Cue {
        int kind, id, ticks;
        Vec3 a = Vec3.ZERO, b = Vec3.ZERO;
        float f;
        long start;
        boolean live;

        float age(float pt) {
            return ticks() - start + pt;
        }
    }

    static final Cue[] CUES = new Cue[64];

    static {
        for (int i = 0; i < CUES.length; i++) CUES[i] = new Cue();
    }

    /** Logs where the scene thinks the god's eyes and palm are (tours). */
    static final boolean DEBUG = Boolean.getBoolean("awaken.ritualDebug") || System.getProperty("awaken.autopilot") != null;

    static long ticks() {
        return ticks;
    }

    public static void init(IEventBus modBus) {
        RitualAudioConfig.init();
        RitualAudio.init();
        modBus.addListener(RegisterGuiLayersEvent.class, RitualHud::register);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> tick());
        NeoForge.EVENT_BUS.addListener(ViewportEvent.ComputeCameraAngles.class, RitualCamera::angles);
        NeoForge.EVENT_BUS.addListener(ViewportEvent.ComputeFov.class, RitualCamera::fov);
        NeoForge.EVENT_BUS.addListener(SubmitCustomGeometryEvent.class, RitualFx::submit);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> reset());
        NeoForge.EVENT_BUS.addListener(ToastAddEvent.class, RitualClient::onToast);
        NeoForge.EVENT_BUS.addListener(RenderHandEvent.class, RitualClient::onHand);
        dev.awaken.core.api.FirstPerson.view(RitualFp::viewPose);
    }

    // ---- quiet: nothing else on screen while the scene plays --------------------------------------------------------

    /** When the tear began (client ticks), or -1. The frame is the tear's alone until a moment after it opens. */
    static long tearAt = -1;
    static final int TEAR_QUIET = 80;
    private static final int MAX_WAITING_TOASTS = 12;
    private static final java.util.ArrayDeque<Toast> waitingToasts = new java.util.ArrayDeque<>();
    private static long nextToast;

    /**
     * From the doors closing until just after the tear: the ritual, the black, the question and the tear are the whole
     * screen (no toasts, no tracker, no gate card, no level line). What arrives meanwhile waits and comes after.
     */
    static boolean quiet() {
        return inScene() || beat == StoryPayloads.RitualScene.VOID || tearing();
    }

    static boolean tearing() {
        return tearAt >= 0 && ticks - tearAt < TEAR_QUIET;
    }

    private static void onToast(ToastAddEvent e) {
        if (!quiet()) return;
        e.setCanceled(true);
        // vanilla folds every recipe unlocked at once into one toast: one waiting is enough
        if (e.getToast() instanceof RecipeToast) for (Toast t : waitingToasts) if (t instanceof RecipeToast) return;
        if (waitingToasts.size() < MAX_WAITING_TOASTS) waitingToasts.addLast(e.getToast());
    }

    /** The black and the tear show no hand: the frame is the dark (and then the world) alone. */
    private static void onHand(RenderHandEvent e) {
        if (beat == StoryPayloads.RitualScene.VOID || tearing()) e.setCanceled(true);
    }

    /** After the scene: what waited is shown, one every half second. */
    private static void releaseToasts(Minecraft mc) {
        if (waitingToasts.isEmpty() || quiet() || ticks < nextToast) return;
        mc.gui.toastManager().addToast(waitingToasts.removeFirst());
        nextToast = ticks + 10;
    }

    /**
     * Another scene takes the frame straight from the Void (Carillon's opening wakes you in the infirmary, no tear):
     * everything of the ritual's lets go, the end's black included, which otherwise waits for the tear to lift it.
     */
    public static void handOff() {
        reset();
        LensClient.ritual(0F, 0F);
    }

    static void reset() {
        beat = StoryPayloads.RitualScene.NONE;
        tearAt = -1;
        waitingToasts.clear();
        for (Cue c : CUES) c.live = false;
        releaseInput();
        RitualFx.reset();
        RitualCamera.reset();
        RitualHud.reset();
        RitualAudio.reset();
        sceneLevel = null;
    }

    // ---- network ----------------------------------------------------------------------------------------------------

    public static void onScene(StoryPayloads.RitualScene s) {
        int was = beat;
        sceneLevel = Minecraft.getInstance().level;
        if (s.beat() != was) {
            for (Cue c : CUES) c.live = false; // a word or a telegraph never outlives its beat
            RitualHud.newBeat();
        }
        beat = s.beat();
        beatStart = ticks - s.age();
        if (s.seated() >= 0) seated = s.seated();
        if (!s.throne().equals(Vec3.ZERO)) {
            throne = s.throne();
            altar = s.altar();
            words = s.words();
            door = s.door();
            plates = List.copyOf(s.plates());
        }
        RitualAudio.scene(was, beat);
        switch (beat) {
            case StoryPayloads.RitualScene.VOID -> {
                LensClient.override("awaken:void", 12);
                LensClient.ritual(0.75F, 0.35F);
                RitualFx.lightsOut();
                RitualHud.question(s.text());

            }
            case StoryPayloads.RitualScene.TEAR -> {

                tearAt = ticks;
                LensClient.tear(36);
                LensClient.clearOverride(40);
                LensClient.ritual(0F, 0F);
                RitualHud.tear();
                RitualHud.flash(0x9ED2FF, 0.45F, 14); // a blue flash as the black opens

            }
            case StoryPayloads.RitualScene.NONE -> {
                RitualFx.lightsOut();
                if (was != StoryPayloads.RitualScene.TEAR) LensClient.clearOverride(40);
                LensClient.ritual(0F, 0F);
                releaseInput();

            }
            case WAITING -> LensClient.clearOverride(40);
            default -> {
                // DOORS waits for the lights to die before the look turns; a relog mid-scene gets it at once
                if (beat != DOORS || s.age() > 34) {
                    LensClient.override("awaken:ritual", s.age() > 0 ? 2 : 30);
                    LensClient.ritual(0.25F, 0.15F);
                }
            }
        }
    }

    public static void onCue(StoryPayloads.RitualCue m) {
        if (sceneLevel == null || sceneLevel != Minecraft.getInstance().level) return;
        Cue c = null;
        long oldest = Long.MAX_VALUE;
        for (Cue x : CUES) {
            if (!x.live) {
                c = x;
                break;
            }
            if (x.start < oldest) {
                oldest = x.start;
                c = x;
            }
        }
        if (c == null) return;
        c.kind = m.kind();
        c.id = m.id();
        c.a = m.a();
        c.b = m.b();
        c.f = m.f();
        c.ticks = m.ticks();
        c.start = ticks;
        c.live = true;
        RitualAudio.cue(c);
        switch (c.kind) {
            case LIGHTS_OUT -> {
                LensClient.override("awaken:ritual", 6);
                LensClient.ritual(0.7F, 0.0F);
                RitualFx.lightsOut();
            }
            case CANDLE -> RitualFx.candleLit(c.a, c.f);
            case LIGHTS_ON -> LensClient.ritual(0.25F, 0.15F);
            case EYES -> {
                if (c.f > 0) {
                    LensClient.ritual(0.45F, 1.0F);
                    RitualCamera.shakeNear(c.a, 1.4F, 30, 64);
                    RitualCamera.fovPunch(-0.12F, 40);
                    RitualHud.flash(0x00FFF2D0, 0.55F, 14);
                } else LensClient.ritual(0.4F, 0.25F);
            }
            case GAZE_FIRE -> {
                LensClient.ritual(0.4F, 0.85F);
                RitualHud.flash(0x00FF5A3C, 0.25F, 8);
            }
            case GAZE_CHARGE -> LensClient.ritual(0.4F, 0.45F);
            case SHAKE -> RitualCamera.shakeNear(c.a, c.f * 2.4F, Math.max(6, c.ticks), 36);
            case HIT -> {
                RitualCamera.shake(1.6F, 10);
                RitualCamera.kick(c.a, 6F);
                RitualHud.hurt();
            }
            case IMPACT -> RitualCamera.shakeNear(c.a, c.f * 2.0F, 10, 9);
            case RULE_STRIKE -> RitualCamera.shake(0.8F, 8);
            case HOLD -> holdInput(c.ticks);
            case DRAIN -> LensClient.ritual(0.15F, 0.6F);
            case DEATH -> {
                LensClient.ritual(0F, 1F);
                RitualHud.death(c.ticks);
            }
            case ERROR -> RitualHud.error(c.ticks);
            case WORD -> RitualHud.word(c.id, c.ticks);
            case PLATE -> RitualHud.plate(c.ticks);
            default -> {}
        }
    }

    // ---- ticking ----------------------------------------------------------------------------------------------------

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            if (beat != StoryPayloads.RitualScene.NONE) reset();
            return;
        }
        if (sceneLevel != null && sceneLevel != mc.level) { reset(); return; }
        if (mc.isPaused()) { RitualAudio.suspend(); return; }
        ticks++;
        RitualAudio.tick();
        if (DEBUG && inScene() && ticks % 40 == 0) {
            var s = RitualFx.seatedEntity();
            dev.awaken.Awaken.LOG.info("[story] ritual client: beat {} age {} seated {} found {} stage {} light {} eyes {} palm {}", beat,
                    (int) beatAge(0), seated, s != null, s == null ? -1 : s.stage(), RitualFx.eyeLight(0), RitualFx.eyes(0), RitualFx.palm(0));
        }
        for (Cue c : CUES) if (c.live && c.ticks >= 0 && ticks - c.start > Math.max(c.ticks, 1) + 40) c.live = false;
        if (holdUntil >= 0 && ticks >= holdUntil) releaseInput();
        RitualCamera.tick();
        RitualFx.tick();
        RitualHud.tick();
        if (tearAt >= 0 && ticks - tearAt >= TEAR_QUIET) tearAt = -1;
        releaseToasts(mc);
    }

    static float beatAge(float pt) {
        return ticks - beatStart + pt;
    }

    static boolean inScene() {
        return beat >= DOORS && beat <= END;
    }

    // ---- input ------------------------------------------------------------------------------------------------------

    /** The lock's owner while the scene holds the player (the shared input lock: core.client.InputLock). */
    private static final Object INPUT = "ritual";
    private static long holdUntil = -1;

    /** The scene takes the controls for a moment (the waking, the end): no walking, the camera is the scene's. */
    private static void holdInput(int forTicks) {
        holdUntil = Math.max(holdUntil, ticks + forTicks);
        dev.awaken.core.client.InputLock.hold(INPUT, (int) (holdUntil - ticks) + 40);
    }

    static void releaseInput() {
        dev.awaken.core.client.InputLock.release(INPUT);
        holdUntil = -1;
    }

    static boolean held() {
        return dev.awaken.core.client.InputLock.held(INPUT);
    }

}
