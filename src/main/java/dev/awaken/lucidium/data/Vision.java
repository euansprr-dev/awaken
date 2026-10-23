package dev.awaken.lucidium.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

/**
 * A real place in the player's world shown in a dream with its coordinates hidden. Once shown it becomes a Codex
 * rumour with a direction hint; walking within 16 blocks of it while awake marks it found.
 *
 * @param shownDay play-day of the dream that showed it, or -1 while it is only prepared
 */
public record Vision(String id, String snapshotId, String label, String dim, BlockPos pos, String hint,
                     long createdDay, long shownDay, boolean found) {

    public static final Codec<Vision> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(Vision::id),
            Codec.STRING.fieldOf("snapshot").forGetter(Vision::snapshotId),
            Codec.STRING.fieldOf("label").forGetter(Vision::label),
            Codec.STRING.fieldOf("dim").forGetter(Vision::dim),
            BlockPos.CODEC.fieldOf("pos").forGetter(Vision::pos),
            Codec.STRING.optionalFieldOf("hint", "").forGetter(Vision::hint),
            Codec.LONG.fieldOf("created").forGetter(Vision::createdDay),
            Codec.LONG.optionalFieldOf("shown", -1L).forGetter(Vision::shownDay),
            Codec.BOOL.optionalFieldOf("found", false).forGetter(Vision::found)
    ).apply(i, Vision::new));

    public boolean shown() {
        return shownDay >= 0;
    }

    public Vision withShown(long day, String hint) {
        return new Vision(id, snapshotId, label, dim, pos, hint, createdDay, day, found);
    }

    public Vision withFound() {
        return new Vision(id, snapshotId, label, dim, pos, hint, createdDay, shownDay, true);
    }
}
