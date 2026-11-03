package dev.awaken.lucidium.plan;

import java.util.List;
import java.util.UUID;

/**
 * What a night's dream is chosen from (SPEC §9.2): the Dream Journal's choices and tonight's seed. The plans themselves
 * are trials ({@code DreamOffers.plan}); the old islands of memory went with the dreams' remake (§9).
 */
public final class DreamPlanner {
    private DreamPlanner() {}

    /** Old intentions an old save may still hold (read, never offered). */
    public static final String FIND_THE_DOOR = "find_the_door", FACE_MY_FEAR = "face_my_fear", FLY = "fly";
    /** The Dream Journal's choices of tonight's dream (SPEC §9.2): a Run, a Room's hidden place, the Chase, a bell's dream. */
    public static final String LEARN_A_MOVE = "learn_a_move", FIND_HIDDEN = "find_hidden", FIND_A_BELL = "find_a_bell";
    public static final List<String> CHOICES = List.of(LEARN_A_MOVE, FIND_HIDDEN, FACE_MY_FEAR, FIND_A_BELL);
    public static final List<String> INTENTIONS = List.of(FIND_THE_DOOR, FACE_MY_FEAR, FLY);

    public static long seed(long worldSeed, UUID player, long day, int dreamsSoFar) {
        long h = worldSeed * 0x9E3779B97F4A7C15L;
        h ^= player.getMostSignificantBits() + 0x632BE59BD9B4E019L + (h << 6) + (h >>> 2);
        h ^= player.getLeastSignificantBits() + 0x85157AF5L + (h << 6) + (h >>> 2);
        h ^= day * 0xC2B2AE3D27D4EB4FL + dreamsSoFar * 0x165667B19E3779F9L;
        return h ^ (h >>> 29);
    }
}
