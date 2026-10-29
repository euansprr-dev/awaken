package dev.awaken.bells;

import dev.awaken.progression.Rank;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The hundred bells (SPEC §43.7; GOALS part 1): each numbered 00 to 99 with its name, rank, where it comes from and its
 * voice (what it does while it hangs). Every bell's number is its note's place in the song. Stage 4 builds the first
 * thirty (their voice isn't {@link Voice#NONE}); the rest are named and ranked so the Book can show their empty slots.
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
        SMELT("The Smeltery's Emberkits smelt faster.");

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
            "01|oath|The Oath Bell|A|COURT|NONE",
            "02|fenwick|Fenwick's Bell|C|PEOPLE|NONE",
            "03|registry|The Registry Handbell|E|PEOPLE|HOMEWARD",
            "04|fourth|The Fourth|A|PROVING|NONE",
            "05|ghost|The Ghost Lantern Bell|C|VEIL|NONE",
            "06|archive|The Archive Bell|C|GATE_WORLD|NONE",
            "07|ember|The Ember Bell|E|OWN|REST",
            "08|hill|The Hill Bell|B|PEOPLE|NONE",
            "09|founders|The Founders' Bell|C|PEOPLE|NONE",
            "10|boulder|The Boulder Bell|A|WILDS|NONE",
            "11|stampede|The Stampede Bell|C|BIG_DAY|NONE",
            "12|nook|The Nook Bell|E|OWN|KIN",
            "13|falling|The Falling Bell|B|LUCIDIUM|NONE",
            "14|sung_iron|The Sung Iron Bell|C|DEEP|SMELT",
            "15|mirror|The Mirror Bell|B|GATE_WORLD|NONE",
            "16|island|The Island Bell|B|ABOVE|NONE",
            "17|wren|The Wren Bell|E|WILDS|GROWTH",
            "18|third|The Third|B|PROVING|NONE",
            "19|black_water|The Black Water Bell|A|DESCENT|NONE",
            "20|market|The Market Bell|B|PEOPLE|NONE",
            "21|barrow|The Barrow Bell|E|WILDS|SHED",
            "22|cloud|The Cloud Bell|B|ABOVE|NONE",
            "23|sorrow|The Sorrow Bell|S|COURT|NONE",
            "24|wind|The Wind Bell|C|ABOVE|NONE",
            "25|herald|The Herald's Bell|S|GATE_WORLD|NONE",
            "26|mere|The Mere Bell|E|WILDS|GROWTH",
            "27|wake|The Wake Bell|B|VEIL|NONE",
            "28|deepglass|The Deepglass Bell|A|DEEP|GLOW",
            "29|whisper|The Whisper Bell|C|VEIL|NONE",
            "30|toll|The Toll Bell|B|GATE_WORLD|NONE",
            "31|thaw|The Thaw Bell|B|WILDS|NONE",
            "32|ford|The Ford Bell|B|PEOPLE|NONE",
            "33|star|The Star Bell|S|ABOVE|NONE",
            "34|wayside|The Wayside Bell|E|WILDS|STRIDE",
            "35|mill|The Mill Bell|C|PEOPLE|NONE",
            "36|true|The True Bell|S|DEEP|NONE",
            "37|counting|The Counting Bell|B|GATE_WORLD|NONE",
            "38|siege|The Siege Bell|B|BIG_DAY|NONE",
            "39|nightmare|The Nightmare's Bell|B|LUCIDIUM|NONE",
            "40|tenor|The Tenor|D|PROVING|RAID",
            "41|sleeper|The Sleeper's Bell|B|GATE_WORLD|NONE",
            "42|silence|The Silence Bell|S|COURT|NONE",
            "43|daydream|The Daydream Bell|C|LUCIDIUM|NONE",
            "44|lintel|The Lintel Bell|D|OWN|REST",
            "45|sung_copper|The Sung Copper Bell|C|DEEP|SMELT",
            "46|founding|The Founding Bell|D|OWN|WARD",
            "47|echo|The Echo Bell|B|DESCENT|NONE",
            "48|roost|The Roost Bell|C|PEOPLE|NONE",
            "49|sung_gold|The Sung Gold Bell|B|DEEP|SHED",
            "50|night|The Night Bell|D|BIG_DAY|GLOW",
            "51|starmetal|The Starmetal Bell|A|DEEP|STRIDE",
            "52|watch|The Watch Bell|D|PEOPLE|WARD",
            "53|halo|The Halo Bell|B|ABOVE|NONE",
            "54|eclipse|The Eclipse Bell|A|BIG_DAY|NONE",
            "55|curfew|The Curfew Bell|D|PEOPLE|STRIDE",
            "56|weight|The Weight Bell|A|DESCENT|NONE",
            "57|glass|The Glass Bell|C|GATE_WORLD|NONE",
            "58|crypt|The Crypt Bell|D|GATE_WORLD|GLOW",
            "59|hunger|The Hunger Bell|S|COURT|NONE",
            "60|second|The Second|C|PROVING|RAID",
            "61|spore|The Spore Bell|D|GATE_WORLD|GROWTH",
            "62|crystal|The Crystal Bell|C|DESCENT|NONE",
            "63|ember_mine|The Ember Mine Bell|D|GATE_WORLD|SHED",
            "64|lucid|The Lucid Bell|A|LUCIDIUM|NONE",
            "65|storm|The Storm Bell|A|ABOVE|NONE",
            "66|hive|The Hive Bell|D|GATE_WORLD|GROWTH",
            "67|furnace|The Furnace Bell|C|DESCENT|NONE",
            "68|root|The Root Bell|D|GATE_WORLD|KIN",
            "69|lullaby|The Lullaby Bell|C|LUCIDIUM|NONE",
            "70|frost|The Frost Bell|B|GATE_WORLD|NONE",
            "71|cairn|The Cairn Bell|D|WILDS|WARD",
            "72|grief|The Grief Bell|A|COURT|NONE",
            "73|spire|The Spire Bell|D|WILDS|STRIDE",
            "74|square|The Square Bell|C|PEOPLE|NONE",
            "75|harbour|The Harbour Bell|B|PEOPLE|NONE",
            "76|reed|The Reed Bell|D|WILDS|KIN",
            "77|crown|The Crown Bell|S|COURT|NONE",
            "78|fifth|The Fifth|S|PROVING|NONE",
            "79|garden|The Garden Bell|D|DESCENT|GROWTH",
            "80|gear|The Gear Bell|C|GATE_WORLD|NONE",
            "81|shepherd|The Shepherd's Bell|C|PEOPLE|NONE",
            "82|drowned_choir|The Drowned Choir's Bell|D|DESCENT|REST",
            "83|bourdon|The Bourdon|S|PROVING|NONE",
            "84|bone|The Bone Bell|B|DESCENT|NONE",
            "85|dreaming|The Dreaming Bell|D|LUCIDIUM|KIN",
            "86|leviathan|The Leviathan's Bell|A|WILDS|NONE",
            "87|flood|The Flood Bell|C|BIG_DAY|NONE",
            "88|spirit|The Spirit Bell|D|VEIL|WARD",
            "89|shallows|The Shallows Bell|D|DESCENT|NONE",
            "90|veil|The Veil Bell|A|VEIL|NONE",
            "91|thorn|The Thorn Bell|C|WILDS|GLOW",
            "92|throne|The Throne Bell|S|COURT|NONE",
            "93|crag|The Crag Bell|C|WILDS|STRIDE",
            "94|ash|The Ash Bell|A|COURT|NONE",
            "95|fen|The Fen Bell|C|WILDS|REST",
            "96|mourner|The Mourner's Bell|B|VEIL|NONE",
            "97|lantern|The Lantern Bell|C|GATE_WORLD|GLOW",
            "98|sky|The Sky Bell|A|GATE_WORLD|NONE",
            "99|deepest|The Deepest Bell|S|DESCENT|NONE"
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
