package dev.awaken.body.ascent.client;

import dev.awaken.body.ascent.AscentState;
import dev.awaken.body.ascent.WayUp;
import dev.awaken.core.client.AwakenKeys;
import dev.awaken.core.client.ui.Kit;
import dev.awaken.core.client.ui.Sheet;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.entity.GauntletGhost;
import dev.awaken.signal.client.Glass;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The Lesson (SPEC §63.3; Euan 2026-11-10: "it has to be clear the moment that you unlock it"): one kit for every unlock.
 * At the moment a move becomes yours, your own key is drawn as a cap on the thing to aim at (pinned to the world, held at
 * the screen's edge with an arrow when it is off it), a pale gold figure of you does the move once ahead of you, the
 * prompt leaves at the tick you do it, and a card of the move's keys shows for four seconds. While a lesson shows it
 * holds tips and the System's other messages back, so nothing is ever drawn over it. In a fight there is no scene, only a
 * strip of keys ({@link Kind#SKY_STRIP}). A lesson owed (a rung handed down by a later fight) is offered when you are
 * free, for twelve seconds, then again a minute later, until the move has been done once.
 */
final class Lesson {
    private Lesson() {}

    enum Kind {
        /** The vault's mast, after the Vow's own pull. */
        ROPE_MAST(WayUp.Rung.ROPE, true, "Throw it at the mast"),
        ROPE_OWED(WayUp.Rung.ROPE, true, "Hold to throw your rope"),
        /** The chasm's edge. */
        LEAP_EDGE(WayUp.Rung.LEAP, false, "Press again in the air"),
        LEAP_OWED(WayUp.Rung.LEAP, false, "Jump, then press again in the air"),
        /** Off a sky platform's pier. */
        FIRST_FLIGHT(WayUp.Rung.WINGS, false, "Run off the end, then press twice"),
        WINGS_OWED(WayUp.Rung.WINGS, false, "In a long fall, press twice"),
        /** In the Sky Warden's fight: keys only, top of the screen. */
        SKY_STRIP(WayUp.Rung.SKY, false, ""),
        FORM(WayUp.Rung.FORM, false, "Press when the gold line is full");

        final WayUp.Rung rung;
        final boolean hold;
        final String words;

        Kind(WayUp.Rung rung, boolean hold, String words) {
            this.rung = rung;
            this.hold = hold;
            this.words = words;
        }
    }

    /** Ticks: a prompt fades in and out, a card stays, an owed prompt shows and then rests, the figure comes again. */
    private static final int FADE = 8, CARD = 80, OWED_FOR = 240, OWED_REST = 1200, GHOST_AGAIN = 240;

    private static @Nullable Kind kind;
    private static int age, doneAt = -1;
    private static @Nullable Vec3 target;
    private static boolean jumpWas;
    private static int ropeHeld;

    private static @Nullable String cardName;
    private static List<Kit.Hint> cardHints = List.of();
    private static int cardAge = -1;

    /** The next tick an owed lesson may be offered (the client's own clock). */
    private static int clock, owedNext = 200;

    // ---- beginning and ending ---------------------------------------------------------------------------------------

    static boolean showing() {
        return kind != null || cardAge >= 0;
    }

    static @Nullable Kind kind() {
        return kind;
    }

    /** A lesson begins: its prompt at {@code at} (null: under the crosshair), and the gold figure along {@code path}. */
    static void begin(Kind k, @Nullable Vec3 at, Vec3 @Nullable [] path) {
        if (kind == k) return;
        endGhost();
        kind = k;
        target = at;
        age = 0;
        doneAt = -1;
        ropeHeld = 0;
        jumpWas = true;
        if (path != null && path.length >= 2 && !dev.awaken.body.client.feel.FeelConfig.reduced()) ghostBegin(path);
    }

    /** The move was done (or the moment has passed): the prompt leaves; with {@code withCard} its keys show for four seconds. */
    static void end(boolean withCard) {
        if (kind == null) return;
        Kind k = kind;
        if (doneAt < 0) doneAt = age;
        if (withCard) card(k.rung);
    }

    /** The card of a rung's keys: its name and two or three caps, no sentence. */
    static void card(WayUp.Rung r) {
        Minecraft mc = Minecraft.getInstance();
        String jump = name(mc.options.keyJump), sprint = name(mc.options.keySprint), sneak = name(mc.options.keyShift);
        switch (r) {
            case ROPE -> {
                cardName = "The rope";
                cardHints = List.of(Kit.Hint.hold("swing", name(AwakenKeys.ROPE)), Kit.Hint.of("let go to fly"));
            }
            case LEAP -> {
                cardName = "The leap";
                cardHints = List.of(Kit.Hint.of("again in the air", jump), Kit.Hint.of("dash", sprint), Kit.Hint.of("slam from high up", sneak));
            }
            case WINGS -> {
                cardName = "Wings";
                cardHints = List.of(Kit.Hint.of("twice in the air: open", jump), Kit.Hint.of("look to steer"), Kit.Hint.of("fold", sneak));
            }
            case SKY -> {
                cardName = "The open sky";
                cardHints = skyHints(mc);
            }
            case FORM -> {
                cardName = "The Sovereign's form";
                cardHints = List.of(Kit.Hint.of("when the gold line is full", name(SovereignClient.ASCEND)));
            }
        }
        cardAge = 0;
    }

    private static List<Kit.Hint> skyHints(Minecraft mc) {
        return List.of(Kit.Hint.of("again on open wings: fly", name(mc.options.keyJump)), Kit.Hint.of("push on", name(mc.options.keyUp)),
                Kit.Hint.of("boost", name(mc.options.keySprint)));
    }

    /** A key by the player's own binding; "Not set" for one with no key (the card then names the Controls page). */
    static String name(KeyMapping k) {
        return k.isUnbound() ? "Not set" : k.getTranslatedKeyMessage().getString();
    }

    static void reset() {
        endGhost();
        kind = null;
        target = null;
        cardAge = -1;
        doneAt = -1;
        owedNext = 200;
        hold(false);
    }

    private static void hold(boolean on) {
        dev.awaken.core.api.TipHooks.client().hold(Lesson.class, on);
        dev.awaken.core.api.Notices.client().hold(Lesson.class, on);
    }

    // ---- the tick ----------------------------------------------------------------------------------------------------

    static void tick(LocalPlayer p) {
        Minecraft mc = Minecraft.getInstance();
        clock++;
        if (cardAge >= 0 && ++cardAge > CARD) cardAge = -1;
        if (kind == null) {
            hold(cardAge >= 0);
            offerOwed(mc, p);
            return;
        }
        hold(true);
        age++;
        ghostTick(mc);
        if (doneAt >= 0) {
            if (age - doneAt > FADE) {
                kind = null;
                target = null;
                endGhost();
            }
            return;
        }
        boolean jump = mc.options.keyJump.isDown();
        AscentState s = AscentMoves.state();
        switch (kind) {
            case ROPE_MAST, ROPE_OWED -> {
                ropeHeld = AwakenKeys.ROPE.isDown() ? ropeHeld + 1 : 0;
                // the Vow's own pull is not the player's: the cap waits for their hand on the key
                if (age > 15 && ropeHeld >= 2) end(true);
                else if (kind == Kind.ROPE_OWED && age > OWED_FOR) rest();
            }
            case LEAP_EDGE, LEAP_OWED -> {
                if (age > 5 && !p.onGround() && jump && !jumpWas) end(kind == Kind.LEAP_OWED);
                else if (age > (kind == Kind.LEAP_EDGE ? 300 : OWED_FOR)) rest();
            }
            case FIRST_FLIGHT, WINGS_OWED -> {
                if (s == AscentState.GLIDE || s == AscentState.SOAR) end(true);
                else if (kind == Kind.WINGS_OWED && age > OWED_FOR) rest();
                else if (kind == Kind.FIRST_FLIGHT && (age > 20 * 90 || target != null && p.position().distanceToSqr(target) > 160 * 160)) rest();
            }
            case SKY_STRIP -> {
                if (s == AscentState.SOAR || age > 200) end(false);
            }
            case FORM -> {
                if (SovereignClient.ASCEND.isDown() && AscentMoves.aether >= 0.9F) end(false);
                else if (age > 20 * 30) rest();
            }
        }
        jumpWas = jump;
        // the figure shows it again for someone who has not moved yet
        if (kind != null && doneAt < 0 && ghost == null && path != null && age % GHOST_AGAIN == 0) ghostBegin(path);
    }

    /** The moment passed without the move: the prompt goes quietly; a lesson still owed comes back later. */
    private static void rest() {
        doneAt = age;
        owedNext = clock + OWED_REST;
    }

    /**
     * A lesson owed: offered when the player is free (no screen, on the ground, out of any Ascent state, nothing else
     * showing), the rope's first, then the leap's, then the wings'. The server marks it done when the move is made.
     */
    private static void offerOwed(Minecraft mc, LocalPlayer p) {
        if (clock < owedNext || cardAge >= 0 || WayClient.owed == 0) return;
        if (mc.gui.screen() != null || !p.onGround() || AscentMoves.active() || AscentScene.running() || VowScene.stage >= 0
                || dev.awaken.core.api.Notices.client().quiet()) return;
        owedNext = clock + OWED_REST;
        if (WayClient.isOwed(WayUp.Rung.ROPE)) begin(Kind.ROPE_OWED, null, null);
        else if (WayClient.isOwed(WayUp.Rung.LEAP)) begin(Kind.LEAP_OWED, null, hopPath(p, 6));
        else if (WayClient.isOwed(WayUp.Rung.WINGS)) begin(Kind.WINGS_OWED, null, null);
    }

    // ---- the gold figure ---------------------------------------------------------------------------------------------

    private static @Nullable GauntletGhost ghost;
    private static Vec3 @Nullable [] path;
    private static int ghostAge;
    private static int nextId = -0x6B0000;
    /** Ticks between two points of a figure's path. */
    private static final int STEP = 3;

    /** A run and two jumps ahead of the player, along their look: the leap, shown. */
    static Vec3[] hopPath(LocalPlayer p, double far) {
        Vec3 look = p.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1e-4) look = new Vec3(0, 0, 1);
        look = look.normalize();
        Vec3 o = p.position().add(look.scale(1.2));
        int n = 14;
        Vec3[] out = new Vec3[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) (n - 1);
            // two arcs: the jump, and the second one from its top
            double y = t < 0.2 ? 0 : t < 0.55 ? 1.25 * Math.sin((t - 0.2) / 0.35 * Math.PI * 0.5)
                    : 1.25 + 1.1 * Math.sin((t - 0.55) / 0.45 * Math.PI) - 2.35 * Math.max(0, (t - 0.8) / 0.2);
            out[i] = o.add(look.scale(t * far)).add(0, Math.max(0, y), 0);
        }
        return out;
    }

    /** A swing from one point to another, sagging in the middle: the rope, shown. */
    static Vec3[] arcPath(Vec3 from, Vec3 to, double sag) {
        int n = 12;
        Vec3[] out = new Vec3[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) (n - 1);
            out[i] = from.lerp(to, t).add(0, -sag * Math.sin(t * Math.PI), 0);
        }
        return out;
    }

    /** A run along a line and off its end: the first flight, shown as far as the jump. */
    static Vec3[] runOff(Vec3 from, Vec3 end, double dx, double dz) {
        int n = 10;
        Vec3[] out = new Vec3[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) (n - 1);
            out[i] = t <= 0.7 ? from.lerp(end, t / 0.7) : end.add(dx * (t - 0.7) * 16, 0.6 * Math.sin((t - 0.7) / 0.3 * Math.PI) - (t - 0.7) * 3, dz * (t - 0.7) * 16);
        }
        return out;
    }

    private static void ghostBegin(Vec3[] pts) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;
        endGhost();
        path = pts;
        ghost = new GauntletGhost(GatesRegistry.GHOST.get(), level);
        ghost.setId(nextId--);
        ghost.route(mc.player.getUUID(), List.of(), List.of());
        ghost.snapTo(pts[0].x, pts[0].y, pts[0].z, yaw(pts, 0), 0);
        level.addEntity(ghost);
        ghostAge = 0;
    }

    private static float yaw(Vec3[] pts, int i) {
        Vec3 a = pts[Math.min(i, pts.length - 2)], b = pts[Math.min(i + 1, pts.length - 1)];
        return (float) (Mth.atan2(b.z - a.z, b.x - a.x) * Mth.RAD_TO_DEG) - 90F;
    }

    private static void ghostTick(Minecraft mc) {
        GauntletGhost g = ghost;
        Vec3[] pts = path;
        if (g == null || pts == null) return;
        if (mc.level == null || g.level() != mc.level) {
            endGhost();
            return;
        }
        double f = ghostAge++ / (double) STEP;
        int i = (int) Math.floor(f);
        if (i >= pts.length - 1 + 6) {
            removeGhost();
            return;
        }
        int j = Math.min(i, pts.length - 2);
        Vec3 at = pts[j].lerp(pts[j + 1], Mth.clamp(f - j, 0, 1));
        float yaw = yaw(pts, j);
        g.xo = g.xOld = g.getX();
        g.yo = g.yOld = g.getY();
        g.zo = g.zOld = g.getZ();
        g.yRotO = g.getYRot();
        g.yBodyRotO = g.yBodyRot;
        g.yHeadRotO = g.yHeadRot;
        g.setPos(at.x, at.y, at.z);
        g.setYRot(yaw);
        g.yBodyRot = yaw;
        g.yHeadRot = yaw;
        g.calculateEntityAnimation(false);
        g.tickCount++;
    }

    /** The figure goes; its path is kept so it can show the move again. */
    private static void removeGhost() {
        if (ghost != null) {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) level.removeEntity(ghost.getId(), Entity.RemovalReason.DISCARDED);
        }
        ghost = null;
    }

    private static void endGhost() {
        removeGhost();
        path = null;
    }

    // ---- drawing -----------------------------------------------------------------------------------------------------

    private static final Vector3f V = new Vector3f();
    private static final Quaternionf Q = new Quaternionf();
    /** Where the target lands on screen (GUI pixels), and whether it had to be held at the edge. */
    private static float sx, sy;
    private static boolean off;

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden()) return;
        float pt = dt.getGameTimeDeltaPartialTick(false);
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        Kind k = kind;
        if (k != null) {
            float a = doneAt >= 0 ? 1F - Mth.clamp((age + pt - doneAt) / FADE, 0, 1) : Mth.clamp((age + pt) / FADE, 0, 1);
            if (a > 0.01F) {
                if (k == Kind.SKY_STRIP) strip(g, font, skyHints(mc), w / 2, (int) (h * 0.08F), a);
                else prompt(g, font, mc, k, w, h, a, pt);
            }
        }
        if (cardAge >= 0 && cardName != null) {
            float t = cardAge + pt;
            float a = Math.min(1F, Math.min(t / FADE, (CARD - t) / 12F));
            if (a > 0.01F) card(g, font, w, h, a);
        }
    }

    /** The cap and its words, pinned over the target with a stem and a ring, or under the crosshair with no target. */
    private static void prompt(GuiGraphicsExtractor g, Font font, Minecraft mc, Kind k, int w, int h, float a, float pt) {
        String key = switch (k) {
            case ROPE_MAST, ROPE_OWED -> name(AwakenKeys.ROPE);
            case FORM -> name(SovereignClient.ASCEND);
            default -> name(mc.options.keyJump);
        };
        int capW = Math.max(11, font.width(key) + 6) + (k.hold ? Math.max(11, font.width("Hold") + 6) + 2 : 0);
        int total = capW + 5 + font.width(k.words);
        boolean pinned = target != null && project(mc, target, w, h);
        int px, py;
        if (target == null) {
            // no thing to pin it to: under the crosshair; the form's sits low, over the gold line it speaks of
            px = w / 2;
            py = k == Kind.FORM ? h - 72 : h / 2 + 38;
        } else {
            px = Math.round(sx);
            py = Math.round(sy);
        }
        int x0 = Mth.clamp(px - total / 2, 6, Math.max(6, w - total - 6));
        int y0 = target == null ? py : Mth.clamp(py - 34, 8, h - 60);
        int rim = Glass.a(Glass.EDGE, 0.85F * a);
        g.fill(x0 - 5, y0 - 5, x0 + total + 5, y0 + 14, Sheet.argb(0x05080F, 0.62F * a));
        g.fill(x0 - 5, y0 - 5, x0 + total + 5, y0 - 4, rim);
        int x = x0;
        if (k.hold) x += Kit.cap(g, font, "Hold", x, y0, a, true) + 2;
        x += Kit.cap(g, font, key, x, y0, a, false) + 5;
        g.text(font, k.words, x, y0, Glass.a(Glass.TITLE, a), false);
        if (target == null) return;
        if (pinned && !off) {
            // a stem down to the thing, and a ring on it that breathes
            int cx = Mth.clamp(px, x0, x0 + total);
            if (py - 4 > y0 + 14) g.fill(cx, y0 + 14, cx + 1, py - 4, rim);
            float beat = 0.6F + 0.4F * Mth.sin((age + pt) * 0.25F);
            int ring = Glass.a(Glass.EDGE, a * beat);
            g.fill(px - 4, py - 4, px + 5, py - 3, ring);
            g.fill(px - 4, py + 4, px + 5, py + 5, ring);
            g.fill(px - 4, py - 3, px - 3, py + 4, ring);
            g.fill(px + 4, py - 3, px + 5, py + 4, ring);
        } else {
            // held at the edge: which way to turn
            String arrow = sx < w * 0.25F ? "<" : sx > w * 0.75F ? ">" : sy < h * 0.5F ? "^" : "v";
            g.text(font, arrow, sx < w * 0.25F ? x0 - 14 : x0 + total + 8, y0, Glass.a(Glass.EDGE, a), false);
        }
    }

    /** A world point to GUI pixels; behind the eye or outside the view it is held at the nearest edge ({@link #off}). */
    private static boolean project(Minecraft mc, Vec3 t, int w, int h) {
        Camera cam = mc.gameRenderer.mainCamera();
        Vec3 e = cam.position();
        Q.set(cam.rotation()).conjugate();
        V.set((float) (t.x - e.x), (float) (t.y - e.y), (float) (t.z - e.z)).rotate(Q);
        double tan = Math.tan(Math.toRadians(cam.getFov()) / 2);
        float aspect = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        float depth = Math.max(0.05F, Math.abs(V.z));
        float nx = (float) (V.x / depth / (tan * aspect)), ny = (float) (V.y / depth / tan);
        boolean behind = V.z >= -0.05F;
        if (behind) {
            // behind you: push it out to the side it lies on
            float len = Math.max(0.001F, Mth.sqrt(nx * nx + ny * ny));
            nx = nx / len * 2;
            ny = ny / len * 2;
        }
        off = behind || Math.abs(nx) > 0.92F || Math.abs(ny) > 0.86F;
        nx = Mth.clamp(nx, -0.92F, 0.92F);
        ny = Mth.clamp(ny, -0.7F, 0.86F);
        sx = (nx + 1) / 2 * w;
        sy = (1 - ny) / 2 * h;
        return true;
    }

    private static void card(GuiGraphicsExtractor g, Font font, int w, int h, float a) {
        int hints = Kit.hintLineWidth(font, cardHints);
        int nameW = font.width(cardName);
        int total = nameW + 10 + hints;
        int x0 = Math.max(6, (w - total) / 2), y0 = h - 78;
        g.fill(x0 - 7, y0 - 6, x0 + total + 7, y0 + 15, Sheet.argb(0x05080F, 0.7F * a));
        g.fill(x0 - 7, y0 - 6, x0 + total + 7, y0 - 5, Glass.a(Glass.EDGE, 0.7F * a));
        g.text(font, cardName, x0, y0, Glass.a(Glass.TITLE, a), false);
        Kit.hintLine(g, font, cardHints, x0 + nameW + 10, y0, a);
    }

    private static void strip(GuiGraphicsExtractor g, Font font, List<Kit.Hint> hints, int cx, int y, float a) {
        int total = Kit.hintLineWidth(font, hints);
        int x0 = cx - total / 2;
        g.fill(x0 - 7, y - 6, x0 + total + 7, y + 15, Sheet.argb(0x05080F, 0.6F * a));
        Kit.hintLine(g, font, hints, x0, y, a);
    }
}
