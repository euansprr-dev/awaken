package dev.awaken.bells;

import dev.awaken.Awaken;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.CityHooks;
import dev.awaken.core.api.Destinations;
import dev.awaken.delve.DelveContent;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.hearth.room.Reading;
import dev.awaken.hearth.room.Room;
import dev.awaken.kin.KinWorld;
import dev.awaken.progression.Counters;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The hundred bells (SPEC §43.7), the public face: who holds which bell, ringing one home, the leads that file
 * themselves in the Bell Book, and each home's voices. A bell comes from a moment (your Ember, your first beast at home,
 * a Grand room, a Proving, a raid held, a gate world's Lord, the Deep, a dream, a spirit) or from a sleeping place
 * woken ({@link BellSites}). Every bell held by anyone who lives at a home hangs in its tower and sings in its song.
 */
public final class Bells {
    private Bells() {}

    /** Counter: how many bells this player holds (for other systems' goals). */
    public static final String COUNTER = "awaken:bells";
    /** The Signal's one line, said once, a few seconds after the first bell rings. */
    static final String SIGNAL_ONCE = "There were a hundred. Find them. Ring them all and I can show you the bottom.";

    public static BellBook book(ServerPlayer p) {
        return p.getData(BellContent.BOOK.get());
    }

    static void save(ServerPlayer p, BellBook b) {
        p.setData(BellContent.BOOK.get(), b);
    }

    public static boolean has(ServerPlayer p, String id) {
        return book(p).has(id);
    }

    public static int count(ServerPlayer p) {
        return book(p).count();
    }

    // ---- ringing a bell home --------------------------------------------------------------------------------------------

    /** Rings bell {@code id} home for this player (once). {@code where}: how the Book remembers it ("by your fire"). */
    public static boolean ring(ServerPlayer p, String id, String where) {
        BellCatalog.Def d = BellCatalog.of(id);
        return d != null && ring(p, d, where);
    }

    static boolean ring(ServerPlayer p, BellCatalog.Def d, String where) {
        BellBook b = book(p);
        if (b.held.containsKey(d.no())) return false;
        MinecraftServer server = p.level().getServer();
        b.held.put(d.no(), new BellBook.Held(PlayDays.current(server), where));
        b.leads.remove(d.no());
        if (b.tracked == d.no()) {
            b.tracked = -1;
            Destinations.get(p).filter(t -> t.kind().equals("bell")).ifPresent(t -> Destinations.clear(p));
        }
        boolean first = !b.given;
        b.given = true;
        save(p, b);
        Progression.setCounter(p, COUNTER, b.count());
        p.level().playSound(null, p.blockPosition(), BellContent.note(d.no()), SoundSource.PLAYERS, 2F, 1F);
        Optional<HearthRecord> home = HearthService.data(server).home(p.getUUID());
        home.ifPresent(h -> BellTower.hang(server, h));
        StringBuilder body = new StringBuilder(d.rank().label + "-rank. ");
        body.append(home.isPresent() ? "It's gone home to your tower." : "It'll hang in your tower once you have a home.");
        body.append(' ').append(d.voice().words);
        for (BellCatalog.Peal pl : BellCatalog.PEALS)
            if (pl.bells().contains(d.id()) && pl.complete(b::has))
                body.append(" With it you can ring the peal ").append(pl.name()).append(" from your handbell: ").append(pl.words().toLowerCase(java.util.Locale.ROOT));
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Bell " + d.number() + " · " + d.name()))
                .body(Component.literal(body.toString())).priority(7).ttl(20 * 12).build());
        if (first) {
            give(p, new ItemStack(BellContent.HANDBELL.get()));
            give(p, new ItemStack(BellContent.BELL_FRAME_ITEM.get()));
            ONCE.put(p.getUUID(), p.level().getGameTime() + 20 * 6);
        }
        Awaken.LOG.info("[bells] {} rang {} {} home ({})", p.getName().getString(), d.number(), d.id(), where);
        return true;
    }

    private static void give(ServerPlayer p, ItemStack s) {
        if (!p.getInventory().add(s)) p.drop(s, false, net.minecraft.util.Prediction.SERVER_ONLY);
    }

    /** Players owed the Signal's one line, and when. Not saved: a restart in those six seconds skips it, harmlessly. */
    private static final Map<UUID, Long> ONCE = new HashMap<>();

    static void say(ServerPlayer p, String line) {
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(line)).topic("bells").ttl(20 * 6).build());
    }

    // ---- leads ------------------------------------------------------------------------------------------------------

    /** A sleeping bell's hum reached this player from {@code bell}: a Heard lead, its two bearings crossed into an area. */
    static void heard(ServerPlayer p, BellCatalog.Def d, BlockPos bell) {
        heard(p, d, bell, "A");
    }

    /** A hum heard: {@code a} starts the line ("A", or "Heard in spirit: a" for the Veil's bells). */
    static void heard(ServerPlayer p, BellCatalog.Def d, BlockPos bell, String a) {
        BellBook b = book(p);
        BellBook.Lead l = b.leads.get(d.no());
        boolean fresh = l == null;
        if (fresh) {
            l = new BellBook.Lead();
            l.since = PlayDays.current(p.level().getServer());
            b.leads.put(d.no(), l);
        }
        double dx = bell.getX() - p.getX(), dz = bell.getZ() - p.getZ();
        if (!l.has(BellBook.How.HEARD)) {
            l.hows |= 1 << BellBook.How.HEARD.ordinal();
            l.bearings = 1;
            l.hx = p.getX();
            l.hz = p.getZ();
            l.lines.add(a + " bell's note, faint, " + toward(dx, dz) + ". Your song is missing this note.");
            say(p, "[" + a + " bell's note, faint, " + toward(dx, dz) + ". It's in your Bell Book.]");
        } else if (l.bearings == 1 && Math.hypot(p.getX() - l.hx, p.getZ() - l.hz) >= 32
                && Math.abs(angle(bell.getX() - l.hx, bell.getZ() - l.hz) - angle(dx, dz)) >= 0.25) {
            // the second bearing from somewhere else: the System crosses them and shades the area round the crossing
            l.bearings = 2;
            RandomOffset o = new RandomOffset(bell.asLong());
            l.tx = bell.getX() + o.x;
            l.tz = bell.getZ() + o.z;
            l.lines.add("Heard again from another side. The two bearings cross: it's somewhere in that stretch.");
            say(p, "[You heard it again from here. The Book has crossed the two bearings.]");
        } else return;
        save(p, b);
        if (b.tracked == d.no()) track(p, d.no());
    }

    /** Where the shaded area's middle sits from the real bell: up to 20 blocks off, always the same for one bell. */
    private record RandomOffset(int x, int z) {
        RandomOffset(long seed) {
            this((int) (Math.floorMod(seed * 0x9E3779B97F4A7C15L >>> 20, 41) - 20), (int) (Math.floorMod(seed * 0xC2B2AE3D27D4EB4FL >>> 20, 41) - 20));
        }
    }

    /** A sleeping place seen up close. */
    static void seen(ServerPlayer p, BellCatalog.Def d) {
        seen(p, d, "This place is asleep. Something here is keeping it that way.");
    }

    /** A sleeping place seen (or, for a spirit bell, felt) up close, in the System's words. */
    static void seen(ServerPlayer p, BellCatalog.Def d, String line) {
        BellBook b = book(p);
        BellBook.Lead l = b.leads.computeIfAbsent(d.no(), k -> {
            BellBook.Lead n = new BellBook.Lead();
            n.since = PlayDays.current(p.level().getServer());
            return n;
        });
        if (l.has(BellBook.How.SEEN)) return;
        l.hows |= 1 << BellBook.How.SEEN.ordinal();
        l.lines.add(line);
        save(p, b);
        say(p, "[" + line + "]");
    }

    /** How far a Rookery's wrens and a Snufflet's letters reach for a sleeping bell. */
    static final int SUNG_REACH = 1200, FOUND_REACH = 800;

    /**
     * A Chimewren came home to the Rookery singing a note this player's song is missing (SPEC §43.12): a Sung lead, one
     * bearing from home toward the nearest sleeping bell they don't hold. A direction, never the place. False when none.
     */
    public static boolean sung(ServerPlayer p, BlockPos home) {
        Map.Entry<BellCatalog.Def, BellWorld.Site> s = sleeping(p, home, SUNG_REACH, BellBook.How.SUNG);
        if (s == null) return false;
        BellBook b = book(p);
        BellBook.Lead l = lead(p, b, s.getKey());
        l.hows |= 1 << BellBook.How.SUNG.ordinal();
        String way = toward(s.getValue().pos.getX() - home.getX(), s.getValue().pos.getZ() - home.getZ()).substring(3);
        if (l.bearings == 0) {
            l.bearings = 1;
            l.hx = home.getX();
            l.hz = home.getZ();
        }
        l.lines.add("Your Chimewrens came home singing a note your song is missing. They flew in from " + way + ".");
        save(p, b);
        say(p, "[Your Chimewrens came home singing a note you're missing. They flew in from " + way + ". It's in your Bell Book.]");
        return true;
    }

    /**
     * An old letter dug up where a Snufflet was snuffling (SPEC §43.12): a Found lead that names a stretch of land round
     * the nearest sleeping bell within 800 blocks the player doesn't hold. False when there's none to tell of.
     */
    public static boolean found(ServerPlayer p, BlockPos at) {
        Map.Entry<BellCatalog.Def, BellWorld.Site> s = sleeping(p, at, FOUND_REACH, BellBook.How.FOUND);
        if (s == null) return false;
        BellBook b = book(p);
        BellBook.Lead l = lead(p, b, s.getKey());
        l.hows |= 1 << BellBook.How.FOUND.ordinal();
        BlockPos bell = s.getValue().pos;
        RandomOffset o = new RandomOffset(bell.asLong());
        l.bearings = 2;
        l.tx = bell.getX() + o.x;
        l.tz = bell.getZ() + o.z;
        String way = toward(bell.getX() - at.getX(), bell.getZ() - at.getZ()).substring(3);
        long far = Math.max(50, Math.round(Math.hypot(bell.getX() - at.getX(), bell.getZ() - at.getZ()) / 50.0) * 50);
        l.lines.add("An old letter dug out of the ground. It says the bell was kept " + way + " of where it was buried, " + far
                + " paces or so. The Book has marked the stretch it means.");
        save(p, b);
        if (b.tracked < 0) track(p, s.getKey().no());
        say(p, "[An old letter, half rotted. It tells of a bell kept " + way + " of here. It's in your Bell Book.]");
        return true;
    }

    private static BellBook.Lead lead(ServerPlayer p, BellBook b, BellCatalog.Def d) {
        return b.leads.computeIfAbsent(d.no(), k -> {
            BellBook.Lead n = new BellBook.Lead();
            n.since = PlayDays.current(p.level().getServer());
            return n;
        });
    }

    /** The nearest place still asleep within {@code r} whose bell this player doesn't hold and has no lead of this sort to. */
    private static Map.@Nullable Entry<BellCatalog.Def, BellWorld.Site> sleeping(ServerPlayer p, BlockPos at, int r, BellBook.How how) {
        BellBook b = book(p);
        ServerLevel level = p.level().getServer().overworld();
        Map.Entry<BellCatalog.Def, BellWorld.Site> best = null;
        double bd = Double.MAX_VALUE;
        for (BellWorld.Site s : BellSites.near(level, at, r).values()) {
            if (s.state == BellWorld.Site.AWAKE || s.state == BellWorld.Site.GONE) continue;
            BellCatalog.Def d = BellCatalog.of(s.bell);
            if (d == null || b.held.containsKey(d.no())) continue;
            BellBook.Lead l = b.leads.get(d.no());
            if (l != null && l.has(how)) continue;
            double dd = Math.hypot(s.pos.getX() - at.getX(), s.pos.getZ() - at.getZ());
            if (dd < bd) {
                bd = dd;
                best = Map.entry(d, s);
            }
        }
        return best;
    }

    /** The holdfast's bells this player hasn't rung yet, in order. A holdfast's bell is awake already. */
    private static final String[] HOLDFAST_BELLS = {"watch", "curfew"};

    /** Standing inside a holdfast's walls: its bell is filed as Rung, the next holdfast bell not held. */
    public static void holdfastHeard(ServerPlayer p, String key, BlockPos bell) {
        BellBook b = book(p);
        for (String id : HOLDFAST_BELLS) {
            BellCatalog.Def d = BellCatalog.of(id);
            if (d == null || b.held.containsKey(d.no())) continue;
            BellBook.Lead l = b.leads.computeIfAbsent(d.no(), k -> new BellBook.Lead());
            if (l.has(BellBook.How.RUNG)) return;
            l.hows |= 1 << BellBook.How.RUNG.ordinal();
            l.since = PlayDays.current(p.level().getServer());
            l.bearings = 2;
            l.tx = bell.getX();
            l.tz = bell.getZ();
            l.lines.add("This holdfast's bell is awake already. Ring it yourself and its note goes home with you.");
            save(p, b);
            say(p, "[The holdfast's bell is awake. Ring it yourself and its note goes home with you.]");
            return;
        }
    }

    /** A player rang a holdfast's bell from inside its walls: the next holdfast bell goes home with them. */
    static void holdfastStruck(ServerPlayer p, String key) {
        for (String id : HOLDFAST_BELLS) if (!has(p, id)) {
            ring(p, id, "rung yourself at a holdfast");
            return;
        }
    }

    /** Track a lead: the top-right card's rough bearing to its area (or along its one bearing). */
    static void track(ServerPlayer p, int no) {
        BellBook b = book(p);
        BellBook.Lead l = b.leads.get(no);
        if (l == null || l.bearings == 0) return;
        b.tracked = no;
        save(p, b);
        BellCatalog.Def d = BellCatalog.of(no);
        BlockPos at;
        if (l.bearings == 2) at = BlockPos.containing(l.tx, 64, l.tz);
        else {
            // one bearing: it's within a hum's reach along it
            at = BlockPos.containing(l.hx, 64, l.hz);
        }
        Destinations.set(p, new Destinations.Target(title(d, l), Level.OVERWORLD.identifier(), at, "bell"));
    }

    /** "The Barrow Bell?" once it's been seen or narrowed down, else "A bell's note". */
    static String title(BellCatalog.Def d, BellBook.Lead l) {
        return l.has(BellBook.How.SEEN) || l.has(BellBook.How.RUNG) || l.bearings == 2 ? d.name() + "?" : "A bell's note";
    }

    static double angle(double dx, double dz) {
        return Math.atan2(dx, -dz);
    }

    /** "to the north-east": the way from a player toward something (north is -z). */
    static String toward(double dx, double dz) {
        int k = (int) Math.floorMod(Math.round(Math.toDegrees(angle(dx, dz)) / 45.0), 8L);
        return new String[]{"to the north", "to the north-east", "to the east", "to the south-east", "to the south", "to the south-west",
                "to the west", "to the north-west"}[k];
    }

    // ---- moments that ring a bell ----------------------------------------------------------------------------------------

    /** Gate worlds' bells by theme, each at the rank its world's bell asks for. */
    private static final Map<String, String> GATE_BELLS = Map.ofEntries(Map.entry("BELL_CRYPT", "crypt"), Map.entry("FUNGAL_HOLLOW", "spore"),
            Map.entry("EMBER_MINES", "ember_mine"), Map.entry("HIVE", "hive"), Map.entry("OVERGROWN_TEMPLE", "root"),
            Map.entry("LANTERN_NIGHT_MARKET", "lantern"), Map.entry("DROWNED_ARCHIVE", "archive"), Map.entry("CLOCKWORK_FOUNDRY", "gear"),
            Map.entry("GLASS_DESERT", "glass"), Map.entry("FROST_MONASTERY", "frost"), Map.entry("MIRROR_PALACE", "mirror"),
            Map.entry("PRECURSOR_LAB", "counting"), Map.entry("NIGHTMARE", "sleeper"), Map.entry("SKY_ARCHIPELAGO", "sky"),
            Map.entry("ABYSSAL_SHRINE", "herald"));

    /** The Court's eight bells, one in each Sovereign's domain (by the Sovereign's name after "sovereign_"). */
    private static final Map<String, String> COURT_BELLS = Map.of("hunger", "hunger", "silence", "silence", "sleep", "sorrow",
            "machines", "throne", "mirrors", "oath", "storms", "crown", "roots", "grief", "ash", "ash");

    /** A Sovereign set down what it carried: its domain's bell goes home with everyone within 96 who saw it, if a player won it. */
    static void sovereignFell(ServerLevel level, LivingEntity s) {
        String boss = s.hasData(dev.awaken.core.api.CoreApi.MOB_ROLE.get()) ? s.getData(dev.awaken.core.api.CoreApi.MOB_ROLE.get()).bossId().orElse("") : "";
        int at = boss.indexOf("sovereign_");
        String id = at < 0 ? null : COURT_BELLS.get(boss.substring(at + 10));
        if (id == null || !(s.getKillCredit() instanceof ServerPlayer)) return;
        String where = "where the " + s.getName().getString() + " set down what it carried";
        for (ServerPlayer p : level.players()) if (p.distanceToSqr(s) < 96 * 96) ring(p, id, where);
    }

    /** The Descent's bells by stratum (1 to 10), with where the Book says each was rung. Two and three are stage 4's. */
    private static final String[][] DESCENT_BELLS = {null, {"shallows", "at the Rim"}, {"garden", "down in the Hanging Gardens"},
            {"drowned_choir", "down in the Drowned Choir"}, {"bone", "in the Bone Orchard"}, {"echo", "in the Silent Halls"},
            {"furnace", "in the Precursor Foundry"}, {"black_water", "under the Inverted Sea"}, {"weight", "in the Dreaming Stratum"},
            null, {"deepest", "at the bottom of the world, by the Seed"}};

    /** Carillon's own bells: where each rings for you, and what people in the city say about it (a Remembered lead). */
    private record CityBell(String id, String spot, int reach, String remembered) {}

    private static final List<CityBell> CITY_BELLS = List.of(
            new CityBell("roost", "roost", 16, "The Runners say the Roost rings its bell for every Runner who comes back to it at C rank."),
            new CityBell("square", "square", 20, "The Choir rings Choir Square's bell for anyone whose tower holds twenty bells. Stand in the square."),
            new CityBell("market", "market", 0, "The traders say the market's bell rings for a B-rank Hunter who walks the market again."),
            new CityBell("harbour", "quay", 0, "The dockers say the Quay's bell rings for anyone whose tower holds fifty bells. Walk the Quay."));

    private static boolean cityBellDue(ServerPlayer p, String id) {
        return switch (id) {
            case "roost" -> Progression.rank(p).ordinal() >= Rank.C.ordinal();
            case "square" -> count(p) >= 20;
            case "market" -> Progression.rank(p).ordinal() >= Rank.B.ordinal();
            case "harbour" -> count(p) >= 50;
            default -> false;
        };
    }

    /** In Carillon: a city bell that's due rings where it hangs, and one that's due elsewhere is remembered aloud. */
    private static void city(ServerPlayer p) {
        ServerLevel level = p.level();
        if (!CityHooks.inCity(level, p.blockPosition())) return;
        for (CityBell c : CITY_BELLS) {
            if (has(p, c.id()) || !cityBellDue(p, c.id())) continue;
            BlockPos at = c.spot().equals("roost") ? CityHooks.startPoint(p) : c.spot().equals("square") ? CityHooks.square(level) : null;
            boolean here = at != null ? p.distanceToSqr(Vec3.atCenterOf(at)) < c.reach() * c.reach() : c.spot().equals(CityHooks.district(level, p.blockPosition()));
            if (here) {
                ring(p, c.id(), "rung for you in Carillon");
                continue;
            }
            remembered(p, BellCatalog.of(c.id()), at, c.remembered());
        }
        // the homes people left: once a play-day, someone in the city remembers the bell of one that's still asleep
        BellBook b = book(p);
        long day = PlayDays.current(level.getServer());
        if (b.rememberedDay == day) return;
        Map.Entry<BellCatalog.Def, BellWorld.Site> s = sleepingPeople(p);
        if (s == null) return;
        b.rememberedDay = day;
        save(p, b);
        String way = toward(s.getValue().pos.getX() - p.getX(), s.getValue().pos.getZ() - p.getZ());
        RandomOffset o = new RandomOffset(s.getValue().pos.asLong());
        remembered(p, s.getKey(), new BlockPos(s.getValue().pos.getX() + o.x, 64, s.getValue().pos.getZ() + o.z),
                PEOPLE_LINES.getOrDefault(s.getKey().id(), "Someone remembers a bell at the home they left") + ", " + way + ".");
    }

    /** What people in the city say about the bells of the homes they left. */
    private static final Map<String, String> PEOPLE_LINES = Map.of(
            "fenwick", "An old woman in the square says Fenwick had a bell, by the lake",
            "mill", "A carter says the mill he grew up at had a bell under the floor",
            "shepherd", "A drover says the shepherds' bell still hangs where the flocks summered, and something sits with it",
            "hill", "A woman on the terrace says her village's bell hung on the hill above it",
            "ford", "A ferryman says there was a bell at the ford, and it went into the river when the water rose");

    /** The nearest place still asleep, of the homes people left, whose bell this player has no Remembered lead to. */
    private static Map.@Nullable Entry<BellCatalog.Def, BellWorld.Site> sleepingPeople(ServerPlayer p) {
        BellBook b = book(p);
        ServerLevel level = p.level().getServer().overworld();
        Map.Entry<BellCatalog.Def, BellWorld.Site> best = null;
        double bd = Double.MAX_VALUE;
        for (BellWorld.Site s : BellSites.near(level, p.blockPosition(), REMEMBERED_REACH).values()) {
            BellCatalog.Def d = BellCatalog.of(s.bell);
            if (d == null || d.source() != BellCatalog.Source.PEOPLE || s.state == BellWorld.Site.AWAKE || s.state == BellWorld.Site.GONE
                    || b.held.containsKey(d.no())) continue;
            BellBook.Lead l = b.leads.get(d.no());
            if (l != null && l.has(BellBook.How.REMEMBERED)) continue;
            double dd = Math.hypot(s.pos.getX() - p.getX(), s.pos.getZ() - p.getZ());
            if (dd < bd) {
                bd = dd;
                best = Map.entry(d, s);
            }
        }
        return best;
    }

    /** How far from the city people remember: the C and B bands' homes. */
    static final int REMEMBERED_REACH = 7200;

    /** A Dreamt lead: a dream bell rang and got away. */
    static void dreamt(ServerPlayer p, BellCatalog.Def d, String line) {
        BellBook b = book(p);
        BellBook.Lead l = lead(p, b, d);
        if (!l.has(BellBook.How.DREAMT)) {
            l.hows |= 1 << BellBook.How.DREAMT.ordinal();
            l.lines.add(line);
            save(p, b);
        }
        say(p, "[" + line + "]");
    }

    /** A Remembered lead: what someone said, and the area it points at if it points anywhere. */
    private static void remembered(ServerPlayer p, BellCatalog.Def d, @Nullable BlockPos at, String line) {
        BellBook b = book(p);
        BellBook.Lead l = lead(p, b, d);
        if (l.has(BellBook.How.REMEMBERED)) return;
        l.hows |= 1 << BellBook.How.REMEMBERED.ordinal();
        if (at != null && l.bearings < 2) {
            l.bearings = 2;
            l.tx = at.getX();
            l.tz = at.getZ();
        }
        l.lines.add(line);
        save(p, b);
        say(p, "[" + line + " It's in your Bell Book.]");
    }

    /** A gate's Lord fell: its world's bell goes home with everyone in the run, if the gate was at its bell's rank. */
    public static void lordFell(ServerLevel level, LivingEntity lord, Rank rank, String theme, Collection<UUID> party, @Nullable ServerPlayer killer) {
        String id = GATE_BELLS.get(theme);
        BellCatalog.Def d = id == null ? null : BellCatalog.of(id);
        if (d == null || rank.ordinal() < d.rank().ordinal()) return;
        for (ServerPlayer p : level.players())
            if ((p == killer || party.contains(p.getUUID())) && p.distanceToSqr(lord) < 96 * 96) ring(p, d, "at a " + Rank.valueOf(rank.name()).label + " gate's Lord");
    }

    /** Checks the moments that are states rather than events, for one player. */
    private static void moments(ServerPlayer p) {
        MinecraftServer server = p.level().getServer();
        Optional<HearthRecord> home = HearthService.data(server).home(p.getUUID());
        if (home.isPresent()) {
            HearthRecord h = home.get();
            ring(p, "ember", "by your fire, the night you lit it");
            if (!KinWorld.get(server).at(h.pos).isEmpty()) ring(p, "nook", "the day a beast came to live with you");
            for (Room r : h.rooms)
                if (r.reading().grade() == Reading.Grade.GRAND) {
                    ring(p, "lintel", "over the door of " + r.reading().name());
                    break;
                }
            if (h.tier >= 2) ring(p, "founding", "the day your Hearth grew");
            if (p.level() instanceof ServerLevel sl && sl.dimension() == Level.OVERWORLD) BellSites.planHome(sl, h.pos);
        }
        var prof = Progression.profile(p);
        if (prof.counter(Counters.GATES_CLEARED) >= 1) ring(p, "registry", "rung by the Registry for your first gate");
        Rank rank = Progression.rank(p);
        if (rank.ordinal() >= Rank.D.ordinal()) ring(p, "tenor", "the Great Bell from your D-rank Proving");
        if (rank.ordinal() >= Rank.C.ordinal()) ring(p, "second", "the Great Bell from your C-rank Proving");
        int deepest = dev.awaken.descent.DescentSync.data(p).deepest;
        if (deepest >= 2) ring(p, "garden", "down in the Hanging Gardens");
        if (deepest >= 3) ring(p, "drowned_choir", "down in the Drowned Choir");
        if (prof.counter(Counters.LUCID_DREAMS) >= 1) ring(p, "dreaming", "carried out of your first lucid dream");
        if (prof.counter(Counters.SPIRITS_SPOKEN) >= 1) ring(p, "spirit", "given by the first spirit you spoke to");
        // stage 6: the later Provings' Great Bells, the Registry's twenty-fifth gate, the Descent's strata, Deepglass in hand
        if (rank.ordinal() >= Rank.B.ordinal()) ring(p, "third", "the Great Bell from your B-rank Proving");
        if (rank.ordinal() >= Rank.A.ordinal()) ring(p, "fourth", "the Great Bell from your A-rank Proving");
        if (rank.ordinal() >= Rank.S.ordinal()) ring(p, "fifth", "the Great Bell from your S-rank Proving");
        if (rank == Rank.SOVEREIGN) ring(p, "bourdon", "the Great Bell from your last Proving");
        if (prof.counter(Counters.GATES_CLEARED) >= 25) ring(p, "toll", "rung by the Registry for your twenty-fifth gate");
        for (int s = 1; s < DESCENT_BELLS.length && s <= deepest; s++)
            if (DESCENT_BELLS[s] != null) ring(p, DESCENT_BELLS[s][0], DESCENT_BELLS[s][1]);
        if (!has(p, "crystal"))
            for (int i = 0; i < p.getInventory().getContainerSize(); i++)
                if (p.getInventory().getItem(i).is(DelveContent.DEEPGLASS_SHARD.get())) {
                    ring(p, "crystal", "the first time you held Deepglass");
                    break;
                }
        // the Above: a Tether Spire and a sky platform scanned, the Halo reached, the Halo at S rank, a storm on the wing
        for (var m : dev.awaken.above.Above.data(p).ruins()) {
            if (m.kind().equals("tether_spire")) ring(p, "wind", "in the wind at a Tether Spire");
            if (m.kind().equals("sky_platform")) ring(p, "island", "on a sky platform");
        }
        if (prof.counter(dev.awaken.above.Above.HALO_REACHED) >= 1) ring(p, "halo", "aboard the Halo");
        if (rank.ordinal() >= Rank.S.ordinal() && dev.awaken.above.halo.Halo.in(p)) ring(p, "star", "under the stars at the Halo");
        if (p.level().dimension() == Level.OVERWORLD && p.level().isThundering() && p.getY() > 160 && !p.onGround() && !p.isInWater())
            ring(p, "storm", "in the sky, in a storm");
        city(p);
    }

    // ---- voices -----------------------------------------------------------------------------------------------------

    /** How many bells hanging at the home whose Anchor is {@code anchor} have voice {@code v}. */
    public static int voice(ServerLevel level, BlockPos anchor, BellCatalog.Voice v) {
        BellWorld.Home h = BellWorld.get(level.getServer()).peek(anchor);
        if (h == null) return 0;
        int n = 0;
        for (int no : h.hung) if (BellCatalog.of(no).voice() == v) n++;
        return n;
    }

    /** The same for a player's own home (0 with no home). */
    public static int voice(ServerPlayer p, BellCatalog.Voice v) {
        if (!(p.level() instanceof ServerLevel level)) return 0;
        Optional<HearthRecord> h = HearthService.data(level.getServer()).home(p.getUUID());
        return h.map(r -> voice(level, r.pos, v)).orElse(0);
    }

    /** How many of the bells at the Hearth this player stands in (their own) have voice {@code v}; 0 outside it. */
    public static int voiceHere(ServerPlayer p, BellCatalog.Voice v) {
        if (!(p.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return 0;
        Optional<HearthRecord> h = HearthService.data(level.getServer()).home(p.getUUID());
        if (h.isEmpty() || p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(h.get().pos)) > (double) h.get().radius() * h.get().radius()) return 0;
        return voice(level, h.get().pos, v);
    }

    /** The voices a body feels inside its own Hearth: Rest, Stride, and stage 6's Sight, Breath, Haste, Warmth, Might, Guard. */
    private static void body(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        Optional<HearthRecord> h = HearthService.data(level.getServer()).home(p.getUUID());
        if (h.isEmpty() || p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(h.get().pos)) > (double) h.get().radius() * h.get().radius()) return;
        int[] n = new int[BellCatalog.Voice.values().length];
        BellWorld.Home home = BellWorld.get(level.getServer()).peek(h.get().pos);
        if (home == null) return;
        for (int no : home.hung) n[BellCatalog.of(no).voice().ordinal()]++;
        int rest = n[BellCatalog.Voice.REST.ordinal()], stride = n[BellCatalog.Voice.STRIDE.ordinal()];
        if (rest > 0 && p.tickCount - p.getLastHurtByMobTimestamp() > 100 && p.getHealth() < p.getMaxHealth())
            feel(p, MobEffects.REGENERATION, rest);
        if (stride > 0 && !p.isPassenger() && !p.isFallFlying()) feel(p, MobEffects.SPEED, stride);
        if (n[BellCatalog.Voice.SIGHT.ordinal()] > 0) p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 320, 0, true, false, true));
        if (n[BellCatalog.Voice.BREATH.ordinal()] > 0 && p.isUnderWater()) p.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 110, 0, true, false, true));
        if (n[BellCatalog.Voice.HASTE.ordinal()] > 0) feel(p, MobEffects.HASTE, n[BellCatalog.Voice.HASTE.ordinal()]);
        if (n[BellCatalog.Voice.WARMTH.ordinal()] > 0) p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 110, 0, true, false, true));
        if (n[BellCatalog.Voice.MIGHT.ordinal()] > 0) feel(p, MobEffects.STRENGTH, n[BellCatalog.Voice.MIGHT.ordinal()]);
        if (n[BellCatalog.Voice.GUARD.ordinal()] > 0) feel(p, MobEffects.RESISTANCE, n[BellCatalog.Voice.GUARD.ordinal()]);
    }

    /** One voice's effect: level I with a bell of it, II from three. */
    private static void feel(ServerPlayer p, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> e, int bells) {
        p.addEffect(new MobEffectInstance(e, 110, bells >= 3 ? 1 : 0, true, false, true));
    }

    // ---- ticking ----------------------------------------------------------------------------------------------------

    static void tick(MinecraftServer server) {
        ServerLevel ow = server.overworld();
        BellSites.tickPaint(ow);
        BellSites.tickSpirits(ow);
        DreamBells.tick(server);
        BellTower.tick(server);
        Peals.tick(server);
        long now = ow.getGameTime();
        if (!ONCE.isEmpty()) ONCE.entrySet().removeIf(e -> {
            if (e.getValue() > now) return false;
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(SIGNAL_ONCE)).priority(8).ttl(20 * 10).build());
            return true;
        });
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if ((p.tickCount + p.getId()) % 100 != 0) continue;
            moments(p);
            BellSites.tick(p);
            body(p);
        }
    }
}
