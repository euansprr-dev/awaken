package dev.awaken.village.road;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.awaken.city.CityPlan;
import dev.awaken.city.SettlementPaint;
import dev.awaken.village.world.VillagePiece;
import dev.awaken.village.world.VillageStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

import java.util.function.Consumer;

/**
 * {@code awaken:clear_of_settlements} (SPEC §28.9, "Tall things keep off"): a tall feature tried here stands only where
 * nothing within its {@code reach} is land a road or a settlement uses. Those clear a few blocks over their ground, so
 * a spire they meet loses a slice and its top hangs in the air, and a neighbouring chunk's feature can land before the
 * paint as well as after it, so felling afterwards can't be made safe. Not standing there is the same in either order.
 * Goes last in a placed feature, once the position is known.
 */
public record ClearOfSettlements(int reach) implements PlacementModifier {
    public static final MapCodec<ClearOfSettlements> CODEC = Codec.intRange(0, 16).fieldOf("reach").xmap(ClearOfSettlements::new, ClearOfSettlements::reach);

    /** How far past its band a road's banks can grade ({@link RoadBanks#WIDTH}, rounded up). */
    static final int BANKS = 8;

    @Override
    public void modify(PlacementContext context, RandomSource random, BlockPos origin, Consumer<BlockPos> output) {
        if (!used(context.getLevel(), context.generator(), origin.getX(), origin.getZ(), reach)) output.accept(origin);
    }

    @Override
    public MapCodec<ClearOfSettlements> codec() {
        return CODEC;
    }

    /** Whether a road or settlement referenced from this chunk or those beside it uses land within reach of (x, z). */
    private static boolean used(WorldGenLevel level, ChunkGenerator generator, int x, int z, int reach) {
        StructureManager all = level.getLevel().structureManager();
        // on a worldgen thread only the region may be read: the server's own view would wait on the chunk being made
        StructureManager structures = level instanceof WorldGenRegion region ? all.forWorldGenRegion(region) : all;
        int r = reach + BANKS, cx = x >> 4, cz = z >> 4;
        // the same three by three that HeroWorldgen reads: only these are sure to have their references yet
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (var start : structures.startsForStructure(cx + dx, cz + dz, s -> s instanceof RoadStructure || s instanceof VillageStructure)) {
                    if (!start.isValid()) continue;
                    for (var piece : start.getPieces()) {
                        SettlementPaint.Layout layout = piece instanceof RoadPiece road ? road.paint()
                                : piece instanceof VillagePiece village ? village.plan(level, generator) : null;
                        if (layout != null && touches(layout, x, z, r)) return true;
                    }
                }
            }
        }
        return false;
    }

    /** Whether the layout uses any land within {@code r} of (x, z). Every other block is enough: nothing it paints is one wide. */
    static boolean touches(SettlementPaint.Layout layout, int x, int z, int r) {
        for (int dx = -r; dx <= r; dx += 2) {
            for (int dz = -r; dz <= r; dz += 2) {
                byte kind = layout.kind(x + dx, z + dz);
                if (kind != CityPlan.VOID && kind != CityPlan.FREE || layout.clears(x + dx, z + dz)) return true;
            }
        }
        return false;
    }
}
