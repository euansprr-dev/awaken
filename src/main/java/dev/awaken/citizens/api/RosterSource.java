package dev.awaken.citizens.api;

import dev.awaken.citizens.Roster;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Where a settlement's people come from (SPEC §28.4). Carillon's are read from the jar ({@link #jar()}: its roster is
 * generated offline from the city's plan, and it walks the plan's own street graph). Any other settlement's are built in
 * code at registration ({@link RosterBuilder}: households, workplaces, stations and days in the shape
 * {@code tools/city/citizens.py} writes, plus the ground they walk). A source must be deterministic for a settlement's
 * key: the roster's hash ties the saved records to it, and a different roster re-seats everyone.
 */
@FunctionalInterface
public interface RosterSource {
    Built build();

    /** Carillon's roster from the jar ({@code data/awaken/city/citizens.json}); its graph is the plan's (null here). */
    static RosterSource jar() {
        return () -> new Built(Roster.get(), null, List.of(), List.of(), List.of());
    }

    /**
     * What a source yields. Everything but the roster is the settlement's ground, as offsets from its origin (x east,
     * z south, ry up): the street graph its people walk ({@code null}: Carillon's plan), its lots (plots an event can use,
     * with their tags), water cells (a bucket line's source: a well, a pier) and district names (index = district id;
     * id 0 is the settlement as a whole).
     */
    record Built(@Nullable Roster roster, @Nullable Graph graph, List<Lot> lots, List<int[]> water, List<String> districts) {}

    /**
     * A street graph: node positions (x, z block centres; ry the paving's top block above the origin), kinds
     * ({@code 0} junction, {@code 1} door, {@code 2} landmark), each door's lot id ({@code -1} for other nodes), edges.
     */
    record Graph(float[] x, int[] ry, float[] z, byte[] kind, int[] ref, int[] a, int[] b, byte[] lane) {
        public int nodes() {
            return x.length;
        }

        public int edges() {
            return a.length;
        }
    }

    /** A plot an event can use: its box, its roof's top above the origin, its door (MIN_VALUE: none), its tags. */
    record Lot(int id, int x0, int z0, int x1, int z1, int top, int doorX, int doorZ, Set<String> tags) {}
}
