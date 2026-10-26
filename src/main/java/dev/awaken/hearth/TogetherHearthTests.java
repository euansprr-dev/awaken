package dev.awaken.hearth;

import dev.awaken.above.Above;
import dev.awaken.above.AboveBlocks;
import dev.awaken.above.MachineType;
import dev.awaken.above.machine.MachineBlockEntity;
import dev.awaken.above.machine.Machines;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.api.PartyHooks;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.powers.Constructs;
import dev.awaken.progression.Progression;
import dev.awaken.veil.RevealFilter;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Optional;

/**
 * GameTests for the shared Hearth (SPEC "Together"): an owner, a member and a stranger; inviting, what a member may
 * do there, leaving, the household's machines, a member founding a sister home from tier III, a kill at home shared
 * with the household, and what a server stop forgets.
 */
final class TogetherHearthTests {
    private TogetherHearthTests() {}

    /** Survival players: creative ones may use anyone's machines. */
    private static ServerPlayer player(GameTestHelper h, BlockPos rel) {
        ServerPlayer p = AwakenTests.survivalPlayer(h, "hearth-" + Integer.toHexString(rel.hashCode() & 0xffff), rel).player();
        Progression.awaken(p);
        return p;
    }

    /** A Hearth for {@code owner} with its Anchor at {@code rel} (the block is placed so upkeep keeps it). */
    private static HearthRecord hearth(GameTestHelper h, ServerPlayer owner, BlockPos rel) {
        BlockPos at = h.absolutePos(rel);
        h.getLevel().setBlock(at, HearthContent.HEARTH_ANCHOR.get().defaultBlockState(), 3);
        HearthRecord r = new HearthRecord(owner.getUUID(), owner.getGameProfile().name(), at, HearthService.OVERWORLD);
        HearthData.get(h.getLevel().getServer()).put(r);
        return r;
    }

    /**
     * A spot about (dx, dz) from {@code from} where an Anchor could take root for someone with no Hearth and no household
     * (so only the household's own rule decides there for a member): the first of a few tries, stepping on when a city,
     * a settlement's ground or another Hearth refuses it.
     */
    private static BlockPos open(GameTestHelper h, ServerPlayer probe, BlockPos from, int dx, int dz) {
        for (int k = 0; k < 16; k++) {
            BlockPos p = from.offset(dx + Integer.signum(dx) * k * 41, 0, dz + k * 67);
            h.getLevel().getChunkAt(p);
            if (HearthService.canPlaceAnchor(probe, h.getLevel(), p)) return p;
        }
        BlockPos p = from.offset(dx, 0, dz);
        h.getLevel().getChunkAt(p);
        return p;
    }

    /** Levels and the fraction of the next one: rises with any XP, across a level-up too. */
    private static double progress(ServerPlayer p) {
        var prof = Progression.profile(p);
        return prof.level() + prof.xp() / dev.awaken.progression.Curve.xpToNext(prof.level());
    }

    private static void cleanup(GameTestHelper h, HearthRecord[] hearths, ServerPlayer... ps) {
        HearthData d = HearthData.get(h.getLevel().getServer());
        for (HearthRecord r : hearths) {
            if (r == null) continue;
            if (d.of(r.owner).orElse(null) == r) HearthService.onAnchorRemoved(h.getLevel(), r.pos); // the town leaves too
            d.remove(r.owner);
            // isolated tests share one spot: leave no Anchor behind for the next test's ground checks
            if (h.getLevel().getBlockState(r.pos).is(HearthContent.HEARTH_ANCHOR.get())) h.getLevel().removeBlock(r.pos, false);
        }
        for (ServerPlayer p : ps) {
            if (p == null) continue;
            d.takeKept(p.getUUID());
            h.getLevel().getServer().getPlayerList().remove(p);
        }
    }

    static void register() {
        AwakenTests.registerIsolated("together_hearth_members_share", 100, h -> {
            ServerLevel level = h.getLevel();
            ServerPlayer owner = null, friend = null, stranger = null;
            HearthRecord[] made = new HearthRecord[1];
            BlockPos vat = null;
            try {
                owner = player(h, new BlockPos(1, 1, 1));
                friend = player(h, new BlockPos(3, 1, 1));
                stranger = player(h, new BlockPos(5, 1, 1));
                HearthRecord home = hearth(h, owner, new BlockPos(2, 1, 4));
                made[0] = home;
                BlockPos inside = home.pos.offset(6, 1, 6);

                h.assertFalse(Constructs.mayChange(friend, inside), "before joining, a friend's powers can't build there");
                h.assertTrue(Household.invite(owner, friend).isEmpty(), "the owner invites");
                h.assertTrue(Household.invited(level.getServer(), friend.getUUID(), owner.getUUID()), "the invitation is open");
                h.assertTrue(Offers.accept(friend, "invite:" + owner.getUUID()), "the friend answers Y");
                h.assertTrue(home.belongs(friend.getUUID()) && !home.belongs(stranger.getUUID()), "the friend is a member, the stranger isn't");

                // the public API other modules use
                h.assertTrue(Hearth.hearthsOf(friend).size() == 1 && Hearth.hearthsOf(friend).get(0).owner().equals(owner.getUUID()),
                        "Hearth.hearthsOf lists the shared Hearth");
                h.assertTrue(Hearth.isMember(friend, home.pos) && Hearth.isMember(level.getServer(), friend.getUUID(), owner.getUUID()),
                        "Hearth.isMember, by Anchor and by owner");
                h.assertFalse(Hearth.isMember(stranger, home.pos), "not for the stranger");
                h.assertTrue(HearthService.homeOf(friend).orElse(null) == home, "it is the friend's home now");
                h.assertTrue(HearthHooks.belongs(friend, home.pos) && HearthHooks.shareHearth(owner, friend.getUUID()), "the bridges agree");

                // what a member may do there
                h.assertTrue(Constructs.mayChange(friend, inside), "a member builds there with powers");
                h.assertFalse(Constructs.mayChange(stranger, inside), "a stranger doesn't");
                Vec3 centre = Vec3.atCenterOf(inside);
                h.assertTrue(RevealFilter.build(level, friend, centre, 16).allowed(inside), "a member's spirit sight sees there");
                h.assertFalse(RevealFilter.build(level, stranger, centre, 16).allowed(inside), "a stranger's doesn't");
                h.assertTrue(PartyHooks.sameParty(owner, friend) && PartyHooks.members(owner).contains(friend.getUUID()),
                        "sharing a Hearth makes a party");
                h.assertFalse(PartyHooks.sameParty(owner, stranger), "the stranger is not in it");

                // the household's machines
                vat = home.pos.offset(-2, 0, -2);
                level.setBlock(vat, AboveBlocks.machine(MachineType.CLONE_VAT).defaultBlockState(), 3);
                MachineBlockEntity be = (MachineBlockEntity) level.getBlockEntity(vat);
                h.assertTrue(be != null, "a Clone Vat stands at home");
                be.setOwner(owner.getUUID());
                h.assertTrue(Machines.mayUse(owner, be) && Machines.mayUse(friend, be), "it answers to the owner and the member");
                h.assertFalse(Machines.mayUse(stranger, be), "not to a stranger");
                Machines.action(friend, vat, "bind", 0);
                h.assertTrue(Above.data(friend).cloneVat().isPresent() && owner.getUUID().equals(be.owner()),
                        "a member binds the household's Vat for their own clone, and it stays the owner's");
                Machines.action(stranger, vat, "bind", 0);
                h.assertTrue(Above.data(stranger).cloneVat().isEmpty(), "a stranger can't bind it");

                // the Anchor: a member uses it as home, a stranger only asks
                HearthService.onAnchorUsed(friend, level, home.pos);
                h.assertTrue(HearthService.player(friend).respawnAtHearth && owner.getUUID().equals(HearthService.player(friend).respawnHearth),
                        "a member who uses the Anchor wakes there");
                HearthService.onAnchorUsed(stranger, level, home.pos);
                h.assertFalse(home.belongs(stranger.getUUID()), "a stranger using it only asks");
                h.assertTrue(Offers.accept(owner, "letin:" + stranger.getUUID()), "the owner lets them in with Y");
                h.assertTrue(home.belongs(stranger.getUUID()), "and they share it");

                // leaving
                h.assertTrue(Household.leave(level.getServer(), home, friend.getUUID(), false), "a member can leave");
                h.assertTrue(HearthService.homeOf(friend).isEmpty() && !Constructs.mayChange(friend, inside), "and has no home there any more");
                h.assertFalse(HearthService.player(friend).respawnAtHearth, "nor wakes there");
            } finally {
                if (vat != null) level.removeBlock(vat, false);
                cleanup(h, made, owner, friend, stranger);
            }
            h.succeed();
        });

        AwakenTests.registerIsolated("together_hearth_sister_home", 100, h -> {
            ServerLevel level = h.getLevel();
            ServerPlayer owner = null, a = null, b = null, probe = null;
            HearthRecord[] made = new HearthRecord[3];
            try {
                owner = player(h, new BlockPos(1, 1, 1));
                a = player(h, new BlockPos(3, 1, 1));
                b = player(h, new BlockPos(5, 1, 1));
                probe = player(h, new BlockPos(7, 1, 1));
                HearthRecord home = hearth(h, owner, new BlockPos(2, 1, 4));
                made[0] = home;
                Household.join(level.getServer(), home, a);
                Household.join(level.getServer(), home, b);
                // ground nothing but the household's rule could refuse (no city, settlement or other Hearth there: the
                // test's spot moves run to run, and a planned village's ground 200 off refused it now and then)
                BlockPos far = open(h, probe, home.pos, 200, 0);
                h.assertFalse(HearthService.canPlaceAnchor(a, level, far), "at tier I a member shares; no second home yet");
                h.assertTrue(Household.sisterSlots(home) == 0, "no sister slots below tier III");
                home.tier = 3;
                h.assertTrue(Household.sisterSlots(home) == 1, "one at tier III");
                h.assertTrue(HearthService.canPlaceAnchor(a, level, far), "now a member's own Anchor may take root");
                level.setBlock(far, HearthContent.HEARTH_ANCHOR.get().defaultBlockState(), 3);
                HearthService.onAnchorPlaced(a, level, far);
                HearthRecord sister = HearthData.get(level.getServer()).of(a.getUUID()).orElse(null);
                made[1] = sister;
                h.assertTrue(sister != null && owner.getUUID().equals(sister.foundedFrom), "a sister home of the first Hearth");
                h.assertTrue(sister.belongs(owner.getUUID()) && sister.belongs(b.getUUID()), "the whole household shares it");
                h.assertTrue(home.belongs(a.getUUID()), "its founder still shares the first");
                BlockPos farther = open(h, probe, home.pos, -200, 0);
                h.assertFalse(HearthService.canPlaceAnchor(b, level, farther), "tier III allows one sister: the slot is taken");
                home.tier = 4;
                h.assertTrue(HearthService.canPlaceAnchor(b, level, farther), "tier IV allows another");
                h.assertTrue(Hearth.hearthsOf(level.getServer(), b.getUUID()).size() == 2, "the member belongs to both homes");

                // the owner moves home: the household is kept and comes back where the Anchor takes root again
                HearthService.onAnchorRemoved(level, home.pos);
                h.assertTrue(HearthData.get(level.getServer()).of(owner.getUUID()).isEmpty(), "the first Anchor went cold");
                BlockPos again = home.pos.offset(0, 0, 40);
                level.setBlock(again, HearthContent.HEARTH_ANCHOR.get().defaultBlockState(), 3);
                HearthService.onAnchorPlaced(owner, level, again);
                HearthRecord back = HearthData.get(level.getServer()).of(owner.getUUID()).orElse(null);
                made[2] = back;
                h.assertTrue(back != null && back.belongs(a.getUUID()) && back.belongs(b.getUUID()), "the household shares the new home");
            } finally {
                cleanup(h, made, owner, a, b, probe);
            }
            h.succeed();
        });

        // A kill at home shares with the household nearby: each their own home XP and their own chance to bind its Echo.
        AwakenTests.registerIsolated("together_rewards_shared_outside_gates", 100, h -> {
            ServerLevel level = h.getLevel();
            ServerPlayer owner = null, friend = null, stranger = null;
            HearthRecord[] made = new HearthRecord[1];
            LivingEntity elite = null;
            try {
                owner = player(h, new BlockPos(1, 1, 1));
                friend = player(h, new BlockPos(3, 1, 1));
                stranger = player(h, new BlockPos(5, 1, 1));
                HearthRecord home = hearth(h, owner, new BlockPos(2, 1, 6));
                made[0] = home;
                Household.join(level.getServer(), home, friend);
                elite = Mobs.spawn(level, Bestiary.get("toll_ghoul"), h.absoluteVec(new Vec3(3.5, 1, 3.5)), 1,
                        new MobRole(MobRole.Role.ELITE, 1, "", Optional.empty(), ""), EnumSet.noneOf(dev.awaken.core.Forces.Tag.class));
                h.assertTrue(elite != null, "an elite takes form");
                double xo = progress(owner), xf = progress(friend), xs = progress(stranger);
                elite.hurtServer(level, level.damageSources().playerAttack(owner), 100000F);
                h.assertFalse(elite.isAlive(), "the owner fells it");
                h.assertTrue(progress(owner) > xo, "the killer's home XP");
                h.assertTrue(progress(friend) > xf, "the household member beside them shares it");
                h.assertTrue(progress(stranger) == xs, "a stranger standing there doesn't");
                h.assertTrue(HearthService.player(owner).tips.contains("linger") && HearthService.player(friend).tips.contains("linger"),
                        "its Echo lingers for the killer and for the member, each to bind themselves");
                h.assertFalse(HearthService.player(stranger).tips.contains("linger"), "not for the stranger");
            } finally {
                if (elite != null) elite.discard();
                cleanup(h, made, owner, friend, stranger);
            }
            h.succeed();
        });

        // Server stop: invitations, askings at an Anchor and open offers are forgotten with the world (and its breaks
        // and Echo duty, cleared by the same call).
        AwakenTests.registerIsolated("together_hearth_stop_forgets", 60, h -> {
            ServerLevel level = h.getLevel();
            ServerPlayer owner = null, friend = null, stranger = null;
            HearthRecord[] made = new HearthRecord[1];
            try {
                owner = player(h, new BlockPos(1, 1, 1));
                friend = player(h, new BlockPos(3, 1, 1));
                stranger = player(h, new BlockPos(5, 1, 1));
                HearthRecord home = hearth(h, owner, new BlockPos(2, 1, 4));
                made[0] = home;
                h.assertTrue(Household.invite(owner, friend).isEmpty(), "the owner invites a friend");
                HearthService.onAnchorUsed(stranger, level, home.pos); // someone else asks at the Anchor
                var server = level.getServer();
                h.assertTrue(Household.invited(server, friend.getUUID(), owner.getUUID()) && Household.asked(stranger.getUUID()),
                        "an invitation and an asking are open");
                h.assertTrue(Offers.open(friend.getUUID(), "invite:" + owner.getUUID()) && Offers.open(owner.getUUID(), "letin:" + stranger.getUUID()),
                        "with a choice card for each");
                HearthService.forgetPeople(); // what the server-stop listener runs
                h.assertFalse(Household.invited(server, friend.getUUID(), owner.getUUID()) || Household.asked(stranger.getUUID()),
                        "the next world starts with no invitations or askings");
                h.assertFalse(Offers.open(friend.getUUID(), "invite:" + owner.getUUID()) || Offers.open(owner.getUUID(), "letin:" + stranger.getUUID()),
                        "and no open offers");
                h.assertFalse(Offers.accept(friend, "invite:" + owner.getUUID()), "an old card's Y does nothing");
                h.assertFalse(home.belongs(friend.getUUID()), "so nobody joined");
            } finally {
                cleanup(h, made, owner, friend, stranger);
            }
            h.succeed();
        });
    }
}
