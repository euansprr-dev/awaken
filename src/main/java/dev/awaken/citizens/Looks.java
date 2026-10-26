package dev.awaken.citizens;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Skins for settlements that aren't Carillon (SPEC §28.4): a pick by hash from Carillon's own generated skins (one per
 * jar roster record, {@code textures/entity/citizen/c###}), from those of the same job, age and build where there are
 * any, else the residents' of that age and build, else anyone's of that age. Stable for a settlement's key and record,
 * so a villager looks the same every time they come out; W3 may bring village clothes of its own.
 */
final class Looks {
    private Looks() {}

    private static Map<Long, int[]> pool;

    /** The Carillon record whose skin a settlement's record wears (-1: none, the renderer's fallback). */
    static synchronized int pick(String key, int rec, int job, int build, int age) {
        Roster carillon = Roster.get();
        if (carillon == null) return -1;
        if (pool == null) pool = build(carillon);
        boolean child = age == 0;
        job = skinJob(job);
        int[] from = pool.get(bucket(job, child, build));
        if (from == null) from = pool.get(bucket(Roster.RESIDENT, child, build));
        if (from == null) from = pool.get(bucket(-1, child, -1));
        if (from == null || from.length == 0) return -1;
        long h = CitizenWork.mixHash(rec * 0x9E3779B97F4A7C15L ^ fnv(key));
        return from[(int) Math.floorMod(h, (long) from.length)];
    }

    /** The city trade whose clothes a village trade wears (SPEC §28.6): working clothes, not the Market's finery. */
    static int skinJob(int job) {
        return switch (job) {
            case Roster.FARMER, Roster.WOODCUTTER, Roster.CARTER, Roster.HUNTER -> Roster.PORTER;
            case Roster.MINER -> Roster.MASON;
            case Roster.FISHER -> Roster.BOATMAN;
            case Roster.SCRIBE -> Roster.CLERK;
            case Roster.SHEPHERD, Roster.ELDER -> Roster.RESIDENT;
            default -> job;
        };
    }

    private static long bucket(int job, boolean child, int build) {
        return ((long) (job + 1) << 8) | (child ? 1L << 4 : 0) | (build + 1);
    }

    private static Map<Long, int[]> build(Roster r) {
        Map<Long, List<Integer>> by = new HashMap<>();
        for (int i = 0; i < r.citizens; i++) {
            if (!r.named[i].isEmpty()) continue; // the named wear their own faces
            boolean child = r.age[i] == 0;
            by.computeIfAbsent(bucket(r.job[i], child, r.build[i]), k -> new ArrayList<>()).add(i);
            by.computeIfAbsent(bucket(-1, child, -1), k -> new ArrayList<>()).add(i);
        }
        Map<Long, int[]> out = new HashMap<>();
        for (var e : by.entrySet()) out.put(e.getKey(), e.getValue().stream().mapToInt(Integer::intValue).toArray());
        return out;
    }

    private static long fnv(String s) {
        long h = 0xcbf29ce484222325L;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            h ^= b & 0xff;
            h *= 0x100000001b3L;
        }
        return h;
    }
}
