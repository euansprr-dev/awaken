package dev.awaken.delve.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.Awaken;
import dev.awaken.delve.DelveContent;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The miner's lamp (PLAN 7.5): its light is drawn on the rock ahead of whoever wears it, never placed (no light
 * blocks, nothing relit). A fan of rays from the brow finds the faces in front; each lit face is drawn again over itself
 * with its own block quads (the same model variant the world chose there, so it lines up), additively and in the
 * lamp's warm colour, brightest at the beam's middle and near, gone at its edge and at {@link #RANGE}: the rock's own
 * texture comes up out of the dark. A soft additive glow (the city lanterns' pass, {@code citizens.client.CityGlow})
 * gives the beam its haze, and from outside the lamp is a small flame on the brow. A lode's glint shows where the beam
 * falls ({@link #lights}).
 */
public final class LampGlow {
    private LampGlow() {}

    // ---- the numbers -----------------------------------------------------------------------------------------------

    /** The beam's reach (blocks), its full half-angle and the half-angle of its bright core; others' lamps within OTHERS. */
    static final double RANGE = 22, OTHERS = 40;
    static final float CONE_DEG = 24F, CORE_DEG = 9F;
    /** How bright the lit rock gets at the beam's heart, near (the texture times this, added). */
    static final float BRIGHT = 0.95F;
    /** The faces one wearer's beam may light in a frame. */
    static final int MAX_FACES = 260;
    /** Rays: one down the middle, a ring of six at a third of the cone, a ring of ten at its edge. */
    private static final float[][] RAYS = rays();
    /** Warm lamp light (a carbide flame through brass). */
    private static final float R = 1.00F, G = 0.80F, B = 0.56F;

    private static float[][] rays() {
        float[][] out = new float[17][];
        out[0] = new float[]{0, 0};
        for (int i = 0; i < 6; i++) out[1 + i] = new float[]{(float) (i * Math.PI / 3), CONE_DEG * 0.38F};
        for (int i = 0; i < 10; i++) out[7 + i] = new float[]{(float) (i * Math.PI / 5 + 0.2), CONE_DEG * 0.85F};
        return out;
    }

    static boolean wears(Player p) {
        return p.getItemBySlot(EquipmentSlot.HEAD).is(DelveContent.MINERS_LAMP.get());
    }

    /** Whether the local player's lamp beam falls on this block now (the glints read it). */
    static boolean lights(BlockPos b) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !wears(p)) return false;
        Vec3 eye = p.getEyePosition(), to = Vec3.atCenterOf(b).subtract(eye);
        double d = to.length();
        return d < RANGE && d > 0.5 && p.getViewVector(1F).dot(to.scale(1 / d)) > Math.cos(Math.toRadians(CONE_DEG));
    }

    // ---- drawing ---------------------------------------------------------------------------------------------------

    private static final Vector3f RIGHT = new Vector3f(), UP = new Vector3f();
    private static final Quaternionf CAM = new Quaternionf();
    private static double cx, cy, cz;
    private static float pt;
    private static ClientLevel level;
    // reused every frame: the lit faces (pos * 8 + face)
    private static final LongOpenHashSet FACES = new LongOpenHashSet();
    private static final BlockPos.MutableBlockPos M = new BlockPos.MutableBlockPos(), N = new BlockPos.MutableBlockPos();
    /** The beam's light on the rock, per vertex. */
    private static final DelveRender.VertexLight BEAM = (x, y, z) -> col(light(x, y, z) * BRIGHT);
    // the beam being drawn: its origin and axis
    private static double ox, oy, oz, ax, ay, az;

    static void submit(SubmitCustomGeometryEvent e) {
        Minecraft mc = Minecraft.getInstance();
        level = mc.level;
        if (level == null || mc.player == null) return;
        boolean any = false;
        for (Player p : level.players()) {
            if (wears(p) && p.distanceToSqr(mc.player) < OTHERS * OTHERS) {
                any = true;
                break;
            }
        }
        if (!any) return;
        var state = e.getLevelRenderState();
        Vec3 cam = state.cameraRenderState.pos;
        cx = cam.x;
        cy = cam.y;
        cz = cam.z;
        pt = state.worldPartialTicks;
        CAM.set(state.cameraRenderState.orientation);
        RIGHT.set(1, 0, 0).rotate(CAM);
        UP.set(0, 1, 0).rotate(CAM);
        RenderType rock = DelveRender.lit();
        if (rock != null) e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), rock, DRAW_ROCK);
        e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), DelveRender.glow(), DRAW_GLOW);
    }

    private static final net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer DRAW_ROCK = (pose, vc) -> {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (level == null || mc.player == null) return;
            for (Player p : level.players()) if (wears(p) && p.distanceToSqr(mc.player) <= OTHERS * OTHERS) rock(vc, p);
        } catch (RuntimeException ex) {
            Awaken.LOG.error("[delve] lamp light failed", ex);
            DelveRender.litOk = 0;
        }
    };

    private static final net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer DRAW_GLOW = (pose, vc) -> {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (level == null || mc.player == null) return;
            for (Player p : level.players()) {
                if (!wears(p) || p.distanceToSqr(mc.player) > OTHERS * OTHERS) continue;
                haze(vc, p);
                if (p != mc.getCameraEntity() || !mc.options.getCameraType().isFirstPerson()) lampHalo(vc, p);
            }
        } catch (RuntimeException ex) {
            Awaken.LOG.error("[delve] lamp glow failed", ex);
        }
    };

    /** Sets the beam's origin (the brow, a hand above the eye) and axis for a wearer. */
    private static void aim(Player p) {
        Vec3 eye = p.getEyePosition(pt), f = p.getViewVector(pt);
        ox = eye.x;
        oy = eye.y + 0.22;
        oz = eye.z;
        ax = f.x;
        ay = f.y;
        az = f.z;
    }

    /** The lit rock: the faces the beam finds (each ray's face and its eight neighbours on that face), drawn again lit. */
    private static void rock(VertexConsumer vc, Player p) {
        aim(p);
        FACES.clear();
        Vec3 f = new Vec3(ax, ay, az), from = new Vec3(ox, oy, oz);
        Vec3 side = new Vec3(-az, 0, ax);
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(f).normalize();
        int drawn = 0;
        for (float[] ray : RAYS) {
            double off = Math.toRadians(ray[1]);
            Vec3 dir = f.scale(Math.cos(off)).add(side.scale(Math.sin(off) * Math.cos(ray[0]))).add(up.scale(Math.sin(off) * Math.sin(ray[0])));
            BlockHitResult hit = level.clip(new ClipContext(from, from.add(dir.scale(RANGE)), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
            if (hit.getType() != HitResult.Type.BLOCK) continue;
            Direction face = hit.getDirection();
            BlockPos b = hit.getBlockPos();
            // the face plane's two in-plane axes
            Direction u = face.getAxis() == Direction.Axis.Y ? Direction.EAST : Direction.UP;
            Direction w = face.getAxis() == Direction.Axis.X ? Direction.SOUTH : face.getAxis() == Direction.Axis.Z ? Direction.EAST : Direction.SOUTH;
            for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) {
                M.set(b.getX() + u.getStepX() * i + w.getStepX() * j, b.getY() + u.getStepY() * i + w.getStepY() * j, b.getZ() + u.getStepZ() * i + w.getStepZ() * j);
                long key = M.asLong() * 8 + face.ordinal();
                if (!FACES.add(key)) continue;
                BlockState s = level.getBlockState(M);
                if (s.isAir() || !s.getFluidState().isEmpty()) continue;
                N.setWithOffset(M, face);
                if (level.getBlockState(N).isSolidRender()) continue;   // that face is buried
                drawn += DelveRender.face(vc, level, s, M, face, BEAM, cx, cy, cz);
                if (drawn >= MAX_FACES) return;
            }
        }
    }

    /** The beam's light at a point: its cone (bright core, soft edge) times its fall-off with distance. */
    private static float light(double x, double y, double z) {
        double dx = x - ox, dy = y - oy, dz = z - oz;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 1e-3 || d > RANGE) return 0;
        double c = (dx * ax + dy * ay + dz * az) / d;
        double edge = Math.cos(Math.toRadians(CONE_DEG)), core = Math.cos(Math.toRadians(CORE_DEG));
        if (c <= edge) return 0;
        double t = Math.min(1, (c - edge) / (core - edge));
        double cone = 0.25 + 0.75 * t * t * (3 - 2 * t);
        double fall = 1 - d / RANGE;
        return (float) (cone * fall * Math.sqrt(fall) * Math.min(1, 0.6 + d / 6));
    }

    /** The beam's haze: a soft pool of warm glow on each face a ray hits (the air lit between you and the rock). */
    private static void haze(VertexConsumer vc, Player p) {
        aim(p);
        Vec3 f = new Vec3(ax, ay, az), from = new Vec3(ox, oy, oz);
        Vec3 side = new Vec3(-az, 0, ax);
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(f).normalize();
        for (int k = 0; k < 7; k++) {
            float[] ray = RAYS[k];
            double off = Math.toRadians(ray[1]);
            Vec3 dir = f.scale(Math.cos(off)).add(side.scale(Math.sin(off) * Math.cos(ray[0]))).add(up.scale(Math.sin(off) * Math.sin(ray[0])));
            BlockHitResult hit = level.clip(new ClipContext(from, from.add(dir.scale(RANGE)), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
            if (hit.getType() != HitResult.Type.BLOCK) continue;
            double d = hit.getLocation().distanceTo(from);
            float fall = (float) (1 - d / RANGE);
            pool(vc, hit.getLocation(), hit.getDirection(), (float) (0.6 + d * 0.18), 0.10F * fall * (k == 0 ? 1.4F : 1F));
        }
    }

    /** A soft disc of light lying on the face it hit: bright in the middle, nothing at its rim. */
    private static void pool(VertexConsumer vc, Vec3 at, Direction face, float r, float s) {
        float x = (float) (at.x + face.getStepX() * 0.02 - cx), y = (float) (at.y + face.getStepY() * 0.02 - cy), z = (float) (at.z + face.getStepZ() * 0.02 - cz);
        DelveRender.pool(vc, x, y, z, face, r, col(s), col(s * 0.7F));
    }

    /** The lamp itself seen from outside: a hot little flame on the brow in a warm halo. */
    private static void lampHalo(VertexConsumer vc, Player p) {
        Vec3 eye = p.getEyePosition(pt), f = p.getViewVector(pt);
        Vec3 at = eye.add(0, 0.27, 0).add(f.scale(0.28));
        float x = (float) (at.x - cx), y = (float) (at.y - cy), z = (float) (at.z - cz);
        DelveRender.disc(vc, x, y, z, 0.5F, col(0.5F), col(0F), RIGHT, UP);
        DelveRender.disc(vc, x, y, z, 0.09F, 0xF2FFF2DA, 0x00FFDCA0, RIGHT, UP);
    }

    private static int col(float a) {
        // additive: the lightning blend takes alpha as the weight of the colour (and, lit, of the texture)
        return Mth.clamp((int) (a * 255F), 0, 255) << 24 | (int) (R * 255F) << 16 | (int) (G * 255F) << 8 | (int) (B * 255F);
    }
}
