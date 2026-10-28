package dev.awaken.delve.client;

import dev.awaken.delve.api.Bands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * The deep's bands, felt (SPEC §34.5): which band you are in under the rock (the Shallows, the Middle Dark, the Deep),
 * a quiet caption the first time you enter each in a play-day (as a region's), the band's look over the plain cave's
 * ({@link CaveLook}), and the <b>cave air</b>: the game's own fog drawn in close underground (starting about 8 blocks
 * out, closing about 52), coloured by the band, thinner where you stand in light, so a far tunnel fades into coloured
 * dark instead of black. Only where "under" (eased), never under water or in lava, snow or blindness's own fog.
 */
final class Depths {
    private Depths() {}

    static final float AIR_NEAR = 8F, AIR_FAR = 52F, LIT_NEAR = 16F, LIT_FAR = 60F;
    /** How far the air's colour goes toward the band's (and how dark the band's is: air, not paint). */
    static final float TINT = 0.85F, TINT_DARK = 0.16F;

    /** The band the camera is in while under the rock (null: not under). */
    static Bands.Band band;
    private static final long[] CAPTIONED = new long[Bands.Band.values().length];
    private static float lit;

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || !SenseState.active || SenseState.under < 0.5F) {
            band = null;
            return;
        }
        Bands.Band b = Bands.of(Mth.floor(SenseState.y));
        long day = level.getOverworldClockTime() / 24000L + 1;
        if (b != band && CAPTIONED[b.ordinal()] != day) {
            CAPTIONED[b.ordinal()] = day;
            CaveHud.show(b.title, b.words);
        }
        band = b;
        lit = level.getBrightness(LightLayer.BLOCK, BlockPos.containing(SenseState.x, SenseState.y, SenseState.z)) / 15F;
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
        float t = SenseState.under * TINT;
        e.setRed(Mth.lerp(t, e.getRed(), (c >> 16 & 255) / 255F * TINT_DARK));
        e.setGreen(Mth.lerp(t, e.getGreen(), (c >> 8 & 255) / 255F * TINT_DARK));
        e.setBlue(Mth.lerp(t, e.getBlue(), (c & 255) / 255F * TINT_DARK));
    }

    static void reset() {
        band = null;
        java.util.Arrays.fill(CAPTIONED, 0L);
    }
}
