package dev.awaken.citizens.client;

import dev.awaken.citizens.CitizensContent;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * The city's sounds the client plays itself (M4 B3b): the animals' voices and the soundscape's one-shots. Ours are
 * synthesised (tools/art/specs/audio_city_life.py; registered in CitizensContent, described in src/main/merge/citizens/assets/awaken/sounds.json); where the
 * game already has the right voice (a cat, a dog, a horse's hooves) it is referenced by id, never copied.
 */
final class CitySounds {
    private CitySounds() {}

    private static SoundEvent vanilla(String path) {
        return SoundEvent.createVariableRangeEvent(Identifier.withDefaultNamespace(path));
    }

    static final SoundEvent PIGEON_FLUTTER = CitizensContent.PIGEON_FLUTTER.get(), PIGEON_COO = CitizensContent.PIGEON_COO.get(),
            GULL_CALL = CitizensContent.GULL_CALL.get(), CART_ROLL = CitizensContent.CART_ROLL.get(), HARNESS_BELLS = CitizensContent.CART_BELLS.get(),
            OARLOCK = CitizensContent.BOAT.get();
    static final SoundEvent CAT_MEOW = vanilla("entity.cat.ambient"), CAT_PURR = vanilla("entity.cat.purr"),
            DOG_BARK = vanilla("entity.wolf.ambient"), HOOF = vanilla("entity.horse.step"), HORSE_SNORT = vanilla("entity.horse.breathe");
}
