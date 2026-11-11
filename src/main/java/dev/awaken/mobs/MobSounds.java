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
    /** A boss's finishing blow landing (SPEC §67.4): Minecraft's own heaviest impact, by name. */
    public static final DeferredHolder<SoundEvent, SoundEvent> FINISH_BLOW = sound("mobs.finish.blow");
    public static final DeferredHolder<SoundEvent, SoundEvent> RISE = sound("mobs.rise");
    public static final DeferredHolder<SoundEvent, SoundEvent> CORD_BITE = sound("mobs.cord_bite");
    public static final DeferredHolder<SoundEvent, SoundEvent> LISTENER_HEAR = sound("mobs.listener_hear");

    /**
     * The carriers (SPEC §47.2): each sounds from where it is as it comes. The game's own recordings (tools/art/specs/audit_rest.py, SPEC §66.7);
     * sounds.json in src/main/merge/mobs_fights. The loops play from mobs.client.CarrierView and the shot renderer.
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> SHOT_RELEASE = sound("mobs.fights.shot_release");
    /** A beast gathering for a told bite (SPEC §47.2). */
    public static final DeferredHolder<SoundEvent, SoundEvent> GROWL = sound("mobs.fights.growl");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHOT_HUM = sound("mobs.fights.shot_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOLLEY_THROW = sound("mobs.fights.volley_throw");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOLLEY_FALL = sound("mobs.fights.volley_fall");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOLLEY_IMPACT = sound("mobs.fights.volley_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> WAVE_ROLL = sound("mobs.fights.wave_roll");
    public static final DeferredHolder<SoundEvent, SoundEvent> LOCK = sound("mobs.fights.lock");
    public static final DeferredHolder<SoundEvent, SoundEvent> GROUND_RUN = sound("mobs.fights.ground_run");
    public static final DeferredHolder<SoundEvent, SoundEvent> BEAM_FIRE = sound("mobs.fights.beam_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> PULL_DRAW = sound("mobs.fights.pull_draw");
    public static final DeferredHolder<SoundEvent, SoundEvent> FIELD_HISS = sound("mobs.fights.field_hiss");

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

    /**
     * The gate monsters' own voices, {@code monster.<id>.<kind>} (SPEC §60): ambient, hurt and death for every monster
     * on a body of its own, step for the ones that walk, and its fight's two (§60.8): wind as it gathers a blow, blow as
     * it lets go. Each points at Minecraft's own recordings by name, re-pitched, in src/main/merge/mobs_monsters
     * (written by tools/art/specs/gate_monsters.py, which also writes the list of the ones that exist:
     * {@code mobs.def.GateVoices}).
     */
    public static final java.util.List<String> MONSTER_KINDS = java.util.List.of("ambient", "hurt", "death", "step", "wind", "blow");
    private static final java.util.Map<String, DeferredHolder<SoundEvent, SoundEvent>> MONSTER = monsterSounds();

    private static java.util.Map<String, DeferredHolder<SoundEvent, SoundEvent>> monsterSounds() {
        java.util.Map<String, DeferredHolder<SoundEvent, SoundEvent>> m = new java.util.HashMap<>();
        for (String key : dev.awaken.mobs.def.GateVoices.KEYS) m.put(key, sound("monster." + key));
        return java.util.Map.copyOf(m);
    }

    /** A gate monster's own sound of this kind, or null (then its fallback model's voice speaks). */
    public static @org.jspecify.annotations.Nullable SoundEvent monster(String id, String kind) {
        DeferredHolder<SoundEvent, SoundEvent> h = MONSTER.get(id + "." + kind);
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
