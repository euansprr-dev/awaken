package dev.awaken.hearth;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.hearth.echo.BindMath;
import dev.awaken.hearth.echo.Duty;
import dev.awaken.hearth.echo.EchoRecord;
import dev.awaken.hearth.echo.EchoRole;
import dev.awaken.hearth.echo.Echoes;
import dev.awaken.hearth.room.Room;
import dev.awaken.hearth.room.RoomType;
import dev.awaken.hearth.training.Training;
import dev.awaken.hearth.town.Rumours;
import dev.awaken.hearth.trial.TrialChunkGenerator;
import dev.awaken.hearth.trial.Trials;
import dev.awaken.progression.Curve;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** GameTests for the hearth module: pure rules first, then in-world behaviour. */
final class HearthTests {
    private HearthTests() {}

    static void register() {
        AwakenTests.register("hearth_registry_nearest", 20, h -> {
            HearthData d = new HearthData();
            UUID a = UUID.randomUUID(), b = UUID.randomUUID();
            HearthRecord ra = new HearthRecord(a, "A", new BlockPos(0, 64, 0), HearthService.OVERWORLD);
            HearthRecord rb = new HearthRecord(b, "B", new BlockPos(500, 70, 0), HearthService.OVERWORLD);
            d.put(ra);
            d.put(rb);
            h.assertTrue(d.of(a).orElseThrow() == ra, "lookup by owner");
            h.assertTrue(d.at(new BlockPos(500, 70, 0), HearthService.OVERWORLD).orElseThrow() == rb, "lookup by anchor position");
            h.assertTrue(d.nearest(new BlockPos(100, 64, 0), HearthService.OVERWORLD, 256).orElseThrow() == ra, "nearest is A");
            h.assertTrue(d.nearest(new BlockPos(420, 64, 0), HearthService.OVERWORLD, 256).orElseThrow() == rb, "nearest is B");
            h.assertTrue(d.nearest(new BlockPos(250, 64, 900), HearthService.OVERWORLD, 256).isEmpty(), "nothing within 256");
            h.assertTrue(d.nearest(new BlockPos(10, 64, 0), "minecraft:the_nether", 256).isEmpty(), "other dimension");
            h.assertTrue(d.containing(new BlockPos(40, 60, -40), HearthService.OVERWORLD).orElseThrow() == ra, "inside A's radius");
            h.assertTrue(d.containing(new BlockPos(60, 64, 0), HearthService.OVERWORLD).isEmpty(), "outside tier I radius");
            ra.tier = 3;
            h.assertTrue(ra.radius() == 64 && d.containing(new BlockPos(60, 64, 0), HearthService.OVERWORLD).isPresent(), "tier III reaches 64");
            h.assertTrue(HearthRecord.radiusForTier(1) == 48 && HearthRecord.radiusForTier(5) == 96, "radius by tier (W6: 80 at IV, 96 at V)");
            d.remove(a);
            h.assertTrue(d.of(a).isEmpty() && d.all().size() == 1, "removed");
            h.succeed();
        });

        AwakenTests.register("hearth_cost_reduction", 20, h -> {
            h.assertTrue(Costs.multiplier(Rank.D.ordinal(), Rank.D.ordinal()) == 1.0, "no discount at the tier's own rank");
            h.assertTrue(Costs.multiplier(Rank.C.ordinal(), Rank.D.ordinal()) == 0.85, "one rank above: 15% off");
            h.assertTrue(Math.abs(Costs.multiplier(Rank.A.ordinal(), Rank.D.ordinal()) - Math.pow(0.85, 3)) < 1e-12, "three ranks above compounds");
            h.assertTrue(Costs.multiplier(Rank.E.ordinal(), Rank.B.ordinal()) == 1.0, "below the tier is never more expensive");
            h.assertTrue(Costs.reduce(48, Rank.C.ordinal(), Rank.D.ordinal()) == 41, "48 × 0.85 = 40.8 → 41, was " + Costs.reduce(48, 2, 1));
            h.assertTrue(Costs.reduce(16, Rank.D.ordinal(), Rank.D.ordinal()) == 16, "full price");
            h.assertTrue(Costs.reduce(1, Rank.SOVEREIGN.ordinal(), Rank.E.ordinal()) == 1, "never below one");
            h.assertTrue(Costs.reduce(0, 3, 0) == 0, "zero stays zero");
            var price = Costs.price(List.of(new Costs.Part(Items.IRON_INGOT, 20), new Costs.Part(Items.DIAMOND, 2)), Rank.B, Rank.D);
            h.assertTrue(price.get(0).count() == 15 && price.get(1).count() == 2, "two ranks: 20 → 15 (14.45 up), 2 → 2 (1.445 up), was " + price);
            h.assertTrue(Costs.discountPercent(Rank.B, Rank.D) == 28, "−28% shown for two ranks");
            h.assertTrue(Upgrades.anchorTier(2).tier() == Rank.D && Upgrades.anchorTier(5).tier() == Rank.A, "anchor tiers belong to D..A");
            h.succeed();
        });

        AwakenTests.register("hearth_bind_chance_and_cap", 20, h -> {
            double elite = BindMath.chance("ELITE", 5, 0, 0, 0);
            h.assertTrue(Math.abs(elite - 0.55) < 1e-9, "base elite chance 55%, was " + elite);
            h.assertTrue(Math.abs(BindMath.chance("WARDEN", 15, 0, 0, 0) - 0.45) < 1e-9, "SPI +1% per point over 5");
            h.assertTrue(Math.abs(BindMath.chance("ELITE", 5, 2, 0, 0) - 0.75) < 1e-9, "+10% per rank above the fallen");
            h.assertTrue(Math.abs(BindMath.chance("ELITE", 5, 0, 2, 0) - 0.25) < 1e-9, "-15% per rank below the fallen");
            h.assertTrue(Math.abs(BindMath.chance("BOSS", 5, 0, 0, 0.25) - 0.45) < 1e-9, "BIND_CHANCE perk adds");
            h.assertTrue(BindMath.chance("BOSS", 0, 0, 6, 0) == BindMath.MIN_CHANCE, "never below 5%");
            h.assertTrue(BindMath.chance("ELITE", 200, 6, 0, 1) == BindMath.MAX_CHANCE, "never above 95%");
            h.assertFalse(BindMath.bossAllowed(Rank.A.ordinal(), ""), "A-rank can't bind bosses");
            h.assertTrue(BindMath.bossAllowed(Rank.S.ordinal(), ""), "S-rank can");
            h.assertTrue(BindMath.bossAllowed(Rank.D.ordinal(), BindMath.SHADOWBINDER), "Shadowbinders can");
            h.assertTrue(BindMath.fieldCap(5) == 3 && BindMath.fieldCap(6) == 4 && BindMath.fieldCap(17) == 5 && BindMath.fieldCap(18) == 6, "cap = 3 + SPI/6");
            h.assertTrue(BindMath.totalCap(12, 3) == 8, "pads add roster room");
            h.assertTrue(BindMath.rankOfLevel(4) == 0 && BindMath.rankOfLevel(17) == 1 && BindMath.rankOfLevel(60) == 3, "mob rank from content level");
            EchoRecord r = new EchoRecord(UUID.randomUUID(), "minecraft:zombie", new CompoundTag(), "Z", 1, 0, true, 0, "ELITE", 5, 0);
            EchoRecord up = r.withXp(EchoRecord.xpToNext(1) + EchoRecord.xpToNext(2));
            h.assertTrue(up.level() == 3 && up.xp() == 0, "echo levels from kills, was " + up.level());
            h.succeed();
        });

        AwakenTests.register("hearth_role_pad_assignment", 20, h -> {
            List<EchoRecord> roster = new ArrayList<>();
            EchoRecord field = rec("Field", 9, true, 0);
            EchoRecord low = rec("Low", 1, false, 3);
            EchoRecord mid = rec("Mid", 3, false, 2);
            EchoRecord top = rec("Top", 5, false, 1);
            roster.add(field); roster.add(low); roster.add(mid); roster.add(top);
            LinkedHashMap<BlockPos, EchoRole> pads = new LinkedHashMap<>();
            BlockPos farm = new BlockPos(1, 0, 0), guard = new BlockPos(2, 0, 0), haul = new BlockPos(3, 0, 0), mine = new BlockPos(4, 0, 0);
            pads.put(farm, EchoRole.FARM);
            pads.put(guard, EchoRole.GUARD);
            pads.put(haul, EchoRole.HAUL);
            Map<UUID, Duty.Assignment> as = Duty.assign(roster, pads);
            h.assertTrue(!as.containsKey(field.id()), "marching Echoes never take a pad");
            h.assertTrue(as.get(top.id()).pad().equals(guard), "the strongest guards first");
            h.assertTrue(as.get(mid.id()).pad().equals(farm), "then pads in placement order");
            h.assertTrue(as.get(low.id()).pad().equals(haul), "last pad gets the last Echo");
            pads.put(mine, EchoRole.MINE);
            as = Duty.assign(roster, pads);
            h.assertTrue(as.size() == 3 && as.values().stream().noneMatch(a -> a.pad().equals(mine)), "a pad with nobody left stays empty");
            pads.clear();
            h.assertTrue(Duty.assign(roster, pads).isEmpty(), "no pads, no duty");
            h.succeed();
        });

        AwakenTests.register("hearth_echo_never_targets_owner", 100, h -> {
            ServerPlayer owner = h.makeMockServerPlayerInLevel();
            ServerLevel level = h.getLevel();
            BlockPos at = h.absolutePos(new BlockPos(0, 1, 0));
            Zombie echo = spawnZombie(level, at);
            Zombie mate = spawnZombie(level, at.east());
            Zombie foe = spawnZombie(level, at.west());
            Echoes.makeEcho(echo, owner.getUUID(), rec("Echo", 1, true, 0), false);
            Echoes.makeEcho(mate, owner.getUUID(), rec("Mate", 1, true, 0), false);
            h.assertTrue(Echoes.isEcho(echo) && Echoes.isEcho(mate) && !Echoes.isEcho(foe), "links set");
            h.assertFalse(Echoes.canTarget(echo, owner), "never its owner");
            h.assertFalse(Echoes.canTarget(echo, mate), "never another of the owner's Echoes");
            h.assertTrue(Echoes.canTarget(echo, foe), "monsters are fair game");
            echo.setTarget(owner);
            h.assertTrue(echo.getTargetUnchecked() == null, "setTarget(owner) is refused");
            echo.setTarget(mate);
            h.assertTrue(echo.getTargetUnchecked() == null, "setTarget(ally) is refused");
            echo.setTarget(foe);
            h.assertTrue(echo.getTargetUnchecked() == foe, "setTarget(monster) works");
            float before = echo.getHealth();
            echo.hurtServer(level, level.damageSources().playerAttack(owner), 5f);
            h.assertTrue(echo.getHealth() == before, "the owner can't hurt their own Echo");
            float ob = owner.getHealth();
            owner.hurtServer(level, level.damageSources().source(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK, echo, owner), 4f);
            h.assertTrue(owner.getHealth() == ob, "an Echo's strike never lands on its owner");
            mate.hurtServer(level, level.damageSources().mobAttack(echo), 4f);
            h.assertTrue(mate.getHealth() == mate.getMaxHealth(), "Echoes don't hurt each other");
            for (Entity e : List.of(echo, mate, foe)) e.discard();
            h.succeed();
        });

        AwakenTests.register("hearth_bind_summon_duty", 100, h -> {
            ServerPlayer sp = h.makeMockServerPlayerInLevel();
            ServerLevel level = h.getLevel();
            BlockPos at = h.absolutePos(new BlockPos(0, 1, 0));
            sp.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            Progression.awaken(sp);
            Zombie elite = spawnZombie(level, at.north(2));
            elite.setData(dev.awaken.core.api.CoreApi.MOB_ROLE.get(), new dev.awaken.core.api.MobRole(
                    dev.awaken.core.api.MobRole.Role.ELITE, 5, "", java.util.Optional.empty(), ""));
            Echoes.Lingering l = Echoes.linger(sp, elite, "ELITE", 5);
            elite.discard();
            h.assertTrue(Echoes.bindAttempt(sp, l, 0.0) == Echoes.BindResult.BOUND, "a sure roll binds");
            var hp = HearthService.player(sp);
            h.assertTrue(hp.echoes.size() == 1 && hp.echoes.get(0).field(), "bound into the field army");
            Echoes.summonField(sp);
            List<LivingEntity> out = Echoes.fieldEntities(sp);
            h.assertTrue(out.size() == 1, "summoned one Echo, got " + out.size());
            LivingEntity e = out.get(0);
            h.assertTrue(e instanceof Zombie && Echoes.isEcho(e) && Echoes.link(e).owner().equals(sp.getUUID()), "a zombie copy linked to its owner");
            h.assertTrue(e.getExistingData(dev.awaken.core.api.CoreApi.MOB_ROLE.get()).map(r -> r.role() == dev.awaken.core.api.MobRole.Role.ECHO).orElse(false),
                    "role ECHO");
            h.assertFalse(dev.awaken.core.Forces.has(e, dev.awaken.core.Forces.Tag.ECHO), "an ally is not Echoing (it would get up when it dies)");
            h.assertTrue(Hearth.ownedEchoes(sp).contains(e), "Hearth.ownedEchoes sees it");
            Echoes.dismissField(sp, true);
            h.assertTrue(Echoes.fieldEntities(sp).isEmpty() && !hp.fieldOut, "dismissed");

            // Send it home onto a Guard pad: it walks out of the roster's field list and onto the pad.
            UUID id = hp.echoes.get(0).id();
            hp.replace(hp.echoes.get(0).withField(false));
            HearthRecord home = new HearthRecord(sp.getUUID(), "test", at.east(3), HearthService.OVERWORLD);
            home.pads.put(at.south(2), EchoRole.GUARD);
            // Duty only spawns workers on entity-ticking pads: keep running upkeep until the fresh test area ticks.
            h.succeedWhen(() -> {
                Duty.upkeep(sp, level, home, level.getGameTime());
                var worker = Echoes.live(level.getServer(), id);
                h.assertTrue(worker != null && Echoes.link(worker).duty(), "the Echo works the pad (pad ticking="
                        + level.isPositionEntityTicking(at.south(2)) + ")");
                h.assertTrue(worker.blockPosition().closerThan(at.south(2), 2.5), "standing on its pad");
                Duty.releaseAll(level, home);
                h.assertTrue(Echoes.live(level.getServer(), id) == null, "released from duty");
            });
        });

        AwakenTests.registerIsolated("hearth_room_detection", 600, h -> {
            ServerLevel level = h.getLevel();
            // Built high above the shared test floor so beds and blocks left by other tests are outside the 48-block sweep,
            // and inside a single chunk (a neighbouring chunk on the test server may not be fully loaded).
            BlockPos origin = h.absolutePos(BlockPos.ZERO);
            BlockPos base = new BlockPos((origin.getX() & ~15) + 4, origin.getY() + 120, (origin.getZ() & ~15) + 4);
            // A closed 5×4×5 stone room with a glass ceiling, a bed and a Meditation Mat inside.
            for (int x = 0; x < 5; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 5; z++) {
                boolean shell = x == 0 || x == 4 || y == 0 || y == 3 || z == 0 || z == 4;
                // a glass ceiling still makes a closed room
                BlockState wall = y == 3 ? Blocks.GLASS.defaultBlockState() : Blocks.STONE.defaultBlockState();
                level.setBlock(base.offset(x, y, z), shell ? wall : Blocks.AIR.defaultBlockState(), 3);
            }
            BlockState foot = Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT);
            level.setBlock(base.offset(1, 1, 1), foot, 3);
            level.setBlock(base.offset(1, 1, 2), foot.setValue(BedBlock.PART, BedPart.HEAD), 3);
            level.setBlock(base.offset(3, 1, 3), HearthContent.MEDITATION_MAT.get().defaultBlockState(), 3);
            UUID owner = UUID.randomUUID();
            HearthRecord rec = new HearthRecord(owner, "test", base.offset(2, 4, 2), HearthService.OVERWORLD);
            // upkeep drops any Hearth whose Anchor block is gone, so the record needs a real Anchor on the roof
            level.setBlock(rec.pos, HearthContent.HEARTH_ANCHOR.get().defaultBlockState(), 3);
            HearthData data = HearthData.get(level.getServer());
            data.put(rec);
            h.assertTrue(Room.classify(1, 1, 0, 0, 0, 0, 0, 0).contains(RoomType.SANCTUARY), "bed + mat classify as a Sanctuary");
            h.assertTrue(Room.classify(0, 0, 1, 1, 1, 2, 1, 1).containsAll(java.util.EnumSet.of(RoomType.ARMORY, RoomType.TROPHY_HALL,
                    RoomType.WORKSHOP, RoomType.BARRACKS)), "armory, hall, workshop, barracks");
            h.succeedWhen(() -> {
                HearthService.scanNow(level, rec);
                boolean ok = rec.hasRoom(RoomType.SANCTUARY) && Hearth.isSanctuary(level, base.offset(2, 1, 2));
                if (ok) { // take it all down: other tests share these columns (a leftover Sanctuary confuses them)
                    data.remove(owner);
                    level.removeBlock(rec.pos, false);
                    for (int x = 0; x < 5; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 5; z++) {
                        level.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
                h.assertTrue(ok, "the closed room is recognised as a Sanctuary (rooms "
                        + rec.rooms.stream().map(r -> r.types().toString()).toList() + ", hearth found "
                        + HearthData.get(level.getServer()).containing(base.offset(2, 1, 2), HearthService.OVERWORLD).isPresent() + ")");
            });
        });

        AwakenTests.register("hearth_trial_pass_ranks_up", 900, h -> {
            ServerPlayer sp = h.makeMockServerPlayerInLevel();
            Progression.awaken(sp);
            double need = 1;
            for (int l = 1; l < Rank.D.levelRequired; l++) need += Curve.xpToNext(l);
            Progression.grantXp(sp, need / dev.awaken.core.AwakenConfig.xpMultiplier(), Progression.XpSource.COMMAND);
            h.assertTrue(Progression.profile(sp).reassessmentReady(), "level " + Progression.profile(sp).level() + " should be ready for D");
            Trials.Session s = Trials.startAt(sp, Trials.Kind.WAVES, Rank.D, h.getLevel(), h.absolutePos(new BlockPos(0, 1, 0)), false, 0);
            h.assertTrue(s.state() == Trials.State.ACTIVE, "trial running");
            h.onEachTick(() -> {
                for (UUID u : List.copyOf(s.mobs())) {
                    if (h.getLevel().getEntity(u) instanceof LivingEntity l && l.isAlive()) l.kill(h.getLevel());
                }
            });
            h.succeedWhen(() -> {
                h.assertTrue(s.state() == Trials.State.PASSED, "trial passed after three waves, stage " + s.stage());
                h.assertTrue(Progression.rank(sp) == Rank.D, "rank is now D, was " + Progression.rank(sp));
                h.assertFalse(Trials.inTrial(sp), "session closed");
            });
        });

        AwakenTests.register("hearth_penalty_zone_lifecycle", 300, h -> {
            ServerPlayer sp = h.makeMockServerPlayerInLevel();
            HearthService.player(sp).penaltyPending = true;
            Trials.Session s = Trials.startAt(sp, Trials.Kind.PENALTY, Rank.E, h.getLevel(), h.absolutePos(new BlockPos(0, 1, 0)), true, 80);
            h.assertTrue(Trials.inTrial(sp) && s.state() == Trials.State.ACTIVE, "penalty zone running");
            List<UUID> seen = new ArrayList<>();
            h.onEachTick(() -> {
                for (UUID u : s.mobs()) if (!seen.contains(u)) seen.add(u);
            });
            h.succeedWhen(() -> {
                h.assertTrue(s.state() == Trials.State.PASSED, "survived the duration");
                h.assertFalse(Trials.inTrial(sp), "session closed");
                h.assertFalse(HearthService.player(sp).penaltyPending, "penalty cleared");
                h.assertTrue(!seen.isEmpty(), "waves spawned");
                for (UUID u : seen) h.assertTrue(h.getLevel().getEntity(u) == null, "wave mobs cleaned up");
            });
        });

        AwakenTests.register("hearth_trials_dimension", 100, h -> {
            // a trial opened in the air (a Penalty Zone mid-jump, on the wing) comes home to the ground under the player
            ServerPlayer sp = h.makeMockServerPlayerInLevel();
            h.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
            for (int y = 2; y <= 8; y++) h.setBlock(new BlockPos(1, y, 1), Blocks.AIR);
            BlockPos aloft = h.absolutePos(new BlockPos(1, 7, 1)), ground = h.absolutePos(new BlockPos(1, 2, 1));
            sp.snapTo(aloft.getX() + 0.5, aloft.getY() + 0.4, aloft.getZ() + 0.5, 0, 0);
            sp.setOnGround(false);
            h.assertTrue(Trials.standingY(sp) == ground.getY(), "home at the ground (" + ground.getY() + "), not " + Trials.standingY(sp));
            sp.setOnGround(true);
            h.assertTrue(Trials.standingY(sp) == sp.getY(), "on the ground, where they stand");
            var regs = h.getLevel().registryAccess();
            h.assertTrue(regs.lookupOrThrow(net.minecraft.core.registries.Registries.DIMENSION_TYPE)
                    .get(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION_TYPE, dev.awaken.Awaken.id("trials"))).isPresent(),
                    "the trials dimension type loads");
            h.assertTrue(regs.lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)
                    .get(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, dev.awaken.Awaken.id("trials"))).isPresent(),
                    "the trials biome loads");
            // The dimension file itself decodes against the live registries (generator codec, biome source, type).
            var res = h.getLevel().getServer().getResourceManager().getResource(dev.awaken.Awaken.id("dimension/trials.json"));
            h.assertTrue(res.isPresent(), "dimension/trials.json is in the mod's data");
            try (var reader = new java.io.InputStreamReader(res.get().open(), java.nio.charset.StandardCharsets.UTF_8)) {
                var json = com.google.gson.JsonParser.parseReader(reader);
                var stem = net.minecraft.world.level.dimension.LevelStem.CODEC.parse(
                        net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, regs), json);
                h.assertTrue(stem.result().isPresent(), "trials dimension decodes: " + stem.error().map(Object::toString).orElse(""));
                h.assertTrue(stem.result().get().generator() instanceof TrialChunkGenerator, "uses the trial generator");
            } catch (java.io.IOException ex) {
                h.fail("could not read dimension/trials.json: " + ex);
            }
            ServerLevel trials = h.getLevel().getServer().getLevel(Trials.DIMENSION);
            if (trials == null) {
                // The GameTest server only creates the overworld; in a real world the dimension is checked below.
                h.succeed();
                return;
            }
            BlockPos o = TrialChunkGenerator.origin(0);
            trials.getChunk(o.getX() >> 4, o.getZ() >> 4);
            BlockState floor = trials.getBlockState(o.offset(3, 0, 2));
            h.assertTrue(floor.is(Blocks.DEEPSLATE_TILES) || floor.is(Blocks.POLISHED_DEEPSLATE), "hall floor generated, was " + floor);
            h.assertTrue(trials.getBlockState(o.offset(0, 3, 10)).is(Blocks.POLISHED_BLACKSTONE_BRICKS), "hall wall generated");
            BlockPos d = TrialChunkGenerator.origin(1);
            trials.getChunk(d.getX() >> 4, d.getZ() >> 4);
            h.assertTrue(trials.getBlockState(d.offset(10, 0, 11)).is(Blocks.SAND), "desert floor generated");
            h.assertTrue(trials.getBlockState(o.offset(TrialChunkGenerator.SIZE + 4, 0, 4)).isAir(), "void between arenas");
            h.succeed();
        });

        AwakenTests.register("hearth_pure_rules", 20, h -> {
            h.assertTrue(Breaks.shouldCount(false, "CLEARED", 2, 2), "count a clean BROKEN→CLEARED");
            h.assertFalse(Breaks.shouldCount(false, "CLEARED", 2, 3), "the gates module already counted it");
            h.assertFalse(Breaks.shouldCount(true, "CLEARED", 2, 2), "never twice");
            h.assertFalse(Breaks.shouldCount(false, "BROKEN", 2, 2), "only once it's cleared");
            var first = Training.compare(null, 400);
            var better = Training.compare(400, 380);
            var worse = Training.compare(380, 390);
            h.assertTrue(first.first() && !first.record(), "first lap sets a best, not a record");
            h.assertTrue(better.record() && better.best() == 380 && better.delta() == -20, "beating the best is a record");
            h.assertTrue(!worse.record() && worse.best() == 380 && worse.delta() == 10, "slower laps keep the old best");
            h.assertTrue(Training.formatTime(1234).equals("1:01.70"), "time format, was " + Training.formatTime(1234));
            h.assertTrue(Rumours.direction(BlockPos.ZERO, new BlockPos(100, 0, -100)).equals("to the northeast"), "compass");
            h.assertTrue(Rumours.direction(BlockPos.ZERO, new BlockPos(0, 0, 50)).equals("to the south"), "compass south");
            h.assertTrue(TrialChunkGenerator.isDesert(1) && !TrialChunkGenerator.isDesert(2), "odd cells are desert");
            h.assertTrue(TrialChunkGenerator.arenaBlock(false, 0, TrialChunkGenerator.FLOOR + 2, 5) != null, "hall walls exist");
            h.assertTrue(TrialChunkGenerator.arenaBlock(true, 10, TrialChunkGenerator.FLOOR, 10).is(Blocks.SAND), "desert floor is sand");
            h.assertTrue(Trials.Kind.forTarget(Rank.D) == Trials.Kind.WAVES && Trials.Kind.forTarget(Rank.A) == Trials.Kind.DESCENT, "trial per rank");
            h.assertTrue(!Duty.rollOre(0, 1, 1).isEmpty() && Duty.rollOre(999, 50, 5).is(Items.DIAMOND), "mine table");
            h.assertTrue(dev.awaken.hearth.trophy.Trophies.bonusCount(25) == 20, "trophy bonus capped");
            h.assertTrue(HearthService.restSeconds(1) == 30 && HearthService.restSeconds(5) == 14, "rest time by tier");
            h.succeed();
        });
    }

    private static EchoRecord rec(String name, int level, boolean field, long day) {
        return new EchoRecord(UUID.randomUUID(), "minecraft:zombie", new CompoundTag(), name, level, 0, field, 0, "ELITE", 5, day);
    }

    private static Zombie spawnZombie(ServerLevel level, BlockPos at) {
        Zombie z = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.MOB_SUMMONED);
        z.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        z.setPersistenceRequired();
        level.addFreshEntity(z);
        return z;
    }
}
