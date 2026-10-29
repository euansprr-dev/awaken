package dev.awaken.mobs.client.anim;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A hero body as plain data: bones (vanilla ModelPart pivots and rotations), cubes (with packed UVs), look bones, the
 * ability-to-move map and keyframe clips (vanilla AnimationDefinition semantics: rotation keys in degrees, position
 * keys in posVec units, scale keys as factors). Parsed from {@link HeroData}, which tools/art/specs/heroes.py writes
 * from the same designs that paint the textures and render the previews.
 *
 * <p>No Minecraft classes here, so the dedicated server and GameTests can load and check every rig; the client turns
 * it into a LayerDefinition and baked animations ({@code mobs.client.model.HeroModel}).
 */
public final class HeroRig {
    public record Cube(int u, int v, float x, float y, float z, int w, int h, int d, boolean mirror, float inflate) {}

    public record Bone(String name, int parent, float px, float py, float pz, float rx, float ry, float rz, List<Cube> cubes) {}

    public record Key(float t, float x, float y, float z, boolean catmull) {}

    public enum Target { POS, ROT, SCALE }

    public record Track(String bone, Target target, List<Key> keys) {}

    public record Clip(String name, float length, boolean loop, List<Track> tracks) {}

    public record Look(String bone, float yaw, float pitch) {}

    /**
     * A secondary part that trails the body (kit bodies, SPEC §35.10): how much of its parents' turn it lags behind
     * (gain), how fast it swings back (hz) and how quickly the swing dies (damping, 0..1).
     */
    public record Spring(String bone, float gain, float hz, float damping) {}

    public final String id;
    public final int texW, texH;
    public final boolean translucent;
    /** Blocks travelled per cycle of the walk and run clips (gaits are locked to distance, so feet never slide). */
    public final float walkStride, runStride;
    /** Ground speed (blocks per tick) where the run starts to blend in, and where it is complete. */
    public final float runFrom, runFull;
    public final float shadow;
    public final List<Bone> bones;
    public final Map<String, Clip> clips;
    /** Ability name (as in the bestiary) to move name; the clips are {@code <move>_windup} and {@code <move>_strike}. */
    public final Map<String, String> moves;
    public final List<Look> look;
    public final List<Spring> springs;
    private final Map<String, Integer> boneIndex;

    private HeroRig(String id, int texW, int texH, boolean translucent, float walkStride, float runStride, float runFrom, float runFull,
                    float shadow, List<Bone> bones, Map<String, Clip> clips, Map<String, String> moves, List<Look> look, List<Spring> springs) {
        this.id = id;
        this.texW = texW;
        this.texH = texH;
        this.translucent = translucent;
        this.walkStride = walkStride;
        this.runStride = runStride;
        this.runFrom = runFrom;
        this.runFull = runFull;
        this.shadow = shadow;
        this.bones = List.copyOf(bones);
        this.clips = clips;
        this.moves = moves;
        this.look = List.copyOf(look);
        this.springs = List.copyOf(springs);
        this.boneIndex = new HashMap<>();
        for (int i = 0; i < bones.size(); i++) boneIndex.put(bones.get(i).name(), i);
    }

    public int bone(String name) {
        Integer i = boneIndex.get(name);
        return i == null ? -1 : i;
    }

    public @Nullable Clip clip(String name) {
        return clips.get(name);
    }

    /** The move an ability plays, or null (the generic wind-up and strike then). */
    public @Nullable String move(String ability) {
        return moves.get(ability);
    }

    // ---- the registry -------------------------------------------------------------------------------------------

    private static @Nullable Map<String, HeroRig> all;

    public static synchronized Map<String, HeroRig> all() {
        if (all == null) {
            Map<String, HeroRig> m = new LinkedHashMap<>();
            for (String[] src : HeroData.SOURCES) {
                HeroRig r = parse(src);
                m.put(r.id, r);
            }
            for (String[] src : Fauna3Data.SOURCES) { // P3 bodies (tools/art/specs/fauna3.py)
                HeroRig r = parse(src);
                m.put(r.id, r);
            }
            for (String[] src : RitualData.SOURCES) { // the Ritual of the Bell's cast (tools/art/specs/ritual_art.py)
                HeroRig r = parse(src);
                m.put(r.id, r);
            }
            all = java.util.Collections.unmodifiableMap(m);
        }
        return all;
    }

    public static @Nullable HeroRig get(String id) {
        return all().get(id);
    }

    // ---- parsing ------------------------------------------------------------------------------------------------

    /** Parses one rig's source (the chunks are concatenated; one record per line). */
    public static HeroRig parse(String[] chunks) {
        String id = null;
        int tw = 64, th = 64;
        boolean translucent = false;
        float walk = 2, run = 4, runFrom = 0.16F, runFull = 0.32F, shadow = 0.8F;
        List<String> names = new ArrayList<>();
        List<Object[]> boneDefs = new ArrayList<>();
        List<List<Cube>> cubes = new ArrayList<>();
        Map<String, Clip> clips = new LinkedHashMap<>();
        Map<String, String> moves = new LinkedHashMap<>();
        List<Look> look = new ArrayList<>();
        List<Spring> springs = new ArrayList<>();
        String clipName = null;
        float clipLen = 0;
        boolean clipLoop = false;
        List<Track> tracks = null;
        for (String chunk : chunks) {
            for (String raw : chunk.split("\n")) {
                String ln = raw.strip();
                if (ln.isEmpty()) continue;
                String[] t = ln.split("\\s+");
                switch (t[0]) {
                    case "rig" -> {
                        id = t[1];
                        tw = Integer.parseInt(t[2]);
                        th = Integer.parseInt(t[3]);
                        translucent = t[4].equals("1");
                        walk = f(t[5]);
                        run = f(t[6]);
                        runFrom = f(t[7]);
                        runFull = f(t[8]);
                        shadow = f(t[9]);
                    }
                    case "bone" -> {
                        int parent = t[2].equals("-") ? -1 : names.indexOf(t[2]);
                        if (!t[2].equals("-") && parent < 0) throw new IllegalArgumentException(id + ": bone " + t[1] + " before its parent " + t[2]);
                        names.add(t[1]);
                        boneDefs.add(new Object[]{t[1], parent, f(t[3]), f(t[4]), f(t[5]), f(t[6]), f(t[7]), f(t[8])});
                        cubes.add(new ArrayList<>());
                    }
                    case "cube" -> cubes.getLast().add(new Cube(Integer.parseInt(t[1]), Integer.parseInt(t[2]), f(t[3]), f(t[4]), f(t[5]),
                            Integer.parseInt(t[6]), Integer.parseInt(t[7]), Integer.parseInt(t[8]), t[9].equals("1"), f(t[10])));
                    case "look" -> look.add(new Look(t[1], f(t[2]), f(t[3])));
                    case "move" -> moves.put(t[1].replace('_', ' '), t[2]);
                    case "spring" -> springs.add(new Spring(t[1], f(t[2]), f(t[3]), f(t[4])));
                    case "clip" -> {
                        if (clipName != null) clips.put(clipName, new Clip(clipName, clipLen, clipLoop, List.copyOf(tracks)));
                        clipName = t[1];
                        clipLen = f(t[2]);
                        clipLoop = t[3].equals("1");
                        tracks = new ArrayList<>();
                    }
                    case "track" -> {
                        Target target = switch (t[2]) {
                            case "pos" -> Target.POS;
                            case "rot" -> Target.ROT;
                            default -> Target.SCALE;
                        };
                        int n = Integer.parseInt(t[3]);
                        List<Key> keys = new ArrayList<>(n);
                        for (int k = 0; k < n; k++) {
                            int o = 4 + k * 5;
                            keys.add(new Key(f(t[o]), f(t[o + 1]), f(t[o + 2]), f(t[o + 3]), t[o + 4].equals("1")));
                        }
                        if (tracks == null) throw new IllegalArgumentException(id + ": track outside a clip");
                        tracks.add(new Track(t[1], target, List.copyOf(keys)));
                    }
                    default -> throw new IllegalArgumentException(id + ": unknown record " + t[0]);
                }
            }
        }
        if (clipName != null) clips.put(clipName, new Clip(clipName, clipLen, clipLoop, List.copyOf(tracks)));
        if (id == null) throw new IllegalArgumentException("rig without a header");
        List<Bone> bones = new ArrayList<>();
        for (int i = 0; i < boneDefs.size(); i++) {
            Object[] b = boneDefs.get(i);
            bones.add(new Bone((String) b[0], (Integer) b[1], (Float) b[2], (Float) b[3], (Float) b[4], (Float) b[5], (Float) b[6], (Float) b[7],
                    List.copyOf(cubes.get(i))));
        }
        return new HeroRig(id, tw, th, translucent, walk, run, runFrom, runFull, shadow, bones, java.util.Collections.unmodifiableMap(clips),
                java.util.Collections.unmodifiableMap(moves), look, springs);
    }

    private static float f(String s) {
        return Float.parseFloat(s);
    }

    /**
     * What vanilla's bake would refuse, plus layout sanity: every animated or look bone exists (bake throws
     * otherwise), keys are in order and inside their clip, every cube's UV rectangle fits the texture, the basic clips
     * exist, and every move has its two clips. Empty when the rig is sound.
     */
    public List<String> problems() {
        List<String> out = new ArrayList<>();
        for (Clip c : clips.values()) {
            if (c.length() <= 0) out.add(id + "/" + c.name() + ": length " + c.length());
            for (Track tr : c.tracks()) {
                if (bone(tr.bone()) < 0) out.add(id + "/" + c.name() + ": no bone " + tr.bone());
                float last = -1;
                for (Key k : tr.keys()) {
                    if (k.t() < last - 1e-4F) out.add(id + "/" + c.name() + "/" + tr.bone() + ": keys out of order");
                    if (k.t() < -1e-4F || k.t() > c.length() + 1e-4F) out.add(id + "/" + c.name() + "/" + tr.bone() + ": key at " + k.t());
                    last = k.t();
                }
                if (tr.keys().isEmpty()) out.add(id + "/" + c.name() + "/" + tr.bone() + ": empty track");
            }
        }
        for (Look l : look) if (bone(l.bone()) < 0) out.add(id + ": look bone " + l.bone() + " missing");
        for (Spring sp : springs) {
            if (bone(sp.bone()) < 0) out.add(id + ": spring bone " + sp.bone() + " missing");
            if (sp.hz() <= 0 || sp.damping() < 0 || sp.damping() > 1) out.add(id + ": spring " + sp.bone() + " needs hz > 0 and damping 0..1");
        }
        for (Bone b : bones) {
            for (Cube c : b.cubes()) {
                int fw = 2 * (c.d() + c.w()), fh = c.d() + c.h();
                if (c.u() < 0 || c.v() < 0 || c.u() + fw > texW || c.v() + fh > texH) out.add(id + "/" + b.name() + ": cube UV outside " + texW + "x" + texH);
            }
        }
        for (String need : List.of("idle", "hurt", "death")) if (!clips.containsKey(need)) out.add(id + ": no " + need + " clip");
        if (!clips.containsKey("walk") && !clips.containsKey("fly")) out.add(id + ": neither walk nor fly");
        for (Map.Entry<String, String> m : moves.entrySet()) {
            String mv = m.getValue();
            if (mv.equals("roar") || mv.equals("warn")) {
                if (!clips.containsKey(mv)) out.add(id + ": move " + m.getKey() + " plays missing clip " + mv);
                continue;
            }
            if (!clips.containsKey(mv + "_windup") || !clips.containsKey(mv + "_strike")) out.add(id + ": move " + m.getKey() + " lacks " + mv + "_windup/_strike");
        }
        return out;
    }
}
