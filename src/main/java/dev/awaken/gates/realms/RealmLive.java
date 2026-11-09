package dev.awaken.gates.realms;

import dev.awaken.core.api.MobRole;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateRegistry;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.block.GateBlocks;
import dev.awaken.gates.run.Hud;
import dev.awaken.gates.run.Rooms;
import dev.awaken.gates.run.RunState;
import dev.awaken.gates.run.Runs;
import dev.awaken.gates.world.FloorLayout;
import dev.awaken.gates.world.GateLayout;
import dev.awaken.progression.Curve;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;
import java.util.Set;

/**
 * What happens in a Realm while someone is in it (SPEC §50.7, §50.8). Runs hands a Realm gate's players here before any of
 * the floors' code sees them, so nothing here runs for the gates of old and nothing of theirs runs here. Monsters wake as
 * you come near their place. The Wardens fall in any order; when the last one does, the way to the Lord is open.
 */
public final class RealmLive {
    private RealmLive() {}

    private static final String SITE = "awaken_realm_site";
    private static final int WAKE = 56, LORD_WAKE = 44;
    /** Of the pay that isn't earned kill by kill, what one Warden banks; the Lord pays whatever's left. */
    private static final double WARDEN_SHARE = 1 / 6.0;

    /** First steps in: the arch home is lit once, and the System says the only thing it will say about the place. */
    public static void arrive(ServerPlayer p, GateRecord g) {
        MinecraftServer server = p.level().getServer();
        ServerLevel level = Realms.level(server);
        RealmPlan plan = Realms.plan(g);
        if (level == null || plan == null) return;
        RealmData data = RealmData.get(server);
        RealmState st = data.state(g.id);
        if (!st.built) {
            Land.Site s = plan.land().site(Land.SiteKind.PORTAL);
            BlockPos at = Realms.world(g.realm.orElseThrow().cell(), s.x(), s.y(), s.z());
            ((GateBlocks.Portal) GatesRegistry.EXIT.get()).place(level, at, Direction.Axis.Z, 3, 3, g.plan.rank().ordinal());
            st.built = true;
            data.setDirty();
        }
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.GATE, Component.literal(g.plan.rank().label + "-rank · " + plan.bp().name()))
                .body(Component.literal("The arch behind you is the way home. It stays open for as long as you're here."))
                .priority(4).ttl(20 * 8).build());
    }

    /** Every half second for each player in a Realm: bring them back if they've left it, and wake what's near. */
    public static void tickPlayer(MinecraftServer server, ServerPlayer p, RunState run, GateRecord g, int t) {
        ServerLevel level = Realms.level(server);
        RealmPlan plan = Realms.plan(g);
        if (level == null || plan == null || p.level() != level) return;
        RealmRef ref = g.realm.orElseThrow();
        int lx = p.getBlockX() - RealmCells.centerX(ref.cell()), lz = p.getBlockZ() - RealmCells.centerZ(ref.cell());
        if (RealmCells.cellAt(p.getBlockX(), p.getBlockZ()) != ref.cell() || p.getY() < -16 || Math.max(Math.abs(lx), Math.abs(lz)) > plan.land().halfSize() - 8) {
            BlockPos e = Realms.entrance(g);
            p.teleportTo(level, e.getX() + 0.5, e.getY(), e.getZ() + 0.5, Set.of(), p.getYRot(), p.getXRot(), true);
            return;
        }
        wake(server, level, g, plan, ref, lx, lz);
        if (((t + p.getId()) / 10) % 2 == 0) Hud.send(p);
    }

    private static void wake(MinecraftServer server, ServerLevel level, GateRecord g, RealmPlan plan, RealmRef ref, int lx, int lz) {
        RealmData data = RealmData.get(server);
        RealmState st = data.state(g.id);
        Land land = plan.land();
        FloorLayout f = GateLayout.of(g.plan).floor(0, 1);
        for (Land.Site s : land.sites()) {
            int dx = s.x() - lx, dz = s.z() - lz, d2 = dx * dx + dz * dz;
            BlockPos at = Realms.world(ref.cell(), s.x(), s.y(), s.z());
            switch (s.kind()) {
                case DEN -> {
                    if (d2 >= WAKE * WAKE || !level.isLoaded(at) || !st.woken.add("den" + s.index())) continue;
                    den(level, g, f, land, ref, s);
                    data.setDirty();
                }
                case WARDEN -> {
                    if (d2 >= WAKE * WAKE || !level.isLoaded(at) || !st.woken.add("warden" + s.index())) continue;
                    LivingEntity e = Rooms.spawn(level, g, f, -1, MobRole.Role.WARDEN, at);
                    if (e != null) e.getPersistentData().putInt(SITE, s.index());
                    data.setDirty();
                }
                case LORD -> {
                    if (!st.heartOpen || st.lordWoken || d2 >= LORD_WAKE * LORD_WAKE || !level.isLoaded(at)) continue;
                    LivingEntity e = Rooms.spawn(level, g, f, -1, MobRole.Role.BOSS, at);
                    if (e == null) continue;
                    g.boss = Optional.of(e.getUUID());
                    st.lordWoken = true;
                    data.setDirty();
                    GateRegistry.get(server).setDirty();
                }
                default -> {}
            }
        }
    }

    private static void den(ServerLevel level, GateRecord g, FloorLayout f, Land land, RealmRef ref, Land.Site s) {
        var rnd = level.getRandom();
        int n = 3 + g.plan.rank().ordinal() / 2 + rnd.nextInt(2);
        for (int i = 0; i < n; i++) {
            int x = s.x() + rnd.nextInt(13) - 6, z = s.z() + rnd.nextInt(13) - 6, y = land.ground(x, z) + 1;
            if (y <= land.water() + 1) continue;
            Rooms.spawn(level, g, f, -1, i == 0 && n >= 4 ? MobRole.Role.ELITE : MobRole.Role.NORMAL, Realms.world(ref.cell(), x, y, z));
        }
    }

    /** Mobs dying: a Warden banks part of the land's pay for everyone here, and the last one opens the way. */
    public static void mobDied(ServerLevel level, GateRecord g, LivingEntity dead, MobRole role) {
        if (role.role() != MobRole.Role.WARDEN) return;
        int index = dead.getPersistentData().getIntOr(SITE, -1);
        RealmPlan plan = Realms.plan(g);
        if (index < 0 || plan == null) return;
        MinecraftServer server = level.getServer();
        RealmData data = RealmData.get(server);
        RealmState st = data.state(g.id);
        if (!st.wardensDown.add(index)) return;
        for (ServerPlayer p : level.players()) {
            RunState run = Runs.run(p);
            if (run.gateId != g.id) continue;
            run.satchelXp += pool(p, g) * (1 - Curve.KILL_SHARE) * WARDEN_SHARE;
            Hud.send(p);
        }
        if (st.wardensDown.size() >= plan.land().sites(Land.SiteKind.WARDEN).size()) st.heartOpen = true;
        data.setDirty();
    }

    /** The Lord is down: a way out opens where it fell, beside the long walk back. */
    public static void lordFell(ServerLevel level, GateRecord g) {
        RealmPlan plan = Realms.plan(g);
        if (plan == null) return;
        Land.Site s = plan.land().site(Land.SiteKind.LORD);
        BlockPos at = Realms.world(g.realm.orElseThrow().cell(), s.x() + 9, s.y(), s.z());
        ((GateBlocks.Portal) GatesRegistry.EXIT.get()).place(level, at, Direction.Axis.X, 3, 3, g.plan.rank().ordinal());
        BlockPos c = at.above();
        level.sendParticles(ParticleTypes.END_ROD, c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5, 40, 1, 1, 1, 0.05);
        level.playSound(null, c, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.8F, 1.2F);
    }

    // ---- pay (SPEC §50.3) -----------------------------------------------------------------------------------------------

    /** All a Realm pays you: what the floors of the same gate would have. */
    public static double pool(ServerPlayer p, GateRecord g) {
        double sum = 0;
        for (int floor = 1; floor <= g.plan.floors(); floor++) sum += Runs.floorReward(p, g, 0, floor);
        return sum;
    }

    /** How many shares the kill pay is split into: a den's monsters are about five, a Warden four. */
    public static int units(GateRecord g) {
        RealmPlan plan = Realms.plan(g);
        return plan == null ? 1 : Math.max(1, plan.land().sites(Land.SiteKind.DEN).size() * 5 + plan.land().sites(Land.SiteKind.WARDEN).size() * 4);
    }

    /** The share of the land's non-kill pay the Wardens have already banked. */
    public static double banked(MinecraftServer server, GateRecord g) {
        return Math.min(1, RealmData.get(server).state(g.id).wardensDown.size() * WARDEN_SHARE);
    }
}
