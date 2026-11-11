package dev.awaken.powers.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.anims.client.ArmLayers;
import dev.awaken.core.api.FirstPerson;
import dev.awaken.core.client.FirstPersonDress;
import dev.awaken.powers.PowerType;
import dev.awaken.powers.fx.CastProfiles;
import dev.awaken.powers.fx.FxStyle;
import dev.awaken.powers.fx.PowerFxDefs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * Your arm shows the power you are casting (SPEC §32, "The arm"; Euan, 10 Nov 2026: "the arm showing the actual type ...
 * looks really good"). A layer over the first-person arms ({@link ArmLayers}): a pattern in whole pixels of the skin's own
 * grid, one for each style a power can have, that runs from the elbow to the fist through a wind-up, leaves through the
 * hand on the release and fades as the arm comes home. Nothing is a texture: every cell is worked out each frame from the
 * style, the power's own seed and the time, so the streaks run, the flames lick and no two powers wear quite the same arm.
 * <p>
 * What lights an arm, in order: a clip's own light keys ({@code FirstPerson.View.lightMain}); else the cast you are in,
 * from its age against its release tick; else a power that sits on you (Barrier, Mend, Brace ...), steady for as long as
 * it lasts. Level 5 wears the awakened palette and a second motif. Off with cast animations off.
 */
final class ArmLight implements ArmLayers.Layer {
    static final ArmLight INSTANCE = new ArmLight();

    private ArmLight() {}

    // ---- what is lighting the arms ----------------------------------------------------------------------------------

    private static FxStyle castStyle = FxStyle.ENERGY, auraStyle = FxStyle.ENERGY;
    private static int castSeed, auraSeed;
    private static boolean castAwake, auraAwake;
    /** The cast's start and release (FxWorld ticks), and when it stops lighting; the aura's end. */
    private static float castStart = -1e6f, castRelease, castEnd = -1e6f, auraEnd = -1e6f, auraStart;
    private static float auraStrength;
    /** A held cast keeps its charge until it is let go (then the release plays from that moment). */
    private static boolean castHeld;

    /** Your own cast began: the arms take its light. */
    static void cast(PowerType type, int level, CastProfiles.Profile prof) {
        PowerFxDefs.Def def = PowerFxDefs.get(type.path);
        castStyle = (def != null ? def.style() : FxStyle.of(type.family)).plain();
        castAwake = level >= 5;
        castSeed = type.path.hashCode();
        castStart = FxWorld.ticks;
        castRelease = prof == null ? 4 : Math.max(2, prof.release());
        castHeld = prof != null && prof.channel();
        castEnd = castHeld ? Float.MAX_VALUE : castStart + castRelease + FADE;
    }

    /** A held cast was let go: the light leaves through the hand now. */
    static void letGo() {
        if (!castHeld) return;
        castHeld = false;
        castRelease = FxWorld.ticks - castStart;
        castEnd = FxWorld.ticks + FADE;
    }

    /** A power that sits on you (a shield, a heal, a guard) shows on your arms for as long as it lasts. */
    static void aura(PowerType type, int level, int ticks, float strength) {
        PowerFxDefs.Def def = PowerFxDefs.get(type.path);
        auraStyle = (def != null ? def.style() : FxStyle.of(type.family)).plain();
        auraAwake = level >= 5;
        auraSeed = type.path.hashCode();
        auraStart = FxWorld.ticks;
        auraEnd = FxWorld.ticks + ticks;
        auraStrength = strength;
    }

    static void endAura(PowerType type) {
        if (type.path.hashCode() == auraSeed) auraEnd = Math.min(auraEnd, FxWorld.ticks + 6);
    }

    static void reset() {
        castStart = castEnd = auraEnd = -1e6f;
        castHeld = false;
    }

    /** Ticks the light takes to die after a release. */
    private static final float FADE = 12;

    private static boolean auraOn(float now) {
        return now < auraEnd;
    }

    /**
     * A stance: while a power sits on you and your main hand is empty, the hand rests as a fist (where vanilla rests it), so
     * there is an arm of ours to light. A hand that holds something is lit through its grip instead.
     */
    static void rest(float pt, FirstPerson.View view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !PowerFxConfig.animations() || !auraOn(FxWorld.ticks + pt)) return;
        if (!mc.player.getMainHandItem().isEmpty() || view.fistMain[3] > 0) return;
        float[] r = FirstPerson.View.REST_FIST;
        FirstPerson.View.fist(view.fistMain, 1F, mc.player.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT ? -r[0] : r[0], r[1], r[2]);
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    /** One arm's light, kept from the submit to the draw (the collector draws later): a small ring, nothing allocated a frame. */
    private static final class Draw implements SubmitNodeCollector.CustomGeometryRenderer {
        FxStyle style;
        boolean awake, halo, dressed;
        int seed;
        float charge, heat, time, x0, x1;

        @Override
        public void render(PoseStack.Pose pose, VertexConsumer vc) {
            pull = dressed ? OVER_PLATE : 0f;
            if (halo) halo(this, pose, vc);
            else cells(this, pose, vc);
            pull = 0f;
        }
    }

    private static final Draw[] RING = new Draw[16];
    private static int next;

    static {
        for (int i = 0; i < RING.length; i++) RING[i] = new Draw();
    }

    /** What lights this arm now ({@link #lit}): its style, seed, how far the light has run and how hot it is. */
    private static FxStyle litStyle = FxStyle.ENERGY;
    private static boolean litAwake;
    private static int litSeed;
    private static float litCharge, litHeat;

    /** Works out what lights this arm now, into the {@code lit} fields; false when nothing does. */
    private static boolean lit(ArmLayers.Arm arm, float now) {
        FxStyle style;
        boolean awake;
        int seed;
        float charge, heat;
        boolean casting = now < castEnd && now >= castStart;
        if (arm.keyed && casting) {
            style = castStyle;
            awake = castAwake;
            seed = castSeed;
            charge = arm.charge;
            heat = arm.heat;
        } else if (casting && !arm.grip) {
            style = castStyle;
            awake = castAwake;
            seed = castSeed;
            float age = now - castStart;
            if (castHeld || age < castRelease) {
                charge = Mth.clamp(age / castRelease, 0f, 1f);
                heat = 0.35f + 0.65f * charge;
                if (castHeld && charge >= 1f) heat = 0.9f + 0.1f * Mth.sin(now * 1.9f);
            } else {
                float after = (age - castRelease) / FADE;
                charge = 1f;
                heat = after < 0.12f ? 1.6f : Math.max(0f, 1.25f * (1f - after));
            }
        } else if (auraOn(now)) {
            style = auraStyle;
            awake = auraAwake;
            seed = auraSeed;
            float in = Math.min(1f, (now - auraStart) / 6f), outK = Math.min(1f, (auraEnd - now) / 8f);
            charge = in;
            heat = auraStrength * Math.min(in, outK) * (0.72f + 0.1f * Mth.sin(now * 0.35f));
        } else {
            return false;
        }
        if (heat <= 0.02f || charge <= 0.01f) return false;
        litStyle = awake ? style.awakened() : style;
        litAwake = awake;
        litSeed = seed;
        litCharge = charge;
        litHeat = heat;
        return true;
    }

    /**
     * Before the arm is dressed: what it wears is told the light on it, so a sleeve or a plate can answer in its own
     * materials (the armour's edges and what glows of itself take the type's colour: SPEC §32.N, "The light on what you wear").
     */
    @Override
    public void prepare(ArmLayers.Arm arm) {
        if (!PowerFxConfig.animations() || !lit(arm, FxWorld.ticks + arm.partialTick)) return;
        // a small steady amount, so the piece's own edge light takes the type's colour; the piece only flares at a release
        // (the second film: a flare all through a cast, and all through a Barrier, washed the whole arm in the type's colour)
        FirstPersonDress.glow(litStyle.mid, 0.12f * Math.min(1f, litHeat) + 1.2f * Math.max(0f, Math.min(1.6f, litHeat) - 0.9f));
    }

    @Override
    public void draw(ArmLayers.Arm arm, PoseStack ps, SubmitNodeCollector out) {
        if (!PowerFxConfig.animations()) return;
        float now = FxWorld.ticks + arm.partialTick;
        if (!lit(arm, now)) return;
        if (arm.dressed) {
            // the arm wears something: the light is laid on that, on its own shapes, and not on the skin under it
            FirstPersonDress.Surface worn = FirstPersonDress.surface(arm.right ? FirstPersonDress.Limb.RIGHT_ARM : FirstPersonDress.Limb.LEFT_ARM);
            ArmourLight.Cells cells = worn == null ? null : ArmourLight.of(worn);
            if (cells != null) {
                ArmourLight.draw(ps, out, cells, litStyle, litAwake, litSeed, litCharge, litHeat, now);
                return;
            }
        }
        submit(ps, out, arm, litStyle, litAwake, litSeed, litCharge, litHeat, now, false);
        submit(ps, out, arm, litStyle, litAwake, litSeed, litCharge, litHeat, now, true);
    }

    private static void submit(PoseStack ps, SubmitNodeCollector out, ArmLayers.Arm arm, FxStyle style, boolean awake, int seed, float charge,
                               float heat, float now, boolean halo) {
        Draw d = RING[next];
        next = (next + 1) % RING.length;
        d.style = style;
        d.awake = awake;
        d.seed = seed;
        d.charge = charge;
        d.heat = heat;
        d.time = now;
        d.x0 = arm.x0;
        d.x1 = arm.x1;
        d.dressed = arm.dressed;
        d.halo = halo;
        out.submitCustomGeometry(ps, halo ? RenderTypes.lightning() : RenderTypes.debugQuads(), d);
    }

    private static final float PX = 1f / 16f, LIFT = 0.004f;
    /**
     * An arm that wears something whose shape nobody gives ({@code FirstPersonDress.surface} has none: a dresser that is
     * not the armour): its light is drawn this much nearer your eye, along the line of sight, so it lies over the sleeve
     * where it lay on the skin. A stopgap; the armour's own pieces take the light on their shapes ({@link ArmourLight}).
     */
    private static final float OVER_PLATE = 0.16f;
    private static float pull;
    private static final org.joml.Vector3f V = new org.joml.Vector3f(), W = new org.joml.Vector3f();
    private static final int ROWS = 12;
    /** Faces of the arm's cube: toward the body's front and back, its two sides, and the hand's end. */
    private static final int FRONT = 0, BACK = 1, OUTER = 2, INNER = 3, CAP = 4;

    private static void cells(Draw d, PoseStack.Pose pose, VertexConsumer vc) {
        FxStyle plain = d.style.plain();
        int across = Math.round(d.x1 - d.x0);
        float fade = Mth.clamp(d.heat, 0f, 1f), flash = Mth.clamp(d.heat - 1f, 0f, 1f);
        for (int face = FRONT; face <= CAP; face++) {
            if (pull > 0f && !faces(pose, d, face)) continue;
            int cols = face == OUTER || face == INNER ? 4 : across, rows = face == CAP ? 4 : ROWS;
            for (int v = 0; v < rows; v++) {
                // the hand's end takes the last row's light, so the fist itself glows when the arm is full
                int row = face == CAP ? ROWS - 1 : v;
                for (int u = 0; u < cols; u++) {
                    float i = pattern(plain, face, u, row, cols, d.time, d.seed, d.charge, d.awake);
                    if (i == 0f) continue;
                    int colour;
                    float alpha;
                    if (i < 0f) { // ink: a dark cell, painted over the skin
                        colour = d.style.accent;
                        alpha = 0.9f * fade * Math.min(1f, -i);
                    } else {
                        colour = mix(d.style.mid, d.style.core, Mth.clamp((i - 0.55f) * 2.2f + flash, 0f, 1f));
                        alpha = fade * Mth.clamp(0.5f + 0.5f * i, 0f, 1f);
                    }
                    cell(pose, vc, d, face, u, v, cols, (colour & 0xFFFFFF) | (Math.round(alpha * 255) << 24));
                }
            }
        }
    }

    /** One pixel of a face, lifted a hair off the skin. */
    private static void cell(PoseStack.Pose pose, VertexConsumer vc, Draw d, int face, int u, int v, int cols, int argb) {
        float xa = d.x0 * PX, xb = d.x1 * PX, za = -2 * PX, zb = 2 * PX;
        switch (face) {
            case FRONT, BACK -> {
                float z = face == FRONT ? za - LIFT : zb + LIFT, x = xa + u * PX, y = (-2 + v) * PX;
                quad(pose, vc, x, y, z, x + PX, y, z, x + PX, y + PX, z, x, y + PX, z, argb);
            }
            case OUTER, INNER -> {
                float x = face == OUTER ? xa - LIFT : xb + LIFT, z = za + u * PX, y = (-2 + v) * PX;
                quad(pose, vc, x, y, z, x, y, z + PX, x, y + PX, z + PX, x, y + PX, z, argb);
            }
            default -> {
                float y = 10 * PX + LIFT, x = xa + u * PX, z = za + v * PX;
                quad(pose, vc, x, y, z, x + PX, y, z, x + PX, y, z + PX, x, y, z + PX, argb);
            }
        }
    }

    /**
     * Whether a face of the arm's cube is turned to your eye. On bare skin the arm's own depth hides the far faces; a light
     * drawn nearer the eye than the arm (over a plate) has to leave them out itself. The hand's space has your eye at its
     * origin, so a face shows when its outward side points back at the origin.
     */
    private static boolean faces(PoseStack.Pose pose, Draw d, int face) {
        float cx = (d.x0 + d.x1) * 0.5f * PX, cy = 4 * PX, cz = 0, nx = 0, ny = 0, nz = 0;
        switch (face) {
            case FRONT -> { cz = -2 * PX; nz = -1; }
            case BACK -> { cz = 2 * PX; nz = 1; }
            case OUTER -> { cx = d.x0 * PX; nx = -1; }
            case INNER -> { cx = d.x1 * PX; nx = 1; }
            default -> { cy = 10 * PX; ny = 1; }
        }
        pose.pose().transformPosition(cx, cy, cz, V);
        pose.pose().transformPosition(cx + nx, cy + ny, cz + nz, W);
        return V.x * (W.x - V.x) + V.y * (W.y - V.y) + V.z * (W.z - V.z) < 0f;
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2,
                             float z2, float x3, float y3, float z3, int argb) {
        vertex(pose, vc, x0, y0, z0, argb);
        vertex(pose, vc, x1, y1, z1, argb);
        vertex(pose, vc, x2, y2, z2, argb);
        vertex(pose, vc, x3, y3, z3, argb);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, int argb) {
        if (pull == 0f) {
            vc.addVertex(pose, x, y, z).setColor(argb);
            return;
        }
        // toward the eye along its own line of sight: the same place on the screen, nearer
        pose.pose().transformPosition(x, y, z, V);
        float len = V.length(), k = len > pull * 2f ? 1f - pull / len : 0.5f;
        vc.addVertex(V.x * k, V.y * k, V.z * k).setColor(argb);
    }

    /** The glow round the lit length of the arm: its faces pushed out a little, in additive light (both windings: no cull is assumed). */
    private static void halo(Draw d, PoseStack.Pose pose, VertexConsumer vc) {
        FxStyle plain = d.style.plain();
        if (plain == FxStyle.SHADOW && d.heat <= 1f) return; // ink doesn't glow
        float k = Mth.clamp(d.heat, 0f, 1.6f);
        float a = 0.085f * k + 0.2f * Mth.clamp(d.heat - 1f, 0f, 1f);
        int c = scale(d.style.mid, a);
        float out = 0.018f + 0.02f * Mth.clamp(d.heat - 1f, 0f, 1f);
        float xa = d.x0 * PX - out, xb = d.x1 * PX + out, za = -2 * PX - out, zb = 2 * PX + out;
        // the lit length: from the elbow down for a light that runs to the fist, from the hand back for one that grows from it
        float lit = ROWS * Mth.clamp(d.charge, 0.15f, 1f);
        float ya, yb;
        if (fromHand(plain)) {
            ya = (10 - lit * 0.75f) * PX;
            yb = 10 * PX + out;
        } else {
            ya = -2 * PX - out;
            yb = (-2 + lit) * PX + out;
        }
        both(pose, vc, xa, ya, za, xb, ya, za, xb, yb, za, xa, yb, za, c);
        both(pose, vc, xa, ya, zb, xb, ya, zb, xb, yb, zb, xa, yb, zb, c);
        both(pose, vc, xa, ya, za, xa, ya, zb, xa, yb, zb, xa, yb, za, c);
        both(pose, vc, xb, ya, za, xb, ya, zb, xb, yb, zb, xb, yb, za, c);
        both(pose, vc, xa, yb, za, xb, yb, za, xb, yb, zb, xa, yb, zb, c);
    }

    /** Styles whose light grows back from the hand (flame, rime, ink) instead of running to it. */
    private static boolean fromHand(FxStyle plain) {
        return plain == FxStyle.SHADOW || plain == FxStyle.SPIRIT || plain == FxStyle.FROST || plain == FxStyle.HEAT || plain == FxStyle.MIND
                || plain == FxStyle.SOUL;
    }

    private static void both(PoseStack.Pose pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2,
                             float z2, float x3, float y3, float z3, int argb) {
        quad(pose, vc, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, argb);
        quad(pose, vc, x3, y3, z3, x2, y2, z2, x1, y1, z1, x0, y0, z0, argb);
    }

    private static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255), g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255),
                bl = (int) Mth.lerp(t, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    private static int scale(int rgb, float k) {
        int r = (int) (((rgb >> 16) & 255) * k), g = (int) (((rgb >> 8) & 255) * k), b = (int) ((rgb & 255) * k);
        return 0xFF000000 | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }

    // ---- the patterns -------------------------------------------------------------------------------------------------

    /** A steady 0..1 from three whole numbers. */
    private static float hash(int a, int b, int c) {
        int h = a * 0x9E3779B1 + b * 0x85EBCA77 + c * 0xC2B2AE3D;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return (h & 0xFFFF) / 65536f;
    }

    /**
     * How lit one pixel is: 0 dark, up to 1 (and a touch over for the brightest), negative for ink. {@code v} runs 0 at the
     * elbow to 11 at the hand; the light has reached {@code charge * 12} rows from the elbow.
     */
    static float pattern(FxStyle style, int face, int u, int v, int cols, float t, int seed, float charge, boolean awake) {
        float reach = charge * ROWS;
        boolean front = v < reach; // the elbow-to-fist run has got here
        int f = face == CAP ? FRONT : face;
        switch (style) {
            case KINETIC -> {
                // cracks in stone: one seam wandering down each face, with short branches, pulsing as if about to split
                if (!front) return 0f;
                int seam = (int) (hash(seed, f, v / 2) * cols);
                boolean on = u == seam || (v % 4 == 1 && Math.abs(u - seam) == 1 && hash(seed, f + 7, v) > 0.45f);
                if (awake && !on) on = v % 6 == 3 && hash(seed, f + 3, u + v) > 0.5f;
                if (!on) return 0f;
                return 0.62f + 0.38f * Mth.sin(t * 0.55f + v * 0.9f);
            }
            case HEAT -> {
                // embers under the skin, and flame that climbs back from the hand
                float lick = 3.5f + 2.5f * Mth.sin(t * 0.9f + u * 2.1f + f) + (awake ? 2f : 0f);
                if (v >= ROWS - lick * charge) return 0.75f + 0.35f * hash(seed, u + f * 5, (int) (t * 0.8f) + v);
                if (!front) return 0f;
                return hash(seed, f * 4 + u, v) > 0.72f ? 0.45f + 0.4f * Mth.sin(t * 0.7f + u + v) : 0f;
            }
            case VELOCITY -> {
                // streaks that run to the fist
                if (!front) return 0f;
                int lane = (int) (hash(seed, f, 1) * cols), lane2 = (lane + 2) % Math.max(1, cols);
                if (u != lane && !(awake && u == lane2)) return 0f;
                float run = (v - t * 1.6f + hash(seed, f, u) * 9f) % 6f;
                if (run < 0) run += 6f;
                return run < 3.6f ? 0.55f + 0.14f * run : 0f;
            }
            case FLIGHT -> {
                // motes that lift off the skin and drift
                if (!front) return 0f;
                int drift = (int) (t * 0.22f);
                float h = hash(seed + drift / 3, f * 5 + u, v + drift);
                return h > (awake ? 0.78f : 0.84f) ? 0.6f + 0.4f * Mth.sin(t * 0.4f + h * 30f) : 0f;
            }
            case GRAVITY -> {
                // bands that fall toward the wrist, and ink where they meet
                if (!front) return 0f;
                float band = (v + t * 0.5f) % 4f;
                if (v >= 9) return -(0.6f + 0.4f * Mth.sin(t * 0.3f + u));
                return band < 1f ? 0.7f : (awake && band < 2f && (u + v) % 2 == 0 ? 0.45f : 0f);
            }
            case ENERGY, PRISM -> {
                // a white-blue vein that forks, with a pulse running down it
                if (!front) return 0f;
                int vein = 1 + ((v / 2 + (int) (hash(seed, f, 2) * 2)) % 2);
                vein = Math.min(cols - 1, vein);
                boolean on = u == vein || (v % 4 == 1 && u == Math.min(cols - 1, vein + 1)) || (awake && v % 4 == 3 && u == Math.max(0, vein - 1));
                if (!on) return 0f;
                float pulse = (v - t * 0.9f) % 5f;
                if (pulse < 0) pulse += 5f;
                return pulse < 1.2f ? 1.15f : 0.62f;
            }
            case CHARGE -> {
                // lightning: a jagged line that jumps every few ticks
                if (!front) return 0f;
                int jump = (int) (t * 0.5f);
                int line = (int) (hash(seed + jump, f, v / 2) * cols);
                boolean on = u == line || (awake && u == (line + 1) % Math.max(1, cols) && v % 3 == 0);
                if (!on) return 0f;
                return hash(seed + (int) t, f + u, v) > 0.25f ? 1.1f : 0.5f;
            }
            case SHADOW -> {
                // ink creeping back from the hand, with one lit edge
                float edge = ROWS - charge * (7f + (awake ? 2f : 0f)) + (hash(seed, f, u) * 2.5f) + 0.6f * Mth.sin(t * 0.25f + u + f);
                if (v >= edge + 1f) return -1f;
                if (v >= edge) return 0.8f;
                return 0f;
            }
            case MIND, SOUL -> {
                // rings that close round the wrist, one after another
                int rings = 1 + (int) (charge * (awake ? 4.99f : 3.99f));
                for (int r = 0; r < rings; r++) {
                    int at = 10 - r * 2;
                    if (v == at) {
                        float turn = (t * 0.25f - r * 0.7f) % 3f;
                        if (turn < 0) turn += 3f;
                        return turn < 1f ? 1.1f : 0.6f;
                    }
                }
                return 0f;
            }
            case ARCHITECT -> {
                // a drawn grid, the lines laid as the light arrives
                if (!front) return 0f;
                boolean line = v % 3 == 0 || u == 0;
                if (!line) return awake && (u + v) % 4 == 0 ? 0.35f : 0f;
                return v > reach - 1.5f ? 1.1f : 0.6f;
            }
            case TECH -> {
                // plates that lock on, seams between them, one pixel blinking
                if (!front) return 0f;
                boolean seam = v % 4 == 3 || u == cols / 2;
                if (seam) return -0.6f;
                if (v % 4 == 1 && u == 0) return ((int) (t * 0.3f) + v) % 3 == 0 ? 1.15f : 0.5f;
                return awake ? 0.7f : 0.52f;
            }
            case SPIRIT -> {
                // tongues of flame from the hand
                float tongue = (2.5f + 3.5f * hash(seed, f, u) + 1.4f * Mth.sin(t * 0.6f + u * 1.7f + f * 2.3f)) * charge + (awake ? 1.5f * charge : 0f);
                if (v < ROWS - tongue) return 0f;
                return v >= ROWS - 2 ? 1.1f : 0.6f + 0.25f * Mth.sin(t * 0.8f + v);
            }
            case FROST -> {
                // rime: crystals growing in diagonals from the hand
                float grown = charge * (9f + (awake ? 3f : 0f));
                if (v < ROWS - grown) return 0f;
                boolean arm = (u + v + f) % 3 == 0 || (u - v - f) % 4 == 0;
                if (!arm) return v >= ROWS - 1 ? 0.5f : 0f;
                return 0.7f + 0.4f * hash(seed, u + f * 3, v + (int) (t * 0.15f));
            }
            default -> {
                if (!front) return 0f;
                return (u + v) % 3 == 0 ? 0.6f : 0f;
            }
        }
    }
}
