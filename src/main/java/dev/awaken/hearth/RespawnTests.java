package dev.awaken.hearth;

import com.mojang.authlib.GameProfile;
import dev.awaken.Awaken;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.hearth.player.HearthPlayer;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;

/**
 * GameTests for waking at your Hearth (SPEC §28.10), one per way it was lost: falling twice (the old body's bed, copied
 * onto the new body at every respawn, read as a bed chosen elsewhere and switched home off: Euan woke at world spawn,
 * 27 Oct), a fall in a gate after quitting and coming back, an inn room lent for one fall and given back, and a bed
 * chosen elsewhere (the one thing that moves it, and it says so). Each fall is the server's own respawn.
 */
final class RespawnTests {
    private RespawnTests() {}

    private static final ResourceKey<Level> GATES = ResourceKey.create(Registries.DIMENSION, Awaken.id("gates"));

    /** A Hearthfire this player set down on the test's own ground (as the Ember does: they wake there from now). */
    private static HearthRecord fire(GameTestHelper h, ServerPlayer sp) {
        ServerLevel level = h.getLevel();
        BlockPos origin = h.absolutePos(BlockPos.ZERO);
        BlockPos ground = new BlockPos((origin.getX() & ~15) + 8, origin.getY() + 100, (origin.getZ() & ~15) + 8);
        HomeTests.ownGround(level, ground);
        level.setBlock(ground, Blocks.STONE.defaultBlockState(), 3);
        return Embers.found(sp, level, ground.above());
    }

    /** A fall: the server's own respawn, which builds a new body and copies the old one's bed onto it. */
    private static ServerPlayer fall(ServerPlayer sp) {
        return sp.level().getServer().getPlayerList().respawn(sp, false, Entity.RemovalReason.KILLED);
    }

    private static boolean byFire(ServerPlayer p, HearthRecord r) {
        return p.level().dimension() == Level.OVERWORLD && p.blockPosition().closerThan(r.pos, 4);
    }

    private static String where(ServerPlayer p) {
        return p.level().dimension().identifier() + " " + p.blockPosition().toShortString();
    }

    /** A bed on a stone floor with room round it, {@code east} blocks east of the fire: its head. */
    private static BlockPos bed(ServerLevel level, BlockPos fire, int east) {
        BlockPos foot = fire.offset(east, 0, 0);
        level.getChunkAt(foot);
        AwakenTests.clearToSky(level, foot.offset(-3, 0, -3), foot.offset(3, 0, 3));
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) level.setBlock(foot.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
        }
        BlockState b = Blocks.BED.pick(DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        level.setBlock(foot, b.setValue(BedBlock.PART, BedPart.FOOT), 3);
        level.setBlock(foot.north(), b.setValue(BedBlock.PART, BedPart.HEAD), 3);
        return foot.north();
    }

    /** The respawn point a bed gives (as sleeping in it, or an inn room, sets it). */
    private static ServerPlayer.RespawnConfig spawnAt(ServerLevel level, BlockPos bed) {
        return new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(level.dimension(), bed, 0F, 0F), false);
    }

    /** Coming back after quitting: a new body that loads what was saved on the way out, as the server's login does. */
    private static ServerPlayer rejoin(GameTestHelper h, GameProfile profile) {
        MinecraftServer server = h.getLevel().getServer();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(server, h.getLevel(), profile, cookie.clientInformation());
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(p.problemPath(), Awaken.LOG)) {
            server.getPlayerList().loadPlayerData(new NameAndId(profile))
                    .map(tag -> TagValueInput.create(reporter, server.registryAccess(), tag)).ifPresent(p::load);
        }
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, p, cookie);
        return p;
    }

    private static void cleanup(GameTestHelper h, HearthRecord r, ServerPlayer p) {
        ServerLevel level = h.getLevel();
        if (r != null) {
            dev.awaken.hearth.town.Town.dissolve(level, r);
            HearthData.get(level.getServer()).remove(r.owner);
            level.removeBlock(r.pos, false);
            level.removeBlock(r.pos.below(), false);
        }
        if (p != null) level.getServer().getPlayerList().remove(p);
    }

    static void register() {
        AwakenTests.registerIsolated("hearth_respawn_survives_death", 200, h -> {
            // Euan's fall: a Hearthfire, no bed anywhere; the first fall woke him at home and switched it off for the next
            ServerPlayer sp = h.makeMockServerPlayerInLevel();
            HearthRecord r = null;
            try {
                r = fire(h, sp);
                h.assertTrue(sp.getRespawnConfig() == null && HearthService.player(sp).respawnAtHearth, "a fire, and no bed anywhere");
                for (int n = 1; n <= 3; n++) {
                    sp = fall(sp);
                    h.assertTrue(byFire(sp, r), "fall " + n + " wakes by the fire, not at world spawn (at " + where(sp) + ")");
                    h.assertTrue(HearthService.player(sp).respawnAtHearth, "fall " + n + " leaves home where you wake");
                }
                // even unchosen: with no other bed, home beats world spawn
                HearthService.player(sp).respawnAtHearth = false;
                sp = fall(sp);
                h.assertTrue(byFire(sp, r), "no bed elsewhere: home, never world spawn (at " + where(sp) + ")");
                h.succeed();
            } finally {
                cleanup(h, r, sp);
            }
        });

        AwakenTests.registerIsolated("hearth_respawn_gate_relog", 200, h -> {
            ServerPlayer sp = h.makeMockServerPlayerInLevel();
            HearthRecord r = null;
            try {
                r = fire(h, sp);
                // the save holds it as true (an absent field reads false, which a save of true never is)
                CompoundTag saved = (CompoundTag) HearthPlayer.MAP_CODEC.codec().encodeStart(NbtOps.INSTANCE, HearthService.player(sp)).getOrThrow();
                h.assertTrue(saved.getBooleanOr("respawn_at_hearth", false), "the save says you wake at home: " + saved);
                // quit (saved on the way out), come back (loaded as the server's login loads you)
                GameProfile profile = sp.getGameProfile();
                MinecraftServer server = h.getLevel().getServer();
                server.getPlayerList().remove(sp);
                sp = null;
                sp = rejoin(h, profile);
                h.assertTrue(HearthService.player(sp).respawnAtHearth, "after coming back you still wake at home");
                // into a gate, and a fall there (GameTests run gates in the test level: then any level not the overworld)
                ServerLevel gates = server.getLevel(GATES);
                if (gates == null) {
                    for (ServerLevel l : server.getAllLevels()) if (gates == null && l.dimension() != Level.OVERWORLD) gates = l;
                }
                h.assertTrue(gates != null, "a level other than the overworld to fall in");
                sp.teleport(new TeleportTransition(gates, new Vec3(0.5, 120, 0.5), Vec3.ZERO, 0F, 0F, TeleportTransition.DO_NOTHING));
                h.assertTrue(sp.level() == gates, "in the gate (at " + where(sp) + ")");
                sp = fall(sp);
                h.assertTrue(byFire(sp, r), "a fall in a gate after a relog wakes by the fire (at " + where(sp) + ")");
                h.assertTrue(HearthService.player(sp).respawnAtHearth, "and home stays where you wake");
                h.succeed();
            } finally {
                cleanup(h, r, sp);
            }
        });

        AwakenTests.registerIsolated("hearth_respawn_inn_keeps", 200, h -> {
            ServerPlayer sp = h.makeMockServerPlayerInLevel();
            HearthRecord r = null;
            BlockPos room = null;
            ServerLevel level = h.getLevel();
            try {
                r = fire(h, sp);
                // an inn room far off the fire's land: lent for one fall
                room = bed(level, r.pos, 60);
                HearthHooks.lendRespawn(sp, spawnAt(level, room));
                h.assertTrue(HearthService.player(sp).respawnAtHearth, "taking a room doesn't cancel home");
                sp = fall(sp);
                h.assertTrue(sp.blockPosition().closerThan(room, 3), "the room's one fall wakes you at the inn (at " + where(sp) + ")");
                // the inn gives the old point back (none): home again
                HearthHooks.returnRespawn(sp, null);
                h.assertTrue(HearthService.player(sp).respawnAtHearth, "leaving the inn keeps home");
                sp = fall(sp);
                h.assertTrue(byFire(sp, r), "the next fall wakes by the fire (at " + where(sp) + ")");
                // a room given back unused (the morning after next) leaves home too
                HearthHooks.lendRespawn(sp, spawnAt(level, room));
                HearthHooks.returnRespawn(sp, null);
                sp = fall(sp);
                h.assertTrue(byFire(sp, r) && HearthService.player(sp).respawnAtHearth, "an unused room given back: home (at " + where(sp) + ")");
                h.succeed();
            } finally {
                if (room != null) AwakenTests.clearToSky(level, room.offset(-3, -1, -2), room.offset(3, 0, 4));
                cleanup(h, r, sp);
            }
        });

        AwakenTests.registerIsolated("hearth_respawn_bed_elsewhere", 200, h -> {
            ServerPlayer sp = h.makeMockServerPlayerInLevel();
            HearthRecord r = null;
            BlockPos home = null, away = null;
            ServerLevel level = h.getLevel();
            try {
                r = fire(h, sp);
                HearthPlayer hp = HearthService.player(sp);
                // (choices first: a respawn's own tick ignores spawn changes, and a test runs in one tick)
                sp.setRespawnPosition(null, false);
                h.assertTrue(hp.respawnAtHearth, "a reset to nothing is no choice");
                home = bed(level, r.pos, 8);
                away = bed(level, r.pos, 60);
                sp.setRespawnPosition(spawnAt(level, home), true);
                h.assertTrue(hp.respawnAtHearth, "a bed on your own land keeps home");
                // a bed chosen elsewhere moves it, and you're told
                sp.setRespawnPosition(spawnAt(level, away), true);
                h.assertFalse(hp.respawnAtHearth, "a bed elsewhere moves where you wake");
                h.assertTrue(DirectorLog.recent(sp.getUUID(), 20).stream().anyMatch(e -> e.detail().contains("chose a bed")), "and says so");
                h.assertTrue(HearthService.wakesAt(sp).isEmpty(), "the guide says the bed you chose");
                // a bed at home chooses home again; then away once more
                sp.setRespawnPosition(spawnAt(level, home), true);
                h.assertTrue(hp.respawnAtHearth, "sleeping at home chooses home again");
                sp.setRespawnPosition(spawnAt(level, away), true);
                h.assertFalse(hp.respawnAtHearth, "and the bed elsewhere once more");
                sp = fall(sp);
                h.assertTrue(sp.blockPosition().closerThan(away, 3), "you wake at the bed you chose (at " + where(sp) + ")");
                h.assertFalse(HearthService.player(sp).respawnAtHearth, "the respawn itself changes nothing");
                // that bed broken: home, not world spawn
                level.removeBlock(away.south(), false);
                level.removeBlock(away, false);
                sp = fall(sp);
                h.assertTrue(byFire(sp, r), "with that bed gone you wake at home (at " + where(sp) + ")");
                h.succeed();
            } finally {
                if (home != null) AwakenTests.clearToSky(level, home.offset(-3, -1, -2), home.offset(3, 0, 4));
                if (away != null) AwakenTests.clearToSky(level, away.offset(-3, -1, -2), away.offset(3, 0, 4));
                cleanup(h, r, sp);
            }
        });
    }
}
