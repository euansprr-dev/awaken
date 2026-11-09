package dev.awaken.shaping.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a look looks like, read from that block's own baked model, so any block that can be worn wears right: its sprite
 * and how the texture lies on each side (a log on its side keeps its grain). Read on the meshing threads; cached until the
 * models reload.
 */
final class LookMaterials {
    private LookMaterials() {}

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final ConcurrentHashMap<BlockState, Optional<Look>> CACHE = new ConcurrentHashMap<>();

    /** One side: its material, and the texture coordinates at the side's four corners. */
    record Side(BakedQuad.MaterialInfo info, float[] uv) {
        float u(float a, float b) {
            return lerp(this.uv, 0, a, b);
        }

        float v(float a, float b) {
            return lerp(this.uv, 1, a, b);
        }

        private static float lerp(float[] c, int at, float a, float b) {
            float near = c[at] + (c[2 + at] - c[at]) * a;
            float far = c[4 + at] + (c[6 + at] - c[4 + at]) * a;
            return near + (far - near) * b;
        }

        TextureAtlasSprite sprite() {
            return this.info.sprite();
        }
    }

    record Look(Side[] sides, Material.Baked particle, int flags) {
        Side side(Direction d) {
            return this.sides[d.ordinal()];
        }
    }

    static void clear() {
        CACHE.clear();
    }

    static @Nullable Look of(BlockState look) {
        return CACHE.computeIfAbsent(look, LookMaterials::read).orElse(null);
    }

    private static Optional<Look> read(BlockState look) {
        BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(look);
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(RandomSource.create(42L), parts);
        if (parts.isEmpty()) return Optional.empty();
        Side[] sides = new Side[DIRECTIONS.length];
        Side any = null;
        for (Direction d : DIRECTIONS) {
            BakedQuad q = find(parts, d);
            if (q == null) continue;
            sides[d.ordinal()] = side(q, d);
            if (any == null) any = sides[d.ordinal()];
        }
        if (any == null) return Optional.empty();
        for (int i = 0; i < sides.length; i++) if (sides[i] == null) sides[i] = any;
        return Optional.of(new Look(sides, model.particleMaterial(), parts.getFirst().materialFlags()));
    }

    private static @Nullable BakedQuad find(List<BlockStateModelPart> parts, Direction d) {
        for (BlockStateModelPart p : parts) {
            List<BakedQuad> quads = p.getQuads(d);
            if (!quads.isEmpty()) return quads.getFirst();
        }
        for (BlockStateModelPart p : parts) {
            for (BakedQuad q : p.getQuads(null)) if (q.direction() == d) return q;
        }
        return null;
    }

    /** Where this quad's corners sit on the side tells us how its texture lies. */
    private static Side side(BakedQuad q, Direction d) {
        TextureAtlasSprite s = q.materialInfo().sprite();
        float[] uv = {s.getU0(), s.getV0(), s.getU1(), s.getV0(), s.getU0(), s.getV1(), s.getU1(), s.getV1()};
        for (int i = 0; i < 4; i++) {
            Vector3fc p = switch (i) {
                case 0 -> q.position0();
                case 1 -> q.position1();
                case 2 -> q.position2();
                default -> q.position3();
            };
            int a = a(d, p.x(), p.y(), p.z()) > 0.5F ? 1 : 0, b = b(d, p.x(), p.y(), p.z()) > 0.5F ? 1 : 0;
            int at = (a + b * 2) * 2;
            uv[at] = UVPair.unpackU(q.packedUV(i));
            uv[at + 1] = UVPair.unpackV(q.packedUV(i));
        }
        return new Side(q.materialInfo(), uv);
    }

    /** Across this side, 0 to 1, in the block's own space, so neighbouring blocks line up. */
    static float a(Direction d, float x, float y, float z) {
        return switch (d) {
            case NORTH -> 1 - x;
            case EAST -> 1 - z;
            case WEST -> z;
            default -> x;
        };
    }

    /** Down this side, 0 to 1. */
    static float b(Direction d, float x, float y, float z) {
        return switch (d) {
            case UP -> z;
            case DOWN -> 1 - z;
            default -> 1 - y;
        };
    }
}
