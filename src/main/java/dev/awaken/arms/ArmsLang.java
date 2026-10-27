package dev.awaken.arms;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Our own English, readable on a server (a dedicated server has no resource packs, so a translatable component can't be
 * turned into words there): the merged lang file off the classpath, for the lines the Now line and the task card say
 * (docs/arms-progression/PLAN.md §3.5). The client still draws the same words through its own translations.
 */
public final class ArmsLang {
    private ArmsLang() {}

    private static final JsonObject LANG = load();

    private static JsonObject load() {
        try (InputStream in = ArmsLang.class.getResourceAsStream("/assets/awaken/lang/en_us.json")) {
            if (in == null) return new JsonObject();
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            return new JsonObject();
        }
    }

    /** A lang line, or null. */
    public static String get(String key) {
        var v = LANG.get(key);
        return v == null ? null : v.getAsString();
    }

    /** An item's name: ours from the lang file, anyone else's from its id ("rabbit_hide" reads "Rabbit Hide"). */
    public static String itemName(Identifier id) {
        String v = get("item." + id.getNamespace() + "." + id.getPath());
        return v != null ? v : pretty(id.getPath());
    }

    public static String pretty(String path) {
        StringBuilder out = new StringBuilder();
        for (String w : path.split("_")) {
            if (w.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }
}
