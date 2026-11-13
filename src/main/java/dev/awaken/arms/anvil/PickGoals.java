package dev.awaken.arms.anvil;

import dev.awaken.arms.ArmsArmoury;
import dev.awaken.arms.net.ArmsPayloads.NeedRow;
import dev.awaken.core.api.Goals;
import dev.awaken.core.api.Goals.Goal;
import dev.awaken.core.api.Goals.Need;
import dev.awaken.delve.WakingPick;
import dev.awaken.delve.forged.DelveForged;
import dev.awaken.delve.forged.ForgedPickItem;
import dev.awaken.delve.forged.ForgedPicks;
import dev.awaken.delve.forged.PickForm;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Forged picks on the Weapons tab (SPEC §49.9): find the head, forge the first pick; and then, off the main path and only
 * while it applies: remake an old one, raise the pick you carry to the rank you've reached, work in a deed, dress a blunt
 * edge. Reads what you carry each time, so a step that's done is gone.
 */
final class PickGoals {
    private PickGoals() {}

    static final String HEAD = "pick/old_head", FIRST = "pick/first", REMAKE = "pick/remake", RAISE = "pick/raise", WORK_IN = "pick/work_in", DRESS = "pick/dress";
    private static final String FORGED = "awaken:pick_forged", REMADE = "awaken:pick_remade";

    static void register() {
        Goals.register(PickGoals::steps);
    }

    static List<Goals.Step> steps(ServerPlayer p) {
        if (!Progression.profile(p).awakened()) return List.of();
        ItemStack best = ItemStack.EMPTY, blunt = ItemStack.EMPTY, waiting = ItemStack.EMPTY, old = ItemStack.EMPTY;
        int bestTier = -1;
        for (ItemStack s : p.getInventory()) {
            if (s.isEmpty()) continue;
            if (PickAnvil.old(s) != null) {
                if (old.isEmpty()) old = s;
                continue;
            }
            ForgedPickItem i = ForgedPicks.item(s);
            if (i == null) continue;
            if (i.tier > bestTier) {
                bestTier = i.tier;
                best = s;
            }
            if (ForgedPicks.blunt(s) && blunt.isEmpty()) blunt = s;
            if (ForgedPicks.waiting(p, s) != 0 && waiting.isEmpty()) waiting = s;
        }
        var profile = Progression.profile(p);
        boolean made = profile.counter(FORGED) > 0 || profile.counter(REMADE) > 0;
        boolean pulled = profile.counter(WakingPick.PULLED) > 0 || Goals.count(p.getInventory(), DelveForged.OLD_HEAD.get()) > 0;
        BlockPos anvil = WeaponGoals.anvil(p, SmithMemory.of(p));
        List<Goals.Step> out = new ArrayList<>(3);
        if (!made && bestTier < 0 && old.isEmpty()) {
            if (!pulled) {
                out.add(Goals.Step.of(HEAD, Goal.WEAPONS, "Find the old pick head")
                        .why("It waits in the stone at the end of an adit, a timbered tunnel into a hillside. If there's no adit near your home, your Hearth keeps it for you. It's the start of your first forged pick.")
                        .optional().priority(46).build());
            } else {
                Goals.Builder b = Goals.Step.of(FIRST, Goal.WEAPONS, "Forge your first pick")
                        .why("Use a War Anvil and open Picks, beside Arms and Armour. By hand at first: knap the head clean, bind it, and tap it down the haft. How well you do decides how well it digs.")
                        .need(Need.of("A War Anvil", anvil != null)).priority(45);
                for (NeedRow r : ArmsArmoury.needsOf(p, ArmsArmoury.PREFIX[0] + "_" + PickForm.DELVER.style())) b.need(WeaponGoals.need(r));
                if (anvil != null) b.at(anvil);
                out.add(b.build());
            }
        }
        if (!old.isEmpty()) {
            Goals.Builder b = Goals.Step.of(REMAKE, Goal.WEAPONS, "Bring your old pick to a War Anvil")
                    .why("Hold it and use any War Anvil. It's remade free, and it keeps everything it has. It goes on working as it is if you'd rather not.")
                    .optional().priority(44);
            if (anvil != null) b.at(anvil);
            out.add(b.build());
        }
        if (!blunt.isEmpty()) {
            ForgedPickItem i = ForgedPicks.item(blunt);
            Goals.Builder b = Goals.Step.of(DRESS, Goal.WEAPONS, "Dress your " + blunt.getHoverName().getString())
                    .why("It's gone blunt, and digs at a third of its speed until it's dressed. It never breaks. Hold it and use a War Anvil.")
                    .optional().priority(40);
            for (NeedRow r : PickAnvil.dressNeeds(p, i)) b.need(WeaponGoals.need(r));
            if (anvil != null) b.at(anvil);
            out.add(b.build());
        }
        if (!waiting.isEmpty()) {
            Goals.Builder b = Goals.Step.of(WORK_IN, Goal.WEAPONS, "Work what you've done into your pick")
                    .why("You've earned a gift it doesn't hold yet. Hold your pick and use any War Anvil: it's worked in free, in a few seconds.")
                    .optional().priority(42);
            if (anvil != null) b.at(anvil);
            out.add(b.build());
        }
        int reached = ArmsArmoury.bestTier(p);
        if (bestTier >= 0 && bestTier < reached) {
            ForgedPickItem i = ForgedPicks.item(best);
            String next = ArmsArmoury.TIERS[bestTier + 1];
            Goals.Builder b = Goals.Step.of(RAISE, Goal.WEAPONS, "Raise your pick to " + next + "-rank")
                    .why("Hold it and use a War Anvil. It keeps its gifts, its enchantments and its tally, and it digs what your rank can reach.")
                    .optional().priority(43);
            for (NeedRow r : PickAnvil.raiseNeeds(p, ArmsArmoury.PREFIX[bestTier + 1] + "_" + i.form.style())) b.need(WeaponGoals.need(r));
            if (anvil != null) b.at(anvil);
            out.add(b.build());
        }
        return out;
    }
}
