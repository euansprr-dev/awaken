package dev.awaken.village.trouble;

import dev.awaken.Awaken;
import dev.awaken.signal.Signal;
import dev.awaken.village.VillageService;
import dev.awaken.village.api.Troubles;
import dev.awaken.village.api.Villages;
import dev.awaken.village.plan.VillagePlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Gone Missing (SPEC §28.7, PLAN 6.3): a storm at dusk, and someone who was out (a hunter, a woodcutter, a miner, a
 * child) doesn't come home. A search party goes out to the village's edge with lanterns, calling the name into the dark;
 * their parent waits by the well. Clues on the land: a strip of their clothes on the way, what they carried dropped at the
 * lip of a drop. They're at its foot (a ledge only a rope reaches, a ravine a climb goes down), hurt. Bring them home on
 * your back, or walking behind you and up the rope you hang. Thanks, a keepsake, Friend standing. Missed, they limp
 * home at dawn on their own.
 */
final class Missing implements Plot {
    static final int SEARCH = 0, FOUND = 3, HOME = Taken.HOME;
    static final int NEAR = 40;

    static @Nullable String cause(Ctx c, VillageService.Live l) {
        if (c.tod() < 11500 || c.tod() > 13800) return null;
        BlockPos o = new BlockPos(l.plan().ox, l.plan().baseY + 2, l.plan().oz);
        boolean storm = c.level().isThundering() || c.level().isRainingAt(o.above(40));
        if (!storm || Math.floorMod(Taken.mix(l.plan().seed, c.day(), 29), 3L) != 0) return null;
        return c.level().isThundering() ? "storm" : "rain";
    }

    @Override
    public boolean begin(Ctx c, Trouble t) {
        VillageService.Live l = c.live();
        if (l == null) return false;
        VillagePlan p = l.plan();
        java.util.Random r = new java.util.Random(Taken.mix(p.seed, t.id, 31));
        // who: someone who works out on the land, else a child; who waits: their household
        int person = -1, kin = -1;
        for (String job : new String[]{"hunter", "woodcutter", "miner", "shepherd", "child"}) {
            for (int i = 0; i < p.people.size() && person < 0; i++) {
                if (!l.settlement().job(i).equals(job) && !(job.equals("child") && l.settlement().age(i) == 0)) continue;
                if (l.settlement().isAway(i) || c.st().memory(l.key()).left.contains(i)) continue;
                for (int m : TroubleDirector.household(l, i)) if (m != i && l.settlement().age(m) >= 1 && !l.settlement().isAway(m)) kin = m;
                if (kin >= 0) person = i;
            }
            if (person >= 0) break;
        }
        if (person < 0) return false;
        BlockPos spot = searchSpot(c.level(), l, r);
        if (spot == null) return false;
        t.person = person;
        t.kin = kin;
        t.place = spot.asLong();
        t.put("drop", dropAt(c.level(), spot) >= 5 ? 1 : 0);
        t.placeName = t.get("drop", 0) != 0 ? "the foot of a drop " + TroubleText.compass(spot.getX() - p.ox, spot.getZ() - p.oz) : "the woods";
        t.due = c.day() + 2;
        l.settlement().away(person, true);
        // the clues: a strip of their clothes on the way, what they carried at the lip
        Vec3 o = TroubleDirector.well(l);
        for (int k = 0; k < 3; k++) {
            double s = 0.3 + k * 0.22;
            t.put("clue" + k, BlockPos.asLong((int) Math.floor(o.x + (spot.getX() - o.x) * s), 0, (int) Math.floor(o.z + (spot.getZ() - o.z) * s)));
        }
        Villages.history(c.level().getServer(), l.key(), TroubleText.given(l.settlement().personName(person)) + " didn't come home in the storm.");
        Villages.addMood(c.level().getServer(), l.key(), -8);
        return true;
    }

    /**
     * Where they are: in a ring 60-180 out, the foot of a drop of five or more (the deepest found), in loaded ground; else
     * any dry ground 90 out.
     */
    static @Nullable BlockPos searchSpot(ServerLevel level, VillageService.Live l, java.util.Random r) {
        VillagePlan p = l.plan();
        BlockPos best = null;
        int bestDrop = 4;
        BlockPos plain = null;
        for (int i = 0; i < 96; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = p.radius() + 50 + r.nextDouble() * 130;
            int x = (int) Math.floor(p.ox + Math.cos(a) * d), z = (int) Math.floor(p.oz + Math.sin(a) * d);
            if (!level.isLoaded(new BlockPos(x, 0, z))) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos at = new BlockPos(x, y, z);
            if (!level.getFluidState(at).isEmpty() || !level.getFluidState(at.below()).isEmpty()) continue;
            if (plain == null && d > 80) plain = at;
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                for (int k = 1; k <= 3; k++) {
                    BlockPos q = at.relative(dir, k);
                    if (!level.isLoaded(q)) continue;
                    int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, q.getX(), q.getZ());
                    if (h - y > bestDrop) {
                        bestDrop = h - y;
                        best = at;
                    }
                }
            }
        }
        return best != null ? best : plain;
    }

    /** How far the ground rises within three blocks of a spot (a drop they fell down). */
    static int dropAt(ServerLevel level, BlockPos at) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()), most = 0;
        for (Direction dir : Direction.Plane.HORIZONTAL)
            for (int k = 1; k <= 3; k++) {
                BlockPos q = at.relative(dir, k);
                if (level.isLoaded(q)) most = Math.max(most, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, q.getX(), q.getZ()) - y);
            }
        return most;
    }

    @Override
    public void tick(Ctx c, Trouble t) {
        VillageService.Live l = c.live();
        if (l == null) return;
        clues(c, t);
        if (t.stage == SEARCH) {
            search(c, t, l);
            find(c, t, l);
        } else if (t.stage == FOUND) {
            Rescuee r = Taken.rescuee(c, t);
            if (r != null && r.position().distanceToSqr(TroubleDirector.well(l)) < (l.plan().greenR + 6) * (l.plan().greenR + 6)) Taken.homecoming(c, t, l, r);
        } else if (t.stage == HOME) Taken.reunion(c, t, l, this::done);
    }

    private void clues(Ctx c, Trouble t) {
        for (int k = 0; k < 3; k++) {
            long at = t.get("clue" + k, 0);
            if (at == 0 || t.get("clued" + k, 0) != 0) continue;
            BlockPos q = BlockPos.of(at);
            if (!c.level().isLoaded(q)) continue;
            int y = c.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, q.getX(), q.getZ());
            BlockPos pos = new BlockPos(q.getX(), y, q.getZ());
            BlockState s = TroubleContent.TORN_CLOTH.get().defaultBlockState().setValue(TrailMark.LOOK, (int) Math.floorMod(t.id + k, 4L));
            if (c.level().getBlockState(pos.below()).isFaceSturdy(c.level(), pos.below(), Direction.UP)) TroubleDirector.lay(c.level(), t, pos, s);
            t.put("clued" + k, 1);
        }
    }

    /** At dusk and through the night: a search party at the edge with lanterns, calling; the parent at the well. */
    private void search(Ctx c, Trouble t, VillageService.Live l) {
        Vec3 w = TroubleDirector.well(l);
        BlockPos spot = BlockPos.of(t.place);
        double toward = Math.atan2(spot.getZ() - w.z, spot.getX() - w.x);
        boolean dark = c.tod() >= 12000 && c.tod() < 23000;
        Vec3 kinAt = TroubleDirector.green(l, toward + 1.2, 2.2);
        TroubleDirector.stage(l, t.kin, kinAt, TroubleDirector.yaw(kinAt, w), dark ? "weep" : "talk", "");
        Set<Integer> not = new HashSet<>(TroubleDirector.household(l, t.kin));
        not.add(t.person);
        List<Integer> party = dark ? TroubleDirector.crowd(l, 5, not, "watch", "hunter", "woodcutter", "farmer") : new ArrayList<>();
        double edge = l.plan().radius() + 2;
        for (int i = 0; i < party.size(); i++) {
            double a = toward + (i - (party.size() - 1) / 2.0) * 0.16;
            Vec3 at = TroubleDirector.stand(c.level(), l, w.x + Math.cos(a) * (edge + (i % 2) * 3), w.z + Math.sin(a) * (edge + (i % 2) * 3));
            Vec3 look = new Vec3(at.x + Math.cos(a) * 12, at.y, at.z + Math.sin(a) * 12);
            TroubleDirector.stage(l, party.get(i), at, TroubleDirector.yaw(at, look), i % 2 == 0 ? "point_shout" : "look_around", "lantern");
        }
        if (dark && !party.isEmpty() && TroubleDirector.every(c, 120, 13)) {
            String name = TroubleText.given(l.settlement().personName(t.person));
            TroubleDirector.say(c.level(), l, party.get((int) Math.floorMod(c.now() / 120, (long) party.size())),
                    TroubleText.fill(TroubleText.line(c.level(), "village.trouble.missing.search", c.now() / 120), "person", name, "upper", name.toUpperCase()), 70);
        }
    }

    /** Someone comes close: there they are, hurt, at the foot of the drop. */
    private void find(Ctx c, Trouble t, VillageService.Live l) {
        if (Taken.rescuee(c, t) != null) return;
        BlockPos spot = BlockPos.of(t.place);
        if (!c.level().isLoaded(spot)) return;
        boolean near = false;
        for (ServerPlayer p : c.level().players()) near |= !p.isSpectator() && p.distanceToSqr(Vec3.atCenterOf(spot)) < NEAR * NEAR;
        if (!near) return;
        Rescuee r = TroubleContent.RESCUEE.get().create(c.level(), EntitySpawnReason.EVENT);
        if (r == null) return;
        var look = l.settlement().look(t.person);
        r.become(t.id, t.village, t.person, l.settlement().personName(t.person), look.texture(), look.slim(), l.settlement().age(t.person) == 0);
        int y = c.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spot.getX(), spot.getZ());
        r.snapTo(spot.getX() + 0.5, y, spot.getZ() + 0.5, c.level().getRandom().nextFloat() * 360f, 0);
        r.mode(Rescuee.LOST);
        c.level().addFreshEntity(r);
        t.put("captive_hi", r.getUUID().getMostSignificantBits());
        t.put("captive_lo", r.getUUID().getLeastSignificantBits());
        // what they carried, dropped by them where they fell
        ItemStack carried = new ItemStack(switch (l.settlement().job(t.person)) {
            case "hunter" -> Items.BOW;
            case "woodcutter" -> Items.IRON_AXE;
            case "miner" -> Items.IRON_PICKAXE;
            case "shepherd" -> Items.SHEARS;
            default -> Items.LANTERN;
        });
        ItemEntity drop = new ItemEntity(c.level(), spot.getX() + 1.5, y + 0.2, spot.getZ() + 0.5, carried);
        drop.setPickUpDelay(20 * 60 * 10);
        c.level().addFreshEntity(drop);
        t.put("drop_hi", drop.getUUID().getMostSignificantBits());
        t.put("drop_lo", drop.getUUID().getLeastSignificantBits());
        c.st().setDirty();
        Awaken.LOG.info("[trouble] {} found at {}", l.settlement().personName(t.person), spot.toShortString());
    }

    @Override
    public void touched(Ctx c, Trouble t, ServerPlayer p, Rescuee r) {
        if (r.mode() == Rescuee.LOST || r.mode() == Rescuee.WAIT || r.mode() == Rescuee.FOLLOW) {
            if (!t.helped(p.getUUID())) t.helpers.add(p.getUUID());
            if (p.isShiftKeyDown()) {
                r.follow = p.getUUID();
                r.mode(Rescuee.FOLLOW);
                r.say(TroubleText.line(c.level(), "village.trouble.missing.walk", c.now()));
            } else if (r.climbOn(p)) {
                r.say(TroubleText.line(c.level(), "village.trouble.missing.found", c.now()));
                p.sendOverlayMessage(Component.literal(TroubleDirector.fill(c, t, "{person} holds on to your back · sneak a moment to set them down")));
            }
            if (t.stage == SEARCH) {
                t.stage = FOUND;
                t.since = c.now();
                c.st().setDirty();
                dev.awaken.living.api.Quests.journalChanged(p);
            }
        }
    }

    private void done(Ctx c, Trouble t, VillageService.Live l, Rescuee r) {
        String person = l.settlement().personName(t.person);
        List<ServerPlayer> paid = TroubleDirector.pay(c, t, r.position(), 48, 10, 30, 0.35, TroubleDirector.fill(c, t, "{person} is home"),
                p -> Keepsake.make(TroubleText.given(person) + "'s Lucky Stone", "A river stone with a hole worn through it.",
                        "From the " + l.plan().people.get(t.person).family() + "s of " + l.plan().name + ", for finding " + TroubleText.given(person) + "."));
        Villages.history(c.level().getServer(), l.key(), TroubleDirector.names(paid) + " found " + TroubleText.given(person) + " at " + t.placeName
                + " and brought them home.");
        for (ServerPlayer p : paid) {
            c.st().rescued(p.getUUID(), new Troubles.Rescued(l.key(), l.plan().name, t.person, person, l.settlement().job(t.person), l.settlement().age(t.person),
                    c.day(), "missing", false));
            l.settlement().acquaint(p, 30, 100);
        }
        TroubleNet.ring(c.level(), r.position(), 0xF6D58A, 14);
    }

    @Override
    public boolean ask(Ctx c, Trouble t, ServerPlayer p, int rec) {
        VillageService.Live l = c.live();
        if (l == null || rec != t.kin && rec != Runner.of(t) || t.stage >= HOME) return false;
        if (t.helped(p.getUUID())) {
            l.settlement().say(p, rec, TroubleDirector.fill(c, t, TroubleText.line(c.level(), "village.trouble.missing.again", c.now() / 100)), 100);
            return true;
        }
        l.settlement().say(p, rec, TroubleDirector.fill(c, t, TroubleText.line(c.level(), "village.trouble.missing.ask", c.now() / 100 + p.getId())), 140);
        String person = TroubleText.given(l.settlement().personName(t.person));
        TroubleDirector.card(p, t, "Gone Missing · " + person, TroubleDirector.fill(c, t,
                "{person} went out before the storm and didn't come home. The search went {dir}. Find {person} and bring them home."));
        return true;
    }

    @Override
    public void help(Ctx c, Trouble t, ServerPlayer p) {
        VillageService.Live l = c.live();
        if (l != null) l.settlement().say(p, t.kin, TroubleDirector.fill(c, t, TroubleText.line(c.level(), "village.trouble.missing.thanks", c.now())), 120);
        Signal.toast(p, Component.literal(TroubleDirector.fill(c, t, "The search went {dir} from {village}")), dev.awaken.core.api.Notices.Tier.IMPORTANT,
                "village_trouble");
    }

    @Override
    public String journal(Ctx c, Trouble t, ServerPlayer p) {
        return t.stage >= FOUND ? TroubleDirector.fill(c, t, "Bring {person} home to {village}'s square.")
                : TroubleDirector.fill(c, t, "Search {dir} of {village} for {person}: look for what they dropped.");
    }

    @Override
    public @Nullable BlockPos goal(Ctx c, Trouble t, ServerPlayer p) {
        VillageService.Live l = c.live();
        if (t.stage >= FOUND && l != null) return BlockPos.containing(TroubleDirector.well(l));
        // the search's way: the clue nearest ahead, not the spot itself
        for (int k = 0; k < 3; k++) {
            BlockPos q = BlockPos.of(t.get("clue" + k, t.place));
            if (Math.hypot(q.getX() - p.getX(), q.getZ() - p.getZ()) > 12) return new BlockPos(q.getX(), (int) p.getY(), q.getZ());
        }
        return null;
    }

    @Override
    public boolean holds(Ctx c, Trouble t) {
        if (c.day() > t.due + 1) return false;
        Rescuee r = Taken.rescuee(c, t);
        return t.stage == HOME || r != null && (r.mode() == Rescuee.CARRIED || r.mode() == Rescuee.FOLLOW || r.mode() == Rescuee.HOME);
    }

    @Override
    public void miss(Ctx c, Trouble t) {
        VillageService.Live l = c.live();
        String person = l != null ? TroubleText.given(l.settlement().personName(t.person)) : "They";
        Villages.history(c.level().getServer(), t.village, person + " limped home at dawn on their own, two days on.");
        Villages.addMood(c.level().getServer(), t.village, -5);
    }

    @Override
    public void cleanup(Ctx c, Trouble t) {
        VillageService.Live l = c.live();
        Rescuee r = Taken.rescuee(c, t);
        if (t.stage != HOME && r != null) r.discard();
        var drop = c.level().getEntity(new java.util.UUID(t.get("drop_hi", 0), t.get("drop_lo", 0)));
        if (drop != null) drop.discard();
        if (l == null) return;
        Runner.release(c, t, l);
        if (t.stage != HOME) {
            TroubleDirector.release(l, t.kin);
            for (int m : TroubleDirector.household(l, t.kin)) if (m != t.person) TroubleDirector.release(l, m);
        }
        if (t.person >= 0 && l.settlement().isAway(t.person) && t.stage != HOME) l.settlement().away(t.person, false);
        if (t.stage == HOME && l.settlement().isAway(t.person)) l.settlement().away(t.person, false);
    }
}
