package dev.awaken.gates.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The world and mood picked for one floor when the gate was made (SPEC §38). Saved in the plan, so an update or a
 * reload never changes a floor someone is standing on. {@code world} is a recipe id ({@code awaken:bell_crypt/sunken_belfry}).
 * {@code tall} asks for a whole quadrant column; it's saved with the pick so the plots never move, even if the recipe
 * goes away (the floor then builds today's rooms in the same plot).
 */
public record FloorWorld(int area, int floor, String world, String mood, boolean tall) {
    public static final Codec<FloorWorld> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("area").forGetter(FloorWorld::area),
            Codec.INT.fieldOf("floor").forGetter(FloorWorld::floor),
            Codec.STRING.fieldOf("world").forGetter(FloorWorld::world),
            Codec.STRING.optionalFieldOf("mood", "").forGetter(FloorWorld::mood),
            Codec.BOOL.optionalFieldOf("tall", false).forGetter(FloorWorld::tall)
    ).apply(i, FloorWorld::new));
}
