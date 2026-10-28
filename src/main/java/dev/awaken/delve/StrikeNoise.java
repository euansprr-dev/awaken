package dev.awaken.delve;

import dev.awaken.Awaken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Strikes are noise (SPEC §34.5): rock or ore broken under the rock calls the Hollows hunters that hear
 * ({@code #awaken:delve/hears_strikes}) within {@value #REACH} and aren't already after someone: they come to the
 * place. A Hushed pick makes none. A player's strikes are heard at most once every {@value #REST} ticks, so a vein
 * mined fast is one call, not twenty. The hunters keep their own light rule: lit, they still won't close in.
 */
public final class StrikeNoise {
    private StrikeNoise() {}

    public static final int REACH = 24, REST = 40;
    public static final TagKey<EntityType<?>> HEARS = TagKey.create(Registries.ENTITY_TYPE, Awaken.id("delve/hears_strikes"));
    private static final Map<UUID, Long> LAST = new HashMap<>();

    static void onBreak(BreakBlockEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel level)) return;
        if (StrikeBlocks.family(e.getState().getBlock()) == StrikeBlocks.Family.LOOSE || level.canSeeSky(e.getPos())) return;
        heard(level, e.getPos(), p);
    }

    /** The hunters a strike at {@code pos} calls (none with Hush, none while the player's last call rests). */
    public static List<Mob> heard(ServerLevel level, BlockPos pos, ServerPlayer p) {
        if (PickGifts.hushed(p)) return List.of();
        long now = level.getGameTime();
        Long last = LAST.get(p.getUUID());
        if (last != null && now - last < REST && now >= last) return List.of();
        LAST.put(p.getUUID(), now);
        List<Mob> called = level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(REACH),
                m -> m.isAlive() && m.getType().builtInRegistryHolder().is(HEARS) && m.getTarget() == null && m.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) <= REACH * REACH);
        for (Mob m : called) m.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
        return called;
    }

    static void forget(UUID id) {
        LAST.remove(id);
    }

    static void clear() {
        LAST.clear();
    }
}
