package dev.awaken.mobs.entity;

import dev.awaken.citizens.client.CityFaunaData;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.living.forms.FormRigs;
import dev.awaken.mobs.client.anim.HeroRig;
import dev.awaken.mobs.client.life.Flight;
import dev.awaken.mobs.client.life.LegRig;
import dev.awaken.mobs.client.life.Legs;
import dev.awaken.mobs.client.life.LifeBones;
import dev.awaken.mobs.client.life.LifeBones.Role;
import dev.awaken.mobs.client.life.LifeProfile;
import dev.awaken.mobs.client.life.MotionProfile;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.def.Model;
import dev.awaken.wildlife.client.WildFaunaData;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The life layer's data (SPEC §23), checked on plain data like the hero rigs: every creature on one of our own bodies
 * gets a temperament, every body with ears or a tail gets springs on them (the rigs, and the naming of our hand-built
 * bodies), and every creature that blinks has a way to: eyelid bones, eye bones, or a closed-eye texture. The motion
 * layer's feet: every body with a gait finds its two-bone legs, can reach where they stand, and its IK puts each foot
 * where it's asked.
 */
public final class LifeTests {
    private LifeTests() {}

    /** Four-legged walkers (the forms' grazers, the hoofed beasts): forelegs break forward, hind legs back (SPEC §23.1). */

    /** Our hand-built bodies' part names (mobs.client.model: AwModels, FaunaModels, WildModels), "name:parent". */
    static final Map<Model, String> HAND_BUILT = Map.ofEntries(
            Map.entry(Model.SERPENT, "head jaw:head seg0 seg1:seg0 seg2:seg1 seg3:seg2 seg4:seg3 seg5:seg4"),
            Map.entry(Model.LISTENER, "body head ear_left:head ear_right:head arm_right arm_left leg_right leg_left"),
            Map.entry(Model.BELL, "leg_right leg_left body arm_right arm_left bell clapper:bell"),
            Map.entry(Model.LANTERN, "robe arm_right arm_left head core:head"),
            Map.entry(Model.SHADE, "robe torso head arm_right arm_left halo"),
            Map.entry(Model.MOTH, "body head:body antenna_left:head antenna_right:head wing_left:body wing_right:body hind_left:body hind_right:body"),
            Map.entry(Model.STAG, "body neck:body head:neck ear_left:head ear_right:head leg_front_left:body leg_front_right:body leg_hind_left:body leg_hind_right:body tail:body"),
            Map.entry(Model.TORTOISE, "shell plant_a:shell plant_b:shell head leg_front_left leg_front_right leg_hind_left leg_hind_right tail"),
            Map.entry(Model.RAY, "body wing_left:body tip_left:wing_left wing_right:body tip_right:wing_right tail:body tail_end:tail"),
            Map.entry(Model.SCARAB, "body head:body horn:head"),
            Map.entry(Model.LIZARD, "body head:body crest:head sail:body tail:body tail_end:tail leg_front_left:body leg_front_right:body leg_hind_left:body leg_hind_right:body"),
            Map.entry(Model.SNAIL, "foot head:foot eye_left:head eye_right:head shell:foot garden:shell"),
            Map.entry(Model.MUSHROOM, "body cap arm_left arm_right leg_left leg_right"),
            Map.entry(Model.MARMOT, "body head:body ear_left:head ear_right:head tail:body leg_front_left:body leg_front_right:body leg_hind_left:body leg_hind_right:body"));

    /** The crested basilisk's and the mudskipper's legs (one model). */
    private static final String LIZARD_FEET = "body@0,20,0#-3,-2,-6,3,2,6 head:body@0,-0.5,-6#-2.5,-2,-8,2.5,2,0"
            + legs4("3,1,-4", "3,1,4", "0,-1,-1,4,1,1", ":body", ",0,0,0.6");

    /**
     * The hand-built walkers that plant their feet (SPEC §23, "Motion"), by creature id, as their models build them (keep
     * in sync): each bone as {@code name[:parent]@x,y,z[,rx,ry,rz][#box]}, its box its own cubes' (min xyz, max xyz).
     * Only the legs and what they hang from; every leg is one bone (a rigid gait), and the root carries a body whose
     * legs hang off it (LifeRig).
     */
    static final Map<String, String> HAND_FEET = Map.ofEntries(
            Map.entry("frost_stag", "body@0,9,0#-4,-4,-8,4,4,8 neck@0,7,-6.5,0.45,0,0#-2,-9,-2,2,1,2 head:neck@0,-9,0,-0.45,0,0#-2.5,-3,-8,2.5,2,1"
                    + legs4("2.5,12,-6", "2.5,12,6", "-1.5,0,-1.5,1.5,12,1.5", "", "")),
            Map.entry("mossback", "shell@0,20,0#-7,-9,-8,7,0,8 head@0,17,-8#-2,-2,-5,2,2,0"
                    + legs4("5,19,-5.5", "5,19,5.5", "-2,0,-2,2,5,2", "", "")),
            Map.entry("crested_basilisk", LIZARD_FEET),
            Map.entry("mudskipper", LIZARD_FEET), // the basilisk's body at 0.75
            Map.entry("scree_marmot", "body@0,19,1#-3,-3,-5,3,3,5 head:body@0,-1,-5#-2.5,-2.5,-4,2.5,1.5,0"
                    + legs4("2,22,-2", "2,22,4", "-1,0,-1,1,2,1", "", "")),
            Map.entry("glass_scarab", "body@0,22.5,0#-3.5,-4.5,-3.5,3.5,0,4 head:body@0,-1,-3.5#-2,-2,-2,2,1,0"
                    + " leg0:body@2.5,-0.5,-2,0,0.25,0.8#0,0,-0.5,3,1,0.5 leg1:body@-2.5,-0.5,-2,0,-0.25,-0.8#-3,0,-0.5,0,1,0.5"
                    + " leg2:body@2.5,-0.5,0,0,0,0.8#0,0,-0.5,3,1,0.5 leg3:body@-2.5,-0.5,0,0,0,-0.8#-3,0,-0.5,0,1,0.5"
                    + " leg4:body@2.5,-0.5,2,0,-0.25,0.8#0,0,-0.5,3,1,0.5 leg5:body@-2.5,-0.5,2,0,0.25,-0.8#-3,0,-0.5,0,1,0.5"),
            Map.entry("shroomlet", "body@0,21,0#-3,-9,-3,3,0,3 cap:body@0,-8,0#-6,-6,-6,6,0,6"
                    + " leg_left@1.5,21,0#-1,0,-1,1,3,1 leg_right@-1.5,21,0#-1,0,-1,1,3,1"),
            Map.entry("listener", "body@0,8,0#-4,-14,-2,4,0,2 head@0,-6,0#-4,-8,-4,4,0,4"
                    + " leg_right@-2,8,0#-1.5,0,-1.5,1.5,16,1.5 leg_left@2,8,0#-1.5,0,-1.5,1.5,16,1.5"));

    /** Four legs, front and hind pivots on the left (+x; the right mirrors them), the left leg's box (the right's mirrored). */
    private static String legs4(String front, String hind, String box, String parent, String turn) {
        StringBuilder out = new StringBuilder();
        String[] b = box.split(",");
        String mirrored = -Float.parseFloat(b[3]) + "," + b[1] + "," + b[2] + "," + -Float.parseFloat(b[0]) + "," + b[4] + "," + b[5];
        String unturn = turn.isEmpty() ? "" : ",0,0," + -Float.parseFloat(turn.substring(turn.lastIndexOf(',') + 1));
        for (String[] leg : new String[][]{{"front", front}, {"hind", hind}}) {
            String[] at = leg[1].split(",");
            out.append(" leg_").append(leg[0]).append("_left").append(parent).append('@').append(leg[1]).append(turn).append('#').append(box);
            out.append(" leg_").append(leg[0]).append("_right").append(parent).append('@').append(-Float.parseFloat(at[0])).append(',').append(at[1])
                    .append(',').append(at[2]).append(unturn).append('#').append(mirrored);
        }
        return out.toString();
    }

    /** A {@link #HAND_FEET} body: its binding, each bone's rest (x, y, z px; x, y, z rotation) and own box (or null). */
    record HandBody(LifeBones.Binding binding, float[][] pose, float[][] boxes) {}

    static HandBody handBody(String spec) {
        List<LifeBones.Bone> bones = new ArrayList<>();
        List<float[]> pose = new ArrayList<>(), boxes = new ArrayList<>();
        Map<String, Integer> at = new HashMap<>();
        for (String tok : spec.trim().split(" ")) {
            String[] bx = tok.split("#"), pp = bx[0].split("@"), np = pp[0].split(":");
            at.put(np[0], bones.size());
            bones.add(new LifeBones.Bone(np[0], np.length > 1 ? at.get(np[1]) : -1));
            float[] q = new float[6];
            String[] v = pp[1].split(",");
            for (int i = 0; i < v.length; i++) q[i] = Float.parseFloat(v[i]);
            pose.add(q);
            float[] box = null;
            if (bx.length > 1) {
                String[] w = bx[1].split(",");
                box = new float[6];
                for (int i = 0; i < 6; i++) box[i] = Float.parseFloat(w[i]);
            }
            boxes.add(box);
        }
        return new HandBody(LifeBones.bind(bones), pose.toArray(new float[0][]), boxes.toArray(new float[0][]));
    }

    static LifeBones.Binding bindHandBuilt(String spec) {
        List<LifeBones.Bone> bones = new ArrayList<>();
        Map<String, Integer> at = new HashMap<>();
        for (String tok : spec.split(" ")) {
            String[] p = tok.split(":");
            at.put(p[0], bones.size());
            bones.add(new LifeBones.Bone(p[0], p.length > 1 ? at.get(p[1]) : -1));
        }
        return LifeBones.bind(bones);
    }

    static LifeBones.Binding bind(HeroRig r) {
        List<LifeBones.Bone> bones = new ArrayList<>();
        for (HeroRig.Bone b : r.bones) bones.add(new LifeBones.Bone(b.name(), b.parent()));
        return LifeBones.bind(bones);
    }

    /** The wild's and the city's walkers: other modules' rigs whose feet plant through CreatureLife's motion id (SPEC §23.1). */
    static final List<String> FAUNA_WALKERS = List.of("wild_fox", "wild_crow", "city_pigeon", "city_gull", "city_cat", "city_dog");
    private static Map<String, HeroRig> fauna;

    /** A wild or city animal's rig by id, from its module's sources (they never reach HeroRig's own table). */
    static synchronized HeroRig fauna(String id) {
        if (fauna == null) {
            Map<String, HeroRig> m = new HashMap<>();
            for (List<String[]> sources : List.of(WildFaunaData.SOURCES, CityFaunaData.SOURCES)) {
                for (String[] s : sources) {
                    HeroRig r = HeroRig.parse(s);
                    m.put(r.id, r);
                }
            }
            fauna = m;
        }
        return fauna.get(id);
    }

    /** Our bodies' rigs, the forms' (the living module's, SPEC 21.13), or the wild's and the city's animals'. */
    static HeroRig walkerRig(String id) {
        HeroRig r = HeroRig.get(id);
        if (r == null) r = FormRigs.all().get(id);
        return r != null ? r : fauna(id);
    }

    /** Ears and tails by a plain reading of the name, independent of the binder's own rules. */
    static boolean looksLike(String name, String part) {
        String n = name.toLowerCase(Locale.ROOT);
        return part.equals("ear") ? n.matches("(.*_)?h?ear(_.*)?") || n.startsWith("ear") || n.contains("_ear") : n.contains("tail") && !n.contains("cattail");
    }

    public static void register() {
        AwakenTests.register("creature_life_binds", 20, h -> {
            List<String> problems = new ArrayList<>();
            LifeProfile.Data data = LifeProfile.data();
            for (String id : data.creatures().keySet()) if (Bestiary.get(id) == null) problems.add("creatures.json names no creature " + id);
            int bodies = 0;
            for (MobDef d : Bestiary.all()) {
                boolean hero = d.model.hero(), hand = HAND_BUILT.containsKey(d.model);
                if (!hero && !hand) continue;
                bodies++;
                LifeProfile p;
                try {
                    p = LifeProfile.of(d.id, d.fauna != null ? d.fauna.name() : null, d.tier.name());
                } catch (RuntimeException e) {
                    problems.add(d.id + ": " + e.getMessage());
                    continue;
                }
                if (p.breath() <= 0 || p.headSpeed() <= 0 || p.springHz() <= 0 || p.fidgetMax() < p.fidgetMin() || p.blinkMax() < p.blinkMin()) {
                    problems.add(d.id + ": temperament " + p.name() + " out of range");
                }
                LifeBones.Binding b;
                if (hero) {
                    HeroRig r = HeroRig.get(d.id);
                    if (r == null) {
                        problems.add(d.id + ": no rig");
                        continue;
                    }
                    b = bind(r);
                } else {
                    b = bindHandBuilt(HAND_BUILT.get(d.model));
                }
                // a creature that blinks needs eyelids, eye bones, or the closed-eye skin tools/art paints
                if (p.blinks() && b.lids().length == 0 && b.eyes().length == 0
                        && LifeTests.class.getResource("/assets/awaken/textures/entity/mobs/" + d.id + "_blink.png") == null) {
                    problems.add(d.id + " blinks (" + p.name() + ") but has no eyelids, eye bones or " + d.id + "_blink.png");
                }
            }
            // every rig with ears or a tail gets springs on them, and a head the slow look can turn
            int springs = 0;
            for (HeroRig r : HeroRig.all().values()) {
                LifeBones.Binding b = bind(r);
                springs += b.springCount();
                boolean ears = false, tail = false;
                for (HeroRig.Bone bone : r.bones) {
                    ears |= looksLike(bone.name(), "ear");
                    tail |= looksLike(bone.name(), "tail");
                }
                if (ears && b.springsOf(Role.EAR) == 0) problems.add(r.id + ": ears without springs");
                if (tail && b.springsOf(Role.TAIL) == 0) problems.add(r.id + ": a tail without springs");
                if (!r.look.isEmpty() && b.head() < 0 && b.neck() < 0 && b.body() < 0) problems.add(r.id + ": looks around with no head");
            }
            for (Map.Entry<Model, String> e : HAND_BUILT.entrySet()) {
                LifeBones.Binding b = bindHandBuilt(e.getValue());
                if (e.getValue().contains("ear_") && b.springsOf(Role.EAR) < 2) problems.add(e.getKey() + ": ears without springs");
                if (e.getValue().contains("tail") && b.springsOf(Role.TAIL) == 0) problems.add(e.getKey() + ": a tail without springs");
                if (e.getValue().contains("wing_") && b.springsOf(Role.WING) == 0) problems.add(e.getKey() + ": wings without springs");
            }
            if (bindHandBuilt(HAND_BUILT.get(Model.RAY)).springsOf(Role.WING) != 4) problems.add("RAY: a wing's tip doesn't follow its wing");
            if (bindHandBuilt(HAND_BUILT.get(Model.BELL)).springsOf(Role.DANGLE) != 1) problems.add("BELL: the clapper doesn't swing");
            h.assertTrue(bodies >= 40, "the life layer reaches every body of ours (" + bodies + ")");
            h.assertTrue(springs > 200, "the rigs carry springs (" + springs + ")");
            h.assertTrue(problems.isEmpty(), String.join("; ", problems));
            h.succeed();
        });

        AwakenTests.register("creature_motion_feet", 20, h -> {
            List<String> problems = new ArrayList<>();
            int checked = 0;
            Matrix4f m = new Matrix4f(), toParent = new Matrix4f();
            Matrix3f hr = new Matrix3f(), kr = new Matrix3f(), back = new Matrix3f();
            Vector3f e = new Vector3f(), want = new Vector3f(), pivot = new Vector3f();
            int rigid = 0;
            for (MotionProfile mp : MotionProfile.all().values()) {
                if (mp.gait() == null) continue; // flight alone: creature_motion_flight
                // our bodies' rigs, the forms', the wild's and the city's animals', or a hand-built walker's legs
                HeroRig r = walkerRig(mp.id());
                HandBody hand = r == null && HAND_FEET.containsKey(mp.id()) ? handBody(HAND_FEET.get(mp.id())) : null;
                if (r == null && hand == null) {
                    problems.add("motion.json names no rig " + mp.id());
                    continue;
                }
                LifeBones.Binding b = r != null ? bind(r) : hand.binding();
                if (r != null) { // the feet ride a body bone that carries the head (LifeRig binds a rig's legs only then)
                    boolean carries = b.head() < 0 && b.body() >= 0; // a headless body carries it all
                    for (int k = b.head(); k >= 0 && b.body() >= 0; k = b.bones().get(k).parent()) if (k == b.body()) carries = true;
                    if (!carries) problems.add(mp.id() + ": its body bone doesn't carry its head, so its feet can't plant");
                }
                float[][] pose = hand != null ? hand.pose() : new float[r.bones.size()][];
                for (int i = 0; hand == null && i < pose.length; i++) {
                    HeroRig.Bone bone = r.bones.get(i);
                    pose[i] = new float[]{bone.px(), bone.py(), bone.pz(), bone.rx(), bone.ry(), bone.rz()};
                }
                LegRig legs = LegRig.of(mp.gait(), b, pose, i -> hand != null ? hand.boxes()[i] : box(r.bones.get(i)));
                if (legs == null) {
                    problems.add(mp.id() + ": a gait but fewer than two legs");
                    continue;
                }
                if (mp.gait().arms() && legs.n < 4) problems.add(mp.id() + ": it walks on its arms but only " + legs.n + " limbs plant");
                if (hand != null) { // every one-bone leg of a hand-built walker is found
                    int own = 0;
                    for (int i = 0; i < b.bones().size(); i++) if (b.roles()[i] == Role.LEG) own++;
                    if (legs.n != own) problems.add(mp.id() + ": " + legs.n + " of its " + own + " legs plant");
                }
                float ws = r != null ? r.walkStride : 1.0F, rs = r != null ? r.runStride : 1.0F;
                float lift = Math.max(mp.gait().liftWalk(), mp.gait().liftRun());
                for (int i = 0; i < legs.n; i++) {
                    // a straight leg whose thigh ends on its knee's pivot opens a wedge at any bend: it moves as one piece
                    if (!legs.rigid(i) && legs.hides(i) < 20.0F * Mth.DEG_TO_RAD) {
                        problems.add(mp.id() + " leg " + i + ": its knee hides only " + Math.round(legs.hides(i) * Mth.RAD_TO_DEG)
                                + " degrees of bend before its joint opens, so its legs must be rigid");
                    }
                    LegRig.rest(legs.chain(i), pose, m);
                    m.invertAffine(toParent);
                    float[] hp = pose[legs.hipBone(i)];
                    float hx = legs.homeX[i], hz = legs.homeZ[i];
                    // home on its crouch (a straight-legged body always stands a touch sunk), a step's reach ahead and
                    // behind, the top of a step's arc, home with the body sunk low to stalk
                    float s = mp.gait().crouch(), c = s + mp.body().creep();
                    // a stride's planted sweep (a foot lands up to half a stand ahead of home and trails as far behind,
                    // never past reach), walking and running: the hips may drop to it, by no more than the vault's most
                    MotionProfile.Gait g = mp.gait();
                    for (int run = 0; run < 2; run++) {
                        float duty = run == 0 ? g.dutyWalk() : g.dutyRun();
                        float st = run == 0 ? (g.strideWalk() > 0 ? g.strideWalk() : ws)
                                : g.strideRun() > 0 ? g.strideRun() : g.strideWalk() > 0 ? g.strideWalk() : rs;
                        // it lands lead further ahead than half its stand and trails that much less
                        float stand = duty * st * 16.0F;
                        for (int sg = -1; sg <= 1; sg += 2) {
                            float half = Math.min(stand * (0.5F + (sg < 0 ? g.lead() : -g.lead())), g.reach() * 16.0F);
                            float drop = legs.sink(i, hx, 24, hz + sg * half) - s;
                            if (drop > legs.vaultMax + 0.01F) problems.add(mp.id() + " leg " + i + ": a " + (run == 0 ? "walk" : "run")
                                    + " stride of " + st + " blocks sweeps its foot " + half + " px, so the hips must drop " + drop + " px (most " + legs.vaultMax + ")");
                        }
                    }
                    float[][] targets = {{hx, 24 - s, hz}, {hx, 24, hz - 5}, {hx, 24, hz + 5}, {hx, 24 - lift, hz - 2}, {hx, 24 - c, hz}};
                    for (int k = 0; k < targets.length; k++) {
                        float[] tg = targets[k];
                        toParent.transformPosition(tg[0], tg[1], tg[2], want);
                        float dist = want.distance(hp[0], hp[1], hp[2]);
                        if (k == 0 || k == 4) {
                            if (dist > legs.reach(i) * 0.98F) problems.add(mp.id() + " leg " + i + ": can't reach its stance (" + dist + " of " + legs.reach(i) + " px)");
                        }
                        if (legs.rigid(i)) { // one piece: the foot on its target, the leg's top inside the body
                            if (!legs.solveRigid(i, toParent, hp[0], hp[1], hp[2], tg[0], tg[1], tg[2], hr, pivot)) {
                                problems.add(mp.id() + " leg " + i + ": no solve");
                                continue;
                            }
                            Vector3f foot = hr.transform(legs.kneePivot(i).add(legs.restShin(i))).add(pivot);
                            float slid = pivot.distance(hp[0], hp[1], hp[2]);
                            if (dist < legs.length(i) * 0.97F && foot.distance(want) > 0.05F) {
                                problems.add(mp.id() + " leg " + i + ": its foot lands " + foot.distance(want) + " px off its target");
                            }
                            if (pivot.z < hp[2] - legs.room(i, -1) - 0.01F || pivot.z > hp[2] + legs.room(i, 1) + 0.01F
                                    || pivot.y > hp[1] + legs.room(i, 0) + 0.01F || slid > legs.length(i) * 0.7F) {
                                problems.add(mp.id() + " leg " + i + ": its top slides out of the body (" + (pivot.y - hp[1]) + ", " + (pivot.z - hp[2]) + " px)");
                            }
                            hr.getEulerAnglesZYX(e);
                            back.rotationZYX(e.z, e.y, e.x);
                            if (!back.equals(hr, 1e-3F)) problems.add(mp.id() + " leg " + i + ": the hip's turn doesn't survive its Euler angles");
                            continue;
                        }
                        if (!legs.solve(i, toParent, hp[0], hp[1], hp[2], tg[0], tg[1], tg[2], hr, kr)) {
                            problems.add(mp.id() + " leg " + i + ": no solve");
                            continue;
                        }
                        // forward again: the hip's turn carries the knee, the knee's the shin, to the foot
                        Vector3f knee = hr.transform(legs.kneePivot(i)).add(hp[0], hp[1], hp[2]);
                        Vector3f foot = hr.transform(kr.transform(legs.shinTip(i))).add(knee);
                        if (dist > legs.fold(i) + 0.2F && dist < legs.reach(i) - 0.2F && foot.distance(want) > 0.05F) {
                            problems.add(mp.id() + " leg " + i + ": the foot lands " + foot.distance(want) + " px off its target");
                        }
                        // the rotations survive the trip through the part's Euler angles (ZYX, as ModelPart draws them)
                        hr.getEulerAnglesZYX(e);
                        back.rotationZYX(e.z, e.y, e.x);
                        if (!back.equals(hr, 1e-3F)) problems.add(mp.id() + " leg " + i + ": the hip's turn doesn't survive its Euler angles");
                    }
                    checked++;
                    if (legs.rigid(i)) rigid++;
                }
                String splits = walkTrial(mp.gait(), mp.body() != null ? mp.body().twist() : 0.0F, legs, ws, rs);
                if (splits != null) problems.add(mp.id() + ": " + splits);
            }
            int handLegs = 4 + 4 + 4 + 6 + 2 + 2 + 4 + 4; // the frost stag's, mossback's, basilisk's, scarab's, shroomlet's, Listener's, mudskipper's and marmot's
            // the Mycelord's two and the otter's forelegs walk stiff beside them
            int stiffHero = 2 + 2;
            int formWalkers = 4 * 2 + 4 * 2 + 4 * 4; // the zombie, skeleton and creeper forms' one-bone legs
            int faunaRigid = 2 * 6; // the fox's, cat's and dog's forelegs, the crow's, pigeon's and gull's legs
            h.assertTrue(rigid >= 12 * 4 + 4 * 2 + 3 * 4 + handLegs + stiffHero + formWalkers + faunaRigid, "the grazers', the walking forms', chickens', hoofed beasts', hand-built walkers', the Mycelord's, the otter's and the wild and city animals' forelegs are rigid (" + rigid + ")");
            h.assertTrue(checked >= 6 + 4 * 8 + 14 + 4 + 4 + 8 + 12 * 4 + 4 * 2 + 3 * 4 + handLegs + 2 + 4 + 4 + 2 + 2 + formWalkers + 3 * 4 + 3 * 2, "the wild and city animals' legs, the drip lurker's six legs, the form spiders' eight, the thousandfoot's fourteen, the wickling's four, the hushling's legs and the long arms it lopes on, the weaver queen's eight, the form grazers' four and chickens' two, the sow's, the stag's and the ram's four, the hand-built walkers' one-bone legs, the Mycelord's two, the otter's and Mire Mother's four, the Tidesteed's and the cricket's hind pair, the walking forms' (and every other gait's) are checked (" + checked + ")");
            h.assertTrue(problems.isEmpty(), String.join("; ", problems));
            h.succeed();
        });

        // SPEC §23.1, "The wild's and the city's walkers": CreatureLife.rig(root, temperament, motion) plants another
        // module's body by its rig's id; each walker has its gait, its rig in its module's sources, and every leg plants
        AwakenTests.register("creature_fauna_feet", 20, h -> {
            List<String> problems = new ArrayList<>();
            for (String id : FAUNA_WALKERS) {
                MotionProfile mp = MotionProfile.of(id);
                HeroRig r = fauna(id);
                if (mp == null || mp.gait() == null || r == null) {
                    problems.add(id + (r == null ? ": no rig in its module's sources" : ": no gait in motion.json"));
                    continue;
                }
                LifeBones.Binding b = bind(r);
                int own = 0; // a leg is a limb whose parent isn't one
                for (int i = 0; i < b.bones().size(); i++) {
                    int p = b.bones().get(i).parent();
                    if (b.roles()[i] == Role.LEG && (p < 0 || b.roles()[p] != Role.LEG)) own++;
                }
                float[][] pose = new float[r.bones.size()][];
                for (int i = 0; i < pose.length; i++) {
                    HeroRig.Bone bone = r.bones.get(i);
                    pose[i] = new float[]{bone.px(), bone.py(), bone.pz(), bone.rx(), bone.ry(), bone.rz()};
                }
                LegRig legs = LegRig.of(mp.gait(), b, pose, i -> box(r.bones.get(i)));
                int n = legs == null ? 0 : legs.n;
                if (n < 2 || n != own) problems.add(id + ": " + n + " of its " + own + " legs plant");
            }
            h.assertTrue(problems.isEmpty(), String.join("; ", problems));
            h.succeed();
        });

        // SPEC §23, "Flight": every flight names a creature and, on a rig, bones it has; the beat sweeps down faster than
        // it comes up and never jumps; a turn banks the body and curves the spine into it; a climb noses up and keeps the
        // whole beat, a dive glides; the body rises as its wings sweep below their mean
        AwakenTests.register("creature_motion_flight", 20, h -> {
            List<String> problems = new ArrayList<>();
            int flights = 0;
            for (MotionProfile mp : MotionProfile.all().values()) {
                MotionProfile.Fly f = mp.fly();
                if (f == null) continue;
                flights++;
                HeroRig r = HeroRig.get(mp.id());
                if (r == null) { // a hand-built body: its bones are checked when it binds; here, that the creature exists
                    MobDef d = Bestiary.get(mp.id());
                    if (d == null || (d.model != Model.MOTH && d.model != Model.RAY && d.model != Model.SERPENT))
                        problems.add(mp.id() + ": no rig and no hand-built flier or swimmer of that name");
                    continue;
                }
                List<String> names = new ArrayList<>();
                for (HeroRig.Bone b : r.bones) names.add(b.name());
                for (String[] list : new String[][]{f.wings(), f.tips(), f.spine(), f.fins()})
                    for (String n : list) if (!names.contains(n)) problems.add(mp.id() + " has no bone " + n);
                for (String n : f.wings()) if (LifeBones.side(n) == 0) problems.add(mp.id() + ": wing " + n + " has no side");
            }
            // the beat: top at 0, bottom at DOWN (under half the cycle), smooth through both turns and the wrap
            if (Math.abs(Flight.stroke(0) - 1) > 1e-4F || Math.abs(Flight.stroke(Flight.DOWN) + 1) > 1e-4F || Flight.DOWN >= 0.5F)
                problems.add("the stroke is not down fast from the top, up slow from the bottom");
            for (float u = -1; u < 2; u += 0.01F) {
                if (Math.abs(Flight.stroke(u + 0.005F) - Flight.stroke(u - 0.005F)) > 0.09F) problems.add("the stroke jumps at " + u);
                if (Math.abs(Flight.stroke(u)) > 1 + 1e-4F) problems.add("the stroke leaves -1..1 at " + u);
            }
            // a turn to its right (yaw rising): the body tips right (-x) and the tail curves to the inside of the turn (-x)
            if (!(Flight.bank(1.5F, 1, 0.2F) < 0) || Flight.bank(1.5F, 0, 0.2F) != 0) problems.add("a right turn does not bank it right, or it banks at a standstill");
            if (!(Flight.bend(1.5F, 0.3F) < 0)) problems.add("a right turn does not curve the spine to the right");
            // a climb noses up (pitch < 0) and keeps every beat; a dive noses down and glides; a hover beats
            float up = Flight.climb(2, 2), down = Flight.climb(2, -3);
            if (!(up > 0 && down < 0)) problems.add("the path's climb has the wrong sign");
            if (!(Flight.pitch(up, 0.5F, 0.6F, 0.1F) < 0 && Flight.pitch(down, 0.5F, 0.6F, 0.1F) > 0)) problems.add("a climb does not nose up or a dive down");
            if (Math.abs(Flight.keep(up, 1, 0.2F, 1) - 1) > 1e-4F) problems.add("a climb glides");
            if (Math.abs(Flight.keep(down, 0.5F, 0.2F, 0) - 0.2F) > 1e-4F) problems.add("a dive beats");
            if (Math.abs(Flight.keep(0, 0, 0.2F, 1) - 1) > 1e-4F) problems.add("a hover glides");
            if (!(Flight.keep(0, 1, 0.2F, 1) < 0.25F)) problems.add("a cruise never glides between beats");
            if (!(Flight.pace(up, 0) > Flight.pace(0, 1))) problems.add("a climb does not beat faster than a cruise");
            // wings below their mean (the downstroke's end): the body is up (a dip below 0)
            if (!(Flight.heave(-0.5F, 2) < 0 && Flight.heave(0.5F, 2) > 0)) problems.add("the body does not rise as the wings sweep down");
            h.assertTrue(flights >= 15, "the fliers and swimmers have flights (" + flights + ")");
            h.assertTrue(problems.isEmpty(), String.join("; ", problems));
            h.succeed();
        });
    }

    /**
     * The feet on flat ground through a stand, a sudden walk, a sudden run and a stop (SPEC §23.1, "Starts"): while it
     * moves no planted foot stands further ahead of its home than its place in a steady stride, nor further behind than
     * a step's catch-up allows, and a one-piece leg never swings past 35 degrees walking or 45 running (FA's walk is
     * well inside both). Null when it holds, else the worst.
     */
    static String walkTrial(MotionProfile.Gait g, float twistShare, LegRig rig, float walkStride, float runStride) {
        Legs feet = new Legs(rig, 7, (x, y, z) -> 0.0);
        float sw = g.strideWalk() > 0 ? g.strideWalk() : walkStride;
        float sr = g.strideRun() > 0 ? g.strideRun() : g.strideWalk() > 0 ? g.strideWalk() : runStride;
        float dt = 1.0F / 60.0F;
        double z = 0;
        // the turn as a client sees a standing mob's (vanilla's tickHeadTurn): the body holds until its facing is 50
        // degrees ahead, then is dragged along 50 behind and stays there; the body bone twists its share of the head's turn
        float facing = 0, body = 0;
        String worst = null;
        float most = 0;
        // seconds, cycles a second (0 stands), how much it runs, degrees a second it turns on the spot
        float[][] legs = {{1.0F, 0, 0, 0}, {3.0F, 1, 0, 0}, {2.0F, 1, 1, 0}, {1.5F, 0, 1, 0}, {1.5F, 0, 0, 120}, {2.0F, 0, 0, 0}};
        for (int p = 0; p < legs.length; p++) {
            float[] leg = legs[p];
            float run = leg[2], st = Mth.lerp(run, sw, sr), duty = Mth.lerp(run, g.dutyWalk(), g.dutyRun());
            boolean turning = leg[3] != 0, after = p == legs.length - 1;
            for (int k = 0, n = Math.round(leg[0] / dt); k < n; k++) {
                z += st * leg[1] * dt;
                facing += leg[3] * dt;
                body = Math.max(body, facing - 50.0F);
                float tw = Mth.clamp((facing - body) * Mth.DEG_TO_RAD * twistShare, -0.35F, 0.35F);
                feet.face = tw * Mth.RAD_TO_DEG;
                feet.update(0, 0, z, body, 1, dt, true, st, run, true);
                boolean settled = after && k >= n - Math.round(0.5F / dt);
                if (leg[1] == 0 && !turning && !settled) continue;
                float ahead = (duty * st * (0.5F + g.lead()) + 0.1F * st) * 16.0F;
                float behind = settled ? g.settle() * 16.0F + 0.75F : turning ? 4.0F * g.settle() * 16.0F + 1.0F
                        : (duty * st * (0.5F - g.lead()) + 0.4F * st) * 16.0F;
                float c = Mth.cos(tw), sn = Mth.sin(tw);
                for (int i = 0; i < rig.n; i++) {
                    if (feet.stepping(i)) continue;
                    // its home under the twisted body (the body bone's yRot in model space)
                    float hx = rig.homeX[i] * c + rig.homeZ[i] * sn, hz = -rig.homeX[i] * sn + rig.homeZ[i] * c;
                    float ox = hx - feet.tx[i], oz = hz - feet.tz[i];
                    // walking: px ahead of its home (model -z is forward); turning or stood after it: px from it any way
                    float off = turning || settled ? -Mth.sqrt(ox * ox + oz * oz) : oz;
                    float over = Math.max(off / ahead, -off / behind);
                    String what = off > 0 ? "lands " + off + " px ahead (most " + ahead + ")"
                            : (turning ? "turning, trails " : settled ? "stood after a turn, " : "trails ") + -off + " px (most " + behind + ")";
                    if (rig.rigid(i)) {
                        float deg = (float) Math.toDegrees(Math.atan2(Math.abs(off), rig.length(i) * (1.0F + g.shoulder())));
                        float limit = turning ? 25.0F : run > 0 ? 45.0F : 35.0F;
                        if (deg / limit > over) {
                            over = deg / limit;
                            what = "swings " + deg + " degrees (most " + limit + ")";
                        }
                    }
                    if (over > 1.0F && over > most) {
                        most = over;
                        worst = "leg " + i + (turning || settled ? " " : run > 0 ? " running " : " walking ") + what;
                    }
                }
            }
        }
        return worst;
    }

    /** A bone's own cubes' box (min xyz, max xyz), or null. */
    static float[] box(HeroRig.Bone bone) {
        if (bone.cubes().isEmpty()) return null;
        float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (HeroRig.Cube c : bone.cubes()) {
            box[0] = Math.min(box[0], c.x());
            box[1] = Math.min(box[1], c.y());
            box[2] = Math.min(box[2], c.z());
            box[3] = Math.max(box[3], c.x() + c.w());
            box[4] = Math.max(box[4], c.y() + c.h());
            box[5] = Math.max(box[5], c.z() + c.d());
        }
        return box;
    }
}
