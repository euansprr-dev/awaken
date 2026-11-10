package dev.awaken.finds;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Every kind of find, in Journal order (set, then number). Filled once at init by the set files. */
public final class Kinds {
    private Kinds() {}

    private static final Map<String, Kind> BY_ID = new LinkedHashMap<>();
    private static List<Kind> all = List.of(), curios = List.of(), finds = List.of(), places = List.of(), monuments = List.of(), remains = List.of();

    static void register(Kind k) {
        if (BY_ID.put(k.id(), k) != null) throw new IllegalStateException("two finds are called " + k.id());
    }

    /** Called after every set file has run: fixes the order and the two layers. */
    static void seal() {
        List<Kind> a = new ArrayList<>(BY_ID.values());
        a.sort((x, y) -> x.set() != y.set() ? x.set().compareTo(y.set()) : Integer.compare(x.number(), y.number()));
        all = Collections.unmodifiableList(a);
        curios = a.stream().filter(k -> k.size() == Kind.Size.CURIO).toList();
        finds = a.stream().filter(k -> k.size() == Kind.Size.FIND).toList();
        places = a.stream().filter(k -> k.size() == Kind.Size.PLACE).toList();
        monuments = places.stream().filter(k -> k.set() == Kind.FSet.MONUMENTS).toList();
        remains = finds.stream().filter(k -> k.set() == Kind.FSet.REMAINS).toList();
    }

    public static List<Kind> all() {
        return all;
    }

    /** The kinds of a layer: 0 curios, 1 finds, 2 places. */
    public static List<Kind> layer(int layer) {
        return layer == 0 ? curios : layer == 1 ? finds : places;
    }

    public static List<Kind> places() {
        return places;
    }

    /** The monuments of the old world (SPEC §61): the only places the world still deals, and the only ones the Journal lists. */
    public static List<Kind> monuments() {
        return monuments;
    }

    /** The remains (SPEC §64.6): small pieces of the old world that stand near a monument and point to it. */
    public static List<Kind> remains() {
        return remains;
    }

    /** A family's five, nearest home first. */
    public static List<Kind> ofFamily(Family f) {
        return monuments.stream().filter(k -> k.family() == f).sorted(java.util.Comparator.comparingInt(Kind::tier)).toList();
    }

    public static @Nullable Kind byId(String id) {
        return BY_ID.get(id);
    }

    public static List<Kind> ofSet(Kind.FSet set) {
        return all.stream().filter(k -> k.set() == set).toList();
    }
}
