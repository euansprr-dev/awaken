package dev.awaken.core.audio;

import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every sound event the audio module adds (sounds.json and subtitles in {@code src/main/merge/audio}, files rendered by
 * {@code tools/art/audio_build.py}). The five System events it re-voices ({@code signal.*}, {@code level_up},
 * {@code rank_up}) stay registered in {@link dev.awaken.core.CoreModule}.
 */
public final class AudioSounds {
    private AudioSounds() {}

    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);
    private static final Map<String, DeferredHolder<SoundEvent, SoundEvent>> BY_ID = new LinkedHashMap<>();

    /** Gate themes with a track of their own (the Abyssal Shrine has none on purpose: "no sound at all"). */
    public static final List<String> GATE_THEMES = List.of("bell_crypt", "fungal_hollow", "ember_mines", "drowned_archive",
            "clockwork_foundry", "glass_desert", "hive", "overgrown_temple", "lantern_night_market", "frost_monastery",
            "mirror_palace", "precursor_lab", "nightmare", "sky_archipelago");
    public static final int STRATA = 10;

    // System UI (the client plays these; quest_done and request_delivered can be asked for by the server)
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_ANSWER_YES = reg("ui.answer_yes");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_ANSWER_NO = reg("ui.answer_no");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_STORY = reg("ui.story");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_NOW = reg("ui.now");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_QUEST_DONE = reg("ui.quest_done");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_REQUEST_DELIVERED = reg("ui.request_delivered");

    // ambience beds
    public static final DeferredHolder<SoundEvent, SoundEvent> AMB_RIFT = reg("amb.rift");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMB_GROVE = reg("amb.grove");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMB_STAR_WHISTLE = reg("amb.star_whistle");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMB_STAR_COOLING = reg("amb.star_cooling");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMB_STAR_COOLED = reg("amb.star_cooled");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMB_WELL = reg("amb.well");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMB_RUIN_NIGHT = reg("amb.ruin_night");

    private static final List<DeferredHolder<SoundEvent, SoundEvent>> CUES = new ArrayList<>();

    static {
        for (Cue c : Cue.values()) CUES.add(reg(c.eventPath()));
        for (String theme : GATE_THEMES) reg("music.gate." + theme);
        for (int s = 1; s <= STRATA; s++) reg("music.descent." + s);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> reg(String path) {
        DeferredHolder<SoundEvent, SoundEvent> h = SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(Awaken.id(path)));
        BY_ID.put(path, h);
        return h;
    }

    public static DeferredHolder<SoundEvent, SoundEvent> cue(Cue cue) {
        return CUES.get(cue.ordinal());
    }

    /** Every event this module registers, by path ("music.cue.awakening", "amb.rift", ...). */
    public static Map<String, DeferredHolder<SoundEvent, SoundEvent>> all() {
        return Collections.unmodifiableMap(BY_ID);
    }

    static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
