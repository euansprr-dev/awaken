package dev.awaken.gates.run;

import dev.awaken.core.api.MobRole;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.world.FloorLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Packs in a Realm (SPEC §50.9 "The Run", stage 0). A den's monsters and a Warden's company stand where you can see them
 * and wait, as a room's do in the old gates ({@link Encounters}): they notice you when you come close in their sight or
 * hurt one, turn toward you for the wake beat, then fight. A gate nobody makes headway in sends waves after the player,
 * and at last a hunter of the theme's Warden kind. A Realm has no rooms, so a pack is simply the monsters that carry its
 * name; nothing of its own is saved. Server thread only.
 */
public final class RealmPacks {
    private RealmPacks() {}

    private static final String PACK = "awaken_realm_pack";
    /** A pack notices you inside NOTICE if it can see you, inside CLOSE whatever stands between; its own stand within COMPANY. */
    private static final double NOTICE = 13, NOTICE_UP = 7, CLOSE = 5, COMPANY = 26;
    /** Pressure: this long with no headway and waves start, one every PERIOD; the hunter comes at HUNTER. */
    public static final int QUIET = 4 * 60 * 20, PERIOD = 45 * 20, HUNTER = 8 * 60 * 20;
    private static final int[] DROP = {0, 1, -1, 2, -2, 3, -3, -4, -5};

    private static String packOf(Entity e) {
        return e.getPersistentData().getStringOr(PACK, "");
    }

    private static boolean clear(ServerLevel level, BlockPos p) {
        var s = level.getBlockState(p);
        return s.getCollisionShape(level, p).isEmpty() && s.getFluidState().isEmpty();
    }

    /** Somewhere a monster can stand: two clear cells over something with a body. Never looks into land that isn't there yet. */
    private static boolean standable(ServerLevel level, BlockPos p) {
        if (!level.isLoaded(p)) return false;
        BlockPos under = p.below();
        return clear(level, p) && clear(level, p.above()) && !level.getBlockState(under).getCollisionShape(level, under).isEmpty();
    }

    private static BlockPos settle(ServerLevel level, BlockPos p) {
        for (int dy : DROP) {
            BlockPos q = p.offset(0, dy, 0);
            if (standable(level, q)) return q;
        }
        return null;
    }

    /** One monster of a pack, standing at its spot and waiting. Null when the gate is full or nothing can stand there. */
    private static LivingEntity stand(ServerLevel level, GateRecord g, FloorLayout f, MobRole.Role role, BlockPos pos, String pack) {
        LivingEntity e = Rooms.spawn(level, g, f, -1, role, pos);
        if (e == null) return null;
        e.getPersistentData().putString(PACK, pack);
        Encounters.holdFree(e);
        return e;
    }

    /** A den's pack: four or five at E rank and more above, in a loose ring, the elite (from five up) in the middle. */
    public static int den(ServerLevel level, GateRecord g, FloorLayout f, BlockPos center, int site) {
        var rnd = level.getRandom();
        int n = 4 + g.plan.rank().ordinal() / 2 + rnd.nextInt(2), made = 0;
        double turn = rnd.nextDouble() * Math.PI * 2;
        for (int i = 0; i < n; i++) {
            double a = turn + i * (Math.PI * 2 / Math.max(1, n - 1)), r = i == 0 ? 0 : 3.5 + rnd.nextDouble() * 3;
            BlockPos at = settle(level, center.offset((int) Math.round(Math.cos(a) * r), 0, (int) Math.round(Math.sin(a) * r)));
            if (at == null) continue;
            if (stand(level, g, f, i == 0 && n >= 5 ? MobRole.Role.ELITE : MobRole.Role.NORMAL, at, "d" + site) != null) made++;
        }
        return made;
    }

    /** A Warden and the company that stands with it. The Warden comes even where its company finds no footing. */
    public static LivingEntity warden(ServerLevel level, GateRecord g, FloorLayout f, BlockPos center, int site) {
        BlockPos at = settle(level, center);
        LivingEntity w = stand(level, g, f, MobRole.Role.WARDEN, at == null ? center : at, "w" + site);
        if (w == null) return null;
        var rnd = level.getRandom();
        int n = 3 + g.plan.rank().ordinal() / 2;
        double turn = rnd.nextDouble() * Math.PI * 2;
        for (int i = 0; i < n; i++) {
            double a = turn + i * (Math.PI * 2 / n), r = 4.5 + rnd.nextDouble() * 2.5;
            BlockPos spot = settle(level, center.offset((int) Math.round(Math.cos(a) * r), 0, (int) Math.round(Math.sin(a) * r)));
            if (spot != null) stand(level, g, f, MobRole.Role.NORMAL, spot, "w" + site);
        }
        return w;
    }

    /** Wakes every pack someone has come close to, been seen by or hurt. Returns how many packs woke. */
    public static int notice(ServerLevel level, ServerPlayer p) {
        if (p.isCreative() || p.isSpectator()) return 0;
        double reach = NOTICE + COMPANY;
        List<Mob> near = level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(reach, 14, reach), m -> Encounters.waiting(m) && !packOf(m).isEmpty());
        if (near.isEmpty()) return 0;
        Set<String> woke = null;
        for (Mob m : near) {
            double d2 = m.distanceToSqr(p);
            boolean seen = d2 <= CLOSE * CLOSE || d2 <= NOTICE * NOTICE && Math.abs(m.getY() - p.getY()) <= NOTICE_UP && m.hasLineOfSight(p);
            if (!seen && m.hurtTime <= 0 && m.getHealth() >= m.getMaxHealth()) continue;
            if (woke == null) woke = new HashSet<>();
            woke.add(packOf(m));
        }
        if (woke == null) return 0;
        for (Mob m : near) if (woke.contains(packOf(m))) Encounters.releaseFree(m);
        return woke.size();
    }

    /** Whether a pack is awake and still has someone standing, anywhere near its place. */
    public static boolean fighting(ServerLevel level, BlockPos center, String pack) {
        return !level.getEntitiesOfClass(Mob.class, new AABB(center).inflate(40, 20, 40), m -> m.isAlive() && pack.equals(packOf(m)) && !Encounters.waiting(m)).isEmpty();
    }

    /** One wave after a player who has made no headway (k counts from 0), or the hunter. Returns how many came. */
    public static int wave(ServerLevel level, GateRecord g, FloorLayout f, ServerPlayer p, int k, boolean hunter) {
        var rnd = level.getRandom();
        int n = hunter ? 1 : 2 + Math.min(4, k), made = 0;
        double turn = rnd.nextDouble() * Math.PI * 2;
        for (int i = 0; i < n * 4 && made < n; i++) {
            double a = turn + i * 2.4, r = 16 + rnd.nextDouble() * 8;
            BlockPos at = settle(level, p.blockPosition().offset((int) Math.round(Math.cos(a) * r), 0, (int) Math.round(Math.sin(a) * r)));
            if (at == null) continue;
            LivingEntity e = Rooms.spawn(level, g, f, -1, hunter ? MobRole.Role.WARDEN : MobRole.Role.NORMAL, at);
            if (e == null) break;
            e.getPersistentData().putString(PACK, "p");
            if (e instanceof Mob m) m.setTarget(p);
            made++;
        }
        return made;
    }
}
