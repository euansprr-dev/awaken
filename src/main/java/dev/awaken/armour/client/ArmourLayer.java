package dev.awaken.armour.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.awaken.armour.ArmourContent;
import dev.awaken.armour.ArmourLooks;
import dev.awaken.armour.ArmourSet;
import dev.awaken.armour.Forged;
import dev.awaken.armour.client.ArmourMeshes.Bone;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorType;

/**
 * Worn armour drawn as sculpted meshes (SPEC §44.1): each piece's mesh on each bone it rides, moved with the bone as the
 * model is posed, through the armour shader. On players (both arm widths), mannequins, armour stands and humanoid mobs.
 * The cape hangs from its yoke in three links that swing with the player's cape motion (vanilla's lean and flap, each
 * link a little further); the cloth over the legs is pushed by the stride (the front by the leg swung forward, the back
 * by the leg swung back). A set's living parts (a halo, a marker, a plume, wings: SPEC §44.10) are drawn apart from
 * their bone's still mesh, each moved by the frame's own state. Nothing per frame but the vertices.
 */
final class ArmourLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends RenderLayer<S, M> {
    private static final ArmorType[] SLOTS = {ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS};

    ArmourLayer(RenderLayerParent<S, M> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack ps, SubmitNodeCollector out, int light, S state, float yRot, float xRot) {
        ItemStack[] worn = state.getRenderData(ArmourClient.WORN);
        if (state.isInvisible || worn == null) return;
        ArmourGlow.Glow glow = state.getRenderData(ArmourClient.GLOW);
        int energy = ArmourGlow.energy(glow);
        if (state.getRenderData(ArmourClient.ADMIRED) != null) {
            // Admire's hero light (SPEC §44.13): never darker than a lit room, and what glows glows as if half charged
            light = net.minecraft.util.LightCoordsUtil.max(light, HERO_LIGHT);
            energy = Math.max(energy, 110);
        }
        boolean low = state.getRenderData(ArmourClient.LOW) != null;
        ArmourLive.Wake wake = state.getRenderData(ArmourClient.WAKE);
        boolean motion = ArmourClientConfig.motion();
        float wind = motion ? ArmourLive.wind(state) : 0F, kick = motion ? ArmourGlow.kick(glow) : 0F;
        ArmourSet aura = null;
        int mastered = 0;
        for (int si = 0; si < SLOTS.length; si++) {
            ArmorType slot = SLOTS[si];
            ItemStack stack = worn[slot.ordinal()];
            if (stack == null) continue;
            ArmourLooks.Look look = ArmourLooks.drawn(stack);
            if (look == null || look.piece() != slot) continue;
            ArmourMesh[] bones = ArmourMeshes.piece(look.set(), slot);
            if (bones == null) continue;
            boolean foil = stack.hasFoil();
            int script = dev.awaken.core.api.EnchantApi.glowBits(stack);
            Forged forged = stack.get(ArmourContent.FORGED.get());
            int mastery = forged == null ? 0 : forged.masteryStep();
            int accent = forged == null ? 0 : forged.accent();
            int flash = ArmourGlow.flash(glow, slot); // a release's flare, or the wake climbing the body (SPEC §44.11)
            int lit = Math.max(energy, flash * 3 / 4);  // and with it what glows on the piece brightens: the seams light
            int marks = marks(forged, look.set());
            // mastery IV: the set's awakened parts grow over the piece's own
            ArmourMesh[] awake = mastery >= AWAKENED ? ArmourMeshes.awakened(look.set(), slot) : null;
            float grown = awake == null || wake == null ? 1F : wake.grown(si);
            // the set's living parts (SPEC §44.10): their bones drawn without them, and each where it is now
            ArmourLive.Part[] live = motion ? ArmourMeshes.live(look.set(), slot) : null;
            ArmourMesh[] still = live == null ? null : ArmourMeshes.still(look.set(), slot);
            for (Bone b : ArmourMeshes.BONES) {
                boolean lives = still != null && ArmourLive.on(live, b);
                ArmourMesh mesh = lives ? still[b.ordinal()] : bones[b.ordinal()], extra = awake == null ? null : awake[b.ordinal()];
                if (mesh == null && extra == null && !lives) continue;
                ps.pushPose();
                if (place(ps, getParentModel(), b, state)) {
                    if (mesh != null) draw(out, ps, mesh, look.set(), light, foil, lit, flash, -1, mastery, low, accent, script, marks);
                    if (lives) for (ArmourLive.Part p : live) {
                        if (p.bone() != b) continue;
                        ps.pushPose();
                        ArmourLive.pose(ps, p, state.ageInTicks, state.walkAnimationSpeed, state.walkAnimationPos, wind, kick);
                        draw(out, ps, p.mesh(), look.set(), light, foil, lit, flash, -1, mastery, low, accent, script, marks);
                        ps.popPose();
                    }
                    if (extra != null) {
                        if (grown < 1F) ArmourLive.grow(ps, extra, grown);
                        draw(out, ps, extra, look.set(), light, foil, lit, flash, -1, mastery, low, accent, script, marks);
                    }
                }
                ps.popPose();
            }
            // mastery V: the whole set drawn as one, every piece at V, wears the set's aura
            if (mastery >= AURA && (aura == null || aura == look.set())) {
                aura = look.set();
                mastered++;
            }
        }
        if (mastered == 4) ArmourAura.draw(out, ps, aura, state.ageInTicks);
        if (glow != null && glow.runes > 0) runes(ps, out, worn[ArmorType.CHESTPLATE.ordinal()], glow, state.ageInTicks);
    }

    private static final int HERO_LIGHT = net.minecraft.util.LightCoordsUtil.pack(11, 0);

    /** Mastery steps (0-based) at which the awakened parts grow (IV) and the aura comes (V). */
    static final int AWAKENED = 3, AURA = 4;

    /** Rune circle: how far out from the body, at what height (model space, y down), a turn's length in ticks. */
    private static final float RUNE_RADIUS = 0.85F, RUNE_Y = 0.42F, RUNE_TURN = 64F;

    /**
     * A Channeler's runes circling its wearer at the chest (SPEC §44.5, Runes): its set's own rune, each one apart and
     * bobbing, a new one growing in.
     */
    private static void runes(PoseStack ps, SubmitNodeCollector out, @org.jspecify.annotations.Nullable ItemStack chest, ArmourGlow.Glow glow, float age) {
        ArmourLooks.Look look = chest == null ? null : ArmourLooks.of(chest);
        ArmourMesh rune = look == null ? null : ArmourMeshes.rune(look.set());
        if (rune == null) return;
        long now = net.minecraft.util.Util.getMillis();
        int n = Math.min(glow.runes, glow.runeAt.length);
        for (int i = 0; i < n; i++) {
            float a = (age / RUNE_TURN + i / (float) n) * net.minecraft.util.Mth.TWO_PI;
            float grow = Math.min(1F, (now - glow.runeAt[i]) / 300F);
            ps.pushPose();
            ps.translate(net.minecraft.util.Mth.cos(a) * RUNE_RADIUS, RUNE_Y + 0.06F * net.minecraft.util.Mth.sin(age * 0.12F + i * 2.1F),
                    net.minecraft.util.Mth.sin(a) * RUNE_RADIUS);
            ps.rotate(Axis.YP, -a + net.minecraft.util.Mth.HALF_PI);
            ps.scale(grow, grow, grow);
            draw(out, ps, rune, look.set(), net.minecraft.util.LightCoordsUtil.FULL_BRIGHT, false, 255, 0, -1, 0, false);
            ps.popPose();
        }
    }

    /** Moves the pose onto a bone as posed this frame; false where the bone is hidden (an armour stand's arms). */
    private static boolean place(PoseStack ps, HumanoidModel<?> m, Bone b, HumanoidRenderState state) {
        boolean stand = state instanceof ArmorStandRenderState;
        switch (b) {
            case HEAD -> {
                return bone(ps, m.head);
            }
            case BODY -> {
                return bone(ps, m.body);
            }
            case RIGHT_ARM -> {
                return bone(ps, m.rightArm);
            }
            case LEFT_ARM -> {
                return bone(ps, m.leftArm);
            }
            case RIGHT_LEG, LEFT_LEG -> {
                if (stand) ps.translate(0, -1 / 16F, 0); // a stand's own legs hang a texel lower than its armour's
                return bone(ps, b == Bone.RIGHT_LEG ? m.rightLeg : m.leftLeg);
            }
            case CAPE0, CAPE1, CAPE2 -> {
                if (!bone(ps, m.body)) return false;
                cape(ps, state, b.ordinal() - Bone.CAPE0.ordinal());
                return true;
            }
            case FRONT_CLOTH -> {
                if (!bone(ps, m.body)) return false;
                ps.translate(0, 13 / 16F, -5 / 16F);
                ps.rotate(Axis.XP, Math.min(0F, Math.min(m.rightLeg.xRot, m.leftLeg.xRot)) * 0.9F);
                return true;
            }
            case BACK_CLOTH -> {
                if (!bone(ps, m.body)) return false;
                ps.translate(0, 13 / 16F, 4 / 16F);
                ps.rotate(Axis.XP, Math.max(0F, Math.max(m.rightLeg.xRot, m.leftLeg.xRot)) * 0.9F);
                return true;
            }
        }
        return false;
    }

    private static boolean bone(PoseStack ps, ModelPart part) {
        if (!part.visible) return false;
        part.translateAndRotate(ps);
        return true;
    }

    /**
     * The cape from its yoke (a texel under the shoulders, behind the cuirass): turned back as vanilla's cape is (its
     * lean, flap and sideways lean), then each further link turned a little more, more as it streams out.
     */
    private static void cape(PoseStack ps, HumanoidRenderState state, int link) {
        float lean = 0, lean2 = 0, flap = 0;
        if (state instanceof AvatarRenderState a) {
            lean = a.capeLean;
            lean2 = a.capeLean2;
            flap = a.capeFlap;
        }
        ps.translate(0, 0.5F / 16, 4F / 16);
        ps.rotateDegrees(Axis.ZP, lean2 / 2);
        ps.rotateDegrees(Axis.XP, 6 + lean / 2 + flap);
        float curl = 4 + Math.min(lean, 60F) * 0.08F + Math.abs(flap) * 0.1F;
        for (int k = 0; k < link; k++) {
            ps.translate(0, 6F / 16, 0);
            ps.rotateDegrees(Axis.XP, curl);
        }
    }

    /** One mesh where the pose stands: the armour shader, or vanilla's entity cutout if that didn't build. */
    static void draw(SubmitNodeCollector out, PoseStack ps, ArmourMesh mesh, ArmourSet set, int light, boolean foil) {
        draw(out, ps, mesh, set, light, foil, 0, 0, -1);
    }

    /**
     * With the set's live state: energy and flash 0..255, and heat (-1 for none; else 0..255 from cold to white) for metal
     * in the making, which the shader lights from within (SPEC §44.4).
     */
    static void draw(SubmitNodeCollector out, PoseStack ps, ArmourMesh mesh, ArmourSet set, int light, boolean foil, int energy, int flash, int heat) {
        draw(out, ps, mesh, set, light, foil, energy, flash, heat, 0, false);
    }

    static void draw(SubmitNodeCollector out, PoseStack ps, ArmourMesh mesh, ArmourSet set, int light, boolean foil, int energy, int flash, int heat,
                     int mastery, boolean low) {
        draw(out, ps, mesh, set, light, foil, energy, flash, heat, mastery, low, 0);
    }

    /**
     * With the piece's mastery step (0-7: I to V and room), whether its wearer is low (mastery III's heartbeat) and its
     * accent colour (0: the set's own; an inlay's or a dye's, SPEC §44.8).
     */
    static void draw(SubmitNodeCollector out, PoseStack ps, ArmourMesh mesh, ArmourSet set, int light, boolean foil, int energy, int flash, int heat,
                     int mastery, boolean low, int accentColour) {
        draw(out, ps, mesh, set, light, foil, energy, flash, heat, mastery, low, accentColour, 0);
    }

    /** And the enchantment light on it now (SPEC §45.11: {@code EnchantApi.glowBits}, 0 none), in bits 8-14 of the first half. */
    static void draw(SubmitNodeCollector out, PoseStack ps, ArmourMesh mesh, ArmourSet set, int light, boolean foil, int energy, int flash, int heat,
                     int mastery, boolean low, int accentColour, int glow) {
        draw(out, ps, mesh, set, light, foil, energy, flash, heat, mastery, low, accentColour, glow, 0);
    }

    /** What the forge left on a piece (SPEC §44.11): its scale flaw, the fold's ripples in its steel, its maker's mark. */
    static final int FLAW = 1, WELD = 2, MAKER = 4;

    /**
     * A piece's forge marks: a scale flaw shows as scale; a set that is folded in the making (A and S) shows the fold's
     * ripples when the piece came out Masterwork or better; a Flawless piece carries its maker's mark.
     */
    static int marks(@org.jspecify.annotations.Nullable Forged f, ArmourSet set) {
        if (f == null) return 0;
        int q = f.quality();
        return (f.flaw() ? FLAW : 0) | (q >= dev.awaken.armour.ForgeSong.Quality.MASTERWORK.ordinal() && dev.awaken.armour.ForgeSong.folds(set) ? WELD : 0)
                | (q >= dev.awaken.armour.ForgeSong.Quality.FLAWLESS.ordinal() ? MAKER : 0);
    }

    /** And its forge marks ({@link #marks}): the flaw in the first half's top bit, the weld and the maker's mark in the second's top two. */
    static void draw(SubmitNodeCollector out, PoseStack ps, ArmourMesh mesh, ArmourSet set, int light, boolean foil, int energy, int flash, int heat,
                     int mastery, boolean low, int accentColour, int glow, int marks) {
        boolean ours = ArmourPipelines.ok();
        var texture = ArmourMeshes.texture(set);
        int v = (foil ? 256 : 0) | (heat >= 0 ? 512 : 0) | (mastery & 7) << 10 | (low ? 8192 : 0) | Math.min(255, Math.max(0, flash))
                | ((marks & WELD) != 0 ? 16384 : 0) | ((marks & MAKER) != 0 ? 32768 : 0);
        int u = (heat >= 0 ? Math.min(255, heat) : Math.min(255, Math.max(0, energy))) | (glow & 127) << 8 | ((marks & FLAW) != 0 ? 32768 : 0);
        int packed = ours ? v << 16 | u : OverlayTexture.NO_OVERLAY;
        int accent = !ours ? 0xFFFFFF : accentColour != 0 ? accentColour & 0xFFFFFF : mesh.accent();
        out.submitCustomGeometry(ps, ours ? ArmourPipelines.armour(texture) : ArmourPipelines.fallback(texture),
                (pose, buffer) -> emit(mesh, pose, buffer, light, packed, ours, accent));
    }

    private static void emit(ArmourMesh mesh, PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, boolean ours, int accent) {
        int r = accent >> 16 & 255, g = accent >> 8 & 255, b = accent & 255;
        float[] p = mesh.pos(), uv = mesh.uv();
        byte[] n = mesh.normal(), along = mesh.along();
        for (int i = 0, count = mesh.vertices(); i < count; i++) {
            corner(buffer, pose, p, uv, n, i, r, g, b, ours ? along[i] & 255 : 255, light, overlay);
            if (!ours && i % 3 == 2) corner(buffer, pose, p, uv, n, i, r, g, b, 255, light, overlay);
        }
    }

    private static void corner(VertexConsumer buffer, PoseStack.Pose pose, float[] p, float[] uv, byte[] n, int i, int r, int g, int b, int a,
                               int light, int overlay) {
        buffer.addVertex(pose, p[i * 3], p[i * 3 + 1], p[i * 3 + 2])
                .setColor(r, g, b, a)
                .setUv(uv[i * 2], uv[i * 2 + 1])
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, n[i * 3] / 127F, n[i * 3 + 1] / 127F, n[i * 3 + 2] / 127F);
    }
}
