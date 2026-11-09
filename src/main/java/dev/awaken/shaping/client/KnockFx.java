package dev.awaken.shaping.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.shaping.ShapingContent;
import dev.awaken.shaping.ShapingPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A knock's feel (SPEC §52.7): real chips of the block's own texture that tumble and land, a little dust in its colour, a
 * very small kick of the view for whoever swung, and the block's own sound under a wooden thud. No particle sheet.
 */
final class KnockFx {
    private KnockFx() {}

    private static final int MAX_CHIPS = 96, MAX_DUST = 96;
    private static final RandomSource R = RandomSource.create();

    private static final class Chip {
        double x, y, z, px, py, pz, vx, vy, vz;
        float size, rx, ry, rz, spinX, spinY, spinZ, u0, u1, v0, v1;
        int life, light;
    }

    private static final class Dust {
        double x, y, z, px, py, pz, vx, vy, vz;
        float size;
        int life, max, rgb;
    }

    private static final List<Chip> CHIPS = new ArrayList<>();
    private static final List<Dust> DUST = new ArrayList<>();
    private static final Quaternionf Q = new Quaternionf();
    private static final Vector3f[] CORNERS = new Vector3f[8];
    private static final Vector3f RIGHT = new Vector3f(), UP = new Vector3f();
    private static float kick, kickBefore;

    static {
        for (int i = 0; i < CORNERS.length; i++) CORNERS[i] = new Vector3f();
    }

    static void add(ShapingPayloads.Fx msg) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        BlockState look = Block.stateById(msg.look());
        Direction face = Direction.values()[Math.floorMod(msg.face(), 6)];
        BlockPos pos = msg.pos();
        LookMaterials.Look m = LookMaterials.of(look);
        double x = pos.getX() + 0.5 + face.getStepX() * 0.5, y = pos.getY() + 0.5 + face.getStepY() * 0.5, z = pos.getZ() + 0.5 + face.getStepZ() * 0.5;
        BlockPos out = pos.relative(face);
        int light = LightCoordsUtil.pack(level.getBrightness(LightLayer.BLOCK, out), level.getBrightness(LightLayer.SKY, out));
        int rgb = look.getMapColor(level, pos).col;
        SoundType sound = look.getSoundType();
        switch (msg.kind()) {
            case ShapingPayloads.FX_KNOCK -> {
                if (m != null) for (int i = 0; i < 7; i++) chip(x, y, z, face, m.side(face).sprite(), light);
                for (int i = 0; i < 6; i++) dust(x, y, z, face, rgb, 0.05F);
                level.playLocalSound(pos, sound.getHitSound(), SoundSource.BLOCKS, 0.75F, 0.85F + R.nextFloat() * 0.1F, false);
                level.playLocalSound(pos, ShapingContent.KNOCK.get(), SoundSource.BLOCKS, 0.55F, 0.92F + R.nextFloat() * 0.16F, false);
                if (mc.player != null && mc.player.getId() == msg.actor()) kick = 0.35F;
            }
            case ShapingPayloads.FX_LOOK -> {
                for (int i = 0; i < 3; i++) dust(x, y, z, face, rgb, 0.03F);
                level.playLocalSound(pos, sound.getPlaceSound(), SoundSource.BLOCKS, 0.4F, 1.1F + R.nextFloat() * 0.15F, false);
            }
            default -> level.playLocalSound(pos, sound.getStepSound(), SoundSource.BLOCKS, 0.5F, 0.8F, false);
        }
    }

    private static void chip(double x, double y, double z, Direction face, TextureAtlasSprite s, int light) {
        if (CHIPS.size() >= MAX_CHIPS) return;
        Chip c = new Chip();
        c.px = c.x = x + (R.nextDouble() - 0.5) * 0.5;
        c.py = c.y = y + (R.nextDouble() - 0.5) * 0.5;
        c.pz = c.z = z + (R.nextDouble() - 0.5) * 0.5;
        double out = 0.08 + R.nextDouble() * 0.08;
        c.vx = face.getStepX() * out + (R.nextDouble() - 0.5) * 0.12;
        c.vy = face.getStepY() * out + 0.06 + R.nextDouble() * 0.1;
        c.vz = face.getStepZ() * out + (R.nextDouble() - 0.5) * 0.12;
        c.size = 0.035F + R.nextFloat() * 0.035F;
        c.spinX = (R.nextFloat() - 0.5F) * 0.9F;
        c.spinY = (R.nextFloat() - 0.5F) * 0.9F;
        c.spinZ = (R.nextFloat() - 0.5F) * 0.9F;
        c.life = 16 + R.nextInt(10);
        float px = 3F / 16F, u = R.nextFloat() * (1F - px), v = R.nextFloat() * (1F - px);
        float du = s.getU1() - s.getU0(), dv = s.getV1() - s.getV0();
        c.u0 = s.getU0() + du * u;
        c.u1 = s.getU0() + du * (u + px);
        c.v0 = s.getV0() + dv * v;
        c.v1 = s.getV0() + dv * (v + px);
        c.light = light;
        CHIPS.add(c);
    }

    private static void dust(double x, double y, double z, Direction face, int rgb, float speed) {
        if (DUST.size() >= MAX_DUST) return;
        Dust d = new Dust();
        d.px = d.x = x + (R.nextDouble() - 0.5) * 0.7;
        d.py = d.y = y + (R.nextDouble() - 0.5) * 0.7;
        d.pz = d.z = z + (R.nextDouble() - 0.5) * 0.7;
        d.vx = face.getStepX() * speed + (R.nextDouble() - 0.5) * speed;
        d.vy = face.getStepY() * speed + R.nextDouble() * speed * 0.6;
        d.vz = face.getStepZ() * speed + (R.nextDouble() - 0.5) * speed;
        d.size = 0.05F + R.nextFloat() * 0.07F;
        d.max = d.life = 12 + R.nextInt(10);
        d.rgb = rgb;
        DUST.add(d);
    }

    static void tick() {
        kickBefore = kick;
        kick *= 0.45F;
        if (kick < 0.01F) kick = 0F;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        for (Iterator<Chip> it = CHIPS.iterator(); it.hasNext(); ) {
            Chip c = it.next();
            c.px = c.x;
            c.py = c.y;
            c.pz = c.z;
            if (--c.life <= 0 || level == null) {
                it.remove();
                continue;
            }
            c.vy -= 0.05;
            double nx = c.x + c.vx, ny = c.y + c.vy, nz = c.z + c.vz;
            if (level.getBlockState(BlockPos.containing(nx, ny, nz)).isSolid()) {
                c.vx *= 0.3;
                c.vz *= 0.3;
                c.vy = c.vy < -0.1 ? -c.vy * 0.25 : 0;
                c.spinX *= 0.4F;
                c.spinY *= 0.4F;
                c.spinZ *= 0.4F;
            } else {
                c.x = nx;
                c.y = ny;
                c.z = nz;
            }
            c.rx += c.spinX;
            c.ry += c.spinY;
            c.rz += c.spinZ;
        }
        for (Iterator<Dust> it = DUST.iterator(); it.hasNext(); ) {
            Dust d = it.next();
            d.px = d.x;
            d.py = d.y;
            d.pz = d.z;
            if (--d.life <= 0) {
                it.remove();
                continue;
            }
            d.x += d.vx;
            d.y += d.vy;
            d.z += d.vz;
            d.vx *= 0.85;
            d.vy = d.vy * 0.85 + 0.002;
            d.vz *= 0.85;
        }
    }

    static void view(ViewportEvent.ComputeCameraAngles e) {
        if (kick <= 0F && kickBefore <= 0F) return;
        e.setPitch(e.getPitch() + kickBefore + (kick - kickBefore) * (float) e.getPartialTick());
    }

    static void submit(SubmitCustomGeometryEvent e, Vec3 cam) {
        if (CHIPS.isEmpty() && DUST.isEmpty()) return;
        float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        if (!CHIPS.isEmpty()) {
            e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), Sheets.cutoutBlockItemSheet(), (pose, vc) -> {
                for (Chip c : CHIPS) chip(vc, c, cam, pt);
            });
        }
        if (!DUST.isEmpty()) {
            var o = e.getLevelRenderState().cameraRenderState.orientation;
            e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), RenderTypes.debugQuads(), (pose, vc) -> {
                RIGHT.set(1, 0, 0).rotate(o);
                UP.set(0, 1, 0).rotate(o);
                for (Dust d : DUST) dust(vc, d, cam, pt);
            });
        }
    }

    private static final int[][] CUBE = {
            {6, 7, 3, 2}, {5, 4, 0, 1}, {1, 0, 2, 3}, {4, 5, 7, 6}, {5, 1, 3, 7}, {0, 4, 6, 2}};

    private static void chip(VertexConsumer vc, Chip c, Vec3 cam, float pt) {
        float x = (float) (c.px + (c.x - c.px) * pt - cam.x), y = (float) (c.py + (c.y - c.py) * pt - cam.y), z = (float) (c.pz + (c.z - c.pz) * pt - cam.z);
        float s = c.life < 4 ? c.size * c.life / 4F : c.size;
        Q.rotationXYZ(c.rx, c.ry, c.rz);
        for (int i = 0; i < 8; i++) CORNERS[i].set((i & 1) == 0 ? -s : s, (i & 2) == 0 ? -s : s, (i & 4) == 0 ? -s : s).rotate(Q);
        for (int[] f : CUBE) {
            Vector3f p0 = CORNERS[f[0]], p1 = CORNERS[f[1]], p2 = CORNERS[f[2]];
            float ax = p1.x - p0.x, ay = p1.y - p0.y, az = p1.z - p0.z, bx = p2.x - p0.x, by = p2.y - p0.y, bz = p2.z - p0.z;
            float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1.0E-6F) continue;
            nx /= len;
            ny /= len;
            nz /= len;
            for (int i = 0; i < 4; i++) {
                Vector3f p = CORNERS[f[i]];
                vc.addVertex(x + p.x, y + p.y, z + p.z).setColor(-1).setUv(i == 2 || i == 3 ? c.u1 : c.u0, i == 1 || i == 2 ? c.v1 : c.v0)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(c.light).setNormal(nx, ny, nz);
            }
        }
    }

    private static void dust(VertexConsumer vc, Dust d, Vec3 cam, float pt) {
        float x = (float) (d.px + (d.x - d.px) * pt - cam.x), y = (float) (d.py + (d.y - d.py) * pt - cam.y), z = (float) (d.pz + (d.z - d.pz) * pt - cam.z);
        float t = d.life / (float) d.max;
        int colour = (int) (150F * t) << 24 | d.rgb & 0xFFFFFF;
        float s = d.size * (1.4F - t * 0.4F);
        float rx = RIGHT.x * s, ry = RIGHT.y * s, rz = RIGHT.z * s, ux = UP.x * s, uy = UP.y * s, uz = UP.z * s;
        vc.addVertex(x - rx + ux, y - ry + uy, z - rz + uz).setColor(colour);
        vc.addVertex(x - rx - ux, y - ry - uy, z - rz - uz).setColor(colour);
        vc.addVertex(x + rx - ux, y + ry - uy, z + rz - uz).setColor(colour);
        vc.addVertex(x + rx + ux, y + ry + uy, z + rz + uz).setColor(colour);
    }
}
