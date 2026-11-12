package dev.awaken.shaping.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.shaping.Corners;
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
import net.minecraft.world.level.block.state.properties.StairsShape;
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
 * real blocks, so what you see is what you'll get; both follow the corner a block turns, or will turn once knocked.
 */
final class ShapingOverlay {
    private ShapingOverlay() {}

    private static final float PROUD = 0.004F;
    private static final int WASH = 0x2EF2E6C9, LINE = 0xC8F2E6C9;

    private record Tried(BlockPos pos, Shape shape, Direction facing, StairsShape corner, int light) {}

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
            RUN.add(new Tried(p.immutable(), parts.shape(), parts.facing(), Corners.of(level.getBlockState(p)), LightCoordsUtil.pack(block, sky)));
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
            BlockState here = level.getBlockState(pos);
            RealShapes.Parts p = RealShapes.read(level, pos, here);
            if (p == null) return;
            Vec3 at = hit.getLocation();
            ShapingRules.Aim aim = ShapingRules.aim(p.shape(), p.facing(), hit.getDirection(), at.x - pos.getX(), at.y - pos.getY(), at.z - pos.getZ());
            // the corner the cut will turn with the neighbours as they are; knocked back to whole, the corner it turns now
            ShapingRules.Cut next = ShapingRules.next(p.shape(), p.facing(), aim);
            StairsShape corner = next.shape().edge() ? Corners.corner(next.shape(), next.facing(), level, pos) : Corners.of(here);
            float ox = (float) (pos.getX() - cam.x), oy = (float) (pos.getY() - cam.y), oz = (float) (pos.getZ() - cam.z);
            e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), RenderTypes.debugQuads(), (pose, vc) -> band(vc, aim, corner, ox, oy, oz));
        }
    }

    private static void tried(VertexConsumer vc, Tried t, LookMaterials.Look m, Vec3 cam) {
        float ox = (float) (t.pos.getX() - cam.x), oy = (float) (t.pos.getY() - cam.y), oz = (float) (t.pos.getZ() - cam.z);
        for (Shape.Face f : t.shape.faces(t.facing, t.corner)) {
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

    /**
     * The part that will go, drawn on the block's outside: a wash on the top (or underside) of each quarter it takes and on
     * the fronts it opens, with a line at half height. A straight cut takes the two quarters along its front, an outer
     * corner the three along either front, an inner corner only the quarter where the two fronts meet.
     */
    private static void band(VertexConsumer vc, ShapingRules.Aim aim, StairsShape corner, float ox, float oy, float oz) {
        ShapingRules.Zone zone = aim.zone();
        if (zone == ShapingRules.Zone.CENTER_TOP || zone == ShapingRules.Zone.CENTER_BOTTOM) {
            flat(vc, zone == ShapingRules.Zone.CENTER_TOP ? 1F + PROUD : -PROUD, ox, oy, oz);
            return;
        }
        Direction f = aim.facing();
        Direction g = switch (corner) {
            case OUTER_LEFT, INNER_LEFT -> f.getCounterClockWise();
            case OUTER_RIGHT, INNER_RIGHT -> f.getClockWise();
            case STRAIGHT -> null;
        };
        boolean inner = corner == StairsShape.INNER_LEFT || corner == StairsShape.INNER_RIGHT;
        float low = zone == ShapingRules.Zone.TOP ? 0.5F : 0F, high = zone == ShapingRules.Zone.UNDER ? 0.5F : 1F;
        for (int qx = 0; qx < 2; qx++) {
            for (int qz = 0; qz < 2; qz++) {
                boolean onF = on(qx, qz, f), onG = g != null && on(qx, qz, g);
                if (!(g == null ? onF : inner ? onF && onG : onF || onG)) continue;
                if (zone == ShapingRules.Zone.TOP) cell(vc, qx, qz, 1F + PROUD, ox, oy, oz);
                else if (zone == ShapingRules.Zone.UNDER) cell(vc, qx, qz, -PROUD, ox, oy, oz);
                if (onF) front(vc, zone, f, qx, qz, low, high, ox, oy, oz);
                if (onG) front(vc, zone, g, qx, qz, low, high, ox, oy, oz);
            }
        }
    }

    /** Whether this quarter of the block (by its x and z halves) lies along side d. */
    private static boolean on(int qx, int qz, Direction d) {
        return switch (d) {
            case EAST -> qx == 1;
            case WEST -> qx == 0;
            case SOUTH -> qz == 1;
            case NORTH -> qz == 0;
            default -> false;
        };
    }

    /** The wash and the line on side d, across this quarter. */
    private static void front(VertexConsumer vc, ShapingRules.Zone zone, Direction d, int qx, int qz, float low, float high, float ox, float oy, float oz) {
        float a = d.getAxis() == Direction.Axis.X ? qz * 0.5F : qx * 0.5F;
        side(vc, d, low, high, a, a + 0.5F, ox, oy, oz, WASH);
        if (zone != ShapingRules.Zone.MID) side(vc, d, 0.49F, 0.51F, a, a + 0.5F, ox, oy, oz, LINE);
    }

    /** A band on side d, from low to high, and across it from a0 to a1 (x on the north and south sides, z on the others). */
    private static void side(VertexConsumer vc, Direction d, float low, float high, float a0, float a1, float ox, float oy, float oz, int colour) {
        float e = PROUD;
        float y0 = oy + low, y1 = oy + high;
        switch (d) {
            case SOUTH -> {
                float z = oz + 1 + e, xa = ox + a0, xb = ox + a1;
                quad(vc, xa, y1, z, xa, y0, z, xb, y0, z, xb, y1, z, colour);
            }
            case NORTH -> {
                float z = oz - e, xa = ox + a0, xb = ox + a1;
                quad(vc, xb, y1, z, xb, y0, z, xa, y0, z, xa, y1, z, colour);
            }
            case EAST -> {
                float x = ox + 1 + e, za = oz + a0, zb = oz + a1;
                quad(vc, x, y1, zb, x, y0, zb, x, y0, za, x, y1, za, colour);
            }
            case WEST -> {
                float x = ox - e, za = oz + a0, zb = oz + a1;
                quad(vc, x, y1, za, x, y0, za, x, y0, zb, x, y1, zb, colour);
            }
            default -> {
            }
        }
    }

    /** One quarter of the top (y above half) or of the underside. */
    private static void cell(VertexConsumer vc, int qx, int qz, float y, float ox, float oy, float oz) {
        float x0 = ox + qx * 0.5F, x1 = x0 + 0.5F, z0 = oz + qz * 0.5F, z1 = z0 + 0.5F, h = oy + y;
        if (y > 0.5F) quad(vc, x0, h, z0, x0, h, z1, x1, h, z1, x1, h, z0, WASH);
        else quad(vc, x0, h, z1, x0, h, z0, x1, h, z0, x1, h, z1, WASH);
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
