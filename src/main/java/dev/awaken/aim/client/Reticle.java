package dev.awaken.aim.client;

import dev.awaken.Awaken;
import dev.awaken.aim.AimRules;
import dev.awaken.core.api.FirstPerson;
import dev.awaken.core.api.HudState;
import dev.awaken.core.api.Strikes;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * The lock's look (SPEC §40.4), in the band's language: thin cyan lines with light round a hot core, the art from
 * {@code tools/art/specs/aim_reticle.py}. The mark: four corner brackets that spring in round the creature. The lock: a
 * broken ring turning slowly, four ticks that fly in, the System's diamond over it; it springs closed with a small
 * overshoot when it takes, slides to the next creature on a switch, kicks and flashes on every blow that lands, and
 * splinters when the creature falls. A creature's blow closes in on the ring as a second ring in its class's colour (white,
 * amber, red, violet), on the body's own clock and at one speed for every blow, meeting the ring on the tick the blow
 * lands; the ground it crosses while a guard raised now turns the blow aside is a band that burns white (SPEC §67.2). A
 * wind-up out of view shows as an arc at that edge. The dot in the middle shows only when there is something to mine,
 * place or use.
 */
final class Reticle {
    private Reticle() {}

    static final Identifier LAYER = Awaken.id("aim_reticle");

    private static final int HOLO = 0x7FE3FF, LIGHT = 0xFFFFFF, HEAVY = 0xFFB340, PERIL = 0xFF4A3D, GRAB = 0xB77BFF;

    private static final Identifier RING = tex("ring"), RING_G = tex("ring_glow"), TICKS = tex("ticks"), TICKS_G = tex("ticks_glow"),
            BRACKET = tex("bracket"), BRACKET_G = tex("bracket_glow"), DIAMOND = tex("diamond"), DIAMOND_G = tex("diamond_glow"),
            TELL = tex("tell"), TELL_G = tex("tell_glow"), EDGE = tex("edge"), EDGE_G = tex("edge_glow"), DOT = tex("dot"),
            DOT_G = tex("dot_glow"), SHARD = tex("shard"), SHARD_G = tex("shard_glow");
    /** The parry band for a window of 3, 4 or 5 ticks (the art's own widths: tools/art/specs/aim_reticle.py). */
    private static final Identifier[] BAND = {tex("band3"), tex("band4"), tex("band5")}, BAND_G = {tex("band3_glow"), tex("band4_glow"), tex("band5_glow")};

    // ---- the closing ring's timing (SPEC §67.2; the band's art is drawn to the same numbers) ----------------------------
    /** Every ring appears this many ticks before its blow, this many lock rings out, and closes in a straight line of time. */
    static final float APPROACH = 12F, SPREAD = 1.15F;
    /**
     * The band is lit from this long inside the weapon's window until this long before the blow: what a press needs to
     * reach the server and be counted (it sees the guard a tick after the press, and counts whole ticks).
     */
    static final float BAND_EARLY = 0.5F, BAND_LATE = 0.75F;
    /** A landed blow's ring stays closed this long (ticks) while it fades. */
    private static final float LINGER = 0.6F;

    private static Identifier tex(String name) {
        return Awaken.id("textures/gui/aim/" + name + ".png");
    }

    /** The ring's radius last drawn (GUI px): the release's fading ring and the splinters start from it. */
    static float lastR = 16F;
    private static float lastX, lastY, slideFromX, slideFromY;
    private static int lastId = -1;
    private static long lastNanos;
    private static float dotAlpha;

    // how the guard last answered: the ring bursts on a deflect and says why a guard missed (Strikes.answered)
    private static int burstOf = -1, wordOf = -1;
    private static long burstAt, wordAt;
    private static boolean wordEarly;
    /** The duel body fighting you whose ring shows without a lock or a mark (found once a tick), or -1. */
    private static int bossRing = -1;
    private static final float[] P = new float[2], ANCHOR = new float[3];
    private static boolean anchored;

    // ---- the camera, for placing it on the creature (as the Boss Plate's lift-off does) ---------------------------------

    private static final Matrix4f VIEW_PROJ = new Matrix4f();
    private static final Vector4f TMP = new Vector4f();
    private static Vec3 camPos = Vec3.ZERO;
    private static boolean camReady;

    static void extract(ExtractLevelRenderStateEvent e) {
        CameraRenderState cam = e.getRenderState().cameraRenderState;
        VIEW_PROJ.set(cam.projectionMatrix).mul(cam.viewRotationMatrix);
        camPos = cam.pos;
        camReady = true;
    }

    /** The GUI position of a world point into P; false behind the camera or well off the screen. */
    private static boolean project(double x, double y, double z, int w, int h, float margin) {
        if (!camReady) return false;
        TMP.set((float) (x - camPos.x), (float) (y - camPos.y), (float) (z - camPos.z), 1F);
        VIEW_PROJ.transform(TMP);
        if (TMP.w <= 0.05F) return false;
        float nx = TMP.x / TMP.w, ny = TMP.y / TMP.w;
        if (Math.abs(nx) > margin || Math.abs(ny) > margin) return false;
        P[0] = (nx + 1) * 0.5F * w;
        P[1] = (1 - ny) * 0.5F * h;
        return true;
    }

    /** Where the ring is this frame (Arms' Resonance arc rides under it). */
    static boolean anchor(float[] out) {
        if (!anchored) return false;
        out[0] = ANCHOR[0];
        out[1] = ANCHOR[1];
        out[2] = ANCHOR[2];
        return true;
    }

    // ---- the layer --------------------------------------------------------------------------------------------------

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        anchored = false;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gui.hud.isHidden() || !AimConfig.enabled() || p.isSpectator()) return;
        float pt = dt.getGameTimeDeltaPartialTick(true);
        long now = System.nanoTime();
        float frame = lastNanos == 0 ? 0F : Math.min(0.1F, (now - lastNanos) / 1.0E9F);
        lastNanos = now;
        int w = g.guiWidth(), h = g.guiHeight();
        boolean reduced = FirstPerson.reducedMotion.getAsBoolean();

        int id = Lock.locked >= 0 ? Lock.locked : markShown(p) ? Lock.marked : -1;
        boolean drew = false;
        if (id >= 0 && mc.level.getEntity(id) instanceof LivingEntity le && le.isAlive()) drew = target(g, p, le, Lock.locked >= 0, false, pt, now, frame, w, h, reduced);
        // a boss that fights you shows its blows' ring with no lock and no mark on it (SPEC §67.2)
        if (!drew && bossRing >= 0 && bossRing != id && AimConfig.bossRing() && mc.level.getEntity(bossRing) instanceof LivingEntity boss && boss.isAlive()) {
            drew = target(g, p, boss, false, true, pt, now, frame, w, h, reduced);
        }
        if (!drew) lastId = -1;
        gone(g, now, w, h, reduced);
        if (AimConfig.threats()) threats(g, p, pt, now, w, h);
        dot(g, mc, p, frame, w, h);
    }

    /** The mark shows while a weapon is in hand or a fight is on (the lock always shows). */
    private static boolean markShown(LocalPlayer p) {
        return AimConfig.mark() && Lock.marked >= 0 && (HudState.fighting() || dev.awaken.arms.Arms.styleOf(p.getMainHandItem()) != null);
    }

    private static boolean target(GuiGraphicsExtractor g, LocalPlayer p, LivingEntity le, boolean locked, boolean bare, float pt, long now, float frame,
                                  int w, int h, boolean reduced) {
        double x = Mth.lerp(pt, le.xo, le.getX()), z = Mth.lerp(pt, le.zo, le.getZ());
        double y = Mth.lerp(pt, le.yo, le.getY()) + le.getBbHeight() * 0.5;
        if (!project(x, y, z, w, h, 1.05F)) {
            if (locked) offScreen(g, p, le, pt, now, w, h);
            return false;
        }
        float sx = P[0], sy = P[1];
        // the ring's size: the body's half-size on the screen, kept between 11 and 44 GUI px
        float half = Math.max(le.getBbHeight(), le.getBbWidth()) * 0.55F;
        float r = project(x, y + half, z, w, h, 3F) ? Math.abs(sy - P[1]) * 1.08F + 5F : lastR;
        r = Mth.clamp(r, 11F, 44F);
        // a new target: brackets spring in where it is; a switch slides the ring from the last one
        if (le.getId() != lastId) {
            slideFromX = lastId >= 0 ? lastX : sx;
            slideFromY = lastId >= 0 ? lastY : sy;
            if (lastId < 0) lastR = r;
        }
        lastId = le.getId();
        lastR += (r - lastR) * Math.min(1F, frame * 14F);
        r = lastR;
        float slide = locked ? ease(ms(now, Lock.switchedAt) / 130F) : 1F;
        if (slide < 1F && !reduced) {
            sx = Mth.lerp(slide, slideFromX, sx);
            sy = Mth.lerp(slide, slideFromY, sy);
        }
        lastX = sx;
        lastY = sy;

        // the blow coming from this creature, on the body's own clock (Strikes.Coming: never the tell's count, SPEC §67.2)
        double left = Strikes.left(le, pt);
        Strikes.Kind kind = Double.isNaN(left) ? Strikes.Kind.NONE : Strikes.comingKind(le);
        boolean coming = kind != Strikes.Kind.NONE && left <= APPROACH && left > -LINGER;
        int tellColour = coming ? colour(kind) : -1;
        boolean reaches = coming && Strikes.reaches(le);
        // whose blow it is (SPEC §70.3): yours closes in full, with its band and its colour on the ring; someone else's is a
        // thin, still ring that promises you nothing
        boolean mine = mine(p, le, coming);
        int window = mine && reaches && Strikes.lands(le) ? window(p, kind) : 0;
        boolean hot = window > 0 && left <= window - BAND_EARLY && left >= BAND_LATE;

        int base = hot ? LIGHT : tellColour >= 0 && mine ? mix(HOLO, tellColour, 0.65F) : HOLO;
        if (locked) lockRing(g, sx, sy, r, base, now, reduced);
        else if (!bare) brackets(g, sx, sy, r, base, now, reduced);
        if (coming) {
            // with no lock on it there is no ring for the blow's to close onto: the mark and the bare boss get one while a blow comes
            if (!locked) glowed(g, RING, RING_G, 128, 128, sx, sy, r * 2.45F, r * 2.45F, 0F, base, mine ? 0.6F : 0.3F);
            // the band: where the ring is while a guard raised now turns the blow aside, white while the ring is in it
            if (window > 0) {
                int i = Mth.clamp(window, 3, 5) - 3;
                band(g, BAND[i], BAND_G[i], sx, sy, r * 3.2F, hot ? LIGHT : tellColour, hot ? 0.62F : 0.22F);
            }
            // the closing ring: one speed for every blow, from 1 + SPREAD lock rings down onto the ring as the blow lands
            float f = Mth.clamp((float) (left / APPROACH), 0F, 1F);
            float tr = r * (1F + SPREAD * f);
            float alpha = (0.5F + 0.5F * (1F - f)) * (!mine ? 0.3F : reaches ? 1F : 0.35F) * (left < 0 ? Mth.clamp((float) (1 + left / LINGER), 0F, 1F) : 1F);
            int ring = hot ? LIGHT : tellColour;
            float size = mine ? 2.3F : 2.1F; // someone else's blow: a thinner ring (SPEC §70.3)
            glowed(g, TELL, TELL_G, 128, 128, sx, sy, tr * size, tr * size, 0F, ring, alpha);
            if (hot) glowed(g, TELL, TELL_G, 128, 128, sx, sy, tr * 2.3F + 2F, tr * 2.3F + 2F, 0F, LIGHT, alpha * 0.8F); // a thicker line while it is "now"
        }
        answered(g, le.getId(), sx, sy, r, now, reduced);
        ANCHOR[0] = sx;
        ANCHOR[1] = sy;
        ANCHOR[2] = r;
        anchored = true;
        return true;
    }

    /** Bodies whose coming blow is aimed at someone else (SPEC §70.3), kept from the tell to the blow's landing. */
    private static final int[] OTHERS = {-1, -1, -1, -1};

    /**
     * Whether this body's coming blow is yours to answer. Its tell says whom it is aimed at only while the wind-up lasts;
     * the ring runs on to the landing (SPEC §67.2), so what the tell last said is kept until the blow is over.
     */
    private static boolean mine(LocalPlayer p, Entity e, boolean coming) {
        int id = e.getId(), at = -1, free = -1;
        for (int i = 0; i < OTHERS.length; i++) {
            if (OTHERS[i] == id) at = i;
            else if (OTHERS[i] < 0 && free < 0) free = i;
        }
        Strikes.Tell tell = Strikes.tell(e);
        if (tell != null && tell.kind() != Strikes.Kind.NONE && tell.ticks() > 0) {
            boolean yours = tell.aimedAt(p.getId());
            if (!yours && at < 0 && free >= 0) OTHERS[free] = id;
            if (yours && at >= 0) OTHERS[at] = -1;
            return yours;
        }
        if (at >= 0 && !coming) OTHERS[at] = -1;
        return at < 0;
    }

    /**
     * The ticks in which the held weapon answers a blow of this class as it lands: its guard's deflect window for a blow
     * that can be turned aside, its dodge's evade ticks for one that can only be got out of; 0 with no fighting style in hand.
     */
    private static int window(LocalPlayer p, Strikes.Kind kind) {
        var style = dev.awaken.arms.Arms.styleOf(p.getMainHandItem());
        if (style == null) return 0;
        return kind.deflectable() ? style.def().guard().deflect() : kind.dodgeable() ? style.def().dodge().evade() : 0;
    }

    /** The band: a soft dark ground under it (it reads on a bright sky), then its own light. */
    private static void band(GuiGraphicsExtractor g, Identifier core, Identifier glow, float cx, float cy, float size, int colour, float alpha) {
        sprite(g, glow, 128, 128, cx, cy, size, size, 0F, ARGB.color(Math.round(alpha * 0.30F * 255), 0x000000));
        sprite(g, core, 128, 128, cx, cy, size, size, 0F, ARGB.color(Math.round(alpha * 255), colour));
    }

    /** How long a deflect's burst and a miss's word last (ms). */
    private static final float BURST_MS = 300F, WORD_MS = 650F;
    private static final int CLASH = 0xFFF2CC;

    /** The guard's answer on this creature's ring: it bursts outward on a deflect, and a miss says why under it. */
    private static void answered(GuiGraphicsExtractor g, int id, float sx, float sy, float r, long now, boolean reduced) {
        if (burstOf == id) {
            float t = ms(now, burstAt) / BURST_MS;
            if (t < 1F) {
                float out = reduced ? 0.25F : 1F - (1F - t) * (1F - t);
                glowed(g, TELL, TELL_G, 128, 128, sx, sy, r * (1F + 0.9F * out) * 2.3F, r * (1F + 0.9F * out) * 2.3F, 0F, CLASH, (1F - t) * (1F - t));
                glowed(g, RING, RING_G, 128, 128, sx, sy, r * (1F + 0.45F * out) * 2.45F, r * (1F + 0.45F * out) * 2.45F, 0F, LIGHT, 0.8F * (1F - t));
            } else burstOf = -1;
        }
        if (wordOf == id && AimConfig.words()) {
            float t = ms(now, wordAt) / WORD_MS;
            if (t < 1F) {
                Minecraft mc = Minecraft.getInstance();
                String word = net.minecraft.network.chat.Component.translatable(wordEarly ? "awaken.aim.too_early" : "awaken.aim.too_late").getString();
                int a = Math.round(255 * Mth.clamp(t < 0.7F ? 1F : (1F - t) / 0.3F, 0F, 1F));
                if (a > 8) g.centeredText(mc.font, word, Math.round(sx), Math.round(sy + r + 12F), ARGB.color(a, 0xFFFFFF));
            } else wordOf = -1;
        }
    }

    // ---- once a tick: the boss whose ring shows without a lock, and the guard's own moments ---------------------------------

    private static boolean guardWas;
    private static int struckBy = -1, struckTicks, pressOf = -1, pressAge;
    private static double pressLeft;
    private static boolean pressHot;
    // a development run's count of what the ring promised against what the guard got (SPEC §67.2, the plan's stage 0)
    private static final boolean DEV = !net.neoforged.fml.loading.FMLEnvironment.isProduction();
    private static int presses, pressesHot, hotDeflected, answers;
    private static double offSum, offMax;

    static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            bossRing = -1;
            return;
        }
        // the duel body nearest its blow, among those that fight within 32 blocks
        int best = -1;
        double bestLeft = Double.MAX_VALUE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le) || le == p || !le.isAlive() || le.distanceToSqr(p) > 32 * 32 || !Strikes.duelist(le)) continue;
            double left = Strikes.left(le, 1F);
            if (Double.isNaN(left)) {
                if (best < 0) best = le.getId();
            } else if (left < bestLeft) {
                bestLeft = left;
                best = le.getId();
            }
        }
        bossRing = best;

        // the guard going up: what the ring showed at that moment (a miss's word and the development count read it)
        boolean guard = p.isUsingItem() && dev.awaken.arms.Arms.guards(p.getUseItem());
        if (guard && !guardWas) {
            int id = Lock.locked >= 0 ? Lock.locked : bossRing >= 0 ? bossRing : Lock.marked;
            Entity e = id >= 0 ? mc.level.getEntity(id) : null;
            double left = e == null ? Double.NaN : Strikes.left(e, 1F);
            if (!Double.isNaN(left) && left <= APPROACH) {
                Strikes.Kind kind = Strikes.comingKind(e);
                int window = mine(p, e, true) && Strikes.reaches(e) && Strikes.lands(e) ? window(p, kind) : 0;
                pressOf = id;
                pressAge = 0;
                pressLeft = left;
                pressHot = window > 0 && left <= window - BAND_EARLY && left >= BAND_LATE;
                presses++;
                if (pressHot) pressesHot++;
            }
            // a guard that goes up just after the blow struck was too late
            if (struckBy >= 0 && struckTicks <= 5) {
                wordOf = struckBy;
                wordAt = System.nanoTime();
                wordEarly = false;
                struckBy = -1;
            }
        }
        guardWas = guard;
        if (struckBy >= 0 && ++struckTicks > 5) struckBy = -1;
        if (pressOf >= 0 && ++pressAge > 30) pressOf = -1;
    }

    /** Arms' client says how the guard answered a blow (Strikes.installAnswers): the ring's burst, a miss's word, the count. */
    static void onAnswer(Strikes.Answer answer, int attackerId) {
        long now = System.nanoTime();
        Minecraft mc = Minecraft.getInstance();
        switch (answer) {
            case DEFLECT -> {
                burstOf = attackerId;
                burstAt = now;
            }
            case BLOCK, GUARD_BREAK -> {
                wordOf = attackerId;
                wordAt = now;
                wordEarly = true;
            }
            case STRUCK -> {
                // what a block or a broken guard let through is the same blow, already answered
                if (wordEarly && wordOf == attackerId && now - wordAt < 150_000_000L) return;
                struckBy = attackerId;
                struckTicks = 0;
            }
        }
        if (!DEV || mc.level == null) return;
        Entity e = attackerId >= 0 ? mc.level.getEntity(attackerId) : null;
        double left = e == null ? Double.NaN : Strikes.left(e, mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
        boolean pressed = pressOf == attackerId;
        // only a blow the ring said reaches you measures the ring: a wave or a shot of the same body lands on its own time
        boolean said = !Double.isNaN(left) && Strikes.reaches(e);
        if (said) {
            answers++;
            offSum += left;
            offMax = Math.max(offMax, Math.abs(left));
        }
        if (pressed && pressHot && answer == Strikes.Answer.DEFLECT) hotDeflected++;
        Awaken.LOG.info("[fights] ring: {} with the ring at {} ticks{}{}", answer, Double.isNaN(left) ? "n/a" : String.format(java.util.Locale.ROOT, "%+.2f", left),
                Double.isNaN(left) || said ? "" : " (the ring said this blow passes by)",
                pressed ? String.format(java.util.Locale.ROOT, "; guard raised at %.2f ticks (%s)", pressLeft, pressHot ? "in the band" : "outside it") : "");
        if (pressed) pressOf = -1;
    }

    /** Leaving the world: a development run's totals, then a clean slate. */
    static void reset() {
        if (DEV && (answers > 0 || presses > 0)) {
            Awaken.LOG.info("[fights] ring totals: {} blows answered, the ring off by {} ticks on average (at most {}); guard raised {} times on a ring, {} in the band, {} of those deflected",
                    answers, answers == 0 ? "n/a" : String.format(java.util.Locale.ROOT, "%+.2f", offSum / answers), String.format(java.util.Locale.ROOT, "%.2f", offMax),
                    presses, pressesHot, hotDeflected);
        }
        presses = pressesHot = hotDeflected = answers = 0;
        offSum = offMax = 0;
        bossRing = burstOf = wordOf = struckBy = pressOf = -1;
        guardWas = false;
    }

    /** The lock: the ring, the ticks and the diamond, springing closed when it takes. */
    private static void lockRing(GuiGraphicsExtractor g, float sx, float sy, float r, int colour, long now, boolean reduced) {
        float t = ms(now, Lock.lockedAt);
        float k = reduced ? 1F : spring(t);
        float scale = Mth.lerp(k, 1.9F, 1F);
        float alpha = Mth.clamp(t / 90F, 0F, 1F);
        // a blow that lands: the ring kicks out and flashes white for a moment
        float hit = 1F - Mth.clamp(ms(now, Lock.hitAt) / 170F, 0F, 1F);
        if (!reduced) scale *= 1F + 0.09F * hit * hit;
        int ringColour = mix(colour, LIGHT, 0.55F * hit);
        float spin = now / 1.0E9F * 16F + (reduced ? 0F : 70F * (1F - k));
        float size = r * scale * 2.45F;
        glowed(g, RING, RING_G, 128, 128, sx, sy, size, size, spin, ringColour, alpha);
        // the ticks come in from further out, a beat behind the ring
        float kt = reduced ? 1F : spring(t - 45F);
        float ts = r * Mth.lerp(kt, 2.6F, 1F) * 2.45F;
        glowed(g, TICKS, TICKS_G, 128, 128, sx, sy, ts, ts, 0F, ringColour, Mth.clamp((t - 45F) / 90F, 0F, 1F));
        // the diamond over it, dropping in, breathing
        float kd = reduced ? 1F : spring(t - 80F);
        float bob = reduced ? 0F : Mth.sin(now / 1.0E9F * 2.4F) * 0.7F;
        float dy = sy - r * scale - 8F - 12F * (1F - kd) + bob;
        glowed(g, DIAMOND, DIAMOND_G, 32, 32, sx, dy, 9F, 9F, 0F, ringColour, Mth.clamp((t - 80F) / 90F, 0F, 1F));
    }

    /** The mark: four corner brackets, springing in from wider. */
    private static void brackets(GuiGraphicsExtractor g, float sx, float sy, float r, int colour, long now, boolean reduced) {
        float t = ms(now, Lock.markedAt);
        float k = reduced ? 1F : spring(t * 1.3F);
        float half = r * Mth.lerp(k, 1.55F, 1.02F) + (reduced ? 0F : Mth.sin(now / 1.0E9F * 2.0F) * 0.5F);
        float alpha = 0.78F * Mth.clamp(t / 110F, 0F, 1F);
        float size = 13F;
        for (int i = 0; i < 4; i++) {
            float cx = sx + (i == 1 || i == 2 ? half : -half), cy = sy + (i >= 2 ? half : -half);
            // the bracket's corner sits at the square's corner: its 64 px art has the corner at (7, 7)
            float off = size * (32F - 7F) / 64F;
            float ox = (i == 1 || i == 2 ? -off : off), oy = (i >= 2 ? -off : off);
            glowed(g, BRACKET, BRACKET_G, 64, 64, cx + ox, cy + oy, size, size, i * 90F, colour, alpha);
        }
    }

    /** A lock on something out of the frame: the diamond at that edge, pointing the way. */
    private static void offScreen(GuiGraphicsExtractor g, LocalPlayer p, LivingEntity le, float pt, long now, int w, int h) {
        Vec3 eye = p.getEyePosition(pt);
        Vec3 c = le.getBoundingBox().getCenter();
        float relDeg = Mth.wrapDegrees(Lock.yawTo(eye, c) - p.getYRot(pt)), relP = Lock.pitchTo(eye, c) - p.getXRot(pt);
        float rel = relDeg * Mth.DEG_TO_RAD, ax = Mth.sin(rel), ay;
        // ahead but over or under the frame (a flier overhead): up or down; else round the way it lies
        if (Math.abs(relDeg) < 60F) ay = Math.abs(relP) < 1F ? Math.signum(relP + 0.01F) : Mth.clamp(relP / 30F, -1F, 1F);
        else ay = -Mth.cos(rel);
        float len = (float) Math.sqrt(ax * ax + ay * ay);
        if (len < 1.0E-3F) return;
        float ex = w / 2F + ax / len * (w / 2F - 18F), ey = h / 2F + ay / len * (h / 2F - 18F);
        float pulse = 0.7F + 0.3F * Mth.sin(now / 1.0E9F * 6F);
        glowed(g, EDGE, EDGE_G, 128, 32, ex, ey, 44F, 11F, (float) Math.toDegrees(Math.atan2(ax, -ay)), HOLO, 0.8F * pulse);
        glowed(g, DIAMOND, DIAMOND_G, 32, 32, ex - ax / len * 9F, ey - ay / len * 9F, 8F, 8F, 0F, HOLO, 0.9F);
    }

    /** The release's ring opening out and fading, and a fallen creature's splinters flying off the ring. */
    private static void gone(GuiGraphicsExtractor g, long now, int w, int h, boolean reduced) {
        float tr = ms(now, Lock.releasedAt), tb = ms(now, Lock.brokeAt);
        if (tr >= 220F && tb >= 520F) return;
        if (!project(Lock.goneX, Lock.goneY, Lock.goneZ, w, h, 1.1F)) return;
        float sx = P[0], sy = P[1], r = Lock.goneR;
        if (tr < 220F) {
            float f = tr / 220F;
            float size = r * (1F + 0.5F * f) * 2.45F;
            glowed(g, RING, RING_G, 128, 128, sx, sy, size, size, now / 1.0E9F * 16F, HOLO, (1F - f) * 0.8F);
        }
        if (tb < 520F) {
            float f = tb / 520F;
            float flash = 1F - Mth.clamp(tb / 120F, 0F, 1F);
            if (flash > 0) glowed(g, RING, RING_G, 128, 128, sx, sy, r * 2.45F * (1F + 0.25F * (1F - flash)), r * 2.45F, 0F, LIGHT, flash);
            if (reduced) return;
            float out = r * (1F + 1.6F * (1F - (1F - f) * (1F - f)));
            for (int i = 0; i < 10; i++) {
                float a = i * 36F + 11F * (i & 3);
                float rad = a * Mth.DEG_TO_RAD;
                float d = out * (0.85F + 0.3F * ((i * 7) % 5) / 5F);
                glowed(g, SHARD, SHARD_G, 16, 16, sx + Mth.sin(rad) * d, sy - Mth.cos(rad) * d, 4.5F, 9F, a + f * 90F * ((i & 1) == 0 ? 1 : -1), i % 3 == 0 ? LIGHT : HOLO,
                        (1F - f) * (1F - f));
            }
        }
    }

    /** A blow wound up out of view, within 12 blocks: an arc at that edge in the tell's colour, brighter as it nears. */
    private static void threats(GuiGraphicsExtractor g, LocalPlayer p, float pt, long now, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 eye = p.getEyePosition(pt);
        float viewYaw = p.getYRot(pt);
        float rad = Math.min(w, h) * 0.43F;
        int shown = 0;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (shown >= 4) break;
            if (!(e instanceof LivingEntity le) || le == p || !le.isAlive()) continue;
            if (le.distanceToSqr(p) > AimRules.THREAT_RANGE * AimRules.THREAT_RANGE) continue;
            // on the blow's own clock, as the ring is (SPEC §67.2): the arc brightens evenly and lasts until the blow lands
            double left = Strikes.left(le, pt);
            Strikes.Kind kind = Double.isNaN(left) ? Strikes.Kind.NONE : Strikes.comingKind(le);
            if (kind == Strikes.Kind.NONE || left <= 0 || !mine(p, le, true)) continue; // someone else's blow is no threat to you (SPEC §70.3)
            AABB b = le.getBoundingBox();
            Vec3 c = b.getCenter();
            if (project(c.x, c.y, c.z, w, h, 0.92F)) continue; // in view: its own tell (and the ring) say it
            float rel = Mth.wrapDegrees(Lock.yawTo(eye, c) - viewYaw);
            float a = rel * Mth.DEG_TO_RAD;
            float near = 1F - Mth.clamp((float) (left / APPROACH), 0F, 1F);
            float pulse = 0.8F + 0.2F * Mth.sin(now / 1.0E9F * 22F);
            float ex = w / 2F + Mth.sin(a) * rad * (w / (float) Math.min(w, h)) * 0.98F, ey = h / 2F - Mth.cos(a) * rad;
            int col = colour(kind);
            glowed(g, EDGE, EDGE_G, 128, 32, ex, ey, 64F + 18F * near, 16F + 4F * near, rel, col, (0.55F + 0.45F * near) * pulse);
            shown++;
        }
    }

    /** The dot: only when you can mine, place or use something (and no crosshair is drawn). */
    private static void dot(GuiGraphicsExtractor g, Minecraft mc, LocalPlayer p, float frame, int w, int h) {
        boolean want = !HudState.crosshair() && Dot.wanted(mc, p);
        dotAlpha += ((want ? 1F : 0F) - dotAlpha) * Math.min(1F, frame * (want ? 18F : 8F));
        if (dotAlpha < 0.02F) return;
        glowed(g, DOT, DOT_G, 16, 16, w / 2F, h / 2F, 6F, 6F, 0F, 0xEAF8FF, dotAlpha * 0.95F);
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------

    /** A shape with its light: the glow dark underneath (it reads on a bright sky), then tinted, then the hot core. */
    private static void glowed(GuiGraphicsExtractor g, Identifier core, Identifier glow, int tw, int th, float cx, float cy, float w, float h, float deg,
                               int colour, float alpha) {
        if (alpha <= 0.01F) return;
        sprite(g, glow, tw, th, cx, cy, w * 1.04F, h * 1.04F, deg, ARGB.color(Math.round(alpha * 0.34F * 255), 0x000000));
        sprite(g, glow, tw, th, cx, cy, w, h, deg, ARGB.color(Math.round(alpha * 0.62F * 255), colour));
        sprite(g, core, tw, th, cx, cy, w, h, deg, ARGB.color(Math.round(alpha * 255), mix(colour, LIGHT, 0.45F)));
    }

    private static void sprite(GuiGraphicsExtractor g, Identifier tex, int tw, int th, float cx, float cy, float w, float h, float deg, int argb) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        if (deg != 0F) pose.rotate(deg * Mth.DEG_TO_RAD);
        pose.scale(w / tw, h / th);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, -tw / 2, -th / 2, 0F, 0F, tw, th, tw, th, argb);
        pose.popMatrix();
    }

    /** A damped spring from 0 to 1 over about 200 ms, overshooting a little (the ring snapping shut). */
    static float spring(float ms) {
        if (ms <= 0F) return 0F;
        return 1F - (float) (Math.exp(-ms / 70.0) * Math.cos(ms / 70.0 * 1.35));
    }

    private static float ease(float f) {
        f = Mth.clamp(f, 0F, 1F);
        return 1F - (1F - f) * (1F - f) * (1F - f);
    }

    private static float ms(long now, long at) {
        return at == 0 ? Float.MAX_VALUE : (now - at) / 1.0E6F;
    }

    static int colour(Strikes.Kind kind) {
        return switch (kind) {
            case HEAVY -> HEAVY;
            case PERILOUS -> PERIL;
            case GRAB -> GRAB;
            default -> LIGHT;
        };
    }

    static int mix(int a, int b, float t) {
        int r = Math.round(Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255)), gr = Math.round(Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255)),
                bl = Math.round(Mth.lerp(t, a & 255, b & 255));
        return (r << 16) | (gr << 8) | bl;
    }
}
