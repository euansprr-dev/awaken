package dev.awaken.gates.worlds;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.FloorState;
import dev.awaken.gates.GateLiveTests;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateService;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.run.Rooms;
import dev.awaken.gates.run.Runs;
import dev.awaken.gates.world.FloorAir;
import dev.awaken.gates.world.FloorLayout;
import dev.awaken.gates.world.GateLayout;
import dev.awaken.gates.world.GateWorldBellTests;
import dev.awaken.gates.world.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The live layer of a world floor (SPEC §38.8) on a painted Sunken Belfry floor with a player on it: the bell rule, the
 * Ringer, a secret place and its keepsake, the Flood; and the drop worlds' catch.
 */
public final class WorldLiveTests {
    private WorldLiveTests() {}

    static final String BELFRY = GateWorldBellTests.BELL + "sunken_belfry";

    public static void register() {
        AwakenTests.registerIsolated("gateworlds_live", 400, h -> {
            ServerLevel level = h.getLevel();
            GatePlan plan = null;
            FloorLayout f = null;
            for (int i = 0; i < 8 && (f == null || !f.isWorld() || f.marks(FloorLayout.Mark.BASIN).isEmpty() || f.placeWorlds.isEmpty()); i++) {
                plan = GateWorldBellTests.dressed(GateWorldBellTests.plan(0xB311L + i * 7919L, i * 4), BELFRY, "still");
                f = GateLayout.of(plan).floor(0, 1);
            }
            h.assertTrue(f != null && f.isWorld() && !f.placeWorlds.isEmpty(), "a Belfry floor with its Rope Shaft");
            GateLiveTests.testLevel(level);
            ServerPlayer p = null;
            GateRecord g = null;
            try {
                g = GateLiveTests.standUp(h, plan, 0, 1);
                p = GateLiveTests.player(h);
                h.assertTrue(Runs.enter(p, g), "entered");
                FloorState fs = g.floor(0, 1);

                // the rule: a handbell answers, the dead near it reel
                FloorLayout.Mark hb = null;
                for (FloorLayout.Mark m : f.marks) if (m.kind().equals(FloorLayout.Mark.BELL) && m.id().equals(FloorLayout.Mark.HANDBELL)) hb = m;
                h.assertTrue(hb != null, "the floor has handbells");
                BlockPos bell = GateService.world(g, f, hb.x0(), hb.y0(), hb.z0());
                h.assertTrue(level.getBlockState(bell).is(GatesRegistry.CRYPT_HANDBELL.get()), "a handbell stands at its mark");
                p.teleportTo(level, bell.getX() + 1.5, bell.getY(), bell.getZ() + 0.5, Set.of(), 0, 0, true);
                LivingEntity dead = Rooms.spawn(level, g, f, -1, MobRole.Role.NORMAL, bell.east(3));
                h.assertTrue(WorldRules.strike(p, bell), "striking it answers the Belfry's rule");
                if (dead != null) h.assertTrue(dead.hasEffect(MobEffects.SLOWNESS), "the dead near it reel");
                // a world's monsters wear its residents' look, in a synced attachment a renderer can read
                if (dead != null) {
                    WorldLook look = dead.getData(GatesRegistry.WORLD_LOOK.get());
                    h.assertTrue(look.world().equals(BELFRY) && look.look().equals("awaken:drowned_vestments"),
                            "a Belfry monster wears the drowned vestments (got " + look + ")");
                }
                // ring too many and the Ringer comes (once a floor)
                for (int i = 1; i < WorldLive.RINGER_AT; i++) {
                    WorldRules.clear();
                    WorldRules.strike(p, bell);
                }
                h.assertTrue(fs.world.contains(WorldLive.RINGER), "the Ringer comes at the " + WorldLive.RINGER_AT + "th bell");

                // a hung bell hums as you pass under it
                FloorLayout.Mark hung = null;
                for (FloorLayout.Mark m : f.marks) if (m.kind().equals(FloorLayout.Mark.BELL) && !m.id().equals(FloorLayout.Mark.HANDBELL)) hung = m;
                h.assertTrue(hung != null, "the floor has hung bells");
                BlockPos o = GateService.world(g, f, 0, 0, 0);
                p.teleportTo(level, o.getX() + hung.cx(), o.getY() + hung.y0() - 6, o.getZ() + hung.cz(), Set.of(), 0, 0, true);
                WorldLive.Live hl = WorldLive.live(g, f);
                WorldLive.hum(level, p, g, f, hl);
                h.assertTrue(!hl.hummed.isEmpty(), "a bell hums over you");

                // the odd rope: coming into the Bellmaker's Rest finds the secret and its keepsake
                int restId = f.placeWorlds.keySet().iterator().next();
                Room rest = f.room(restId);
                WorldLive.places(p, g, f, rest);
                GateAtlas atlas = p.getData(GatesRegistry.ATLAS.get());
                String restWorld = f.placeWorlds.get(restId);
                h.assertTrue(atlas.entry(restWorld) != null, "the Rest is found as a world of its own");
                h.assertTrue(atlas.entry(BELFRY).secrets.contains("odd_rope"), "the odd rope is found by coming");
                h.assertTrue(atlas.entry(restWorld).keepsake, "the Rest's keepsake is yours");
                boolean trophy = false;
                for (int i = 0; i < p.getInventory().getContainerSize(); i++) trophy |= !p.getInventory().getItem(i).isEmpty();
                h.assertTrue(trophy, "the keepsake is in your pack");

                // the Flood: the first key taken, the water rises from low tide to the brim and the lilies with it
                WorldLive.progress(level, g, f);
                h.assertTrue(fs.world.contains(WorldLive.FLOOD), "the Flood starts");
                WorldLive.Live live = WorldLive.LIVE.get(WorldLive.Key.of(g, f));
                h.assertTrue(live != null && live.flood != null, "the Flood rises");
                for (int i = 0; i < 4000 && live.flood != null; i++) {
                    live.flood.resume = Long.MIN_VALUE;
                    if (live.flood.step(level)) live.flood = null;
                }
                h.assertTrue(live.flood == null && fs.world.contains(WorldLive.FLOOD_DONE), "the Flood finishes");
                FloorLayout.Mark basin = f.marks(FloorLayout.Mark.BASIN).get(0);
                int water = 0, cells = 0;
                for (int x = basin.x0(); x <= basin.x1(); x++) {
                    for (int z = basin.z0(); z <= basin.z1(); z += 3) {
                        cells++;
                        if (level.getBlockState(GateService.world(g, f, x, basin.y1(), z)).getBlock() == Blocks.WATER
                                || !level.getBlockState(GateService.world(g, f, x, basin.y1(), z)).isAir()) water++;
                    }
                }
                h.assertTrue(water == cells, "the water stands at the brim: " + water + " of " + cells);
                boolean raised = false;
                for (FloorAir.Water wt : WorldLive.air(g, f).waters()) raised |= wt.y() == basin.y1();
                h.assertTrue(raised, "Gate Air's water rises with it");
            } finally {
                WorldLive.clear();
                GateLiveTests.teardown(level, p, g);
            }
            h.succeed();
        });

        AwakenTests.register("gateworlds_sounds", 20, h -> {
            // every gate.world sound is in sounds.json with its files there, every world's bed is one, its life is known
            List<String> problems = new ArrayList<>();
            JsonObject sounds = null;
            try (InputStream in = WorldLiveTests.class.getResourceAsStream("/assets/awaken/sounds.json")) {
                sounds = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (IOException | RuntimeException e) {
                problems.add("sounds.json unreadable: " + e);
            }
            int count = 0;
            for (var id : BuiltInRegistries.SOUND_EVENT.keySet()) {
                if (!id.getNamespace().equals("awaken") || !id.getPath().startsWith("gate.world.") || sounds == null) continue;
                count++;
                if (!sounds.has(id.getPath())) {
                    problems.add(id.getPath() + " has no sounds.json entry");
                    continue;
                }
                for (var e : sounds.getAsJsonObject(id.getPath()).getAsJsonArray("sounds")) {
                    String file = e.isJsonObject() ? e.getAsJsonObject().get("name").getAsString() : e.getAsString();
                    if (file.startsWith("awaken:") && WorldLiveTests.class.getResource("/assets/awaken/sounds/" + file.substring(7) + ".ogg") == null)
                        problems.add(id.getPath() + ": missing " + file);
                }
            }
            Set<String> life = Set.of("moths", "bats", "pale_fish", "rats", "dust", "drips");
            for (var e : GateWorlds.all().entrySet()) {
                GateWorld.Dressing d = e.getValue().dressing();
                if (!d.sound().isEmpty() && !BuiltInRegistries.SOUND_EVENT.containsKey(net.minecraft.resources.Identifier.parse(d.sound())))
                    problems.add(e.getKey() + "'s bed " + d.sound() + " isn't registered");
                for (String k : d.life()) if (!life.contains(k)) problems.add(e.getKey() + ": unknown life " + k);
            }
            h.assertTrue(count >= 19, "the Bell Crypt's sounds are registered (" + count + ")");
            h.assertTrue(problems.isEmpty(), problems.size() + " problems: " + String.join("; ", problems.subList(0, Math.min(8, problems.size()))));
            h.succeed();
        });

        AwakenTests.register("gateworlds_catch", 100, h -> {
            // a fall on a drop world puts you back on the last ledge you stood on, a little the worse for it
            ServerLevel level = h.getLevel();
            ServerPlayer p = GateLiveTests.player(h);
            p.setGameMode(GameType.SURVIVAL); // a mock player is creative, and creative takes no fall damage
            p.connection.markClientLoaded(); // nor can a player be hurt before its client has loaded
            BlockPos ledge = h.absolutePos(new BlockPos(1, 2, 1));
            WorldLive.Ledge l = new WorldLive.Ledge();
            l.drop = true;
            l.floor = new WorldLive.Key(-1, 0, 1);
            l.pos = Vec3.atBottomCenterOf(ledge);
            WorldLive.LEDGES.put(p.getUUID(), l);
            try {
                p.teleportTo(level, l.pos.x, l.pos.y - 3, l.pos.z, Set.of(), 0, 0, true);
                p.setOnGround(false);
                p.fallDistance = 4;
                WorldLive.ledge(level, p);
                h.assertTrue(p.getY() < l.pos.y - 1, "a short drop isn't caught");
                p.teleportTo(level, l.pos.x, l.pos.y - 20, l.pos.z, Set.of(), 0, 0, true);
                p.setOnGround(false);
                p.fallDistance = 14;
                float hp = p.getHealth();
                WorldLive.ledge(level, p);
                h.assertTrue(Math.abs(p.getY() - l.pos.y) < 0.01 && p.fallDistance == 0, "a long fall is caught on the ledge, at y " + p.getY());
                h.assertTrue(p.getHealth() < hp, "with a little damage");
            } finally {
                WorldLive.LEDGES.remove(p.getUUID());
            }
            h.succeed();
        });
    }
}
