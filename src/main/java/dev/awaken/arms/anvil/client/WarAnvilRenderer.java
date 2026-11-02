package dev.awaken.arms.anvil.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.Awaken;
import dev.awaken.arms.WeaponMesh;
import dev.awaken.arms.anvil.AnvilContent;
import dev.awaken.arms.anvil.AnvilWork;
import dev.awaken.arms.anvil.WarAnvil;
import dev.awaken.arms.anvil.WarAnvilBlockEntity;
import dev.awaken.arms.anvil.WeaponForged;
import dev.awaken.arms.client.WeaponDraw;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * The War Anvil drawn (SPEC §46.3): the work as the weapon's own mesh through the weapon shader's heat (in the Forge's
 * mouth, on the face drawn out strike by strike, in the trough, the hilt coming tap by tap), the hammer and tongs (in
 * the smith's hands, laid by, or on the peg), a catalyst being set in the guard while a weapon is attuned (§46.8), the
 * Deepglass weight on the grip as it's balanced and the column of starlight an S-rank weapon is quenched in (§46.9), and
 * a finished weapon standing hilt up in the slot, waking as it's set there, and from the Deepglass horn the last three
 * forged standing in the rack behind it (§46.6). Where each is: {@link AnvilScene}.
 */
public final class WarAnvilRenderer implements BlockEntityRenderer<WarAnvilBlockEntity, WarAnvilRenderer.State> {
    public static final class State extends BlockEntityRenderState {
        final AnvilScene.Placed placed = new AnvilScene.Placed();
        @Nullable String weapon, slot;
        final AnvilScene.Lay slotLay = new AnvilScene.Lay();
        int forgeLight, slotFlash, slotEnergy, slotFlaw;
        /** The catalyst being set in the guard (attuning), as vanilla draws the item. */
        final ItemStackRenderState gem = new ItemStackRenderState();
        final Quaternionf gemTurn = new Quaternionf();
        boolean hasGem;
        /** S's starlight (§46.9), drawn by its own geometry so a frame makes nothing new. */
        final Column column = new Column();
        /** The rack's weapons (§46.6): their designs (null for none), where each stands and its flaw and acts. */
        final @Nullable String[] rack = new String[WarAnvilBlockEntity.RACK];
        final AnvilScene.Lay[] rackLay = {new AnvilScene.Lay(), new AnvilScene.Lay(), new AnvilScene.Lay()};
        final int[] rackFlaw = new int[WarAnvilBlockEntity.RACK];
    }

    /** A soft column of starlight from high over the anvil down onto the work: a bright core in a wide fading sheath. */
    static final class Column implements SubmitNodeCollector.CustomGeometryRenderer {
        float k, shimmer;
        /** Its foot (block-relative): over the anvil's face, where the work is held up into it. */
        final org.joml.Vector3f at = new org.joml.Vector3f();
        static final float HIGH = 5F;

        @Override
        public void render(PoseStack.Pose pose, VertexConsumer vc) {
            box(pose, vc, 0.05F, 0.75F * k * shimmer);
            box(pose, vc, 0.13F, 0.32F * k);
            box(pose, vc, 0.26F, 0.12F * k);
        }

        /** Four sides, each both ways round, bright at the foot and gone at the top. */
        private static void box(PoseStack.Pose pose, VertexConsumer vc, float r, float a) {
            int foot = argb(a), top = argb(0F);
            for (int i = 0; i < 4; i++) {
                // the corners in turn round the square: (-r,-r), (r,-r), (r,r), (-r,r)
                float x0 = i == 0 || i == 3 ? -r : r, z0 = i < 2 ? -r : r, x1 = i < 2 ? r : -r, z1 = i == 0 ? -r : i == 3 ? -r : r;
                vc.addVertex(pose, x0, 0F, z0).setColor(foot);
                vc.addVertex(pose, x0, HIGH, z0).setColor(top);
                vc.addVertex(pose, x1, HIGH, z1).setColor(top);
                vc.addVertex(pose, x1, 0F, z1).setColor(foot);
                vc.addVertex(pose, x1, 0F, z1).setColor(foot);
                vc.addVertex(pose, x1, HIGH, z1).setColor(top);
                vc.addVertex(pose, x0, HIGH, z0).setColor(top);
                vc.addVertex(pose, x0, 0F, z0).setColor(foot);
            }
        }

        private static int argb(float a) {
            return Math.round(Mth.clamp(a, 0F, 1F) * 255F) << 24 | 0xE0EFFF;
        }
    }

    private static final Identifier HAMMER = Awaken.id("textures/item/arms/anvil_hammer.png"), TONGS = Awaken.id("textures/item/arms/anvil_tongs.png"),
            STONE = Awaken.id("textures/item/arms/anvil_stone_hammer.png");
    private static final Matrix4f M = new Matrix4f();
    private static final Quaternionf FRAME = new Quaternionf();

    private final ItemModelResolver items;

    public WarAnvilRenderer(BlockEntityRendererProvider.Context ctx) {
        this.items = ctx.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public AABB getRenderBoundingBox(WarAnvilBlockEntity be) {
        // the work heats in a Forge up to six blocks off
        return new AABB(be.getBlockPos()).inflate(WarAnvil.REACH + 1, 3, WarAnvil.REACH + 1);
    }

    @Override
    public void extractRenderState(WarAnvilBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumbling);
        var level = be.getLevel();
        float now = level == null ? 0F : level.getGameTime() + partial;
        boolean mine = AnvilClient.sceneAnvil() == be;
        AnvilScene.place(be, now, mine, s.placed);
        if (level != null && be.work() != null) AnvilBodies.seen(level, be.getBlockPos(), be.work());
        AnvilWork w = be.work();
        s.weapon = w == null ? null : w.weapon;
        s.column.k = s.placed.column;
        s.column.shimmer = 0.85F + 0.15F * Mth.sin(now * 0.4F);
        if (s.column.k > 0F) AnvilScene.model(AnvilScene.frame(be.getBlockState(), FRAME), 0.5F, 1.05F, 7.5F / 16F, s.column.at);
        s.hasGem = s.placed.hasGem;
        if (s.hasGem) {
            var catalyst = dev.awaken.arms.ArmsForge.catalystItem(dev.awaken.core.Forces.Force.values()[s.placed.gemForce]);
            items.updateForTopItem(s.gem, new ItemStack(catalyst), ItemDisplayContext.FIXED, level, null, (int) be.getBlockPos().asLong());
            AnvilScene.frame(be.getBlockState(), s.gemTurn);
        }
        if (w != null && level != null && s.placed.inForge) {
            BlockState fs = level.getBlockState(w.forge);
            Direction face = fs.hasProperty(HorizontalDirectionalBlock.FACING) ? fs.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
            s.forgeLight = LightCoordsUtil.getLightCoords(level, w.forge.relative(face));
        }
        ItemStack st = be.slot();
        var key = st.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(st.getItem());
        s.slot = key != null && key.getNamespace().equals(Awaken.MODID) && WeaponDraw.mesh(AnvilShape.meshId(key.getPath())) != null ? key.getPath() : null;
        if (s.slot != null) {
            AnvilScene.inSlot(AnvilScene.frame(be.getBlockState(), FRAME), AnvilShape.of(s.slot), mine ? 0.22F * AnvilScene.drawK : 0F, s.slotLay);
            // it wakes as it's set there, then keeps a low glow of its own until it's drawn
            s.slotFlash = Math.round(255 * Mth.clamp(1F - (now - be.stoodAt) / 50F, 0F, 1F));
            s.slotEnergy = Math.round(60 + 35 * Mth.sin(now * 0.09F));
            WeaponForged f = st.get(AnvilContent.FORGED.get());
            s.slotFlaw = f == null ? 0 : (f.flaw() & 3 | (f.acts() & 3) << 2) << 11; // its flaw and acts show as it stands there (SPEC §46.7, §46.9)
        }
        var rack = be.rack();
        for (int k = 0; k < s.rack.length; k++) {
            ItemStack r = k < rack.size() ? rack.get(k) : ItemStack.EMPTY;
            var rk = r.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(r.getItem());
            s.rack[k] = rk != null && rk.getNamespace().equals(Awaken.MODID) && WeaponDraw.mesh(AnvilShape.meshId(rk.getPath())) != null ? rk.getPath() : null;
            if (s.rack[k] == null) continue;
            AnvilScene.inRack(AnvilScene.frame(be.getBlockState(), FRAME), AnvilShape.of(s.rack[k]), k, s.rackLay[k]);
            WeaponForged f = r.get(AnvilContent.FORGED.get());
            s.rackFlaw[k] = f == null ? 0 : (f.flaw() & 3 | (f.acts() & 3) << 2) << 11;
        }
    }

    @Override
    public void submit(State s, PoseStack ps, SubmitNodeCollector out, CameraRenderState camera) {
        AnvilScene.Placed p = s.placed;
        if (p.hasWork && s.weapon != null) {
            WeaponMesh mesh = WeaponDraw.mesh(AnvilShape.meshId(s.weapon));
            if (mesh != null) {
                ps.pushPose();
                ps.mulPose(p.work.matrix(M));
                int x = p.heat < 0 ? 0 : p.heat | p.glow, y = p.heat < 0 ? 0 : p.front | p.flags;
                WeaponDraw.draw(out, ps, mesh, texture(AnvilShape.meshId(s.weapon)), p.inForge ? s.forgeLight : s.lightCoords, x, y, p.cut);
                ps.popPose();
            }
        }
        if (s.hasGem) {
            // lying in the guard, face up, sinking in with each tap; lit as if by the hot steel under it
            ps.pushPose();
            ps.translate(p.gemAt.x, p.gemAt.y, p.gemAt.z);
            ps.rotate(s.gemTurn);
            ps.rotate(com.mojang.math.Axis.XP.rotationDegrees(-90F));
            ps.scale(p.gemScale, p.gemScale, p.gemScale);
            s.gem.submit(ps, out, LightCoordsUtil.withBlock(s.lightCoords, 13), OverlayTexture.NO_OVERLAY, 0);
            ps.popPose();
        }
        if (s.column.k > 0F) {
            ps.pushPose();
            ps.translate(s.column.at.x, s.column.at.y, s.column.at.z);
            out.submitCustomGeometry(ps, RenderTypes.lightning(), s.column);
            ps.popPose();
        }
        tool(ps, out, p.stone ? "anvil/stone_hammer" : "anvil/hammer", p.stone ? STONE : HAMMER, p.hammer, s.lightCoords);
        if (p.hasTongs) tool(ps, out, "anvil/tongs", TONGS, p.tongs, p.inForge ? s.forgeLight : s.lightCoords);
        rack(s, ps, out);
        if (s.slot != null) {
            WeaponMesh mesh = WeaponDraw.mesh(AnvilShape.meshId(s.slot));
            if (mesh != null) {
                ps.pushPose();
                ps.mulPose(s.slotLay.matrix(M));
                // a blade forged alone stands as its Longsword's blade, no hilt
                float from = AnvilShape.blade(s.slot) ? AnvilShape.of(s.slot).cut[0] : 0F;
                WeaponDraw.draw(out, ps, mesh, texture(AnvilShape.meshId(s.slot)), LightCoordsUtil.withBlock(s.lightCoords, Math.max(LightCoordsUtil.block(s.lightCoords), 12)), s.slotEnergy, s.slotFlash | s.slotFlaw, from);
                ps.popPose();
            }
        }
    }

    private static void rack(State s, PoseStack ps, SubmitNodeCollector out) {
        for (int k = 0; k < s.rack.length; k++) {
            WeaponMesh mesh = s.rack[k] == null ? null : WeaponDraw.mesh(AnvilShape.meshId(s.rack[k]));
            if (mesh == null) continue;
            ps.pushPose();
            ps.mulPose(s.rackLay[k].matrix(M));
            WeaponDraw.draw(out, ps, mesh, texture(AnvilShape.meshId(s.rack[k])), s.lightCoords, 0, s.rackFlaw[k], 0F);
            ps.popPose();
        }
    }

    private static void tool(PoseStack ps, SubmitNodeCollector out, String id, Identifier texture, Matrix4f pose, int light) {
        WeaponMesh mesh = WeaponDraw.mesh(id);
        if (mesh == null) return;
        ps.pushPose();
        ps.mulPose(pose);
        WeaponDraw.draw(out, ps, mesh, texture, light, 0, 0, 0F);
        ps.popPose();
    }

    private static Identifier texture(String weapon) {
        return Awaken.id("textures/item/arms/" + weapon + ".png");
    }
}
