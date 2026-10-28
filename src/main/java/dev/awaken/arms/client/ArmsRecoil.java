package dev.awaken.arms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.Awaken;
import dev.awaken.arms.ArmsRules;
import dev.awaken.arms.Blow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.joml.Quaternionf;

/**
 * The struck body's answer (SPEC §25 "Fight Feel"): once a blow's freeze ends, the body recoils on a damped spring,
 * shoved along the blow, turned with the cut and tipped away, less the bigger it is (a ravager barely rocks, a
 * silverfish is thrown about). A body the blow killed falls the way the blade went instead of vanilla's flip onto its
 * side, on vanilla's timing. The shove and the turn ride the render state (every renderer); the tip and the fall a
 * pose pushed in {@code RenderLivingEvent.Pre} at the lowest priority (so a form or a creature drawing its own body,
 * which cancels the event, plays its own clip with the blow's direction instead). Our own creatures play their clips
 * from {@code CombatFeedback.blow} and are left alone here. A fixed pool, no allocation per frame.
 */
final class ArmsRecoil {
    private ArmsRecoil() {}

    /** A recoil is let go this long after its start; a kill holds its fall until the body is gone. */
    private static final long LIFE = 600_000_000L, KILL_LIFE = 2_500_000_000L;
    /** Bodies recoiling at once. */
    static final int POOL = 32;

    /** One body's recoil. */
    static final class Slot {
        int entity = Integer.MIN_VALUE;
        long start, until;
        float dx, dz, shove, rise, turn, tip, k;
        boolean kill;
        // this frame's values, for the pose in Pre
        float tipNow, fallNow;
    }

    private static final Slot[] SLOTS = new Slot[POOL];
    private static int next;
    /** The last recoil's end: past it, the hook has nothing to do (most frames). */
    private static long anyUntil;
    static final ContextKey<Slot> KEY = new ContextKey<>(Awaken.id("arms_recoil"));

    static {
        for (int i = 0; i < POOL; i++) SLOTS[i] = new Slot();
    }

    /** The body's size scale: about 1 for a zombie, down to a quarter for the big, up to double for the small. */
    static float sizeScale(float width, float height) {
        return Mth.clamp(1.5F / (width * height + 0.3F), 0.25F, 2F);
    }

    /** A blow landed on {@code e} (its freeze {@code freezeMs}): the recoil starts as the freeze lets go. */
    static void struck(Entity e, Blow b, ArmsRules.Heft heft, int freezeMs) {
        if (!(e instanceof LivingEntity l) || !ArmsComfort.recoil() || ArmsRules.lordBody(l.getMaxHealth())) return;
        if (Awaken.MODID.equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace())) return;
        Minecraft mc = Minecraft.getInstance();
        if (e == mc.getCameraEntity()) return;
        Slot s = slot(e.getId());
        long now = System.nanoTime();
        float weight = heft.weight * (b.strong() ? ArmsRules.STRONG_WEIGHT : 1F);
        float w = Mth.clamp((weight - ArmsRules.Heft.SNAP.weight) / (ArmsRules.Heft.HEAVY.weight * ArmsRules.STRONG_WEIGHT - ArmsRules.Heft.SNAP.weight), 0, 1);
        float k = sizeScale(e.getBbWidth(), e.getBbHeight());
        s.entity = e.getId();
        s.start = now + Math.max(0, freezeMs) * 1_000_000L;
        s.kill |= b.kill();
        s.until = s.start + (s.kill ? KILL_LIFE : LIFE);
        anyUntil = Math.max(anyUntil, s.until);
        s.dx = b.dirX();
        s.dz = b.dirZ();
        s.k = k;
        s.shove = heft.shove * (b.strong() ? ArmsRules.STRONG_WEIGHT : 1F) * (ArmsComfort.reduced() ? 0.5F : 1F);
        // a pinning blow drives the body down, a lifting one up
        s.rise = b.lift() * 0.5F;
        // the near side moves along the cut: a cut to the right turns the body's yaw down
        s.turn = -b.sideShare() * Mth.lerp(w, 6F, 12F);
        s.tip = Mth.lerp(w, 4F, 8F);
    }

    private static Slot slot(int entity) {
        for (Slot s : SLOTS) if (s.entity == entity) return s;
        long now = System.nanoTime();
        for (Slot s : SLOTS) {
            if (s.entity == Integer.MIN_VALUE || now > s.until) {
                s.kill = false;
                return s;
            }
        }
        Slot s = SLOTS[next];
        next = (next + 1) % POOL;
        s.kill = false;
        return s;
    }

    /** Render-state modifier: the shove and the turn, this frame's tip and fall marked for {@link #onPre}. */
    static void modify(Entity entity, EntityRenderState state) {
        if (!(state instanceof LivingEntityRenderState living) || System.nanoTime() > anyUntil) return;
        Slot s = null;
        for (Slot c : SLOTS) {
            if (c.entity == entity.getId()) {
                s = c;
                break;
            }
        }
        if (s == null) return;
        long now = System.nanoTime();
        if (now > s.until || entity.isRemoved()) {
            s.entity = Integer.MIN_VALUE;
            return;
        }
        float x = ArmsRules.recoil((now - s.start) / 1e9F) * s.k;
        state.x += s.dx * s.shove * x;
        state.y += s.rise * s.shove * x;
        state.z += s.dz * s.shove * x;
        living.bodyRot += s.turn * x;
        s.tipNow = s.tip * x;
        // a body the blow killed: vanilla's flip, on vanilla's timing, turned to fall along the blow
        s.fallNow = s.kill && living.deathTime > 0 ? Math.min(1F, Mth.sqrt(Math.max(0, living.deathTime - 1F) / 20F * 1.6F)) : 0F;
        state.setRenderData(KEY, s);
    }

    private static final Quaternionf Q = new Quaternionf();
    // the poses pushed, innermost last (a Post pops only what its own Pre pushed)
    private static final EntityRenderState[] PUSHED = new EntityRenderState[8];
    private static int depth;

    static void onPre(RenderLivingEvent.Pre<?, ?, ?> e) {
        LivingEntityRenderState state = e.getRenderState();
        Slot s = state.getRenderData(KEY);
        if (s == null || (s.tipNow == 0 && s.fallNow == 0) || depth >= PUSHED.length) return;
        if (s.fallNow > 0) state.deathTime = 0; // no flip to the side: our fall instead
        float a = (s.tipNow + 90F * s.fallNow) * Mth.DEG_TO_RAD;
        PoseStack pose = e.getPoseStack();
        pose.pushPose();
        // about the flat axis across the blow, at the feet: the top goes along the blow
        pose.rotate(Q.identity().rotateAxis(a, s.dz, 0, -s.dx));
        PUSHED[depth++] = state;
    }

    static void onPost(RenderLivingEvent.Post<?, ?, ?> e) {
        if (depth == 0 || PUSHED[depth - 1] != e.getRenderState()) return;
        PUSHED[--depth] = null;
        e.getPoseStack().popPose();
    }

    /** A frame begins: nothing pushed is left from the last one (a Post another listener cancelled away). */
    static void frame() {
        while (depth > 0) PUSHED[--depth] = null;
    }

    static void reset() {
        anyUntil = 0;
        for (Slot s : SLOTS) {
            s.entity = Integer.MIN_VALUE;
            s.kill = false;
        }
        frame();
    }
}
