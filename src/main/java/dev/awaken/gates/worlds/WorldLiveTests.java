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
                // a signature comes where there's room for a fight, here or next door, and where it can stand (38.13)
                for (Room r : f.rooms) {
                    if (r.spawnSpots.size() < 2 || r.type == Room.Type.START || r.type == Room.Type.BOSS) continue;
                    BlockPos from = Rooms.spots(level, g, f, r).get(0), at = WorldThemes.roomy(level, g, f, r, from);
                    h.assertTrue(WorldThemes.roomAt(level, at) >= WorldThemes.roomAt(level, WorldThemes.spot(level, g, f, r, from, 9)), "the roomiest spot near " + from + " (" + at + ")");
                    h.assertTrue(level.getBlockState(at).getCollisionShape(level, at).isEmpty() && level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty(),
                            "a signature stands at " + at);
                    break;
                }

                // a hung bell hums as you pass under it
                FloorLayout.Mark hung = null;
                for (FloorLayout.Mark m : f.marks) if (m.kind().equals(FloorLayout.Mark.BELL) && !m.id().equals(FloorLayout.Mark.HANDBELL)) hung = m;
                h.assertTrue(hung != null, "the floor has hung bells");
                BlockPos o = GateService.world(g, f, 0, 0, 0);
                p.teleportTo(level, o.getX() + hung.cx(), o.getY() + hung.y0() - 6, o.getZ() + hung.cz(), Set.of(), 0, 0, true);
                WorldLive.Live hl = WorldLive.live(g, f);
                WorldLive.hum(level, p, g, f, hl);
                h.assertTrue(!hl.hummed.isEmpty(), "a bell hums over you");

                // the hung bells near you hang as props and swing when they ring (SPEC §38.6)
                List<FloorLayout.Mark> props = f.marks(FloorLayout.Mark.PROP);
                h.assertTrue(!props.isEmpty() && props.size() <= 32, "the floor has swinging bells (" + props.size() + ")");
                FloorLayout.Mark pm = props.get(0);
                p.teleportTo(level, o.getX() + pm.cx(), o.getY() + pm.y0() - 4, o.getZ() + pm.cz(), Set.of(), 0, 0, true);
                WorldProps.near(level, p, g, f);
                WorldProps.Prop prop = WorldProps.of(g, f).get(0);
                h.assertTrue(prop.display != null && WorldProps.prop(prop.display), "a bell near you hangs as a prop");
                // the props run on the server's tick count (WorldLive.tick), not the level's clock: set it, then ring
                long t0 = level.getServer().getTickCount() / WorldProps.STEP * WorldProps.STEP + WorldProps.STEP;
                WorldProps.tick(level, t0);
                WorldProps.swing(g, f, pm, 1F);
                WorldProps.tick(level, t0 + WorldProps.STEP);
                WorldProps.tick(level, t0 + 2 * WorldProps.STEP);
                h.assertTrue(Math.abs(prop.display.getXRot()) > 1, "a rung bell swings (pitch " + prop.display.getXRot() + ")");

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

        AwakenTests.registerIsolated("gateworlds_touch", 400, h -> {
            // a cave world's rule (38.10) on a painted Glowing Deep floor: a puffball answers, the monsters near it choke on
            // its spores, too many bring the Spore Warden; a nook is found by standing in it, and the Mycelium Mind hears you
            ServerLevel level = h.getLevel();
            String deep = "awaken:fungal_hollow/glowing_deep";
            GatePlan plan = null;
            FloorLayout f = null;
            for (int i = 0; i < 12 && (f == null || !f.isWorld() || f.marks(FloorLayout.Mark.TOUCH).isEmpty() || f.marks(FloorLayout.Mark.NOOK).isEmpty()
                    || f.placeWorlds.isEmpty()); i++) {
                plan = GateWorldBellTests.dressed(GateWorldBellTests.plan(0xF00DL + i * 7919L, i * 4, dev.awaken.gates.content.Theme.FUNGAL_HOLLOW), deep, "quiet");
                f = GateLayout.of(plan).floor(0, 1);
            }
            h.assertTrue(f != null && f.isWorld() && !f.marks(FloorLayout.Mark.TOUCH).isEmpty() && !f.marks(FloorLayout.Mark.NOOK).isEmpty(),
                    "a Glowing Deep floor with puffballs and a nook");
            GateLiveTests.testLevel(level);
            ServerPlayer p = null;
            GateRecord g = null;
            try {
                g = GateLiveTests.standUp(h, plan, 0, 1);
                p = GateLiveTests.player(h);
                h.assertTrue(Runs.enter(p, g), "entered");
                FloorState fs = g.floor(0, 1);
                FloorLayout.Mark tm = f.marks(FloorLayout.Mark.TOUCH).get(0);
                BlockPos puff = GateService.world(g, f, tm.x0(), tm.y0(), tm.z0());
                h.assertTrue(level.getBlockState(puff).getBlock() instanceof dev.awaken.gates.block.ThemeBlocks.Touchstone, "a puffball stands at its mark");
                p.teleportTo(level, puff.getX() + 1.5, puff.getY(), puff.getZ() + 0.5, Set.of(), 0, 0, true);
                LivingEntity mob = Rooms.spawn(level, g, f, -1, MobRole.Role.NORMAL, puff.east(3));
                // struck as a player strikes it: the block answers the Hollow's rule and lights
                level.getBlockState(puff).attack(level, puff, p);
                h.assertTrue(level.getBlockState(puff).getValue(dev.awaken.gates.block.ThemeBlocks.Touchstone.LIT), "the struck puffball glows");
                if (mob != null) h.assertTrue(mob.hasEffect(MobEffects.SLOWNESS) && mob.hasEffect(MobEffects.WEAKNESS), "a monster near it chokes");
                for (int i = 1; i < WorldThemes.TOO_MANY; i++) {
                    WorldThemes.clear();
                    WorldThemes.touch(p, puff);
                }
                h.assertTrue(fs.world.contains("too_many"), "the Spore Warden comes at the " + WorldThemes.TOO_MANY + "th puff");

                // a nook: standing in it finds its secret
                FloorLayout.Mark nook = f.marks(FloorLayout.Mark.NOOK).get(0);
                BlockPos at = GateService.world(g, f, (nook.x0() + nook.x1()) / 2, nook.y0(), (nook.z0() + nook.z1()) / 2);
                p.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), 0, 0, true);
                Room in = null;
                for (Room r : f.rooms) if (r.containsSlot(((nook.x0() + nook.x1()) / 2) >> 4, ((nook.z0() + nook.z1()) / 2) >> 4)) in = r;
                if (in != null) WorldLive.places(p, g, f, in);
                WorldLive.nooks(p, g, f, in);
                String nookWorld = in == null ? f.world : f.worldOf(in);
                GateAtlas atlas = p.getData(GatesRegistry.ATLAS.get());
                h.assertTrue(atlas.entry(nookWorld) != null && atlas.entry(nookWorld).secrets.contains(nook.id()), "standing in the nook finds " + nook.id());

                // the Mycelium Mind: say something in it and it answers
                if (!f.placeWorlds.isEmpty()) {
                    int mindId = f.placeWorlds.keySet().iterator().next();
                    Room mind = f.room(mindId);
                    BlockPos c = GateService.world(g, f, mind.centerX(), 0, mind.centerZ());
                    p.teleportTo(level, c.getX() + 0.5, c.getY() + 1, c.getZ() + 0.5, Set.of(), 0, 0, true);
                    WorldThemes.chat(new net.neoforged.neoforge.event.ServerChatEvent(p, "hello", net.minecraft.network.chat.Component.literal("hello")));
                    GateAtlas.Entry e = p.getData(GatesRegistry.ATLAS.get()).entry(f.placeWorlds.get(mindId));
                    h.assertTrue(e != null && e.secrets.contains("the_thought"), "the Mind shares a thought when you speak in it");
                }
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
            for (var e : GateWorlds.all().entrySet()) {
                GateWorld.Dressing d = e.getValue().dressing();
                if (!d.sound().isEmpty() && !BuiltInRegistries.SOUND_EVENT.containsKey(net.minecraft.resources.Identifier.parse(d.sound())))
                    problems.add(e.getKey() + "'s bed " + d.sound() + " isn't registered");
                for (String k : d.life()) if (!GateWorld.LIFE.contains(k)) problems.add(e.getKey() + ": unknown life " + k);
                for (GateWorld.Mood m : e.getValue().moods())
                    for (String k : m.life()) if (!GateWorld.LIFE.contains(k)) problems.add(e.getKey() + "/" + m.id() + ": unknown life " + k);
            }
            h.assertTrue(count >= 19, "the Bell Crypt's sounds are registered (" + count + ")");
            h.assertTrue(problems.isEmpty(), problems.size() + " problems: " + String.join("; ", problems.subList(0, Math.min(8, problems.size()))));
            h.succeed();
        });

        AwakenTests.register("gateworlds_sets", 40, h -> {
            // the Bell Crypt's last keepsake gives its bell tower, which tolls the hours from dawn to dusk
            h.assertTrue(dev.awaken.gates.block.WorldBlocks.BellTower.tollsAt(0) == 6, "dawn (6 o'clock) is six tolls");
            h.assertTrue(dev.awaken.gates.block.WorldBlocks.BellTower.tollsAt(6000) == 12, "noon is twelve");
            h.assertTrue(dev.awaken.gates.block.WorldBlocks.BellTower.tollsAt(18000) == 0, "midnight is silent");
            ServerPlayer p = GateLiveTests.player(h);
            GateAtlas atlas = new GateAtlas();
            List<String> crypt = GateWorlds.ofTheme(dev.awaken.gates.content.Theme.BELL_CRYPT);
            for (String w : crypt) atlas.arrive(w, "still", 1);
            for (String w : crypt) if (!GateWorlds.get(w).dressing().keepsake().isEmpty()) atlas.entry(w).keepsake = true;
            String last = null;
            for (String w : crypt) if (!GateWorlds.get(w).dressing().keepsake().isEmpty()) last = w;
            h.assertTrue(last != null && crypt.size() == 5, "the Bell Crypt has five worlds with keepsakes");
            atlas.entry(last).keepsake = false;
            p.setData(GatesRegistry.ATLAS.get(), atlas);
            h.assertTrue(!WorldSets.complete(atlas, dev.awaken.gates.content.Theme.BELL_CRYPT), "four keepsakes aren't the set");
            p.getInventory().clearContent();
            WorldLive.keepsake(p, last);
            boolean tower = false;
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) tower |= p.getInventory().getItem(i).is(GatesRegistry.BELL_TOWER.get().asItem());
            h.assertTrue(tower, "the fifth keepsake brings the bell tower");
            // every other theme has its piece and five keepsakes (a rare world's keepsake belongs to no theme's set)
            for (var t : dev.awaken.gates.content.Theme.values()) {
                if (t == dev.awaken.gates.content.Theme.BELL_CRYPT) continue;
                h.assertTrue(WorldSets.of(t) != null && WorldSets.of(t).item().get() != null, t.id + " has its set piece");
                long keeps = GateWorlds.ofTheme(t).stream().filter(w -> !GateWorlds.get(w).dressing().keepsake().isEmpty()).count();
                h.assertTrue(keeps == 5, t.id + " has a keepsake in each of its five worlds (" + keeps + ")");
            }
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
