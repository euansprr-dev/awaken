package dev.awaken.core.audio;

import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.function.Supplier;

/** System UI sounds the server can ask one player's client to play ({@link AudioCues#ui}). */
public enum UiSound {
    /** A Directive or the Daily Regimen completed. */
    QUEST_DONE(() -> AudioSounds.UI_QUEST_DONE, 0.6F),
    /** A request delivered at the Anchor. */
    REQUEST_DELIVERED(() -> AudioSounds.UI_REQUEST_DELIVERED, 0.7F);

    private final Supplier<DeferredHolder<SoundEvent, SoundEvent>> event;
    public final float volume;

    UiSound(Supplier<DeferredHolder<SoundEvent, SoundEvent>> event, float volume) {
        this.event = event;
        this.volume = volume;
    }

    public DeferredHolder<SoundEvent, SoundEvent> event() {
        return event.get();
    }

    public static UiSound byOrdinal(int i) {
        UiSound[] v = values();
        return i >= 0 && i < v.length ? v[i] : null;
    }
}
