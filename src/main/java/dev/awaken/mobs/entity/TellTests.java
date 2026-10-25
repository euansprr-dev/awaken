package dev.awaken.mobs.entity;

import dev.awaken.core.api.BodyMaterial;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.api.Strikes;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.def.Ability;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.MobDef;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.Set;

/** Tells and stances (SPEC §24): every melee blow is told and can be stepped out of; held weapons are only a look. */
public final class TellTests {
    private TellTests() {}

    public static void register() {
        // a Lord's held weapon used to add its damage before the level scaling multiplied it (the Ember Tyrant: 13.4 -> 37.6)
        AwakenTests.register("mobs_held_weapon_damage", 40, h -> {
            ServerLevel level = h.getLevel();
            MobDef def = Bestiary.get("ember_tyrant");
            AwakenMob lord = Mobs.spawn(level, def, h.absoluteVec(new Vec3(1.5, 1, 1.5)), 17,
                    new MobRole(MobRole.Role.BOSS, 17, def.theme.id, Optional.of(def.bossId()), ""), Set.of());
            h.assertTrue(lord != null, "the Ember Tyrant spawned");
            double want = def.damage * (1 + 0.04 * 17) * 1.6;
            double has = lord.getAttributeValue(Attributes.ATTACK_DAMAGE);
            h.assertTrue(Math.abs(has - want) < 0.01, "a D Ember Tyrant hits for " + want + " (its axe is only a look), has " + has);
            h.assertTrue(!lord.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(), "it still holds its axe");
            lord.discard();
            h.succeed();
        });

        // a bolt lands as the class of the ability that fired it, whenever it lands (Arms can answer it: SPEC §25 sim)
        AwakenTests.register("mobs_bolt_class", 20, h -> {
            ServerLevel level = h.getLevel();
            MobDef def = Bestiary.get("toll_ghoul");
            AwakenMob ghoul = Mobs.spawn(level, def, h.absoluteVec(new Vec3(1.5, 1, 1.5)), 5,
                    new MobRole(MobRole.Role.NORMAL, 5, def.theme.id, Optional.empty(), ""), Set.of());
            h.assertTrue(ghoul != null, "a ghoul spawned");
            AwBolt[] fired = new AwBolt[1];
            Strikes.deliver(ghoul, Strikes.Kind.HEAVY, () -> {
                fired[0] = AwBolt.shoot(level, ghoul, ghoul.position().add(0, 1.5, 0), new Vec3(1, 0, 0), dev.awaken.mobs.def.BoltKind.SPIRIT, 3F, null, 0.2);
                return true;
            });
            h.assertTrue(fired[0].strike() == Strikes.Kind.HEAVY, "the bolt carries the class it was fired under");
            var src = Abilities.source(level, ghoul, fired[0], dev.awaken.mobs.def.BoltKind.SPIRIT);
            h.assertTrue(Strikes.of(src) == Strikes.Kind.HEAVY, "and lands as it, a Force bolt included (was " + Strikes.of(src) + ")");
            fired[0].discard();
            ghoul.discard();
            h.succeed();
        });

        // an answered blow (refused: deflected, evaded, blocked whole) brings none of its burn or effects
        AwakenTests.register("mobs_answered_no_burn", 20, h -> {
            ServerLevel level = h.getLevel();
            MobDef def = Bestiary.get("toll_ghoul");
            AwakenMob ghoul = Mobs.spawn(level, def, h.absoluteVec(new Vec3(1.5, 1, 1.5)), 5,
                    new MobRole(MobRole.Role.NORMAL, 5, def.theme.id, Optional.empty(), ""), Set.of());
            Pig answered = EntityTypes.PIG.create(level, EntitySpawnReason.MOB_SUMMONED), struck = EntityTypes.PIG.create(level, EntitySpawnReason.MOB_SUMMONED);
            answered.setPos(h.absoluteVec(new Vec3(3.5, 1, 1.5)));
            struck.setPos(h.absoluteVec(new Vec3(3.5, 1, 3.5)));
            answered.setNoAi(true);
            struck.setNoAi(true);
            answered.setPermanentlyInvulnerable(true); // its hurt is refused, as a deflect refuses one
            level.addFreshEntity(answered);
            level.addFreshEntity(struck);
            var poison = new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 60, 0);
            Abilities.hurtBy(level, ghoul, null, answered, 3F, dev.awaken.mobs.def.BoltKind.FIRE, poison);
            Abilities.hurtBy(level, ghoul, null, struck, 3F, dev.awaken.mobs.def.BoltKind.FIRE, poison);
            h.assertTrue(!answered.isOnFire() && !answered.hasEffect(net.minecraft.world.effect.MobEffects.POISON), "the answered blow neither burns nor poisons");
            h.assertTrue(struck.isOnFire() && struck.hasEffect(net.minecraft.world.effect.MobEffects.POISON), "the landed one does both");
            answered.discard();
            struck.discard();
            ghoul.discard();
            h.succeed();
        });

        // a broken stance starts no ability until it stands again (the Break is the opening it earned)
        AwakenTests.register("mobs_broken_idle", 80, h -> {
            ServerLevel level = h.getLevel();
            MobDef def = Bestiary.get("crypt_keeper");
            AwakenMob keeper = Mobs.spawn(level, def, h.absoluteVec(new Vec3(1.5, 1, 1.5)), 5,
                    new MobRole(MobRole.Role.ELITE, 5, def.theme.id, Optional.empty(), ""), Set.of());
            h.assertTrue(keeper != null, "a keeper spawned");
            AbilityRunner runner = new AbilityRunner(keeper); // its own is made on its first server tick
            keeper.setNoAi(true);
            Pig pig = EntityTypes.PIG.create(level, EntitySpawnReason.MOB_SUMMONED);
            pig.setPos(h.absoluteVec(new Vec3(3.5, 1, 1.5)));
            pig.setNoAi(true);
            level.addFreshEntity(pig);
            keeper.setTarget(pig);
            keeper.stagger(60);
            for (int i = 0; i < 56; i++) {
                runner.tick(level);
                h.assertTrue(keeper.telegraphTicks() == 0, "nothing starts while broken (tick " + i + ")");
            }
            pig.discard();
            keeper.discard();
            h.succeed();
        });

        // the blow lands only after its tell, and a target that stepped out of reach is missed
        AwakenTests.register("mobs_told_melee", 80, h -> {
            ServerLevel level = h.getLevel();
            MobDef def = Bestiary.get("toll_ghoul");
            AwakenMob ghoul = Mobs.spawn(level, def, h.absoluteVec(new Vec3(1.5, 1, 1.5)), 5,
                    new MobRole(MobRole.Role.NORMAL, 5, def.theme.id, Optional.empty(), ""), Set.of());
            h.assertTrue(ghoul != null, "a ghoul spawned");
            ghoul.setNoAi(true); // the test drives its goal by hand
            Pig pig = EntityTypes.PIG.create(level, EntitySpawnReason.MOB_SUMMONED);
            pig.setPos(h.absoluteVec(new Vec3(2.6, 1, 1.5)));
            pig.setNoAi(true);
            level.addFreshEntity(pig);
            ghoul.setTarget(pig);
            MobGoals.ToldMelee goal = new MobGoals.ToldMelee(ghoul);
            int tell = MobGoals.ToldMelee.tellTicks(ghoul, Strikes.Kind.LIGHT);
            h.assertTrue(tell == 8, "a normal creature tells a light blow for 8 ticks, was " + tell);
            float hp = pig.getHealth();
            goal.checkAndPerformAttack(pig);
            h.assertTrue(ghoul.telegraphTicks() == tell && ghoul.tellKind() == Strikes.Kind.LIGHT, "the tell starts: LIGHT, " + tell + " ticks");
            Strikes.Tell seen = Strikes.tell(ghoul);
            h.assertTrue(seen != null && seen.kind() == Strikes.Kind.LIGHT, "the tell is readable through core.api.Strikes");
            for (int i = 0; i < tell - 1; i++) goal.tick();
            h.assertTrue(pig.getHealth() == hp, "nothing lands during the tell");
            goal.tick();
            h.assertTrue(pig.getHealth() < hp, "the blow lands when the tell ends");
            h.assertTrue(ghoul.tellKind() == Strikes.Kind.NONE, "and the tell is over");
            // a second blow, but the target steps back out of reach before it lands: a whiff
            pig.damageCooldownTime = 0;
            pig.setHealth(pig.getMaxHealth());
            for (int i = 0; i < 30 && ghoul.telegraphTicks() == 0; i++) {
                goal.tick();
                goal.checkAndPerformAttack(pig);
            }
            h.assertTrue(ghoul.telegraphTicks() > 0, "a second tell starts after the recovery");
            pig.setPos(pig.position().add(4, 0, 0));
            float full = pig.getHealth();
            for (int i = 0; i < tell; i++) goal.tick();
            h.assertTrue(pig.getHealth() == full, "stepping out of reach during the tell makes it whiff");
            // a broken stance ends the tell at once
            goal.checkAndPerformAttack(pig);
            ghoul.stagger(20);
            goal.tick();
            h.assertTrue(ghoul.staggered() && ghoul.telegraphTicks() == 0 && ghoul.isHeld(), "a stagger ends the tell and holds the body still");
            ghoul.discard();
            pig.discard();
            h.succeed();
        });

        AwakenTests.register("mobs_body_materials", 20, h -> {
            int bone = 0, wood = 0, stone = 0;
            for (MobDef d : Bestiary.all()) {
                BodyMaterial m = d.material();
                if (d.traits.contains(dev.awaken.mobs.def.Trait.HAUNTED)) h.assertTrue(m == BodyMaterial.SPIRIT, d.id + ": a haunted body is a spirit");
                else if (d.model == dev.awaken.mobs.def.Model.SKELETON) h.assertTrue(m == BodyMaterial.BONE, d.id + ": a skeleton is bone");
                if (m == BodyMaterial.BONE) bone++;
                if (m == BodyMaterial.WOOD) wood++;
                if (m == BodyMaterial.STONE) stone++;
            }
            h.assertTrue(bone > 0 && wood > 0 && stone > 0, "the bestiary has bone, wood and stone bodies (" + bone + ", " + wood + ", " + stone + ")");
            MobDef saint = Bestiary.get("root_saint");
            h.assertTrue(saint != null && saint.material() == BodyMaterial.WOOD, "the Root Saint is wood");
            h.succeed();
        });

        AwakenTests.register("mobs_ability_classes", 20, h -> {
            h.assertTrue(Ability.of(Ability.Kind.BOLT, "b").build().strike() == Strikes.Kind.LIGHT, "a bolt is LIGHT: deflect it");
            h.assertTrue(Ability.of(Ability.Kind.SLAM, "s").build().strike() == Strikes.Kind.HEAVY, "a slam is HEAVY");
            h.assertTrue(Ability.of(Ability.Kind.SLAM, "s").heavy().build().strike() == Strikes.Kind.PERILOUS, "a heavy tell is PERILOUS");
            h.assertTrue(Ability.of(Ability.Kind.RAIN, "r").build().strike() == Strikes.Kind.PERILOUS, "an area is PERILOUS");
            h.assertTrue(Ability.of(Ability.Kind.SUMMON, "m").build().strike() == Strikes.Kind.NONE, "a summon is no blow");
            for (MobDef d : Bestiary.all()) {
                if (!d.tier.isBoss()) continue;
                for (var phase : d.phases) for (Ability a : phase.abilities()) {
                    boolean blow = a.kind() != Ability.Kind.SUMMON && a.kind() != Ability.Kind.HEAL && a.kind() != Ability.Kind.GUARD
                            && a.kind() != Ability.Kind.DECOY && a.kind() != Ability.Kind.FRENZY;
                    h.assertTrue(!blow || a.strike() != Strikes.Kind.NONE, d.id + " " + a.name() + " is a blow with a class");
                    h.assertTrue(a.strike() == Strikes.Kind.NONE || a.windup() >= 8, d.id + " " + a.name() + " is told for at least 8 ticks");
                }
            }
            h.succeed();
        });
    }
}
