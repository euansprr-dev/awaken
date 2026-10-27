package dev.awaken.atlas.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.Awaken;
import dev.awaken.atlas.AtlasData;
import dev.awaken.atlas.Explored;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.zip.Inflater;

/**
 * What each piece of the land you know belongs to, so the table can colour it and draw where one part ends and the
 * next begins. Inside Carillon a column belongs to its district, block by block from the city plan's raster; outside,
 * a 16×16 cell belongs to its Living Wilds region (the server names it per explored cell). A zone is an int: 0 none,
 * 1..254 a region (1-based into the known regions), {@link #DISTRICT} + id a district. Regions are coloured by danger
 * (their levels), districts each by their own colour. The mesh worker reads a {@link View}, a snapshot.
 */
final class ZoneMap {
    static final int DISTRICT = 1000;
    /** Land ranks E, D, C, B, A, S (SPEC §21.10: 1, 10, 25, 45, 70, 100), safe green to deep violet. */
    static final int[] BAND_COLOUR = {0xFF62D99A, 0xFFE4D063, 0xFFF29A48, 0xFFEC5656, 0xFFB07CFF, 0xFF6C47C6};
    static final String[] BAND_NAME = {"Lv 1–9", "Lv 10–24", "Lv 25–44", "Lv 45–69", "Lv 70–99", "Lv 100+"};
    /** District colours, by the plan's district id (1 market … 7 wallring). */
    static final int[] DISTRICT_COLOUR = {0, 0xFFE8A85E, 0xFF7E9BFF, 0xFF4FD2C6, 0xFFF4E4A2, 0xFFFF7E52, 0xFF86E06E, 0xFFB4BCC8};
    static final String[] DISTRICT_NAME = {"", "Choir Square and the Market", "Bellwatch", "Lantern Quay", "High Terrace", "The Works",
            "Garden Ward", "The Wallring"};

    /**
     * A region's colour: its rank's, in one of three shades picked by the region (lighter, as is, deeper), so
     * two neighbours in the same band still read as two lands, not one.
     */
    static int regionColour(AtlasData.Region r) {
        int c = BAND_COLOUR[band((r.low() + r.high()) / 2)];
        int shade = (int) Math.floorMod(r.id() * 0x9E3779B97F4A7C15L >>> 40, 3L);
        return shade == 0 ? AtlasMesh.mix(c, 0xFFFFFFFF, 0.30f) : shade == 1 ? c : AtlasMesh.mix(c, 0xFF000000, 0.28f);
    }

    /** A land's rank index by its level (a region: the middle of what it spans). */
    static int band(int level) {
        return dev.awaken.living.region.RegionMap.rankOf(level);
    }

    /** An immutable picture of the zones for the worker. */
    record View(Long2ObjectOpenHashMap<byte[]> cells, byte[] city, int cityX0, int cityZ0, int[] regionColour) {
        int zone(int bx, int bz) {
            if (city != null) {
                int dx = bx - cityX0, dz = bz - cityZ0;
                if (dx >= 0 && dz >= 0 && dx < CITY && dz < CITY) {
                    int d = city[dz * CITY + dx];
                    if (d > 0) return DISTRICT + d;
                }
            }
            int cx = bx >> 4, cz = bz >> 4;
            byte[] t = cells.get(Explored.key(cx >> Explored.TILE_SHIFT, cz >> Explored.TILE_SHIFT));
            if (t == null || t.length == 0) return 0;
            return t[((cz & (Explored.TILE - 1)) << Explored.TILE_SHIFT) | (cx & (Explored.TILE - 1))] & 0xFF;
        }

        int colour(int zone) {
            if (zone >= DISTRICT) return DISTRICT_COLOUR[Math.min(DISTRICT_COLOUR.length - 1, zone - DISTRICT)];
            return zone > 0 && zone < regionColour.length ? regionColour[zone] : 0;
        }
    }

    static final int CITY = 512;
    private final Long2ObjectOpenHashMap<byte[]> cells = new Long2ObjectOpenHashMap<>();
    private static byte[] cityRaster;
    private boolean cityPresent;
    private int cityX0, cityZ0;
    private int version, viewVersion = -1;
    private View view = new View(new Long2ObjectOpenHashMap<>(), null, 0, 0, new int[1]);

    void put(int tx, int tz, byte[] zones) {
        if (zones.length == 0) return;
        cells.put(Explored.key(tx, tz), zones);
        version++;
    }

    void clear() {
        cells.clear();
        version++;
    }

    /** Carillon's square in this world, or none. */
    void city(boolean present, int x, int z) {
        cityPresent = present && raster() != null;
        cityX0 = x - CITY / 2;
        cityZ0 = z - CITY / 2;
        version++;
    }

    /** Bumps the picture when the known regions change (their colours). */
    void regionsChanged() {
        version++;
    }

    int version() {
        return version;
    }

    /** The current picture (rebuilt only after a change). Main thread. */
    View view(List<AtlasData.Region> regions) {
        if (viewVersion != version) {
            viewVersion = version;
            int[] colours = new int[regions.size() + 1];
            for (int i = 0; i < regions.size(); i++) colours[i + 1] = regionColour(regions.get(i));
            Long2ObjectOpenHashMap<byte[]> copy = new Long2ObjectOpenHashMap<>(cells.size());
            for (var e : cells.long2ObjectEntrySet()) copy.put(e.getLongKey(), e.getValue());
            view = new View(copy, cityPresent ? cityRaster : null, cityX0, cityZ0, colours);
        }
        return view;
    }

    /** The plan's district raster (512×512, z then x, from −256), read once; null without the city's plan. */
    private static byte[] raster() {
        if (cityRaster != null) return cityRaster;
        try (InputStream in = ZoneMap.class.getResourceAsStream("/data/awaken/city/plan.json")) {
            if (in == null) return null;
            JsonObject r = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("raster");
            if (r.get("size").getAsInt() != CITY || r.get("origin").getAsInt() != -CITY / 2) return null;
            Inflater inf = new Inflater();
            inf.setInput(Base64.getDecoder().decode(r.get("district").getAsString()));
            byte[] out = new byte[CITY * CITY];
            int n = 0;
            while (n < out.length && !inf.finished()) n += inf.inflate(out, n, out.length - n);
            inf.end();
            if (n == out.length) cityRaster = out;
        } catch (Exception e) {
            Awaken.LOG.warn("[atlas] couldn't read the city plan's districts: {}", e.toString());
        }
        return cityRaster;
    }
}
