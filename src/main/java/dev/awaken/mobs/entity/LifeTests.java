package dev.awaken.mobs.entity;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.living.forms.FormRigs;
import dev.awaken.mobs.client.anim.HeroRig;
import dev.awaken.mobs.client.life.Flight;
import dev.awaken.mobs.client.life.LegRig;
import dev.awaken.mobs.client.life.LifeBones;
import dev.awaken.mobs.client.life.LifeBones.Role;
import dev.awaken.mobs.client.life.LifeProfile;
import dev.awaken.mobs.client.life.MotionProfile;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.def.Model;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

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
    private static final Pattern FOUR_LEGS = Pattern.compile("form_(pig|cow|sheep)_\\d|briar_sow|old_antler|stonehorn");

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
            Vector3f e = new Vector3f(), want = new Vector3f();
            for (MotionProfile mp : MotionProfile.all().values()) {
                if (mp.gait() == null) continue; // flight alone: creature_motion_flight
                // our bodies' rigs, or the forms' (the living module's, SPEC 21.13)
                HeroRig r = HeroRig.get(mp.id()) != null ? HeroRig.get(mp.id()) : FormRigs.all().get(mp.id());
                if (r == null) {
                    problems.add("motion.json names no rig " + mp.id());
                    continue;
                }
                LifeBones.Binding b = bind(r);
                // the feet ride a body bone that carries the head (LifeRig binds legs only then)
                boolean carries = b.head() < 0 && b.body() >= 0; // a headless body carries it all
                for (int k = b.head(); k >= 0 && b.body() >= 0; k = b.bones().get(k).parent()) if (k == b.body()) carries = true;
                if (!carries) problems.add(mp.id() + ": its body bone doesn't carry its head, so its feet can't plant");
                float[][] pose = new float[r.bones.size()][];
                for (int i = 0; i < pose.length; i++) {
                    HeroRig.Bone bone = r.bones.get(i);
                    pose[i] = new float[]{bone.px(), bone.py(), bone.pz(), bone.rx(), bone.ry(), bone.rz()};
                }
                LegRig legs = LegRig.of(mp.gait(), b, pose, i -> box(r.bones.get(i)));
                if (legs == null) {
                    problems.add(mp.id() + ": a gait but fewer than two two-bone legs");
                    continue;
                }
                float lift = Math.max(mp.gait().liftWalk(), mp.gait().liftRun());
                for (int i = 0; i < legs.n; i++) {
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
                        float st = run == 0 ? (g.strideWalk() > 0 ? g.strideWalk() : r.walkStride)
                                : g.strideRun() > 0 ? g.strideRun() : g.strideWalk() > 0 ? g.strideWalk() : r.runStride;
                        float half = Math.min(duty * st * 8.0F, g.reach() * 16.0F);
                        for (int sg = -1; sg <= 1; sg += 2) {
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
                        // four legs on their crouch: the forelegs' knees break forward, the hocks back
                        if (k == 0 && FOUR_LEGS.matcher(mp.id()).matches()) {
                            Vector3f kneeM = m.transformPosition(new Vector3f(knee)), hipM = m.transformPosition(new Vector3f(hp[0], hp[1], hp[2]));
                            Vector3f footM = m.transformPosition(new Vector3f(foot));
                            float t = (kneeM.y - hipM.y) / Math.max(0.01F, footM.y - hipM.y);
                            float bulge = kneeM.z - (hipM.z + (footM.z - hipM.z) * t);
                            if (hipM.z < 0 ? bulge > -0.1F : bulge < 0.1F) {
                                problems.add(mp.id() + " leg " + i + ": its knee breaks " + (bulge < 0 ? "forward" : "back") + " (" + bulge + " px)");
                            }
                        }
                        // the rotations survive the trip through the part's Euler angles (ZYX, as ModelPart draws them)
                        hr.getEulerAnglesZYX(e);
                        back.rotationZYX(e.z, e.y, e.x);
                        if (!back.equals(hr, 1e-3F)) problems.add(mp.id() + " leg " + i + ": the hip's turn doesn't survive its Euler angles");
                    }
                    checked++;
                }
            }
            h.assertTrue(checked >= 6 + 4 * 8 + 14 + 4 + 2 + 8 + 12 * 4 + 4 * 2 + 3 * 4, "the drip lurker's six legs, the form spiders' eight, the thousandfoot's fourteen, the wickling's four, the hushling's two, the weaver queen's eight, the form grazers' four and chickens' two, the sow's, the stag's and the ram's four (and every other gait's) are checked (" + checked + ")");
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
