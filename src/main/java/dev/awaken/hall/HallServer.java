package dev.awaken.hall;

import dev.awaken.Awaken;
import dev.awaken.arms.ArmsApiAccess;
import dev.awaken.arms.ArmsArmoury;
import dev.awaken.arms.ArmsForge;
import dev.awaken.arms.ArmsHomes;
import dev.awaken.arms.LordForged;
import dev.awaken.arms.anvil.SmithMemory;
import dev.awaken.delve.forged.ForgedPicks;
import dev.awaken.delve.forged.PickForm;
import dev.awaken.progression.Progression;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** What the wall shows, judged here (SPEC §52): the client draws these and decides nothing. */
public final class HallServer {
    private HallServer() {}

    /** Answers an {@link HallNet.Ask}. A stand counts only if you're still at it; otherwise the suits are for looking. */
    public static void onAsk(ServerPlayer p, HallNet.Ask a) {
        dev.awaken.armour.FittingStandBlockEntity be = dev.awaken.core.api.Ready.loaded(p.level(), a.pos()) && p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(a.pos())) <= 10 * 10
                && p.level().getBlockEntity(a.pos()) instanceof dev.awaken.armour.FittingStandBlockEntity at ? at : null;
        if (a.op() == HallNet.TRACK) dev.awaken.armour.ArmourForge.track(p, dev.awaken.armour.ArmourSet.byId(a.set()));
        if (a.op() == HallNet.UNTRACK) dev.awaken.armour.ArmourForge.track(p, null);
        if (a.op() == HallNet.PEEK) dev.awaken.core.AwakenNet.send(p, wall(p)); // K's line-up: your level, for the close-up's words
        dev.awaken.core.AwakenNet.send(p, new HallNet.Suits(dev.awaken.armour.ArmourForge.hall(p, be)));
    }

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
        byte[] picks = new byte[HallNet.PICK_COLS * HallNet.ROWS];
        for (PickForm f : PickForm.values()) for (int t = 0; t < HallNet.ROWS; t++) picks[f.ordinal() * HallNet.ROWS + t] = (byte) pickState(p, f, t, level);
        return new HallNet.Wall(level, niches, lords(p), picks);
    }

    /** A pick's niche (SPEC §49, §52): the same states as a weapon's, a form not yet learned standing as a shape. */
    private static int pickState(ServerPlayer p, PickForm f, int tier, int level) {
        if (!ForgedPicks.open(p, f)) return HallNet.SHAPE;
        if (carries(p, f, tier) || SmithMemory.of(p).remembers(f.style(), tier)) return HallNet.MADE;
        if (level < ArmsArmoury.LEVEL[tier]) return HallNet.OVER;
        if (p.hasInfiniteMaterials()) return HallNet.READY;
        for (var n : ArmsArmoury.needs(p, f.style(), tier)) if (n.have() < n.need()) return HallNet.KNOWN;
        return HallNet.READY;
    }

    private static boolean carries(ServerPlayer p, PickForm f, int tier) {
        return ArmsForge.count(p, ForgedPicks.get(f, tier)) > 0;
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
