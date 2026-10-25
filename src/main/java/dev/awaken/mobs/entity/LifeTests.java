package dev.awaken.mobs.entity;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.mobs.client.anim.HeroRig;
import dev.awaken.mobs.client.life.LifeBones;
import dev.awaken.mobs.client.life.LifeBones.Role;
import dev.awaken.mobs.client.life.LifeProfile;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.def.Model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The life layer's data (SPEC §23), checked on plain data like the hero rigs: every creature on one of our own bodies
 * gets a temperament, every body with ears or a tail gets springs on them (the rigs, and the naming of our hand-built
 * bodies), and every creature that blinks has a way to: eyelid bones, eye bones, or a closed-eye texture.
 */
public final class LifeTests {
    private LifeTests() {}

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
    }
}
