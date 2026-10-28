package dev.awaken.citizens;

import dev.awaken.Awaken;
import dev.awaken.city.CityPlan;
import dev.awaken.core.api.CityHooks;
import dev.awaken.core.audio.Intensity;
import dev.awaken.core.audio.MusicLayers;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The city's beds on the core audio's ambience slot (M4 B3b, SPEC §16.0 decision 28): a player inside the wall hears
 * their district's bed by day ({@code awaken:city/<district>}, assets/awaken/music_layers/city/) and the city's night
 * bed after dark ({@code awaken:city/night}), the slot crossfading as they walk from one district into the next. The
 * level is the crowd: how many of the district's citizens are outdoors now against its busiest hour (the ledger,
 * counted every 100 ticks), one lower in the rain; at night, the taverns' hours. The places that sound (fountains,
 * quays, forges, taverns, the carillon) are the client's ({@code citizens.client.CitySound}). A player whose client runs
 * AmbientSounds (it plays birds and crickets for real wherever there's greenery) hears the same beds without our
 * synthetic birds and crickets: {@code awaken:city/as/<district>}, {@code awaken:city/as/night}.
 */
public final class Soundscape {
    private Soundscape() {}

    /** The districts' keys by plan id (1 market .. 7 wallring); their outdoor count at the busiest hour (roster, B3a). */
    static final String[] KEYS = {"wallring", "market", "bellwatch", "quay", "terrace", "works", "garden", "wallring"};
    static final int[] PEAK = {21, 169, 42, 55, 21, 41, 20, 21};
    private static final Identifier[] DAY = new Identifier[KEYS.length], DAY_AS = new Identifier[KEYS.length];
    private static final Identifier NIGHT = Awaken.id("city/night"), NIGHT_AS = Awaken.id("city/as/night");

    static {
        for (int i = 0; i < KEYS.length; i++) {
            DAY[i] = Awaken.id("city/" + KEYS[i]);
            DAY_AS[i] = Awaken.id("city/as/" + KEYS[i]);
        }
    }

    /** Players whose clients run AmbientSounds (told at login, CitizensNet.Ears). */
    private static final java.util.Set<java.util.UUID> NATURE = new java.util.HashSet<>();

    /** Citizens outdoors in each district (plan id), as last counted. */
    static final int[] OUTDOORS = new int[KEYS.length];
    private static final double[] POS = new double[3];

    static void register() {
        MusicLayers.register(MusicLayers.Slot.AMBIENCE, 100, "carillon", Soundscape::context); // SPEC Plan v2 · audio: the city's reserved ids
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) -> NATURE.remove(e.getEntity().getUUID()));
    }

    /** Whether a player's client plays AmbientSounds' birds and crickets (W3's village beds leave ours out for them). */
    public static boolean nature(ServerPlayer p) {
        return NATURE.contains(p.getUUID());
    }

    static void hears(ServerPlayer p, boolean ambientSounds) {
        if (ambientSounds) NATURE.add(p.getUUID());
        else NATURE.remove(p.getUUID());
    }

    /** The bed a player hears for a district (plan id), or the night bed: with or without our birds and crickets. */
    static Identifier bed(boolean nature, int district, boolean night) {
        if (night) return nature ? NIGHT_AS : NIGHT;
        return (nature ? DAY_AS : DAY)[district];
    }

    /** Every 100 ticks: where the ledger's people stand, by district (no allocation: the plan's raster). */
    static void count(Settlement c, long now) {
        if (now % 100 != 7) return;
        java.util.Arrays.fill(OUTDOORS, 0);
        CityPlan plan = CityPlan.get();
        Ledger l = c.ledger;
        for (int i = 0; i < l.n; i++) {
            if (!l.position(i, POS)) continue;
            int d = plan.district((int) Math.floor(POS[0]) - c.work.cx, (int) Math.floor(POS[2]) - c.work.cz);
            if (d >= 0 && d < OUTDOORS.length) OUTDOORS[d]++;
        }
    }

    /** The level a district's crowd makes: quiet, light, busy, packed. */
    static int level(int district, int outdoors) {
        float f = outdoors / (float) Math.max(1, PEAK[Math.max(0, Math.min(PEAK.length - 1, district))]);
        return f < 0.15F ? Intensity.CALM : f < 0.45F ? Intensity.TENSION : f < 0.8F ? Intensity.COMBAT : Intensity.LORD;
    }

    /** The night bed's level: the taverns full in the evening, quieter toward midnight, asleep after. */
    static int nightLevel(int tod) {
        if (tod >= 12500 && tod < 14500) return Intensity.COMBAT;
        if (tod >= 14500 && tod < 16500) return Intensity.TENSION;
        return Intensity.CALM;
    }

    static boolean night(int tod) {
        return tod >= 12500 && tod < 23300;
    }

    private static @Nullable Identifier context(ServerPlayer p, Intensity.Signals s) {
        Settlement c = Citizens.carillon();
        if (c == null || p.level() != c.level || !CityHooks.inCity(p.level(), p.blockPosition())) return null;
        int tod = (int) Math.floorMod(p.level().getOverworldClockTime(), 24000L);
        boolean nature = NATURE.contains(p.getUUID());
        if (night(tod)) {
            s.forced = p.level().canSeeSky(p.blockPosition().above()) ? nightLevel(tod) : Intensity.CALM;
            return bed(nature, 0, true);
        }
        int d = CityPlan.get().district(p.getBlockX() - c.work.cx, p.getBlockZ() - c.work.cz);
        if (d < 0 || d >= KEYS.length) d = 0;
        int lv = level(d, OUTDOORS[d]);
        if (p.level().isRainingAt(p.blockPosition().above())) lv = Math.max(Intensity.CALM, lv - 1);
        s.forced = p.level().canSeeSky(p.blockPosition().above()) ? lv : Intensity.CALM;
        return bed(nature, d, false);
    }
}
