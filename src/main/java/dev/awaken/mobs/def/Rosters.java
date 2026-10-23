package dev.awaken.mobs.def;

import dev.awaken.core.Forces;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which creatures answer to which theme id. Gate themes and strata come from the bestiary by theme; a few extra ids
 * let other modules ask for a mood rather than a place ({@code awaken:silence} for the Silence modifier's Listeners,
 * {@code awaken:haunted} for Veil creatures, {@code awaken:mirror} for "your shadow hunts you", and the nightmare
 * templates {@code awaken:nightmare/<family>}). Unknown ids fall back to a theme chosen from the id's hash, so a
 * caller always gets something that fits a gate.
 */
public final class Rosters {
    private Rosters() {}

    public record Roster(String id, Theme theme, List<MobDef> normals, @Nullable MobDef elite, @Nullable MobDef warden,
                         @Nullable MobDef boss, Set<Forces.Tag> tags) {}

    public static final List<String> NIGHTMARE_FAMILIES = List.of("falling", "burning", "drowning", "undead", "arthropod", "explosion", "generic");

    private static final Map<String, Roster> BY_ID = new LinkedHashMap<>();
    private static final List<Roster> GATES = new ArrayList<>();

    static {
        for (Theme t : Theme.values()) {
            if (t == Theme.FEAR) continue;
            List<MobDef> normals = Bestiary.of(t, Tier.NORMAL);
            if (normals.isEmpty()) continue;
            MobDef elite = first(Bestiary.of(t, Tier.ELITE));
            MobDef warden = first(Bestiary.of(t, Tier.WARDEN));
            MobDef boss = first(Bestiary.of(t, Tier.LORD));
            if (boss == null) boss = first(Bestiary.of(t, Tier.GUARDIAN));
            if (t == Theme.SEED) boss = Bestiary.get("guardian_seed");
            Roster r = new Roster(t.id, t, normals, elite, warden, boss, Set.of());
            BY_ID.put(t.id, r);
            if (t.isGate()) GATES.add(r);
        }
        // Moods other modules can ask for.
        Roster silent = BY_ID.get(Theme.SILENT.id);
        BY_ID.put("awaken:silence", new Roster("awaken:silence", Theme.SILENT, List.of(Bestiary.get("listener")), null, null, Bestiary.get("guardian_silent"), Set.of()));
        // The Veil proper is Cord-Eaters; the Haunted gate modifier brings Veil Wraiths with them.
        BY_ID.put(Theme.VEIL.id, new Roster(Theme.VEIL.id, Theme.VEIL, List.of(Bestiary.get("cord_eater")), null, null, null, Set.of()));
        BY_ID.put("awaken:haunted", new Roster("awaken:haunted", Theme.VEIL, List.of(Bestiary.get("veil_wraith"), Bestiary.get("cord_eater")),
                null, null, null, Set.of(Forces.Tag.HAUNTED)));
        BY_ID.put("awaken:mirror", new Roster("awaken:mirror", Theme.MIRROR, List.of(Bestiary.get("mirror_shade"), Bestiary.get("court_shadow")), null, null, null, Set.of()));
        // Dream fauna borrows the Dreaming Stratum's wisps too.
        Roster dream = BY_ID.get(Theme.DREAM.id);
        List<MobDef> dreamers = new ArrayList<>(dream.normals());
        dreamers.add(Bestiary.get("dream_wisp"));
        dreamers.add(Bestiary.get("lullaby_phantom"));
        BY_ID.put(Theme.DREAM.id, new Roster(dream.id(), Theme.DREAM, List.copyOf(dreamers), null, null, null, Set.of()));
        // Nightmares: one template per death-cause family, whatever role is asked for.
        for (String f : NIGHTMARE_FAMILIES) {
            MobDef d = Bestiary.get("nightmare_" + f);
            String id = "awaken:nightmare/" + f;
            BY_ID.put(id, new Roster(id, Theme.FEAR, List.of(d), d, d, d, Set.of()));
        }
        if (silent == null) throw new IllegalStateException("silent halls roster missing");
    }

    private static @Nullable MobDef first(List<MobDef> l) {
        return l.isEmpty() ? null : l.get(0);
    }

    public static @Nullable Roster exact(String id) {
        return BY_ID.get(id);
    }

    /** The roster for a theme id; unknown ids fall back to a gate theme picked from the id's hash. */
    public static Roster forTheme(String id) {
        if (id != null) {
            Roster r = BY_ID.get(id);
            if (r != null) return r;
            if (id.startsWith("awaken:nightmare/")) return BY_ID.get("awaken:nightmare/generic");
        }
        return GATES.get(Math.floorMod(id == null ? 0 : id.hashCode(), GATES.size()));
    }

    public static Map<String, Roster> all() {
        return BY_ID;
    }

    /** Ids callers are expected to use (gates, strata, Veil, dream, moods, nightmares). */
    public static List<String> ids() {
        return List.copyOf(BY_ID.keySet());
    }
}
