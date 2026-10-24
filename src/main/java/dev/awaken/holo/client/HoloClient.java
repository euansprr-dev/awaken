package dev.awaken.holo.client;

import com.google.common.reflect.TypeToken;
import dev.awaken.core.api.UiHooks;
import dev.awaken.holo.HoloKind;
import dev.awaken.holo.HoloNet;
import dev.awaken.holo.ui.HoloTimeline;
import dev.awaken.progression.client.ProgressionClient;
import dev.awaken.progression.client.StatusScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.PlayerModelType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Vector3f;

import java.util.List;

/**
 * Holo UI, client half (docs/modules/holo.md): System panels as real 3D objects that trail your view, the K menu
 * unfolding from a band on your wrist, the wrist other players see, and the calm HUD. One frame:
 * <ol>
 *   <li>level extract: view facts ({@link ViewMath}), springs, timelines and panel placement; the demo records;</li>
 *   <li>GUI extract: HUD layers sway ({@link HudMotion}); an open {@link HoloStatusScreen} records its panel;</li>
 *   <li>level render: {@link HoloRender} replays the panels in the always-on-top phase;</li>
 *   <li>hand pass: {@link Wrist} draws the raised forearm and its band.</li>
 * </ol>
 */
public final class HoloClient {
    private HoloClient() {}

    private static final HoloPanel DEMO = new HoloPanel(0);
    private static final HoloPanel STATUS = new HoloPanel(1);
    private static final List<HoloRender.Draw> DRAWS = List.of(new HoloRender.Draw(DEMO), new HoloRender.Draw(STATUS));
    private static int demoMode;
    private static long lastNanos, frame;
    private static HoloKind sent = HoloKind.NONE;
    private static final Vector3f WRIST = new Vector3f();

    public static void init(IEventBus modBus) {
        HoloConfig.register();
        STATUS.w = HoloStatusScreen.W;
        STATUS.h = HoloStatusScreen.H;
        STATUS.anchor = HoloPanel.Anchor.WRIST;
        modBus.addListener(RegisterRenderPipelinesEvent.class, HoloPipelines::onRegister);
        dev.awaken.signal.client.HudLook.install(HoloGuiLook.INSTANCE);
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, HoloClient::onPayloads);
        modBus.addListener(RegisterRenderStateModifiersEvent.class,
                e -> e.registerEntityModifier(new TypeToken<EntityRenderer<Entity, EntityRenderState>>() {}, HoloOthers::modify));
        modBus.addListener(EntityRenderersEvent.AddLayers.class, HoloClient::onLayers);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ExtractLevelRenderStateEvent.class, HoloClient::onExtract);
        bus.addListener(SubmitCustomGeometryEvent.class, HoloRender::submit);
        // HIGH: replaces both hands before Lucidium (NORMAL) and CastAnims (LOW), which then don't run
        bus.addListener(EventPriority.HIGH, RenderHandEvent.class, Wrist::onHand);
        // push last, pop first: nothing after us can cancel the layer and strand the translate
        bus.addListener(EventPriority.LOWEST, RenderGuiLayerEvent.Pre.class, HudMotion::pre);
        // the K menu is worked with the cursor; the crosshair would only sit on the panel's edge
        bus.addListener(RenderGuiLayerEvent.Pre.class, e -> {
            if (e.getName().equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.CROSSHAIR)
                    && Minecraft.getInstance().gui.screen() instanceof HoloStatusScreen) e.setCanceled(true);
        });
        bus.addListener(EventPriority.HIGHEST, RenderGuiLayerEvent.Post.class, HudMotion::post);
        bus.addListener(EventPriority.LOWEST, RenderGuiEvent.Post.class, HudMotion::endFrame);
        bus.addListener(ClientTickEvent.Pre.class, e -> Wrist.tickStart(wristClosing()));
        bus.addListener(ClientTickEvent.Post.class, e -> onTick());
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> reset());
        bus.addListener(ClientPlayerNetworkEvent.LoggingIn.class, e -> HoloPipelines.recheck());
        bus.addListener(ClientPlayerNetworkEvent.Clone.class, e -> HoloOthers.clear());
        // reported each frame (UiHooks.setFocus); other sources (the Atlas) report beside it
        ProgressionClient.installStatusOpener(HoloClient::statusScreen);
    }

    private static void onPayloads(RegisterClientPayloadHandlersEvent e) {
        e.register(HoloNet.Relay.TYPE, (msg, ctx) -> HoloOthers.onRelay(msg));
        e.register(HoloNet.Demo.TYPE, (msg, ctx) -> demo(msg.mode()));
    }

    private static void onLayers(EntityRenderersEvent.AddLayers e) {
        for (PlayerModelType type : e.getSkins()) {
            AvatarRenderer<?> r = e.getPlayerRenderer(type);
            if (r != null) addLayer(r, type);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addLayer(AvatarRenderer r, PlayerModelType type) {
        r.addLayer(new HoloOthers.Layer(r, type));
    }

    // ---- what K opens ---------------------------------------------------------------------------------------------

    /** The K menu: holo when chosen and the shaders loaded, otherwise the flat screen. */
    public static Screen statusScreen() {
        if (HoloConfig.statusStyle == HoloConfig.StatusStyle.HOLO && HoloPipelines.ok()) return new HoloStatusScreen();
        return new StatusScreen();
    }

    static HoloPanel statusPanel() {
        return STATUS;
    }

    /** The panel the wrist belongs to right now (the K menu, or the wrist demo). */
    static HoloPanel wristPanel() {
        if (STATUS.timeline.active()) return STATUS;
        if (DEMO.anchor == HoloPanel.Anchor.WRIST && DEMO.timeline.active()) return DEMO;
        return null;
    }

    static void openStatusPanel() {
        if (!STATUS.timeline.active()) {
            Minecraft mc = Minecraft.getInstance();
            var cam = mc.gameRenderer.mainCamera();
            STATUS.spring.reset(cam.yRot(), cam.xRot());
        }
        STATUS.timeline.hold(false);
        STATUS.timeline.open();
    }

    static void closeStatusPanel() {
        STATUS.timeline.hold(false);
        boolean was = STATUS.timeline.active() && !STATUS.timeline.closing();
        STATUS.timeline.close();
        if (was) Wrist.handsBack();
    }

    private static boolean wristClosing() {
        HoloPanel w = wristPanel();
        return w != null && w.anchor == HoloPanel.Anchor.WRIST && w.timeline.closing();
    }

    static void pressFlash(double x, double y) {
        STATUS.flashX = (float) x;
        STATUS.flashY = (float) y;
        STATUS.flashMs = 0;
    }

    static List<HoloRender.Draw> draws() {
        return DRAWS;
    }

    static long frame() {
        return frame;
    }

    private static double focus() {
        float f = STATUS.timeline.focus();
        if (DEMO.anchor == HoloPanel.Anchor.WRIST) f = Math.max(f, DEMO.timeline.focus());
        return Math.max(f, HoloTimeline.easeInOut(readFocus));
    }

    private static final Object FOCUS_OWNER = new Object();

    /** A projected reading screen (the first-power choice) is open: 0..1, eased over 300 ms each way. */
    private static float readFocus;

    private static void easeReadFocus(float dt) {
        boolean reading = Minecraft.getInstance().gui.screen() instanceof dev.awaken.signal.client.ReadingScreen
                && dev.awaken.signal.client.HudLook.holo();
        readFocus = reading ? Math.min(1f, readFocus + dt / 0.3f) : Math.max(0f, readFocus - dt / 0.3f);
    }

    // ---- per tick and per frame -----------------------------------------------------------------------------------

    private static void onTick() {
        HoloConfig.refresh();
        Wrist.tickEnd(wristClosing());
        // tell the server which panel the wrist shows (it relays to whoever can see us, at most 4 a second)
        HoloKind want = STATUS.timeline.active() && !STATUS.timeline.closing() ? HoloKind.STATUS
                : DEMO.anchor == HoloPanel.Anchor.WRIST && DEMO.timeline.active() && !DEMO.timeline.closing() ? HoloKind.STATUS : HoloKind.NONE;
        Minecraft mc = Minecraft.getInstance();
        if (want != sent && mc.getConnection() != null && mc.getConnection().hasChannel(HoloNet.State.TYPE)) {
            ClientPacketDistributor.sendToServer(new HoloNet.State(want.id));
            sent = want;
        }
    }

    private static void onExtract(ExtractLevelRenderStateEvent e) {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        frame++;
        CameraRenderState cam = e.getRenderState().cameraRenderState;
        ViewMath.update(cam);
        HudMotion.update(cam.yRot, cam.xRot, dt);
        HoloOthers.advance(dt * 1000f);
        easeReadFocus(dt);
        UiHooks.setFocus(FOCUS_OWNER, (float) focus());
        boolean reduced = HoloConfig.reducedMotion;
        if (demoMode != 0) HoloDemo.record(DEMO);
        for (HoloRender.Draw d : DRAWS) {
            HoloPanel p = d.p;
            p.timeline.advance(dt * 1000f);
            if (p.flashMs >= 0) p.flashMs += dt * 1000f;
            if (p.flashMs > 400) p.flashMs = -1;
            p.spring.follow(cam.yRot, cam.xRot, dt, reduced);
            if (!HoloRender.visible(p)) {
                p.drawn = false;
                continue;
            }
            p.thirdPerson = !Minecraft.getInstance().options.getCameraType().isFirstPerson();
            p.animate(reduced);
            float grow = p.anchor == HoloPanel.Anchor.WRIST ? p.timeline.grow(reduced) : 1f;
            wristView(p, WRIST);
            p.place(grow, WRIST);
        }
        if (demoMode == 2 && !DEMO.timeline.active()) demoMode = 0;
    }

    /** Just above the wrist device, as a point in the level's view space (the hand pass has its own field of view). */
    private static void wristView(HoloPanel p, Vector3f out) {
        Minecraft mc = Minecraft.getInstance();
        int side = mc.player != null && mc.player.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT ? 1 : -1;
        Wrist.emitter(Math.min(1f, p.timeline.arm()), side, out);
        float k = ViewMath.tanY / ViewMath.tanYHand;
        out.x *= k;
        out.y *= k;
    }

    // ---- demo and autopilot ---------------------------------------------------------------------------------------

    static void demo(int mode) {
        demoMode = mode;
        if (mode == 0) {
            DEMO.timeline.hold(false);
            if (DEMO.anchor == HoloPanel.Anchor.VIEW) {
                DEMO.timeline.stop();
                DEMO.anchor = HoloPanel.Anchor.WRIST;
            } else {
                DEMO.timeline.close();
            }
            return;
        }
        if (mode == 2) {
            Minecraft mc = Minecraft.getInstance();
            var cam = mc.gameRenderer.mainCamera();
            DEMO.spring.reset(cam.yRot(), cam.xRot());
        }
        HoloDemo.show(DEMO, mode);
    }

    /**
     * Autopilot {@code holo ...} lines: {@code demo 0|1|2}, {@code at <ms>} (freeze the open panel's timeline at a
     * moment), {@code run}, {@code close}, {@code hud off|subtle|full}, {@code log}.
     */
    public static String autopilot(String args) {
        String[] a = args.trim().split("\\s+");
        HoloPanel p = STATUS.timeline.active() ? STATUS : DEMO;
        switch (a[0]) {
            case "demo" -> demo(Integer.parseInt(a[1]));
            case "at" -> {
                p.timeline.seek(Float.parseFloat(a[1]));
                p.timeline.hold(true);
            }
            case "run" -> p.timeline.hold(false);
            case "close" -> {
                p.timeline.hold(false);
                if (p == STATUS) Minecraft.getInstance().gui.setScreen(null);
                else p.timeline.close();
            }
            case "hud" -> {
                HoloConfig.forcedHudMotion = HoloConfig.HudMotion.valueOf(a[1].toUpperCase(java.util.Locale.ROOT));
                HoloConfig.refresh();
            }
            case "peek" -> HoloOthers.peekSelf = a[1].equals("on");
            case "glow" -> HoloRender.glowOn = a[1].equals("on");
            case "tex" -> HoloDemo.showTexture = a[1].equals("on");
            case "style" -> { // style holo|flat|auto: the projected look on or off (K menu, System cards, first power)
                HoloConfig.forcedStyle = a[1].equals("auto") ? null : HoloConfig.StatusStyle.valueOf(a[1].toUpperCase(java.util.Locale.ROOT));
                HoloConfig.refresh();
            }
            case "arm" -> Wrist.forcedSkin = switch (a[1]) {
                case "slim" -> net.minecraft.client.resources.DefaultPlayerSkin.get(new java.util.UUID(0, 6));
                case "wide" -> net.minecraft.client.resources.DefaultPlayerSkin.get(new java.util.UUID(0, 15));
                default -> null;
            };
            case "clicktab" -> {
                // click a tab through the real input path: project its centre to the screen, then click there
                if (!(Minecraft.getInstance().gui.screen() instanceof HoloStatusScreen screen)) return "no holo screen";
                float[] gui = new float[2];
                if (!screen.tabCentreOnScreen(Integer.parseInt(a[1]), gui)) return "tab not on screen";
                var ev = new net.minecraft.client.input.MouseButtonEvent(gui[0], gui[1], new net.minecraft.client.input.MouseButtonInfo(1, 0));
                screen.mouseClicked(ev, false);
                return String.format(java.util.Locale.ROOT, "clicked %.1f,%.1f -> tab %s", gui[0], gui[1], screen.tab());
            }
            case "log" -> {
                org.joml.Vector3f c = new org.joml.Vector3f();
                p.pose.transformPosition(p.w / 2f, p.h / 2f, 0, c);
                return String.format(java.util.Locale.ROOT, "hud dx=%.2f dy=%.2f lag=%.2f/%.2f ppu=%d t=%.0f sx=%.3f sy=%.3f grow=%.2f edges=%.2f "
                        + "glass=%.2f beam=%.2f wrist=%s centre(world-rel)=%s anchor=%s", HudMotion.dx(), HudMotion.dy(),
                        p.spring.yaw(), p.spring.pitch(), p.ppu, p.timeline.ms(), p.sx, p.sy, p.timeline.grow(HoloConfig.reducedMotion),
                        p.edgeAlpha, p.glassAlpha, p.beam, WRIST, c, p.anchor);
            }
            default -> {
                return "unknown holo line: " + args;
            }
        }
        return "ok";
    }

    private static void reset() {
        UiHooks.setFocus(FOCUS_OWNER, 0f);
        HoloOthers.clear();
        demoMode = 0;
        DEMO.timeline.hold(false);
        DEMO.timeline.close();
        sent = HoloKind.NONE;
    }
}
