package dev.awaken.core.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.Awaken;
import dev.awaken.core.api.Sources;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Items;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The bar's machine part (SPEC §43.11, JOURNEY "Nothing without a way in"): every {@code awaken} item and block has a
 * way in (a recipe's result, an item in a loot table, a quest's relic, or a way declared in code through
 * {@link Sources}) or is on the reviewed never-held list. Spawn eggs are creative's own. A block with an item is its
 * item's question; a block without one needs a declared placement.
 */
final class JourneyLedgerTests {
    private JourneyLedgerTests() {}

    static void register() {
        LedgerSources.register();
        AwakenTests.register("journey_ledger", 40, JourneyLedgerTests::ledger);
    }

    static void ledger(GameTestHelper h) {
        ResourceManager rm = h.getLevel().getServer().getResourceManager();
        Set<String> in = new TreeSet<>();
        scan(rm, "recipe", (j, out) -> {
            if (j.isJsonObject() && j.getAsJsonObject().has("result")) ids(j.getAsJsonObject().get("result"), out);
        }, in);
        scan(rm, "loot_table", JourneyLedgerTests::lootItems, in);
        scan(rm, "awaken/quest", (j, out) -> {
            if (j.isJsonObject() && j.getAsJsonObject().has("relic")) ids(j.getAsJsonObject().get("relic"), out);
        }, in);
        Map<String, String> declared = Sources.declared(), never = Sources.neverHeld();
        List<String> none = new ArrayList<>();
        for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
            if (!id.getNamespace().equals(Awaken.MODID)) continue;
            String p = id.getPath();
            if (p.endsWith("_spawn_egg") || in.contains(p) || declared.containsKey(p) || never.containsKey(p)) continue;
            none.add("item " + p);
        }
        for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
            if (!id.getNamespace().equals(Awaken.MODID) || BuiltInRegistries.BLOCK.getValue(id).asItem() != Items.AIR) continue;
            String p = id.getPath();
            if (declared.containsKey(p) || never.containsKey(p)) continue;
            none.add("block " + p);
        }
        for (String p : declared.keySet())
            h.assertTrue(!never.containsKey(p), p + " is declared both a way in and never held");
        h.assertTrue(none.isEmpty(), none.size() + " with no way in (add a recipe, loot, or Sources.declare, or the never-held list): "
                + String.join(", ", none));
        h.succeed();
    }

    interface Reads {
        void read(JsonElement json, Set<String> out);
    }

    /** Every JSON under a data folder, in every namespace. */
    static void scan(ResourceManager rm, String folder, Reads reads, Set<String> out) {
        for (Map.Entry<Identifier, Resource> e : rm.listResources(folder, id -> id.getPath().endsWith(".json")).entrySet()) {
            try (Reader r = e.getValue().openAsReader()) {
                reads.read(JsonParser.parseReader(r), out);
            } catch (Exception ignored) {
                // a file that doesn't parse is the data's own test's to catch
            }
        }
    }

    /** "awaken:x" strings anywhere in a result (an id, an object with "id" or "item", a list). */
    static void ids(JsonElement j, Set<String> out) {
        if (j.isJsonPrimitive() && j.getAsJsonPrimitive().isString()) {
            String s = j.getAsString();
            if (s.startsWith(Awaken.MODID + ":")) out.add(s.substring(Awaken.MODID.length() + 1));
        } else if (j.isJsonArray()) {
            for (JsonElement e : j.getAsJsonArray()) ids(e, out);
        } else if (j.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : j.getAsJsonObject().entrySet()) ids(e.getValue(), out);
        }
    }

    /** A loot table's item entries' names, however deep. */
    static void lootItems(JsonElement j, Set<String> out) {
        if (j.isJsonArray()) {
            for (JsonElement e : j.getAsJsonArray()) lootItems(e, out);
        } else if (j.isJsonObject()) {
            JsonObject o = j.getAsJsonObject();
            if (o.has("type") && o.get("type").isJsonPrimitive() && o.get("type").getAsString().endsWith("item") && o.has("name")) ids(o.get("name"), out);
            for (Map.Entry<String, JsonElement> e : o.entrySet()) lootItems(e.getValue(), out);
        }
    }
}
