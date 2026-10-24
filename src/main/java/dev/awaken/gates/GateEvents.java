package dev.awaken.gates;

import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobRole;
import dev.awaken.gates.content.Palette;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.net.GatePayloads;
import dev.awaken.gates.run.GateMobs;
import dev.awaken.gates.run.Hud;
import dev.awaken.gates.run.RunEvents;
import dev.awaken.gates.run.Rooms;
import dev.awaken.gates.run.Runs;
import dev.awaken.gates.world.GateLayout;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashSet;
import java.util.Set;

/** Wires game events to the gates module. */
final class GateEvents {
    private GateEvents() {}

    static void register() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(GateEvents::onServerTick);
        bus.addListener(GateEvents::onStarted);
        bus.addListener(GateEvents::onStopped);
        bus.addListener(GateEvents::onLogin);
        bus.addListener(GateEvents::onLogout);
        bus.addListener(GateEvents::onRespawn);
        bus.addListener(GateEvents::onDimension);
        bus.addListener(EventPriority.HIGH, GateEvents::onPlayerDeath);
        bus.addListener(GateEvents::onMobDeath);
        bus.addListener(GateEvents::onJoin);
        bus.addListener(GateEvents::onLeave);
        bus.addListener(GateEvents::onBreak);
        bus.addListener(GateEvents::onExplode);
        bus.addListener(GateEvents::onEntityInteract);
        RunEvents.register(bus);
    }

    private static void onServerTick(ServerTickEvent.Post e) {
        MinecraftServer server = e.getServer();
        GateService.tickBuilds(server);
        GateService.tickRemovals(server);
        Director.tick(server);
        RunEvents.tick(server);
        Breaks.tick(server);
        GateCard.tick(server);
        int t = server.getTickCount();
        if (t % 100 == 50) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (Math.floorMod(t + p.getUUID().hashCode(), 1000) < 100) GatePayloads.syncBeacons(p);
            }
        }
    }

    private static void onStarted(ServerStartedEvent e) {
        resetStatics();
        GateService.onStarted(e.getServer());
    }

    private static void onStopped(ServerStoppedEvent e) {
        GateService.onStopped(e.getServer());
        resetStatics();
    }

    /** Singleplayer opens another world in the same JVM: no per-world state may survive in statics. */
    private static void resetStatics() {
        GateMobs.clear();
        Breaks.clear();
        GateCard.resetAll();
        dev.awaken.gates.run.Hud.resetAll();
        dev.awaken.gates.run.Interactions.resetAll();
        dev.awaken.gates.run.Modifiers.resetAll();
        dev.awaken.gates.run.Twists.resetAll();
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        RunEvents.onLogin(p);
        GatePayloads.syncBeacons(p);
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) RunEvents.onLogout(p);
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        RunEvents.onRespawn(p);
        GatePayloads.syncBeacons(p);
    }

    private static void onDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        GatePayloads.syncBeacons(p);
        Hud.send(p);
    }

    private static void onPlayerDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer) RunEvents.onPlayerDeath(e);
    }

    private static void onMobDeath(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (dead instanceof ServerPlayer || !(dead.level() instanceof ServerLevel level) || e.isCanceled()) return;
        ServerPlayer killer = e.getSource().getEntity() instanceof ServerPlayer sp ? sp
                : dead.getKillCredit() instanceof ServerPlayer sp ? sp : null; // finished by fire, a fall or an Echo after your hit
        MobRole role = dead.getData(CoreApi.MOB_ROLE.get());
        RunEvents.onMobDeath(level, dead, killer, role);
        if (role.gateId().isEmpty()) return;
        GateRecord g;
        try {
            g = GateRegistry.get(level.getServer()).gate(Integer.parseInt(role.gateId()));
        } catch (NumberFormatException ex) {
            return;
        }
        if (g == null) return;
        if (level.dimension() == Level.OVERWORLD && g.state == GateState.BROKEN && g.lord.isPresent() && g.lord.get().equals(dead.getUUID())) {
            Breaks.onLordDeath(level, g, dead, killer);
            return;
        }
        if (!GateService.inGate(dead)) return;
        RunEvents.onGateMobDeath(level, g, dead, killer, role);
    }

    private static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide()) return;
        if (e.loadedFromDisk() && e.getLevel().dimension() == Level.OVERWORLD && e.getLevel() instanceof ServerLevel sl
                && Breaks.strayLord(sl.getServer(), e.getEntity())) {
            e.setCanceled(true);
            return;
        }
        if (!GateService.isGateLevel(e.getLevel())) return;
        if (!(e.getEntity() instanceof net.minecraft.world.entity.player.Player) && e.getLevel() instanceof ServerLevel gl) {
            // cells are reused while running: an entity saved with an older use of its cell leaves when loaded
            int cell = dev.awaken.gates.world.Cells.cellAt(e.getEntity().getBlockX(), e.getEntity().getBlockZ());
            if (cell >= 0) {
                int gen = GateRegistry.get(gl.getServer()).cellGen(cell);
                var data = e.getEntity().getPersistentData();
                if (e.loadedFromDisk()) {
                    if (data.getIntOr(CELL_GEN, 0) != gen) {
                        e.setCanceled(true);
                        return;
                    }
                } else if (gen > 0) data.putInt(CELL_GEN, gen);
            }
        }
        // an Inverted puppet saved with its chunk (server stop) is only a statue now: its player gets a fresh one
        if (e.loadedFromDisk() && e.getEntity().getPersistentData().getBooleanOr("awaken_gate_puppet", false)) {
            e.setCanceled(true);
            return;
        }
        if (e.loadedFromDisk()) GateMobs.onLoad(e.getEntity());
    }

    /** Persistent-data key: the generation of the cell an entity in the gate world belongs to. */
    static final String CELL_GEN = "awaken_cell_gen";

    private static void onLeave(EntityLeaveLevelEvent e) {
        if (e.getLevel().isClientSide() || !GateService.isGateLevel(e.getLevel())) return;
        Entity ent = e.getEntity();
        Entity.RemovalReason why = ent.getRemovalReason();
        if (why == null || !why.shouldDestroy()) return;
        GateMobs.Key key = GateMobs.remove(ent);
        if (key != null && e.getLevel() instanceof ServerLevel sl) RunEvents.mobGone(sl, key);
    }

    // ---- protection -------------------------------------------------------------------------------------------------

    private static Set<Block> fabric;

    /** Blocks gates are built from: players can't break them inside a gate (they can break what they placed). */
    static Set<Block> fabric() {
        Set<Block> f = fabric;
        if (f == null) {
            f = new HashSet<>();
            for (Theme t : Theme.values()) {
                Palette p = t.palette();
                for (BlockState s : new BlockState[]{p.fill(), p.floor(), p.floorAlt(), p.wall(), p.wallAlt(), p.pillar(), p.ceiling(), p.stair(), p.light(), p.hang(), p.carpet()}) f.add(s.getBlock());
                for (BlockState s : p.floorDecor()) f.add(s.getBlock());
                for (BlockState s : p.hangDecor()) f.add(s.getBlock());
                for (BlockState s : p.clutter()) f.add(s.getBlock());
            }
            for (var b : GatesRegistry.BLOCKS.getEntries()) f.add(b.get());
            f.add(net.minecraft.world.level.block.Blocks.WATER);
            f.add(net.minecraft.world.level.block.Blocks.LAVA);
            fabric = f;
        }
        return f;
    }

    private static void onBreak(BreakBlockEvent e) {
        if (e.getLevel().isClientSide() || !GateService.inGate((Level) e.getLevel(), e.getPos())) return;
        if (e.getPlayer() != null && e.getPlayer().isCreative()) return;
        if (fabric().contains(e.getState().getBlock())) e.setCanceled(true);
    }

    private static void onExplode(ExplosionEvent.Detonate e) {
        if (GateService.inGate(e.getLevel(), net.minecraft.core.BlockPos.containing(e.getExplosion().center()))) e.getAffectedBlocks().clear();
    }

    private static void onEntityInteract(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !GateService.inGate(p)) return;
        Entity target = e.getTarget();
        var data = target.getPersistentData();
        if (data.contains(Rooms.SPIRIT_TAG)) {
            RunEvents.talkToSpirit(p, data.getIntOr(Rooms.SPIRIT_TAG, -1), target);
            e.setCanceled(true);
        } else if (data.contains(Rooms.LORD_TAG)) {
            RunEvents.friendlyLord(p, data.getIntOr(Rooms.LORD_TAG, -1), target);
        }
    }

    static GateLayout layoutOf(GateRecord g) {
        return GateLayout.of(g.plan);
    }

    static boolean inRun(ServerPlayer p) {
        return Runs.run(p).active();
    }
}
