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
        bus.addListener(ServerStoppedEvent.class, e -> {
            Sites.reset();
            Hearts.reset();
        });
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

    /** Something heavy in the hand slows you: a stone sheep is not carried at a run. */
    private static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || (p.tickCount + p.getId()) % 10 != 0) return;
        if (p.getMainHandItem().is(FindsModule.HEAVY) || p.getOffhandItem().is(FindsModule.HEAVY))
            p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 25, 1, true, false, false));
    }
}
