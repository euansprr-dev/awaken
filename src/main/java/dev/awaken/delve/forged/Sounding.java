package dev.awaken.delve.forged;

import dev.awaken.delve.DelveNet;
import dev.awaken.delve.WakingPick;
import dev.awaken.delve.api.Lodes;
import dev.awaken.delve.api.Pick;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Sounding (SPEC §49.5): miners tapped the roof and listened. Stand still, sneak and tap rock: the server walks 26 lines
 * out through the rock, and says how far along each one the rock turned hollow, and which lode (if any) is near enough
 * to answer. The client turns that into sound from that side and dust off that part of the wall. Nothing is drawn through
 * the rock, and no block is named but the lode's own direction.
 */
public final class Sounding {
    private Sounding() {}

    public static final int RADIUS = 12, LONG_RADIUS = 24, REST = 20 * 20;
    public static final float SOUNDER = 1.5F;
    /** A hollow needs this many open neighbours to be a cave, not a one-block gap in the rock. */
    private static final int ROOMY = 2;

    private static final Map<UUID, Long> LAST = new HashMap<>();

    /** How far this pick carries. */
    public static int radius(ItemStack s) {
        int r = Pick.has(s, Pick.Gift.LONG_SOUNDING) ? LONG_RADIUS : RADIUS;
        return "sounder".equals(Pick.form(s)) ? Math.round(r * SOUNDER) : r;
    }

    /** The tap. False when the smith moved or the rock is still ringing (they're told which). */
    public static boolean tap(ServerPlayer p, BlockPos at, Direction face) {
        if (p.getKnownMovement().horizontalDistanceSqr() > 0.0025) {
            p.sendOverlayMessage(Component.literal("Hold still and the rock will answer").withColor(0xC8C2B0));
            return false;
        }
        ServerLevel level = p.level();
        long now = level.getGameTime();
        Long was = LAST.get(p.getUUID());
        if (was != null && now >= was && now - was < REST) {
            p.sendOverlayMessage(Component.literal("The rock is still ringing (" + ((REST - (now - was)) / 20 + 1) + " s)").withColor(0xC8C2B0));
            return false;
        }
        LAST.put(p.getUUID(), now);
        int radius = radius(p.getMainHandItem());
        byte[] hollow = listen(level, at, face, radius);
        Optional<Lodes.View> lode = WakingPick.nearest(level, at, radius);
        level.playSound(null, at, DelveForged.SOUNDING_TAP.get(), SoundSource.PLAYERS, 0.9F, 0.95F + level.getRandom().nextFloat() * 0.1F);
        DelveNet.send(p, new DelveNet.PickSounding(at.asLong(), face.ordinal(), radius, hollow,
                lode.map(v -> v.ore().ordinal()).orElse(-1), lode.map(v -> v.nearest().asLong()).orElse(0L)));
        Progression.count(p, "awaken:delve/soundings");
        return true;
    }

    /** One byte per direction ({@link #direction}): how far along it the rock turned hollow, 0 for nothing in reach. */
    public static byte[] listen(ServerLevel level, BlockPos from, Direction face, int radius) {
        byte[] out = new byte[DIRECTIONS];
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int k = 0; k < DIRECTIONS; k++) {
            int[] d = direction(k);
            // never out through the face you tapped: that air is the cave you're standing in
            if (d[0] * face.getStepX() + d[1] * face.getStepY() + d[2] * face.getStepZ() > 0) continue;
            float len = (float) Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
            for (int r = 1; r <= radius; r++) {
                m.set(from.getX() + Math.round(d[0] / len * r), from.getY() + Math.round(d[1] / len * r), from.getZ() + Math.round(d[2] / len * r));
                if (!dev.awaken.core.api.Ready.loaded(level, m)) break;
                if (open(level.getBlockState(m)) && roomy(level, m)) {
                    out[k] = (byte) r;
                    break;
                }
            }
        }
        return out;
    }

    private static boolean open(BlockState s) {
        return !s.isSolid();
    }

    private static boolean roomy(ServerLevel level, BlockPos at) {
        int n = 0;
        for (Direction d : Direction.values()) if (open(level.getBlockState(at.relative(d))) && ++n >= ROOMY) return true;
        return false;
    }

    public static final int DIRECTIONS = 26;

    /** Direction {@code k} of the 26 round a cube: both sides read it the same. */
    public static int[] direction(int k) {
        int n = 0;
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            if (x == 0 && y == 0 && z == 0) continue;
            if (n++ == k) return new int[]{x, y, z};
        }
        return new int[]{0, -1, 0};
    }

    public static void forget(UUID id) {
        LAST.remove(id);
    }

    public static void clear() {
        LAST.clear();
    }
}
