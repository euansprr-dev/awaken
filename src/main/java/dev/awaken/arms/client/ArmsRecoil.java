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

    /** One spring: its start (the freeze's end), the blow's way and what it moves, by weight and size. */
    static final class Layer {
        long start;
        float dx, dz, shove, rise, turn, tip, k;
        /** Its clock stood still from {@code holdAt} for {@code holdFor} (a blow after it froze the body again). */
        long holdAt = Long.MAX_VALUE, holdFor;
        boolean live;

        /** The spring's travel now, as a share of its peak, times the body's size. */
        float at(long now) {
            if (!live) return 0;
            long t = now - start;
            if (now > holdAt) t -= Math.min(now - holdAt, holdFor);
            return ArmsRules.recoil(t / 1e9F) * k;
        }

        void copy(Layer o) {
            start = o.start;
            dx = o.dx;
            dz = o.dz;
            shove = o.shove;
            rise = o.rise;
            turn = o.turn;
            tip = o.tip;
            k = o.k;
            holdAt = o.holdAt;
            holdFor = o.holdFor;
            live = o.live;
        }
    }

    /**
     * One body's recoil: the newest blow's spring, and the one before it still settling (the two add, as springs do,
     * so a blow landing mid-recoil never snaps the body back to rest first).
     */
    static final class Slot {
        int entity = Integer.MIN_VALUE;
        long until, killAt, killFreeze;
        final Layer now = new Layer(), was = new Layer();
        boolean kill;
        // this frame's values, for the pose in Pre
        float tipNow, fallNow, fallX, fallZ;
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
        long freeze = ArmsComfort.hitStop() ? Math.max(0, freezeMs) * 1_000_000L : 0L;
        // the spring still settling carries on under the new one, its clock held through the new freeze
        if (s.entity == e.getId() && s.now.live && now < s.until) {
            s.was.copy(s.now);
            if (s.was.holdAt == Long.MAX_VALUE) {
                s.was.holdAt = now;
                s.was.holdFor = freeze;
            }
        } else {
            s.was.live = false;
        }
        float weight = heft.weight * (b.strong() ? ArmsRules.STRONG_WEIGHT : 1F);
        float w = Mth.clamp((weight - ArmsRules.Heft.SNAP.weight) / (ArmsRules.Heft.HEAVY.weight * ArmsRules.STRONG_WEIGHT - ArmsRules.Heft.SNAP.weight), 0, 1);
        Layer n = s.now;
        n.live = true;
        n.start = now + freeze;
        n.holdAt = Long.MAX_VALUE;
        n.holdFor = 0;
        n.dx = b.dirX();
        n.dz = b.dirZ();
        n.k = sizeScale(e.getBbWidth(), e.getBbHeight()) * (ArmsComfort.reduced() ? 0.5F : 1F);
        n.shove = heft.shove * (b.strong() ? ArmsRules.STRONG_WEIGHT : 1F);
        // a pinning blow drives the body down, a lifting one up
        n.rise = b.lift() * 0.5F;
        // the near side moves along the cut: a cut to the right turns the body's yaw down
        n.turn = -b.sideShare() * Mth.lerp(w, 6F, 12F);
        n.tip = Mth.lerp(w, 4F, 8F);
        s.entity = e.getId();
        if (b.kill() && !s.kill) {
            s.kill = true;
            s.killAt = now;
            s.killFreeze = freeze;
            s.fallX = n.dx;
            s.fallZ = n.dz;
        }
        s.until = n.start + (s.kill ? KILL_LIFE : LIFE);
        anyUntil = Math.max(anyUntil, s.until);
    }

    /** A body's death clock, less the kill's freeze: it holds upright through the beat, then falls on vanilla's timing. */
    static float fall(float deathTime, long since, long freeze) {
        float held = Math.min(since, freeze) / 50_000_000F;
        float t = deathTime - held;
        return t <= 1F ? 0F : Math.min(1F, Mth.sqrt((t - 1F) / 20F * 1.6F));
    }

    private static Slot slot(int entity) {
        for (Slot s : SLOTS) if (s.entity == entity) return s;
        long now = System.nanoTime();
        for (Slot s : SLOTS) if (s.entity == Integer.MIN_VALUE || now > s.until) return fresh(s);
        Slot s = SLOTS[next];
        next = (next + 1) % POOL;
        return fresh(s);
    }

    private static Slot fresh(Slot s) {
        s.entity = Integer.MIN_VALUE;
        s.kill = false;
        s.now.live = s.was.live = false;
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
            fresh(s);
            return;
        }
        Layer a = s.now, b = s.was;
        float xa = a.at(now), xb = b.at(now);
        state.x += a.dx * a.shove * xa + b.dx * b.shove * xb;
        state.y += a.rise * a.shove * xa + b.rise * b.shove * xb;
        state.z += a.dz * a.shove * xa + b.dz * b.shove * xb;
        living.bodyRot += a.turn * xa + b.turn * xb;
        // one tip about the newest blow's axis: the older one's projected onto it
        s.tipNow = a.tip * xa + b.tip * xb * (a.dx * b.dx + a.dz * b.dz);
        // a body the blow killed: vanilla's flip on vanilla's timing (held through the kill's freeze), turned to
        // fall along the killing blow
        s.fallNow = s.kill && living.deathTime > 0 ? fall(living.deathTime, now - s.killAt, s.killFreeze) : 0F;
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
        PoseStack pose = e.getPoseStack();
        pose.pushPose();
        // about the flat axis across the blow, at the feet: the top goes along the blow (the fall along the killing one)
        if (s.fallNow > 0) pose.rotate(Q.identity().rotateAxis(90F * s.fallNow * Mth.DEG_TO_RAD, s.fallZ, 0, -s.fallX));
        if (s.tipNow != 0) pose.rotate(Q.identity().rotateAxis(s.tipNow * Mth.DEG_TO_RAD, s.now.dz, 0, -s.now.dx));
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
        for (Slot s : SLOTS) fresh(s);
        frame();
    }
}
