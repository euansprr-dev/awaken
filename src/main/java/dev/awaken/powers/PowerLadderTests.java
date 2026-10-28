package dev.awaken.powers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.powers.fx.CastProfiles;
import dev.awaken.powers.ladder.PowerLadder;
import dev.awaken.powers.ladder.Rider;
import dev.awaken.powers.ladder.Riders;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** GameTests for the power ladders (SPEC 36.9): the table, every level casting, each shared rider, saves, the moment, earning. */
final class PowerLadderTests {
    private PowerLadderTests() {}

    static void register() {
        // Every power has four steps (levels 2-5) with words, level 5 a body line; riders parse; a level row of a cast profile
        // keeps its timing; the steps' cooldown scale stays a cut, never a wait.
        AwakenTests.register("power_ladder_complete", 20, h -> {
            JsonObject lang;
            try (InputStream in = PowerLadderTests.class.getResourceAsStream("/assets/awaken/lang/en_us.json")) {
                h.assertTrue(in != null, "missing the merged lang file");
                lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (java.io.IOException ex) {
                throw new IllegalStateException(ex);
            }
            List<String> bad = new ArrayList<>();
            for (PowerType t : PowerRegistry.all()) {
                String id = t.path;
                if (PowerLadder.entry(id) == null) {
                    bad.add(id + ": no ladder");
                    continue;
                }
                for (int lv = 2; lv <= PowerLadder.TOP; lv++) {
                    PowerLadder.Step s = PowerLadder.step(id, lv);
                    if (s == null) {
                        bad.add(id + " " + lv + ": no step");
                        continue;
                    }
                    for (String part : lv == PowerLadder.TOP ? List.of("name", "look", "does", "body") : List.of("name", "look", "does")) {
                        String key = "awaken.ladder." + id + "." + lv + "." + part;
                        if (!lang.has(key) || lang.get(key).getAsString().isBlank()) bad.add(key + " missing");
                        else if (lang.get(key).getAsString().contains("{")) bad.add(key + " has an unfilled number");
                    }
                    for (Rider r : s.riders()) if (r.kind() == null) bad.add(id + " " + lv + ": a rider of no known kind");
                    for (JsonObject look : s.looks()) if (!look.has("k") || !look.has("on")) bad.add(id + " " + lv + ": a look without its type or events");
                }
                float cd = PowerLadder.cdScale(id, PowerLadder.TOP);
                if (cd <= 0 || cd > 1) bad.add(id + ": cooldown scale " + cd);
                CastProfiles.Profile p = CastProfiles.byPower(id);
                if (p != null) {
                    CastProfiles.Profile five = CastProfiles.at(p, 5), three = CastProfiles.at(p, 4);
                    if (five.release() != p.release() || three.release() != p.release()) bad.add(id + ": a level row moved the release");
                    if (five.begin3p().equals(p.begin3p())) bad.add(id + ": level 5 has no gesture of its own");
                }
            }
            h.assertTrue(bad.isEmpty(), "ladder table: " + bad);
            h.succeed();
        });

        // Every active power casts at levels 1, 3 and 5 without throwing, and one that casts at level 1 still casts at 5.
        AwakenTests.register("power_ladder_every_level_casts", 400, h -> {
            ServerPlayer sp = player(h, new BlockPos(-3, 1, 0));
            Vec3 home = sp.position();
            List<String> broke = new ArrayList<>(), lost = new ArrayList<>();
            int casts = 0;
            for (PowerType t : PowerRegistry.all()) {
                if (t.isPassive()) continue;
                boolean okAt1 = false;
                for (int lv : new int[]{1, 3, 5}) {
                    floor(h, 7);
                    // the last level's roof or rampart still stands above the floor (constructs live on)
                    AwakenTests.clearAbove(h.getLevel(), h.absolutePos(BlockPos.ZERO), 8, 4, 12);
                    sp.snapTo(home.x, home.y, home.z, -90f, 0f);
                    sp.setDeltaMovement(Vec3.ZERO);
                    Powers.setLevel(sp, t, lv, 0);
                    Powers.resetCooldowns(sp);
                    Powers.fillEnergy(sp);
                    Zombie z = stillZombie(h, new BlockPos(0, 1, 0));
                    try {
                        PowerType.Result r = Casting.cast(sp, t, 0, false, -90f, 0f);
                        if (r.ok()) casts++;
                        if (lv == 1) okAt1 = r.ok();
                        if (lv == 5 && okAt1 && !r.ok()) lost.add(t.path + " (" + r.reason() + ")");
                        // a channel holds a moment so its ticks run, then lets go
                        if (r.ok() && t.mode == PowerType.Mode.CHANNEL) for (int i = 0; i < 6; i++) Casting.tickPlayer(sp);
                    } catch (RuntimeException ex) {
                        broke.add(t.path + "@" + lv + ": " + ex);
                    }
                    Casting.endAll(sp, Running.Why.CANCELLED);
                    z.discard();
                    for (var e : h.getLevel().getEntitiesOfClass(dev.awaken.powers.content.PowerSummon.class, sp.getBoundingBox().inflate(48),
                            e -> e.getOwner() == sp)) e.discard();
                }
            }
            h.assertTrue(broke.isEmpty(), "casts threw: " + broke);
            h.assertTrue(lost.isEmpty(), "cast at level 1 but not at level 5: " + lost);
            h.assertTrue(casts >= 120, "most powers cast at every level (" + casts + ")");
            h.succeed();
        });

        // One per shared rider mechanism (SPEC 36.3), on the step that carries it: a hit arcs on, lands again, pulses round,
        // leaves a burning patch, stuns and lifts; a guard cuts damage after the cast; an extend reaches further.
        rider("power_ladder_rider_chain", "thunderclap", 3, (h, c, a, b) -> {
            float hp = b.getHealth();
            Riders.onHit(c, a, 10f, null);
            h.assertTrue(b.getHealth() < hp, "Thunderclap 3's chain arcs to the next foe");
            h.succeed();
        });
        rider("power_ladder_rider_echo", "haymaker", 5, (h, c, a, b) -> {
            Riders.onHit(c, a, 10f, null);
            float hp = a.getHealth();
            h.runAfterDelay(10, () -> {
                h.assertTrue(a.getHealth() < hp, "Haymaker 5's ghost fist lands again on the same foe");
                h.succeed();
            });
        });
        rider("power_ladder_rider_aftershock", "ground_slam", 3, (h, c, a, b) -> {
            float hp = b.getHealth();
            Riders.onHit(c, a, 10f, null);
            h.assertTrue(b.getHealth() == hp, "the aftershock waits its beat");
            h.runAfterDelay(14, () -> {
                h.assertTrue(b.getHealth() < hp, "Ground Slam 3's aftershock hits round the first hit");
                h.succeed();
            });
        });
        rider("power_ladder_rider_linger", "beam", 4, (h, c, a, b) -> {
            Riders.onHit(c, a, 10f, null);
            float hp = a.getHealth();
            h.runAfterDelay(25, () -> {
                h.assertTrue(a.getHealth() < hp, "Beam 4's burning patch keeps hurting the foe standing in it");
                h.succeed();
            });
        });
        rider("power_ladder_rider_status_push", "shockwave", 4, (h, c, a, b) -> {
            Riders.onHit(c, a, 10f, null);
            h.assertTrue(a.hasEffect(dev.awaken.powers.content.PowerContent.STUNNED), "Shockwave 4 stuns the foe hit");
            h.assertTrue(a.getDeltaMovement().y > 0.2, "and throws it up: " + a.getDeltaMovement());
            h.succeed();
        });
        AwakenTests.register("power_ladder_rider_guard_extend", 20, h -> {
            floor(h, 3);
            ServerPlayer sp = player(h, new BlockPos(0, 1, 0));
            PowerType mend = PowerRegistry.get("mend");
            Powers.setLevel(sp, mend, 3, 0);
            Powers.fillEnergy(sp);
            h.assertTrue(Casting.cast(sp, mend, 0, false, 0f, 0f).ok(), "mend casts");
            long now = sp.level().getServer().overworld().getGameTime();
            h.assertTrue(Math.abs(PowerState.of(sp).buff(PowerState.Buffs.GUARD, now) - 0.2) < 1e-4, "Mend 3 guards you for a while after");
            PowerType sw = PowerRegistry.get("shockwave");
            double r1 = new Cast(sp, sw, 0, 1, 0, false, 0f, 0f).range(10), r2 = new Cast(sp, sw, 0, 2, 0, false, 0f, 0f).range(10);
            h.assertTrue(Math.abs(r2 / r1 - 1.15) < 1e-3, "Shockwave 2 reaches 15% further: " + r1 + " -> " + r2);
            h.succeed();
        });

        // SPEC §13 "Dash direction", at every level of the ladder: the dash goes the way you move and never gets shorter.
        AwakenTests.register("power_ladder_dash_every_level", 60, h -> {
            floor(h, 12);
            ServerPlayer sp = player(h, new BlockPos(0, 1, 0));
            Vec3 home = sp.position();
            h.runAfterDelay(3, () -> {
                PowerType dash = PowerRegistry.get("dash");
                double last = 0;
                for (int lv = 1; lv <= PowerLadder.TOP; lv++) {
                    Powers.setLevel(sp, dash, lv, 0);
                    for (float[] k : new float[][]{{0, 1}, {1, 0}, {-1, -1}}) {
                        sp.snapTo(home.x, home.y, home.z, 0f, 0f);
                        Powers.fillEnergy(sp);
                        Powers.resetCooldowns(sp);
                        sp.setDeltaMovement(Vec3.ZERO);
                        PowerType.Result r = Casting.cast(sp, dash, 0, false, 0f, 0f, k[0], k[1]);
                        h.assertTrue(r.ok(), "the dash casts at level " + lv + ": " + r.reason());
                        Vec3 v = sp.getDeltaMovement();
                        double hl = Math.sqrt(v.x * v.x + v.z * v.z), el = Math.sqrt(k[0] * k[0] + k[1] * k[1]);
                        h.assertTrue((v.x * k[0] + v.z * k[1]) / (hl * el) > 0.99, "level " + lv + " keys " + k[0] + "," + k[1] + " went " + v);
                        if (k[1] == 1) {
                            h.assertTrue(hl >= last - 1e-6, "level " + lv + " dashes no shorter than level " + (lv - 1) + ": " + hl + " < " + last);
                            last = hl;
                        }
                    }
                    Casting.endAll(sp, Running.Why.CANCELLED);
                }
                h.succeed();
            });
        });

        // A schema-2 save loads with every power's moment already seen and one window pending about ladders; schema 3 keeps seen.
        AwakenTests.register("power_ladder_save_migrates", 20, h -> {
            Loadout l = new Loadout();
            l.owned.put("awaken:beam", new Loadout.Owned("awaken:beam", 0, 3));
            l.owned.put("awaken:dash", new Loadout.Owned("awaken:dash", 0, 1));
            var tag = (CompoundTag) Loadout.CODEC.encodeStart(NbtOps.INSTANCE, l).getOrThrow();
            tag.remove("seen");
            tag.putInt("schema", 2);
            Loadout old = Loadout.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
            h.assertTrue(old.schema() == Loadout.SCHEMA && old.seen("awaken:beam") == 3 && old.seen("awaken:dash") == 1, "levels you had count as seen");
            h.assertTrue(Powers.ladderIntroPending(old), "and one window explains the ladders");
            Powers.markSeen(old, "awaken:dash", 2);
            Powers.clearLadderIntro(old);
            var again = (CompoundTag) Loadout.CODEC.encodeStart(NbtOps.INSTANCE, old).getOrThrow();
            Loadout q = Loadout.CODEC.parse(NbtOps.INSTANCE, again).getOrThrow();
            h.assertTrue(q.seen("awaken:dash") == 2 && !Powers.ladderIntroPending(q), "schema 3 keeps what was seen and asks nothing");
            h.assertTrue(!Powers.ladderIntroPending(new Loadout()), "a new player gets no such window");
            h.succeed();
        });

        // A level gained out of a fight plays its moment once; one gained in a fight waits (a sting), then plays once when calm.
        AwakenTests.register("power_ladder_moment_once", Mastery.FIGHT_WINDOW + 60, h -> {
            floor(h, 3);
            ServerPlayer sp = player(h, new BlockPos(0, 1, 0));
            PowerType beam = PowerRegistry.get("beam");
            String id = beam.id.toString();
            Powers.setLevel(sp, beam, 1, 0);
            Loadout l = Powers.loadout(sp);
            h.assertTrue(Mastery.add(sp, beam, Mastery.need(1)) == 1 && l.seen(id) == 2, "a calm level-up shows its moment");
            PowerState.of(sp).markCombat(sp);
            h.assertTrue(Mastery.add(sp, beam, Mastery.need(2)) == 1 && l.get(id).level() == 3 && l.seen(id) == 2, "in a fight it waits");
            h.runAfterDelay(Mastery.FIGHT_WINDOW + 25, () -> {
                long t = sp.level().getServer().overworld().getGameTime();
                long at = t + Math.floorMod(7 - t, 20); // Evolution looks once a second, on tick 7 of it
                dev.awaken.powers.ladder.Evolution.tick(sp, l, at);
                h.assertTrue(l.seen(id) == 3, "and plays once the fight is over");
                dev.awaken.powers.ladder.Evolution.tick(sp, l, at + 20);
                h.assertTrue(l.seen(id) == 3, "only once");
                h.succeed();
            });
        });

        // Wall Stride, Water Run and Danger Sense earn from use, at most USE_CAP a minute each; a power you don't own earns nothing.
        AwakenTests.register("power_ladder_earning", 20, h -> {
            ServerPlayer sp = player(h, new BlockPos(0, 1, 0));
            PowerType stride = PowerRegistry.get("wall_run");
            String id = stride.id.toString();
            h.assertTrue(Mastery.use(sp, id, Mastery.WALL_SECOND) == 0, "not owned: nothing");
            Powers.setLevel(sp, stride, 1, 0);
            Loadout l = Powers.loadout(sp);
            h.assertTrue(Mastery.use(sp, id, Mastery.WALL_SECOND) == Mastery.WALL_SECOND && l.mastery(id) == Mastery.WALL_SECOND, "a second on a wall pays");
            float paid = 0;
            for (int i = 0; i < 100; i++) paid += Mastery.use(sp, id, Mastery.WALL_SECOND);
            h.assertTrue(Math.abs(paid + Mastery.WALL_SECOND - Mastery.USE_CAP) < 1e-3, "a minute's use pays at most the cap: " + (paid + Mastery.WALL_SECOND));
            PowerType sense = PowerRegistry.get("danger_sense");
            Powers.setLevel(sp, sense, 1, 0);
            h.assertTrue(Mastery.use(sp, sense.id.toString(), Mastery.DODGED) == Mastery.DODGED, "each power has its own cap");
            h.succeed();
        });

        // A second press (SPEC 36.3): Shadow Step 4 steps you back to the pool, free, and only then starts its cooldown.
        AwakenTests.register("power_ladder_follow_up", 40, h -> {
            floor(h, 10);
            ServerPlayer sp = player(h, new BlockPos(0, 1, -6));
            sp.setGameMode(GameType.SURVIVAL);
            h.runAfterDelay(3, () -> {
                PowerType step = PowerRegistry.get("shadow_step");
                Powers.setLevel(sp, step, 4, 0);
                Powers.fillEnergy(sp);
                Vec3 from = sp.position();
                h.assertTrue(Casting.cast(sp, step, 0, false, 0f, 0f).ok(), "shadow step casts");
                h.assertTrue(sp.position().distanceTo(from) > 2, "and moves you: " + sp.position());
                Loadout l = Powers.loadout(sp);
                float energy = l.energy();
                h.assertTrue(Casting.cast(sp, step, 0, false, 0f, 0f).ok(), "the second press goes off");
                h.assertTrue(sp.position().distanceTo(from) < 1.5, "and steps you back to the pool: " + sp.position() + " vs " + from);
                h.assertTrue(Math.abs(l.energy() - energy) < 1e-3, "for free");
                long now = sp.level().getServer().overworld().getGameTime();
                h.assertTrue(l.cooldownLeft(step.id.toString(), now) > 0, "and the cooldown starts now");
                h.succeed();
            });
        });
    }

    interface RiderTest {
        void run(GameTestHelper h, Cast c, Zombie hit, Zombie other);
    }

    /** A rider test: a cast of {@code power} at {@code level} (not run: its kit only), a foe hit and one beside it. */
    private static void rider(String name, String power, int level, RiderTest test) {
        AwakenTests.register(name, 40, h -> {
            floor(h, 6);
            ServerPlayer sp = player(h, new BlockPos(-3, 1, 0));
            PowerType t = PowerRegistry.get(power);
            Powers.setLevel(sp, t, level, 0);
            Zombie a = helmeted(h, new BlockPos(0, 1, 0)), b = helmeted(h, new BlockPos(1, 1, 2));
            h.runAfterDelay(2, () -> test.run(h, new Cast(sp, t, 0, level, 0, false, -90f, 0f), a, b));
        });
    }

    private static ServerPlayer player(GameTestHelper h, BlockPos rel) {
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        Vec3 at = h.absoluteVec(Vec3.atBottomCenterOf(rel));
        sp.snapTo(at.x, at.y, at.z, 0f, 0f);
        return sp;
    }

    private static void floor(GameTestHelper h, int r) {
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
            }
        }
    }

    /** A still zombie the sun can't burn that fire can still hurt (a burning patch's test). */
    private static Zombie helmeted(GameTestHelper h, BlockPos pos) {
        Zombie z = h.spawn(EntityTypes.ZOMBIE, pos);
        z.setNoAi(true);
        z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET));
        return z;
    }

    private static Zombie stillZombie(GameTestHelper h, BlockPos pos) {
        Zombie z = h.spawn(EntityTypes.ZOMBIE, pos);
        z.setNoAi(true);
        z.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 20 * 60, 0, false, false));
        return z;
    }
}
