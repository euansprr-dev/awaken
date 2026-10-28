package dev.awaken.living.hunt;

import dev.awaken.Awaken;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.NowHooks;
import dev.awaken.core.api.TipHooks;
import dev.awaken.living.LivingContent;
import dev.awaken.living.LivingNet;
import dev.awaken.living.craft.Craft;
import dev.awaken.living.craft.Crafts;
import dev.awaken.living.population.Herds;
import dev.awaken.living.population.Ledger;
import dev.awaken.living.population.Spoor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Reading signs and following a trail (SPEC §41.3). Crouch over a sign for a second to read it: what passed, how many, which
 * way and how long ago (more exact the better you've studied the kind). Reading signs of one population builds its trail:
 * after two, the band's amber scout motes drift toward the next sign; after a few more (five, fewer the better you know the
 * kind), straight to the quarry, and it goes on the Now line. The trail fades after five in-game hours without a read. Every
 * sign read is research for good, and beds, dens and kill sites go on the Atlas.
 */
public final class Tracking {
    private Tracking() {}

    /** Ticks crouched over a sign to read it (half once a kind is well studied), how near, how far the sync and the motes look. */
    static final int READ_TICKS = 20, READ_FAST = 10, SYNC = 20, SEND_MAX = 64, FADE = 6000, NEXT_AT = 2;
    static final double READ_REACH = 2.2, SEE = 48, NEXT_REACH = 64;
    /** Hunting craft XP for a sign read the first time. */
    static final double READ_XP = 3;
    public static final Identifier TIP = Awaken.id("living/read_signs");

    /** One hunter's trail and what they're crouched over. Not saved: the trail itself fades. */
    static final class Hunter {
        final Set<Integer> read = new HashSet<>();
        int pop = -1, reads, stage, target = -1, reading = -1, readingTicks;
        String species = "";
        long lastLaid, lastRead;
        double tx, ty, tz;
        long sentMarks = Long.MIN_VALUE;
        String sentTrail = "";
    }

    private static final Map<UUID, Hunter> HUNTERS = new HashMap<>();
    private static @Nullable Ledger testLedger;

    /** Tests: read populations from this ledger instead of the world's (null: the world's). */
    public static void useLedger(@Nullable Ledger l) {
        testLedger = l;
    }

    static Ledger ledger(MinecraftServer server) {
        return testLedger != null ? testLedger : Ledger.get(server);
    }

    static Hunter hunter(ServerPlayer p) {
        return HUNTERS.computeIfAbsent(p.getUUID(), k -> new Hunter());
    }

    public static void register() {
        NowHooks.register(Tracking::now);
    }

    public static void forget(ServerPlayer p) {
        HUNTERS.remove(p.getUUID());
    }

    public static HuntState state(ServerPlayer p) {
        return p.getData(LivingContent.HUNT);
    }

    static boolean tracks(ServerPlayer p) {
        return Crafts.has(p, Craft.HUNTING, "tracks");
    }

    /** Whether this player sees this sign now (prints last longer with the Tracks perk). */
    static boolean sees(Spoor.Mark m, long now, boolean tracks) {
        return m.age(now) >= 0 && m.age(now) <= Spoor.life(m.kind(), tracks);
    }

    // ---- the tick ---------------------------------------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        int t = server.getTickCount();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            if (t % 5 == 0) crouch(level, p);
            if ((t + p.getId()) % SYNC == 0) update(level, p);
        }
    }

    /** Every 5 ticks: crouched on the ground over a sign you can see, you read it once you've stayed long enough. */
    public static void crouch(ServerLevel level, ServerPlayer p) {
        Hunter h = hunter(p);
        long now = level.getGameTime();
        boolean tracks = tracks(p);
        Spoor.Mark best = null;
        double bd = Double.MAX_VALUE;
        for (Spoor.Mark m : Spoor.of(level).near(p.getX(), p.getZ(), 4)) {
            if (!sees(m, now, tracks) || h.read.contains(m.id()) || Math.abs(m.y() - p.getY()) > 2) continue;
            double d = Math.hypot(m.x() - p.getX(), m.z() - p.getZ());
            if (d < bd) {
                bd = d;
                best = m;
            }
        }
        if (best != null && state(p).found().isEmpty() && state(p).research().isEmpty()) TipHooks.offer(p, TIP);
        if (best == null || bd > READ_REACH || !p.isShiftKeyDown() || !p.onGround()) {
            h.reading = -1;
            h.readingTicks = 0;
            return;
        }
        if (h.reading != best.id()) {
            h.reading = best.id();
            h.readingTicks = 0;
        }
        h.readingTicks += 5;
        int need = state(p).level(best.species()) >= 2 ? READ_FAST : READ_TICKS;
        if (h.readingTicks >= need) {
            h.reading = -1;
            h.readingTicks = 0;
            read(level, p, best);
        }
    }

    /** Reads a sign: the line, research, the trail, the Atlas. */
    public static void read(ServerLevel level, ServerPlayer p, Spoor.Mark m) {
        Hunter h = hunter(p);
        if (!h.read.add(m.id())) return;
        long now = level.getGameTime();
        HuntState before = state(p);
        int studied = before.level(m.species());
        HuntState s = before.study(m.species(), 1);
        long day = PlayDays.current(level.getServer());
        if (m.kind() == Spoor.Kind.DUNG) s = s.find(new HuntState.Found(HuntState.Found.BED, (int) m.x(), (int) m.z(), m.species(), day));
        if (m.kind() == Spoor.Kind.KILL) s = s.find(new HuntState.Found(HuntState.Found.KILL, (int) m.x(), (int) m.z(), m.species(), day));
        if (h.pop != m.pop()) {
            h.pop = m.pop();
            h.species = m.species();
            h.reads = 0;
            h.stage = 0;
            h.lastLaid = Long.MIN_VALUE;
        }
        h.reads++;
        h.lastLaid = Math.max(h.lastLaid, m.laid());
        h.lastRead = now;
        int was = h.stage;
        h.stage = h.reads >= need(s.level(m.species())) ? 3 : h.reads >= NEXT_AT ? 2 : 1;
        if (h.stage == 3 && was < 3) s = foundHome(level, s, m, day);
        p.setData(LivingContent.HUNT, s);
        TipHooks.done(p, TIP);
        Crafts.gain(p, Craft.HUNTING, READ_XP, 0);
        level.playSound(null, m.x(), m.y(), m.z(), SoundEvents.BRUSH_GENERIC, SoundSource.PLAYERS, 0.5F, 0.8F);
        String line = describe(m, studied, now);
        if (h.stage == 2 && was < 2) line += " Your band has the scent now: follow the amber motes to the next sign.";
        else if (h.stage == 3 && was < 3) line += " Your band knows where they are now: the motes lead straight to them.";
        if (s.level(m.species()) > studied) line += " You know " + Herds.name(m.species(), true) + " better now (" + studyName(s.level(m.species())) + ").";
        p.sendSystemMessage(Component.literal(line));
        update(level, p);
    }

    /** Reads it takes to find the quarry: five, one fewer for each research level, never under three. */
    static int need(int studied) {
        return Math.max(3, 5 - studied);
    }

    static String studyName(int lv) {
        return switch (lv) {
            case 1 -> "you know their signs";
            case 2 -> "you know their ways";
            case 3 -> "you know them well";
            default -> "new to you";
        };
    }

    /** The trail found its quarry: its bed (tonight's) or its den goes on the Atlas. */
    private static HuntState foundHome(ServerLevel level, HuntState s, Spoor.Mark m, long day) {
        Ledger l = ledger(level.getServer());
        Ledger.Pop pop = l.pop(m.pop());
        if (pop == null) return s;
        if (pop.kind.equals("pack")) return s.find(new HuntState.Found(HuntState.Found.DEN, pop.hx, pop.hz, pop.species, day));
        double[] bed = l.where(pop, day, 18000);
        return s.find(new HuntState.Found(HuntState.Found.BED, (int) bed[0], (int) bed[1], pop.species, day));
    }

    // ---- what a sign says -------------------------------------------------------------------------------------------

    /** A sign in plain words; more exact the better the reader knows the kind (studied 0..3). */
    public static String describe(Spoor.Mark m, int studied, long now) {
        String many = Herds.name(m.species(), true), one = Herds.name(m.species(), false);
        String when = when(m.age(now));
        return switch (m.kind()) {
            case PRINTS -> {
                String what = studied == 0 ? (m.hoofed() ? "Hoof prints" : "Paw prints") : cap(one) + " tracks";
                yield what + ": " + count(m.count(), studied) + " went " + compass(m.yaw()) + ", " + when + ".";
            }
            case DUNG -> "Droppings and flattened grass: " + (studied == 0 ? "a herd" : count(m.count(), studied) + " " + many)
                    + " bedded down here, " + when + ".";
            case KILL -> "Bones and blood: " + (studied == 0 ? "a pack" : many) + " killed " + (m.prey().isEmpty() ? "something"
                    : "a " + Herds.name(m.prey(), false)) + " here, " + when + ".";
        };
    }

    static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** How many, as a tracker would say it: vague until the kind is studied. */
    static String count(int n, int studied) {
        if (n <= 1) return "one";
        if (studied == 0) return n <= 3 ? "a couple of them" : n <= 8 ? "several" : "a lot of them";
        if (n <= 5) return String.valueOf(n);
        return "about " + (Math.round(n / 2.0) * 2);
    }

    static String when(long age) {
        if (age < 300) return "only minutes ago";
        if (age < 1200) return "within the last hour";
        if (age < 2600) return "an hour or two ago";
        if (age < 7000) return "a few hours ago";
        return "most of a day ago";
    }

    /** The way prints point (Minecraft yaw) as a compass word. */
    static String compass(float yaw) {
        double dx = -Math.sin(Math.toRadians(yaw)), dz = Math.cos(Math.toRadians(yaw));
        return compass(dx, dz);
    }

    static String compass(double dx, double dz) {
        String[] names = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        double a = Math.toDegrees(Math.atan2(dx, -dz));
        return names[Math.floorMod((int) Math.round(a / 45), 8)];
    }

    // ---- the trail and the sync -------------------------------------------------------------------------------------

    /** Every second: the trail fades or finds its target, and the signs in sight and the trail go to the player. */
    public static void update(ServerLevel level, ServerPlayer p) {
        Hunter h = hunter(p);
        long now = level.getGameTime();
        boolean tracks = tracks(p);
        Spoor spoor = Spoor.of(level);
        h.read.removeIf(id -> spoor.find(id) == null);
        if (h.pop >= 0 && now - h.lastRead > FADE) h.pop = -1;
        double[] q = null;
        if (h.pop >= 0 && h.stage == 3) {
            q = ledger(level.getServer()).quarry(level, h.pop);
            if (q == null) h.pop = -1; // gone: hunted out
        }
        if (h.pop < 0) {
            h.stage = 0;
            h.reads = 0;
            h.target = -1;
        } else if (h.stage == 2) {
            Spoor.Mark next = next(spoor, h, p, now, tracks);
            h.target = next == null ? -1 : next.id();
            if (next != null) {
                h.tx = next.x();
                h.ty = next.y();
                h.tz = next.z();
            }
        } else if (h.stage == 3 && q != null) {
            h.target = -1;
            h.tx = q[0];
            h.ty = Double.isNaN(q[1]) ? p.getY() : q[1];
            h.tz = q[2];
        }
        sync(level, p, h, now, tracks);
    }

    /** The next sign on the trail: the first laid after the last one read, else the newest not read; in reach. */
    static Spoor.@Nullable Mark next(Spoor spoor, Hunter h, ServerPlayer p, long now, boolean tracks) {
        Spoor.Mark after = null, newest = null;
        for (Spoor.Mark m : spoor.near(p.getX(), p.getZ(), NEXT_REACH)) {
            if (m.pop() != h.pop || h.read.contains(m.id()) || !sees(m, now, tracks)) continue;
            if (m.laid() > h.lastLaid && (after == null || m.laid() < after.laid())) after = m;
            if (newest == null || m.laid() > newest.laid()) newest = m;
        }
        return after != null ? after : newest;
    }

    private static void sync(ServerLevel level, ServerPlayer p, Hunter h, long now, boolean tracks) {
        List<Spoor.Mark> near = new ArrayList<>();
        for (Spoor.Mark m : Spoor.of(level).near(p.getX(), p.getZ(), SEE)) if (sees(m, now, tracks)) near.add(m);
        near.sort(Comparator.comparingDouble(m -> (m.x() - p.getX()) * (m.x() - p.getX()) + (m.z() - p.getZ()) * (m.z() - p.getZ())));
        if (near.size() > SEND_MAX) near = near.subList(0, SEND_MAX);
        long hash = tracks ? 1 : 0;
        for (Spoor.Mark m : near) hash = hash * 31 + m.id() * 2L + (h.read.contains(m.id()) ? 1 : 0);
        if (hash != h.sentMarks) {
            h.sentMarks = hash;
            List<LivingNet.SpoorMark> out = new ArrayList<>(near.size());
            for (Spoor.Mark m : near) out.add(new LivingNet.SpoorMark(m.id(), m.kind().ordinal(), m.x(), m.y(), m.z(), m.yaw(), m.count(),
                    (int) Math.min(Integer.MAX_VALUE, m.age(now)), m.hoofed(), h.read.contains(m.id())));
            dev.awaken.core.AwakenNet.send(p, new LivingNet.Spoor(out));
        }
        String quarry = h.pop < 0 ? "" : Herds.name(h.species, true);
        LivingNet.Trail trail = new LivingNet.Trail(h.stage, h.target, h.tx, h.ty, h.tz, quarry);
        String key = h.stage + "|" + h.target + "|" + Math.round(h.tx) + "|" + Math.round(h.ty) + "|" + Math.round(h.tz) + "|" + quarry;
        if (!key.equals(h.sentTrail)) {
            h.sentTrail = key;
            dev.awaken.core.AwakenNet.send(p, trail);
        }
    }

    // ---- the Now line -------------------------------------------------------------------------------------------------

    static List<NowHooks.Suggestion> now(ServerPlayer p) {
        Hunter h = HUNTERS.get(p.getUUID());
        if (h == null || h.pop < 0 || p.level().dimension() != Level.OVERWORLD) return List.of();
        String many = Herds.name(h.species, true);
        return switch (h.stage) {
            case 1 -> List.of(NowHooks.Suggestion.of("hunt_trail", 30, "Look for more signs of the " + many + " nearby."));
            case 2 -> h.target < 0 ? List.of(NowHooks.Suggestion.of("hunt_trail", 35, "The trail of the " + many + " is cold here: look further on."))
                    : List.of(NowHooks.Suggestion.at("hunt_trail", 45, "Follow the motes to the next sign of the " + many + ".", BlockPos.containing(h.tx, h.ty, h.tz)));
            case 3 -> {
                double dx = h.tx - p.getX(), dz = h.tz - p.getZ();
                int d = (int) Math.round(Math.hypot(dx, dz) / 10) * 10;
                String where = d < 20 ? "right here" : "about " + d + " blocks " + compass(dx, dz);
                yield List.of(new NowHooks.Suggestion("hunt_trail", 50, Component.literal("The " + many + " are " + where + ". Come at them from downwind."),
                        Optional.of(BlockPos.containing(h.tx, h.ty, h.tz))));
            }
            default -> List.of();
        };
    }

    /** Tests: the trail's stage (0 none, 1 reading, 2 to the next sign, 3 to the quarry), its target sign and where the motes go. */
    public static int stage(ServerPlayer p) {
        return hunter(p).stage;
    }

    public static int target(ServerPlayer p) {
        return hunter(p).target;
    }

    public static double[] motes(ServerPlayer p) {
        Hunter h = hunter(p);
        return new double[]{h.tx, h.ty, h.tz};
    }

    /** Dev and tests: the trail's stage and target for a player. */
    public static String describe(ServerPlayer p) {
        Hunter h = hunter(p);
        return h.pop < 0 ? "no trail" : "trail of " + Herds.name(h.species, true) + " (population " + h.pop + "): " + h.reads + " read, stage " + h.stage
                + (h.stage >= 2 ? " → " + Math.round(h.tx) + " " + Math.round(h.ty) + " " + Math.round(h.tz) : "");
    }
}
