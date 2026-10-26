package dev.awaken.citizens;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.Awaken;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What people say ({@code data/awaken/city/citizen_lines.json}, authored in tools/city/lines.py): greetings by job and
 * tier, the named four's own voices, street calls, the crier's news and event shouts. Picked with a seeded hash, never
 * at random, and filtered by the hour's time tag ([m] [d] [e] [n]).
 */
public final class Lines {
    static final String RESOURCE = "/data/awaken/city/citizen_lines.json";
    /** (SPEC §28.6) the villages' pools, read under the prefix "village." (village.greet.farmer.friend, village.inn.rumour). */
    static final String VILLAGE = "/data/awaken/village/village_lines.json";
    static final String[] TIERS = {"stranger", "familiar", "known"};

    private final Map<String, List<String>> pools = new HashMap<>();
    private static Lines instance;

    public static synchronized Lines get() {
        if (instance == null) instance = new Lines();
        return instance;
    }

    private Lines() {
        try (InputStream in = Lines.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                Awaken.LOG.warn("[citizens] no {}: citizens say nothing", RESOURCE);
                return;
            }
            JsonObject o = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            flatten("", o, pools);
        } catch (Exception e) {
            Awaken.LOG.error("[citizens] cannot read {}", RESOURCE, e);
        }
        try (InputStream in = Lines.class.getResourceAsStream(VILLAGE)) {
            if (in != null) flatten("village", JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject(), pools);
        } catch (Exception e) {
            Awaken.LOG.error("[citizens] cannot read {}", VILLAGE, e);
        }
    }

    /** Every array in the file under its dotted path (greet.watch.known, calls.stallkeeper.bread, news.city, event.thief). */
    private static void flatten(String prefix, JsonObject o, Map<String, List<String>> out) {
        for (var e : o.entrySet()) {
            String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            JsonElement v = e.getValue();
            if (v.isJsonObject()) flatten(key, v.getAsJsonObject(), out);
            else if (v.isJsonArray()) {
                List<String> l = new ArrayList<>();
                for (JsonElement s : (JsonArray) v) l.add(s.getAsString());
                out.put(key, l);
            }
        }
    }

    List<String> pool(String key) {
        return pools.getOrDefault(key, List.of());
    }

    /** The part of the day a time tag names: m 06-11, d 11-17, e 17-21, n 21-06. */
    static char part(int tod) {
        if (tod < 5000) return 'm';
        if (tod < 11000) return 'd';
        if (tod < 15000) return 'e';
        return 'n';
    }

    /** A seeded line from the pools that fits this hour ("" when none). */
    public String pick(long seed, int tod, String... keys) {
        char part = part(tod);
        int total = 0;
        for (String k : keys) for (String s : pool(k)) if (fits(s, part)) total++;
        if (total == 0) return "";
        int n = (int) Math.floorMod(CitizenWork.mixHash(seed), (long) total);
        for (String k : keys) {
            for (String s : pool(k)) {
                if (!fits(s, part)) continue;
                if (n-- == 0) return strip(s);
            }
        }
        return "";
    }

    private static boolean fits(String s, char part) {
        return s.length() < 3 || s.charAt(0) != '[' || s.charAt(2) != ']' || s.charAt(1) == part;
    }

    private static String strip(String s) {
        return s.length() >= 3 && s.charAt(0) == '[' && s.charAt(2) == ']' ? s.substring(3) : s;
    }

    public static String fill(String line, String name, String district, String gate, int hour) {
        return line.replace("{name}", name).replace("{district}", district).replace("{gate}", gate)
                .replace("{hour}", Integer.toString(hour));
    }
}
