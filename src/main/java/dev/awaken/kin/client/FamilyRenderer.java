package dev.awaken.kin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.Awaken;
import dev.awaken.kin.FamilyPet;
import dev.awaken.mobs.client.anim.HeroRig;
import dev.awaken.mobs.client.life.CreatureLife;
import dev.awaken.mobs.client.life.Life;
import dev.awaken.mobs.client.model.HeroModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws Aya and Dubu (SPEC §69.9) from their rigs ({@link FamilyData}), as the kin are drawn: the pose it holds picks the
 * clip of the same name, moving swaps in its walk or run, clips crossfade, and the life layer (breath, blinks, springy
 * ears and tail) goes on top. Asleep, its eyes are shut. Four things are theirs. A walk or a run is played by the ground
 * covered, a stride a cycle, so feet stay where they land (up to a cap, past which they patter as the game's own animals
 * do). Sitting down, lying down, curling up, the flop onto her back and getting up again are clips of their own
 * ({@code to_sit}, {@code from_rest}, ...), played once between standing and the pose, never a crossfade. Standing about
 * is one of three clips, picked afresh each time one ends. And a turn on the spot is stepped, not slid.
 */
public final class FamilyRenderer extends EntityRenderer<FamilyPet, KinRenderState> {
    private static @Nullable Map<String, HeroRig> rigs;

    static synchronized Map<String, HeroRig> rigs() {
        if (rigs == null) {
            Map<String, HeroRig> m = new LinkedHashMap<>();
            for (String[] src : FamilyData.SOURCES) {
                HeroRig r = HeroRig.parse(src);
                m.put(r.id, r);
            }
            rigs = m;
        }
        return rigs;
    }

    static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        for (HeroRig r : rigs().values()) e.registerLayerDefinition(KinRenderer.layer(r.id), () -> HeroModel.create(r));
    }

    /** What the client remembers of one body's clips (the server only sends its pose). */
    private static final class Anim {
        int clip = -1, prev = -1;
        float start, prevStart, prevEnd = -100F, fade = KinRenderer.FADE;
        /** The clip its pose asks for (what {@link #clip} is, or is on its way to through a getting-down or getting-up clip). */
        int goal = -1;
        /** While a getting-down or getting-up clip plays: when it ends, and the held pose it ends in (null: on its feet). */
        float bridgeEnd = -1F;
        @Nullable String bridgeTo;
        /** The standing-about clip in hand is kept until this age, then one is picked again. */
        float idleUntil;
        /** Turning on the spot: its body's heading last frame, and until when it steps. */
        float lastBody, turnUntil = -1F;
        /** Strides taken (cycles of a walk or run), and where and when it was last drawn. */
        float phase, lastAge = -1F;
        double lastX, lastZ;
        @Nullable Object life;
    }

    private final Map<FamilyPet, Anim> anims = new WeakHashMap<>();
    /** The held pose each clip belongs to ("sit", "rest", "sleep", "roll"); a clip not here is one on its feet. */
    private final Map<Integer, String> held = new java.util.HashMap<>();
    private final int[] idles;
    private final KinModel model;
    private final Identifier skin;
    private final @Nullable Identifier blink;
    private final float scale;
    private final int salt;

    public FamilyRenderer(EntityRendererProvider.Context ctx, String name) {
        super(ctx);
        String id = "family_" + name;
        model = new KinModel(ctx.bakeLayer(KinRenderer.layer(id)), rigs().get(id), FamilyData.TEMPER.getOrDefault(id, "cute"));
        String base = "textures/entity/family/" + name;
        skin = Awaken.id(base + ".png");
        Identifier b = Awaken.id(base + "_blink.png");
        blink = Minecraft.getInstance().getResourceManager().getResource(b).isPresent() ? b : null;
        scale = FamilyData.SCALE.getOrDefault(id, 0.5F);
        salt = name.hashCode();
        String[][] groups = {{"sit", "sit", "song", "groom", "gaze", "swat", "scratch"}, {"rest", "loaf", "lie"}, {"sleep", "sleep"}, {"roll", "roll"}};
        for (String[] g : groups) {
            for (int k = 1; k < g.length; k++) {
                int c = model.clip(g[k]);
                if (c >= 0) held.put(c, g[0]);
            }
        }
        // plain standing twice as often as each of the other two
        int i0 = model.clip("idle"), i1 = model.clip("idle_b"), i2 = model.clip("idle_c");
        idles = i1 < 0 || i2 < 0 ? new int[]{i0} : new int[]{i0, i0, i1, i2};
    }

    private boolean idle(int clip) {
        for (int i : idles) if (i == clip) return true;
        return false;
    }

    /** Starts a clip, fading from the one in hand over {@code fade} ticks. */
    private static void play(Anim a, int clip, float age, float fade) {
        a.prev = a.clip;
        a.prevStart = a.start;
        a.prevEnd = age;
        a.clip = clip;
        a.start = age;
        a.fade = fade;
    }

    @Override
    public KinRenderState createRenderState() {
        return new KinRenderState();
    }

    /** The clip for a pose: its own, or its walk or run while it moves (the giraffe stays in a carrying mouth). */
    private boolean gait(int clip) {
        return clip >= 0 && (clip == model.clip("move") || clip == model.clip("run") || clip == model.clip("carry"));
    }

    /** A pose it settles into or gets up from (those blends are slower: a movement, not a pop). */
    private boolean settles(int clip) {
        return clip >= 0 && !gait(clip) && !idle(clip) && clip != model.clip("nudge");
    }

    private int clipFor(int act, double speed, boolean turning) {
        boolean moving = speed > 0.015, fast = speed > 0.15;
        String name = act >= 0 && act < FamilyPet.ACTS.length ? FamilyPet.ACTS[act] : "idle";
        if (moving) name = "carry".equals(name) ? "carry" : fast ? "run" : "move";
        else if (turning && "idle".equals(name)) name = "move"; // turning on the spot: it steps round
        int clip = model.clip(name);
        if (clip < 0 && "rest".equals(name)) clip = model.clip(model.clip("loaf") >= 0 ? "loaf" : "lie");
        return clip < 0 ? model.clip("idle") : clip;
    }

    @Override
    public void extractRenderState(FamilyPet e, KinRenderState s, float partial) {
        super.extractRenderState(e, s, partial);
        float age = e.tickCount + partial;
        s.ageInTicks = age;
        s.bodyRot = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
        s.yRot = Mth.clamp(Mth.wrapDegrees(Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot) - s.bodyRot), -100F, 100F);
        s.xRot = e.getXRot(partial);
        double speed = Math.hypot(e.getX() - e.xo, e.getZ() - e.zo);
        s.walkAnimationSpeed = Mth.clamp((float) speed * 6F, 0F, 1F);
        s.modelScale = scale;
        s.airborne = false;
        s.gnawed = false;
        Anim a = anims.computeIfAbsent(e, x -> new Anim());
        float dt = a.lastAge < 0F ? 0F : Mth.clamp(age - a.lastAge, 0F, 3F);
        // a turn on the spot (more than four degrees a tick) is stepped for as long as it lasts and a moment after
        float turned = dt > 0F ? Math.abs(Mth.wrapDegrees(s.bodyRot - a.lastBody)) : 0F;
        if (dt > 0F && turned / Math.max(dt, 0.05F) > 4F) a.turnUntil = age + 4F;
        a.lastBody = s.bodyRot;
        int want = clipFor(e.act(), speed, age < a.turnUntil);
        if (idle(want)) {
            // standing about: the clip in hand is played through, then one of the three is picked again
            if (idle(a.goal) && age < a.idleUntil) want = a.goal;
            else {
                int h = (Math.round(age) * 73856093) ^ ((e.getId() + salt) * 19349663);
                want = idles[Math.floorMod(h ^ (h >>> 13), idles.length)];
                a.idleUntil = age + model.length(want) * 20F;
            }
        }
        if (want != a.goal) {
            String from = held.get(a.goal), to = held.get(want);
            // down into a pose it holds, or up out of one: the clip made for it, once (a one-shot such as a hello or a
            // stretch starts from standing by itself, and a change between two held poses is a blend)
            int via = -1;
            if (a.goal >= 0 && model.loops(want) && !java.util.Objects.equals(from, to)) {
                if (from == null) via = model.clip("to_" + to);
                else if (to == null) via = model.clip("from_" + from);
            }
            a.goal = want;
            if (via >= 0) {
                // changed its mind half way down (or up): the other clip is picked up at the matching moment
                float done = a.bridgeEnd >= 0F ? Mth.clamp((age - a.start) / Math.max(1F, a.bridgeEnd - a.start), 0F, 1F) : 1F;
                float len = model.length(via) * 20F;
                play(a, via, age, KinRenderer.FADE);
                a.start = age - (1F - done) * len;
                a.bridgeEnd = a.start + len;
                a.bridgeTo = to;
            } else if (a.bridgeEnd < 0F || !model.loops(want) || !java.util.Objects.equals(a.bridgeTo, to)) {
                play(a, want, age, settles(want) || settles(a.clip) ? 10F : KinRenderer.FADE);
                a.bridgeEnd = -1F;
            } // else the clip in hand already ends where this one begins: it is played out first
        }
        if (a.bridgeEnd >= 0F && age >= a.bridgeEnd) {
            play(a, a.goal, age, 6F);
            a.bridgeEnd = -1F;
        }
        // strides by the ground covered: a walk's cycle is one stride long, a run's one bound (a turn on the spot counts
        // a full circle as three strides)
        double x = Mth.lerp(partial, e.xo, e.getX()), z = Mth.lerp(partial, e.zo, e.getZ());
        if (dt > 0F) {
            boolean run = a.clip == model.clip("run");
            float stride = Math.max(0.1F, run ? model.rig().runStride : model.rig().walkStride);
            a.phase += Math.min((float) Math.hypot(x - a.lastX, z - a.lastZ) / stride + turned / 120F, (run ? 0.19F : 0.175F) * dt);
        }
        a.lastAge = age;
        a.lastX = x;
        a.lastZ = z;
        s.clip = a.clip;
        s.clipTime = gait(a.clip) ? a.phase * model.length(a.clip) : Math.max(0F, (age - a.start) / 20F);
        s.prevClip = a.prev;
        float since = age - a.prevEnd, fade = a.fade;
        s.prevWeight = a.prev >= 0 && since < fade ? 1F - Mth.smoothstep(Mth.clamp(since / fade, 0F, 1F)) : 0F;
        s.prevTime = gait(a.prev) ? a.phase * model.length(a.prev) : Math.max(0F, (age - a.prevStart) / 20F);
        Life life = CreatureLife.life(a.life, model.life, e.getId() * 31 + salt);
        a.life = life;
        life.walk = s.walkAnimationSpeed;
        life.airborne = false;
        life.busy = !gait(a.clip) && s.clipTime < 0.2F;
        CreatureLife.tick(life, s, true);
        s.life = life;
        boolean asleep = "sleep".equals(FamilyPet.ACTS[Math.max(0, Math.min(FamilyPet.ACTS.length - 1, e.act()))]);
        s.texture = blink != null && (asleep || CreatureLife.eyesClosed(life) > 0.5F) ? blink : skin;
        s.glow = null;
        s.glowAlpha = 0F;
        s.held.clear();
    }

    @Override
    protected float getShadowRadius(KinRenderState s) {
        return 0.3F * s.modelScale / 0.7F;
    }

    @Override
    public void submit(KinRenderState s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        pose.pushPose();
        pose.rotateDegrees(Axis.YP, 180F - s.bodyRot);
        if (s.life != null) CreatureLife.rootPose(s.life, pose, s);
        pose.scale(-s.modelScale, -s.modelScale, s.modelScale);
        pose.translate(0F, -1.501F, 0F);
        Identifier tex = s.texture == null ? skin : s.texture;
        out.submitModel(model, s, pose, RenderTypes.entityCutout(tex), s.lightCoords, OverlayTexture.NO_OVERLAY, -1, null, s.outlineColor);
        pose.popPose();
        super.submit(s, pose, out, camera);
    }
}
