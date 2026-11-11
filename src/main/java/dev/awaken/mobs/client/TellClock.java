package dev.awaken.mobs.client;

import dev.awaken.core.api.Strikes;
import dev.awaken.mobs.boss.KitMoves;
import dev.awaken.mobs.boss.KitReach;
import dev.awaken.mobs.boss.Kits;
import dev.awaken.mobs.client.kit.ServerPace;
import dev.awaken.mobs.entity.AwakenBoss;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * One clock for every told blow (SPEC §67.2, {@link Strikes.Coming}): how long until it reaches you, as a number that
 * glides and ends on the tick the blow lands.
 * <p>
 * The tell a body shows is a count the server sends again only every second or fourth tick and clears as its wind-up
 * ends, a tick or more before the blow lands; drawn as it comes it moved in jumps and vanished early (Euan 2026-11-11:
 * "jittery", "you're not exactly sure when the ring is going to close"). Here a blow's time is fixed once, when it is
 * first seen, and counted on one clock from then:
 * <ul>
 * <li>a kit body's move from its own synced start, its move table and the server's pace ({@link ServerPace}), the clock
 * its body animates on, to the first active tick whose capsules reach where you stand ({@link KitReach#firstTouch}, the
 * server's own rule); if none will, to the first tick that strikes at all, and the blow is said to pass you by;</li>
 * <li>any other body's from the count it first showed, on the game's clock.</li>
 * </ul>
 * A later count is never read for time: a tell that ends well before its blow was called off (the slot is let go), and a
 * count well over what is left is a new blow. Fixed slots, nothing allocated a tick.
 */
public final class TellClock implements Strikes.Coming {
    public static final TellClock INSTANCE = new TellClock();

    private TellClock() {}

    private static final int SLOTS = 48;
    private static final double RANGE = 40;
    /** A blow's slot is kept this long after it lands (ticks), so what is drawn about it can end on the landing. */
    private static final double KEPT = 6;
    /** A count this far over what is left is a new blow; a tell gone with more than this left was called off. */
    private static final double NEW_BLOW = 2.5, CALLED_OFF = 1.5;
    /** How far the landing tick may move a tick while you move about a sweep, and the time left at which it is fixed. */
    private static final double SLEW = 0.5, FIXED = 5.0;

    private static final int[] ID = new int[SLOTS], KIND = new int[SLOTS], SERIAL = new int[SLOTS];
    /** The game tick each slot counts from, and how many ticks after it the blow lands. */
    private static final long[] FROM = new long[SLOTS];
    private static final double[] AT = new double[SLOTS];
    private static final boolean[] KIT = new boolean[SLOTS], REACH = new boolean[SLOTS], LANDS = new boolean[SLOTS], SEEN = new boolean[SLOTS];

    static {
        java.util.Arrays.fill(ID, Integer.MIN_VALUE);
    }

    // ---- what is asked of it ------------------------------------------------------------------------------------------

    @Override
    public double left(Entity e, float partialTick) {
        int s = find(e.getId());
        if (s < 0 || KIND[s] == Strikes.Kind.NONE.ordinal()) return Double.NaN;
        double left = AT[s] - elapsed(e.level(), s, partialTick);
        return left < -KEPT ? Double.NaN : left;
    }

    @Override
    public boolean reaches(Entity e) {
        int s = find(e.getId());
        return s < 0 || REACH[s];
    }

    @Override
    public Strikes.Kind kind(Entity e) {
        int s = find(e.getId());
        return s < 0 ? Strikes.Kind.NONE : Strikes.Kind.values()[KIND[s]];
    }

    @Override
    public boolean lands(Entity e) {
        int s = find(e.getId());
        return s < 0 || LANDS[s];
    }

    private static double elapsed(Level level, int s, float partialTick) {
        return KIT[s] ? ServerPace.since(level, FROM[s], partialTick) : (level.getGameTime() - FROM[s]) + (double) partialTick;
    }

    // ---- every client tick --------------------------------------------------------------------------------------------

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (mc.level == null || p == null) {
            reset();
            return;
        }
        java.util.Arrays.fill(SEEN, false);
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == p || !(e instanceof LivingEntity) || e.distanceToSqr(p) > RANGE * RANGE) continue;
            if (e instanceof AwakenBoss boss && boss.kitMove() >= 0 && kitMove(mc.level, p, boss)) continue;
            told(mc.level, e);
        }
        // a slot nobody answered for: its body left, or its blow is long over
        for (int s = 0; s < SLOTS; s++) {
            if (ID[s] == Integer.MIN_VALUE || SEEN[s]) continue;
            Entity e = mc.level.getEntity(ID[s]);
            if (e == null || AT[s] - elapsed(mc.level, s, 1F) < -KEPT) ID[s] = Integer.MIN_VALUE;
        }
    }

    /** A kit body in a move: its blow on the body's own clock. False when this move isn't one (no kit, no such move). */
    private static boolean kitMove(Level level, LocalPlayer p, AwakenBoss boss) {
        Kits.Kit kit = boss.kit();
        int mv = boss.kitMove();
        if (kit == null || mv >= kit.moves().moves().size()) return false;
        KitMoves.Move m = kit.moves().get(mv);
        int s = slot(boss.getId());
        if (s < 0) return true;
        SEEN[s] = true;
        KitMoves.Played played = KitMoves.played(m, boss.kitSpeed(), boss.kitHold());
        int wind = played.windup() + played.hold(), first = KitReach.firstCapsule(m);
        boolean began = !KIT[s] || SERIAL[s] != boss.kitSerial();
        if (began) {
            // a new move: counted from the tick the server began it (sent as an int of its game time: the difference is exact)
            KIT[s] = true;
            SERIAL[s] = boss.kitSerial();
            long now = level.getGameTime();
            FROM[s] = now + (boss.kitStart() - (int) now);
            KIND[s] = Strikes.Kind.NONE.ordinal();
            LANDS[s] = first >= 0;
            REACH[s] = true;
            // the strike of active tick k runs wind + 1 + k ticks after the move began (FightRunner.step counts from 1)
            AT[s] = wind + 1 + Math.max(0, first);
        }
        // its class, which the tell carries only while the wind-up lasts
        if (KIND[s] == Strikes.Kind.NONE.ordinal() && boss.telegraphTicks() > 0) KIND[s] = boss.tellKind().ordinal();
        if (first < 0) return true;
        double left = AT[s] - ServerPace.since(level, FROM[s], 1F);
        if (left <= FIXED && !began) return true; // fixed for the ring's last ticks: "now" never moves once it is near
        // which tick of the blow reaches where you stand now, by the rule the server strikes with
        int k = KitReach.firstTouch(m, played.active(), boss.getX(), boss.getY(), boss.getZ(), boss.yBodyRot, boss.kitLean(),
                boss.getBbHeight() * AwakenBoss.KIT_LEAN_HIP, m.motion().advance(), m.motion().rise(), p.getBoundingBox());
        REACH[s] = k >= 0;
        double want = wind + 1 + (k >= 0 ? k : first);
        // as the move begins the landing is simply set; after that it follows you about a sweep at an even pace, never a jump
        AT[s] = began ? want : AT[s] + Math.max(-SLEW, Math.min(SLEW, want - AT[s]));
        return true;
    }

    /** Any other body: its blow from the count it first showed, on the game's clock. */
    private static void told(Level level, Entity e) {
        Strikes.Tell t = Strikes.tell(e);
        int s = t == null ? find(e.getId()) : slot(e.getId());
        if (s < 0) return;
        if (t == null || t.kind() == Strikes.Kind.NONE || t.ticks() <= 0) {
            if (KIT[s]) {
                ID[s] = Integer.MIN_VALUE; // its kit move is over
                return;
            }
            // the tell ended: with time still on the clock it was called off; else its blow is landing, and the slot runs out
            double left = AT[s] - elapsed(level, s, 1F);
            if (KIND[s] == Strikes.Kind.NONE.ordinal() || left > CALLED_OFF || left < -KEPT) ID[s] = Integer.MIN_VALUE;
            else SEEN[s] = true;
            return;
        }
        SEEN[s] = true;
        boolean fresh = KIT[s] || KIND[s] == Strikes.Kind.NONE.ordinal() || t.ticks() > AT[s] - elapsed(level, s, 1F) + NEW_BLOW;
        if (fresh) {
            KIT[s] = false;
            SERIAL[s] = -1;
            FROM[s] = level.getGameTime();
            AT[s] = t.ticks();
            REACH[s] = true;
            LANDS[s] = true;
        }
        KIND[s] = t.kind().ordinal();
    }

    private static int find(int id) {
        for (int i = 0; i < SLOTS; i++) if (ID[i] == id) return i;
        return -1;
    }

    /** The body's slot, or a free one claimed for it; -1 when every slot is taken. */
    private static int slot(int id) {
        int free = -1;
        for (int i = 0; i < SLOTS; i++) {
            if (ID[i] == id) return i;
            if (ID[i] == Integer.MIN_VALUE && free < 0) free = i;
        }
        if (free < 0) return -1;
        ID[free] = id;
        KIND[free] = Strikes.Kind.NONE.ordinal();
        KIT[free] = false;
        SERIAL[free] = -1;
        AT[free] = 0;
        FROM[free] = 0;
        REACH[free] = true;
        LANDS[free] = true;
        return free;
    }

    static void reset() {
        java.util.Arrays.fill(ID, Integer.MIN_VALUE);
    }
}
