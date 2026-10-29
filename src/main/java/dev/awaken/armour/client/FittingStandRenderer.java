package dev.awaken.armour.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.armour.ArmourLooks;
import dev.awaken.armour.ArmourSet;
import dev.awaken.armour.FittingStandBlock;
import dev.awaken.armour.FittingStandBlockEntity;
import dev.awaken.armour.ForgeSong;
import dev.awaken.armour.ForgeWork;
import dev.awaken.armour.client.ArmourMeshes.Bone;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/**
 * The Fitting Stand drawn (SPEC §44.4): the suit fitted on its form, posed as a figure stands, glowing as it wakes when a
 * set is whole; and the piece in the making, a squat blank glowing in the Forge's mouth while it heats, then laid face up
 * on the anvil taking shape strike by strike, cooling as it is worked. By hand (E) the hide lies on the works table, cold.
 */
public final class FittingStandRenderer implements BlockEntityRenderer<FittingStandBlockEntity, FittingStandRenderer.State> {
    public static final class State extends BlockEntityRenderState {
        final @Nullable ArmourSet[] suit = new ArmourSet[4];
        final boolean[] foil = new boolean[4];
        final int[] mastery = new int[4], accent = new int[4];
        float yaw, wake, age;
        boolean whole;
        // the work
        @Nullable ArmourSet set;
        ArmorType piece = ArmorType.HELMET;
        int stage, workLight, fireLight;
        float heat, shaped;
        @Nullable ForgeFrame frame;
        Vec3 mouth = Vec3.ZERO;
        float mouthYaw;
    }

    public FittingStandRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public AABB getRenderBoundingBox(FittingStandBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(dev.awaken.armour.ArmourForge.REACH + 1, 3, dev.awaken.armour.ArmourForge.REACH + 1);
    }

    @Override
    public void extractRenderState(FittingStandBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumbling);
        var level = be.getLevel();
        long now = level == null ? 0 : level.getGameTime();
        s.age = now + partial;
        s.yaw = be.getBlockState().getValue(FittingStandBlock.FACING).toYRot();
        for (int i = 0; i < 4; i++) {
            ItemStack st = be.suit(i);
            ArmourLooks.Look look = ArmourLooks.drawn(st);
            s.suit[i] = look != null && ForgeSong.slot(look.piece()) == i ? look.set() : null;
            s.foil[i] = st.hasFoil();
            dev.awaken.armour.Forged f = st.get(dev.awaken.armour.ArmourContent.FORGED.get());
            s.mastery[i] = f == null ? 0 : f.masteryStep();
            s.accent[i] = f == null ? 0 : f.accent();
        }
        s.whole = be.wholeSet() != null;
        s.wake = Math.max(0F, 1F - (s.age - be.wokeAt) / 50F);
        ForgeWork w = be.work();
        s.set = null;
        s.frame = null;
        if (w == null || level == null) return;
        s.set = w.set;
        s.piece = w.piece;
        s.stage = w.stage;
        s.shaped = w.shaped();
        float t = s.age - w.stageStart;
        boolean hand = ForgeSong.byHand(w.set);
        if (hand) s.heat = -1F;
        else if (w.stage == ForgeSong.HEAT) s.heat = ForgeSong.heat(t);
        else if (w.stage == ForgeSong.SHAPE) {
            float span = Math.max(40F, ForgeSong.song(w.set).rhythm().due(w.strikesNeeded() - 1 - w.strikeBase) + 20F);
            s.heat = Math.max(0.2F, w.heat * (1F - 0.35F * Math.min(1F, t / span)));
        } else s.heat = Math.max(0.2F, w.heat * 0.6F);
        s.frame = ForgeFrame.of(be.getBlockPos(), w.work);
        s.workLight = LightCoordsUtil.getLightCoords(level, w.work.above());
        if (w.forge.isPresent()) {
            BlockPos f = w.forge.get();
            BlockState fs = level.getBlockState(f);
            Direction face = fs.hasProperty(HorizontalDirectionalBlock.FACING) ? fs.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
            // half in the fire, half out of the mouth, so the glow is seen
            s.mouth = new Vec3(f.getX() + 0.5 + face.getStepX() * 0.5, f.getY() + 0.3, f.getZ() + 0.5 + face.getStepZ() * 0.5);
            s.mouthYaw = face.toYRot();
            s.fireLight = LightCoordsUtil.getLightCoords(level, f.relative(face));
        }
    }

    @Override
    public void submit(State s, PoseStack ps, SubmitNodeCollector out, CameraRenderState camera) {
        suit(s, ps, out);
        if (s.set != null && s.frame != null) work(s, ps, out);
    }

    // ---- the suit on its form ----------------------------------------------------------------------------------------

    private static final float[][] PIVOT = {{0, 0, 0}, {0, 0, 0}, {-5, 2, 0}, {5, 2, 0}, {-1.9F, 12, 0}, {1.9F, 12, 0}};

    private static void suit(State s, PoseStack ps, SubmitNodeCollector out) {
        boolean any = false;
        for (ArmourSet a : s.suit) any |= a != null;
        if (!any) return;
        ps.pushPose();
        ps.translate(0.5F, 3 / 16F, 0.5F);
        ps.rotateDegrees(Axis.YP, 180F - s.yaw);
        ps.scale(-1F, -1F, 1F);
        ps.translate(0F, -1.501F, 0F);
        int flash = (int) (s.wake * 255);
        int energy = s.whole ? (int) (70 + 50 * Math.sin(s.age * 0.08)) : 0;
        int mastered = 0;
        for (int slot = 0; slot < 4; slot++) {
            ArmourSet set = s.suit[slot];
            if (set == null) continue;
            ArmourMesh[] bones = ArmourMeshes.piece(set, ForgeSong.PIECES[slot]);
            if (bones == null) continue;
            ArmourMesh[] awake = s.mastery[slot] >= ArmourLayer.AWAKENED ? ArmourMeshes.awakened(set, ForgeSong.PIECES[slot]) : null;
            for (Bone b : ArmourMeshes.BONES) {
                ArmourMesh mesh = bones[b.ordinal()], extra = awake == null ? null : awake[b.ordinal()];
                if (mesh == null && extra == null) continue;
                ps.pushPose();
                rest(ps, b);
                if (mesh != null) ArmourLayer.draw(out, ps, mesh, set, s.lightCoords, s.foil[slot], energy, flash, -1, s.mastery[slot], false, s.accent[slot]);
                if (extra != null) ArmourLayer.draw(out, ps, extra, set, s.lightCoords, s.foil[slot], energy, flash, -1, s.mastery[slot], false, s.accent[slot]);
                ps.popPose();
            }
            if (s.mastery[slot] >= ArmourLayer.AURA && set == s.suit[0]) mastered++;
        }
        if (mastered == 4) ArmourAura.draw(out, ps, s.suit[0], s.age);
        ps.popPose();
    }

    /** A bone at rest on the form: the model's pivots, arms a little out, the cape hanging, the cloth still. */
    private static void rest(PoseStack ps, Bone b) {
        int i = Math.min(b.ordinal(), 5);
        if (b.ordinal() >= Bone.CAPE0.ordinal()) i = 1;
        ps.translate(PIVOT[i][0] / 16F, PIVOT[i][1] / 16F, PIVOT[i][2] / 16F);
        switch (b) {
            case RIGHT_ARM -> ps.rotate(Axis.ZP, 0.1F);
            case LEFT_ARM -> ps.rotate(Axis.ZP, -0.1F);
            case CAPE0, CAPE1, CAPE2 -> {
                ps.translate(0, 0.5F / 16, 4F / 16);
                ps.rotateDegrees(Axis.XP, 6);
                for (int k = 0; k < b.ordinal() - Bone.CAPE0.ordinal(); k++) {
                    ps.translate(0, 6F / 16, 0);
                    ps.rotateDegrees(Axis.XP, 4);
                }
            }
            case FRONT_CLOTH -> ps.translate(0, 13 / 16F, -5 / 16F);
            case BACK_CLOTH -> ps.translate(0, 13 / 16F, 4 / 16F);
            default -> {}
        }
    }

    // ---- the work ------------------------------------------------------------------------------------------------------

    private static final Bone[][] LAID = {
            {Bone.HEAD}, {Bone.BODY, Bone.RIGHT_ARM, Bone.LEFT_ARM}, {Bone.BODY, Bone.RIGHT_LEG, Bone.LEFT_LEG}, {Bone.RIGHT_LEG, Bone.LEFT_LEG}};

    private static void work(State s, PoseStack ps, SubmitNodeCollector out) {
        ArmourMesh[] bones = ArmourMeshes.piece(s.set, s.piece);
        if (bones == null) return;
        ForgeFrame f = s.frame;
        BlockPos origin = s.blockPos;
        boolean inFire = s.stage == ForgeSong.HEAT;
        int heat = s.heat < 0 ? -1 : (int) Math.min(255, s.heat / 1.2F * 255);
        int light = inFire ? s.fireLight : s.workLight;
        ps.pushPose();
        Vec3 at;
        Vec3 along, across;
        float shaped;
        if (inFire) {
            at = s.mouth;
            double a = Math.toRadians(s.mouthYaw);
            along = new Vec3(-Math.sin(a), 0, Math.cos(a));
            across = new Vec3(-along.z, 0, along.x);
            shaped = 0F;
        } else {
            at = f.top();
            along = f.along();
            across = f.across();
            shaped = s.stage == ForgeSong.SHAPE || s.stage == ForgeSong.RIVET ? s.shaped : 1F;
            if (s.stage == ForgeSong.CUT) shaped = 0.6F;
        }
        ps.translate(at.x - origin.getX(), at.y - origin.getY() + (inFire ? 0 : 0.02), at.z - origin.getZ());
        // model x → across, model up (−y) → along, model front (−z) → up
        Matrix4f m = new Matrix4f(
                (float) across.x, (float) across.y, (float) across.z, 0,
                (float) -along.x, (float) -along.y, (float) -along.z, 0,
                0, -1, 0, 0,
                0, 0, 0, 1);
        ps.mulPose(m);
        float sc = ForgeFrame.SCALE * (inFire ? 0.55F : 1F);
        // the blank: narrow and flat; each strike draws it toward the piece
        ps.scale(sc * (0.7F + 0.3F * shaped), sc * (0.8F + 0.2F * shaped), sc * (0.18F + 0.82F * shaped));
        ps.translate(0, -ForgeFrame.middle(s.piece) / 16F, 0);
        for (Bone b : LAID[ForgeSong.slot(s.piece)]) {
            ArmourMesh mesh = bones[b.ordinal()];
            if (mesh == null) continue;
            ps.pushPose();
            int i = b.ordinal();
            ps.translate(PIVOT[i][0] / 16F * 1.15F, PIVOT[i][1] / 16F, 0);
            ArmourLayer.draw(out, ps, mesh, s.set, light, false, 0, 0, heat);
            ps.popPose();
        }
        ps.popPose();
    }
}
