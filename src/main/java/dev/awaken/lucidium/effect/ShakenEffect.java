package dev.awaken.lucidium.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Shaken: what dying in a dream leaves behind. A little slower for a little while, nothing more. */
public class ShakenEffect extends MobEffect {
    public ShakenEffect() {
        super(MobEffectCategory.HARMFUL, 0xB48CC8);
    }
}
