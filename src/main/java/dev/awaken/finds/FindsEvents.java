package dev.awaken.finds;

import dev.awaken.finds.verb.Ctx;
import dev.awaken.finds.verb.Script;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Everything that happens to a block reaches a find here (SPEC §58.5). A part is only a position; these listeners ask
 * the loaded hearts whose site a position belongs to and hand the event to that heart's script. The same listeners are
 * the ward: a site's own blocks can't be broken or built on by a survival player unless its script says so.
 */
final class FindsEvents {
    private FindsEvents() {}

    static void init(IEventBus bus) {
        bus.addListener(FindsEvents::onUse);
        bus.addListener(FindsEvents::onHit);
        bus.addListener(FindsEvents::onBreak);
        bus.addListener(FindsEvents::onPlace);
        bus.addListener(FindsEvents::onShot);
        bus.addListener(FindsEvents::onPlayerTick);
        bus.addListener(FindsEvents::onSpawnCheck);
        bus.addListener(ServerStoppedEvent.class, e -> {
            Sites.reset();
            Hearts.reset();
        });
        if (CENSUS != null) bus.addListener(FindsEvents::countAndStop);
    }

    /**
     * A count without a client (SPEC §62.9): with AWAKEN_FINDS_CENSUS=&lt;rings of cells&gt; in its environment a dedicated
     * dev server counts the world's monuments into its log as soon as the regions are known, and stops. The count asks
     * only the generator's noise, so no land is made for it: a world of 17 km square is counted in a minute or two,
     * where a look tour waits its turn on a runner for hours.
     */
    private static final String CENSUS = System.getenv("AWAKEN_FINDS_CENSUS");
    private static boolean counted;

    private static void countAndStop(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) {
        var server = e.getServer();
        if (counted || !server.isDedicatedServer() || dev.awaken.living.region.RegionService.mapIfReady(server) == null) return;
        counted = true;
        ServerLevel level = server.overworld();
        for (String line : Sites.census(level, Integer.parseInt(CENSUS.trim()))) dev.awaken.Awaken.LOG.info("[finds] census {}", line);
        if (System.getenv("AWAKEN_FINDS_REAL") != null) {
            // a fair sample, never a flattering one: the four a player meets first, the eight on the roughest ground,
            // and the nearest of every family's painted one (or of whatever of the family stands)
            Terrain t = Terrain.of(level);
            BlockPos spawn = level.getRespawnData().pos();
            java.util.List<Site> all = new java.util.ArrayList<>(Sites.counted);
            java.util.LinkedHashSet<Site> pick = new java.util.LinkedHashSet<>();
            java.util.Comparator<Site> near = java.util.Comparator.comparingDouble(s -> Math.hypot(s.x() - spawn.getX(), s.z() - spawn.getZ()));
            all.sort(near);
            for (int i = 0; i < Math.min(4, all.size()); i++) pick.add(all.get(i));
            java.util.Map<Site, Integer> rough = new java.util.HashMap<>();
            for (Site s : all) {
                int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, r = s.kind().reach();
                for (int j = 0; j < 24; j++) {
                    double a = j * Math.PI / 12;
                    int f = t.floor(s.x() + (int) Math.round(Math.cos(a) * r), s.z() + (int) Math.round(Math.sin(a) * r));
                    lo = Math.min(lo, f);
                    hi = Math.max(hi, f);
                }
                rough.put(s, hi - lo);
            }
            java.util.List<Site> byRough = new java.util.ArrayList<>(all);
            byRough.sort(java.util.Comparator.comparingInt((Site s) -> -rough.get(s)));
            for (int i = 0; i < Math.min(8, byRough.size()); i++) pick.add(byRough.get(i));
            for (Family f : Family.values()) {
                Site best = null;
                for (Site s : all) if (s.kind().family() == f && (best == null || (s.kind().tier() == 3 && best.kind().tier() != 3))) best = s;
                if (best != null) pick.add(best);
            }
            int[] spread = rough.values().stream().mapToInt(Integer::intValue).sorted().toArray();
            if (spread.length > 0) dev.awaken.Awaken.LOG.info("[finds] census the land's fall across a monument's whole reach: middle {}, nine tenths within {}, most {}",
                    spread[spread.length / 2], spread[spread.length * 9 / 10], spread[spread.length - 1]);
            FindsDump.real(level, java.nio.file.Path.of("finds-real"), new java.util.ArrayList<>(pick));
        }
        server.halt(false);
    }

    private static void resync(ServerPlayer p, BlockPos pos) {
        p.connection.send(new ClientboundBlockUpdatePacket(p.level(), pos));
        for (var d : net.minecraft.core.Direction.values()) p.connection.send(new ClientboundBlockUpdatePacket(p.level(), pos.relative(d)));
    }

    private static void onUse(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel level) || e.getHand() != InteractionHand.MAIN_HAND || p.isSpectator()) return;
        FindHeartBlockEntity be = Hearts.at(level, e.getPos());
        if (be == null) return;
        Ctx c = be.ctx();
        Script s = be.script();
        if (c == null || s == null) return;
        Scene.PartRef ref = c.scene.partAt(c.site.rel(e.getPos()));
        if (ref == null) return;
        boolean handled = c.done() ? Reward.late(c, p) : s.use(c, p, ref.name(), ref.index(), e.getItemStack());
        if (handled) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS_SERVER);
            p.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
            resync(p, e.getPos());
        }
    }

    private static void onHit(PlayerInteractEvent.LeftClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel level) || e.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        FindHeartBlockEntity be = Hearts.at(level, e.getPos());
        if (be == null) return;
        Ctx c = be.ctx();
        Script s = be.script();
        if (c == null || s == null || c.done()) return;
        Scene.PartRef ref = c.scene.partAt(c.site.rel(e.getPos()));
        if (ref == null) return;
        if (s.hit(c, p, ref.name(), ref.index(), p.getMainHandItem()) && !p.isCreative()) e.setCanceled(true);
    }

    private static void onBreak(BreakBlockEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel level) || p.isCreative()) return;
        FindHeartBlockEntity be = Hearts.at(level, e.getPos());
        if (be == null) return;
        Ctx c = be.ctx();
        Script s = be.script();
        if (c == null || s == null) return;
        BlockPos rel = c.site.rel(e.getPos());
        Scene.PartRef ref = c.scene.partAt(rel);
        if (ref != null && s.mayBreak(c, p, ref.name(), ref.index())) {
            level.getServer().execute(() -> {
                Ctx later = be.ctx();
                if (later != null && !be.isRemoved()) s.broke(later, p, ref.name(), ref.index());
            });
            return;
        }
        if (ref != null || c.scene.built(rel)) e.setCanceled(true);
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel level) || p.isCreative()) return;
        FindHeartBlockEntity be = Hearts.at(level, e.getPos());
        if (be == null) return;
        Ctx c = be.ctx();
        Script s = be.script();
        if (c == null || s == null) return;
        BlockPos rel = c.site.rel(e.getPos());
        if (!c.scene.inBox(rel, 1)) return;
        if (s.mayPlace(c, p, rel, e.getPlacedBlock())) {
            level.getServer().execute(() -> {
                Ctx later = be.ctx();
                if (later != null && !be.isRemoved()) s.placed(later, p, rel, e.getPlacedBlock());
            });
            return;
        }
        e.setCanceled(true);
    }

    private static void onShot(ProjectileImpactEvent e) {
        Projectile proj = e.getProjectile();
        if (!(proj.level() instanceof ServerLevel level) || !(e.getRayTraceResult() instanceof BlockHitResult hit)) return;
        FindHeartBlockEntity be = Hearts.at(level, hit.getBlockPos());
        if (be == null) return;
        Ctx c = be.ctx();
        Script s = be.script();
        if (c == null || s == null || c.done()) return;
        Scene.PartRef ref = c.scene.partAt(c.site.rel(hit.getBlockPos()));
        if (ref == null) return;
        s.shot(c, proj.getOwner() instanceof ServerPlayer sp ? sp : null, ref.name(), ref.index());
    }

    /** A find that wards its ground once it's finished (a road made safe) keeps hostile spawns off it. */
    private static void onSpawnCheck(net.neoforged.neoforge.event.entity.living.MobSpawnEvent.SpawnPlacementCheck e) {
        if (e.getEntityType().getCategory() != net.minecraft.world.entity.MobCategory.MONSTER) return;
        ServerLevel level = e.getLevel().getLevel();
        BlockPos p = e.getPos();
        for (FindHeartBlockEntity be : Hearts.all(level)) {
            Site s = be.site();
            if (s == null || s.kind().ward() <= 0 || !be.done()) continue;
            int r = s.kind().ward();
            if (Math.abs(p.getX() - s.x()) <= r && Math.abs(p.getZ() - s.z()) <= r) {
                e.setResult(net.neoforged.neoforge.event.entity.living.MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
                return;
            }
        }
    }

    /** Something heavy in the hand slows you: a stone sheep is not carried at a run. */
    private static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || (p.tickCount + p.getId()) % 10 != 0) return;
        if (p.getMainHandItem().is(FindsModule.HEAVY) || p.getOffhandItem().is(FindsModule.HEAVY))
            p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 25, 1, true, false, false));
    }
}
