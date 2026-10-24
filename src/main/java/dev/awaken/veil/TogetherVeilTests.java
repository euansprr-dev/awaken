package dev.awaken.veil;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.hearth.HearthData;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.hearth.Household;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.Set;

/**
 * GameTests for projected Bodies among friends (SPEC "Together"): a Body takes no player hits unless PvP is on and the
 * attacker isn't in its owner's party, and a friend can never kill its owner through it.
 */
final class TogetherVeilTests {
    private TogetherVeilTests() {}

    static void register() {
        AwakenTests.registerIsolated("together_veil_bodies_spare_friends", 100, h -> {
            ServerLevel level = h.getLevel();
            var server = level.getServer();
            boolean pvpBefore = level.getGameRules().get(GameRules.PVP);
            BlockPos base = h.absolutePos(BlockPos.ZERO).above(2);
            ServerPlayer owner = h.makeMockServerPlayerInLevel(), friend = h.makeMockServerPlayerInLevel(), stranger = h.makeMockServerPlayerInLevel();
            HearthRecord home = new HearthRecord(owner.getUUID(), "owner", base.offset(40, 0, 0), HearthService.OVERWORLD);
            try {
                for (ServerPlayer p : new ServerPlayer[]{owner, friend, stranger}) {
                    p.connection.markClientLoaded();
                    p.teleportTo(level, base.getX() + 0.5, base.getY(), base.getZ() + 0.5, Set.of(), 0F, 0F, true);
                }
                HearthData.get(server).put(home);
                Household.join(server, home, friend); // friends share a Hearth: one party
                h.assertTrue(Projection.start(owner, true, null), "the owner steps out of their body");
                Projection.Spirit s = Projection.live(owner);
                Mannequin body = s.body();
                h.assertTrue(body != null && body.isAlive(), "a Body stays behind");
                float full = body.getHealth();

                level.getGameRules().set(GameRules.PVP, false, server);
                body.hurtServer(level, level.damageSources().playerAttack(stranger), 4F);
                h.assertTrue(body.getHealth() == full, "PvP off: a stranger's blow does nothing");
                body.hurtServer(level, level.damageSources().playerAttack(friend), 4F);
                h.assertTrue(body.getHealth() == full, "nor a friend's");

                level.getGameRules().set(GameRules.PVP, true, server);
                body.hurtServer(level, level.damageSources().playerAttack(friend), 4F);
                h.assertTrue(body.getHealth() == full, "PvP on: a party member still can't hurt it");
                h.assertFalse(Projection.playerMayHurtBody(owner, owner.getUUID()), "nor its own spirit");
                h.assertTrue(Projection.playerMayHurtBody(stranger, owner.getUUID()), "a stranger may, with PvP on");
                body.setInvulnerableTime(0);
                body.hurtServer(level, level.damageSources().playerAttack(stranger), 4F);
                h.assertTrue(body.getHealth() < full, "and does");

                // the Body falls to a friend's hand (any way it could): the owner comes home alive
                Projection.onBodyDeath(body, level.damageSources().playerAttack(friend));
                Projection.tick(owner, s);
                h.assertFalse(Veil.isSpirit(owner), "the spirit is pulled home");
                h.assertTrue(owner.isAlive() && !Projection.owesDeath(owner), "and lives: a friend never kills you through your Body");
            } finally {
                level.getGameRules().set(GameRules.PVP, pvpBefore, server);
                if (Projection.LIVE.containsKey(owner.getUUID())) Projection.end(owner, Projection.EndReason.LOGOUT);
                HearthData.get(server).remove(owner.getUUID());
                for (ServerPlayer p : new ServerPlayer[]{owner, friend, stranger}) server.getPlayerList().remove(p);
            }
            h.succeed();
        });
    }
}
