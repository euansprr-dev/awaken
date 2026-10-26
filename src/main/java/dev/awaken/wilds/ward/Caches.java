package dev.awaken.wilds.ward;

import dev.awaken.Awaken;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.Throttle;
import dev.awaken.core.api.ArrivalHooks;
import dev.awaken.core.api.Promises;
import dev.awaken.core.api.WardHooks;
import dev.awaken.signal.Signal;
import dev.awaken.wilds.WildsContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.Nullable;

/**
 * A climb cache's inner seal (SPEC §17.27; the progression plan §5.5). A chest at a climb site (a Tether Spire's crown
 * deck, a sky platform's deck, a hero tree's crown) opens its ordinary roll to anyone who got there without building;
 * its best roll waits behind an inner seal that opens only to a climber the line carried (rope or wings: the Ascent's
 * own ladder, {@link ArrivalHooks#byLine}), once per climber, so nobody's open ever spends it for anyone else. Someone
 * who built their way up finds the cache shut: "The cache's inner seal holds. It opens to a climb." The best roll is
 * its own table beside the chest's ({@code chests/precursor_spire} keeps {@code chests/precursor_spire_best}).
 */
public final class Caches {
    private Caches() {}

    /** The seal's line, said at most this often per player (the hold light and tone answer every time). */
    static final int TELL_EVERY = 100;
    /** The best roll's table beside a cache's: "_best" on the end. */
    static final String BEST = "_best";

    static void onUse(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel level)) return;
        // sneaking with something in hand never opens a chest: vanilla uses the item instead
        if (p.isSecondaryUseActive() && !(p.getMainHandItem().isEmpty() && p.getOffhandItem().isEmpty())) return;
        if (!opens(p, level, e.getPos())) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /**
     * A player opens the chest at {@code pos}: false when a climb cache's seal keeps it shut for them (they built their
     * way up). Otherwise its ordinary roll unpacks as usual, and its best roll joins it when the line carried them and
     * the seal hasn't opened to them yet. Not a climb cache: true, untouched.
     */
    public static boolean opens(ServerPlayer p, ServerLevel level, BlockPos pos) {
        if (!Wards.on() || !(level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity chest)) return true;
        var ward = WardHooks.at(level, pos);
        if (ward.isEmpty() || !Wards.climb(ward.get())) return true;
        InnerSeal seal = chest.getExistingDataOrNull(WildsContent.INNER_SEAL);
        String best = seal != null && seal.cache() ? seal.best() : bestOf(chest.getLootTable());
        if (best == null) return true;
        if (!Wards.binds(p)) {
            // a builder in creative opens it as it is, but the seal is stamped first so the cache stays a cache
            if (seal == null || !seal.cache()) store(chest, new InnerSeal(InnerSeal.SCHEMA, best, java.util.List.of()));
            return true;
        }
        if (!ArrivalHooks.byMovement(p)) {
            WardFx.play(level, WardFx.Kind.HOLD, pos, level.getBlockState(pos));
            if (Throttle.allow(p, "inner_seal", TELL_EVERY)) p.sendOverlayMessage(Component.translatable("awaken.wilds.cache.sealed"));
            DirectorLog.log(p, "wilds", "a climb cache stays shut to a built arrival at " + pos.toShortString());
            return false;
        }
        if (seal == null || !seal.cache()) {
            // the first open stamps the seal: the ordinary roll unpacks now, the best one waits behind it
            chest.unpackLootTable(p);
            seal = new InnerSeal(InnerSeal.SCHEMA, best, java.util.List.of());
        }
        if (seal.openedTo(p.getUUID())) {
            store(chest, seal);
            return true;
        }
        if (!ArrivalHooks.byLine(p)) {
            store(chest, seal);
            if (Throttle.allow(p, "inner_seal", TELL_EVERY)) p.sendOverlayMessage(Component.translatable("awaken.wilds.cache.sealed"));
            return true;
        }
        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, net.minecraft.resources.Identifier.parse(seal.best()));
        LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
        table.fill(chest, new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.THIS_ENTITY, p).withLuck(p.getLuck()).create(LootContextParamSets.CHEST), level.getRandom().nextLong());
        store(chest, seal.openTo(p.getUUID()));
        WardFx.play(level, WardFx.Kind.UNSEAL, pos, level.getBlockState(pos));
        Signal.toast(p, Component.translatable("awaken.wilds.cache.unsealed"));
        // the climb this site waited for is made: its promise is kept
        Promises.kept(p, ClimbWard.promiseKey(ward.get()));
        DirectorLog.log(p, "wilds", "a climb cache's inner seal opens to the line at " + pos.toShortString() + " (" + seal.best() + ")");
        return true;
    }

    /** The best roll's table for a cache's own, or null: only a climb's precursor tables keep one. */
    static @Nullable String bestOf(@Nullable ResourceKey<LootTable> table) {
        if (table == null || !table.identifier().getNamespace().equals(Awaken.MODID)) return null;
        String path = table.identifier().getPath();
        if (!path.equals("chests/precursor_spire") && !path.equals("chests/precursor_sky")) return null;
        return Awaken.id(path + BEST).toString();
    }

    /** A climb cache at {@code pos}: a chest with a best roll waiting (its table, or a stamped seal). Kept whole by the ward. */
    static boolean isCache(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity chest)) return false;
        InnerSeal seal = chest.getExistingDataOrNull(WildsContent.INNER_SEAL);
        return seal != null && seal.cache() || bestOf(chest.getLootTable()) != null;
    }

    private static void store(RandomizableContainerBlockEntity chest, InnerSeal seal) {
        chest.setData(WildsContent.INNER_SEAL, seal);
        chest.setChanged();
    }
}
