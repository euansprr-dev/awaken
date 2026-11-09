package dev.awaken.shaping.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.shaping.MalletData;
import dev.awaken.shaping.RealShapes;
import dev.awaken.shaping.Shape;
import dev.awaken.shaping.ShapingRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What the Mallet draws in the world (SPEC §52.6): the chalk line showing where a knock will land, and the look you're
 * pointing at on the ring, tried on every block it would change. The preview is the real mesh, just a hair proud of the
 * real blocks, so what you see is what you'll get.
 */
final class ShapingOverlay {
    private ShapingOverlay() {}

    private static final float PROUD = 0.004F;
    private static final int WASH = 0x2EF2E6C9, LINE = 0xC8F2E6C9;

    private record Tried(BlockPos pos, Shape shape, Direction facing, int light) {}

    private static @Nullable BlockPos aimed;
    private static final List<Tried> RUN = new ArrayList<>();
    private static @Nullable BlockState trying;

    static void begin(ClientLevel level, BlockPos target, List<BlockPos> run) {
        aimed = target.immutable();
        RUN.clear();
        for (BlockPos p : run) {
            RealShapes.Parts parts = RealShapes.read(level, p, level.getBlockState(p));
            if (parts == null) continue;
            // the brightest side: the block above a wall's inner course is more wall, and dark
            int block = 0, sky = 0;
            for (Direction d : Direction.values()) {
                BlockPos out = p.relative(d);
                block = Math.max(block, level.getBrightness(LightLayer.BLOCK, out));
                sky = Math.max(sky, level.getBrightness(LightLayer.SKY, out));
            }
            RUN.add(new Tried(p.immutable(), parts.shape(), parts.facing(), LightCoordsUtil.pack(block, sky)));
        }
        trying = null;
    }

    static void preview(@Nullable BlockState look) {
        trying = look;
    }

    static void end() {
        aimed = null;
        RUN.clear();
        trying = null;
    }

    static void submit(SubmitCustomGeometryEvent e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;
        Vec3 cam = e.getLevelRenderState().cameraRenderState.pos;
        KnockFx.submit(e, cam);
        BlockState look = trying;
        LookMaterials.Look m = look == null ? null : LookMaterials.of(look);
        if (m != null && !RUN.isEmpty()) {
            e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), Sheets.cutoutBlockItemSheet(), (pose, vc) -> {
                for (Tried t : RUN) tried(vc, t, m, cam);
            });
            return;
        }
        if (mc.gui.screen() == null && MalletInput.held(mc) && MalletData.of(mc.player.getMainHandItem()).mode() == MalletData.Mode.KNOCK
                && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            RealShapes.Parts p = RealShapes.read(level, pos, level.getBlockState(pos));
            if (p == null) return;
            Vec3 at = hit.getLocation();
            ShapingRules.Aim aim = ShapingRules.aim(p.shape(), p.facing(), hit.getDirection(), at.x - pos.getX(), at.y - pos.getY(), at.z - pos.getZ());
            float ox = (float) (pos.getX() - cam.x), oy = (float) (pos.getY() - cam.y), oz = (float) (pos.getZ() - cam.z);
            e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), RenderTypes.debugQuads(), (pose, vc) -> band(vc, aim, ox, oy, oz));
        }
    }

    private static void tried(VertexConsumer vc, Tried t, LookMaterials.Look m, Vec3 cam) {
        float ox = (float) (t.pos.getX() - cam.x), oy = (float) (t.pos.getY() - cam.y), oz = (float) (t.pos.getZ() - cam.z);
        for (Shape.Face f : t.shape.faces(t.facing)) {
            LookMaterials.Side side = m.side(f.dominant());
            Direction d = f.dominant();
            for (int i = 0; i < 4; i++) {
                float x = f.x(i), y = f.y(i), z = f.z(i);
                float a = LookMaterials.a(d, x, y, z), b = LookMaterials.b(d, x, y, z);
                vc.addVertex(ox + x + f.nx() * PROUD, oy + y + f.ny() * PROUD, oz + z + f.nz() * PROUD).setColor(-1).setUv(side.u(a, b), side.v(a, b))
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(t.light).setNormal(f.nx(), f.ny(), f.nz());
            }
        }
    }

    // ---- the chalk ----------------------------------------------------------------------------------------------------------

    private static void band(VertexConsumer vc, ShapingRules.Aim aim, float ox, float oy, float oz) {
        Direction f = aim.facing();
        float e = PROUD;
        switch (aim.zone()) {
            case TOP -> {
                side(vc, f, 0.5F, 1F, ox, oy, oz, WASH);
                strip(vc, f, 1F + e, ox, oy, oz, WASH);
                side(vc, f, 0.49F, 0.51F, ox, oy, oz, LINE);
            }
            case UNDER -> {
                side(vc, f, 0F, 0.5F, ox, oy, oz, WASH);
                strip(vc, f, -e, ox, oy, oz, WASH);
                side(vc, f, 0.49F, 0.51F, ox, oy, oz, LINE);
            }
            case MID -> side(vc, f, 0F, 1F, ox, oy, oz, WASH);
            case CENTER_TOP -> flat(vc, 1F + e, ox, oy, oz);
            case CENTER_BOTTOM -> flat(vc, -e, ox, oy, oz);
        }
    }

    /** A band across one side of the block, from low to high. */
    private static void side(VertexConsumer vc, Direction d, float low, float high, float ox, float oy, float oz, int colour) {
        float e = PROUD;
        float y0 = oy + low, y1 = oy + high;
        float x0 = ox, x1 = ox + 1, z0 = oz, z1 = oz + 1;
        switch (d) {
            case SOUTH -> quad(vc, x0, y1, z1 + e, x0, y0, z1 + e, x1, y0, z1 + e, x1, y1, z1 + e, colour);
            case NORTH -> quad(vc, x1, y1, z0 - e, x1, y0, z0 - e, x0, y0, z0 - e, x0, y1, z0 - e, colour);
            case EAST -> quad(vc, x1 + e, y1, z1, x1 + e, y0, z1, x1 + e, y0, z0, x1 + e, y1, z0, colour);
            case WEST -> quad(vc, x0 - e, y1, z0, x0 - e, y0, z0, x0 - e, y0, z1, x0 - e, y1, z1, colour);
            default -> {
            }
        }
    }

    /** The half of the top or the underside nearest this side. */
    private static void strip(VertexConsumer vc, Direction d, float y, float ox, float oy, float oz, int colour) {
        float x0 = ox + (d == Direction.EAST ? 0.5F : 0F), x1 = ox + (d == Direction.WEST ? 0.5F : 1F);
        float z0 = oz + (d == Direction.SOUTH ? 0.5F : 0F), z1 = oz + (d == Direction.NORTH ? 0.5F : 1F);
        float h = oy + y;
        if (y > 0.5F) quad(vc, x0, h, z0, x0, h, z1, x1, h, z1, x1, h, z0, colour);
        else quad(vc, x0, h, z1, x0, h, z0, x1, h, z0, x1, h, z1, colour);
    }

    private static void flat(VertexConsumer vc, float y, float ox, float oy, float oz) {
        float h = oy + y;
        if (y > 0.5F) quad(vc, ox, h, oz, ox, h, oz + 1, ox + 1, h, oz + 1, ox + 1, h, oz, WASH);
        else quad(vc, ox, h, oz + 1, ox, h, oz, ox + 1, h, oz, ox + 1, h, oz + 1, WASH);
    }

    private static void quad(VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, int c) {
        vc.addVertex(x0, y0, z0).setColor(c);
        vc.addVertex(x1, y1, z1).setColor(c);
        vc.addVertex(x2, y2, z2).setColor(c);
        vc.addVertex(x3, y3, z3).setColor(c);
    }
}
