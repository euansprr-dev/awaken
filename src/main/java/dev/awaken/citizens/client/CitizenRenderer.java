package dev.awaken.citizens.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.Awaken;
import dev.awaken.citizens.Act;
import dev.awaken.citizens.CitizenBody;
import dev.awaken.citizens.CitizensContent;
import dev.awaken.citizens.Roster;
import dev.awaken.core.client.skin.SkinnedMobRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

import static dev.awaken.citizens.client.CitizenClips.*;

/**
 * Citizens drawn as people (SPEC §12 core.client.skin, never AvatarRenderState): each wears its own generated skin
 * (the roster's look; Oren, Mira, Teodor and Sun their townsfolk skins), at its own height, fading in when it comes
 * into range and out when it goes in or away, posed by its job clip over vanilla's walk, with a crate on the back when
 * a porter carries one.
 */
public final class CitizenRenderer extends SkinnedMobRenderer<CitizenBody, CitizenRenderState> {
    private static Identifier[] skins, blinks;
    private static boolean[] slim;
    private static final Identifier FALLBACK = Awaken.id("textures/entity/townsfolk/oren.png");
    private static final float FADE_IN = 16f;
    private static final float BLEND = 5f;

    private final CitizenModel wideModel, slimModel;

    public CitizenRenderer(EntityRendererProvider.Context ctx) {
        this(ctx, new CitizenModel(ctx.bakeLayer(ModelLayers.PLAYER), false), new CitizenModel(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true));
    }

    private CitizenRenderer(EntityRendererProvider.Context ctx, CitizenModel wide, CitizenModel slim) {
        super(ctx, wide, slim, 0.5f);
        this.wideModel = wide;
        this.slimModel = slim;
        addLayer(new BackLayer(this));
        addLayer(new HeldLayer(this, ctx));
        addLayer(new LanternLayer(this, new CritterModel(ctx.bakeLayer(CritterRenderer.layer("city_hand_lantern")),
                CritterRenderer.rigs().get("city_hand_lantern"), "citizen")));
    }

    @Override
    public CitizenRenderState createRenderState() {
        return new CitizenRenderState();
    }

    /** One skin per record, decided once (no allocation per frame); a missing texture falls back to a townsfolk skin. */
    private static void skins() {
        if (skins != null) return;
        Roster r = Roster.get();
        int n = r == null ? 0 : r.records;
        skins = new Identifier[n];
        blinks = new Identifier[n];
        slim = new boolean[n];
        var rm = Minecraft.getInstance().getResourceManager();
        for (int i = 0; i < n; i++) {
            String path;
            if (!r.named[i].isEmpty()) path = "textures/entity/townsfolk/" + r.named[i];
            else if (i >= r.citizens) path = String.format(Locale.ROOT, "textures/entity/citizen/t%d", i - r.citizens);
            else path = String.format(Locale.ROOT, "textures/entity/citizen/c%03d", i);
            Identifier id = Awaken.id(path + ".png"), blink = Awaken.id(path + "_blink.png");
            if (rm.getResource(id).isEmpty()) id = FALLBACK;
            skins[i] = id;
            // the closed-eyes copy the life layer's blink shows (generated beside every skin; none: no blink)
            blinks[i] = rm.getResource(blink).isPresent() ? blink : null;
            slim[i] = r.named[i].isEmpty() && r.build[i] == 1;
        }
    }

    @Override
    public void extractRenderState(CitizenBody body, CitizenRenderState s, float partial) {
        super.extractRenderState(body, s, partial);
        skins();
        int rec = body.record();
        s.rec = rec;
        // (SPEC §28.4) outside Carillon a body wears the Carillon skin the server picked for it from the pool
        int skin = body.look();
        if (skin >= 0 && skin < skins.length) s.wear(skins[skin], slim[skin]);
        else s.wear(FALLBACK, false);
        float age = body.tickCount + partial;
        float a = Math.min(1f, age / FADE_IN);
        if (body.fadeStart >= 0) a = Math.min(a, 1f - (body.tickCount - body.fadeStart + partial) / CitizenBody.FADE_TICKS_CLIENT);
        s.alpha = Mth.clamp(a, 0f, 1f);
        s.crate = body.has(CitizenBody.F_CRATE);
        s.hurt = body.has(CitizenBody.F_HURT);
        // the clip and the one it crossfades from
        Act cur = Act.of(body.cAct), prev = Act.of(body.pAct);
        float t = body.tickCount - body.cStart + partial;
        s.hasPose = cur != Act.NONE && CitizenClips.sample(cur, t + (rec & 7) * 3, s.pose);
        // (M4 B3b, Euan: "the boatman isn't holding anything") the work in hand for clips that mime it empty-handed
        int heldKind = switch (cur) {
            case COIL, HAUL -> HeldProp.ROPE;
            case MEND -> HeldProp.NET;
            case SERVE -> body.getMainHandItem().isEmpty() ? HeldProp.PARCEL : HeldProp.NONE;
            default -> HeldProp.NONE;
        };
        s.held = null;
        if (heldKind != HeldProp.NONE && s.hasPose) {
            HeldProp hp;
            if (body.clientHeld instanceof HeldProp got) hp = got;
            else body.clientHeld = hp = new HeldProp();
            hp.kind = heldKind;
            hp.state.ageInTicks = age;
            hp.state.clip = 0;
            hp.state.clipTime = age / 20F + (rec & 7) * 0.27F;
            hp.state.prevWeight = 0F;
            hp.state.life = null;
            s.held = hp;
        }
        s.working = switch (cur) {
            case NONE, ARMS_CROSSED, HANDS_BEHIND, LEAN, SIT, LOOK_AROUND, TALK, HALBERD_STAND -> false;
            default -> true;
        };
        s.blend = Math.min(1f, t / BLEND);
        s.hasPrev = s.blend < 1f && prev != Act.NONE && CitizenClips.sample(prev, body.tickCount - body.pStart + partial, s.prevPose);
        if (s.crate) {
            if (crate == null) crate = new ItemStack(CitizensContent.PORTER_CRATE.get());
            itemModelResolver.updateForLiving(s.backItem, crate, ItemDisplayContext.NONE, body);
        }
        else s.backItem.clear();
        s.height = body.getBbHeight();
        // a lantern: the 3D one in the off hand replaces the flat item; it lights its bearer, and CityGlow throws its light
        // from the flame the lantern layer finds
        s.lantern = null;
        if (CityGlow.carries(body)) {
            s.lightCoords = net.minecraft.util.LightCoordsUtil.withBlock(s.lightCoords, Math.max(13, net.minecraft.util.LightCoordsUtil.block(s.lightCoords)));
            LanternHold h;
            if (body.clientLantern instanceof LanternHold held) h = held;
            else body.clientLantern = h = new LanternHold();
            s.lantern = h;
            s.leftHandItemState.clear(); // the off hand (the right holds the Watch's halberd)
            // the swing: the lantern lags the arm's swing a quarter beat, and sways a little at a stand
            float walk = s.walkAnimationSpeed, pos = s.walkAnimationPos;
            h.state.xRot = Mth.sin(pos * 0.6662F - 0.9F) * 0.55F * walk + Mth.sin(age * 0.11F + rec) * 0.05F;
            h.state.yRot = Mth.sin(age * 0.07F + rec * 1.7F) * 0.04F;
            h.flicker = CityGlow.flicker(age / 20F, body.getId());
            h.state.ageInTicks = age;
            h.state.clip = 0;
            h.state.clipTime = age / 20F + (rec & 7) * 0.31F;
            h.state.prevWeight = 0F;
            h.state.life = null;
        }
        // what they say, fading in and out; their name when you look at them up close
        s.speech = null;
        if (body.speech != null && body.tickCount < body.speechUntil && s.distanceToCameraSq < 28 * 28) {
            float in = (body.tickCount - body.speechStart + partial) / 5f, out = (body.speechUntil - body.tickCount - partial) / 10f;
            s.speech = body.speech;
            s.speechAlpha = Mth.clamp(Math.min(in, out), 0f, 1f) * s.alpha;
            s.speechKind = body.speechKind;
        }
        Minecraft mc = Minecraft.getInstance();
        life(body, s, mc, rec, partial);
        s.label = null;
        if (mc.crosshairPickEntity == body && s.distanceToCameraSq < 9 * 9 && s.speech == null && !body.staged()) {
            s.label = CitizenFx.label(body);
            s.labelWidth = body.labelWidth;
        }
    }

    @Override
    public void submit(CitizenRenderState s, PoseStack pose, SubmitNodeCollector collector, net.minecraft.client.renderer.state.level.CameraRenderState camera) {
        super.submit(s, pose, collector, camera);
        if (s.speech == null && s.label == null) return;
        var font = getFont();
        pose.pushPose();
        pose.translate(0f, s.height + 0.42f, 0f);
        pose.rotate(camera.orientation);
        float scale = s.speech != null && s.speechKind == 2 ? 0.027f : 0.024f;
        pose.scale(scale, -scale, scale);
        if (s.speech != null) {
            int a = Math.max(4, Math.round(s.speechAlpha * 255f));
            int lines = s.speech.size();
            float lh = font.lineHeight + 1;
            float top = -lines * lh;
            float w = 0;
            for (var line : s.speech) w = Math.max(w, font.width(line));
            int bg = Math.round(s.speechAlpha * 150f) << 24 | (s.speechKind == 2 ? 0x3A1A10 : 0x2A2018);
            collector.submitTextBackground(pose, -w / 2f - 3, top - 2, w / 2f + 3, 1, bg, net.minecraft.client.gui.Font.DisplayMode.NORMAL, s.lightCoords);
            for (int k = 0; k < lines; k++) {
                var line = s.speech.get(k);
                float x = -font.width(line) / 2f, y = top + k * lh;
                // a faint copy seen through posts and awnings, then the line itself
                collector.submitText(pose, x, y, line, false, net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH,
                        net.minecraft.util.LightCoordsUtil.FULL_BRIGHT, (a / 3) << 24 | 0xFFFFFF, 0, 0);
                collector.submitText(pose, x, y, line, false, net.minecraft.client.gui.Font.DisplayMode.NORMAL,
                        net.minecraft.util.LightCoordsUtil.FULL_BRIGHT, a << 24 | 0xFFFFFF, 0, 0);
            }
        } else {
            collector.submitText(pose, -s.labelWidth / 2f, -font.lineHeight, s.label, false, net.minecraft.client.gui.Font.DisplayMode.NORMAL,
                    net.minecraft.util.LightCoordsUtil.FULL_BRIGHT, 0xE0FFFFFF, 0x50201810, 0);
        }
        pose.popPose();
    }

    /**
     * Creature life's layer (SPEC §23, M4 B3a): breath, blinks, the slow head with its idle glances and settles. The head
     * turns to the player only up close (6 blocks, in front of them) or while they speak; otherwise it keeps its own
     * idle looks. Clips win where they drive a bone (CitizenModel applies them after the layer). Client only; nothing is
     * allocated per frame once a body has its life.
     */
    private void life(CitizenBody body, CitizenRenderState s, Minecraft mc, int rec, float partial) {
        boolean sl = s.slim;
        CitizenModel m = sl ? slimModel : wideModel;
        var life = dev.awaken.mobs.client.life.CreatureLife.life(body.clientLife, m.rig, body.getId());
        body.clientLife = life;
        life.walk = Mth.clamp(s.walkAnimationSpeed * 2.5f, 0f, 1f);
        life.busy = s.hasPose;
        life.hurt = s.hurt ? 0.6f : 0f;
        boolean talking = s.speech != null;
        float look = 0f;
        var cam = mc.gameRenderer.mainCamera();
        if (cam != null && s.distanceToCameraSq < 6 * 6) {
            var eye = cam.position();
            double dx = eye.x - s.x, dz = eye.z - s.z, dy = eye.y - (s.y + s.height * 0.92);
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
            float rel = Mth.wrapDegrees(yaw - s.bodyRot);
            // (M4 B3b, Euan: a knot of boatmen all staring at him) only someone you talk to or stand right in front of
            // looks at you for long; otherwise an idle citizen glances now and then, briefly, in a window of its own (about
            // one window in six, 2.5 s of every 4), and nobody at work looks up from it
            boolean close = s.distanceToCameraSq < 2.4 * 2.4 && Math.abs(rel) < 50f;
            int window = (int) (s.ageInTicks / 80f) + body.getId() * 3;
            boolean glance = !s.working && Math.floorMod(window * 0x9E3779B1, 6) == 0 && s.ageInTicks % 80f < 50f;
            if (Math.abs(rel) < 80f && (talking || close || glance)) {
                s.yRot = rel;
                s.xRot = (float) (-Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
                look = 1f;
            }
        }
        life.lookWeight = look > 0 || talking ? 1f : 0f;
        dev.awaken.mobs.client.life.CreatureLife.tick(life, s, true);
        s.life = life;
        s.lifeSlim = sl;
        if (rec >= 0 && rec < blinks.length && blinks[rec] != null && dev.awaken.mobs.client.life.CreatureLife.eyesClosed(life) > 0.5f) {
            s.skin = blinks[rec];
        }
    }

    /** Made on first use: an ItemStack can't be built while the renderer class loads (components aren't bound yet). */
    private static ItemStack crate;

    @Override
    protected int getModelTint(CitizenRenderState s) {
        int a = Math.round(s.alpha * 255f);
        return a << 24 | 0xFFFFFF;
    }

    @Override
    protected float getShadowRadius(CitizenRenderState s) {
        return super.getShadowRadius(s) * s.alpha;
    }

    @Override
    protected void setupRotations(CitizenRenderState s, PoseStack pose, float bodyRot, float scale) {
        super.setupRotations(s, pose, bodyRot, scale);
        if (!s.hasPose && !s.hasPrev) return;
        float w = s.hasPose ? s.blend : 0f;
        float lean = CitizenModel.mix(s, ROOT_RX, 0f, w), tilt = CitizenModel.mix(s, ROOT_RZ, 0f, w);
        float px = CitizenModel.mix(s, ROOT_PX, 0f, w), py = CitizenModel.mix(s, ROOT_PY, 0f, w), pz = CitizenModel.mix(s, ROOT_PZ, 0f, w);
        if (lean == 0f && tilt == 0f && px == 0f && py == 0f && pz == 0f) return;
        // the library's "body" bone in entity space: about the waist (12 px up), x flipped, y up, front is -z
        pose.translate(-px / 16f, py / 16f, pz / 16f);
        pose.translate(0f, 0.75f, 0f);
        if (lean != 0f) pose.rotateDegrees(Axis.XP, -lean);
        if (tilt != 0f) pose.rotateDegrees(Axis.ZP, tilt);
        pose.translate(0f, -0.75f, 0f);
    }

    /**
     * What citizens work with, in 3D (M4 B3b, Euan): a coil of rope over the off hand while coiling or hauling, a net
     * hanging from the hand while mending, a paper parcel in the hand while serving. Rope and net hang plumb from the fist
     * (they swing with the hand, not its turn); the parcel is held as the hand holds it.
     */
    private static final class HeldLayer extends RenderLayer<CitizenRenderState, dev.awaken.core.client.skin.SkinModel<CitizenRenderState>> {
        private final CritterModel rope, netModel, parcel;
        private static final Identifier ROPE = Awaken.id("textures/entity/city/city_prop_rope.png"),
                NET = Awaken.id("textures/entity/city/city_prop_net.png"), PARCEL = Awaken.id("textures/entity/city/city_prop_parcel.png");

        HeldLayer(CitizenRenderer parent, EntityRendererProvider.Context ctx) {
            super(parent);
            rope = model(ctx, "city_prop_rope");
            netModel = model(ctx, "city_prop_net");
            parcel = model(ctx, "city_prop_parcel");
        }

        private static CritterModel model(EntityRendererProvider.Context ctx, String id) {
            return new CritterModel(ctx.bakeLayer(CritterRenderer.layer(id)), CritterRenderer.rigs().get(id), "citizen");
        }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector collector, int light, CitizenRenderState s, float yRot, float xRot) {
            HeldProp h = s.held;
            if (h == null || s.alpha < 0.1F) return;
            var m = getParentModel();
            boolean left = h.kind == HeldProp.ROPE;
            var arm = left ? m.leftArm : m.rightArm;
            pose.pushPose();
            m.root().translateAndRotate(pose);
            arm.translateAndRotate(pose);
            pose.translate((left ? 1F : -1F) / 16F, 10F / 16F, 0F); // the fist
            CritterModel model;
            Identifier tex;
            float sc;
            if (h.kind == HeldProp.PARCEL) {
                // in the hand, as the hand holds it: the parcel sits in the palm, in front of the fist
                pose.rotate(Axis.XP.rotation((float) Math.PI / 2F));
                pose.translate(0F, -1F / 16F, -1.5F / 16F);
                model = parcel;
                tex = PARCEL;
                sc = 0.8F;
            } else {
                // plumb from the fist: undo the arm's own turn (applied Z, Y, X)
                pose.rotate(Axis.XP.rotation(-arm.xRot));
                pose.rotate(Axis.YP.rotation(-arm.yRot));
                pose.rotate(Axis.ZP.rotation(-arm.zRot));
                model = h.kind == HeldProp.ROPE ? rope : netModel;
                tex = h.kind == HeldProp.ROPE ? ROPE : NET;
                sc = h.kind == HeldProp.ROPE ? 1.0F : 0.9F; // (props tour: the coil read small at seven blocks)
                if (h.kind == HeldProp.NET) pose.translate(left ? 0F : 0.25F, 0F, -0.12F); // between the hands, in front
            }
            pose.scale(sc, sc, sc);
            int a = Math.round(s.alpha * 255F);
            collector.submitModel(model, h.state, pose, net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(tex), light,
                    OverlayTexture.NO_OVERLAY, a << 24 | 0xFFFFFF, null, s.outlineColor);
            pose.popPose();
        }
    }

    /**
     * The Watch's hand lantern, in 3D (M4 B3b, Euan): it hangs from the off hand's fist by its bail, plumb whatever the
     * arm does, swinging a little behind the arm's swing; its glass and flame glow (full bright, flickering with the light
     * CityGlow throws). Where the flame is this frame goes back to the bearer's LanternHold, camera-relative, so the halo and
     * the pool on the paving come from the flame itself.
     */
    private static final class LanternLayer extends RenderLayer<CitizenRenderState, dev.awaken.core.client.skin.SkinModel<CitizenRenderState>> {
        private static final Identifier SKIN = Awaken.id("textures/entity/city/city_hand_lantern.png"),
                GLOW = Awaken.id("textures/entity/city/city_hand_lantern_glow.png");
        /** The flame's middle in the lantern's model (pixels below the grip), and the lantern's size in the hand. */
        private static final float FLAME_Y = 6.6F / 16F, SCALE = 0.9F;
        private final CritterModel model;
        private final org.joml.Vector3f v = new org.joml.Vector3f();

        LanternLayer(RenderLayerParent<CitizenRenderState, dev.awaken.core.client.skin.SkinModel<CitizenRenderState>> parent, CritterModel model) {
            super(parent);
            this.model = model;
        }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector collector, int light, CitizenRenderState s, float yRot, float xRot) {
            LanternHold h = s.lantern;
            if (h == null || s.alpha < 0.1F) return;
            var m = getParentModel();
            var arm = m.leftArm;
            pose.pushPose();
            m.root().translateAndRotate(pose);
            arm.translateAndRotate(pose);
            pose.translate(1F / 16F, 10F / 16F, 0F); // the fist at the end of the arm
            // plumb: undo the arm's own turn (it was applied Z, Y, X), then the swing behind it
            pose.rotate(Axis.XP.rotation(-arm.xRot + h.state.xRot));
            pose.rotate(Axis.YP.rotation(-arm.yRot + h.state.yRot));
            pose.rotate(Axis.ZP.rotation(-arm.zRot));
            pose.scale(SCALE, SCALE, SCALE);
            int a = Math.round(s.alpha * 255F);
            collector.submitModel(model, h.state, pose, net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(SKIN), light,
                    OverlayTexture.NO_OVERLAY, a << 24 | 0xFFFFFF, null, s.outlineColor);
            float glow = Mth.clamp(0.72F + 0.28F * h.flicker, 0F, 1F) * s.alpha;
            collector.order(1).submitModel(model, h.state, pose, net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentEmissive(GLOW),
                    net.minecraft.util.LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, net.minecraft.util.ARGB.white(glow), null, 0);
            pose.last().pose().transformPosition(0F, FLAME_Y, 0F, v);
            h.rx = v.x;
            h.ry = v.y;
            h.rz = v.z;
            h.frame = CityGlow.frame();
            pose.popPose();
        }
    }

    /** A porter's crate, strapped high on the back. */
    private static final class BackLayer extends RenderLayer<CitizenRenderState, dev.awaken.core.client.skin.SkinModel<CitizenRenderState>> {
        BackLayer(RenderLayerParent<CitizenRenderState, dev.awaken.core.client.skin.SkinModel<CitizenRenderState>> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector collector, int light, CitizenRenderState s, float yRot, float xRot) {
            if (!s.crate || s.backItem.isEmpty()) return;
            pose.pushPose();
            var m = getParentModel();
            m.root().translateAndRotate(pose);
            m.body.translateAndRotate(pose);
            // between the shoulder blades (+z is the back here, as for the cape), a whole crate wider than the torso
            pose.translate(0f, 0.34f, 0.45f);
            pose.scale(0.6f, 0.6f, 0.6f);
            s.backItem.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, s.outlineColor);
            pose.popPose();
        }
    }
}
