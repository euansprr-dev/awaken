package dev.awaken.mobs.def;

import dev.awaken.Awaken;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Every creature def, by id. Built once, in declaration order (which is also registration order). */
public final class Bestiary {
    private Bestiary() {}

    private static final Map<String, MobDef> BY_ID;
    private static final List<MobDef> FAUNA;

    static {
        List<MobDef> all = new ArrayList<>();
        GateBestiary.define(all);
        DeepBestiary.define(all);
        FaunaBestiary.define(all);
        WildBestiary.define(all);
        WaterBestiary.define(all);
        CaveBestiary.define(all);
        Map<String, MobDef> map = new LinkedHashMap<>();
        for (MobDef d : all) {
            if (map.put(d.id, d) != null) throw new IllegalStateException("Duplicate mob id " + d.id);
        }
        BY_ID = Collections.unmodifiableMap(map);
        List<MobDef> fauna = new ArrayList<>();
        for (MobDef d : all) if (d.isFauna() && !d.placed) fauna.add(d);
        FAUNA = List.copyOf(fauna);
        for (MobDef d : all) {
            for (MobDef.Phase p : d.phases) {
                for (Ability a : p.abilities()) {
                    if (a.kind() == Ability.Kind.SUMMON && (a.summon() == null || !map.containsKey(a.summon()))) {
                        Awaken.LOG.error("[mobs] {} summons unknown creature {}", d.id, a.summon());
                    }
                }
            }
        }
    }

    public static Collection<MobDef> all() {
        return BY_ID.values();
    }

    public static MobDef get(String id) {
        return BY_ID.get(id);
    }

    /** Accepts "bell_warden" or "awaken:bell_warden". */
    public static MobDef byAnyId(String id) {
        if (id == null) return null;
        String path = id.startsWith(Awaken.MODID + ":") ? id.substring(Awaken.MODID.length() + 1) : id;
        return BY_ID.get(path);
    }

    /**
     * The overworld creatures of the biome families (the Awakened layer), in declaration order. Placed creatures (the seep
     * heart) are fauna too but are left out: no biome spawns them, no family, lair or codex counts them.
     */
    public static List<MobDef> fauna() {
        return FAUNA;
    }

    public static List<MobDef> of(Theme theme, Tier tier) {
        List<MobDef> out = new ArrayList<>();
        for (MobDef d : BY_ID.values()) if (d.theme == theme && d.tier == tier) out.add(d);
        return out;
    }
}
