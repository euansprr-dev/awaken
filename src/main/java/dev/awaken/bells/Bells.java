package dev.awaken.bells;

import dev.awaken.Awaken;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.CityHooks;
import dev.awaken.core.api.Destinations;
import dev.awaken.delve.DelveContent;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
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
 * The hundred bells (SPEC §43.7, §43.16), the public face: who holds which bell, ringing one home, the leads that file
 * themselves in the Bell Book, and each home's voices. No bell lies in the open: each comes from somewhere a story puts
 * it (your Ember, your first beast at home, a gate's Lord that kept one the gate swallowed, a Proving, a raid held, the
 * Deep, a dream, a spirit). Every bell held by anyone who lives at a home hangs on its frame and sings in its song.
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
        // nothing plays at your feet: a bell is heard where it hangs, when your song rings
        Optional<HearthRecord> home = HearthService.data(server).home(p.getUUID());
        home.ifPresent(h -> BellTower.hang(server, h));
        StringBuilder body = new StringBuilder(d.rank().label + "-rank. ");
        body.append(where(server, home.orElse(null)));
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
            b.once("frame_told");
            save(p, b);
            say(p, FRAME_TOLD);
        }
        Awaken.LOG.info("[bells] {} rang {} {} home ({})", p.getName().getString(), d.number(), d.id(), where);
        return true;
    }

    /** What the frame and the handbell are, said once to whoever is given them. */
    static final String FRAME_TOLD = "[You've got a Bell Frame and a handbell. Set the frame down by your fire and your bells hang on it; "
            + "they ring from there at dawn and dusk. The handbell sends wild beasts running, and rings the peals your bells make.]";

    /** Where a new bell is, in the card's words: true whether or not there's a frame to hang it on yet. */
    private static String where(MinecraftServer server, @Nullable HearthRecord h) {
        if (h == null) return "It'll hang by your fire once you have a home and set a Bell Frame there.";
        BellWorld.Home home = BellWorld.get(server).peek(h.pos);
        return home != null && home.frame != null ? "It's hanging on your bell frame now." : "It'll hang by your fire once you set your Bell Frame down there.";
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

    /** A player rang a holdfast's bell from inside its walls: the next holdfast bell goes home with them. */
    static void holdfastStruck(ServerPlayer p, String key) {
        for (String id : HOLDFAST_BELLS) if (!has(p, id)) {
            ring(p, id, "rung yourself at a holdfast");
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

    /** A Sovereign set down what it carried: its domain's bell goes home with everyone within 96 who saw it, if a player won it. */
    static void sovereignFell(ServerLevel level, LivingEntity s) {
        String boss = s.hasData(dev.awaken.core.api.CoreApi.MOB_ROLE.get()) ? s.getData(dev.awaken.core.api.CoreApi.MOB_ROLE.get()).bossId().orElse("") : "";
        int at = boss.indexOf("sovereign_");
        String id = at < 0 ? null : COURT_BELLS.get(boss.substring(at + 10));
        if (id == null || !(s.getKillCredit() instanceof ServerPlayer)) return;
        String where = "where the " + s.getName().getString() + " set down what it carried";
        for (ServerPlayer p : level.players())
            if (p.distanceToSqr(s) < 96 * 96) {
                ring(p, id, where);
                belfryPiece(p, BellContent.SOVEREIGN_RELIC.get(), "[The Sovereign left something behind: a shard of its crown, still holding a little light. A Great Belfry is hung with one of these.]");
            }
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
        // the homes people left: their bells wait for the villages' quests (§43.16), so nobody tells of them yet
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

    /** A Remembered lead: what someone said, and the area it points at if it points anywhere. */
    /** Ilse told them of the hundred (SPEC §43.15): the Ember's lead opens the Bell Book at 0 / 100 before a bell rings. */
    public static void told(ServerPlayer p) {
        BellCatalog.Def d = BellCatalog.of("ember");
        if (d == null || has(p, "ember")) return;
        remembered(p, d, null, "Ilse says there were a hundred bells across the land. The first will ring by your own fire, once you set her coal down.");
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
     * A gate's Lord fell: for each player in the run nearby, the bell it kept. Its world's own bell first (a gate world's
     * Lord at its bell's rank), else one the gate swallowed, if this Lord kept one for them.
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
        for (ServerPlayer p : level.players()) {
            if (!(p == killer || party.contains(p.getUUID())) || p.distanceToSqr(lord) >= 96 * 96) continue;
            BellBook b = book(p);
            if (own != null && rank.ordinal() >= own.rank().ordinal() && !b.held.containsKey(own.no())) {
                ring(p, own, "in the hoard of a " + label + " gate's Lord");
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
            ring(p, d, "under a " + label + " gate's Lord, where the gate had taken it");
            // its clapper has come loose in the fall (a Chime Perch is built round one), and wild Chimewrens love the sound
            dev.awaken.kin.KinApi.clapper(p);
            if (b.once("first_swallowed")) {
                save(p, b);
                say(p, "[The Lord was keeping a bell the gate took. Ilse said a lot of them fell in. Bring it home and it'll ring there.]");
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
        Optional<HearthRecord> home = HearthService.data(server).home(p.getUUID());
        if (home.isPresent()) {
            HearthRecord h = home.get();
            ring(p, "ember", "by your fire, the night you lit it");
            if (!KinWorld.get(server).at(h.pos).isEmpty()) ring(p, "nook", "the day a beast came to live with you");
            if (h.tier >= 2) ring(p, "founding", "the day your Hearth grew");
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
        // the Eclipse Bell: out under the open sky in the middle of a new moon's night, at A rank
        if (rank.ordinal() >= Rank.A.ordinal() && p.level().dimension() == Level.OVERWORLD) {
            long clock = p.level().getOverworldClockTime(), tod = clock % 24000L;
            if ((clock / 24000L) % 8 == 4 && tod >= 16000 && tod <= 20000 && p.level().canSeeSky(p.blockPosition().above()))
                ring(p, "eclipse", "under the dark of the moon");
        }
        city(p);
    }

    /** A Book from before (SPEC §43.16): leads to bells that lay in the land close, said once. */
    private static void upgrade(ServerPlayer p) {
        BellBook b = book(p);
        if (b.schema >= BellBook.SCHEMA) return;
        boolean closed = BellSites.closeOldLeads(b);
        b.schema = BellBook.SCHEMA;
        save(p, b);
        if (closed) say(p, "[Some of the bells in your Book weren't where they seemed to be after all. The gates may have them.]");
    }

    /**
     * A World Pulse happening witnessed (SPEC §43.13), from C rank: the Stampede Bell in a stampede's thunder, the Flood
     * Bell when the Deep Tide comes up under the new moon. {@code happening} is the happening's path ("stampede").
     */
    public static void witnessed(ServerPlayer p, String happening) {
        if (Progression.rank(p).ordinal() < Rank.C.ordinal()) return;
        switch (happening) {
            case "stampede" -> ring(p, "stampede", "in the thunder of a stampede");
            case "deep_tide" -> ring(p, "flood", "when the Deep Tide came up under the new moon");
            default -> {}
        }
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
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if ((p.tickCount + p.getId()) % 100 != 0) continue;
            moments(p);
            BellSites.tick(p);
            body(p);
        }
    }
}
