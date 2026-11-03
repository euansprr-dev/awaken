package dev.awaken.arms.anvil;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.Awaken;
import dev.awaken.arms.ArmsArmoury;
import dev.awaken.arms.net.ArmsPayloads.NeedRow;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What every forged weapon and Hunter blade takes at the War Anvil (SPEC §46.7): {@code data/awaken/arms/anvil_costs.json},
 * written by the weapon generator from the same shapes the grid recipes were, so each is its old recipe minus the
 * Blueprint. Read once from the jar, the same on both sides (JEI reads it too).
 */
public final class AnvilCosts {
    private AnvilCosts() {}

    /** One part: a tag ("tag", "awaken:hunter_core_d") or an item ("item", "awaken:beastbound_blade"), and how many. */
    public record Part(String kind, String id, int n) {}

    private static @Nullable Map<String, List<Part>> table;

    /** Every weapon and blade with its parts (ids without the namespace: "beastbound_daggers"). */
    public static synchronized Map<String, List<Part>> table() {
        if (table == null) table = load();
        return table;
    }

    public static @Nullable List<Part> of(String id) {
        return table().get(id);
    }

    private static Map<String, List<Part>> load() {
        Map<String, List<Part>> out = new LinkedHashMap<>();
        try (InputStream in = AnvilCosts.class.getResourceAsStream("/data/awaken/arms/anvil_costs.json")) {
            if (in == null) return Map.of();
            JsonObject costs = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("costs");
            for (var e : costs.entrySet()) {
                List<Part> parts = new ArrayList<>();
                for (JsonElement row : e.getValue().getAsJsonArray()) {
                    String token = row.getAsJsonArray().get(0).getAsString();
                    int n = row.getAsJsonArray().get(1).getAsInt();
                    parts.add(token.startsWith("#") ? new Part("tag", token.substring(1), n) : new Part("item", token, n));
                }
                out.put(e.getKey(), List.copyOf(parts));
            }
        } catch (IOException | RuntimeException e) {
            Awaken.LOG.error("[arms] the War Anvil's cost table can't be read", e);
        }
        return Map.copyOf(out);
    }

    /** Whether a part is a Hunter blade (a weapon's blade stands in for the blade's own parts). */
    public static boolean blade(Part part) {
        return part.kind().equals("item") && part.id().startsWith(Awaken.MODID + ":") && part.id().endsWith("_blade")
                && table().containsKey(part.id().substring(Awaken.MODID.length() + 1));
    }

    /**
     * What it takes now and how much of each the player holds: the table's parts, with each blade the player doesn't carry
     * made from its own parts instead (added in), so the anvil never asks for a blade you'd have to forge first.
     */
    public static List<NeedRow> needs(ServerPlayer p, String id) {
        List<Part> parts = of(id);
        if (parts == null) return List.of();
        Map<String, NeedRow> out = new LinkedHashMap<>();
        List<Part> extra = new ArrayList<>();
        for (Part part : parts) {
            int have = ArmsArmoury.have(p, part.kind(), part.id());
            if (blade(part) && have < part.n()) {
                if (have > 0) out.put(part.kind() + part.id(), new NeedRow(part.kind(), part.id(), have, have));
                List<Part> own = of(part.id().substring(Awaken.MODID.length() + 1));
                if (own != null) for (Part q : own) extra.add(new Part(q.kind(), q.id(), q.n() * (part.n() - have)));
                continue;
            }
            out.put(part.kind() + part.id(), new NeedRow(part.kind(), part.id(), part.n(), have));
        }
        for (Part q : extra) {
            out.merge(q.kind() + q.id(), new NeedRow(q.kind(), q.id(), q.n(), ArmsArmoury.have(p, q.kind(), q.id())),
                    (a, b) -> new NeedRow(a.kind(), a.id(), a.need() + b.need(), a.have()));
        }
        return List.copyOf(out.values());
    }
}
