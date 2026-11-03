package dev.awaken.arms.anvil;

import dev.awaken.arms.Archetypes;
import dev.awaken.arms.ArmsArmoury;
import dev.awaken.arms.ArmsData;
import dev.awaken.arms.ArmsItems;
import dev.awaken.core.api.ArmsApi;
import dev.awaken.core.api.NowHooks;
import dev.awaken.progression.Progression;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The Now tracker's anvil line (SPEC §46.7; priority 25, an idea): "You can forge Beastbound Daggers at your War Anvil"
 * when the player holds every part of a weapon they know, at a tier they've reached, better than any of its style they
 * carry. The highest tier first. One line at most, worked out at most once a second per player.
 */
final class AnvilNow {
    private AnvilNow() {}

    static final int PRIORITY = 25;
    private static final int CACHE_TICKS = 20;

    private record Cached(long at, List<NowHooks.Suggestion> lines) {}
    private static final Map<UUID, Cached> CACHE = new HashMap<>();

    static void register() {
        NowHooks.register(AnvilNow::suggest);
    }

    static void forget(UUID player) {
        CACHE.remove(player);
    }

    static List<NowHooks.Suggestion> suggest(ServerPlayer p) {
        if (!Progression.profile(p).awakened()) return List.of();
        long now = p.level().getGameTime();
        Cached c = CACHE.get(p.getUUID());
        if (c != null && now >= c.at() && now - c.at() < CACHE_TICKS) return c.lines();
        String id = next(p);
        List<NowHooks.Suggestion> lines = id == null ? List.of() : List.of(new NowHooks.Suggestion("arms:forge/" + id, PRIORITY,
                Component.literal("You can forge ").append(new ItemStack(WarAnvil.item(id)).getHoverName()).append(" at your War Anvil"),
                Optional.ofNullable(SmithMemory.of(p).anvil).filter(g -> g.dimension() == p.level().dimension()).map(GlobalPos::pos)));
        CACHE.put(p.getUUID(), new Cached(now, lines));
        return lines;
    }

    /** The weapon to suggest, or null: the highest reached tier first, a known style whose every part is in the pack. */
    static @Nullable String next(ServerPlayer p) {
        int top = WeaponGoals.reached(p);
        int[] carried = carried(p);
        for (int tier = top; tier >= 0; tier--) {
            for (Archetypes.Loaded st : Archetypes.all()) {
                if (carried[st.index()] >= tier || !ArmsApi.knows(p, st.id())) continue;
                String id = ArmsArmoury.PREFIX[tier] + "_" + st.id();
                if (AnvilCosts.of(id) == null) continue;
                if (AnvilCosts.needs(p, id).stream().allMatch(n -> n.have() >= n.need())) return id;
            }
        }
        return null;
    }

    /** The best tier of each style the player carries (-1 none), by the style's index. */
    private static int[] carried(ServerPlayer p) {
        int[] out = new int[Archetypes.all().size()];
        java.util.Arrays.fill(out, -1);
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            ArmsData.Weapon w = s.isEmpty() ? null : ArmsItems.of(s.getItem());
            if (w == null) continue;
            Archetypes.Loaded st = Archetypes.get(w.style());
            int tier = java.util.Arrays.asList(ArmsArmoury.TIERS).indexOf(w.tier());
            if (st != null && st.index() < out.length) out[st.index()] = Math.max(out[st.index()], tier);
        }
        return out;
    }
}
