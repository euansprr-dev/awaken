package dev.awaken.delve.client;

import dev.awaken.core.audio.client.Soundscape;
import dev.awaken.delve.CaveStyle;
import dev.awaken.delve.api.Bands;
import dev.awaken.delve.sense.SenseModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * The deep's bands, felt (SPEC §34.5): which band you are in under the rock (the Shallows, the Middle Dark, the Deep),
 * a quiet caption the first time you enter each in a play-day (as a region's), the band's look over the plain cave's
 * ({@link CaveLook}), and the <b>cave air</b>: the game's own fog drawn in close underground (starting about 8 blocks
 * out, closing about 52), coloured by the band, thinner where you stand in light, so a far tunnel fades into coloured
 * dark instead of black. Only where "under" (eased), never under water or in lava, snow or blindness's own fog. Under a
 * styled land (SPEC §34.7) the air leans half way to that land's colour: green-brown under woods, amber under sand,
 * blue under snow, each crystal's own under the crystal lands. In a cave region (SPEC §42.7) the air is mostly the region's,
 * its biome's fog colour, and the region has its own caption ({@code biome.<id>} and {@code caves.region.<id>}) the first
 * time you enter it in a play-day, as a band's. Each band has its <b>room tone</b>: a recorded cave's air
 * round the listener, lower and fuller the deeper (pitch 1, 0.88, 0.76), under the Conductor's nature level, half in
 * the deep dark.
 */
final class Depths {
    private Depths() {}

    static final float AIR_NEAR = 8F, AIR_FAR = 52F, LIT_NEAR = 16F, LIT_FAR = 60F;
    /** How far the air's colour goes toward the band's (and how dark the band's is: air, not paint). */
    static final float TINT = 0.85F, TINT_DARK = 0.16F;

    /** The band the camera is in while under the rock (null: not under). */
    static Bands.Band band;
    /** The style of the land over the camera's column (read once a second while under). */
    static CaveStyle style = CaveStyle.NONE;
    static final float STYLE_MIX = 0.5F;
    private static final long[] CAPTIONED = new long[Bands.Band.values().length];
    /** The cave region the camera is in (its biome), its air colour (-1: none) and the day each was last captioned. */
    private static ResourceKey<Biome> region;
    static int regionAir = -1;
    static final float REGION_MIX = 0.75F;
    private static final java.util.Map<ResourceKey<Biome>, Long> REGION_CAPTIONED = new java.util.HashMap<>();
    private static float lit;
    /** The room tone's level and pitch by band (Shallows, Middle Dark, Deep). */
    static final float[] ROOM = {0.55F, 0.7F, 0.8F}, ROOM_PITCH = {1.0F, 0.88F, 0.76F};
    private static SenseLoop room;

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || !SenseState.active || SenseState.under < 0.5F) {
            band = null;
            room(mc, null);
            return;
        }
        Bands.Band b = Bands.of(Mth.floor(SenseState.y));
        long day = level.getOverworldClockTime() / 24000L + 1;
        if (b != band && CAPTIONED[b.ordinal()] != day) {
            CAPTIONED[b.ordinal()] = day;
            CaveHud.show(b.title, b.words);
        }
        band = b;
        room(mc, b);
        if (level.getGameTime() % 20 == 0) {
            int x = Mth.floor(SenseState.x), z = Mth.floor(SenseState.z);
            style = CaveStyle.of(level.getBiome(new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z)));
            region(SenseState.biome == SenseState.REGION ? SenseState.here : null, day);
        }
        lit = level.getBrightness(LightLayer.BLOCK, BlockPos.containing(SenseState.x, SenseState.y, SenseState.z)) / 15F;
    }

    /** A region entered: its caption once a play-day (over the band's), and its air from its biome's fog colour. */
    private static void region(Holder<Biome> here, long day) {
        ResourceKey<Biome> key = here == null ? null : here.unwrapKey().orElse(null);
        if (key != null && !key.equals(region) && REGION_CAPTIONED.getOrDefault(key, 0L) != day) {
            REGION_CAPTIONED.put(key, day);
            var id = key.identifier();
            CaveHud.show(I18n.get(id.toLanguageKey("biome")), I18n.get(id.toLanguageKey("caves.region")));
        }
        region = key;
        EnvironmentAttributeMap attrs = here == null ? null : here.value().getAttributes();
        regionAir = attrs == null || !attrs.contains(EnvironmentAttributes.FOG_COLOR) ? -1
                : rgb(attrs.applyModifier(EnvironmentAttributes.FOG_COLOR, EnvironmentAttributes.FOG_COLOR.defaultValue()));
    }

    private static int rgb(org.joml.Vector3fc v) {
        return Mth.clamp((int) (v.x() * 255F), 0, 255) << 16 | Mth.clamp((int) (v.y() * 255F), 0, 255) << 8 | Mth.clamp((int) (v.z() * 255F), 0, 255);
    }

    private static void room(Minecraft mc, Bands.Band b) {
        float goal = b == null || SenseState.underwater ? 0F : ROOM[b.ordinal()] * (float) Math.sqrt(SenseState.under)
                * Mth.clamp(Soundscape.natureLevel(), 0F, 1F) * (SenseState.biome == SenseState.DEEP_DARK ? 0.5F : 1F);
        if (room != null && (room.isStopped() || room.age > 40 && !mc.getSoundManager().isActive(room))) room = null;
        if (room == null) {
            if (goal < 0.01F) return;
            room = new SenseLoop(SenseModule.BAND_ROOM.get(), true, 0, 0, 0, ROOM_PITCH[b.ordinal()], 0.01F);
            mc.getSoundManager().play(room);
        }
        room.goal = goal;
        if (b != null) room.pitchGoal = ROOM_PITCH[b.ordinal()];
    }

    /** The band's look id for Lens's place layer 0 (a plain cave's look by band). */
    static String look() {
        if (band == null) return CaveLook.UNDERGROUND;
        return switch (band) {
            case SHALLOWS -> "awaken:shallows";
            case MIDDLE -> "awaken:middle_dark";
            case DEEP -> "awaken:deep";
        };
    }

    private static boolean air(FogType type) {
        return DelveClientConfig.caveAir() && SenseState.active && !SenseState.underwater && type == FogType.NONE && SenseState.under > 0.02F;
    }

    static void fog(ViewportEvent.RenderFog e) {
        if (!air(e.getType())) return;
        float u = SenseState.under;
        float near = Mth.lerp(lit, AIR_NEAR, LIT_NEAR), far = Mth.lerp(lit, AIR_FAR, LIT_FAR);
        float n0 = e.getNearPlaneDistance(), f0 = e.getFarPlaneDistance();
        float n = Mth.lerp(u, n0, Math.min(n0, near)), f = Mth.lerp(u, f0, Math.min(f0, far));
        e.setNearPlaneDistance(Math.min(n, f - 1F));
        e.setFarPlaneDistance(f);
    }

    static void fogColour(ViewportEvent.ComputeFogColor e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !air(mc.gameRenderer.mainCamera().getFluidInCamera())) return;
        int c = (band == null ? Bands.of(Mth.floor(SenseState.y)) : band).air;
        if (regionAir >= 0) c = mix(c, regionAir, REGION_MIX);
        else if (style != CaveStyle.NONE) c = mix(c, style.air, STYLE_MIX);
        float t = SenseState.under * TINT;
        e.setRed(Mth.lerp(t, e.getRed(), (c >> 16 & 255) / 255F * TINT_DARK));
        e.setGreen(Mth.lerp(t, e.getGreen(), (c >> 8 & 255) / 255F * TINT_DARK));
        e.setBlue(Mth.lerp(t, e.getBlue(), (c & 255) / 255F * TINT_DARK));
    }

    static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, a >> 16 & 255, b >> 16 & 255), g = (int) Mth.lerp(t, a >> 8 & 255, b >> 8 & 255), bl = (int) Mth.lerp(t, a & 255, b & 255);
        return r << 16 | g << 8 | bl;
    }

    static void reset() {
        if (room != null) room.end();
        room = null;
        band = null;
        style = CaveStyle.NONE;
        region = null;
        regionAir = -1;
        REGION_CAPTIONED.clear();
        java.util.Arrays.fill(CAPTIONED, 0L);
    }
}
