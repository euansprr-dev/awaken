package dev.awaken.delve.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;

/**
 * Where the listener is under the ground, for the senses (SPEC §28.5): read once a tick from the camera and shared by
 * {@link CaveSound}, {@link DeepHum} and {@link CaveLook}. How far under the rock ({@link #under}: no sky over the
 * head, the land itself 3 to 12 blocks above it (not leaves or a roof: under a house or a town the land under what's
 * built), and daylight's sky light not reaching in: full at sky light 4 or less, none at 10), how deep ({@link #depth} for
 * the cave's sound, {@link #below} for the deep's hum), and which cave biome. Client thread only; nothing allocated.
 */
final class SenseState {
    private SenseState() {}

    static final int PLAIN = 0, LUSH = 1, DRIPSTONE = 2, DEEP_DARK = 3, SULFUR = 4, REGION = 5;
    static final String[] BIOME_NAMES = {"a plain cave", "lush caves", "dripstone caves", "the deep dark", "sulfur caves", "a cave region"};
    /** The Living Deep's cave regions (SPEC §42.7): cave biomes with their own looks, so the plain cave's look stays off. */
    static final TagKey<Biome> REGIONS = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("awaken", "caves/regions"));

    /** A player in the overworld (the senses are the overworld's caves; the Descent has its own). */
    static boolean active;
    static double x, y, z;
    /** 0 under open sky .. 1 well under the rock, eased over about two and a half seconds. */
    static float under;
    /** 0 at y 48 .. 1 at y -64: the deeper, the fewer events and the lower the air. */
    static float depth;
    /** 0 at y 0 .. 1 at y -60: the deep's hum rising below y 0. */
    static float below;
    /** Sky light at the camera (0..15), daylight's reach whatever the hour. */
    static int sky;
    /** The camera's cave biome ({@link #PLAIN} when it is none), sampled every second, and the biome itself. */
    static int biome = PLAIN;
    static Holder<Biome> here;
    static boolean underwater;
    static long tick;
    private static boolean fresh = true;
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
    /** The Lands' own rock and soil (fused glass, faultstone, ...: the terrain module adds them for the carvers). */
    private static final TagKey<Block> CARVABLE = TagKey.create(Registries.BLOCK, Identifier.withDefaultNamespace("overworld_carver_replaceables"));

    static void update(Minecraft mc) {
        ClientLevel level = mc.level;
        active = level != null && mc.player != null && level.dimension() == Level.OVERWORLD;
        if (!active) {
            reset();
            return;
        }
        tick++;
        Camera cam = mc.gameRenderer.mainCamera();
        Vec3 c = cam.position();
        x = c.x;
        y = c.y;
        z = c.z;
        int bx = Mth.floor(x), by = Mth.floor(y), bz = Mth.floor(z);
        sky = level.getBrightness(LightLayer.SKY, POS.set(bx, by, bz));
        boolean open = level.canSeeSky(POS.set(bx, by + 1, bz));
        // a cave is under the ground, not under leaves or a roof: the land itself must be over you, under whatever's
        // built on it (a mine under a house, a cave under a town's roofs and streets)
        int land = land(level, bx, by, bz, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz));
        float cover = land - (float) y;
        boolean ground = land > by + 1;
        float goal = open || !ground ? 0F : Mth.clamp((cover - 3F) / 9F, 0F, 1F) * (1F - Mth.clamp((sky - 4F) / 6F, 0F, 1F));
        under = fresh ? goal : under + (goal - under) * 0.02F;
        depth = Mth.clamp((48F - (float) y) / 112F, 0F, 1F);
        below = Mth.clamp((float) -y / 60F, 0F, 1F);
        underwater = cam.getFluidInCamera() == FogType.WATER;
        if (fresh || tick % 20 == 0) biome = kind(here = level.getBiome(POS.set(bx, by, bz)));
        fresh = false;
    }

    /** The land's own blocks (stone, soil, sand, terracotta, the Lands' rock, snow, ice, water), not a roof or a wall. */
    static boolean natural(BlockState s) {
        return s.is(BlockTags.SCULK_REPLACEABLE) || s.is(CARVABLE) || s.is(BlockTags.SNOW) || s.is(BlockTags.ICE) || !s.getFluidState().isEmpty();
    }

    /**
     * The top of the land itself over the camera's column (the y over its highest natural block): down from the column's
     * top through anything built on it (roofs, floors, streets) to the first of the land's own blocks; {@code by} when
     * there is none over the camera (a house on the surface, a tree's branch overhead). Usually one look: the top is
     * the land.
     */
    private static int land(ClientLevel level, int bx, int by, int bz, int top) {
        for (int yy = top - 1; yy > by + 1; yy--) if (natural(level.getBlockState(POS.set(bx, yy, bz)))) return yy + 1;
        return by;
    }

    private static int kind(Holder<Biome> b) {
        if (b.is(Biomes.LUSH_CAVES)) return LUSH;
        if (b.is(Biomes.DRIPSTONE_CAVES)) return DRIPSTONE;
        if (b.is(Biomes.DEEP_DARK)) return DEEP_DARK;
        if (b.is(Biomes.SULFUR_CAVES)) return SULFUR;
        if (b.is(REGIONS)) return REGION;
        return PLAIN;
    }

    static void reset() {
        active = false;
        fresh = true;
        under = depth = below = 0F;
        sky = 15;
        biome = PLAIN;
        here = null;
        underwater = false;
    }
}
