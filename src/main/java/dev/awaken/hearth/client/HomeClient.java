package dev.awaken.hearth.client;

import dev.awaken.citizens.client.CityGlow;
import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.block.HearthfireBlockEntity;
import dev.awaken.hearth.net.HomeNet;
import dev.awaken.hearth.player.HearthView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The carried flame on the client (SPEC §28.2): the moment a coal takes (it sinks into the ring with a breath of
 * sparks, then warmth runs out over the land as a ring of soft glow on the ground's own height, and at night the
 * fireflies come), the lifting (the warmth drawn back in), fireflies over your land at night, and the drawn light of
 * the Ember carried in a hand and of every Hearthfire (a warm pool on the ground and a halo: the city's drawn-light
 * way through {@link CityGlow}, so no light block is ever placed or relit).
 */
public final class HomeClient {
    private HomeClient() {}

    /** One moment playing: where, how far the warmth runs, when it began, whether the coal was lifted. */
    private record Moment(BlockPos pos, int radius, long start, boolean lifted) {}

    private static final List<Moment> MOMENTS = new ArrayList<>();
    static final int SINK = 14, WAVE_FROM = 10, WAVE_TICKS = 44, TOTAL = 80;
    /** The Ember's light carried: the pool round the bearer's feet and the halo at the lantern. */
    static final float EMBER_POOL = 5.2F, EMBER_POOL_A = 0.34F, EMBER_HALO = 0.75F, EMBER_CORE = 0.07F;
    /** A Hearthfire's own glow over its block light (warmer, wider, softer). */
    static final float FIRE_POOL = 7.5F, FIRE_POOL_A = 0.22F, FIRE_HALO = 1.25F, FIRE_CORE = 0.12F;
    /** The Anchor's brazier, by tier: a wider, warmer pool than the campfire's and a halo round the fire above the rim. */
    static final float ANCHOR_POOL = 9.5F, ANCHOR_POOL_STEP = 0.8F, ANCHOR_POOL_A = 0.28F, ANCHOR_POOL_A_STEP = 0.02F,
            ANCHOR_HALO = 1.7F, ANCHOR_CORE = 0.16F;
    /** An Anchor not seen burning for this long (out of animateTick's reach) drops out of the glow. */
    static final long ANCHOR_FORGET = 20 * 30;
    private static final BlockPos.MutableBlockPos ANCHOR_AT = new BlockPos.MutableBlockPos();

    static void payloads(RegisterClientPayloadHandlersEvent e) {
        e.register(HomeNet.Kindled.TYPE, (msg, ctx) -> kindled(msg));
        e.register(HomeNet.NameCard.TYPE, (msg, ctx) -> Minecraft.getInstance().gui.setScreen(new NameScreen(msg.offered())));
        e.register(HomeNet.RoomCaption.TYPE, (msg, ctx) -> HomeHud.room(msg.name(), msg.grade(), msg.purpose()));
    }

    private static void kindled(HomeNet.Kindled m) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        MOMENTS.add(new Moment(m.pos(), m.radius(), level.getGameTime(), m.lifted()));
    }

    static void clear() {
        MOMENTS.clear();
        HearthfireBlockEntity.LOADED.clear();
    }

    // ---- per tick -------------------------------------------------------------------------------------------------------

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;
        long now = level.getGameTime();
        RandomSource r = level.getRandom();
        for (int i = MOMENTS.size() - 1; i >= 0; i--) {
            Moment m = MOMENTS.get(i);
            long age = now - m.start;
            if (age > TOTAL || age < 0) {
                MOMENTS.remove(i);
                continue;
            }
            if (m.lifted) lifting(level, m, (int) age, r);
            else taking(level, m, (int) age, r);
        }
        if (now % 8 == 0) fireflies(mc, level, r);
        HomeHud.tick(mc);
    }

    /** The coal sinks into the ring with a breath of sparks; then the warmth runs out over the land; fireflies at dusk. */
    private static void taking(ClientLevel level, Moment m, int age, RandomSource r) {
        double cx = m.pos.getX() + 0.5, cy = m.pos.getY() + 0.25, cz = m.pos.getZ() + 0.5;
        if (age < SINK) {
            // sparks drawn down into the coal, and a tongue of flame rising as it takes
            for (int k = 0; k < 4; k++) {
                double a = r.nextDouble() * Math.PI * 2, d = 0.6 + r.nextDouble() * 0.9;
                level.addParticle(HearthContent.HEARTH_EMBER.get(), true, true, cx + Math.cos(a) * d, cy + 0.9 + r.nextDouble() * 0.6,
                        cz + Math.sin(a) * d, -Math.cos(a) * d * 0.06, -0.05, -Math.sin(a) * d * 0.06);
            }
            if (age > SINK / 2) level.addParticle(HearthContent.HEARTH_FLAME.get(), true, true, cx, cy, cz, 0, 0.06, 0);
        }
        if (age == SINK) {
            for (int k = 0; k < 36; k++) {
                double a = r.nextDouble() * Math.PI * 2;
                level.addParticle(HearthContent.HEARTH_EMBER.get(), true, true, cx, cy + 0.3, cz, Math.cos(a) * 0.08, 0.06 + r.nextDouble() * 0.1,
                        Math.sin(a) * 0.08);
            }
        }
        int wave = age - WAVE_FROM;
        if (wave >= 0 && wave <= WAVE_TICKS) {
            // the warmth's front: eased out, so it leaves fast and settles at the edge of the land
            double f0 = ease((wave - 1) / (double) WAVE_TICKS), f1 = ease(wave / (double) WAVE_TICKS);
            ring(level, m, f0 * m.radius, f1 * m.radius, 1.0 - 0.55 * f1, r);
        }
        if (age > WAVE_FROM + 6 && age < WAVE_FROM + WAVE_TICKS && night(level) && r.nextInt(2) == 0) {
            firefly(level, m.pos, m.radius, r);
        }
    }

    /** The coal comes out of the ring: the warmth is drawn back in from the edge, sparks lift out of the stones. */
    private static void lifting(ClientLevel level, Moment m, int age, RandomSource r) {
        if (age > 30) return;
        double f1 = 1.0 - ease(age / 30.0), f0 = 1.0 - ease((age - 1) / 30.0);
        ring(level, m, f1 * m.radius, f0 * m.radius, 0.6, r);
        double cx = m.pos.getX() + 0.5, cy = m.pos.getY() + 0.3, cz = m.pos.getZ() + 0.5;
        if (age < 12) level.addParticle(HearthContent.HEARTH_EMBER.get(), true, true, cx, cy, cz, (r.nextDouble() - 0.5) * 0.05, 0.12, (r.nextDouble() - 0.5) * 0.05);
    }

    /** One band of the warmth's ring between two radii, each mote on the ground's own height at its spot. */
    private static void ring(ClientLevel level, Moment m, double r0, double r1, double bright, RandomSource r) {
        double mid = (r0 + r1) / 2;
        int n = Mth.clamp((int) (mid * Math.PI * 2 / 1.6), 6, 110);
        Vec3 cam = Minecraft.getInstance().gameRenderer.mainCamera().position();
        for (int k = 0; k < n; k++) {
            double a = (k + r.nextDouble()) / n * Math.PI * 2, d = r0 + r.nextDouble() * Math.max(0.5, r1 - r0);
            int x = Mth.floor(m.pos.getX() + 0.5 + Math.cos(a) * d), z = Mth.floor(m.pos.getZ() + 0.5 + Math.sin(a) * d);
            if (!level.hasChunkAt(new BlockPos(x, m.pos.getY(), z))) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - m.pos.getY()) > 24) continue;
            double px = x + r.nextDouble(), pz = z + r.nextDouble();
            if (cam.distanceToSqr(px, y, pz) > 110 * 110) continue;
            level.addParticle(HearthContent.HEARTH_WARMTH.get(), true, true, px, y + 0.08, pz, 0, bright, 0);
        }
    }

    private static double ease(double t) {
        t = Mth.clamp(t, 0, 1);
        return 1 - Math.pow(1 - t, 3);
    }

    /** Fireflies over your own land at night, a few round you where you stand. */
    private static void fireflies(Minecraft mc, ClientLevel level, RandomSource r) {
        if (!night(level)) return;
        HearthView v = mc.player.getData(HearthContent.VIEW.get());
        if (!v.has() || !HomeHud.within(v, mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ())) return;
        firefly(level, mc.player.blockPosition(), 14, r);
    }

    private static void firefly(ClientLevel level, BlockPos around, int radius, RandomSource r) {
        int x = around.getX() + r.nextInt(radius * 2 + 1) - radius, z = around.getZ() + r.nextInt(radius * 2 + 1) - radius;
        if (!level.hasChunkAt(new BlockPos(x, around.getY(), z))) return;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (Math.abs(y - around.getY()) > 12) return;
        level.addParticle(HearthContent.HEARTH_FIREFLY.get(), x + r.nextDouble(), y + 0.6 + r.nextDouble() * 1.8, z + r.nextDouble(), 0, 0, 0);
    }

    static boolean night(ClientLevel level) {
        long t = Math.floorMod(level.getOverworldClockTime(), 24000L);
        return t >= 12600 && t <= 23200;
    }

    // ---- drawn light ----------------------------------------------------------------------------------------------------

    static final int WAVE_POOLS = 40;

    /** {@code n} pools of light round a circle on the ground (each at the ground's height there). */
    private static void groundRing(CityGlow.Out out, ClientLevel level, BlockPos c, float radius, float pool, float strength, int n) {
        if (strength <= 0.01F) return;
        int count = radius < 2 ? 1 : n;
        for (int k = 0; k < count; k++) {
            double a = k * Math.PI * 2 / count;
            double x = c.getX() + 0.5 + Math.cos(a) * radius, z = c.getZ() + 0.5 + Math.sin(a) * radius;
            int bx = Mth.floor(x), bz = Mth.floor(z);
            if (!level.hasChunk(bx >> 4, bz >> 4)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            if (Math.abs(y - c.getY()) > 24 || out.far(x, y, z)) continue;
            out.pool(x, y + 0.03, z, pool, strength);
        }
    }

    /** The Ember's light in a hand and every Hearthfire's glow, drawn in the city's light pass (allocation-free). */
    static final CityGlow.Source GLOW = (out, pt, cx, cy, cz) -> {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        for (Player p : level.players()) {
            boolean main = p.getMainHandItem().is(HearthContent.EMBER_ITEM.get()), off = p.getOffhandItem().is(HearthContent.EMBER_ITEM.get());
            if (!main && !off || p.isSpectator()) continue;
            double x = Mth.lerp(pt, p.xo, p.getX()), y = Mth.lerp(pt, p.yo, p.getY()), z = Mth.lerp(pt, p.zo, p.getZ());
            if (out.far(x, y, z)) continue;
            float f = out.flicker(p.getId() * 31 + 7);
            // the lantern hangs at the hip on its side: where the hand is, a little forward
            float yaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
            double side = (main == (p.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT)) ? -0.38 : 0.38;
            double fx = -Mth.sin(yaw), fz = Mth.cos(yaw), lx = Mth.cos(yaw), lz = Mth.sin(yaw);
            double hx = x + fx * 0.25 + lx * side, hy = y + (p.isCrouching() ? 0.62 : 0.78), hz = z + fz * 0.25 + lz * side;
            out.pool(hx, y + 0.03, hz, EMBER_POOL, EMBER_POOL_A * f);
            if (p != mc.player || !mc.options.getCameraType().isFirstPerson()) out.halo(hx, hy, hz, EMBER_HALO, EMBER_CORE, f);
        }
        // the warmth running out over the land (or drawn back in when the coal is lifted): a ring of light on the ground,
        // each pool on the ground's own height at its spot, a fainter one behind the front
        long now = level.getGameTime();
        for (int i = 0; i < MOMENTS.size(); i++) {
            Moment m = MOMENTS.get(i);
            float age = now - m.start + pt;
            float front, fade;
            if (m.lifted) {
                if (age > 30) continue;
                front = (float) (1 - ease(age / 30.0)) * m.radius;
                fade = 0.7F;
            } else {
                float w = age - WAVE_FROM;
                if (w < 0 || w > WAVE_TICKS + 24) continue;
                front = (float) ease(Math.min(1.0, w / WAVE_TICKS)) * m.radius;
                fade = Mth.clamp(w / 6F, 0F, 1F) * (w > WAVE_TICKS ? 1F - (w - WAVE_TICKS) / 24F : 1F) * (1F - 0.45F * front / m.radius);
            }
            groundRing(out, level, m.pos, front, 2.8F, 0.30F * fade, WAVE_POOLS);
            if (front > 4) groundRing(out, level, m.pos, front * 0.62F, 3.4F, 0.11F * fade, WAVE_POOLS / 2);
        }
        for (int i = 0; i < HearthfireBlockEntity.LOADED.size(); i++) {
            BlockPos b = HearthfireBlockEntity.LOADED.get(i);
            double x = b.getX() + 0.5, z = b.getZ() + 0.5;
            if (out.far(x, b.getY(), z)) continue;
            float f = out.flicker(b.hashCode());
            out.halo(x, b.getY() + 0.55, z, FIRE_HALO, FIRE_CORE, f);
            out.pool(x, b.getY() + 0.03, z, FIRE_POOL, FIRE_POOL_A * f);
        }
        // every Anchor burning nearby: the pool spreads and warms a little with each tier
        for (int i = dev.awaken.hearth.block.HearthAnchorBlock.seen - 1; i >= 0; i--) {
            ANCHOR_AT.set(dev.awaken.hearth.block.HearthAnchorBlock.SEEN_POS[i]);
            BlockState st = level.getBlockState(ANCHOR_AT);
            if (!st.is(HearthContent.HEARTH_ANCHOR.get()) || now - dev.awaken.hearth.block.HearthAnchorBlock.SEEN_AT[i] > ANCHOR_FORGET) {
                dev.awaken.hearth.block.HearthAnchorBlock.forget(i);
                continue;
            }
            double x = ANCHOR_AT.getX() + 0.5, y = ANCHOR_AT.getY(), z = ANCHOR_AT.getZ() + 0.5;
            if (out.far(x, y, z)) continue;
            int tier = st.getValue(dev.awaken.hearth.block.HearthAnchorBlock.TIER);
            float f = out.flicker(ANCHOR_AT.hashCode());
            out.halo(x, y + 1.45, z, ANCHOR_HALO + 0.12F * tier, ANCHOR_CORE, f);
            out.pool(x, y + 0.03, z, ANCHOR_POOL + ANCHOR_POOL_STEP * tier, (ANCHOR_POOL_A + ANCHOR_POOL_A_STEP * tier) * f);
        }
    };
}
