package dev.awaken.bells.client;

import dev.awaken.bells.BellCatalog;
import dev.awaken.bells.BellContent;
import dev.awaken.bells.BellNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Plays a tower's song as it rings (SPEC §43.7): a hundred steps, four ticks apart. Bell #n's note plays at step n if
 * the home holds it, else a dull knock at that pitch, so the song's holes are heard where they fall. Sounds come from
 * the tower and carry 256 blocks; one song plays at a time, a newer one taking over.
 */
public final class SongPlayer {
    private SongPlayer() {}

    private static BellNet.@Nullable Song song;
    private static int step, wait;
    /** The song is played back at the listener, not from a tower (the Book's "Play the song"). */
    private static boolean here;

    public static void start(BellNet.Song s, boolean atListener) {
        song = s;
        here = atListener;
        step = 0;
        wait = 0;
    }

    public static boolean playing() {
        return song != null;
    }

    public static void stop() {
        song = null;
    }

    static void tick(ClientTickEvent.Post e) {
        BellNet.Song s = song;
        if (s == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            song = null;
            return;
        }
        if (mc.isPaused() || wait-- > 0) return;
        wait = BellCatalog.STEP - 1;
        double x = here ? mc.player.getX() : s.at().getX() + 0.5, y = here ? mc.player.getEyeY() : s.at().getY() + 0.5,
                z = here ? mc.player.getZ() : s.at().getZ() + 0.5;
        boolean has = s.has(step);
        SoundEvent snd = has ? BellContent.note(step) : BellContent.GAP.get();
        float vol = here ? (has ? 0.8F : 0.35F) : has ? 16F : 6F;
        mc.getSoundManager().play(new SimpleSoundInstance(snd, SoundSource.BLOCKS, vol, has ? 1F : BellContent.pitch(step), mc.player.getRandom(), x, y, z));
        if (++step >= 100) song = null;
    }
}
