package dev.awaken.delve.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.Awaken;
import dev.awaken.delve.DelveContent;
import dev.awaken.delve.DelveNet;
import dev.awaken.delve.Ore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayList;
import java.util.List;

/**
 * The Delver's Pick's gifts, seen (SPEC §34.4): the wake's burst, the embers' sparks (their light is
 * {@link TorchGlow}'s), the Call's ore flying home, and the two sights drawn through the rock: Echo (a ring out from the
 * struck block, ores and the open air of caves within {@value #ECHO_REACH} lighting as it passes, four seconds) and
 * Glass (the rock in a cone before your eyes turns to glass for five seconds: hidden caves, ores and creatures beyond).
 * Both read the client's own world: the server only says when.
 */
final class PickFx {
    private PickFx() {}

    static final int ECHO_REACH = 12, GLASS_REACH = 22;
    /** The ring's speed (blocks a tick), the glass's sweep, the cone's half-angle (cosine) and the most it draws. */
    private static final float RING = 0.8F, SWEEP = 1.4F, CONE = 0.78F;
    private static final int MAX_ORE = 320, MAX_AIR = 900, FADE_IN = 4, FADE_OUT = 20;
    private static final int AIR = 0x9FD8FF, GLASS_AIR = 0xC7B8FF, CREATURE = 0xFF8A6A, ORE = 0xFFD27A;
    private static final RandomSource R = RandomSource.create();

    // ---- the sight ------------------------------------------------------------------------------------------------------

    private static int kind = -1, ticks;
    private static long start;
    private static double ox, oy, oz;
    private static Vec3 look = Vec3.ZERO;
    private static long[] pos = new long[0];
    private static int[] rgb = new int[0];
    private static float[] dist = new float[0];
    private static boolean[] solid = new boolean[0];
    private static int n;

    static void sight(DelveNet.PickSight m) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;
        kind = m.kind();
        ticks = m.ticks();
        start = level.getGameTime();
        BlockPos o = BlockPos.of(m.origin());
        if (kind == 0) {
            ox = o.getX() + 0.5;
            oy = o.getY() + 0.5;
            oz = o.getZ() + 0.5;
        } else {
            Vec3 eye = mc.player.getEyePosition();
            ox = eye.x;
            oy = eye.y;
            oz = eye.z;
            look = mc.player.getViewVector(1F);
        }
        try {
            scan(level, kind == 0 ? ECHO_REACH : GLASS_REACH);
        } catch (RuntimeException ex) {
            Awaken.LOG.error("[delve] the pick's sight failed", ex);
            kind = -1;
        }
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI / 12;
            level.addParticle(ParticleTypes.END_ROD, ox + Math.cos(a) * 0.6, oy, oz + Math.sin(a) * 0.6, Math.cos(a) * 0.08, 0.0, Math.sin(a) * 0.08);
        }
    }

    /** What the sight shows: ores (full blocks, their ore's tint) and open air in caves (a lattice of points), nearest first. */
    private static void scan(ClientLevel level, int reach) {
        int cap = MAX_ORE + MAX_AIR;
        if (pos.length < cap) {
            pos = new long[cap];
            rgb = new int[cap];
            dist = new float[cap];
            solid = new boolean[cap];
        }
        n = 0;
        int ores = 0, airs = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int bx = Mth.floor(ox), by = Mth.floor(oy), bz = Mth.floor(oz);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    int d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 > reach * reach) continue;
                    int x = bx + dx, y = by + dy, z = bz + dz;
                    if (level.isOutsideBuildHeight(y) || !level.hasChunk(x >> 4, z >> 4)) continue;
                    float d = Mth.sqrt(d2);
                    if (kind == 1) {
                        if (d < 2) continue;
                        double cos = (dx * look.x + dy * look.y + dz * look.z) / d;
                        if (cos < CONE) continue;
                    }
                    m.set(x, y, z);
                    BlockState s = level.getBlockState(m);
                    int tint = ore(s);
                    if (tint != 0) {
                        if (ores >= MAX_ORE || kind == 1 && !hidden(level, x + 0.5, y + 0.5, z + 0.5)) continue;
                        add(m.asLong(), tint, d, true);
                        ores++;
                    } else if (s.isAir() && (x & 1) == 0 && (y & 1) == 0 && (z & 1) == 0 && airs < MAX_AIR && !level.canSeeSky(m)) {
                        if (kind == 1 && !hidden(level, x + 0.5, y + 0.5, z + 0.5)) continue;
                        add(m.asLong(), kind == 0 ? AIR : GLASS_AIR, d, false);
                        airs++;
                    }
                }
            }
        }
    }

    private static void add(long p, int c, float d, boolean block) {
        pos[n] = p;
        rgb[n] = c;
        dist[n] = d;
        solid[n] = block;
        n++;
    }

    /** Whether rock stands between your eyes and a point (the glass shows what you couldn't see). */
    private static boolean hidden(ClientLevel level, double x, double y, double z) {
        double dx = x - ox, dy = y - oy, dz = z - oz;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = (int) (len / 0.5);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        long target = BlockPos.asLong(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            m.set(ox + dx * t, oy + dy * t, oz + dz * t);
            if (m.asLong() == target) break;
            if (level.getBlockState(m).isSolidRender()) return true;
        }
        return false;
    }

    /** An ore's tint (0: not ore): ours by the Ore it is, any other ore a warm gold. */
    private static int ore(BlockState s) {
        if (s.isAir()) return 0;
        for (Ore o : Ore.values()) if (o.matches(s) || s.is(o.heart())) return o.tint;
        return s.is(Tags.Blocks.ORES) ? ORE : 0;
    }

    static void submit(SubmitCustomGeometryEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (kind < 0 || mc.level == null) return;
        float age = mc.level.getGameTime() - start + e.getLevelRenderState().worldPartialTicks;
        if (age > ticks || age < 0) {
            kind = -1;
            return;
        }
        RenderType type = DelveRender.through();
        if (type == null) return;
        Vec3 cam = e.getLevelRenderState().cameraRenderState.pos;
        cx = cam.x;
        cy = cam.y;
        cz = cam.z;
        now = age;
        e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), type, DRAW);
    }

    private static double cx, cy, cz;
    private static float now;

    private static final net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer DRAW = (pose, vc) -> {
        try {
            draw(vc);
        } catch (RuntimeException ex) {
            Awaken.LOG.error("[delve] the pick's sight failed", ex);
            kind = -1;
        }
    };

    private static void draw(VertexConsumer vc) {
        float front = now * (kind == 0 ? RING : SWEEP);
        float out = Mth.clamp((ticks - now) / FADE_OUT, 0F, 1F);
        for (int i = 0; i < n; i++) {
            float since = (front - dist[i]) / (kind == 0 ? RING : SWEEP);
            if (since < 0) continue;
            float a = Math.min(1F, since / FADE_IN) * out;
            // a flash as the ring passes, settling to a steady glow
            float pass = since < 6 ? 1F + (6 - since) / 6F : 1F;
            long p = pos[i];
            float x = (float) (BlockPos.getX(p) - cx), y = (float) (BlockPos.getY(p) - cy), z = (float) (BlockPos.getZ(p) - cz);
            if (solid[i]) {
                DelveRender.box(vc, x + 0.06F, y + 0.06F, z + 0.06F, x + 0.94F, y + 0.94F, z + 0.94F, DelveRender.col(rgb[i], 0.22F * a * pass));
                DelveRender.box(vc, x + 0.3F, y + 0.3F, z + 0.3F, x + 0.7F, y + 0.7F, z + 0.7F, DelveRender.col(rgb[i], 0.35F * a * pass));
            } else {
                float r = 0.07F + 0.05F * (pass - 1F);
                DelveRender.box(vc, x + 0.5F - r, y + 0.5F - r, z + 0.5F - r, x + 0.5F + r, y + 0.5F + r, z + 0.5F + r, DelveRender.col(rgb[i], 0.5F * a * pass));
            }
        }
        if (kind != 1) return;
        // creatures beyond the glass, where they are now
        Minecraft mc = Minecraft.getInstance();
        AABB around = new AABB(ox - GLASS_REACH, oy - GLASS_REACH, oz - GLASS_REACH, ox + GLASS_REACH, oy + GLASS_REACH, oz + GLASS_REACH);
        for (Entity en : mc.level.getEntities(mc.player, around, en -> en instanceof LivingEntity && en.isAlive())) {
            Vec3 c = en.getBoundingBox().getCenter();
            double dx = c.x - ox, dy = c.y - oy, dz = c.z - oz, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < 1 || d > GLASS_REACH || (dx * look.x + dy * look.y + dz * look.z) / d < CONE) continue;
            AABB b = en.getBoundingBox();
            DelveRender.box(vc, (float) (b.minX - cx), (float) (b.minY - cy), (float) (b.minZ - cz), (float) (b.maxX - cx), (float) (b.maxY - cy), (float) (b.maxZ - cz),
                    DelveRender.col(CREATURE, 0.3F * out));
        }
    }

    // ---- the wake, embers' sparks, the Call -------------------------------------------------------------------------

    /** A verse wakes: light gathers round the pick in your hand. */
    static void woke(DelveNet.PickWoke m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Vec3 eye = mc.player.getEyePosition();
        for (int i = 0; i < 40; i++) {
            double a = R.nextDouble() * Math.PI * 2, r = 1.2 + R.nextDouble() * 0.8, h = (R.nextDouble() - 0.4) * 1.6;
            double x = eye.x + Math.cos(a) * r, y = eye.y - 0.4 + h, z = eye.z + Math.sin(a) * r;
            DelveParticles.spawn(mc.level, DelveContent.LIGHT_MOTE.get(), 0xFFE2B0, 1.4F, x, y, z, (eye.x - x) * 0.04, (eye.y - 0.4 - y) * 0.04, (eye.z - z) * 0.04);
        }
    }

    private record Call(int player, int ore, long[] cells, int ticks, long start) {}

    private static final List<Call> CALLS = new ArrayList<>();

    static void call(DelveNet.CallAnswer m) {
        var level = Minecraft.getInstance().level;
        if (level != null) CALLS.add(new Call(m.player(), m.ore(), m.cells(), m.ticks(), level.getGameTime()));
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) return;
        sparks(level);
        for (var it = CALLS.iterator(); it.hasNext(); ) {
            Call c = it.next();
            long t = level.getGameTime() - c.start();
            if (t > c.ticks() + 20) {
                it.remove();
                continue;
            }
            Entity who = level.getEntity(c.player());
            if (who == null) continue;
            int tint = c.ore() >= 0 && c.ore() < Ore.values().length ? Ore.values()[c.ore()].tint : ORE;
            Vec3 to = who.getBoundingBox().getCenter();
            // each block, in its turn, sends a streak of its ore home
            int i = (int) Math.min(c.cells().length - 1, t * c.cells().length / Math.max(1, c.ticks()));
            BlockPos b = BlockPos.of(c.cells()[i]);
            for (int k = 0; k < 4; k++) {
                double x = b.getX() + 0.5 + (R.nextDouble() - 0.5) * 0.6, y = b.getY() + 0.5 + (R.nextDouble() - 0.5) * 0.6, z = b.getZ() + 0.5 + (R.nextDouble() - 0.5) * 0.6;
                DelveParticles.spawn(level, DelveContent.LIGHT_MOTE.get(), tint, 1.2F, x, y, z, (to.x - x) * 0.09, (to.y - y) * 0.09, (to.z - z) * 0.09);
            }
        }
    }

    /** The embers burning near you: a spark now and then off each. */
    private static void sparks(ClientLevel level) {
        long[] at = TorchGlow.emberAt;
        byte[] face = TorchGlow.emberFace;
        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < at.length; i++) {
            if (R.nextInt(6) != 0) continue;
            var d = net.minecraft.core.Direction.from3DDataValue(face[i]);
            double x = BlockPos.getX(at[i]) + 0.5 + d.getStepX() * 0.42, y = BlockPos.getY(at[i]) + 0.5 + d.getStepY() * 0.42, z = BlockPos.getZ(at[i]) + 0.5 + d.getStepZ() * 0.42;
            if (mc.player.distanceToSqr(x, y, z) > 48 * 48) continue;
            level.addParticle(R.nextInt(3) == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.ELECTRIC_SPARK, x, y, z, -d.getStepX() * 0.01, 0.01, -d.getStepZ() * 0.01);
        }
    }

    static void embers(DelveNet.Embers m) {
        TorchGlow.emberAt = m.at();
        TorchGlow.emberFace = m.face();
    }

    static void reset() {
        kind = -1;
        n = 0;
        CALLS.clear();
        TorchGlow.emberAt = new long[0];
        TorchGlow.emberFace = new byte[0];
    }
}
