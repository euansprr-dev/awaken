package dev.awaken.mobs;

import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Mob sounds. Each points at re-pitched vanilla files in src/main/merge/mobs/assets/awaken/sounds.json. */
public final class MobSounds {
    private MobSounds() {}

    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);

    /** Light wind-up tell (bolts, small swings). */
    public static final DeferredHolder<SoundEvent, SoundEvent> TELEGRAPH = sound("mobs.telegraph");
    /** Heavy wind-up tell (slams, beams, boss signatures). */
    public static final DeferredHolder<SoundEvent, SoundEvent> TELEGRAPH_HEAVY = sound("mobs.telegraph_heavy");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_INTRO = sound("mobs.boss_intro");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_PHASE = sound("mobs.boss_phase");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_TOLL = sound("mobs.boss_toll");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_DEFEATED = sound("mobs.boss_defeated");
    public static final DeferredHolder<SoundEvent, SoundEvent> RISE = sound("mobs.rise");
    public static final DeferredHolder<SoundEvent, SoundEvent> CORD_BITE = sound("mobs.cord_bite");
    public static final DeferredHolder<SoundEvent, SoundEvent> LISTENER_HEAR = sound("mobs.listener_hear");

    /**
     * The overworld creatures' own voices, {@code fauna.<id>.<kind>}: ambient, hurt, death and step for every one
     * (a flier's step is its wingbeat), and a roar for each named beast (its signature move, its phases). The files
     * come from tools/art/specs/fauna_sounds.py; sounds.json lives in src/main/merge/fauna.
     */
    public static final java.util.List<String> FAUNA_KINDS = java.util.List.of("ambient", "hurt", "death", "step");
    /**
     * A creature's voices beyond those (fauna.<id>.<kind> too): the seep heart's heartbeat, the rock groaning as its
     * roots push through, the roots bursting out, its shards, its pulse and its call to the walls.
     */
    public static final java.util.Map<String, java.util.List<String>> EXTRA = java.util.Map.of("seep_heart",
            java.util.List.of("beat", "rumble", "roots", "shards", "pulse", "call"));
    private static final java.util.Map<String, DeferredHolder<SoundEvent, SoundEvent>> FAUNA = faunaSounds();

    private static java.util.Map<String, DeferredHolder<SoundEvent, SoundEvent>> faunaSounds() {
        java.util.Map<String, DeferredHolder<SoundEvent, SoundEvent>> m = new java.util.HashMap<>();
        for (dev.awaken.mobs.def.MobDef d : dev.awaken.mobs.def.Bestiary.all()) {
            if (!d.isFauna()) continue;
            for (String kind : FAUNA_KINDS) m.put(d.id + "." + kind, sound("fauna." + d.id + "." + kind));
            if (d.fauna == dev.awaken.mobs.def.FaunaRole.BEAST) m.put(d.id + ".roar", sound("fauna." + d.id + ".roar"));
            for (String kind : EXTRA.getOrDefault(d.id, java.util.List.of())) m.put(d.id + "." + kind, sound("fauna." + d.id + "." + kind));
        }
        return java.util.Map.copyOf(m);
    }

    /** A creature's own sound of this kind, or null if it has none (then its borrowed vanilla voice speaks). */
    public static @org.jspecify.annotations.Nullable SoundEvent fauna(String id, String kind) {
        DeferredHolder<SoundEvent, SoundEvent> h = FAUNA.get(id + "." + kind);
        return h == null || !h.isBound() ? null : h.get();
    }

    /** Every registered fauna sound id (for tests). */
    public static java.util.Set<String> faunaSoundIds() {
        java.util.Set<String> out = new java.util.TreeSet<>();
        for (String k : FAUNA.keySet()) out.add("fauna." + k);
        return out;
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Awaken.id(name)));
    }

    static void init(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
