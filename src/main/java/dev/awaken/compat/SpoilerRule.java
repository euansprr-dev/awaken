package dev.awaken.compat;

import dev.awaken.core.api.MobRole;
import dev.awaken.mobs.entity.AwakenMob;
import dev.awaken.mobs.entity.RitualStatue;
import dev.awaken.mobs.entity.SeatedOne;
import dev.awaken.progression.Progression;
import dev.awaken.veil.block.VeilMarkBlock;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The spoiler rule's decisions (SPEC §14), in one place for every viewer: JEI's list and recipes, Jade's look-at lines.
 * {@link Discovered} keeps the record; this says what it means for a player. It takes any {@link Player} (the client's
 * own or one on the server), so GameTests check it with neither companion installed. Callers pass {@code guard}, the
 * answer of {@link #applies} for the player's setting, so one frame's decisions agree.
 */
public final class SpoilerRule {
    private SpoilerRule() {}

    /** The Codex records a creature's first sighting as this counter plus its bestiary id. */
    private static final String SEEN = "awaken:discovered/creature/";

    /** The guard applies while the setting is on and the player isn't in creative or spectator (those list everything). */
    public static boolean applies(Player p, boolean setting) {
        return setting && !p.isCreative() && !p.isSpectator();
    }

    /** An item may be shown: it isn't Awaken's, the guard is off, or the player has discovered it. */
    public static boolean shows(Player p, Item item, boolean guard) {
        return !guard || !Discovered.isAwaken(item) || Discovered.of(p).has(item);
    }

    /** A guarded recipe shows once any of its key items may be shown. */
    public static boolean showsAny(Player p, List<Item> keys, boolean guard) {
        for (int i = 0; i < keys.size(); i++) if (shows(p, keys.get(i), guard)) return true;
        return false;
    }

    /** A creature's details (temperament, level, rank) wait until the Codex has it. */
    public static boolean knowsCreature(Player p, String defId, boolean guard) {
        return !guard || Progression.profile(p).counter(SEEN + defId) > 0;
    }

    /** A creature a look-at tooltip never names: naming it would give the trick away. Reads synced state only. */
    public static boolean disguised(Entity e) {
        if (e instanceof SeatedOne || e instanceof RitualStatue) return true;
        return e instanceof AwakenMob m
                && (m.mimicId() >= 0 || m.flag(AwakenMob.F_DORMANT) || m.syncedRole() == MobRole.Role.RITUAL);
    }

    /** A block a look-at tooltip never names: Veil marks are only for spirit sight. */
    public static boolean disguised(BlockState state) {
        return state.getBlock() instanceof VeilMarkBlock;
    }
}
