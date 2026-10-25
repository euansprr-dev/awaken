package dev.awaken.living.craft;

import dev.awaken.living.LivingContent;
import dev.awaken.living.LivingModule;
import dev.awaken.progression.Progression;
import dev.awaken.progression.XpBudget;
import dev.awaken.signal.Signal;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Crafts that level as you do them (SPEC §21.9). An action pays craft XP and, through its craft's budget (the sim's
 * forage/mine/fish/cook/smith), a share of the Hunter level ({@link #ACTION} of a level, by rank like all wild XP);
 * the craft XP shrinks by the same share the budget kept back, so an AFK farm levels off on both. Hunting rides the
 * kill's own "hunt" budget. A craft level is a toast; a perk opening is a toast that names it.
 */
public final class Crafts {
    private Crafts() {}

    /** A paying craft action's share of a Hunter level (pacing_sim CRAFT_ACTION). */
    public static final double ACTION = 0.008;

    public static CraftState state(Player p) {
        return p.getData(LivingContent.CRAFTS.get());
    }

    public static int level(Player p, Craft c) {
        return state(p).level(c);
    }

    public static boolean has(Player p, Craft c, String perk) {
        return state(p).has(c, perk);
    }

    /**
     * {@code xp} craft XP for an action ({@code actions} of them for the Hunter level: 1 a herb, 1 an ore, 3 a temper).
     * Returns the craft XP actually gained.
     */
    public static double gain(ServerPlayer p, Craft c, double xp, double actions) {
        if (!LivingModule.shown(p.level().getServer()) || xp <= 0) return 0;
        double share = 1;
        if (c == Craft.HUNTING) {
            // the kill already paid the Hunter level through "hunt": the craft follows how worn that budget is
            share = 1 / Math.max(1, XpBudget.wear(p.getUUID(), LivingModule.HUNT, p.level().getGameTime()));
        } else if (actions > 0) {
            double rank = LivingModule.wildsRank(Progression.profile(p).rank());
            double raw = ACTION * actions * (c == Craft.FISHING && has(p, c, "leviathan_line") ? 2 : 1);
            double paid = Progression.worldXp(p, c.budget, raw, rank);
            share = rank <= 0 ? 0 : Math.min(1, paid / (raw * rank));
        }
        double got = xp * share;
        if (got <= 0) return 0;
        CraftState s = state(p);
        int before = s.level(c);
        s.xp.merge(c.id, got, Double::sum);
        int after = s.level(c);
        p.setData(LivingContent.CRAFTS.get(), s);
        if (after > before) levelled(p, c, before, after);
        return got;
    }

    private static void levelled(ServerPlayer p, Craft c, int before, int after) {
        Signal.toast(p, Component.literal(c.title + " · level " + after));
        for (Craft.Perk perk : c.perks) {
            if (perk.level() > before && perk.level() <= after) {
                Signal.toast(p, Component.literal("New perk · " + perk.title() + " · " + perk.text()));
                Progression.count(p, "awaken:craft_perks");
            }
        }
        Progression.markNewThing(p, "craft/" + c.id + "/" + after);
    }

    /** Hunting: one more of this kind killed (Weak Spot counts them). */
    static void hunted(ServerPlayer p, String type) {
        CraftState s = state(p);
        s.hunted.merge(type, 1, Integer::sum);
        p.setData(LivingContent.CRAFTS.get(), s);
    }

    /** Dev and tests: sets a craft's level outright (its XP at the start of that level). */
    public static void setLevel(ServerPlayer p, Craft c, int level) {
        double xp = 0;
        for (int l = 1; l < Math.max(1, Math.min(Craft.MAX_LEVEL, level)); l++) xp += Craft.toNext(l);
        CraftState s = state(p);
        s.xp.put(c.id, xp + 0.5); // a hair past the line, so rounding never leaves it a level short
        p.setData(LivingContent.CRAFTS.get(), s);
    }
}
