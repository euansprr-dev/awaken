package dev.awaken.citizens;

import dev.awaken.core.api.CitizenHooks;
import dev.awaken.core.api.CityHooks;
import dev.awaken.core.api.RenownHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Carillon's people (docs/SPEC.md §16.5; Milestone 3, "Alive"): 240 citizens on a ledger with a day each, bodies only
 * near players, the work that changes the city you see, the city's events, and a first step of recognition (who has
 * seen you, and who says your name). Installs {@code CitizenHooks} and a minimal {@code RenownHooks}.
 *
 * <p>The lamps' rule: inside the wall, monsters spawn naturally only near a lamp that is dark at night (broken, or not
 * yet lit). That is what makes the lamplighter matter.
 */
public final class CitizensModule {
    private CitizensModule() {}

    public static void init(IEventBus modBus) {
        CitizensContent.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent e) -> e.put(CitizensContent.CITIZEN.get(), CitizenBody.createAttributes().build()));
        modBus.addListener(CitizensNet::register);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre e) -> Citizens.onTickPre(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> Citizens.onTickPost(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> Citizens.stop());
        NeoForge.EVENT_BUS.addListener(CitizensModule::onLogin);
        NeoForge.EVENT_BUS.addListener(CitizensModule::onSpawnCheck);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, CitizensModule::onLeftClick);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, CitizensModule::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, CitizensModule::onInteract);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, CitizensModule::onBroken);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, CitizensModule::onExploded);
        CitizenHooks.install(new CitizenAccess());
        RenownHooks.install(new RenownAccess());
        CitizensCommands.register();
        Soundscape.register();
        CitizensTests.register();
        CityLifeTests.register();
    }

    /** A citizen's stable id for other modules (CitizenHooks.witnesses). */
    public static UUID uuid(int rec) {
        return UUID.nameUUIDFromBytes(("awaken:citizen/" + rec).getBytes(StandardCharsets.UTF_8));
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        Citizens c = Citizens.active;
        if (c != null && e.getEntity() instanceof ServerPlayer sp) c.work.sendChimneys(sp);
    }

    /** Inside the wall, monsters come only where a lamp is dark at night. */
    private static void onSpawnCheck(MobSpawnEvent.SpawnPlacementCheck e) {
        Citizens c = Citizens.active;
        if (c == null || e.getSpawnType() != EntitySpawnReason.NATURAL || e.getEntityType().getCategory() != MobCategory.MONSTER) return;
        ServerLevel level = e.getLevel().getLevel();
        if (level != c.level || !CityHooks.inCity(level, e.getPos())) return;
        if (!c.work.darkLampNear(e.getPos())) e.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
    }

    /** Beating out a flame or punching a spilled crate away: allowed in the city, and seen. */
    private static void onLeftClick(PlayerInteractEvent.LeftClickBlock e) {
        Citizens c = Citizens.active;
        if (c == null || !(e.getEntity() instanceof ServerPlayer p) || e.getLevel() != c.level) return;
        BlockState s = c.level.getBlockState(e.getPos());
        if (s.is(CitizensContent.CITY_FIRE.get()) && c.events.beatFlame(p, e.getPos())) e.setCanceled(true);
        else if (s.is(CitizensContent.CARGO_CRATE.get()) && c.events.clearCrate(p, e.getPos())) e.setCanceled(true);
    }

    /** Pulling a jammed door open in a fire. */
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        Citizens c = Citizens.active;
        if (c == null || !(e.getEntity() instanceof ServerPlayer p) || e.getLevel() != c.level) return;
        if (c.events.rescue(p, e.getPos())) e.setCanceled(true);
    }

    /** Speaking to someone: they answer the way they know you (a trapped one is pulled out). */
    private static void onInteract(PlayerInteractEvent.EntityInteract e) {
        Citizens c = Citizens.active;
        if (c == null || !(e.getEntity() instanceof ServerPlayer p) || !(e.getTarget() instanceof CitizenBody b) || b.rec < 0) return;
        if (c.data.role[b.rec] == Roles.TRAPPED && c.events.rescue(p, b.blockPosition())) {
            e.setCanceled(true);
            return;
        }
        // a named citizen (Kestrel, Bram, Oren, Mira, Teodor, Sun) answers a Wayfarer chain first; then their own words
        String who = c.roster.named[b.rec];
        if (!who.isEmpty() && dev.awaken.living.api.Quests.talk(p, who)) {
            e.setCanceled(true);
            return;
        }
        // a Registry clerk answers "anything for me?" with the one next step that fits you (SPEC §27.8)
        if (c.roster.isCitizen(b.rec) && c.data.role[b.rec] == 0 && "clerk".equals(Roster.JOBS[c.roster.job[b.rec]])) {
            CitizensNet.say(p, b, dev.awaken.progression.Advice.next(p), 110, 0);
            e.setCanceled(true);
            return;
        }
        if (c.roster.isCitizen(b.rec) && c.data.role[b.rec] == 0) c.recognition.greet(b, b.rec, p, c.level.getGameTime(), true);
        e.setCanceled(true);
    }

    /** With protection off, what players break in the city waits for a mason (and someone may have seen it). */
    private static void onBroken(BreakBlockEvent e) {
        Citizens c = Citizens.active;
        if (c == null || e.isCanceled() || e.getLevel() != c.level || !(e.getPlayer() instanceof ServerPlayer p) || p.isCreative()) return;
        if (!CityHooks.isCivic(c.level, e.getPos())) return;
        BlockState s = e.getState();
        if (s.is(CitizensContent.CITY_FIRE.get()) || s.is(CitizensContent.CARGO_CRATE.get())) return;
        c.work.addRepair(e.getPos().immutable(), s, c.level.getGameTime() + 1200);
        c.recognition.witness(p, Identifier.fromNamespaceAndPath("awaken", "broke_civic"), e.getPos(), -1);
    }

    private static void onExploded(ExplosionEvent.Detonate e) {
        Citizens c = Citizens.active;
        if (c == null || e.getLevel() != c.level) return;
        for (BlockPos pos : e.getAffectedBlocks()) {
            if (!CityHooks.isCivic(c.level, pos)) continue;
            BlockState s = c.level.getBlockState(pos);
            if (!s.isAir()) c.work.addRepair(pos.immutable(), s, c.level.getGameTime() + 1200);
        }
    }

    private static final class CitizenAccess implements CitizenHooks.Access {
        @Override
        public List<UUID> witnesses(ServerLevel level, Vec3 at, double radius) {
            Citizens c = Citizens.active;
            if (c == null || level != c.level) return List.of();
            List<UUID> out = new ArrayList<>();
            for (int i : c.recognition.witnesses(at, radius)) out.add(uuid(i));
            return out;
        }

        @Override
        public void post(ServerLevel level, Identifier event, BlockPos pos) {
            Citizens c = Citizens.active;
            if (c != null && level == c.level) c.events.post(event, pos);
        }
    }

    private static final class RenownAccess implements RenownHooks.Access {
        @Override
        public void witness(ServerPlayer p, Identifier deed, BlockPos pos, int weight) {
            Citizens c = Citizens.active;
            if (c != null && p.level() == c.level) c.recognition.witness(p, deed, pos, weight);
        }

        @Override
        public int tier(Player p) {
            Citizens c = Citizens.active;
            return c == null ? RenownHooks.STRANGER : c.recognition.tier(p);
        }
    }
}
