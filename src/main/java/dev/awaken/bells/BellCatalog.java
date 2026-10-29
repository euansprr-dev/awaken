package dev.awaken.bells;

import dev.awaken.progression.Rank;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The hundred bells (SPEC §43.7; GOALS part 1): each numbered 00 to 99 with its name, rank, where it comes from and its
 * voice (what it does while it hangs). Every bell's number is its note's place in the song. Stage 4 built the first
 * thirty, stage 5 the Foundry's five and stage 6 the rest (a bell is built once its voice isn't {@link Voice#NONE}); the
 * Seed (00) and the True Bell (36) wait for stage 7.
 */
public final class BellCatalog {
    private BellCatalog() {}

    /** Where a bell comes from (GOALS part 1's table). The player never sees this. */
    public enum Source { OWN, PROVING, BIG_DAY, PEOPLE, GATE_WORLD, WILDS, DESCENT, DEEP, VEIL, LUCIDIUM, ABOVE, COURT, SEED }

    /** What a bell does while it hangs. Each kind counts the home's bells that have it ({@link Voices}). */
    public enum Voice {
        NONE(""),
        REST("Sit by your fire and wounds close faster."),
        GROWTH("Crops inside your Hearth grow faster."),
        KIN("Beasts warm to you sooner when you help them."),
        WARD("Fewer night creatures come up inside your Hearth."),
        STRIDE("You move faster on foot inside your Hearth."),
        GLOW("A raid's creatures glow, so none of them hides at your walls."),
        SHED("The Ore Shed finds more nuggets in what it crushes."),
        RAID("You hear of a raid a day sooner."),
        HOMEWARD("The peal Homeward takes you home sooner."),
        SMELT("The Smeltery's Emberkits smelt faster."),
        SIGHT("You see in the dark inside your Hearth."),
        BREATH("You don't run out of air under water inside your Hearth."),
        FEATHER("A fall doesn't hurt you inside your Hearth."),
        HASTE("You dig and build faster inside your Hearth."),
        WARMTH("Fire and lava don't burn you inside your Hearth."),
        MIGHT("You hit harder inside your Hearth."),
        GUARD("You take less harm inside your Hearth.");

        public final String words;

        Voice(String words) {
            this.words = words;
        }
    }

    public record Def(int no, String id, String name, Rank rank, Source source, Voice voice) {
        /** "#07". */
        public String number() {
            return (no < 10 ? "#0" : "#") + no;
        }

        public boolean built() {
            return voice != Voice.NONE;
        }
    }

    private static final String[] TABLE = {
            "00|seed|The Seed Bell|S|SEED|NONE",
            "01|oath|The Oath Bell|A|COURT|GUARD",
            "02|fenwick|Fenwick's Bell|C|PEOPLE|GROWTH",
            "03|registry|The Registry Handbell|E|PEOPLE|HOMEWARD",
            "04|fourth|The Fourth|A|PROVING|GUARD",
            "05|ghost|The Ghost Lantern Bell|C|VEIL|SIGHT",
            "06|archive|The Archive Bell|C|GATE_WORLD|SIGHT",
            "07|ember|The Ember Bell|E|OWN|REST",
            "08|hill|The Hill Bell|B|PEOPLE|STRIDE",
            "09|founders|The Founders' Bell|C|PEOPLE|SMELT",
            "10|boulder|The Boulder Bell|A|WILDS|GUARD",
            "11|stampede|The Stampede Bell|C|WILDS|STRIDE",
            "12|nook|The Nook Bell|E|OWN|KIN",
            "13|falling|The Falling Bell|B|LUCIDIUM|FEATHER",
            "14|sung_iron|The Sung Iron Bell|C|DEEP|SMELT",
            "15|mirror|The Mirror Bell|B|GATE_WORLD|GUARD",
            "16|island|The Island Bell|B|ABOVE|FEATHER",
            "17|wren|The Wren Bell|E|WILDS|GROWTH",
            "18|third|The Third|B|PROVING|MIGHT",
            "19|black_water|The Black Water Bell|A|DESCENT|BREATH",
            "20|market|The Market Bell|B|PEOPLE|KIN",
            "21|barrow|The Barrow Bell|E|WILDS|SHED",
            "22|cloud|The Cloud Bell|B|ABOVE|GROWTH",
            "23|sorrow|The Sorrow Bell|S|COURT|REST",
            "24|wind|The Wind Bell|C|ABOVE|STRIDE",
            "25|herald|The Herald's Bell|S|GATE_WORLD|MIGHT",
            "26|mere|The Mere Bell|E|WILDS|GROWTH",
            "27|wake|The Wake Bell|B|VEIL|REST",
            "28|deepglass|The Deepglass Bell|A|DEEP|GLOW",
            "29|whisper|The Whisper Bell|C|VEIL|WARD",
            "30|toll|The Toll Bell|B|GATE_WORLD|RAID",
            "31|thaw|The Thaw Bell|B|WILDS|BREATH",
            "32|ford|The Ford Bell|B|PEOPLE|BREATH",
            "33|star|The Star Bell|S|ABOVE|GLOW",
            "34|wayside|The Wayside Bell|E|WILDS|STRIDE",
            "35|mill|The Mill Bell|C|PEOPLE|GROWTH",
            "36|true|The True Bell|S|DEEP|NONE",
            "37|counting|The Counting Bell|B|GATE_WORLD|HASTE",
            "38|siege|The Siege Bell|B|BIG_DAY|GLOW",
            "39|nightmare|The Nightmare's Bell|B|LUCIDIUM|GLOW",
            "40|tenor|The Tenor|D|PROVING|RAID",
            "41|sleeper|The Sleeper's Bell|B|GATE_WORLD|SIGHT",
            "42|silence|The Silence Bell|S|COURT|WARD",
            "43|daydream|The Daydream Bell|C|LUCIDIUM|GROWTH",
            "44|lintel|The Lintel Bell|D|OWN|REST",
            "45|sung_copper|The Sung Copper Bell|C|DEEP|SMELT",
            "46|founding|The Founding Bell|D|OWN|WARD",
            "47|echo|The Echo Bell|B|DESCENT|SIGHT",
            "48|roost|The Roost Bell|C|PEOPLE|STRIDE",
            "49|sung_gold|The Sung Gold Bell|B|DEEP|SHED",
            "50|night|The Night Bell|D|BIG_DAY|GLOW",
            "51|starmetal|The Starmetal Bell|A|DEEP|STRIDE",
            "52|watch|The Watch Bell|D|PEOPLE|WARD",
            "53|halo|The Halo Bell|B|ABOVE|FEATHER",
            "54|eclipse|The Eclipse Bell|A|WILDS|SIGHT",
            "55|curfew|The Curfew Bell|D|PEOPLE|STRIDE",
            "56|weight|The Weight Bell|A|DESCENT|HASTE",
            "57|glass|The Glass Bell|C|GATE_WORLD|WARMTH",
            "58|crypt|The Crypt Bell|D|GATE_WORLD|GLOW",
            "59|hunger|The Hunger Bell|S|COURT|GROWTH",
            "60|second|The Second|C|PROVING|RAID",
            "61|spore|The Spore Bell|D|GATE_WORLD|GROWTH",
            "62|crystal|The Crystal Bell|C|DESCENT|HASTE",
            "63|ember_mine|The Ember Mine Bell|D|GATE_WORLD|SHED",
            "64|lucid|The Lucid Bell|A|LUCIDIUM|SIGHT",
            "65|storm|The Storm Bell|A|ABOVE|RAID",
            "66|hive|The Hive Bell|D|GATE_WORLD|GROWTH",
            "67|furnace|The Furnace Bell|C|DESCENT|WARMTH",
            "68|root|The Root Bell|D|GATE_WORLD|KIN",
            "69|lullaby|The Lullaby Bell|C|LUCIDIUM|REST",
            "70|frost|The Frost Bell|B|GATE_WORLD|REST",
            "71|cairn|The Cairn Bell|D|WILDS|WARD",
            "72|grief|The Grief Bell|A|COURT|KIN",
            "73|spire|The Spire Bell|D|WILDS|STRIDE",
            "74|square|The Square Bell|C|PEOPLE|REST",
            "75|harbour|The Harbour Bell|B|PEOPLE|BREATH",
            "76|reed|The Reed Bell|D|WILDS|KIN",
            "77|crown|The Crown Bell|S|COURT|MIGHT",
            "78|fifth|The Fifth|S|PROVING|MIGHT",
            "79|garden|The Garden Bell|D|DESCENT|GROWTH",
            "80|gear|The Gear Bell|C|GATE_WORLD|HASTE",
            "81|shepherd|The Shepherd's Bell|C|PEOPLE|KIN",
            "82|drowned_choir|The Drowned Choir's Bell|D|DESCENT|REST",
            "83|bourdon|The Bourdon|S|PROVING|GUARD",
            "84|bone|The Bone Bell|B|DESCENT|WARD",
            "85|dreaming|The Dreaming Bell|D|LUCIDIUM|KIN",
            "86|leviathan|The Leviathan's Bell|A|WILDS|FEATHER",
            "87|flood|The Flood Bell|C|WILDS|BREATH",
            "88|spirit|The Spirit Bell|D|VEIL|WARD",
            "89|shallows|The Shallows Bell|D|DESCENT|BREATH",
            "90|veil|The Veil Bell|A|VEIL|SIGHT",
            "91|thorn|The Thorn Bell|C|WILDS|GLOW",
            "92|throne|The Throne Bell|S|COURT|GUARD",
            "93|crag|The Crag Bell|C|WILDS|STRIDE",
            "94|ash|The Ash Bell|A|COURT|WARMTH",
            "95|fen|The Fen Bell|C|WILDS|REST",
            "96|mourner|The Mourner's Bell|B|VEIL|KIN",
            "97|lantern|The Lantern Bell|C|GATE_WORLD|GLOW",
            "98|sky|The Sky Bell|A|GATE_WORLD|FEATHER",
            "99|deepest|The Deepest Bell|S|DESCENT|HASTE"
    };

    public static final List<Def> ALL;
    private static final Map<String, Def> BY_ID = new HashMap<>();

    static {
        Def[] out = new Def[100];
        for (String row : TABLE) {
            String[] f = row.split("\\|");
            Def d = new Def(Integer.parseInt(f[0]), f[1], f[2], Rank.valueOf(f[3]), Source.valueOf(f[4]), Voice.valueOf(f[5]));
            out[d.no()] = d;
            BY_ID.put(d.id(), d);
        }
        ALL = List.of(out);
    }

    public static Def of(int no) {
        return ALL.get(no);
    }

    public static @Nullable Def of(String id) {
        return BY_ID.get(id);
    }

    // ---- the song ---------------------------------------------------------------------------------------------------

    /**
     * The song, one step per bell (bell #n rings at step n): which of the fifteen tower bells (D major pentatonic, D3 to
     * B5) plays it. Ten phrases of ten; with every bell it's a whole tune, with a few it's a handful of notes and gaps.
     */
    public static final int[] MELODY = {
            5, 7, 8, 7, 5, 4, 5, 7, 5, 0,
            5, 7, 8, 10, 9, 8, 7, 8, 5, 3,
            8, 9, 10, 12, 10, 9, 8, 7, 8, 5,
            5, 7, 8, 7, 5, 4, 3, 4, 5, 0,
            10, 12, 13, 12, 10, 9, 10, 12, 10, 8,
            8, 9, 10, 9, 8, 7, 5, 7, 8, 5,
            3, 4, 5, 7, 8, 7, 5, 4, 3, 0,
            10, 11, 12, 13, 14, 13, 12, 10, 9, 10,
            8, 10, 12, 10, 8, 7, 5, 7, 8, 5,
            5, 7, 8, 7, 5, 4, 3, 1, 2, 0};

    /** Ticks between the song's steps: a hundred steps take 20 seconds. */
    public static final int STEP = 4;

    // ---- peals ------------------------------------------------------------------------------------------------------

    /** A set of bells that, held together, is a power you ring from your handbell (GOALS part 1, "Peals"). */
    public record Peal(String id, String name, String words, List<String> bells) {
        public boolean complete(java.util.function.Predicate<String> has) {
            for (String b : bells) if (!has.test(b)) return false;
            return true;
        }
    }

    public static final List<Peal> PEALS = List.of(
            new Peal("homeward", "Homeward", "Takes you home to your Hearth.", List.of("ember", "wren", "wayside")),
            new Peal("gather", "Gather", "Calls your beasts to the yard.", List.of("nook", "founding", "root")),
            new Peal("stillness", "Stillness", "Stills a raid's creatures at your walls for a while.", List.of("tenor", "night", "watch")));

    public static @Nullable Peal peal(String id) {
        for (Peal p : PEALS) if (p.id().equals(id)) return p;
        return null;
    }
}
