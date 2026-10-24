package dev.awaken.terrain.client;

import dev.awaken.Awaken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The air of the second pair of Awakened biomes (docs/modules/lands.md), for air.json biome rules whose {@code motes}
 * name a {@code kind} ({@link FallingLeaves} hands those spots here):
 * <ul>
 * <li>{@code glint} (Glass Steppe): by day, where the sun reaches glass ({@code #awaken:lands/glints}: fused glass,
 * sunshard, its clusters), a star of light blinks on it now and then, on the face the camera sees;</li>
 * <li>{@code fault} (Shattered Highlands): at night sparks rise from the faults ({@code #awaken:lands/faults}); by day
 * grains of dust ride the wind low over the rock.</li>
 * </ul>
 * Held in fixed slots (at most {@link #CAP} alive, times the leaves' frame-budget scale); nothing allocated per tick but
 * the particles.
 */
final class LandsMotes {
    private LandsMotes() {}

    static final int CAP = 120;
    static final TagKey<Block> GLINTS = TagKey.create(Registries.BLOCK, Awaken.id("lands/glints"));
    static final TagKey<Block> FAULTS = TagKey.create(Registries.BLOCK, Awaken.id("lands/faults"));
    private static final Identifier[] GLINT = {Awaken.id("glint_0"), Awaken.id("glint_1")};
    private static final Identifier SPARK = Awaken.id("fault_spark");
    private static final Identifier[] DUST = {Awaken.id("dust_0"), Awaken.id("dust_1")};
    private static final MoteParticle[] SLOTS = new MoteParticle[CAP];
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
    private static final RandomSource RNG = RandomSource.create();
    private static @Nullable ClientLevel slotLevel;

    static long particleNanos, extractNanos;

    /** Tries one spot (bx, bz) for a mote of this style; true when one was born. */
    static boolean spawn(Minecraft mc, ClientLevel level, int bx, int bz, Vec3 cam, float night, float budget, AirTable.MoteStyle style) {
        if (level != slotLevel) {
            java.util.Arrays.fill(SLOTS, null);
            slotLevel = level;
        }
        int free = -1, alive = 0;
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] != null && SLOTS[i].isAlive()) alive++;
            else {
                SLOTS[i] = null;
                if (free < 0) free = i;
            }
        }
        if (free < 0 || alive >= Math.max(8, (int) (CAP * budget))) return false;
        MoteParticle p = switch (style.kind()) {
            case "glint" -> glint(mc, level, bx, bz, cam, night, style);
            case "fault" -> RNG.nextFloat() < night ? spark(mc, level, bx, bz, style) : dust(mc, level, bx, bz, cam, night);
            default -> null;
        };
        if (p == null) return false;
        SLOTS[free] = p;
        mc.particleEngine.add(p);
        return true;
    }

    /** Sun on glass: a blink on the face of a glass block the camera sees, if the sun reaches it. */
    private static @Nullable MoteParticle glint(Minecraft mc, ClientLevel level, int bx, int bz, Vec3 cam, float night, AirTable.MoteStyle style) {
        if (RNG.nextFloat() < night) return null;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) - 1;
        if (top < cam.y - 24 || top > cam.y + 40) return null;
        BlockState s = level.getBlockState(POS.set(bx, top, bz));
        if (!s.is(GLINTS)) return null;
        double x, y, z;
        // a spire: somewhere up its side, on the face toward the camera
        int k = RNG.nextInt(Math.max(1, Math.min(12, top - Mth.floor(cam.y) + 12)));
        int sy = top - k;
        boolean side = k > 0 && level.getBlockState(POS.set(bx, sy, bz)).is(GLINTS);
        if (side) {
            double dx = cam.x - (bx + 0.5), dz = cam.z - (bz + 0.5);
            int ox = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? 1 : -1) : 0, oz = ox == 0 ? (dz > 0 ? 1 : -1) : 0;
            if (!level.getBlockState(POS.set(bx + ox, sy, bz + oz)).isAir()) return null;
            if (level.getBrightness(LightLayer.SKY, POS) < 12) return null;
            x = bx + 0.5 + ox * 0.52 + (oz != 0 ? RNG.nextFloat() - 0.5 : 0) * 0.9;
            y = sy + 0.1 + RNG.nextFloat() * 0.8;
            z = bz + 0.5 + oz * 0.52 + (ox != 0 ? RNG.nextFloat() - 0.5 : 0) * 0.9;
        } else {
            if (level.getBrightness(LightLayer.SKY, POS.set(bx, top + 1, bz)) < 12) return null;
            x = bx + 0.1 + RNG.nextFloat() * 0.8;
            y = top + 1.04;
            z = bz + 0.1 + RNG.nextFloat() * 0.8;
        }
        int rgb = style.color() < 0 ? 0xFFF4D6 : style.color();
        float size = 0.13F + RNG.nextFloat() * 0.14F;
        return new MoteParticle(level, x, y, z, sprite(mc, GLINT[RNG.nextInt(GLINT.length)]), rgb, size, MoteParticle.GLINT);
    }

    /** A spark leaving a fault: looks round the spot (a fault is a few blocks wide) for faultstone or the seam. */
    private static @Nullable MoteParticle spark(Minecraft mc, ClientLevel level, int bx, int bz, AirTable.MoteStyle style) {
        for (int k = 0; k < 9; k++) {
            int x = bx + (k % 3 - 1) * 2, z = bz + (k / 3 - 1) * 2;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
            if (!level.getBlockState(POS.set(x, top, z)).is(FAULTS)) continue;
            int rgb = RNG.nextFloat() < 0.6F ? (style.color() < 0 ? 0x8FF6FF : style.color()) : 0xB89CFF;
            float size = 0.05F + RNG.nextFloat() * 0.06F;
            return new MoteParticle(level, x + 0.15 + RNG.nextFloat() * 0.7, top + 1.05, z + 0.15 + RNG.nextFloat() * 0.7,
                    sprite(mc, SPARK), rgb, size, MoteParticle.SPARK);
        }
        return null;
    }

    /** A grain of dust riding the wind a little over the ground, nearer the camera than leaves fall. */
    private static @Nullable MoteParticle dust(Minecraft mc, ClientLevel level, int bx, int bz, Vec3 cam, float night) {
        if (RNG.nextFloat() < 0.5F) return null; // dust is thinner than sparks
        double x = cam.x + (bx + RNG.nextFloat() - cam.x) * 0.6, z = cam.z + (bz + RNG.nextFloat() - cam.z) * 0.6;
        int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
        if (gy < cam.y - 16 || gy > cam.y + 16) return null;
        int shade = 0xA9A3B8;
        float size = 0.04F + RNG.nextFloat() * 0.05F;
        return new MoteParticle(level, x, gy + 0.3 + RNG.nextFloat() * 3.0, z, sprite(mc, DUST[RNG.nextInt(DUST.length)]), shade, size, MoteParticle.DUST);
    }

    private static TextureAtlasSprite sprite(Minecraft mc, Identifier id) {
        return mc.getAtlasManager().getAtlasOrThrow(AtlasIds.PARTICLES).getSprite(id);
    }

    static int alive() {
        int n = 0;
        for (MoteParticle p : SLOTS) if (p != null && p.isAlive()) n++;
        return n;
    }

    private static long lastStatus = System.nanoTime();

    /** Alive now, and the CPU the motes took since the last status line (microseconds a second). */
    static String status() {
        long now = System.nanoTime();
        double secs = Math.max(0.001, (now - lastStatus) / 1e9);
        String line = String.format(java.util.Locale.ROOT, "motes %d alive (cap %d), cpu %.0f us/s moving + %.0f us/s drawing", alive(), CAP,
                particleNanos / 1e3 / secs, extractNanos / 1e3 / secs);
        particleNanos = extractNanos = 0;
        lastStatus = now;
        return line;
    }
}
