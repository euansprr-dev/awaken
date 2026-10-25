package dev.awaken.arms;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every fighting style, read once from the mod's own {@code data/awaken/arms/archetypes.json} on both sides (the
 * client predicts the same rows the server runs, so they ship in the jar, not in a datapack). The pipeline's
 * {@code tools/art/specs/arms_*.py} reads the same file.
 */
public final class Archetypes {
    private Archetypes() {}

    public static final int SCHEMA_VERSION = 1;
    static final String PATH = "/data/awaken/arms/archetypes.json";

    private record File(int schemaVersion, List<Archetype> archetypes) {
        static final Codec<File> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("schemaVersion").forGetter(File::schemaVersion),
                Archetype.CODEC.listOf().fieldOf("archetypes").forGetter(File::archetypes)
        ).apply(i, File::new));
    }

    /** A style with its moves resolved by enum. */
    public record Loaded(Archetype def, int index, Map<Archetype.Move, Archetype.MoveDef> moves, List<Archetype.Move> chain, List<Archetype.Move> masteryChain) {
        public Archetype.@Nullable MoveDef move(Archetype.Move m) {
            return moves.get(m);
        }

        public String id() {
            return def.id();
        }
    }

    private static final Map<String, Loaded> BY_ID = new LinkedHashMap<>();
    private static final List<Loaded> BY_INDEX = new ArrayList<>();

    static {
        load();
    }

    private static void load() {
        try (InputStream in = Archetypes.class.getResourceAsStream(PATH)) {
            if (in == null) throw new IllegalStateException("missing " + PATH);
            JsonElement json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            File file = File.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(msg -> new IllegalStateException(PATH + ": " + msg));
            if (file.schemaVersion() != SCHEMA_VERSION) throw new IllegalStateException(PATH + ": schemaVersion " + file.schemaVersion());
            for (Archetype a : file.archetypes()) {
                Map<Archetype.Move, Archetype.MoveDef> moves = new EnumMap<>(a.byMove());
                Loaded l = new Loaded(a, BY_INDEX.size(), moves, resolve(a, a.chain(), moves), resolve(a, a.masteryChain(), moves));
                if (l.chain().isEmpty()) throw new IllegalStateException(a.id() + ": empty chain");
                BY_ID.put(a.id(), l);
                BY_INDEX.add(l);
            }
            Awaken.LOG.info("[arms] {} fighting styles", BY_INDEX.size());
        } catch (java.io.IOException e) {
            throw new IllegalStateException(PATH, e);
        }
    }

    private static List<Archetype.Move> resolve(Archetype a, List<String> keys, Map<Archetype.Move, Archetype.MoveDef> moves) {
        List<Archetype.Move> out = new ArrayList<>(keys.size());
        for (String k : keys) {
            Archetype.Move m = Archetype.Move.byKey(k);
            if (m == null || !moves.containsKey(m)) throw new IllegalStateException(a.id() + ": chain names a missing move " + k);
            out.add(m);
        }
        return List.copyOf(out);
    }

    public static @Nullable Loaded get(String id) {
        return BY_ID.get(id);
    }

    public static @Nullable Loaded byIndex(int index) {
        return index >= 0 && index < BY_INDEX.size() ? BY_INDEX.get(index) : null;
    }

    public static List<Loaded> all() {
        return BY_INDEX;
    }
}
