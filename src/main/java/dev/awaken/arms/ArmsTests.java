package dev.awaken.arms;

import dev.awaken.Awaken;
import dev.awaken.arms.net.ArmsPayloads;
import dev.awaken.core.api.CreatureTag;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.api.Strikes;
import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import dev.awaken.progression.Rank;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Arms' rules and their server path (SPEC §25): the chain and its rhythm, the arc, the damage stack intact, the guard's
 * answers, the latency grace, the dodge's evade and perfect ticks, stances that break, and the world switch.
 */
final class ArmsTests {
    private ArmsTests() {}

    static void register() {
        AwakenTests.register("arms_timeline", 20, ArmsTests::timeline);
        AwakenTests.register("arms_defence_rules", 20, ArmsTests::defenceRules);
        AwakenTests.register("arms_strike_arc", 60, ArmsTests::strikeArc);
        AwakenTests.register("arms_damage_stack", 60, ArmsTests::damageStack);
        AwakenTests.register("arms_deflect", 60, ArmsTests::deflect);
        AwakenTests.register("arms_deflect_latency", 80, ArmsTests::deflectLatency);
        AwakenTests.register("arms_dodge", 60, ArmsTests::dodge);
        AwakenTests.register("arms_posture_break", 60, ArmsTests::postureBreak);
        AwakenTests.register("arms_kill_switch", 40, ArmsTests::killSwitch);
        AwakenTests.register("arms_sounds", 20, ArmsTests::sounds);
        AwakenTests.register("arms_stance_rules", 20, ArmsTests::stanceRules);
        AwakenTests.register("arms_clips", 20, ArmsTests::clips);
        AwakenTests.register("arms_art", 60, ArmsTests::art);
        AwakenTests.register("arms_plunge", 60, ArmsTests::plungeTest);
        AwakenTests.register("arms_assets", 20, ArmsTests::assets);
        AwakenTests.register("arms_hand_pose", 20, ArmsTests::handPose);
        AwakenTests.register("arms_style_traits", 20, ArmsTests::styleTraits);
        AwakenTests.register("arms_weave", 40, ArmsTests::weave);
        AwakenTests.register("arms_lord_forged", 40, ArmsTests::lordForged);
        AwakenTests.register("arms_mastery", 20, ArmsTests::mastery);
        AwakenTests.register("arms_fp_swings", 20, ArmsTests::fpSwings);
        AwakenTests.register("arms_grip", 20, ArmsTests::grip);
        AwakenTests.register("arms_marks", 40, ArmsTests::marks);
        AwakenTests.register("arms_forge", 20, ArmsTests::forge);
    }

    /** At most this many triangle corners in one weapon (6,000 triangles: a few weapons in view cost what a mob does). */
    static final int MESH_BUDGET = 18_000;

    /**
     * Every generated weapon: registered with its row's numbers, fighting and guarding with its style, its recipe
     * loaded, its files on the classpath, its hands drawn by the weapon renderer; its mesh read by the game's own reader,
     * inside the item box, within budget, its normals whole and its texels in the albedo half (tools/art/specs/
     * arms_weapons.py checks the look before writing).
     */
    static void assets(GameTestHelper h) {
        h.assertTrue(ArmsItems.ALL.size() == ArmsData.WEAPONS.size(), "an item per row");
        for (ArmsData.Weapon w : ArmsData.WEAPONS) {
            ItemStack stack = new ItemStack(ArmsItems.ALL.get(ArmsData.WEAPONS.indexOf(w)).get());
            h.assertTrue(ArmsItems.of(stack.getItem()) == w, w.id() + " knows its row");
            h.assertTrue(Arms.styleOf(stack) == Archetypes.get(w.style()) && Arms.guards(stack), w.id() + " fights and guards as a " + w.style());
            double damage = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
                    .filter(m -> m.attribute().is(Attributes.ATTACK_DAMAGE)).mapToDouble(m -> m.modifier().amount()).sum();
            h.assertTrue(Math.abs(damage - w.damage()) < 1e-4 && stack.getMaxDamage() == w.durability() && stack.getRarity() == w.rarity(),
                    w.id() + " carries its row's damage, uses and rarity");
            var tag = switch (w.style()) {
                case Arms.AXE -> ItemTags.AXES;
                case Arms.MAUL -> ItemTags.MACE_ENCHANTABLE;
                case Arms.SPEAR -> ItemTags.SPEARS;
                default -> ItemTags.SWORDS;
            };
            h.assertTrue(stack.is(tag), w.id() + " is in " + tag.location() + " to vanilla (its enchantments)");
            var recipe = ResourceKey.create(Registries.RECIPE, Awaken.id(w.id()));
            boolean crafted = h.getLevel().getServer().getRecipeManager().byKey(recipe).isPresent();
            if (LordForged.byWeapon(w.id()) != null) h.assertTrue(!crafted, w.id() + " only drops (a Lord-forged weapon has no recipe)");
            else h.assertTrue(crafted, w.id() + " has its recipe");
            for (String file : new String[]{"items/" + w.id() + ".json", "models/item/" + w.id() + ".json", "models/item/" + w.id() + "_in_hand.json",
                    "textures/item/arms/" + w.id() + ".png", "textures/item/arms/" + w.id() + "_icon.png", "arms/weapons/" + w.id() + ".mesh"}) {
                h.assertTrue(ArmsTests.class.getResource("/assets/awaken/" + file) != null, w.id() + ": " + file + " is on the classpath");
            }
            com.google.gson.JsonObject fallback = json("/assets/awaken/items/" + w.id() + ".json").getAsJsonObject("model").getAsJsonObject("fallback");
            com.google.gson.JsonObject special = fallback.getAsJsonObject("model");
            h.assertTrue("awaken:weapon".equals(special.get("type").getAsString()) && w.id().equals(special.get("weapon").getAsString()),
                    w.id() + ": the hands draw it with the weapon renderer");
            WeaponMesh mesh;
            try (var in = ArmsTests.class.getResourceAsStream("/assets/awaken/arms/weapons/" + w.id() + ".mesh")) {
                mesh = WeaponMesh.read(in);
            } catch (java.io.IOException ex) {
                throw new IllegalStateException(w.id() + ": its mesh doesn't read: " + ex.getMessage(), ex);
            }
            h.assertTrue(mesh.vertices() <= MESH_BUDGET, w.id() + ": " + mesh.vertices() + " corners (at most " + MESH_BUDGET + ")");
            float[] b = mesh.bounds();
            h.assertTrue(b[0] >= -1 && b[1] >= -1 && b[2] >= -1 && b[3] <= 2 && b[4] <= 2 && b[5] <= 2, w.id() + ": the mesh leaves the item box");
            for (int i = 0; i < mesh.vertices(); i++) {
                float nx = mesh.normal()[i * 3] / 127F, ny = mesh.normal()[i * 3 + 1] / 127F, nz = mesh.normal()[i * 3 + 2] / 127F;
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                float u = mesh.uv()[i * 2], v = mesh.uv()[i * 2 + 1];
                if (len < 0.9F || len > 1.1F || u < 0 || u > 0.5F || v < 0 || v > 1) {
                    h.fail(w.id() + ": corner " + i + " has a broken normal or a texel outside the albedo");
                }
            }
            for (WeaponMesh.Anchor a : mesh.anchors()) h.assertTrue(a.radius() > 0, w.id() + ": a glow with no size");
            // the light that lifts off it: each stream a kind, a rate, a spread, its line on the weapon
            float[] bb = mesh.bounds();
            for (WeaponMesh.Stream st : mesh.streams()) {
                h.assertTrue(st.kind() >= WeaponMesh.EMBER && st.kind() <= WeaponMesh.MOTE && st.rate() > 0 && st.radius() > 0,
                        w.id() + ": a stream with no kind, rate or spread");
                for (int k = 0; k < st.count(); k++) {
                    for (int c = 0; c < 3; c++) {
                        float v = st.points()[k * 3 + c];
                        h.assertTrue(v >= bb[c] - 0.05F && v <= bb[3 + c] + 0.05F, w.id() + ": a stream's point off the weapon");
                    }
                }
            }
            boolean high = w.tier().equals("B") || w.tier().equals("A") || w.tier().equals("S");
            h.assertTrue(high == !mesh.streams().isEmpty(), w.id() + ": light lifts off the high blades (B, A, S) and only them");
        }
        h.succeed();
    }

    private static com.google.gson.JsonObject json(String path) {
        try (var in = ArmsTests.class.getResourceAsStream(path)) {
            return com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** A heavy held through to full charge with the meter full is the Art: the meter spent, its wave reaching past any heavy. */
    static void art(GameTestHelper h) {
        ServerPlayer p = fighter(h, 1.5, 0.5, 0F);
        LivingEntity far = put(h, EntityTypes.ZOMBIE, 1.5, 6.0, 200);
        Archetype.Charge c = longsword().move(Archetype.Move.HEAVY).charge().orElseThrow();
        ArmsState s = ArmsServer.state(p);
        long now = ArmsServer.now(p);
        // not full: a held-through heavy is only a heavy
        s.resonance = ArmsRules.RESONANCE_MAX - 1;
        ArmsServer.onPress(p, new ArmsPayloads.Press(1, ArmsPayloads.PRESS_STRIKE, 0F, 0F, -1));
        ArmsServer.onPress(p, new ArmsPayloads.Press(1, ArmsPayloads.PRESS_CHARGE, 0F, 0F, -1));
        s.chargeStart = now - (c.max() - c.min());
        ArmsServer.onPress(p, new ArmsPayloads.Press(1, ArmsPayloads.PRESS_RELEASE, 0F, 0F, -1));
        h.assertTrue(s.move == Archetype.Move.HEAVY, "short of a full meter, it's a heavy (" + s.move + ")");
        s.clearMove();
        // full: the Art, and the meter is spent
        s.resonance = ArmsRules.RESONANCE_MAX;
        ArmsServer.onPress(p, new ArmsPayloads.Press(2, ArmsPayloads.PRESS_STRIKE, 0F, 0F, -1));
        ArmsServer.onPress(p, new ArmsPayloads.Press(2, ArmsPayloads.PRESS_CHARGE, 0F, 0F, -1));
        s.chargeStart = now - (c.max() - c.min());
        ArmsServer.onPress(p, new ArmsPayloads.Press(2, ArmsPayloads.PRESS_RELEASE, 0F, 0F, -1));
        h.assertTrue(s.move == Archetype.Move.ART && s.resonance == 0, "held through with the meter full: the Art, the meter spent (" + s.move + ", " + s.resonance + ")");
        Archetype.MoveDef def = longsword().move(Archetype.Move.ART);
        h.assertTrue(longsword().move(Archetype.Move.HEAVY).reach() + 0.3 < far.distanceTo(p), "a heavy wouldn't reach it");
        for (int i = 0; i < def.active(); i++) ArmsServer.sweep(p, s, def, s.moveStart + def.windup() + i);
        h.assertTrue(far.getHealth() < 200, "the Art's wave rolls out and strikes it " + String.format("%.1f", far.distanceTo(p)) + " blocks off");
        done(h, p);
    }

    /** A strike in a fall arms a Plunge; it lands all round where the fighter comes down, harder the further the fall. */
    static void plungeTest(GameTestHelper h) {
        ServerPlayer p = fighter(h, 1.5, 1.5, 0F);
        LivingEntity behind = put(h, EntityTypes.ZOMBIE, 1.5, -0.5, 200);
        ArmsState s = ArmsServer.state(p);
        p.setOnGround(false);
        p.fallDistance = 1;
        ArmsServer.onPress(p, new ArmsPayloads.Press(1, ArmsPayloads.PRESS_STRIKE, 0F, 0F, -1));
        h.assertTrue(s.plungeSeq < 0, "a hop is no Plunge (it's the chain's strike)");
        s.clearMove();
        p.fallDistance = 4;
        ArmsServer.onPress(p, new ArmsPayloads.Press(2, ArmsPayloads.PRESS_STRIKE, 0F, 0F, -1));
        h.assertTrue(s.plungeSeq == 2 && s.move == null, "in a fall the strike arms a Plunge and waits for the ground");
        p.setOnGround(true);
        ArmsServer.plunge(p, s, ArmsServer.now(p));
        h.assertTrue(s.move == Archetype.Move.PLUNGE && Math.abs(s.plungePower - ArmsRules.plungePower(4)) < 1e-4, "it lands as the Plunge, powered by the fall");
        runActive(p);
        float dealt = 200 - behind.getHealth();
        float expect = 7F * longsword().move(Archetype.Move.PLUNGE).motion() * ArmsRules.plungePower(4);
        h.assertTrue(Math.abs(dealt - expect) < 0.6F, "all round, even behind, by the fall: dealt " + dealt + ", expected " + expect);
        h.assertTrue(ArmsRules.plungePower(10) > ArmsRules.plungePower(4) && ArmsRules.plungePower(100) <= 2.8F, "further falls land harder, to a cap");
        done(h, p);
    }

    /** Every move has its third-person clip in the generated file, each with a bent twin; held ones loop, the rest end. */
    static void clips(GameTestHelper h) {
        com.google.gson.JsonObject anims;
        try (var in = ArmsTests.class.getResourceAsStream("/" + ArmsClips.FILE)) {
            h.assertTrue(in != null, ArmsClips.FILE + " is on the classpath");
            anims = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("animations");
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
        for (Archetype.Move m : longsword().moves().keySet()) {
            String c = ArmsClips.of(m);
            h.assertTrue(c != null && ArmsClips.ALL.contains(c), m + " has a clip");
        }
        // every style's every move resolves to a clip in the file, its own on its own row (tools/art/specs/arms_moves.py)
        for (Archetypes.Loaded st : Archetypes.all()) {
            for (Archetype.Move m : st.moves().keySet()) {
                String c = ArmsClips.of(st.def().id(), m);
                h.assertTrue(c != null && anims.has(c) && anims.has(c + "_bent"), st.def().id() + " " + m + ": its clip " + c + " is in the file");
            }
        }
        for (String base : ArmsClips.ALL) {
            for (String name : new String[]{base, base + "_bent"}) {
                h.assertTrue(anims.has(name), name + " is missing from " + ArmsClips.FILE);
                var a = anims.getAsJsonObject(name);
                boolean loops = a.has("loop") && a.get("loop").getAsBoolean();
                h.assertTrue(loops == ArmsClips.loops(base), name + (loops ? " loops but should end" : " should loop"));
            }
        }
        h.assertTrue(ArmsClips.dodge(180).equals(ArmsClips.DODGE_BACK) && ArmsClips.dodge(90).equals(ArmsClips.DODGE_RIGHT)
                && ArmsClips.dodge(-90).equals(ArmsClips.DODGE_LEFT), "a dodge's clip follows its way");
        h.succeed();
    }

    /** A stance drains by one rule on both sides; a focus holds while looked at. */
    /**
     * The swing on Hands' view layer lands the blade exactly where Arms' own transform put it: for poses across every
     * move's range (both hands), the view move then the rest place equals the hand moved, rolled and tilted about itself.
     */
    static void handPose(GameTestHelper h) {
        float[] m = new float[6];
        float worst = 0;
        for (int side = -1; side <= 1; side += 2) {
            for (float roll = -150; roll <= 150; roll += 37.5F) {
                for (float tilt = -40; tilt <= 60; tilt += 25) {
                    float hx = side * 0.56F, hy = -0.52F, hz = -0.72F, dx = side * 0.31F, dy = 0.22F, dz = -0.18F;
                    HandPose.viewMove(hx, hy, hz, dx, dy, dz, side * roll, tilt, m);
                    org.joml.Matrix4f want = new org.joml.Matrix4f().translate(hx + dx, hy + dy, hz + dz)
                            .rotateZ((float) Math.toRadians(side * roll)).rotateX((float) Math.toRadians(-tilt));
                    org.joml.Matrix4f got = new org.joml.Matrix4f().translate(m[0], m[1], m[2])
                            .rotateX((float) Math.toRadians(m[3])).rotateY((float) Math.toRadians(m[4])).rotateZ((float) Math.toRadians(m[5]))
                            .translate(hx, hy, hz);
                    for (int c = 0; c < 4; c++) for (int r = 0; r < 4; r++) worst = Math.max(worst, Math.abs(want.get(c, r) - got.get(c, r)));
                }
            }
        }
        h.assertTrue(worst < 1e-4F, "the view move reproduces the hand's pose (worst element off by " + worst + ")");
        HandPose.viewMove(0.56F, -0.52F, -0.72F, 0, 0, 0, 0, 0, m);
        for (float v : m) h.assertTrue(Math.abs(v) < 1e-6F, "at rest the view move adds nothing");
        h.succeed();
    }

    /**
     * Weaves: 36 distinct names; in the beat after an opening a cast costs 30% less, weaves, and is remembered once;
     * the opening is spent; past the beat nothing weaves.
     */
    static void weave(GameTestHelper h) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (int o = ArmsWeaves.DEFLECT; o <= ArmsWeaves.RHYTHM; o++) {
            for (dev.awaken.powers.PowerType.Family f : dev.awaken.powers.PowerType.Family.values()) ids.add(ArmsWeaves.id(o, f));
        }
        h.assertTrue(ids.size() == 36, "36 Weaves, each its own name (" + ids.size() + ")");
        ServerPlayer p = fighter(h, 1.5, 0.5, 0F);
        ArmsState s = ArmsServer.state(p);
        long now = ArmsServer.now(p);
        h.assertTrue(ArmsWeaves.INSTANCE.costScale(p) == 1F, "outside a beat a cast costs what it costs");
        ArmsWeaves.open(p, s, ArmsWeaves.DEFLECT, now, -1);
        h.assertTrue(Math.abs(ArmsWeaves.INSTANCE.costScale(p) - ArmsWeaves.COST) < 1e-6, "in the beat after a deflect it costs 30% less");
        ArmsWeaves.INSTANCE.cast(p, "ENERGY", "HEAT", 1);
        h.assertTrue(p.getData(ArmsContent.MEMORY).weaves().contains("ember_riposte"), "a deflect and a Heat power: Ember Riposte, remembered");
        h.assertTrue(ArmsWeaves.INSTANCE.costScale(p) == 1F && s.opening == 0, "the opening is spent");
        ArmsWeaves.open(p, s, ArmsWeaves.DODGE, now - ArmsWeaves.WINDOW - 1, -1);
        int found = ArmsWeaves.found(p);
        ArmsWeaves.INSTANCE.cast(p, "SHADOW", null, 1);
        h.assertTrue(ArmsWeaves.found(p) == found, "past the beat nothing weaves");
        h.succeed();
    }

    /** Lord-forged: fifteen weapons with their rows, the pity, the reveal to the hand and a Lord's move as the Art. */
    static void lordForged(GameTestHelper h) {
        h.assertTrue(LordForged.ALL.size() == 15, "one for each Gate Lord (" + LordForged.ALL.size() + ")");
        java.util.Set<String> styles = new java.util.HashSet<>();
        for (LordForged.Forged f : LordForged.ALL) {
            ArmsData.Weapon w = ArmsData.WEAPONS.stream().filter(x -> x.id().equals(f.weapon())).findFirst().orElse(null);
            h.assertTrue(w != null && ArmsItems.item(f.weapon()) != null, f.weapon() + " has its row and item");
            h.assertTrue(Archetypes.get(w.style()) != null && w.style().equals(f.weapon().substring(f.weapon().lastIndexOf('_') + 1)),
                    f.weapon() + " fights in its style");
            styles.add(w.style());
            h.assertTrue(LordForged.byBoss("awaken:" + f.lord()) == f, f.lord() + " gives it");
        }
        h.assertTrue(styles.size() == 15, "fifteen Lords, fifteen styles (" + styles.size() + ")");
        h.assertTrue(!LordForged.falls(0, 0.9F) && LordForged.falls(0, 0.1F), "a 35% roll");
        h.assertTrue(LordForged.falls(LordForged.PITY - 1, 0.99F), "certain on the third Lord without one");
        ServerPlayer p = fighter(h, 1.5, 0.5, 0F);
        LordForged.Forged bell = LordForged.byWeapon("bell_warden_maul");
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        LordForged.reveal(p, new ItemStack(ArmsItems.item(bell.weapon())), bell);
        h.assertTrue(LordForged.held(p) == bell, "the reveal puts it in the empty hand");
        var zombie = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(1, 1, 4));
        float before = zombie.getHealth();
        LordForged.unleash(p, bell);
        h.assertTrue(zombie.getHealth() < before && zombie.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS),
                "the Grand Peal strikes what's near and slows it");
        // a Lord's death with the pity due: the weapon comes on the next tick, never inside the death (the kill is
        // tallied first, unarmed while the hand is empty; mobs_boss_kill_counts_and_pity)
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        p.setData(ArmsContent.MEMORY, p.getData(ArmsContent.MEMORY).withLordsWithout(LordForged.PITY - 1));
        var saint = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(3, 1, 4));
        saint.setData(dev.awaken.core.api.CoreApi.MOB_ROLE, new dev.awaken.core.api.MobRole(dev.awaken.core.api.MobRole.Role.BOSS, 5, "",
                java.util.Optional.of("awaken:root_saint"), ""));
        LordForged.onDeath(new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(saint, p.damageSources().playerAttack(p)));
        h.assertTrue(p.getMainHandItem().isEmpty(), "the hand is still empty while the death is tallied");
        h.runAfterDelay(2, () -> {
            h.assertTrue(LordForged.held(p) == LordForged.byWeapon("root_saint_warstaff"), "the next tick it is in the hand");
            h.assertTrue(p.getData(ArmsContent.MEMORY).lordsWithout() == 0, "and the pity starts again");
            h.succeed();
        });
    }

    /**
     * The lab's first-person swings (FpSwings, tools/art/anim_lab.py): keys for every style's every move, each ending
     * at rest; on the hit tick a slash's hand is turned well off its rest, not slid (the old wave); and the view move of
     * any turn is exactly the hand turned about its own place (as HandPose's roll-and-tilt one).
     */
    static void fpSwings(GameTestHelper h) {
        org.joml.Vector3f d = new org.joml.Vector3f();
        org.joml.Quaternionf q = new org.joml.Quaternionf();
        for (Archetypes.Loaded a : Archetypes.all()) {
            FpSwings.Style st = FpSwings.style(a.id());
            h.assertTrue(st != null, a.id() + " has its swings");
            for (var e : a.moves().entrySet()) {
                Archetype.MoveDef def = e.getValue();
                if (def.swing() == null) continue;
                h.assertTrue(FpSwings.sample(st, e.getKey(), 0, true, d, q), a.id() + " " + e.getKey() + " has keys");
                FpSwings.sample(st, e.getKey(), def.total() + 1, true, d, q);
                h.assertTrue(d.length() < 1e-3F && Math.abs(Math.abs(q.w) - 1) < 1e-3F, a.id() + " " + e.getKey() + " ends at rest");
            }
            Archetype.MoveDef s1 = a.move(Archetype.Move.STRIKE_1);
            if (s1 != null && s1.windup() > 0 && s1.swing().from().size() < 4) {
                FpSwings.sample(st, Archetype.Move.STRIKE_1, s1.windup(), true, d, q);
                float turned = (float) Math.toDegrees(2 * Math.acos(Math.min(1, Math.abs(q.w))));
                h.assertTrue(turned > 15F, a.id() + "'s first strike turns the blade into the cut on its hit (" + turned + " degrees)");
            }
        }
        float[] m = new float[6];
        float[] old = new float[6];
        float worst = 0;
        for (float roll = -150; roll <= 150; roll += 50) {
            for (float tilt = -40; tilt <= 60; tilt += 50) {
                q.identity().rotateZ((float) Math.toRadians(roll)).rotateX((float) Math.toRadians(-tilt));
                HandPose.viewMove(0.56F, -0.52F, -0.72F, 0.2F, 0.1F, -0.1F, q, m);
                HandPose.viewMove(0.56F, -0.52F, -0.72F, 0.2F, 0.1F, -0.1F, roll, tilt, old);
                org.joml.Matrix4f a = viewMatrix(m), b = viewMatrix(old);
                for (int c = 0; c < 4; c++) for (int r = 0; r < 4; r++) worst = Math.max(worst, Math.abs(a.get(c, r) - b.get(c, r)));
            }
        }
        h.assertTrue(worst < 1e-3F, "a turn's view move matches the roll-and-tilt one (off by " + worst + ")");
        h.succeed();
    }

    /**
     * The arms that hold a weapon (FirstPerson.View's grips, drawn by Hands): unset, a view is untouched (nothing moves,
     * no arm); set, it asks for the arm; cleared each frame, it is untouched again. Every style knows where its hand
     * closes, near vanilla's resting hand.
     */
    static void grip(GameTestHelper h) {
        dev.awaken.core.api.FirstPerson.View v = new dev.awaken.core.api.FirstPerson.View();
        h.assertTrue(!v.moved() && v.gripMain[3] == 0 && v.gripOff[3] == 0, "an untouched view has no grip and moves nothing");
        dev.awaken.core.api.FirstPerson.View.fist(v.gripMain, 1F, 0.5F, -0.5F, -0.7F);
        h.assertTrue(v.moved() && v.gripMain[3] == 1F && Math.abs(v.gripMain[0] - 0.5F) < 1e-6, "a set grip asks for the arm");
        h.assertTrue(v.fistMain[3] == 0 && !v.hideMain, "a grip is not a fist: the held item still draws");
        v.clear();
        h.assertTrue(!v.moved() && v.gripMain[3] == 0, "cleared each frame");
        for (Archetypes.Loaded a : Archetypes.all()) {
            FpSwings.Style st = FpSwings.style(a.id());
            h.assertTrue(st != null && st.hand().length() < 0.45F, a.id() + " knows where its hand closes (" + (st == null ? "none" : st.hand()) + ")");
        }
        h.succeed();
    }

    private static org.joml.Matrix4f viewMatrix(float[] m) {
        return new org.joml.Matrix4f().translate(m[0], m[1], m[2]).rotateX((float) Math.toRadians(m[3]))
                .rotateY((float) Math.toRadians(m[4])).rotateZ((float) Math.toRadians(m[5]));
    }

    /** Mastery: kills raise a style's step; step I carries the chain's finisher into its mastery strike. */
    static void mastery(GameTestHelper h) {
        h.assertTrue(ArmsMastery.step(0) == 0 && ArmsMastery.step(ArmsMastery.KILLS[0]) == 1 && ArmsMastery.step(100_000) == 5, "five steps from kills");
        Archetypes.Loaded a = longsword();
        Archetype.MoveDef s3 = a.move(Archetype.Move.STRIKE_3);
        long link = 100 + s3.windup() + s3.active() + ArmsRules.MASTERY_LINK;
        ArmsRules.Decision plain = ArmsRules.tap(a, Archetype.Move.STRIKE_3, 100, 2, 2, link, 0);
        h.assertTrue(plain == null || plain.move() == Archetype.Move.STRIKE_1, "unmastered, the finisher ends the chain");
        ArmsRules.Decision on = ArmsRules.tap(a, Archetype.Move.STRIKE_3, 100, 2, 2, link, 1);
        h.assertTrue(on != null && on.move() == Archetype.Move.STRIKE_4 && on.step() == 3 && on.cadence() == 3,
                "mastered, a press after the finisher's blow is the mastery strike, still in full rhythm (" + on + ")");
        ServerPlayer p = fighter(h, 1.5, 0.5, 0F);
        h.assertTrue(ArmsMastery.step(p, a) == 0, "a fresh fighter has no mastery");
        h.assertTrue(ArmsMastery.count(p, a, ArmsMastery.KILLS[1]) == 2 && ArmsMastery.step(p, a) == 2, "kills count and are remembered");
        h.assertTrue(ArmsMastery.slipcutBonus(2) > 0 && ArmsMastery.deflect(2) == 1F && ArmsMastery.artAt(5) < ArmsRules.RESONANCE_MAX,
                "II a longer Slipcut, III the charged deflect, V the Art at 80%");
        h.succeed();
    }

    /** Marks: 48 rows, every trigger with every effect; a weapon's Marks act on their trigger; found weapons carry them. */
    static void marks(GameTestHelper h) {
        h.assertTrue(Marks.all().size() == 48, "48 Marks (" + Marks.all().size() + ")");
        java.util.Set<String> pairs = new java.util.HashSet<>();
        for (Marks.Mark m : Marks.all()) pairs.add(m.trigger() + "/" + m.effect());
        h.assertTrue(pairs.size() == 48, "each trigger with each effect once");
        ServerPlayer p = fighter(h, 1.5, 0.5, 0F);
        ItemStack sword = p.getMainHandItem();
        sword.set(ArmsContent.INSTANCE.get(), Marks.Instance.EMPTY.withMark("weight").withMark("leech").withMark("bite"));
        h.assertTrue(Marks.of(sword).marks().size() == Marks.MAX_MARKS, "at most two Marks");
        var zombie = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(1, 1, 3));
        p.hurtServer(h.getLevel(), p.damageSources().generic(), 6F);
        float hp = p.getHealth();
        Marks.fire(p, Marks.Trigger.HIT, zombie);
        Posture post = zombie.getExistingDataOrNull(ArmsContent.POSTURE);
        h.assertTrue(post != null && post.value > 0, "Weight: a hit's posture");
        h.assertTrue(p.getHealth() > hp, "Leech: a hit heals");
        Marks.fire(p, Marks.Trigger.DEFLECT, zombie);
        var bytes = Marks.Instance.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, Marks.of(sword)).getOrThrow();
        h.assertTrue(Marks.Instance.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, bytes).getOrThrow().equals(Marks.of(sword)), "the component round-trips");
        ItemStack found = MarkedDrops.roll(Rank.C.contentLevel, h.getLevel().getRandom());
        ArmsData.Weapon row = ArmsItems.of(found.getItem());
        h.assertTrue(row != null && row.tier().equals("C") && !Marks.of(found).marks().isEmpty(), "an Elite's weapon: its rank's tier, with a Mark");
        LordForged.Forged bell = LordForged.byWeapon("bell_warden_maul");
        h.assertTrue(Marks.of(LordForged.stack(bell, ArmsItems.item(bell.weapon()))).marks().contains("rebuke"), "a Lord-forged weapon carries its Lord's Mark");
        h.succeed();
    }

    /** The Forge: attunement for its price, priming both ways; a Blueprint drawn, and the grid gives it back. */
    static void forge(GameTestHelper h) {
        ServerPlayer p = fighter(h, 1.5, 0.5, 0F);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ArmsItems.item("wildhide_glaive")));
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.BLAZE_POWDER));
        h.assertTrue(!ArmsForge.attune(p, dev.awaken.core.Forces.Force.HEAT), "nothing to pay with: no attunement (creative "
                + p.isCreative() + ", essence " + ArmsForge.count(p, dev.awaken.core.api.CoreApi.ESSENCE_SHARD.get()) + ")");
        for (ArmsForge.Cost c : ArmsForge.attunePrice(dev.awaken.core.Forces.Force.HEAT)) {
            if (c.item() != Items.BLAZE_POWDER) p.getInventory().add(new ItemStack(c.item(), c.n()));
        }
        h.assertTrue(ArmsForge.attune(p, dev.awaken.core.Forces.Force.HEAT), "with its price, it attunes");
        h.assertTrue(ArmsForge.attunement(p) == dev.awaken.core.Forces.Force.HEAT && p.getOffhandItem().isEmpty(), "attuned to Heat, the catalyst spent");
        ArmsState s = ArmsServer.state(p);
        long now = ArmsServer.now(p);
        var zombie = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(1, 1, 3));
        ArmsForge.landed(p, s, zombie, now, false);
        h.assertTrue(ArmsWeaves.INSTANCE.costScale(p) < 1F, "an attuned hit makes the next power cheaper");
        ArmsWeaves.INSTANCE.cast(p, "ENERGY", "HEAT", 1);
        h.assertTrue(ArmsWeaves.INSTANCE.costScale(p) == 1F, "the cast spends it");
        h.assertTrue(ArmsForge.bladePrimed(p, s, now) && !ArmsForge.bladePrimed(p, s, now), "a Heat power primes the next blow, once");
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        for (ArmsForge.Cost c : ArmsForge.blueprintPrice()) p.getInventory().add(new ItemStack(c.item(), c.n()));
        h.assertTrue(ArmsForge.drawBlueprint(p, "glaive") && ArmsForge.owns(p, "glaive"), "a Glaive Blueprint drawn");
        Item bp = ArmsItems.BLUEPRINTS.get("glaive").get();
        h.assertTrue(bp.getCraftingRemainder(new ItemStack(bp)) != null, "the crafting grid gives a Blueprint back");
        h.assertTrue(!ArmsItems.BLUEPRINTS.containsKey(Arms.LONGSWORD) && ArmsItems.BLUEPRINTS.size() == 15, "every style but the Longsword has one");
        h.succeed();
    }

    /** The Daggers' backstab and the Katana's draw cut: their rules and their rows. */
    static void styleTraits(GameTestHelper h) {
        h.assertTrue(ArmsRules.behind(0F, 180F) && ArmsRules.behind(90F, -60F), "a striker at its back, or well round it, is behind");
        h.assertTrue(!ArmsRules.behind(0F, 0F) && !ArmsRules.behind(0F, 100F), "ahead of it, or at its side, is not");
        Archetypes.Loaded daggers = Archetypes.get("daggers"), katana = Archetypes.get("katana");
        h.assertTrue(daggers != null && daggers.def().traits().backstab() > 1.5F, "the Daggers strike harder from behind");
        h.assertTrue(katana != null && katana.def().traits().drawIdle() > 0, "the Katana has a draw cut");
        Archetype.Traits t = katana.def().traits();
        h.assertTrue(ArmsRules.drawn(t, Archetype.Move.STRIKE_1, t.drawIdle()), "the first strike after it rested is drawn");
        h.assertTrue(!ArmsRules.drawn(t, Archetype.Move.STRIKE_1, t.drawIdle() - 1), "not before it has rested");
        h.assertTrue(!ArmsRules.drawn(t, Archetype.Move.STRIKE_2, 1000), "and only the chain's first strike");
        h.assertTrue(!ArmsRules.drawn(longsword().def().traits(), Archetype.Move.STRIKE_1, 1000), "a Longsword draws nothing");
        h.assertTrue(Archetypes.get("scythe").def().traits().spirit() > 1F, "the Scythe cuts spirits deeper");
        h.assertTrue(Archetypes.get("chainblade").def().traits().pull() > 0F, "the Chain-blade reels them in");
        h.assertTrue(Archetypes.get("warstaff").def().traits().energy() > 0F, "the War-staff's blows charge power energy");
        h.assertTrue(Archetypes.get("precursor").def().traits().reachPerStep() > 0F, "the Precursor Arm's blade grows with the chain");
        h.assertTrue(Archetypes.get("gauntlets").def().traits().riposteThrow() > 0F, "the Gauntlets' riposte throws");
        h.assertTrue(Archetypes.all().size() == 16, "sixteen styles, as PLAN §4.5 (" + Archetypes.all().size() + ")");
        h.succeed();
    }

    static void stanceRules(GameTestHelper h) {
        h.assertTrue(ArmsRules.drained(20, ArmsRules.POSTURE_IDLE_TICKS, false) == 20, "untouched for its idle time: nothing drains yet");
        h.assertTrue(Math.abs(ArmsRules.drained(20, ArmsRules.POSTURE_IDLE_TICKS + 20, false) - (20 - 20 * ArmsRules.POSTURE_REGEN)) < 1e-4,
                "then it drains at its rate");
        h.assertTrue(ArmsRules.drained(20, ArmsRules.POSTURE_IDLE_TICKS + 20, true) < ArmsRules.drained(20, ArmsRules.POSTURE_IDLE_TICKS + 20, false),
                "a Lord's stance comes back faster");
        h.assertTrue(ArmsRules.drained(5, 10_000, false) == 0, "never below empty");
        Posture p = new Posture();
        p.value = 20;
        p.max = 40;
        p.lastHitAt = 100;
        p.settle(100 + ArmsRules.POSTURE_IDLE_TICKS + 20, false);
        h.assertTrue(Math.abs(p.value - ArmsRules.drained(20, ArmsRules.POSTURE_IDLE_TICKS + 20, false)) < 1e-4, "the server's settle is the same rule");
        p.settle(100 + ArmsRules.POSTURE_IDLE_TICKS + 20, false);
        h.assertTrue(Math.abs(p.value - ArmsRules.drained(20, ArmsRules.POSTURE_IDLE_TICKS + 20, false)) < 1e-4, "and never drains the same time twice");
        h.assertTrue(ArmsRules.lookedAt(170, -170) && !ArmsRules.lookedAt(90, 0), "the focus is looked at within 60 degrees, across the wrap");
        h.succeed();
    }

    /** A blow sounds like what it meets and how hard it lands; every class of tell has its one voice. */
    static void sounds(GameTestHelper h) {
        Archetypes.Loaded a = longsword();
        Archetype.MoveDef s1 = a.move(Archetype.Move.STRIKE_1), heavy = a.move(Archetype.Move.HEAVY), exec = a.move(Archetype.Move.EXECUTION);
        h.assertTrue(ArmsSounds.swish(s1) == ArmsSounds.SWISH_LIGHT.get() && ArmsSounds.swish(heavy) == ArmsSounds.SWISH_HEAVY.get()
                && ArmsSounds.swish(a.move(Archetype.Move.STRIKE_4)) == ArmsSounds.SWISH_THRUST.get(), "cuts, the heavy and the thrust each have their swish");
        h.assertTrue(ArmsSounds.swishPitch(2) > ArmsSounds.swishPitch(1) && ArmsSounds.swishPitch(0) == 1F, "the rhythm climbs a semitone a step");
        LivingEntity zombie = put(h, EntityTypes.ZOMBIE, 1, 1, 20);
        h.assertTrue(ArmsSounds.impact(zombie, Archetype.Move.STRIKE_1, s1) == ArmsSounds.HIT_FLESH.get(), "a cut into a zombie is flesh");
        h.assertTrue(ArmsSounds.impact(zombie, Archetype.Move.HEAVY, heavy) == ArmsSounds.HIT_FLESH_HEAVY.get(), "a heavy into it lands heavy");
        h.assertTrue(ArmsSounds.impact(zombie, Archetype.Move.EXECUTION, exec) == ArmsSounds.EXECUTION.get(), "an Execution is its own blow");
        h.assertTrue(ArmsSounds.impact(zombie, Archetype.Move.STRIKE_1, s1, Arms.MAUL) == ArmsSounds.HIT_BLUNT.get(), "a maul lands blunt");
        h.assertTrue(ArmsSounds.impact(zombie, Archetype.Move.STRIKE_1, s1, Arms.AXE) == ArmsSounds.HIT_FLESH.get(), "an axe cuts as a blade");
        zombie.getAttribute(Attributes.ARMOR).setBaseValue(15);
        h.assertTrue(ArmsSounds.impact(zombie, Archetype.Move.STRIKE_1, s1) == ArmsSounds.HIT_ARMOR.get(), "plate worn over flesh rings as armour");
        LivingEntity skeleton = put(h, EntityTypes.SKELETON, 2, 1, 20), golem = put(h, EntityTypes.IRON_GOLEM, 3, 1, 20),
                slime = put(h, EntityTypes.SLIME, 1, 3, 20), vex = put(h, EntityTypes.VEX, 2, 3, 20);
        h.assertTrue(ArmsSounds.impact(skeleton, Archetype.Move.STRIKE_1, s1) == ArmsSounds.HIT_BONE.get(), "a skeleton is bone");
        h.assertTrue(ArmsSounds.impact(golem, Archetype.Move.STRIKE_1, s1) == ArmsSounds.HIT_STONE.get(), "a golem is stone and iron");
        h.assertTrue(ArmsSounds.impact(slime, Archetype.Move.STRIKE_1, s1) == ArmsSounds.HIT_SLIME.get(), "a slime is slime");
        h.assertTrue(ArmsSounds.impact(vex, Archetype.Move.STRIKE_1, s1) == ArmsSounds.HIT_SPIRIT.get(), "a vex is a spirit");
        h.assertTrue(ArmsSounds.impactPitch(golem, heavy) < 1F && ArmsSounds.impactPitch(zombie, s1) == 1F, "a heavy on a hard body lands lower");
        for (Strikes.Kind k : Strikes.Kind.values()) {
            h.assertTrue((Arms.tellSound(k) == null) == (k == Strikes.Kind.NONE), k + ": every class of blow has its tell's voice");
        }
        for (LivingEntity e : new LivingEntity[]{zombie, skeleton, golem, slime, vex}) e.discard();
        h.succeed();
    }

    static Archetypes.Loaded longsword() {
        Archetypes.Loaded a = Archetypes.get(Arms.LONGSWORD);
        if (a == null) throw new IllegalStateException("no longsword in the timing table");
        return a;
    }

    // ---- pure rules -------------------------------------------------------------------------------------------------

    static void timeline(GameTestHelper h) {
        Archetypes.Loaded a = longsword();
        Archetype.MoveDef s1 = a.move(Archetype.Move.STRIKE_1);
        h.assertTrue(s1 != null && s1.hasCadence() && s1.windup() == 2 && s1.active() == 2, "strike 1 is 2 + 2 with a cadence window");
        ArmsRules.Decision idle = ArmsRules.tap(a, null, 0, -1, 0, 100, 0);
        h.assertTrue(idle != null && idle.move() == Archetype.Move.STRIKE_1 && idle.startAt() == 100, "idle: the chain starts at once");
        int c0 = s1.cadence().get(0), c1 = s1.cadence().get(1);
        ArmsRules.Decision onBeat = ArmsRules.tap(a, Archetype.Move.STRIKE_1, 100, 0, 0, 100 + c0, 0);
        h.assertTrue(onBeat != null && onBeat.move() == Archetype.Move.STRIKE_2 && onBeat.cadence() == 1 && onBeat.startAt() == 100 + c0,
                "a press in the window: strike 2 now, one rhythm step");
        ArmsRules.Decision mash = ArmsRules.tap(a, Archetype.Move.STRIKE_1, 100, 0, 2, 100 + c0 - 2, 0);
        h.assertTrue(mash != null && mash.cadence() == 0 && mash.startAt() == 100 + c0, "a press just early is buffered to the window and loses the rhythm");
        h.assertTrue(ArmsRules.tap(a, Archetype.Move.STRIKE_1, 100, 0, 0, 100, 0) == null, "a press far ahead of the window counts for nothing");
        ArmsRules.Decision late = ArmsRules.tap(a, Archetype.Move.STRIKE_1, 100, 0, 2, 100 + c1 + 2, 0);
        h.assertTrue(late != null && late.move() == Archetype.Move.STRIKE_2 && late.cadence() == 0, "a late press continues the chain without the rhythm");
        ArmsRules.Decision gone = ArmsRules.tap(a, Archetype.Move.STRIKE_1, 100, 0, 2, 100 + s1.total() + a.def().late(), 0);
        h.assertTrue(gone != null && gone.move() == Archetype.Move.STRIKE_1, "long after: the chain starts again");
        Archetype.MoveDef fin = a.move(Archetype.Move.STRIKE_3);
        ArmsRules.Decision after = ArmsRules.tap(a, Archetype.Move.STRIKE_3, 100, 2, 3, 100 + fin.total(), 0);
        h.assertTrue(after != null && after.move() == Archetype.Move.STRIKE_1, "after the finisher the chain starts again");
        ArmsRules.Decision mastered = ArmsRules.tap(a, Archetype.Move.STRIKE_3, 100, 2, 3, 100 + fin.total(), 1);
        h.assertTrue(mastered != null && mastered.move() == Archetype.Move.STRIKE_4, "mastered, the finisher's recovery carries on into the mastery strike");
        h.assertTrue(Math.abs(ArmsRules.motion(s1, 3, false) - s1.motion() * 1.24F) < 1e-4, "three rhythm steps: +24%");
        Archetype.MoveDef heavy = a.move(Archetype.Move.HEAVY);
        h.assertTrue(heavy != null && heavy.charge().isPresent() && heavy.charge().get().perfect(heavy.charge().get().glint() + 2)
                && !heavy.charge().get().perfect(heavy.charge().get().glint() + 3), "the glint: a release within 2 ticks is perfect");
        h.assertTrue(ArmsRules.recovery(s1, a, a.def().baseSpeed() * 2) < s1.recovery() && ArmsRules.recovery(s1, a, a.def().baseSpeed()) == s1.recovery(),
                "faster arms recover sooner");
        h.succeed();
    }

    static void defenceRules(GameTestHelper h) {
        Archetype.Guard g = longsword().def().guard();
        h.assertTrue(ArmsRules.guard(g, Strikes.Kind.LIGHT, 0, 0, 0, false, true) == ArmsRules.Answer.DEFLECT, "the raise's first tick deflects");
        h.assertTrue(ArmsRules.guard(g, Strikes.Kind.LIGHT, g.deflect() - 1, 0, 0, false, true) == ArmsRules.Answer.DEFLECT, "the window's last tick deflects");
        h.assertTrue(ArmsRules.guard(g, Strikes.Kind.LIGHT, g.deflect(), 0, 0, false, true) == ArmsRules.Answer.BLOCK, "after the window: a block");
        h.assertTrue(ArmsRules.guard(g, Strikes.Kind.HEAVY, g.deflect() + 4, 0, 0, false, true) == ArmsRules.Answer.GUARD_BREAK, "a heavy breaks a held guard");
        h.assertTrue(ArmsRules.guard(g, Strikes.Kind.HEAVY, 1, 0, 0, false, true) == ArmsRules.Answer.DEFLECT, "a heavy can be deflected");
        h.assertTrue(ArmsRules.guard(g, Strikes.Kind.PERILOUS, 0, 0, 0, false, true) == ArmsRules.Answer.PASS, "a perilous blow passes any guard");
        h.assertTrue(ArmsRules.guard(g, Strikes.Kind.LIGHT, 0, 0, 0, true, true) == ArmsRules.Answer.BLOCK, "locked out: a spammed raise only blocks");
        h.assertTrue(ArmsRules.guard(g, Strikes.Kind.LIGHT, 0, 0, 0, false, false) == ArmsRules.Answer.PASS, "a blow from behind passes the guard");
        // the grace: a raise that arrived after the blow, within the grace, still deflects as the player saw it
        h.assertTrue(ArmsRules.guard(g, Strikes.Kind.LIGHT, 0, 2, 2, false, true) == ArmsRules.Answer.DEFLECT, "held 2 ticks, grace 2: a deflect");
        h.assertTrue(ArmsRules.guard(g, Strikes.Kind.LIGHT, 0, 3, 2, false, true) == ArmsRules.Answer.BLOCK, "beyond the grace: a block");
        h.assertTrue(ArmsRules.grace(20) == 0 && ArmsRules.grace(150) == 2 && ArmsRules.grace(300) == 3 && ArmsRules.grace(900) == ArmsRules.GRACE_MAX,
                "grace: none near, 2 ticks at 150 ms, capped at 3");
        Archetype.Dodge d = longsword().def().dodge();
        double run = 0;
        for (int t = 0; t < d.ticks(); t++) run += ArmsRules.dodgeSpeed(d, t);
        h.assertTrue(Math.abs(run - d.distance()) < 1e-6, "the dodge covers its distance: " + run);
        h.assertTrue(ArmsRules.dodgeSpeed(d, 0) > ArmsRules.dodgeSpeed(d, d.ticks() - 1), "a fast start easing out");
        h.assertTrue(ArmsRules.evades(d, d.evade() - 1, Strikes.Kind.PERILOUS) && !ArmsRules.evades(d, d.evade(), Strikes.Kind.LIGHT)
                && !ArmsRules.evades(d, 0, Strikes.Kind.NONE), "evade ticks, nothing after, never a fall or fire");
        h.assertTrue(ArmsRules.perfect(d, d.perfect() - 1) && !ArmsRules.perfect(d, d.perfect()), "perfect ticks");
        h.assertTrue(ArmsRules.postureMax(CreatureTag.NONE, MobRole.Role.BOSS) == ArmsRules.POSTURE_LORD
                && ArmsRules.postureMax(CreatureTag.NONE, MobRole.Role.ELITE) == ArmsRules.POSTURE_ELITE
                && ArmsRules.postureMax(CreatureTag.NONE, MobRole.Role.NONE) == ArmsRules.POSTURE_NORMAL, "stances by role");
        h.assertTrue(ArmsRules.capExecution(500, 1000, 0.08F, true) == 80 && ArmsRules.capExecution(500, 1000, 0.08F, false) == 500,
                "an Execution takes at most 8% of a Lord");
        h.assertTrue(ArmsRules.focusTurn(20, 0) == ArmsRules.FOCUS_TURN && ArmsRules.focusTurn(90, 0) == 0, "soft focus turns at most 15°, only near");
        h.succeed();
    }

    // ---- the server path --------------------------------------------------------------------------------------------

    static ServerPlayer fighter(GameTestHelper h, double x, double z, float yaw) {
        for (int bx = -3; bx <= 6; bx++) for (int bz = -3; bz <= 6; bz++) {
            h.setBlock(new BlockPos(bx, 0, bz), Blocks.STONE);
            for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(bx, y, bz), Blocks.AIR);
        }
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.connection.markClientLoaded();
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        // a mock player never ticks, so its held item's modifiers never apply: give it the diamond sword's 7 by hand
        p.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(7);
        Vec3 v = h.absoluteVec(new Vec3(x, 1, z));
        p.snapTo(v.x, v.y, v.z, yaw, 0F);
        p.setYHeadRot(yaw);
        p.setOnGround(true);
        p.removeEffect(MobEffects.SLOWNESS);
        return p;
    }

    static <T extends Mob> T put(GameTestHelper h, net.minecraft.world.entity.EntityType<T> type, double x, double z, float health) {
        T m = type.create(h.getLevel(), EntitySpawnReason.COMMAND);
        m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        m.setHealth(health);
        m.getAttribute(Attributes.ARMOR).setBaseValue(0);
        Vec3 v = h.absoluteVec(new Vec3(x, 1, z));
        m.snapTo(v.x, v.y, v.z, 180F, 0F);
        m.setNoAi(true);
        h.getLevel().addFreshEntity(m);
        return m;
    }

    static void done(GameTestHelper h, ServerPlayer... ps) {
        for (ServerPlayer p : ps) {
            ArmsServer.clear(p);
            h.getLevel().getServer().getPlayerList().remove(p);
        }
        h.succeed();
    }

    /** Runs a move's active ticks on the server's clock, as the player tick would. */
    static void runActive(ServerPlayer p) {
        ArmsState s = ArmsServer.state(p);
        if (s.move == null || s.style == null) return;
        Archetype.MoveDef def = s.style.move(s.move);
        for (int i = 0; i < def.active(); i++) ArmsServer.sweep(p, s, def, ArmsServer.now(p));
    }

    /** The aimed creature is struck at the move's multiplier; a hostile in the arc too; nothing behind or friendly. */
    static void strikeArc(GameTestHelper h) {
        ServerPlayer p = fighter(h, 1.5, 0.5, 0F); // facing +z
        LivingEntity aimed = put(h, EntityTypes.PIG, 1.5, 2.3, 200);
        LivingEntity side = put(h, EntityTypes.ZOMBIE, 2.6, 2.0, 200);
        LivingEntity behind = put(h, EntityTypes.ZOMBIE, 1.5, -1.6, 200);
        LivingEntity calm = put(h, EntityTypes.COW, 0.4, 2.0, 200);
        ArmsServer.onPress(p, new ArmsPayloads.Press(1, ArmsPayloads.PRESS_STRIKE, 0F, 0F, aimed.getId()));
        ArmsState s = ArmsServer.state(p);
        h.assertTrue(s.move == Archetype.Move.STRIKE_1, "a tap starts strike 1, was " + s.move);
        runActive(p);
        float want = 7F * longsword().move(Archetype.Move.STRIKE_1).motion();
        h.assertTrue(Math.abs(200 - aimed.getHealth() - want) < 0.05, "the aimed pig takes 7 x 0.9 = " + want + ", took " + (200 - aimed.getHealth()));
        h.assertTrue(side.getHealth() < 200, "a hostile in the arc is struck too");
        h.assertTrue(behind.getHealth() == 200, "nothing behind is struck");
        h.assertTrue(calm.getHealth() == 200, "a calm creature in the arc is left alone (only the aimed one is struck)");
        // the same move never strikes twice
        float was = aimed.getHealth();
        runActive(p);
        h.assertTrue(aimed.getHealth() == was, "one hit per creature per move");
        done(h, p);
    }

    /** Every listener after the Arms stage still applies: the momentum hit's +30% from a slide-speed run. */
    static void damageStack(GameTestHelper h) {
        ServerPlayer p = fighter(h, 1.5, 0.5, 0F);
        LivingEntity pig = put(h, EntityTypes.PIG, 1.5, 2.3, 200);
        p.setSprinting(true);
        p.setKnownMovement(new Vec3(0, 0, 0.45));
        ArmsServer.onPress(p, new ArmsPayloads.Press(1, ArmsPayloads.PRESS_STRIKE, 0F, 0F, pig.getId()));
        runActive(p);
        float base = 7F * longsword().move(Archetype.Move.STRIKE_1).motion();
        float dealt = 200 - pig.getHealth();
        h.assertTrue(dealt > base * 1.25F, "the momentum hit still adds its share on an Arms strike: " + dealt + " vs " + base);
        // and vanilla's own attack never resolves for an Arms weapon
        float before = pig.getHealth();
        pig.damageCooldownTime = 0;
        p.attack(pig);
        h.assertTrue(pig.getHealth() == before, "a vanilla attack with an Arms weapon does nothing (the moves strike)");
        done(h, p);
    }

    /** A blow in the raise's first ticks is deflected: no damage, the attacker's stance takes it; a perilous one lands. */
    static void deflect(GameTestHelper h) {
        ServerPlayer p = fighter(h, 1.5, 0.5, 0F);
        Mob z = put(h, EntityTypes.ZOMBIE, 1.5, 1.9, 60);
        z.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6);
        p.startUsingItem(InteractionHand.MAIN_HAND);
        ArmsServer.onUseStart(new net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Start(p, p.getMainHandItem(), InteractionHand.MAIN_HAND, 72000));
        h.assertTrue(p.isBlocking(), "a sword raises a guard at once (the blocking component, no delay)");
        float hp = p.getHealth();
        Strikes.deliver(z, Strikes.Kind.LIGHT, () -> z.doHurtTarget(h.getLevel(), p));
        h.assertTrue(p.getHealth() == hp, "a LIGHT blow on the raise is deflected: no damage (took " + (hp - p.getHealth()) + ")");
        h.assertTrue(Arms.posture(z) > 0, "the attacker's stance took the deflect");
        ArmsState s = ArmsServer.state(p);
        h.assertTrue(s.raiseDeflected && ArmsServer.now(p) - s.deflectAt <= 1, "the deflect is remembered (the riposte opens)");
        // the riposte: the next tap is the riposte
        p.stopUsingItem();
        ArmsServer.onPress(p, new ArmsPayloads.Press(2, ArmsPayloads.PRESS_STRIKE, 0F, 0F, z.getId()));
        h.assertTrue(s.move == Archetype.Move.RIPOSTE, "a tap right after a deflect is a riposte, was " + s.move);
        // a perilous blow passes the guard
        p.startUsingItem(InteractionHand.MAIN_HAND);
        z.damageCooldownTime = 0;
        p.damageCooldownTime = 0;
        hp = p.getHealth();
        Strikes.deliver(z, Strikes.Kind.PERILOUS, () -> p.hurtServer(h.getLevel(), h.getLevel().damageSources().mobAttack(z), 4F));
        h.assertTrue(p.getHealth() < hp, "a PERILOUS blow passes any guard");
        done(h, p);
    }

    /** At 150 ms a blow on an unguarded fighter waits out the grace; a guard raised in it still deflects. */
    static void deflectLatency(GameTestHelper h) {
        ServerPlayer p = fighter(h, 1.5, 0.5, 0F);
        Mob z = put(h, EntityTypes.ZOMBIE, 1.5, 1.9, 60);
        z.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6);
        ArmsServer.state(p).testGrace = ArmsRules.grace(150);
        float hp = p.getHealth();
        Strikes.deliver(z, Strikes.Kind.LIGHT, () -> z.doHurtTarget(h.getLevel(), p));
        h.assertTrue(p.getHealth() == hp && ArmsServer.holds(p) == 1, "the blow is held for the grace, not landed");
        // the press arrives a tick later: the guard rises, the held blow meets it on the next server tick
        p.startUsingItem(InteractionHand.MAIN_HAND);
        ArmsServer.onUseStart(new net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Start(p, p.getMainHandItem(), InteractionHand.MAIN_HAND, 72000));
        h.startSequence().thenIdle(2).thenExecute(() -> {
            h.assertTrue(ArmsServer.holds(p) == 0, "the held blow was landed");
            h.assertTrue(p.getHealth() == hp, "and deflected by the guard raised inside the grace (took " + (hp - p.getHealth()) + ")");
            h.assertTrue(ArmsServer.state(p).raiseDeflected, "counted as a deflect");
            // no guard at all: the blow lands once its grace is over
            p.stopUsingItem();
            z.damageCooldownTime = 0;
            p.damageCooldownTime = 0;
            Strikes.deliver(z, Strikes.Kind.LIGHT, () -> z.doHurtTarget(h.getLevel(), p));
        }).thenIdle(ArmsRules.grace(150) + 1).thenExecute(() -> {
            h.assertTrue(p.getHealth() < hp, "unanswered, it lands after the grace");
            done(h, p);
        });
    }

    /** The dodge's first ticks evade a blow; the first of them are perfect and slow the attacker alone. */
    static void dodge(GameTestHelper h) {
        ServerPlayer p = fighter(h, 1.5, 0.5, 0F);
        Mob z = put(h, EntityTypes.ZOMBIE, 1.5, 1.9, 60);
        z.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6);
        ArmsServer.onDodge(p, new ArmsPayloads.Dodge(1, -1F, 0F, p.getX(), p.getY(), p.getZ()));
        ArmsState s = ArmsServer.state(p);
        h.assertTrue(s.dodgeAt == ArmsServer.now(p), "the dodge was accepted");
        float hp = p.getHealth();
        Strikes.deliver(z, Strikes.Kind.PERILOUS, () -> z.doHurtTarget(h.getLevel(), p));
        h.assertTrue(p.getHealth() == hp, "a blow on the dodge's first tick is evaded");
        h.assertTrue(ArmsServer.slowed(z), "a perfect dodge slows the attacker");
        Archetype.Dodge d = longsword().def().dodge();
        h.assertTrue(s.dodgeCharges == d.charges() - 1, "a charge spent");
        ArmsServer.onDodge(p, new ArmsPayloads.Dodge(2, 1F, 0F, p.getX(), p.getY(), p.getZ()));
        h.assertTrue(s.dodgeCharges == d.charges() - 1, "no second dodge while one is running");
        h.startSequence().thenIdle(d.evade() + 1).thenExecute(() -> {
            z.damageCooldownTime = 0;
            p.damageCooldownTime = 0;
            Strikes.deliver(z, Strikes.Kind.LIGHT, () -> z.doHurtTarget(h.getLevel(), p));
            h.assertTrue(p.getHealth() < hp, "after the evade ticks a blow lands");
            // a dodge far from where the server saw the feet is refused
            int before = s.dodgeCharges;
            ArmsServer.onDodge(p, new ArmsPayloads.Dodge(3, 1F, 0F, p.getX() + 5, p.getY(), p.getZ()));
            h.assertTrue(s.dodgeCharges == before, "a dodge claimed from somewhere else is refused");
            done(h, p);
        });
    }

    /** A full stance breaks: the creature staggers, it can't break again at once, and a heavy on it is an Execution. */
    static void postureBreak(GameTestHelper h) {
        ServerPlayer p = fighter(h, 1.5, 0.5, 0F);
        Mob z = put(h, EntityTypes.ZOMBIE, 1.5, 2.0, 400);
        Posture post = ArmsServer.posture(z);
        h.assertTrue(post.max == ArmsRules.POSTURE_NORMAL, "a plain zombie holds " + ArmsRules.POSTURE_NORMAL + ", holds " + post.max);
        ArmsServer.addPosture(z, ArmsRules.POSTURE_NORMAL - 1, p);
        h.assertTrue(!Arms.broken(z), "one short of full: not broken");
        ArmsServer.addPosture(z, 1, p);
        h.assertTrue(Arms.broken(z) && z.hasEffect(MobEffects.SLOWNESS), "full: broken, and it staggers");
        h.assertTrue(post.value == 0, "a broken stance starts again from nothing");
        // a heavy on a broken creature is an Execution
        ArmsState s = ArmsServer.state(p);
        s.aimed = z.getId();
        Archetypes.Loaded a = longsword();
        ArmsServer.heavy(p, s, a, a.move(Archetype.Move.HEAVY), 10, ArmsServer.now(p), 5);
        h.assertTrue(s.move == Archetype.Move.EXECUTION, "a heavy on a broken creature is an Execution, was " + s.move);
        // it can't break again for a while
        post.brokenUntil = 0;
        ArmsServer.addPosture(z, ArmsRules.POSTURE_NORMAL, p);
        h.assertTrue(!Arms.broken(z), "no second Break inside the cooldown");
        // idle stances drain
        Posture q = new Posture();
        q.max = 20;
        q.value = 10;
        q.lastHitAt = 0;
        q.settle(ArmsRules.POSTURE_IDLE_TICKS + 20, false);
        h.assertTrue(Math.abs(q.value - (10 - 20 * ArmsRules.POSTURE_REGEN)) < 1e-4, "a stance left alone drains after 2 s");
        q.settle(ArmsRules.POSTURE_IDLE_TICKS + 20, false);
        h.assertTrue(Math.abs(q.value - (10 - 20 * ArmsRules.POSTURE_REGEN)) < 1e-4, "and never drains twice for the same time");
        done(h, p);
    }

    /** Off: vanilla melee everywhere, the Arms stage stands aside. */
    static void killSwitch(GameTestHelper h) {
        ServerPlayer p = fighter(h, 1.5, 0.5, 0F);
        LivingEntity pig = put(h, EntityTypes.PIG, 1.5, 2.3, 200);
        ArmsConfig.enabledOverride = false;
        try {
            h.assertTrue(!Arms.fights(p), "off: Arms runs no one's melee");
            p.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(1024);
            p.attack(pig);
            h.assertTrue(Math.abs(200 - pig.getHealth() - 7F) < 0.05, "off: vanilla's attack deals the sword's 7, dealt " + (200 - pig.getHealth()));
            p.startUsingItem(InteractionHand.MAIN_HAND);
            h.assertTrue(!p.isUsingItem(), "off: a sword raises no guard, as in vanilla");
        } finally {
            ArmsConfig.enabledOverride = null;
        }
        h.assertTrue(Arms.fights(p), "on again");
        p.startUsingItem(InteractionHand.MAIN_HAND);
        h.assertTrue(p.isUsingItem() && p.isBlocking(), "on: the sword's guard rises");
        p.stopUsingItem();
        done(h, p);
    }
}
