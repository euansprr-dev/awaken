package dev.awaken.gates.worlds;

import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.content.Theme;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A theme's set (SPEC §38.8, "Sets"): when the last of its worlds' keepsakes is yours, the theme gives you a piece for
 * home made of them all (the Bell Crypt's five bells hung together as a working bell tower). Nothing new is saved: the
 * Atlas already knows each keepsake, and the last one comes once.
 */
public final class WorldSets {
    private WorldSets() {}

    public record Piece(Supplier<? extends ItemLike> item, String name, String line) {}

    static final Map<Theme, Piece> PIECES = new EnumMap<>(Theme.class);

    static {
        PIECES.put(Theme.BELL_CRYPT, new Piece(GatesRegistry.BELL_TOWER, "The Bell Tower",
                "All five bells of the crypt, hung together in a little tower. Set it up at home: it rings the hours from dawn to"
                        + " dusk, and you can ring it yourself."));
        PIECES.put(Theme.FUNGAL_HOLLOW, new Piece(() -> GatesRegistry.theme("spore_jar"), "The Spore Jar",
                "A jar of the Hollow's spores that never stop glowing. Tap it and they swirl up."));
        PIECES.put(Theme.EMBER_MINES, new Piece(() -> GatesRegistry.theme("ember_forge"), "The Ever-Ember Forge",
                "A miner's forge whose coals never go out. Hold raw food over it and it cooks."));
        PIECES.put(Theme.HIVE, new Piece(() -> GatesRegistry.theme("queens_comb"), "The Queen's Comb",
                "A comb from the queen's own garden. Fill a glass bottle from it, and it slowly fills itself up again."));
        PIECES.put(Theme.OVERGROWN_TEMPLE, new Piece(() -> GatesRegistry.theme("little_idol"), "The Little Idol",
                "A little idol from the temple. Set it near your crops and they grow faster around it."));
        PIECES.put(Theme.DROWNED_ARCHIVE, new Piece(() -> GatesRegistry.theme("diving_lamp"), "The Diving Lamp",
                "An old diver's lamp from the drowned stacks. Use it and you can breathe underwater for a minute."));
        PIECES.put(Theme.CLOCKWORK_FOUNDRY, new Piece(() -> GatesRegistry.theme("pocket_engine"), "The Pocket Engine",
                "A little engine that ticks along on its own. Use it to start or stop it; while it runs, it powers redstone."));
        PIECES.put(Theme.GLASS_DESERT, new Piece(() -> GatesRegistry.theme("sun_hourglass"), "The Sun Hourglass",
                "An hourglass that keeps the desert's time. Turn it and it tells you how long it is until sunset or sunrise."));
        PIECES.put(Theme.LANTERN_NIGHT_MARKET, new Piece(() -> GatesRegistry.theme("lucky_cat"), "The Lucky Cat",
                "A lucky cat from the market's busiest stall. Once a day it has a small gift for you."));
        PIECES.put(Theme.FROST_MONASTERY, new Piece(() -> GatesRegistry.theme("singing_bowl"), "The Singing Bowl",
                "The monks' singing bowl. Strike it and anyone close by shakes off the cold, the slowness and the weakness. It needs"
                        + " a minute to stop ringing before it works again."));
        PIECES.put(Theme.MIRROR_PALACE, new Piece(() -> GatesRegistry.theme("hand_mirror"), "The Hand Mirror",
                "A little mirror from the palace. Look into it and it shows you the way back to your bed."));
        PIECES.put(Theme.PRECURSOR_LAB, new Piece(() -> GatesRegistry.theme("humming_cube"), "The Humming Cube",
                "A cube from the lab that hums when someone stands near it, and powers redstone while it does."));
        PIECES.put(Theme.NIGHTMARE, new Piece(() -> GatesRegistry.theme("night_light"), "The Night Light",
                "A child's night light from the house. Use it and every monster nearby glows, so nothing creeps up on you in the dark."));
    }

    public static Piece of(Theme t) {
        return PIECES.get(t);
    }

    /** Whether every world of a theme that has a keepsake has given you its keepsake. */
    static boolean complete(GateAtlas atlas, Theme t) {
        int with = 0;
        for (String id : GateWorlds.ofTheme(t)) {
            GateWorld w = GateWorlds.get(id);
            if (w == null || w.dressing().keepsake().isEmpty()) continue;
            with++;
            GateAtlas.Entry e = atlas.entry(id);
            if (e == null || !e.keepsake) return false;
        }
        return with > 0;
    }

    /** A keepsake was just given: if it was a theme's last, its set piece too. */
    static void after(ServerPlayer p, GateWorld w) {
        Piece piece = PIECES.get(w.theme());
        if (piece == null || !complete(p.getData(GatesRegistry.ATLAS.get()), w.theme())) return;
        ItemStack s = new ItemStack(piece.item().get());
        if (!p.addItem(s)) p.drop(s, false, net.minecraft.util.Prediction.SERVER_ONLY);
        p.sendSystemMessage(Component.literal("You have every keepsake from the " + w.theme().displayName + ". " + piece.line()));
    }
}
