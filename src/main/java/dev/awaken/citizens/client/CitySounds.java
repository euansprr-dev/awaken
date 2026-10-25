package dev.awaken.citizens.client;

import dev.awaken.Awaken;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * The city's sounds the client plays itself (M4 B3b): the animals' voices and the soundscape's one-shots. Ours are
 * synthesised (tools/art/specs/audio_city.py, events in src/main/merge/citizens/assets/awaken/sounds.json); where the
 * game already has the right voice (a cat, a dog, a horse's hooves) it is referenced by id, never copied.
 */
final class CitySounds {
    private CitySounds() {}

    private static SoundEvent ours(String path) {
        return SoundEvent.createVariableRangeEvent(Awaken.id(path));
    }

    private static SoundEvent vanilla(String path) {
        return SoundEvent.createVariableRangeEvent(Identifier.withDefaultNamespace(path));
    }

    static final SoundEvent PIGEON_FLUTTER = ours("city.pigeon.flutter"), PIGEON_COO = ours("city.pigeon.coo"),
            GULL_CALL = ours("city.gull.call"), CART_ROLL = ours("city.cart.roll"), HARNESS_BELLS = ours("city.cart.bells");
    static final SoundEvent CAT_MEOW = vanilla("entity.cat.ambient"), CAT_PURR = vanilla("entity.cat.purr"),
            DOG_BARK = vanilla("entity.wolf.ambient"), HOOF = vanilla("entity.horse.step"), HORSE_SNORT = vanilla("entity.horse.breathe");
}
