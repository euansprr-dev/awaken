package dev.awaken.story;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** World-level story state: which ending was chosen, Ascension tier, and the world effects that follow. */
public final class StoryWorld extends SavedData {
    public static final Codec<StoryWorld> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("ending").forGetter(w -> w.ending),
            Codec.INT.fieldOf("ascension").forGetter(w -> w.ascension),
            Codec.BOOL.fieldOf("gates_calm").forGetter(w -> w.gatesCalm),
            Codec.BOOL.fieldOf("colour").forGetter(w -> w.colourRestored)
    ).apply(i, StoryWorld::new));

    public static final SavedDataType<StoryWorld> TYPE = new SavedDataType<>(Awaken.id("story"), StoryWorld::new, CODEC);

    String ending = "";
    int ascension;
    boolean gatesCalm;
    boolean colourRestored;

    public StoryWorld() {}

    private StoryWorld(String ending, int ascension, boolean gatesCalm, boolean colourRestored) {
        this.ending = ending;
        this.ascension = ascension;
        this.gatesCalm = gatesCalm;
        this.colourRestored = colourRestored;
    }

    public static StoryWorld get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }
}
