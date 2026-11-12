package dev.awaken.enchant;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lantern (SPEC §45.16), the first of the new words: a helmet that lights the dark around its wearer. Where it is
 * darker than the word's own light (8, 10, 12 for I, II, III), one {@link LanternGlowBlock} stands at the wearer's head
 * and is moved when they have gone two blocks; in daylight or by a brighter fire there is none. Server only: the light
 * is a real block, so a friend sees by it too. Asked every fourth tick a wearer; no allocation unless the light moves.
 */
final class Lantern {
    private Lantern() {}

    /** The light each level sheds. */
    static final int[] LIGHT = {0, 8, 10, 12};
    /** How far the wearer goes before the light follows, and how far behind it may be left before it is put out. */
    private static final int FOLLOW = 2, LEFT = 4;

    private record Spot(ResourceKey<Level> dim, BlockPos pos) {}

    private static final Map<UUID, Spot> SPOTS = new HashMap<>();

    /** The Lantern level on what this player wears on their head (0: none). */
    static int level(Player p) {
        return Math.min(3, EnchantEffects.levelOf(p.getItemBySlot(EquipmentSlot.HEAD), EnchantContent.LANTERN.get()));
    }

    static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || (p.tickCount & 3) != 0) return;
        Spot at = SPOTS.get(p.getUUID());
        int lv = p.isAlive() && !p.isSpectator() ? level(p) : 0;
        ServerLevel level = (ServerLevel) p.level();
        if (lv <= 0) {
            if (at != null) clear(level.getServer(), p.getUUID());
            return;
        }
        if (at != null && at.dim != level.dimension()) {
            clear(level.getServer(), p.getUUID());
            at = null;
        }
        BlockPos head = BlockPos.containing(p.getX(), p.getEyeY(), p.getZ());
        int gone = at == null ? Integer.MAX_VALUE : at.pos.distManhattan(head);
        if (gone < FOLLOW && ours(level, at.pos, lv)) return;
        BlockState there = level.getBlockState(head);
        boolean water = there.is(Blocks.WATER) && there.getFluidState().isSource();
        boolean free = there.isAir() || water || there.is(EnchantContent.LANTERN_GLOW.get());
        if (!free) {
            // the head is in a doorway, a bush, a ladder: the light waits where it is, unless it has been left behind
            if (at != null && gone >= LEFT) clear(level.getServer(), p.getUUID());
            return;
        }
        int light = LIGHT[lv];
        // what the old glow lends this place doesn't count as light of the world's own
        int own = at == null ? 0 : Math.max(0, light - gone);
        if (!dark(level.getBrightness(LightLayer.SKY, head) - level.getSkyDarken(), level.getBrightness(LightLayer.BLOCK, head), own, light)) {
            if (at != null) clear(level.getServer(), p.getUUID());
            return;
        }
        if (at != null && !at.pos.equals(head)) put(level, at.pos, null);
        boolean waterlogged = water || there.is(EnchantContent.LANTERN_GLOW.get()) && there.getValue(LanternGlowBlock.WATERLOGGED);
        level.setBlock(head, EnchantContent.LANTERN_GLOW.get().defaultBlockState().setValue(LanternGlowBlock.LEVEL, lv)
                .setValue(LanternGlowBlock.WATERLOGGED, waterlogged), Block.UPDATE_CLIENTS);
        SPOTS.put(p.getUUID(), new Spot(level.dimension(), head));
        // it catches as you step into the dark: one soft sound, not one a step
        if (at == null) level.playSound(null, head, SoundEvents.LANTERN_PLACE, SoundSource.PLAYERS, 0.35F, 1.25F);
    }

    /**
     * Whether a place is dark enough for the word's light: the sky gives less than it, and so does every fire that isn't
     * the old glow ({@code own}: what the old glow still lends here; block light no brighter than that is its alone).
     */
    static boolean dark(int sky, int block, int own, int light) {
        return sky < light && (block <= own || block < light);
    }

    private static boolean ours(ServerLevel level, BlockPos pos, int lv) {
        BlockState s = level.getBlockState(pos);
        return s.is(EnchantContent.LANTERN_GLOW.get()) && s.getValue(LanternGlowBlock.LEVEL) == lv;
    }

    /** Puts a glow out (state null), if one is still there. */
    private static void put(ServerLevel level, BlockPos pos, @org.jspecify.annotations.Nullable BlockState state) {
        if (!dev.awaken.core.api.Ready.loaded(level, pos)) return;
        BlockState s = level.getBlockState(pos);
        if (s.is(EnchantContent.LANTERN_GLOW.get())) level.setBlock(pos, state != null ? state : LanternGlowBlock.gone(s), Block.UPDATE_CLIENTS);
    }

    /** This player's light goes out: the helmet came off, they left, died or changed worlds. */
    static void clear(MinecraftServer server, UUID id) {
        Spot at = SPOTS.remove(id);
        if (at == null) return;
        ServerLevel level = server.getLevel(at.dim);
        if (level != null) put(level, at.pos, null);
    }

    static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) clear(p.level().getServer(), p.getUUID());
    }

    /** Statics cleared with the server; any glow in a sleeping chunk clears itself when it wakes. */
    static void onStopping(ServerStoppingEvent e) {
        for (UUID id : SPOTS.keySet().toArray(new UUID[0])) clear(e.getServer(), id);
        SPOTS.clear();
    }
}
