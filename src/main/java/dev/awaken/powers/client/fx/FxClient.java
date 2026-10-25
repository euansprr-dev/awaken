package dev.awaken.powers.client.fx;

import com.google.common.reflect.TypeToken;
import dev.awaken.powers.PowerType;
import dev.awaken.powers.fx.CastAnim;
import dev.awaken.powers.fx.CastClips;
import dev.awaken.powers.fx.FxEvent;
import dev.awaken.powers.fx.FxKind;
import dev.awaken.powers.fx.FxStyle;
import dev.awaken.powers.fx.PowerFxDefs;
import dev.awaken.powers.net.FxPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client half of the power FX system (docs/modules/power-fx.md): receives FX events and builds them, ticks and draws
 * the live effects, poses casters, and gives the local player's own key presses an instant wind-up before the
 * server answers.
 */
public final class FxClient {
    private FxClient() {}

    public static void init(IEventBus modBus) {
        PowerFxConfig.register();
        CastAnims.init();
        modBus.addListener(RegisterParticleProvidersEvent.class, FxSprites::register);
        modBus.addListener(RegisterClientPayloadHandlersEvent.class,
                e -> e.register(FxPayloads.Fx.TYPE, (msg, ctx) -> onEvent(msg.e())));
        modBus.addListener(RegisterGuiLayersEvent.class, FxScreen::register);
        modBus.addListener(RegisterRenderStateModifiersEvent.class,
                e -> e.registerEntityModifier(new TypeToken<EntityRenderer<Entity, EntityRenderState>>() {}, CastAnims::modify));

        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ClientTickEvent.Post.class, e -> tick());
        bus.addListener(ExtractLevelRenderStateEvent.class, FxRender::onExtract);
        bus.addListener(SubmitCustomGeometryEvent.class, FxRender::onSubmit);
        bus.addListener(ViewportEvent.ComputeFov.class, FxScreen::onFov);
        bus.addListener(ViewportEvent.ComputeCameraAngles.class, FxScreen::onAngles);
        // lowest: our lean is pushed last and popped first, and a later cancel can't strand it
        bus.addListener(EventPriority.LOWEST, RenderPlayerEvent.Pre.class, CastAnims::onPlayerPre);
        bus.addListener(EventPriority.HIGHEST, RenderPlayerEvent.Post.class, CastAnims::onPlayerPost);
        bus.addListener(EventPriority.LOW, RenderHandEvent.class, CastAnims::onHand);
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> reset());
        bus.addListener(ClientPlayerNetworkEvent.Clone.class, e -> reset());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        try {
            FxWorld.tick();
            CastAnims.tick();
            FxScreen.tick();
        } catch (RuntimeException ex) {
            // effects must never take the game down: drop them all and carry on
            dev.awaken.Awaken.LOG.warn("Power FX tick failed; clearing effects", ex);
            reset();
            return;
        }
        LocalPlayer p = mc.player;
        if (p != null) {
            for (Shape s : FxWorld.SHAPES) {
                if (s.live && (s.flags & Shape.AIM) != 0 && s.follow == p.getId()) FxBeams.tickAim(s, p);
            }
        }
    }

    private static void reset() {
        FxWorld.reset();
        CastAnims.reset();
        FxScreen.reset();
    }

    /** An FX event from the server. */
    static void onEvent(FxEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        CastAnim anim = e.anim();
        if (anim != CastAnim.NONE && e.caster() >= 0) {
            boolean own = mc.player != null && e.caster() == mc.player.getId();
            // the local key press already started this pose; don't restart it when the server echoes it
            if (!(own && CastAnims.recently(e.caster(), anim, 8))) CastAnims.play(e.caster(), anim, 0, CastClips.family(e));
        }
        try {
            FxRecipes.build(e);
        } catch (RuntimeException ex) {
            // an effect must never take the game down; log once per kind
            logOnce(e.kind(), ex);
        }
    }

    private static final boolean[] LOGGED = new boolean[FxKind.values().length];

    private static void logOnce(FxKind kind, RuntimeException ex) {
        if (LOGGED[kind.ordinal()]) return;
        LOGGED[kind.ordinal()] = true;
        dev.awaken.Awaken.LOG.warn("Power FX {} failed to build", kind, ex);
    }

    // ---- hooks for the power client (prediction and feel) -----------------------------------------------------------

    /** The local player pressed a power key and the cast was sent: pose and wind up at once. */
    public static void predictCast(LocalPlayer p, PowerType type) {
        PowerFxDefs.Def def = PowerFxDefs.get(type.path);
        if (def == null) return;
        boolean channel = type.mode == PowerType.Mode.CHANNEL;
        CastAnim anim = channel && def.windAnim() != null ? def.windAnim() : def.anim();
        if (anim != CastAnim.NONE) CastAnims.play(p.getId(), anim, 0, type.family);
        if (channel && def.windup() == FxKind.CHARGE && FxWorld.findAttached(p.getId(), Shape.DISC, FxRecipes.E_CHARGE) == null) {
            Vec3 at = p.position();
            if (FxWorld.begin(at.x, at.y, at.z, p.getId() * 7919 + FxWorld.ticks)) {
                FxStyle st = def.style();
                FxRecipes.startCharge(p, st, 1f, Math.max(10, type.maxDuration) + 8, p.getId());
            }
        }
    }

    /** A wake behind the local player for client-side movement passives (Wall Run): a trail that extends while it runs. */
    public static void localTrail(LocalPlayer p, FxStyle style) {
        onEvent(FxEvent.of(FxKind.TRAIL, style, p.position()).caster(p).ticks(12).seed(p.getId()).build());
    }

    // ---- for other modules' weapons (Arms, SPEC §24): the same engine, budget and settings --------------------------

    /**
     * A blade's arc that moves with {@code wielder}: a crescent about their eyes + {@code offset}, in the plane of
     * {@code axis0} (angle 0) and {@code axis90} (90 degrees; world-space unit vectors), from {@code startDeg} through
     * {@code sweepDeg}, at {@code radius} with a band of {@code band} (the blade's length), its head crossing in
     * {@code sweepTicks}. The local player's own is drawn softer in first person.
     */
    public static void slash(Entity wielder, Vec3 offset, Vec3 axis0, Vec3 axis90, float startDeg, float sweepDeg, float radius, float band,
                             int sweepTicks, int argb) {
        Shape s = blade(wielder, offset, sweepTicks, argb);
        if (s == null) return;
        s.nx = (float) axis0.x;
        s.ny = (float) axis0.y;
        s.nz = (float) axis0.z;
        s.ux = (float) axis90.x;
        s.uy = (float) axis90.y;
        s.uz = (float) axis90.z;
        s.c = (float) Math.toRadians(startDeg);
        s.range = (float) Math.toRadians(sweepDeg);
        s.a = radius;
        s.b = band;
    }

    /** A thrust's streak that moves with {@code wielder}: from their eyes + {@code offset} along {@code dir}, {@code from}..{@code to} blocks out. */
    public static void thrust(Entity wielder, Vec3 offset, Vec3 dir, float from, float to, float width, int sweepTicks, int argb) {
        Shape s = blade(wielder, offset, sweepTicks, argb);
        if (s == null) return;
        s.flags |= Shape.THRUST;
        s.nx = (float) dir.x;
        s.ny = (float) dir.y;
        s.nz = (float) dir.z;
        s.a = (from + to) * 0.5f;
        s.b = (to - from) * 0.5f;
        s.c = width;
    }

    private static @org.jspecify.annotations.Nullable Shape blade(Entity wielder, Vec3 offset, int sweepTicks, int argb) {
        Vec3 at = wielder.getEyePosition();
        if (!FxWorld.begin(at.x, at.y, at.z, wielder.getId())) return null;
        int sweep = Math.max(1, sweepTicks);
        Shape s = FxWorld.shape(Shape.SLASH, sweep * 2 + 6);
        if (s == null) return null;
        s.x = at.x;
        s.y = at.y;
        s.z = at.z;
        s.ex = offset.x;
        s.ey = offset.y;
        s.ez = offset.z;
        s.follow = wielder.getId();
        s.anchor = Anchors.EYE;
        s.d = sweep;
        s.c0 = argb;
        s.c1 = 0xFFFFFFFF;
        s.fadeIn = 0;
        s.fadeOut = 3;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && wielder.getId() == mc.player.getId()) s.flags |= Shape.OWN;
        return s;
    }

    /** A blade landing at {@code at}, travelling along {@code dir}: sparks, chips or wisps by what it meets. */
    public static void bladeHit(Vec3 at, Vec3 dir, float size, dev.awaken.core.api.BodyMaterial material) {
        if (!FxWorld.begin(at.x, at.y, at.z, (int) (at.x * 31 + at.z * 17 + FxWorld.ticks))) return;
        FxRecipes.bladeHit(at.x, at.y, at.z, dir.x, dir.y, dir.z, size, material);
    }

    /** Cast animations are switched on in the power FX settings. */
    public static boolean animations() {
        return PowerFxConfig.animations();
    }

    /** The local player let go of a channelled power key. */
    public static void predictRelease(LocalPlayer p) {
        CastAnims.release(p.getId());
    }

    /** Combat feel from the server: the caster's own pose pauses for the hit-stop too. */
    public static void onFeel(int hitEntityId, int hitstopMs) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && hitstopMs > 0) CastAnims.hold(mc.player.getId(), hitstopMs);
    }

    /** A beam sent by the older beam payload (kept for compatibility): built as a layered FX beam. */
    public static void legacyBeam(int casterId, double x, double y, double z, int colour, int ticks, float width, float range, boolean follow) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity caster = mc.level.getEntity(casterId);
        Vec3 from = caster == null ? new Vec3(x, y, z) : caster.getEyePosition();
        FxEvent e = FxEvent.of(FxKind.BEAM, dev.awaken.powers.Fx.styleFor(colour), from).caster(caster).to(new Vec3(x, y, z)).colour(colour)
                .aux(width).scale(range).ticks(ticks).flag(FxEvent.FOLLOW, follow).seed(casterId * 31 + ticks).build();
        onEvent(e);
    }
}
