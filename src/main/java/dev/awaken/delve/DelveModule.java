package dev.awaken.delve;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The deep (SPEC §28.3; PLAN section 7, "Metal from the deep"): lodes with hearts and the vein giving, fair cave-ins,
 * Deepglass, starmetal and bell bronze, the heavy strike, the Tuning Fork, pitons and Runner's rope, the miner's lamp.
 * The strike's feel, the vein's light, the fork's song and the lamp are client-side ({@code delve.client}). Other modules
 * ask through {@link dev.awaken.delve.api.Lodes}. See docs/modules/delve.md.
 */
public final class DelveModule {
    private DelveModule() {}

    public static void init(IEventBus modBus) {
        DelveContent.register(modBus);
        modBus.addListener(DelveNet::register);
        var bus = NeoForge.EVENT_BUS;
        // after every protection has had its say (they cancel at HIGH): a heart that really broke opens its lode
        bus.addListener(EventPriority.LOW, false, (BreakBlockEvent e) -> Veins.onBreak(e));
        bus.addListener(Veins::onBreakSpeed);
        // after every other speed (Veins' two blows included): true strikes multiply what the block would take
        bus.addListener(EventPriority.LOWEST, false, (PlayerEvent.BreakSpeed e) -> TrueStrikes.onBreakSpeed(e));
        bus.addListener(EventPriority.LOW, false, (BlockDropsEvent e) -> TrueStrikes.onDrops(e));
        bus.addListener(EventPriority.LOWEST, false, (BlockEvent.EntityPlaceEvent e) -> onPlace(e));
        bus.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer p) Veins.tickPlayer(p);
        });
        bus.addListener((ServerTickEvent.Post e) -> CaveIns.tick(e.getServer()));
        // the Delver's Pick (SPEC §34.4): its hum, kept through death
        bus.addListener((ServerTickEvent.Post e) -> WakingPick.tick(e.getServer()));
        bus.addListener(EventPriority.LOW, false, (net.neoforged.neoforge.event.entity.living.LivingDropsEvent e) -> WakingPick.onDrops(e));
        bus.addListener((PlayerEvent.PlayerRespawnEvent e) -> WakingPick.onRespawn(e));
        // its verses' deeds and gifts (SPEC §34.4)
        bus.addListener(EventPriority.LOW, false, (BreakBlockEvent e) -> {
            if (e.getPlayer() instanceof ServerPlayer p && e.getState().is(DelveContent.DEEPGLASS.get())) PickVerses.deed(p, PickVerses.Verse.GLASS);
        });
        bus.addListener(PickGifts::onLeftClick);
        // strikes are noise to the Hollows (SPEC §34.5)
        bus.addListener(EventPriority.LOW, false, (BreakBlockEvent e) -> StrikeNoise.onBreak(e));
        // the Delver lamps (SPEC §34.6)
        bus.addListener(Lamps::onLeftClick);
        bus.addListener((ServerTickEvent.Post e) -> Lamps.tick(e.getServer()));
        bus.addListener(PickGifts::onRightClick);
        bus.addListener(PickGifts::onGameEvent);
        bus.addListener(PickGifts::onBreakSpeed);
        bus.addListener((ServerTickEvent.Post e) -> PickGifts.tick(e.getServer()));
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                PickGifts.onJoin(p);
                Lamps.onJoin(p);
            }
        });
        bus.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                PickGifts.onJoin(p);
                Lamps.onJoin(p);
            }
        });
        // the wall lodes read the overworld's own noise caves (SPEC §34.2)
        bus.addListener((LevelEvent.Load e) -> {
            if (e.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
                var gen = level.getChunkSource().getGenerator();
                var rs = level.getChunkSource().randomState();
                LodePlans.columns(level.getSeed(), (x, z) -> gen.getBaseColumn(x, z, level, rs));
            }
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            Veins.forget(e.getEntity().getUUID());
            HeavyStrikes.forget(e.getEntity().getUUID());
            TrueStrikes.forget(e.getEntity().getUUID());
            WakingPick.forget(e.getEntity().getUUID());
            PickGifts.forget(e.getEntity().getUUID());
            StrikeNoise.forget(e.getEntity().getUUID());
        });
        bus.addListener((ServerStoppedEvent e) -> {
            Veins.clear();
            CaveIns.clear();
            HeavyStrikes.clear();
            TrueStrikes.clear();
            WakingPick.clear();
            PickGifts.clear();
            Lamps.clear();
            StrikeNoise.clear();
            LodePlans.EXTRA.clear();
            LodePlans.columns(0L, null);
        });
        DelveCommands.register();
        DelveTests.register();
        // W5, caves worth entering (SPEC §28.5): the places, a way home (with the vents and the Descent's dressing),
        // the senses' sound events
        dev.awaken.delve.cave.CaveModule.init(modBus);
        dev.awaken.delve.home.HomeModule.init(modBus);
        dev.awaken.delve.sense.SenseModule.init(modBus);
    }

    /** A solid block a player built underground: that section's roof never caves in. */
    private static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer) || !(e.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (!e.getPlacedBlock().isCollisionShapeFullBlock(level, e.getPos())) return;
        DelveData.get(level).built(e.getPos());
    }
}
