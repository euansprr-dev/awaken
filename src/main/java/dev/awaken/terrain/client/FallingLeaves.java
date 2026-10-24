package dev.awaken.terrain.client;

import dev.awaken.Awaken;
import dev.awaken.terrain.Wind;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;

/**
 * Leaves in the air (docs/modules/lands.md): each tick a few random spots 6-32 blocks from the camera, most of them
 * where it looks, are tried; a spot under a canopy (a leaf block with air below) may drop a leaf, more often in strong
 * wind and in leafy biomes ({@link AirTable}). The Lumen Forest's air is glowing motes rising at night instead.
 * <ul>
 * <li>At most the setting's cap alive (150 at normal), held in fixed slots, scaled down when frames run slow.</li>
 * <li>Colour from the leaf itself: its tint (BlockColors) over the average of its texture, so oak follows the biome's
 * foliage and cherry, azalea or pale oak keep their own; a biome may push every leaf toward its colour (Elderwood's
 * gold).</li>
 * <li>Nothing allocated per tick beyond the particles themselves.</li>
 * </ul>
 */
final class FallingLeaves {
    private FallingLeaves() {}

    private static final float LEAF_CHANCE = 0.06F, MOTE_CHANCE = 0.05F;
    private static final float MIN_R = 6F, MAX_R = 32F;
    private static final Identifier[] LEAF_SPRITES = new Identifier[FallingLeafParticle.LEAF_SPRITES];
    private static final Identifier[] MOTE_SPRITES = {Awaken.id("lumen_mote_0"), Awaken.id("lumen_mote_1")};
    private static final FallingLeafParticle[] SLOTS = new FallingLeafParticle[TerrainClientConfig.LeafAmount.HIGH.cap];
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos(), BELOW = new BlockPos.MutableBlockPos();
    private static final RandomSource RNG = RandomSource.create();
    private static final IdentityHashMap<Block, Integer> AVERAGE = new IdentityHashMap<>();

    static {
        for (int i = 0; i < LEAF_SPRITES.length; i++) LEAF_SPRITES[i] = Awaken.id("falling_leaf_" + i);
    }

    private static @Nullable ClientLevel slotLevel;
    private static int alive, spawned;
    /** 1 while frames are fast; down to 0.25 when they are slow (an average over the last second or so). */
    private static float budgetScale = 1F;
    private static double frameMs = 8;
    private static long lastFrame;
    /** Debug: spawn as if every spot were in this biome ({@code /awaken terrain leaves biome}). */
    static AirTable.@Nullable BiomeStyle forcedBiome;
    static @Nullable Identifier forcedBiomeId;

    /** Resources reloaded: sprites and textures may have changed. */
    static void reloaded() {
        AVERAGE.clear();
    }

    /** Once a frame: keep a running frame time and scale the cap from it. */
    static void frame() {
        framesMeasured++;
        long now = System.nanoTime();
        if (lastFrame != 0) {
            double ms = Math.min(250, (now - lastFrame) / 1e6);
            frameMs += (ms - frameMs) * 0.03;
            // full at 50 fps or better (a 60 fps cap costs nothing), a quarter by 30 fps
            budgetScale = (float) Math.clamp((33.0 - frameMs) / 13.0, 0.25, 1.0);
        }
        lastFrame = now;
    }

    // what the air costs on the CPU, summed between two status lines (load-proof, unlike frame times on a busy Mac)
    static long spawnNanos, particleNanos, extractNanos, windNanos;
    private static int ticksMeasured, framesMeasured;

    static void tick(Minecraft mc) {
        long t0 = System.nanoTime();
        tickSpawner(mc);
        spawnNanos += System.nanoTime() - t0;
        ticksMeasured++;
    }

    private static void tickSpawner(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level != slotLevel) {
            java.util.Arrays.fill(SLOTS, null);
            slotLevel = level;
        }
        alive = 0;
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] != null && SLOTS[i].isAlive()) alive++;
            else SLOTS[i] = null;
        }
        TerrainClientConfig.LeafAmount amount = TerrainClientConfig.fallingLeaves();
        if (amount == TerrainClientConfig.LeafAmount.OFF && alive > 0) { // switched off: the air clears at once
            for (int i = 0; i < SLOTS.length; i++) {
                if (SLOTS[i] != null) SLOTS[i].remove();
                SLOTS[i] = null;
            }
            alive = 0;
        }
        Wind.Field wind = ClientWind.field();
        if (level == null || mc.player == null || mc.isPaused() || amount == TerrainClientConfig.LeafAmount.OFF || wind == null) return;
        int cap = Math.max(1, (int) (amount.cap * budgetScale));
        Camera cam = mc.gameRenderer.mainCamera();
        Vec3 c = cam.position();
        float yaw = cam.yRot() * Mth.DEG_TO_RAD;
        float night = night(Math.floorMod(level.getOverworldClockTime(), 24000L));
        for (int i = 0; i < amount.attempts && alive < cap; i++) {
            if (trySpawn(mc, level, wind, c, yaw, night, amount.rate)) {
                alive++;
                spawned++;
            }
        }
    }

    private static boolean trySpawn(Minecraft mc, ClientLevel level, Wind.Field wind, Vec3 c, float yaw, float night, float rateMul) {
        // most spots where the camera looks: forward is (-sin yaw, cos yaw)
        float a = RNG.nextFloat() < 0.7F ? yaw + (RNG.nextFloat() - 0.5F) * 2.4F : RNG.nextFloat() * Mth.TWO_PI;
        float r = MIN_R + (MAX_R - MIN_R) * Mth.sqrt(RNG.nextFloat());
        int bx = Mth.floor(c.x - Mth.sin(a) * r), bz = Mth.floor(c.z + Mth.cos(a) * r);
        if (!level.hasChunk(bx >> 4, bz >> 4)) return false;
        Holder<Biome> biome = level.getBiome(POS.set(bx, Mth.floor(c.y), bz));
        AirTable.BiomeStyle bs = forcedBiome != null ? forcedBiome : AirTable.biome(biome);
        float strength = wind.strength(bx + 0.5, bz + 0.5);

        if (bs.motes() != null && bs.motes().kind() != null) {
            // glints on glass, sparks and dust over the faults (the second pair of Awakened biomes)
            AirTable.MoteStyle m = bs.motes();
            if (RNG.nextFloat() < MOTE_CHANCE * m.rate() * rateMul && LandsMotes.spawn(mc, level, bx, bz, c, night, budgetScale, m)) return true;
        } else if (bs.motes() != null) {
            AirTable.MoteStyle m = bs.motes();
            float when = m.night() ? night : 1F;
            if (when > 0F && RNG.nextFloat() < MOTE_CHANCE * m.rate() * when * rateMul) return spawnMote(mc, level, bx, bz, c, m);
        }
        if (bs.rate() <= 0F) return false;

        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz); // first free block above the highest one
        if (top < c.y - 20 || top > c.y + 40) return false;
        int floor = Math.max(level.getMinY() + 1, top - 16);
        for (int y = top - 1; y >= floor; y--) {
            BlockState st = level.getBlockState(POS.set(bx, y, bz));
            if (st.is(BlockTags.LEAVES)) {
                if (!level.getBlockState(BELOW.set(bx, y - 1, bz)).isAir()) continue;
                AirTable.LeafStyle ls = AirTable.leaf(st);
                if (ls.skip()) return false;
                float chance = LEAF_CHANCE * bs.rate() * ls.rate() * rateMul * (0.25F + 1.5F * strength);
                if (RNG.nextFloat() >= chance) return false;
                return spawnLeaf(mc, level, st, bx, y, bz, ls, bs);
            } else if (!st.isAir()) {
                return false; // a trunk, a roof, the ground: no canopy over air here
            }
        }
        return false;
    }

    private static boolean spawnLeaf(Minecraft mc, ClientLevel level, BlockState st, int bx, int y, int bz,
                                     AirTable.LeafStyle ls, AirTable.BiomeStyle bs) {
        int[] sprites = bs.sprites() != null ? bs.sprites() : ls.sprites();
        TextureAtlasSprite sprite = sprite(mc, LEAF_SPRITES[sprites[RNG.nextInt(sprites.length)]]);
        int rgb = leafColor(mc, level, st, POS.set(bx, y, bz), ls, bs);
        float size = 0.09F * ls.size() * bs.size() * (0.85F + RNG.nextFloat() * 0.3F);
        return add(mc, new FallingLeafParticle(level, bx + 0.1 + RNG.nextFloat() * 0.8, y - 0.08, bz + 0.1 + RNG.nextFloat() * 0.8,
                sprite, rgb, size, false));
    }

    private static boolean spawnMote(Minecraft mc, ClientLevel level, int bx, int bz, Vec3 c, AirTable.MoteStyle m) {
        TextureAtlasSprite sprite = sprite(mc, MOTE_SPRITES[RNG.nextInt(MOTE_SPRITES.length)]);
        int rgb = shade(m.color() < 0 ? 0x7FF0F2 : m.color(), 0.9F + RNG.nextFloat() * 0.3F);
        float size = 0.12F + RNG.nextFloat() * 0.1F;
        // motes gather nearer than leaves fall (3.6-19 blocks): a speck far off is a pixel
        double x = c.x + (bx + RNG.nextFloat() - c.x) * 0.6, z = c.z + (bz + RNG.nextFloat() - c.z) * 0.6;
        int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
        if (gy < c.y - 16 || gy > c.y + 16) return false;
        return add(mc, new FallingLeafParticle(level, x, gy + 0.2 + RNG.nextFloat() * 2.5, z, sprite, rgb, size, true));
    }

    private static boolean add(Minecraft mc, FallingLeafParticle p) {
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] == null) {
                SLOTS[i] = p;
                mc.particleEngine.add(p);
                return true;
            }
        }
        return false;
    }

    private static TextureAtlasSprite sprite(Minecraft mc, Identifier id) {
        return mc.getAtlasManager().getAtlasOrThrow(AtlasIds.PARTICLES).getSprite(id);
    }

    /** The leaf's colour: its tint over the average of its texture, a biome's colour mixed in, a little variation. */
    private static int leafColor(Minecraft mc, ClientLevel level, BlockState st, BlockPos pos, AirTable.LeafStyle ls, AirTable.BiomeStyle bs) {
        int rgb;
        if (ls.color() >= 0) {
            rgb = ls.color();
        } else {
            Integer avg = AVERAGE.get(st.getBlock());
            if (avg == null) {
                avg = average(mc, st);
                AVERAGE.put(st.getBlock(), avg);
            }
            BlockTintSource tint = mc.getBlockColors().getTintSource(st, 0);
            rgb = tint == null ? shade(avg, 1.1F) : multiply(avg, tint.colorInWorld(st, level, pos), 1.55F);
        }
        if (bs.color() >= 0) rgb = mix(rgb, bs.color(), bs.colorMix());
        if (RNG.nextFloat() < 0.18F) rgb = mix(rgb, 0xC9A83A, 0.35F); // now and then one already turning
        return shade(rgb, 0.86F + RNG.nextFloat() * 0.26F);
    }

    /** Average colour of the block's particle texture (its opaque pixels). */
    private static int average(Minecraft mc, BlockState st) {
        try {
            TextureAtlasSprite s = mc.getModelManager().getBlockStateModelSet().getParticleMaterial(st).sprite();
            long r = 0, g = 0, b = 0, n = 0;
            int w = s.contents().width(), h = s.contents().height();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = s.getPixelARGB(0, x, y);
                    if ((argb >>> 24) < 128) continue;
                    r += (argb >> 16) & 255;
                    g += (argb >> 8) & 255;
                    b += argb & 255;
                    n++;
                }
            }
            if (n > 0) return (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
        } catch (RuntimeException e) {
            Awaken.LOG.debug("[lands] no texture average for {}: {}", st, e.toString());
        }
        return 0x5F8A3A;
    }

    private static int multiply(int a, int b, float gain) {
        int r = Math.min(255, Math.round(((a >> 16) & 255) * ((b >> 16) & 255) / 255F * gain));
        int g = Math.min(255, Math.round(((a >> 8) & 255) * ((b >> 8) & 255) / 255F * gain));
        int bl = Math.min(255, Math.round((a & 255) * (b & 255) / 255F * gain));
        return r << 16 | g << 8 | bl;
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255));
        int g = Math.round(Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255));
        int bl = Math.round(Mth.lerp(t, a & 255, b & 255));
        return r << 16 | g << 8 | bl;
    }

    private static int shade(int rgb, float f) {
        int r = Math.min(255, Math.round(((rgb >> 16) & 255) * f));
        int g = Math.min(255, Math.round(((rgb >> 8) & 255) * f));
        int b = Math.min(255, Math.round((rgb & 255) * f));
        return r << 16 | g << 8 | b;
    }

    /** 0 by day, 1 at night, eased across dusk and dawn (overworld clock). */
    static float night(long day) {
        if (day >= 13500 && day <= 22500) return 1F;
        if (day > 12000 && day < 13500) return (day - 12000) / 1500F;
        if (day > 22500 && day < 23800) return (23800 - day) / 1300F;
        return 0F;
    }

    static String status() {
        double perTick = ticksMeasured == 0 ? 0 : (spawnNanos + particleNanos) / 1e3 / ticksMeasured;
        double perFrame = framesMeasured == 0 ? 0 : (extractNanos + windNanos) / 1e3 / framesMeasured;
        double framesPerTick = ticksMeasured == 0 ? 1 : Math.max(1.0, framesMeasured / (double) ticksMeasured);
        String line = String.format(java.util.Locale.ROOT,
                "%d alive (cap %d x %.2f budget), %d spawned, frame %.1f ms; cpu: spawner+rustle %.1f us + particles %.1f us a tick, wind+texels %.1f us + drawing %.1f us a frame = %.3f ms a frame%s",
                alive, TerrainClientConfig.fallingLeaves().cap, budgetScale, spawned, frameMs,
                ticksMeasured == 0 ? 0 : spawnNanos / 1e3 / ticksMeasured, ticksMeasured == 0 ? 0 : particleNanos / 1e3 / ticksMeasured,
                framesMeasured == 0 ? 0 : windNanos / 1e3 / framesMeasured, framesMeasured == 0 ? 0 : extractNanos / 1e3 / framesMeasured, (perTick / framesPerTick + perFrame) / 1e3, forcedBiomeId == null ? "" : ", forced biome " + forcedBiomeId);
        spawnNanos = particleNanos = extractNanos = windNanos = 0;
        ticksMeasured = framesMeasured = 0;
        return line;
    }
}
