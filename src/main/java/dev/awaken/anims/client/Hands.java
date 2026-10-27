package dev.awaken.anims.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.Awaken;
import dev.awaken.anims.Ease;
import dev.awaken.anims.FpClipSet;
import dev.awaken.core.api.FirstPerson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.neoforge.client.event.RenderHandEvent;

import java.util.List;
import java.util.Map;

/**
 * The one owner of your hands in first person (core.api.FirstPerson): each frame it asks every stance for its moves,
 * adds the clips that are playing, and moves vanilla's own hands (and whatever they hold) by the sum, the same for
 * every module. While the world layer draws your arms on something in the world, vanilla's hands step aside.
 * <p>
 * Order with the other hand listeners (casts still draw their own arms): this runs first for each hand; the off hand's
 * part runs last ({@link #lastOff}), after anything else has undone its own main-hand moves.
 * <p>
 * What the hands hold is put away while a cast ({@code View.stow}: at once), a climb ({@code View.climb}) or the world
 * layer's arm on that side asks: it sinks out of view as vanilla's equip lowers an item while the fists come up from
 * under the frame, then is hidden; once nothing asks it rises back the same way as the last fists sink away.
 */
final class Hands implements FirstPerson.Player {
    static final Hands INSTANCE = new Hands();

    private Hands() {}

    private static final FirstPerson.View VIEW = new FirstPerson.View();
    private static final float[] M = new float[6], O = new float[6], CLIP_MAIN = new float[4], CLIP_OFF = new float[4];
    private static Map<String, FpClipSet.Clip> clips;

    /** Clips playing: their clip, start (ticks), speed, events fired so far, a fade out (ticks, or 0). */
    private static final class Playing {
        FpClipSet.Clip clip;
        float start, speed = 1, stopAt = -1;
        int fired;
    }

    private static final Playing[] PLAYING = new Playing[8];
    private static int ticks;
    private static boolean mainPushed, broken;

    static {
        for (int i = 0; i < PLAYING.length; i++) PLAYING[i] = new Playing();
    }

    static Map<String, FpClipSet.Clip> clips() {
        if (clips == null) {
            StringBuilder errors = new StringBuilder();
            clips = FpClipSet.load(errors);
            if (!errors.isEmpty()) Awaken.LOG.warn("First-person clips: {}", errors);
        }
        return clips;
    }

    // ---- the player -------------------------------------------------------------------------------------------------

    @Override
    public boolean play(String name, float speed) {
        FpClipSet.Clip c = clips().get(name);
        if (c == null) return false;
        Playing slot = null;
        for (Playing p : PLAYING) if (p.clip == c) slot = p;
        if (slot == null) for (Playing p : PLAYING) if (p.clip == null) {
            slot = p;
            break;
        }
        if (slot == null) slot = PLAYING[0];
        slot.clip = c;
        slot.start = ticks;
        slot.speed = Math.max(0.05F, speed);
        slot.stopAt = -1;
        slot.fired = 0;
        return true;
    }

    @Override
    public void stop(String name) {
        for (Playing p : PLAYING) if (p.clip != null && p.clip.name.equals(name) && p.stopAt < 0) p.stopAt = ticks;
    }

    @Override
    public boolean playing(String name) {
        for (Playing p : PLAYING) if (p.clip != null && p.clip.name.equals(name)) return true;
        return false;
    }

    @Override
    public boolean armsInWorld() {
        return WorldBody.armsThisFrame;
    }

    /** A clip stopped early fades out over this many ticks (a cut never snaps). */
    private static final float FADE = 3;

    static void tick() {
        ticks++;
        for (Playing p : PLAYING) {
            if (p.clip == null) continue;
            float age = (ticks - p.start) * p.speed;
            while (p.fired < p.clip.events.length && p.clip.eventAt[p.fired] <= age) FirstPerson.fire(p.clip.name, p.clip.events[p.fired++]);
            if (!p.clip.loop && age > p.clip.length || p.stopAt >= 0 && ticks - p.stopAt > FADE) p.clip = null;
        }
    }

    static void reset() {
        for (Playing p : PLAYING) p.clip = null;
        mainPushed = false;
        java.util.Arrays.fill(STOW, 0F);
        STOW_FIST_MAIN[3] = STOW_FIST_OFF[3] = 0;
        stowClock = Float.NaN;
        sinkMain = sinkOff = 0;
    }

    // ---- putting away what the hands hold (FirstPerson.View stow and climb) ---------------------------------------------

    /** How far each hand's held thing is put away (main, off; 0 in hand, 1 out of view), and whether this frame asks it. */
    private static final float[] STOW = new float[2];
    private static final boolean[] STOW_ASKED = new boolean[2];
    /** Each hand's fist while what it holds was put away (x, y, z, then 1 if it had one): it sinks from there after. */
    private static final float[] STOW_FIST_MAIN = new float[4], STOW_FIST_OFF = new float[4];
    /** Ticks to put a held thing away and to bring it back (vanilla's equip moves 0.4 a tick: 2.5). */
    private static final float STOW_DOWN = 2.5F, STOW_UP = 3F;
    /**
     * How far a held thing sinks (vanilla's equip lowers an item 0.6) and how far under its place a fist comes up from
     * (out of the frame at the hand pass's 70°, even from a hang's lip).
     */
    private static final float ITEM_DROP = 0.6F, FIST_DROP = 0.8F;
    private static float stowClock = Float.NaN;
    /** This frame's sink of what each hand holds (blocks), for both hand passes. */
    private static float sinkMain, sinkOff;

    /** Each hand's stow toward what this frame asks: a cast's at once, a climb's (and the world's arms') eased. */
    private static void stepStow(float now, boolean atOnce) {
        float dt = Float.isNaN(stowClock) ? 0 : Math.max(0, Math.min(2, now - stowClock));
        stowClock = now;
        for (int h = 0; h < 2; h++) {
            if (atOnce) STOW[h] = 1;
            else STOW[h] = STOW_ASKED[h] ? Math.min(1, STOW[h] + dt / STOW_DOWN) : Math.max(0, STOW[h] - dt / STOW_UP);
        }
        if (STOW[0] == 0) STOW_FIST_MAIN[3] = 0;
        if (STOW[1] == 0) STOW_FIST_OFF[3] = 0;
    }

    /** This frame's sinks from the stows, for what each hand holds (an empty hand has nothing to sink). */
    private static void sinks(boolean holdsMain, boolean holdsOff) {
        sinkMain = holdsMain ? Ease.at(Ease.SMOOTH, STOW[0]) * ITEM_DROP : 0;
        sinkOff = holdsOff ? Ease.at(Ease.SMOOTH, STOW[1]) * ITEM_DROP : 0;
    }

    private static void keep(float[] to, float[] f) {
        to[0] = f[0] / f[3];
        to[1] = f[1] / f[3];
        to[2] = f[2] / f[3];
        to[3] = 1;
    }

    private static boolean holdsOff(net.minecraft.client.player.LocalPlayer p) {
        return !p.getOffhandItem().isEmpty() || FirstPerson.offHandShows.getAsBoolean();
    }

    // ---- the hand pass ----------------------------------------------------------------------------------------------

    // ---- the procedural layer: the hands trail fast turns of the view ------------------------------------------------

    private static long lagNanos;
    private static float lagYaw, lagPitch, lagX, lagY, lagRoll;

    /** Look-lag: a damped spring toward a small offset against the view's turn (deg/s), stepped once a frame. */
    private static void lookLag(float pt) {
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        float yaw = p.getViewYRot(pt), pitch = p.getViewXRot(pt);
        long t = System.nanoTime();
        float dt = lagNanos == 0 ? 0.016F : Math.min(0.1F, Math.max(0.001F, (t - lagNanos) / 1e9F));
        float yawRate = net.minecraft.util.Mth.wrapDegrees(yaw - lagYaw) / dt, pitchRate = (pitch - lagPitch) / dt;
        lagNanos = t;
        lagYaw = yaw;
        lagPitch = pitch;
        float scale = FirstPerson.reducedMotion.getAsBoolean() ? 0.5F : 1F;
        float tx = clamp(-yawRate * 0.00022F, 0.06F) * scale, ty = clamp(pitchRate * 0.00018F, 0.045F) * scale;
        float tr = clamp(-yawRate * 0.005F, 4F) * scale;
        float k = 1 - (float) Math.exp(-dt * 14);
        lagX += (tx - lagX) * k;
        lagY += (ty - lagY) * k;
        lagRoll += (tr - lagRoll) * k;
        FirstPerson.View.add(VIEW.both, 1, lagX, lagY, 0, 0, 0, lagRoll);
    }

    private static float clamp(float v, float max) {
        return v < -max ? -max : v > max ? max : v;
    }

    /** This frame's moves: every stance, then every clip (mirrored for a left main arm), then the look-lag. */
    private static void compose(float pt, boolean leftMain) {
        VIEW.clear();
        lookLag(pt);
        java.util.Arrays.fill(CLIP_MAIN, 0F);
        java.util.Arrays.fill(CLIP_OFF, 0F);
        List<FirstPerson.ViewPose> stances = FirstPerson.viewSources();
        for (int i = 0; i < stances.size(); i++) stances.get(i).pose(pt, VIEW);
        for (Playing p : PLAYING) {
            if (p.clip == null) continue;
            float age = (ticks + pt - p.start) * p.speed;
            p.clip.sample(age, M, O);
            float k = p.stopAt < 0 ? 1 : Math.max(0, 1 - (ticks + pt - p.stopAt) / FADE);
            if (leftMain) mirror(M, O);
            if (p.clip.climb) VIEW.climb = true;
            if (p.clip.fists) {
                // a clip's fists take the hand from any stance's (a ledge jump over the hang's fists)
                if (p.clip.hasMain()) FirstPerson.View.fist(CLIP_MAIN, k, M[0], M[1], M[2]);
                if (p.clip.hasOff()) FirstPerson.View.fist(CLIP_OFF, k, O[0], O[1], O[2]);
                if (p.clip.stow) VIEW.stow = true;
                continue;
            }
            FirstPerson.View.add(VIEW.main, k, M[0], M[1], M[2], M[3], M[4], M[5]);
            FirstPerson.View.add(VIEW.off, k, O[0], O[1], O[2], O[3], O[4], O[5]);
            if (p.clip.both) VIEW.drawOff = true;
        }
        if (CLIP_MAIN[3] > 0) System.arraycopy(CLIP_MAIN, 0, VIEW.fistMain, 0, 4);
        if (CLIP_OFF[3] > 0) System.arraycopy(CLIP_OFF, 0, VIEW.fistOff, 0, 4);
        publishFists();
    }

    /** The fists this frame's clips keyed, for effects that start at a hand (FirstPerson.LAST_FIST_*). */
    private static void publishFists() {
        System.arraycopy(VIEW.fistMain, 0, FirstPerson.LAST_FIST_MAIN, 0, 4);
        System.arraycopy(VIEW.fistOff, 0, FirstPerson.LAST_FIST_OFF, 0, 4);
        FirstPerson.lastFistNanos = System.nanoTime();
    }

    private static void mirror(float[] a, float[] b) {
        a[0] = -a[0];
        a[4] = -a[4];
        a[5] = -a[5];
        b[0] = -b[0];
        b[4] = -b[4];
        b[5] = -b[5];
    }

    /** First for each hand: the world layer's arms hide vanilla's; the moves go on. */
    static void first(RenderHandEvent e) {
        if (broken) return;
        boolean main = e.getHand() == InteractionHand.MAIN_HAND;
        if (main) {
            WorldBody.drawNearLegs(e); // before any of the hands' moves
            mainPushed = false;
        }
        var pl = Minecraft.getInstance().player;
        if (pl == null) return;
        // the off hand holding a pair's twin is full: it pumps and moves with the dagger in it, never a stance's fist
        boolean holdsMain = !pl.getMainHandItem().isEmpty(), holdsOff = holdsOff(pl);
        if (WorldBody.armsThisFrame) {
            // the world layer's arms take the hand pass: vanilla's hand steps aside on each side the world body has an
            // arm, what it holds sinking away first as the arm comes up (back up after); the other stays as it is (a
            // touch with one hand leaves the sword in the other)
            AvatarRenderState av = e.getPlayerState().avatarRenderState;
            boolean rightMain = av == null || av.mainArm != HumanoidArm.LEFT;
            if (main) {
                WorldBody.drawArms(e);
                STOW_ASKED[0] = WorldBody.armShown(rightMain);
                STOW_ASKED[1] = WorldBody.armShown(!rightMain);
                STOW_FIST_MAIN[3] = STOW_FIST_OFF[3] = 0; // the world's arms let go in the world
                stepStow(ticks + e.getPartialTick(), false);
                sinks(holdsMain, holdsOff);
            }
            int h = main ? 0 : 1;
            if (main ? holdsMain : holdsOff) {
                if (STOW[h] >= 1) e.setCanceled(true);
                else if (main && sinkMain > 0) {
                    e.getPoseStack().pushPose();
                    mainPushed = true;
                    e.getPoseStack().translate(0, -sinkMain, 0);
                }
            } else if (WorldBody.armShown(main == rightMain)) {
                e.setCanceled(true);
            }
            return;
        }
        if (!main) return;
        try {
            AvatarRenderState avatar = e.getPlayerState().avatarRenderState;
            boolean leftMain = avatar != null && avatar.mainArm == HumanoidArm.LEFT;
            compose(e.getPartialTick(), leftMain);
            // what the hands hold is put away while a cast or a climb asks (a climb's sinks as the fists come up)
            boolean ask = VIEW.stow || VIEW.climb;
            STOW_ASKED[0] = STOW_ASKED[1] = ask;
            stepStow(ticks + e.getPartialTick(), VIEW.stow);
            sinks(holdsMain, holdsOff);
            if (!VIEW.moved() && sinkMain == 0 && sinkOff == 0) return;
            PoseStack ps = e.getPoseStack();
            float upMain = -FIST_DROP * (1 - sinkMain / ITEM_DROP), upOff = -FIST_DROP * (1 - sinkOff / ITEM_DROP);
            // fists first, before any offset: each empty hand with one has its arm drawn rising to it; a hand putting
            // away what it holds has its fist come up from under the frame as that sinks, and sink again as it returns
            boolean mainFist = VIEW.fistMain[3] > 0 && (!holdsMain || ask);
            if (mainFist) {
                if (holdsMain) keep(STOW_FIST_MAIN, VIEW.fistMain);
                fist(e, !leftMain, VIEW.fistMain, holdsMain ? upMain : 0);
            } else if (holdsMain && !ask && STOW_FIST_MAIN[3] > 0 && sinkMain > 0) {
                fist(e, !leftMain, STOW_FIST_MAIN, upMain);
            }
            boolean offFist = VIEW.fistOff[3] > 0 && (!holdsOff || ask);
            if (offFist) {
                if (holdsOff) keep(STOW_FIST_OFF, VIEW.fistOff);
                fist(e, leftMain, VIEW.fistOff, holdsOff ? upOff : 0);
            } else if (holdsOff && !ask && STOW_FIST_OFF[3] > 0 && sinkOff > 0) {
                fist(e, leftMain, STOW_FIST_OFF, upOff);
            }
            // vanilla's hand steps aside for an empty hand's fist, and for a held thing once it's out of view
            if (mainFist && !holdsMain || holdsMain && STOW[0] >= 1) VIEW.hideMain = true;
            if (offFist && !holdsOff || holdsOff && STOW[1] >= 1) VIEW.hideOff = true;
            apply(ps, VIEW.both); // stays on the stack for the off hand too
            // grips: a held thing swung far from its resting place is held by an arm rising to its grip (it still draws,
            // and sinks with it while it's put away)
            if (VIEW.gripMain[3] > 0 && !VIEW.hideMain) fist(e, !leftMain, VIEW.gripMain, -sinkMain);
            if (VIEW.gripOff[3] > 0 && !VIEW.hideOff) fist(e, leftMain, VIEW.gripOff, -sinkOff);
            if (VIEW.drawOff && !offFist && avatar != null && (!holdsOff || STOW[1] >= 1)) drawArm(e, avatar, !leftMain);
            if (VIEW.hideMain) {
                e.setCanceled(true);
                return;
            }
            ps.pushPose();
            mainPushed = true;
            if (sinkMain > 0) ps.translate(0, -sinkMain, 0); // straight down the view, as its grip goes
            apply(ps, VIEW.main);
        } catch (RuntimeException ex) {
            broken = true;
            Awaken.LOG.error("First-person hands failed and are off for this session", ex);
        }
    }

    /** Last for the off hand (cancelled or not): the main hand's own moves come off, the off hand's go on. */
    static void lastOff(RenderHandEvent e) {
        if (e.getHand() != InteractionHand.OFF_HAND) return;
        PoseStack ps = e.getPoseStack();
        if (mainPushed) {
            ps.popPose();
            mainPushed = false;
        }
        if (broken || e.isCanceled()) return;
        // the hand pass's stack is new every frame: nothing to undo after the last hand
        if (WorldBody.armsThisFrame) {
            if (sinkOff > 0) ps.translate(0, -sinkOff, 0);
            return;
        }
        if (VIEW.hideOff) {
            e.setCanceled(true);
            return;
        }
        if (sinkOff > 0) ps.translate(0, -sinkOff, 0);
        apply(ps, VIEW.off);
    }

    /** A fist (or a grip) at its weighted place, moved dy up the view (down when negative). */
    private static void fist(RenderHandEvent e, boolean right, float[] f, float dy) {
        float w = f[3];
        WorldBody.drawFist(e, right, f[0] / w, f[1] / w + dy, f[2] / w, VIEW.tint, VIEW.glow);
    }

    private static void apply(PoseStack ps, float[] m) {
        if (m[0] != 0 || m[1] != 0 || m[2] != 0) ps.translate(m[0], m[1], m[2]);
        if (m[3] != 0) ps.rotateDegrees(Axis.XP, m[3]);
        if (m[4] != 0) ps.rotateDegrees(Axis.YP, m[4]);
        if (m[5] != 0) ps.rotateDegrees(Axis.ZP, m[5]);
    }

    /** The off arm, empty, for a two-handed clip: vanilla's own empty-arm place on that side, moved by the off moves. */
    private static void drawArm(RenderHandEvent e, AvatarRenderState avatar, boolean right) {
        Minecraft mc = Minecraft.getInstance();
        AvatarRenderer<?> renderer = mc.getEntityRenderDispatcher().getRenderer(avatar);
        PoseStack ps = e.getPoseStack();
        float invert = right ? 1 : -1;
        ps.pushPose();
        apply(ps, VIEW.off);
        // vanilla's empty-hand arm transform (FirstPersonHandsAndItemsRenderer.renderPlayerArm), no swing, fully raised
        ps.translate(invert * 0.64000005F, -0.6F, -0.71999997F);
        ps.rotateDegrees(Axis.YP, invert * 45.0F);
        ps.translate(invert * -1.0F, 3.6F, 3.5F);
        ps.rotateDegrees(Axis.ZP, invert * 120.0F);
        ps.rotateDegrees(Axis.XP, 200.0F);
        ps.rotateDegrees(Axis.YP, invert * -135.0F);
        ps.translate(invert * 5.6F, 0.0F, 0.0F);
        var skin = avatar.skin.body().texturePath();
        if (right) renderer.renderRightHand(ps, e.getSubmitNodeCollector(), e.getPackedLight(), skin, avatar.showRightSleeve);
        else renderer.renderLeftHand(ps, e.getSubmitNodeCollector(), e.getPackedLight(), skin, avatar.showLeftSleeve);
        ps.popPose();
    }
}
