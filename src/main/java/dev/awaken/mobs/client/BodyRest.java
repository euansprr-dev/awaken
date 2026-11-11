package dev.awaken.mobs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.awaken.Awaken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.AbstractCubeMobRenderer;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.CodRenderer;
import net.minecraft.client.renderer.entity.EndermiteRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.PhantomRenderer;
import net.minecraft.client.renderer.entity.PufferfishRenderer;
import net.minecraft.client.renderer.entity.SalmonRenderer;
import net.minecraft.client.renderer.entity.ShulkerRenderer;
import net.minecraft.client.renderer.entity.SilverfishRenderer;
import net.minecraft.client.renderer.entity.SpiderRenderer;
import net.minecraft.client.renderer.entity.SquidRenderer;
import net.minecraft.client.renderer.entity.TropicalFishRenderer;
import net.minecraft.client.renderer.entity.WitherBossRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.SheepRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * A body rests on the ground (SPEC §47.12). A dying or lying body (a death clip, vanilla's sideways flip, a carcass, a
 * fallen or toppled creature) is measured as it will be drawn this frame, every face of it, then leaned along a slope and
 * raised (or let down a little) until its lowest point lies a hair above the ground under that point. Without this a body
 * turned on its side sank by half its width, and its flat faces crossing or lying on the ground's own face shimmered.
 */
public final class BodyRest {
    private BodyRest() {}

    /** One of our renderers: the same transforms its submit makes before the model (scale, setupRotations, flip, scale, offset). */
    public interface Posed<S extends LivingEntityRenderState> {
        void bodyChain(S state, PoseStack pose);
    }

    /** The gap under the lowest point: no face shares the ground's plane (depth keeps 1/64 apart to past 64 blocks). */
    static final float HAIR = 1.0F / 64.0F;
    /** Ground at most this far above the feet bears the body (a slab's step); higher is a wall beside it. */
    static final float STEP = 9.0F / 16.0F;
    /** Blocks under the feet searched for ground. */
    static final int DEPTH = 2;
    /** The most a body hanging above its ground is let down (a fall in the air is the server's), its steepest lean. */
    static final float LOWER = 0.5F, LEAN = 20.0F * Mth.DEG_TO_RAD;
    /**
     * The most an outlying part (a held weapon, an antler, a wing tip, a tail: anything outside the middle half of the body
     * each way) may hold the body up above where its middle would lie; deeper, the part goes into the ground instead.
     */
    static final float PROP = 3.0F / 16.0F, CORE = 0.5F;
    /** At the foot of a higher step the ground rises over this much (blocks), so a body sweeping onto it never jumps. */
    static final float EDGE = 0.25F;
    /** Bodies further than this (squared, blocks) are left as drawn. */
    static final double FAR = 64.0 * 64.0;

    // the columns of ground round the feet, read once a body per frame
    private static final int R = 8, SIDE = 2 * R + 1;
    private static final float[] TOP = new float[SIDE * SIDE];
    private static final int[] SEEN = new int[SIDE * SIDE];
    private static int stamp;
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
    private static final PoseStack SCRATCH = new PoseStack();
    private static final Probe PROBE = new Probe();
    /** Renderers whose model could not be measured: left as drawn from then on. */
    private static final Set<Class<?>> BROKEN = new HashSet<>();

    private static @Nullable Level level;
    private static double x0, y0, z0;
    private static int bx0, bz0;
    /** The body this rest is pushed for (one at a time: bodies are drawn one after another). */
    private static @Nullable LivingEntityRenderState lifted;
    private static float lift, cy, leanX, leanZ;

    public static void register(IEventBus bus) {
        // lowest: pushed after every other mod's own push, so a later cancel can't strand it; popped first
        bus.addListener(EventPriority.LOWEST, RenderLivingEvent.Pre.class, BodyRest::onPre);
        bus.addListener(EventPriority.HIGHEST, RenderLivingEvent.Post.class, BodyRest::onPost);
    }

    static void onPre(RenderLivingEvent.Pre<?, ?, ?> e) {
        if (e.isCanceled() || lifted != null) return;
        LivingEntityRenderState s = e.getRenderState();
        if (s.deathTime <= 0.0F || s.isInvisible || s instanceof AvatarRenderState || s.distanceToCameraSq > FAR) return;
        LivingEntityRenderer<?, ?, ?> r = e.getRenderer();
        level = Minecraft.getInstance().level;
        if (level == null || BROKEN.contains(r.getClass()) || !(r instanceof Posed<?> || vanilla(r))) return;
        // eased in over the first ticks of a fall, so nothing jumps the moment it dies
        float k = Mth.smoothstep(Mth.clamp((s.deathTime - 1.0F) / 3.0F, 0.0F, 1.0F));
        if (k <= 0.0F) return;
        try {
            if (!rest(r, s, k)) return;
        } catch (RuntimeException ex) {
            BROKEN.add(r.getClass());
            Awaken.LOG.warn("Body rest: can't measure {}, drawn as it is from now on", r.getClass().getName(), ex);
            return;
        }
        PoseStack pose = e.getPoseStack();
        pose.pushPose();
        place(pose);
        lifted = s;
    }

    static void onPost(RenderLivingEvent.Post<?, ?, ?> e) {
        if (lifted == null || lifted != e.getRenderState()) return;
        e.getPoseStack().popPose();
        lifted = null;
    }

    /** Measures the body; false when it lies right as it is. */
    private static boolean rest(LivingEntityRenderer<?, ?, ?> r, LivingEntityRenderState s, float k) {
        x0 = s.x;
        y0 = s.y;
        z0 = s.z;
        bx0 = Mth.floor(x0);
        bz0 = Mth.floor(z0);
        stamp++;
        // the lie of the land round where it lies (not where its pose reaches, so the lean never jumps as it falls),
        // leaning in as it goes down
        float mid = top(0.0F, 0.0F);
        if (Float.isNaN(mid)) mid = 0.0F;
        float reach = Mth.clamp(Math.max(s.boundingBoxWidth, s.boundingBoxHeight) * 0.5F, 0.5F, 2.0F);
        float kl = Mth.smoothstep(Mth.clamp((s.deathTime - 1.0F) / 10.0F, 0.0F, 1.0F));
        float east = orMid(top(reach, 0.0F), mid) - orMid(top(-reach, 0.0F), mid);
        float south = orMid(top(0.0F, reach), mid) - orMid(top(0.0F, -reach), mid);
        lift = 0.0F;
        cy = mid;
        leanZ = Mth.clamp((float) Math.atan(east / (2.0F * reach)), -LEAN, LEAN) * kl; // about z: +x rises
        leanX = -Mth.clamp((float) Math.atan(south / (2.0F * reach)), -LEAN, LEAN) * kl; // about x: +z rises
        // the body as it will be drawn this frame, leaned: how far its lowest point must rise to clear the ground under it
        @SuppressWarnings("unchecked")
        EntityModel<LivingEntityRenderState> model = (EntityModel<LivingEntityRenderState>) (EntityModel<?>) r.getModel();
        model.setupAnim(s);
        measure(r, model, s);
        if (PROBE.minY == Float.POSITIVE_INFINITY) return false; // nothing drawn
        float up;
        if (!PROBE.held) {
            up = Math.max(0.0F, HAIR - PROBE.minY); // nothing under it in reach: at least not below its own feet
        } else {
            up = PROBE.settle(PROP * Math.max(1.0F, s.scale)) + HAIR + outer(s);
            if (up < 0.0F) up = s.isInWater ? 0.0F : Math.max(up, -LOWER);
        }
        lift = up * k;
        return lift != 0.0F || leanX != 0.0F || leanZ != 0.0F;
    }

    /** The lift, then the lean about the ground under the feet (the measure's order, outermost first). */
    private static void place(PoseStack pose) {
        pose.translate(0.0F, lift, 0.0F);
        if (leanX == 0.0F && leanZ == 0.0F) return;
        pose.translate(0.0F, cy, 0.0F);
        pose.rotate(Axis.ZP, leanZ);
        pose.rotate(Axis.XP, leanX);
        pose.translate(0.0F, -cy, 0.0F);
    }

    /** Runs the body's own chain and draws its model into the probe (every visible face, as the GPU gets it). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void measure(LivingEntityRenderer<?, ?, ?> r, EntityModel<LivingEntityRenderState> model, LivingEntityRenderState s) {
        PoseStack p = SCRATCH;
        p.pushPose();
        place(p);
        if (r instanceof Posed posed) posed.bodyChain(s, p);
        else vanillaChain(r, s, p);
        PROBE.reset();
        model.renderToBuffer(p, PROBE, 0, 0, -1);
        p.popPose();
    }

    /** Vanilla's chain for a body that falls by its sideways flip (LivingEntityRenderer.submit and setupRotations). */
    private static void vanillaChain(LivingEntityRenderer<?, ?, ?> r, LivingEntityRenderState s, PoseStack p) {
        p.scale(s.scale, s.scale, s.scale);
        p.rotateDegrees(Axis.YP, 180.0F - s.bodyRot);
        float fall = Math.min(1.0F, Mth.sqrt(Math.max(0.0F, (s.deathTime - 1.0F) / 20.0F * 1.6F)));
        boolean back = r instanceof SpiderRenderer || r instanceof SilverfishRenderer || r instanceof EndermiteRenderer;
        p.rotateDegrees(Axis.ZP, fall * (back ? 180.0F : 90.0F));
        p.scale(-1.0F, -1.0F, 1.0F);
        p.translate(0.0F, -1.501F, 0.0F);
    }

    /** A vanilla renderer whose body falls by the plain flip (not one that turns or scales its own way). */
    private static boolean vanilla(LivingEntityRenderer<?, ?, ?> r) {
        if (!r.getClass().getPackageName().startsWith("net.minecraft.")) return false;
        return !(r instanceof AbstractCubeMobRenderer || r instanceof PhantomRenderer || r instanceof WitherBossRenderer
                || r instanceof ShulkerRenderer || r instanceof ArmorStandRenderer || r instanceof SquidRenderer
                || r instanceof CodRenderer || r instanceof SalmonRenderer || r instanceof TropicalFishRenderer
                || r instanceof PufferfishRenderer);
    }

    /** What its layers add outside the model: a sheep's wool (1.75 px), worn armour (1 px). */
    private static float outer(LivingEntityRenderState s) {
        if (s instanceof SheepRenderState sh && !sh.isSheared) return 1.75F / 16.0F * s.scale;
        if (s instanceof HumanoidRenderState h && !(h.headEquipment.isEmpty() && h.chestEquipment.isEmpty()
                && h.legsEquipment.isEmpty() && h.feetEquipment.isEmpty())) return 1.0F / 16.0F * s.scale;
        return 0.0F;
    }

    private static float orMid(float h, float mid) {
        return Float.isNaN(h) ? mid : h;
    }

    /**
     * The ground's top under (x, z) (from the feet), NaN when there is none in reach or it is a wall. Within {@link #EDGE}
     * of a higher column it rises to meet it (never sinks toward a lower one): a slope, not a sheer jump.
     */
    static float top(float x, float z) {
        double wx = x0 + x, wz = z0 + z;
        int bx = Mth.floor(wx), bz = Mth.floor(wz);
        float h = column(bx, bz);
        if (Float.isNaN(h)) return h;
        float fx = (float) (wx - bx), fz = (float) (wz - bz);
        float rise = 0.0F;
        if (fx < EDGE) rise = Math.max(rise, ramp(h, column(bx - 1, bz), fx));
        else if (fx > 1.0F - EDGE) rise = Math.max(rise, ramp(h, column(bx + 1, bz), 1.0F - fx));
        if (fz < EDGE) rise = Math.max(rise, ramp(h, column(bx, bz - 1), fz));
        else if (fz > 1.0F - EDGE) rise = Math.max(rise, ramp(h, column(bx, bz + 1), 1.0F - fz));
        return h + rise;
    }

    private static float ramp(float h, float next, float d) {
        return Float.isNaN(next) || next <= h ? 0.0F : (next - h) * (1.0F - d / EDGE);
    }

    /** A column's ground (cached for this body). */
    private static float column(int bx, int bz) {
        int ix = bx - bx0 + R, iz = bz - bz0 + R;
        if (ix < 0 || iz < 0 || ix >= SIDE || iz >= SIDE) return Float.NaN;
        int i = iz * SIDE + ix;
        if (SEEN[i] != stamp) {
            SEEN[i] = stamp;
            TOP[i] = ground(bx, bz);
        }
        return TOP[i];
    }

    /**
     * The first ground down the column from a step above the feet. A block's top is where it stops a body, or where it is
     * seen when its outline spans the whole block (snow layers, soul sand, mud): grass and flowers are no ground.
     */
    private static float ground(int bx, int bz) {
        Level l = level;
        int from = Mth.floor(y0 + STEP), to = Mth.floor(y0) - DEPTH;
        for (int by = from; by >= to; by--) {
            POS.set(bx, by, bz);
            BlockState st = l.getBlockState(POS);
            if (st.isAir()) continue;
            VoxelShape hit = st.getCollisionShape(l, POS);
            double t = hit.isEmpty() ? Double.NEGATIVE_INFINITY : hit.max(Direction.Axis.Y);
            VoxelShape seen = st.getShape(l, POS);
            if (!seen.isEmpty() && seen.min(Direction.Axis.X) <= 0.0 && seen.max(Direction.Axis.X) >= 1.0
                    && seen.min(Direction.Axis.Z) <= 0.0 && seen.max(Direction.Axis.Z) >= 1.0) {
                t = Math.max(t, seen.max(Direction.Axis.Y));
            }
            if (t == Double.NEGATIVE_INFINITY) continue;
            double y = by + t - y0;
            return y > STEP + 1.0E-4 ? Float.NaN : (float) y;
        }
        return Float.NaN;
    }

    /** Takes the model's vertices: its lowest point, and how far each must rise to clear the ground under it. */
    private static final class Probe implements VertexConsumer {
        boolean held;
        float minY, minX, maxX, minZ, maxZ;
        private float[] xs = new float[4096], zs = new float[4096], needs = new float[4096];
        private int n;

        void reset() {
            held = false;
            n = 0;
            minY = minX = minZ = Float.POSITIVE_INFINITY;
            maxX = maxZ = Float.NEGATIVE_INFINITY;
        }

        /**
         * How far the body must rise: enough for its middle half (each way) to clear the ground, and for the rest to clear
         * it too, but an outlying part holds the middle up by at most {@code prop}.
         */
        float settle(float prop) {
            float cx = (minX + maxX) * 0.5F, cz = (minZ + maxZ) * 0.5F;
            float hx = (maxX - minX) * 0.5F * CORE, hz = (maxZ - minZ) * 0.5F * CORE;
            float all = Float.NEGATIVE_INFINITY, core = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < n; i++) {
                float need = needs[i];
                if (Float.isNaN(need)) continue;
                if (need > all) all = need;
                if (need > core && Math.abs(xs[i] - cx) <= hx && Math.abs(zs[i] - cz) <= hz) core = need;
            }
            if (core == Float.NEGATIVE_INFINITY) return all; // its middle is over no ground: the rest decides
            return Math.max(core, Math.min(all, core + prop));
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (y < minY) minY = y;
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (z < minZ) minZ = z;
            if (z > maxZ) maxZ = z;
            float t = top(x, z);
            if (!Float.isNaN(t)) held = true;
            if (n == xs.length) {
                xs = Arrays.copyOf(xs, n * 2);
                zs = Arrays.copyOf(zs, n * 2);
                needs = Arrays.copyOf(needs, n * 2);
            }
            xs[n] = x;
            zs[n] = z;
            needs[n] = Float.isNaN(t) ? Float.NaN : t - y;
            n++;
            return this;
        }

        // the rest of a vertex is not wanted
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setColor(int color) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setUv3(float u, float v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
        @Override public VertexConsumer setLineWidth(float width) { return this; }
    }
}
