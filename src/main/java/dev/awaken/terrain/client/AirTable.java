package dev.awaken.terrain.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.Awaken;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What drifts in the air, from {@code assets/<namespace>/lands/air*.json} (schemaVersion 1): a style per leaf block and
 * per biome, matched by id or tag, first match wins (a later file's rules are tried first). Data, so the Awakened trees
 * of the vertical slice plug in with a line each. Read on resource reload; matching allocates nothing.
 */
final class AirTable {
    private AirTable() {}

    static final int SCHEMA_VERSION = 1;

    /** How one kind of leaf falls. */
    record LeafStyle(boolean skip, int[] sprites, float size, float rate, int color) {
        static final LeafStyle DEFAULT = new LeafStyle(false, new int[]{0, 2}, 1F, 1F, -1);
    }

    /**
     * Glowing motes rising instead of (or as well as) leaves. {@code kind} null: the Lumen Forest's motes; otherwise the
     * second pair's air ({@link LandsMotes}: {@code glint}, {@code fault}).
     */
    record MoteStyle(int color, float rate, boolean night, @Nullable String kind) {}

    /** What a biome does to the air. color -1: leaves keep their own colour. */
    record BiomeStyle(float rate, int color, float colorMix, int @Nullable [] sprites, float size, @Nullable MoteStyle motes) {
        static final BiomeStyle DEFAULT = new BiomeStyle(0.5F, -1, 0F, null, 1F, null);
    }

    private record Matcher(List<Identifier> ids, List<Identifier> tags, boolean any) {}

    private static final List<Matcher> LEAF_MATCH = new ArrayList<>();
    private static final List<LeafStyle> LEAF_STYLE = new ArrayList<>();
    private static final List<Matcher> BIOME_MATCH = new ArrayList<>();
    private static final List<BiomeStyle> BIOME_STYLE = new ArrayList<>();
    private static final List<TagKey<Block>> BLOCK_TAGS = new ArrayList<>();
    private static final List<TagKey<Biome>> BIOME_TAGS = new ArrayList<>();
    private static final List<List<Block>> LEAF_BLOCKS = new ArrayList<>(); // each leaf rule's ids, resolved

    static void reload(ResourceManager manager) {
        LEAF_MATCH.clear();
        LEAF_STYLE.clear();
        BIOME_MATCH.clear();
        BIOME_STYLE.clear();
        BLOCK_TAGS.clear();
        BIOME_TAGS.clear();
        LEAF_BLOCKS.clear();
        var files = new TreeMap<>(manager.listResources("lands", id -> id.getPath().startsWith("lands/air") && id.getPath().endsWith(".json")));
        // later files first: an add-on's rules win over the base table's
        List<Map.Entry<Identifier, net.minecraft.server.packs.resources.Resource>> ordered = new ArrayList<>(files.entrySet());
        java.util.Collections.reverse(ordered);
        for (var file : ordered) {
            try (Reader r = file.getValue().openAsReader()) {
                JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                int version = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : 0;
                if (version != SCHEMA_VERSION) Awaken.LOG.warn("[lands] {} schema {} (expected {}): reading it anyway", file.getKey(), version, SCHEMA_VERSION);
                for (JsonElement e : arr(root, "leaves")) {
                    JsonObject o = e.getAsJsonObject();
                    LEAF_MATCH.add(matcher(o));
                    LEAF_STYLE.add(new LeafStyle(o.has("skip") && o.get("skip").getAsBoolean(), ints(o, "sprites", LeafStyle.DEFAULT.sprites()),
                            num(o, "size", 1F), num(o, "rate", 1F), color(o, "color")));
                }
                for (JsonElement e : arr(root, "biomes")) {
                    JsonObject o = e.getAsJsonObject();
                    BIOME_MATCH.add(matcher(o));
                    MoteStyle motes = null;
                    if (o.has("motes")) {
                        JsonObject m = o.getAsJsonObject("motes");
                        motes = new MoteStyle(color(m, "color"), num(m, "rate", 1F), m.has("night") && m.get("night").getAsBoolean(),
                                m.has("kind") ? m.get("kind").getAsString() : null);
                    }
                    BIOME_STYLE.add(new BiomeStyle(num(o, "rate", 1F), color(o, "color"), num(o, "colorMix", 0.75F),
                            o.has("sprites") ? ints(o, "sprites", null) : null, num(o, "size", 1F), motes));
                }
            } catch (RuntimeException | java.io.IOException ex) {
                Awaken.LOG.warn("[lands] {} unreadable, skipped: {}", file.getKey(), ex.toString());
            }
        }
        for (Matcher m : LEAF_MATCH) {
            for (Identifier t : m.tags()) BLOCK_TAGS.add(TagKey.create(Registries.BLOCK, t));
            List<Block> blocks = new ArrayList<>();
            for (Identifier id : m.ids()) BuiltInRegistries.BLOCK.getOptional(id).ifPresent(blocks::add); // a slice block not built yet: skipped
            LEAF_BLOCKS.add(List.copyOf(blocks));
        }
        for (Matcher m : BIOME_MATCH) for (Identifier t : m.tags()) BIOME_TAGS.add(TagKey.create(Registries.BIOME, t));
        Awaken.LOG.info("[lands] air: {} files, {} leaf rules, {} biome rules", files.size(), LEAF_MATCH.size(), BIOME_MATCH.size());
    }

    /** The style of a leaf block; DEFAULT when no rule names it. */
    static LeafStyle leaf(BlockState state) {
        int tag = 0;
        for (int i = 0; i < LEAF_MATCH.size(); i++) {
            Matcher m = LEAF_MATCH.get(i);
            if (m.any()) return LEAF_STYLE.get(i);
            List<Block> blocks = LEAF_BLOCKS.get(i);
            for (int k = 0; k < blocks.size(); k++) if (state.getBlock() == blocks.get(k)) return LEAF_STYLE.get(i);
            for (int k = 0; k < m.tags().size(); k++) if (state.is(BLOCK_TAGS.get(tag + k))) return LEAF_STYLE.get(i);
            tag += m.tags().size();
        }
        return LeafStyle.DEFAULT;
    }

    /** The style of a biome; DEFAULT when no rule names it. */
    static BiomeStyle biome(Holder<Biome> biome) {
        int tag = 0;
        for (int i = 0; i < BIOME_MATCH.size(); i++) {
            Matcher m = BIOME_MATCH.get(i);
            if (m.any()) return BIOME_STYLE.get(i);
            for (int k = 0; k < m.ids().size(); k++) if (biome.is(m.ids().get(k))) return BIOME_STYLE.get(i);
            for (int k = 0; k < m.tags().size(); k++) if (biome.is(BIOME_TAGS.get(tag + k))) return BIOME_STYLE.get(i);
            tag += m.tags().size();
        }
        return BiomeStyle.DEFAULT;
    }

    /** The style a biome id would have (for the debug override), or null if unknown to the table. */
    static @Nullable BiomeStyle biomeById(Identifier id) {
        for (int i = 0; i < BIOME_MATCH.size(); i++) if (BIOME_MATCH.get(i).ids().contains(id)) return BIOME_STYLE.get(i);
        return null;
    }

    private static Matcher matcher(JsonObject o) {
        List<Identifier> ids = new ArrayList<>(), tags = new ArrayList<>();
        boolean any = false;
        for (JsonElement e : arr(o, "match")) {
            String s = e.getAsString();
            if (s.equals("*")) any = true;
            else if (s.startsWith("#")) tags.add(Identifier.parse(s.substring(1)));
            else ids.add(Identifier.parse(s));
        }
        return new Matcher(List.copyOf(ids), List.copyOf(tags), any);
    }

    private static JsonArray arr(JsonObject o, String key) {
        return o.has(key) ? o.getAsJsonArray(key) : new JsonArray();
    }

    private static float num(JsonObject o, String key, float fallback) {
        return o.has(key) ? o.get(key).getAsFloat() : fallback;
    }

    private static int @Nullable [] ints(JsonObject o, String key, int @Nullable [] fallback) {
        if (!o.has(key)) return fallback;
        JsonArray a = o.getAsJsonArray(key);
        int[] out = new int[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = Math.clamp(a.get(i).getAsInt(), 0, FallingLeafParticle.LEAF_SPRITES - 1);
        return out.length == 0 ? fallback : out;
    }

    private static int color(JsonObject o, String key) {
        if (!o.has(key)) return -1;
        String s = o.get(key).getAsString();
        return Integer.parseInt(s.startsWith("#") ? s.substring(1) : s, 16) & 0xFFFFFF;
    }
}
