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
    private static Identifier[] skins;
    private static boolean[] slim;
    private static final Identifier FALLBACK = Awaken.id("textures/entity/townsfolk/oren.png");
    private static final float FADE_IN = 16f;
    private static final float BLEND = 5f;

    public CitizenRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new CitizenModel(ctx.bakeLayer(ModelLayers.PLAYER), false), new CitizenModel(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true), 0.5f);
        addLayer(new BackLayer(this));
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
        slim = new boolean[n];
        var rm = Minecraft.getInstance().getResourceManager();
        for (int i = 0; i < n; i++) {
            Identifier id;
            if (!r.named[i].isEmpty()) id = Awaken.id("textures/entity/townsfolk/" + r.named[i] + ".png");
            else if (i >= r.citizens) id = Awaken.id(String.format(Locale.ROOT, "textures/entity/citizen/t%d.png", i - r.citizens));
            else id = Awaken.id(String.format(Locale.ROOT, "textures/entity/citizen/c%03d.png", i));
            if (rm.getResource(id).isEmpty()) id = FALLBACK;
            skins[i] = id;
            slim[i] = r.named[i].isEmpty() && r.build[i] == 1;
        }
    }

    @Override
    public void extractRenderState(CitizenBody body, CitizenRenderState s, float partial) {
        super.extractRenderState(body, s, partial);
        skins();
        int rec = body.record();
        s.rec = rec;
        if (rec >= 0 && rec < skins.length) s.wear(skins[rec], slim[rec]);
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
        s.blend = Math.min(1f, t / BLEND);
        s.hasPrev = s.blend < 1f && prev != Act.NONE && CitizenClips.sample(prev, body.tickCount - body.pStart + partial, s.prevPose);
        if (s.crate) itemModelResolver.updateForLiving(s.backItem, CRATE, ItemDisplayContext.FIXED, body);
        else s.backItem.clear();
        s.height = body.getBbHeight();
        // what they say, fading in and out; their name when you look at them up close
        s.speech = null;
        if (body.speech != null && body.tickCount < body.speechUntil && s.distanceToCameraSq < 28 * 28) {
            float in = (body.tickCount - body.speechStart + partial) / 5f, out = (body.speechUntil - body.tickCount - partial) / 10f;
            s.speech = body.speech;
            s.speechAlpha = Mth.clamp(Math.min(in, out), 0f, 1f) * s.alpha;
            s.speechKind = body.speechKind;
        }
        Minecraft mc = Minecraft.getInstance();
        s.label = null;
        if (mc.crosshairPickEntity == body && s.distanceToCameraSq < 9 * 9 && s.speech == null) {
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
                collector.submitText(pose, -font.width(line) / 2f, top + k * lh, line, false, net.minecraft.client.gui.Font.DisplayMode.NORMAL,
                        net.minecraft.util.LightCoordsUtil.FULL_BRIGHT, a << 24 | 0xFFFFFF, 0, 0);
            }
        } else {
            collector.submitText(pose, -s.labelWidth / 2f, -font.lineHeight, s.label, false, net.minecraft.client.gui.Font.DisplayMode.NORMAL,
                    net.minecraft.util.LightCoordsUtil.FULL_BRIGHT, 0xE0FFFFFF, 0x50201810, 0);
        }
        pose.popPose();
    }

    private static final ItemStack CRATE = new ItemStack(CitizensContent.PORTER_CRATE.get());

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
            pose.translate(0f, 0.36f, 0.42f);
            pose.scale(0.62f, 0.62f, 0.62f);
            s.backItem.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, s.outlineColor);
            pose.popPose();
        }
    }
}
