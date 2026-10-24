package dev.awaken.story;

import dev.awaken.core.Throttle;
import dev.awaken.core.api.MobHooks;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.api.PartyHooks;
import dev.awaken.core.api.StoryHooks;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateState;
import dev.awaken.gates.run.RunState;
import dev.awaken.hearth.HearthData;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.hearth.player.HearthPlayer;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * GameTests for "Together" (SPEC): the world's one story with two and three players, each player's own Ascension
 * tier, calm gates per owner, the Rival's private scene, the dedicated-server helpers, and old single-player saves
 * loading and playing as before.
 */
final class TogetherTests {
    private TogetherTests() {}

    /** The world's story as it was, put back after a test. */
    private record Saved(String ending, int ascension, boolean calm, boolean colour, UUID by, String byName, long day, Map<UUID, Integer> reached) {
        static Saved of(StoryWorld w) {
            return new Saved(w.ending, w.ascension, w.gatesCalm, w.colourRestored, w.endingBy, w.endingByName, w.endingDay, Map.copyOf(w.reached));
        }

        void restore(StoryWorld w) {
            w.ending = ending;
            w.ascension = ascension;
            w.gatesCalm = calm;
            w.colourRestored = colour;
            w.endingBy = by;
            w.endingByName = byName;
            w.endingDay = day;
            w.reached.clear();
            w.reached.putAll(reached);
            w.setDirty();
        }
    }

    private static void fresh(StoryWorld w) {
        w.ending = "";
        w.ascension = 0;
        w.gatesCalm = false;
        w.colourRestored = false;
        w.endingBy = null;
        w.endingByName = "";
        w.endingDay = -1;
        w.reached.clear();
    }

    private static ServerPlayer player(GameTestHelper h, Vec3 at) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        Progression.awaken(p);
        p.teleportTo(h.getLevel(), at.x, at.y, at.z, Set.of(), 0F, 0F, true);
        return p;
    }

    private static void remove(GameTestHelper h, ServerPlayer... ps) {
        for (ServerPlayer p : ps) if (p != null) h.getLevel().getServer().getPlayerList().remove(p);
    }

    static void register() {
        // Two players reach the Seed: the first choice is the world's, the second is told it; Ascension rises once.
        AwakenTests.registerIsolated("together_story_one_ending_per_world", 60, h -> {
            StoryWorld w = StoryWorld.get(h.getLevel().getServer());
            Saved before = Saved.of(w);
            Vec3 at = h.absoluteVec(new Vec3(1.5, 1, 1.5));
            ServerPlayer a = null, b = null, late = null;
            try {
                fresh(w);
                a = player(h, at);
                b = player(h, at.add(2, 0, 0));
                Prologue.progress(a).seen.add("seed:defeated");
                Prologue.progress(b).seen.add("seed:defeated");
                h.assertTrue(StoryContent.ENDINGS.containsKey(dev.awaken.Awaken.id("become")) && StoryContent.ENDINGS.containsKey(dev.awaken.Awaken.id("break")),
                        "the endings are loaded");
                Court.choose(a, "become");
                h.assertTrue(w.ending.equals("become") && a.getUUID().equals(w.endingBy), "the first choice is the world's: " + w.ending);
                h.assertTrue(w.ascension == 1, "Become the Signal raises the world one tier, got " + w.ascension);
                h.assertTrue(Prologue.progress(b).seen.contains("ending:told"), "the friend still choosing at the same Seed is told at once");
                Court.choose(b, "break");
                h.assertTrue(w.ending.equals("become") && !w.gatesCalm, "a later choice never overwrites the world's ending");
                h.assertTrue(w.ascension == 1, "Ascension counts once per world, not per player: " + w.ascension);
                h.assertTrue(w.reached(a.getUUID()) && w.reached(b.getUUID()), "both reached the world's ending");
                h.assertTrue(StoryHooks.ascensionOf(a) == 1 && StoryHooks.ascensionOf(b) == 1, "both took part in its tier");
                // a third player who joins later and hasn't reached the Seed
                late = player(h, at.add(4, 0, 0));
                h.assertTrue(StoryHooks.ascensionOf(late) == 0, "a friend who joins late meets tier 0");
                Prologue.progress(late).seen.add("seed:defeated");
                Court.reofferEnding(late);
                h.assertTrue(Prologue.progress(late).seen.contains("ending:become") && w.ending.equals("become"),
                        "reaching the Seed later tells the world's ending");
            } finally {
                before.restore(w);
                Court.forget();
                remove(h, a, b, late);
            }
            h.succeed();
        });

        // A veteran and a friend who joined late fight beside each other: each meets their own tier.
        AwakenTests.registerIsolated("together_story_late_friend_own_tier", 60, h -> {
            ServerLevel level = h.getLevel();
            StoryWorld w = StoryWorld.get(level.getServer());
            Saved before = Saved.of(w);
            Vec3 here = h.absoluteVec(new Vec3(1.5, 1, 1.5));
            ServerPlayer vet = null, late = null;
            LivingEntity byLate = null, byVet = null;
            try {
                fresh(w);
                w.ending = "become";
                w.ascension = 2;
                vet = player(h, here.add(0, 0, 400)); // far: the first creature appears beside the late friend only
                late = player(h, here.add(1, 0, 0));
                w.reached.put(vet.getUUID(), 2);
                h.assertTrue(StoryHooks.ascensionOf(vet) == 2 && StoryHooks.ascensionOf(late) == 0, "own tiers 2 and 0");
                MobRole role = new MobRole(MobRole.Role.NORMAL, 0, "awaken:bell_crypt", Optional.empty(), "");
                byLate = Mobs.spawn(level, Bestiary.get("toll_ghoul"), here, 0, role, EnumSet.noneOf(dev.awaken.core.Forces.Tag.class));
                h.assertTrue(byLate != null && MobHooks.scaledAscension(byLate) == 0, "a creature by the late friend is scaled for tier 0");
                vet.teleportTo(level, here.x + 1, here.y, here.z + 1, Set.of(), 0F, 0F, true);
                late.teleportTo(level, here.x, here.y, here.z + 300, Set.of(), 0F, 0F, true);
                byVet = Mobs.spawn(level, Bestiary.get("toll_ghoul"), here, 0, role, EnumSet.noneOf(dev.awaken.core.Forces.Tag.class));
                h.assertTrue(byVet != null && MobHooks.scaledAscension(byVet) == 2, "a creature by the veteran is scaled for tier 2");
                double hp = byVet.getMaxHealth() / byLate.getMaxHealth();
                h.assertTrue(Math.abs(hp - 1.6) < 0.02, "tier 2 has +60% health, got x" + hp);

                // the veteran hitting the tier-0 creature hits as if it had tier 2 health; the friend's hits on the
                // tier-2 creature land as if it had tier 0 health; its claws hit the friend at tier 0
                float vetOnLate = hit(byLate, level.damageSources().playerAttack(vet), 16F);
                float lateOnVet = hit(byVet, level.damageSources().playerAttack(late), 16F);
                float clawOnLate = hit(late, level.damageSources().mobAttack(byVet), 13F);
                float clawOnVet = hit(vet, level.damageSources().mobAttack(byVet), 13F);
                h.assertTrue(Math.abs(vetOnLate - 16F / 1.6F) < 0.01, "the veteran's hit on a tier-0 creature: " + vetOnLate);
                h.assertTrue(Math.abs(lateOnVet - 16F * 1.6F) < 0.01, "the friend's hit on a tier-2 creature: " + lateOnVet);
                h.assertTrue(Math.abs(clawOnLate - 13F / 1.3F) < 0.01, "a tier-2 claw on the friend lands at tier 0: " + clawOnLate);
                h.assertTrue(Math.abs(clawOnVet - 13F) < 0.01, "and on the veteran at tier 2: " + clawOnVet);

                // XP: the veteran +30%, the friend unscaled
                double xv = Progression.profile(vet).xp(), xl = Progression.profile(late).xp();
                Progression.grantXp(vet, 10, Progression.XpSource.COMMAND);
                Progression.grantXp(late, 10, Progression.XpSource.COMMAND);
                double gv = Progression.profile(vet).xp() - xv, gl = Progression.profile(late).xp() - xl;
                h.assertTrue(gl > 0 && Math.abs(gv / gl - 1.3) < 0.01, "XP follows the own tier, x" + (gv / gl));
            } finally {
                before.restore(w);
                if (byLate != null) byLate.discard();
                if (byVet != null) byVet.discard();
                remove(h, vet, late);
            }
            h.succeed();
        });

        // The gates are calm for whoever reached the ending; a newcomer's gates still break.
        AwakenTests.registerIsolated("together_story_calm_gates_per_owner", 40, h -> {
            StoryWorld w = StoryWorld.get(h.getLevel().getServer());
            Saved before = Saved.of(w);
            ServerPlayer vet = null, late = null;
            try {
                fresh(w);
                w.ending = "break";
                w.gatesCalm = true;
                vet = player(h, h.absoluteVec(new Vec3(1.5, 1, 1.5)));
                late = player(h, h.absoluteVec(new Vec3(3.5, 1, 1.5)));
                w.reached.put(vet.getUUID(), 0);
                var server = h.getLevel().getServer();
                h.assertTrue(StoryHooks.gatesCalm(server, Optional.of(vet.getUUID())), "the veteran's gates are calm");
                h.assertFalse(StoryHooks.gatesCalm(server, Optional.of(late.getUUID())), "the newcomer's gates are not");
                h.assertTrue(StoryHooks.gatesCalm(server, Optional.empty()), "a gate with no owner follows the world");
                GateRecord mine = new GateRecord(-7, dev.awaken.gates.GateLiveTests.plan(1, dev.awaken.gates.content.Theme.BELL_CRYPT,
                        dev.awaken.gates.content.Layout.LABYRINTH, dev.awaken.gates.content.Twist.NONE, 0), BlockPos.ZERO, 0, 0, 0, 2,
                        GateState.READY, Optional.of(late.getUUID()));
                h.assertFalse(mine.neverBreaks(server), "so a gate rolled for the newcomer still breaks");
                mine.owner = Optional.of(vet.getUUID());
                h.assertTrue(mine.neverBreaks(server), "and one rolled for the veteran never does");
            } finally {
                before.restore(w);
                remove(h, vet, late);
            }
            h.succeed();
        });

        // Vael's beat is one player's scene: a friend standing beside them doesn't see Vael.
        AwakenTests.register("together_story_rival_scene_is_private", 40, h -> {
            ServerPlayer p = player(h, h.absoluteVec(new Vec3(1.5, 1, 1.5)));
            ServerPlayer q = player(h, h.absoluteVec(new Vec3(2.5, 1, 1.5)));
            VaelEntity v = Rival.spawnNear(p);
            try {
                h.assertTrue(v != null && p.getUUID().equals(v.viewer), "Vael knows whose scene it is");
                h.assertTrue(v.broadcastToPlayer(p), "the player sees Vael");
                h.assertFalse(v.broadcastToPlayer(q), "the friend beside them doesn't");
            } finally {
                if (v != null) v.discard();
                remove(h, p, q);
            }
            h.succeed();
        });

        // Dedicated server: per-player throttles (the playtest flag among them), and "/awaken as <player>" runs a command
        // for that player (by name, as vanilla's player arguments take it: mock players all share one name, so these two
        // have their own).
        AwakenTests.register("together_server_throttle_and_as", 40, h -> {
            AwakenTests.Live lp = AwakenTests.survivalPlayer(h, "AsCaller", new BlockPos(1, 1, 1));
            AwakenTests.Live lq = AwakenTests.survivalPlayer(h, "AsTarget", new BlockPos(2, 1, 1));
            ServerPlayer p = lp.player(), q = lq.player();
            Progression.awaken(p);
            Progression.awaken(q);
            try {
                h.assertTrue(Throttle.allow(p, "test_once", 40), "the first press passes");
                h.assertFalse(Throttle.allow(p, "test_once", 40), "a second within the window doesn't");
                h.assertTrue(Throttle.allow(q, "test_once", 40), "another player has their own window");
                int ok = 0;
                for (int i = 0; i < 10; i++) if (Throttle.allowBurst(p, "test_burst", 4, 20)) ok++;
                h.assertTrue(ok == 4, "a burst of 4 per second, got " + ok);
                // the playtest "unfair death" key: one flag per two seconds each
                int fp = 0, fq = 0;
                for (int i = 0; i < 5; i++) {
                    if (dev.awaken.core.CorePayloads.flagUnfair(p)) fp++;
                    if (dev.awaken.core.CorePayloads.flagUnfair(q)) fq++;
                }
                h.assertTrue(fp == 1 && fq == 1, "unfair-death flags: one each, got " + fp + " and " + fq);
                var server = h.getLevel().getServer();
                int levelBefore = Progression.profile(q).level(), callerBefore = Progression.profile(p).level();
                try {
                    server.getCommands().getDispatcher().execute("awaken as AsTarget admin levels 2", server.createCommandSourceStack());
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                    h.fail("/awaken as failed: " + e.getMessage());
                }
                h.assertTrue(Progression.profile(q).level() >= levelBefore + 2, "the console raised that player's level: " + Progression.profile(q).level());
                h.assertTrue(Progression.profile(p).level() == callerBefore, "and only that player's");
            } finally {
                lp.leave();
                lq.leave();
            }
            h.succeed();
        });

        // A world saved before "Together" loads and plays the same for its one player.
        AwakenTests.registerIsolated("together_old_saves_load_the_same", 60, h -> {
            ServerLevel level = h.getLevel();
            StoryWorld w = StoryWorld.get(level.getServer());
            Saved before = Saved.of(w);
            ServerPlayer solo = null;
            try {
                // the story: schema 1 had no "chosen" and no "reached"
                CompoundTag old = new CompoundTag();
                old.putString("ending", "become");
                old.putInt("ascension", 1);
                old.putBoolean("gates_calm", false);
                old.putBoolean("colour", false);
                StoryWorld loaded = StoryWorld.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow();
                h.assertTrue(loaded.ending.equals("become") && loaded.ascension == 1 && loaded.reached.isEmpty(), "an old story loads");
                fresh(w);
                w.ending = loaded.ending;
                w.ascension = loaded.ascension;
                solo = player(h, h.absoluteVec(new Vec3(1.5, 1, 1.5)));
                Prologue.progress(solo).seen.add("seed:defeated");
                Prologue.progress(solo).seen.add("ending:chosen");
                Prologue.progress(solo).seen.add("ending:become");
                Court.reofferEnding(solo); // what login does
                h.assertTrue(StoryHooks.ascensionOf(solo) == 1, "the one who chose meets the world's tier, as before");
                h.assertTrue(PartyHooks.members(solo).equals(List.of(solo.getUUID())), "alone, a party of one");

                // a Hearth saved before households: no members, the owner's home as before
                HearthRecord r = new HearthRecord(solo.getUUID(), "solo", h.absolutePos(new BlockPos(1, 1, 1)), HearthService.OVERWORLD);
                r.tier = 2;
                CompoundTag rec = (CompoundTag) HearthRecord.CODEC.encodeStart(NbtOps.INSTANCE, r).getOrThrow();
                rec.remove("household");
                HearthRecord back = HearthRecord.CODEC.parse(NbtOps.INSTANCE, rec).getOrThrow();
                h.assertTrue(back.members.isEmpty() && back.foundedFrom == null && back.tier == 2, "an old Hearth loads with no members");
                CompoundTag data = new CompoundTag();
                data.putInt("schema", 1);
                net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
                list.add(rec);
                data.put("hearths", list);
                HearthData hd = HearthData.CODEC.parse(NbtOps.INSTANCE, data).getOrThrow();
                h.assertTrue(hd.home(solo.getUUID()).isPresent() && hd.of(solo.getUUID()).isPresent(), "home is still the owner's own Hearth");
                Tag hp = HearthPlayer.MAP_CODEC.codec().encodeStart(NbtOps.INSTANCE, new HearthPlayer()).getOrThrow();
                ((CompoundTag) hp).remove("respawn_hearth");
                h.assertTrue(HearthPlayer.MAP_CODEC.codec().parse(NbtOps.INSTANCE, hp).getOrThrow().respawnHearth == null, "old player Hearth data loads");

                // a gate run saved before "company": a solo run
                RunState run = new RunState();
                run.gateId = 3;
                CompoundTag rs = (CompoundTag) RunState.MAP_CODEC.codec().encodeStart(NbtOps.INSTANCE, run).getOrThrow();
                rs.remove("company");
                h.assertFalse(RunState.MAP_CODEC.codec().parse(NbtOps.INSTANCE, rs).getOrThrow().company, "an old run is a solo run");
                // gates of a single player: still their own, still calm or not with the world
                h.assertTrue(dev.awaken.gates.run.Rooms.partyCompany(5, 1, 1)[0] == 5 && dev.awaken.gates.run.Rooms.partyCompany(5, 1, 1)[1] == 1,
                        "a party of one meets the same rooms");
            } finally {
                before.restore(w);
                remove(h, solo);
            }
            h.succeed();
        });
    }

    /** The damage a hit does after the Ascension evening-out (before armour). */
    private static float hit(LivingEntity target, net.minecraft.world.damagesource.DamageSource source, float amount) {
        LivingIncomingDamageEvent e = new LivingIncomingDamageEvent(target, new DamageContainer(source, amount));
        Ascension.onIncomingDamage(e);
        return e.getAmount();
    }
}
