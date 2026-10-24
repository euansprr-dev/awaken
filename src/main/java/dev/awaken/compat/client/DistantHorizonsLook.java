package dev.awaken.compat.client;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.config.EDhApiHorizontalQuality;
import com.seibel.distanthorizons.api.enums.config.EDhApiMaxHorizontalResolution;
import com.seibel.distanthorizons.api.enums.config.EDhApiVerticalQuality;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import com.seibel.distanthorizons.api.interfaces.config.client.IDhApiGraphicsConfig;
import dev.awaken.compat.Look;
import dev.awaken.compat.Look.DhKey;
import dev.awaken.compat.Look.DhSetting;
import org.jspecify.annotations.Nullable;

/**
 * The Look switch's hand on Distant Horizons 3.3.4, through its public config API ({@code DhApi.Delayed.configs}).
 * An API value takes effect at once and is never saved by DH; while one is set, DH's own menu shows that setting as
 * "API LOCK" with our name. So the switch also writes the same keys into DH's file ({@link LookSwitch}) and never sets
 * them through the API again after a restart: from then on they are plain settings in DH's own menu. Loaded only
 * when Distant Horizons is present.
 */
final class DistantHorizonsLook {
    private DistantHorizonsLook() {}

    /** How DH's menu names who holds a setting. */
    static final String WHO = "AWAKEN Look";

    /** Sets the tier's live keys now; -1 when DH's config API isn't up yet, else how many it took. */
    static int applyLive(Look.Tier tier) {
        if (DhApi.Delayed.configs == null) return -1;
        IDhApiGraphicsConfig g = DhApi.Delayed.configs.graphics();
        int n = 0;
        for (DhSetting s : Look.dh(tier)) {
            if (s.key().live() && set(g, s)) n++;
        }
        return n;
    }

    private static boolean set(IDhApiGraphicsConfig g, DhSetting s) {
        DhKey k = s.key();
        Object v = s.value();
        if (k == Look.RADIUS) return g.chunkRenderDistance().setValue((Integer) v, WHO);
        if (k == Look.MAX_RESOLUTION) return g.maxHorizontalResolution().setValue(EDhApiMaxHorizontalResolution.valueOf((String) v), WHO);
        if (k == Look.VERTICAL_QUALITY) return g.verticalQuality().setValue(EDhApiVerticalQuality.valueOf((String) v), WHO);
        if (k == Look.HORIZONTAL_QUALITY) return g.horizontalQuality().setValue(EDhApiHorizontalQuality.valueOf((String) v), WHO);
        if (k == Look.SSAO) return g.ambientOcclusion().enabled().setValue((Boolean) v, WHO);
        if (k == Look.TEXTURED_LODS) return g.texture().texturesEnabled().setValue((Boolean) v, WHO);
        return false;
    }

    private static @Nullable IDhApiConfigValue<?> value(DhKey k) {
        if (DhApi.Delayed.configs == null) return null;
        IDhApiGraphicsConfig g = DhApi.Delayed.configs.graphics();
        if (k == Look.RADIUS) return g.chunkRenderDistance();
        if (k == Look.MAX_RESOLUTION) return g.maxHorizontalResolution();
        if (k == Look.VERTICAL_QUALITY) return g.verticalQuality();
        if (k == Look.HORIZONTAL_QUALITY) return g.horizontalQuality();
        if (k == Look.SSAO) return g.ambientOcclusion().enabled();
        if (k == Look.TEXTURED_LODS) return g.texture().texturesEnabled();
        return null;
    }

    /** DH's value of an owned live key as {@link DhSetting#value()} types (enums by name), or null. */
    static @Nullable Object current(DhKey k) {
        IDhApiConfigValue<?> v = value(k);
        if (v == null) return null;
        Object o = v.getValue();
        return o instanceof Enum<?> e ? e.name() : o;
    }

    /** The far-terrain radius DH draws now, in chunks; 0 when its config API isn't up. */
    static int radius() {
        Object r = current(Look.RADIUS);
        return r instanceof Integer i ? i : 0;
    }
}
