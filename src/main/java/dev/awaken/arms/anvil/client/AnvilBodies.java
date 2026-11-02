package dev.awaken.arms.anvil.client;

import dev.awaken.arms.anvil.AnvilSong;
import dev.awaken.arms.anvil.AnvilWork;
import dev.awaken.arms.anvil.WarAnvilBlockEntity;
import dev.awaken.core.api.PlayerClips;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The smith as everyone sees them in third person (SPEC §46.7): bent over the anvil, the hammer arm rising and falling on
 * each strike and tap, both hands on the tongs at the Forge's mouth and in the trough, winding the grip's cord (clips
 * from {@code tools/art/specs/anvil_moves.py}, through {@link PlayerClips}). Without the animation library, vanilla's arm
 * swing on each strike. Read from the anvil's synced work as it is drawn; let go the moment that work ends.
 */
final class AnvilBodies {
    private AnvilBodies() {}

    static final String OVER = "arms_anvil", STRIKE = "arms_anvil_strike", TAP = "arms_anvil_tap", HEAT = "arms_anvil_heat",
            QUENCH = "arms_anvil_quench", BIND = "arms_anvil_bind";

    private static final class Smith {
        int entity, stage, beats;
        BlockPos anvil = BlockPos.ZERO;
        @Nullable String clip;
        long strikeEnd;
    }

    private static final Map<UUID, Smith> SMITHS = new HashMap<>();

    /** A War Anvil with work on it is drawn this frame: its smith takes the pose of the stage, and a new beat its blow. */
    static void seen(Level level, BlockPos anvil, AnvilWork w) {
        Player p = level.getPlayerByUUID(w.forger);
        if (p == null) return;
        int beats = w.beats().size();
        Smith s = SMITHS.get(w.forger);
        if (s == null || s.entity != p.getId()) {
            if (s != null && s.clip != null) PlayerClips.stop(s.entity);
            s = new Smith();
            s.entity = p.getId();
            s.stage = w.stage;
            s.beats = beats;
            SMITHS.put(w.forger, s);
        }
        s.anvil = anvil;
        long now = level.getGameTime();
        boolean struck = w.stage == s.stage && beats > s.beats && blows(w.stage);
        s.stage = w.stage;
        s.beats = beats;
        if (struck) {
            String blow = w.stage == AnvilSong.SHAPE ? STRIKE : TAP;
            if (PlayerClips.play(s.entity, blow, 1)) {
                s.clip = blow;
                s.strikeEnd = now + (w.stage == AnvilSong.SHAPE ? 6 : 4);
            } else {
                p.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
            }
            return;
        }
        if (now < s.strikeEnd) return;
        String want = pose(w.stage);
        if (!want.equals(s.clip) && PlayerClips.play(s.entity, want, s.clip == null ? 4 : 2)) s.clip = want;
    }

    /** Each tick: a smith whose anvil no longer holds their work (drawn, broken, left) stands up again. */
    static void tick() {
        if (SMITHS.isEmpty()) return;
        Level level = Minecraft.getInstance().level;
        for (Iterator<Map.Entry<UUID, Smith>> it = SMITHS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Smith> e = it.next();
            Smith s = e.getValue();
            AnvilWork w = level != null && level.isLoaded(s.anvil) && level.getBlockEntity(s.anvil) instanceof WarAnvilBlockEntity be ? be.work() : null;
            if (w != null && w.forger.equals(e.getKey())) continue;
            if (s.clip != null) PlayerClips.stop(s.entity);
            it.remove();
        }
    }

    static void clear() {
        SMITHS.clear();
    }

    /** The stages whose beats are blows of the hammer: the draw-out's strikes, the hilt's taps, the knap's flakes. */
    private static boolean blows(int stage) {
        return stage == AnvilSong.SHAPE || stage == AnvilSong.HILT || stage == AnvilSong.KNAP;
    }

    private static String pose(int stage) {
        return switch (stage) {
            case AnvilSong.HEAT -> HEAT;
            case AnvilSong.QUENCH -> QUENCH;
            case AnvilSong.BIND -> BIND;
            default -> OVER;
        };
    }
}
