package dev.awaken.body.ascent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.Awaken;
import dev.awaken.body.ascent.AscentContent;
import dev.awaken.body.ascent.AscentPayloads;
import dev.awaken.body.ascent.AscentState;
import dev.awaken.body.ascent.WingForm;
import dev.awaken.body.client.feel.FeelConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Wings of light on a player's back (the Ascent's Wings tier).
 * <p>
 * <b>Feathers</b> (SPEC §62, the look since 10 Nov 2026): each wing is {@link WingForm}'s three bones and 46 feathers in
 * boxes, drawn by {@link WingFeathers}. What the wings do is a state of their own, read from what the body does: they
 * open (a gather, a spread that runs out feather by feather, a settle), glide, beat in flight, tuck for a dive, beat
 * slow and whole in a hover, cup forward as the feet touch down, fold onto the back, stay there four seconds and fade
 * into the mark between the shoulder blades. A change of state is a 0.32 s crossfade from wherever the feathers were.
 * Each rung of the ladder has its own wings: one pair; Skyborn a larger pair and a smaller under it; the Sovereign's form
 * three gold pairs and a turning halo.
 * <p>
 * <b>Classic</b> ({@code wingsFeathers} off): the single sheet the wings were before, unchanged, kept for one release.
 */
final class WingLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    static final Identifier TEXTURE = Awaken.id("textures/ascent/wing.png");
    static final Identifier GLOW = Awaken.id("textures/ascent/wing_glow.png");

    /** What feathered wings are doing. */
    static final int FOLDED = 0, UNFOLD = 1, GLIDING = 2, FLYING = 3, DIVING = 4, HOVERING = 5, LANDING = 6, GATHER = 7;
    /** How brightly the halos burn in each. */
    private static final float[] GLOW_BY = {0.2F, 1F, 0.6F, 0.9F, 0.5F, 1F, 1F, 0.7F};
    /** A change of state crossfades over this many ticks (0.32 s). */
    private static final float BLEND_TICKS = 6.4F;
    /** Folded wings stay on the back this long, then fade into the mark. */
    private static final int FOLDED_TICKS = 80;

    /**
     * For your own eyes the wings are drawn from the body but set a little ahead of the eye and a little under it, so
     * the outer feathers sit at the frame's lower sides and are seen from above (the body is not drawn in first person:
     * where the wings really are, nothing of them is in view; level with the eye they are thin lines seen edge-on, and
     * higher they fill the view in a dive: an offline render of this code decided the numbers, docs/movement/PLAN.md).
     * Blocks; and how solid they are this close.
     */
    static float fpAhead = 0.8F, fpUp = -0.12F, fpAlpha = 0.8F;

    /** One view of a slot's wings this frame (the renderers run after every submit, so each view keeps its own). */
    private static final class View {
        final float[][] pose = new float[3][WingForm.N];
        float[][] pairs = WingForm.PAIRS_WINGS;
        int[] pal = WingFeathers.LIGHT;
        float alpha, glow, turn;
        int lod;
        boolean first, ring;
        /** A renderer a pair (each pair is its own draw: WingPipelines.feather), and the halos'. */
        final SubmitNodeCollector.CustomGeometryRenderer[] feathers = {(pose, vc) -> feathers(pose, vc, 0), (pose, vc) -> feathers(pose, vc, 1),
                (pose, vc) -> feathers(pose, vc, 2)};
        final SubmitNodeCollector.CustomGeometryRenderer lights = this::lights;

        private void feathers(PoseStack.Pose pose, VertexConsumer vc, int i) {
            if (i >= pairs.length || probePairs > 0 && i >= probePairs) return;
            WingFeathers.pair(pose, vc, this.pose[i], 0, pairs[i], i, pal, alpha, lod, first);
            if (i == 0 && ring && !first) WingFeathers.ring(pose, vc, turn, alpha);
        }

        private void lights(PoseStack.Pose pose, VertexConsumer vc) {
            if (!probeHalos) return;
            WingFeathers.faceEye(pose, AscentRender.eye());
            int n = probePairs > 0 ? Math.min(probePairs, pairs.length) : pairs.length;
            for (int i = 0; i < n; i++) WingFeathers.halos(pose, vc, this.pose[i], 0, pairs[i], i, pal[WingFeathers.HALO], glow, alpha);
            if (ring && !first) WingFeathers.ringGlow(pose, vc, alpha);
        }
    }

    /** One player's wings. */
    private static final class Wings {
        int entity = -1;
        // classic: how open the sheet is, eased per tick
        float spread, spreadO, beat, beatO, sweep, sweepO;
        int lastFlap = -100, flapDone = -100;
        boolean open;
        // what this frame draws (the renderers run after every submit, so each slot keeps its own)
        float dSpread, dBeat, dSweep;
        /** Drawn for your own eyes (first person): only the outer feathers, faded in, never the root at the camera. */
        boolean dFirst;
        // light, not cloth: a see-through body tinted toward the Signal's cyan, and a soft halo of glow over it
        // (radiant gold while their bearer holds the Sovereign form: SPEC §17.19)
        final SubmitNodeCollector.CustomGeometryRenderer body = (pose, vc) -> wings(pose, vc, this,
                SovereignClient.radiant(entity) ? 0x9CFFE6A8 : 0x94A8DCFF, 1F);
        final SubmitNodeCollector.CustomGeometryRenderer glow = (pose, vc) -> wings(pose, vc, this,
                SovereignClient.radiant(entity) ? 0x48FFC05A : 0x3C7FC0FF, 1.07F);

        // feathers: the state, the client tick it began, the pose it left (the crossfade's start)
        int mode = FOLDED, modeAt = -1000;
        final float[] from = new float[WingForm.N];
        /** How far out of the mark the wings are (0 hidden, 1 there), eased per tick. */
        float show, showO;
        /** What the body is doing this tick (from touch()). */
        AscentState state = AscentState.NONE;
        double speed, hspeed, vy;
        boolean flourish, flew, skyborn;
        /** A takeoff's crouch: how far the folded wings are lifted (0 none). */
        float gather;
        int gatherAt = -100;
        /** The flare's approach or landing last heard (the wings cup only for a landing that is one). */
        int flareAt = -100;
        final View third = new View(), own = new View();
    }

    private static final Wings[] WINGS = new Wings[32];

    static {
        for (int i = 0; i < WINGS.length; i++) WINGS[i] = new Wings();
    }

    private WingLayer(AvatarRenderer<?> parent) {
        super(parent);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void addTo(AvatarRenderer<?> r) {
        ((AvatarRenderer) r).addLayer(new WingLayer(r));
    }

    private static Wings find(int id) {
        for (Wings w : WINGS) if (w.entity == id) return w;
        return null;
    }

    private static Wings slot(Entity e) {
        Wings w = find(e.getId());
        if (w != null) return w;
        for (Wings f : WINGS) if (f.entity < 0) {
            f.entity = e.getId();
            f.spread = f.spreadO = 0;
            f.beat = f.beatO = 0;
            f.sweep = f.sweepO = 0;
            f.lastFlap = AscentMoves.ticks; // opening is a beat
            f.flapDone = f.lastFlap;
            f.mode = FOLDED;
            f.modeAt = AscentMoves.ticks - 1000;
            System.arraycopy(WingForm.FOLD, 0, f.from, 0, WingForm.N);
            f.show = f.showO = 0;
            f.flew = f.flourish = false;
            f.gather = 0;
            f.gatherAt = f.flareAt = -100;
            f.state = AscentState.NONE;
            return f;
        }
        return null;
    }

    // ---- who bears wings (the mark shows on a back whose wings are closed) ---------------------------------------------

    /** Others seen with their wings open this session, and whether in flight (Skyborn's two pairs): entity ids. */
    private static final int[] SEEN = new int[64];
    private static final boolean[] SEEN_SKY = new boolean[64];

    static {
        java.util.Arrays.fill(SEEN, -1);
    }

    private static void seen(int id, boolean soar) {
        int free = -1;
        for (int i = 0; i < SEEN.length; i++) {
            if (SEEN[i] == id) {
                SEEN_SKY[i] |= soar;
                return;
            }
            if (SEEN[i] < 0 && free < 0) free = i;
        }
        if (free < 0) free = Math.floorMod(id, SEEN.length);
        SEEN[free] = id;
        SEEN_SKY[free] = soar;
    }

    private static int seenAt(int id) {
        for (int i = 0; i < SEEN.length; i++) if (SEEN[i] == id) return i;
        return -1;
    }

    private static boolean bearer(int id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getId() == id) return AscentMoves.has(AscentPayloads.Tier.WINGS);
        return seenAt(id) >= 0;
    }

    private static boolean skyborn(int id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getId() == id) return AscentMoves.has(AscentPayloads.Tier.SKYBORN);
        int i = seenAt(id);
        return i >= 0 && SEEN_SKY[i];
    }

    static void reset() {
        studioEntity = studioMode = studioRung = -1;
        java.util.Arrays.fill(SEEN, -1);
        for (Wings w : WINGS) w.entity = -1;
        for (int i = 0; i < FLOURISH.length; i++) FLOURISH[i] = -1;
    }

    /** Wings opened for a moment outside any state (a scene): entity ids and the client tick they fold at. */
    private static final int[] FLOURISH = {-1, -1, -1, -1}, FLOURISH_UNTIL = new int[4];

    /** Opens this entity's wings for {@code ticks}, with a beat in the middle, then folds them. */
    static void flourish(int entityId, int ticks) {
        int slot = 0;
        for (int i = 0; i < FLOURISH.length; i++) {
            if (FLOURISH[i] == entityId || FLOURISH[i] < 0) {
                slot = i;
                break;
            }
        }
        FLOURISH[slot] = entityId;
        FLOURISH_UNTIL[slot] = AscentMoves.ticks + ticks;
    }

    /** A flap for this entity (a beat is a quick down-stroke then a slower recovery). */
    static void flap(int entityId) {
        Wings w = find(entityId);
        if (w != null) w.lastFlap = AscentMoves.ticks;
    }

    /**
     * A takeoff's crouch (SPEC §62): the folded wings come out of the mark and lift, {@code k} of the way (call every
     * tick of the crouch; they settle back by themselves when the calls stop).
     */
    static void gather(Entity e, float k) {
        Wings w = slot(e);
        if (w == null) return;
        w.gather = Mth.clamp(k, 0, 1);
        w.gatherAt = AscentMoves.ticks;
    }

    /**
     * The flare (SPEC §62): this entity is coming down slow and level, or has just landed so. Open wings begin to cup;
     * wings that close on the ground within a moment of it finish the flare and shed light instead of just folding.
     */
    static void flare(int entityId) {
        Wings w = find(entityId);
        if (w != null) w.flareAt = AscentMoves.ticks;
    }

    // ---- the studio (a look tour, /awaken wings show) ------------------------------------------------------------------

    /** An entity whose wings are held in one state (-1 none) and at one rung (-1 its own; 0 wings, 1 Skyborn, 2 the Sovereign's form). */
    private static int studioEntity = -1, studioMode = -1, studioRung = -1;
    static final String[] MODES = {"folded", "open", "glide", "fly", "dive", "hover", "land", "gather"};

    /**
     * Holds this entity's wings in a state by name (see {@link #MODES}; "off" lets go) at a rung ("wings", "skyborn",
     * "sovereign", or "" for its own), so they can be looked at from every side without flying. False for a name it
     * doesn't know.
     */
    static boolean studio(int entityId, String mode, String rung) {
        if (mode.equals("off")) {
            studioEntity = studioMode = studioRung = -1;
            return true;
        }
        int m = java.util.Arrays.asList(MODES).indexOf(mode);
        if (m < 0) return false;
        studioEntity = entityId;
        studioMode = m;
        studioRung = switch (rung) {
            case "wings" -> 0;
            case "skyborn" -> 1;
            case "sovereign" -> 2;
            default -> -1;
        };
        Wings w = find(entityId);
        if (w != null && w.mode == m && m != FOLDED) w.mode = FOLDED; // asked for again: the state plays from the fold
        return true;
    }

    /**
     * Probes for a look tour or a slow machine (/awaken wings set probe_*): a level of detail forced on every wing (-1
     * none), the most pairs drawn (0 all), the Sovereign's ring and the halos drawn or not. They change what is drawn,
     * never the wings' state.
     */
    private static int probeLod = -1, probePairs = 0;
    private static boolean probeRing = true, probeHalos = true;

    /** A number of the wings' look set by name while the game runs (/awaken wings set); false for a name it doesn't know. */
    static boolean tune(String key, float value) {
        switch (key) {
            case "fp_ahead" -> fpAhead = value;
            case "fp_up" -> fpUp = value;
            case "fp_alpha" -> fpAlpha = Mth.clamp(value, 0, 1);
            case "probe_lod" -> probeLod = (int) value;
            case "probe_pairs" -> probePairs = (int) value;
            case "probe_ring" -> probeRing = value != 0;
            case "probe_halos" -> probeHalos = value != 0;
            default -> {
                return false;
            }
        }
        return true;
    }

    private static int rungOf(Wings w) {
        if (w.entity == studioEntity && studioRung >= 0) return studioRung;
        return SovereignClient.radiant(w.entity) ? 2 : w.skyborn ? 1 : 0;
    }

    /** True on the tick a feathered wing's downstroke begins (the beat's sound and the air it moves follow the wing). */
    static boolean downstroke(int entityId) {
        Wings w = find(entityId);
        if (w == null) return false;
        int age = AscentMoves.ticks - w.modeAt;
        return w.mode == FLYING && age % WingForm.FLY_TICKS == 0 || w.mode == HOVERING && age % WingForm.HOVER_TICKS == 0;
    }

    /** Whether your own wings are drawn through your own eyes now: open, never folded or lifting for a takeoff (they would fill the view). */
    static boolean inView(int entityId) {
        Wings w = find(entityId);
        return w != null && w.mode != FOLDED && w.mode != GATHER;
    }

    /** What this entity's feathered wings are doing (FOLDED when it has none out). */
    static int mode(int entityId) {
        Wings w = find(entityId);
        return w == null ? FOLDED : w.mode;
    }

    /** How far the beat lifts and drops the body this frame, in texels along its own up (the figure rides its wings). */
    static float bob(int entityId, float pt) {
        Wings w = find(entityId);
        if (w == null || !FeelConfig.wingsFeathers() || FeelConfig.reduced() || w.mode != FLYING && w.mode != HOVERING) return 0;
        poseNow(w, pt, 0, TMP);
        return TMP[WingForm.BOB];
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        // anyone with wings open gets a slot; slots ease shut and free themselves
        if (mc.player != null) {
            AscentMoves.velocity(VEL);
            touch(mc.player, AscentMoves.state(), AscentMoves.speed(), AscentMoves.flapAt, Math.sqrt(VEL[0] * VEL[0] + VEL[2] * VEL[2]), VEL[1], false);
        }
        for (AscentRemote.Slot r : AscentRemote.SLOTS) {
            if (r.entity < 0) continue;
            Entity e = mc.level.getEntity(r.entity);
            if (e != null) {
                double vx = e.getX() - e.xo, vy = e.getY() - e.yo, vz = e.getZ() - e.zo;
                if (r.state.wings) seen(r.entity, r.state == AscentState.SOAR);
                touch(e, r.state, Math.sqrt(vx * vx + vy * vy + vz * vz), -100, Math.sqrt(vx * vx + vz * vz), vy, false);
            }
        }
        for (int i = 0; i < FLOURISH.length; i++) {
            if (FLOURISH[i] < 0) continue;
            int left = FLOURISH_UNTIL[i] - AscentMoves.ticks;
            Entity e = mc.level.getEntity(FLOURISH[i]);
            if (left <= 0 || e == null) {
                FLOURISH[i] = -1;
                continue;
            }
            // one slow beat in the middle, the wings showing their whole span
            if (e != mc.player) seen(e.getId(), false);
            touch(e, AscentState.GLIDE, 0, left == 22 ? AscentMoves.ticks : -100, 0, 0, true);
        }
        if (studioEntity >= 0) {
            Entity held = mc.level.getEntity(studioEntity);
            if (held != null) slot(held);
        }
        for (Wings w : WINGS) {
            if (w.entity < 0) continue;
            w.spreadO = w.spread;
            w.beatO = w.beat;
            w.sweepO = w.sweep;
            Entity e = mc.level.getEntity(w.entity);
            if (e == null) {
                w.entity = -1;
                continue;
            }
            float target = w.open ? 1F : 0F;
            // opening is quick after a short gather (anticipation); folding is a slower settle (follow-through)
            w.spread += (target - w.spread) * (w.open ? 0.32F : 0.16F);
            int since = AscentMoves.ticks - w.lastFlap;
            w.beat = since < 12 ? beatCurve(since) : 0.06F * Mth.sin(AscentMoves.ticks * 0.21F + w.entity);
            featherTick(mc, w, e);
            if (!w.open && w.spread < 0.01F && w.show <= 0 && w.mode == FOLDED) w.entity = -1;
            w.open = false; // refreshed by touch() every tick while the state lasts
        }
    }

    private static final double[] VEL = new double[3];

    private static void touch(Entity e, AscentState st, double speed, int flapAt, double hspeed, double vy, boolean flourish) {
        if (!st.wings) return;
        Wings w = slot(e);
        if (w == null) return;
        // a scene's flourish only holds wings open that nothing else has: a body really flying keeps its own wings' state
        if (flourish && w.open && !w.flourish) return;
        w.open = true;
        w.state = st;
        w.speed = speed;
        w.hspeed = hspeed;
        w.vy = vy;
        w.flourish = flourish;
        if (!flourish) w.flew = true;
        if (flapAt > w.lastFlap) w.lastFlap = flapAt;
        float sweepTarget = (float) Mth.clamp((speed - 0.8) / 1.6, 0, 1);
        w.sweep += (sweepTarget - w.sweep) * 0.15F;
    }

    // ---- feathers: what the wings do --------------------------------------------------------------------------------

    private static final float[] TMP = new float[WingForm.N], TIP = new float[3];
    private static final double[] F = new double[9];

    private static void set(Wings w, int mode) {
        // the crossfade starts from wherever the feathers are this tick
        poseNow(w, 0, 0, TMP);
        System.arraycopy(TMP, 0, w.from, 0, WingForm.N);
        w.mode = mode;
        w.modeAt = AscentMoves.ticks;
    }

    private static void featherTick(Minecraft mc, Wings w, Entity e) {
        w.showO = w.show;
        w.skyborn = skyborn(w.entity);
        int age = AscentMoves.ticks - w.modeAt;
        boolean gathering = AscentMoves.ticks - w.gatherAt <= 1;
        if (w.entity == studioEntity && studioMode >= 0) {
            // held for looking at: the one state, played from its start, and nothing else decides
            w.show = Math.min(1, w.show + 0.34F);
            w.gather = 1;
            if (w.mode != studioMode) set(w, studioMode);
            return;
        }
        if (w.open) {
            w.show = Math.min(1, w.show + 0.34F);
            if (w.mode == FOLDED || w.mode == GATHER) {
                // a takeoff's wings are already lifted: the opening starts at its spread
                boolean lifted = w.mode == GATHER;
                set(w, UNFOLD);
                if (lifted) w.modeAt -= 4;
            } else if (w.mode != UNFOLD || age >= 13) {
                // coming in to land slow and level, the wings cup before the feet touch
                int next = AscentMoves.ticks - w.flareAt <= 1 && !w.flourish ? LANDING : pick(w);
                if (next != w.mode) set(w, next);
                else if (w.mode == FLYING && w.lastFlap > w.flapDone) set(w, FLYING); // a flap you asked for starts a fresh stroke
            }
            w.flapDone = w.lastFlap;
            if (!FeelConfig.reduced() && FeelConfig.wingsFeathers()) shed(mc, w, e);
        } else if (gathering) {
            w.show = Math.min(1, w.show + 0.34F);
            if (w.mode != GATHER) set(w, GATHER);
        } else {
            switch (w.mode) {
                case FOLDED -> {
                    if (age > FOLDED_TICKS) w.show = Math.max(0, w.show - 0.1F);
                }
                case LANDING -> {
                    // cupped already on the way down: the touch sheds its light once, then the wings fold
                    if (w.flew && !FeelConfig.reduced() && FeelConfig.wingsFeathers() && AscentMoves.ticks - w.flareAt <= 3) burst(mc, w, e);
                    w.flew = false;
                    if (age >= WingForm.LAND_TICKS) set(w, FOLDED);
                }
                case GATHER -> set(w, FOLDED);
                default -> {
                    // closed: a flare cups the wings as the feet touch; anything else folds them where they are
                    boolean flare = w.flew && !w.flourish && w.mode != UNFOLD && AscentMoves.ticks - w.flareAt <= 3;
                    set(w, flare ? LANDING : FOLDED);
                    if (flare && !FeelConfig.reduced() && FeelConfig.wingsFeathers()) burst(mc, w, e);
                    w.flew = false;
                }
            }
        }
    }

    /** Glide, beat, tuck or hover, from what the body is doing (with a margin either side, so it never flickers). */
    private static int pick(Wings w) {
        if (w.flourish) return HOVERING;
        boolean dive = w.mode == DIVING ? w.vy < -0.45 * w.speed && w.speed > 0.7 : w.vy < -0.6 * w.speed && w.speed > 0.9;
        if (dive) return DIVING;
        if (w.state != AscentState.SOAR) return GLIDING;
        double still = w.mode == HOVERING ? 0.6 : 0.45;
        return w.hspeed < still && Math.abs(w.vy) < still ? HOVERING : FLYING;
    }

    /** The pose of this slot's wings now, {@code lag} seconds behind (a second or third pair follows the first). */
    private static void poseNow(Wings w, float pt, float lag, float[] out) {
        int age = AscentMoves.ticks - w.modeAt;
        float t = Math.max(0, (age + pt) / 20F - lag);
        boolean reduced = FeelConfig.reduced();
        float amp = reduced ? 0.5F : 1F, tremble = reduced ? 0 : 1;
        switch (w.mode) {
            case UNFOLD -> WingForm.unfold(t, out);
            // the tips tremble more the faster the air goes by (the model's own tremble at a middling speed)
            case GLIDING -> {
                float sweep = Mth.lerp(pt, w.sweepO, w.sweep);
                WingForm.glide(t, sweep, tremble * (0.6F + 0.8F * sweep), out);
            }
            // the beats count whole ticks round their own cycle (never a float of time that grows)
            case FLYING -> WingForm.beat(WingForm.GLIDE, (Math.floorMod(age, WingForm.FLY_TICKS) + pt) / 20F - lag, WingForm.FLY_PERIOD, amp, 0, out);
            case DIVING -> WingForm.dive(t, tremble, out);
            case HOVERING -> WingForm.beat(WingForm.HOVER, (Math.floorMod(age, WingForm.HOVER_TICKS) + pt) / 20F - lag, WingForm.HOVER_PERIOD,
                    0.9F * amp, 1, out);
            case LANDING -> WingForm.land(t, out);
            case GATHER -> WingForm.gather(WingForm.ease(w.gather), out);
            default -> System.arraycopy(WingForm.FOLD, 0, out, 0, WingForm.N);
        }
        float b = (age + pt) / BLEND_TICKS;
        if (b < 1) WingForm.mix(w.from, out, WingForm.ease(b), out);
    }

    /** A hard downstroke sheds a few square motes of light from the tips of the large wings. */
    private static void shed(Minecraft mc, Wings w, Entity e) {
        int age = AscentMoves.ticks - w.modeAt, phase;
        if (w.mode == FLYING) phase = age % WingForm.FLY_TICKS;
        else if (w.mode == HOVERING) phase = age % WingForm.HOVER_TICKS - 2;
        else return;
        if (phase < 2 || phase > 5 || mc.level == null || mc.level.getRandom().nextBoolean()) return;
        float[][] pairs = pairs(w);
        poseNow(w, 0, 0, TMP);
        AscentAnims.frame(e, 1F, F);
        for (int i = 0; i < pairs.length; i++) {
            if (pairs[i][0] <= 0.9F) continue;
            for (int s = -1; s <= 1; s += 2) mote(mc, e, s, pairs[i], i, -0.06, 0.03);
        }
    }

    /** The flare: feathers of light fall from both wings as the feet touch. */
    private static void burst(Minecraft mc, Wings w, Entity e) {
        if (mc.level == null) return;
        float[][] pairs = pairs(w);
        poseNow(w, 0, 0, TMP);
        AscentAnims.frame(e, 1F, F);
        for (int n = 0; n < 5; n++) {
            for (int s = -1; s <= 1; s += 2) mote(mc, e, s, pairs[0], 0, -0.02, 0.09);
        }
    }

    /** One mote near a wing's tip (the pose in TMP, the body's frame in F), drifting down and back. */
    private static void mote(Minecraft mc, Entity e, int s, float[] pair, int index, double fall, double scatter) {
        WingFeathers.tip(TMP, 0, s, pair, index, TIP);
        var r = mc.level.getRandom();
        // model space (x left, y down from the neck, z behind) out through the figure's frame (facing, up, right), which
        // turns about a point 0.9 over the feet
        double left = TIP[0] * 0.9375 * (0.55 + 0.45 * r.nextDouble()), up = 1.407 - TIP[1] * 0.9375 - 0.9, back = TIP[2] * 0.9375;
        double x = e.getX() - F[6] * left + F[3] * up - F[0] * back;
        double y = e.getY() + 0.9 - F[7] * left + F[4] * up - F[1] * back;
        double z = e.getZ() - F[8] * left + F[5] * up - F[2] * back;
        mc.level.addParticle(AscentContent.MOTE.get(), x, y, z, (r.nextDouble() - 0.5) * scatter, fall + (r.nextDouble() - 0.5) * scatter,
                (r.nextDouble() - 0.5) * scatter);
    }

    private static float[][] pairs(Wings w) {
        int rung = rungOf(w);
        return rung == 2 ? WingForm.PAIRS_SOVEREIGN : rung == 1 ? WingForm.PAIRS_SKYBORN : WingForm.PAIRS_WINGS;
    }

    /** Fills a view for this frame; false when there is nothing to draw. */
    private static boolean prepare(Wings w, View v, float pt, int lod, boolean first) {
        float show = Mth.lerp(pt, w.showO, w.show);
        if (show < 0.02F) return false;
        int rung = rungOf(w);
        boolean radiant = rung == 2;
        v.pairs = pairs(w);
        v.pal = radiant ? WingFeathers.GOLD : rung == 1 ? WingFeathers.SKY : WingFeathers.LIGHT;
        for (int i = 0; i < v.pairs.length; i++) poseNow(w, pt, v.pairs[i][2], v.pose[i]);
        v.alpha = show * (first ? fpAlpha : 1F);
        // through your own eyes the opening comes in as it spreads: folded, the wings would pass across the middle of the view
        if (first && w.mode == UNFOLD) v.alpha *= WingForm.ease((AscentMoves.ticks - w.modeAt + pt - 3) / 9F);
        v.glow = GLOW_BY[w.mode];
        v.lod = probeLod >= 0 ? Math.min(2, probeLod) : lod;
        v.first = first;
        v.ring = radiant && probeRing;
        // the halo turns half a radian a second: a whole turn in 251 ticks
        v.turn = (AscentMoves.ticks % 251 + pt) / 251F * Mth.TWO_PI;
        return true;
    }

    /** The mark's core breathes: a quarter larger and back, every 50 ticks. */
    private static float markPulse = 1;
    private static final SubmitNodeCollector.CustomGeometryRenderer MARK = (pose, vc) -> WingFeathers.mount(pose, vc, 0xDFF8FF, 1F, markPulse);

    /** One beat: down hard (to -1) over 4 ticks, back up past level over 8. */
    private static float beatCurve(int t) {
        if (t < 4) return -Mth.sin(t / 4F * Mth.HALF_PI);
        float u = (t - 4) / 8F;
        return -Mth.cos(u * Mth.HALF_PI) + 0.35F * Mth.sin(u * Mth.PI);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector out, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
        Wings w = find(state.id);
        float pt = state.partialTick;
        if (FeelConfig.wingsFeathers()) {
            boolean mark = !state.isInvisible && bearer(state.id);
            if (w == null && !mark) return;
            int lod = WingForm.lod(state.distanceToCameraSq);
            poseStack.pushPose();
            getParentModel().body.translateAndRotate(poseStack);
            if (mark && lod < 2) {
                markPulse = 1 + 0.25F * Mth.sin((AscentMoves.ticks % 50 + pt) / 50F * Mth.TWO_PI);
                out.submitCustomGeometry(poseStack, WingPipelines.feather(0), MARK);
            }
            if (w != null && prepare(w, w.third, pt, lod, false)) {
                for (int i = 0; i < w.third.pairs.length; i++) out.submitCustomGeometry(poseStack, WingPipelines.feather(i), w.third.feathers[i]);
                if (lod < 2) out.order(1).submitCustomGeometry(poseStack, WingPipelines.halo(), w.third.lights);
            }
            poseStack.popPose();
            return;
        }
        if (w == null) return;
        float spread = Mth.lerp(pt, w.spreadO, w.spread), beat = Mth.lerp(pt, w.beatO, w.beat), sweep = Mth.lerp(pt, w.sweepO, w.sweep);
        if (spread < 0.01F) return;
        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack);
        w.dFirst = false;
        draw(poseStack, out, w, spread, beat, sweep);
        poseStack.popPose();
    }

    /** First person: your own wings, drawn in the world from the body (only their outer feathers reach into view). */
    static boolean drawFirstPerson(PoseStack ps, SubmitNodeCollector out, Entity e, float pt) {
        Wings w = find(e.getId());
        if (w == null) return false;
        if (FeelConfig.wingsFeathers()) {
            if (!prepare(w, w.own, pt, 0, true)) return false;
            for (int i = 0; i < w.own.pairs.length; i++) out.submitCustomGeometry(ps, WingPipelines.feather(i), w.own.feathers[i]);
            out.order(1).submitCustomGeometry(ps, WingPipelines.halo(), w.own.lights);
            return true;
        }
        float spread = Mth.lerp(pt, w.spreadO, w.spread), beat = Mth.lerp(pt, w.beatO, w.beat), sweep = Mth.lerp(pt, w.sweepO, w.sweep);
        if (spread < 0.01F) return false;
        w.dFirst = true;
        draw(ps, out, w, spread, beat, sweep);
        return true;
    }

    private static RenderType body, glow;

    private static void draw(PoseStack ps, SubmitNodeCollector out, Wings w, float spread, float beat, float sweep) {
        if (body == null) {
            body = RenderTypes.entityTranslucentEmissive(TEXTURE);
            glow = RenderTypes.energySwirl(GLOW, 0, 0);
        }
        w.dSpread = spread;
        w.dBeat = beat;
        w.dSweep = sweep;
        out.submitCustomGeometry(ps, body, w.body);
        out.order(1).submitCustomGeometry(ps, glow, w.glow);
    }

    // ---- classic geometry: model space (x to the player's left, y down, z behind) ---------------------------------------

    private static final float[] LX = new float[4], LY = new float[4], LZ = new float[4];
    private static final float[] FEATHER = {0.55F, 1.05F, 1.2F, 0.55F};
    /** Bone directions folded (down the back) and open (out to the side, a little up); x outward, y down, z back. */
    private static final float[][] FOLD = {{0.35F, -0.45F, 0.75F}, {0.12F, 0.9F, 0.35F}, {0.06F, 1F, 0.25F}};
    private static final float[][] OPEN = {{0.92F, -0.38F, 0.12F}, {1F, -0.04F, 0.1F}, {0.9F, 0.22F, 0.3F}};
    /** How far back speed sweeps each bone, and the bones' lengths. */
    private static final float[] SWEEP = {0F, 0.3F, 0.55F}, BONE = {0.55F, 0.78F, 0.92F};
    private static final float[] U = {0F, 0.3F, 0.66F, 1F};

    private static void wings(PoseStack.Pose pose, VertexConsumer vc, Wings w, int argb, float scale) {
        wing(pose, vc, 1, w.dSpread, w.dBeat, w.dSweep, argb, scale, w.dFirst);
        wing(pose, vc, -1, w.dSpread, w.dBeat, w.dSweep, argb, scale, w.dFirst);
    }

    /** First person: no feathers inside this much of the span (they'd sit on the camera), faded in over the next 0.15. */
    private static final float FIRST_CUT = 0.5F;

    /**
     * One wing, side +1 (the player's left, model +x) or -1. The leading edge's three bones turn from folded (down the
     * back) to open (out to the side, a little up) with {@code spread}; a beat swings the whole wing about the shoulder,
     * the outer bones lagging; speed sweeps the outer wing back.
     */
    private static void wing(PoseStack.Pose pose, VertexConsumer vc, int side, float spread, float beat, float sweep, int argb, float scale,
                             boolean first) {
        float s = side;
        // bone directions: folded and open, mixed by spread (x outward, y down, z back)

        LX[0] = s * 0.1F;
        LY[0] = 0.1F;
        LZ[0] = 0.17F;
        for (int b = 0; b < 3; b++) {
            float dx = Mth.lerp(spread, FOLD[b][0], OPEN[b][0]), dy = Mth.lerp(spread, FOLD[b][1], OPEN[b][1]);
            float dz = Mth.lerp(spread, FOLD[b][2], OPEN[b][2] + SWEEP[b] * sweep);
            // a beat: rotate about the body's long axis at the shoulder (outer bones swing further)
            float a = beat * (0.55F + 0.25F * b) * spread;
            float c = Mth.cos(a), sn = Mth.sin(a);
            float rx = dx * c - dy * sn, ry = dx * sn + dy * c;
            dx = rx;
            dy = ry;
            float l = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            float bl = BONE[b] * scale;
            LX[b + 1] = LX[b] + s * dx / l * bl;
            LY[b + 1] = LY[b] + dy / l * bl;
            LZ[b + 1] = LZ[b] + dz / l * bl;
        }
        // the trailing edge: STRIPS points along the leading edge, each trailing a feather toward the feet and a
        // little behind (folded they lie along the back); the outer ones part into separate primaries
        int a = (int) (((argb >>> 24) & 255) * Math.min(1F, spread * 1.6F));
        int col = (a << 24) | (argb & 0xFFFFFF);
        float fy = Mth.lerp(spread, 0.3F, 0.95F), fz = Mth.lerp(spread, 1F, 0.32F);
        float fl = Mth.sqrt(fy * fy + fz * fz);
        fy /= fl;
        fz /= fl;
        float px = 0, py = 0, pz = 0, ptx = 0, pty = 0, ptz = 0, pu = 0;
        for (int i = 0; i <= STRIPS; i++) {
            float u = (float) i / STRIPS;
            // the point on the leading edge, by bone (each bone a third of the span)
            float bu = u * 3;
            int b = Math.min(2, (int) bu);
            float f = bu - b;
            float x = Mth.lerp(f, LX[b], LX[b + 1]), y = Mth.lerp(f, LY[b], LY[b + 1]), z = Mth.lerp(f, LZ[b], LZ[b + 1]);
            float len = feather(u) * scale * Mth.lerp(spread, 0.55F, 1F);
            // primaries part at the tip: every other one shorter, all a little spread toward the tip
            if (u > 0.68F && (i & 1) == 1) len *= 0.8F;
            float tx = x + s * 0.05F * u, ty = y + fy * len, tz = z + fz * len;
            if (i > 0 && (!first || pu >= FIRST_CUT)) {
                float u0 = side < 0 ? 1 - pu : pu, u1 = side < 0 ? 1 - u : u;
                int c = col;
                if (first) {
                    // fade the cut edge in: the body by its alpha, the additive glow (which ignores alpha) by its colour
                    // and lighter overall: this close, at full strength they read as solid blades, not light
                    float fade = Math.min(1F, (pu - FIRST_CUT) / 0.15F);
                    c = scale > 1F ? 0xFF000000 | scaled(col, fade * 0.45F) : ((int) (((col >>> 24) & 255) * fade * 0.6F) << 24) | (col & 0xFFFFFF);
                }
                quad(pose, vc, c, px, py, pz, u0, 0, x, y, z, u1, 0, tx, ty, tz, u1, 1, ptx, pty, ptz, u0, 1);
            }
            px = x;
            py = y;
            pz = z;
            ptx = tx;
            pty = ty;
            ptz = tz;
            pu = u;
        }
    }

    private static final int STRIPS = 14;

    private static int scaled(int rgb, float f) {
        return (int) (((rgb >> 16) & 255) * f) << 16 | (int) (((rgb >> 8) & 255) * f) << 8 | (int) ((rgb & 255) * f);
    }

    /** Feather length along the span: short at the shoulder, longest mid-wing, tapering to a rounded tip. */
    private static float feather(float u) {
        for (int i = 0; i < 3; i++) {
            if (u <= U[i + 1]) return Mth.lerp((u - U[i]) / (U[i + 1] - U[i]), FEATHER[i], FEATHER[i + 1]);
        }
        return FEATHER[3];
    }

    /** A quad seen from both sides. */
    private static void quad(PoseStack.Pose pose, VertexConsumer vc, int col,
                             float x0, float y0, float z0, float u0, float v0, float x1, float y1, float z1, float u1, float v1,
                             float x2, float y2, float z2, float u2, float v2, float x3, float y3, float z3, float u3, float v3) {
        vert(pose, vc, col, x0, y0, z0, u0, v0);
        vert(pose, vc, col, x1, y1, z1, u1, v1);
        vert(pose, vc, col, x2, y2, z2, u2, v2);
        vert(pose, vc, col, x3, y3, z3, u3, v3);
        vert(pose, vc, col, x3, y3, z3, u3, v3);
        vert(pose, vc, col, x2, y2, z2, u2, v2);
        vert(pose, vc, col, x1, y1, z1, u1, v1);
        vert(pose, vc, col, x0, y0, z0, u0, v0);
    }

    private static void vert(PoseStack.Pose pose, VertexConsumer vc, int col, float x, float y, float z, float u, float v) {
        vc.addVertex(pose, x, y, z).setColor(col).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(pose, 0, -1, 0);
    }
}
