package dev.awaken.world.fauna;

import com.mojang.serialization.JsonOps;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.mobs.entity.AwakenMob;
import dev.awaken.mobs.entity.Fauna3Kinds;
import dev.awaken.mobs.entity.MountPhysics;
import dev.awaken.mobs.entity.WildMount;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Mounts (P3): the riding numbers, the trust ritual step by step (refused below level 11, refused unless sneaking, one
 * feeding at a time, trust after the kind's count), the bridle, the rider in control, the whistle (comes, says why
 * not, its cooldown), and the bond saved on both sides (the mount's codec and entity save, the player's attachment).
 */
final class MountTests {
    private MountTests() {}

    static final int UP = FaunaTests.UP;

    static void register() {
        AwakenTests.register("fauna_mount_physics", 20, h -> {
            Vec3 g = MountPhysics.groundInput(1.0F, 1.0F);
            h.assertTrue(g.x == 0.5 && g.z == 1.0 && g.y == 0, "land: half speed sideways, full ahead: " + g);
            h.assertTrue(MountPhysics.groundInput(0, -1.0F).z == -0.25, "a quarter speed backwards");
            Vec3 down = MountPhysics.swimInput(0, 1.0F, 45.0F), up = MountPhysics.swimInput(0, 1.0F, -45.0F);
            h.assertTrue(down.y < -0.6 && down.z > 0.6 && up.y > 0.6, "in water you swim where you look: " + down + " / " + up);
            h.assertTrue(MountPhysics.swimInput(0, 0, 30).lengthSqr() == 0, "no keys, no swim");
            h.assertTrue(MountPhysics.glideVertical(0.2, 0.0) > 0 && MountPhysics.glideVertical(0.2, 0.0) <= 0.18, "a glider below its hover rises gently");
            double v = 0.8;
            for (int i = 0; i < 80; i++) v = MountPhysics.glideVertical(6.0, v);
            h.assertTrue(v < 0 && v >= -MountPhysics.GLIDE_SINK, "after a soar it glides down slowly: " + v);
            double ground = MountPhysics.jumpImpulse(Fauna3Kinds.Style.GROUND, 1.0F, 0.9F), glide = MountPhysics.jumpImpulse(Fauna3Kinds.Style.GLIDE, 1.0F, 1.0F);
            h.assertTrue(ground > 0.7 && ground < 0.95 && glide > ground, "a full stag jump clears 3-4 blocks, the ray soars higher: " + ground + " / " + glide);
            h.assertTrue(MountPhysics.jumpImpulse(Fauna3Kinds.Style.GROUND, 0.0F, 1.0F) < ground, "a short tap jumps lower");
            Vec3 dash = MountPhysics.dash(new Vec3(0, 0, 1), 1.0F, 1.3, true, false), leap = MountPhysics.dash(new Vec3(0, 0, 1), 1.0F, 1.3, true, true);
            h.assertTrue(dash.z > 0.5 && dash.y == 0 && leap.y > 0.5, "a swimmer dashes ahead, and at the surface leaps out");
            float stag = MountPhysics.rideSpeed(Fauna3Kinds.Style.GROUND, 0.3, 1.0F, false), steed = MountPhysics.rideSpeed(Fauna3Kinds.Style.SWIM, 1.3, 1.0F, true);
            h.assertTrue(stag >= 0.25 && stag <= 0.35 && steed > 0.03 && steed < 0.05, "ridden speeds: stag " + stag + ", steed in water " + steed);
            h.assertTrue(MountPhysics.rideSpeed(Fauna3Kinds.Style.SWIM, 1.3, 1.0F, false) < steed, "a steed is slow ashore");
            h.succeed();
        });

        AwakenTests.register("fauna_mount_saves", 20, h -> {
            UUID owner = UUID.randomUUID();
            WildMount.Bond b = new WildMount.Bond(WildMount.SCHEMA, Optional.of(owner), Optional.of(owner), 3, WildMount.B_TRUSTED | WildMount.B_BRIDLED,
                    Optional.of(new BlockPos(1, 64, 2)));
            var json = WildMount.Bond.CODEC.encodeStart(JsonOps.INSTANCE, b).getOrThrow();
            WildMount.Bond back = WildMount.Bond.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
            h.assertTrue(back.equals(b), "the mount's bond round-trips: " + back);
            h.assertTrue(json.getAsJsonObject().get("schema").getAsInt() == WildMount.SCHEMA, "versioned");
            MountBond pb = MountBond.of(UUID.randomUUID(), "frost_stag", new BlockPos(5, 70, -3), "minecraft:overworld", true);
            var pj = MountBond.MAP_CODEC.codec().encodeStart(JsonOps.INSTANCE, pb).getOrThrow();
            h.assertTrue(MountBond.MAP_CODEC.codec().parse(JsonOps.INSTANCE, pj).getOrThrow().equals(pb), "the player's bond round-trips");
            h.assertTrue(MountBond.MAP_CODEC.codec().parse(JsonOps.INSTANCE, new com.google.gson.JsonObject()).getOrThrow().equals(MountBond.EMPTY), "an old save without a bond loads empty");
            h.succeed();
        });

        // The trust ritual, the bridle, the rider in control, the whistle and the saved mount, with a real player.
        AwakenTests.registerIsolated("fauna_mount_taming", 800, h -> {
            ServerLevel level = h.getLevel();
            BlockPos centre = h.absolutePos(new BlockPos(0, UP, 0));
            for (int dx = -14; dx <= 14; dx++) for (int dz = -14; dz <= 14; dz++) {
                level.setBlock(centre.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int dy = 0; dy < 4; dy++) level.setBlock(centre.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
            }
            ServerPlayer p = FaunaTests.realPlayer(h, new BlockPos(0, UP, 0));
            FaunaTests.awaken(p);
            List<AwakenMob> made = new ArrayList<>();
            WildMount stag = (WildMount) Fauna3Tests.spawn(level, "frost_stag", Vec3.atBottomCenterOf(centre.offset(2, 0, 0)), made);
            stag.setNoAi(true);
            Fauna3Kinds.Kind kind = stag.kind();
            h.assertTrue(kind != null && kind.trust() == 3 && kind.favourite(new ItemStack(Items.SWEET_BERRIES)), "the stag is a mount: sweet berries, three times");
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SWEET_BERRIES, 16));
            h.startSequence()
                    .thenExecute(() -> {
                        // below level 11: it shies away and keeps its distance (nothing eaten)
                        p.setShiftKeyDown(true);
                        stag.mobInteract(p, InteractionHand.MAIN_HAND);
                        h.assertTrue(stag.trust() == 0 && p.getMainHandItem().getCount() == 16, "a Hunter below level 11 can't win its trust");
                        while (Progression.profile(p).level() < Mounts.MIN_LEVEL) Progression.grantLevels(p, 1.0, Progression.XpSource.COMMAND);
                        // standing up: it won't take food from a towering stranger
                        p.setShiftKeyDown(false);
                        stag.mobInteract(p, InteractionHand.MAIN_HAND);
                        h.assertTrue(stag.trust() == 0, "only from a sneaking hand");
                        p.setShiftKeyDown(true);
                        stag.mobInteract(p, InteractionHand.MAIN_HAND);
                        h.assertTrue(stag.trust() == 1 && p.getMainHandItem().getCount() == 15, "one feeding counts and eats one berry");
                        stag.mobInteract(p, InteractionHand.MAIN_HAND);
                        h.assertTrue(stag.trust() == 1, "it needs a moment to eat");
                        h.assertTrue(Progression.profile(p).counter("awaken:mount_met") == 1, "the level road knows you have met a mount");
                    })
                    .thenIdle(WildMount.FEED_GAP + 1)
                    .thenExecute(() -> stag.mobInteract(p, InteractionHand.MAIN_HAND))
                    .thenIdle(WildMount.FEED_GAP + 1)
                    .thenExecute(() -> {
                        stag.mobInteract(p, InteractionHand.MAIN_HAND);
                        h.assertTrue(stag.trust() == 3 && stag.trusted() && stag.ownedBy(p), "three feedings: it trusts you");
                        h.assertTrue(stag.isPersistenceRequired() && !stag.removeWhenFarAway(10000.0), "a trusted mount never despawns");
                        h.assertTrue(Progression.profile(p).counter("awaken:mounts_tamed") == 1, "counted (the level road's goal)");
                        h.assertTrue(p.getData(Mounts.BOND.get()).is(stag.getUUID()), "it is your mount now (the whistle calls it)");
                        stag.setTarget(p);
                        h.assertTrue(stag.getTarget() == null, "it never turns on its owner");
                        // the bridle
                        p.setShiftKeyDown(false);
                        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                        stag.mobInteract(p, InteractionHand.MAIN_HAND);
                        h.assertTrue(!p.isPassenger(), "no riding without a bridle");
                        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FaunaItems.BRIDLE.get()));
                        stag.mobInteract(p, InteractionHand.MAIN_HAND);
                        h.assertTrue(stag.bridled() && p.getMainHandItem().isEmpty() && stag.canJump(), "the bridle goes on (and is used up)");
                        h.assertTrue(Progression.profile(p).counter("awaken:mounts_bridled") == 1, "bridled is counted");
                        // the rider in control: keys become the mount's input, a charged jump waits for the ground
                        stag.mobInteract(p, InteractionHand.MAIN_HAND);
                        h.assertTrue(p.getVehicle() == stag && stag.getControllingPassenger() == p, "right-click to ride; the rider steers");
                        h.assertTrue(Progression.profile(p).counter("awaken:mount_rides") == 1, "the ride is counted");
                        p.zza = 1.0F;
                        p.xxa = 0.0F;
                        Vec3 in = stag.riddenInputFor(p);
                        h.assertTrue(in.z == 1.0 && in.x == 0.0, "forward key, forward input: " + in);
                        h.assertTrue(stag.riddenSpeedFor(p) > 0.2F, "ridden at a gallop: " + stag.riddenSpeedFor(p));
                        stag.onPlayerJump(90);
                        h.assertTrue(stag.jumpPending() >= 0.99F, "a full charge is held for the jump");
                        stag.drive(20, -1);
                        h.assertTrue(stag.riddenInputFor(p).z == 1.0, "the autopilot can drive it");
                        p.stopRiding();
                        p.zza = 0.0F;
                        // the saved mount: owner, trust and bridle survive a save and load
                        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
                        stag.saveWithoutId(out);
                        CompoundTag tag = out.buildResult();
                        WildMount copy = (WildMount) stag.getType().create(level, EntitySpawnReason.LOAD);
                        copy.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
                        h.assertTrue(copy.ownedBy(p) && copy.trusted() && copy.bridled() && copy.trust() == 3 && copy.isPersistenceRequired(), "the bond is saved with the mount");
                        copy.discard();
                        // the whistle: from across the meadow it comes; the item then cools down
                        stag.setNoAi(false);
                        stag.teleportTo(centre.getX() + 12.5, centre.getY(), centre.getZ() + 12.5);
                        p.setShiftKeyDown(false);
                        ItemStack whistle = new ItemStack(FaunaItems.WHISTLE.get());
                        p.setItemInHand(InteractionHand.MAIN_HAND, whistle);
                        whistle.use(level, p, InteractionHand.MAIN_HAND);
                        h.assertTrue(p.getCooldowns().isOnCooldown(whistle), "the whistle has a cooldown");
                    })
                    .thenWaitUntil(() -> h.assertTrue(stag.distanceTo(p) < 6, "it comes when whistled: " + stag.distanceTo(p)))
                    .thenExecute(() -> {
                        Mounts.Result far = Mounts.whistle(p, false);
                        h.assertTrue(far.ok(), "near: still answers (" + far.line() + ")");
                        // why not: home without a Hearth, a mount that is gone, no mount at all
                        Mounts.Result home = Mounts.whistle(p, true);
                        h.assertTrue(home.answer() == Mounts.Answer.NO_HEARTH || home.answer() == Mounts.Answer.SENT_HOME, "sent home or told there's no Hearth: " + home.line());
                        p.setData(Mounts.BOND.get(), MountBond.of(UUID.randomUUID(), "dune_ray", centre.offset(900, 0, 0), level.dimension().identifier().toString(), false));
                        Mounts.Result gone = Mounts.whistle(p, false);
                        h.assertTrue(gone.answer() == Mounts.Answer.OUT_OF_EARSHOT && gone.line().contains("900 m"), "out of earshot, last seen where: " + gone.line());
                        p.setData(Mounts.BOND.get(), MountBond.EMPTY);
                        Mounts.Result none = Mounts.whistle(p, false);
                        h.assertTrue(none.answer() == Mounts.Answer.NONE && !none.ok(), "no mount: it says how to get one");
                        // a ray glides; a steed swims
                        WildMount ray = (WildMount) Fauna3Tests.spawn(level, "dune_ray", Vec3.atBottomCenterOf(centre.offset(-3, 1, 0)), made);
                        h.assertTrue(ray.kind().style() == Fauna3Kinds.Style.GLIDE && ray.kind().trust() == 4, "the ray glides; four cactus flowers");
                        ray.tameTo(p, true);
                        h.assertTrue(ray.bridled() && ray.ownedBy(p) && p.getData(Mounts.BOND.get()).is(ray.getUUID()), "tamed by command (autopilot) and bonded");
                    })
                    .thenExecute(() -> {
                        made.forEach(AwakenMob::discard);
                        level.getServer().getPlayerList().remove(p);
                    })
                    .thenSucceed();
        });

        // A Tidesteed carries you under water: it swims where you look, and its rider breathes.
        AwakenTests.registerIsolated("fauna_mount_tidesteed", 300, h -> {
            ServerLevel level = h.getLevel();
            BlockPos centre = h.absolutePos(new BlockPos(0, UP + 2, 0));
            Fauna3Commands.buildTank(level, centre, 6, 6, 6);
            ServerPlayer p = FaunaTests.realPlayer(h, new BlockPos(0, UP + 5, 0));
            FaunaTests.awaken(p);
            List<AwakenMob> made = new ArrayList<>();
            h.startSequence()
                    .thenExecute(() -> {
                        WildMount steed = (WildMount) Fauna3Tests.spawn(level, "tidesteed", Vec3.atBottomCenterOf(centre.above(2)), made);
                        h.assertTrue(steed.isInWater(), "in the water");
                        steed.tameTo(p, true);
                        p.teleportTo(level, steed.getX(), steed.getY() + 1, steed.getZ(), Set.of(), 0, 0, true);
                        h.assertTrue(p.startRiding(steed), "climbs on");
                        p.zza = 1.0F;
                        p.setXRot(40.0F);
                        Vec3 in = steed.riddenInputFor(p);
                        h.assertTrue(in.y < -0.5 && in.z > 0.5, "looking down, it dives: " + in);
                        p.setXRot(0.0F);
                        p.setAirSupply(5);
                    })
                    .thenIdle(45)
                    .thenExecute(() -> {
                        h.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.BREATH_OF_THE_NAUTILUS), "its rider breathes under water");
                        p.stopRiding();
                        p.zza = 0.0F;
                        made.forEach(AwakenMob::discard);
                        WildTests.wipe(level, centre.offset(-8, -1, -8), centre.offset(8, 9, 8));
                        level.getServer().getPlayerList().remove(p);
                    })
                    .thenSucceed();
        });
    }
}
