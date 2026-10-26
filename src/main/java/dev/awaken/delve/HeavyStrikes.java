package dev.awaken.delve;

import dev.awaken.core.api.CityHooks;
import dev.awaken.core.api.GateHooks;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.core.api.WardHooks;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The heavy strike (PLAN 7.4, SPEC §28.3): sneak and hold the attack with a pick or mattock to charge it on Arms' heavy
 * timing (the Maul's: it starts at {@link #MIN} ticks, glints at {@link #GLINT}), let go to bore three blocks along your
 * aim. The server checks it all: a mining tool in hand, a charge it could have held, a breath since the last one,
 * enough food, and each block breakable by that tool and outside a ward, the city, a gate and anyone else's Hearth; the
 * break itself goes through the game's own path, so every other protection still has its say. It costs three times the
 * durability (twice on the glint) and real hunger.
 */
public final class HeavyStrikes {
    private HeavyStrikes() {}

    /** The Maul's charge (data/awaken/arms/archetypes.json): starts at 6 ticks, glints at 20, held at most 30. */
    public static final int MIN = 6, GLINT = 20, MAX = 30;
    public static final int DEPTH = 3, COOLDOWN = 14;
    /** Durability per block on top of the break's own: x3 in all, x2 on the glint. */
    public static final int EXTRA_WEAR = 2, EXTRA_WEAR_GLINT = 1;
    public static final float EXHAUSTION_PER_BLOCK = 1.0F;
    public static final int MIN_FOOD = 6;

    private static final Map<UUID, Long> LAST = new HashMap<>();

    public static boolean isMiningTool(ItemStack s) {
        return !s.isEmpty() && s.is(DelveContent.MINING_TOOLS);
    }

    /** The client let go after {@code charge} ticks. Returns the blocks bored. */
    static List<BlockPos> release(ServerPlayer p, int charge, boolean perfect) {
        ServerLevel level = p.level();
        ItemStack tool = p.getMainHandItem();
        if (!isMiningTool(tool) || charge < MIN || p.isSpectator()) return List.of();
        long now = level.getGameTime();
        Long last = LAST.get(p.getUUID());
        if (last != null && now - last < COOLDOWN) return List.of();
        if (!p.isCreative() && p.getFoodData().getFoodLevel() < MIN_FOOD) {
            p.sendOverlayMessage(Component.literal("Too hungry to swing that hard").withColor(0xE8C9A0));
            return List.of();
        }
        if (GateHooks.inGate(p)) return List.of();
        LAST.put(p.getUUID(), now);
        boolean glint = perfect && Math.abs(charge - GLINT) <= 2;
        BlockHitResult hit = aim(p);
        if (hit == null) return List.of();
        Direction into = hit.getDirection().getOpposite();
        List<BlockPos> bored = new ArrayList<>();
        List<Integer> states = new ArrayList<>();
        BlockPos at = hit.getBlockPos();
        for (int i = 0; i < DEPTH; i++, at = at.relative(into)) {
            BlockState s = level.getBlockState(at);
            if (s.isAir() || !mayBore(level, p, at, s, tool)) break;
            int id = Block.getId(s);
            if (!p.gameMode.destroyBlock(at)) break;   // the game's own break: drops, stats, every protection's say
            bored.add(at);
            states.add(id);
            if (!p.isCreative()) {
                tool = p.getMainHandItem();
                if (!tool.isEmpty()) tool.hurtAndBreak(glint ? EXTRA_WEAR_GLINT : EXTRA_WEAR, p, EquipmentSlot.MAINHAND);
                p.causeFoodExhaustion(EXHAUSTION_PER_BLOCK);
            }
            if (!isMiningTool(p.getMainHandItem())) break;   // it broke on the way in
        }
        if (bored.isEmpty()) return bored;
        LongArrayList longs = new LongArrayList();
        for (BlockPos b : bored) longs.add(b.asLong());
        BlockPos c = bored.getFirst();
        level.playSound(null, c, DelveContent.HEAVY_STRIKE.get(), SoundSource.PLAYERS, 1.3F, glint ? 1.12F : 0.95F);
        PacketDistributor.sendToPlayersNear(level, null, c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5, 48,
                new DelveNet.HeavyFx(p.getId(), longs.toLongArray(), states, glint));
        dev.awaken.progression.Progression.count(p, "awaken:heavy_strikes");
        return bored;
    }

    /** Where the strike lands: the block in reach along the player's aim. */
    static BlockHitResult aim(ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getViewVector(1F).scale(p.blockInteractionRange()));
        BlockHitResult hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    /** One block of the bore: one the tool takes (and drops), outside every place a strike mustn't reach. */
    static boolean mayBore(ServerLevel level, ServerPlayer p, BlockPos pos, BlockState s, ItemStack tool) {
        float hardness = s.getDestroySpeed(level, pos);
        if (hardness < 0 || s.hasBlockEntity()) return false;
        if (s.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(s)) return false;
        if (tool.getDestroySpeed(s) <= 1F && hardness > 0) return false;   // a pick doesn't bore through wood it can't bite
        if (CityHooks.inCity(level, pos) || WardHooks.at(level, pos).isPresent()) return false;
        if (HearthHooks.inside(level, pos)) {
            var anchor = HearthHooks.nearest(level, pos, 96);
            if (anchor.isEmpty() || !HearthHooks.belongs(p, anchor.get())) return false;   // someone else's Hearth
        }
        return true;
    }

    static void forget(UUID player) {
        LAST.remove(player);
    }

    static void clear() {
        LAST.clear();
    }
}
