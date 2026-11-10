package dev.awaken.finds;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a player has found (SPEC §58.7): how many of each kind they've done, the kinds they've only seen through the
 * glass, the sites they've been paid at ("kind@x,z"), the leads they hold and their best times on the runs. Saved,
 * kept through death, synced to its owner (the Journal and the Atlas draw from it). Immutable: every change makes a
 * new one, so the attachment syncs.
 */
public record FindsData(int schemaVersion, Map<String, Integer> done, List<String> seen, List<String> sites, List<String> leads, Map<String, Integer> best) {
    public static final int SCHEMA = 1, MAX_SITES = 4000;
    public static final FindsData EMPTY = new FindsData(SCHEMA, Map.of(), List.of(), List.of(), List.of(), Map.of());

    public static final MapCodec<FindsData> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(FindsData::schemaVersion),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("done", Map.of()).forGetter(FindsData::done),
            Codec.STRING.listOf().optionalFieldOf("seen", List.of()).forGetter(FindsData::seen),
            Codec.STRING.listOf().optionalFieldOf("sites", List.of()).forGetter(FindsData::sites),
            Codec.STRING.listOf().optionalFieldOf("leads", List.of()).forGetter(FindsData::leads),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("best", Map.of()).forGetter(FindsData::best)).apply(i, FindsData::new));

    public int count(String kind) {
        return done.getOrDefault(kind, 0);
    }

    public boolean paidAt(String siteKey) {
        return sites.contains(siteKey);
    }

    public boolean knows(String kind) {
        return done.containsKey(kind) || seen.contains(kind);
    }

    public int kindsDone() {
        return done.size();
    }

    public FindsData withDone(String kind, String siteKey) {
        Map<String, Integer> d = new LinkedHashMap<>(done);
        d.merge(kind, 1, Integer::sum);
        List<String> s = new ArrayList<>(sites);
        s.add(siteKey);
        if (s.size() > MAX_SITES) s = s.subList(s.size() - MAX_SITES, s.size());
        List<String> seenNow = seen.contains(kind) ? seen.stream().filter(k -> !k.equals(kind)).toList() : seen;
        return new FindsData(SCHEMA, Map.copyOf(d), seenNow, List.copyOf(s), leads, best);
    }

    public FindsData withSeen(String kind) {
        if (knows(kind)) return this;
        List<String> s = new ArrayList<>(seen);
        s.add(kind);
        return new FindsData(SCHEMA, done, List.copyOf(s), sites, leads, best);
    }

    public FindsData withLead(String lead) {
        List<String> l = new ArrayList<>(leads);
        l.add(lead);
        if (l.size() > 24) l = l.subList(l.size() - 24, l.size());
        return new FindsData(SCHEMA, done, seen, sites, List.copyOf(l), best);
    }

    /** A run's time in ticks, kept when it beats the best. */
    public FindsData withTime(String kind, int ticks) {
        Integer old = best.get(kind);
        if (old != null && old <= ticks) return this;
        Map<String, Integer> b = new LinkedHashMap<>(best);
        b.put(kind, ticks);
        return new FindsData(SCHEMA, done, seen, sites, leads, Map.copyOf(b));
    }
}
