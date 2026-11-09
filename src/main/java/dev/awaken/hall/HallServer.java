package dev.awaken.hall;

import dev.awaken.Awaken;
import dev.awaken.arms.ArmsApiAccess;
import dev.awaken.arms.ArmsArmoury;
import dev.awaken.arms.ArmsForge;
import dev.awaken.arms.ArmsHomes;
import dev.awaken.arms.LordForged;
import dev.awaken.arms.anvil.SmithMemory;
import dev.awaken.progression.Progression;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** What the wall shows, judged here (SPEC §52): the client draws these and decides nothing. */
public final class HallServer {
    private HallServer() {}

    public static HallNet.Wall wall(ServerPlayer p) {
        int level = Progression.profile(p).level(), rank = Progression.rank(p).ordinal();
        byte[] niches = new byte[HallNet.COLS * HallNet.ROWS];
        for (int c = 0; c < HallNet.COLS; c++) {
            String style = HallNet.COLUMNS[c];
            boolean known = ArmsApiAccess.knows(p, style);
            ArmsHomes.Home home = ArmsHomes.of(style);
            boolean offer = !known && home != null && home.rank().ordinal() <= rank;
            for (int t = 0; t < HallNet.ROWS; t++) niches[c * HallNet.ROWS + t] = (byte) state(p, style, t, level, known, offer);
        }
        return new HallNet.Wall(level, niches, lords(p));
    }

    private static int state(ServerPlayer p, String style, int tier, int level, boolean known, boolean offer) {
        if (!known) return offer ? HallNet.OFFER : HallNet.SHAPE;
        if (holds(p, style, tier) || SmithMemory.of(p).remembers(style, tier)) return HallNet.MADE;
        if (level < ArmsArmoury.LEVEL[tier]) return HallNet.OVER;
        if (p.hasInfiniteMaterials()) return HallNet.READY;
        for (var n : ArmsArmoury.needs(p, style, tier)) if (n.have() < n.need()) return HallNet.KNOWN;
        return HallNet.READY;
    }

    private static boolean holds(ServerPlayer p, String style, int tier) {
        Item item = BuiltInRegistries.ITEM.getValue(Awaken.id(ArmsArmoury.PREFIX[tier] + "_" + style));
        return item != Items.AIR && ArmsForge.count(p, item) > 0;
    }

    private static int lords(ServerPlayer p) {
        int bits = 0, i = 0;
        for (LordForged.Forged f : LordForged.ALL) {
            Item item = BuiltInRegistries.ITEM.getValue(Awaken.id(f.weapon()));
            if (item != Items.AIR && ArmsForge.count(p, item) > 0) bits |= 1 << i;
            i++;
        }
        return bits;
    }
}
