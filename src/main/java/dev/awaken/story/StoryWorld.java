package dev.awaken.story;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * World-level story state: which ending the world chose, the Ascension tier, and the world effects that follow. The
 * story is the world's (SPEC "Together"): the first ending chosen is the world's, and the players who reached it are
 * kept with the Ascension tier they took part in, so a friend who joins late meets only what their own story reached.
 * Schema 2 adds who chose the ending and who has reached it; a schema 1 save loads with nobody recorded yet (players
 * who chose before are recorded when they log in).
 */
public final class StoryWorld extends SavedData {
    public static final int SCHEMA = 2;

    private record Ending(Optional<UUID> by, String byName, long day) {
        static final Ending NONE = new Ending(Optional.empty(), "", -1);
        static final Codec<Ending> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.optionalFieldOf("by").forGetter(Ending::by),
                Codec.STRING.optionalFieldOf("by_name", "").forGetter(Ending::byName),
                Codec.LONG.optionalFieldOf("day", -1L).forGetter(Ending::day)
        ).apply(i, Ending::new));
    }

    public static final Codec<StoryWorld> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("ending").forGetter(w -> w.ending),
            Codec.INT.fieldOf("ascension").forGetter(w -> w.ascension),
            Codec.BOOL.fieldOf("gates_calm").forGetter(w -> w.gatesCalm),
            Codec.BOOL.fieldOf("colour").forGetter(w -> w.colourRestored),
            Codec.INT.optionalFieldOf("schema", 1).forGetter(w -> SCHEMA),
            Ending.CODEC.optionalFieldOf("chosen", Ending.NONE).forGetter(w -> new Ending(Optional.ofNullable(w.endingBy), w.endingByName, w.endingDay)),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT).optionalFieldOf("reached", Map.of()).forGetter(w -> Map.copyOf(w.reached))
    ).apply(i, StoryWorld::new));

    public static final SavedDataType<StoryWorld> TYPE = new SavedDataType<>(Awaken.id("story"), StoryWorld::new, CODEC);

    String ending = "";
    int ascension;
    boolean gatesCalm;
    boolean colourRestored;
    /** Who chose the world's ending, and on which play-day; null / -1 for a save from before schema 2. */
    UUID endingBy;
    String endingByName = "";
    long endingDay = -1;
    /** Players who reached the world's ending → the Ascension tier they took part in (their own tier). */
    final Map<UUID, Integer> reached = new LinkedHashMap<>();

    public StoryWorld() {}

    private StoryWorld(String ending, int ascension, boolean gatesCalm, boolean colourRestored, int schema, Ending chosen,
                       Map<UUID, Integer> reached) {
        this.ending = ending;
        this.ascension = ascension;
        this.gatesCalm = gatesCalm;
        this.colourRestored = colourRestored;
        this.endingBy = chosen.by().orElse(null);
        this.endingByName = chosen.byName();
        this.endingDay = chosen.day();
        this.reached.putAll(reached);
    }

    public static StoryWorld get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** Whether the world's ending is chosen (and locked). */
    public boolean ended() {
        return !ending.isEmpty();
    }

    /** Whether this player has reached the world's ending (chose it, or was told it). */
    public boolean reached(UUID player) {
        return reached.containsKey(player);
    }

    /** The Ascension tier this player meets: the world's, never above the tier they took part in. */
    public int ascensionOf(UUID player) {
        return Math.min(ascension, reached.getOrDefault(player, 0));
    }
}
