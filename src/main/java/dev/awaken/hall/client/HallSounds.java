package dev.awaken.hall.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** What the Hall sounds like (SPEC §52): Minecraft's own recordings by name, nothing built. Iron, chain and fire. */
public final class HallSounds {
    private HallSounds() {}

    private static void ui(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    /** You come in: a fire, low. */
    static void open() {
        ui(SoundEvents.FURNACE_FIRE_CRACKLE, .7F, .5F);
    }

    /** Something is brought close. */
    static void reach() {
        ui(SoundEvents.CHAIN_PLACE, .85F, .55F);
    }

    /** A different rank, rite, stone or choice. */
    static void rank() {
        ui(SoundEvents.ARMOR_EQUIP_IRON.value(), 1.15F, .5F);
    }

    /** Back out. */
    static void back() {
        ui(SoundEvents.IRON_TRAPDOOR_OPEN, .8F, .3F);
    }

    /** The fire rising under a held press, in three steps. */
    static void rising(int step) {
        ui(SoundEvents.FURNACE_FIRE_CRACKLE, .85F + step * .25F, .4F + step * .15F);
    }

    /** The weapon coming down onto the anvil. */
    static void set() {
        ui(SoundEvents.ANVIL_LAND, 1.2F, .22F);
    }

    /** It's made. */
    static void landed() {
        ui(SoundEvents.ANVIL_LAND, .85F, .5F);
        ui(SoundEvents.HEAVY_CORE_PLACE, .8F, .8F);
    }
}
