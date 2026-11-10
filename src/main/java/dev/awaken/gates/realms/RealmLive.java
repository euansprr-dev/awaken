package dev.awaken.gates.realms;

import dev.awaken.core.api.MobRole;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateRegistry;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.block.GateBlocks;
import dev.awaken.gates.run.Hud;
import dev.awaken.gates.run.Interactions;
import dev.awaken.gates.run.Satchel;
import dev.awaken.gates.run.RealmPacks;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    /** A pack is put in the world this far off, so it is standing there before you can see it (SPEC §50.9). */
    private static final int WAKE = 80, LORD_WAKE = 44;
    /** When each gate's players last made headway (a pack woken, a monster down), waves sent since, and whether the hunter has come. Not saved: a restart is a rest. */
    private static final Map<Integer, long[]> HEADWAY = new HashMap<>();
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
            ((GateBlocks.Portal) GatesRegistry.EXIT.get()).place(level, at, plan.land().portalAxis(), 3, 3, g.plan.rank().ordinal());
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
        double over = plan.land().edge(lx, lz);
        if (over > 0) {
            // the valley's edge: no wall, just a wind that keeps turning you back, stronger the further you push
            double dx = -lx + 190, dz = -lz, l = Math.max(1, Math.hypot(dx, dz)), f = Math.min(1.6, 0.35 + over / 60);
            p.push(dx / l * f, 0.08, dz / l * f);
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p)); // a player moves on their own side
            if (t % 600 < 10) Signal.toast(p, Component.literal("The wind won't let you go any further this way."));
        }
        wake(server, level, g, plan, ref, lx, lz);
        if (RealmPacks.notice(level, p) > 0) madeHeadway(level, g);
        seal(server, level, p, g, plan, ref, lx, lz, t);
        pressure(level, p, g, plan, lx, lz);
        RealmAir.tick(p, g, plan, ref, t);
        RealmLife.tick(level, p, g, plan, ref, lx, lz, t);
        if (((t + p.getId()) / 10) % 2 == 0) Hud.send(p);
    }

    // ---- Waystones (SPEC §50.7) ----------------------------------------------------------------------------------------

    /** Laying a hand on a Waystone: it remembers you, keeps your Satchel safe, and offers the next one you've reached. */
    public static void useWaystone(ServerPlayer p, GateRecord g, BlockPos pos) {
        MinecraftServer server = p.level().getServer();
        RealmPlan plan = Realms.plan(g);
        if (plan == null) return;
        RealmRef ref = g.realm.orElseThrow();
        List<Land.Site> stones = plan.land().sites(Land.SiteKind.WAYSTONE);
        int here = -1;
        for (Land.Site s : stones) if (Realms.world(ref.cell(), s.x(), s.y(), s.z()).equals(pos)) here = s.index();
        if (here < 0) return;
        RealmData data = RealmData.get(server);
        RealmState st = data.state(g.id);
        boolean first = st.waystonesFound.add(here);
        if (first) data.setDirty();
        RunState run = Runs.run(p);
        int stacks = Satchel.count(run.satchel);
        long xp = Math.round(run.satchelXp);
        Runs.bankHere(p, g, "waystone" + here);
        Hud.send(p);
        // the next stone you've reached, going round; alone, it can only wait for you to find another
        int next = -1;
        for (int i = 1; i < stones.size() && next < 0; i++) {
            int k = (here + i) % stones.size();
            if (st.waystonesFound.contains(k)) next = k;
        }
        String safe = stacks > 0 || xp > 0 ? "Everything in your Satchel is safe now, whatever happens next." : "Your Satchel is empty, so there's nothing to keep safe yet.";
        var w = SignalWindow.builder(SignalWindow.Kind.GATE, Component.literal("Waystone"))
                .row("Here", capital(stones.get(here).what()))
                .row("Reached", st.waystonesFound.size() + " of " + stones.size())
                .body(Component.literal((first ? "It will remember you. " : "") + safe)).priority(5);
        if (next >= 0) w.choice(Interactions.WAYSTONE + next, "Go to " + stones.get(next).what(), "Stay here");
        else w.footer(Component.literal("Find another one and this stone will carry you to it.")).ttl(20 * 8);
        Signal.send(p, w.build());
        level(p).playSound(null, pos, GatesRegistry.SND_SHRINE.get(), SoundSource.BLOCKS, 1F, first ? 1F : 1.2F);
    }

    /** Carried to a Waystone you've reached: the land stays as you left it. */
    public static void travel(ServerPlayer p, GateRecord g, int index) {
        RealmPlan plan = Realms.plan(g);
        ServerLevel level = Realms.level(p.level().getServer());
        if (plan == null || level == null) return;
        RealmState st = RealmData.get(p.level().getServer()).state(g.id);
        List<Land.Site> stones = plan.land().sites(Land.SiteKind.WAYSTONE);
        if (index < 0 || index >= stones.size() || !st.waystonesFound.contains(index)) return;
        Land.Site s = stones.get(index);
        int stand = plan.land().ground(s.x() + 2, s.z() + 2) + 2;
        if (Math.abs(stand - s.y()) > 6) stand = s.y(); // a stone high up over a lower floor: the ground under it isn't where you land
        BlockPos at = Realms.world(g.realm.orElseThrow().cell(), s.x() + 2, stand, s.z() + 2);
        level.getChunk(at); // the land there is made before you arrive
        p.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), p.getYRot(), p.getXRot(), true);
        level.playSound(null, at, GatesRegistry.SND_SHRINE.get(), SoundSource.BLOCKS, 1F, 0.8F);
    }

    private static ServerLevel level(ServerPlayer p) {
        return (ServerLevel) p.level();
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static void wake(MinecraftServer server, ServerLevel level, GateRecord g, RealmPlan plan, RealmRef ref, int lx, int lz) {
        RealmData data = RealmData.get(server);
        RealmState st = data.state(g.id);
        Land land = plan.land();
        FloorLayout f = GateLayout.of(g.plan).floor(0, 1);
        heart(server, level, g, plan, ref, st, data, f);
        RealmToll.tick(level, g, plan, ref, st);
        RealmRoots.tick(level, g, plan, ref, st);
        RealmSalt.tick(level, g, plan, ref, st);
        RealmHush.tick(level, g, plan, ref, st);
        RealmWax.tick(level, g, plan, ref, st);
        for (Land.Site s : land.sites()) {
            int dx = s.x() - lx, dz = s.z() - lz, d2 = dx * dx + dz * dz;
            BlockPos at = Realms.world(ref.cell(), s.x(), s.y(), s.z());
            switch (s.kind()) {
                case DEN -> {
                    if (d2 >= WAKE * WAKE || !level.isLoaded(at) || !st.woken.add("den" + s.index())) continue;
                    RealmPacks.den(level, g, f, at, s.index());
                    data.setDirty();
                }
                case WARDEN -> {
                    if (d2 >= WAKE * WAKE || !level.isLoaded(at) || !st.woken.add("warden" + s.index())) continue;
                    LivingEntity e = RealmPacks.warden(level, g, f, at, s.index());
                    if (e != null) e.getPersistentData().putInt(SITE, s.index());
                    data.setDirty();
                }
                default -> {}
            }
        }
    }

    // ---- the Lord's hall (SPEC §50.8) --------------------------------------------------------------------------------------

    /** Whoever comes in wakes the Lord; the way shuts behind them; the hall changes as the fight goes on; nobody left inside, it all resets. */
    private static void heart(MinecraftServer server, ServerLevel level, GateRecord g, RealmPlan plan, RealmRef ref, RealmState st, RealmData data, FloorLayout f) {
        Land land = plan.land();
        Land.Site lord = land.site(Land.SiteKind.LORD);
        boolean anyInside = false, anyAtDoor = false;
        for (ServerPlayer p : level.players()) {
            if (Runs.run(p).gateId != g.id) continue;
            int x = p.getBlockX() - RealmCells.centerX(ref.cell()), z = p.getBlockZ() - RealmCells.centerZ(ref.cell());
            boolean in = land.inHeart(x, p.getBlockY(), z);
            anyInside |= in;
            if (!in && Math.hypot(x - lord.x(), z - lord.z()) < LORD_WAKE + 24) anyAtDoor = true;
            if (!st.heartOpen && !in && Math.hypot(x - lord.x(), z - lord.z()) < LORD_WAKE + 14 && st.woken.add("door_line")) {
                if (land.heartClosedLine() != null) Signal.toast(p, Component.literal(land.heartClosedLine()));
                data.setDirty();
            }
        }
        if (st.heartOpen && !st.lordWoken && anyInside) {
            BlockPos at = Realms.world(ref.cell(), lord.x(), lord.y(), lord.z());
            LivingEntity e = Rooms.spawn(level, g, f, -1, MobRole.Role.BOSS, at);
            if (e == null) return;
            g.boss = Optional.of(e.getUUID());
            st.lordWoken = true;
            data.setDirty();
            GateRegistry.get(server).setDirty();
        }
        if (!st.lordWoken) return;
        LivingEntity boss = g.boss.map(level::getEntity).orElse(null) instanceof LivingEntity l && l.isAlive() ? l : null;
        // everyone's gone (fled or fallen): it goes back to sleep and the way in is open again, so nobody's ever locked out
        if (!anyInside && st.doorShut) {
            if (boss != null) boss.discard();
            g.boss = Optional.empty();
            st.lordWoken = false;
            st.doorShut = false;
            st.heartStage = 0;
            change(level, ref, land, "heart_still");
            change(level, ref, land, "heart_open");
            data.setDirty();
            GateRegistry.get(server).setDirty();
            return;
        }
        if (boss == null) return;
        if (!st.doorShut && anyInside && !anyAtDoor) {
            st.doorShut = true;
            change(level, ref, land, "heart_shut");
            level.playSound(null, boss.blockPosition(), SoundEvents.HONEY_BLOCK_PLACE, SoundSource.HOSTILE, 2F, 0.5F);
            data.setDirty();
        }
        float hp = boss.getHealth() / boss.getMaxHealth();
        int want = hp <= 0.33F ? 2 : hp <= 0.66F ? 1 : 0;
        while (st.heartStage < want) {
            st.heartStage++;
            change(level, ref, land, st.heartStage == 1 ? "heart_weeps" : "heart_winds");
            level.playSound(null, boss.blockPosition(), SoundEvents.DRIPSTONE_BLOCK_FALL, SoundSource.HOSTILE, 2F, 0.55F);
            data.setDirty();
        }
    }

    private static void change(ServerLevel level, RealmRef ref, Land land, String moment) {
        for (Land.Edit e : land.change(moment)) level.setBlock(Realms.world(ref.cell(), e.x(), e.y(), e.z()), e.state(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }

    /** The last Warden is down: the wax over the door breaks, whether or not anyone's there to see it. */
    public static void openHeart(ServerLevel level, GateRecord g) {
        RealmPlan plan = Realms.plan(g);
        if (plan == null) return;
        RealmData data = RealmData.get(level.getServer());
        RealmState st = data.state(g.id);
        st.heartOpen = true;
        RealmRef ref = g.realm.orElseThrow();
        change(level, ref, plan.land(), "heart_open");
        Land.Site lord = plan.land().site(Land.SiteKind.LORD);
        level.playSound(null, Realms.world(ref.cell(), lord.x(), lord.y(), lord.z()), SoundEvents.HONEY_BLOCK_BREAK, SoundSource.BLOCKS, 4F, 0.5F);
        data.setDirty();
    }

    private static long[] headway(ServerLevel level, GateRecord g) {
        return HEADWAY.computeIfAbsent(g.id, k -> new long[]{level.getGameTime(), 0, 0});
    }

    /** Someone woke a pack or put a monster down: the gate stops pressing for a while. */
    private static void madeHeadway(ServerLevel level, GateRecord g) {
        long[] h = headway(level, g);
        h[0] = level.getGameTime();
        h[1] = 0;
        h[2] = 0;
    }

    /** A Warden's fight holds you: once it's awake, the ground past its ring turns you back until one of you is down. */
    private static void seal(MinecraftServer server, ServerLevel level, ServerPlayer p, GateRecord g, RealmPlan plan, RealmRef ref, int lx, int lz, int t) {
        if (p.isCreative() || p.isSpectator()) return;
        RealmState st = RealmData.get(server).state(g.id);
        for (Land.Site s : plan.land().sites(Land.SiteKind.WARDEN)) {
            if (st.wardensDown.contains(s.index()) || !st.woken.contains("warden" + s.index())) continue;
            double dx = s.x() - lx, dz = s.z() - lz, d = Math.hypot(dx, dz), ring = Math.max(12, s.radius()) + 6;
            if (d <= ring || d > ring + 12 || Math.abs(p.getBlockY() - s.y()) > 16) continue;
            if (!RealmPacks.fighting(level, Realms.world(ref.cell(), s.x(), s.y(), s.z()), "w" + s.index())) continue;
            double f = Math.min(0.9, 0.3 + (d - ring) / 10);
            p.push(dx / d * f, 0.05, dz / d * f);
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p)); // a player moves on their own side
            if (t % 200 < 10) Signal.toast(p, Component.literal("It won't let you walk away from this."));
            return;
        }
    }

    /** No pack woken and nothing killed for four minutes: waves come looking for you, and at eight the hunter (SPEC §7's pressure, in a Realm). */
    private static void pressure(ServerLevel level, ServerPlayer p, GateRecord g, RealmPlan plan, int lx, int lz) {
        if (p.isCreative() || p.isSpectator() || level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) return;
        long[] h = headway(level, g);
        long quiet = level.getGameTime() - h[0];
        if (quiet < RealmPacks.QUIET + h[1] * RealmPacks.PERIOD || plan.land().inHeart(lx, p.getBlockY(), lz)) return;
        boolean hunter = quiet >= RealmPacks.HUNTER && h[2] == 0;
        FloorLayout f = GateLayout.of(g.plan).floor(0, 1);
        if (RealmPacks.wave(level, g, f, p, (int) h[1], hunter) > 0 && h[1] == 0) Signal.toast(p, Component.literal("You've been still too long. Something has come looking for you."));
        h[1]++;
        if (hunter) h[2] = 1;
    }

    /** Mobs dying: a Warden banks part of the land's pay for everyone here, and the last one opens the way. */
    public static void mobDied(ServerLevel level, GateRecord g, LivingEntity dead, MobRole role) {
        madeHeadway(level, g);
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
        if (st.wardensDown.size() >= plan.land().sites(Land.SiteKind.WARDEN).size()) openHeart(level, g);
        data.setDirty();
    }

    /** The Lord is down: a way out opens where it fell, beside the long walk back. */
    public static void lordFell(ServerLevel level, GateRecord g) {
        RealmPlan plan = Realms.plan(g);
        if (plan == null) return;
        Land.Site s = plan.land().site(Land.SiteKind.LORD);
        RealmData data = RealmData.get(level.getServer());
        RealmState st = data.state(g.id);
        change(level, g.realm.orElseThrow(), plan.land(), "heart_still");
        change(level, g.realm.orElseThrow(), plan.land(), "heart_open");
        st.doorShut = false;
        st.heartStage = 0;
        data.setDirty();
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

    /** How many shares the kill pay is split into: a den's monsters are about five, a Warden and its company seven. */
    public static int units(GateRecord g) {
        RealmPlan plan = Realms.plan(g);
        return plan == null ? 1 : Math.max(1, plan.land().sites(Land.SiteKind.DEN).size() * 5 + plan.land().sites(Land.SiteKind.WARDEN).size() * 7);
    }

    /** The share of the land's non-kill pay the Wardens have already banked. */
    public static double banked(MinecraftServer server, GateRecord g) {
        return Math.min(1, RealmData.get(server).state(g.id).wardensDown.size() * WARDEN_SHARE);
    }
}
