package dev.awaken.delve.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.Awaken;
import dev.awaken.delve.DelveContent;
import dev.awaken.delve.HeavyStrikes;
import dev.awaken.delve.Ore;
import dev.awaken.delve.StrikeBlocks;
import dev.awaken.delve.TrueStrikes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The strike, seen (SPEC §34.3). It owns the hit and break particles of the rock, ore and loose-ground families (client
 * extensions, only on blocks no mod has claimed): a bite throws a few {@link StrikeChip chips} of the block's own texture,
 * a break a handful more and a low breath of dust in the block's colour. Sparks are streaks, thin lines drawn along their
 * flight, white-gold cooling to the ore's colour, struck only off metal ores and hard rock by a pick. On an ore the seam
 * of a true strike shows on the face being looked at. At most {@link #MAX_CHIPS} chips and {@link #MAX_STREAKS} streaks
 * from mining at once; the streaks live in fixed arrays.
 */
final class StrikeFx {
    private StrikeFx() {}

    static final int MAX_CHIPS = StrikeBlocks.MAX_CHIPS, MAX_STREAKS = StrikeBlocks.MAX_STREAKS;

    // ---- the block particles we own ---------------------------------------------------------------------------------

    private static final IClientBlockExtensions EXT = new IClientBlockExtensions() {
        @Override
        public boolean addHitEffects(BlockState state, Level level, BlockPos pos, Direction face, ParticleEngine manager) {
            // vanilla's crumbs come every tick; our bite comes with the hit's sound (every fourth), so nothing here
            return DelveClientConfig.strikeParticles();
        }

        @Override
        public boolean playHitSound(BlockState state, Level level, BlockPos pos, Direction face, SoundManager sounds) {
            if (DelveClientConfig.strikeParticles() && level instanceof ClientLevel cl) bite(cl, pos, face, state);
            return false;   // the hit's sound stays vanilla's until the recordings (P1b)
        }

        @Override
        public boolean addDestroyEffects(BlockState state, Level level, BlockPos pos, ParticleEngine manager) {
            if (!DelveClientConfig.strikeParticles() || !(level instanceof ClientLevel cl)) return false;
            broke(cl, pos, state);
            return true;
        }
    };

    static int claimed;

    /** Claims every block of our families that no mod has claimed (another mod's particles are never replaced). */
    static void register(RegisterClientExtensionsEvent e) {
        List<Block> mine = new ArrayList<>();
        for (Block b : BuiltInRegistries.BLOCK) if (StrikeBlocks.owns(b) && !e.isBlockRegistered(b)) mine.add(b);
        if (mine.isEmpty()) return;
        e.registerBlock(EXT, mine.toArray(Block[]::new));
        claimed = mine.size();
        Awaken.LOG.info("[delve] the strike draws the particles of {} blocks", claimed);
    }

    // ---- chips ------------------------------------------------------------------------------------------------------

    private static final StrikeChip[] LIVE = new StrikeChip[MAX_CHIPS];

    private static boolean chip(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, BlockState s, BlockPos pos, float size) {
        for (int i = 0; i < MAX_CHIPS; i++) {
            if (LIVE[i] == null || !LIVE[i].isAlive()) {
                StrikeChip c = new StrikeChip(level, x, y, z, vx, vy, vz, s, pos, size);
                LIVE[i] = c;
                Minecraft.getInstance().particleEngine.add(c);
                return true;
            }
        }
        return false;
    }

    /** A bite into the block: chips off the face, out and down. */
    static void bite(ClientLevel level, BlockPos pos, Direction face, BlockState s) {
        Minecraft mc = Minecraft.getInstance();
        int n = StrikeBlocks.biteChips(StrikeFeel.density(mc), level.getRandom());
        if (n == 0) return;
        RandomSource r = level.getRandom();
        Vec3 at = mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK && bh.getBlockPos().equals(pos)
                ? bh.getLocation() : Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        double ox = face.getStepX() * 0.06, oy = face.getStepY() * 0.06, oz = face.getStepZ() * 0.06;
        for (int i = 0; i < n; i++) {
            double sp = 0.08 + r.nextDouble() * 0.1;
            double vx = face.getStepX() * sp + (r.nextDouble() - 0.5) * 0.14;
            double vy = face.getStepY() * sp + 0.04 + r.nextDouble() * 0.1;
            double vz = face.getStepZ() * sp + (r.nextDouble() - 0.5) * 0.14;
            double jx = (r.nextDouble() - 0.5) * 0.15, jy = (r.nextDouble() - 0.5) * 0.15, jz = (r.nextDouble() - 0.5) * 0.15;
            if (!chip(level, at.x + ox + jx * (1 - Math.abs(face.getStepX())), at.y + oy + jy * (1 - Math.abs(face.getStepY())),
                    at.z + oz + jz * (1 - Math.abs(face.getStepZ())), vx, vy, vz, s, pos, 0.045F)) return;
        }
    }

    /** The block let go: chips from all through it, and a low breath of its dust. */
    static void broke(ClientLevel level, BlockPos pos, BlockState s) {
        Minecraft mc = Minecraft.getInstance();
        int density = StrikeFeel.density(mc);
        RandomSource r = level.getRandom();
        int n = StrikeBlocks.breakChips(density, r);
        VoxelShape shape = s.getShape(level, pos);
        double x0 = shape.isEmpty() ? 0 : shape.min(Direction.Axis.X), x1 = shape.isEmpty() ? 1 : shape.max(Direction.Axis.X);
        double y0 = shape.isEmpty() ? 0 : shape.min(Direction.Axis.Y), y1 = shape.isEmpty() ? 1 : shape.max(Direction.Axis.Y);
        double z0 = shape.isEmpty() ? 0 : shape.min(Direction.Axis.Z), z1 = shape.isEmpty() ? 1 : shape.max(Direction.Axis.Z);
        for (int i = 0; i < n; i++) {
            double fx = r.nextDouble(), fy = r.nextDouble(), fz = r.nextDouble();
            double x = pos.getX() + Mth.lerp(fx, x0, x1), y = pos.getY() + Mth.lerp(fy, y0, y1), z = pos.getZ() + Mth.lerp(fz, z0, z1);
            // out from the middle, a little up: the block comes apart rather than bursting
            double vx = (fx - 0.5) * 0.18 + (r.nextDouble() - 0.5) * 0.05, vy = 0.03 + fy * 0.1, vz = (fz - 0.5) * 0.18 + (r.nextDouble() - 0.5) * 0.05;
            if (!chip(level, x, y, z, vx, vy, vz, s, pos, i % 3 == 0 ? 0.07F : 0.05F)) break;
        }
        if (density == 0) return;
        int col = s.getMapColor(level, pos).col;
        int wisps = density == 1 ? 2 : 3 + r.nextInt(3);
        for (int i = 0; i < wisps; i++) {
            DelveParticles.spawn(level, DelveContent.DUST.get(), col, 2.2F, pos.getX() + 0.2 + r.nextDouble() * 0.6, pos.getY() + 0.15 + r.nextDouble() * 0.4,
                    pos.getZ() + 0.2 + r.nextDouble() * 0.6, (r.nextDouble() - 0.5) * 0.02, 0.004 + r.nextDouble() * 0.01, (r.nextDouble() - 0.5) * 0.02);
        }
    }

    // ---- streaks ----------------------------------------------------------------------------------------------------

    private static final double[] X = new double[MAX_STREAKS], Y = new double[MAX_STREAKS], Z = new double[MAX_STREAKS];
    private static final double[] PX = new double[MAX_STREAKS], PY = new double[MAX_STREAKS], PZ = new double[MAX_STREAKS];
    private static final double[] VX = new double[MAX_STREAKS], VY = new double[MAX_STREAKS], VZ = new double[MAX_STREAKS];
    private static final int[] AGE = new int[MAX_STREAKS], LIFE = new int[MAX_STREAKS], TINT = new int[MAX_STREAKS];
    private static final float[] HOT = new float[MAX_STREAKS];
    private static int streaks;
    private static final BlockPos.MutableBlockPos M = new BlockPos.MutableBlockPos();

    /** Sparks off the face at {@code at}: out along its normal, spread wide, falling and skittering. */
    static void sparks(ClientLevel level, Vec3 at, Direction face, int n, int tint, boolean hot, RandomSource r) {
        for (int i = 0; i < n && streaks < MAX_STREAKS; i++) {
            int k = streaks++;
            double sp = 0.22 + r.nextDouble() * 0.26;
            VX[k] = face.getStepX() * sp + (r.nextDouble() - 0.5) * 0.36;
            VY[k] = face.getStepY() * sp + (r.nextDouble() - 0.25) * 0.3;
            VZ[k] = face.getStepZ() * sp + (r.nextDouble() - 0.5) * 0.36;
            X[k] = PX[k] = at.x + face.getStepX() * 0.03;
            Y[k] = PY[k] = at.y + face.getStepY() * 0.03;
            Z[k] = PZ[k] = at.z + face.getStepZ() * 0.03;
            AGE[k] = 0;
            LIFE[k] = 4 + r.nextInt(5);
            TINT[k] = tint;
            HOT[k] = hot ? 1.35F : 1F;
        }
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            streaks = 0;
            return;
        }
        for (int k = 0; k < streaks; k++) {
            if (++AGE[k] > LIFE[k]) {
                int last = --streaks;
                copy(last, k);
                k--;
                continue;
            }
            PX[k] = X[k];
            PY[k] = Y[k];
            PZ[k] = Z[k];
            VY[k] -= 0.035;
            VX[k] *= 0.9;
            VY[k] *= 0.9;
            VZ[k] *= 0.9;
            // skitter: a face stops only its own axis, which bounces back weaker
            if (solid(level, X[k] + VX[k], Y[k], Z[k])) VX[k] = -VX[k] * 0.4;
            else X[k] += VX[k];
            if (solid(level, X[k], Y[k] + VY[k], Z[k])) VY[k] = -VY[k] * 0.4;
            else Y[k] += VY[k];
            if (solid(level, X[k], Y[k], Z[k] + VZ[k])) VZ[k] = -VZ[k] * 0.4;
            else Z[k] += VZ[k];
        }
        seamFade = Mth.clamp(seamFade + (seamTarget() != null ? 0.2F : -0.25F), 0F, 1F);
    }

    private static void copy(int from, int to) {
        X[to] = X[from];
        Y[to] = Y[from];
        Z[to] = Z[from];
        PX[to] = PX[from];
        PY[to] = PY[from];
        PZ[to] = PZ[from];
        VX[to] = VX[from];
        VY[to] = VY[from];
        VZ[to] = VZ[from];
        AGE[to] = AGE[from];
        LIFE[to] = LIFE[from];
        TINT[to] = TINT[from];
        HOT[to] = HOT[from];
    }

    private static boolean solid(ClientLevel level, double x, double y, double z) {
        M.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        if (!level.hasChunkAt(M)) return false;
        BlockState s = level.getBlockState(M);
        return !s.isAir() && s.isCollisionShapeFullBlock(level, M);
    }

    // ---- the seam ---------------------------------------------------------------------------------------------------

    private static float seamFade;
    private static BlockPos seamPos = BlockPos.ZERO;
    private static Direction seamFace = Direction.UP;
    private static int seamN, seamTint;

    /** The ore the local player is looking at with a pick (and sets the seam's block), or null. */
    private static BlockHitResult seamTarget() {
        Minecraft mc = Minecraft.getInstance();
        if (!DelveClientConfig.trueStrikes() || mc.player == null || mc.level == null) return null;
        if (!(mc.hitResult instanceof BlockHitResult bh) || bh.getType() != HitResult.Type.BLOCK) return null;
        if (!HeavyStrikes.isMiningTool(mc.player.getMainHandItem())) return null;
        BlockState s = mc.level.getBlockState(bh.getBlockPos());
        Ore ore = Ore.of(s);
        if (ore == null) return null;
        seamPos = bh.getBlockPos();
        seamFace = bh.getDirection();
        seamN = TrueStrikes.trues(mc.player, seamPos);
        seamTint = ore.tint;
        return bh;
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    private static double cx, cy, cz;
    private static float pt, time;

    static void submit(SubmitCustomGeometryEvent e) {
        if (streaks == 0 && seamFade <= 0F) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var state = e.getLevelRenderState();
        Vec3 cam = state.cameraRenderState.pos;
        cx = cam.x;
        cy = cam.y;
        cz = cam.z;
        pt = state.worldPartialTicks;
        time = (mc.level.getGameTime() % 24000L) + pt;
        e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), DelveRender.glow(), DRAW);
    }

    private static final net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer DRAW = (pose, vc) -> {
        try {
            for (int k = 0; k < streaks; k++) streak(vc, k);
            if (seamFade > 0F) seam(vc);
        } catch (RuntimeException ex) {
            Awaken.LOG.error("[delve] strike drawing failed", ex);
            streaks = 0;
        }
    };

    private static void streak(VertexConsumer vc, int k) {
        double hx = Mth.lerp(pt, PX[k], X[k]) - cx, hy = Mth.lerp(pt, PY[k], Y[k]) - cy, hz = Mth.lerp(pt, PZ[k], Z[k]) - cz;
        // the tail: where it was a tick and a half ago, so a fast spark is a long line and a slow one shortens to a fleck
        double tx = hx - VX[k] * 1.5, ty = hy - VY[k] * 1.5, tz = hz - VZ[k] * 1.5;
        double dx = hx - tx, dy = hy - ty, dz = hz - tz;
        // across the line, facing the eye
        double sx = dy * hz - dz * hy, sy = dz * hx - dx * hz, sz = dx * hy - dy * hx;
        double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (len < 1e-6) return;
        float t = (AGE[k] + pt) / (LIFE[k] + 1F);
        double w = 0.011 * (1.0 - 0.4 * t) / len;
        sx *= w;
        sy *= w;
        sz *= w;
        float heat = Math.max(0F, 1F - t * 1.6F) * HOT[k];
        int tint = TINT[k];
        // white-gold at first, the ore's colour as it cools
        int r = (int) Mth.lerp(Math.min(1F, heat), (tint >> 16) & 255, 255), g = (int) Mth.lerp(Math.min(1F, heat), (tint >> 8) & 255, 238),
                b = (int) Mth.lerp(Math.min(1F, heat), tint & 255, 196);
        float a = (1F - t * t) * Math.min(1F, 0.75F + 0.25F * HOT[k]);
        int head = DelveRender.col(r << 16 | g << 8 | b, a), tail = DelveRender.col(r << 16 | g << 8 | b, 0F);
        vc.addVertex((float) (tx - sx), (float) (ty - sy), (float) (tz - sz)).setColor(tail);
        vc.addVertex((float) (tx + sx), (float) (ty + sy), (float) (tz + sz)).setColor(tail);
        vc.addVertex((float) (hx + sx), (float) (hy + sy), (float) (hz + sz)).setColor(head);
        vc.addVertex((float) (hx - sx), (float) (hy - sy), (float) (hz - sz)).setColor(head);
    }

    /** The seam: a short bright line on the face, tapering at both ends, breathing a little. */
    private static void seam(VertexConsumer vc) {
        double[] s = TrueStrikes.seam(seamPos.asLong(), seamFace.get3DDataValue(), seamN);
        Vec3 mid = TrueStrikes.onFace(seamPos, seamFace, s[0], s[1]);
        Direction.Axis ax = seamFace.getAxis();
        // the face's two axes in x, y, z order (as TrueStrikes reads u and v)
        double ux = ax == Direction.Axis.X ? 0 : 1, uy = ax == Direction.Axis.X ? 1 : 0;
        double vy = ax == Direction.Axis.Z ? 1 : 0, vz = ax == Direction.Axis.Z ? 0 : 1;
        double ca = Math.cos(s[2]), sa = Math.sin(s[2]);
        // along the seam (u, v rotated by its angle) and across it, both on the face
        double lx = ux * ca, ly = uy * ca + vy * sa, lz = vz * sa;
        double nx = seamFace.getStepX(), ny = seamFace.getStepY(), nz = seamFace.getStepZ();
        double wx = ny * lz - nz * ly, wy = nz * lx - nx * lz, wz = nx * ly - ny * lx;
        double L = TrueStrikes.HALF_LENGTH, W = 0.035;
        float x = (float) (mid.x + nx * 0.004 - cx), y = (float) (mid.y + ny * 0.004 - cy), z = (float) (mid.z + nz * 0.004 - cz);
        float breathe = 0.7F + 0.3F * Mth.sin(time * 0.25F);
        int tint = seamTint;
        int rgb = ((((tint >> 16) & 255) + 255) / 2) << 16 | ((((tint >> 8) & 255) + 240) / 2) << 8 | (((tint & 255) + 210) / 2);
        int core = DelveRender.col(rgb, 0.9F * breathe * seamFade), edge = DelveRender.col(rgb, 0F), end = DelveRender.col(rgb, 0.12F * seamFade);
        // four quads round the middle: middle bright, the edges across nothing, the ends along faint
        for (int i = -1; i <= 1; i += 2) {
            for (int j = -1; j <= 1; j += 2) {
                float ex = (float) (lx * L * i), ey = (float) (ly * L * i), ez = (float) (lz * L * i);
                float fx = (float) (wx * W * j), fy = (float) (wy * W * j), fz = (float) (wz * W * j);
                vc.addVertex(x, y, z).setColor(core);
                vc.addVertex(x + ex, y + ey, z + ez).setColor(end);
                vc.addVertex(x + ex + fx, y + ey + fy, z + ez + fz).setColor(edge);
                vc.addVertex(x + fx, y + fy, z + fz).setColor(edge);
            }
        }
    }

    static void reset() {
        streaks = 0;
        seamFade = 0F;
        java.util.Arrays.fill(LIVE, null);
    }
}
