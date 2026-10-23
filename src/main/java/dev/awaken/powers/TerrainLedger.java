package dev.awaken.powers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.powers.content.ConstructBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Temporary terrain changes (Terrain Rip craters, Waking Dream doors). Every block a power lifts out is written here
 * with its original state and put back after a few seconds. Saved per dimension, so a crash or restart never loses
 * terrain: overdue entries restore as soon as their chunk is loaded. Restoring never overwrites a block someone put
 * there in the meantime (only air and replaceable things like flowing water).
 */
public final class TerrainLedger extends SavedData {
    public record Entry(BlockPos pos, BlockState state, long restoreAt) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
                BlockState.CODEC.fieldOf("state").forGetter(Entry::state),
                Codec.LONG.fieldOf("at").forGetter(Entry::restoreAt)
        ).apply(i, Entry::new));
    }

    public static final Codec<TerrainLedger> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schema", 1).forGetter(l -> 1),
            Entry.CODEC.listOf().fieldOf("entries").forGetter(l -> List.copyOf(l.entries))
    ).apply(i, (schema, list) -> {
        TerrainLedger l = new TerrainLedger();
        l.entries.addAll(list);
        return l;
    }));

    public static final SavedDataType<TerrainLedger> TYPE = new SavedDataType<>(Awaken.id("powers_terrain"), TerrainLedger::new, CODEC);

    /** Silent swap: no neighbour updates, so sand above doesn't fall and torches don't pop. */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private final List<Entry> entries = new ArrayList<>();

    public TerrainLedger() {}

    public static TerrainLedger of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /** Whether a power may lift this block out temporarily. */
    public static boolean canLift(ServerLevel level, ServerPlayer by, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.isAir() || s.hasBlockEntity() || !s.getFluidState().isEmpty()) return false;
        if (s.getBlock() instanceof ConstructBlock || s.getBlock() instanceof FallingBlock) return false;
        float hardness = s.getDestroySpeed(level, pos);
        if (hardness < 0 || hardness > 5) return false;
        if (!s.isCollisionShapeFullBlock(level, pos)) return false;
        return Constructs.mayChange(by, pos);
    }

    /** Lifts a block out for {@code ticks}. Returns the original state, or null when not allowed. */
    public static BlockState lift(ServerLevel level, ServerPlayer by, BlockPos pos, int ticks) {
        if (!canLift(level, by, pos)) return null;
        BlockState s = level.getBlockState(pos);
        TerrainLedger l = of(level);
        l.entries.add(new Entry(pos.immutable(), s, level.getGameTime() + ticks));
        l.setDirty();
        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), FLAGS);
        return s;
    }

    static void tick(ServerLevel level) {
        TerrainLedger l = level.getDataStorage().get(TYPE);
        if (l == null || l.entries.isEmpty()) return;
        long now = level.getGameTime();
        boolean changed = false;
        for (int i = l.entries.size() - 1; i >= 0; i--) {
            Entry e = l.entries.get(i);
            if (e.restoreAt() > now || !level.isLoaded(e.pos())) continue;
            if (!restore(level, e, now)) {
                l.entries.set(i, new Entry(e.pos(), e.state(), now + 20));
            } else {
                l.entries.remove(i);
            }
            changed = true;
        }
        if (changed) l.setDirty();
    }

    /** Restores one entry; returns false to try again later (something is standing in the hole). */
    private static boolean restore(ServerLevel level, Entry e, long now) {
        BlockState current = level.getBlockState(e.pos());
        if (!current.isAir() && !current.canBeReplaced()) return true; // someone built here: leave it
        boolean blocked = !level.getEntitiesOfClass(LivingEntity.class, new AABB(e.pos()), x -> x.isAlive()).isEmpty();
        if (blocked && now - e.restoreAt() < 600) return false;
        level.setBlock(e.pos(), e.state(), Block.UPDATE_ALL);
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, e.state()),
                e.pos().getX() + 0.5, e.pos().getY() + 0.5, e.pos().getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.02);
        return true;
    }

    /** Restores everything loaded right now (server stopping). */
    static void restoreAll(ServerLevel level) {
        TerrainLedger l = level.getDataStorage().get(TYPE);
        if (l == null || l.entries.isEmpty()) return;
        for (int i = l.entries.size() - 1; i >= 0; i--) {
            Entry e = l.entries.get(i);
            if (!level.isLoaded(e.pos())) continue;
            BlockState current = level.getBlockState(e.pos());
            if (current.isAir() || current.canBeReplaced()) level.setBlock(e.pos(), e.state(), Block.UPDATE_ALL);
            l.entries.remove(i);
        }
        l.setDirty();
    }

    public int size() {
        return entries.size();
    }
}
