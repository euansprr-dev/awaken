package dev.awaken.bells.client;

import dev.awaken.bells.BellContent;
import dev.awaken.bells.BellNet;
import dev.awaken.bells.SongPlan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Plays a home's song as it rings (SPEC §43.16), as {@link SongPlan} lays it out: each bell struck from where it hangs
 * (the tower's spot for one the packet doesn't place), so the song comes from the bells you can see. A far bell's note
 * is heard from its bearing, fainter the further it is. Missing bells are silence. One song at a time, a newer one
 * taking over; the Book's "Play your song" plays it at the listener instead.
 */
public final class SongPlayer {
    private SongPlayer() {}

    /** Past this, a bell is heard from its bearing at {@link #FAR_AT} blocks off rather than from where it is. */
    static final double FAR = 200, FAR_AT = 24;

    private static BellNet.@Nullable Song song;
    private static SongPlan.@Nullable Plan plan;
    private static int tick, next;
    /** The song is played back at the listener, not from its bells (the Book's "Play your song"). */
    private static boolean here;
    /** When each bell's spot was last struck (client game time), for the renderers' swing. */
    private static final Map<Long, Long> STRUCK = new HashMap<>();

    public static void start(BellNet.Song s, boolean atListener) {
        song = s;
        plan = SongPlan.of(s::has);
        here = atListener;
        tick = 0;
        next = 0;
    }

    public static boolean playing() {
        return song != null;
    }

    public static void stop() {
        song = null;
        plan = null;
    }

    /** Game time the bell hanging at {@code pos} was last struck by a song, or a long time ago. */
    public static long struck(BlockPos pos) {
        return STRUCK.getOrDefault(pos.asLong(), Long.MIN_VALUE / 2);
    }

    static void tick(ClientTickEvent.Post e) {
        BellNet.Song s = song;
        SongPlan.Plan p = plan;
        if (s == null || p == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            stop();
            return;
        }
        if (mc.isPaused()) return;
        while (next < p.strikes().size() && p.strikes().get(next).tick() <= tick) strike(mc, s, p.strikes().get(next++).no());
        if (++tick > p.length()) stop();
    }

    private static void strike(Minecraft mc, BellNet.Song s, int no) {
        if (here) {
            Vec3 at = mc.player.getEyePosition();
            mc.getSoundManager().play(new SimpleSoundInstance(BellContent.note(no), SoundSource.BLOCKS, 0.8F, 1F, mc.player.getRandom(), at.x, at.y, at.z));
            return;
        }
        BlockPos where = s.place(no);
        if (mc.level != null) STRUCK.put(where.asLong(), mc.level.getGameTime());
        Vec3 at = Vec3.atCenterOf(where), ear = mc.player.getEyePosition();
        double d = at.distanceTo(ear);
        float vol = 16F;
        if (d > FAR) {
            // too far to carry from where it is: heard from its bearing, fainter the further it is
            at = ear.add(at.subtract(ear).normalize().scale(FAR_AT));
            vol = (float) Math.max(1.6, 4.0 * FAR / d);
        }
        mc.getSoundManager().play(new SimpleSoundInstance(BellContent.note(no), SoundSource.BLOCKS, vol, 1F, mc.player.getRandom(), at.x, at.y, at.z));
    }
}
