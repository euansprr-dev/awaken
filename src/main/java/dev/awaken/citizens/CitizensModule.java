package dev.awaken.citizens;

import dev.awaken.citizens.api.Settlement;
import dev.awaken.citizens.api.SettlementSpec;
import dev.awaken.citizens.api.Settlements;
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
import net.minecraft.world.level.Level;
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
import java.util.Optional;
import java.util.UUID;

/**
 * The people engine (docs/SPEC.md §16.5, §28.4): Carillon's 460 citizens on a ledger with a day each, and since W0 any
 * number of settlements beside them (W3's villages), each on its own ledger and ground, bodies only near players out of
 * one shared pool, the work that changes their streets, their events, and what each remembers of players (who has seen
 * you, and who says your name). Installs {@code CitizenHooks}, a minimal {@code RenownHooks} and
 * {@code citizens.api.Settlements}.
 *
 * <p>The lamps' rule: inside Carillon's wall, monsters spawn naturally only near a lamp that is dark at night (broken,
 * or not yet lit). That is what makes the lamplighter matter.
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
        Settlements.install(new SettlementsAccess());
        CitizensCommands.register();
        Soundscape.register();
        CitizensTests.register();
        CityLifeTests.register();
        SettlementTests.register();
        VillageDayTests.register();
        GatheringTests.register();
    }

    /** A Carillon citizen's stable id for other modules (CitizenHooks.witnesses). */
    public static UUID uuid(int rec) {
        return UUID.nameUUIDFromBytes(("awaken:citizen/" + rec).getBytes(StandardCharsets.UTF_8));
    }

    /** Any settlement's citizen's stable id (Carillon's as before W0; another settlement's under its key). */
    static UUID uuid(dev.awaken.citizens.Settlement c, int rec) {
        return c.site.carillon() ? uuid(rec) : UUID.nameUUIDFromBytes(("awaken:citizen/" + c.key + "/" + rec).getBytes(StandardCharsets.UTF_8));
    }

    /** Whether a point (with a reach round it) touches a settlement: Carillon always, others within their radius. */
    static boolean near(dev.awaken.citizens.Settlement c, double x, double z, double reach) {
        if (c.site.carillon()) return true;
        double dx = x - c.site.cx(), dz = z - c.site.cz(), r = c.radius + reach;
        return dx * dx + dz * dz <= r * r;
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) Citizens.sendSites(sp);
    }

    /** Inside the wall, monsters come only where a lamp is dark at night (Carillon's rule; elsewhere vanilla's). */
    private static void onSpawnCheck(MobSpawnEvent.SpawnPlacementCheck e) {
        dev.awaken.citizens.Settlement c = Citizens.carillon();
        if (c == null || e.getSpawnType() != EntitySpawnReason.NATURAL || e.getEntityType().getCategory() != MobCategory.MONSTER) return;
        ServerLevel level = e.getLevel().getLevel();
        if (level != c.level || !CityHooks.inCity(level, e.getPos())) return;
        if (!c.work.darkLampNear(e.getPos())) e.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
    }

    /** Beating out a flame or punching a spilled crate away: allowed, and seen (whichever settlement's event it is). */
    private static void onLeftClick(PlayerInteractEvent.LeftClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        List<dev.awaken.citizens.Settlement> live = Citizens.live();
        for (int k = 0; k < live.size(); k++) {
            dev.awaken.citizens.Settlement c = live.get(k);
            if (e.getLevel() != c.level) continue;
            BlockState s = c.level.getBlockState(e.getPos());
            if (s.is(CitizensContent.CITY_FIRE.get()) && c.events.beatFlame(p, e.getPos())
                    || s.is(CitizensContent.CARGO_CRATE.get()) && c.events.clearCrate(p, e.getPos())) {
                e.setCanceled(true);
                return;
            }
        }
    }

    /** Pulling a jammed door open in a fire. */
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        List<dev.awaken.citizens.Settlement> live = Citizens.live();
        for (int k = 0; k < live.size(); k++) {
            dev.awaken.citizens.Settlement c = live.get(k);
            if (e.getLevel() == c.level && c.events.rescue(p, e.getPos())) {
                e.setCanceled(true);
                return;
            }
        }
    }

    /** Speaking to someone: they answer the way their settlement knows you (a trapped one is pulled out). */
    private static void onInteract(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getTarget() instanceof CitizenBody b)) return;
        e.setCanceled(true);
        e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        if (e.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND || !(e.getEntity() instanceof ServerPlayer p) || b.rec < 0) return;
        dev.awaken.citizens.Settlement c = b.home;
        if (c == null || !c.live) return;
        if (c.data.role[b.rec] == Roles.TRAPPED && c.events.rescue(p, b.blockPosition())) {
            e.setCanceled(true);
            return;
        }
        // (SPEC §43.3) something shown to them answers first (a keepsake with their family's name)
        var held = p.getMainHandItem();
        if (!held.isEmpty() && c.roster.isCitizen(b.rec)) {
            for (Settlements.Shown s : Settlements.shown()) {
                if (s.shown(p, c, b.rec, held)) return;
            }
        }
        // (SPEC §28.6) in a village, its people answer first: a trader's goods, the inn's rooms, the notice's news
        // (SPEC §28.7) someone a village's trouble stands in the square (staged) answers too: the parent, the elder
        if (!c.site.carillon() && c.roster.isCitizen(b.rec) && (c.data.role[b.rec] == 0 || c.data.role[b.rec] == Roles.STAGED)) {
            for (Settlements.Talk t : Settlements.talkers()) {
                if (t.talk(p, c, b.rec)) {
                    e.setCanceled(true);
                    return;
                }
            }
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

    /** With protection off, what players break in the city waits for a mason (and someone may have seen it). Carillon's. */
    private static void onBroken(BreakBlockEvent e) {
        dev.awaken.citizens.Settlement c = Citizens.carillon();
        if (c == null || e.isCanceled() || e.getLevel() != c.level || !(e.getPlayer() instanceof ServerPlayer p) || p.isCreative()) return;
        if (!CityHooks.isCivic(c.level, e.getPos())) return;
        BlockState s = e.getState();
        if (s.is(CitizensContent.CITY_FIRE.get()) || s.is(CitizensContent.CARGO_CRATE.get())) return;
        c.work.addRepair(e.getPos().immutable(), s, c.level.getGameTime() + 1200);
        c.recognition.witness(p, Identifier.fromNamespaceAndPath("awaken", "broke_civic"), e.getPos(), -1);
    }

    private static void onExploded(ExplosionEvent.Detonate e) {
        dev.awaken.citizens.Settlement c = Citizens.carillon();
        if (c == null || e.getLevel() != c.level) return;
        for (BlockPos pos : e.getAffectedBlocks()) {
            if (!CityHooks.isCivic(c.level, pos)) continue;
            BlockState s = c.level.getBlockState(pos);
            if (!s.isAir()) c.work.addRepair(pos.immutable(), s, c.level.getGameTime() + 1200);
        }
    }

    /** Witnesses come from every settlement near the point; the opening's posts, stages and named people are Carillon's. */
    private static final class CitizenAccess implements CitizenHooks.Access {
        @Override
        public List<UUID> witnesses(ServerLevel level, Vec3 at, double radius) {
            List<UUID> out = new ArrayList<>();
            List<dev.awaken.citizens.Settlement> live = Citizens.live();
            for (int k = 0; k < live.size(); k++) {
                dev.awaken.citizens.Settlement c = live.get(k);
                if (level != c.level || !c.awake || !near(c, at.x, at.z, radius)) continue;
                for (int i : c.recognition.witnesses(at, radius)) out.add(uuid(c, i));
            }
            return out;
        }

        @Override
        public void post(ServerLevel level, Identifier event, BlockPos pos) {
            dev.awaken.citizens.Settlement c = Citizens.carillon();
            if (c != null && level == c.level) c.events.post(event, pos);
        }

        @Override
        public void stage(ServerLevel level, String who, @org.jspecify.annotations.Nullable Vec3 at, float yaw, String act, String prop) {
            dev.awaken.citizens.Settlement c = Citizens.carillon();
            if (c != null && level == c.level) c.events.stage(who, at, yaw, act, prop);
        }

        @Override
        public @org.jspecify.annotations.Nullable Vec3 where(ServerLevel level, String who) {
            dev.awaken.citizens.Settlement c = Citizens.carillon();
            if (c == null || level != c.level) return null;
            int i = c.roster.namedIndex(who);
            CitizenBody b = i < 0 ? null : c.ledger.body[i];
            return b == null || b.isRemoved() ? null : b.position();
        }
    }

    /** A deed counts with every settlement whose people could have seen it; the city's tier stays Carillon's. */
    private static final class RenownAccess implements RenownHooks.Access {
        @Override
        public void witness(ServerPlayer p, Identifier deed, BlockPos pos, int weight) {
            List<dev.awaken.citizens.Settlement> live = Citizens.live();
            for (int k = 0; k < live.size(); k++) {
                dev.awaken.citizens.Settlement c = live.get(k);
                if (p.level() == c.level && c.awake && near(c, pos.getX() + 0.5, pos.getZ() + 0.5, 24)) c.recognition.witness(p, deed, pos, weight);
            }
        }

        @Override
        public int tier(Player p) {
            dev.awaken.citizens.Settlement c = Citizens.carillon();
            return c == null ? RenownHooks.STRANGER : c.recognition.tier(p);
        }
    }

    /** citizens.api.Settlements (W3's way in). */
    private static final class SettlementsAccess implements Settlements.Access {
        @Override
        public Optional<Settlement> register(ServerLevel level, SettlementSpec spec) {
            return Optional.ofNullable(Citizens.register(level, spec));
        }

        @Override
        public boolean unregister(int id) {
            return Citizens.unregister(id);
        }

        @Override
        public Optional<Settlement> at(Level level, BlockPos pos) {
            return Optional.ofNullable(Citizens.at(level, pos));
        }

        @Override
        public Optional<Settlement> byId(int id) {
            return Optional.ofNullable(Citizens.byId(id));
        }

        @Override
        public List<Settlement> all() {
            return List.copyOf(Citizens.live());
        }

        @Override
        public int bodies(int id) {
            dev.awaken.citizens.Settlement s = Citizens.byId(id);
            return s == null ? 0 : s.bodies();
        }
    }
}
