package dev.awaken.arms.anvil.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.Awaken;
import dev.awaken.arms.WeaponMesh;
import dev.awaken.arms.anvil.AnvilWork;
import dev.awaken.arms.anvil.WarAnvil;
import dev.awaken.arms.anvil.WarAnvilBlockEntity;
import dev.awaken.arms.client.WeaponDraw;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
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
 * the smith's hands, laid by, or on the peg), and a finished weapon standing hilt up in the slot, waking as it's set
 * there. Where each is: {@link AnvilScene}.
 */
public final class WarAnvilRenderer implements BlockEntityRenderer<WarAnvilBlockEntity, WarAnvilRenderer.State> {
    public static final class State extends BlockEntityRenderState {
        final AnvilScene.Placed placed = new AnvilScene.Placed();
        @Nullable String weapon, slot;
        final AnvilScene.Lay slotLay = new AnvilScene.Lay();
        int forgeLight, slotFlash, slotEnergy;
    }

    private static final Identifier HAMMER = Awaken.id("textures/item/arms/anvil_hammer.png"), TONGS = Awaken.id("textures/item/arms/anvil_tongs.png");
    private static final Matrix4f M = new Matrix4f();
    private static final Quaternionf FRAME = new Quaternionf();

    public WarAnvilRenderer(BlockEntityRendererProvider.Context ctx) {}

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
        AnvilWork w = be.work();
        s.weapon = w == null ? null : w.weapon;
        if (w != null && level != null && s.placed.inForge) {
            BlockState fs = level.getBlockState(w.forge);
            Direction face = fs.hasProperty(HorizontalDirectionalBlock.FACING) ? fs.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
            s.forgeLight = LightCoordsUtil.getLightCoords(level, w.forge.relative(face));
        }
        ItemStack st = be.slot();
        var key = st.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(st.getItem());
        s.slot = key != null && key.getNamespace().equals(Awaken.MODID) && WeaponDraw.mesh(key.getPath()) != null ? key.getPath() : null;
        if (s.slot != null) {
            AnvilScene.inSlot(AnvilScene.frame(be.getBlockState(), FRAME), mine ? 0.22F * AnvilScene.drawK : 0F, s.slotLay);
            // it wakes as it's set there, then keeps a low glow of its own until it's drawn
            s.slotFlash = Math.round(255 * Mth.clamp(1F - (now - be.stoodAt) / 50F, 0F, 1F));
            s.slotEnergy = Math.round(60 + 35 * Mth.sin(now * 0.09F));
        }
    }

    @Override
    public void submit(State s, PoseStack ps, SubmitNodeCollector out, CameraRenderState camera) {
        AnvilScene.Placed p = s.placed;
        if (p.hasWork && s.weapon != null) {
            WeaponMesh mesh = WeaponDraw.mesh(s.weapon);
            if (mesh != null) {
                ps.pushPose();
                ps.mulPose(p.work.matrix(M));
                int x = p.heat < 0 ? 0 : p.heat, y = p.heat < 0 ? 0 : p.front | p.flags;
                WeaponDraw.draw(out, ps, mesh, texture(s.weapon), p.inForge ? s.forgeLight : s.lightCoords, x, y, p.cut);
                ps.popPose();
            }
        }
        tool(ps, out, "anvil/hammer", HAMMER, p.hammer, s.lightCoords);
        if (p.hasTongs) tool(ps, out, "anvil/tongs", TONGS, p.tongs, p.inForge ? s.forgeLight : s.lightCoords);
        if (s.slot != null) {
            WeaponMesh mesh = WeaponDraw.mesh(s.slot);
            if (mesh != null) {
                ps.pushPose();
                ps.mulPose(s.slotLay.matrix(M));
                WeaponDraw.draw(out, ps, mesh, texture(s.slot), LightCoordsUtil.withBlock(s.lightCoords, Math.max(LightCoordsUtil.block(s.lightCoords), 12)), s.slotEnergy, s.slotFlash, 0F);
                ps.popPose();
            }
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
