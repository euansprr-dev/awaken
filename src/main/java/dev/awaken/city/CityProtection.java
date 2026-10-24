package dev.awaken.city;

import dev.awaken.core.Throttle;
import dev.awaken.core.api.CityHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * Civic protection (SPEC §16.0.6): nobody breaks or places blocks in the city, pours buckets there or blows it up.
 * Creative players and operators may (they build and repair it). Server option {@code cityProtection} (per world,
 * {@code awaken-city-synced.toml}) turns it off. There are no player plots yet (MMO-lite later).
 */
final class CityProtection {
    private CityProtection() {}

    static final ModConfigSpec SPEC;
    static final ModConfigSpec.BooleanValue ON;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("carillon");
        ON = b.comment("Civic protection: players can't break or place blocks inside Carillon's walls (creative players and",
                "operators can). false turns it off for this world.").define("cityProtection", true);
        b.pop();
        SPEC = b.build();
    }

    static void registerConfig() {
        ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.SYNCED, SPEC, "awaken-city-synced.toml");
    }

    /** On unless the server says otherwise (on while the config isn't loaded, as in GameTests). */
    static boolean enabled() {
        try {
            return ON.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    /** Whether this player's hands are stopped at this block. */
    static boolean denies(Player p, Level level, BlockPos pos) {
        if (!(p instanceof ServerPlayer sp) || !enabled()) return false;
        if (sp.isCreative() || sp.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) return false;
        return CityHooks.isCivic(level, pos);
    }

    static void tell(Player p) {
        if (p instanceof ServerPlayer sp && Throttle.allow(sp, "city_protect", 40)) {
            sp.sendOverlayMessage(Component.translatable("awaken.city.protected"));
        }
    }

    static void onBreak(BreakBlockEvent e) {
        if (e.getLevel().isClientSide() || !(e.getLevel() instanceof Level level)) return;
        if (!denies(e.getPlayer(), level, e.getPos())) return;
        e.setCanceled(true);
        e.setNotifyClient(true);
        tell(e.getPlayer());
    }

    static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof Player p) || !(e.getLevel() instanceof Level level) || level.isClientSide()) return;
        if (!denies(p, level, e.getPos())) return;
        e.setCanceled(true);
        tell(p);
    }

    static void onUse(PlayerInteractEvent.RightClickBlock e) {
        if (e.getLevel().isClientSide() || !(e.getItemStack().getItem() instanceof BucketItem)) return;
        BlockPos at = e.getPos().relative(e.getFace() == null ? net.minecraft.core.Direction.UP : e.getFace());
        if (!denies(e.getEntity(), e.getLevel(), at)) return;
        e.setCanceled(true);
        tell(e.getEntity());
    }

    static void onExplode(ExplosionEvent.Detonate e) {
        if (e.getLevel().isClientSide() || !enabled()) return;
        e.getAffectedBlocks().removeIf(pos -> CityHooks.isCivic(e.getLevel(), pos));
    }
}
