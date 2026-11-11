package dev.awaken.bells;

import dev.awaken.Awaken;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.CitizenHooks;
import dev.awaken.core.api.CityHooks;
import dev.awaken.core.api.Destinations;
import dev.awaken.delve.DelveContent;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The hundred bells (SPEC §43.7, §43.16), the public face: who holds which bell, ringing one home, the leads that file
 * themselves in the Bell Book, and each home's voices. No bell lies in the open: each comes from somewhere a story puts
 * it (Ilse's Ember, the Lampwing's old roost, a gate's Lord that kept one the gate swallowed, a Proving, a raid held, the
 * Deep, a dream, a spirit), and you carry it home and hang it (SPEC §43.16). A home's song is its hung bells and the six
 * people still ring that anyone living there has heard.
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
        return ring(p, d, where, null);
    }

    /** The six bells people still ring (Carillon's four, the holdfasts' two): they stay where they hang and answer your song. */
    static final java.util.Set<String> FAR = java.util.Set.of("roost", "square", "market", "harbour", "watch", "curfew");

    static boolean far(BellCatalog.Def d) {
        return FAR.contains(d.id());
    }

    /**
     * Bell {@code d} becomes this player's (once): filed in the Book, and either put in their hands to carry home and hang
     * (SPEC §43.16, stage 2), or, for one of the six people still ring, kept where it hangs ({@code hangs}, else here) to
     * answer their home's song. Nothing plays at their feet.
     */
    static boolean ring(ServerPlayer p, BellCatalog.Def d, String where, @Nullable BlockPos hangs) {
        return ring(p, d, where, hangs, false);
    }

    /** As above; {@code byWagon}: a bell you'd have been handed goes on the Registry's wagon to your yard instead (SPEC §67). */
    static boolean ring(ServerPlayer p, BellCatalog.Def d, String where, @Nullable BlockPos hangs, boolean byWagon) {
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
        boolean isFar = far(d), seed = d.source() == BellCatalog.Source.SEED, proving = d.source() == BellCatalog.Source.PROVING;
        boolean wagon = proving || byWagon && !isFar && !seed;
        if (isFar) b.far.put(d.no(), (hangs != null ? hangs : p.blockPosition()).asLong());
        // a Proving's Great Bell: the Registry's kept it in its cellar, and its wagon brings it to your yard the next day
        if (wagon) b.wagon.put(d.no(), PlayDays.current(server));
        save(p, b);
        Progression.setCounter(p, COUNTER, b.count());
        Optional<HearthRecord> home = HearthService.data(server).home(p.getUUID());
        if (isFar) home.ifPresent(h -> BellTower.hang(server, h));
        else if (!seed && !wagon) giveBack(p, BellItem.of(d.no()));
        StringBuilder body = new StringBuilder(d.rank().label + "-rank. ");
        body.append(isFar ? "People still ring it. It stays where it hangs and answers your song from there."
                : seed ? "It hangs in the middle of your Great Belfry."
                : proving ? "The Registry has kept it in its cellar for eleven years. Its wagon will leave it in your yard by morning"
                        + (home.isPresent() ? "." : ", once you have a home.") + " It needs a Great Beam, 12 or more blocks above your fire."
                : wagon ? "The Registry's wagon will leave it in your yard by morning" + (home.isPresent() ? "." : ", once you have a home.") + " Hang it by your fire."
                : "It's yours. Take it home and hang it.");
        if (!d.voice().words.isEmpty()) body.append(" While it hangs: ").append(d.voice().words.substring(0, 1).toLowerCase(java.util.Locale.ROOT)).append(d.voice().words.substring(1));
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Bell " + d.number() + " · " + d.name()))
                .body(Component.literal(body.toString())).priority(7).ttl(20 * 12).build());
        if (first) {
            give(p, new ItemStack(BellContent.HANDBELL.get()));
            b.once("frame_told");
            save(p, b);
            say(p, FIRST_BELL);
        }
        Awaken.LOG.info("[bells] {} rang {} {} home ({})", p.getName().getString(), d.number(), d.id(), where);
        return true;
    }

    /** Said with the first bell (SPEC §43.16): what a bell is for, and the handbell that came with it. */
    static final String FIRST_BELL = "[A fire and a bell make a home. Make a Bell Frame from planks and logs, set it down by your fire and hang the bell on it. "
            + "The handbell's yours too: it sends wild beasts running, and rings the peals your bells make.]";

    /** What the frame and the handbell are, said once to an old Book that was given both with its first bell. */
    static final String FRAME_TOLD = "[You've got a Bell Frame and a handbell. Set the frame down by your fire and hang your bells on it; "
            + "they ring from there at dawn and dusk. The handbell sends wild beasts running, and rings the peals your bells make.]";

    private static void give(ServerPlayer p, ItemStack s) {
        if (!p.getInventory().add(s)) p.drop(s, false, net.minecraft.util.Prediction.SERVER_ONLY);
    }

    /** A bell (or anything) back into this player's hands, or at their feet if their bag is full. */
    static void giveBack(ServerPlayer p, ItemStack s) {
        give(p, s);
    }

    /** The Signal's one line, six seconds from now (the first bell this player ever hangs). */
    static void signalSoon(ServerPlayer p) {
        ONCE.put(p.getUUID(), p.level().getGameTime() + 20 * 6);
    }

    /** Players owed the Signal's one line, and when. Not saved: a restart in those six seconds skips it, harmlessly. */
    private static final Map<UUID, Long> ONCE = new HashMap<>();

    static void say(ServerPlayer p, String line) {
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(line)).topic("bells").ttl(20 * 6).build());
    }

    /** The bells in this player's home's song (hung there, and the far six that answer it); empty with no home. */
    static java.util.Set<Integer> song(ServerPlayer p) {
        Optional<HearthRecord> h = HearthService.data(p.level().getServer()).home(p.getUUID());
        BellWorld.Home home = h.map(r -> BellWorld.get(p.level().getServer()).peek(r.pos)).orElse(null);
        return home == null ? java.util.Set.of() : home.hung;
    }

    // ---- leads ------------------------------------------------------------------------------------------------------

    /**
     * A Chimewren came home to the Rookery singing a note this player's song is missing (SPEC §43.12). Nothing lies in the
     * open to sing of any more (§43.16), so there's nothing to tell: false, and the Rookery gives what it else would.
     */
    public static boolean sung(ServerPlayer p, BlockPos home) {
        return false;
    }

    /**
     * An old letter dug up where a Snufflet was snuffling (SPEC §43.12). Nothing lies in the open to tell of any more
     * (§43.16): false, and the Snufflet digs up something else.
     */
    public static boolean found(ServerPlayer p, BlockPos at) {
        return false;
    }

    /**
     * A Signal Relay near your Hearth listened (SPEC §27.13). Nothing lies in the open to hear any more (§43.16): false,
     * and the relay's panel no longer offers to listen.
     */
    public static boolean relayed(ServerPlayer p, BlockPos relay) {
        return false;
    }

    private static BellBook.Lead lead(ServerPlayer p, BellBook b, BellCatalog.Def d) {
        return b.leads.computeIfAbsent(d.no(), k -> {
            BellBook.Lead n = new BellBook.Lead();
            n.since = PlayDays.current(p.level().getServer());
            return n;
        });
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

    /** A player rang a holdfast's bell from inside its walls: the next holdfast bell joins their song from where it hangs. */
    static void holdfastStruck(ServerPlayer p, String key, BlockPos bell) {
        for (String id : HOLDFAST_BELLS) if (!has(p, id)) {
            ring(p, BellCatalog.of(id), "rung yourself at a holdfast", bell);
            return;
        }
    }

    /**
     * Track a lead: since §27.13 that's the one pin, the lead's step on the Tasks page leading the card with its rough
     * bearing. Before the Tasks page has the step (or without the tasks module) the old way: the top-right card's bearing.
     */
    static void track(ServerPlayer p, int no) {
        BellBook b = book(p);
        BellBook.Lead l = b.leads.get(no);
        if (l == null || l.bearings == 0) return;
        if (dev.awaken.core.api.Goals.pin(p, "bell/" + no)) {
            if (b.tracked >= 0) { // the pin follows the lead itself: the old card goes
                b.tracked = -1;
                save(p, b);
            }
            return;
        }
        b.tracked = no;
        save(p, b);
        BellCatalog.Def d = BellCatalog.of(no);
        BlockPos at;
        if (l.bearings == 2) at = BlockPos.containing(l.tx, 64, l.tz);
        else {
            // one bearing: it's within a hum's reach along it
            at = BlockPos.containing(l.hx, 64, l.hz);
        }
        Destinations.set(p, new Destinations.Target(title(d, l), Level.OVERWORLD.identifier(), at, "bell"), "bell/" + no);
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

    /**
     * A Sovereign set down what it carried: its domain's bell lies where it fell for everyone within 96 who saw it, if a
     * player won it (§43.16 stage 5).
     */
    static void sovereignFell(ServerLevel level, LivingEntity s) {
        String boss = s.hasData(dev.awaken.core.api.CoreApi.MOB_ROLE.get()) ? s.getData(dev.awaken.core.api.CoreApi.MOB_ROLE.get()).bossId().orElse("") : "";
        int at = boss.indexOf("sovereign_");
        String id = at < 0 ? null : COURT_BELLS.get(boss.substring(at + 10));
        if (id == null || !(s.getKillCredit() instanceof ServerPlayer)) return;
        String where = "where the " + s.getName().getString() + " set down what it carried";
        List<ServerPlayer> saw = new ArrayList<>();
        for (ServerPlayer p : level.players()) if (p.distanceToSqr(s) < 96 * 96) saw.add(p);
        leave(level, s.blockPosition(), id, saw, where, "[Where the " + s.getName().getString() + " fell, it set down what it carried: a bell.]");
        for (ServerPlayer p : saw)
            belfryPiece(p, BellContent.SOVEREIGN_RELIC.get(), "[The Sovereign left something behind: a shard of its crown, still holding a little light. A Great Belfry is hung with one of these.]");
    }

    /**
     * One of the Great Belfry's two pieces (SPEC §43.14), given to a player who isn't carrying one and whose home hasn't
     * set one into its frame yet. Every S-rank Lord and every Sovereign gives again, so a lost one is never the end.
     */
    static void belfryPiece(ServerPlayer p, net.minecraft.world.item.Item piece, String line) {
        if (p.getInventory().contains(new ItemStack(piece))) return;
        Optional<HearthRecord> h = HearthService.data(p.level().getServer()).home(p.getUUID());
        BellWorld.Home home = h.map(r -> BellWorld.get(p.level().getServer()).peek(r.pos)).orElse(null);
        boolean clapper = piece == BellContent.HEART_CLAPPER.get();
        if (home != null && (home.stage >= 3 || (clapper ? home.clapper : home.relic))) return;
        give(p, new ItemStack(piece));
        say(p, line);
        Awaken.LOG.info("[bells] {} was given {}", p.getName().getString(), clapper ? "the Heart-clapper" : "a Sovereign's relic");
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
        boolean inCity = level == level.getServer().overworld() && CityHooks.inCity(level, p.blockPosition());
        if (!inCity && dev.awaken.core.api.TheLull.farLanding(level.getServer())) {
            farCity(p);
            return;
        }
        if (!inCity) return;
        for (CityBell c : CITY_BELLS) {
            if (has(p, c.id()) || !cityBellDue(p, c.id())) continue;
            BlockPos at = c.spot().equals("roost") ? CityHooks.startPoint(p) : c.spot().equals("square") ? CityHooks.square(level) : null;
            boolean here = at != null ? p.distanceToSqr(Vec3.atCenterOf(at)) < c.reach() * c.reach() : c.spot().equals(CityHooks.district(level, p.blockPosition()));
            if (here) {
                ring(p, BellCatalog.of(c.id()), "rung for you in Carillon", at != null ? at : p.blockPosition());
                continue;
            }
            remembered(p, BellCatalog.of(c.id()), at, c.remembered());
        }
        // the homes people left: their people tell of them in their own words (VillageBells, §43.16 stage 5)
    }

    /**
     * A Far Landing world (SPEC §67): you're out on the land, so a city bell that's due is rung for you in Carillon wherever
     * you are. It hangs where it always has and answers your song from there, and the city writes to you about it (two of
     * the four letters ask you to come and hear it).
     */
    private static void farCity(ServerPlayer p) {
        ServerLevel over = p.level().getServer().overworld();
        for (CityBell c : CITY_BELLS) {
            if (has(p, c.id()) || !cityBellDue(p, c.id())) continue;
            BlockPos at = c.spot().equals("square") ? CityHooks.square(over) : null;
            if (at == null) at = dev.awaken.core.api.TheLull.centre(p.level().getServer());
            if (ring(p, BellCatalog.of(c.id()), "rung for you in Carillon while you were out on the land", at)) dev.awaken.core.api.FarPost.cityBell(p, c.id());
        }
    }

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

    /**
     * Ilse told them of the hundred (SPEC §43.15) and gave them her bell (§43.16): the Ember Bell, the little bell from over
     * her fire at home, into their hands. One who already had a fire of their own skipped her coal line, so it's said.
     */
    public static void told(ServerPlayer p) {
        BellCatalog.Def d = BellCatalog.of("ember");
        if (d == null || has(p, "ember")) return;
        boolean hadFire = HearthService.data(p.level().getServer()).home(p.getUUID()).isPresent();
        ring(p, d, "given you by Ilse at the Last Fire: the little bell from over her fire at home");
        if (hadFire) say(p, "[Ilse gave you her bell too, the little one from over her fire at home. She's carried it eleven years and never rung it. Hang it by your own fire.]");
    }

    /** How far a home's bells keep new gates off (SPEC §43.15). */
    static final int QUIET = 100;

    /** Whether a home's bell frame is within {@link #QUIET} blocks of {@code at} (overworld only). */
    public static boolean quiet(ServerLevel level, BlockPos at) {
        if (level.dimension() != Level.OVERWORLD) return false;
        BellWorld w = BellWorld.get(level.getServer());
        long r2 = (long) QUIET * QUIET;
        for (BellWorld.Home h : w.homes.values())
            if (h.frame != null && flat(h.frame, at) <= r2) return true;
        return false;
    }

    private static long flat(BlockPos a, BlockPos b) {
        long dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

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

    /**
     * The bells the gates swallowed (SPEC §43.16), by rank. A gate's Lord sits on what its gate took: kill one and a bell
     * may be lying in its room. Your first Lord always kept the Wren; after that about one in three keeps one (never two
     * in a row, and never four without), of the gate's rank or below, the lowest rank first.
     */
    static final Map<Rank, List<String>> SWALLOWED = Map.of(
            Rank.E, List.of("wren", "barrow", "mere", "wayside"),
            Rank.D, List.of("cairn", "spire", "reed", "lintel"),
            Rank.C, List.of("thorn", "crag", "fen"),
            Rank.B, List.of("thaw"),
            Rank.A, List.of("boulder", "leviathan"));

    /** Lords since the last that kept a bell, before one always does again; and the odds in between. */
    static final int LORDS_WITHOUT = 3, LORD_ODDS = 3;

    /** The swallowed bell this Lord kept for this player, or null: the first ever is the Wren. */
    static BellCatalog.@Nullable Def swallowed(ServerPlayer p, Rank gate, long roll) {
        BellBook b = book(p);
        BellCatalog.Def wren = BellCatalog.of("wren");
        boolean first = b.once("first_lord");
        if (first) save(p, b);
        if (first && wren != null && !b.held.containsKey(wren.no())) return wren;
        if (b.lordsSince == 0 && !first) return null;
        if (b.lordsSince < LORDS_WITHOUT && Math.floorMod(roll, LORD_ODDS) != 0) return null;
        for (Rank r : Rank.values()) {
            if (r.ordinal() > gate.ordinal()) break;
            for (String id : SWALLOWED.getOrDefault(r, List.of())) {
                BellCatalog.Def d = BellCatalog.of(id);
                if (d != null && !b.held.containsKey(d.no())) return d;
            }
        }
        return null;
    }

    /**
     * A gate's Lord fell (SPEC §43.16 stage 4): for each player in the run nearby, the bell it kept, lying on its arena's
     * floor near where it fell, one of each for whoever it's for: its world's own bell first (a gate world's Lord at its
     * bell's rank), else one the gate swallowed, if this Lord kept one for them. Each hunter lifts their own; if the gate
     * closes first it goes back to the pool ({@link FoundBells#closed}). With no floor near, it's put in their hands.
     */
    public static void lordFell(ServerLevel level, LivingEntity lord, Rank rank, String theme, Collection<UUID> party, @Nullable ServerPlayer killer) {
        // the deepest room of an S-rank gate holds the Heart-clapper (SPEC §43.14)
        if (rank.ordinal() >= Rank.S.ordinal())
            for (ServerPlayer p : level.players())
                if ((p == killer || party.contains(p.getUUID())) && p.distanceToSqr(lord) < 96 * 96)
                    belfryPiece(p, BellContent.HEART_CLAPPER.get(), "[In the Lord's hoard there's a clapper as long as your arm, black iron with something red and warm in it. It's the Heart-clapper. A Great Belfry is hung on one.]");
        String id = GATE_BELLS.get(theme);
        BellCatalog.Def own = id == null ? null : BellCatalog.of(id);
        String label = Rank.valueOf(rank.name()).label;
        // each bell this Lord kept, and who it's lying there for (in the order they're set down)
        Map<Integer, List<ServerPlayer>> kept = new java.util.LinkedHashMap<>();
        for (ServerPlayer p : level.players()) {
            if (!(p == killer || party.contains(p.getUUID())) || p.distanceToSqr(lord) >= 96 * 96) continue;
            BellBook b = book(p);
            if (own != null && rank.ordinal() >= own.rank().ordinal() && !b.held.containsKey(own.no())) {
                kept.computeIfAbsent(own.no(), k -> new ArrayList<>()).add(p);
                continue;
            }
            BellCatalog.Def d = swallowed(p, rank, lord.getUUID().getLeastSignificantBits() ^ p.getUUID().getMostSignificantBits());
            b = book(p);
            if (d == null) {
                b.lordsSince++;
                save(p, b);
                continue;
            }
            b.lordsSince = 0;
            save(p, b);
            kept.computeIfAbsent(d.no(), k -> new ArrayList<>()).add(p);
            // its clapper has come loose in the fall (a Chime Perch is built round one), and wild Chimewrens love the sound
            dev.awaken.kin.KinApi.clapper(p);
        }
        String gate = dev.awaken.core.api.MobRole.of(lord).gateId();
        for (Map.Entry<Integer, List<ServerPlayer>> e : kept.entrySet()) {
            BellCatalog.Def d = BellCatalog.of(e.getKey());
            boolean hoard = d == own;
            String where = hoard ? "in the hoard of a " + label + " gate's Lord" : "under a " + label + " gate's Lord, where the gate had taken it";
            BlockPos at = FoundBells.keep(level, lord.blockPosition(), gate, d.no(), where, e.getValue().stream().map(ServerPlayer::getUUID).toList());
            for (ServerPlayer p : e.getValue()) {
                if (at == null) ring(p, d, where);
                BellBook b = book(p);
                if (!hoard && b.once("first_swallowed")) {
                    save(p, b);
                    say(p, at == null ? "[The Lord was keeping a bell the gate took. Ilse said a lot of them fell in. Bring it home and it'll ring there.]"
                            : "[The Lord was keeping a bell the gate took. Ilse said a lot of them fell in. It's lying where the Lord fell: lift it and bring it home.]");
                } else if (at != null) say(p, hoard ? "[There's a bell in the Lord's hoard, lying where it fell. Lift it and take it home.]"
                        : "[The Lord was keeping a bell. It's lying where it fell.]");
            }
        }
    }

    static final int ILSE_DAYS = 3, ILSE_NEAR = 24, ILSE_PATH = 28;

    /**
     * After the Wake (SPEC §43.14), Ilse walks out from the Last Fire to see each home once: three play-days on, by day,
     * when someone who lives there is by the fire. She comes up the path from the city's side.
     */
    private static void ilse(ServerPlayer p, HearthRecord h) {
        MinecraftServer server = p.level().getServer();
        if (!dev.awaken.core.api.StoryHooks.colourRestored(server) || !(p.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        BellWorld w = BellWorld.get(server);
        long today = PlayDays.current(server);
        if (w.woke < 0) {
            w.woke = today;
            w.setDirty();
        }
        BellWorld.Home home = w.home(h.pos);
        if (home.ilse || today < w.woke + ILSE_DAYS || p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(h.pos)) > ILSE_NEAR * ILSE_NEAR) return;
        long tod = Math.floorMod(level.getOverworldClockTime(), 24000L);
        if (tod < 1000 || tod > 9000) return;
        BlockPos c = dev.awaken.core.api.TheLull.centre(server);
        double dx = c.getX() - h.pos.getX(), dz = c.getZ() - h.pos.getZ(), len = Math.max(1, Math.hypot(dx, dz));
        int x = h.pos.getX() + (int) Math.round(dx / len * ILSE_PATH), z = h.pos.getZ() + (int) Math.round(dz / len * ILSE_PATH);
        if (!level.hasChunkAt(new BlockPos(x, 0, z))) return;
        BlockPos from = new BlockPos(x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        if (!dev.awaken.core.api.LivingHooks.visit(level, from, h.pos)) return;
        home.ilse = true;
        w.setDirty();
        for (ServerPlayer m : level.players())
            if (h.belongs(m.getUUID()) && m.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(h.pos)) < 64 * 64)
                say(m, "[Someone's coming up the path from the city. It's Ilse, from the Last Fire.]");
    }

    /** Dev and tours: Ilse's visit now, as if three play-days had passed since the Wake. */
    static boolean ilseNow(ServerPlayer p) {
        Optional<HearthRecord> h = HearthService.data(p.level().getServer()).home(p.getUUID());
        if (h.isEmpty()) return false;
        BellWorld w = BellWorld.get(p.level().getServer());
        w.woke = PlayDays.current(p.level().getServer()) - ILSE_DAYS;
        w.home(h.get().pos).ilse = false;
        ilse(p, h.get());
        return w.home(h.get().pos).ilse;
    }

    /** Checks the moments that are states rather than events, for one player. */
    private static void moments(ServerPlayer p) {
        MinecraftServer server = p.level().getServer();
        upgrade(p);
        owed(p);
        Optional<HearthRecord> home = HearthService.data(server).home(p.getUUID());
        if (home.isPresent()) {
            HearthRecord h = home.get();
            // Ilse gives the Ember with her coal; one who never met her finds it by their fire
            ring(p, "ember", "by your fire, the night you lit it");
            FoundBells.roost(p, h);
            FoundBells.wagon(p, h);
            if (h.tier >= 2 && HearthService.OVERWORLD.equals(h.dimension) && p.level() == server.overworld()
                    && p.distanceToSqr(Vec3.atCenterOf(h.pos)) < 48 * 48 && !has(p, "founding")) {
                BlockPos by = FoundBells.yard(p.level(), h.pos);
                if (by != null) FoundBells.leave(p.level(), by, BellCatalog.of("founding"), List.of(p), "the day your Hearth grew",
                        "[Your Hearth's grown. There's a new bell standing by your fire for it: the Founding Bell.]");
            }
            // the Great Belfry and the Hundred Peal reach a member who was away when they happened (SPEC §43.14)
            BellWorld.Home tower = BellWorld.get(server).peek(h.pos);
            if (tower != null && tower.stage >= 3) GreatBelfry.seed(p);
            if (tower != null && tower.pealed >= 0) HundredPeal.pealed(p);
            ilse(p, h);
            BellWorld.Home tower2 = BellWorld.get(server).peek(h.pos);
            BellBook b = book(p);
            if (b.given && (tower2 == null || tower2.frame == null) && b.once("frame_told")) {
                // a Book from before the frame was explained
                save(p, b);
                say(p, FRAME_TOLD);
            }
        }
        var prof = Progression.profile(p);
        Rank rank = Progression.rank(p);
        // the Registry's two: Bram hands them over at the Registry (stage 5)
        bram(p, prof.counter(Counters.GATES_CLEARED));
        if (rank.ordinal() >= Rank.D.ordinal()) ring(p, "tenor", "the Great Bell from your D-rank Proving");
        if (rank.ordinal() >= Rank.C.ordinal()) ring(p, "second", "the Great Bell from your C-rank Proving");
        if (prof.counter(Counters.SPIRITS_SPOKEN) >= 1) ring(p, "spirit", "given by the first spirit you spoke to");
        if (rank.ordinal() >= Rank.B.ordinal()) ring(p, "third", "the Great Bell from your B-rank Proving");
        if (rank.ordinal() >= Rank.A.ordinal()) ring(p, "fourth", "the Great Bell from your A-rank Proving");
        if (rank.ordinal() >= Rank.S.ordinal()) ring(p, "fifth", "the Great Bell from your S-rank Proving");
        if (rank == Rank.SOVEREIGN) ring(p, "bourdon", "the Great Bell from your last Proving");
        // the Descent's: set into the rock of each stratum, found the first time you're down there
        int stratum = dev.awaken.descent.Strata.stratumOf(p.level().dimension());
        if (stratum > 0 && stratum < DESCENT_BELLS.length && DESCENT_BELLS[stratum] != null)
            found(p, DESCENT_BELLS[stratum][0], DESCENT_BELLS[stratum][1], "[There's a bell set into the rock here, half grown over. It's been waiting down here a long time.]");
        if (!has(p, "crystal"))
            for (int i = 0; i < p.getInventory().getContainerSize(); i++)
                if (p.getInventory().getItem(i).is(DelveContent.DEEPGLASS_SHARD.get())) {
                    if (ring(p, "crystal", "grown into the first Deepglass you held")) say(p, "[There's a little bell grown into the Deepglass, as if it had been there all along.]");
                    break;
                }
        // the Above: in the ruin of a Tether Spire or a sky platform you've found, aboard the Halo, out of a storm
        for (var m : dev.awaken.above.Above.data(p).ruins()) {
            if (p.level().dimension() != Level.OVERWORLD || flat(m.center(), p.blockPosition()) > 24 * 24) continue;
            if (m.kind().equals("tether_spire") && !has(p, "wind"))
                FoundBells.leave(p.level(), m.center(), BellCatalog.of("wind"), List.of(p), "in the wind at a Tether Spire",
                        "[There's a bell in the spire's ruin, wedged where the wind left it.]");
            if (m.kind().equals("sky_platform") && !has(p, "island"))
                FoundBells.leave(p.level(), m.center(), BellCatalog.of("island"), List.of(p), "on a sky platform",
                        "[There's a bell lying in what's left of the platform.]");
        }
        if (dev.awaken.above.halo.Halo.in(p)) {
            if (prof.counter(dev.awaken.above.Above.HALO_REACHED) >= 1)
                found(p, "halo", "aboard the Halo", "[There's a bell aboard the Halo, lying where nobody's stood in years.]");
            if (rank.ordinal() >= Rank.S.ordinal())
                found(p, "star", "under the stars at the Halo", "[Under the stars, there's another bell aboard the Halo, small and bright.]");
        }
        if (p.level().dimension() == Level.OVERWORLD && p.level().isThundering() && p.getY() > 160 && !p.onGround() && !p.isInWater()
                && ring(p, "storm", "in the sky, in a storm")) say(p, "[Something fell out of the storm and into your hands: a bell.]");
        // the Eclipse Bell: out under the open sky in the middle of a new moon's night, at A rank, the dark sky drops it
        if (rank.ordinal() >= Rank.A.ordinal() && p.level().dimension() == Level.OVERWORLD) {
            long clock = p.level().getOverworldClockTime(), tod = clock % 24000L;
            if ((clock / 24000L) % 8 == 4 && tod >= 16000 && tod <= 20000 && p.level().canSeeSky(p.blockPosition().above()))
                found(p, "eclipse", "under the dark of the moon", "[Something fell out of the dark sky and rang as it hit the ground near you: a bell.]");
        }
        city(p);
    }

    /**
     * A Book from before (SPEC §43.16), brought up to date once. Schema 3 or below: leads to bells that lay in the land
     * close. Schema 4 or below (stage 2): every bell it holds becomes a thing to hang, in their hands (but the far six,
     * which join the song from where they hang); the Signal's line was said with the first bell then, so it's not again.
     */
    static void upgrade(ServerPlayer p) {
        BellBook b = book(p);
        if (b.schema >= BellBook.SCHEMA) return;
        boolean closed = b.schema <= 3 && BellSites.closeOldLeads(b);
        boolean things = false;
        if (b.schema <= 4) {
            if (b.given) b.once("signal");
            ServerLevel ow = p.level().getServer().overworld();
            java.util.Iterator<BlockPos> holdfasts = dev.awaken.village.Holdfast.heard(p).values().stream().filter(k -> k.inside())
                    .map(k -> BlockPos.of(k.bell())).sorted(java.util.Comparator.comparingLong(BlockPos::asLong)).iterator();
            for (int no : new java.util.TreeSet<>(b.held.keySet())) {
                BellCatalog.Def d = BellCatalog.of(no);
                if (far(d)) {
                    BlockPos at = switch (d.id()) {
                        case "roost" -> CityHooks.startPoint(p);
                        case "square" -> CityHooks.square(ow);
                        case "market" -> CityHooks.spot(ow, "market");
                        case "harbour" -> CityHooks.spot(ow, "quay");
                        default -> holdfasts.hasNext() ? holdfasts.next() : null;
                    };
                    if (at == null) at = CityHooks.square(ow);
                    // no city in this world: it answers from where they stand now
                    b.far.put(no, (at != null ? at : p.blockPosition()).asLong());
                } else if (!b.owed.contains(no)) {
                    b.owed.add(no);
                    things = true;
                }
            }
        }
        b.schema = BellBook.SCHEMA;
        save(p, b);
        if (closed) say(p, "[Some of the bells in your Book weren't where they seemed to be after all. The gates may have them.]");
        if (things) say(p, "[Your bells are waiting to be hung. Use each one on your Bell Frame by your fire.]");
        HearthService.data(p.level().getServer()).home(p.getUUID()).ifPresent(h -> BellTower.hang(p.level().getServer(), h));
    }

    /** Bells owed back as things (carried through a death, or from an old Book) go into their hands while they're alive. */
    static void owed(ServerPlayer p) {
        BellBook b = book(p);
        if (b.owed.isEmpty() || !p.isAlive()) return;
        List<Integer> back = new java.util.ArrayList<>(b.owed);
        b.owed.clear();
        save(p, b);
        for (int no : back) giveBack(p, BellItem.of(no));
    }

    /** A player died: their bells don't fall with the rest, they come back into their hands (SPEC §43.16). */
    static void died(ServerPlayer p, Collection<net.minecraft.world.entity.item.ItemEntity> drops) {
        BellBook b = book(p);
        boolean any = false;
        for (java.util.Iterator<net.minecraft.world.entity.item.ItemEntity> it = drops.iterator(); it.hasNext(); ) {
            net.minecraft.world.entity.item.ItemEntity e = it.next();
            BellCatalog.Def d = BellItem.def(e.getItem());
            if (d == null) continue;
            for (int k = 0; k < e.getItem().getCount(); k++) b.owed.add(d.no());
            it.remove();
            any = true;
        }
        if (any) save(p, b);
    }

    /**
     * A World Pulse happening witnessed (SPEC §43.13), from C rank: the Stampede Bell lying in a stampede's wake, the Flood
     * Bell left at your feet when the Deep Tide comes up under the new moon (§43.16 stage 5). {@code happening} is the
     * happening's path ("stampede").
     */
    public static void witnessed(ServerPlayer p, String happening) {
        if (Progression.rank(p).ordinal() < Rank.C.ordinal()) return;
        switch (happening) {
            case "stampede" -> found(p, "stampede", "in the wake of a stampede",
                    "[The herd's leader lost something as it thundered past: a bell, lying in its wake.]");
            case "deep_tide" -> {
                BellCatalog.Def d = BellCatalog.of("flood");
                if (!has(p, d.id())) FoundBells.leave(p.level(), p.blockPosition(), d, List.of(p), "when the Deep Tide came up under the new moon",
                        "[The Deep Tide left something at your feet as it went back down: a bell.]");
            }
            default -> {}
        }
    }

    /** Bell {@code id} set down a few steps in front of this player for them alone (stage 5), if they don't hold it. */
    static void found(ServerPlayer p, String id, String where, String line) {
        BellCatalog.Def d = BellCatalog.of(id);
        if (d != null && !has(p, id)) FoundBells.leave(p, d, where, line);
    }

    /**
     * Bell {@code id} won by {@code ps} at a real place (SPEC §43.16 stage 5): set down on the floor nearest {@code near}
     * for each of them who doesn't hold it (one bell, each lifts their own), else into their hands. Each is told
     * {@code line}. The Foundry's casts by its Crucible, a held raid's bells where the wall was breached.
     */
    public static void leave(ServerLevel level, BlockPos near, String id, List<ServerPlayer> ps, String where, String line) {
        BellCatalog.Def d = BellCatalog.of(id);
        if (d != null && !ps.isEmpty()) FoundBells.leave(level, near, d, ps, where, line);
    }

    /** Whether bell {@code id} is lying somewhere for this player to lift (a Lord's floor, by a Crucible, anywhere it was won). */
    public static boolean lying(ServerPlayer p, String id) {
        BellCatalog.Def d = BellCatalog.of(id);
        return d != null && FoundBells.waiting(p.level().getServer(), p.getUUID(), d.no());
    }

    /** How near Bram a hunter must stand to be handed the Registry's bells. */
    static final int BRAM_NEAR = 8;

    /**
     * The Registry's two (stage 5): for your first and twenty-fifth gates the System says Bram has something for you once,
     * and he hands it over when you're by him at the Registry.
     */
    static void bram(ServerPlayer p, long gates) {
        String id = gates >= 1 && !has(p, "registry") ? "registry" : gates >= 25 && !has(p, "toll") ? "toll" : null;
        if (id == null) return;
        BellBook b = book(p);
        if (b.once("bram_" + id)) {
            save(p, b);
            say(p, "[Bram at the Registry has something for you.]");
        }
        if (dev.awaken.core.api.TheLull.farLanding(p.level().getServer())) {
            posted(p, id);
            return;
        }
        Vec3 at = CitizenHooks.where(p.level(), "bram");
        if (at != null && p.distanceToSqr(at) <= BRAM_NEAR * BRAM_NEAR) handed(p, id);
    }

    /**
     * A Far Landing world (SPEC §67): you're a long way from the Registry, so Bram puts the bell on the Registry's wagon,
     * which leaves it in your yard, and writes to you.
     */
    static void posted(ServerPlayer p, String id) {
        boolean first = id.equals("registry");
        if (!ring(p, BellCatalog.of(id), first ? "sent by Bram on the Registry's wagon, for your first gate"
                : "sent by Bram on the Registry's wagon, for your twenty-fifth gate", null, true)) return;
        dev.awaken.core.api.FarPost.letter(p, "Bram at the Registry", first
                ? "Your first gate. The Registry gives every hunter one of these for it: the old handbell off the front desk. We had a cupboard full of them once. You're a long way out, so it's coming on the wagon. It'll be in your yard by morning."
                : "Twenty-five gates. The Registry used to toll this one for every hunter who got that far. Nobody has, in eleven years. It's on the wagon to you now. Hang it somewhere it can be heard.");
    }

    /** Bram hands over one of the Registry's bells, in his own words. */
    static void handed(ServerPlayer p, String id) {
        boolean first = id.equals("registry");
        if (!ring(p, BellCatalog.of(id), first ? "handed you by Bram at the Registry, for your first gate" : "handed you by Bram at the Registry, for your twenty-fifth gate")) return;
        String line = first ? "Your first gate. The Registry gives every hunter one of these for it: the old handbell off the front desk. We had a cupboard full of them once."
                : "Twenty-five gates. The Registry used to toll this one for every hunter who got that far. Nobody has, in eleven years. Take it home and hang it.";
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Bram")).body(Component.literal(line))
                .dialogue().ttl(20 * 12).priority(8).build());
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
        DreamBells.tick(server);
        BellTower.tick(server);
        GreatBelfry.tick(server);
        HundredPeal.tick(server);
        Peals.tick(server);
        long now = ow.getGameTime();
        if (!ONCE.isEmpty()) ONCE.entrySet().removeIf(e -> {
            if (e.getValue() > now) return false;
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(SIGNAL_ONCE)).priority(8).ttl(20 * 10).build());
            return true;
        });
        if (server.getTickCount() % 100 == 60) {
            FoundBells.closed(server);
            FoundBells.watch(server);
        }
        VillageBells.tick(server);
        SpiritBells.tick(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if ((p.tickCount + p.getId()) % 100 != 0) continue;
            FoundBells.lost(p);
            moments(p);
            BellSites.tick(p);
            body(p);
        }
    }
}
