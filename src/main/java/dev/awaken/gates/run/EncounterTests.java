package dev.awaken.gates.run;

import com.mojang.serialization.JsonOps;
import dev.awaken.core.api.GateHooks;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.FloorState;
import dev.awaken.gates.GateLiveTests;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateService;
import dev.awaken.gates.content.Layout;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.content.Twist;
import dev.awaken.gates.world.GateLayout;
import dev.awaken.gates.world.Room;
import dev.awaken.mobs.entity.AwakenMob;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.TagValueOutput;

import java.util.ArrayList;

/** Reproductions of cleared-room reinforcement, pre-entry activation, and runtime-index reload defects. */
public final class EncounterTests {
    private EncounterTests() {}

    public static void register() {
        AwakenTests.registerIsolated("gates_encounter_reload_once", 100, h -> {
            var level = h.getLevel();
            GateService.useTestLevel(level);
            GateRecord g = null;
            ServerPlayer p = null;
            try {
                var plan = GateLiveTests.plan(6161, Theme.BELL_CRYPT, Layout.ARENA_CHAIN, Twist.NONE, 0);
                g = GateLiveTests.standUp(h, plan, 0, 1);
                p = GateLiveTests.player(h);
                Runs.enter(p, g);
                var f = GateLayout.of(plan).floor(0, 1);
                Room room = f.rooms.stream().filter(r -> r.type == Room.Type.ARENA && !r.spawnSpots.isEmpty()).findFirst().orElseThrow();
                var key = new GateMobs.Key(g.id, 0, 1, room.id);
                Rooms.prepare(level, g, f, room);
                var ids = GateMobs.aliveIds(key);
                h.assertTrue(!ids.isEmpty(), "encounter exists before entry");
                GateLiveTests.put(level, p, g, f, room);
                Rooms.enter(level, p, g, f, room, room.centerX(), room.centerZ());
                // Real entity unload leaves the saved roster intact; a fresh runtime index sees the same company.
                var saved = new ArrayList<net.minecraft.nbt.CompoundTag>();
                for (var id : ids) {
                    Entity mob = level.getEntity(id);
                    var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
                    mob.save(output);
                    saved.add(output.buildResult());
                    mob.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                }
                h.assertTrue(GateMobs.alive(key) == ids.size(), "chunk unload is not a kill");
                var encoded = FloorState.CODEC.encodeStart(JsonOps.INSTANCE, g.floor(0, 1)).getOrThrow();
                g.floors.put("0:1", FloorState.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
                GateMobs.forgetGate(g.id);
                GateMobs.restore(level.getServer());
                Rooms.unsealAll(level, g);
                Rooms.enter(level, p, g, f, room, room.centerX(), room.centerZ());
                h.assertTrue(GateMobs.aliveIds(key).equals(ids) && g.floor(0, 1).waves.get(room.id) == 1,
                        "re-entry before entity load cannot advance or replay the saved first wave");
                for (var tag : saved) {
                    Entity mob = EntityType.loadEntityRecursive(tag, level, new net.minecraft.world.entity.EntitySpawnRequest(EntitySpawnReason.LOAD, false), e -> e);
                    h.assertTrue(mob != null, "saved member loads");
                    GateMobs.onLoad(mob);
                    level.addFreshEntity(mob);
                }
                h.assertTrue(GateMobs.aliveIds(key).equals(ids), "load-order reindex deduplicates each UUID");
                // Complete both actual waves, then re-enter and round-trip the old schema too.
                for (int wave = 0; wave < 2; wave++) for (var id : GateMobs.aliveIds(key)) {
                    Entity mob = level.getEntity(id);
                    if (mob != null) mob.discard();
                }
                h.assertTrue(g.floor(0, 1).clearedRooms.contains(room.id), "finished waves clear once");
                var legacy = FloorState.CODEC.encodeStart(JsonOps.INSTANCE, g.floor(0, 1)).getOrThrow().getAsJsonObject();
                legacy.remove("prepared");
                legacy.remove("living");
                g.floors.put("0:1", FloorState.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow());
                Rooms.prepareAround(level, g, f, room);
                Rooms.enter(level, p, g, f, room, room.centerX(), room.centerZ());
                h.assertTrue(GateMobs.alive(key) == 0 && !g.floor(0, 1).locked.contains(room.id), "legacy cleared encounters stay cleared and open");
            } finally { GateLiveTests.teardown(level, p, g); }
            h.succeed();
        });

        AwakenTests.registerIsolated("gates_encounter_preview_activation", 100, h -> {
            var level = h.getLevel();
            GateService.useTestLevel(level);
            GateRecord g = null;
            ServerPlayer p = null;
            ServerPlayer mate = null;
            try {
                var plan = GateLiveTests.plan(7171, Theme.HIVE, Layout.LABYRINTH, Twist.NONE, 0);
                g = GateLiveTests.standUp(h, plan, 0, 1);
                p = GateLiveTests.player(h);
                mate = GateLiveTests.player(h);
                Runs.enter(p, g);
                Runs.enter(mate, g);
                var f = GateLayout.of(plan).floor(0, 1);
                Room current = f.rooms.stream().filter(r -> r.doors.stream().filter(d -> {
                    Room next = f.room(d.toRoom);
                    return next.type.combat() && next.type != Room.Type.CAVERN && next.normals + next.elites > 0;
                }).count() >= 2).findFirst().orElseThrow();
                Rooms.prepareAround(level, g, f, current);
                var neighbours = current.doors.stream().map(d -> f.room(d.toRoom)).filter(r -> r.type.combat()
                        && r.type != Room.Type.CAVERN && r.normals + r.elites > 0).toList();
                Room a = neighbours.get(0), b = neighbours.get(1);
                var ka = new GateMobs.Key(g.id, 0, 1, a.id);
                var kb = new GateMobs.Key(g.id, 0, 1, b.id);
                Mob first = (Mob) level.getEntity(GateMobs.aliveIds(ka).iterator().next());
                Mob other = (Mob) level.getEntity(GateMobs.aliveIds(kb).iterator().next());
                h.assertTrue(first.isNoAi() && other.isNoAi(), "connected rooms contain visible waiting enemies before entry");
                h.assertFalse(GateHooks.mayTarget(first, p), "a preview does not acquire an approaching player");
                GateLiveTests.put(level, p, g, f, a);
                GateLiveTests.put(level, mate, g, f, a);
                // A real damage event also engages a preview; other room stays asleep for both players.
                first.hurtServer(level, p.damageSources().playerAttack(p), 1);
                h.assertTrue(!first.isNoAi() && GateHooks.mayTarget(first, mate), "an attacked encounter wakes and may fight another player in it");
                h.assertTrue(other.isNoAi() && !GateHooks.mayTarget(other, mate), "the adjacent encounter does not all aggro");
                var before = GateMobs.aliveIds(ka);
                Rooms.enter(level, p, g, f, a, a.centerX(), a.centerZ());
                Rooms.enter(level, mate, g, f, a, a.centerX(), a.centerZ());
                h.assertTrue(GateMobs.aliveIds(ka).equals(before), "two players entering share the same company");
                Room far = f.rooms.stream().max(java.util.Comparator.comparingInt(r -> Math.abs(r.centerX() - a.centerX()) + Math.abs(r.centerZ() - a.centerZ()))).orElseThrow();
                var at = GateService.roomFloor(g, f, far, far.centerX(), far.centerZ());
                mate.snapTo(at.getX() + .5, at.getY(), at.getZ() + .5);
                h.assertFalse(GateHooks.mayTarget(first, mate), "an engaged room cannot target the far end of the floor");
            } finally {
                if (mate != null && Runs.run(mate).active()) Runs.endRun(mate, g, false);
                GateLiveTests.teardown(level, p, g);
            }
            h.succeed();
        });

        AwakenTests.registerIsolated("gates_cavern_zone_clear", 100, h -> {
            var level = h.getLevel();
            GateService.useTestLevel(level);
            GateRecord g = null;
            ServerPlayer p = null;
            try {
                var plan = GateLiveTests.plan(7171, Theme.HIVE, Layout.OPEN_CAVERN, Twist.NONE, 0);
                g = GateLiveTests.standUp(h, plan, 0, 1);
                p = GateLiveTests.player(h);
                Runs.enter(p, g);
                var f = GateLayout.of(plan).floor(0, 1);
                Room cavern = f.rooms.stream().filter(r -> r.type == Room.Type.CAVERN).findFirst().orElseThrow();
                Rooms.prepare(level, g, f, cavern);
                var fs = g.floor(0, 1);
                int zoneKey = fs.living.values().stream().filter(k -> k >= 256).findFirst().orElseThrow();
                var key = new GateMobs.Key(g.id, 0, 1, zoneKey);
                Mob member = (Mob) level.getEntity(GateMobs.aliveIds(key).iterator().next());
                p.snapTo(member.getX(), member.getY(), member.getZ());
                var at = Where.at(level.getServer(), p.blockPosition());
                Rooms.enter(level, p, g, f, cavern, at.x(), at.z());
                h.assertTrue(fs.triggered.contains(zoneKey) && !member.isNoAi(), "cavern members stand in and wake with their own zone");
                for (var id : GateMobs.aliveIds(key)) level.getEntity(id).discard();
                Rooms.prepare(level, g, f, cavern);
                Rooms.enter(level, p, g, f, cavern, at.x(), at.z());
                h.assertTrue(fs.clearedRooms.contains(zoneKey) && GateMobs.alive(key) == 0, "cleared cavern zone never repopulates");
                h.assertFalse(Pressure.eligible(fs, cavern), "untouched neighbouring zones do not admit pressure into a cleared zone");
            } finally { GateLiveTests.teardown(level, p, g); }
            h.succeed();
        });

        AwakenTests.registerIsolated("gates_pressure_and_encounter_bars", 100, h -> {
            var level = h.getLevel();
            GateService.useTestLevel(level);
            GateRecord g = null;
            ServerPlayer p = null;
            try {
                var plan = GateLiveTests.plan(7171, Theme.HIVE, Layout.LABYRINTH, Twist.NONE, 0);
                g = GateLiveTests.standUp(h, plan, 0, 1);
                p = GateLiveTests.player(h);
                Runs.enter(p, g);
                var f = GateLayout.of(plan).floor(0, 1);
                Room room = GateLiveTests.biggestCombat(f);
                GateLiveTests.put(level, p, g, f, room);
                var fs = g.floor(0, 1);
                for (Room r : f.rooms) { fs.triggered.add(r.id); fs.clearedRooms.add(r.id); }
                h.assertTrue(Pressure.spotNear(level, g, f, p, 1) == null && Pressure.wave(level, g, f, p, 0) == 0,
                        "cleared rooms cannot receive timed reinforcements");
                fs.clearedRooms.remove(room.id);
                h.assertTrue(Pressure.spotNear(level, g, f, p, 1) != null, "unfinished engaged encounter still receives pressure");
                AwakenMob warden = (AwakenMob) Rooms.spawn(level, g, f, -1, MobRole.Role.WARDEN, p.blockPosition());
                h.assertTrue(warden != null, "Hive pressure Warden exists");
                warden.startSeenByPlayer(p);
                h.assertTrue(warden.bar() != null && warden.bar().getPlayers().contains(p)
                        && warden.bar().getName().getString().contains("Warden"), "nearby Swarm Mind is explicitly labelled Warden");
                warden.stopSeenByPlayer(p);
                // Adjacent floors can be physically close on stairs: room ownership must win over height alone.
                GateMobs.remove(warden);
                GateMobs.register(warden, new GateMobs.Key(g.id, 0, 2, -1));
                warden.startSeenByPlayer(p);
                h.assertFalse(warden.bar().getPlayers().contains(p), "another floor's bar is hidden even at identical height");
                var wardenId = warden.getUUID().toString();
                warden.hurtServer(level, p.damageSources().playerAttack(p), 1_000_000);
                h.assertTrue(!warden.isAlive() && !g.floor(0, 2).living.containsKey(wardenId), "a corpse is absent from the saved living roster before its animation ends");
                warden.discard();
                var bossFloor = GateLayout.of(plan).floor(0, 3);
                GateLiveTests.paintFloor(level, g, 0, 3);
                Room arena = bossFloor.rooms.stream().filter(r -> r.type == Room.Type.BOSS).findFirst().orElseThrow();
                Rooms.prepare(level, g, bossFloor, arena);
                AwakenMob lord = (AwakenMob) level.getEntity(g.boss.orElseThrow());
                p.snapTo(lord.getX(), lord.getY(), lord.getZ());
                lord.startSeenByPlayer(p);
                h.assertFalse(lord.bar().getPlayers().contains(p), "visible but unengaged Lord has no bar");
                Rooms.enter(level, p, g, bossFloor, arena, arena.centerX(), arena.centerZ());
                lord.startSeenByPlayer(p);
                h.assertTrue(lord.bar().getPlayers().contains(p), "engaging the actual Lord displays its bar");
            } finally { GateLiveTests.teardown(level, p, g); }
            h.succeed();
        });
    }
}
