package dev.awaken.gates;

import dev.awaken.core.Forces;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.content.Layout;
import dev.awaken.gates.content.Modifier;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.content.Twist;
import dev.awaken.gates.entity.EchoGhost;
import dev.awaken.gates.entity.GateHoarder;
import dev.awaken.gates.entity.GateShade;
import dev.awaken.gates.entity.HollowFollower;
import dev.awaken.gates.run.GateMobs;
import dev.awaken.gates.run.Interactions;
import dev.awaken.gates.run.Modifiers;
import dev.awaken.gates.run.Pressure;
import dev.awaken.gates.run.Rooms;
import dev.awaken.gates.run.RunEvents;
import dev.awaken.gates.run.RunState;
import dev.awaken.gates.run.Runs;
import dev.awaken.gates.run.Satchel;
import dev.awaken.gates.run.Twists;
import dev.awaken.gates.world.Cells;
import dev.awaken.gates.world.FeatureBlocks;
import dev.awaken.gates.world.FloorLayout;
import dev.awaken.gates.world.GateCells;
import dev.awaken.gates.world.GateLayout;
import dev.awaken.gates.world.GatePainter;
import dev.awaken.gates.world.Room;
import dev.awaken.mobs.entity.AwakenMob;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Live smoke tests for the twists and modifiers: a gate stands in the test level (its floors painted in place), a mock
 * player walks in, and each rule is checked where it acts. Every test runs inside one tick and resets the test level.
 */
final class GateLiveTests {
    private GateLiveTests() {}

    static void register() {
        AwakenTests.registerIsolated("gates_modifiers_live", 100, h -> {
            ServerLevel level = h.getLevel();
            GatePlan plan = plan(818181, Theme.BELL_CRYPT, Layout.LABYRINTH, Twist.NONE, 0,
                    Modifier.LOW_GRAVITY, Modifier.NO_REGEN, Modifier.GLASS, Modifier.SWARM, Modifier.ECHOING, Modifier.VOLATILE);
            GateService.testLevel = level;
            ServerPlayer p = null;
            GateRecord g = null;
            try {
                g = standUp(h, plan, 0, 1);
                p = player(h);
                h.assertTrue(Runs.enter(p, g), "entered");
                // Low Gravity: lighter while inside, normal again outside
                h.assertTrue(p.getAttributeValue(Attributes.GRAVITY) < 0.05, "Low Gravity lightens you, gravity " + p.getAttributeValue(Attributes.GRAVITY));
                // No Regen: trickle healing is refused, real healing still works
                p.setHealth(10F);
                p.heal(1F);
                h.assertTrue(p.getHealth() == 10F, "No Regen refuses natural regeneration, health " + p.getHealth());
                p.heal(4F);
                h.assertTrue(p.getHealth() == 14F, "No Regen lets a real heal through, health " + p.getHealth());
                // Glass: everyone deals double
                var hit = new LivingIncomingDamageEvent(p, new DamageContainer(level.damageSources().generic(), 3F));
                NeoForge.EVENT_BUS.post(hit);
                h.assertTrue(hit.getAmount() == 6F, "Glass doubles damage taken, was " + hit.getAmount());
                FloorLayout f = GateLayout.of(plan).floor(0, 1);
                Room r = biggestCombat(f);
                LivingEntity mob = Rooms.spawn(level, g, f, r.id, MobRole.Role.NORMAL, Rooms.spots(level, g, f, r).get(0));
                h.assertTrue(mob != null, "a gate monster spawned");
                var dealt = new LivingIncomingDamageEvent(mob, new DamageContainer(p.damageSources().playerAttack(p), 3F));
                NeoForge.EVENT_BUS.post(dealt);
                h.assertTrue(dealt.getAmount() == 6F, "Glass doubles damage dealt, was " + dealt.getAmount());
                // Swarm: weaker each (spawn-time only)
                h.assertTrue(mob.getAttribute(Attributes.MAX_HEALTH).getModifier(dev.awaken.Awaken.id("gate_swarm_health")) != null,
                        "Swarm monsters spawn with less health");
                // Echoing: the dead get up once; Volatile: the dead burst (no block damage)
                h.assertTrue(Forces.has(mob, Forces.Tag.ECHO) && Forces.has(mob, Forces.Tag.VOLATILE), "modifier tags reach the monster");
                var key = new GateMobs.Key(g.id, 0, 1, r.id);
                if (mob instanceof AwakenMob am) {
                    // Awaken creatures rise by themselves: the first death is a fall (mobs module)
                    am.hurtServer(level, p.damageSources().playerAttack(p), 1_000_000F);
                    h.assertTrue(am.isAlive() && am.flag(AwakenMob.F_FALLEN), "an Echoing creature falls instead of dying");
                }
                mob.discard();
                // Anything else the gate brings back once, as a fresh monster of the same room
                Zombie plain = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
                h.assertTrue(plain != null, "a plain monster");
                plain.snapTo(mob.getX(), mob.getY(), mob.getZ(), 0, 0);
                plain.setData(CoreApi.MOB_ROLE.get(), new MobRole(MobRole.Role.NORMAL, 1, "", Optional.empty(), g.key()));
                level.addFreshEntity(plain);
                GateMobs.register(plain, key);
                Forces.tag(plain, Forces.Tag.ECHO);
                Forces.tag(plain, Forces.Tag.VOLATILE);
                BlockPos under = plain.blockPosition().below();
                var floorBefore = level.getBlockState(under);
                plain.hurtServer(level, p.damageSources().playerAttack(p), 1_000_000F);
                h.assertFalse(plain.isAlive(), "the monster died");
                plain.discard();
                h.assertTrue(GateMobs.alive(key) == 0, "its room is empty for a moment, alive " + GateMobs.alive(key));
                RunEvents.pendingNow(level.getServer());
                h.assertTrue(GateMobs.alive(key) == 1, "an Echoing monster rises once, alive " + GateMobs.alive(key));
                LivingEntity risen = (LivingEntity) level.getEntity(GateMobs.aliveIds(key).iterator().next());
                h.assertTrue(risen != null && !Forces.has(risen, Forces.Tag.ECHO) && Forces.hasRisen(risen), "the risen one won't rise again");
                risen.hurtServer(level, p.damageSources().playerAttack(p), 1_000_000F);
                h.assertFalse(risen.isAlive(), "the risen one stays down");
                risen.discard();
                RunEvents.pendingNow(level.getServer());
                h.assertTrue(GateMobs.alive(key) == 0, "the second death is final, alive " + GateMobs.alive(key));
                h.assertTrue(level.getBlockState(under).equals(floorBefore), "Volatile bursts break no blocks");
                Gates.forceExtract(p, false);
                h.assertTrue(p.getAttributeValue(Attributes.GRAVITY) > 0.07, "gravity is back to normal outside");
            } finally {
                teardown(level, p, g);
            }
            h.succeed();
        });

        AwakenTests.registerIsolated("gates_modifiers_timed", 100, h -> {
            ServerLevel level = h.getLevel();
            GatePlan plan = plan(828282, Theme.EMBER_MINES, Layout.LABYRINTH, Twist.NONE, 0,
                    Modifier.MIRROR, Modifier.SILENCE, Modifier.COLLAPSE_TIMER);
            GateService.testLevel = level;
            ServerPlayer p = null;
            GateRecord g = null;
            try {
                g = standUp(h, plan, 0, 1);
                p = player(h);
                h.assertTrue(Runs.enter(p, g), "entered");
                FloorLayout f = GateLayout.of(plan).floor(0, 1);
                Room r = biggestCombat(f);
                put(level, p, g, f, r);
                long now = GateService.now(level.getServer());
                RunState run = Runs.run(p);
                run.floorStartedAt = now - 41 * 20;
                run.noise = 30;
                RunEvents.tickNow(p);
                AABB around = p.getBoundingBox().inflate(48);
                h.assertTrue(level.getEntitiesOfClass(GateShade.class, around).size() == 1, "Mirror: your shadow steps out after 40 s");
                h.assertTrue(!level.getEntitiesOfClass(LivingEntity.class, around,
                        e -> e.getCustomName() != null && e.getCustomName().getString().equals("Listener")).isEmpty(), "Silence: noise draws a Listener");
                RunEvents.tickNow(p);
                h.assertTrue(level.getEntitiesOfClass(GateShade.class, around).size() == 1, "one shadow per floor");
                // Collapse Timer: the floor comes down once its time is up
                FloorState fs = g.floor(0, 1);
                fs.enteredAt = now - Modifiers.collapseTicks(g) - 1;
                RunEvents.tickGateNow(level, g);
                h.assertTrue(fs.collapsed, "Collapse Timer: the floor caves in on time");
                // pressure: waves after 4 minutes, the Warden at 8
                h.assertTrue(fs.pressureWaves == 0 && !fs.warden, "no pressure early on");
                run.floorStartedAt = now - Pressure.WARDEN - 1;
                RunEvents.tickGateNow(level, g);
                h.assertTrue(fs.pressureWaves == 1, "the gate sends a wave, waves " + fs.pressureWaves);
                h.assertTrue(fs.warden, "the theme's Warden arrives at 8 minutes");
                h.assertTrue(GateMobs.alive(new GateMobs.Key(g.id, 0, 1, -1)) >= 3, "wave, Warden, Listener and shadow are tracked on the floor");
                h.assertTrue(Pressure.stage(g, Pressure.START - 600) == 1 && Pressure.stage(g, Pressure.START) == 2 && Pressure.stage(g, Pressure.WARDEN) == 3,
                        "pressure stages");
            } finally {
                teardown(level, p, g);
            }
            h.succeed();
        });

        AwakenTests.registerIsolated("gates_twist_echo_hollow", 100, h -> {
            ServerLevel level = h.getLevel();
            GateService.testLevel = level;
            ServerPlayer p = null;
            GateRecord echo = null, hollow = null;
            try {
                echo = standUp(h, plan(838383, Theme.HIVE, Layout.LABYRINTH, Twist.ECHO_GATE, 0), 0, 1);
                p = player(h);
                h.assertTrue(Runs.enter(p, echo), "entered the Echo Gate");
                Entity ghost = RunEvents.ghostOf(p);
                h.assertTrue(ghost instanceof EchoGhost, "your echo walks the floor with you");
                h.assertTrue(ghost.distanceTo(p) < 40, "it starts in the start room, " + ghost.distanceTo(p) + " blocks away");
                Gates.forceExtract(p, false);
                h.assertTrue(RunEvents.ghostOf(p) == null && ghost.isRemoved(), "the echo goes when you do");

                hollow = standUp(h, plan(848484, Theme.FUNGAL_HOLLOW, Layout.LABYRINTH, Twist.HOLLOW_GATE, 0), 0, 1);
                FloorLayout hf = GateLayout.of(hollow.plan).floor(0, 1);
                h.assertTrue(hf.plannedMobs == 0, "a Hollow floor has no monsters");
                h.assertTrue(Runs.enter(p, hollow), "entered the Hollow Gate");
                RunEvents.tickGateNow(level, hollow);
                h.assertTrue(RunEvents.twistActor(level, hollow, 0, 1, true) == null, "nothing follows at first");
                Runs.run(p).floorStartedAt -= 15 * 20 + 1;
                RunEvents.tickGateNow(level, hollow);
                h.assertTrue(RunEvents.twistActor(level, hollow, 0, 1, true) instanceof HollowFollower, "after 15 s something came in after you");
                h.assertTrue(Pressure.stage(hollow, Pressure.WARDEN) == 0, "no pressure waves in a Hollow Gate");
            } finally {
                teardown(level, p, echo, hollow);
            }
            h.succeed();
        });

        AwakenTests.registerIsolated("gates_twist_inverted_treasure", 100, h -> {
            ServerLevel level = h.getLevel();
            GateService.testLevel = level;
            ServerPlayer p = null;
            GateRecord inv = null, treasure = null;
            try {
                inv = standUp(h, plan(858585, Theme.EMBER_MINES, Layout.LABYRINTH, Twist.INVERTED, 0), 0, 1);
                p = player(h);
                h.assertTrue(Runs.enter(p, inv), "entered the Inverted gate");
                h.assertTrue(RunEvents.hasPuppet(p), "you wear one of the Lord's minions");
                h.assertTrue(Twists.disguised(p, inv), "disguised on arrival");
                FloorLayout f = GateLayout.of(inv.plan).floor(0, 1);
                Room r = biggestCombat(f);
                LivingEntity minion = Rooms.spawn(level, inv, f, r.id, MobRole.Role.NORMAL, Rooms.spots(level, inv, f, r).get(0));
                h.assertTrue(minion != null, "a minion spawned");
                var look = new LivingChangeTargetEvent(minion, p, LivingChangeTargetEvent.LivingTargetType.MOB_TARGET);
                NeoForge.EVENT_BUS.post(look);
                h.assertTrue(look.isCanceled(), "minions don't attack one of their own");
                minion.hurtServer(level, p.damageSources().playerAttack(p), 1F);
                h.assertFalse(Twists.disguised(p, inv), "striking one breaks the disguise for a while");
                var look2 = new LivingChangeTargetEvent(minion, p, LivingChangeTargetEvent.LivingTargetType.MOB_TARGET);
                NeoForge.EVENT_BUS.post(look2);
                h.assertFalse(look2.isCanceled(), "now they come for you");
                Gates.forceExtract(p, false);
                h.assertFalse(RunEvents.hasPuppet(p), "the disguise stays behind");

                treasure = standUp(h, plan(868686, Theme.OVERGROWN_TEMPLE, Layout.LABYRINTH, Twist.TREASURE_GATE, 0), 0, 1);
                h.assertTrue(Runs.enter(p, treasure), "entered the Treasure Gate");
                Entity hoarder = RunEvents.twistActor(level, treasure, 0, 1, false);
                h.assertTrue(hoarder instanceof GateHoarder, "a Hoarder waits on the floor");
                ((LivingEntity) hoarder).hurtServer(level, p.damageSources().playerAttack(p), 1_000_000F);
                h.assertFalse(hoarder.isAlive(), "caught it");
                h.assertTrue(Satchel.items(Runs.run(p).satchel) > 0, "its hoard went into the Satchel");
            } finally {
                teardown(level, p, inv, treasure);
            }
            h.succeed();
        });

        AwakenTests.registerIsolated("gates_twist_friendly_red", 100, h -> {
            ServerLevel level = h.getLevel();
            GateService.testLevel = level;
            ServerPlayer p = null;
            GateRecord friendly = null, red = null;
            try {
                friendly = standUp(h, plan(878787, Theme.OVERGROWN_TEMPLE, Layout.LABYRINTH, Twist.FRIENDLY, 0), 0, 1);
                p = player(h);
                h.assertTrue(Runs.enter(p, friendly), "entered the Friendly gate");
                FloorLayout f = GateLayout.of(friendly.plan).floor(0, 1);
                FloorState fs = friendly.floor(0, 1);
                Room end = f.room(f.end);
                BlockPos seal = Rooms.barsPositions(friendly, f, end.doors.get(0)).get(0);
                h.assertTrue(level.getBlockState(seal).is(GatesRegistry.BARS.get()), "the shrine is sealed until the spirit rests");
                int lanterns = 0;
                for (Room r : f.rooms) {
                    for (Room.Feature ft : r.features(Room.FeatureKind.LANTERN)) {
                        BlockPos lp = GateService.roomFloor(friendly, f, r, ft.x(), ft.z()).above(ft.y());
                        h.assertTrue(level.getBlockState(lp).is(GatesRegistry.LANTERN.get()), "a lost lantern stands at " + lp);
                        Interactions.takeLantern(p, lp);
                        lanterns++;
                    }
                }
                h.assertTrue(lanterns == 3 && fs.lanternsTaken.size() == 3, "three lanterns found (" + lanterns + ", taken " + fs.lanternsTaken.size() + ")");
                Allay spirit = null;
                for (Allay a : level.getEntitiesOfClass(Allay.class, cellBox(level, friendly))) {
                    if (a.getPersistentData().getIntOr(Rooms.SPIRIT_TAG, -1) == friendly.id) spirit = a;
                }
                h.assertTrue(spirit != null, "the lost spirit is waiting");
                RunEvents.talkToSpirit(p, friendly.id, spirit);
                h.assertTrue(fs.spiritDone && fs.shrineOpen, "the spirit rests and the shrine opens");
                h.assertTrue(level.getBlockState(seal).isAir(), "the shrine's bars are gone");
                h.assertTrue(Satchel.items(Runs.run(p).satchel) > 0, "the spirit's gift is in the Satchel");
                h.assertTrue(Pressure.stage(friendly, Pressure.WARDEN) == 0, "a Friendly gate never presses you");
                Gates.forceExtract(p, false);

                red = standUp(h, plan(888888, Theme.EMBER_MINES, Layout.LABYRINTH, Twist.RED_GATE, 0));
                h.assertTrue(Runs.enter(p, red), "entered the Red Gate");
                Runs.run(p).cleared.add("0:1");
                Runs.extract(p, false);
                h.assertTrue(Runs.run(p).active(), "a Red Gate's exit stays sealed until the Lord dies");
                h.assertTrue(Pressure.stage(red, 3 * 60 * 20) == 2, "a Red Gate presses sooner");
                Gates.forceExtract(p, false);
                h.assertFalse(Runs.run(p).active(), "the story can still pull you out");
            } finally {
                teardown(level, p, friendly, red);
            }
            h.succeed();
        });

        AwakenTests.registerIsolated("gates_twist_double_gate", 100, h -> {
            ServerLevel level = h.getLevel();
            GateService.testLevel = level;
            ServerPlayer p = null;
            GateRecord g = null;
            try {
                // law 2: strike from behind
                g = standUp(h, plan(898989, Theme.EMBER_MINES, Layout.ARENA_CHAIN, Twist.DOUBLE_GATE, 2), 0, 3, 1, 1);
                p = player(h);
                h.assertTrue(Runs.enter(p, g), "entered the Double Gate");
                FloorLayout bf = GateLayout.of(g.plan).floor(0, 3);
                Room arena = bf.room(bf.end);
                Room.Feature rift = arena.feature(Room.FeatureKind.RIFT_WALL);
                h.assertTrue(rift != null, "the Lord's arena has a cracked wall");
                BlockPos at = GateService.roomFloor(g, bf, arena, arena.centerX() + 6, arena.centerZ() + 6);
                p.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), 0, 0, true);
                RunEvents.tickNow(p);
                h.assertTrue(g.boss.isPresent(), "the Lord appeared");
                ((LivingEntity) level.getEntity(g.boss.get())).hurtServer(level, p.damageSources().playerAttack(p), 1_000_000F);
                h.assertTrue(g.bossDead, "the Lord fell");
                for (int[] b : FeatureBlocks.riftWall(arena, rift)) {
                    BlockPos bp = GateService.world(g, bf, b[0], arena.dy + b[1], b[2]);
                    if (b[3] == 2) h.assertTrue(level.getBlockState(bp).is(GatesRegistry.RIFT.get()), "the wall tore open into a rift at " + bp);
                    else h.assertTrue(level.getBlockState(bp).isAir(), "the cracked layer fell away at " + bp);
                }
                RunEvents.enterAnnexNow(p, g);
                RunState run = Runs.run(p);
                h.assertTrue(run.area == 1 && run.floor == 1, "through the rift into the second gate, at " + run.area + ":" + run.floor);
                FloorLayout af = GateLayout.of(g.plan).floor(1, 1);
                Room r = biggestCombat(af);
                LivingEntity m = Rooms.spawn(level, g, af, r.id, MobRole.Role.NORMAL, Rooms.spots(level, g, af, r).get(0));
                h.assertTrue(m != null, "a second-gate monster spawned");
                m.setYRot(0);
                m.setYBodyRot(0);
                m.setYHeadRot(0);
                float hp = m.getHealth();
                p.teleportTo(level, m.getX(), m.getY(), m.getZ() + 2, Set.of(), 180, 0, true);
                h.assertFalse(Twists.lawHolds(2, p, m, level), "face to face breaks the law");
                m.hurtServer(level, p.damageSources().playerAttack(p), 2F);
                h.assertTrue(m.getHealth() == hp, "the law turns the blow aside, health " + m.getHealth() + " of " + hp);
                p.teleportTo(level, m.getX(), m.getY(), m.getZ() - 2, Set.of(), 0, 0, true);
                h.assertTrue(Twists.lawHolds(2, p, m, level), "from behind keeps the law");
                m.hurtServer(level, p.damageSources().playerAttack(p), 2F);
                h.assertTrue(m.getHealth() < hp, "a strike from behind lands");
            } finally {
                teardown(level, p, g);
            }
            h.succeed();
        });

        AwakenTests.registerIsolated("gates_twist_gate_within", 100, h -> {
            ServerLevel level = h.getLevel();
            GateService.testLevel = level;
            ServerPlayer p = null;
            GateRecord g = null;
            try {
                GatePlan plan = plan(909090, Theme.BELL_CRYPT, Layout.LABYRINTH, Twist.GATE_WITHIN, 0);
                h.assertTrue(plan.innerGateFloor() == 2, "the inner gate opens on floor 2");
                g = standUp(h, plan, 0, 2, 1, 2);
                FloorLayout f2 = GateLayout.of(plan).floor(0, 2);
                Room inner = null;
                Room.Feature ig = null;
                for (Room r : f2.rooms) {
                    if (r.feature(Room.FeatureKind.INNER_GATE) != null) {
                        inner = r;
                        ig = r.feature(Room.FeatureKind.INNER_GATE);
                    }
                }
                h.assertTrue(inner != null, "floor 2 holds the inner gate");
                h.assertTrue(level.getBlockState(GateService.roomFloor(g, f2, inner, ig.x(), ig.z()).above(1)).is(GatesRegistry.INNER.get()),
                        "the inner gate's portal is painted");
                p = player(h);
                h.assertTrue(Runs.enter(p, g), "entered");
                RunEvents.enterAnnexNow(p, g);
                RunState run = Runs.run(p);
                h.assertTrue(run.area == 1 && run.floor == 1, "inside the inner gate");
                FloorLayout a2 = GateLayout.of(plan).floor(1, 2);
                h.assertTrue(a2 != null && a2.bossFloor, "the inner gate has two floors and a keeper");
                Room arena = a2.room(a2.end);
                BlockPos at = GateService.roomFloor(g, a2, arena, arena.centerX() + 6, arena.centerZ() + 6);
                p.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), 0, 0, true);
                RunEvents.tickNow(p);
                h.assertTrue(run.area == 1 && run.floor == 2, "the run follows you down, at " + run.area + ":" + run.floor);
                h.assertTrue(g.annexBoss.isPresent(), "the keeper appeared");
                ((LivingEntity) level.getEntity(g.annexBoss.get())).hurtServer(level, p.damageSources().playerAttack(p), 1_000_000F);
                h.assertTrue(g.annexBossDead && !g.bossDead && g.state == GateState.ACTIVE, "the keeper fell; the gate's own Lord still waits, state " + g.state);
                Room.Feature ret = arena.feature(Room.FeatureKind.ANNEX_RETURN);
                h.assertTrue(ret != null && level.getBlockState(GateService.world(g, a2, ret.x(), arena.dy + 1, ret.z())).is(GatesRegistry.RETURN.get()),
                        "a way back opened");
                RunEvents.returnFromInnerNow(p, g);
                h.assertTrue(run.area == 0 && run.floor == 2, "back on floor 2, at " + run.area + ":" + run.floor);
                RunEvents.enterAnnexNow(p, g);
                h.assertTrue(run.area == 0, "the inner gate is quiet now");
            } finally {
                teardown(level, p, g);
            }
            h.succeed();
        });
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------

    static GatePlan plan(long seed, Theme theme, Layout layout, Twist twist, int annexRule, Modifier... mods) {
        Theme annex = theme == Theme.HIVE ? Theme.BELL_CRYPT : Theme.HIVE;
        return new GatePlan(seed, GatePlan.CONTENT_VERSION, Rank.E, theme, layout, List.of(mods), twist, 3, theme.bossId(), annex, annexRule);
    }

    /** A READY gate in a fresh cell of the test level with the given (area, floor) pairs painted. */
    static GateRecord standUp(GameTestHelper h, GatePlan plan, int... areaFloors) {
        ServerLevel level = h.getLevel();
        GateRegistry reg = GateRegistry.get(level.getServer());
        int cell = reg.allocateCell();
        GateCells.put(cell, plan);
        GateRecord g = new GateRecord(reg.nextId(), plan, h.absolutePos(BlockPos.ZERO), 0, cell, 0, 99, GateState.READY, Optional.empty());
        g.interiorReady = true;
        reg.put(g);
        for (int i = 0; i + 1 < areaFloors.length; i += 2) paintFloor(level, g, areaFloors[i], areaFloors[i + 1]);
        return g;
    }

    static void paintFloor(ServerLevel level, GateRecord g, int area, int floor) {
        GateLayout l = GateLayout.of(g.plan);
        FloorLayout f = l.floor(area, floor);
        for (int gx = -1; gx <= f.w; gx++) {
            for (int gz = -1; gz <= f.h; gz++) {
                int cx = (Cells.originX(g.cell) >> 4) + f.originSlotX + gx, cz = (Cells.originZ(g.cell) >> 4) + f.originSlotZ + gz;
                level.getChunk(cx, cz);
                GatePainter.paint(l, g.cell, cx, cz, (x, y, z, s) -> {
                    if (Cells.floorAtY(y) == f.floor) level.setBlock(new BlockPos(cx * 16 + x, y, cz * 16 + z), s, 818);
                });
            }
        }
    }

    static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        Progression.awaken(p);
        return p;
    }

    static Room biggestCombat(FloorLayout f) {
        Room best = null;
        for (int id : f.criticalPath()) {
            Room r = f.room(id);
            if (!r.type.combat() || r.type == Room.Type.BOSS || r.spawnSpots.isEmpty()) continue;
            if (best == null || r.gw * r.gh > best.gw * best.gh) best = r;
        }
        if (best == null) {
            for (Room r : f.rooms) if (r.type.combat() && r.type != Room.Type.BOSS && !r.spawnSpots.isEmpty()) best = r;
        }
        return best;
    }

    static void put(ServerLevel level, ServerPlayer p, GateRecord g, FloorLayout f, Room r) {
        int[] spot = r.spawnSpots.get(0);
        BlockPos at = GateService.roomFloor(g, f, r, spot[0], spot[1]);
        p.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), 0, 0, true);
    }

    static AABB cellBox(ServerLevel level, GateRecord g) {
        int x0 = Cells.originX(g.cell), z0 = Cells.originZ(g.cell);
        return new AABB(x0 - 16, level.getMinY(), z0 - 16, x0 + 528, level.getMaxY() + 1, z0 + 528);
    }

    /** Ends the run, clears the gates' monsters and forgets their cells; always resets the test level. */
    static void teardown(ServerLevel level, ServerPlayer p, GateRecord... gates) {
        try {
            if (p != null && Runs.run(p).active()) Gates.forceExtract(p, false);
            for (GateRecord g : gates) {
                if (g == null) continue;
                for (Entity e : level.getEntities((Entity) null, cellBox(level, g), x -> !(x instanceof Player))) e.discard();
                GateMobs.forgetGate(g.id);
                GateCells.remove(g.cell);
            }
        } finally {
            GateService.testLevel = null;
        }
    }
}
