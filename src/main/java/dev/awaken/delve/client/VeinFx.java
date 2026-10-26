package dev.awaken.delve.client;

import dev.awaken.core.api.CombatFeedback;
import dev.awaken.delve.DelveContent;
import dev.awaken.delve.DelveNet;
import dev.awaken.delve.Ore;
import dev.awaken.delve.Veins;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Lodes on the client (PLAN 7.3-7.4): the glints where light touches a lode near you (a lantern or torch close by, or the
 * miner's lamp's beam), and the vein giving when its heart breaks. Then light runs out from the heart through the rock
 * along the vein: the front travels block by block with a crackle, each vein block it reaches flashes from inside its
 * own texture (its ore pixels brightest) and glowing cracks open across it, and the rock in front of a buried stretch
 * glows faintly where the light bleeds through, so the seam shows on the wall without a box or an edge drawn. The glow
 * settles to a low pulse while the window lasts. For 30 s its blocks fall in two blows each for the one who broke it
 * (the client's copy of the server's speed, so the bar and the break agree).
 */
public final class VeinFx {
    private VeinFx() {}

    /** The lodes the server says are near: id, ore, blocks. */
    private record Known(long id, Ore ore, long[] cells) {}

    private static final List<Known> NEAR = new ArrayList<>();
    static final int GLINT_EVERY = 6, GLINTS = 4, GLINT_RANGE = 24;

    // the opened lode: its blocks, each one's distance from the heart along it, when it opened, whether the window is ours
    private static final LongOpenHashSet OPEN = new LongOpenHashSet();
    private static final Long2IntOpenHashMap DEPTH = new Long2IntOpenHashMap();
    private static long heart, openedAt = Long.MIN_VALUE / 2, windowUntil = Long.MIN_VALUE, life;
    private static int tint, maxDepth, crackled, flashed;

    /** The front's speed along the vein (blocks of depth a tick), how fast a block's flash dies (ticks). */
    static final float CRACK_SPEED = 1.6F, FLASH = 7F;
    /** Full glow, then down to the trace that pulses while the window lasts (ticks; the trace's strength). */
    static final int HOLD = 60, SETTLE = 50, LAST = 24;
    static final float TRACE = 0.3F;
    /** Rock in front of a buried vein block glows through this many blocks, this much of the vein's own light each. */
    static final int REACH = 3;
    private static final float[] SEEP = {1F, 0.42F, 0.2F, 0.09F};
    /** Faces drawn a frame at most; the faces are found again this often (ticks) as the vein is mined. */
    static final int MAX_FACES = 700, REFIND = 5;

    // the faces the light shows on: block, face, rock between it and its vein block (k), that block's depth
    private static final LongArrayList FACE_POS = new LongArrayList();
    private static final IntArrayList FACE_META = new IntArrayList();
    private static final Long2IntOpenHashMap FACE_AT = new Long2IntOpenHashMap();

    static void near(DelveNet.LodesNear m) {
        NEAR.clear();
        for (DelveNet.Near n : m.lodes()) {
            if (n.ore() < 0 || n.ore() >= Ore.values().length) continue;
            NEAR.add(new Known(n.id(), Ore.values()[n.ore()], n.cells()));
        }
    }

    static void open(DelveNet.VeinOpen m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || m.ore() < 0 || m.ore() >= Ore.values().length) return;
        Ore ore = Ore.values()[m.ore()];
        OPEN.clear();
        DEPTH.clear();
        for (long c : m.cells()) OPEN.add(c);
        heart = m.heart();
        tint = ore.tint;
        openedAt = mc.level.getGameTime();
        windowUntil = m.yours() ? openedAt + m.ticks() : Long.MIN_VALUE;
        life = Math.max(HOLD + SETTLE, m.yours() ? m.ticks() : 0);
        crackled = flashed = 0;
        // the light's order: out from the heart through the vein's blocks (26 neighbours: the seam steps diagonally)
        LongArrayFIFOQueue q = new LongArrayFIFOQueue();
        DEPTH.put(heart, 0);
        q.enqueue(heart);
        maxDepth = 0;
        while (!q.isEmpty()) {
            long c = q.dequeueLong();
            int d = DEPTH.get(c);
            int x = BlockPos.getX(c), y = BlockPos.getY(c), z = BlockPos.getZ(c);
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                long n = BlockPos.asLong(x + dx, y + dy, z + dz);
                if (!OPEN.contains(n) || DEPTH.containsKey(n)) continue;
                DEPTH.put(n, d + 1);
                maxDepth = Math.max(maxDepth, d + 1);
                q.enqueue(n);
            }
        }
        // a block the walk cut off from the heart (a cave between) lights last
        for (long c : OPEN) if (!DEPTH.containsKey(c)) DEPTH.put(c, ++maxDepth);
        findFaces(mc.level);
        if (m.yours()) {
            CombatFeedback.hold(100);
            CombatFeedback.kick(0F, -2.6F, 1.4F, 12);
        }
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Player p = mc.player;
        if (level == null || p == null) return;
        long now = level.getGameTime();
        if (now % GLINT_EVERY == 0) glints(level, p);
        if (OPEN.isEmpty()) return;
        long age = now - openedAt;
        if (age > life) {
            OPEN.clear();
            FACE_POS.clear();
            FACE_META.clear();
            return;
        }
        if (age % REFIND == 0) findFaces(level);
        front(level, age);
    }

    /**
     * Where the light shows: each vein block's faces open to the air, and, out from a buried one, the first rock face
     * within {@link #REACH} that is (the light bleeding through the stone between). The nearest vein block wins a face.
     */
    private static void findFaces(ClientLevel level) {
        FACE_POS.clear();
        FACE_META.clear();
        FACE_AT.clear();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(), b = new BlockPos.MutableBlockPos(), o = new BlockPos.MutableBlockPos();
        for (long c : OPEN) {
            m.set(c);
            if (level.getBlockState(m).isAir()) continue;   // mined: its light went with it
            int d = DEPTH.get(c);
            for (Direction f : Direction.values()) {
                for (int k = 0; k <= REACH; k++) {
                    b.setWithOffset(m, f.getStepX() * k, f.getStepY() * k, f.getStepZ() * k);
                    if (k > 0 && OPEN.contains(b.asLong())) break;   // another vein block carries it from here
                    BlockState s = level.getBlockState(b);
                    if (s.isAir() || !s.getFluidState().isEmpty()) break;
                    o.setWithOffset(b, f);
                    if (level.getBlockState(o).isSolidRender()) continue;
                    long key = b.asLong() * 6 + f.ordinal();
                    int meta = f.ordinal() | k << 3 | d << 5;
                    int at = FACE_AT.getOrDefault(key, -1);
                    if (at < 0) {
                        FACE_AT.put(key, FACE_POS.size());
                        FACE_POS.add(b.asLong());
                        FACE_META.add(meta);
                    } else if ((meta >> 3 & 3) < (FACE_META.getInt(at) >> 3 & 3)
                            || (meta >> 3 & 3) == (FACE_META.getInt(at) >> 3 & 3) && d < FACE_META.getInt(at) >> 5) {
                        FACE_META.set(at, meta);
                    }
                    break;
                }
            }
        }
    }

    /** The front as it passes: the crackle travelling with it, grit and a glint off each open vein block it reaches. */
    private static void front(ClientLevel level, long age) {
        float front = age * CRACK_SPEED;
        while (crackled * 6 < Math.min(front, maxDepth) && crackled < 12) {
            BlockPos b = BlockPos.of(farthestAt(crackled * 6));
            level.playLocalSound(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5, DelveContent.VEIN_CRACK.get(), SoundSource.BLOCKS,
                    0.7F, 0.9F + crackled * 0.03F, false);
            crackled++;
        }
        int reached = (int) Math.min(front, maxDepth);
        if (flashed > reached || StrikeFeel.density(Minecraft.getInstance()) == 0) {
            flashed = reached + 1;
            return;
        }
        RandomSource r = level.getRandom();
        for (int i = 0; i < FACE_POS.size(); i++) {
            int meta = FACE_META.getInt(i), d = meta >> 5;
            if ((meta >> 3 & 3) != 0 || d < flashed || d > reached) continue;
            Direction f = Direction.values()[meta & 7];
            BlockPos b = BlockPos.of(FACE_POS.getLong(i));
            double x = b.getX() + 0.5 + f.getStepX() * 0.55, y = b.getY() + 0.5 + f.getStepY() * 0.55, z = b.getZ() + 0.5 + f.getStepZ() * 0.55;
            for (int g = 0; g < 2; g++) {
                DelveParticles.spawn(level, DelveContent.GRIT.get(), 0x9a948c, 0.9F, x + (r.nextDouble() - 0.5) * 0.7, y + (r.nextDouble() - 0.5) * 0.7,
                        z + (r.nextDouble() - 0.5) * 0.7, f.getStepX() * 0.04, 0.02, f.getStepZ() * 0.04);
            }
            if (r.nextFloat() < 0.5F) DelveParticles.spawn(level, DelveContent.GLINT.get(), tint, 1.3F, x, y, z, 0, 0, 0);
        }
        flashed = reached + 1;
    }

    private static long farthestAt(int depth) {
        for (var e : DEPTH.long2IntEntrySet()) if (e.getIntValue() == depth) return e.getLongKey();
        return heart;
    }

    // ---- drawing ---------------------------------------------------------------------------------------------------

    private static double cx, cy, cz;
    private static float time;
    // the face being drawn: its light (rgb, strength)
    private static int curRgb;
    private static float curA;
    private static final BlockPos.MutableBlockPos DRAW = new BlockPos.MutableBlockPos();
    private static final TextureAtlasSprite[] CRACKS = new TextureAtlasSprite[4];
    /** The vein's light on a face, per vertex: a slow shimmer through the rock so it reads as light, not paint. */
    private static final DelveRender.VertexLight VEIN = (x, y, z) -> {
        float n = Mth.sin((float) (x * 2.3 + y * 3.1 + z * 1.7) + time * 0.21F) * 0.5F + 0.5F;
        return DelveRender.col(curRgb, curA * (0.72F + 0.28F * n));
    };

    static void submit(SubmitCustomGeometryEvent e) {
        if (OPEN.isEmpty() || FACE_POS.isEmpty()) return;
        var state = e.getLevelRenderState();
        Vec3 cam = state.cameraRenderState.pos;
        cx = cam.x;
        cy = cam.y;
        cz = cam.z;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        time = (mc.level.getGameTime() - openedAt) + state.worldPartialTicks;
        RenderType lit = DelveRender.lit();
        if (lit != null) e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), lit, DRAW_LIT);
        e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), DelveRender.glow(), DRAW_GLOW);
    }

    /** The glow behind everything: full, then settling to a pulsing trace, gone over the window's last ticks. */
    private static float glow(float age) {
        float g = age < HOLD ? 1F : age < HOLD + SETTLE ? Mth.lerp((age - HOLD) / SETTLE, 1F, TRACE) : TRACE * (0.8F + 0.2F * Mth.sin(age * 0.12F));
        return g * Mth.clamp((life - age) / LAST, 0F, 1F);
    }

    /** A vein block's light at {@code age}: dark until the front reaches it (a little lead glow just ahead), a flash, then the glow. */
    private static float at(int depth, float age) {
        float front = age * CRACK_SPEED;
        if (depth > front) return Mth.clamp(1F - (depth - front) / 2.5F, 0F, 1F) * 0.25F * glow(age);
        float since = (front - depth) / CRACK_SPEED;
        return 0.55F * glow(age) + 0.95F * (float) Math.exp(-since / FLASH);
    }

    private static final net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer DRAW_LIT = (pose, vc) -> {
        try {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) return;
            for (int i = 0; i < 4; i++) CRACKS[i] = DelveRender.blockSprite(dev.awaken.Awaken.id("block/vein_cracks_" + i));
            int hot = mix(tint, 0xFFFFFF, 0.55F);
            int drawn = 0;
            for (int i = 0; i < FACE_POS.size() && drawn < MAX_FACES; i++) {
                int meta = FACE_META.getInt(i), k = meta >> 3 & 3;
                float a = at(meta >> 5, time) * SEEP[k];
                if (a < 0.01F) continue;
                DRAW.set(FACE_POS.getLong(i));
                BlockState s = level.getBlockState(DRAW);
                if (s.isAir()) continue;
                Direction f = Direction.values()[meta & 7];
                // the rock's own texture lit from within: the ore's colour, whiter in the flash
                float flash = Mth.clamp(a - 0.55F * glow(time) * SEEP[k], 0F, 1F);
                curRgb = mix(tint, 0xFFFFFF, 0.35F * flash);
                curA = Math.min(1.1F, a * (k == 0 ? 0.85F : 0.7F));
                drawn += DelveRender.face(vc, level, s, DRAW, f, VEIN, cx, cy, cz);
                // the cracks the light comes through: on the vein's own faces and the rock right over it
                if (k <= 1) {
                    long h = FACE_POS.getLong(i) * 0x9E3779B97F4A7C15L + f.ordinal();
                    TextureAtlasSprite sp = CRACKS[(int) (h >>> 40) & 3];
                    DelveRender.sprite(vc, sp, DRAW, f, (int) (h >>> 44) & 3, DelveRender.col(hot, Math.min(1F, a * (k == 0 ? 1F : 0.75F))), cx, cy, cz);
                    drawn++;
                }
            }
        } catch (RuntimeException ex) {
            dev.awaken.Awaken.LOG.error("[delve] vein light failed", ex);
            DelveRender.litOk = 0;
        }
    };

    /** A soft pool of the ore's light on each open vein face while it flashes: light spilling into the air. */
    private static final net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer DRAW_GLOW = (pose, vc) -> {
        try {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) return;
            for (int i = 0; i < FACE_POS.size(); i++) {
                int meta = FACE_META.getInt(i);
                if ((meta >> 3 & 3) != 0) continue;
                float a = at(meta >> 5, time);
                if (a < 0.05F) continue;
                DRAW.set(FACE_POS.getLong(i));
                if (level.getBlockState(DRAW).isAir()) continue;
                Direction f = Direction.values()[meta & 7];
                float x = (float) (DRAW.getX() + 0.5 + f.getStepX() * 0.53 - cx), y = (float) (DRAW.getY() + 0.5 + f.getStepY() * 0.53 - cy),
                        z = (float) (DRAW.getZ() + 0.5 + f.getStepZ() * 0.53 - cz);
                float s = Math.min(0.32F, a * 0.2F);
                DelveRender.pool(vc, x, y, z, f, 1.15F, DelveRender.col(tint, s), DelveRender.col(tint, s * 0.45F));
            }
        } catch (RuntimeException ex) {
            dev.awaken.Awaken.LOG.error("[delve] vein glow failed", ex);
        }
    };

    private static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, a >> 16 & 255, b >> 16 & 255), g = (int) Mth.lerp(t, a >> 8 & 255, b >> 8 & 255), bl = (int) Mth.lerp(t, a & 255, b & 255);
        return r << 16 | g << 8 | bl;
    }

    /** Glints: a few lode blocks near you that are open to the air and lit (a lamp's beam, or a light close by). */
    private static void glints(ClientLevel level, Player p) {
        if (NEAR.isEmpty() || StrikeFeel.density(Minecraft.getInstance()) == 0) return;
        RandomSource r = level.getRandom();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(), n = new BlockPos.MutableBlockPos();
        int shown = 0;
        for (int tries = 0; tries < 48 && shown < GLINTS; tries++) {
            Known k = NEAR.get(r.nextInt(NEAR.size()));
            long c = k.cells()[r.nextInt(k.cells().length)];
            m.set(c);
            if (m.distSqr(p.blockPosition()) > GLINT_RANGE * GLINT_RANGE || !k.ore().matches(level.getBlockState(m))) continue;
            Direction d = Direction.getRandom(r);
            n.setWithOffset(m, d);
            if (!level.getBlockState(n).isAir()) continue;
            if (level.getBrightness(LightLayer.BLOCK, n) < 8 && !LampGlow.lights(m)) continue;
            double x = m.getX() + 0.5 + d.getStepX() * 0.52 + (d.getStepX() == 0 ? (r.nextDouble() - 0.5) * 0.8 : 0);
            double y = m.getY() + 0.5 + d.getStepY() * 0.52 + (d.getStepY() == 0 ? (r.nextDouble() - 0.5) * 0.8 : 0);
            double z = m.getZ() + 0.5 + d.getStepZ() * 0.52 + (d.getStepZ() == 0 ? (r.nextDouble() - 0.5) * 0.8 : 0);
            DelveParticles.spawn(level, DelveContent.GLINT.get(), k.ore().tint, 1F, x, y, z, 0, 0, 0);
            shown++;
        }
    }

    /** The client's copy of the two-blow speed in the opened lode's window. */
    static void onBreakSpeed(PlayerEvent.BreakSpeed e) {
        if (!e.getEntity().level().isClientSide() || e.getPosition().isEmpty() || OPEN.isEmpty()) return;
        if (e.getEntity().level().getGameTime() > windowUntil) return;
        BlockPos pos = e.getPosition().get();
        if (!OPEN.contains(pos.asLong())) return;
        e.setNewSpeed(Math.max(e.getNewSpeed(), Veins.twoBlows(e.getState().getDestroySpeed(e.getEntity().level(), pos))));
    }

    static void reset() {
        NEAR.clear();
        OPEN.clear();
        FACE_POS.clear();
        FACE_META.clear();
        windowUntil = Long.MIN_VALUE;
    }
}
