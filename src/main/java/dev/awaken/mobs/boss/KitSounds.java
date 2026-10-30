package dev.awaken.mobs.boss;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A kit body's sounds (SPEC §35.8): the sets of its move table's palette, each a few layers played together at the
 * body (a crack, a thud and a rumble make one stomp). Played on the server, so everyone near hears the same blow.
 */
public final class KitSounds {
    private KitSounds() {}

    /** Plays the set at the body, every layer drifting a little in pitch; false when the body has no such set. */
    public static boolean play(Entity at, KitMoves.Table table, String set, float volume) {
        List<KitMoves.Layer> layers = table.sounds().get(set);
        if (layers == null || layers.isEmpty()) return false;
        float drift = 0.95F + at.getRandom().nextFloat() * 0.1F;
        for (KitMoves.Layer l : layers) {
            SoundEvent ev = resolve(l.sound());
            if (ev != null) at.level().playSound(null, at.getX(), at.getY() + at.getBbHeight() * 0.5, at.getZ(), ev,
                    SoundSource.HOSTILE, l.volume() * volume, l.pitch() * drift);
        }
        return true;
    }

    public static @Nullable SoundEvent resolve(String id) {
        Identifier key = Identifier.tryParse(id);
        return key == null ? null : BuiltInRegistries.SOUND_EVENT.getValue(key);
    }
}
