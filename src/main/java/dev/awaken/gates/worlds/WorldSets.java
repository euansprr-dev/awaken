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
