package dev.awaken.village.trouble;

import dev.awaken.Awaken;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.living.camp.Camps;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.signal.Signal;
import dev.awaken.village.VillageService;
import dev.awaken.village.api.Troubles;
import dev.awaken.village.api.Villages;
import dev.awaken.village.plan.VillagePlan;
import dev.awaken.world.fauna.LairRegistry;
import dev.awaken.world.fauna.lair.LairStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Taken (SPEC §28.7, PLAN 6.3): a beast with a lair within 400 of a village (or a Hollows den) takes someone at night.
 * The bell rings the alarm, heard to 256; their parent weeps by the well while the Watch stands at the green's edge with
 * lanterns looking the way it went. Next morning the parent is in the square and asks. A trail runs from the village's
 * edge to the lair (scuffed ground where they were dragged, a strip of their clothes now and then; the Tracks perk lights
 * it). At the lair they're bound in the nest: fight what lives there, cut them free, and bring them out on your back
 * (no sprinting or wall-running) or, set down, walking behind you and up a rope you hang. In the square they run to
 * their parent. The lair is cleared for good, they greet you by name from then on (and can settle at your Hearth: the
 * {@link Troubles} API), and a carved token of yours goes up by the family's door. Missed, they get out on their own
 * days later and the family leaves for Carillon: the house stands empty.
 */
final class Taken implements Plot {
    static final int ALARM = 0, MORNING = 1, LAIR = 2, FREED = 3, HOME = 4;
    /** How far a lair or den may be for a village's people to be taken to it. */
    static final int REACH = 400;
    /** The captive is put in the nest when a player comes this close to it. */
    static final int NEST = 56;
    /** Each use cuts this much of the bonds (holding use repeats every 4 ticks: about a second and a half). */
    static final float CUT = 0.12f;

    // ---- the cause ---------------------------------------------------------------------------------------------------

    /** At night, one night in four, when a beast's lair (its beast at home) or a den lies within 400. */
    static @Nullable String cause(Ctx c, VillageService.Live l) {
        if (c.tod() < 14000 || c.tod() > 20500) return null;
        if (Math.floorMod(mix(l.plan().seed, c.day(), 7), 4L) != 0) return null;
        return nearest(c, l);
    }

    /** The nearest lair or den that could have done it ("lair:<key>" or "den:<camp id>"), or null. */
    static @Nullable String nearest(Ctx c, VillageService.Live l) {
        BlockPos o = new BlockPos(l.plan().ox, l.plan().baseY, l.plan().oz);
        String best = null;
        double bd = REACH;
        for (LairRegistry.Entry e : LairRegistry.get(c.level().getServer()).all()) {
            LairStyle style = lairStyle(e.beast());
            if (style == null || style.placement != LairStyle.Placement.SURFACE) continue; // a lair you can walk to
            if (Troubles.lairSealed(c.level(), e.den()) || HearthHooks.inside(c.level(), e.den())) continue;
            if (e.felledAt() > 0 && c.now() - e.felledAt() < dev.awaken.world.fauna.Lairs.RESPAWN) continue; // its beast fell: quiet
            double d = TroubleDirector.horizontal(o, e.den());
            if (d < bd && d > l.plan().radius() + 24) {
                bd = d;
                best = "lair:" + e.key();
            }
        }
        if (best != null) return best;
        for (Camps.Camp camp : Camps.get(c.level().getServer()).all()) {
            if (!camp.resolved() || camp.cleared(c.day()) || !camp.kind.equals("hollow_den") && !camp.kind.equals("riftborn")) continue;
            double d = TroubleDirector.horizontal(o, camp.pos());
            if (d < bd && d > l.plan().radius() + 24) {
                bd = d;
                best = "den:" + camp.id;
            }
        }
        return best;
    }

    static @Nullable LairStyle lairStyle(String beast) {
        return LairStyle.byBeast(beast);
    }

    static long mix(long a, long b, long salt) {
        long z = a ^ b * 0x9E3779B97F4A7C15L ^ salt * 0xC2B2AE3D27D4EB4FL;
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    // ---- begin: who, where, the alarm ---------------------------------------------------------------------------------------

    @Override
    public boolean begin(Ctx c, Trouble t) {
        VillageService.Live l = c.live();
        if (l == null) return false;
        // the place
        BlockPos den;
        if (t.cause.startsWith("lair:")) {
            LairRegistry.Entry e = LairRegistry.get(c.level().getServer()).get(Long.parseLong(t.cause.substring(5)));
            if (e == null) return false;
            den = e.den();
            LairStyle style = lairStyle(e.beast());
            t.placeName = style != null ? style.title : "the lair";
            MobDef d = Bestiary.get(e.beast());
            t.text = d != null ? d.name : pretty(e.beast());
        } else if (t.cause.startsWith("den:")) {
            Camps.Camp camp = camp(c, t);
            if (camp == null) return false;
            den = camp.pos();
            t.placeName = camp.kind.equals("hollow_den") ? "the Hollows' den" : "the riftborn camp";
            t.text = camp.kind.equals("hollow_den") ? "the Hollows" : "the riftborn";
        } else return false;
        t.place = den.asLong();
        // who: a child with a parent at home, else a young one; who asks: someone of their household
        VillagePlan p = l.plan();
        long seed = mix(p.seed, c.day(), 11);
        int person = -1, kin = -1;
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < p.people.size(); i++) order.add(i);
        java.util.Collections.shuffle(order, new java.util.Random(seed));
        for (int pass = 0; pass < 2 && person < 0; pass++) {
            for (int i : order) {
                VillagePlan.Person v = p.people.get(i);
                boolean fits = pass == 0 ? v.age() == 0 : v.age() == 1 && !v.job().equals("watch");
                if (!fits || l.settlement().isAway(i) || c.st().memory(l.key()).left.contains(i)) continue;
                for (int m : TroubleDirector.household(l, i)) {
                    if (m != i && p.people.get(m).age() >= 1 && !l.settlement().isAway(m)) {
                        kin = m;
                        break;
                    }
                }
                if (kin >= 0) {
                    person = i;
                    break;
                }
            }
        }
        if (person < 0) return false;
        t.person = person;
        t.kin = kin;
        t.stage = ALARM;
        t.since = c.now();
        t.due = c.day() + 3;
        t.put("began", c.now());
        l.settlement().away(person, true);
        trail(c, t, l);
        alarm(c, t, l);
        Villages.addMood(c.level().getServer(), l.key(), -15);
        Villages.history(c.level().getServer(), l.key(), TroubleDirector.fill(c, t, "{person} was taken in the night; the trail ran {dir} toward {place}."));
        return true;
    }

    static String pretty(String id) {
        StringBuilder b = new StringBuilder();
        for (String w : id.split("_")) b.append(b.isEmpty() ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        return b.toString();
    }

    static Camps.@Nullable Camp camp(Ctx c, Trouble t) {
        if (!t.cause.startsWith("den:")) return null;
        int id = Integer.parseInt(t.cause.substring(4));
        for (Camps.Camp camp : Camps.get(c.level().getServer()).all()) if (camp.id == id) return camp;
        return null;
    }

    /** The bell rings the alarm (heard to 256), and the players who hear it are told whose bell it is. */
    static void alarm(Ctx c, Trouble t, VillageService.Live l) {
        Vec3 w = TroubleDirector.well(l);
        l.settlement().alarm(BlockPos.containing(w), 36);
        t.put("rang", c.now());
        for (ServerPlayer p : c.level().players()) {
            if (p.distanceToSqr(w) > 256 * 256 || p.distanceToSqr(w) < (l.plan().radius() + 8) * (l.plan().radius() + 8)) continue;
            Signal.toast(p, Component.literal(l.plan().name + "'s bell is ringing the alarm, " + TroubleText.compass(w.x - p.getX(), w.z - p.getZ())),
                    dev.awaken.core.api.Notices.Tier.AMBIENT, "village_alarm");
        }
    }

    // ---- the trail ----------------------------------------------------------------------------------------------------------

    /** From the village's edge toward the den, a point every seven blocks, wandering a little (seeded); the last six short. */
    static void trail(Ctx c, Trouble t, VillageService.Live l) {
        VillagePlan p = l.plan();
        BlockPos den = BlockPos.of(t.place);
        double dx = den.getX() - p.ox, dz = den.getZ() - p.oz, len = Math.max(1, Math.hypot(dx, dz));
        double ux = dx / len, uz = dz / len;
        double start = p.radius() - 2, end = len - 7;
        java.util.Random r = new java.util.Random(mix(p.seed, t.id, 13));
        int n = 0;
        double side = 0;
        for (double s = start; s < end && n < 90; s += 6 + r.nextDouble() * 2) {
            side = Math.clamp(side + (r.nextDouble() - 0.5) * 2.2, -4, 4);
            double x = p.ox + ux * s - uz * side, z = p.oz + uz * s + ux * side;
            t.put("t" + n, BlockPos.asLong((int) Math.floor(x), 0, (int) Math.floor(z)));
            n++;
        }
        t.put("trail", n);
        t.put("laid_to", 0);
    }

    static int trailLength(Trouble t) {
        return (int) t.get("trail", 0);
    }

    static BlockPos trailPoint(Trouble t, int i) {
        return BlockPos.of(t.get("t" + i, 0));
    }

    /** Lays the trail's marks where their ground is loaded (a few a second; each point once). */
    static void layTrail(Ctx c, Trouble t) {
        int n = trailLength(t);
        long done = t.get("done_mask_lo", 0) , doneHi = t.get("done_mask_hi", 0);
        int laid = 0;
        for (int i = 0; i < n && laid < 6; i++) {
            boolean was = i < 64 ? (done >>> i & 1) != 0 : (doneHi >>> (i - 64) & 1) != 0;
            if (was) continue;
            BlockPos at = trailPoint(t, i);
            if (!c.level().isLoaded(at)) continue;
            BlockPos prev = i > 0 ? trailPoint(t, i - 1) : at;
            mark(c, t, at, prev, i);
            if (i < 64) done |= 1L << i;
            else doneHi |= 1L << (i - 64);
            laid++;
        }
        t.put("done_mask_lo", done);
        t.put("done_mask_hi", doneHi);
    }

    /** One point's marks: two or three scuffs along the way it ran, or a strip of cloth every fifth. */
    private static void mark(Ctx c, Trouble t, BlockPos at, BlockPos prev, int i) {
        ServerLevel level = c.level();
        double dx = at.getX() - prev.getX(), dz = at.getZ() - prev.getZ();
        Direction facing = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
        boolean cloth = i % 5 == 3;
        int scuffs = cloth ? 1 : 2 + (i & 1);
        for (int k = 0; k < scuffs; k++) {
            int x = at.getX() + (facing.getAxis() == Direction.Axis.X ? k : 0), z = at.getZ() + (facing.getAxis() == Direction.Axis.Z ? k : 0);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            BlockState below = level.getBlockState(pos.below());
            if (!below.isFaceSturdy(level, pos.below(), Direction.UP) || !level.getFluidState(pos).isEmpty()) continue;
            BlockState s = (cloth && k == 0 ? TroubleContent.TORN_CLOTH.get() : TroubleContent.DRAG_MARKS.get()).defaultBlockState()
                    .setValue(TrailMark.FACING, facing).setValue(TrailMark.LOOK, cloth && k == 0 ? (int) Math.floorMod(t.id, 4L) : (i + k) & 3);
            TroubleDirector.lay(level, t, pos, s);
        }
    }

    // ---- the tick ----------------------------------------------------------------------------------------------------------

    @Override
    public void tick(Ctx c, Trouble t) {
        VillageService.Live l = c.live();
        if (l == null) return;
        layTrail(c, t);
        switch (t.stage) {
            case ALARM -> night(c, t, l);
            case MORNING -> morning(c, t, l);
            case LAIR, FREED -> lair(c, t, l);
            case HOME -> reunion(c, t, l, this::done);
            default -> {}
        }
        if (t.stage <= MORNING && c.now() % 100 == 13) Runner.tick(c, t, l);
        // the trail for its followers (the Tracks perk lights it on their clients)
        if (c.now() % 100 == 33 && t.stage <= LAIR) {
            for (java.util.UUID h : t.helpers) {
                ServerPlayer hp = c.level().getServer().getPlayerList().getPlayer(h);
                if (hp != null && TroubleDirector.horizontal(hp.blockPosition(), BlockPos.of(t.place)) < REACH + 200) TroubleNet.trail(hp, t, true);
            }
        }
        // anyone near the nest finds them there (a helper or not: they can still bring them home)
        if (t.stage <= MORNING || t.stage == LAIR) nest(c, t, l);
    }

    /** The night: the bell again now and then, the parent weeping at the well, the Watch at the green's edge with lanterns. */
    private void night(Ctx c, Trouble t, VillageService.Live l) {
        boolean dark = c.tod() >= 12800 && c.tod() < 23000;
        if (!dark) {
            t.stage = MORNING;
            t.since = c.now();
            c.st().setDirty();
            return;
        }
        if (c.now() - t.get("rang", 0) > 20 * 100 && t.get("rings", 0) < 3) {
            alarm(c, t, l);
            t.put("rings", t.get("rings", 0) + 1);
        }
        Vec3 w = TroubleDirector.well(l);
        BlockPos den = BlockPos.of(t.place);
        double toward = Math.atan2(den.getZ() - w.z, den.getX() - w.x);
        Vec3 kinAt = TroubleDirector.green(l, toward + Math.PI * 0.5, 1.8);
        TroubleDirector.stage(l, t.kin, kinAt, TroubleDirector.yaw(kinAt, w), "weep", "");
        // the rest of the household by them
        int k = 0;
        for (int m : TroubleDirector.household(l, t.kin)) {
            if (m == t.kin || m == t.person) continue;
            Vec3 at = TroubleDirector.green(l, toward + Math.PI * 0.5 + 0.5 + k * 0.45, 2.2);
            TroubleDirector.stage(l, m, at, TroubleDirector.yaw(at, kinAt), k == 0 ? "embrace" : "arms_crossed", "");
            k++;
        }
        // the Watch and the neighbours out with lanterns at the green's edge, looking the way it went
        Set<Integer> not = new HashSet<>(TroubleDirector.household(l, t.kin));
        not.add(t.person);
        List<Integer> out = TroubleDirector.crowd(l, 5, not, "watch", "hunter", "smith");
        double r = l.plan().greenR + 1.5;
        for (int i = 0; i < out.size(); i++) {
            double a = toward + (i - (out.size() - 1) / 2.0) * 0.32;
            Vec3 at = TroubleDirector.green(l, a, r);
            Vec3 look = new Vec3(at.x + Math.cos(toward) * 10, at.y, at.z + Math.sin(toward) * 10);
            boolean watch = l.settlement().job(out.get(i)).equals("watch");
            TroubleDirector.stage(l, out.get(i), at, TroubleDirector.yaw(at, look), i == out.size() / 2 ? "point_shout" : watch ? "halberd_stand" : "look_around",
                    watch && i != out.size() / 2 ? "halberd" : "lantern");
        }
        if (c.now() % 160 == 13) TroubleDirector.say(c.level(), l, t.kin, TroubleDirector.fill(c, t, TroubleText.line(c.level(), "village.trouble.taken.night", c.now() / 160)), 90);
        if (c.now() % 220 == 113 && !out.isEmpty())
            TroubleDirector.say(c.level(), l, out.get(out.size() / 2), TroubleDirector.fill(c, t, TroubleText.line(c.level(), "village.trouble.taken.watch", c.now() / 220)), 80);
    }

    /** The morning on: the parent in the square, pointing the way it went, a neighbour by them; asks whoever comes. */
    private void morning(Ctx c, Trouble t, VillageService.Live l) {
        Vec3 w = TroubleDirector.well(l);
        BlockPos den = BlockPos.of(t.place);
        double toward = Math.atan2(den.getZ() - w.z, den.getX() - w.x);
        Vec3 kinAt = TroubleDirector.green(l, toward, 2.6);
        Vec3 look = new Vec3(kinAt.x + Math.cos(toward) * 8, kinAt.y + 1, kinAt.z + Math.sin(toward) * 8);
        boolean someone = false;
        for (ServerPlayer p : c.level().players()) someone |= p.distanceToSqr(kinAt) < 14 * 14 && !t.helped(p.getUUID());
        TroubleDirector.stage(l, t.kin, kinAt, someone ? Float.NaN : TroubleDirector.yaw(kinAt, look), someone ? "talk" : (c.now() / 200 % 2 == 0 ? "point_shout" : "weep"), "");
        int k = 0;
        for (int m : TroubleDirector.household(l, t.kin)) {
            if (m == t.kin || m == t.person || k >= 2) continue;
            Vec3 at = TroubleDirector.green(l, toward - 0.7 - k * 0.5, 2.8);
            TroubleDirector.stage(l, m, at, TroubleDirector.yaw(at, kinAt), "arms_crossed", "");
            k++;
        }
        if (someone && c.now() % 140 == 13)
            TroubleDirector.say(c.level(), l, t.kin, TroubleDirector.fill(c, t, TroubleText.line(c.level(), "village.trouble.taken.call", c.now() / 140)), 100);
    }

    @Override
    public boolean ask(Ctx c, Trouble t, ServerPlayer p, int rec) {
        VillageService.Live l = c.live();
        if (l == null || rec != t.kin && rec != Runner.of(t)) return false;
        if (t.stage >= HOME) return false;
        if (t.helped(p.getUUID())) {
            String key = t.stage >= FREED ? "village.trouble.taken.waiting_home" : "village.trouble.taken.again";
            l.settlement().say(p, rec, TroubleDirector.fill(c, t, TroubleText.line(c.level(), key, c.now() / 100)), 100);
            return true;
        }
        String line = TroubleDirector.fill(c, t, TroubleText.line(c.level(), rec == Runner.of(t) ? "village.trouble.runner" : "village.trouble.taken.ask",
                c.now() / 100 + p.getId()));
        l.settlement().say(p, rec, line, 140);
        String person = TroubleText.given(l.settlement().personName(t.person));
        TroubleDirector.card(p, t, "Taken · " + person, TroubleDirector.fill(c, t,
                "{person} was taken in the night by {beast}. The trail runs {dir} from {village} to {place}. Bring {person} home."));
        return true;
    }

    @Override
    public void help(Ctx c, Trouble t, ServerPlayer p) {
        VillageService.Live l = c.live();
        if (l != null) {
            String line = TroubleDirector.fill(c, t, TroubleText.line(c.level(), "village.trouble.taken.thanks", c.now()));
            l.settlement().say(p, t.kin, line, 120);
        }
        Signal.toast(p, Component.literal(TroubleDirector.fill(c, t, "Follow the trail {dir} from {village}")), dev.awaken.core.api.Notices.Tier.IMPORTANT,
                "village_trouble");
        TroubleNet.trail(p, t, true);
    }

    @Override
    public String journal(Ctx c, Trouble t, ServerPlayer p) {
        return switch (t.stage) {
            case ALARM, MORNING -> TroubleDirector.fill(c, t, "Follow the trail {dir} from {village} to {place}, and bring {person} home.");
            case LAIR -> TroubleDirector.fill(c, t, "{person} is bound in {place}. Cut {person} free.");
            case FREED -> TroubleDirector.fill(c, t, "Bring {person} home to {village}'s square.");
            default -> TroubleDirector.fill(c, t, "{person} is home.");
        };
    }

    @Override
    public @Nullable BlockPos goal(Ctx c, Trouble t, ServerPlayer p) {
        VillageService.Live l = c.live();
        if (t.stage == FREED && l != null) return BlockPos.containing(TroubleDirector.well(l));
        if (t.stage == LAIR) return BlockPos.of(t.place);
        // the trail's next point ahead of you: following it is the deed
        int n = trailLength(t);
        if (n == 0) return BlockPos.of(t.place);
        int near = 0;
        double nd = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            BlockPos q = trailPoint(t, i);
            double d = Math.hypot(q.getX() - p.getX(), q.getZ() - p.getZ());
            if (d < nd) {
                nd = d;
                near = i;
            }
        }
        int ahead = Math.min(n - 1, near + (nd < 10 ? 3 : 0));
        if (near >= n - 2) return BlockPos.of(t.place);
        BlockPos q = trailPoint(t, ahead);
        return new BlockPos(q.getX(), (int) p.getY(), q.getZ());
    }

    // ---- the nest ------------------------------------------------------------------------------------------------------------

    /** Someone comes near the lair: the taken one is there, bound in the nest. */
    private void nest(Ctx c, Trouble t, VillageService.Live l) {
        BlockPos den = BlockPos.of(t.place);
        if (rescuee(c, t) != null) return;
        if (t.get("nested", 0) != 0 && t.stage >= LAIR) {
            // their body is in an unloaded chunk; nothing to do until someone comes
            return;
        }
        ServerPlayer near = null;
        for (ServerPlayer p : c.level().players()) {
            if (p.isSpectator()) continue;
            if (TroubleDirector.horizontal(p.blockPosition(), den) < NEST) near = p;
        }
        if (near == null || !c.level().isLoaded(den)) return;
        spawnNest(c, t, l);
    }

    /** Tours and tests: in the nest now (its ground loaded). */
    static void forceNest(Ctx c, Trouble t, VillageService.Live l) {
        if (rescuee(c, t) == null && c.level().isLoaded(BlockPos.of(t.place))) spawnNest(c, t, l);
    }

    /** Tours and tests: cut free now by this player. */
    static void forceCut(Ctx c, Trouble t, ServerPlayer p, Rescuee r) {
        ((Taken) TroubleDirector.PLOTS.get(TroubleKind.TAKEN)).cut(c, t, p, r, true);
    }

    private static void spawnNest(Ctx c, Trouble t, VillageService.Live l) {
        BlockPos den = BlockPos.of(t.place);
        BlockPos spot = nestSpot(c.level(), den, l);
        Rescuee r = TroubleContent.RESCUEE.get().create(c.level(), EntitySpawnReason.EVENT);
        if (r == null) return;
        var look = l.settlement().look(t.person);
        r.become(t.id, t.village, t.person, l.settlement().personName(t.person), look.texture(), look.slim(), l.settlement().age(t.person) == 0);
        r.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, TroubleDirector.yaw(Vec3.atBottomCenterOf(spot), Vec3.atCenterOf(den)) + 180f, 0);
        r.mode(Rescuee.BOUND);
        c.level().addFreshEntity(r);
        t.put("captive_hi", r.getUUID().getMostSignificantBits());
        t.put("captive_lo", r.getUUID().getLeastSignificantBits());
        t.put("nested", 1);
        t.put("nest", spot.asLong());
        // the nest's webbing round them (not on the side the village is)
        Direction open = Direction.getApproximateNearest(l.plan().ox - spot.getX(), 0, l.plan().oz - spot.getZ());
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (d == open) continue;
            TroubleDirector.lay(c.level(), t, spot.relative(d), Blocks.COBWEB.defaultBlockState());
        }
        TroubleDirector.lay(c.level(), t, spot.above(2), Blocks.COBWEB.defaultBlockState());
        if (t.stage < LAIR) {
            t.stage = LAIR;
            t.since = c.now();
        }
        c.st().setDirty();
        Awaken.LOG.info("[trouble] {} is in the nest at {}", l.settlement().personName(t.person), spot.toShortString());
    }

    /** Beside the den on dry, solid ground with room to stand, the side toward the village; else the den itself. */
    static BlockPos nestSpot(ServerLevel level, BlockPos den, VillageService.Live l) {
        double dx = l.plan().ox - den.getX(), dz = l.plan().oz - den.getZ(), len = Math.max(1, Math.hypot(dx, dz));
        for (int r = 4; r <= 8; r++) {
            for (int k = 0; k < 8; k++) {
                double a = Math.atan2(dz / len, dx / len) + (k % 2 == 0 ? 1 : -1) * (k / 2) * 0.45;
                int x = den.getX() + (int) Math.round(Math.cos(a) * r), z = den.getZ() + (int) Math.round(Math.sin(a) * r);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos at = new BlockPos(x, y, z);
                if (Math.abs(y - den.getY()) > 3) continue;
                if (!level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP) || !level.getFluidState(at).isEmpty()) continue;
                if (!level.getBlockState(at).isAir() || !level.getBlockState(at.above()).isAir() || !level.getBlockState(at.above(2)).isAir()) continue;
                return at;
            }
        }
        return den;
    }

    static @Nullable Rescuee rescuee(Ctx c, Trouble t) {
        if (t.get("captive_hi", 0) == 0 && t.get("captive_lo", 0) == 0) return null;
        Entity e = c.level().getEntity(new UUID(t.get("captive_hi", 0), t.get("captive_lo", 0)));
        return e instanceof Rescuee r && r.isAlive() ? r : null;
    }

    /** Whether what took them is still there: the lair's beast alive, the den's heart standing. */
    static boolean guarded(Ctx c, Trouble t) {
        if (t.cause.startsWith("lair:")) {
            LairRegistry.Entry e = LairRegistry.get(c.level().getServer()).get(Long.parseLong(t.cause.substring(5)));
            if (e == null || e.resident().isEmpty()) return false;
            Entity beast = c.level().getEntity(e.resident().get());
            return beast != null && beast.isAlive() && beast.distanceToSqr(Vec3.atCenterOf(e.den())) < 64 * 64;
        }
        Camps.Camp camp = camp(c, t);
        return camp != null && !camp.cleared(c.day()) && Camps.heartStanding(c.level(), camp);
    }

    private void lair(Ctx c, Trouble t, VillageService.Live l) {
        Rescuee r = rescuee(c, t);
        if (r == null) return;
        if (t.stage == LAIR && r.mode() != Rescuee.BOUND) {
            t.stage = FREED;
            t.since = c.now();
            c.st().setDirty();
            for (UUID h : t.helpers) {
                ServerPlayer hp = c.level().getServer().getPlayerList().getPlayer(h);
                if (hp != null) dev.awaken.living.api.Quests.journalChanged(hp);
            }
        }
        if (t.stage != FREED) return;
        // home: they've come into the square
        Vec3 w = TroubleDirector.well(l);
        if (r.position().distanceToSqr(w) < (l.plan().greenR + 6) * (l.plan().greenR + 6)) homecoming(c, t, l, r);
    }

    // ---- touched: cut free, climb on, follow ----------------------------------------------------------------------------------

    @Override
    public void touched(Ctx c, Trouble t, ServerPlayer p, Rescuee r) {
        switch (r.mode()) {
            case Rescuee.BOUND -> cut(c, t, p, r);
            case Rescuee.WAIT, Rescuee.FOLLOW -> {
                if (p.isShiftKeyDown()) {
                    r.follow = p.getUUID();
                    r.mode(Rescuee.FOLLOW);
                    r.say(TroubleText.line(c.level(), "village.trouble.rescue.walk", c.now()));
                } else if (r.climbOn(p)) {
                    r.say(TroubleText.line(c.level(), "village.trouble.rescue.carried", c.now()));
                    p.sendOverlayMessage(Component.literal(TroubleDirector.fill(c, t, "{person} holds on to your back · sneak a moment to set them down")));
                }
            }
            default -> {}
        }
    }

    private void cut(Ctx c, Trouble t, ServerPlayer p, Rescuee r) {
        cut(c, t, p, r, false);
    }

    private void cut(Ctx c, Trouble t, ServerPlayer p, Rescuee r, boolean force) {
        if (!force && guarded(c, t)) {
            p.sendOverlayMessage(Component.literal(TroubleDirector.fill(c, t, "Not while {beast} is here. Deal with it first.")));
            if (r.tickCount - r.said > 60) r.say(TroubleText.line(c.level(), "village.trouble.rescue.behind_you", c.now()));
            return;
        }
        float f = force ? 1f : Math.min(1f, r.cut() + CUT);
        r.cut(f);
        ServerLevel level = c.level();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBWEB.defaultBlockState()), r.getX(), r.getY() + 1.0, r.getZ(), 6, 0.3, 0.45, 0.3, 0.02);
        level.playSound(null, r.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 0.6f, 1.3f + level.getRandom().nextFloat() * 0.2f);
        int bars = Math.round(f * 12);
        p.sendOverlayMessage(Component.literal(TroubleDirector.fill(c, t, "Cutting {person} free  ") + "▮".repeat(bars) + "▯".repeat(12 - bars)));
        if (f < 1f) return;
        // free: the webbing torn away, and onto your back
        BlockPos nest = BlockPos.of(t.get("nest", r.blockPosition().asLong()));
        for (Direction d : Direction.values()) {
            for (int up = 0; up <= 2; up++) {
                BlockPos q = nest.relative(d).above(up);
                if (level.getBlockState(q).is(Blocks.COBWEB) && t.laid.contains(q.asLong())) level.destroyBlock(q, false);
            }
        }
        if (level.getBlockState(nest.above(2)).is(Blocks.COBWEB)) level.destroyBlock(nest.above(2), false);
        level.playSound(null, r.blockPosition(), SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, 1f, 0.8f);
        r.cut(0f);
        r.mode(Rescuee.WAIT);
        if (!t.helped(p.getUUID())) t.helpers.add(p.getUUID()); // whoever cuts them free is helping, said Help or not
        if (r.climbOn(p)) {
            r.say(TroubleText.line(level, "village.trouble.rescue.freed", c.now()));
            p.sendOverlayMessage(Component.literal(TroubleDirector.fill(c, t, "{person} holds on to your back · sneak a moment to set them down")));
        }
        t.stage = FREED;
        t.since = c.now();
        c.st().setDirty();
        dev.awaken.living.api.Quests.journalChanged(p);
        Awaken.LOG.info("[trouble] {} cut {} free", p.getName().getString(), r.getName().getString());
    }

    // ---- home --------------------------------------------------------------------------------------------------------------

    /** Into the square: off your back, running to their parent. */
    static void homecoming(Ctx c, Trouble t, VillageService.Live l, Rescuee r) {
        if (r.carried()) r.setDown(false);
        Vec3 w = TroubleDirector.well(l);
        Vec3 from = r.position();
        double a = Math.atan2(from.z - w.z, from.x - w.x);
        Vec3 kinAt = TroubleDirector.green(l, a, 2.0);
        r.home = new Vec3(kinAt.x + Math.cos(a) * 0.9, kinAt.y, kinAt.z + Math.sin(a) * 0.9);
        r.mode(Rescuee.HOME);
        TroubleDirector.stage(l, t.kin, kinAt, TroubleDirector.yaw(kinAt, r.home), "embrace", "");
        t.stage = HOME;
        t.since = c.now();
        t.put("reunion", c.now());
        c.st().setDirty();
        TroubleDirector.say(c.level(), l, t.kin, TroubleDirector.fill(c, t, TroubleText.line(c.level(), "village.trouble." + t.kind + ".reunion", c.now())), 120);
    }

    /** What happens once they're in their parent's arms (paid, remembered). */
    interface Done {
        void done(Ctx c, Trouble t, VillageService.Live l, Rescuee r);
    }

    static void reunion(Ctx c, Trouble t, VillageService.Live l, Done then) {
        Rescuee r = rescuee(c, t);
        long since = c.now() - t.get("reunion", c.now());
        Vec3 w = TroubleDirector.well(l);
        // the green gathers round them, cheering
        Set<Integer> not = new HashSet<>(TroubleDirector.household(l, t.kin));
        not.add(t.person);
        List<Integer> out = TroubleDirector.crowd(l, 7, not);
        Vec3 centre = r != null ? r.position() : w;
        for (int i = 0; i < out.size(); i++) {
            double a = i * (Math.PI * 2 / out.size()) + 0.3;
            Vec3 at = TroubleDirector.green(l, Math.atan2(centre.z - w.z, centre.x - w.x) + a * 0.35 + Math.PI * 0.85, 5.5 + (i % 2));
            TroubleDirector.stage(l, out.get(i), at, TroubleDirector.yaw(at, centre), since < 160 && i % 2 == 0 ? "cheer" : "arms_crossed", "");
        }
        if (r != null && r.mode() == Rescuee.EMBRACE && since > 40 && t.get("paid", 0) == 0) {
            then.done(c, t, l, r);
            t.put("paid", 1);
        }
        if (since > 20 * 9 && t.get("paid", 0) == 0 && r != null) {
            // didn't make it to their parent's arms (a crowd in the way): it's still home
            then.done(c, t, l, r);
            t.put("paid", 1);
        }
        if (t.get("paid", 0) != 0 && since > 20 * 14) {
            // the body hands back to the village's people: they come home for good, in their parent's arms a while longer
            if (r != null) r.discard();
            l.settlement().away(t.person, false);
            Vec3 kinAt = l.settlement().where(t.kin);
            if (kinAt != null) {
                Vec3 at = kinAt.add(0.9, 0, 0);
                TroubleDirector.stage(l, t.person, at, TroubleDirector.yaw(at, kinAt), "embrace", "");
            }
            TroubleDirector.end(c, t);
        }
    }

    /** Home: paid, remembered, the lair cleared for good, a token by their door. */
    private void done(Ctx c, Trouble t, VillageService.Live l, Rescuee r) {
        ServerLevel level = c.level();
        String person = l.settlement().personName(t.person);
        String family = l.plan().people.get(t.person).family();
        boolean child = l.settlement().age(t.person) == 0;
        List<ServerPlayer> paid = TroubleDirector.pay(c, t, r.position(), 48, l.plan().kind == VillagePlan.Kind.VILLAGE ? 20 : 14, 25, 0.5,
                TroubleDirector.fill(c, t, "{person} is home"),
                p -> Keepsake.make(child ? TroubleText.given(person) + "'s Whistle" : "The " + family + "s' Knot",
                        child ? TroubleText.given(person) + " carved it in the long hours in " + t.placeName + "." : "Red cord tied for luck, given by the " + family + "s.",
                        "From " + l.plan().name + ", for bringing " + TroubleText.given(person) + " home."));
        String who = TroubleDirector.names(paid);
        Villages.history(level.getServer(), l.key(), who + " brought " + TroubleText.given(person) + " back from " + t.placeName + ".");
        // remembered: the rescued for the API (W6 settles them), the lair for good, the token
        long day = c.day();
        for (ServerPlayer p : paid) {
            c.st().rescued(p.getUUID(), new Troubles.Rescued(l.key(), l.plan().name, t.person, person, l.settlement().job(t.person), l.settlement().age(t.person),
                    day, "taken", false));
            l.settlement().acquaint(p, 40, 100);
        }
        seal(c, t);
        token(c, t, l, who, person);
        Villages.addMood(level.getServer(), l.key(), 15);
        level.playSound(null, BlockPos.containing(r.position()), SoundEvents.PLAYER_LEVELUP, SoundSource.AMBIENT, 0.5f, 1.4f);
        TroubleNet.ring(level, r.position(), 0xF6D58A, 18);
    }

    /** The lair cleared for good (its beast never comes back), or the den sealed. */
    static void seal(Ctx c, Trouble t) {
        String key = t.cause.startsWith("lair:") ? "lair:" + t.place : t.cause;
        TroubleState.Memory mem = c.st().memory(t.village);
        if (!mem.sealed.contains(key)) mem.sealed.add(key);
        Camps.Camp camp = camp(c, t);
        if (camp != null) Camps.sealForGood(c.level(), camp.id);
        c.st().setDirty();
    }

    /** The family's carved token, nailed up beside their door, with your name on it. */
    static void token(Ctx c, Trouble t, VillageService.Live l, String who, String person) {
        int home = TroubleDirector.homeOf(l, t.person);
        if (home < 0) return;
        VillagePlan p = l.plan();
        VillagePlan.Building b = p.buildings.get(home);
        int fx = Integer.signum(b.outX() - b.doorX()), fz = Integer.signum(b.outZ() - b.doorZ());
        Direction out = Direction.getApproximateNearest(fx, 0, fz);
        Direction side = out.getClockWise();
        int y = p.ground(b.doorX(), b.doorZ()).getY() + 2;
        for (int s : new int[]{1, -1, 2, -2}) {
            BlockPos wall = new BlockPos(p.ox + b.doorX() + side.getStepX() * s, y, p.oz + b.doorZ() + side.getStepZ() * s);
            BlockPos at = wall.relative(out);
            if (!c.level().isLoaded(at) || !c.level().getBlockState(at).isAir()) continue;
            BlockState tok = TroubleContent.CARVED_TOKEN.get().defaultBlockState().setValue(CarvedToken.FACING, out);
            if (!tok.canSurvive(c.level(), at)) continue;
            c.level().setBlock(at, tok, net.minecraft.world.level.block.Block.UPDATE_ALL);
            String family = p.people.get(t.person).family();
            c.st().memory(t.village).tokens.put(at.asLong(),
                    "Carved by the " + family + "s for " + who + ", who brought " + TroubleText.given(person) + " home from " + t.placeName + ".");
            c.st().setDirty();
            return;
        }
    }

    // ---- without you -----------------------------------------------------------------------------------------------------

    @Override
    public boolean holds(Ctx c, Trouble t) {
        if (c.day() > t.due + 1) return false;
        Rescuee r = rescuee(c, t);
        if (r != null && (r.mode() == Rescuee.CARRIED || r.mode() == Rescuee.FOLLOW || r.mode() == Rescuee.HOME || r.mode() == Rescuee.EMBRACE)) return true;
        if (t.stage == HOME) return true;
        for (ServerPlayer p : c.level().players()) if (t.helped(p.getUUID()) && TroubleDirector.horizontal(p.blockPosition(), BlockPos.of(t.place)) < 96) return true;
        return false;
    }

    @Override
    public void miss(Ctx c, Trouble t) {
        VillageService.Live l = c.live();
        Rescuee r = rescuee(c, t);
        if (r != null) r.discard();
        TroubleState.Memory mem = c.st().memory(t.village);
        String person = l != null ? TroubleText.given(l.settlement().personName(t.person)) : "They";
        String family = l != null ? l.plan().people.get(t.person).family() : "family";
        // they found their own way home, days on, and the family left for Carillon: the house stands empty
        if (l != null) {
            for (int m : TroubleDirector.household(l, t.person)) {
                if (!mem.left.contains(m)) mem.left.add(m);
                l.settlement().away(m, true);
            }
        }
        Villages.history(c.level().getServer(), t.village, person + " got out of " + t.placeName + " alone, days later. The " + family + "s left for Carillon.");
        Villages.addMood(c.level().getServer(), t.village, -12);
        Villages.addProsperity(c.level().getServer(), t.village, -6);
        // the village remembers who said they'd come
        for (UUID id : t.helpers) {
            ServerPlayer p = c.level().getServer().getPlayerList().getPlayer(id);
            if (p != null && l != null) VillageService.standing(p, l, -4, "didn't come back for " + person);
        }
        c.st().setDirty();
    }

    @Override
    public void cleanup(Ctx c, Trouble t) {
        for (java.util.UUID h : t.helpers) {
            ServerPlayer hp = c.level().getServer().getPlayerList().getPlayer(h);
            if (hp != null) TroubleNet.trail(hp, t, false);
        }
        VillageService.Live l = c.live();
        if (l == null) return;
        Runner.release(c, t, l);
        // ended any other way (a command, a failure, a reunion cut short): whoever was taken is simply back home
        if (t.person >= 0 && l.settlement().isAway(t.person) && !c.st().memory(t.village).left.contains(t.person)) {
            Rescuee r = rescuee(c, t);
            if (r != null) r.discard();
            l.settlement().away(t.person, false);
        }
        // home: they stay in each other's arms until it lapses on its own (half a minute)
        if (t.stage == HOME) return;
        TroubleDirector.release(l, t.kin);
        for (int m : TroubleDirector.household(l, t.kin)) if (m != t.person) TroubleDirector.release(l, m);
    }
}
