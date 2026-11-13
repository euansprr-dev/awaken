package dev.awaken.hall.client;

import dev.awaken.arms.ArmsArmoury;
import dev.awaken.arms.anvil.AnvilPayloads;
import dev.awaken.arms.anvil.AnvilWork;
import dev.awaken.arms.anvil.PickAnvil;
import dev.awaken.delve.HeavyStrikes;
import dev.awaken.delve.TrueStrikes;
import dev.awaken.delve.forged.ForgedPickItem;
import dev.awaken.delve.forged.ForgedPicks;
import dev.awaken.delve.forged.PickForm;
import dev.awaken.delve.forged.Sounding;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Picks wall's knowledge (SPEC §49, §52): which pick a niche is, what sets each form apart (from the numbers the code digs
 * with, never typed per pick), and which of the anvil's rows for the pick in your hand belongs on which niche.
 */
final class HallPicks {
    private HallPicks() {}

    static PickForm form(int col) {
        return PickForm.values()[col];
    }

    static String id(int col, int tier) {
        return ForgedPicks.id(form(col), tier);
    }

    static String style(int col) {
        return form(col).style();
    }

    /** The wall's word under each form, the bays' names. */
    static String label(int col) {
        return form(col).label;
    }

    /** "Gatewrought Delver": the item's own name. */
    static String name(int col, int tier) {
        return PieceView.weaponName(id(col, tier));
    }

    static ItemStack hand() {
        var player = Minecraft.getInstance().player;
        return player == null ? ItemStack.EMPTY : player.getMainHandItem();
    }

    /** The forged pick in your hand, or null. */
    static @Nullable ForgedPickItem forged() {
        return ForgedPicks.item(hand());
    }

    /** What an old pick or old mattock in your hand is remade as, or null. */
    static PickAnvil.@Nullable Remade old() {
        return PickAnvil.old(hand());
    }

    /**
     * The niche a forged pick in hand opens on: its form, at the next rank up from the best pick you carry, held to the rank
     * your level has reached (the old Picks side's opening, SPEC §49.9). An old pick opens on what it is remade as.
     */
    static int @Nullable [] opening(int you) {
        PickAnvil.Remade r = old();
        if (r != null) return new int[] {r.form().ordinal(), r.tier()};
        ForgedPickItem held = forged();
        if (held == null) return null;
        int carried = -1;
        var player = Minecraft.getInstance().player;
        if (player != null) for (ItemStack s : player.getInventory()) {
            ForgedPickItem i = ForgedPicks.item(s);
            if (i != null) carried = Math.max(carried, i.tier);
        }
        return new int[] {held.form.ordinal(), Math.clamp(Math.min(carried + 1, you), 0, ArmsArmoury.TIERS.length - 1)};
    }

    /**
     * The anvil's rows for the pick in your hand that make this niche, in the order they're offered: Raise on the next rank of
     * its form, Re-draw on another form at its rank, Work in and Dress on its own niche, Remake on what an old pick becomes.
     */
    static List<AnvilPayloads.Option> rows(int col, int tier) {
        AnvilPayloads.Held held = HallClient.held;
        if (held == null || held.options().isEmpty()) return List.of();
        ForgedPickItem i = forged();
        PickAnvil.Remade r = old();
        List<AnvilPayloads.Option> out = new ArrayList<>(2);
        for (AnvilPayloads.Option o : held.options()) {
            int kind = o.kind() % 100, formCode = o.kind() / 100;
            boolean here = switch (kind) {
                case AnvilWork.RAISE -> i != null && col == i.form.ordinal() && tier == i.tier + 1;
                case AnvilWork.REDRAW -> i != null && formCode == col + 1 && tier == i.tier;
                case AnvilWork.WORK_IN, AnvilWork.DRESS -> i != null && col == i.form.ordinal() && tier == i.tier;
                case AnvilWork.MIGRATE -> r != null && col == r.form().ordinal() && tier == r.tier();
                default -> false;
            };
            if (here) out.add(o);
        }
        // the free and quick one first: working in before dressing
        out.sort((a, b) -> Integer.compare(order(a.kind() % 100), order(b.kind() % 100)));
        return out;
    }

    private static int order(int kind) {
        return kind == AnvilWork.WORK_IN ? 0 : kind == AnvilWork.DRESS ? 1 : 2;
    }

    /** Whether any row at all is waiting for the pick in your hand (the hand line under the tabs says so). */
    static boolean anyRows() {
        return HallClient.held != null && !HallClient.held.options().isEmpty();
    }

    // ---- what sets a form apart --------------------------------------------------------------------------------------

    static List<HallSpecial.Mark> marks(PickForm f) {
        List<HallSpecial.Mark> out = new ArrayList<>(4);
        switch (f) {
            case DELVER -> {
                out.add(new HallSpecial.Mark("Hums near a lode", f.hum + " blocks", "It hums in your hand when a lode of ore is within " + f.hum + " blocks."));
                out.add(new HallSpecial.Mark("Digs rock at full speed", pct(f.speed), "The steady pick: the whole of its rank's speed in rock, and nothing given up for it."));
            }
            case BREAKER -> {
                out.add(new HallSpecial.Mark("Heavy strike ready sooner", HallSpecial.n(HeavyStrikes.BREAKER_GLINT / 20F) + " s",
                        "Hold for a heavy strike and it glints at " + HallSpecial.n(HeavyStrikes.BREAKER_GLINT / 20F) + " seconds instead of "
                                + HallSpecial.n(HeavyStrikes.GLINT / 20F) + ", and each heavy strike wears it a little less."));
                out.add(new HallSpecial.Mark("Lasts longer", "×" + HallSpecial.n(f.uses), "It takes " + pct(f.uses - 1F) + " more uses than a Delver of its rank before it goes blunt."));
                out.add(new HallSpecial.Mark("Slower in rock", pct(f.speed), "It digs rock at " + pct(f.speed) + " of its rank's speed: the weight is in the blow, not the swing."));
            }
            case MATTOCK -> {
                out.add(new HallSpecial.Mark("Digs earth and wood", "×" + HallSpecial.n(f.soft), "Its blade end digs earth and chops wood at " + pct(f.soft) + " of its rank's speed in rock."));
                out.add(new HallSpecial.Mark("Turns in your hand", "", "Look from rock to earth or wood and it flips round to the blade. Look back and it flips to the point."));
                out.add(new HallSpecial.Mark("Strips, flattens and tills", "", "It strips logs like an axe and flattens paths like a shovel. Sneak to till the ground like a hoe."));
            }
            case SOUNDER -> {
                out.add(new HallSpecial.Mark("Wider seam", HallSpecial.n((float) TrueStrikes.SOUNDER_RADIUS) + " block",
                        "A bite counts as on the ore's seam within " + HallSpecial.n((float) TrueStrikes.SOUNDER_RADIUS) + " of a block of it, not "
                                + HallSpecial.n((float) TrueStrikes.RADIUS) + ", so true strikes come easier."));
                out.add(new HallSpecial.Mark("More ore", pct(TrueStrikes.SOUNDER_BONUS), "Each ore you break has a " + pct(TrueStrikes.SOUNDER_BONUS)
                        + " chance of giving one more, against " + pct(TrueStrikes.BONUS) + " with any other pick."));
                out.add(new HallSpecial.Mark("Hums near a lode", f.hum + " blocks", "It hums in your hand when a lode of ore is within " + f.hum + " blocks."));
                out.add(new HallSpecial.Mark("Hears further", "×" + HallSpecial.n(Sounding.SOUNDER), "When you sneak and tap the rock to listen, the answers come from half again as far."));
            }
        }
        return out;
    }

    private static String pct(float f) {
        return Math.round(f * 100F) + "%";
    }
}
