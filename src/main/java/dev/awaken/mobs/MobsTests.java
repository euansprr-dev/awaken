package dev.awaken.mobs;

import dev.awaken.Awaken;
import dev.awaken.core.Forces;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobHooks;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.mobs.def.Ability;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.def.Rosters;
import dev.awaken.mobs.def.Theme;
import dev.awaken.mobs.def.Tier;
import dev.awaken.mobs.def.Trait;
import dev.awaken.mobs.entity.AwakenBoss;
import dev.awaken.mobs.entity.AwakenMob;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** GameTests for the bestiary: scaling maths, the spawner for every id, Forces tags, boss bars, loot tables, counters. */
final class MobsTests {
    private MobsTests() {}

    static void register() {
        dev.awaken.mobs.entity.HeroTests.register(); // hero bodies, their clips and the creatures' voices
        dev.awaken.mobs.entity.LifeTests.register(); // the life layer's data and bindings (SPEC §23)
        dev.awaken.mobs.entity.TellTests.register(); // tells, classes and stances (SPEC §24)
        dev.awaken.mobs.entity.KitTests.register(); // the Creature Kit: tables, sync, hit volumes, phases (SPEC §35)
        dev.awaken.mobs.entity.SeepTests.register(); // the seep heart (W5): placed, asleep, woken, felled
        AwakenTests.register("mobs_scaling_maths", 40, h -> {
            h.assertTrue(Math.abs(MobScaling.healthMultiplier(5) - 1.375) < 1e-9, "health multiplier at lv5");
            h.assertTrue(Math.abs(MobScaling.damageMultiplier(150) - 7.0) < 1e-9, "damage multiplier at lv150");
            h.assertTrue(Math.abs(MobScaling.armorBonus(57) - 4.75) < 1e-9, "armour bonus at lv57");
            h.assertTrue(MobLoot.rankFor(0) == dev.awaken.progression.Rank.E && MobLoot.rankFor(57) == dev.awaken.progression.Rank.B
                    && MobLoot.rankFor(200) == dev.awaken.progression.Rank.SOVEREIGN, "rank for content level");
            h.assertTrue(MobLoot.count(0.4F, 0.04F, 64, 57, 0.99F) == 3 && MobLoot.count(6F, 0.4F, 64, 0, 0F) == 6, "level-scaled count");
            ServerLevel level = h.getLevel();
            // Our permanent scaling must match the core fallback (MobHooks.scale) number for number.
            for (MobRole.Role role : List.of(MobRole.Role.NORMAL, MobRole.Role.ELITE, MobRole.Role.WARDEN, MobRole.Role.BOSS)) {
                Zombie a = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.MOB_SUMMONED);
                Zombie b = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.MOB_SUMMONED);
                MobRole mr = new MobRole(role, 57, "", Optional.empty(), "");
                MobHooks.scale(a, 57, mr);
                MobScaling.apply(b, 57, mr, Tier.NORMAL);
                h.assertTrue(Math.abs(a.getMaxHealth() - b.getMaxHealth()) < 1e-3, role + " health " + a.getMaxHealth() + " vs " + b.getMaxHealth());
                h.assertTrue(Math.abs(a.getAttributeValue(Attributes.ATTACK_DAMAGE) - b.getAttributeValue(Attributes.ATTACK_DAMAGE)) < 1e-3, role + " damage");
                h.assertTrue(Math.abs(a.getAttributeValue(Attributes.ARMOR) - b.getAttributeValue(Attributes.ARMOR)) < 1e-3, role + " armour");
                a.discard();
                b.discard();
            }
            AwakenMob ghoul = Mobs.spawn(level, Bestiary.get("toll_ghoul"), h.absoluteVec(new net.minecraft.world.phys.Vec3(0.5, 1, 0.5)), 35,
                    new MobRole(MobRole.Role.ELITE, 35, Theme.BELL.id, Optional.empty(), ""), Set.of());
            h.assertTrue(ghoul != null, "toll ghoul spawned");
            double want = 22 * (1 + 0.075 * 35) * 2.5;
            h.assertTrue(Math.abs(ghoul.getMaxHealth() - want) < 0.01, "elite toll ghoul at lv35 has " + want + " hp, has " + ghoul.getMaxHealth());
            h.assertTrue(ghoul.getHealth() == ghoul.getMaxHealth(), "spawned at full health");
            h.assertTrue(ghoul.getData(CoreApi.MOB_ROLE.get()).role() == MobRole.Role.ELITE, "role attachment set");
            ghoul.discard();
            h.succeed();
        });

        AwakenTests.register("mobs_spawner_every_theme", 60, h -> {
            ServerLevel level = h.getLevel();
            BlockPos pos = h.absolutePos(new BlockPos(0, 1, 0));
            List<String> ids = new ArrayList<>(Rosters.ids());
            for (Theme t : Theme.values()) if (t != Theme.FEAR) h.assertTrue(ids.contains(t.id), "roster for " + t.id);
            for (int s = 1; s <= 10; s++) h.assertTrue(ids.contains("awaken:stratum_" + s), "roster for stratum " + s);
            ids.add("awaken:not_a_real_theme");
            ids.add("");
            for (String theme : ids) {
                for (MobRole.Role role : List.of(MobRole.Role.NORMAL, MobRole.Role.ELITE, MobRole.Role.WARDEN, MobRole.Role.BOSS, MobRole.Role.BREAK, MobRole.Role.NIGHTMARE)) {
                    LivingEntity e = MobHooks.spawnThemed(level, theme, role, pos, 17, Set.of(), "test");
                    h.assertTrue(e instanceof AwakenMob, "spawnThemed(" + theme + ", " + role + ") gave " + e);
                    AwakenMob m = (AwakenMob) e;
                    MobRole.Role want = m.def().theme == Theme.FEAR ? MobRole.Role.NIGHTMARE : role;
                    h.assertTrue(m.role().role() == want, theme + " " + role + " role was " + m.role().role());
                    h.assertTrue(m.role().contentLevel() == 17, "content level carried");
                    e.discard();
                }
            }
            for (Theme t : Theme.values()) {
                if (!t.isGate()) continue;
                Rosters.Roster r = Rosters.forTheme(t.id);
                h.assertTrue(r.normals().size() >= 3, t.id + " has " + r.normals().size() + " normals");
                h.assertTrue(r.warden() != null && r.warden().tier == Tier.WARDEN, t.id + " has a Warden");
                h.assertTrue(r.boss() != null && r.boss().tier == Tier.LORD, t.id + " has a Lord");
            }
            for (int s = 1; s <= 10; s++) {
                Rosters.Roster r = Rosters.forTheme("awaken:stratum_" + s);
                h.assertTrue(r.normals().size() >= 2 && r.normals().size() <= 3, "stratum " + s + " fauna");
                h.assertTrue(r.boss() != null, "stratum " + s + " guardian");
            }
            h.succeed();
        });

        AwakenTests.register("mobs_spawner_every_boss", 60, h -> {
            ServerLevel level = h.getLevel();
            BlockPos pos = h.absolutePos(new BlockPos(0, 1, 0));
            List<String> required = new ArrayList<>(List.of("awaken:guardian_rim", "awaken:guardian_gardens", "awaken:guardian_choir", "awaken:guardian_orchard",
                    "awaken:guardian_silent", "awaken:guardian_foundry", "awaken:guardian_inverted", "awaken:guardian_dreaming", "awaken:guardian_court",
                    "awaken:signals_shadow", "awaken:sovereign_hunger", "awaken:sovereign_silence", "awaken:sovereign_sleep", "awaken:sovereign_machines",
                    "awaken:sovereign_mirrors", "awaken:sovereign_storms", "awaken:sovereign_roots", "awaken:sovereign_ash",
                    "awaken:bell_warden", "awaken:mother_of_spores", "awaken:ember_tyrant", "awaken:drowned_archivist", "awaken:gearwright_colossus",
                    "awaken:glass_serpent", "awaken:hive_regent", "awaken:root_saint", "awaken:lantern_broker", "awaken:abbot_of_frost",
                    "awaken:mirror_knight", "awaken:unit_zero", "awaken:the_sleeper", "awaken:sky_leviathan", "awaken:hollow_herald"));
            for (String f : Rosters.NIGHTMARE_FAMILIES) required.add("awaken:nightmare/" + f);
            for (String id : Mobs.bossIds()) if (!required.contains(id)) required.add(id);
            for (String id : required) {
                LivingEntity e = MobHooks.spawnBoss(level, id, pos, 35, "test");
                h.assertTrue(e instanceof AwakenMob, "spawnBoss(" + id + ") gave " + e);
                AwakenMob m = (AwakenMob) e;
                MobDef d = MobsSpawner.bossDef(id);
                h.assertTrue(m.def() == d, id + " spawned " + m.def().id);
                if (d.tier.isBoss()) {
                    h.assertTrue(e instanceof AwakenBoss, id + " is a boss entity");
                    h.assertTrue(m.bar() != null, id + " has a boss bar");
                    h.assertTrue(m.isPersistenceRequired(), id + " never despawns");
                    h.assertTrue(m.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0, id + " resists knockback");
                    h.assertTrue(d.phases.size() >= 2, id + " has phases");
                }
                e.discard();
            }
            LivingEntity unknown = MobHooks.spawnBoss(level, "awaken:nobody", pos, 5, "");
            h.assertTrue(unknown instanceof AwakenBoss, "unknown boss id falls back to a Lord");
            unknown.discard();
            h.succeed();
        });

        AwakenTests.register("mobs_boss_bar_and_phases", 80, h -> {
            ServerLevel level = h.getLevel();
            LivingEntity e = MobHooks.spawnBoss(level, "awaken:bell_warden", h.absolutePos(new BlockPos(0, 1, 0)), 5, "");
            AwakenBoss boss = (AwakenBoss) e;
            h.assertTrue(boss.bar() != null && boss.bar().isVisible(), "visible bar");
            h.assertTrue(boss.def().phases.size() == 3, "three phases");
            boss.setHealth(boss.getMaxHealth() * 0.5F);
            h.runAfterDelay(8, () -> {
                h.assertTrue(boss.phase() == 1, "phase two below 66% (was " + boss.phase() + ")");
                h.assertTrue(Math.abs(boss.bar().getProgress() - 0.5F) < 0.05F, "bar follows health");
                boss.discard();
                h.succeed();
            });
        });

        AwakenTests.register("mobs_boss_bar_only_on_its_storey", 100, h -> {
            // tracking ignores height and gate floors are stacked 32 apart: a Lord's bar must not show a floor up
            ServerLevel level = h.getLevel();
            AwakenBoss boss = (AwakenBoss) MobHooks.spawnBoss(level, "awaken:bell_warden", h.absolutePos(new BlockPos(0, 1, 0)), 5, "");
            ServerPlayer near = h.makeMockServerPlayerInLevel();
            ServerPlayer above = h.makeMockServerPlayerInLevel();
            near.snapTo(boss.getX() + 3, boss.getY(), boss.getZ());
            above.snapTo(boss.getX() + 3, boss.getY() + 32, boss.getZ());
            boss.startSeenByPlayer(near);
            boss.startSeenByPlayer(above);
            h.assertTrue(boss.bar().getPlayers().contains(near), "a player beside the Lord sees its bar");
            h.assertTrue(!boss.bar().getPlayers().contains(above), "a player a floor up doesn't");
            above.snapTo(boss.getX() - 3, boss.getY() + 2, boss.getZ());
            h.succeedWhen(() -> {
                h.assertTrue(boss.bar().getPlayers().contains(above), "the bar shows once they come down to its floor");
                boss.discard();
            });
        });

        AwakenTests.register("mobs_volatile_explodes_on_death", 100, h -> {
            ServerLevel level = h.getLevel();
            BlockPos pos = h.absolutePos(new BlockPos(0, 1, 0));
            Zombie bystander = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.MOB_SUMMONED);
            bystander.snapTo(pos.getX() + 1.5, pos.getY(), pos.getZ() + 0.5);
            bystander.setNoAi(true);
            level.addFreshEntity(bystander);
            float before = bystander.getHealth();
            LivingEntity e = MobHooks.spawnThemed(level, "awaken:bell_crypt", MobRole.Role.NORMAL, pos, 5, EnumSet.of(Forces.Tag.VOLATILE), "");
            AwakenMob m = (AwakenMob) e;
            m.setNoAi(true);
            h.assertTrue(Forces.has(m, Forces.Tag.VOLATILE) && m.flag(AwakenMob.F_VOLATILE), "volatile tag mirrored");
            m.kill(level);
            h.succeedWhen(() -> {
                h.assertTrue(m.isRemoved(), "volatile mob finished dying");
                h.assertTrue(bystander.getHealth() < before || bystander.isRemoved(), "explosion hurt the bystander");
                bystander.discard();
            });
        });

        AwakenTests.register("mobs_echo_rises_once", 140, h -> {
            ServerLevel level = h.getLevel();
            // a floor of its own to fall onto (it stood on whatever other tests had left under the plot, else fell 66
            // blocks to the superflat ground and wasn't up again in time)
            AwakenTests.site(h, new BlockPos(-3, 0, -3), new BlockPos(3, 4, 3), Blocks.STONE.defaultBlockState());
            AwakenMob m = Mobs.spawn(level, Bestiary.get("toll_ghoul"), h.absoluteVec(new net.minecraft.world.phys.Vec3(0.5, 1, 0.5)), 5,
                    new MobRole(MobRole.Role.NORMAL, 5, Theme.BELL.id, Optional.empty(), ""), EnumSet.of(Forces.Tag.ECHO));
            m.hurtServer(level, level.damageSources().generic(), 10000F);
            h.assertTrue(m.isAlive() && m.flag(AwakenMob.F_FALLEN), "first death falls instead of dying");
            h.runAfterDelay(70, () -> {
                h.assertTrue(m.isAlive() && m.flag(AwakenMob.F_RISEN) && !m.flag(AwakenMob.F_FALLEN), "it got up");
                h.assertTrue(m.getHealth() > m.getMaxHealth() * 0.4F && m.getHealth() <= m.getMaxHealth() * 0.5F + 0.01F, "back at half health, has " + m.getHealth());
                m.damageCooldownTime = 0;
                m.hurtServer(level, level.damageSources().generic(), 10000F);
                h.assertTrue(m.isDeadOrDying(), "second death is final");
                h.succeed();
            });
        });

        AwakenTests.register("mobs_break_is_a_roaming_lord", 40, h -> {
            ServerLevel level = h.getLevel();
            LivingEntity e = MobHooks.spawnThemed(level, "awaken:bell_crypt", MobRole.Role.BREAK, h.absolutePos(new BlockPos(1, 1, 1)), 10, Set.of(), "test");
            h.assertTrue(e instanceof AwakenBoss b && b.def().tier == Tier.LORD, "a broken gate's Lord is the theme's Lord, got " + e);
            AwakenBoss lord = (AwakenBoss) e;
            h.assertTrue(lord.role().role() == MobRole.Role.BREAK, "role BREAK");
            h.assertTrue(lord.bar() == null, "no boss bar of its own (the gates show one)");
            double want = lord.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).getBaseValue()
                    * MobScaling.totalHealth(10, MobRole.Role.BREAK, Tier.LORD);
            h.assertTrue(Math.abs(lord.getMaxHealth() - want) < 0.5, "Lord-sized health " + lord.getMaxHealth() + ", want " + want);
            h.assertTrue(lord.isPersistenceRequired(), "it stays until the break is held");
            lord.discard();
            h.succeed();
        });

        AwakenTests.register("mobs_loot_tables_present", 40, h -> {
            var regs = h.getLevel().getServer().reloadableRegistries();
            for (MobDef d : Bestiary.all()) {
                ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, Awaken.id("entities/" + d.id));
                LootTable t = regs.getLootTable(key);
                h.assertTrue(t != LootTable.EMPTY, "loot table for " + d.id);
                var type = MobTypes.type(d);
                h.assertTrue(type != null && type.getDefaultLootTable().isPresent() && type.getDefaultLootTable().get().equals(key), d.id + " uses its table");
            }
            h.succeed();
        });

        AwakenTests.register("mobs_bestiary_complete", 20, h -> {
            int gateNormals = 0;
            for (MobDef d : Bestiary.all()) {
                h.assertTrue(MobTypes.type(d) != null, d.id + " registered");
                h.assertTrue(MobTypes.eggs().containsKey(d.id), d.id + " has a spawn egg");
                if (d.tier == Tier.NORMAL && d.theme.isGate()) gateNormals++;
                // Ambient overworld creatures never fight, so they have nothing to telegraph.
                boolean passive = d.has(Trait.FUSE) || d.has(Trait.STEALER) || (d.fauna != null && d.fauna.peaceful());
                h.assertTrue(passive || !d.phases.get(0).abilities().isEmpty(), d.id + " has a telegraphed ability");
                if (d.tier == Tier.LORD || d.tier == Tier.SOVEREIGN || d.tier == Tier.FINAL) h.assertTrue(d.phases.size() == 3, d.id + " has three phases");
                if (d.tier == Tier.LORD) h.assertTrue(d.parts.size() == 2, d.id + " drops two parts");
                if (d.tier.countsAsBoss()) h.assertTrue(!d.parts.isEmpty(), d.id + " drops a part");
                for (MobDef.Part p : d.parts) h.assertTrue(MobTypes.part(p.id()) != null, "part item " + p.id());
                for (MobDef.Phase ph : d.phases) {
                    for (Ability a : ph.abilities()) {
                        if (a.kind() == Ability.Kind.SUMMON) h.assertTrue(a.summon() != null && Bestiary.get(a.summon()) != null, d.id + " summons " + a.summon());
                    }
                }
            }
            h.assertTrue(gateNormals >= 45, "at least 45 gate creatures, have " + gateNormals);
            h.succeed();
        });

        AwakenTests.register("mobs_boss_kill_counts_and_pity", 60, h -> {
            ServerLevel level = h.getLevel();
            ServerPlayer sp = h.makeMockServerPlayerInLevel();
            BossRecord rec = BossRecord.EMPTY.afterBoss(false).afterBoss(false);
            h.assertTrue(!rec.pityDue(), "two empty bosses: no pity yet");
            rec = rec.afterBoss(false);
            h.assertTrue(rec.pityDue(), "three empty bosses: pity due");
            h.assertTrue(!rec.afterBoss(true).pityDue() && rec.afterBoss(true).sinceCore() == 0, "a core resets pity");
            long before = Progression.profile(sp).counter("awaken:bosses_killed");
            long unarmed = Progression.profile(sp).counter("awaken:bosses_killed_unarmed");
            LivingEntity e = MobHooks.spawnBoss(level, "awaken:root_saint", h.absolutePos(new BlockPos(0, 1, 0)), 5, "");
            e.hurtServer(level, level.damageSources().playerAttack(sp), 1.0E6F);
            h.runAfterDelay(2, () -> {
                h.assertTrue(e.isDeadOrDying(), "boss died");
                h.assertTrue(Progression.profile(sp).counter("awaken:bosses_killed") == before + 1, "bosses_killed counted");
                h.assertTrue(Progression.profile(sp).counter("awaken:bosses_killed_unarmed") == unarmed + 1, "unarmed kill counted (mock player has empty hands)");
                h.assertTrue(sp.getData(MobLoot.BOSS_RECORD.get()).hasKilled("awaken:root_saint"), "first kill remembered");
                h.succeed();
            });
        });

        AwakenTests.register("mobs_decoys_and_summons_are_cheap", 40, h -> {
            ServerLevel level = h.getLevel();
            AwakenMob boss = (AwakenMob) MobHooks.spawnBoss(level, "awaken:bell_warden", h.absolutePos(new BlockPos(0, 1, 0)), 5, "");
            h.assertTrue(Mobs.minionRoom(level, boss, 3), "room for the first summons");
            List<Entity> spawned = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                AwakenMob m = Mobs.spawnMinion(level, Bestiary.get("toll_ghoul"), boss.position(), boss);
                h.assertTrue(m != null && m.role().gateId().isEmpty(), "minions carry no gate id");
                spawned.add(m);
            }
            h.assertTrue(!Mobs.minionRoom(level, boss, 3), "summon budget stops a seventh wave");
            spawned.forEach(Entity::discard);
            boss.discard();
            h.succeed();
        });
    }
}
