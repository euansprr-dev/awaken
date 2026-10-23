package dev.awaken.progression;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.function.BiConsumer;

/** Applies {@link Reward}s. The powers module installs {@link #powerLearner} at init. */
public final class RewardHooks {
    private RewardHooks() {}

    /** Set by the powers module: learn a power by id. */
    public static BiConsumer<ServerPlayer, String> powerLearner = (p, id) -> {};

    public static void learnPower(ServerPlayer player, String power) {
        powerLearner.accept(player, power);
    }

    public static void apply(ServerPlayer player, Reward r, Progression.XpSource source) {
        if (r == null) return;
        Profile p = Progression.profile(player);
        if (r.levels() > 0) Progression.grantLevels(player, r.levels(), source);
        if (r.statPoints() > 0) p.unspent += r.statPoints();
        r.stat().ifPresent(s -> Progression.addStat(player, s, Math.max(1, r.statAmount())));
        r.power().ifPresent(pw -> learnPower(player, pw));
        r.item().ifPresent(id -> {
            var parsed = Identifier.tryParse(id);
            if (parsed == null) return;
            BuiltInRegistries.ITEM.getOptional(parsed).ifPresent(item -> {
                ItemStack stack = new ItemStack(item, Math.max(1, r.itemCount()));
                player.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);
            });
        });
        Progression.markDirty(player);
    }
}
