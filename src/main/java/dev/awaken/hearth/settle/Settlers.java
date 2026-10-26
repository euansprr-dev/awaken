package dev.awaken.hearth.settle;

import dev.awaken.core.PlayDays;
import dev.awaken.core.api.RenownHooks;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.hearth.HearthViews;
import dev.awaken.hearth.Household;
import dev.awaken.hearth.room.Reading;
import dev.awaken.hearth.room.Room;
import dev.awaken.hearth.room.RoomType;
import dev.awaken.hearth.town.Townsfolk;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Who comes to live at your Hearth (SPEC §28.8, PLAN 4.7, decision 2). Someone moves in when three things are true:
 * you know them (Terraria's readable rule): a townsperson by the household's rank (Oren E, Mira D, Teodor C, Sun B,
 * Vael A until S), a rank early once Carillon knows you (its renown at Known: "Trusted"); a Wayfarer whose chain you
 * finished (Ilse, Hettie, Wren, Pell); someone from a village where you're a Friend; someone you brought home from a
 * trouble; and a spare bedroom of Snug or better stands, and their trade has its room (Ilse a Greenhouse, Hettie a field
 * or a pen, a miner a Mine head; the townsfolk only want a bed). One comes at a time, up to the tier's cap; they walk up
 * the road with a bundle ({@link Arrival}) and take the room ("Ilse's room"). Checked every ten seconds per Hearth.
 */
public final class Settlers {
    private Settlers() {}

    /** How many may live here at each tier (0 Hearthfire .. V Town): PLAN 4.8's "two settlers" at II, four at III. */
    public static int cap(int tier) {
        return switch (Math.max(0, tier)) {
            case 0 -> 0;
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 4;
            case 4 -> 8;
            default -> 12;
        };
    }

    /** The tier that opens one more place (the guide's "Tier II for a second settler"), or -1 at the top. */
    static int tierFor(int living) {
        for (int t = 1; t <= HearthRecord.MAX_TIER; t++) if (cap(t) > living) return t;
        return -1;
    }

    // ---- who you know --------------------------------------------------------------------------------------------

    /** The Wayfarers who settle, by the chain of theirs that makes you known to them. */
    private static final Map<String, String> CHAINS = Map.of("ilse", "moth_lights", "hettie", "ten_sheep_short", "wren", "wrens_rope",
            "pell", "pells_message");

    static Folk.Person wayfarer(String who) {
        return switch (who) {
            case "ilse" -> new Folk.Person("ilse", "Ilse", "", "ilse", "healer", "wayfarer/ilse", true, "the Wayfarer camp");
            case "hettie" -> new Folk.Person("hettie", "Hettie", "", "hettie", "farmer", "wayfarer/hettie", true, "her flock's hills");
            case "wren" -> new Folk.Person("wren", "Wren", "", "wren", "resident", "wayfarer/wren", true, "the high roads");
            default -> new Folk.Person("pell", "Pell", "", "pell", "runner", "wayfarer/pell", false, "the roads");
        };
    }

    /** A townsperson as someone who lives with you: Carillon's named faces for the four, their trades in the people engine. */
    static Folk.Person townsperson(String id) {
        Townsfolk.Def d = Townsfolk.get(id).orElse(null);
        String given = d == null ? Townsfolk.shortName(id) : d.name();
        String skin = Townsfolk.skin(id);
        return switch (skin) {
            case "oren" -> new Folk.Person(id, given, "", "oren", "smith", "townsfolk/oren", false, "Carillon");
            case "mira" -> new Folk.Person(id, given, "", "mira", "watch", "townsfolk/mira", false, "Carillon");
            case "teodor" -> new Folk.Person(id, given, "", "teodor", "archivist", "townsfolk/teodor", false, "Carillon");
            case "sun" -> new Folk.Person(id, given, "", "sun", "healer", "townsfolk/sun", false, "Carillon");
            case "vael" -> new Folk.Person(id, given, "", "vael", "resident", "townsfolk/vael", false, "the Court");
            default -> new Folk.Person(id, given, "", "hand", "resident", "townsfolk/" + skin, false, "Carillon");
        };
    }

    private static final String[] GIVEN = {"Ada", "Aldo", "Bettany", "Cass", "Dunstan", "Edda", "Fen", "Garrick", "Hester", "Ivo",
            "Jory", "Kit", "Lark", "Maddox", "Nell", "Osric", "Petra", "Rowan", "Sorrel", "Tamsin", "Ulric", "Wynn", "Yara", "Tobin"};
    private static final Set<String> SHE = Set.of("Ilse", "Hettie", "Wren", "Mira", "Sun", "Ada", "Bettany", "Cass", "Edda", "Hester", "Lark",
            "Nell", "Petra", "Sorrel", "Tamsin", "Yara");
    private static final Set<String> HE = Set.of("Oren", "Teodor", "Pell", "Aldo", "Dunstan", "Fen", "Garrick", "Ivo", "Jory", "Maddox",
            "Osric", "Rowan", "Ulric", "Tobin");

    /** The word for their work ("smith", "herbalist", "miner"). */
    public static String title(Folk.Person p) {
        return Work.title(p);
    }

    /** "him", "her" or "them", for the guide's lines ("Oren will come when there's a bed for him"). */
    public static String them(Folk.Person p) {
        return SHE.contains(p.given()) ? "her" : HE.contains(p.given()) ? "him" : "them";
    }

    public static String they(Folk.Person p) {
        return SHE.contains(p.given()) ? "she" : HE.contains(p.given()) ? "he" : "they";
    }

    /** Someone from a place you're welcome in (a village where you're a Friend, or the city): named by seed, work decided at move-in. */
    static Folk.Person outsider(String id, String from, long seed) {
        String given = GIVEN[(int) Math.floorMod(seed, (long) GIVEN.length)];
        return new Folk.Person(id, given, "", "any", "resident", "", false, from);
    }

    /**
     * Everyone each online member of the household knows now joins the Hearth's known (sticky: an offline owner's
     * people still come). Vael leaves at S.
     */
    static void learn(ServerLevel level, HearthRecord h) {
        MinecraftServer server = level.getServer();
        Folk f = h.folk;
        Rank best = null;
        boolean trusted = false;
        for (ServerPlayer p : Household.online(server, h)) {
            Rank r = Progression.rank(p);
            if (best == null || r.ordinal() > best.ordinal()) best = r;
            trusted |= RenownHooks.tier(p) >= RenownHooks.KNOWN;
            // the Wayfarers whose chain you finished
            var quests = p.getData(dev.awaken.living.LivingContent.QUESTS.get()).done();
            for (var e : CHAINS.entrySet()) if (quests.contains(e.getValue())) know(f, wayfarer(e.getKey()));
            // a village where you're a Friend sends someone
            for (var v : dev.awaken.village.api.Villages.all(level)) {
                if (dev.awaken.village.api.Villages.standing(p, v.key()) < dev.awaken.village.api.Villages.FRIEND) continue;
                String id = "village:" + v.key();
                if (!f.known.containsKey(id)) know(f, outsider(id, v.name(), v.key().hashCode() * 31L + h.owner.hashCode()));
            }
            // the city: once Carillon knows you well, someone of its own comes out to the wilds with you
            if (RenownHooks.tier(p) >= RenownHooks.KNOWN && !f.known.containsKey("carillon")) {
                know(f, outsider("carillon", "Carillon", h.owner.getLeastSignificantBits() ^ 0x5EED));
            }
            for (Folk.Person rescued : Rescued.of(p)) know(f, rescued);
        }
        if (best == null) return;
        // the townsfolk keep their ranks; once Carillon trusts you each comes a rank early (SPEC §16 decision 8)
        int rank = best.ordinal(), reach = rank + (trusted ? 1 : 0);
        for (var e : Townsfolk.inArrivalOrder()) {
            Townsfolk.Def d = e.getValue();
            boolean present = d.arrivesAt().ordinal() <= reach && d.leavesAt().map(l -> rank < l.ordinal()).orElse(true);
            if (present) know(f, townsperson(e.getKey()));
            else if (d.leavesAt().isPresent() && rank >= d.leavesAt().get().ordinal()) leave(level, h, e.getKey());
        }
        for (String id : h.arrived) if (Townsfolk.get(id).isPresent()) know(f, townsperson(id)); // an old world's town
    }

    private static void know(Folk f, Folk.Person p) {
        f.known.putIfAbsent(p.id(), p);
    }

    /**
     * Admin and tours: someone is known now whatever brought them ("oren_blacksmith" or "oren", "ilse", "hettie", "wren",
     * "pell", "village:<key>", "carillon", "miner": someone from the city who'll work your shaft). Null when nobody's by
     * that name.
     */
    public static Folk.@Nullable Person forceKnow(ServerLevel level, HearthRecord h, String id) {
        Folk.Person p = null;
        for (var e : Townsfolk.all().keySet()) if (e.equals(id) || Townsfolk.skin(e).equals(id)) p = townsperson(e);
        if (p == null && CHAINS.containsKey(id)) p = wayfarer(id);
        if (p == null && id.startsWith("village:")) p = outsider(id, id.substring(8), id.hashCode());
        if (p == null && (id.equals("carillon") || id.equals("miner"))) {
            Folk.Person o = outsider(id.equals("miner") ? "carillon:miner" : "carillon", "Carillon", id.hashCode() ^ h.owner.hashCode());
            p = id.equals("miner") ? new Folk.Person(o.id(), o.given(), o.family(), "miner", "miner", o.face(), o.slim(), o.from()) : o;
        }
        if (p == null) return null;
        h.folk.known.remove(p.id());
        h.folk.known.put(p.id(), p);
        HearthService.dirty(level.getServer());
        return p;
    }

    /** Admin and tours: lets this one person in now if they can come (cap, their trade's room, a spare bedroom); why not otherwise. */
    public static String settleNow(ServerLevel level, HearthRecord h, String id, @Nullable BlockPos from) {
        Folk.Person p = h.folk.known.get(id);
        if (p == null) return "nobody known as " + id;
        if (h.folk.settler(id) != null) return p.given() + " already lives here";
        if (h.folk.settlers.size() >= cap(h.tier)) return "the Hearth's tier holds " + cap(h.tier);
        Folk.Person who = settled(h, p);
        String need = need(h, who);
        if (!need.isEmpty()) return who.given() + " needs " + need;
        List<Bedroom> spare = spare(level, h);
        if (spare.isEmpty()) return "no spare Snug bedroom";
        Arrival.start(level, h, who, nearestTo(spare, workAt(h, who)), from);
        return "";
    }

    /** Someone whose time here is over (Vael at S): gone, with a note. */
    static void leave(ServerLevel level, HearthRecord h, String id) {
        Folk.Settler s = h.folk.settler(id);
        h.folk.known.remove(id);
        if (s == null) return;
        h.folk.settlers.remove(s);
        HearthService.dirty(level.getServer());
        HomePeople.changed(h);
        String note = "vael".equals(s.who.role()) ? "Gone to the Court. Don't follow. (Follow.)" : "Moved on.";
        for (ServerPlayer p : Household.online(level.getServer(), h)) {
            Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(s.who.name() + " left"))
                    .body(Component.literal("A note on the table: “" + note + "”")).ttl(20 * 10).build());
        }
    }

    // ---- the rooms they need ----------------------------------------------------------------------------------------

    /** A bedroom and the bed in it (its head). */
    public record Bedroom(Room room, BlockPos bed) {}

    private static final Map<UUID, List<Bedroom>> BEDROOMS = new HashMap<>();
    /** The rooms list each cache entry was read from (a scan replaces the list: the cache goes with it). */
    private static final Map<UUID, List<Room>> BEDROOMS_AT = new HashMap<>();

    /** Every read bedroom and its bed, best first (cached until the next scan). */
    public static List<Bedroom> bedrooms(ServerLevel level, HearthRecord h) {
        List<Room> at = BEDROOMS_AT.get(h.owner);
        List<Bedroom> cached = BEDROOMS.get(h.owner);
        if (cached != null && at == h.rooms && h.lastScan >= 0) return cached;
        List<Bedroom> out = new ArrayList<>();
        for (Room r : h.rooms) {
            if (!r.types().contains(RoomType.BEDROOM)) continue;
            BlockPos bed = bedIn(level, r);
            if (bed != null) out.add(new Bedroom(r, bed));
        }
        out.sort(Comparator.comparingInt((Bedroom b) -> -b.room.reading().grade().ordinal()).thenComparingDouble(b -> b.bed.distSqr(h.pos)));
        if (h.lastScan >= 0) {
            BEDROOMS.put(h.owner, List.copyOf(out));
            BEDROOMS_AT.put(h.owner, h.rooms);
        }
        return out;
    }

    /** The head of the first bed inside a room (its box read once a scan; a hall's beds are a hall's). */
    static @Nullable BlockPos bedIn(ServerLevel level, Room r) {
        BlockPos a = r.min(), b = r.max();
        long volume = (long) (b.getX() - a.getX() + 1) * (b.getY() - a.getY() + 1) * (b.getZ() - a.getZ() + 1);
        if (volume > 12_000) return null;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = a.getY(); y <= b.getY(); y++) for (int z = a.getZ(); z <= b.getZ(); z++) for (int x = a.getX(); x <= b.getX(); x++) {
            m.set(x, y, z);
            if (!level.isLoaded(m)) continue;
            BlockState s = level.getBlockState(m);
            if (s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.HEAD && r.contains(m)) return m.immutable();
        }
        return null;
    }

    /** The bedroom a bed stands in. */
    static @Nullable Bedroom roomOf(List<Bedroom> rooms, BlockPos bed) {
        for (Bedroom b : rooms) if (b.bed.equals(bed) || b.room.contains(bed)) return b;
        return null;
    }

    /**
     * The bedrooms nobody has: not a settler's, not the bed a member of the household last woke in, and for each member
     * who has never slept here the best one is kept for them. Of Snug or better.
     */
    public static List<Bedroom> spare(ServerLevel level, HearthRecord h) {
        List<Bedroom> all = new ArrayList<>(bedrooms(level, h));
        Set<Room> taken = new HashSet<>();
        for (Folk.Settler s : h.folk.settlers) {
            if (s.bed == null) continue;
            Bedroom b = roomOf(all, s.bed);
            if (b != null) taken.add(b.room);
        }
        int unhoused = 0;
        for (UUID m : h.everyone()) {
            BlockPos own = h.folk.ownBeds.get(m);
            Bedroom b = own == null ? null : roomOf(all, own);
            if (b != null) taken.add(b.room);
            else unhoused++;
        }
        List<Bedroom> out = new ArrayList<>();
        for (Bedroom b : all) {
            if (taken.contains(b.room)) continue;
            if (unhoused > 0) {
                unhoused--; // kept for a member of the household who hasn't slept here yet
                continue;
            }
            if (b.room.reading().grade().ordinal() >= Reading.Grade.SNUG.ordinal()) out.add(b);
        }
        return out;
    }

    /** "" when their trade has its room here, else what's missing ("a Greenhouse"). */
    static String need(HearthRecord h, Folk.Person p) {
        return switch (role(h, p)) {
            case "ilse" -> h.hasRoom(RoomType.GREENHOUSE) ? "" : "a Greenhouse";
            case "hettie", "farmer" -> (h.land & (dev.awaken.hearth.Comfort.FIELD | dev.awaken.hearth.Comfort.PEN)) != 0 ? "" : "a field or a pen";
            case "miner" -> h.hasRoom(RoomType.MINE_HEAD) && workedShafts(h, p) ? "" : "a Mine head";
            case "smith" -> h.hasRoom(RoomType.ARMORY) ? "" : "a Forge";
            default -> "";
        };
    }

    /** A Mine head free for one more miner (one miner a shaft). */
    private static boolean workedShafts(HearthRecord h, Folk.Person p) {
        int shafts = 0, miners = 0;
        for (Room r : h.rooms) if (r.types().contains(RoomType.MINE_HEAD)) shafts++;
        for (Folk.Settler s : h.folk.settlers) if (!s.who.id().equals(p.id()) && "miner".equals(s.who.role())) miners++;
        return shafts > miners;
    }

    /**
     * What someone from outside (a village, the city, one you rescued) will do here, decided when they come: work your
     * mine if a shaft waits for a miner, your fields if nobody tends them, else they lend a hand.
     */
    static String role(HearthRecord h, Folk.Person p) {
        if (!p.role().equals("any")) return p.role();
        boolean miner = false, farmer = false;
        for (Folk.Settler s : h.folk.settlers) {
            miner |= "miner".equals(s.who.role());
            farmer |= "hettie".equals(s.who.role()) || "farmer".equals(s.who.role());
        }
        if (h.hasRoom(RoomType.MINE_HEAD) && !miner) return "miner";
        if ((h.land & (dev.awaken.hearth.Comfort.FIELD | dev.awaken.hearth.Comfort.PEN)) != 0 && !farmer) return "farmer";
        return "hand";
    }

    static Folk.Person settled(HearthRecord h, Folk.Person p) {
        String role = role(h, p);
        if (role.equals(p.role())) return p;
        String trade = switch (role) {
            case "miner" -> "miner";
            case "farmer" -> "farmer";
            default -> "resident";
        };
        return new Folk.Person(p.id(), p.given(), p.family(), role, trade, p.face(), p.slim(), p.from());
    }

    // ---- every ten seconds ------------------------------------------------------------------------------------------

    /** Learns who's known, keeps beds honest, lets the next one in, and keeps their people engine in step. */
    public static void upkeep(ServerLevel level, HearthRecord h, long now) {
        if (h.hearthfire()) {
            HomePeople.stop(h);
            return;
        }
        learn(level, h);
        if (h.lastScan < 0) return; // the rooms come back with the next scan: nobody moves until they're read
        List<Bedroom> rooms = bedrooms(level, h);
        boolean changed = false;
        Folk.Settler coming = null;
        for (Folk.Settler s : h.folk.settlers) {
            if (s.state == Folk.Settler.COMING) coming = s;
            // a bed taken away (broken, or the room no longer a bedroom): they wait for one again, at home
            if (s.bed != null && roomOf(rooms, s.bed) == null) {
                s.bed = null;
                changed = true;
            }
        }
        // a settler between rooms takes a spare before anyone new comes
        for (Folk.Settler s : h.folk.settlers) {
            if (s.bed != null || !s.home()) continue;
            List<Bedroom> spare = spare(level, h);
            if (spare.isEmpty()) break;
            s.bed = spare.getFirst().bed;
            changed = true;
        }
        if (coming != null && !Arrival.walking(coming.who.id()) && now - Arrival.startedAt(coming.who.id()) > 20 * 30) {
            // the walk ended without them (a restart, unloaded land): they're in
            Arrival.movedIn(level, h, coming, false);
            changed = true;
        }
        if (coming == null) changed |= invite(level, h, null);
        if (changed) {
            HearthService.dirty(level.getServer());
            HomePeople.changed(h);
        }
        HomePeople.sync(level, h, now);
    }

    /**
     * The first known person who can come now does: under the cap, their trade's room standing, a spare bedroom (the one
     * nearest their work). {@code from}: where they walk up from (null: their own road). True when someone set out.
     */
    public static boolean invite(ServerLevel level, HearthRecord h, @Nullable BlockPos from) {
        Folk f = h.folk;
        if (f.settlers.size() >= cap(h.tier)) return false;
        for (Folk.Person p : f.known.values()) {
            if (f.settler(p.id()) != null || !need(h, p).isEmpty()) continue;
            List<Bedroom> spare = spare(level, h);
            if (spare.isEmpty()) return false;
            Folk.Person who = settled(h, p);
            Bedroom room = nearestTo(spare, workAt(h, who));
            Arrival.start(level, h, who, room, from);
            return true;
        }
        return false;
    }

    /** Where someone's work is (their trade's room, the field), for the bedroom nearest it; the Anchor otherwise. */
    static BlockPos workAt(HearthRecord h, Folk.Person p) {
        RoomType t = switch (p.role()) {
            case "oren", "smith" -> RoomType.ARMORY;
            case "ilse" -> RoomType.GREENHOUSE;
            case "miner" -> RoomType.MINE_HEAD;
            case "teodor" -> RoomType.STUDY;
            case "mira" -> RoomType.LOOKOUT;
            case "sun" -> RoomType.SANCTUARY;
            default -> null;
        };
        if (t != null) for (Room r : h.rooms) if (r.types().contains(t)) return r.centre();
        if ((p.role().equals("hettie") || p.role().equals("farmer")) && !h.yard.fields().isEmpty()) return h.yard.fields().getFirst();
        return h.pos;
    }

    static Bedroom nearestTo(List<Bedroom> rooms, BlockPos at) {
        Bedroom best = rooms.getFirst();
        int bestGrade = best.room.reading().grade().ordinal();
        double bd = Double.MAX_VALUE;
        for (Bedroom b : rooms) {
            // the best grade first; among those, nearest their work
            int g = b.room.reading().grade().ordinal();
            double d = b.bed.distSqr(at);
            if (g > bestGrade || g == bestGrade && d < bd) {
                best = b;
                bestGrade = g;
                bd = d;
            }
        }
        return best;
    }

    // ---- what the home says about them ------------------------------------------------------------------------------

    /** A room's name: "Ilse's room" once someone lives in it, else the Hearth's reading ("the snug timber bedroom"). */
    public static String roomName(HearthRecord h, Room r) {
        for (Folk.Settler s : h.folk.settlers) {
            if (s.bed != null && s.home() && r.contains(s.bed) && r.types().contains(RoomType.BEDROOM)) return s.who.given() + "'s room";
        }
        return r.reading().name();
    }

    /** Someone known who isn't here yet, and why ("waits for a bed", "needs a Greenhouse", "Tier II"). */
    public record Waiting(Folk.Person who, String why, String line) {}

    public static List<Waiting> waiting(ServerLevel level, HearthRecord h) {
        List<Waiting> out = new ArrayList<>();
        Folk f = h.folk;
        boolean full = f.settlers.size() >= cap(h.tier);
        boolean beds = !spare(level, h).isEmpty();
        for (Folk.Person p : f.known.values()) {
            if (f.settler(p.id()) != null) continue;
            Folk.Person who = settled(h, p);
            String need = need(h, who);
            String why, line;
            if (full) {
                int t = tierFor(f.settlers.size());
                why = t < 0 ? "no room for more" : "Tier " + dev.awaken.hearth.Upgrades.roman(t);
                line = who.given() + " would come at Tier " + (t < 0 ? "V" : dev.awaken.hearth.Upgrades.roman(t));
            } else if (!need.isEmpty()) {
                why = "needs " + need;
                line = who.given() + " will come when there's " + need;
            } else if (!beds) {
                why = "waits for a bed";
                line = who.given() + " will come when there's a bed for " + them(who);
            } else {
                why = "on the way";
                line = who.given() + " is on the way";
            }
            out.add(new Waiting(who, why, line));
        }
        return out;
    }

    /** The household's online players get the news (a notice, or a toast). */
    static void tell(ServerLevel level, HearthRecord h, SignalWindow w) {
        for (ServerPlayer p : Household.online(level.getServer(), h)) {
            Signal.send(p, w);
            HearthViews.refresh(p);
            dev.awaken.hearth.guide.Guide.refresh(p);
        }
    }

    /** A member woke in a bed at home: that bed is theirs (never a settler's, which is only borrowed). */
    public static void woke(ServerPlayer sp, HearthRecord h, BlockPos bed) {
        for (Folk.Settler s : h.folk.settlers) if (bed.equals(s.bed)) return;
        if (!bed.equals(h.folk.ownBeds.get(sp.getUUID()))) {
            h.folk.ownBeds.put(sp.getUUID(), bed.immutable());
            HearthService.dirty(sp.level().getServer());
        }
    }

    static long today(MinecraftServer server) {
        return PlayDays.current(server);
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
