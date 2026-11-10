package dev.awaken.finds;

import dev.awaken.finds.verb.Script;
import dev.awaken.living.region.RegionMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * One kind of find (SPEC §58): what it's called, where it can stand, what it builds and what you do there. Kinds are
 * written in {@code finds/sets/}, one file per set, and registered in {@link Kinds}. Everything a player reads about a
 * kind is here ({@link #name}, {@link #see}, {@link #learned}); the rest is the engine's.
 */
public record Kind(String id, String name, FSet set, int number, Verb verb, Size size, int rank, Set<RegionMap.Group> lands,
                   Anchor anchor, Made made, String see, String learned, int reach, int slope, int length, Maker maker,
                   Supplier<Script> script, String loot, boolean persists, Tell tell, Supplier<ItemStack> icon, boolean needsMove,
                   boolean lead, int weight, java.util.function.Consumer<dev.awaken.finds.verb.Ctx> after, @org.jspecify.annotations.Nullable Play play, int ward, String move, @org.jspecify.annotations.Nullable Air air,
                   @org.jspecify.annotations.Nullable Family family, int tier) {

    /** The eighteen things your hands do. A kind is built on one; many borrow a second. */
    public enum Verb { STACK, CARRY, LIGHT, RING, TURN, OFFER, FOLLOW, DIG, PULL, POUR, SHOOT, RUN, REACH, WAIT, MEND, READ, BREAK, SNEAK }

    /**
     * A place is somewhere you walk into, tens of blocks across, with its own air: the only size the world deals now
     * (SPEC §59). A curio is a few blocks; a find is a small scene. Those two are furnishing, not discoveries.
     */
    public enum Size { CURIO, FIND, PLACE }

    public enum Made { BUILT, STILLED, BEAST, NATURAL, ALIVE, FALLEN, STRANGE }

    /**
     * The ground a kind needs. {@code FLAT}: level within {@link Kind#slope}. {@code HILLTOP}: the highest point about,
     * standing clear of the land round it. {@code LINE}: a gentle run {@link Kind#length} long (an old road).
     * {@code SHORE}: dry land beside open water, facing it. {@code CLIFF}: the foot of a steep rise, facing it.
     * {@code SLOPE}: a hillside, facing downhill. {@code PEAK}: a hilltop high above the sea.
     */
    public enum Anchor {
        FLAT, HILLTOP, LINE, SHORE, CLIFF, SLOPE, PEAK,
        /** Dry ground that can be levelled: no more than {@link Kind#slope} between any two of seventeen points across the footprint. */
        FIELD,
        /**
         * Ground a monument can stand a terrace on (SPEC §62.3): dry, and no more than {@link Kind#slope} between any two
         * of seventeen points. The site's height is toward the top of them, so the terrace is built up more than it is
         * cut in, and the kind is turned so that its front (south) looks downhill.
         */
        TERRACE,
        /** The same, taking the highest spot in the cell that fits. */
        HIGH
    }

    /** What gives a find away from a distance. Drawn by the client near the heart; small on purpose. */
    public enum Tell { NONE, GLINT, SMOKE, MOTHS, CHIME, HUM, DRIP, FIREFLIES, STEAM }

    /** The eighteen sets of eight, in Journal order. */
    public enum FSet {
        CAIRNS("Cairns"), STILLED("The Stilled at Rest"), LAMPS("Lamplighter's Road"), RINGING("Ringing Stones"),
        MACHINES("Old Machines"), GODS("Small Gods"), WISPS("Wisps"), BURIED("Buried Things"), HUNG("Hung Things"),
        DRY("Dry Things"), MARKS("Marks"), RUNS("Runs"), HIGH("High Things"), WEATHER("Night and Weather"),
        MENDED("Mended Things"), SURVEY("Surveyor's Stones"), SEALED("Sealed Things"), BEASTS("Beast Works"), PLACES("Places"), MONUMENTS("Monuments"),
        REMAINS("Remains");

        public final String display;

        FSet(String display) {
            this.display = display;
        }
    }

    /** How a kind is finished, step by step, as a scripted player does it in the tests. It doubles as the kind's walkthrough. */
    @FunctionalInterface
    public interface Play {
        void run(Trial t);
    }

    /** Builds the kind's scene at a site. */
    @FunctionalInterface
    public interface Maker {
        void build(Scene s);
    }

    public boolean fits(RegionMap.Group g) {
        return lands.isEmpty() || lands.contains(g);
    }

    public String key() {
        return "find/" + id;
    }

    public static Builder of(FSet set, int number, String id, String name) {
        return new Builder(set, number, id, name);
    }

    /** Fluent writer for a kind; {@link #add()} registers it. */
    public static final class Builder {
        private final FSet set;
        private final int number;
        private final String id, name;
        private Verb verb = Verb.REACH;
        private Size size = Size.CURIO;
        private int rank, reach = 8, slope = 2, length = 0, weight = 10;
        private final EnumSet<RegionMap.Group> lands = EnumSet.noneOf(RegionMap.Group.class);
        private Anchor anchor = Anchor.FLAT;
        private Made made = Made.BUILT;
        private String see = "", learned = "", loot = "";
        private Maker maker = s -> {};
        private Supplier<Script> script = () -> new Script() {};
        private boolean persists, needsMove, lead;
        private Tell tell = Tell.NONE;
        private Supplier<ItemStack> icon = () -> ItemStack.EMPTY;
        private java.util.function.Consumer<dev.awaken.finds.verb.Ctx> after = c -> {};
        private Play play;
        private int ward;
        private String move = "";
        private Air air;
        private Family family;
        private int tier;

        private Builder(FSet set, int number, String id, String name) {
            this.set = set;
            this.number = number;
            this.id = id;
            this.name = name;
        }

        public Builder verb(Verb v) { verb = v; return this; }
        public Builder curio() { size = Size.CURIO; return this; }
        public Builder find() { size = Size.FIND; return this; }
        public Builder place() { size = Size.PLACE; return this; }
        /** The place's own air: fog, motes, sound. */
        public Builder air(Air a) { air = a; return this; }
        /** 0 is E (near home), 4 is A. */
        public Builder rank(int r) { rank = r; return this; }
        /** The land groups it stands in; none means any land. */
        public Builder lands(RegionMap.Group... g) { lands.addAll(java.util.Arrays.asList(g)); return this; }
        public Builder anchor(Anchor a) { anchor = a; return this; }
        public Builder made(Made m) { made = m; return this; }
        /** What you see, as the Journal says it before you've done it. */
        public Builder see(String s) { see = s; return this; }
        /** What the Journal keeps once you've done it. */
        public Builder learned(String s) { learned = s; return this; }
        /** No block of the scene lies further than this from the heart. */
        public Builder reach(int r) { reach = r; return this; }
        /** How uneven the ground may be across the footprint. */
        public Builder slope(int s) { slope = s; return this; }
        /** For {@link Anchor#LINE}: how long a run it needs. */
        public Builder length(int l) { length = l; return this; }
        public Builder scene(Maker m) { maker = m; return this; }
        public Builder script(Supplier<Script> s) { script = s; return this; }
        /** The loot table under {@code awaken:finds/}; empty takes the tier table for its rank and size. */
        public Builder loot(String l) { loot = l; return this; }
        /** What you did here stays done for everyone (a mended bridge, a taller cairn); it doesn't reset. */
        public Builder persists() { persists = true; return this; }
        /** Needs a move or a power a player may not have yet: kept under "What waits" when met. */
        public Builder needsMove() { needsMove = true; return this; }
        /** Needs a rung of the Ascent by its tier name ("tether", "wings"): met without it, it's kept as a promise and the world calls you back. */
        public Builder needsMove(String tier) { needsMove = true; move = tier; return this; }
        /** Finishing it gives a direction to a rarer find further out. */
        public Builder lead() { lead = true; return this; }
        public Builder tell(Tell t) { tell = t; return this; }
        public Builder icon(Supplier<? extends ItemLike> i) { icon = () -> new ItemStack(i.get()); return this; }
        /** What the world does the moment the find is finished, before anyone is paid (birds come, a door opens). */
        public Builder after(java.util.function.Consumer<dev.awaken.finds.verb.Ctx> a) { after = a; return this; }
        /** Once finished, nothing hostile spawns within this many blocks of its heart (a road made safe). */
        public Builder wards(int radius) { ward = radius; return this; }
        /** The walkthrough the tests run. */
        public Builder play(Play p) { play = p; return this; }
        /** How often it's dealt against its neighbours (10 is usual). */
        public Builder weight(int w) { weight = w; return this; }
        /** One of a family's five monuments (SPEC §62.1): {@code tier} 1 is met first, 5 furthest out. Sets the rank. */
        public Builder family(Family f, int tier) { family = f; this.tier = tier; rank = f.rankOf(tier); return this; }

        public Kind add() {
            Kind k = new Kind(id, name, set, number, verb, size, rank, lands.isEmpty() ? Set.of() : EnumSet.copyOf(lands), anchor, made, see, learned,
                    reach, slope, length, maker, script, loot, persists, tell, icon, needsMove, lead, weight, after, play, ward, move, air, family, tier);
            Kinds.register(k);
            return k;
        }
    }
}
