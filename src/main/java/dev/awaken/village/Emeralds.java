package dev.awaken.village;

import dev.awaken.core.api.EconomyHooks;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The emerald is the coin of the world (decision 5, SPEC §28.6): {@code EconomyHooks} counts, takes and pays emeralds
 * from the player's own inventory. A payment is made once per claim id (the world remembers the claims), so a contract,
 * a request or an event can never pay twice.
 */
final class Emeralds implements EconomyHooks.Access {
    @Override
    public long balance(Player p) {
        long n = 0;
        var inv = p.getInventory();
        for (int s = 0; s < inv.getContainerSize(); s++) {
            ItemStack st = inv.getItem(s);
            if (st.is(Items.EMERALD)) n += st.getCount();
        }
        return n;
    }

    @Override
    public boolean pay(ServerPlayer p, long amount, Identifier claimId) {
        if (amount <= 0) return false;
        VillageState st = VillageState.get(p.level().getServer());
        if (!st.claims.add(claimId.toString())) return false;
        st.setDirty();
        long left = amount;
        while (left > 0) {
            int n = (int) Math.min(64, left);
            ItemStack stack = new ItemStack(Items.EMERALD, n);
            p.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);
            left -= n;
        }
        return true;
    }

    @Override
    public boolean charge(ServerPlayer p, long amount) {
        if (amount <= 0) return true;
        if (balance(p) < amount) return false;
        long left = amount;
        var inv = p.getInventory();
        for (int s = 0; s < inv.getContainerSize() && left > 0; s++) {
            ItemStack st = inv.getItem(s);
            if (!st.is(Items.EMERALD)) continue;
            int n = (int) Math.min(st.getCount(), left);
            st.shrink(n);
            left -= n;
        }
        return true;
    }
}
