package dev.awaken.together;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** What Together registers (SPEC §70): a player's fights setting, and seven sounds built from Minecraft's own recordings. */
public final class TogetherContent {
    private TogetherContent() {}

    /** My fights: Full or Gentle. Kept through death and logout. */
    public record FightsSetting(boolean gentle, int schemaVersion) {
        public static final FightsSetting FULL = new FightsSetting(false, 1);
        public static final MapCodec<FightsSetting> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.BOOL.optionalFieldOf("gentle", false).forGetter(FightsSetting::gentle),
                Codec.INT.optionalFieldOf("schemaVersion", 1).forGetter(FightsSetting::schemaVersion)
        ).apply(i, FightsSetting::new));
    }

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Awaken.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);

    public static final Supplier<AttachmentType<FightsSetting>> FIGHTS = ATTACHMENTS.register("fights",
            () -> AttachmentType.builder(() -> FightsSetting.FULL).serialize(FightsSetting.MAP_CODEC, FightsSetting::gentle).copyOnDeath().build());

    static final DeferredHolder<SoundEvent, SoundEvent> JOIN = sound("together.join");
    static final DeferredHolder<SoundEvent, SoundEvent> LEAVE = sound("together.leave");
    static final DeferredHolder<SoundEvent, SoundEvent> SECOND_WIND = sound("together.second_wind");
    static final DeferredHolder<SoundEvent, SoundEvent> UP = sound("together.up");
    static final DeferredHolder<SoundEvent, SoundEvent> DOWN = sound("together.down");
    static final DeferredHolder<SoundEvent, SoundEvent> LIFT = sound("together.lift");
    public static final DeferredHolder<SoundEvent, SoundEvent> PING = sound("together.ping");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String id) {
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(Awaken.id(id)));
    }

    static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        SOUNDS.register(modBus);
    }
}
