package dev.awaken.gates.world;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * Gate Air's data for one floor (SPEC §38, "Gate Air data"): the lights mist takes its colour from, the shafts of light
 * through windows and cracks, the mist layers and the still water planes. Floor-local coordinates (x, z from the floor's
 * origin, y above its base). Planned with the floor and sent once when a player arrives on it; Lens reads it on the
 * client. A few KB at most: the engines keep to {@link #MAX_LIGHTS} and the other caps.
 */
public record FloorAir(List<Light> lights, List<Shaft> shafts, List<Mist> mists, List<Water> waters) {
    public static final int MAX_LIGHTS = 96, MAX_SHAFTS = 24, MAX_MISTS = 6, MAX_WATERS = 12;
    public static final FloorAir NONE = new FloorAir(List.of(), List.of(), List.of(), List.of());

    public FloorAir {
        lights = List.copyOf(lights.subList(0, Math.min(MAX_LIGHTS, lights.size())));
        shafts = List.copyOf(shafts.subList(0, Math.min(MAX_SHAFTS, shafts.size())));
        mists = List.copyOf(mists.subList(0, Math.min(MAX_MISTS, mists.size())));
        waters = List.copyOf(waters.subList(0, Math.min(MAX_WATERS, waters.size())));
    }

    public boolean isEmpty() {
        return lights.isEmpty() && shafts.isEmpty() && mists.isEmpty() && waters.isEmpty();
    }

    /** A light the air around it glows with: colour (0xRRGGBB), reach in blocks, strength 0..1. */
    public record Light(int x, int y, int z, int rgb, float radius, float strength) {
        static final StreamCodec<ByteBuf, Light> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Light::x, ByteBufCodecs.VAR_INT, Light::y, ByteBufCodecs.VAR_INT, Light::z,
                ByteBufCodecs.INT, Light::rgb, ByteBufCodecs.FLOAT, Light::radius, ByteBufCodecs.FLOAT, Light::strength, Light::new);
    }

    /** A shaft of light from (x0, y0, z0) to (x1, y1, z1), {@code radius} wide. */
    public record Shaft(int x0, int y0, int z0, int x1, int y1, int z1, float radius, int rgb, float strength) {
        static final StreamCodec<ByteBuf, Shaft> CODEC = StreamCodec.of((buf, s) -> {
            for (int v : new int[]{s.x0, s.y0, s.z0, s.x1, s.y1, s.z1}) ByteBufCodecs.VAR_INT.encode(buf, v);
            ByteBufCodecs.FLOAT.encode(buf, s.radius);
            ByteBufCodecs.INT.encode(buf, s.rgb);
            ByteBufCodecs.FLOAT.encode(buf, s.strength);
        }, buf -> new Shaft(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.INT.decode(buf), ByteBufCodecs.FLOAT.decode(buf)));
    }

    /** A layer of mist centred {@code y} above the base, {@code thickness} blocks deep. */
    public record Mist(int y, int thickness, int rgb, float density) {
        static final StreamCodec<ByteBuf, Mist> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Mist::y, ByteBufCodecs.VAR_INT, Mist::thickness, ByteBufCodecs.INT, Mist::rgb,
                ByteBufCodecs.FLOAT, Mist::density, Mist::new);
    }

    /** Still water at height {@code y} over [x0, x1] x [z0, z1], for reflections. */
    public record Water(int y, int x0, int z0, int x1, int z1) {
        static final StreamCodec<ByteBuf, Water> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Water::y, ByteBufCodecs.VAR_INT, Water::x0, ByteBufCodecs.VAR_INT, Water::z0,
                ByteBufCodecs.VAR_INT, Water::x1, ByteBufCodecs.VAR_INT, Water::z1, Water::new);
    }

    public static final StreamCodec<ByteBuf, FloorAir> STREAM_CODEC = StreamCodec.composite(
            Light.CODEC.apply(ByteBufCodecs.list(MAX_LIGHTS)), FloorAir::lights,
            Shaft.CODEC.apply(ByteBufCodecs.list(MAX_SHAFTS)), FloorAir::shafts,
            Mist.CODEC.apply(ByteBufCodecs.list(MAX_MISTS)), FloorAir::mists,
            Water.CODEC.apply(ByteBufCodecs.list(MAX_WATERS)), FloorAir::waters,
            FloorAir::new);
}
