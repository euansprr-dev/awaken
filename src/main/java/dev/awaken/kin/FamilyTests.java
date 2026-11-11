package dev.awaken.kin;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.hearth.Hearth;
import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.HearthData;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.UUID;

/** GameTests for the Hearth's family (SPEC §69): one per mechanism. CI runs them. */
final class FamilyTests {
    private FamilyTests() {}

    static void register() {
        AwakenTests.register("family_never_lost", 100, FamilyTests::neverLost);
        AwakenTests.register("family_ask_and_stay", 60, FamilyTests::askAndStay);
    }

    private static BlockPos home(GameTestHelper h, UUID owner) {
        ServerLevel level = h.getLevel();
        // the test space has no floor of its own
        for (int x = 0; x <= 6; x++)
            for (int z = 0; z <= 6; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        BlockPos anchor = h.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(anchor, HearthContent.HEARTH_ANCHOR.get().defaultBlockState(), 3);
        HearthService.data(level.getServer()).put(new HearthRecord(owner, "test", anchor, HearthService.OVERWORLD));
        return anchor;
    }

    private static void forget(ServerLevel level, UUID owner) {
        FamilyWorld world = FamilyWorld.get(level.getServer());
        for (FamilyPet b : FamilyService.bodies(level, owner)) b.discard();
        world.homes.remove(owner);
        world.asked.remove(owner);
        world.hinted.remove(owner);
        HearthService.data(level.getServer()).remove(owner);
    }

    /**
     * Neither can be hurt, targeted or lost, and neither can be two: nothing damages one, a zombie cannot take one as its
     * target, a second body the record does not name removes itself, and a body that is gone is made again from the
     * record (not at once: it may only be unloaded). They are family, not kin: no nook is taken.
     */
    static void neverLost(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        UUID owner = UUID.randomUUID();
        BlockPos anchor = home(h, owner);
        FamilyWorld world = FamilyWorld.get(level.getServer());
        FamilyPet aya = FamilyService.spawn(level, owner, FamilyKind.AYA, h.absolutePos(new BlockPos(3, 2, 3)), 0F);
        h.assertTrue(aya != null, "Aya is made");
        FamilyWorld.Home home = world.of(owner);
        h.assertTrue(home != null && home.aya.arrived && home.aya.id.isPresent() && home.aya.id.get().equals(aya.getUUID()), "the record names her body");
        h.assertTrue(aya.getCustomName() != null && aya.getCustomName().getString().equals("Aya"), "she comes with her name");
        h.assertTrue(!aya.hurtServer(level, level.damageSources().lava(), 100F) && !aya.hurtServer(level, level.damageSources().magic(), 100F)
                && aya.getHealth() == aya.getMaxHealth(), "lava and magic do nothing to her");
        var zombie = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(5, 2, 1));
        zombie.setTarget(aya);
        h.assertTrue(zombie.getTarget() == null, "a zombie cannot take her as its target");
        zombie.discard();
        h.assertTrue(KinWorld.get(level.getServer()).at(anchor).isEmpty(), "she takes no nook");
        FamilyPet twin = FamilyContent.AYA.get().create(level, EntitySpawnReason.COMMAND);
        h.assertTrue(twin != null, "a second body can be put in the world");
        twin.owner = owner;
        twin.snapTo(aya.getX() + 1.0, aya.getY(), aya.getZ(), 0F, 0F);
        level.addFreshEntity(twin);
        h.runAfterDelay(12, () -> {
            h.assertTrue(twin.isRemoved() && !aya.isRemoved(), "the body the record does not name removes itself; hers stays");
            aya.discard();
            Hearth.HearthInfo info = FamilyService.hearth(level.getServer(), owner);
            h.assertTrue(info != null, "the home stands");
            long now = level.getGameTime();
            FamilyService.keep(level, world, info, home, FamilyKind.AYA, now);
            h.assertTrue(FamilyService.body(level, home.aya) == null, "not made again at once: a body may only be unloaded");
            FamilyService.keep(level, world, info, home, FamilyKind.AYA, now + FamilyService.LOST_AFTER);
            FamilyPet again = FamilyService.body(level, home.aya);
            h.assertTrue(again != null && again != aya && again.owner != null && again.owner.equals(owner), "gone a while, she is made again from the record");
            forget(level, owner);
            h.succeed();
        });
    }

    /**
     * Sneak and use asks one along and tells it to stay: at home that is home, away from home it waits where it is. It
     * answers to one person at a time and only to its household; a plain use pets it; its plate and its Hearth page card
     * say whose it is and what it is doing; only the Hearth's owner renames it; your own powers count it as yours; and a
     * zombie hunting you is a fight for it to keep out of.
     */
    static void askAndStay(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = h.makeMockServerPlayerInLevel(), q = h.makeMockServerPlayerInLevel();
        UUID owner = p.getUUID();
        BlockPos anchor = home(h, owner);
        p.snapTo(anchor.getX() + 2.5, anchor.getY(), anchor.getZ() + 2.5, 0F, 0F);
        q.snapTo(anchor.getX() + 3.5, anchor.getY(), anchor.getZ() + 2.5, 0F, 0F);
        FamilyWorld world = FamilyWorld.get(level.getServer());
        FamilyPet dubu = FamilyService.spawn(level, owner, FamilyKind.DUBU, h.absolutePos(new BlockPos(3, 2, 4)), 0F);
        h.assertTrue(dubu != null, "Dubu is made");
        FamilyWorld.One one = world.of(owner).dubu;
        world.of(owner).stage = 3;
        KinNet.Bond home = FamilyService.bond(p, dubu);
        h.assertTrue(home.who().equals("yours") && home.tone() == KinPlates.WARM && home.note().contains("ask him along"), "his plate at home: " + home);
        h.assertTrue(KinPlates.wears(dubu) && KinPlates.read(q, dubu).tone() == KinPlates.OTHER, "he wears the kin plate; to a stranger he is someone else's");
        h.assertTrue(dev.awaken.powers.Combat.isAlly(p, dubu), "your powers count him as yours");
        // a stranger cannot take him
        FamilyService.used(q, dubu, true);
        h.assertTrue(one.state == FamilyWorld.HOME, "a stranger's ask changes nothing");
        // asked along; told to stay at home
        FamilyService.used(p, dubu, true);
        h.assertTrue(one.state == FamilyWorld.WITH && one.with.isPresent() && one.with.get().equals(owner) && world.asked.contains(owner), "asked along");
        h.assertTrue(FamilyService.bond(p, dubu).note().startsWith("Coming with you."), "his plate says so: " + FamilyService.bond(p, dubu).note());
        FamilyService.used(p, dubu, true);
        h.assertTrue(one.state == FamilyWorld.HOME && one.with.isEmpty(), "told to stay at home: he is home");
        // away from home he waits where he stands
        FamilyService.used(p, dubu, true);
        double x = dubu.getX(), y = dubu.getY(), z = dubu.getZ();
        dubu.snapTo(x + 400.0, y, z, 0F, 0F);
        FamilyService.used(p, dubu, true);
        boolean waiting = one.state == FamilyWorld.WAITING && one.waitAt.isPresent() && one.waitAt.get().equals(dubu.blockPosition());
        dubu.snapTo(x, y, z, 0F, 0F);
        h.assertTrue(waiting, "told to stay away from home: he waits where he is");
        FamilyService.sendHome(level, dubu, one);
        h.assertTrue(one.state == FamilyWorld.HOME && one.waitAt.isEmpty(), "sent home: home in the record");
        // a plain use pets him
        FamilyService.used(p, dubu, false);
        h.assertTrue(dubu.act() == FamilyPet.NUDGE, "a use pets him: he says hello");
        // the Hearth page: his card, first, with his name; only the owner renames him
        Hearth.HearthInfo info = FamilyService.hearth(level.getServer(), owner);
        h.assertTrue(info != null, "the home stands");
        List<List<String>> rows = FamilyService.rows(p, level, info);
        h.assertTrue(rows.size() == 1 && rows.getFirst().get(0).equals("family_dubu") && rows.getFirst().get(1).equals("Dog") && rows.getFirst().get(6).equals("Dubu"),
                "his card: " + rows);
        h.assertTrue(!FamilyService.name(q, dubu, "Rex") && FamilyService.name(p, dubu, "Tofu") && one.name.equals("Tofu")
                && dubu.getCustomName() != null && dubu.getCustomName().getString().equals("Tofu"), "only the Hearth's owner renames him");
        h.assertTrue(FamilyService.name(p, dubu, "") && one.name.equals("Dubu"), "an empty name gives his own back");
        // a zombie hunting you is a fight. Hunted is a real survival player: a mock one is always creative, and no mob
        // takes a creative player as its target (Mob.setTarget drops it), so with p this asked about a fight that never
        // was. (The fight check answers from memory for half a second, so it is asked once, after the zombie hunts.)
        AwakenTests.Live live = AwakenTests.survivalPlayer(h, "family-fight", new BlockPos(3, 2, 3));
        var zombie = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(5, 2, 5));
        try {
            ServerPlayer hunted = live.player();
            zombie.setTarget(hunted);
            h.assertTrue(zombie.getTarget() == hunted, "the test's own ground: a zombie hunts a survival player");
            h.assertTrue(FamilyService.threat(level, hunted) != null, "a zombie hunting you is a fight he keeps out of");
        } finally {
            zombie.discard();
            live.leave();
        }
        forget(level, owner);
        level.getServer().getPlayerList().remove(p);
        level.getServer().getPlayerList().remove(q);
        h.succeed();
    }
}
