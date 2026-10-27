package dev.awaken.arms;

import dev.awaken.Awaken;
import dev.awaken.core.Later;
import dev.awaken.core.api.Promises;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

/**
 * How a gear tier is announced (docs/arms-progression/PLAN.md §3.6): the first time a fighter picks up the signature part
 * of a tier they haven't reached, the tier's card in the Armoury shows what it waits for and "What waits" files it (the
 * Horizon calls them back when it opens); at its level a toast names the job; Hale's board carries the part job
 * (data/awaken/awaken/bounty/part_job_*.json). The lines are the lang keys {@code awaken.armoury.pull.<tier>} and
 * {@code awaken.armoury.open.<tier>}, read through {@link ArmsLang} so a dedicated server can say them.
 */
public final class ArmsTiers {
    private ArmsTiers() {}

    private static final String[] SET = {"Wildhide", "Beastbound", "Gatewrought", "Veilwoven", "Lordscale", "Sovereign"};

    static String foreshadowKey(String tier) {
        return "awaken:arms_foreshadow/" + tier;
    }

    /** The set's name ("Beastbound") for tier index {@code i}. */
    public static String set(int i) {
        return SET[i];
    }

    /** The highest tier index whose level the fighter has reached, or -1 below the first. */
    static int best(ServerPlayer p) {
        int level = Progression.profile(p).level(), best = -1;
        for (int i = 0; i < ArmsArmoury.LEVEL.length; i++) if (level >= ArmsArmoury.LEVEL[i]) best = i;
        return best;
    }

    /**
     * What a locked tier says it waits for, or "" when the fighter hasn't met it yet: the next tier is always seen, the
     * ones beyond it only after the fighter has held one of their parts.
     */
    static String pull(ServerPlayer p, int tier, int best) {
        if (tier < 1 || tier <= best) return "";
        if (tier > best + 1 && Progression.profile(p).counter(foreshadowKey(ArmsArmoury.TIERS[tier])) == 0) return "";
        String s = ArmsLang.get("awaken.armoury.pull." + ArmsArmoury.TIERS[tier]);
        return s == null ? "" : s;
    }

    /** At a tier's level: the toast that names what it needs and the job on Hale's board (a moment after "Ready now"). */
    public static void onLevel(ServerPlayer p, int level) {
        for (int i = 1; i < ArmsArmoury.LEVEL.length; i++) {
            if (ArmsArmoury.LEVEL[i] != level) continue;
            String tier = ArmsArmoury.TIERS[i];
            String body = ArmsLang.get("awaken.armoury.open." + tier), name = SET[i];
            Promises.kept(p, "arms_tier:" + tier);
            if (body == null) continue;
            Later.run(p.level().getServer(), 60, () -> Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(name + " gear is open"))
                    .body(Component.literal(body)).presentation(SignalWindow.Presentation.TOAST).ttl(20 * 10).build()));
        }
    }

    /** Picking up a part of a tier you haven't reached files that tier under "What waits" (once). */
    public static void onPickup(ItemEntityPickupEvent.Post e) {
        if (e.getPlayer() instanceof ServerPlayer p) seen(p, e.getOriginalStack());
    }

    /** Files every unreached tier that this stack is a part of. */
    static void seen(ServerPlayer p, ItemStack stack) {
        if (stack.isEmpty()) return;
        int level = Progression.profile(p).level();
        for (int i = 1; i < ArmsArmoury.LEVEL.length; i++) {
            if (level >= ArmsArmoury.LEVEL[i]) continue;
            String tier = ArmsArmoury.TIERS[i];
            if (!stack.is(TagKey.create(Registries.ITEM, Awaken.id("hunter_core_" + tier.toLowerCase())))) continue;
            if (Progression.profile(p).counter(foreshadowKey(tier)) > 0) continue;
            Progression.setCounter(p, foreshadowKey(tier), 1);
            Promises.seen(p, "arms_tier:" + tier, null, Promises.Need.level(ArmsArmoury.LEVEL[i]), SET[i] + " gear");
        }
    }
}
