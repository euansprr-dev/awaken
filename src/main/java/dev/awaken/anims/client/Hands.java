package dev.awaken.anims.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.Awaken;
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
 */
final class Hands implements FirstPerson.Player {
    static final Hands INSTANCE = new Hands();

    private Hands() {}

    private static final FirstPerson.View VIEW = new FirstPerson.View();
    private static final float[] M = new float[6], O = new float[6];
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
    }

    // ---- the hand pass ----------------------------------------------------------------------------------------------

    /** This frame's moves: every stance, then every clip (mirrored for a left main arm). */
    private static void compose(float pt, boolean leftMain) {
        VIEW.clear();
        List<FirstPerson.ViewPose> stances = FirstPerson.viewSources();
        for (int i = 0; i < stances.size(); i++) stances.get(i).pose(pt, VIEW);
        for (Playing p : PLAYING) {
            if (p.clip == null) continue;
            float age = (ticks + pt - p.start) * p.speed;
            p.clip.sample(age, M, O);
            float k = p.stopAt < 0 ? 1 : Math.max(0, 1 - (ticks + pt - p.stopAt) / FADE);
            if (leftMain) mirror(M, O);
            FirstPerson.View.add(VIEW.main, k, M[0], M[1], M[2], M[3], M[4], M[5]);
            FirstPerson.View.add(VIEW.off, k, O[0], O[1], O[2], O[3], O[4], O[5]);
            if (p.clip.both) VIEW.drawOff = true;
        }
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
        if (WorldBody.armsThisFrame) {
            e.setCanceled(true);
            mainPushed = false;
            return;
        }
        if (e.getHand() != InteractionHand.MAIN_HAND) return;
        mainPushed = false;
        try {
            AvatarRenderState avatar = e.getPlayerState().avatarRenderState;
            boolean leftMain = avatar != null && avatar.mainArm == HumanoidArm.LEFT;
            compose(e.getPartialTick(), leftMain);
            if (!VIEW.moved()) return;
            PoseStack ps = e.getPoseStack();
            apply(ps, VIEW.both); // stays on the stack for the off hand too
            if (VIEW.drawOff && avatar != null && Minecraft.getInstance().player != null && Minecraft.getInstance().player.getOffhandItem().isEmpty()) {
                drawArm(e, avatar, !leftMain);
            }
            if (VIEW.hideMain) {
                e.setCanceled(true);
                return;
            }
            ps.pushPose();
            mainPushed = true;
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
        if (broken || e.isCanceled() || WorldBody.armsThisFrame) return;
        if (VIEW.hideOff) {
            e.setCanceled(true);
            return;
        }
        apply(ps, VIEW.off); // the hand pass's stack is new every frame: nothing to undo after the last hand
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
