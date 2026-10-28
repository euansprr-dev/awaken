package dev.awaken.powers;

import dev.awaken.core.Forces;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.powers.content.ConstructBlock;
import dev.awaken.powers.content.PowerContent;
import dev.awaken.progression.Counters;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.progression.RewardHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** GameTests for the powers module: maths, catalog integrity, learning, casting, constructs, terrain, the Five Forces. */
final class PowersTests {
    private PowersTests() {}

    static void register() {
        AwakenTests.register("powers_energy_math", 20, h -> {
            h.assertTrue(PowerMath.maxEnergy(5) == 125f, "pool at INT 5 should be 125, was " + PowerMath.maxEnergy(5));
            h.assertTrue(PowerMath.maxEnergy(40) == 300f, "pool at INT 40 should be 300");
            h.assertTrue(Math.abs(PowerMath.regenPerSecond(5, 0) - 4.4f) < 1e-4, "regen at INT 5 should be 4.4/s");
            h.assertTrue(Math.abs(PowerMath.regenPerSecond(50, 0) - 8.0f) < 1e-4, "regen at INT 50 should be 8/s");
            h.assertTrue(Math.abs(PowerMath.regenPerSecond(5, 0.5) - 6.6f) < 1e-4, "energy_regen perk multiplies");
            Loadout l = new Loadout();
            l.energy = 50;
            l.maxEnergy = 125;
            l.regenPerTick = 0.22f;
            l.syncTime = 100;
            l.pauseUntil = 120;
            h.assertTrue(l.energyAt(110) == 50f, "regen is paused for a second after spending");
            h.assertTrue(Math.abs(l.energyAt(220) - 72f) < 1e-3, "regen resumes after the pause, was " + l.energyAt(220));
            h.assertTrue(l.energyAt(100_000) == 125f, "energy caps at the pool");
            h.succeed();
        });

        AwakenTests.register("powers_icons_present", 20, h -> {
            for (PowerType t : PowerRegistry.all()) {
                h.assertTrue(PowersTests.class.getResource("/assets/awaken/textures/gui/sprites/power/" + t.path + ".png") != null,
                        t.path + " has no icon (tools/art/powerglyphs.py)");
                h.assertTrue(PowersTests.class.getResource("/assets/awaken/models/item/power_core/" + t.path + ".json") != null,
                        t.path + " has no Power Core crystal model");
            }
            h.succeed();
        });

        AwakenTests.register("powers_cooldown_math", 20, h -> {
            h.assertTrue(PowerMath.cooldown(100, 1f, 1, 0) == 100, "base cooldown");
            h.assertTrue(PowerMath.cooldown(100, 1f, 1, 0.2) == 80, "20% cooldown reduction");
            h.assertTrue(PowerMath.cooldown(100, 1f, 1, 0.9) == 50, "cooldown reduction caps at 50%");
            h.assertTrue(PowerMath.cooldown(100, 0.5f, 1, 0) == 50, "variant multiplier");
            h.assertTrue(PowerMath.cooldown(100, 1f, 5, 0) == 84, "power level 5 shortens by 16%");
            float d1 = PowerMath.damage(10, 1, 5, 1, 1f);
            float d35 = PowerMath.damage(10, 35, 5, 1, 1f);
            h.assertTrue(Math.abs(d35 / d1 - (1 + 0.075 * 35) / 1.075) < 1e-4, "damage scales with level like mob health");
            h.assertTrue(PowerMath.capBossHit(500, 1000) == 200, "boss hits cap at 20% of max health");
            h.assertTrue(PowerMath.flowTicks(5, 0, 1f) == 85 && PowerMath.flowTicks(25, 0.3, 1f) == 137, "flow length");
            h.succeed();
        });

        AwakenTests.register("powers_catalog_complete", 40, h -> {
            var all = PowerRegistry.all();
            h.assertTrue(all.size() >= 50, "at least 50 powers, have " + all.size());
            for (PowerType t : all) {
                if (t.isPassive()) h.assertTrue(t.passive != null, t + " is passive but has no passive behaviour");
                else h.assertTrue(t.behaviour != null, t + " has no behaviour");
                h.assertTrue(!t.description.isEmpty(), t + " has no description");
                h.assertTrue(t.variants.get(0).name().equals("Standard"), t + " variant 0 must be Standard");
            }
            // every power named by content (path signatures, latents) exists and is hidden
            for (String id : List.of("thunderclap", "lightning_step", "prism_lance", "legion", "foresight", "citadel", "drone_swarm",
                    "bulwark", "hundred_palms", "void_maw", "waking_dream", "storm_call", "ember_step", "frost_nova", "ghost_step",
                    "twin_strike", "gravity_flip")) {
                PowerType t = PowerRegistry.get(id);
                h.assertTrue(t != null, "missing power " + id);
                h.assertTrue(t.hidden, id + " must be hidden (never rolled from cores)");
            }
            RandomSource rnd = RandomSource.create(42);
            for (int i = 0; i < 2000; i++) {
                PowerType t = PowerRegistry.roll(Rank.SOVEREIGN, rnd);
                h.assertTrue(t != null && !t.hidden, "rolled a hidden power " + t);
            }
            for (int i = 0; i < 500; i++) {
                PowerType t = PowerRegistry.roll(Rank.E, rnd);
                h.assertTrue(t != null && t.unlock == Rank.E, "an E-rank core rolled " + t + " (" + (t == null ? "" : t.unlock) + ")");
            }
            h.succeed();
        });

        AwakenTests.register("powers_loadout_codec_roundtrip", 20, h -> {
            Loadout l = new Loadout();
            l.owned.put("awaken:beam", new Loadout.Owned("awaken:beam", 1, 3));
            l.owned.put("awaken:dash", new Loadout.Owned("awaken:dash", 0, 1));
            l.slots[0] = "awaken:beam";
            l.slots[Loadout.MOVEMENT] = "awaken:dash";
            l.cooldowns.put("awaken:beam", new Loadout.Cooldown(1234L, 60));
            l.energy = 77.5f;
            var tag = Loadout.CODEC.encodeStart(NbtOps.INSTANCE, l).getOrThrow();
            Loadout q = Loadout.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
            h.assertTrue(q.get("awaken:beam").variant() == 1 && q.get("awaken:beam").level() == 3, "owned power");
            h.assertTrue(q.slot(0).equals("awaken:beam") && q.slot(Loadout.MOVEMENT).equals("awaken:dash"), "slots");
            h.assertTrue(q.cooldown("awaken:beam").end() == 1234L && q.energy() == 77.5f, "cooldowns and energy");
            h.succeed();
        });

        AwakenTests.register("powers_learn_via_core", 40, h -> {
            ServerPlayer sp = player(h, new BlockPos(0, 1, 0));
            PowerType beam = PowerRegistry.get("beam");
            long before = Progression.profile(sp).lastNewThing();
            var core = PowerContent.core(beam, 1);
            h.assertTrue(core.get(PowerContent.CORE_DATA.get()).power().equals("awaken:beam"), "core carries its power");
            h.assertTrue(Powers.useCore(sp, beam, 1), "using a core learns it");
            Loadout l = Powers.loadout(sp);
            h.assertTrue(l.get("awaken:beam") != null && l.get("awaken:beam").variant() == 1, "learned with its variant");
            h.assertTrue(l.slotOf("awaken:beam") >= 0 && l.slotOf("awaken:beam") < 4, "auto-equipped on an active key");
            h.assertTrue(Progression.profile(sp).lastNewThing() >= before && Progression.profile(sp).lastNewThing() > 0, "counts as a new thing");
            h.assertTrue(Powers.useCore(sp, beam, 0), "a second core levels up");
            h.assertTrue(l.get("awaken:beam").level() == 2 && l.get("awaken:beam").variant() == 1, "level 2, same variant");
            RewardHooks.learnPower(sp, "awaken:ember_step");
            h.assertTrue(l.owns("awaken:ember_step") && l.slot(Loadout.MOVEMENT).equals("awaken:ember_step"), "reward hook learns latents");
            var stack = dev.awaken.core.api.PowerCoreHooks.roll(Rank.C, RandomSource.create(7));
            h.assertTrue(stack.is(PowerContent.POWER_CORE.get()), "the core roller is installed");
            h.assertTrue(dev.awaken.core.api.PowerCoreHooks.forPower("awaken:legion", 0).is(PowerContent.POWER_CORE.get()), "forPower works for signatures");
            h.succeed();
        });

        AwakenTests.register("powers_cast_ground_slam", 60, h -> {
            floor(h, 3);
            ServerPlayer sp = player(h, new BlockPos(0, 1, 0));
            Zombie z = stillZombie(h, new BlockPos(2, 1, 0));
            h.runAfterDelay(3, () -> { // act once the forced test chunks tick
                float hp = z.getHealth();
                PowerType slam = PowerRegistry.get("ground_slam");
                Powers.learn(sp, slam.id, 0);
                Powers.fillEnergy(sp);
                float energy = Powers.loadout(sp).energy();
                PowerType.Result r = Casting.cast(sp, slam, 0, false, sp.getYRot(), sp.getXRot());
                h.assertTrue(r.ok(), "ground slam should cast: " + r.reason());
                h.assertTrue(z.getHealth() < hp, "the zombie took damage (" + hp + " -> " + z.getHealth() + ")");
                Loadout l = Powers.loadout(sp);
                h.assertTrue(l.cooldownLeft("awaken:ground_slam", Casting.now(sp)) > 0, "cooldown started");
                h.assertTrue(l.energy() < energy, "energy spent");
                h.assertTrue(Progression.profile(sp).counter("awaken:power_uses/kinetic") == 1, "kinetic use counted");
                PowerType.Result again = Casting.cast(sp, slam, 0, false, sp.getYRot(), sp.getXRot());
                h.assertTrue(!again.ok() && again.reason().equals("cooldown"), "cooldown blocks a recast");
                h.succeed();
            });
        });

        AwakenTests.register("powers_cast_spirit_palm_aimed", 60, h -> {
            floor(h, 3);
            ServerPlayer sp = player(h, new BlockPos(0, 1, 0));
            Zombie z = stillZombie(h, new BlockPos(2, 1, 0));
            h.runAfterDelay(3, () -> { // act once the forced test chunks tick
                PowerType palm = PowerRegistry.get("spirit_palm");
                Powers.learn(sp, palm.id, 0);
                Powers.fillEnergy(sp);
                float hp = z.getHealth();
                Vec3 to = z.getEyePosition().subtract(sp.getEyePosition());
                float yRot = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
                PowerType.Result r = Casting.cast(sp, palm, 0, false, yRot, 0f);
                h.assertTrue(r.ok(), "palm should land: " + r.reason());
                h.assertTrue(z.getHealth() < hp, "the zombie took spirit damage");
                PowerType.Result miss = Casting.cast(sp, PowerRegistry.get("dash"), 0, false, yRot, 0f);
                h.assertTrue(!miss.ok() && miss.reason().equals("not_owned"), "unowned powers can't be cast");
                h.succeed();
            });
        });

        // SPEC §13 "Dash direction": a dash goes the way you move, at every level and in every variant (an upgrade once sent
        // every dash forward). Facing +z, left is +x.
        AwakenTests.register("powers_dash_goes_where_you_move", 60, h -> {
            floor(h, 12);
            ServerPlayer sp = player(h, new BlockPos(0, 1, 0));
            h.runAfterDelay(3, () -> {
                PowerType dash = PowerRegistry.get("dash");
                Powers.learn(sp, dash.id, 0);
                float[][] keys = {{0, 0}, {0, 1}, {0, -1}, {1, 0}, {-1, 0}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
                for (int step = 0; step < 3; step++) {
                    if (step == 1) Powers.learnOrLevel(sp, dash.id, 0, false); // a duplicate core: level 2
                    if (step == 2) Powers.learnOrLevel(sp, dash.id, 2, true); // Double, the variant, kept at its level
                    h.assertTrue(Powers.loadout(sp).get(dash.id.toString()).level() == (step == 0 ? 1 : 2), "the dash is at its step's level");
                    for (float[] k : keys) {
                        Powers.fillEnergy(sp);
                        Powers.resetCooldowns(sp);
                        sp.setDeltaMovement(Vec3.ZERO);
                        PowerType.Result r = Casting.cast(sp, dash, 0, false, 0f, 0f, k[0], k[1]);
                        h.assertTrue(r.ok(), "the dash casts: " + r.reason());
                        Vec3 v = sp.getDeltaMovement();
                        double hl = Math.sqrt(v.x * v.x + v.z * v.z);
                        // no key is forward; otherwise the strafe is x and the forward key z
                        double ex = k[0], ez = k[0] == 0 && k[1] == 0 ? 1 : k[1];
                        double el = Math.sqrt(ex * ex + ez * ez);
                        String at = "step " + step + " keys " + k[0] + "," + k[1] + " went " + v;
                        h.assertTrue(hl > 1.2, "the dash is a real dash: " + at);
                        h.assertTrue((v.x * ex + v.z * ez) / (hl * el) > 0.99, "the dash goes the way you move: " + at);
                    }
                }
                // the keys the server last heard steer a cast that carries none (commands, fake players)
                Powers.fillEnergy(sp);
                Powers.resetCooldowns(sp);
                sp.setDeltaMovement(Vec3.ZERO);
                sp.setLastClientInput(new net.minecraft.world.entity.player.Input(false, false, false, true, false, false, false));
                h.assertTrue(Casting.cast(sp, dash, 0, false, 0f, 0f).ok(), "the dash casts on held keys");
                h.assertTrue(sp.getDeltaMovement().x < -1.2, "a held right key dashes right: " + sp.getDeltaMovement());
                sp.setLastClientInput(net.minecraft.world.entity.player.Input.EMPTY);
                // Blink, the teleporting variant, turns the same way
                Powers.learnOrLevel(sp, dash.id, 1, true);
                Powers.fillEnergy(sp);
                Powers.resetCooldowns(sp);
                sp.snapTo(sp.getX(), sp.getY(), sp.getZ() - 4, 0f, 0f);
                double x0 = sp.getX(), z0 = sp.getZ();
                h.assertTrue(Casting.cast(sp, dash, 0, false, 0f, 0f, 1f, 0f).ok(), "the blink casts sideways");
                h.assertTrue(sp.getX() - x0 > 2 && Math.abs(sp.getZ() - z0) < 0.5, "a left blink goes left: " + (sp.getX() - x0) + "," + (sp.getZ() - z0));
                h.succeed();
            });
        });

        // a blink aimed a little low glides along the ground (it stopped dead at its first step into the floor)
        AwakenTests.register("powers_blink_low_aim", 40, h -> {
            floor(h, 18); // past the step's 14 blocks from where it starts, so it never runs off the edge
            // clear of the test's own block (a blink across it stopped at once), stepping away from it (yaw 180 looks north)
            ServerPlayer sp = player(h, new BlockPos(0, 1, -3));
            Vec3 home = sp.position();
            h.runAfterDelay(3, () -> {
                // a blink (Moves.blinkTarget) and a phase through walls (Moves.phaseTarget)
                for (String id : List.of("lightning_step", "ghost_step")) {
                    PowerType step = PowerRegistry.get(id);
                    Powers.learn(sp, step.id, 0);
                    sp.snapTo(home.x, home.y, home.z, 180f, 12f);
                    PowerType.Result r = Casting.cast(sp, step, -1, false, 180f, 12f);
                    h.assertTrue(r.ok(), id + " casts looking down: " + r.reason());
                    h.assertTrue(home.z - sp.getZ() > 4 && Math.abs(sp.getY() - home.y) < 0.01,
                            id + " goes forward along the ground: " + (home.z - sp.getZ()) + "," + (sp.getY() - home.y));
                }
                h.succeed();
            });
        });

        AwakenTests.register("powers_energy_gate", 40, h -> {
            ServerPlayer sp = player(h, new BlockPos(0, 1, 0));
            PowerType nova = PowerRegistry.get("nova");
            Powers.learn(sp, nova.id, 0);
            Powers.loadout(sp).energy = 5;
            PowerType.Result r = Casting.cast(sp, nova, 0, false, 0, 0);
            h.assertTrue(!r.ok() && r.reason().equals("energy"), "not enough energy should refuse, got " + r.reason());
            h.succeed();
        });

        AwakenTests.register("powers_construct_wall_decays", 120, h -> {
            floor(h, 4);
            ServerPlayer sp = player(h, new BlockPos(0, 1, 0));
            BlockPos stone = h.absolutePos(new BlockPos(0, 1, 2));
            h.getLevel().setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
            List<BlockPos> wall = Constructs.wall(h.absolutePos(new BlockPos(0, 1, 0)), Direction.SOUTH, 2, 3, 2);
            int placed = Constructs.place(sp, wall, Set.of(), false, 40);
            h.assertTrue(placed == 5, "5 of 6 placed (one spot is stone), placed " + placed);
            h.assertTrue(h.getLevel().getBlockState(stone).is(Blocks.STONE), "never overwrites a block");
            BlockPos probe = h.absolutePos(new BlockPos(1, 1, 2));
            h.assertTrue(h.getLevel().getBlockState(probe).getBlock() instanceof ConstructBlock, "construct stands");
            h.assertTrue(!Constructs.ownedNear(sp, Vec3.atCenterOf(probe), 3).isEmpty(), "construct remembered for Collapse");
            h.runAfterDelay(60, () -> {
                h.assertTrue(h.getLevel().getBlockState(probe).isAir(), "construct decayed after its lifetime");
                h.assertTrue(h.getLevel().getBlockState(stone).is(Blocks.STONE), "stone untouched");
                h.succeed();
            });
        });

        AwakenTests.register("powers_terrain_restores", 120, h -> {
            floor(h, 2);
            ServerPlayer sp = player(h, new BlockPos(0, 2, 0));
            BlockPos p = h.absolutePos(new BlockPos(1, 0, 1));
            var original = h.getLevel().getBlockState(p);
            h.assertTrue(TerrainLedger.lift(h.getLevel(), sp, p, 20) != null, "a stone floor block can be lifted");
            h.assertTrue(h.getLevel().getBlockState(p).isAir(), "lifted out");
            h.runAfterDelay(50, () -> {
                h.assertTrue(h.getLevel().getBlockState(p).equals(original), "restored to the original block");
                h.succeed();
            });
        });

        AwakenTests.register("powers_force_table", 20, h -> {
            var none = EnumSet.noneOf(Forces.Tag.class);
            h.assertTrue(ForceInteractions.reactionFor(Forces.Force.HEAT, subject(EnumSet.of(Forces.Tag.SPORES), false, false, false, false, false))
                    == ForceInteractions.Reaction.SPORE_CHAIN, "heat + spores");
            h.assertTrue(ForceInteractions.reactionFor(Forces.Force.CHARGE, subject(none, true, false, false, false, false))
                    == ForceInteractions.Reaction.STUN_SPREAD, "charge + water");
            h.assertTrue(ForceInteractions.reactionFor(Forces.Force.GRAVITY, subject(none, false, false, false, false, true))
                    == ForceInteractions.Reaction.VOLATILE_DETONATION, "gravity + volatile");
            h.assertTrue(ForceInteractions.reactionFor(Forces.Force.SPIRIT, subject(EnumSet.of(Forces.Tag.MIRROR), false, false, false, false, false))
                    == ForceInteractions.Reaction.MIRROR_SHATTER, "spirit + mirror");
            h.assertTrue(ForceInteractions.reactionFor(Forces.Force.HEAT, subject(none, false, false, true, false, false))
                    == ForceInteractions.Reaction.STEAM, "heat + frost");
            h.assertTrue(ForceInteractions.reactionFor(Forces.Force.FROST, subject(none, false, true, false, false, false))
                    == ForceInteractions.Reaction.STEAM, "frost + burning");
            h.assertTrue(ForceInteractions.reactionFor(Forces.Force.CHARGE, subject(none, true, false, false, true, false))
                    == ForceInteractions.Reaction.OVERCHARGE, "charge + your echo (even wet)");
            h.assertTrue(ForceInteractions.reactionFor(Forces.Force.HEAT, subject(none, true, false, false, false, false))
                    == ForceInteractions.Reaction.NONE, "heat + water does nothing");
            h.succeed();
        });

        AwakenTests.register("powers_force_spore_chain", 80, h -> {
            floor(h, 4);
            ServerPlayer sp = player(h, new BlockPos(-3, 1, 0));
            // keep both zombies in one chunk: on the test server a neighbouring chunk may not be entity-ticking
            int ax = h.absolutePos(BlockPos.ZERO).getX();
            int dx = ((ax + 3) >> 4) == (ax >> 4) ? 3 : -3;
            Zombie a = stillZombie(h, new BlockPos(0, 1, 0));
            Zombie b = stillZombie(h, new BlockPos(dx, 1, 0));
            Forces.tag(a, Forces.Tag.SPORES);
            Forces.tag(b, Forces.Tag.SPORES);
            // let the spawned mobs settle before the first hit; schedule both steps from the body (never nest them)
            float[] hpB = new float[1];
            long[] before = new long[1];
            boolean[] hit = new boolean[1];
            h.runAfterDelay(5, () -> {
                hpB[0] = b.getHealth();
                before[0] = Progression.profile(sp).counter(Counters.FORCE_INTERACTIONS);
                hit[0] = Combat.hurtFrom(sp, null, a, 2f, Forces.Force.HEAT, true);
            });
            h.runAfterDelay(20, () -> {
                h.assertTrue(b.getHealth() < hpB[0] || !b.isAlive(), "the chain reached the second spore-tagged zombie (first hit=" + hit[0]
                        + ", a=" + a.blockPosition() + " alive=" + a.isAlive() + ", b=" + b.blockPosition() + " hp " + hpB[0] + " -> " + b.getHealth() + ")");
                h.assertTrue(Progression.profile(sp).counter(Counters.FORCE_INTERACTIONS) > before[0], "force interaction counted");
                h.succeed();
            });
        });

        AwakenTests.register("powers_force_charge_water", 80, h -> {
            floor(h, 3);
            BlockPos w = new BlockPos(1, 1, 0);
            h.setBlock(w, Blocks.WATER);
            ServerPlayer sp = player(h, new BlockPos(-2, 1, 0));
            Zombie z = stillZombie(h, w);
            Forces.tag(z, Forces.Tag.WATER); // a Flooding gate tags its mobs; also keeps the test steady while it bobs
            h.runAfterDelay(5, () -> {
                float hp = z.getHealth();
                long before = Progression.profile(sp).counter(Counters.FORCE_INTERACTIONS);
                boolean hit = Combat.hurtFrom(sp, null, z, 1f, Forces.Force.CHARGE, true);
                h.assertTrue(hit && z.getHealth() < hp, "the charge landed (hit=" + hit + ", hp " + hp + " -> " + z.getHealth()
                        + ", alive=" + z.isAlive() + ", invul=" + z.isInvulnerable() + ", cd=" + z.damageCooldownTime + ")");
                h.assertTrue(Progression.profile(sp).counter(Counters.FORCE_INTERACTIONS) > before, "reaction counted (wet=" + z.isInWater() + ")");
                h.assertTrue(z.hasEffect(PowerContent.STUNNED), "a zombie standing in water is stunned by charge (effects="
                        + z.getActiveEffects() + ", wet=" + z.isInWater() + ", tagged=" + Forces.has(z, Forces.Tag.WATER)
                        + ", pos=" + z.blockPosition() + ", alive=" + z.isAlive() + ")");
                h.succeed();
            });
        });

        AwakenTests.register("powers_flow_time_slow", 60, h -> {
            floor(h, 3);
            ServerPlayer sp = player(h, new BlockPos(0, 1, 0));
            PowerType flow = PowerRegistry.get("flow");
            Powers.learn(sp, flow.id, 0);
            Powers.fillEnergy(sp);
            h.assertTrue(Casting.cast(sp, flow, 0, false, 0, 0).ok(), "flow casts");
            h.assertTrue(TimeSlow.active(), "a time-slow zone is active");
            h.assertTrue(sp.getPostEffects().contains(Moves.FX_FLOW), "flow post effect on");
            Casting.endAll(sp, Running.Why.CANCELLED);
            h.assertTrue(!sp.getPostEffects().contains(Moves.FX_FLOW), "post effect cleared when flow ends");
            h.assertTrue(!TimeSlow.active(), "zone removed");
            h.succeed();
        });
    }

    // ---- the smoke test runs alone: its area powers would otherwise hit other tests' mobs ------------------------------

    static void registerIsolated(net.neoforged.bus.api.IEventBus modBus) {
        AwakenTests.registerBare("powers_cast_every_power", "powers_isolated", 200, PowersTests::castEveryPower);
    }

    /** Casts every power once on a fake player with a zombie in front: nothing may throw, nothing may linger. */
    static void castEveryPower(GameTestHelper h) {
        floor(h, 6);
        ServerPlayer sp = player(h, new BlockPos(-3, 1, 0));
        Set<String> fine = Set.of("no_target", "blocked", "no_summon", "no_constructs", "too_heavy", "too_strong", "no_ground");
        int errorsBefore = Casting.errors;
        int cast = 0;
        for (PowerType t : PowerRegistry.all()) {
            if (t.isPassive()) continue;
            Powers.learn(sp, t.id, 0);
            Powers.resetCooldowns(sp);
            Powers.fillEnergy(sp);
            Zombie z = stillZombie(h, new BlockPos(0, 1, 0));
            Vec3 to = z.getEyePosition().subtract(sp.getEyePosition());
            float yRot = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
            PowerType.Result r = Casting.cast(sp, t, 0, false, yRot, 0f);
            h.assertTrue(r.ok() || fine.contains(r.reason()), t + " failed to cast: " + r.reason());
            if (r.ok()) cast++;
            for (int i = 0; i < 30; i++) {
                if (i == 12) Casting.release(sp, t);
                Casting.tickPlayer(sp);
            }
            Casting.endAll(sp, Running.Why.CANCELLED);
            z.discard();
            for (var e : h.getLevel().getEntitiesOfClass(dev.awaken.powers.content.PowerSummon.class, sp.getBoundingBox().inflate(48),
                    e -> e.getOwner() == sp)) e.discard();
        }
        for (int i = 0; i < 20; i++) Casting.tickPlayer(sp); // passives
        int errors = Casting.errors - errorsBefore;
        int count = cast;
        h.assertTrue(errors == 0, errors + " power behaviours threw (see the log)");
        h.assertTrue(count >= 45, "most powers should cast in the open with a target in front, only " + count + " did");
        h.assertTrue(sp.getPostEffects().stream().noneMatch(Moves.ALL_FX::contains), "no post effect outlives its power");
        h.assertTrue(!TimeSlow.active(), "time slow cleared");
        // let delayed hits, bolts and thrown blocks play out, then make sure none of them threw either
        h.runAfterDelay(60, () -> {
            h.assertTrue(Casting.errors == errorsBefore, (Casting.errors - errorsBefore) + " delayed power effects threw (see the log)");
            h.succeed();
        });
    }

    private static ForceInteractions.Subject subject(EnumSet<Forces.Tag> tags, boolean wet, boolean burning, boolean frozen, boolean echo, boolean vol) {
        return new ForceInteractions.Subject(tags, wet, burning, frozen, echo, vol);
    }

    private static ServerPlayer player(GameTestHelper h, BlockPos rel) {
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        Vec3 at = h.absoluteVec(Vec3.atBottomCenterOf(rel));
        sp.snapTo(at.x, at.y, at.z, 0f, 0f);
        return sp;
    }

    private static void floor(GameTestHelper h, int r) {
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
    }

    /** Test zombies stand still and can't burn, so force tests don't depend on AI movement or daylight. */
    private static Zombie stillZombie(GameTestHelper h, BlockPos pos) {
        Zombie z = h.spawn(EntityTypes.ZOMBIE, pos);
        z.setNoAi(true);
        z.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 20 * 60, 0, false, false));
        return z;
    }
}
