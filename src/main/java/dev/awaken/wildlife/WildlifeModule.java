package dev.awaken.wildlife;

import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Wild life, the common half (SPEC "Wild life"): the client-only body every wild animal is drawn with, the voices they
 * call with (tools/art/specs/audio_wild.py), the density tables ({@link Density}) and the module's GameTests. Everything
 * that moves lives on the client ({@code wildlife.client}); the server registers the type and never makes one.
 */
public final class WildlifeModule {
    private WildlifeModule() {}

    static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(Awaken.MODID);
    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);

    /** The wild's small animals: made and moved on the client only (wildlife.client.Wildlife), never saved. */
    public static final DeferredHolder<EntityType<?>, EntityType<WildCritter>> CRITTER = ENTITIES.registerEntityType("wild_critter",
            WildCritter::new, MobCategory.MISC, b -> b.sized(0.4f, 0.4f).clientTrackingRange(0).noSave().noSummon());

    // the voices, mono and placed: a calling bird is a bird on a branch
    public static final DeferredHolder<SoundEvent, SoundEvent> SONG_ROBIN = sound("wild.song.robin"), SONG_TIT = sound("wild.song.tit"),
            SONG_FINCH = sound("wild.song.finch"), SONG_WREN = sound("wild.song.wren"), CHIP = sound("wild.chip"),
            FLUSH = sound("wild.flush"), WINGS_BIG = sound("wild.wings.big"), DRUM = sound("wild.woodpecker.drum"),
            TAP = sound("wild.woodpecker.tap"), CAW = sound("wild.crow.caw"), RAPTOR = sound("wild.raptor.call"),
            HOOT = sound("wild.owl.hoot"), QUACK = sound("wild.duck.quack"), CROAK_HERON = sound("wild.heron.croak"),
            CHATTER = sound("wild.squirrel.chatter"), PLOP = sound("wild.frog.plop"), CRICKET = sound("wild.cricket"),
            RUSTLE = sound("wild.rustle");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Awaken.id(name)));
    }

    public static void init(IEventBus modBus) {
        ENTITIES.register(modBus);
        SOUNDS.register(modBus);
        WildlifeTests.register();
    }
}
