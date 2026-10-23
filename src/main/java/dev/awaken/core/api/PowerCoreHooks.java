package dev.awaken.core.api;

import dev.awaken.progression.Rank;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/** Power Core drops from any module. The powers module installs the real roller. */
public final class PowerCoreHooks {
    private PowerCoreHooks() {}

    public interface Roller {
        /** A random Power Core fit for this rank (never one of the hidden/latent-only powers). */
        ItemStack roll(Rank rank, RandomSource random);

        /** A specific core, e.g. a boss signature. Empty if unknown. */
        ItemStack forPower(String powerId, int variant);
    }

    private static Roller roller = new Roller() {
        @Override public ItemStack roll(Rank rank, RandomSource random) { return ItemStack.EMPTY; }
        @Override public ItemStack forPower(String powerId, int variant) { return ItemStack.EMPTY; }
    };

    public static void install(Roller r) {
        roller = r;
    }

    public static ItemStack roll(Rank rank, RandomSource random) {
        return roller.roll(rank, random);
    }

    public static ItemStack forPower(String powerId, int variant) {
        return roller.forPower(powerId, variant);
    }
}
