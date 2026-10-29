package dev.awaken.hearth;

import dev.awaken.core.test.AwakenTests;
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

        // (SPEC §43.4) an older save's Echoes are read only to be let go: one line at login, never written back
        AwakenTests.register("hearth_old_echoes_released", 20, h -> {
            CompoundTag old = new CompoundTag();
            old.putInt("schema", 4);
            net.minecraft.nbt.ListTag echoes = new net.minecraft.nbt.ListTag();
            for (int k = 0; k < 2; k++) {
                CompoundTag e = new CompoundTag();
                e.putString("name", "Echo " + k);
                echoes.add(e);
            }
            old.put("echoes", echoes);
            var hp = dev.awaken.hearth.player.HearthPlayer.MAP_CODEC.codec().parse(net.minecraft.nbt.NbtOps.INSTANCE, old).getOrThrow();
            h.assertTrue(hp.echoesToRelease == 2, "two Echoes to let go, got " + hp.echoesToRelease);
            var again = dev.awaken.hearth.player.HearthPlayer.MAP_CODEC.codec().encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, hp).getOrThrow();
            h.assertTrue(again instanceof CompoundTag c && !c.contains("echoes"), "and never written back");
            ServerPlayer sp = h.makeMockServerPlayerInLevel();
            HearthService.player(sp).echoesToRelease = 2;
            h.assertTrue(dev.awaken.hearth.echo.OldEchoes.release(sp), "told once");
            h.assertFalse(dev.awaken.hearth.echo.OldEchoes.release(sp), "and only once");
            Zombie body = spawnZombie(h.getLevel(), h.absolutePos(new BlockPos(0, 1, 0)));
            h.assertFalse(dev.awaken.hearth.echo.OldEchoes.isOldEcho(body), "a plain zombie is not an old Echo");
            body.setData(dev.awaken.core.api.CoreApi.MOB_ROLE.get(), new dev.awaken.core.api.MobRole(
                    dev.awaken.core.api.MobRole.Role.ECHO, 5, "", java.util.Optional.empty(), ""));
            h.assertTrue(dev.awaken.hearth.echo.OldEchoes.isOldEcho(body), "an Echo's body is");
            body.discard();
            h.succeed();
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
            h.assertTrue(Room.classify(1, 1, 0, 0, 0, 0, 0).contains(RoomType.SANCTUARY), "bed + mat classify as a Sanctuary");
            h.assertTrue(Room.classify(0, 0, 1, 1, 1, 2, 1).containsAll(java.util.EnumSet.of(RoomType.ARMORY, RoomType.TROPHY_HALL,
                    RoomType.WORKSHOP)), "armory, hall, workshop");
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
            h.assertTrue(dev.awaken.hearth.trophy.Trophies.bonusCount(25) == 20, "trophy bonus capped");
            h.assertTrue(HearthService.restSeconds(1) == 30 && HearthService.restSeconds(5) == 14, "rest time by tier");
            h.succeed();
        });
    }

    private static Zombie spawnZombie(ServerLevel level, BlockPos at) {
        Zombie z = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.MOB_SUMMONED);
        z.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        z.setPersistenceRequired();
        level.addFreshEntity(z);
        return z;
    }
}
