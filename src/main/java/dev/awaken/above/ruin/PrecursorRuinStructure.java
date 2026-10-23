package dev.awaken.above.ruin;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.above.AboveWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * A Precursor ruin of one {@link RuinKind}. Data-driven placement (structure sets under data/awaken/worldgen); the
 * building itself is code ({@link RuinBuilder}) so it needs no template files.
 */
public class PrecursorRuinStructure extends Structure {
    public static final MapCodec<PrecursorRuinStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            RuinKind.CODEC.fieldOf("kind").forGetter(PrecursorRuinStructure::kind)
    ).apply(i, PrecursorRuinStructure::new));

    /** Sky platforms float between these heights (the overworld build limit is 319). */
    public static final int SKY_MIN_Y = 262;

    private final RuinKind kind;

    public PrecursorRuinStructure(Structure.StructureSettings settings, RuinKind kind) {
        super(settings);
        this.kind = kind;
    }

    public RuinKind kind() {
        return kind;
    }

    @Override
    protected Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext ctx) {
        ChunkPos chunk = ctx.chunkPos();
        RuinBuilder.Size size = RuinBuilder.size(kind);
        WorldgenRandom random = ctx.random();
        long seed = random.nextLong();
        int rotation = random.nextInt(4);
        int minX = chunk.getMinBlockX() + Math.max(0, (16 - size.x()) / 2);
        int minZ = chunk.getMinBlockZ() + Math.max(0, (16 - size.z()) / 2);
        int cx = minX + size.x() / 2, cz = minZ + size.z() / 2;
        int surface = ctx.chunkGenerator().getFirstOccupiedHeight(cx, cz, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
        int y;
        int biomeY = surface;
        switch (kind) {
            case VAULT, SPAWN_VAULT -> y = Structure.getMeanFirstOccupiedHeight(ctx, minX, size.x(), minZ, size.z()) - 1;
            case SKY -> y = SKY_MIN_Y + random.nextInt(8);
            case OCEAN -> {
                int floor = ctx.chunkGenerator().getFirstOccupiedHeight(cx, cz, Heightmap.Types.OCEAN_FLOOR_WG, ctx.heightAccessor(), ctx.randomState());
                y = floor - 1;
                biomeY = floor;
            }
            default -> y = surface - 7;
        }
        int maxY = ctx.heightAccessor().getMaxY() - size.y();
        y = Math.max(ctx.heightAccessor().getMinY() + 8, Math.min(maxY, y));
        double fromOrigin = Math.sqrt((double) cx * cx + (double) cz * cz);
        int rank = kind == RuinKind.SPAWN_VAULT ? 1 : RuinBuilder.vaultRank(fromOrigin);
        BlockPos corner = new BlockPos(minX, y, minZ);
        return Optional.of(new Structure.GenerationStub(new BlockPos(cx, biomeY, cz),
                builder -> builder.addPiece(new PrecursorRuinPiece(kind, corner, rotation, seed, rank))));
    }

    @Override
    public StructureType<?> type() {
        return AboveWorldgen.RUIN_TYPE.get();
    }
}
