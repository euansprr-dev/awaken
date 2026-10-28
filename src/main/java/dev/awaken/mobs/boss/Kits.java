package dev.awaken.mobs.boss;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.core.api.Strikes;
import dev.awaken.mobs.client.anim.HeroRig;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every kit body (SPEC §35.2), read once from the jar on both sides: {@code data/awaken/kit/index.json} names them;
 * each has a rig ({@code assets/awaken/kit/rigs/<id>.rig}, the hero rig text format), a move table and, for bosses, a
 * fight script. A kit that is missing a file or fails a check is left out (logged once): its creature keeps its old
 * body and fights as it did.
 */
public final class Kits {
    private Kits() {}

    static final String INDEX = "/data/awaken/kit/index.json";

    /** One kit body: its rig, its moves, and (bosses) its fight. */
    public record Kit(String id, HeroRig rig, KitMoves.Table moves, FightScripts.@Nullable Fight fight) {}

    private record Index(int schemaVersion, List<String> kits) {
        static final Codec<Index> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("schemaVersion").forGetter(Index::schemaVersion),
                Codec.STRING.listOf().fieldOf("kits").forGetter(Index::kits)
        ).apply(i, Index::new));
    }

    private static @Nullable Map<String, Kit> all;
    /** Why each kit that didn't load was left out (for the tests and the log). */
    private static final Map<String, String> FAILED = new LinkedHashMap<>();

    public static synchronized Map<String, Kit> all() {
        if (all == null) all = load();
        return all;
    }

    public static @Nullable Kit get(String id) {
        return all().get(id);
    }

    /** The kits that failed to load, with the reason. */
    public static Map<String, String> failed() {
        all();
        return Collections.unmodifiableMap(FAILED);
    }

    /** The ids the index names (loaded or not). */
    public static List<String> named() {
        try {
            return read(INDEX, Index.CODEC).kits();
        } catch (RuntimeException | IOException e) {
            return List.of();
        }
    }

    private static Map<String, Kit> load() {
        Map<String, Kit> out = new LinkedHashMap<>();
        for (String id : named()) {
            try {
                Kit k = loadOne(id);
                List<String> problems = problems(k);
                if (!problems.isEmpty()) throw new IllegalStateException(String.join("; ", problems));
                out.put(id, k);
            } catch (RuntimeException | IOException e) {
                FAILED.put(id, String.valueOf(e.getMessage()));
                Awaken.LOG.warn("[kit] {} left on its old body: {}", id, e.getMessage());
            }
        }
        if (!out.isEmpty()) Awaken.LOG.info("[kit] {} kit bodies: {}", out.size(), String.join(", ", out.keySet()));
        return Collections.unmodifiableMap(out);
    }

    static Kit loadOne(String id) throws IOException {
        String rigPath = "/assets/awaken/kit/rigs/" + id + ".rig";
        HeroRig rig;
        try (InputStream in = Kits.class.getResourceAsStream(rigPath)) {
            if (in == null) throw new IOException("missing " + rigPath);
            rig = HeroRig.parse(new String[]{new String(in.readAllBytes(), StandardCharsets.UTF_8)});
        }
        KitMoves.Table moves = read("/data/awaken/kit/moves/" + id + ".json", KitMoves.Table.CODEC);
        if (moves.schemaVersion() != KitMoves.SCHEMA_VERSION) throw new IOException(id + " moves: schemaVersion " + moves.schemaVersion());
        String fightPath = "/data/awaken/kit/fights/" + id + ".json";
        FightScripts.Fight fight = null;
        if (Kits.class.getResource(fightPath) != null) {
            fight = read(fightPath, FightScripts.Fight.CODEC);
            if (fight.schemaVersion() != FightScripts.SCHEMA_VERSION) throw new IOException(id + " fight: schemaVersion " + fight.schemaVersion());
        }
        return new Kit(id, rig, moves, fight);
    }

    private static <T> T read(String path, Codec<T> codec) throws IOException {
        try (InputStream in = Kits.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("missing " + path);
            JsonElement json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            return codec.parse(JsonOps.INSTANCE, json).getOrThrow(msg -> new IllegalStateException(path + ": " + msg));
        }
    }

    // ---- the checks (load time and the kit_ GameTests) -------------------------------------------------------------

    /** The kinds every boss's script must cover (SPEC §35.2, PLAN §4.3). */
    public static final List<String> REQUIRED = List.of("gap", "combo", "delay", "area", "ranged", "behind", "signature");

    /** Everything wrong with a kit, empty if it may be used. */
    public static List<String> problems(Kit k) {
        List<String> out = new ArrayList<>();
        for (KitMoves.Move m : k.moves.moves()) {
            String p = k.id + "/" + m.id();
            HeroRig.Clip c = k.rig.clip(m.clip());
            if (c == null) out.add(p + ": no clip " + m.clip());
            else if (Math.abs(c.length() * 20 - m.length()) > 1.01F) out.add(p + ": clip " + c.length() + " s against " + m.length() + " ticks");
            if (m.time().link() > m.time().recover()) out.add(p + ": links after its recovery ends");
            if (m.kind() != Strikes.Kind.NONE && m.time().windup() < KitMoves.minWindup(m.kind()))
                out.add(p + ": a " + m.kind() + " wind-up of " + m.time().windup() + " ticks is under " + KitMoves.minWindup(m.kind()));
            if (m.time().hold() > 0 && m.kind() != Strikes.Kind.HEAVY) out.add(p + ": only a HEAVY move may hold");
            if (m.time().hold() > KitMoves.MAX_HOLD) out.add(p + ": a hold over " + KitMoves.MAX_HOLD + " ticks");
            if (m.time().active() < 1) out.add(p + ": no active ticks");
            if (m.hits().size() > m.time().active()) out.add(p + ": hits past its active ticks");
            if (m.time().lock() > m.time().windup()) out.add(p + ": locks after its wind-up");
        }
        if (k.fight != null) out.addAll(fightProblems(k));
        return out;
    }

    private static List<String> fightProblems(Kit k) {
        List<String> out = new ArrayList<>();
        FightScripts.Fight f = k.fight;
        if (f.phases().isEmpty()) out.add(k.id + ": a fight with no phases");
        Set<String> covered = new LinkedHashSet<>();
        for (int p = 0; p < f.phases().size(); p++) {
            FightScripts.Phase ph = f.phases().get(p);
            if (ph.pool().isEmpty()) out.add(k.id + ": phase " + p + " has an empty pool");
            for (FightScripts.Entry e : ph.pool()) {
                need(k, e.move(), out, covered);
                for (List<String> string : e.then()) {
                    if (!string.isEmpty()) covered.add("combo");
                    for (String s : string) need(k, s, out, covered);
                }
            }
            ph.transition().ifPresent(t -> need(k, t, out, null));
        }
        f.intro().move().ifPresent(t -> need(k, t, out, null));
        f.death().ifPresent(t -> need(k, t, out, null));
        for (String r : REQUIRED) if (!covered.contains(r)) out.add(k.id + ": its fight has no " + r + " move");
        return out;
    }

    private static void need(Kit k, String move, List<String> out, @Nullable Set<String> covered) {
        int i = k.moves.index(move);
        if (i < 0) {
            out.add(k.id + ": the fight names a missing move " + move);
            return;
        }
        if (covered == null) return;
        KitMoves.Move m = k.moves.get(i);
        covered.addAll(m.kinds());
        if (m.signature()) covered.add("signature");
        if (m.time().hold() > 0) covered.add("delay");
    }
}
