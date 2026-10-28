package dev.awaken.powers.client.fx;

import com.google.common.reflect.TypeToken;
import dev.awaken.core.api.FirstPerson;
import dev.awaken.powers.PowerType;
import dev.awaken.powers.fx.CastAnim;
import dev.awaken.powers.fx.CastClips;
import dev.awaken.powers.fx.CastProfiles;
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
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
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
        // where a cast's palm, core and foot are on a drawn body (Muzzles)
        modBus.addListener(EntityRenderersEvent.AddLayers.class, e -> {
            for (var type : e.getSkins()) {
                var r = e.getPlayerRenderer(type);
                if (r != null) MuzzleLayer.addTo(r);
            }
        });
        // casts in first person are Hands': the stamp's boot, the lit palm, the clips' hand-overs
        FirstPerson.world("powers_stomp", 350, CastLegs::pose);
        FirstPerson.view(CastLegs::glow);
        FirstPerson.listen(FxClient::onFirstPerson);

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
            LadderFx.tick();
            CastAnims.tick();
            FxScreen.tick();
            CastLens.tick();
            drainDeferred();
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
        java.util.Arrays.fill(DEFERRED, null);
        head = count = 0;
        FxWorld.reset();
        LadderFx.reset();
        CastAnims.reset();
        FxScreen.reset();
        CastLens.reset();
    }

    /** An FX event from the server. */
    static void onEvent(FxEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        e = LadderFx.restyle(e);
        CastAnim anim = e.anim();
        boolean own = mc.player != null && e.caster() == mc.player.getId();
        // the caster's level picks the row: the same timing, that level's gesture (SPEC 34)
        CastProfiles.Profile prof = e.caster() >= 0 ? CastProfiles.at(CastProfiles.byId(e.profile()), dev.awaken.powers.Fx.levelOf(e)) : null;
        if (prof != null) {
            onProfile(e, prof, own);
        } else if (anim != CastAnim.NONE && e.caster() >= 0) {
            // the local key press already started this pose; don't restart it when the server echoes it
            if (!(own && CastAnims.recently(e.caster(), anim, 8))) CastAnims.play(e.caster(), anim, 0, CastClips.family(e));
        }
        if (defer(e)) return;
        build(e);
    }

    private static void build(FxEvent e) {
        try {
            // the power's own recipe, then the look layers of its ladder steps on what it drew (SPEC 34.5)
            LadderFx.begin();
            FxRecipes.build(e);
            LadderFx.decorate(e);
        } catch (RuntimeException ex) {
            // an effect must never take the game down; log once per kind
            logOnce(e.kind(), ex);
        }
    }

    /** A cast profile's beat from the server: everyone but the caster starts its clips here (the caster's key press did). */
    private static void onProfile(FxEvent e, CastProfiles.Profile prof, boolean own) {
        switch (e.beat()) {
            case CastProfiles.BEGIN -> {
                // your own key press already started both views; a cast that began on the server alone (a command) starts them here
                if (!(own && CastAnims.recentlyProfile(e.caster(), prof.id(), 8))) {
                    CastAnims.playProfile(e.caster(), prof, CastClips.family(e));
                    if (own && animations()) FirstPerson.play(prof.begin1p());
                }
            }
            case CastProfiles.RELEASE -> {
                // a power with no wind-up has only this event: it starts the pose too (the caster's own press did already)
                if (CastAnims.profileOf(e.caster()) != prof.id()) {
                    CastAnims.playProfile(e.caster(), prof, CastClips.family(e));
                    if (own && animations()) FirstPerson.play(prof.begin1p());
                }
            }
            case CastProfiles.END -> {
                if (own) stopFirstPerson(prof);
            }
            default -> {
            }
        }
    }

    private static void stopFirstPerson(CastProfiles.Profile prof) {
        FirstPerson.stop(prof.begin1p());
        if (prof.hold1p() != null) FirstPerson.stop(prof.hold1p());
    }

    /** A first-person clip's keyed event: a profile's begin clip hands over to its hold loop where it ends. */
    private static void onFirstPerson(String clip, String event) {
        if (!"hold".equals(event)) return;
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        CastProfiles.Profile prof = CastAnims.profile(p.getId());
        if (prof != null && prof.hold1p() != null && prof.begin1p().equals(clip) && CastAnims.holding(p.getId(), prof.id())) FirstPerson.play(prof.hold1p());
    }

    // ---- the effect waits for the body's release frame (decision D4) --------------------------------------------------

    /** A ring of events waiting for their tick; due ticks only grow (every wait is the profile's release minus the cast's age). */
    private static final FxEvent[] DEFERRED = new FxEvent[64];
    private static final int[] DEFERRED_AT = new int[64];
    private static int head, count;

    /** Kinds that never wait: the pose itself, the gathering light (it begins with the press) and the let-go. */
    private static boolean immediate(FxKind k) {
        return k == FxKind.POSE || k == FxKind.CHARGE || k == FxKind.CHARGE_END || k == FxKind.FLASH;
    }

    /**
     * An effect, sound or beam from a caster whose body is still on its way to the release (the arm to the aim, the knee to the
     * plant) waits for the tick the clips say, so everything lands on the frame the body does, on every client. Returns true when
     * it took the event.
     */
    private static boolean defer(FxEvent e) {
        if (e.caster() < 0 || immediate(e.kind())) return false;
        CastProfiles.Profile prof = CastProfiles.byId(CastAnims.profileOf(e.caster()));
        if (prof == null || prof.release() <= 0) return false;
        float age = CastAnims.profileAge(e.caster(), prof.id(), 1f);
        int wait = (int) Math.ceil(prof.release() - age);
        if (wait <= 0 || count == DEFERRED.length) return false;
        int at = (head + count) % DEFERRED.length;
        DEFERRED[at] = e;
        DEFERRED_AT[at] = FxWorld.ticks + wait;
        count++;
        return true;
    }

    private static void drainDeferred() {
        while (count > 0 && FxWorld.ticks >= DEFERRED_AT[head]) {
            FxEvent e = DEFERRED[head];
            DEFERRED[head] = null;
            head = (head + 1) % DEFERRED.length;
            count--;
            build(e);
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
    public static void predictCast(LocalPlayer p, PowerType type, int level) {
        PowerFxDefs.Def def = PowerFxDefs.get(type.path);
        if (def == null) return;
        boolean channel = type.mode == PowerType.Mode.CHANNEL;
        CastProfiles.Profile prof = CastProfiles.at(CastProfiles.byPower(type.path), level);
        CastAnim anim = channel && def.windAnim() != null ? def.windAnim() : def.anim();
        if (prof != null && animations()) {
            CastAnims.playProfile(p.getId(), prof, type.family);
            FirstPerson.play(prof.begin1p());
        } else if (anim != CastAnim.NONE) {
            CastAnims.play(p.getId(), anim, 0, type.family);
        }
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

    /** A wave rolling out along the ground from {@code from}'s feet along {@code dir} to {@code range}, ±{@code halfDeg} wide. */
    public static void groundWave(Entity from, Vec3 dir, float range, float halfDeg, int ticks, FxStyle style) {
        onEvent(FxEvent.of(FxKind.CONE, style, from.position()).caster(from).dir(dir).scale(range).aux(halfDeg).ticks(ticks)
                .seed(from.getId() * 31 + FxWorld.ticks).flag(FxEvent.QUIET, true).build());
    }

    /** A blade landing at {@code at}, travelling along {@code dir}: sparks, chips or wisps by what it meets. */
    public static void bladeHit(Vec3 at, Vec3 dir, float size, dev.awaken.core.api.BodyMaterial material) {
        if (!FxWorld.begin(at.x, at.y, at.z, (int) (at.x * 31 + at.z * 17 + FxWorld.ticks))) return;
        FxRecipes.bladeHit(at.x, at.y, at.z, dir.x, dir.y, dir.z, size, material);
    }

    /**
     * A shot meeting a raised guard (Arms, SPEC §25 "Shots"): a white-hot flash where it struck the steel and sparks
     * thrown back along {@code (dx, dy, dz)}, the way it came, in the weapon's light (rgb). {@code size} 1 is a
     * deflect's bright glance (with a ring off the edge), less a block's dull stop. {@code near}: the fighter's own
     * blade in first person, drawn even at sword's length from the eye, and sized for it.
     */
    public static void guardSpark(double x, double y, double z, double dx, double dy, double dz, int rgb, float size, boolean near) {
        if (!FxWorld.begin(x, y, z, (int) (x * 47 + z * 23) + FxWorld.ticks * 389)) return;
        FxRecipes.guardSpark(x, y, z, dx, dy, dz, 0xFF000000 | rgb, size, near);
    }

    /** A weapon's own light (Arms' streams): an ember that rises and cools to red. */
    public static final int MOTE_EMBER = 1;
    /** A wisp that drifts, swells and curls away to nothing. */
    public static final int MOTE_WISP = 2;
    /** A mote that hangs in the air near where it was let go and twinkles out. */
    public static final int MOTE_MOTE = 3;

    /**
     * Light lifting off a weapon at (x, y, z) with its own drift, in the weapon's colour (rgb), {@code size} blocks
     * across: one particle of the shared pool, under its budget and the particle setting (Arms, SPEC §25 "Weapons").
     * {@code near}: the fighter's own blade in first person, drawn even this close to the eye.
     */
    public static void weaponMote(int kind, double x, double y, double z, double vx, double vy, double vz, int rgb, float size, boolean near) {
        if (!FxWorld.begin(x, y, z, (int) (x * 131 + y * 37 + z * 71) + FxWorld.ticks * 7919 + kind)) return;
        if (FxWorld.R.f() > FxWorld.k) return; // the density setting thins them like any effect
        FxRecipes.weaponMote(kind, x, y, z, vx, vy, vz, 0xFF000000 | rgb, size, near ? dev.awaken.powers.fx.FxPool.NEAR : 0);
    }

    /** A tell's marks (Arms, SPEC §25 "Tells"): a glint, a flare with its ring, a sign over the head, a held glow. */
    public static final int TELL_GLINT = 1, TELL_FLARE = 2, TELL_SIGN = 3, TELL_GLOW = 4;

    /**
     * One mark of an enemy's tell at (x, y, z) in its class's colour (rgb), {@code size} blocks, living {@code life}
     * ticks; drawn even close to the eye (a blow is told at sword's length). The effects engine's budget and setting.
     */
    public static void tellMark(int mark, double x, double y, double z, int rgb, float size, int life) {
        if (!FxWorld.begin(x, y, z, (int) (x * 53 + z * 29) + FxWorld.ticks * 131 + mark)) return;
        FxRecipes.tellMark(mark, x, y, z, 0xFF000000 | rgb, size, life);
    }

    /** Cast animations are switched on in the power FX settings. */
    public static boolean animations() {
        return PowerFxConfig.animations();
    }

    /** The local player let go of a channelled power key. */
    public static void predictRelease(LocalPlayer p) {
        CastAnims.release(p.getId());
        CastProfiles.Profile prof = CastAnims.profile(p.getId());
        if (prof != null) stopFirstPerson(prof);
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
