package dev.awaken.terrain.client;

import dev.awaken.Awaken;
import dev.awaken.terrain.Wind;
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
 * grains of dust ride the wind low over the rock;</li>
 * <li>{@code frost} (Frost Spires): snow blown sideways on the wind, day and night, thicker in gusts; at night cold
 * glints wake on the frostspar ({@code #awaken:lands/frost_glints});</li>
 * <li>{@code mist} (Mirror Marsh): soft wisps lying low on still water, most at night and dawn.</li>
 * </ul>
 * Held in fixed slots (at most {@link #CAP} alive, times the leaves' frame-budget scale); nothing allocated per tick but
 * the particles.
 */
final class LandsMotes {
    private LandsMotes() {}

    static final int CAP = 120;
    static final TagKey<Block> GLINTS = TagKey.create(Registries.BLOCK, Awaken.id("lands/glints"));
    static final TagKey<Block> FAULTS = TagKey.create(Registries.BLOCK, Awaken.id("lands/faults"));
    /** What glints up a spire's side (a sunshard spire); everything else of #GLINTS glints on its top. */
    private static final TagKey<Block> SPIRE = TagKey.create(Registries.BLOCK, Awaken.id("lands/glint_spires"));
    /** What wakes with cold glints at night (frostspar, its spires and slivers). */
    static final TagKey<Block> FROST_GLINTS = TagKey.create(Registries.BLOCK, Awaken.id("lands/frost_glints"));
    private static final Identifier[] GLINT = {Awaken.id("glint_0"), Awaken.id("glint_1")};
    private static final Identifier SPARK = Awaken.id("fault_spark");
    private static final Identifier[] DUST = {Awaken.id("dust_0"), Awaken.id("dust_1")};
    private static final Identifier[] SNOW = {Awaken.id("snow_0"), Awaken.id("snow_1")};
    private static final Identifier[] MIST = {Awaken.id("mist_0"), Awaken.id("mist_1")};
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
            case "glint" -> RNG.nextFloat() < night ? null : glint(mc, level, bx, bz, cam, GLINTS, SPIRE, style.color() < 0 ? 0xFFF4D6 : style.color());
            case "fault" -> RNG.nextFloat() < night ? spark(mc, level, bx, bz, style) : dust(mc, level, bx, bz, cam, night);
            case "frost" -> RNG.nextFloat() < night * 0.3F ? glint(mc, level, bx, bz, cam, FROST_GLINTS, FROST_GLINTS, 0xBFF4FF) : snow(mc, level, bx, bz, cam, style);
            case "mist" -> RNG.nextFloat() < 0.35F + 0.65F * night ? mist(mc, level, bx, bz, cam, style) : null;
            default -> null;
        };
        if (p == null) return false;
        SLOTS[free] = p;
        mc.particleEngine.add(p);
        return true;
    }

    /**
     * A blink of light on a block of {@code on} the camera sees, where the sky reaches it: up the side of a spire (two
     * of {@code spire} stacked) or on top of the ground. Sun on glass by day; cold glints on frostspar at night.
     */
    private static @Nullable MoteParticle glint(Minecraft mc, ClientLevel level, int bx, int bz, Vec3 cam, TagKey<Block> on, TagKey<Block> spireTag, int rgb) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) - 1;
        if (top < cam.y - 24 || top > cam.y + 40) return null;
        BlockState s = level.getBlockState(POS.set(bx, top, bz));
        if (!s.is(on)) return null;
        double x, y, z;
        boolean spire = s.is(spireTag) && level.getBlockState(POS.set(bx, top - 1, bz)).is(spireTag);
        int sy = spire ? top - RNG.nextInt(Math.max(1, Math.min(14, top - Mth.floor(cam.y) + 14))) : top;
        if (spire && !level.getBlockState(POS.set(bx, sy, bz)).is(spireTag)) return null;
        if (spire && RNG.nextBoolean()) {
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

    /** A fleck of snow blown sideways: born upwind of the spot, low over the ground, so it streams past the camera. */
    private static @Nullable MoteParticle snow(Minecraft mc, ClientLevel level, int bx, int bz, Vec3 cam, AirTable.MoteStyle style) {
        Wind.Field wind = ClientWind.field();
        float strength = wind == null ? 0.3F : wind.strength(bx + 0.5, bz + 0.5);
        if (RNG.nextFloat() > 0.3F + 0.7F * strength) return null; // thicker in the gusts
        double x = cam.x + (bx + RNG.nextFloat() - cam.x) * 0.5, z = cam.z + (bz + RNG.nextFloat() - cam.z) * 0.5;
        if (wind != null) {
            x -= wind.dirX * 6;
            z -= wind.dirZ * 6;
        }
        int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
        if (gy < cam.y - 16 || gy > cam.y + 16) return null;
        int rgb = style.color() < 0 ? 0xF4FBFF : style.color();
        float size = 0.03F + RNG.nextFloat() * 0.045F;
        return new MoteParticle(level, x, gy + 0.2 + RNG.nextFloat() * RNG.nextFloat() * 5.0, z, sprite(mc, SNOW[RNG.nextInt(SNOW.length)]), rgb, size,
                MoteParticle.SNOW);
    }

    /** A wisp of mist on open water near the spot, lying just over the surface. */
    private static @Nullable MoteParticle mist(Minecraft mc, ClientLevel level, int bx, int bz, Vec3 cam, AirTable.MoteStyle style) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) - 1;
        if (top < cam.y - 20 || top > cam.y + 12 || !level.getFluidState(POS.set(bx, top, bz)).isSource()) return null;
        int rgb = style.color() < 0 ? 0xC8D6CF : style.color();
        float size = 1.0F + RNG.nextFloat() * 1.2F;
        return new MoteParticle(level, bx + RNG.nextFloat(), top + 1.25 + RNG.nextFloat() * 0.6, bz + RNG.nextFloat(), sprite(mc, MIST[RNG.nextInt(MIST.length)]), rgb,
                size, MoteParticle.MIST);
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
