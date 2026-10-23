package dev.awaken.mobs.entity;

import dev.awaken.core.api.MobRole;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.mobs.MobSounds;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.client.anim.HeroRig;
import dev.awaken.mobs.def.Ability;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.FaunaRole;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.def.Model;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The hero bodies and the creatures' voices: every named beast (and the owl and the bogwick) wears its own rig, the
 * rigs are sound (what vanilla's bake would refuse, checked on plain data so the server can run it), the moves they
 * animate are real abilities, the textures exist at the rig's size, every overworld creature's sounds are registered
 * with sounds.json entries and files, and a beast's body is cued by its real ability runner (wind-up, then strike)
 * and takes its longer fall.
 */
public final class HeroTests {
    private HeroTests() {}

    /** The creatures this pass reworked the textures of (hero bodies have their own check). */
    static final List<String> RETEXTURED = List.of("honey_bear", "scree_marmot");

    public static void register() {
        AwakenTests.register("heroes_beasts_wear_their_rigs", 20, h -> {
            List<String> problems = new ArrayList<>();
            int heroes = 0;
            for (MobDef d : Bestiary.all()) {
                if (d.fauna == FaunaRole.BEAST && !d.model.hero()) problems.add(d.id + " is a named beast on a borrowed body (" + d.model + ")");
                if (!d.model.hero()) continue;
                heroes++;
                HeroRig rig = HeroRig.get(d.id);
                if (rig == null) {
                    problems.add(d.id + ": no rig");
                    continue;
                }
                problems.addAll(rig.problems());
                if (!d.model.ownTexture()) problems.add(d.id + ": a hero body must use its own texture");
                List<String> abilities = FaunaMob.moveNames(d);
                for (String a : rig.moves.keySet()) if (!abilities.contains(a)) problems.add(d.id + ": rig animates \"" + a + "\", which it never uses");
                String sig = FaunaMob.SIGNATURES.get(d.id);
                if (d.fauna == FaunaRole.BEAST) {
                    if (sig == null || !abilities.contains(sig)) problems.add(d.id + ": signature move " + sig + " is not one of its abilities");
                    else if (rig.move(sig) == null) problems.add(d.id + ": no clip for its signature move " + sig);
                    for (String need : List.of("roar", "warn", "bite", "windup", "strike")) if (rig.clip(need) == null) problems.add(d.id + ": no " + need + " clip");
                    if (rig.clip("walk") == null && rig.clip("fly") == null) problems.add(d.id + ": it neither walks nor flies");
                }
            }
            h.assertTrue(heroes >= 11, "nine beasts, the owl and the bogwick wear hero bodies (" + heroes + ")");
            h.assertTrue(Bestiary.get("hollow_owl").model == Model.OWL, "the Hollow Owl is an owl");
            h.assertTrue(Bestiary.get("bogwick").model == Model.FIREFLY, "the Bogwick is a firefly");
            h.assertTrue(problems.isEmpty(), String.join("; ", problems));
            h.succeed();
        });

        AwakenTests.register("heroes_textures_exist", 20, h -> {
            List<String> problems = new ArrayList<>();
            for (MobDef d : Bestiary.all()) {
                if (!d.model.hero()) continue;
                HeroRig rig = HeroRig.get(d.id);
                for (String suffix : List.of("", "_glow")) {
                    int[] size = pngSize("/assets/awaken/textures/entity/mobs/" + d.id + suffix + ".png");
                    if (size == null) problems.add(d.id + suffix + ".png missing");
                    else if (rig != null && (size[0] != rig.texW || size[1] != rig.texH)) problems.add(d.id + suffix + ".png is " + size[0] + "x" + size[1] + ", the rig wants " + rig.texW + "x" + rig.texH);
                }
            }
            for (String id : RETEXTURED) {
                for (String suffix : List.of("", "_glow")) if (pngSize("/assets/awaken/textures/entity/mobs/" + id + suffix + ".png") == null) problems.add(id + suffix + ".png missing");
            }
            h.assertTrue(problems.isEmpty(), String.join("; ", problems));
            h.succeed();
        });

        AwakenTests.register("fauna_sounds_registered", 20, h -> {
            List<String> problems = new ArrayList<>();
            JsonObject sounds = null;
            try (InputStream in = HeroTests.class.getResourceAsStream("/assets/awaken/sounds.json")) {
                if (in == null) problems.add("no assets/awaken/sounds.json on the classpath");
                else sounds = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (IOException | RuntimeException e) {
                problems.add("sounds.json unreadable: " + e);
            }
            int count = 0;
            for (MobDef d : Bestiary.all()) {
                if (!d.isFauna()) continue;
                List<String> kinds = new ArrayList<>(MobSounds.FAUNA_KINDS);
                if (d.fauna == FaunaRole.BEAST) kinds.add("roar");
                for (String kind : kinds) {
                    String name = "fauna." + d.id + "." + kind;
                    count++;
                    if (MobSounds.fauna(d.id, kind) == null) problems.add(name + " not registered");
                    if (!BuiltInRegistries.SOUND_EVENT.containsKey(dev.awaken.Awaken.id(name))) problems.add(name + " not in the sound registry");
                    if (sounds == null) continue;
                    if (!sounds.has(name)) {
                        problems.add(name + " has no sounds.json entry");
                        continue;
                    }
                    var list = sounds.getAsJsonObject(name).getAsJsonArray("sounds");
                    if (list == null || list.isEmpty()) problems.add(name + " has no sounds");
                    else for (var s : list) {
                        String file = s.isJsonObject() ? s.getAsJsonObject().get("name").getAsString() : s.getAsString();
                        boolean event = s.isJsonObject() && s.getAsJsonObject().has("type") && s.getAsJsonObject().get("type").getAsString().equals("event");
                        if (!event && file.startsWith("awaken:") && HeroTests.class.getResource("/assets/awaken/sounds/" + file.substring(7) + ".ogg") == null) {
                            problems.add(name + ": missing file " + file);
                        }
                    }
                }
            }
            h.assertTrue(count > 130, "every overworld creature has its voice (" + count + " sounds)");
            h.assertTrue(problems.isEmpty(), problems.size() + " problems: " + String.join("; ", problems.subList(0, Math.min(12, problems.size()))));
            h.succeed();
        });

        AwakenTests.register("heroes_cue_encoding", 20, h -> {
            MobDef d = Bestiary.get("briar_sow");
            List<String> names = FaunaMob.moveNames(d);
            h.assertTrue(names.indexOf("Bramble Charge") == 0 && names.size() >= 6, "moves in phase order, each once: " + names);
            int cue = (3 + 1) | FaunaMob.CUE_STRIKE << 8 | 77 << 11;
            h.assertTrue(FaunaMob.cueSlot(cue) == 3 && FaunaMob.cueStage(cue) == FaunaMob.CUE_STRIKE, "slot and stage decode");
            h.assertTrue(FaunaMob.cueSlot(FaunaMob.CUE_ROAR << 8) == -1, "a roar has no move slot");
            h.succeed();
        });

        // A real fight: the beast's ability runner winds up (the body cues the wind-up), the move fires (the strike
        // follows), and when it dies it takes its longer fall before it is removed.
        AwakenTests.registerIsolated("heroes_cue_follows_abilities", 400, h -> {
            ServerLevel level = h.getLevel();
            BlockPos at = h.absolutePos(new BlockPos(0, WildTalentTests.UP, 0));
            WildTalentTests.floor(level, at, 9, Blocks.GRASS_BLOCK);
            MobDef d = Bestiary.get("briar_sow");
            AwakenMob e = Mobs.spawn(level, d, Vec3.atBottomCenterOf(at), 3, new MobRole(d.tier.role, 3, d.theme.id, Optional.empty(), ""), Set.of());
            h.assertTrue(e instanceof NamedBeast, "a named beast");
            NamedBeast beast = (NamedBeast) e;
            Pig pig = EntityTypes.PIG.create(level, EntitySpawnReason.MOB_SUMMONED);
            pig.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 7.5, 0, 0);
            pig.setNoAi(true);
            pig.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000.0);
            pig.setHealth(1000.0F);
            level.addFreshEntity(pig);
            beast.setTarget(pig);
            int[] seen = {0, 0, -1, -1}; // wind-up seen, strike seen (after it), kill tick, slot of the wind-up
            int[] tick = {0};
            h.succeedWhen(() -> {
                tick[0]++;
                if (beast.getTarget() == null && beast.isAlive()) beast.setTarget(pig);
                int cue = beast.cue();
                int stage = FaunaMob.cueStage(cue);
                if (seen[0] == 0 && stage == FaunaMob.CUE_WINDUP) {
                    seen[0] = 1;
                    seen[3] = FaunaMob.cueSlot(cue);
                    Ability a = beast.runner() == null ? null : beast.runner().active();
                    h.assertTrue(a != null && FaunaMob.moveNames(d).indexOf(a.name()) == seen[3], "the wind-up cue names the ability being wound up");
                }
                if (seen[0] == 1 && seen[1] == 0 && stage == FaunaMob.CUE_STRIKE) {
                    seen[1] = 1;
                    h.assertTrue(FaunaMob.cueSlot(cue) == seen[3], "the strike is the same move");
                    beast.hurtServer(level, level.damageSources().generic(), 1.0E6F);
                    seen[2] = tick[0];
                }
                h.assertTrue(seen[1] == 1, "waiting for a wind-up and its strike");
                if (!beast.isRemoved()) {
                    h.assertTrue(beast.isDeadOrDying(), "it died");
                    h.assertTrue(false, "still falling at " + (tick[0] - seen[2]) + " ticks");
                }
                h.assertTrue(tick[0] - seen[2] >= NamedBeast.DEATH_TICKS - 2, "a beast's fall lasts its death clip (" + (tick[0] - seen[2]) + " ticks)");
                pig.discard();
                WildTalentTests.wipe(level, at.offset(-9, -1, -9), at.offset(9, 3, 9));
            });
        });
    }

    /** Width and height of a PNG on the classpath (from its IHDR), or null if it is missing. */
    static int[] pngSize(String path) {
        try (InputStream in = HeroTests.class.getResourceAsStream(path)) {
            if (in == null) return null;
            byte[] b = in.readNBytes(24);
            if (b.length < 24) return null;
            int w = ((b[16] & 255) << 24) | ((b[17] & 255) << 16) | ((b[18] & 255) << 8) | (b[19] & 255);
            int hgt = ((b[20] & 255) << 24) | ((b[21] & 255) << 16) | ((b[22] & 255) << 8) | (b[23] & 255);
            return new int[]{w, hgt};
        } catch (IOException e) {
            return null;
        }
    }
}
