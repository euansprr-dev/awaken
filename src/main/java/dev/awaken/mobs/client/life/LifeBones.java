package dev.awaken.mobs.client.life;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What each bone of a body is, read from its name alone (SPEC §23): the life layer is driven by bone names and a
 * creature's temperament, never by per-creature code. {@code ear_l}, {@code hear_r} and {@code fin_ear_l} are ears,
 * {@code otail2} the second link of a tail, {@code mane0} a hanging part, {@code left_arm} an arm, and so on.
 *
 * <p>Plain data (no Minecraft classes), so GameTests check every rig's binding on the server like {@code HeroRig}.
 */
public final class LifeBones {
    private LifeBones() {}

    public enum Role {
        NONE, BODY, CHEST, NECK, HEAD, JAW, EYE, LID,
        /** Springy parts that stand up: ears, tufts, crests, plumes. */
        EAR,
        /** Thin feelers: antennae, whiskers, a lure on its rod. */
        ANTENNA,
        /** Parts trailing behind: tails, flukes, streamers. */
        TAIL,
        /** Parts hanging down: manes, beards, tentacles, skirts, lamps, reeds, a bell's clapper. */
        DANGLE,
        /** Parts held out to the side: wings and their tips. */
        WING,
        /** Small fins and gills. */
        FIN,
        ARM, LEG;

        /** Whether the role gets a damped spring. */
        public boolean springy() {
            return this == EAR || this == ANTENNA || this == TAIL || this == DANGLE || this == WING || this == FIN || this == ARM;
        }
    }

    private static final Set<String> HEADS = Set.of("head", "bhead", "chead", "dhead", "hhead", "mhead", "thead", "whead", "ghead", "ohead");
    private static final Set<String> JAWS = Set.of("jaw", "mjaw", "tjaw", "hjaw", "mouth");
    private static final Set<String> EARS = Set.of("ear", "hear", "tuft", "crest", "plume", "sail");
    private static final Set<String> ANTENNAE = Set.of("antenna", "cant", "tant", "feeler", "whisk", "palp", "rod", "filament", "esca");
    private static final Set<String> TAILS = Set.of("tail", "otail", "ttail", "wtail", "mtail", "gtail", "fluke", "fan", "caudal", "streamer", "stinger");
    private static final Set<String> DANGLES = Set.of("mane", "beard", "ruff", "shag", "skirt", "tent", "drip", "silk", "reed", "cattail",
            "bramble", "sprig", "lamp", "cup", "moss", "frill", "wfrill", "forelock", "clapper", "robe", "garden", "plant", "leaf",
            "cape", "tassel", "sleeve", "chain");
    private static final Set<String> WINGS = Set.of("wing", "cwing", "bwing", "wingtip");
    private static final Set<String> FINS = Set.of("fin", "dorsal", "anal", "pec", "mpec", "mdorsal", "manal", "gill");
    private static final Set<String> ARMS = Set.of("arm", "harm", "fore", "hfore", "forearm");
    private static final Set<String> LEGS = Set.of("leg", "shin", "thigh", "foot", "hoof", "hock", "knee", "talon", "hind", "fleg", "hleg",
            "tleg", "dleg", "cleg", "wleg", "oleg", "bleg", "hthigh", "hshin", "dshin", "hfoot", "thoof", "tshin", "wfoot", "tfoot", "root");
    private static final Set<String> BODIES = Set.of("body", "torso", "trunk", "bell", "gbody");
    private static final Set<String> CHESTS = Set.of("chest", "abdomen", "belly", "sac", "thorax");
    /** Name parts that say nothing alone: such a bone takes its parent's springy role (a wing's tip, a tail's end). */
    private static final Set<String> GENERIC = Set.of("tip", "end", "a", "b", "c", "d", "lower", "upper", "outer", "inner");

    /** The bone's name in lower case, without its side ({@code _l}, {@code _right}, {@code left_}...). */
    static String stem(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.startsWith("left_")) n = n.substring(5);
        else if (n.startsWith("right_")) n = n.substring(6);
        for (String suf : new String[]{"_left", "_right", "_l", "_r", "_fl", "_fr", "_hl", "_hr"}) {
            if (n.endsWith(suf) && n.length() > suf.length()) return n.substring(0, n.length() - suf.length());
        }
        return n;
    }

    /** The alphabetic head of each part of the name: {@code tent0b} gives {@code tent}, {@code cant2} {@code cant}. */
    static List<String> tokens(String name) {
        List<String> out = new ArrayList<>(3);
        for (String t : stem(name).split("_")) {
            int k = 0;
            while (k < t.length() && Character.isLetter(t.charAt(k))) k++;
            if (k > 0) out.add(t.substring(0, k));
        }
        return out;
    }

    /** -1 for the right side, +1 for the left (model space has the left at +x), 0 for the middle. */
    public static int side(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.startsWith("left_") || n.endsWith("_left") || n.endsWith("_l") || n.endsWith("_fl") || n.endsWith("_hl")) return 1;
        if (n.startsWith("right_") || n.endsWith("_right") || n.endsWith("_r") || n.endsWith("_fr") || n.endsWith("_hr")) return -1;
        return 0;
    }

    /** The role a name alone implies (NONE when it says nothing; see {@link #bind} for parents and roots). */
    public static Role role(String name) {
        List<String> t = tokens(name);
        if (t.isEmpty()) return Role.NONE;
        String a = t.getFirst();
        if (t.contains("ear") || t.contains("hear")) return Role.EAR; // fin_ear_l
        if (HEADS.contains(a)) return Role.HEAD;
        if (a.equals("neck")) return Role.NECK;
        if (JAWS.contains(a)) return Role.JAW;
        if (a.equals("eye") || a.equals("meye") || a.equals("eyes")) return Role.EYE;
        if (a.equals("lid") || a.equals("eyelid")) return Role.LID;
        if (EARS.contains(a)) return Role.EAR;
        if (ANTENNAE.contains(a)) return Role.ANTENNA;
        if (TAILS.contains(a)) return Role.TAIL;
        if (DANGLES.contains(a)) return Role.DANGLE;
        if (WINGS.contains(a) || a.startsWith("wing")) return Role.WING;
        if (FINS.contains(a)) return Role.FIN;
        if (ARMS.contains(a)) return Role.ARM;
        if (LEGS.contains(a)) return Role.LEG;
        if (BODIES.contains(a)) return Role.BODY;
        if (CHESTS.contains(a)) return Role.CHEST;
        return Role.NONE;
    }

    /** Whether a hind leg (for a scratch): {@code leg_hl}, {@code hleg_l}, {@code leg_hind_left}, {@code hind_l}. */
    static boolean hind(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.contains("hind") || n.startsWith("hleg") || n.endsWith("_hl") || n.endsWith("_hr") || n.startsWith("hthigh");
    }

    /** One bone as the binder sees it. */
    public record Bone(String name, int parent) {}

    /**
     * A body's binding: each bone's role and side, which bone breathes, which spring links follow which. Indices are
     * into the bone list it was made from; parents always come before their children.
     */
    public record Binding(List<Bone> bones, Role[] roles, int[] sides, int body, int head, int neck, int jaw, int[] eyes, int[] lids,
                          int[] chests, int[] springs, int[] springParent, int[] springDepth, int[] hindLegs, int[] arms) {
        public int springCount() {
            return springs.length;
        }

        /** How many springs carry a role (the GameTest: every rig with ears or a tail has them). */
        public int springsOf(Role r) {
            int n = 0;
            for (int s : springs) if (roles[s] == r) n++;
            return n;
        }

        public boolean has(Role r) {
            for (Role x : roles) if (x == r) return true;
            return false;
        }
    }

    /** Binds a body from its bones (parents first). */
    public static Binding bind(List<Bone> bones) {
        int n = bones.size();
        Role[] roles = new Role[n];
        int[] sides = new int[n];
        boolean[] hasChild = new boolean[n];
        for (Bone b : bones) if (b.parent() >= 0) hasChild[b.parent()] = true;
        for (int i = 0; i < n; i++) {
            Bone b = bones.get(i);
            Role r = role(b.name());
            List<String> t = tokens(b.name());
            boolean generic = t.isEmpty() || GENERIC.contains(t.getFirst());
            if ((r == Role.NONE || generic) && b.parent() >= 0 && roles[b.parent()].springy()) r = roles[b.parent()];
            // the snail's foot is its body: a root bone carrying others is a body whatever it's called
            if (b.parent() < 0 && hasChild[i] && (r == Role.LEG || r == Role.NONE)) r = Role.BODY;
            roles[i] = r;
            sides[i] = side(b.name());
        }
        int body = -1, head = -1, neck = -1, jaw = -1;
        List<Integer> eyes = new ArrayList<>(), lids = new ArrayList<>(), chests = new ArrayList<>(), springs = new ArrayList<>(),
                hind = new ArrayList<>(), arms = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            switch (roles[i]) {
                case BODY -> { if (body < 0) body = i; }
                case HEAD -> { if (head < 0) head = i; }
                case NECK -> { if (neck < 0) neck = i; }
                case JAW -> { if (jaw < 0) jaw = i; }
                case EYE -> eyes.add(i);
                case LID -> lids.add(i);
                case CHEST -> chests.add(i);
                case LEG -> { if (hind(bones.get(i).name()) && bones.get(i).parent() >= 0 && roles[bones.get(i).parent()] != Role.LEG) hind.add(i); }
                default -> { }
            }
            if (roles[i] == Role.ARM && (bones.get(i).parent() < 0 || roles[bones.get(i).parent()] != Role.ARM)) arms.add(i);
            if (roles[i].springy()) springs.add(i);
        }
        // a body without a "body" bone breathes through its first chest bone, else its first root
        if (body < 0 && !chests.isEmpty()) body = chests.getFirst();
        if (body < 0) for (int i = 0; i < n; i++) if (bones.get(i).parent() < 0) { body = i; break; }
        int[] sp = toArray(springs);
        int[] spParent = new int[sp.length], spDepth = new int[sp.length];
        for (int k = 0; k < sp.length; k++) {
            spParent[k] = -1;
            int p = bones.get(sp[k]).parent();
            for (int j = 0; j < k; j++) {
                if (sp[j] == p && roles[p] == roles[sp[k]]) {
                    spParent[k] = j;
                    spDepth[k] = spDepth[j] + 1;
                    break;
                }
            }
        }
        return new Binding(List.copyOf(bones), roles, sides, body, head, neck, jaw, toArray(eyes), toArray(lids), toArray(chests), sp, spParent,
                spDepth, toArray(hind), toArray(arms));
    }

    private static int[] toArray(List<Integer> l) {
        int[] a = new int[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.get(i);
        return a;
    }
}
