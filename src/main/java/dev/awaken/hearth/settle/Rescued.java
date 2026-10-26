package dev.awaken.hearth.settle;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * The people a player brought home from a village's trouble (W4's Taken and Gone Missing), as people who could settle
 * at their Hearth (PLAN 4.7: "you know them ... rescued them").
 *
 * <p><b>W4 CALL SITE.</b> W4 writes {@code dev.awaken.village.api.Troubles.rescued(player)} (a list of
 * {@code Rescued(village, villageName, rec, name, trade, age, day, trouble, settled)}) and
 * {@code Troubles.settle(player, who, hearthName)}; it isn't on build/awaken-v1 yet, so this answers nobody. When it is,
 * {@link #of} becomes: for each {@code r} not {@code settled()} and grown ({@code age() >= 1}), a person with id
 * {@code "rescued:" + r.village() + ":" + r.rec()}, given and family split from {@code r.name()}, role by trade (miner,
 * farmer or shepherd: farmer, smith, else "any"), trade {@code r.trade()}, face {@code "as:village:" + r.village() + "#" + r.rec()}
 * (the face they wore in their village), from {@code r.villageName()}; and {@link #settled} calls {@code Troubles.settle}.
 */
public final class Rescued {
    private Rescued() {}

    /** Who this player brought home who could settle with them (none until W4's Troubles is merged). */
    static List<Folk.Person> of(ServerPlayer player) {
        return List.of();
    }

    /** One of them moved into this player's Hearth (the village's history says so, once W4 is merged). */
    static void settled(ServerPlayer player, Folk.Person who, String hearthName) {
        // W4: Troubles.settle(player, <their Rescued>, hearthName)
    }
}
