package dev.awaken.gates.run;

import dev.awaken.core.api.MobRole;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.world.FloorLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Packs in a Realm (SPEC §50.9 "The Run", stage 0). A den's monsters and a Warden's company stand where you can see them
 * and wait, as a room's do in the old gates ({@link Encounters}): they notice you when you come close in their sight or
 * hurt one, turn toward you for the wake beat, then fight. A gate nobody makes headway in sends waves after the player,
 * and at last a hunter of the theme's Warden kind. A Realm has no rooms, so a pack is simply the monsters that carry its
 * name; nothing of its own is saved. Server thread only.
 *
 * <p>Out in an open land (SPEC §50.9 "Who lives there", {@code RealmCountry}) a pack may walk a beat instead of standing,
 * a lookout on a perch sees further than anyone and calls its own to it, and the theme's Warden kind may be found at home.
 */
public final class RealmPacks {
    private RealmPacks() {}

    private static final String PACK = "awaken_realm_pack", FREE = "awaken_realm_free", LOOKOUT = "awaken_realm_lookout";
    /** A lookout sees this far with a clear line, whatever the height between; the horn brings its own from this far round it. */
    private static final double LOOK = 24, CALL = 38;
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

    /** Somewhere a monster can stand: two clear cells over something with a body, or wading one deep with its head in the air. Never looks into land that isn't there yet. */
    private static boolean standable(ServerLevel level, BlockPos p) {
        if (!dev.awaken.core.api.Ready.loaded(level, p)) return false;
        BlockPos under = p.below();
        if (level.getBlockState(under).getCollisionShape(level, under).isEmpty() || !clear(level, p.above())) return false;
        var feet = level.getBlockState(p);
        return feet.getCollisionShape(level, p).isEmpty() && (feet.getFluidState().isEmpty() || feet.is(net.minecraft.world.level.block.Blocks.WATER));
    }

    private static BlockPos settle(ServerLevel level, BlockPos p) {
        for (int dy : DROP) {
            BlockPos q = p.offset(0, dy, 0);
            if (standable(level, q)) return q;
        }
        return null;
    }

    /**
     * A pack as a sheet writes it ("id" or "id*n"), one name a monster, with one more of the first for every two ranks.
     * Empty when the pack rolls from the theme. A name keeps its mark: "~" for a creature that keeps its own habits (a
     * lurker hangs from the roof, a hushling listens in the dark) instead of standing and waiting, "!" for a Warden named
     * outright.
     */
    static List<String> roll(List<String> pack, int rank) {
        List<String> out = new java.util.ArrayList<>();
        String more = null;
        for (String e : pack) {
            int star = e.indexOf('*');
            String id = star < 0 ? e : e.substring(0, star);
            if (more == null && !id.startsWith("!")) more = id;
            for (int i = star < 0 ? 1 : Integer.parseInt(e.substring(star + 1)); i > 0; i--) out.add(id);
        }
        if (more != null) for (int i = 0; i < rank / 2; i++) out.add(more);
        return out;
    }

    /** One monster of a pack, standing at its spot and waiting, or walking the pack's beat. Null when the gate is full or nothing can stand there. */
    private static LivingEntity stand(ServerLevel level, GateRecord g, FloorLayout f, MobRole.Role role, BlockPos pos, String pack, long @Nullable [] beat) {
        LivingEntity e = Rooms.spawn(level, g, f, -1, role, pos);
        if (e == null) return null;
        e.getPersistentData().putString(PACK, pack);
        hold(e, beat);
        return e;
    }

    private static void hold(LivingEntity e, long @Nullable [] beat) {
        if (beat == null) Encounters.holdFree(e);
        else Encounters.holdBeat(e, beat);
    }

    /** One monster a sheet named. A name the mobs module doesn't know gets one of the theme's own instead, so a typo never empties a room. */
    private static LivingEntity named(ServerLevel level, GateRecord g, FloorLayout f, String name, BlockPos pos, String pack, ServerPlayer wakeFor, long @Nullable [] beat) {
        boolean free = name.startsWith("~");
        LivingEntity e = Rooms.named(level, g, f, -1, free || name.startsWith("!") ? name.substring(1) : name, pos);
        if (e == null) {
            e = Rooms.spawn(level, g, f, -1, MobRole.Role.NORMAL, pos);
            free = false;
        }
        if (e == null) return null;
        e.getPersistentData().putString(PACK, pack);
        if (free) e.getPersistentData().putBoolean(FREE, true); // its own habits are how it waits
        else if (wakeFor == null) hold(e, beat);
        else if (e instanceof Mob m) m.setTarget(wakeFor);
        return e;
    }

    /**
     * A den's pack. Rolled from the theme: four or five at E rank and more above, in a loose ring, the elite (from five up)
     * in the middle. Named by the sheet: exactly those. With {@code wakeFor} it's an ambush: they come up awake, a few
     * steps further out, already after that player.
     */
    public static int den(ServerLevel level, GateRecord g, FloorLayout f, BlockPos center, int site, List<String> pack, ServerPlayer wakeFor) {
        return den(level, g, f, center, "d" + site, pack, wakeFor, null);
    }

    /**
     * A pack on the prowl in an open land (SPEC §50.9 "Who lives there"): a den's worth that walks a beat between two or
     * three places, a while at each, and can be watched, waited out or met. It notices and fights as a den's does.
     */
    public static int prowl(ServerLevel level, GateRecord g, FloorLayout f, BlockPos center, int site, List<String> pack, long[] beat) {
        return den(level, g, f, center, "c" + site, pack, null, beat);
    }

    private static int den(ServerLevel level, GateRecord g, FloorLayout f, BlockPos center, String name, List<String> pack, ServerPlayer wakeFor, long @Nullable [] beat) {
        var rnd = level.getRandom();
        List<String> names = roll(pack, g.plan.rank().ordinal());
        int n = names.isEmpty() ? 4 + g.plan.rank().ordinal() / 2 + rnd.nextInt(2) : names.size(), made = 0;
        double turn = rnd.nextDouble() * Math.PI * 2;
        for (int i = 0; i < n; i++) {
            double a = turn + i * (Math.PI * 2 / Math.max(1, n - 1)), r = wakeFor != null ? 4.5 + rnd.nextDouble() * 3 : i == 0 ? 0 : 3.5 + rnd.nextDouble() * 3;
            BlockPos at = settle(level, center.offset((int) Math.round(Math.cos(a) * r), 0, (int) Math.round(Math.sin(a) * r)));
            if (at == null) at = settle(level, center);
            if (at == null) continue;
            LivingEntity e;
            if (!names.isEmpty()) e = named(level, g, f, names.get(i), at, name, wakeFor, beat);
            else {
                e = wakeFor == null ? stand(level, g, f, i == 0 && n >= 5 ? MobRole.Role.ELITE : MobRole.Role.NORMAL, at, name, beat) : Rooms.spawn(level, g, f, -1, MobRole.Role.NORMAL, at);
                if (e != null && wakeFor != null) {
                    e.getPersistentData().putString(PACK, name);
                    if (e instanceof Mob m) m.setTarget(wakeFor);
                }
            }
            if (e != null) made++;
        }
        return made;
    }

    /**
     * A fight in motion (SPEC §72.5): {@code count} of the theme's own about a landing, a perch or the start of a chase,
     * as pack "h" + index. A hold waits and wakes as a den's pack does, and is tied to within {@code radius} of its place;
     * a perch's are the theme's marksman where it has one ({@code marksman}, else one of the theme's). With {@code chasing}
     * they come up awake behind whoever passed, after them. Returns how many came.
     */
    public static int hold(ServerLevel level, GateRecord g, FloorLayout f, BlockPos center, int index, int count, int radius, String marksman, @Nullable ServerPlayer chasing) {
        String pack = "h" + index;
        var rnd = level.getRandom();
        int made = 0;
        // a party meets a few more, as a room's company grows (SPEC §70): two players make three into four
        count = Rooms.partyCompany(count, 0, Rooms.fightersOnFloor(level, g, f))[0];
        for (int i = 0; i < count; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = i == 0 ? 0 : 1 + rnd.nextDouble() * Math.max(0, radius - 1);
            BlockPos at = settle(level, center.offset((int) Math.round(Math.cos(a) * r), 0, (int) Math.round(Math.sin(a) * r)));
            if (at == null) at = settle(level, center);
            if (at == null) continue;
            LivingEntity e = marksman.isEmpty() ? null : named(level, g, f, marksman, at, pack, chasing, null);
            if (e == null && chasing == null) e = stand(level, g, f, MobRole.Role.NORMAL, at, pack, null);
            else if (e == null) {
                e = Rooms.spawn(level, g, f, -1, MobRole.Role.NORMAL, at);
                if (e != null) {
                    e.getPersistentData().putString(PACK, pack);
                    if (e instanceof Mob m) m.setTarget(chasing);
                }
            }
            if (e == null) continue;
            // tied to its place: it fights there and goes back to it, never following anyone along the line
            if (chasing == null && e instanceof Mob m) m.setHomeTo(center, radius + 2);
            made++;
        }
        return made;
    }

    /** The living members of a pack near its place (only asked while someone is near, so they are in the world). */
    public static List<Mob> members(ServerLevel level, BlockPos center, String pack) {
        return level.getEntitiesOfClass(Mob.class, new AABB(center).inflate(48, 28, 48), m -> m.isAlive() && pack.equals(packOf(m)));
    }

    /** A Warden and the company that stands with it: the sheet's, or three of the theme's and more with rank. The Warden comes even where its company finds no footing. */
    public static LivingEntity warden(ServerLevel level, GateRecord g, FloorLayout f, BlockPos center, int site, List<String> company) {
        BlockPos at = settle(level, center);
        List<String> names = roll(company, g.plan.rank().ordinal());
        LivingEntity w = null;
        if (!names.isEmpty() && names.getFirst().startsWith("!")) {
            // the sheet names the Warden itself: a creature with a body and a fight of its own
            w = named(level, g, f, names.removeFirst(), at == null ? center : at, "w" + site, null, null);
            if (w != null && !Encounters.waiting(w)) Encounters.holdFree(w);
        }
        if (w == null) w = stand(level, g, f, MobRole.Role.WARDEN, at == null ? center : at, "w" + site, null);
        if (w == null) return null;
        var rnd = level.getRandom();
        int n = names.isEmpty() ? 3 + g.plan.rank().ordinal() / 2 : names.size();
        double turn = rnd.nextDouble() * Math.PI * 2;
        for (int i = 0; i < n; i++) {
            double a = turn + i * (Math.PI * 2 / n), r = 4.5 + rnd.nextDouble() * 2.5;
            BlockPos spot = settle(level, center.offset((int) Math.round(Math.cos(a) * r), 0, (int) Math.round(Math.sin(a) * r)));
            if (spot == null) continue;
            if (names.isEmpty()) stand(level, g, f, MobRole.Role.NORMAL, spot, "w" + site, null);
            else named(level, g, f, names.get(i), spot, "w" + site, null, null);
        }
        return w;
    }

    /** How many of a gate's own monsters are alive within reach of a place. */
    public static int near(ServerLevel level, BlockPos center, double reach) {
        return level.getEntitiesOfClass(Mob.class, new AABB(center).inflate(reach, 24, reach), m -> m.isAlive() && !packOf(m).isEmpty()).size();
    }

    /**
     * A lookout on its perch with its reserve out of sight behind it (SPEC §50.9 "Who lives there"): one of the theme's,
     * its marksman where the theme has one, and three or four more standing at {@code reserve}. Seen by the lookout, you
     * hear a horn and the reserve comes; put the lookout down unseen and the reserve never knows. Returns how many came.
     */
    public static int lookout(ServerLevel level, GateRecord g, FloorLayout f, BlockPos perch, BlockPos reserve, int site, String creature) {
        BlockPos at = settle(level, perch);
        if (at == null) return 0;
        String pack = "c" + site;
        LivingEntity e = creature.isEmpty() ? stand(level, g, f, MobRole.Role.NORMAL, at, pack, null) : named(level, g, f, creature, at, pack, null, null);
        if (e == null) return 0;
        e.getPersistentData().putBoolean(LOOKOUT, true);
        var rnd = level.getRandom();
        int n = 3 + g.plan.rank().ordinal() / 2, made = 1;
        double turn = rnd.nextDouble() * Math.PI * 2;
        for (int i = 0; i < n; i++) {
            double a = turn + i * (Math.PI * 2 / n), r = i == 0 ? 0 : 2.5 + rnd.nextDouble() * 2.5;
            BlockPos spot = settle(level, reserve.offset((int) Math.round(Math.cos(a) * r), 0, (int) Math.round(Math.sin(a) * r)));
            if (spot != null && stand(level, g, f, MobRole.Role.NORMAL, spot, pack, null) != null) made++;
        }
        return made;
    }

    /**
     * The big one at home (SPEC §50.9 "Who lives there"): the theme's Warden kind where it lives, off the way, with two
     * of its own (more with rank) about it. Nobody has to go near it. Returns how many came.
     */
    public static int lair(ServerLevel level, GateRecord g, FloorLayout f, BlockPos center, int site) {
        BlockPos at = settle(level, center);
        if (at == null || stand(level, g, f, MobRole.Role.WARDEN, at, "c" + site, null) == null) return 0;
        var rnd = level.getRandom();
        int n = 2 + g.plan.rank().ordinal() / 3, made = 1;
        double turn = rnd.nextDouble() * Math.PI * 2;
        for (int i = 0; i < n; i++) {
            double a = turn + i * (Math.PI * 2 / n), r = 4.5 + rnd.nextDouble() * 2.5;
            BlockPos spot = settle(level, center.offset((int) Math.round(Math.cos(a) * r), 0, (int) Math.round(Math.sin(a) * r)));
            if (spot != null && stand(level, g, f, MobRole.Role.NORMAL, spot, "c" + site, null) != null) made++;
        }
        return made;
    }

    /** Whether any of a pack is still alive near its place. Only asked while someone is near, so the pack is in the world to be found. */
    public static boolean alive(ServerLevel level, BlockPos center, String pack) {
        return !level.getEntitiesOfClass(Mob.class, new AABB(center).inflate(56, 28, 56), m -> m.isAlive() && pack.equals(packOf(m))).isEmpty();
    }

    /** Wakes every pack someone has come close to, been seen by or hurt. Returns how many packs woke. */
    public static int notice(ServerLevel level, ServerPlayer p) {
        if (p.isCreative() || p.isSpectator()) return 0;
        double reach = NOTICE + COMPANY;
        List<Mob> near = level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(reach, 20, reach), m -> Encounters.waiting(m) && !packOf(m).isEmpty());
        if (near.isEmpty()) return 0;
        Set<String> woke = null;
        Mob caller = null;
        for (Mob m : near) {
            double d2 = m.distanceToSqr(p);
            boolean lookout = m.getPersistentData().getBooleanOr(LOOKOUT, false);
            // a lookout is on its perch to see: further than the rest, and up or down a slope
            boolean inSight = lookout ? d2 <= LOOK * LOOK : d2 <= NOTICE * NOTICE && Math.abs(m.getY() - p.getY()) <= NOTICE_UP;
            boolean seen = d2 <= CLOSE * CLOSE || inSight && m.hasLineOfSight(p);
            if (!seen && m.hurtTime <= 0 && m.getHealth() >= m.getMaxHealth()) continue;
            if (woke == null) woke = new HashSet<>();
            woke.add(packOf(m));
            if (lookout && seen) caller = m;
        }
        if (woke == null) return 0;
        for (Mob m : near) if (woke.contains(packOf(m))) Encounters.releaseFree(m);
        if (caller != null) call(level, caller, p);
        return woke.size();
    }

    /** A lookout that has seen someone sounds its horn: every one of its own within earshot wakes and comes for whoever it saw. */
    private static void call(ServerLevel level, Mob lookout, ServerPlayer p) {
        String pack = packOf(lookout);
        for (Mob m : level.getEntitiesOfClass(Mob.class, lookout.getBoundingBox().inflate(CALL, 24, CALL), m -> m != lookout && m.isAlive() && pack.equals(packOf(m)))) {
            Encounters.releaseFree(m);
            m.setLastHurtByMob(p); // who it turns to when its wake beat ends, however far off they are
        }
        level.playSound(null, lookout.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4F, 1.2F);
    }

    /** Whether a pack is awake and still has someone standing, anywhere near its place. */
    public static boolean fighting(ServerLevel level, BlockPos center, String pack) {
        return !level.getEntitiesOfClass(Mob.class, new AABB(center).inflate(40, 20, 40),
                m -> m.isAlive() && pack.equals(packOf(m)) && !Encounters.waiting(m) && (!m.getPersistentData().getBooleanOr(FREE, false) || m.getTarget() != null)).isEmpty();
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
